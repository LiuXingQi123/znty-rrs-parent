package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外评AA-及以下主体自动入池（已排除ABS债）
 *
 * 执行顺序：
 * 1. 读取认可外评机构和主体入池目标池
 * 2. 合并禁止库、低评级和重点观察名单三类入池候选
 * 3. 跳过已不满足条件、命中调入限制池或主体数据不完整的记录
 * 4. 写入主体和旗下普通债、CRMW 的入池记录
 * 5. 返回主体和旗下债券的处理结果
 */
@Slf4j
@Service
public class CompanyOuterRatingAaMinusAutoInService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "company_outer_rating_aa_minus_auto_in";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 自动入池原因 */
    private static final String REASON = "外评AA-及以下主体自动入池";
    /**
     * 本任务扩展参数说明（配置页按行拆成列表展示）
     */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[17]}</code>；也可不填 poolIds，仅扫描投资池关系配置中绑定了本任务的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[17]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：符合质押券黑名单（一）（二）（三）之一、尚未在 17（黑名单质押库）的主体，自动调入 17（黑名单质押库）\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多池）：<code>{\"poolIds\":[17,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：符合（一）（二）（三）之一的主体，分别补充调入 17（黑名单质押库）、16（观察池）中尚未在池的目标池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体入池目标池）：可选；与投资池「关系配置 → 自动调入规则」中绑定本任务的池取并集后扫描\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：满足下列任一且尚未在目标池则自动入池：（一）当前在公司信用债禁止库 15；（二）近一年认可外评孰低为 AA-及以下；（三）当前在重点观察名单 23\n"
                    + "评级临界：AA-及以下因评级入池；AA及以上不因评级入池；空评级/近一年无认可外评不因评级入池，但命中禁止库 15 或重点观察 23 时仍按相应条件入池\n"
                    + "评级口径：近一年（日历年）内配置表中的有效机构多评级取孰低，一年以前忽略；近一年无认可外评不因（二）入库\n"
                    + "限制规则：主体已在目标池配置的调入限制池时，跳过该条记录\n"
                    + "联动处理：主体成功入池后，本任务内自写逻辑同批调入该主体旗下未退市、未到期债券（不复用人工 syncCompanyBonds）；"
                    + "旗下债已排除 ABS，ABS 的进出池与未确认提醒由「主体下ABS债券自动入库」任务负责；"
                    + "债命中调入限制池则阻断主体入池并整批回滚；入池后按互斥/反向限制从债当前所在池自动调出\n"
                    + "执行方式：直接生效，不走审批；参数格式错误时，本轮任务失败";

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
    /** 黑名单质押库三条件统一判定服务 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;
    /** 外部评级机构配置服务 */
    @Resource
    private ExternalRatingAgencyService externalRatingAgencyService;

    /**
     * 返回与库表绑定的任务编码
     */
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

    /**
     * 执行外评AA-及以下主体自动入池（已排除ABS债）
     *
     * 执行顺序：
     * 1. 读取任务配置并准备过程日志
     * 2. 查询并合并三类主体入池候选
     * 3. 对不满足单条业务条件的主体记录原因后跳过
     * 4. 写入主体和旗下债券入池记录
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
            // 3. 执行主体自动入池
            AutoInSummary summary = doAutoIn(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = summary.buildMessage();
            // 4. 记录任务结果并返回成功结果
            infoDetail(detail, "任务结束：" + message + "；耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, summary.companyCount, startTime, duration,
                    detail.build());
        } catch (BizException e) {
            // 4.1 业务错误时标记本轮事务回滚并返回失败结果
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            warnDetail(detail, "任务未完成：" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName, e.getMessage(), startTime, duration,
                    detail.build());
        } catch (Exception e) {
            // 4.2 系统错误时标记本轮事务回滚并返回失败结果
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", taskName, e);
            detail.line("任务未完成：系统异常，" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName,
                    "执行异常: " + e.getMessage(), startTime, duration, detail.build());
        }
    }

    /**
     * 按目标池将满足黑名单三项条件之一且尚未入池的主体自动入池
     *
     * 执行顺序：
     * 1. 读取认可外评机构和目标池配置
     * 2. 按三项条件查询并合并主体候选
     * 3. 逐个检查主体当前状态和调入限制
     * 4. 写入主体入池记录并处理旗下债券
     * 5. 汇总目标池处理结果
     *
     * @param taskName 任务展示名称
     * @param detail 过程日志
     * @return 本轮主体与旗下债券入池汇总
     */
    private AutoInSummary doAutoIn(String taskName, TaskDetailLog detail) {
        // 1. 读取本轮统一使用的外部评级机构
        List<String> agencyCodes = externalRatingAgencyService.queryRequiredAgencyCodeList();
        // 2. 合并扩展参数和关系配置中的入池目标池
        List<Long> poolIds = resolvePoolIds(taskName, detail);
        infoDetail(detail, "扫描范围：目标池编号 " + poolIds);
        infoDetail(detail, "处理条件：主体尚未在目标池生效，且满足禁止库、低评级或重点观察任一条件");
        // 3. 构建池信息映射
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        ScheduledAdjustBatchNoContext batchNoContext = new ScheduledAdjustBatchNoContext();
        Date submitTime = batchNoContext.getSubmitTime();
        // 4. 读取全量池关系，供调入限制池校验
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        AutoInSummary summary = new AutoInSummary();
        // 5. 逐个目标池查询和处理主体
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                summary.addSkipped(1);
                skipDetail(detail, "目标池【" + poolId + "】不存在，本轮未处理");
                continue;
            }
            // 5.1 读取当前目标池的调入限制池
            List<Long> inRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.IN_RESTRICT.getCode(), allRelations);
            // 5.2 查询并合并三类主体入池候选
            List<ScheduledAdjustCandidateDto> companies = queryInboundCandidates(poolId, agencyCodes);
            if (companies == null || companies.isEmpty()) {
                summary.addPool(poolId, pool.getPoolName(), 0, 0, 0, 0);
                infoDetail(detail, "扫描结果：池【" + pool.getPoolName() + "】没有待入池主体");
                continue;
            }
            int poolCount = 0;
            int poolBondCount = 0;
            int poolSkippedCount = 0;
            for (ScheduledAdjustCandidateDto company : companies) {
                if (company == null || !StringUtils.hasText(company.getSecurityCode())) {
                    poolSkippedCount++;
                    skipDetail(detail, "扫描结果缺少主体编码，无法处理");
                    continue;
                }
                if (PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(poolId)
                        && !pledgeBlacklistRuleService.evaluate(company.getSecurityCode()).shouldBeInBlacklist()) {
                    // 5.3 记录主体条件变化后的跳过原因
                    poolSkippedCount++;
                    skipDetail(detail, "主体【" + company.getSecurityCode() + "】：当前已不再满足黑名单入池条件，本轮不处理");
                    continue;
                }
                // 5.4 对齐老 AdjustPoolByRule.checkSecurityInPoolRelation（关系 11 / 调入限制池），命中时跳过
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        securityPoolAdjustMapper.querySecurityCurrentPoolIdList(company.getSecurityCode()),
                        inRestrictPoolIds)) {
                    poolSkippedCount++;
                    skipDetail(detail, "主体【" + company.getSecurityCode() + "】：当前位于调入限制池，本轮不处理");
                    continue;
                }
                company.setSecurityType("company");
                company.setAdjustType("自动调整");
                company.setAdjustMode(AdjustMode.IN.getCode());
                company.setTargetPoolId(poolId);
                company.setTargetPoolName(pool.getPoolName());
                company.setPoolType(pool.getPoolType());
                company.setAuditStatus(AuditStatus.APPROVED.getCode());
                company.setAdjusterId(AUTO_ADJUSTER_ID);
                company.setAdjusterName(AUTO_ADJUSTER_NAME);
                // 5.5 按命中的三项条件拼接调整原因
                String reason = buildAdjustReason(company);
                company.setAdjustReason(reason);
                // 5.6 生成主体入池批次号
                String batchNo = batchNoContext.resolveCompanyBatchNo(
                        company.getSecurityCode(), poolId, AdjustMode.IN.getCode());
                company.setAdjustBatchNo(batchNo);
                company.setSubmitTime(submitTime);
                // 5.7 写主体入池日志并更新池状态
                if (securityPoolAdjustMapper.addAdjustLog(company) != 1) {
                    throw new BizException("主体[" + company.getSecurityCode() + "]自动调入日志写入失败");
                }
                company.setAdjustLogId(company.getId());
                int inserted = securityPoolAdjustMapper.addPoolStatus(company);
                if (inserted != 1) {
                    throw new BizException("主体[" + company.getSecurityCode()
                            + "]写入池状态失败（可能并发已入池）");
                }
                // 5.8 主体入池成功后处理旗下债券
                poolBondCount += inSamePoolBonds(company, pool, allRelations, poolMap, detail);
                poolCount++;
            }
            summary.addPool(poolId, pool.getPoolName(), companies.size(), poolCount, poolBondCount, poolSkippedCount);
            infoDetail(detail, "处理结果：池【" + pool.getPoolName() + "】实际入池 "
                    + poolCount + " 个主体、" + poolBondCount + " 只债券，未处理 " + poolSkippedCount + " 个主体");
        }
        return summary;
    }

    /** 本轮主体自动入池及其旗下债券同步结果 */
    private static final class AutoInSummary {

        /** 本轮自动入池主体总数 */
        private int companyCount;
        /** 本轮同步入池债券总数 */
        private int bondCount;
        /** 本轮扫描到的主体候选数 */
        private int candidateCount;
        /** 本轮未处理的主体或配置项数量 */
        private int skippedCount;
        /** 按目标池保存入池统计，保持任务扫描顺序 */
        private final Map<Long, PoolAutoInSummary> poolSummaries = new LinkedHashMap<>();

        /** 累加未处理配置项数量 */
        private void addSkipped(int count) {
            skippedCount += count;
        }

        /** 累加一个目标池的主体和债券入池数量 */
        private void addPool(Long poolId, String poolName, int candidateCount,
                             int companyCount, int bondCount, int skippedCount) {
            PoolAutoInSummary poolSummary = poolSummaries.get(poolId);
            if (poolSummary == null) {
                poolSummary = new PoolAutoInSummary(poolId, poolName);
                poolSummaries.put(poolId, poolSummary);
            }
            poolSummary.companyCount += companyCount;
            poolSummary.bondCount += bondCount;
            poolSummary.candidateCount += candidateCount;
            poolSummary.skippedCount += skippedCount;
            this.candidateCount += candidateCount;
            this.companyCount += companyCount;
            this.bondCount += bondCount;
            this.skippedCount += skippedCount;
        }

        /** 构建最近结果展示的主体、债券及目标池明细 */
        private String buildMessage() {
            StringBuilder message = new StringBuilder("任务完成：扫描候选主体 ")
                    .append(candidateCount).append(" 个，实际入池 ")
                    .append(companyCount).append(" 个主体、")
                    .append(bondCount).append(" 只债券，未处理 ")
                    .append(skippedCount).append(" 个主体");
            if (!poolSummaries.isEmpty()) {
                message.append("；目标池明细：");
                int index = 0;
                for (PoolAutoInSummary poolSummary : poolSummaries.values()) {
                    if (index++ > 0) {
                        message.append("；");
                    }
                    message.append(poolSummary.poolName).append("（")
                            .append(poolSummary.poolId).append("）：扫描 ")
                            .append(poolSummary.candidateCount).append(" 个主体，入池 ")
                            .append(poolSummary.companyCount).append(" 个主体、")
                            .append(poolSummary.bondCount).append(" 只债券，未处理 ")
                            .append(poolSummary.skippedCount).append(" 个主体");
                }
            }
            return message.toString();
        }
    }

    /** 单个目标池的主体和债券入池统计 */
    private static final class PoolAutoInSummary {

        /** 目标池 ID */
        private final Long poolId;
        /** 目标池名称 */
        private final String poolName;
        /** 目标池主体入池数 */
        private int companyCount;
        /** 目标池债券入池数 */
        private int bondCount;
        /** 目标池扫描主体数 */
        private int candidateCount;
        /** 目标池未处理主体数 */
        private int skippedCount;

        /** 创建目标池统计对象 */
        private PoolAutoInSummary(Long poolId, String poolName) {
            this.poolId = poolId;
            this.poolName = poolName;
        }
    }

    /**
     * 分别查询条款（一）（二）（三），按主体代码合并并回填命中标记
     *
     * @param targetPoolId 入池目标池
     * @param agencyCodes 有效外部评级机构编码
     * @return 去重后的待入池主体
     */
    private List<ScheduledAdjustCandidateDto> queryInboundCandidates(
            Long targetPoolId, List<String> agencyCodes) {
        Map<String, ScheduledAdjustCandidateDto> merged = new LinkedHashMap<>();
        // 1. 查询当前在禁止库且尚未在目标池的主体
        mergeInboundHits(merged, autoAdjustMapper.queryCompanyInPoolNotInTarget(
                AutoAdjustRestrictHelper.COMPANY_FORBIDDEN_POOL_ID, targetPoolId), true, false, false);
        // 2. 查询当前在重点观察且尚未在目标池的主体
        mergeInboundHits(merged, autoAdjustMapper.queryCompanyInPoolNotInTarget(
                AutoAdjustRestrictHelper.KEY_WATCH_POOL_ID, targetPoolId), false, false, true);
        // 3. 查询近一年孰低 AA-及以下且尚未在目标池的主体
        mergeInboundHits(merged, autoAdjustMapper.queryCompanyByLowOuterRatingNotInPool(targetPoolId, agencyCodes),
                false, true, false);
        return new ArrayList<>(merged.values());
    }

    /**
     * 将一批候选并入结果，并按本批命中的条款打标记
     *
     * @param merged     按主体代码去重的结果
     * @param rows       本条款查询结果
     * @param forbidden  是否条款（一）
     * @param lowRating  是否条款（二）
     * @param restricted 是否条款（三）
     */
    private void mergeInboundHits(Map<String, ScheduledAdjustCandidateDto> merged,
                                  List<ScheduledAdjustCandidateDto> rows,
                                  boolean forbidden, boolean lowRating, boolean restricted) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (ScheduledAdjustCandidateDto row : rows) {
            if (row == null || !StringUtils.hasText(row.getSecurityCode())) {
                continue;
            }
            ScheduledAdjustCandidateDto exist = merged.get(row.getSecurityCode());
            if (exist == null) {
                exist = row;
                exist.setSecurityType("company");
                exist.setInForbiddenPool(0);
                exist.setInLowOuterRating(0);
                exist.setInRestrictedPool(0);
                merged.put(row.getSecurityCode(), exist);
            } else if (!StringUtils.hasText(exist.getOuterRating())
                    && StringUtils.hasText(row.getOuterRating())) {
                exist.setOuterRating(row.getOuterRating());
            }
            if (forbidden) {
                exist.setInForbiddenPool(1);
            }
            if (lowRating) {
                exist.setInLowOuterRating(1);
            }
            if (restricted) {
                exist.setInRestrictedPool(1);
            }
        }
    }

    /**
     * 调整原因按命中的（一）（二）（三）拼接
     *
     * @param company 入池候选（含命中标记与孰低外评）
     * @return 调整原因
     */
    static String buildAdjustReason(ScheduledAdjustCandidateDto company) {
        if (company == null) {
            return REASON;
        }
        List<String> parts = new ArrayList<>();
        // 1. 拼接当前在公司信用债禁止库的命中说明
        if (isFlagHit(company.getInForbiddenPool())) {
            parts.add("公司信用债禁止库内主体");
        }
        // 2. 拼接近一年认可外评孰低为 AA-及以下的命中说明
        if (isLowOuterRatingHit(company)) {
            if (StringUtils.hasText(company.getOuterRating())) {
                parts.add("近一年孰低外评：" + company.getOuterRating().trim());
            } else {
                parts.add("近一年孰低外评为AA-及以下");
            }
        }
        // 3. 拼接当前在重点观察名单的命中说明
        if (isFlagHit(company.getInRestrictedPool())) {
            parts.add("重点观察名单内主体");
        }
        if (parts.isEmpty()) {
            return REASON;
        }
        StringBuilder builder = new StringBuilder(REASON);
        builder.append('（');
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append('；');
            }
            builder.append(parts.get(i));
        }
        builder.append('）');
        return builder.toString();
    }

    /**
     * 将主体旗下尚未在目标池的未退市、未到期债券一并调入，并处理互斥出池
     *
     * 执行顺序：
     * 1. 查询主体旗下符合范围的普通债和 CRMW
     * 2. 检查目标池和债券调入限制
     * 3. 写入债券入池日志和池状态
     * 4. 按互斥和反向限制关系处理债券原所在池
     *
     * @param companyLog 已生效的主体入池日志
     * @param pool 目标池
     * @param allRelations 全量池关系
     * @param poolMap 全量池映射
     * @param detail 过程日志
     * @return 实际入池债券条数
     */
    private int inSamePoolBonds(IpAdjustLogBo companyLog, InvestmentPoolBo pool,
                                List<PoolRelationBo> allRelations, Map<Long, InvestmentPoolBo> poolMap,
                                TaskDetailLog detail) {
        if (companyLog == null || pool == null || companyLog.getTargetPoolId() == null
                || !StringUtils.hasText(companyLog.getSecurityCode())) {
            return 0;
        }
        Long poolId = companyLog.getTargetPoolId();
        String companyCode = companyLog.getSecurityCode();
        // 1. 校验目标池类型，CRMW 组合池不走普通池状态
        if (CompanyBondSyncPolicy.isCrmwCombinationPool(pool)) {
            throw new BizException("CRMW 组合池不支持主体及旗下债券同池调整");
        }
        List<ScheduledAdjustCandidateDto> bonds = autoAdjustMapper.queryCompanyBondNotInSamePoolForAutoIn(
                companyCode, poolId, CompanyBondSyncPolicy.outerRatingInboundScope());
        if (bonds == null || bonds.isEmpty()) {
            return 0;
        }
        // 2. 读取目标池调入限制池，供旗下债券校验
        List<Long> inRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                poolId, RelationType.IN_RESTRICT.getCode(), allRelations);
        int bondCount = 0;
        String bondReason = (companyLog.getAdjustReason() == null ? REASON : companyLog.getAdjustReason())
                + "（同池旗下债）";
        for (ScheduledAdjustCandidateDto bond : bonds) {
            if (bond == null || !StringUtils.hasText(bond.getSecurityCode())) {
                skipDetail(detail, "主体【" + companyCode + "】旗下债券缺少证券编码，本轮未处理");
                continue;
            }
            List<Long> currentPoolIds = securityPoolAdjustMapper
                    .querySecurityCurrentPoolIdList(bond.getSecurityCode());
            // 3. 对齐老 AdjustPoolByRule.checkSecurityInPoolRelation（关系 11 / 调入限制池），命中时阻断主体联动
            if (AutoAdjustRestrictHelper.isInAnyPool(currentPoolIds, inRestrictPoolIds)) {
                throw new BizException("债券[" + bond.getSecurityCode()
                        + "]当前在调入限制池中，不能联动调入主体");
            }
            // 4. 回填债券自动入池日志字段
            fillAutoInBondLog(bond, companyLog, pool, bondReason);
            // 5. 写债券入池日志并更新池状态
            if (securityPoolAdjustMapper.addAdjustLog(bond) != 1) {
                throw new BizException("债券[" + bond.getSecurityCode() + "]自动调入日志写入失败");
            }
            bond.setAdjustLogId(bond.getId());
            int inserted = securityPoolAdjustMapper.addPoolStatus(bond);
            if (inserted != 1) {
                throw new BizException("债券[" + bond.getSecurityCode()
                        + "]写入池状态失败（可能并发已入池）");
            }
            bondCount++;
            // 6. 入目标池后处理债券原所在的互斥和反向限制池
            int autoOutCount = AutoAdjustRelationHelper.autoOutCurrentRelationPools(
                    bond, currentPoolIds, poolMap, allRelations, securityPoolAdjustMapper);
            if (autoOutCount > 0) {
                infoDetail(detail, "债券[" + bond.getSecurityCode() + "]自动调出关系池 "
                        + autoOutCount + " 个");
            }
        }
        if (bondCount > 0) {
            infoDetail(detail, "主体[" + companyCode + "]同池旗下债调入 " + bondCount + " 条");
        }
        return bondCount;
    }

    /**
     * 按主体入池日志回填旗下债自动入池公共字段
     *
     * @param bond       旗下债日志
     * @param companyLog 主体入池日志
     * @param pool       目标池
     * @param reason     调整原因
     */
    private void fillAutoInBondLog(ScheduledAdjustCandidateDto bond, IpAdjustLogBo companyLog,
                                   InvestmentPoolBo pool, String reason) {
        bond.setAdjustType("自动调整");
        bond.setAdjustMode(AdjustMode.IN.getCode());
        bond.setTargetPoolId(companyLog.getTargetPoolId());
        bond.setTargetPoolName(pool.getPoolName());
        bond.setPoolType(pool.getPoolType());
        bond.setAuditStatus(AuditStatus.APPROVED.getCode());
        bond.setAdjusterId(AUTO_ADJUSTER_ID);
        bond.setAdjusterName(AUTO_ADJUSTER_NAME);
        bond.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(reason,
                ScheduledAdjustLogHelper.passiveAbsIdentityDetail(bond),
                ScheduledAdjustLogHelper.triggerCompanyDetail(bond)));
        bond.setAdjustBatchNo(companyLog.getAdjustBatchNo());
        bond.setSubmitTime(companyLog.getSubmitTime());
    }

    /**
     * 判断查询回填的命中标记是否为 1
     *
     * @param flag 命中标记
     * @return true=命中
     */
    private static boolean isFlagHit(Integer flag) {
        return flag != null && flag.intValue() == 1;
    }

    /**
     * 判断是否命中条款（二）；查询未带回标记时，仅有外评文本则按（二）展示
     *
     * @param company 入池候选
     * @return true=命中近一年孰低 AA-及以下
     */
    private static boolean isLowOuterRatingHit(ScheduledAdjustCandidateDto company) {
        // 1. 优先使用 SQL 回填的（二）命中标记
        if (isFlagHit(company.getInLowOuterRating())) {
            return true;
        }
        return company.getInLowOuterRating() == null
                && StringUtils.hasText(company.getOuterRating())
                && !isFlagHit(company.getInForbiddenPool())
                && !isFlagHit(company.getInRestrictedPool());
    }

    /**
     * 解析扫描池：扩展参数 poolIds 与关系配置绑定本任务的池取并集
     *
     * @param taskName 任务展示名称
     * @param detail   过程日志
     * @return 入池目标池 ID
     */
    private List<Long> resolvePoolIds(String taskName, TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        String paramJson = (conf != null && StringUtils.hasText(conf.getParamJson()))
                ? conf.getParamJson().trim() : null;
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        // 1. 合并扩展参数和关系配置绑定池
        return poolScopeHelper.resolveUnionPoolIds(paramJson, TASK_CODE, RuleType.AUTO_IN.getCode(), detail);
    }

    /**
     * 解析扩展参数 JSON 为池 ID 列表，格式：{"poolIds":[17]}
     *
     * @param raw      扩展参数原文
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
     * @param raw 扩展参数原文
     * @return 池 ID 列表
     */
    List<Long> parsePoolIds(String raw) {
        return parsePoolIds(raw, TASK_CODE);
    }

    /**
     * 构建池 ID 到投资池对象的映射
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
     * 从库表读取任务展示名称，缺失时回退为任务编码
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
        return TASK_CODE;
    }

    /**
     * 写 INFO 级过程日志并同步控制台
     *
     * @param detail 过程日志
     * @param line   日志内容
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
     * @param detail 过程日志
     * @param line   日志内容
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
     * @param detail 过程日志
     * @param line 未处理原因
     */
    private void skipDetail(TaskDetailLog detail, String line) {
        log.info("定时任务未处理：{}", line);
        if (detail != null) {
            detail.line("未处理：" + line);
        }
    }

    /** 当前任务发生异常时标记事务回滚 */
    private void markTransactionRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            log.debug("当前无可回滚事务: {}", TASK_CODE);
        }
    }
}
