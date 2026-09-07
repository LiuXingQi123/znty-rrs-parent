package com.znty.rrs.service;

import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;

/**
 * 主体旗下债券联动范围策略。
 *
 * <p>所有人工与定时主体联动入口从这里取得当前类型范围。后续如需统一排除 ABS
 * 或某些 {@code security_type}，只调整本策略的返回值；如仅个别链路需要差异口径，
 * 则显式构建另一份 {@link CompanyBondTypeScopeBo}，不得在 Mapper SQL 中写死。</p>
 */
public final class CompanyBondSyncPolicy {

    /** 工具类不允许实例化。 */
    private CompanyBondSyncPolicy() {
    }

    /**
     * 获取当前主体旗下债券类型范围。
     *
     * <p>当前口径：不排除任何 bond 子类型，普通债、ABS、CRMW 都跟随主体入/出池。
     * CRMW 凭证作为旗下债写入 {@code ip_pool_status}；凭证+标的进 CRMW 库仍走专用链路写 {@code ip_pool_status_crmw}。</p>
     *
     * <p>后续若要改排除范围，只改本方法返回值，例如：</p>
     * <pre>
     * // 排除 ABS，同时排除 CRMW
     * return CompanyBondTypeScopeBo.excluding(true, Arrays.asList("crmw"));
     *
     * // 只排除 CRMW，ABS 仍跟随
     * return CompanyBondTypeScopeBo.excluding(false, Arrays.asList("crmw"));
     *
     * // 只排除 ABS，CRMW 仍跟随
     * return CompanyBondTypeScopeBo.excluding(true, Collections.emptyList());
     * </pre>
     *
     * @return 包含普通债、ABS、CRMW及其他 bond 子类型的范围
     */
    public static CompanyBondTypeScopeBo currentTypeScope() {
        // 当前不排除 ABS / CRMW。若要排除，按上方注释示例改这一行即可。
        return CompanyBondTypeScopeBo.includeAllBondTypes();
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
}
