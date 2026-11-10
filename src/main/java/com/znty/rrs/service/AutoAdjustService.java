package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 到期证券自动出池
 *
 * 执行顺序：
 * 1. 合并扩展参数和关系配置中的扫描池
 * 2. 按 maturityOffsetDays 扫描目标池内已生效且到期日早于截止日期的债券和股票
 * 3. 跳过主体、基金、CRMW、调出限制池和已提前出池的记录
 * 4. 软删除池状态成功后写入自动出池日志并统计实际出池数量
 * 5. 返回本轮扫描、出池和未处理数量
 */
@Slf4j
@Service
public class AutoAdjustService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "security_expired_auto_out";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 自动调出原因（写入调库日志） */
    private static final String REASON_EXPIRED_OUT = "证券到期自动调出";
    /**
     * 本任务扩展参数说明（配置页按行拆成列表展示）
     */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[15],\"maturityOffsetDays\":1}</code>；也可不填 poolIds，仅扫描投资池关系配置中绑定了本任务的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：扫描 15（债券禁止库）内已生效的债券、股票，到期后从 15（债券禁止库）自动调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多池）：<code>{\"poolIds\":[15,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：分别扫描 15（债券禁止库）、16（观察池）内的证券，到期后从各自所在池自动调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（到期出池扫描池）：可选；与投资池「关系配置 → 自动调出规则」中绑定本任务的池取并集后扫描\n"
                    + "maturityOffsetDays（到期日期偏移天数）：可选，省略默认 1；须为 int 范围内的整数，允许 0 和负数\n"
                    + "天数含义：到期日早于数据库当前日期减去该天数才出池（不含截止当天）；1=早于昨天，0=早于今天，3=早于三天前，-1=早于明天\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：扫描目标池内已生效的债券、股票；按 maturityOffsetDays 计算截止日期，默认 1 沿用早于昨天（T-2）的口径\n"
                    + "排除范围：主体、基金和 CRMW 不处理；CRMW 请使用“CRMW到期自动出池”任务\n"
                    + "限制规则：证券已在目标池配置的调出限制池时，跳过该条记录\n"
                    + "执行方式：直接生效，不走审批；仅软删除成功才写日志并计入影响条数\n"
                    + "参数格式错误时，本轮任务失败";

    /** 自动调库查询 Mapper */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 证券池调库落地 Mapper */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 投资池查询 Mapper */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 定时任务配置 Mapper（param_json / 名称） */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;
    /** 扫描池并集（参数 ∪ 关系配置） */
    @Resource
    private AutoAdjustPoolScopeHelper poolScopeHelper;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /**
     * 向配置页提供本任务扩展参数填写说明（仅 JSON）
     */
    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /** 兼容旧调用入口，内部调用当前定时任务执行方法 */
    public void executeAutoAdjust() {
        execute();
    }

    /**
     * 执行到期证券自动出池
     *
     * 执行顺序：
     * 1. 读取任务名称并记录任务开始
     * 2. 解析扫描池并逐池查询到期证券
     * 3. 对不满足业务条件的证券记录原因后跳过
     * 4. 对实际出池的证券写入调库日志并汇总结果
     * 5. 返回任务成功或任务级失败结果
     *
     * @return 本轮任务执行结果
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        // 1. 读取任务展示名称
        String taskName = resolveTaskName();
        // 2. 记录任务开始
        infoDetail(detail, "任务开始：" + taskName);
        try {
            // 3. 执行到期证券自动出池
            AutoOutSummary summary = doAutoOutExpired(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = summary.buildMessage();
            // 4. 记录任务结果并返回成功结果
            infoDetail(detail, "任务结束：" + message + "；耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, summary.total, startTime, duration,
                    detail.build());
        } catch (BizException e) {
            long duration = System.currentTimeMillis() - begin;
            warnDetail(detail, "任务未完成：" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName, e.getMessage(), startTime, duration,
                    detail.build());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", taskName, e);
            detail.line("任务未完成：系统异常，" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName,
                    "执行异常: " + e.getMessage(), startTime, duration, detail.build());
        }
    }

    /**
     * 按扫描池处理到期证券
     *
     * 执行顺序：
     * 1. 解析扩展参数和关系配置得到扫描池
     * 2. 读取投资池和关系配置，准备调库所需信息
     * 3. 逐池查询到期证券并按业务条件判断是否处理
     * 4. 统计各池实际出池和未处理数量
     *
     * @param taskName 任务展示名称
     * @param detail 过程日志
     * @return 本轮到期证券出池汇总
     */
    private AutoOutSummary doAutoOutExpired(String taskName, TaskDetailLog detail) {
        // 1. 读取本轮扩展参数，解析日期偏移并合并待扫描池
        String paramJson = resolveParamJson(detail);
        int maturityOffsetDays = AutoAdjustPoolScopeHelper.parseMaturityOffsetDays(paramJson);
        List<Long> poolIds = poolScopeHelper.resolveUnionPoolIds(
                paramJson, TASK_CODE, RuleType.AUTO_OUT.getCode(), detail);
        infoDetail(detail, "扫描范围：目标池编号 " + poolIds);
        String maturityRule = "到期日早于当前日期减去 " + maturityOffsetDays + " 天";
        infoDetail(detail, "扫描条件：已生效的债券和股票，且" + maturityRule
                + "（数据库日期，maturityOffsetDays=" + maturityOffsetDays + "）；CRMW、主体和基金不处理");
        // 2. 读取投资池信息
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> poolList = investmentPoolMapper.queryPoolList();
        if (poolList != null) {
            for (InvestmentPoolBo pool : poolList) {
                poolMap.put(pool.getId(), pool);
            }
        }
        // 3. 准备自动调库批次和提交时间
        ScheduledAdjustBatchNoContext batchNoContext = new ScheduledAdjustBatchNoContext();
        Date submitTime = batchNoContext.getSubmitTime();
        // 4. 读取全量池关系，供调出限制池校验
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        AutoOutSummary summary = new AutoOutSummary();
        // 5. 逐个目标池扫描和处理到期证券
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                summary.addSkipped(1);
                skipDetail(detail, "目标池【" + poolId + "】不存在，本轮未处理");
                continue;
            }
            // 5.1 读取当前目标池的调出限制池
            List<Long> outRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.OUT_RESTRICT.getCode(), allRelations);
            List<ScheduledAdjustCandidateDto> expiredList = autoAdjustMapper.queryPoolSecurityByExpired(
                    poolId, maturityOffsetDays);
            if (expiredList == null || expiredList.isEmpty()) {
                summary.addPool(poolId, pool.getPoolName(), 0, 0, 0);
                infoDetail(detail, "扫描结果：池【" + pool.getPoolName() + "】没有发现到期证券");
                continue;
            }
            int poolCount = 0;
            int poolSkippedCount = 0;
            for (ScheduledAdjustCandidateDto sec : expiredList) {
                if (sec == null || !StringUtils.hasText(sec.getSecurityCode())) {
                    poolSkippedCount++;
                    skipDetail(detail, "扫描结果缺少证券编码，无法处理");
                    continue;
                }
                // 5.2 对齐老 AdjustPoolByRule.checkSecurityOutPoolRelation（关系 12 / 调出限制池），命中时跳过
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        securityPoolAdjustMapper.querySecurityCurrentPoolIdList(sec.getSecurityCode()),
                        outRestrictPoolIds)) {
                    poolSkippedCount++;
                    skipDetail(detail, "证券【" + sec.getSecurityCode() + "】：当前位于调出限制池，本轮不处理");
                    continue;
                }
                // 5.3 软删除目标池状态，未删到时视为已被其他处理提前出池
                int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(sec.getSecurityCode(), poolId);
                if (deleted == 0) {
                    poolSkippedCount++;
                    skipDetail(detail, "证券【" + sec.getSecurityCode() + "】：当前已不在目标池，本轮不处理");
                    continue;
                }
                // 5.4 准备自动出池日志字段
                sec.setAdjustType("自动调整");
                sec.setAdjustMode(AdjustMode.OUT.getCode());
                sec.setTargetPoolId(poolId);
                sec.setTargetPoolName(pool.getPoolName());
                sec.setPoolType(pool.getPoolType());
                sec.setAuditStatus(AuditStatus.APPROVED.getCode());
                sec.setAdjusterId(AUTO_ADJUSTER_ID);
                sec.setAdjusterName(AUTO_ADJUSTER_NAME);
                String reason = ScheduledAdjustLogHelper.appendDetails(REASON_EXPIRED_OUT,
                        ScheduledAdjustLogHelper.dateDetail("到期日", sec.getMaturityDate()),
                        "出池口径：" + maturityRule);
                sec.setAdjustReason(reason);
                // 5.5 生成批次号并写入自动出池日志
                String batchNo = batchNoContext.resolveBondBatchNo(
                        sec.getSecurityCode(), poolId, AdjustMode.OUT.getCode());
                sec.setAdjustBatchNo(batchNo);
                sec.setSubmitTime(submitTime);
                // 5.6 软删除成功后写入自动出池日志
                securityPoolAdjustMapper.addAdjustLog(sec);
                poolCount++;
            }
            summary.addPool(poolId, pool.getPoolName(), expiredList.size(), poolCount, poolSkippedCount);
            infoDetail(detail, "处理结果：池【" + pool.getPoolName() + "】实际出池 "
                    + poolCount + " 条，未处理 " + poolSkippedCount + " 条");
        }
        return summary;
    }

    /** 本轮到期证券出池汇总 */
    private static final class AutoOutSummary {

        /** 本轮实际出池总数 */
        private int total;
        /** 本轮扫描到的候选记录数 */
        private int candidateCount;
        /** 本轮未处理的记录数 */
        private int skippedCount;
        /** 按目标池保存实际出池数量 */
        private final Map<Long, PoolSummary> poolSummaries = new LinkedHashMap<>();

        /** 累加未处理记录数 */
        private void addSkipped(int count) {
            if (count > 0) {
                skippedCount += count;
            }
        }

        /** 累加目标池出池数量 */
        private void addPool(Long poolId, String poolName, int candidates, int count, int skipped) {
            candidateCount += candidates;
            skippedCount += skipped;
            poolSummaries.put(poolId, new PoolSummary(poolId, poolName, candidates, count, skipped));
            total += count;
        }

        /** 构建最近执行结果文案 */
        private String buildMessage() {
            StringBuilder message = new StringBuilder("任务完成：扫描候选 ")
                    .append(candidateCount).append(" 条，实际出池 ")
                    .append(total).append(" 条，未处理 ")
                    .append(skippedCount).append(" 条");
            if (!poolSummaries.isEmpty()) {
                message.append("；池明细：");
                int index = 0;
                for (PoolSummary pool : poolSummaries.values()) {
                    if (index++ > 0) {
                        message.append("；");
                    }
                    message.append(pool.poolName).append("（").append(pool.poolId).append("）：扫描 ")
                            .append(pool.candidateCount).append(" 条，出池 ")
                            .append(pool.count).append(" 条，未处理 ")
                            .append(pool.skippedCount).append(" 条");
                }
            }
            return message.toString();
        }
    }

    /** 单个目标池出池统计 */
    private static final class PoolSummary {

        /** 目标池 ID */
        private final Long poolId;
        /** 目标池名称 */
        private final String poolName;
        /** 扫描候选数量 */
        private final int candidateCount;
        /** 实际出池数量 */
        private final int count;
        /** 未处理数量 */
        private final int skippedCount;

        /** 创建目标池统计 */
        private PoolSummary(Long poolId, String poolName, int candidateCount, int count, int skippedCount) {
            this.poolId = poolId;
            this.poolName = poolName;
            this.candidateCount = candidateCount;
            this.count = count;
            this.skippedCount = skippedCount;
        }
    }

    /**
     * 读取本轮任务使用的扩展参数
     *
     * 执行顺序：
     * 1. 从任务配置读取 param_json 并记录过程日志
     * 2. 返回参数原文，供日期偏移和扫描池解析共用
     *
     * @param detail 过程日志
     * @return 扩展参数原文，未配置时为 null
     */
    private String resolveParamJson(TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        String paramJson = (conf != null && StringUtils.hasText(conf.getParamJson()))
                ? conf.getParamJson().trim() : null;
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        return paramJson;
    }

    /**
     * 解析扩展参数中的扫描池
     *
     * 非法 JSON 或空池列表直接返回业务错误，正式任务入口使用参数池和关系配置的并集
     *
     * @param raw 扩展参数原文
     * @param taskName 任务展示名称
     * @return 扫描池 ID 列表
     */
    List<Long> parsePoolIds(String raw, String taskName) {
        List<Long> ids = AutoAdjustPoolScopeHelper.parseOptionalPoolIds(raw);
        if (ids.isEmpty()) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[15]}");
        }
        return ids;
    }

    /**
     * 写 INFO 级过程日志并同步控制台
     */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line(line);
        }
    }

    /**
     * 写 WARN 级过程日志并同步控制台
     */
    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line(line);
        }
    }

    /**
     * 记录一条业务上未处理的原因，不把可预期情况写成任务异常
     *
     * @param detail 过程日志
     * @param line 未处理原因
     */
    private void skipDetail(TaskDetailLog detail, String line) {
        log.info("定时任务未处理：{}", line);
        if (detail != null) {
            detail.line("未处理：" + line);
        }
    }

    /**
     * 单测入口：解析 poolIds
     */
    List<Long> parsePoolIds(String raw) {
        return parsePoolIds(raw, TASK_CODE);
    }

    /**
     * 从库表读取任务展示名称，缺失时回退为任务编码
     */
    private String resolveTaskName() {
        try {
            SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
            if (conf != null && StringUtils.hasText(conf.getTaskName())) {
                return conf.getTaskName();
            }
        } catch (Exception e) {
            log.debug("读取任务名称失败: {}", TASK_CODE);
        }
        return TASK_CODE;
    }
}
