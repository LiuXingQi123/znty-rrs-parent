package com.znty.rrs.service;

import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;

import java.util.Collections;

/** 主体旗下债券定时同步的类型范围 */
public final class CompanyBondSyncPolicy {

    /** 工具类不允许实例化。 */
    private CompanyBondSyncPolicy() {
    }

    /** 包含全部债券子类型的范围 */
    public static CompanyBondTypeScopeBo currentTypeScope() {
        // 当前不排除 ABS / CRMW。若要排除，按上方注释示例改这一行即可。
        return CompanyBondTypeScopeBo.includeAllBondTypes();
    }

    /** 普通债和 CRMW 独立补入使用的范围，ABS 由专用任务处理 */
    public static CompanyBondTypeScopeBo newBondInboundScope() {
        return CompanyBondTypeScopeBo.excluding(true, Collections.<String>emptyList());
    }

    /**
     * 判断投资池是否为“凭证 + 标的证券”组合状态的 CRMW 专用池。
     *
     * @param pool 投资池
     * @return {@code true}=只能使用 ip_pool_status_crmw 的 CRMW 组合池
     */
    public static boolean isCrmwCombinationPool(InvestmentPoolBo pool) {
        return pool != null && PoolType.CRMW.getCode().equals(pool.getPoolType());
    }
    /** 摘牌日按 yyyyMMdd 存储，空值沿用原有校验口径 */
    static boolean meetsDelistCutoff(String delistDate, String cutoff) {
        return delistDate == null || delistDate.compareTo(cutoff) >= 0;
    }
}
