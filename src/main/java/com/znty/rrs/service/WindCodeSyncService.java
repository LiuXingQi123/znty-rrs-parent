package com.znty.rrs.service;

import com.znty.rrs.common.enums.WindCodeChangeResult;
import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 读取 Wind 代码变更，按 SEC_ID 找到证券并换码，同时处理相关调库记录。 */
@Slf4j
@Service
public class WindCodeSyncService implements RrsScheduledTask {
    /** 任务编码。 */
    public static final String TASK_CODE = "wind_code_sync";
    /** 任务名称。 */
    public static final String TASK_NAME = "Wind代码变更同步";
    /** 查询 Wind 代码变更和证券信息。 */
    @Resource
    private WindCodeSyncMapper windCodeSyncMapper;
    /** 把证券信息、临时代码记录和调库记录中的旧代码改成新代码。 */
    @Resource
    private TempSecurityCodeService tempSecurityCodeService;

    /**
     * 返回定时任务的注册编码。
     *
     * @return Wind 代码变更同步任务编码
     */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /**
     * 提供任务页面展示的扫描条件、处理范围和执行结果说明。
     *
     * @return 任务使用说明
     */
    @Override
    public String getParamHelp() {
        return "参数说明：本任务无需扩展参数，请将 param_json 留空\n"
                + "扫描条件：ais_inv_ods.wind_cbondchangewindcode，CHANGE_DATE 为昨天至今天的 yyyyMMdd，旧码不等于新码\n"
                + "身份匹配：按唯一 SEC_ID 查询 rrs_securityinfo，原地更名并保留证券 ID\n"
                + "临时代码记录：指 rrs_temp_security_code 表中的记录；official 新增一条，temporary 使用原记录并转为 temp_converted，temp_converted 新增本次记录并保留历史\n"
                + "处理范围：仅本次旧新码，复用普通池、CRMW及在途申请转换；主档缺失或旧码不存在时特别提示并跳过，不计失败\n"
                + "执行保障：每条换码一起保存，失败就撤销这条的修改；成功后在临时代码记录备注中保留变更标记，重复扫描不重复调库\n"
                + "结果口径：正常完成扫描和逐条处理即为任务成功，单条失败保留明细及计数；扫描或任务级异常才算任务失败\n"
                + "执行频率：Demo 每 10 分钟的第 2 分钟执行，会修改池状态，须与其他调库任务错开";
    }

    /**
     * 执行一次 Wind 代码变更同步，记录开始和结束日志并返回汇总结果。
     * 单条换码失败由 doSync 记录；扫描等任务级异常由本方法记录为任务失败。
     *
     * @return 本次执行状态、各类处理数量及明细日志
     */
    @Override
    public ScheduledTaskResult execute() {
        // 1、记录开始时间，准备明细日志和统计结果。
        Date start = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        detail.line("任务开始：【" + TASK_NAME + "】");
        SyncSummary summary = new SyncSummary();
        boolean executionFailed = false;
        try {
            // 2、查询并逐条换码，每条的成功、跳过或失败结果由 doSync 记录。
            doSync(detail, summary);
        } catch (Exception e) {
            // 2.1 查询等操作出错，导致任务没执行完时，记为任务失败。
            executionFailed = true;
            summary.failed++;
            log.error("Wind 代码变更扫描异常", e);
            detail.line("ERROR", "扫描执行异常：" + e.getMessage());
        }
        // 3、汇总处理数量，记录任务结束日志并返回结果。
        long duration = System.currentTimeMillis() - begin;
        String message = "成功 " + summary.updated + " 条，主档缺失跳过 " + summary.missing
                + " 条，旧码缺失跳过 " + summary.missingOldCode + " 条，重复跳过 " + summary.repeated + " 条，失败 " + summary.failed + " 条";
        detail.line("任务结束（" + (executionFailed ? "失败" : "成功") + "）：" + message + "，耗时 " + duration + " 毫秒");
        return executionFailed ? ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, message, start, duration, detail.build())
                : ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, summary.updated, start, duration, detail.build());
    }

    /**
     * 扫描上海日期昨天至今天的代码变更，按 SEC_ID 分组后逐条执行换码。
     * 同一只券先改 A→B，再改 B→C；前一条失败了，这只券后面的变更就先不处理。
     *
     * @param detailLog 本次任务的明细日志，记录每条变更的处理结果
     * @param summary 本次任务的累计结果，分别统计成功、各类跳过及失败数量
     */
    private void doSync(TaskDetailLog detailLog, SyncSummary summary) {
        // 1、按上海日期确定查询范围，包含昨天和今天，格式为 yyyyMMdd。
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String startDate = today.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        String endDate = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        detailLog.line("扫描条件：ais_inv_ods.wind_cbondchangewindcode，CHANGE_DATE=" + startDate + "～" + endDate
                + "（yyyyMMdd），S_INFO_OLDWINDCODE!=S_INFO_NEWWINDCODE；按唯一 SEC_ID 匹配主档");
        // 2、查询日期范围内旧代码与新代码不同的变更记录。
        List<WindCodeChangeDto> windCodeChanges = windCodeSyncMapper.queryRecentWindCodeChangeList(startDate, endDate);
        detailLog.line("扫描完成：候选 " + windCodeChanges.size() + " 条");
        // 3、按 SEC_ID 分组，把同一只券的变更放在一起处理。
        Map<String, List<WindCodeChangeDto>> changesBySecId = groupChangesBySecId(windCodeChanges);
        for (List<WindCodeChangeDto> securityChanges : changesBySecId.values()) {
            List<WindCodeChangeDto> pendingChanges = new ArrayList<>(securityChanges);
            while (!pendingChanges.isEmpty()) {
                // 3.1 找出接下来要处理的变更，例如 A→B 要先于 B→C 执行。
                WindCodeChangeDto currentChange = findNextProcessableChange(pendingChanges);
                if (currentChange == null) {
                    // 3.2 如果变更相互循环，例如 A→B、B→A，就记录失败，停止处理这只券。
                    summary.failed += pendingChanges.size();
                    detailLog.line("ERROR", "代码变更链存在循环，SEC_ID=" + pendingChanges.get(0).getSecId());
                    break;
                }
                // 4、取出这条变更，准备日志中要展示的证券和代码信息。
                pendingChanges.remove(currentChange);
                String changeDescription = "SEC_ID=" + currentChange.getSecId() + "，旧代码=" + currentChange.getOldWindCode()
                        + "，新代码=" + currentChange.getNewWindCode() + "，变更日期=" + currentChange.getChangeDate();
                try {
                    // 5、执行换码，证券信息和调库记录一起保存，失败就撤销这条的修改。
                    WindCodeChangeResult changeResult = tempSecurityCodeService.editWindCodeChangeByJob(currentChange);
                    // 6、按返回结果统计成功或跳过数量，并记录原因。
                    if (changeResult == WindCodeChangeResult.UPDATED) {
                        summary.updated++;
                        detailLog.line("换码成功：" + changeDescription);
                    } else if (changeResult == WindCodeChangeResult.MISSING_MASTER) {
                        summary.missing++;
                        detailLog.line("WARN", "特别提示：按 SEC_ID 未找到证券主档，本条不处理，请核查主档数据及ID映射；" + changeDescription);
                    } else if (changeResult == WindCodeChangeResult.MISSING_OLD_CODE) {
                        summary.missingOldCode++;
                        detailLog.line("WARN", "特别提示：旧代码不在 rrs_securityinfo 中，本条不处理，不新增临时代码记录或调库记录；" + changeDescription);
                    } else if (changeResult == WindCodeChangeResult.ALREADY_COMPLETED) {
                        summary.repeated++;
                        detailLog.line("重复事件跳过：" + changeDescription);
                    } else {
                        throw new IllegalStateException("Wind 单条事务未返回明确结果");
                    }
                } catch (Exception e) {
                    // 6.1 换码报错时，记录原因；这只券后面的变更也先不执行，一并计入失败。
                    summary.failed++;
                    log.error("Wind 换码失败，SEC_ID={}，旧码={}，新码={}", currentChange.getSecId(), currentChange.getOldWindCode(), currentChange.getNewWindCode(), e);
                    detailLog.line("ERROR", "换码失败：" + changeDescription + "，原因=" + e.getMessage());
                    summary.failed += pendingChanges.size();
                    if (!pendingChanges.isEmpty()) {
                        detailLog.line("ERROR", "同一证券后续 " + pendingChanges.size() + " 条事件未执行：前置换码失败，SEC_ID=" + currentChange.getSecId());
                    }
                    break;
                }
            }
        }
    }

    /**
     * 按 SEC_ID 整理代码变更，让同一证券的连续换码在同一组中处理。
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
     * 查找下一条可处理的换码，保证 A→B 先于 B→C 执行。
     *
     * @param pendingChanges 同一 SEC_ID 尚未处理的换码记录
     * @return 不需要等待其他换码的记录；没有这样的记录时返回 null
     */
    private WindCodeChangeDto findNextProcessableChange(List<WindCodeChangeDto> pendingChanges) {
        for (WindCodeChangeDto change : pendingChanges) {
            boolean requiresEarlierChange = false;
            for (WindCodeChangeDto otherChange : pendingChanges) {
                // 其他记录的新码等于本条旧码时，需要先执行其他记录。
                if (otherChange != change && change.getOldWindCode() != null
                        && change.getOldWindCode().equals(otherChange.getNewWindCode())) {
                    requiresEarlierChange = true;
                    break;
                }
            }
            if (!requiresEarlierChange) {
                return change;
            }
        }
        return null;
    }

    /** 单次任务的业务处理计数，供核心流程与执行结果包装共用。 */
    private static final class SyncSummary {
        /** 实际换码成功条数。 */
        private int updated;
        /** 主档缺失跳过条数。 */
        private int missing;
        /** 旧代码不在主档中的跳过条数。 */
        private int missingOldCode;
        /** 已完成事件跳过条数。 */
        private int repeated;
        /** 换码失败及前置失败未执行条数。 */
        private int failed;
    }

}
