package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.mapper.AutoAdjustMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 黑名单质押库三条件校验
 *
 * 供人工调库和债券定时同步判断发行主体是否满足黑名单质押库条件
 */
@Service
public class PledgeBlacklistRuleService {

    /** 债券禁止库 */
    public static final Long FORBIDDEN_POOL_ID = 15L;
    /** 黑名单质押库 */
    public static final Long BLACKLIST_POOL_ID = 17L;
    /** 重点观察名单 */
    public static final Long KEY_WATCH_POOL_ID = 23L;

    /** 自动调库规则查询数据访问组件 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 外部评级机构配置服务 */
    @Resource
    private ExternalRatingAgencyService externalRatingAgencyService;

    /**
     * ABS 按全部关联主体检查原有黑名单质押库硬条件
     * 执行顺序：
     * 1. 复用候选主体集合并逐主体执行原三条件
     * 2. 调入要求任一命中，调出要求全部不再命中
     * @param codes 已查询的 ABS 关联主体及本次自选代码
     * @param adjustMode 调整方向
     * @return 原硬条件不通过的具体原因
     */
    public String validateCompanies(List<String> codes, String adjustMode) {
        return validateCompanies(codes, adjustMode,
                Collections.<Long>emptySet(), Collections.<Long>emptySet());
    }

    /**
     * 按本批次完成后的预期池状态校验 ABS 关联主体是否满足黑名单质押库条件
     *
     * 执行顺序：
     * 1. 逐个计算关联主体在 15、23 池的预期状态
     * 2. 调入时任一关联主体命中即可通过，调出时全部关联主体均不命中才可通过
     *
     * @param codes 已查询的 ABS 关联主体及本次自选代码
     * @param adjustMode 调整方向
     * @param inboundPoolIds 本批预计调入的主体池 ID
     * @param outboundPoolIds 本批预计调出的主体池 ID
     * @return 原硬条件不通过的具体原因
     */
    public String validateCompanies(List<String> codes, String adjustMode,
                                    Set<Long> inboundPoolIds, Set<Long> outboundPoolIds) {
        if (codes.isEmpty()) {
            return "无法识别 ABS 关联主体，不能调整黑名单质押库";
        }
        for (String code : codes) {
            Decision decision = evaluate(code, inboundPoolIds, outboundPoolIds);
            if (decision.shouldBeInBlacklist()) {
                if (AdjustMode.IN.getCode().equals(adjustMode)) {
                    return null;
                }
                return "ABS 关联主体[" + code + "]仍命中黑名单质押库条件（" + decision.getMatchDescription() + "），不能调出17（黑名单质押库）";
            }
        }
        return AdjustMode.IN.getCode().equals(adjustMode) ? "ABS 关联主体均未命中黑名单质押库三个条件，不能调入17（黑名单质押库）" : null;
    }

    /**
     * 按当前已生效池状态判断主体是否应在黑名单质押库。
     *
     * @param companyCode 发行主体代码
     * @return 黑名单质押库三条件判定结果
     */
    public Decision evaluate(String companyCode) {
        return evaluate(companyCode, Collections.<Long>emptySet(), Collections.<Long>emptySet());
    }

    /**
     * 创建本轮债券定时任务共用的主体判定
     *
     * 首次有主体需要判断时才读取认可评级机构配置
     *
     * @return 仅在本轮任务内使用的主体判定结果容器
     */
    ScheduledDecisions scheduledDecisions() {
        return new ScheduledDecisions();
    }

    /** 只在当前任务内保留主体判定，供多组债券候选复用 */
    final class ScheduledDecisions {
        /** 本轮已查询的主体判定，包含未命中结果 */
        private final Map<String, Boolean> decisions = new HashMap<>();
        /** 本轮使用的认可评级机构 */
        private List<String> agencyCodes;

        /**
         * 分批判断本轮尚未查过的发行主体
         *
         * 已查过的主体直接复用结果，包括三项条件均未命中的主体
         *
         * 执行顺序：
         * 1. 提取有代码、未查询且去重后的主体
         * 2. 首次有待判断主体时读取认可评级机构配置
         * 3. 分批查询命中任一条件的主体，记录每个候选的判定结果
         *
         * @param companyCodes 本组候选债券的发行主体代码
         */
        void load(List<String> companyCodes) {
            // 1. 筛出本轮尚未查过的非空主体代码
            List<String> missing = companyCodes.stream()
                    .filter(StringUtils::hasText).filter(code -> !decisions.containsKey(code))
                    .distinct().collect(Collectors.toList());
            if (missing.isEmpty()) {
                return;
            }
            // 2. 优先复用本轮已读取的认可评级机构
            if (agencyCodes == null) {
                agencyCodes = externalRatingAgencyService.queryRequiredAgencyCodeList();
            }
            // 3. 按批查询并保存命中与未命中的结果
            for (int offset = 0; offset < missing.size(); offset += AutoAdjustRestrictHelper.QUERY_BATCH_SIZE) {
                List<String> batch = missing.subList(offset,
                        Math.min(offset + AutoAdjustRestrictHelper.QUERY_BATCH_SIZE, missing.size()));
                Set<String> matched = new HashSet<>(autoAdjustMapper.queryScheduledBlacklistCompanyCodes(
                        batch, agencyCodes, FORBIDDEN_POOL_ID, KEY_WATCH_POOL_ID));
                for (String code : batch) {
                    decisions.put(code, matched.contains(code));
                }
            }
        }

        /**
         * 读取已批量查询的主体判定结果
         *
         * @param companyCode 发行主体代码
         * @return 本轮已查到该主体命中任一条件时返回 true
         */
        boolean matches(String companyCode) {
            return Boolean.TRUE.equals(decisions.get(companyCode));
        }
    }

    /**
     * 按一次调整完成后的预期状态判断主体是否应在黑名单质押库。
     * 调入/调出集合仅对条件池 15、23 生效，调出优先于当前状态，调入优先于调出。
     *
     * @param companyCode    发行主体代码
     * @param inboundPoolIds 本批预计调入的主体池 ID
     * @param outboundPoolIds 本批预计调出的主体池 ID
     * @return 黑名单质押库三条件判定结果
     */
    public Decision evaluate(String companyCode, Set<Long> inboundPoolIds, Set<Long> outboundPoolIds) {
        Set<Long> inIds = inboundPoolIds == null ? Collections.<Long>emptySet() : inboundPoolIds;
        Set<Long> outIds = outboundPoolIds == null ? Collections.<Long>emptySet() : outboundPoolIds;
        // 计算本批完成后主体在债券禁止库的预期状态
        boolean inForbidden = projectedInPool(companyCode, FORBIDDEN_POOL_ID, inIds, outIds);
        // 计算本批完成后主体在重点观察名单的预期状态
        boolean inKeyWatch = projectedInPool(companyCode, KEY_WATCH_POOL_ID, inIds, outIds);
        // 使用当前有效外部评级机构配置判断低外评条件
        List<String> agencyCodes = externalRatingAgencyService.queryRequiredAgencyCodeList();
        boolean lowOuterRating = autoAdjustMapper.queryCompanyHasLowOuterRating(companyCode, agencyCodes);
        return new Decision(inForbidden, lowOuterRating, inKeyWatch);
    }

    /**
     * 返回指定方向不符合黑名单质押库三条件时的失败原因。
     *
     * @param companyCode    发行主体代码
     * @param adjustMode     调整方向
     * @param inboundPoolIds 本批预计调入的主体池 ID
     * @param outboundPoolIds 本批预计调出的主体池 ID
     * @return 校验失败原因，符合条件时返回 {@code null}
     */
    public String validate(String companyCode, String adjustMode,
                           Set<Long> inboundPoolIds, Set<Long> outboundPoolIds) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            return "无法识别发行主体，不能调整黑名单质押库";
        }
        Decision decision = evaluate(companyCode, inboundPoolIds, outboundPoolIds);
        if (AdjustMode.IN.getCode().equals(adjustMode) && !decision.shouldBeInBlacklist()) {
            return "发行主体未命中黑名单质押库三个条件，不能调入17（黑名单质押库）";
        }
        if (AdjustMode.OUT.getCode().equals(adjustMode) && decision.shouldBeInBlacklist()) {
            return "发行主体仍命中黑名单质押库条件（" + decision.getMatchDescription()
                    + "），不能调出17（黑名单质押库）";
        }
        return null;
    }

    /**
     * 根据当前在池状态及本批调整方向计算主体在条件池中的预期状态。
     *
     * @param companyCode    发行主体代码
     * @param poolId         条件池 ID
     * @param inboundPoolIds 本批预计调入的主体池 ID
     * @param outboundPoolIds 本批预计调出的主体池 ID
     * @return {@code true}=本批完成后主体在条件池
     */
    private boolean projectedInPool(String companyCode, Long poolId,
                                    Set<Long> inboundPoolIds, Set<Long> outboundPoolIds) {
        if (inboundPoolIds.contains(poolId)) {
            return true;
        }
        if (outboundPoolIds.contains(poolId)) {
            return false;
        }
        return autoAdjustMapper.queryCompanyInPool(companyCode, poolId);
    }

    /** 三条件判定结果。 */
    public static class Decision {
        /** 主体是否在债券禁止库。 */
        private final boolean inForbiddenPool;
        /** 主体近一年认可机构外评孰低是否为 AA- 及以下。 */
        private final boolean lowOuterRating;
        /** 主体是否在重点观察名单。 */
        private final boolean inKeyWatchPool;

        /**
         * 构建黑名单质押库三条件判定结果。
         *
         * @param inForbiddenPool 主体是否在债券禁止库
         * @param lowOuterRating  主体是否命中低外评条件
         * @param inKeyWatchPool  主体是否在重点观察名单
         */
        Decision(boolean inForbiddenPool, boolean lowOuterRating, boolean inKeyWatchPool) {
            this.inForbiddenPool = inForbiddenPool;
            this.lowOuterRating = lowOuterRating;
            this.inKeyWatchPool = inKeyWatchPool;
        }

        /**
         * 判断主体是否应在黑名单质押库。
         *
         * @return {@code true}=三个条件至少命中一个
         */
        public boolean shouldBeInBlacklist() {
            return inForbiddenPool || lowOuterRating || inKeyWatchPool;
        }

        /** @return 主体是否在债券禁止库 */
        public boolean isInForbiddenPool() {
            return inForbiddenPool;
        }

        /** @return 主体是否命中低外评条件 */
        public boolean isLowOuterRating() {
            return lowOuterRating;
        }

        /** @return 主体是否在重点观察名单 */
        public boolean isInKeyWatchPool() {
            return inKeyWatchPool;
        }

        /**
         * 返回已命中的条件说明。
         *
         * @return 已命中条件的中文描述
         */
        public String getMatchDescription() {
            StringBuilder result = new StringBuilder();
            // 追加债券禁止库条件说明
            append(result, inForbiddenPool, "当前在债券禁止库15");
            // 追加低外评条件说明
            append(result, lowOuterRating, "近一年认可外评孰低为AA-及以下");
            // 追加重点观察名单条件说明
            append(result, inKeyWatchPool, "当前在重点观察名单23");
            return result.length() == 0 ? "无" : result.toString();
        }

        /**
         * 将已命中的条件追加到描述中。
         *
         * @param result  条件描述
         * @param matched 是否命中
         * @param text    条件文案
         */
        private void append(StringBuilder result, boolean matched, String text) {
            if (!matched) {
                return;
            }
            if (result.length() > 0) {
                result.append("、");
            }
            result.append(text);
        }
    }
}
