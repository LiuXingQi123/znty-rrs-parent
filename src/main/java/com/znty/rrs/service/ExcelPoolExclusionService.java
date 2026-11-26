package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.mapper.ExcelPoolExclusionMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 判断定时调库应避开哪些 Excel 人工调整记录
 *
 * 按主体或证券、目标池汇总最近一次生效的 Excel 导入或清空决定，提供自动补入和跟随出池等场景使用
 * 优先按审核时间比较，缺少审核时间时使用创建时间，时间相同时用调库日志 ID 确认先后
 */
@Service
public class ExcelPoolExclusionService {

    /** 批量读取已生效的 Excel 调库流水和受保护池状态 */
    @Resource
    private ExcelPoolExclusionMapper excelPoolExclusionMapper;

    /**
     * 批量读取受 Excel 保护的当前在池记录 ID
     *
     * 执行顺序：
     * 1. 指定空的池范围时直接返回空集合
     * 2. 按范围查询 Excel 入池记录，根据任务需要计入主体跟随债券
     *
     * @param poolIds 池范围，为 null 时查询所有池
     * @param includeCompanyFollow 是否包含 Excel 主体调入后由定时任务补入的债券
     * @return 受保护的在池记录 ID
     */
    Set<Long> loadProtectedStatusIds(Collection<Long> poolIds, boolean includeCompanyFollow) {
        if (poolIds != null && poolIds.isEmpty()) {
            return Collections.emptySet();
        }
        return excelPoolExclusionMapper.queryExcelProtectedPoolStatusIds(poolIds, includeCompanyFollow);
    }

    /**
     * 批量读取受 Excel 入池保护的 CRMW 组合状态
     *
     * 执行顺序：
     * 1. 指定空的池范围时直接返回空集合
     * 2. 按范围读取已生效的 Excel 导入及同批配套组合
     *
     * @param poolIds 池范围，为 null 时查询全部 CRMW 池
     * @return 受保护的 CRMW 组合状态 ID
     */
    Set<Long> loadProtectedCrmwStatusIds(Collection<Long> poolIds) {
        if (poolIds != null && poolIds.isEmpty()) {
            return Collections.emptySet();
        }
        return excelPoolExclusionMapper.queryExcelProtectedCrmwStatusIds(poolIds);
    }

    /**
     * 识别本轮增量候选中由 Excel 导入产生的调库流水
     *
     * 执行顺序：
     * 1. 无候选时直接返回空集合
     * 2. 批量识别候选的导入、同批配套及主体跟随来源
     *
     * @param adjustLogIds 本轮候选流水 ID
     * @return 候选中应按 Excel 来源排除的调库流水 ID
     */
    Set<Long> loadExcelOriginAdjustLogIds(Collection<Long> adjustLogIds) {
        if (adjustLogIds.isEmpty()) {
            return Collections.emptySet();
        }
        return excelPoolExclusionMapper.queryExcelOriginAdjustLogIds(adjustLogIds);
    }

    /**
     * 读取本轮主体的 Excel 调整决定
     *
     * 执行顺序：
     * 1. 批量读取生效流水，包含同批联动和互斥
     * 2. 按主体和池保留最新一次决定
     *
     * @param poolIds 池范围，为 null 时不限制池
     * @param issuerCodes 主体范围，为 null 时不限制主体
     * @return 本轮各主体在各池的最新 Excel 调整决定
     */
    CompanyDecisions loadCompanyDecisions(Collection<Long> poolIds, Collection<String> issuerCodes) {
        List<IpAdjustLogBo> actions = excelPoolExclusionMapper.queryExcelCompanyActions(poolIds, issuerCodes);
        return new CompanyDecisions(actions);
    }

    /**
     * 按在池记录 ID 排除受保护的候选，保留同券的其他入池依据
     *
     * 执行顺序：
     * 1. 从候选中剔除 ID 命中保护集合的记录
     *
     * @param candidates 定时任务候选
     * @param protectedStatusIds 受保护的在池记录 ID
     * @return 排除保护记录后的候选
     */
    static List<ScheduledAdjustCandidateDto> excludeProtectedRows(List<ScheduledAdjustCandidateDto> candidates,
                                                                  Set<Long> protectedStatusIds) {
        if (candidates == null || protectedStatusIds.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> candidate == null || !protectedStatusIds.contains(candidate.getPoolStatusId()))
                .collect(Collectors.toList());
    }

    /**
     * 保存各主体在各目标池的最新生效 Excel 决定
     *
     * 优先按审核时间比较，缺少审核时间时使用创建时间，再以日志 ID 区分先后
     * 供后续判断主体旗下债券能否随主体自动入池或出池
     */
    static final class CompanyDecisions {

        /** 按审核时间、创建时间回退和日志 ID 比较生效先后 */
        private static final Comparator<IpAdjustLogBo> EFFECTIVE_ORDER = Comparator
                .comparing(CompanyDecisions::effectiveTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(IpAdjustLogBo::getId);

        /** 按主体和投资池记录最近生效的 Excel 调整 */
        private final Map<String, Map<Long, IpAdjustLogBo>> latestActionsByIssuerAndPool = new HashMap<>();

        /**
         * 汇总 Excel 调库可复用的主体历史结论
         *
         * 执行顺序：
         * 1. 按主体代码和目标池归集已生效的调库记录，每组只保留执行顺序最新的一条
         *
         * @param actions 用于判断最新结论的主体调库记录
         */
        private CompanyDecisions(List<IpAdjustLogBo> actions) {
            for (IpAdjustLogBo action : actions) {
                Map<Long, IpAdjustLogBo> actionsByPool = latestActionsByIssuerAndPool
                        .computeIfAbsent(action.getSecurityCode(), issuerCode -> new HashMap<>());
                Long poolId = action.getTargetPoolId();
                IpAdjustLogBo latestAction = actionsByPool.get(poolId);
                if (latestAction == null || EFFECTIVE_ORDER.compare(action, latestAction) > 0) {
                    actionsByPool.put(poolId, action);
                }
            }
        }

        /**
         * 筛选旗下普通债的跟随入池候选
         *
         * 执行顺序：
         * 1. 来源池或目标池已通过 Excel 调出主体时跳过
         * 2. 来源池由 Excel 调入主体时直接保留，其余按到期门槛筛选
         *
         * @param candidates 普通债入池候选
         * @param sourcePoolId 主体来源池
         * @param targetPoolId 债券目标池
         * @param cutoff 普通债摘牌日截止日期
         * @return 可继续检查入池的候选，候选未传入时返回空列表
         */
        List<ScheduledAdjustCandidateDto> filterInbound(List<ScheduledAdjustCandidateDto> candidates,
                                                        Long sourcePoolId, Long targetPoolId, String cutoff) {
            if (candidates == null) {
                return Collections.emptyList();
            }
            List<ScheduledAdjustCandidateDto> result = new ArrayList<>();
            for (ScheduledAdjustCandidateDto bond : candidates) {
                if (bond == null) {
                    result.add(null);
                    continue;
                }
                String issuerCode = bond.getIssuerCode();
                // 先拦截明确调出主体的来源池和目标池
                if (hasExcelDecision(issuerCode, sourcePoolId, AdjustMode.OUT)
                        // 使用主体和目标池的历史结论判断是否保留这条记录
                        || hasExcelDecision(issuerCode, targetPoolId, AdjustMode.OUT)) {
                    continue;
                }
                // Excel 调入的主体可以跳过普通到期门槛
                boolean excelCompanyIn = hasExcelDecision(issuerCode, sourcePoolId, AdjustMode.IN);
                bond.setExcelCompanyAdjustment(excelCompanyIn);
                if (excelCompanyIn || CompanyBondSyncPolicy.meetsDelistCutoff(bond.getDelistDate(), cutoff)) {
                    result.add(bond);
                }
            }
            return result;
        }

        /**
         * 筛选旗下普通债的跟随出池候选
         *
         * 执行顺序：
         * 1. 查清来源池是否有 Excel 调出决定，以及主体是否还有有效留池依据
         * 2. 普通跟随出池跳过不适用的证券，Excel 调出按其优先级继续处理
         * 3. 目标池明确调出或已没有任何有效来源池时，保留出池候选
         *
         * @param candidates 普通债出池候选
         * @param bondPoolId 债券所在池
         * @param sourcePoolIds 主体来源池范围，包含债券所在池
         * @param activeCompanyPools 每个主体当前所在的池
         * @return 可继续检查出池的候选
         */
        List<ScheduledAdjustCandidateDto> filterOutbound(List<ScheduledAdjustCandidateDto> candidates, Long bondPoolId,
                                                         Set<Long> sourcePoolIds, Map<String, Set<Long>> activeCompanyPools) {
            List<ScheduledAdjustCandidateDto> result = new ArrayList<>();
            for (ScheduledAdjustCandidateDto bond : candidates) {
                if (bond == null) {
                    continue;
                }
                String issuerCode = bond.getIssuerCode();
                Set<Long> activePools = activeCompanyPools.getOrDefault(issuerCode, Collections.emptySet());
                boolean excelCompanyOut = false;
                boolean hasActiveSource = false;
                for (Long sourcePoolId : sourcePoolIds) {
                    // Excel 明确调出的来源池不能再作为留池依据
                    if (hasExcelDecision(issuerCode, sourcePoolId, AdjustMode.OUT)) {
                        excelCompanyOut = true;
                    } else if (activePools.contains(sourcePoolId)) {
                        hasActiveSource = true;
                    }
                }
                if (bond.isInactiveForOrdinaryOut() && !excelCompanyOut) {
                    continue;
                }
                // 债券所在池的 Excel 调出优先于其他池的留池依据
                if (hasActiveSource && !hasExcelDecision(issuerCode, bondPoolId, AdjustMode.OUT)) {
                    continue;
                }
                bond.setExcelCompanyAdjustment(excelCompanyOut);
                result.add(bond);
            }
            return result;
        }

        /**
         * 汇总主体明确通过 Excel 调出的池，供补债联动项复核
         *
         * 执行顺序：
         * 1. 在该主体各个池的最新决定中筛出调出项
         *
         * @param issuerCode 主体代码
         * @return 应拦截补债的池 ID
         */
        Set<Long> outPools(String issuerCode) {
            Set<Long> result = new HashSet<>();
            Map<Long, IpAdjustLogBo> actionsByPool = latestActionsByIssuerAndPool
                    .getOrDefault(issuerCode, Collections.emptyMap());
            actionsByPool.forEach((poolId, action) -> {
                if (AdjustMode.OUT.getCode().equals(action.getAdjustMode())) {
                    result.add(poolId);
                }
            });
            return result;
        }

        /**
         * 判断该主体在指定池的最新 Excel 决定是否为指定方向
         *
         * 执行顺序：
         * 1. 按主体和池查找最近生效的 Excel 调库日志，再比较该日志记录的调整方向
         *
         * @param issuerCode 主体代码
         * @param poolId 池 ID
         * @param mode 指定的调整方向
         * @return true=最新生效决定为指定方向，false=没有生效决定或方向不同
         */
        boolean hasExcelDecision(String issuerCode, Long poolId, AdjustMode mode) {
            Map<Long, IpAdjustLogBo> actionsByPool = latestActionsByIssuerAndPool
                    .getOrDefault(issuerCode, Collections.emptyMap());
            IpAdjustLogBo action = actionsByPool.get(poolId);
            return action != null && mode.getCode().equals(action.getAdjustMode());
        }

        /**
         * 取得用于比较 Excel 流水先后的时间
         *
         * 执行顺序：
         * 1. 优先取审核时间，缺失时取创建时间
         *
         * @param action 已生效的主体调整流水
         * @return 流水用于排序的时间
         */
        private static Date effectiveTime(IpAdjustLogBo action) {
            return action.getAuditTime() == null ? action.getCrteTime() : action.getAuditTime();
        }
    }
}
