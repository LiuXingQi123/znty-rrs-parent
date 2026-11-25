package com.znty.rrs.service;

import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;

import java.util.Collections;

/**
 * 确定主体旗下债券的自动同步范围
 *
 * 本类是主体债券同步规则类，通用范围包含全部债券子类型，普通新债补入任务排除由 {@link CompanyAbsBondAutoInService} 处理的 ABS
 * 提供 CRMW 组合池判断和摘牌日判断，供有关任务筛选目标池及债券候选
 */
public final class CompanyBondSyncPolicy {

    /**
     * 限制主体债券联动范围工具类的实例化
     */
    private CompanyBondSyncPolicy() {
    }

    /**
     * 包含全部债券子类型的范围
     *
     * 执行顺序：
     * 1. 创建不排除任何债券子类型的查询范围
     *
     * @return 包含全部债券子类型的范围
     */
    public static CompanyBondTypeScopeBo currentTypeScope() {
        // 当前不排除 ABS / CRMW。若要排除，按上方注释示例改这一行即可
        return CompanyBondTypeScopeBo.includeAllBondTypes();
    }

    /**
     * 普通债和 CRMW 独立补入使用的范围，ABS 由专用任务处理
     *
     * 执行顺序：
     * 1. 创建排除 ABS 且不排除其他子类型的查询范围
     *
     * @return 排除 ABS 的债券子类型范围
     */
    public static CompanyBondTypeScopeBo newBondInboundScope() {
        return CompanyBondTypeScopeBo.excluding(true, Collections.<String>emptyList());
    }

    /**
     * 判断投资池是否为 CRMW 凭证和标的证券组合池
     *
     * 执行顺序：
     * 1. 检查池是否存在并且池类型为 CRMW
     *
     * @param pool 投资池
     * @return true=池为 CRMW 凭证和标的证券组合池，false=池不存在或池类型不是 CRMW
     */
    public static boolean isCrmwCombinationPool(InvestmentPoolBo pool) {
        return pool != null && PoolType.CRMW.getCode().equals(pool.getPoolType());
    }
    /**
     * 摘牌日按 yyyyMMdd 存储，空值沿用原有校验口径
     *
     * 执行顺序：
     * 1. 摘牌日为空时直接通过，否则按 yyyyMMdd 比较摘牌日与截止日
     *
     * @param delistDate 证券摘牌日，格式为 yyyyMMdd
     * @param cutoff 摘牌日截止值，格式为 yyyyMMdd
     * @return true=摘牌日为空或不早于截止日，false=摘牌日早于截止日
     */
    static boolean meetsDelistCutoff(String delistDate, String cutoff) {
        return delistDate == null || delistDate.compareTo(cutoff) >= 0;
    }
}
