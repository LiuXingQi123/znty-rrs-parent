package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.TempOprtSource;
import com.znty.rrs.common.util.MarketCodeMatchUtil;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotCrmwBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时代码转正业务转换，由转正入口持有事务。
 * 1. 准备并校验普通池转换计划，检查信用债独立流程中申请、市场和池关系。
 * 2. 普通池处理：按六种情况执行当前在池状态和流程中申请的整组转换。
 * 3. CRMW 处理：同一凭证下，按标的债券的全部市场代码处理流程中申请和已在池记录。
 */
@Service
public class TempSecurityCodeBusinessConverter {

    /** 转正时临时出库原因（对齐老系统「债券临时代码调出」） */
    private static final String REASON_TEMP_OUT = "债券临时代码调出";
    /** 原在池记录缺少原因时使用的正式代码调入原因 */
    private static final String REASON_FORMAL_IN = "债券正式代码调入";
    /** 系统操作人 ID（对齐老系统 inputId=0） */
    private static final String SYSTEM_ADJUSTER_ID = "0";
    /** 系统操作人名称 */
    private static final String SYSTEM_ADJUSTER_NAME = "系统";
    /** 系统调出类型（对齐老系统 adjustType=5 语义） */
    private static final String ADJUST_TYPE_TEMP_OUT = "临时代码调出";
    /** 与普通证券调库一致的互斥配套调出类型。 */
    private static final String ADJUST_TYPE_MUTEX = "互斥调整";

    /** 临时代码与转换运行态访问组件。 */
    @Resource
    private TempSecurityCodeMapper tempSecurityCodeMapper;
    /** 调库日志、证券主档及快照访问组件。 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** CRMW 池状态及证券快照写入组件。 */
    @Resource
    private CrmwPoolAdjustMapper crmwPoolAdjustMapper;
    /** 同券及当前代码市场权威查询组件。 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 查询当前在池状态和流程中申请，生成并校验普通池整组转换计划。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConversionPlan prepareOrdinaryPoolConversion(TempSecurityCodeBo mapping) {
        // 原临时转正继续按同券全部市场代码转换。
        return prepareConversion(mapping, false);
    }

    /** Wind 按唯一 SEC_ID 对应的本次代码转换，不扩展其他市场。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConversionPlan prepareWindCodeConversion(TempSecurityCodeBo mapping) {
        // 与原转正共用校验和写入，仅限制目标代码范围。
        return prepareConversion(mapping, true);
    }

    /** 按指定代码范围准备共享转换计划。 */
    private ConversionPlan prepareConversion(TempSecurityCodeBo mapping, boolean singleCode) {
        ConversionPlan plan = new ConversionPlan();
        plan.singleCode = singleCode;
        // 1. 查询临时代码已在池、流程中申请涉及的普通池 ID
        List<Long> poolIds = tempSecurityCodeMapper.queryOrdinaryConversionPoolIdList(mapping);
        if (poolIds.isEmpty()) {
            return plan;
        }
        // 1.1 查询临时代码当前已在池的记录，供后续执行临时码出池、正式码入池
        plan.active = tempSecurityCodeMapper.queryActivePoolStatusList(mapping);
        // 1.2 查询临时代码流程中的调入/调出申请，供后续换码并补其他市场关联申请
        plan.pending = tempSecurityCodeMapper.queryOrdinaryPendingLogList(mapping);
        if (plan.active.isEmpty() && plan.pending.isEmpty()) {
            return plan;
        }
        // 2. 查询选中正式码及同券其他市场代码，检查主数据是否可用，并保存各代码的市场
        List<String> codes = loadFormalGroup(plan, mapping.getSecurityCode());
        // 2.1 查询目标池配置，供第5步判断正式代码的市场是否符合目标池
        Map<Long, InvestmentPoolBo> pools = new HashMap<>();
        for (InvestmentPoolBo pool : tempSecurityCodeMapper.queryConversionPoolList(poolIds)) {
            pools.put(pool.getId(), pool);
        }
        // 2.2 查询池关系，供后续确定相关池范围和检查来源、限制及互斥关系
        List<PoolRelationBo> relations = securityPoolAdjustMapper.queryAllPoolRelationList();
        // 3. 校验正式代码组在相关池中是否整组在池、是否重复在池，并记录已整组在池的池 ID
        readExistingGroupState(plan, codes, poolIds, relations);
        // 4. 信用债流程中申请校验：正式代码组在信用债大库及各级库已有00/11申请时，拒绝转正
        validateCreditBondPending(codes);
        // 5. 校验调入/调出与在池状态是否一致、正式代码市场是否符合目标池、池关系是否冲突
        validateConversionPlan(plan, pools, relations);
        return plan;
    }

    /**
     * 2. 查询选中正式码及同券其他市场代码，逐码检查主数据是否可用，保存主数据和市场。
     * 例如选中 A，查到同券 B、C，则为后续整组换码准备 A、B、C 的信息。
     * 此处查询市场；是否符合目标池允许的市场，在第5步校验。
     */
    private List<String> loadFormalGroup(ConversionPlan plan, String selectedCode) {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(selectedCode);
        if (!plan.singleCode) {
            for (SecurityMarketDto related : securityMarketService.queryRelatedMarketCodes(selectedCode)) {
                codes.add(related.getWindCode());
            }
        }
        for (String code : codes) {
            SecurityInfoBo member = securityPoolAdjustMapper.querySecurityBoByCode(code);
            if (member == null || member.getSecurityType() == null || member.getSecurityType().trim().isEmpty()
                    || member.getSecurityStatus() == null || member.getSecuritySource() == null
                    || "temporary".equals(member.getSecuritySource()) || "D".equals(member.getSecurityStatus())
                    || "crmw".equals(member.getSecurityType()) || "company".equals(member.getSecurityType())) {
                throw new BizException("正式关联证券不存在或不可用，securityCode=" + code);
            }
            plan.members.add(member);
            plan.markets.put(code, securityMarketService.queryMarketCode(code));
        }
        return new ArrayList<>(codes);
    }

    /**
     * 3. 校验正式代码组的在池数据：每个相关池内必须整组在池或整组不在池，同码不能重复在池。
     * 相关池包括临时代码已在池/申请涉及的池，以及与这些池直接存在关系的池。
     * 保存正式代码组已完整在池的池 ID，供后续避免重复入池；无关池数据不参与本项校验。
     * CRMW 仅核对当前凭证下的标的组，其他凭证的记录不参与本组去重。
     */
    private void readExistingGroupState(ConversionPlan plan, List<String> codes, List<Long> targetPoolIds,
                                        List<PoolRelationBo> relations) {
        Set<Long> relevantPools = new HashSet<>(targetPoolIds);
        List<IpPoolStatusBo> formalStates = plan.crmw
                ? tempSecurityCodeMapper.queryFormalGroupCrmwPoolStatusList(codes, plan.crmwScode, plan.crmwStype)
                : tempSecurityCodeMapper.queryFormalGroupPoolStatusList(codes);
        Set<Long> formalPoolIds = new HashSet<>();
        for (IpPoolStatusBo state : formalStates) {
            formalPoolIds.add(state.getTargetPoolId());
        }
        // 互斥配套调出也要检查其自身的池关系，提前加载这些池的直接关系池状态
        Set<Long> adjustmentPools = new HashSet<>(targetPoolIds);
        for (PoolRelationBo relation : relations) {
            if (targetPoolIds.contains(relation.getPoolId())
                    && RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())
                    && formalPoolIds.contains(relation.getRelationPoolId())) {
                adjustmentPools.add(relation.getRelationPoolId());
            }
        }
        for (PoolRelationBo relation : relations) {
            if (adjustmentPools.contains(relation.getPoolId())) {
                relevantPools.add(relation.getRelationPoolId());
            }
            if (adjustmentPools.contains(relation.getRelationPoolId())) {
                relevantPools.add(relation.getPoolId());
            }
        }
        Map<Long, Set<String>> codesByPool = new HashMap<>();
        for (IpPoolStatusBo state : formalStates) {
            if (!relevantPools.contains(state.getTargetPoolId())) {
                continue;
            }
            if (!codesByPool.computeIfAbsent(state.getTargetPoolId(), key -> new HashSet<>())
                    .add(state.getSecurityCode())) {
                throw new BizException("正式代码重复在池，securityCode=" + state.getSecurityCode()
                        + "，投资池：" + state.getTargetPoolName());
            }
            plan.existingStates.computeIfAbsent(state.getTargetPoolId(), key -> new ArrayList<>()).add(state);
        }
        for (Map.Entry<Long, Set<String>> entry : codesByPool.entrySet()) {
            if (entry.getValue().size() != codes.size()) {
                throw new BizException("同券市场代码未整组在池，投资池："
                        + plan.existingStates.get(entry.getKey()).get(0).getTargetPoolName());
            }
            plan.existingPools.add(entry.getKey());
        }
    }

    /** 4. 校验正式代码组是否已有信用债流程中申请（00/11），范围为信用债大库及各级库。 */
    private void validateCreditBondPending(List<String> codes) {
        List<IpAdjustLogBo> conflicts = tempSecurityCodeMapper.queryFormalGroupPendingLogList(codes);
        if (!conflicts.isEmpty()) {
            IpAdjustLogBo conflict = conflicts.get(0);
            throw new BizException("正式代码在信用债大库存在流程中数据，securityCode=" + conflict.getSecurityCode()
                    + "，投资池：" + conflict.getTargetPoolName() + "，logId=" + conflict.getId());
        }
    }

    /**
     * 5. 校验原业务能否换码：当前在池记录不能重复，原申请批次、方向和在池状态必须有效。
     * 正式代码组实际新增入池时校验市场；按转正后及原申请批次执行后的池集合校验池关系。
     * 同时保存原申请 ID 和快照，供后续换码及补其他市场关联申请使用。
     */
    private void validateConversionPlan(ConversionPlan plan, Map<Long, InvestmentPoolBo> pools,
                                        List<PoolRelationBo> relations) {
        Set<Long> afterConversion = new HashSet<>(plan.existingPools);
        Set<Long> tempPools = new HashSet<>();
        // 5.1 已在池：检查T是否重复在池；正式组需要进入时检查市场和池关系，已完整在池则跳过入池校验
        for (IpPoolStatusBo active : plan.active) {
            Long poolId = active.getTargetPoolId();
            if (!tempPools.add(poolId)) {
                throw new BizException("临时代码重复在池，投资池：" + active.getTargetPoolName());
            }
            afterConversion.add(poolId);
            // 校验目标池存在；正式代码组需要新进入该池时，再逐码校验市场
            validateMarket(plan, pools.get(poolId), !plan.existingPools.contains(poolId));
        }
        Set<Long> newActivePools = new HashSet<>(tempPools);
        newActivePools.removeAll(plan.existingPools);
        // 5.1.1 已在池转换：正式组新进入目标池时，安排其原互斥池整组即时调出
        Set<Long> activeMutexPools = findMutexOutPools(newActivePools, afterConversion, relations);
        Set<Long> activeSourceContext = new HashSet<>(afterConversion);
        for (Long poolId : activeMutexPools) {
            if (tempPools.contains(poolId)) {
                throw new BizException("临时代码同时在互斥目标池中，存在池关系冲突，投资池：" + pools.get(poolId).getPoolName());
            }
            // 配套调出池也必须存在且类型匹配，不重新校验调入市场
            validateMutexPool(plan, pools, poolId);
            plan.activeMutexOut.addAll(plan.existingStates.get(poolId));
        }
        afterConversion.removeAll(activeMutexPools);
        for (Long poolId : tempPools) {
            if (!plan.existingPools.contains(poolId)) {
                // 检查正式代码组新进入该池后，是否满足来源池要求及限制、互斥关系
                validateRelations(poolId, true, afterConversion, activeSourceContext, relations, pools);
            }
        }
        for (Long poolId : activeMutexPools) {
            // 即时互斥调出仍检查调出限制，不能绕过目标池的关系配置
            validateRelations(poolId, false, afterConversion, afterConversion, relations, pools);
        }
        Map<String, Set<Long>> pendingIn = new LinkedHashMap<>();
        Map<String, Set<Long>> pendingOut = new LinkedHashMap<>();
        Set<String> pendingKeys = new HashSet<>();
        // 5.2 流程中：检查批次、方向和重复申请；调入时应未在池，调出时应已在池，并检查调入市场
        for (IpAdjustLogBo pending : plan.pending) {
            if (pending.getAdjustBatchNo() == null || pending.getAdjustBatchNo().trim().isEmpty()) {
                throw new BizException("在途申请缺少审批批次，logId=" + pending.getId());
            }
            Long poolId = pending.getTargetPoolId();
            boolean inbound = AdjustMode.IN.getCode().equals(pending.getAdjustMode());
            if (!inbound && !AdjustMode.OUT.getCode().equals(pending.getAdjustMode())) {
                throw new BizException("在途申请调整方向不合法，logId=" + pending.getId());
            }
            if (!pendingKeys.add(poolId + "_" + pending.getAdjustMode())
                    || inbound == afterConversion.contains(poolId)) {
                throw new BizException("临时代码的流程中申请与转正后的证券池状态冲突，投资池：" + pending.getTargetPoolName());
            }
            // 仅校验调入方向市场，不检查容量、启停、锁定、报告、评级或冻结期
            validateMarket(plan, pools.get(poolId), inbound);
            (inbound ? pendingIn : pendingOut)
                    .computeIfAbsent(pending.getAdjustBatchNo(), key -> new HashSet<>()).add(poolId);
            if (plan.crmw) {
                plan.crmwSnapshots.put(pending.getId(), tempSecurityCodeMapper.queryCrmwConversionSnapshot(pending.getId()));
            } else {
                plan.snapshots.put(pending.getId(), tempSecurityCodeMapper.queryConversionSnapshot(pending.getId()));
            }
        }
        Set<String> batches = new LinkedHashSet<>(pendingIn.keySet());
        batches.addAll(pendingOut.keySet());
        // 5.3 池关系：模拟每个原批次调入、调出后的池集合，检查来源、限制和互斥关系
        for (String batch : batches) {
            Set<Long> finalPools = new HashSet<>(afterConversion);
            Set<Long> inPools = pendingIn.getOrDefault(batch, Collections.emptySet());
            Set<Long> outPools = new HashSet<>(pendingOut.getOrDefault(batch, Collections.emptySet()));
            // 5.3.1 流程中调入：补充正式组原互斥池的调出项，与原调入申请共用审批批次
            for (Long poolId : findMutexOutPools(inPools, afterConversion, relations)) {
                if (outPools.add(poolId)) {
                    if (pendingKeys.contains(poolId + "_" + AdjustMode.OUT.getCode())) {
                        // 提示使用当前池名称，复用已加载配置，缺失时仅查询该池
                        throw new BizException("互斥池已有其他批次的流程中调出申请，投资池：" + queryConversionPoolName(pools, poolId));
                    }
                    // 加载互斥池配置，并从本批调入申请保留发起人、状态、审批信息和快照来源
                    validateMutexPool(plan, pools, poolId);
                    for (IpAdjustLogBo original : plan.pending) {
                        if (batch.equals(original.getAdjustBatchNo()) && inPools.contains(original.getTargetPoolId())
                                && AdjustMode.IN.getCode().equals(original.getAdjustMode())
                                && hasInMutexRelation(original.getTargetPoolId(), poolId, relations)) {
                            IpAdjustLogBo out = new IpAdjustLogBo();
                            BeanUtils.copyProperties(original, out);
                            out.setTargetPoolId(poolId);
                            out.setTargetPoolName(pools.get(poolId).getPoolName());
                            out.setPoolType(pools.get(poolId).getPoolType());
                            out.setAdjustMode(AdjustMode.OUT.getCode());
                            out.setAdjustType(ADJUST_TYPE_MUTEX);
                            out.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(original.getAdjustReason(),
                                    "调入池：" + pools.get(original.getTargetPoolId()).getPoolName(),
                                    "互斥配套调出池：" + pools.get(poolId).getPoolName()));
                            plan.pendingMutexOut.add(out);
                            pendingKeys.add(poolId + "_" + AdjustMode.OUT.getCode());
                            break;
                        }
                    }
                }
            }
            finalPools.addAll(inPools);
            Set<Long> sourceContext = new HashSet<>(finalPools);
            finalPools.removeAll(outPools);
            for (Long poolId : inPools) {
                // 同批配套调出完成后不得留下限制或互斥双池
                validateRelations(poolId, true, finalPools, sourceContext, relations, pools);
            }
            for (Long poolId : outPools) {
                // 原调出申请仍受池关系约束，但不重新检查冻结期
                validateRelations(poolId, false, finalPools, sourceContext, relations, pools);
            }
        }
    }

    /** 查出目标池主动配置的调入互斥池，仅对已有持仓安排配套调出，不反向推断关系。 */
    private Set<Long> findMutexOutPools(Set<Long> inboundPools, Set<Long> currentPools,
                                        List<PoolRelationBo> relations) {
        Set<Long> result = new LinkedHashSet<>();
        for (PoolRelationBo relation : relations) {
            if (inboundPools.contains(relation.getPoolId()) && currentPools.contains(relation.getRelationPoolId())
                    && RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())) {
                result.add(relation.getRelationPoolId());
            }
        }
        return result;
    }

    /** 判断某个调入申请是否是本条互斥配套调出的来源。 */
    private boolean hasInMutexRelation(Long inboundPool, Long outboundPool, List<PoolRelationBo> relations) {
        for (PoolRelationBo relation : relations) {
            if (inboundPool.equals(relation.getPoolId()) && outboundPool.equals(relation.getRelationPoolId())
                    && RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())) {
                return true;
            }
        }
        return false;
    }

    /** 校验新增配套调出池存在且属于当前链路，配置仅加载一次。 */
    private void validateMutexPool(ConversionPlan plan, Map<Long, InvestmentPoolBo> pools, Long poolId) {
        if (!pools.containsKey(poolId)) {
            for (InvestmentPoolBo pool : tempSecurityCodeMapper.queryConversionPoolList(Collections.singletonList(poolId))) {
                pools.put(pool.getId(), pool);
            }
        }
        // 配套调出不重新要求市场准入，只检查池存在及类型
        validateMarket(plan, pools.get(poolId), false);
    }

    /** 提示使用投资池自身名称，复用已有配置；仅在相关池尚未加载时补查，不拼接层级全名。 */
    private String queryConversionPoolName(Map<Long, InvestmentPoolBo> pools, Long poolId) {
        if (!pools.containsKey(poolId)) {
            for (InvestmentPoolBo pool : tempSecurityCodeMapper.queryConversionPoolList(Collections.singletonList(poolId))) {
                pools.put(pool.getId(), pool);
            }
        }
        InvestmentPoolBo pool = pools.get(poolId);
        if (pool == null) {
            throw new BizException("关联投资池不存在，无法获取投资池名称");
        }
        return pool.getPoolName();
    }

    /** 校验目标池存在且类型匹配；实际新增入池时，逐码检查市场是否符合目标池配置。 */
    private void validateMarket(ConversionPlan plan, InvestmentPoolBo pool, boolean inbound) {
        if (pool == null) {
            throw new BizException("目标投资池不存在");
        }
        if (plan.crmw != "crmw".equals(pool.getPoolType())) {
            throw new BizException("目标投资池类型不符，投资池：" + pool.getPoolName());
        }
        if (inbound) {
            for (SecurityInfoBo member : plan.members) {
                if (!MarketCodeMatchUtil.matchesPoolMarkets(pool.getMarketCodes(), plan.markets.get(member.getWindCode()))) {
                    throw new BizException("正式代码市场不符合目标池，securityCode=" + member.getWindCode()
                            + "，投资池：" + pool.getPoolName());
                }
            }
        }
    }

    /** 校验调入/调出后的池关系是否满足来源池要求，以及限制、互斥关系。 */
    private void validateRelations(Long poolId, boolean inbound, Set<Long> finalPools,
                                    Set<Long> sourceContext, List<PoolRelationBo> relations,
                                    Map<Long, InvestmentPoolBo> pools) {
        Set<Long> sourcePools = new HashSet<>();
        for (PoolRelationBo relation : relations) {
            if (poolId.equals(relation.getPoolId())) {
                String type = relation.getRelationType();
                if (inbound && RelationType.SOURCE.getCode().equals(type)) {
                    sourcePools.add(relation.getRelationPoolId());
                }
                boolean blocked = inbound
                        ? RelationType.IN_RESTRICT.getCode().equals(type) || RelationType.IN_MUTEX.getCode().equals(type)
                        : RelationType.OUT_RESTRICT.getCode().equals(type) || RelationType.OUT_MUTEX.getCode().equals(type);
                if (blocked && finalPools.contains(relation.getRelationPoolId())) {
                    // 冲突双方均展示投资池名称，不将内部池 ID 暴露到业务提示
                    throw new BizException("正式代码整组转换存在池关系冲突，投资池：" + queryConversionPoolName(pools, poolId)
                            + "，关联投资池：" + queryConversionPoolName(pools, relation.getRelationPoolId()));
                }
            }
        }
        if (!sourcePools.isEmpty() && Collections.disjoint(sourcePools, sourceContext)) {
            // 目标池配置已加载，直接读取名称用于提示
            throw new BizException("正式代码整组转换不满足来源池，投资池：" + queryConversionPoolName(pools, poolId));
        }
    }

    /**
     * 6. 执行整组转换：先处理当前状态，再迁移原流程中申请；由调用方事务保证原子性。
     * 情况①无普通池业务时两个阶段均为空；情况⑤同时执行两个阶段，不能提前审批调出。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void convertOrdinaryPoolBusinessData(ConversionPlan plan, TempSecurityCodeBo mapping) {
        // 6.1 处理已在池的记录：临时代码出库、正式代码组入库，记录调库日志、快照和替换审计
        // 对应情况③/④/⑤/⑥；正式代码组已完整在池时，仅临时代码出库，避免重复入库
        convertActiveStates(plan, mapping);
        // 6.2 处理流程中的申请：临时代码替换成正式代码，联动调整同券不同市场的其他代码
        // 对应情况②/⑤；原调库日志换码、补关联日志和快照、记录替换审计，沿用原批次与审批
        convertPendingLogs(plan, mapping);
    }

    /** 准备 CRMW 标的整组转换计划，同一凭证单独核对在池状态、申请、市场和池关系。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareCrmwConversion(TempSecurityCodeBo mapping, ConversionPlan plan) {
        // 1. 查询临时标的已在池和流程中的 CRMW 记录；没有业务时不扩展标的代码
        List<IpPoolStatusBo> active = tempSecurityCodeMapper.queryActiveCrmwPoolStatusList(mapping);
        List<IpAdjustLogBo> pending = tempSecurityCodeMapper.queryCrmwPendingLogList(mapping);
        if (active.isEmpty() && pending.isEmpty()) {
            return;
        }
        // 2. 与普通池共用同一正式代码组；仅有 CRMW 业务时在此查询
        List<String> codes = new ArrayList<>();
        if (plan.members.isEmpty()) {
            // 查询并检查正式标的主数据，保存每个代码的市场
            codes = loadFormalGroup(plan, mapping.getSecurityCode());
        } else {
            for (SecurityInfoBo member : plan.members) {
                codes.add(member.getWindCode());
            }
        }
        // 3. 按凭证分组，避免同一标的在不同凭证下相互去重或覆盖申请
        for (IpPoolStatusBo state : active) {
            // 为当前凭证建立独立计划并保留临时标的在池记录
            crmwPlanForCredential(plan, state.getCrmwScode(), state.getCrmwStype()).active.add(state);
        }
        for (IpAdjustLogBo log : pending) {
            // 为当前凭证保存原流程中申请，不创建新审批
            crmwPlanForCredential(plan, log.getCrmwScode(), log.getCrmwStype()).pending.add(log);
        }
        List<PoolRelationBo> relations = securityPoolAdjustMapper.queryAllPoolRelationList();
        // 4. 每个凭证分别检查整组在池、流程中冲突、市场和池关系
        for (ConversionPlan group : plan.crmwPlans.values()) {
            Set<Long> poolIds = new LinkedHashSet<>();
            for (IpPoolStatusBo state : group.active) {
                poolIds.add(state.getTargetPoolId());
            }
            for (IpAdjustLogBo log : group.pending) {
                poolIds.add(log.getTargetPoolId());
            }
            Map<Long, InvestmentPoolBo> pools = new HashMap<>();
            for (InvestmentPoolBo pool : tempSecurityCodeMapper.queryConversionPoolList(new ArrayList<>(poolIds))) {
                pools.put(pool.getId(), pool);
            }
            // 同一凭证下正式标的必须整组在池或整组不在池，其他凭证的记录不参与
            readExistingGroupState(group, codes, new ArrayList<>(poolIds), relations);
            // 复用普通池的状态、互斥配套调出、调入市场和池关系校验，使用 CRMW 专属快照表
            validateConversionPlan(group, pools, relations);
            for (IpPoolStatusBo state : group.activeMutexOut) {
                poolIds.add(state.getTargetPoolId());
            }
            for (IpAdjustLogBo out : group.pendingMutexOut) {
                poolIds.add(out.getTargetPoolId());
            }
            for (IpAdjustLogBo conflict : tempSecurityCodeMapper.queryFormalGroupCrmwPendingLogList(
                    codes, group.crmwScode, group.crmwStype)) {
                if (poolIds.contains(conflict.getTargetPoolId())) {
                    throw new BizException("正式代码在CRMW库存在流程中数据，crmwScode=" + group.crmwScode
                            + "，securityCode=" + conflict.getSecurityCode() + "，投资池：" + conflict.getTargetPoolName());
                }
            }
        }
    }

    /** 为同一凭证创建独立转换计划，复用正式代码组与本次即时转换批次。 */
    private ConversionPlan crmwPlanForCredential(ConversionPlan plan, String code, String type) {
        if (code == null || code.trim().isEmpty() || !"crmw".equals(type)) {
            throw new BizException("CRMW凭证信息不完整，crmwScode=" + code + "，crmwStype=" + type);
        }
        String key = code + "|" + type;
        ConversionPlan group = plan.crmwPlans.get(key);
        if (group == null) {
            group = new ConversionPlan();
            group.crmw = true;
            group.singleCode = plan.singleCode;
            group.crmwScode = code;
            group.crmwStype = type;
            group.active = new ArrayList<>();
            group.pending = new ArrayList<>();
            group.members.addAll(plan.members);
            group.markets.putAll(plan.markets);
            group.batchNo = plan.batchNo;
            plan.crmwPlans.put(key, group);
        }
        return group;
    }

    /** 6.1 处理已在池的记录：临时代码出库、正式代码组入库，已完整在池时仅清理临时代码。 */
    private void convertActiveStates(ConversionPlan plan, TempSecurityCodeBo mapping) {
        // 6.1.1 先执行正式代码组的互斥配套调出，再执行临时码出库和正式码入库，同一事务提交
        convertActiveMutexOut(plan, mapping);
        for (IpPoolStatusBo active : plan.active) {
            IpAdjustLogBo out = new IpAdjustLogBo();
            BeanUtils.copyProperties(active, out);
            out.setId(null);
            out.setAdjustLogId(null);
            out.setAdjustMode(AdjustMode.OUT.getCode());
            out.setAdjustType(plan.singleCode ? "Wind代码变更调出" : ADJUST_TYPE_TEMP_OUT);
            out.setAdjusterId(SYSTEM_ADJUSTER_ID);
            out.setAdjusterName(SYSTEM_ADJUSTER_NAME);
            out.setAdjustBatchNo(plan.batchNo);
            out.setFlowId(null);
            out.setFlowKey(null);
            out.setFlowType(null);
            out.setSubmitTime(mapping.getUpdateTime());
            out.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(plan.singleCode ? "Wind代码变更调出" : REASON_TEMP_OUT,
                    (plan.singleCode ? "旧代码：" : "临时代码：") + mapping.getTempSecurityCode(),
                    (plan.singleCode ? "新代码：" : "正式代码：") + mapping.getSecurityCode()));
            out.setAdjustAdvice(TempOprtSource.JOB.getCode().equals(mapping.getOprtSource()) ? null : out.getAdjustReason());
            if (securityPoolAdjustMapper.addAdjustLog(out) != 1) {
                throw new BizException("临时代码调出日志写入失败，投资池：" + active.getTargetPoolName());
            }
            int deleted = plan.crmw
                    ? tempSecurityCodeMapper.deleteCrmwPoolStatusSoftById(active.getId(), mapping.getUpdateTime())
                    : tempSecurityCodeMapper.deleteOrdinaryPoolStatusForConversion(active.getId(), mapping.getUpdateTime());
            if (deleted != 1) {
                throw new BizException("临时代码在池状态已变化，投资池：" + active.getTargetPoolName());
            }
            // 记录实际失效的临时池状态
            addAudit(mapping, plan.crmw ? "ip_pool_status_crmw" : "ip_pool_status", active.getId());
            if (plan.existingPools.contains(active.getTargetPoolId())) {
                continue;
            }
            AdjustSecuritySnapshotBo source = plan.crmw || active.getAdjustLogId() == null ? null
                    : tempSecurityCodeMapper.queryConversionSnapshot(active.getAdjustLogId());
            AdjustSecuritySnapshotCrmwBo crmwSource = !plan.crmw || active.getAdjustLogId() == null ? null
                    : tempSecurityCodeMapper.queryCrmwConversionSnapshot(active.getAdjustLogId());
            for (SecurityInfoBo member : plan.members) {
                IpAdjustLogBo in = new IpAdjustLogBo();
                BeanUtils.copyProperties(active, in);
                in.setId(null);
                in.setAdjustLogId(null);
                in.setSecurityCode(member.getWindCode());
                in.setSecurityShortName(member.getShortName());
                in.setSecurityType(member.getSecurityType());
                in.setAdjustMode(AdjustMode.IN.getCode());
                if (!mapping.getSecurityCode().equals(member.getWindCode())) {
                    // 复用普通证券调库的关联记录构建
                    in = RelatedMarketAdjustHelper.buildRelatedLog(in, member.getWindCode(), member.getShortName(), member.getSecurityType());
                }
                in.setAdjustBatchNo(plan.batchNo);
                in.setAdjusterId(SYSTEM_ADJUSTER_ID);
                in.setAdjusterName(SYSTEM_ADJUSTER_NAME);
                in.setFlowId(null);
                in.setFlowKey(null);
                in.setFlowType(null);
                in.setSubmitTime(mapping.getUpdateTime());
                in.setAuditStatus(AuditStatus.APPROVED.getCode());
                in.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(active.getAdjustReason() == null
                        ? REASON_FORMAL_IN : active.getAdjustReason(), "代码替换：" + mapping.getTempSecurityCode()
                        + "→" + mapping.getSecurityCode(), "本条正式代码：" + member.getWindCode()));
                in.setAdjustAdvice(TempOprtSource.JOB.getCode().equals(mapping.getOprtSource()) ? null : in.getAdjustReason());
                if (securityPoolAdjustMapper.addAdjustLog(in) != 1) {
                    throw new BizException("正式代码调入日志写入失败，securityCode=" + member.getWindCode());
                }
                in.setAdjustLogId(in.getId());
                int entered = plan.crmw ? crmwPoolAdjustMapper.addPoolStatus(in) : securityPoolAdjustMapper.addPoolStatus(in);
                if (entered != 1) {
                    throw new BizException("正式代码入池失败，securityCode=" + member.getWindCode());
                }
                if (plan.crmw) {
                    // 同一凭证下逐市场保存 CRMW 标的证券快照
                    saveCrmwSnapshot(in, crmwSource, member, false, mapping);
                } else {
                    // 普通池新日志独立保存证券快照
                    saveSnapshot(in, source, member, false, mapping);
                }
                // 审计中正式代码使用本条实际市场代码
                addMemberAudit(mapping, member, "ip_adjust_log", in.getId());
            }
        }
    }

    /** 6.2 处理流程中的申请：临时代码替换成正式代码，联动调整同券其他市场代码，沿用原审批。 */
    private void convertPendingLogs(ConversionPlan plan, TempSecurityCodeBo mapping) {
        for (IpAdjustLogBo original : plan.pending) {
            AdjustSecuritySnapshotBo source = plan.snapshots.get(original.getId());
            for (SecurityInfoBo member : plan.members) {
                boolean selected = mapping.getSecurityCode().equals(member.getWindCode());
                IpAdjustLogBo log = new IpAdjustLogBo();
                BeanUtils.copyProperties(original, log);
                if (!selected) {
                    // 关联项共享原申请批次与流程，不创建独立审批步骤
                    log = RelatedMarketAdjustHelper.buildRelatedLog(original, member.getWindCode(), member.getShortName(), member.getSecurityType());
                    if (TempOprtSource.JOB.getCode().equals(mapping.getOprtSource())) {
                        log.setAdjustAdvice(null);
                    }
                }
                log.setSecurityCode(member.getWindCode());
                log.setSecurityShortName(member.getShortName());
                log.setSecurityType(member.getSecurityType());
                log.setUpdtTime(mapping.getUpdateTime());
                log.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(original.getAdjustReason(),
                        "代码替换：" + mapping.getTempSecurityCode() + "→" + mapping.getSecurityCode(),
                        "本条正式代码：" + member.getWindCode()));
                int written = selected ? tempSecurityCodeMapper.editConversionPendingLog(log, mapping.getTempSecurityCode())
                        : securityPoolAdjustMapper.addAdjustLog(log);
                if (written != 1) {
                    throw new BizException("在途调库转换失败，securityCode=" + member.getWindCode());
                }
                if (plan.crmw) {
                    // CRMW 原位更新主项快照，关联代码保留原凭证并复制业务信息
                    saveCrmwSnapshot(log, plan.crmwSnapshots.get(original.getId()), member, selected, mapping);
                } else {
                    // 普通池原位更新主项快照，其余代码保存独立快照
                    saveSnapshot(log, source, member, selected, mapping);
                }
                // 每个市场代码独立记录替换审计
                addMemberAudit(mapping, member, "ip_adjust_log", log.getId());
            }
        }
        // 6.2.1 补本次转正新增的互斥配套调出，原互斥池持仓继续保留到审批通过
        convertPendingMutexOut(plan, mapping);
    }

    /** 已在池转换的互斥配套调出：整组即时生效，逐代码保存系统调库日志、快照及审计。 */
    private void convertActiveMutexOut(ConversionPlan plan, TempSecurityCodeBo mapping) {
        for (IpPoolStatusBo state : plan.activeMutexOut) {
            IpAdjustLogBo out = new IpAdjustLogBo();
            BeanUtils.copyProperties(state, out);
            out.setId(null);
            out.setAdjustLogId(null);
            out.setAdjustMode(AdjustMode.OUT.getCode());
            out.setAdjustType(mapping.getSecurityCode().equals(state.getSecurityCode())
                    ? ADJUST_TYPE_MUTEX : RelatedMarketAdjustHelper.ADJUST_TYPE);
            out.setAdjustBatchNo(plan.batchNo);
            out.setAdjusterId(SYSTEM_ADJUSTER_ID);
            out.setAdjusterName(SYSTEM_ADJUSTER_NAME);
            out.setFlowId(null);
            out.setFlowKey(null);
            out.setFlowType(null);
            out.setSubmitTime(mapping.getUpdateTime());
            out.setAdjustReason(ScheduledAdjustLogHelper.appendDetails("临时代码转正互斥配套调出",
                    "代码替换：" + mapping.getTempSecurityCode() + "→" + mapping.getSecurityCode(),
                    "本条正式代码：" + state.getSecurityCode(), "互斥配套调出池：" + state.getTargetPoolName()));
            out.setAdjustAdvice(TempOprtSource.JOB.getCode().equals(mapping.getOprtSource()) ? null : out.getAdjustReason());
            if (securityPoolAdjustMapper.addAdjustLog(out) != 1) {
                throw new BizException("互斥配套调出日志写入失败，securityCode=" + state.getSecurityCode());
            }
            int deleted = plan.crmw
                    ? tempSecurityCodeMapper.deleteCrmwPoolStatusSoftById(state.getId(), mapping.getUpdateTime())
                    : tempSecurityCodeMapper.deleteOrdinaryPoolStatusForConversion(state.getId(), mapping.getUpdateTime());
            if (deleted != 1) {
                throw new BizException("互斥配套调出失败，securityCode=" + state.getSecurityCode());
            }
            for (SecurityInfoBo member : plan.members) {
                if (member.getWindCode().equals(state.getSecurityCode())) {
                    if (plan.crmw) {
                        AdjustSecuritySnapshotCrmwBo source = state.getAdjustLogId() == null ? null
                                : tempSecurityCodeMapper.queryCrmwConversionSnapshot(state.getAdjustLogId());
                        // 互斥调出保留该正式代码原业务快照
                        saveCrmwSnapshot(out, source, member, false, mapping);
                    } else {
                        AdjustSecuritySnapshotBo source = state.getAdjustLogId() == null ? null
                                : tempSecurityCodeMapper.queryConversionSnapshot(state.getAdjustLogId());
                        // 普通池互斥调出记录独立快照
                        saveSnapshot(out, source, member, false, mapping);
                    }
                    // 逐市场记录实际失效的持仓与新增调出日志
                    addMemberAudit(mapping, member, plan.crmw ? "ip_pool_status_crmw" : "ip_pool_status", state.getId());
                    // 保留互斥配套调出的新增日志审计
                    addMemberAudit(mapping, member, "ip_adjust_log", out.getId());
                    break;
                }
            }
        }
    }

    /** 流程中转换的互斥配套调出：沿用原调入审批，只新增各市场的调出日志和快照。 */
    private void convertPendingMutexOut(ConversionPlan plan, TempSecurityCodeBo mapping) {
        for (IpAdjustLogBo source : plan.pendingMutexOut) {
            for (SecurityInfoBo member : plan.members) {
                // 复用关联项构建；选中代码保留互斥调整类型，其他代码为关联调整
                IpAdjustLogBo out = RelatedMarketAdjustHelper.buildRelatedLog(source,
                        member.getWindCode(), member.getShortName(), member.getSecurityType());
                if (mapping.getSecurityCode().equals(member.getWindCode())) {
                    out.setAdjustType(ADJUST_TYPE_MUTEX);
                }
                out.setUpdtTime(mapping.getUpdateTime());
                out.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(source.getAdjustReason(),
                        "代码替换：" + mapping.getTempSecurityCode() + "→" + mapping.getSecurityCode(),
                        "本条正式代码：" + member.getWindCode()));
                if (TempOprtSource.JOB.getCode().equals(mapping.getOprtSource())) {
                    out.setAdjustAdvice(null);
                }
                if (securityPoolAdjustMapper.addAdjustLog(out) != 1) {
                    throw new BizException("流程中互斥配套调出写入失败，securityCode=" + member.getWindCode());
                }
                if (plan.crmw) {
                    // 快照沿用原调入申请的信息和凭证，不复制审批步骤
                    saveCrmwSnapshot(out, plan.crmwSnapshots.get(source.getId()), member, false, mapping);
                } else {
                    // 快照来源仍是原申请 ID，避免关联项误用新日志 ID
                    saveSnapshot(out, plan.snapshots.get(source.getId()), member, false, mapping);
                }
                // 每条互斥配套调出独立记录替换审计
                addMemberAudit(mapping, member, "ip_adjust_log", out.getId());
            }
        }
    }

    /** 7. 保存本次实际证券身份，来源快照中的业务填写信息保持不变。 */
    private void saveSnapshot(IpAdjustLogBo log, AdjustSecuritySnapshotBo source, SecurityInfoBo member,
                              boolean replace, TempSecurityCodeBo mapping) {
        AdjustSecuritySnapshotBo snapshot = new AdjustSecuritySnapshotBo();
        BeanUtils.copyProperties(source == null ? member : source, snapshot);
        snapshot.setId(replace && source != null ? source.getId() : null);
        snapshot.setAdjustLogId(log.getId());
        snapshot.setWindCode(member.getWindCode());
        snapshot.setFullName(member.getFullName());
        snapshot.setShortName(member.getShortName());
        snapshot.setSecurityType(member.getSecurityType());
        snapshot.setUpdtTime(mapping.getUpdateTime());
        if (replace && source != null) {
            if (tempSecurityCodeMapper.editConversionSnapshotIdentity(snapshot) != 1) {
                throw new BizException("在途证券快照更新失败，logId=" + log.getId());
            }
        } else {
            snapshot.setSubmitterId(log.getAdjusterId());
            snapshot.setSubmitTime(log.getSubmitTime());
            snapshot.setCrteTime(mapping.getUpdateTime());
            snapshot.setIsDeleted(0);
            if (securityPoolAdjustMapper.addAdjustSecuritySnapshot(snapshot) != 1) {
                throw new BizException("转正证券快照写入失败，logId=" + log.getId());
            }
        }
    }

    /** 8. 为本条正式代码建立审计参数，避免污染后续成员的主映射。 */
    private void addMemberAudit(TempSecurityCodeBo mapping, SecurityInfoBo member, String table, Long id) {
        TempSecurityCodeBo audit = new TempSecurityCodeBo();
        BeanUtils.copyProperties(mapping, audit);
        audit.setSecurityCode(member.getWindCode());
        audit.setSecurityName(member.getShortName());
        audit.setSecurityType(member.getSecurityType());
        audit.setSecurityMarket(securityMarketService.queryMarketCode(member.getWindCode()));
        // 沿用现有替换日志结构保存逐记录审计
        addAudit(audit, table, id);
    }

    /** 写入本次转正实际影响的业务记录。 */
    private void addAudit(TempSecurityCodeBo mapping, String table, Long id) {
        mapping.setReplaceStatus("success");
        mapping.setReplaceTableName(table);
        mapping.setReplaceRecordId(id);
        tempSecurityCodeMapper.addTempSecurityCodeUpdateLog(mapping);
    }

    /**
     * 7. CRMW 整组转换：与普通池一致，先处理当前状态，再迁移流程中申请。
     * 每个凭证分别处理六种情况，逐码记录并沿用原审批；由调用方事务保证原子性。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void convertCrmwBusinessData(ConversionPlan plan, TempSecurityCodeBo mapping) {
        for (ConversionPlan group : plan.crmwPlans.values()) {
            // 7.1 处理已在池的记录：同凭证下临时标的出库、正式代码组入库，记录日志、快照和审计
            // 对应情况③/④/⑤/⑥；正式代码组已完整在池时，仅临时标的出库，避免重复入库
            convertActiveStates(group, mapping);
            // 7.2 处理流程中的申请：临时标的替换成正式代码，联动调整同券不同市场的其他代码
            // 对应情况②/⑤；补同凭证关联日志和快照、记录替换审计，沿用原批次与审批
            convertPendingLogs(group, mapping);
        }
        // 7.3 保留旧的凭证字段替换，仅处理 crmw_* 字段匹配临时代码的记录
        replaceCrmwCredentialReferences(mapping);
    }

    /** CRMW 凭证字段的旧替换分支，不对凭证自身扩展多市场。 */
    private void replaceCrmwCredentialReferences(TempSecurityCodeBo mapping) {
        List<Long> pendingIds = tempSecurityCodeMapper.queryPendingAdjustLogCrmwReferenceIdList(mapping);
        if (!pendingIds.isEmpty()) {
            tempSecurityCodeMapper.editAdjustLogCrmwReference(mapping, pendingIds);
            // 保存流程中记录凭证字段的替换审计
            addReplaceLogList(mapping, "ip_adjust_log.crmw", pendingIds);
        }
        List<Long> poolIds = tempSecurityCodeMapper.queryPoolStatusCrmwReferenceIdList(mapping);
        if (!poolIds.isEmpty()) {
            tempSecurityCodeMapper.editPoolStatusCrmwReference(mapping, poolIds);
            // 保存普通池当前记录凭证字段的替换审计
            addReplaceLogList(mapping, "ip_pool_status.crmw", poolIds);
        }
        List<Long> crmwIds = tempSecurityCodeMapper.queryCrmwPoolStatusCrmwReferenceIdList(mapping);
        if (!crmwIds.isEmpty()) {
            tempSecurityCodeMapper.editCrmwPoolStatusCrmwReference(mapping, crmwIds);
            // 保存 CRMW 池当前记录凭证字段的替换审计
            addReplaceLogList(mapping, "ip_pool_status_crmw.crmw", crmwIds);
        }
    }

    /** 逐市场保存 CRMW 标的快照，保留原凭证及原申请填写的业务信息。 */
    private void saveCrmwSnapshot(IpAdjustLogBo log, AdjustSecuritySnapshotCrmwBo source, SecurityInfoBo member,
                                  boolean replace, TempSecurityCodeBo mapping) {
        AdjustSecuritySnapshotCrmwBo snapshot = new AdjustSecuritySnapshotCrmwBo();
        BeanUtils.copyProperties(source == null ? member : source, snapshot);
        snapshot.setId(replace && source != null ? source.getId() : null);
        snapshot.setAdjustLogId(log.getId());
        snapshot.setWindCode(member.getWindCode());
        snapshot.setFullName(member.getFullName());
        snapshot.setShortName(member.getShortName());
        snapshot.setSecurityType(member.getSecurityType());
        snapshot.setCrmwName(log.getCrmwName());
        snapshot.setCrmwScode(log.getCrmwScode());
        snapshot.setCrmwMktcode(log.getCrmwMktcode());
        snapshot.setCrmwStype(log.getCrmwStype());
        snapshot.setUpdtTime(mapping.getUpdateTime());
        if (replace && source != null) {
            if (tempSecurityCodeMapper.editCrmwConversionSnapshotIdentity(snapshot) != 1) {
                throw new BizException("CRMW流程中证券快照更新失败，logId=" + log.getId());
            }
        } else {
            snapshot.setSubmitterId(log.getAdjusterId());
            snapshot.setSubmitTime(log.getSubmitTime());
            snapshot.setCrteTime(mapping.getUpdateTime());
            snapshot.setIsDeleted(0);
            if (crmwPoolAdjustMapper.addAdjustSecuritySnapshotCrmw(snapshot) != 1) {
                throw new BizException("CRMW转正证券快照写入失败，logId=" + log.getId());
            }
        }
    }

    /**
     * 批量写入替换日志
     */
    private void addReplaceLogList(TempSecurityCodeBo replaceBo, String tableName, List<Long> recordIds) {
        for (Long recordId : recordIds) {
            replaceBo.setReplaceTableName(tableName);
            replaceBo.setReplaceRecordId(recordId);
            tempSecurityCodeMapper.addTempSecurityCodeUpdateLog(replaceBo);
        }
    }

    /** 临时代码转正计划：保存普通池业务，以及按凭证分别校验的 CRMW 计划。 */
    public static final class ConversionPlan {
        /** Wind 身份更名仅转换事件指定的新代码。 */
        private boolean singleCode;
        /** 当前计划是否为单个 CRMW 凭证的标的转换。 */
        private boolean crmw;
        /** 当前分组的 CRMW 凭证代码。 */
        private String crmwScode;
        /** 当前分组的 CRMW 凭证类型。 */
        private String crmwStype;
        /** 每个凭证各自的在池及流程中转换计划。 */
        private final Map<String, ConversionPlan> crmwPlans = new LinkedHashMap<>();
        /** CRMW 原流程中业务快照，使用独立快照表。 */
        private final Map<Long, AdjustSecuritySnapshotCrmwBo> crmwSnapshots = new HashMap<>();
        /** 当前计划对应的原临时证券在池记录。 */
        private List<IpPoolStatusBo> active = Collections.emptyList();
        /** 当前计划对应的原临时证券流程中申请。 */
        private List<IpAdjustLogBo> pending = Collections.emptyList();
        /** 本次正式证券组，首项为用户选择的代码。 */
        private final List<SecurityInfoBo> members = new ArrayList<>();
        /** 每个正式代码的权威市场。 */
        private final Map<String, String> markets = new HashMap<>();
        /** 当前计划中原先已完整持有正式组的池。 */
        private final Set<Long> existingPools = new HashSet<>();
        /** 正式代码组原持仓，供互斥配套调出逐码使用。 */
        private final Map<Long, List<IpPoolStatusBo>> existingStates = new HashMap<>();
        /** 已在池转换需即时调出的正式代码组持仓。 */
        private final List<IpPoolStatusBo> activeMutexOut = new ArrayList<>();
        /** 流程中转换需追加的互斥调出模板，ID 保留原调入申请作为快照来源。 */
        private final List<IpAdjustLogBo> pendingMutexOut = new ArrayList<>();
        /** 原普通池流程中业务快照。 */
        private final Map<Long, AdjustSecuritySnapshotBo> snapshots = new HashMap<>();
        /** 本次即时转换共用批次，不影响流程中审批批次。 */
        private String batchNo = UUID.randomUUID().toString().replace("-", "");

    }
}
