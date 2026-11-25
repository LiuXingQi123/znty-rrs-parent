package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
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
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 外评非AA-及以下主体自动出池（仅主体）
 *
 * 执行顺序：
 * 1. 读取认可外评机构、扫描池和额外拦截池，查询已在池但符合外评出池条件的主体
 * 2. 按黑名单质押库留池条件复核，跳过受 Excel 人工决定保护、命中调出限制或资料不完整的记录
 * 3. 写入通过校验的主体出池日志及状态，汇总本轮出池结果
 */
@Slf4j
@Service
public class CompanyOuterRatingNotAaMinusAutoOutService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "company_outer_rating_not_aa_minus_auto_out";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";

    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";

    /** 任务默认展示名称 */
    private static final String DEFAULT_TASK_NAME = "外评非AA-及以下主体自动出池（仅主体）";

    /** JSON 解析 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 本任务扩展参数说明（配置页按行拆成列表展示）
     */
    private static final String PARAM_HELP =
            "参数格式：须填写 JSON 对象，例如 <code>{\"poolIds\":[17],\"limitPoolIds\":[]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（目标池 + 空拦截）：<code>{\"poolIds\":[17],\"limitPoolIds\":[]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：已在 17（黑名单质押库），近一年存在认可外评，且不在禁止库 15、不在重点观察 23、孰低外评不属于 AA-及以下的主体，自动调出 17\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（仅目标池）：<code>{\"poolIds\":[17]}</code>；省略 limitPoolIds 与填写 <code>[]</code> 相同，不再默认拦禁投池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：与 Demo 相同，从 17（黑名单质押库）按（一）（二）（三）均不满足的规则出库\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（目标池 + 额外拦截）：<code>{\"poolIds\":[17],\"limitPoolIds\":[16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：在 17 且三条都不满足的主体出库；同时在 16（观察池）的主体额外排除\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体出池目标池）：可选；与投资池「关系配置 → 自动调出规则」中绑定本任务的池取并集后扫描\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "limitPoolIds（禁止出池拦截池）：可选；Demo 为 <code>[]</code>。主体当前已在这些池中的任一池时，不从扫描目标池自动出库\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "limitPoolIds 省略或 <code>[]</code>：不追加额外拦截（条款（一）（三）已在扫描中排除 15/23）\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：已在目标池，近一年存在认可外评，且不在 15、不在 23、孰低外评不属于 AA-及以下时，自动调出主体\n"
                    + "评级临界：AA-及以下不出池；AA及以上在其他条件均不命中时可出池；空评级/近一年无认可外评不出池\n"
                    + "评级口径：近一年（日历年）内配置表中的有效机构多评级取孰低；无认可外评不自动出池\n"
                    + "旗下债券由独立任务同步\n"
                    + "限制规则：主体命中调出限制池时跳过该主体\n"
                    + "执行方式：直接生效，不走审批；参数格式错误时，本轮任务失败";

    /** 自动调库查询 Mapper */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;

    /** 证券池调库落地 Mapper */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 读取主体自动出池时应保留的 Excel 入池记录 */
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

    /** 黑名单质押库三条件统一判定 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;

    /** 外部评级机构配置服务 */
    @Resource
    private ExternalRatingAgencyService externalRatingAgencyService;

    /**
     * 返回与库表绑定的任务编码
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
     * 执行外评非AA-及以下主体自动出池
     *
     * 执行顺序：
     * 1. 读取任务配置并准备过程日志
     * 2. 扫描符合外评规则的目标池主体
     * 3. 对不满足单条业务条件的主体记录原因后跳过
     * 4. 写入主体出池记录
     * 5. 返回任务成功或任务级失败结果
     *
     * @return 本轮任务执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        // 1. 读取任务展示名称
        String taskName = resolveTaskName();
        // 2. 记录任务开始
        infoDetail(detail, "任务开始：" + taskName);
        try {
            // 3. 执行主体自动出池
            AutoOutSummary summary = doAutoOut(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = summary.buildMessage();
            // 4. 记录任务结果并返回成功结果
            infoDetail(detail, "任务结束：" + message + "；耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, taskName, message,
                    summary.companyCount, startTime, duration,
                    detail.build());
        } catch (BizException e) {
            // 业务错误时标记本轮事务回滚并返回失败结果
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            // 写 WARN 级过程日志并同步控制台
            warnDetail(detail, "任务未完成：" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName, e.getMessage(), startTime, duration,
                    detail.build());
        } catch (Exception e) {
            // 系统错误时标记本轮事务回滚并返回失败结果
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", taskName, e);
            detail.line("任务未完成：系统异常，" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName,
                    "执行异常: " + e.getMessage(), startTime, duration, detail.build());
        }
    }

    /**
     * 按目标池将不再满足黑名单三项条件的主体自动出池
     *
     * 执行顺序：
     * 1. 读取认可外评机构和目标池配置
     * 2. 查询并复核主体出池候选
     * 3. 逐个检查主体限制条件并处理主体出池
     * 4. 主体出池成功后处理同池旗下债券
     * 5. 汇总目标池处理结果
     *
     * @param taskName 任务展示名称
     * @param detail 过程日志
     * @return 本轮主体出池汇总
     */
    private AutoOutSummary doAutoOut(String taskName, TaskDetailLog detail) {
        // 1. 读取本轮统一使用的外部评级机构
        List<String> agencyCodes = externalRatingAgencyService.queryRequiredAgencyCodeList();
        // 2. 读取扩展参数
        String paramJson = resolveParamJson();
        // 写 INFO 级过程日志并同步控制台
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        // 3. 合并参数池和关系配置绑定池
        List<Long> poolIds = poolScopeHelper.resolveUnionPoolIds(
                paramJson, TASK_CODE, RuleType.AUTO_OUT.getCode(), detail);
        Set<Long> protectedStatusIds = poolIds.isEmpty() ? Collections.emptySet()
                : excelPoolExclusionService.loadProtectedStatusIds(poolIds, false);
        // 写 INFO 级过程日志并同步控制台
        infoDetail(detail, "扫描范围：目标池编号 " + poolIds);
        // 4. 构建池信息映射并解析额外拦截池
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        // 解析额外拦截池（省略或 [] 都不拦截）
        List<Long> limitPoolIds = resolveLimitPoolIds(paramJson);
        // 写 INFO 级过程日志并同步控制台
        infoDetail(detail, "额外拦截池：" + limitPoolIds);
        // 写 INFO 级过程日志并同步控制台
        infoDetail(detail, "扫描条件：主体已在目标池，近一年有认可外评，且不满足禁止库、低评级和重点观察三项条件");
        ScheduledAdjustBatchNoContext batchNoContext = new ScheduledAdjustBatchNoContext();
        Date submitTime = batchNoContext.getSubmitTime();
        // 5. 读取全量池关系，供调出限制池校验
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        AutoOutSummary summary = new AutoOutSummary();
        // 6. 逐个目标池查询和处理主体
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                summary.addSkipped(1);
                // 记录业务上未处理的主体或债券，不把可预期情况写成任务异常
                skipDetail(detail, "目标池【" + poolId + "】不存在，本轮未处理");
                continue;
            }
            // 读取当前目标池的调出限制池
            List<Long> outRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.OUT_RESTRICT.getCode(), allRelations);
            // 查询满足外评条件的主体候选
            List<ScheduledAdjustCandidateDto> companies = autoAdjustMapper.queryCompanyByNotLowOuterRatingInPool(
                    poolId, limitPoolIds, agencyCodes);
            // 排除直接或同批经 Excel 入池的主体状态
            companies = ExcelPoolExclusionService.excludeProtectedRows(companies, protectedStatusIds);
            // 使用统一规则复核主体三项条件
            companies = filterCompaniesNotMatchingBlacklistConditions(companies);
            if (companies == null || companies.isEmpty()) {
                summary.addPool(poolId, pool.getPoolName(), 0, 0, 0);
                // 写 INFO 级过程日志并同步控制台
                infoDetail(detail, "扫描结果：池【" + pool.getPoolName() + "】没有待出池主体");
                continue;
            }
            int poolCount = 0;
            int poolSkippedCount = 0;
            for (ScheduledAdjustCandidateDto company : companies) {
                if (company == null || !StringUtils.hasText(company.getSecurityCode())) {
                    poolSkippedCount++;
                    // 记录业务上未处理的主体或债券，不把可预期情况写成任务异常
                    skipDetail(detail, "扫描结果缺少主体编码，无法处理");
                    continue;
                }
                // 对齐老 AdjustPoolByRule.checkSecurityOutPoolRelation（关系 12 / 调出限制池），命中时跳过
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        securityPoolAdjustMapper.querySecurityCurrentPoolIdList(company.getSecurityCode()),
                        outRestrictPoolIds)) {
                    poolSkippedCount++;
                    // 记录业务上未处理的主体或债券，不把可预期情况写成任务异常
                    skipDetail(detail, "主体【" + company.getSecurityCode() + "】：当前位于调出限制池，本轮不处理");
                    continue;
                }
                // 准备主体出池原因和批次信息
                String reason = buildAdjustReason(company.getOuterRating(), taskName);
                String batchNo = batchNoContext.resolveCompanyBatchNo(
                        company.getSecurityCode(), poolId, AdjustMode.OUT.getCode());
                // 回填主体自动出池日志字段
                fillAutoOutLog(company, pool, poolId, batchNo, submitTime, reason);
                // 写主体出池日志并更新池状态
                if (securityPoolAdjustMapper.addAdjustLog(company) != 1) {
                    throw new BizException("主体[" + company.getSecurityCode() + "]自动调出日志写入失败");
                }
                int deleted = securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft(company.getSecurityCode(), poolId);
                if (deleted != 1) {
                    throw new BizException("主体[" + company.getSecurityCode()
                            + "]软删池状态失败（可能并发已出池）");
                }
                poolCount++;
            }
            summary.addPool(poolId, pool.getPoolName(), companies.size(), poolCount, poolSkippedCount);
            // 写 INFO 级过程日志并同步控制台
            infoDetail(detail, "处理结果：池【" + pool.getPoolName() + "】实际出池 "
                    + poolCount + " 个主体，未处理 " + poolSkippedCount + " 个主体");
        }
        return summary;
    }

    /**
     * 汇总本轮外评变动触发的主体自动调出结果
     *
     * 按扫描顺序保留各目标池统计，并累计候选、实际调出和未处理主体数量
     */
    private static final class AutoOutSummary {

        /** 本轮自动出池主体总数 */
        private int companyCount;

        /** 本轮扫描到的主体候选数 */
        private int candidateCount;

        /** 本轮未处理的主体或配置项数量 */
        private int skippedCount;

        /** 按目标池保存出池统计，保持任务扫描顺序 */
        private final Map<Long, PoolAutoOutSummary> poolSummaries = new LinkedHashMap<>();

        /**
         * 累加未处理配置项数量
         *
         * 执行顺序：
         * 1. 把新增未处理数量计入本轮出池统计
         *
         * @param count 本次累计的处理条数
         */
        private void addSkipped(int count) {
            skippedCount += count;
        }

        /**
         * 记录一个目标池的主体自动出池结果
         *
         * 执行顺序：
         * 1. 按池 ID 初始化或取已有统计，累计本池候选、出池和未处理数
         * 2. 同步累计任务总数量
         *
         * @param poolId 本次查询或统计的投资池 ID
         * @param poolName 目标池名称
         * @param candidateCount 本次候选记录数
         * @param companyCount 本目标池实际进出池的主体数
         * @param skippedCount 本次未处理记录数
         */
        private void addPool(Long poolId, String poolName, int candidateCount,
                             int companyCount, int skippedCount) {
            PoolAutoOutSummary poolSummary = poolSummaries.get(poolId);
            if (poolSummary == null) {
                poolSummary = new PoolAutoOutSummary(poolId, poolName);
                poolSummaries.put(poolId, poolSummary);
            }
            poolSummary.companyCount += companyCount;
            poolSummary.candidateCount += candidateCount;
            poolSummary.skippedCount += skippedCount;
            this.candidateCount += candidateCount;
            this.companyCount += companyCount;
            this.skippedCount += skippedCount;
        }

        /**
         * 生成主体自动出池的结果说明
         *
         * 执行顺序：
         * 1. 先输出所有目标池的候选主体、实际出池及未处理数量
         * 2. 已有分池统计时逐池追加明细
         *
         * @return 主体出池任务的总计及分池明细
         */
        private String buildMessage() {
            StringBuilder message = new StringBuilder("任务完成：扫描候选主体 ")
                    .append(candidateCount).append(" 个，实际出池 ")
                    .append(companyCount).append(" 个主体，未处理 ")
                    .append(skippedCount).append(" 个主体");
            if (!poolSummaries.isEmpty()) {
                message.append("；目标池明细：");
                int index = 0;
                for (PoolAutoOutSummary poolSummary : poolSummaries.values()) {
                    if (index++ > 0) {
                        message.append("；");
                    }
                    message.append(poolSummary.poolName).append("（")
                            .append(poolSummary.poolId).append("）：扫描 ")
                            .append(poolSummary.candidateCount).append(" 个主体，出池 ")
                            .append(poolSummary.companyCount).append(" 个主体，未处理 ")
                            .append(poolSummary.skippedCount).append(" 个主体");
                }
            }
            return message.toString();
        }
    }

    /**
     * 记录某个目标池的外评触发调出情况
     *
     * 按目标池保存扫描主体数、实际调出数和未处理数
     */
    private static final class PoolAutoOutSummary {

        /** 目标池 ID */
        private final Long poolId;

        /** 目标池名称 */
        private final String poolName;

        /** 目标池主体出池数 */
        private int companyCount;

        /** 目标池扫描主体数 */
        private int candidateCount;

        /** 目标池未处理主体数 */
        private int skippedCount;

        /**
         * 初始化目标池的外评触发自动调出统计
         *
         * 执行顺序：
         * 1. 保存用于归集调出统计的池 ID 和名称
         *
         * @param poolId 目标池 ID
         * @param poolName 目标池名称
         */
        private PoolAutoOutSummary(Long poolId, String poolName) {
            this.poolId = poolId;
            this.poolName = poolName;
        }
    }

    /**
     * 使用统一判定再次筛出三个条件均不满足的主体
     *
     * 执行顺序：
     * 1. 候选为空时原样返回
     * 2. 逐个取有效主体代码并复核黑名单三项条件，保留不满足条件的主体
     *
     * @param companies 条件（二）反面的出池候选
     * @return 三个条件全部不满足的主体
     */
    private List<ScheduledAdjustCandidateDto> filterCompaniesNotMatchingBlacklistConditions(
            List<ScheduledAdjustCandidateDto> companies) {
        if (companies == null || companies.isEmpty()) {
            return companies;
        }
        List<ScheduledAdjustCandidateDto> result = new ArrayList<>();
        for (ScheduledAdjustCandidateDto company : companies) {
            if (company == null || !StringUtils.hasText(company.getSecurityCode())
                    || pledgeBlacklistRuleService.evaluate(company.getSecurityCode()).shouldBeInBlacklist()) {
                continue;
            }
            result.add(company);
        }
        return result;
    }

    /**
     * 调整原因带上近一年孰低外评
     *
     * 执行顺序：
     * 1. 取定时任务的说明作为基础原因，有外评时去掉评级首尾空白并追加在原因后
     *
     * @param outerRating 近一年孰低外评
     * @param taskName 任务名称
     * @return 调整原因
     */
    private static String buildAdjustReason(String outerRating, String taskName) {
        String label = ScheduledAdjustLogHelper.taskLabel(taskName);
        return StringUtils.hasText(outerRating) ? label + "（近一年孰低外评：" + outerRating.trim() + "）" : label;
    }

    /**
     * 回填自动出池日志公共字段
     *
     * 执行顺序：
     * 1. 依次设置调整类型、调整方向、目标投资池 ID、目标投资池名称等字段
     *
     * @param log 调库日志
     * @param pool 目标池
     * @param poolId 目标池 ID
     * @param batchNo 批次号
     * @param submitTime 提交时间
     * @param reason 调整原因
     */
    private void fillAutoOutLog(IpAdjustLogBo log, InvestmentPoolBo pool, Long poolId,
                                String batchNo, Date submitTime, String reason) {
        log.setAdjustType("自动调整");
        log.setAdjustMode(AdjustMode.OUT.getCode());
        log.setTargetPoolId(poolId);
        log.setTargetPoolName(pool.getPoolName());
        log.setPoolType(pool.getPoolType());
        log.setAuditStatus(AuditStatus.APPROVED.getCode());
        log.setAdjusterId(AUTO_ADJUSTER_ID);
        log.setAdjusterName(AUTO_ADJUSTER_NAME);
        log.setAdjustReason(reason);
        log.setAdjustBatchNo(batchNo);
        log.setSubmitTime(submitTime);
    }

    /**
     * 从库表读取本任务扩展参数原文
     *
     * 执行顺序：
     * 1. 按任务编码查询任务配置
     *
     * @return param_json；未配置时返回 null
     */
    private String resolveParamJson() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf != null && StringUtils.hasText(conf.getParamJson())) {
            return conf.getParamJson().trim();
        }
        return null;
    }

    /**
     * 解析额外拦截池：省略或 [] 都不拦截
     *
     * 执行顺序：
     * 1. 解析 JSON 中的数字 ID 数组
     *
     * @param paramJson 扩展参数原文
     * @return 拦截池 ID；空表示不追加拦截
     */
    List<Long> resolveLimitPoolIds(String paramJson) {
        // 1. 从 JSON 读取 limitPoolIds，缺字段视为空
        return parseOptionalIdArray(paramJson, "limitPoolIds");
    }

    /**
     * 解析 JSON 中的数字 ID 数组；缺字段或空数组返回空列表
     *
     * 执行顺序：
     * 1. 解析 JSON 中的数字 ID 数组
     *
     * @param raw 扩展参数原文
     * @param field 数组字段名
     * @return ID 列表
     */
    List<Long> parseOptionalIdArray(String raw, String field) {
        List<Long> result = new ArrayList<>();
        if (!StringUtils.hasText(raw) || !raw.trim().startsWith("{")) {
            return result;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw.trim());
            JsonNode arr = root.get(field);
            if (arr == null || !arr.isArray()) {
                return result;
            }
            for (JsonNode item : arr) {
                if (item == null || item.isNull() || !item.isNumber()) {
                    throw new BizException(field + " 元素须为数字，非法值: " + item);
                }
                result.add(item.asLong());
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析扩展参数 JSON 为池 ID 列表，格式：{"poolIds":[17]}
     *
     * 执行顺序：
     * 1. 解析 JSON 中的 poolIds 数组
     *
     * @param raw 扩展参数原文
     * @param taskName 任务展示名称
     * @return 池 ID 列表
     */
    List<Long> parsePoolIds(String raw, String taskName) {
        // 1. 解析 JSON 中的 poolIds 数组
        List<Long> ids = AutoAdjustPoolScopeHelper.parseOptionalPoolIds(raw);
        if (ids.isEmpty()) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[17]}");
        }
        return ids;
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
     * 构建池 ID 到投资池对象的映射
     *
     * 执行顺序：
     * 1. 查询投资池列表
     * 2. 构建池 ID 到投资池对象的映射
     *
     * @return 池映射
     */
    private Map<Long, InvestmentPoolBo> buildPoolMap() {
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> poolList = investmentPoolMapper.queryPoolList();
        if (poolList != null) {
            for (InvestmentPoolBo pool : poolList) {
                poolMap.put(pool.getId(), pool);
            }
        }
        return poolMap;
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
                return ScheduledAdjustLogHelper.withTaskScope(TASK_CODE, conf.getTaskName());
            }
        } catch (Exception e) {
            log.debug("读取任务名称失败: {}", TASK_CODE);
        }
        return DEFAULT_TASK_NAME;
    }

    /**
     * 写 INFO 级过程日志并同步控制台
     *
     * 执行顺序：
     * 1. 将消息写入应用日志的 INFO 级别
     * 2. 提供任务明细记录器时，将同一条消息写入明细
     *
     * @param detail 过程日志
     * @param line 日志内容
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
     * @param line 日志内容
     */
    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line(line);
        }
    }

    /**
     * 记录业务上未处理的主体或债券，不把可预期情况写成任务异常
     *
     * 执行顺序：
     * 1. 向应用日志记录未处理原因，有任务明细记录器时再加上「未处理」写入明细
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
     * 当前任务发生异常时标记事务回滚
     *
     * 执行顺序：
     * 1. 将当前事务标记为只回滚，没有事务时仅记调试日志
     */
    private void markTransactionRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            log.debug("当前无可回滚事务: {}", TASK_CODE);
        }
    }
}
