package com.znty.rrs.service;

import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;

import java.util.Collections;

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
     * 获取主体旗下债券类型范围。
     *
     * <p>当前口径：不排除任何 bond 子类型，普通债、ABS、CRMW 都跟随主体入/出同一目标池，不按池 ID 限制。
     * CRMW 凭证作为旗下债写入 {@code ip_pool_status}；凭证+标的进 CRMW 库仍走专用链路写 {@code ip_pool_status_crmw}。</p>
     *
     * <p>当前调用方为人工禁投池调库入口与 {@code company_not_in_pool_bond_auto_out}、
     * {@code company_outer_rating_not_aa_minus_auto_out} 两条联动任务。定时任务需要排除 ABS 时，
     * 使用各自专用方法（{@link #observerPoolScope()}、{@link #samePoolBondScope()} 等），不要改本方法。</p>
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
     * 「主体观察名单同步」（{@code company_observer_pool_sync}）专用类型范围
     *
     * <p>该任务固定排除 ABS，只让普通债与 CRMW 跟随主体进出观察池、重点观察池及其联动池。
     * ABS 的主体确认与按池进出统一由 {@code company_abs_bond_auto_in} 负责：观察名单只反映主体状态，
     * 不代表 ABS 已满足该池的入池条件；留在这里会与 ABS 对账任务重复写同一池状态，
     * 未确认主体的 ABS 还会额外生成一批待确认日志。</p>
     *
     * <p>不能用 {@link #currentTypeScope()} 顶替：该口径还被人工禁投池调库入口和其余主体联动任务共用。</p>
     *
     * @return 排除 ABS、保留 CRMW 及其他 bond 子类型的范围
     */
    public static CompanyBondTypeScopeBo observerPoolScope() {
        return CompanyBondTypeScopeBo.excluding(true, Collections.<String>emptyList());
    }

    /**
     * 「主体下债券自动入池」（{@code company_same_pool_bond_auto_in}）专用类型范围
     *
     * <p>该任务固定排除 ABS，只处理自身带有发行主体（{@code issuer_code} 非空）的普通债与 CRMW，
     * 候选 SQL 中 ABS 的四类关系码匹配分支已整体移除。ABS 的进出池与未确认提醒统一由
     * {@code company_abs_bond_auto_in} 按池对账负责。</p>
     *
     * <p>不能用 {@link #currentTypeScope()} 顶替：该口径还被其余主体联动任务与人工调库入口共用。</p>
     *
     * @return 排除 ABS、保留 CRMW 及其他 bond 子类型的范围
     */
    public static CompanyBondTypeScopeBo samePoolBondScope() {
        return CompanyBondTypeScopeBo.excluding(true, Collections.<String>emptyList());
    }

    /**
     * 「在池主体旗下债券自动入池」（{@code company_inpool_bond_auto_in}）专用类型范围
     *
     * <p>该任务固定排除 ABS，只补入自身带有发行主体的普通债与 CRMW。ABS 若继续留在这里，
     * 会出现与 {@code company_abs_bond_auto_in} 重复生成提醒、重复写池状态的问题，
     * 因此 ABS 的入池时效改由 ABS 对账任务统一承担。</p>
     *
     * <p>与 {@link #samePoolBondScope()} 当前口径相同，分开命名是为了两条任务后续能独立调整。</p>
     *
     * @return 排除 ABS、保留 CRMW 及其他 bond 子类型的范围
     */
    public static CompanyBondTypeScopeBo newBondInboundScope() {
        return CompanyBondTypeScopeBo.excluding(true, Collections.<String>emptyList());
    }

    /**
     * 「外评AA-及以下主体自动入池」（{@code company_outer_rating_aa_minus_auto_in}）专用类型范围
     *
     * <p>该任务固定排除 ABS，主体入池后同批跟进的旗下债只含普通债与 CRMW。
     * ABS 的主体确认与进出池统一由 {@code company_abs_bond_auto_in} 按池对账负责，
     * 留在这里只会重复生成提醒并和一个小时级任务争抢写池状态。</p>
     *
     * <p>不能用 {@link #currentTypeScope()} 顶替：该口径还被人工调库入口和其余联动任务共用。</p>
     *
     * @return 排除 ABS、保留 CRMW 及其他 bond 子类型的范围
     */
    public static CompanyBondTypeScopeBo outerRatingInboundScope() {
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
}
