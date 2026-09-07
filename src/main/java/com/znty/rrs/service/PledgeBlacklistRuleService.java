package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.mapper.AutoAdjustMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Set;

/** 黑名单质押库三条件统一判定服务。 */
@Service
public class PledgeBlacklistRuleService {

    /** 债券禁止库。 */
    public static final Long FORBIDDEN_POOL_ID = 15L;
    /** 黑名单质押库。 */
    public static final Long BLACKLIST_POOL_ID = 17L;
    /** 重点观察名单。 */
    public static final Long KEY_WATCH_POOL_ID = 23L;

    /** 自动调库规则查询数据访问组件。 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;

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
        boolean lowOuterRating = autoAdjustMapper.queryCompanyHasLowOuterRating(companyCode);
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
