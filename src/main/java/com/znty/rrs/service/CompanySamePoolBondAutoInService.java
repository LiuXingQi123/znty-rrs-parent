package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
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
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主体下债券自动入库任务（同池）
 * <p>
 * 对应老系统 IP_RULE type=0「主体下债券自动入库」：主体已在目标池 → 旗下 bond 大类、
 * 未到期（含到期当天）、尚未在<strong>同一池</strong>的债券自动入池；尊重池 market_codes（空不限制）；
 * 不排除临时代码已更新记录。与 {@link CompanyNewBondAutoInService}
 * （对应 AutoAdjustInNewBondToLimitPoolJob，可跨池、排除临时代码）区分。
 * </p>
 */
@Slf4j
@Service
public class CompanySamePoolBondAutoInService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "company_same_pool_bond_auto_in";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 自动入池原因 */
    private static final String REASON = "主体下债券自动入库";
    /** 批次号后缀 */
    private static final String BATCH_SUFFIX = "3006";

    /**
     * 扩展参数说明
     */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[15,17]}</code>；也可不填 poolIds，仅扫描投资池关系配置中绑定了本任务的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：主体已在 15（债券禁止库）时，旗下符合条件且尚未在 15（债券禁止库）的债券自动调入该池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多池）：<code>{\"poolIds\":[15,17]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：分别扫描 15（债券禁止库）、17（黑名单质押库）内的主体，将其旗下符合条件的债券补充调入主体所在的同一池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体所在池 + 债券入池目标池）：可选；与投资池「关系配置 → 自动调入规则」中绑定本任务的池取并集后扫描\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：主体已在目标池时，将其旗下未到期（含到期当天）且未在同一池的债券自动入池\n"
                    + "17 特别规则：扫描黑名单质押库时再次校验主体三条件，三个条件均不满足则不补债\n"
                    + "市场规则：目标池 market_codes 为空或 [] 时不限制；有配置时债券须命中允许市场\n"
                    + "限制规则：债券已在目标池配置的调入限制池时，跳过该条记录\n"
                    + "关系调出：入池成功后，按目标池调入互斥关系及反向调入限制关系自动调出债券原所在池并记录日志\n"
                    + "范围说明：不排除已更新临时代码、ABS、CRMW；跨池场景请使用“在池主体旗下债券自动入池”任务\n"
                    + "CRMW 说明：CRMW 证券跟随主体进入普通目标池时写 ip_pool_status，不写 CRMW 组合状态表\n"
                    + "参数格式错误时，本轮任务失败";

    /** 自动调库查询 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 调库落地 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 投资池 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 定时任务配置 */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;
    /** 扫描池并集（参数 ∪ 关系配置） */
    @Resource
    private AutoAdjustPoolScopeHelper poolScopeHelper;
    /** 黑名单质押库三条件统一判定 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        String taskName = resolveTaskName();
        infoDetail(detail, taskName + " 开始");
        try {
            int total = doAutoIn(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共自动入池 " + total + " 条债券";
            infoDetail(detail, taskName + " 结束，" + message);
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, total, startTime, duration,
                    detail.build());
        } catch (BizException e) {
            // 返回失败结果前标记本轮事务回滚
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            warnDetail(detail, taskName + " 失败: " + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName, e.getMessage(), startTime, duration,
                    detail.build());
        } catch (Exception e) {
            // 返回失败结果前标记本轮事务回滚
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", taskName, e);
            detail.line("ERROR", taskName + " 异常: " + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName,
                    "执行异常: " + e.getMessage(), startTime, duration, detail.build());
        }
    }

    /**
     * 按 poolIds 将同池在池主体旗下未在池债券自动入池
     */
    private int doAutoIn(String taskName, TaskDetailLog detail) {
        List<Long> poolIds = resolvePoolIds(taskName, detail);
        infoDetail(detail, "目标池列表 poolIds=" + poolIds + "（主体与债同一池）");
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        infoDetail(detail, "本轮批次号 " + batchNo);
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        int total = 0;
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                warnDetail(detail, "池[" + poolId + "]不存在，跳过");
                continue;
            }
            if (CompanyBondSyncPolicy.isCrmwCombinationPool(pool)) {
                warnDetail(detail, "池[" + poolId
                        + "]为 CRMW 组合池，主体旗下证券不能通过普通池状态写入，跳过");
                continue;
            }
            List<Long> inRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.IN_RESTRICT.getCode(), allRelations);
            // 查询同池待入库债券（含市场过滤）
            List<IpAdjustLogBo> bondList = autoAdjustMapper.queryCompanyBondSamePoolForAutoIn(
                    poolId, CompanyBondSyncPolicy.currentTypeScope());
            if (bondList == null || bondList.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无待入库同池债券");
                continue;
            }
            int poolCount = 0;
            for (IpAdjustLogBo bond : bondList) {
                if (bond == null || !StringUtils.hasText(bond.getSecurityCode())) {
                    continue;
                }
                if (PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(poolId)) {
                    SecurityInfoBo security = securityPoolAdjustMapper
                            .querySecurityBoByCode(bond.getSecurityCode());
                    String companyCode = security == null ? null : security.getIssuerCode();
                    if (companyCode == null
                            || !pledgeBlacklistRuleService.evaluate(companyCode).shouldBeInBlacklist()) {
                        // 记录未命中黑名单质押库条件的跳过原因
                        warnDetail(detail, "债券[" + bond.getSecurityCode()
                                + "]发行主体未命中黑名单质押库三个条件，跳过");
                        continue;
                    }
                }
                List<Long> currentPoolIds = securityPoolAdjustMapper
                        .querySecurityCurrentPoolIdList(bond.getSecurityCode());
                // 对齐老 AdjustPoolByRule.checkSecurityInPoolRelation（关系 11 / 调入限制池）
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        currentPoolIds, inRestrictPoolIds)) {
                    warnDetail(detail, "债券[" + bond.getSecurityCode() + "]当前在调入限制池中，跳过");
                    continue;
                }
                bond.setAdjustType("自动调整");
                bond.setAdjustMode(AdjustMode.IN.getCode());
                bond.setTargetPoolId(poolId);
                bond.setTargetPoolName(pool.getPoolName());
                bond.setPoolType(pool.getPoolType());
                bond.setAuditStatus(AuditStatus.APPROVED.getCode());
                bond.setAdjusterId(AUTO_ADJUSTER_ID);
                bond.setAdjusterName(AUTO_ADJUSTER_NAME);
                bond.setAdjustReason(REASON);
                bond.setAdjustAdvice(REASON);
                bond.setAdjustBatchNo(batchNo);
                bond.setSubmitTime(submitTime);
                // 写自动入池日志
                if (securityPoolAdjustMapper.addAdjustLog(bond) != 1) {
                    throw new BizException("债券[" + bond.getSecurityCode() + "]自动调入日志写入失败");
                }
                bond.setAdjustLogId(bond.getId());
                // 写入在池状态
                int inserted = securityPoolAdjustMapper.addPoolStatus(bond);
                if (inserted != 1) {
                    throw new BizException("债券[" + bond.getSecurityCode()
                            + "]写入池状态失败（可能并发已入池）");
                }
                poolCount++;
                total++;
                // 入池成功后从当前实际所在的互斥/受限池调出，并写同批自动调出日志
                int autoOutCount = AutoAdjustRelationHelper.autoOutCurrentRelationPools(
                        bond, currentPoolIds, poolMap, allRelations, securityPoolAdjustMapper);
                if (autoOutCount > 0) {
                    infoDetail(detail, "债券[" + bond.getSecurityCode() + "]自动调出关系池 "
                            + autoOutCount + " 个");
                }
            }
            infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 入池 " + poolCount + " 条");
        }
        infoDetail(detail, "批次号 " + batchNo + "，合计入池 " + total + " 条债券");
        return total;
    }

    /**
     * 解析扫描池：扩展参数 poolIds 与关系配置绑定本任务的池取并集
     */
    private List<Long> resolvePoolIds(String taskName, TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        String paramJson = (conf != null && StringUtils.hasText(conf.getParamJson()))
                ? conf.getParamJson().trim() : null;
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        return poolScopeHelper.resolveUnionPoolIds(paramJson, TASK_CODE, RuleType.AUTO_IN.getCode(), detail);
    }

    /**
     * 解析 {"poolIds":[15,16]}（包内可测）
     */
    List<Long> parsePoolIds(String raw, String taskName) {
        List<Long> ids = AutoAdjustPoolScopeHelper.parseOptionalPoolIds(raw);
        if (ids.isEmpty()) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[15]}");
        }
        return ids;
    }

    /** 单测入口 */
    List<Long> parsePoolIds(String raw) {
        return parsePoolIds(raw, TASK_CODE);
    }

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

    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line("INFO", line);
        }
    }

    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line("WARN", line);
        }
    }

    /** 任务捕获异常并返回失败结果时，将当前数据库事务标记为回滚。 */
    private void markTransactionRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            log.debug("当前无可回滚事务: {}", TASK_CODE);
        }
    }
}
