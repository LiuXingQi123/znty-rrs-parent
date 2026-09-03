package com.znty.rrs.service;

import com.znty.rrs.entity.bo.PoolRelationBo;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动调库池关系拦截（对齐老 AdjustPoolByRule 关系 11 / 12）。
 */
final class AutoAdjustRestrictHelper {

    /** 管理办法（一）：公司信用债禁止库 */
    static final long COMPANY_FORBIDDEN_POOL_ID = 15L;
    /** 管理办法（三）：重点观察名单 */
    static final long KEY_WATCH_POOL_ID = 23L;

    private AutoAdjustRestrictHelper() {
    }

    /**
     * 取出目标池某类关系下的关联池 ID。
     *
     * @param poolId       目标池 ID
     * @param relationType 关系类型
     * @param relations    全量池关系
     * @return 关联池 ID 列表
     */
    static List<Long> resolveRelationPoolIds(Long poolId, String relationType, List<PoolRelationBo> relations) {
        List<Long> result = new ArrayList<>();
        if (poolId == null || relationType == null || relations == null) {
            return result;
        }
        for (PoolRelationBo relation : relations) {
            if (relation == null || !poolId.equals(relation.getPoolId())
                    || !relationType.equals(relation.getRelationType())
                    || relation.getRelationPoolId() == null) {
                continue;
            }
            result.add(relation.getRelationPoolId());
        }
        return result;
    }

    /**
     * 当前所在池是否命中限制关联池：命中则应阻断本次自动入/出。
     *
     * @param currentPoolIds  主体或证券当前所在池
     * @param restrictPoolIds 限制关联池
     * @return true=应阻断
     */
    static boolean isInAnyPool(List<Long> currentPoolIds, List<Long> restrictPoolIds) {
        if (currentPoolIds == null || currentPoolIds.isEmpty()
                || restrictPoolIds == null || restrictPoolIds.isEmpty()) {
            return false;
        }
        for (Long currentPoolId : currentPoolIds) {
            if (currentPoolId != null && restrictPoolIds.contains(currentPoolId)) {
                return true;
            }
        }
        return false;
    }
}
