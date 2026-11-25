package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.common.enums.SecurityStatus;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 到期证券自动出池
 *
 * 执行顺序：
 * 1. 合并扩展参数和关系配置中的扫描池
 * 2. 扫描 ip_pool_status 内已生效且状态为 D 或摘牌日早于截止日的债券（含 ABS、CRMW）
 * 3. 剔除 Excel 保护记录，跳过命中调出限制池或已提前出池的记录
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

    /** 任务默认展示名称 */
    private static final String DEFAULT_TASK_NAME = "到期证券自动出池";

    /**
     * 本任务扩展参数说明（配置页按行拆成列表展示）
     */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[15],\"maturityOffsetDays\":1}</code>；也可不填 poolIds，仅扫描投资池关系配置中绑定了本任务的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：扫描 15（债券禁止库）中已生效且未删除的债券，找出到期候选\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多池）：<code>{\"poolIds\":[15,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：分别扫描 15（债券禁止库）、16（观察池）中的到期债券候选\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（到期出池扫描池）：可选；与投资池「关系配置 → 自动调出规则」中绑定本任务的池取并集后扫描\n"
                    + "maturityOffsetDays（到期日期偏移天数）：可选，省略默认 1；须为 int 范围内的整数，允许 0 和负数\n"
                    + "天数含义：按数据库当前日期减去该天数比较摘牌日（不含截止当天）。1=早于昨天（T-2），0=早于今天，3=早于三天前，-1=早于明天\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "候选查询：扫描 ip_pool_status 中已生效且未删除的债券（含 ABS、CRMW）。证券状态为 D 或摘牌日早于截止日时进入候选\n"
                    + "CRMW 组合池：ip_pool_status_crmw 中的凭证与标的组合由“CRMW到期自动出池”任务处理\n"
                    + "最终出池：先按在池记录 ID 剔除 Excel 保护项，再跳过缺少证券编码或命中调出限制池的记录\n"
                    + "执行方式：直接生效，不走审批；仅软删除成功才写日志并计入影响条数\n"
                    + "参数格式错误时，本轮任务失败";

    /** 自动调库查询 Mapper */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;

    /** 证券池调库落地 Mapper */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 读取到期扫描需要排除的 Excel 保护记录 */
    @Resource
    private ExcelPoolExclusionService excelPoolExclusionService;

    /** 投资池查询 Mapper */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 定时任务配置 Mapper（param_json / 名称） */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    /** 扫描池并集（参数 ∪ 关系配置） */
    @Resource
    private AutoAdjustPoolScopeHelper poolScopeHelper;

    /**
     * 提供本定时任务的唯一编码
     *
     * 执行顺序：
     * 1. 返回当前任务定义的固定编码
     *
     * @return 任务编码
     */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /**
     * 向配置页提供本任务扩展参数填写说明（仅 JSON）
     *
     * 执行顺序：
     * 1. 返回当前任务定义的参数说明
     *
     * @return 任务参数说明
     */
    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 兼容旧调用入口，内部调用当前定时任务执行方法
     *
     * 执行顺序：
     * 1. 转交 execute 执行当前任务
     */
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
            // 写 WARN 级过程日志并同步控制台
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
     * 1. 读取到期偏移天数，打印扫描条件，再合并扫描池
     * 2. 读取投资池信息
     * 3. 准备本轮批次和提交时间
     * 4. 读取池关系配置
     * 5. 逐池查询到期候选，同时标记调出限制命中结果
     * 6. 跳过缺少证券代码或命中限制的候选
     * 7. 软删可调出的记录，实际删到才写出池日志
     * 8. 统计各池实际出池和未处理数量
     *
     * @param taskName 任务展示名称
     * @param detail 过程日志
     * @return 本轮到期证券出池汇总
     */
    private AutoOutSummary doAutoOutExpired(String taskName, TaskDetailLog detail) {
        // 1. 读取本轮扩展参数，解析日期偏移并合并待扫描池
        String paramJson = resolveParamJson(detail);
        int maturityOffsetDays = AutoAdjustPoolScopeHelper.parseMaturityOffsetDays(paramJson);
        String maturityRule = "摘牌日早于当前日期减去 " + maturityOffsetDays + " 天";
        // 查池配置和到期候选之前，先说明从哪张表查和哪些记录能出池
        logExecutionRules(detail, maturityRule, maturityOffsetDays);
        List<Long> poolIds = poolScopeHelper.resolveUnionPoolIds(
                paramJson, TASK_CODE, RuleType.AUTO_OUT.getCode(), detail);
        // 写 INFO 级过程日志并同步控制台
        infoDetail(detail, "扫描范围：目标池编号 " + poolIds);
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
        Set<Long> protectedStatusIds = poolIds.isEmpty() ? Collections.emptySet()
                : excelPoolExclusionService.loadProtectedStatusIds(poolIds, true);
        AutoOutSummary summary = new AutoOutSummary();
        // 5. 逐池扫描到期证券，同时标记调出限制命中结果
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                summary.addSkipped(1);
                // 记录一条业务上未处理的原因，不把可预期情况写成任务异常
                skipDetail(detail, "目标池【" + poolId + "】不存在，本轮未处理");
                continue;
            }
            // 传入本目标池配置的调出限制池，随候选一起检查
            List<Long> outRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.OUT_RESTRICT.getCode(), allRelations);
            List<ScheduledAdjustCandidateDto> expiredList = autoAdjustMapper.queryPoolSecurityByExpired(
                    poolId, maturityOffsetDays, outRestrictPoolIds);
            // 统计 Excel 保护跳过数，区分没有查到候选和候选全部受保护
            int matchedCount = expiredList == null ? 0 : expiredList.size();
            expiredList = ExcelPoolExclusionService.excludeProtectedRows(expiredList, protectedStatusIds);
            int candidateCount = expiredList == null ? 0 : expiredList.size();
            // 写 INFO 级过程日志并同步控制台
            infoDetail(detail, "扫描结果：池【" + pool.getPoolName() + "】到期命中 "
                    + matchedCount + " 条，Excel 保护跳过 " + (matchedCount - candidateCount) + " 条");
            if (expiredList == null || expiredList.isEmpty()) {
                summary.addPool(poolId, pool.getPoolName(), 0, 0, 0);
                continue;
            }
            int poolCount = 0;
            int poolSkippedCount = 0;
            // 6. 跳过缺少证券代码或命中调出限制的候选
            for (ScheduledAdjustCandidateDto sec : expiredList) {
                if (sec == null || !StringUtils.hasText(sec.getSecurityCode())) {
                    poolSkippedCount++;
                    // 记录一条业务上未处理的原因，不把可预期情况写成任务异常
                    skipDetail(detail, "扫描结果缺少证券编码，无法处理");
                    continue;
                }
                // 使用候选查询返回的调出限制结果
                if (sec.isOutRestricted()) {
                    poolSkippedCount++;
                    // 记录一条业务上未处理的原因，不把可预期情况写成任务异常
                    skipDetail(detail, "证券【" + sec.getSecurityCode() + "】：当前位于调出限制池，本轮不处理");
                    continue;
                }
                // 7. 软删仍在目标池的记录，实际删到才写出池日志
                int deleted = securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft(sec.getSecurityCode(), poolId);
                if (deleted == 0) {
                    poolSkippedCount++;
                    // 记录一条业务上未处理的原因，不把可预期情况写成任务异常
                    skipDetail(detail, "证券【" + sec.getSecurityCode() + "】：当前已不在目标池，本轮不处理");
                    continue;
                }
                // 记录实际命中的到期依据
                sec.setAdjustType("自动调整");
                sec.setAdjustMode(AdjustMode.OUT.getCode());
                sec.setTargetPoolId(poolId);
                sec.setTargetPoolName(pool.getPoolName());
                sec.setPoolType(pool.getPoolType());
                sec.setAuditStatus(AuditStatus.APPROVED.getCode());
                sec.setAdjusterId(AUTO_ADJUSTER_ID);
                sec.setAdjusterName(AUTO_ADJUSTER_NAME);
                String reason = ScheduledAdjustLogHelper.appendDetails(ScheduledAdjustLogHelper.taskLabel(taskName),
                        ScheduledAdjustLogHelper.dateDetail("摘牌日", sec.getDelistDate()),
                        "出池口径：" + (SecurityStatus.DELISTED.getCode().equals(sec.getSecurityStatus())
                                ? "证券状态为 D（退市）" : maturityRule));
                sec.setAdjustReason(reason);
                // 以本次出池证券和目标池生成批次号
                String batchNo = batchNoContext.resolveBondBatchNo(
                        sec.getSecurityCode(), poolId, AdjustMode.OUT.getCode());
                sec.setAdjustBatchNo(batchNo);
                sec.setSubmitTime(submitTime);
                securityPoolAdjustMapper.addAdjustLog(sec);
                poolCount++;
            }
            // 8. 分别汇总本池候选、实际出池和未处理数量
            summary.addPool(poolId, pool.getPoolName(), candidateCount, poolCount, poolSkippedCount);
            // 写 INFO 级过程日志并同步控制台
            infoDetail(detail, "处理结果：池【" + pool.getPoolName() + "】实际出池 "
                    + poolCount + " 条，未处理 " + poolSkippedCount + " 条");
        }
        return summary;
    }

    /**
     * 在扫描前打印本任务的查询和出池规则
     *
     * 执行顺序：
     * 1. 说明扩展参数和投资池配置都会影响本轮扫描范围
     * 2. 写明证券主档及两种到期条件
     * 3. 区分查到的到期债券和实际能出池的记录
     *
     * @param detail 过程日志
     * @param maturityRule 本轮摘牌日判断说明
     * @param maturityOffsetDays 本轮到期偏移天数
     */
    private void logExecutionRules(TaskDetailLog detail, String maturityRule, int maturityOffsetDays) {
        // 1. 说明扩展参数和投资池配置都会影响本轮扫描范围
        infoDetail(detail, "扫描范围：合并扩展参数 poolIds 和投资池绑定");
        // 2. 写明证券主档及两种到期条件
        infoDetail(detail, "扫描条件：扫描 ip_pool_status 中已生效且未删除的债券（含 ABS、CRMW），"
                + "按证券代码匹配 rrs_securityinfo，证券状态为 D 或" + maturityRule
                + "（数据库日期，maturityOffsetDays=" + maturityOffsetDays + "）");
        // 3. 区分查到的到期债券和实际能出池的记录
        infoDetail(detail, "最终出池范围：先按在池记录 ID 剔除 Excel 保护项，"
                + "再跳过缺少证券编码或命中调出限制池的记录，软删除成功才写调库记录并计入实际出池");
    }

    /**
     * 汇总本轮到期证券自动出池结果
     *
     * 合计各目标池扫描候选、实际出池和未处理的记录数，供任务返回结果
     */
    private static final class AutoOutSummary {

        /** 本轮实际出池总数 */
        private int total;

        /** 本轮扫描到的候选记录数 */
        private int candidateCount;

        /** 本轮未处理的记录数 */
        private int skippedCount;

        /** 按目标池保存实际出池数量 */
        private final Map<Long, PoolSummary> poolSummaries = new LinkedHashMap<>();

        /**
         * 累加未处理记录数
         *
         * 执行顺序：
         * 1. 只有新增未处理数大于零时才计入汇总
         *
         * @param count 本次累计的处理条数
         */
        private void addSkipped(int count) {
            if (count > 0) {
                skippedCount += count;
            }
        }

        /**
         * 记录一个目标池的出池统计
         *
         * 执行顺序：
         * 1. 按池 ID 保存池名称、候选数、出池数和未处理数
         * 2. 将该池各项数量加到任务总计
         *
         * @param poolId 本次查询或统计的投资池 ID
         * @param poolName 目标池名称
         * @param candidates 本池候选记录数
         * @param count 本次累计的处理条数
         * @param skipped 本池未处理记录数
         */
        private void addPool(Long poolId, String poolName, int candidates, int count, int skipped) {
            candidateCount += candidates;
            skippedCount += skipped;
            poolSummaries.put(poolId, new PoolSummary(poolId, poolName, candidates, count, skipped));
            total += count;
        }

        /**
         * 生成到期出池任务的结果说明
         *
         * 执行顺序：
         * 1. 先写本轮扫描、实际出池和未处理总数
         * 2. 有分池结果时按汇总顺序追加明细
         *
         * @return 包含本轮总数和分池明细的执行结果
         */
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

    /**
     * 记录单个目标池的到期证券出池结果
     *
     * 按池保留扫描候选数、实际出池数和未处理数，供整轮任务汇总
     */
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

        /**
         * 记录目标池的到期证券出池统计
         *
         * 执行顺序：
         * 1. 保存目标池信息以及扫描候选、实际出池和未处理数量
         *
         * @param poolId 目标池 ID
         * @param poolName 目标池名称
         * @param candidateCount 扫描候选数量
         * @param count 实际出池数量
         * @param skippedCount 未处理数量
         */
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
        // 写 INFO 级过程日志并同步控制台
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        return paramJson;
    }

    /**
     * 解析扩展参数中的扫描池
     *
     * 非法 JSON 或空池列表直接返回业务错误，正式任务入口使用参数池和关系配置的并集
     *
     * 执行顺序：
     * 1. 委托共用解析器读取池 ID，未指定任何扫描池时提示需要配置 poolIds
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
     *
     * 执行顺序：
     * 1. 将消息写入应用日志的 INFO 级别
     * 2. 提供任务明细记录器时，将同一条消息写入明细
     *
     * @param detail 过程日志
     * @param line 未处理原因
     */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line(line);
        }
    }

    /**
     * 写 WARN 级过程日志并同步控制台
     *
     * 执行顺序：
     * 1. 将消息写入应用日志的 WARN 级别
     * 2. 提供任务明细记录器时，将同一条消息写入明细
     *
     * @param detail 过程日志
     * @param line 未处理原因
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
     * 执行顺序：
     * 1. 将未处理原因写入 INFO 应用日志
     * 2. 提供任务明细记录器时，为同一条原因加上「未处理」后写入明细
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
     *
     * 执行顺序：
     * 1. 带入此任务编码，读取扩展参数指定的扫描池 ID
     *
     * @param raw 扩展参数原文
     * @return 池 ID 列表
     */
    List<Long> parsePoolIds(String raw) {
        return parsePoolIds(raw, TASK_CODE);
    }

    /**
     * 从库表读取任务展示名称，缺失时使用默认任务名称
     *
     * 执行顺序：
     * 1. 按任务编码查询任务配置
     *
     * @return 任务名称
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
        return DEFAULT_TASK_NAME;
    }
}
