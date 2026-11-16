package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 主体依据失效的债券自动出池
 *
 * 执行顺序：
 * 1. 合并扩展参数和关系配置中的债券池、主体池映射
 * 2. 查询债券已在债券池且主体不在对应主体池的记录
 * 3. 排除退市债、ABS 和来源无法确认的记录
 * 4. 软删除债券池状态并写入自动出池日志
 * 5. 汇总每组映射的实际出池和未处理数量
 *
 * 本任务处理普通债与普通风险池内的 CRMW，ABS 由专用任务同步
 */
@Slf4j
@Service
public class CompanyNotInPoolBondAutoOutService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "company_not_in_pool_bond_auto_out";

    private static final String AUTO_ADJUSTER_ID = "0";
    private static final String AUTO_ADJUSTER_NAME = "系统";
    static final String DEFAULT_TASK_NAME = "主体依据失效的债券自动出池（仅债券）";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PARAM_HELP =
            "参数格式：须填写 JSON 对象；poolIds、mappings 可组合，例如 <code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单个同池）：<code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：债券在 15（债券禁止库）、发行主体不在 15（债券禁止库）时，债券从 15（债券禁止库）自动调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多个同池）：<code>{\"poolIds\":[15,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：分别检查债券与主体在 15（债券禁止库）、16（观察池）的同池关系；主体不在对应池时，债券从该池自动调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "跨池映射写法：<code>{\"mappings\":[{\"bondPoolId\":16,\"companyPoolId\":15}]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：债券在 16（观察池）、发行主体不在 15（债券禁止库）时，债券从 16（观察池）自动调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "组合写法：<code>{\"poolIds\":[15],\"mappings\":[{\"bondPoolId\":16,\"companyPoolId\":15}]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：同时检查两组关系：债券在 15（债券禁止库）且主体不在 15（债券禁止库）时出池；以及债券在 16（观察池）且主体不在 15（债券禁止库）时出池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（债券当前所在池 + 主体应在池）：可选；债券在这些池内、但发行主体不在同一池时，债券从该池出库；可填写多个数字 ID\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "mappings（跨池出池映射）：可选；用于债券当前所在池与主体应在池不一致的场景，可配置多组\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "bondPoolId（债券当前所在池）：扫描该池内的债券；命中条件后，债券从该池出库\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "companyPoolId（主体应在池）：用于检查债券发行主体是否仍在该池；主体不在该池时，债券才出库\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "关系配置：在投资池「自动调出规则」中绑定本任务的池，会按同池映射并入扫描范围\n"
                    + "扫描范围：扩展参数 poolIds/mappings 与投资池关系配置绑定本任务的池（按同池映射）取并集；并集为空时本轮失败\n"
                    + "处理规则：仅调出可识别为主体跟随入池且所有映射依据均已失效的债券\n"
                    + "类型范围：普通债和 CRMW；排除退市债和 ABS\n"
                    + "CRMW 说明：仅调出 CRMW 证券在普通池的 ip_pool_status，不操作 CRMW 组合状态表\n"
                    + "范围说明：遵守调出限制池、调出互斥和在途流程；保留其他来源入池记录\n"
                    + "参数格式错误时，本轮任务失败";

    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 普通债补入范围 */
    @Resource
    private CompanyNewBondAutoInService companyNewBondAutoInService;
    /** 黑名单质押库三条件判定 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;
    /** 扫描池并集（参数 ∪ 关系配置） */
    @Resource
    private AutoAdjustPoolScopeHelper poolScopeHelper;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 调出主体依据全部失效的跟随入池债券
     *
     * 执行顺序：
     * 1. 读取任务名称并记录任务开始
     * 2. 扫描债券池与主体池映射
     * 3. 对不满足单条业务条件的债券记录原因后跳过
     * 4. 写入债券出池记录并汇总结果
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
        // 1. 读取任务展示名称并记录任务开始
        String taskName = resolveTaskName();
        infoDetail(detail, "任务开始：" + taskName);
        try {
            // 2. 调出主体依据全部失效的跟随入池债券
            AutoOutSummary summary = doAutoOut(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = summary.buildMessage();
            // 3. 记录任务结果并返回成功结果
            infoDetail(detail, "任务结束：" + message + "；耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, summary.total, startTime, duration,
                    detail.build());
        } catch (BizException e) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            long duration = System.currentTimeMillis() - begin;
            warnDetail(detail, taskName + " 失败: " + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName, e.getMessage(), startTime, duration,
                    detail.build());
        } catch (Exception e) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", taskName, e);
            detail.line("ERROR", taskName + " 异常: " + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName,
                    "执行异常: " + e.getMessage(), startTime, duration, detail.build());
        }
    }

    /**
     * 按债券池和主体池映射处理主体不在池的债券
     *
     * 执行顺序：
     * 1. 解析扩展参数和关系配置映射
     * 2. 读取投资池、批次信息和扫描候选
     * 3. 逐条检查债券并处理出池
     * 4. 汇总映射处理结果
     *
     * @param taskName 任务展示名称
     * @param detail 过程日志
     * @return 本轮债券出池汇总
     */
    private AutoOutSummary doAutoOut(String taskName, TaskDetailLog detail) {
        // 1. 读取扩展参数并解析有效映射
        String paramJson = resolveParamJson();
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        List<long[]> outboundMappings = new ArrayList<>(parseParamMappings(paramJson, taskName));
        for (Long poolId : autoAdjustMapper.queryCompanyHistoricalPoolIds()) {
            outboundMappings.add(new long[]{poolId, poolId});
        }
        for (long[] pair : companyNewBondAutoInService.resolveInboundMappings(detail)) {
            outboundMappings.add(new long[]{pair[1], pair[0]});
        }
        if (outboundMappings.isEmpty() && poolScopeHelper.queryBoundPoolIds(TASK_CODE, RuleType.AUTO_OUT.getCode()).isEmpty()) {
            return new AutoOutSummary();
        }
        List<long[]> pairList = poolScopeHelper.unionSamePoolMappings(
                outboundMappings, TASK_CODE, RuleType.AUTO_OUT.getCode(), detail);
        Map<Long, Set<Long>> companyPoolsByBondPool = new LinkedHashMap<>();
        for (long[] pair : pairList) {
            companyPoolsByBondPool.computeIfAbsent(pair[0], key -> new LinkedHashSet<>()).add(pair[1]);
        }
        infoDetail(detail, "有效债券池数 " + companyPoolsByBondPool.size());
        infoDetail(detail, "扫描条件：普通债跟随记录已生效，且发行主体已不在任一依据池");
        // 2. 准备投资池信息和自动调库批次
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        List<PoolRelationBo> relations = securityPoolAdjustMapper.queryAllPoolRelationList();
        ScheduledAdjustBatchNoContext batchNoContext = new ScheduledAdjustBatchNoContext();
        Date submitTime = batchNoContext.getSubmitTime();
        AutoOutSummary summary = new AutoOutSummary();
        // 3. 逐个映射查询和处理债券
        for (Map.Entry<Long, Set<Long>> entry : companyPoolsByBondPool.entrySet()) {
            Long bondPoolId = entry.getKey();
            Set<Long> companyPoolIds = entry.getValue();
            InvestmentPoolBo bondPool = poolMap.get(bondPoolId);
            if (bondPool == null) {
                warnDetail(detail, "债券池[" + bondPoolId + "]不存在，跳过映射 "
                        + bondPoolId + "←主体池" + companyPoolIds);
                continue;
            }
            if (CompanyBondSyncPolicy.isCrmwCombinationPool(bondPool)) {
                warnDetail(detail, "债券池[" + bondPoolId
                        + "]为 CRMW 组合池，主体旗下证券不能通过普通池状态调出，跳过该映射");
                continue;
            }
            // 每个债券池一次核对所有主体依据
            companyPoolIds.add(bondPoolId);
            List<ScheduledAdjustCandidateDto> bonds = autoAdjustMapper.queryCompanyFollowedBondsForAutoOut(
                    bondPoolId, companyPoolIds);
            List<Long> restrictPools = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    bondPoolId, RelationType.OUT_RESTRICT.getCode(), relations);
            List<Long> mutexPools = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    bondPoolId, RelationType.OUT_MUTEX.getCode(), relations);
            int pairCount = 0;
            for (ScheduledAdjustCandidateDto bond : bonds) {
                if (bond == null || !StringUtils.hasText(bond.getSecurityCode())) {
                    continue;
                }
                if (PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(bondPoolId)
                        && pledgeBlacklistRuleService.evaluate(bond.getIssuerCode()).shouldBeInBlacklist()) {
                    infoDetail(detail, "债券[" + bond.getSecurityCode() + "]的发行主体仍满足黑名单条件，暂不调出");
                    continue;
                }
                List<Long> currentPools = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(bond.getSecurityCode());
                if (AutoAdjustRestrictHelper.isInAnyPool(currentPools, restrictPools)
                        || AutoAdjustRestrictHelper.isInAnyPool(currentPools, mutexPools)
                        || securityPoolAdjustMapper.querySecurityHasPendingProcess(bond.getSecurityCode())) {
                    infoDetail(detail, "债券[" + bond.getSecurityCode() + "]命中调出限制、互斥或在途流程，暂不调出");
                    continue;
                }
                // 只删除已识别的跟随记录，其他渠道入池记录继续有效
                int deleted = autoAdjustMapper.deleteCompanyFollowedBondStatus(bond.getPoolStatusId());
                if (deleted == 0) {
                    warnDetail(detail, "债券[" + bond.getSecurityCode() + "]软删失败（可能并发已出池），跳过");
                    continue;
                }
                if (securityPoolAdjustMapper.querySecurityCurrentPoolIdList(bond.getSecurityCode()).contains(bondPoolId)) {
                    infoDetail(detail, "债券[" + bond.getSecurityCode() + "]撤销跟随依据，其他在池记录仍生效");
                    continue;
                }
                // 3.3 准备债券自动出池日志字段
                bond.setAdjustType("自动调整");
                bond.setAdjustMode(AdjustMode.OUT.getCode());
                bond.setTargetPoolId(bondPoolId);
                bond.setTargetPoolName(bondPool.getPoolName());
                bond.setPoolType(bondPool.getPoolType());
                bond.setAuditStatus(AuditStatus.APPROVED.getCode());
                bond.setAdjusterId(AUTO_ADJUSTER_ID);
                bond.setAdjusterName(AUTO_ADJUSTER_NAME);
                String reason = ScheduledAdjustLogHelper.appendDetails(ScheduledAdjustLogHelper.taskLabel(taskName),
                        ScheduledAdjustLogHelper.issuerDetail(bond),
                        "主体未在任一依据池：" + companyPoolIds);
                bond.setAdjustReason(reason);
                // 3.4 生成批次号并写入自动出池日志
                String batchNo = batchNoContext.resolveBondBatchNo(
                        bond.getSecurityCode(), bondPoolId, AdjustMode.OUT.getCode());
                bond.setAdjustBatchNo(batchNo);
                bond.setSubmitTime(submitTime);
                // 3.5 写债券出池日志
                if (securityPoolAdjustMapper.addAdjustLog(bond) != 1) {
                    throw new BizException("债券[" + bond.getSecurityCode() + "]自动调出日志写入失败");
                }
                pairCount++;
            }
            summary.addPool(bondPool.getPoolName(), companyPoolIds, pairCount);
            infoDetail(detail, "债券池[" + bondPool.getPoolName() + "](" + bondPoolId
                    + ")←主体池[" + companyPoolIds + "] 出池 " + pairCount + " 条");
        }
        infoDetail(detail, summary.buildMessage());
        return summary;
    }

    /** 本轮主体不在池债券出池汇总 */
    private static final class AutoOutSummary {

        /** 本轮实际出池总数 */
        private int total;
        /** 各债券池的出池结果 */
        private final List<String> poolResults = new ArrayList<>();

        /** 累计实际出池记录 */
        private void addPool(String bondPoolName, Set<Long> companyPoolIds, int count) {
            total += count;
            poolResults.add(bondPoolName + "（主体依据池 " + companyPoolIds + "）出池 " + count + " 条");
        }

        /** 汇总本轮出池结果 */
        private String buildMessage() {
            return "任务完成：实际出池 " + total + " 条。" + String.join("；", poolResults);
        }
    }

    private String resolveParamJson() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf != null && StringUtils.hasText(conf.getParamJson())) {
            return conf.getParamJson().trim();
        }
        return null;
    }

    /**
     * 解析为 [bondPoolId, companyPoolId]；poolIds 表示两池相同
     */
    List<long[]> parseParamMappings(String raw, String taskName) {
        List<long[]> result = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return result;
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            throw new BizException("扩展参数须为 JSON 对象，示例 {\"poolIds\":[15]}，当前: " + text);
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage());
        }
        JsonNode poolIdsNode = root.get("poolIds");
        if (poolIdsNode != null && !poolIdsNode.isNull()) {
            if (!poolIdsNode.isArray()) {
                throw new BizException("poolIds 须为数字数组，示例 {\"poolIds\":[15]}");
            }
            for (JsonNode item : poolIdsNode) {
                if (item == null || item.isNull() || !item.isNumber()) {
                    throw new BizException("poolIds 元素须为数字，非法值: " + item);
                }
                long id = item.asLong();
                result.add(new long[]{id, id});
            }
        }
        JsonNode mappings = root.get("mappings");
        if (mappings != null && mappings.isArray()) {
            for (JsonNode item : mappings) {
                if (item == null || item.isNull()) {
                    continue;
                }
                JsonNode bondNode = item.get("bondPoolId");
                JsonNode companyNode = item.get("companyPoolId");
                if (bondNode == null || !bondNode.isNumber() || companyNode == null || !companyNode.isNumber()) {
                    throw new BizException("mappings 项须含 bondPoolId、companyPoolId 数字，非法项: " + item);
                }
                result.add(new long[]{bondNode.asLong(), companyNode.asLong()});
            }
        }
        return result;
    }

    List<long[]> parseParamMappings(String raw) {
        return parseParamMappings(raw, TASK_CODE);
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
                return ScheduledAdjustLogHelper.withTaskScope(TASK_CODE, conf.getTaskName());
            }
        } catch (Exception e) {
            log.debug("读取任务名称失败: {}", TASK_CODE);
        }
        return DEFAULT_TASK_NAME;
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
}
