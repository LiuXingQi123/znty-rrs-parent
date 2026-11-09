package com.znty.rrs.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.WindCodeChangeResult;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Wind代码变更同步
 *
 * 执行顺序：
 * 1. 按扫描天数查询 Wind 代码变更记录
 * 2. 按 SEC_ID 合并同一证券的最新变更
 * 3. 校验最新记录并调用换码业务处理
 * 4. 汇总成功、跳过和失败数量
 */
@Slf4j
@Service
public class WindCodeSyncService implements RrsScheduledTask {
    /** 任务编码 */
    public static final String TASK_CODE = "wind_code_sync";
    /** 任务名称 */
    public static final String TASK_NAME = "Wind代码变更同步";
    /** 未填写扫描天数时使用 1 天 */
    private static final int DEFAULT_SCAN_DAYS = 1;
    /** 解析任务扩展参数，拒绝 JSON 对象后面再拼其他内容 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    /** 读取页面保存的任务扩展参数 */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;
    /** 查询 Wind 代码变更和证券信息 */
    @Resource
    private WindCodeSyncMapper windCodeSyncMapper;
    /** 把证券信息、临时代码记录和调库记录中的旧代码改成新代码 */
    @Resource
    private TempSecurityCodeService tempSecurityCodeService;

    /** 返回 Wind代码变更同步任务编码 */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** 提供 Wind代码变更同步任务参数说明 */
    @Override
    public String getParamHelp() {
        return "参数说明：scanDays 为扫描天数，须为正整数；扩展参数留空或省略 scanDays 时默认 1 天\n"
                + PARAM_HELP_TOOLTIP_PREFIX + "填写示例：{\"scanDays\":3}，从数据库当前日期的 3 天前开始扫描\n"
                + "扫描条件：SQL 使用数据库 NOW() 往前减 scanDays 天，CHANGE_DATE 为八位 yyyyMMdd；同一 SEC_ID 取 CHANGE_DATE 最新记录，旧码不等于新码\n"
                + "身份匹配：按唯一 SEC_ID 查询 rrs_securityinfo，拆分保存旧临时代码失效主档和新代码主档\n"
                + "临时代码记录：指 rrs_temp_security_code 表中的记录；official 新增一条，temporary 使用原记录并转为 temp_converted，temp_converted 新增本次记录并保留历史\n"
                + "处理范围：仅本次旧新码，复用普通池、CRMW及在途申请转换；主档缺失时特别提示并跳过；主档有数据时，从当前代码拆分为失效旧主档和最新代码主档\n"
                + "执行保障：每条换码一起保存，失败就撤销这条的修改；成功后在临时代码记录备注中保留变更标记，重复扫描不重复调库\n"
                + "结果口径：正常完成扫描和逐条处理即为任务成功，单条失败保留明细及计数；扫描或任务级异常才算任务失败\n"
                + "执行频率：Demo 每 10 分钟的第 2 分钟执行，会修改池状态，须与其他调库任务错开";
    }

    /**
     * 执行 Wind代码变更同步
     *
     * 执行顺序：
     * 1. 读取扫描配置并记录任务开始
     * 2. 查询并逐条处理 Wind 代码变更
     * 3. 记录扫描级异常和逐条处理结果
     * 4. 汇总数量并返回任务结果
     *
     * @return 本次执行状态、各类处理数量及明细日志
     */
    @Override
    public ScheduledTaskResult execute() {
        // 1. 记录开始时间，准备明细日志和统计结果
        Date start = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        detail.line("任务开始：【" + TASK_NAME + "】");
        SyncSummary summary = new SyncSummary();
        boolean executionFailed = false;
        try {
            // 2. 查询并逐条换码，由 doSync 记录每条结果
            doSync(detail, summary);
        } catch (Exception e) {
            // 2.1 查询等操作出错时记为任务失败
            executionFailed = true;
            summary.failed++;
            log.error("Wind 代码变更扫描异常", e);
            detail.line("ERROR", "扫描执行异常：" + e.getMessage());
        }
        // 3. 汇总处理数量，记录任务结束日志并返回结果
        long duration = System.currentTimeMillis() - begin;
        String message = "成功 " + summary.updated + " 条，主档缺失跳过 " + summary.missing
                + " 条，已是新码跳过 " + summary.alreadyCurrent + " 条，重复跳过 " + summary.repeated + " 条，失败 " + summary.failed + " 条";
        detail.line("任务结束（" + (executionFailed ? "失败" : "成功") + "）：" + message + "，耗时 " + duration + " 毫秒");
        return executionFailed ? ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, message, start, duration, detail.build())
                : ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, summary.updated, start, duration, detail.build());
    }

    /**
     * 读取扫描范围并按 SEC_ID 逐条执行换码
     *
     * 执行顺序：
     * 1. 读取扫描天数并查询最新变更
     * 2. 按 SEC_ID 分组并保留最新变更
     * 3. 校验同一证券的最新代码并执行换码
     * 4. 记录每条成功、跳过或失败结果
     *
     * @param detailLog 本次任务的明细日志
     * @param summary 本次任务的累计结果
     */
    private void doSync(TaskDetailLog detailLog, SyncSummary summary) {
        // 1. 读取页面保存的扫描天数，日期范围仍由数据库计算
        int scanDays = resolveScanDays();
        detailLog.line("扫描天数=" + scanDays + "，日期按数据库 NOW() 计算");
        detailLog.line("扫描条件：ais_inv_ods.wind_cbondchangewindcode，CHANGE_DATE >= DATE_FORMAT(TIMESTAMPADD(DAY, -"
                + scanDays + ", NOW()), '%Y%m%d')"
                + "，同一 SEC_ID 取 CHANGE_DATE 最新记录，S_INFO_OLDWINDCODE!=S_INFO_NEWWINDCODE；按唯一 SEC_ID 匹配主档");
        // 2. 查询最新变更记录，同一只券的较早记录不再重复处理
        List<WindCodeChangeDto> windCodeChanges = windCodeSyncMapper.queryRecentWindCodeChangeList(scanDays);
        detailLog.line("扫描完成：候选 " + windCodeChanges.size() + " 条");
        // 3. 按 SEC_ID 分组，最新日期下内容相同的多条记录只处理一次
        Map<String, List<WindCodeChangeDto>> changesBySecId = groupChangesBySecId(windCodeChanges);
        for (List<WindCodeChangeDto> securityChanges : changesBySecId.values()) {
            WindCodeChangeDto currentChange = securityChanges.get(0);
            String changeDescription = "SEC_ID=" + currentChange.getSecId() + "，变更记录旧代码=" + currentChange.getOldWindCode()
                    + "，新代码=" + currentChange.getNewWindCode() + "，变更日期=" + currentChange.getChangeDate();
            try {
                // 4. 检查最新日期下的新代码是否一致
                validateLatestChanges(securityChanges);
                // 5. 按 SEC_ID 执行换码并同步相关记录
                WindCodeChangeResult changeResult = tempSecurityCodeService.editWindCodeChangeByJob(currentChange);
                // 6. 按返回结果统计成功或跳过数量，并记录原因
                if (changeResult == WindCodeChangeResult.UPDATED) {
                    summary.updated++;
                    detailLog.line("换码成功：" + changeDescription);
                } else if (changeResult == WindCodeChangeResult.MISSING_MASTER) {
                    summary.missing++;
                    detailLog.line("WARN", "特别提示：按 SEC_ID 未找到证券主档，本条不处理，请核查主档数据及ID映射；" + changeDescription);
                } else if (changeResult == WindCodeChangeResult.ALREADY_CURRENT) {
                    summary.alreadyCurrent++;
                    detailLog.line("已是新码跳过：主档当前代码已经是最新代码，不新增业务记录；" + changeDescription);
                } else if (changeResult == WindCodeChangeResult.ALREADY_COMPLETED) {
                    summary.repeated++;
                    detailLog.line("重复事件跳过：" + changeDescription);
                } else {
                    throw new IllegalStateException("Wind 单条事务未返回明确结果");
                }
            } catch (Exception e) {
                // 6.1 单只证券失败时记录原因，继续处理其他 SEC_ID
                summary.failed++;
                log.error("Wind 换码失败，SEC_ID={}，记录旧码={}，最新代码={}", currentChange.getSecId(),
                        currentChange.getOldWindCode(), currentChange.getNewWindCode(), e);
                detailLog.line("ERROR", "换码失败：" + changeDescription + "，原因=" + e.getMessage());
            }
        }
    }

    /**
     * 读取页面扩展参数中的扫描天数
     *
     * 执行顺序：
     * 1. 读取任务扩展参数，未填写时使用默认值
     * 2. 校验 JSON 对象和字段名称
     * 3. 校验扫描天数为正整数
     *
     * @return 本次扫描天数
     */
    private int resolveScanDays() {
        // 1. 读取页面保存的参数，留空就使用默认 1 天
        SysScheduledTaskBo task = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (task == null || !StringUtils.hasText(task.getParamJson())) {
            return DEFAULT_SCAN_DAYS;
        }
        // 2. 检查参数是不是有效的 JSON 对象
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(task.getParamJson());
        } catch (Exception e) {
            throw new BizException("Wind代码变更同步扩展参数 JSON 格式不正确");
        }
        if (root == null || !root.isObject()) {
            throw new BizException("Wind代码变更同步扩展参数须为 JSON 对象，例如 {\"scanDays\":3}");
        }
        // 3. 只接收 scanDays，字段名写错就提示
        root.fieldNames().forEachRemaining(field -> {
            if (!"scanDays".equals(field)) {
                throw new BizException("Wind代码变更同步扩展参数包含未知字段：" + field);
            }
        });
        // 4. 未填 scanDays 使用 1 天，填写时必须是正整数
        JsonNode scanDays = root.get("scanDays");
        if (scanDays == null) {
            return DEFAULT_SCAN_DAYS;
        }
        if (!scanDays.isIntegralNumber() || !scanDays.canConvertToInt() || scanDays.intValue() <= 0) {
            throw new BizException("scanDays 须为 int 范围内的正整数，例如 {\"scanDays\":3}");
        }
        return scanDays.intValue();
    }

    /**
     * 按 SEC_ID 整理最新变更，便于检查同一日期是否存在冲突记录
     *
     * @param windCodeChanges Wind 查询返回的变更记录
     * @return SEC_ID 与变更列表的对应关系，保留各证券首次出现的顺序
     */
    private Map<String, List<WindCodeChangeDto>> groupChangesBySecId(List<WindCodeChangeDto> windCodeChanges) {
        Map<String, List<WindCodeChangeDto>> changesBySecId = new LinkedHashMap<>();
        for (WindCodeChangeDto change : windCodeChanges) {
            changesBySecId.computeIfAbsent(change.getSecId(), key -> new ArrayList<>()).add(change);
        }
        return changesBySecId;
    }

    /**
     * 检查最新日期下的记录是否指向同一个新代码，避免同一天有多个不同的新代码时随意换码
     *
     * @param changes 同一 SEC_ID 在最新 CHANGE_DATE 下的变更记录
     */
    private void validateLatestChanges(List<WindCodeChangeDto> changes) {
        WindCodeChangeDto first = changes.get(0);
        for (WindCodeChangeDto change : changes) {
            if (!Objects.equals(first.getNewWindCode(), change.getNewWindCode())
                    || !Objects.equals(first.getChangeDate(), change.getChangeDate())) {
                throw new BizException("同一 SEC_ID 的最新日期存在不同变更，无法确定最新代码，请核查源数据；SEC_ID="
                        + first.getSecId() + "，CHANGE_DATE=" + first.getChangeDate());
            }
        }
    }

    /** 单次任务的业务处理计数，供核心流程与执行结果包装共用 */
    private static final class SyncSummary {
        /** 实际换码成功条数 */
        private int updated;
        /** 主档缺失跳过条数 */
        private int missing;
        /** 主档当前代码已经是最新代码的跳过条数 */
        private int alreadyCurrent;
        /** 已完成事件跳过条数 */
        private int repeated;
        /** 换码失败或最新记录冲突的证券数量 */
        private int failed;
    }

}
