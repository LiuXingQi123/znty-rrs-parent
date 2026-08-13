package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自动调库数据访问组件。
 *
 * <p>支持到期证券自动出池、在池主体旗下债券自动入池、主体下债券同池自动入库、
 * 外评 AA- 及以下主体自动入池、外评非 AA- 及以下主体自动出池等定时任务查询，
 * 复用 {@link SecurityPoolAdjustMapper#addAdjustLog} /
 * {@link SecurityPoolAdjustMapper#addPoolStatus} /
 * {@link SecurityPoolAdjustMapper#deletePoolStatusSoft} 落地。
 */
@Mapper
public interface AutoAdjustMapper {

    /**
     * 查询配置了自动调出规则（rule_type=auto_out）的启用投资池 ID 列表。
     *
     * @return 池 ID 列表
     */
    List<Long> queryAutoOutPoolIds();

    /**
     * 查询指定池中已生效（audit_status=20）且已到期的在池证券。
     *
     * <p>到期口径对齐老系统 {@code AdjustRuleByExpired}：{@code maturity_date} 早于昨天（T-2），
     * 到期当天与到期次日仍不出池。大类对齐老 ptype=4000/2000，仅债、股；排除 crmw。
     * 仅回填 securityCode/securityShortName/securityType，其余由调用方补充。
     *
     * @param poolId 目标池 ID
     * @return 到期在池证券列表（每条对应一条待自动调出记录）
     */
    List<IpAdjustLogBo> queryPoolSecurityByExpired(@Param("poolId") Long poolId);

    /**
     * 查询「主体已在主体池、旗下未到期债券尚未在目标池」的待自动入池债券。
     *
     * <p>对应老系统 {@code AutoAdjustInNewBondToLimitPoolJob}：
     * 主体在 companyPoolId（category_type=company）且 bond 大类未到期、未在 targetPoolId；
     * 排除已更新为正式代码的临时代码；排除 ABS（{@code abs_flag=1}）与 CRMW
     * （{@code security_type=crmw}）。ABS 须走禁投 ABS 独立入口，与主体禁止库同步债口径不同。
     *
     * @param companyPoolId 主体所在池 ID（如债券禁止库 15）
     * @param targetPoolId  债券自动入池目标池 ID（可与主体池相同）
     * @return 待入池债券（仅回填 securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyNewBondForAutoIn(@Param("companyPoolId") Long companyPoolId,
                                                     @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询「主体已在本池、旗下债未在本池」的同池自动入库候选。
     * <p>对应老系统 IP_RULE type=0「主体下债券自动入库」：主体与债<strong>必须同一池</strong>；
     * 债大类未到期（含到期当天，对齐老 {@code enddate >= sysdate}）；尊重池 {@code market_codes}（空则不限制）；
     * <strong>不</strong>排除临时代码已更新记录（与 Job 版口径区分）。</p>
     *
     * @param poolId 主体所在池且债写入池（同一 ID）
     * @return 待入池债券（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyBondSamePoolForAutoIn(@Param("poolId") Long poolId);

    /**
     * 查询最新主体外评落在「AA-及以下」列表、且尚未在目标池生效在池的主体。
     * <p>对应老系统 {@code AdjustRuleInAA}：数据源为 Wind 表 ais_inv_ods.wind_cbondissuerrating；
     * 有效外评按 12 个月拆分（近 12 个月取档位最高，12 个月前取日期最新）后再取更近一条；
     * 评级命中 AA-/A/BBB… 等列表；security_type 固定 company。</p>
     *
     * @param poolId 目标池 ID
     * @return 待入池主体（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyByLowOuterRatingNotInPool(@Param("poolId") Long poolId);

    /**
     * 查询最新主体外评<strong>不在</strong>「AA-及以下」列表、且当前已在目标池生效在池的主体。
     * <p>对应老系统 {@code AdjustRuleOutAA}：有效外评取数与
     * {@link #queryCompanyByLowOuterRatingNotInPool} 同构（12 个月拆分），评级列表互为补集。
     * {@code limitPoolIds} 对应老配置 LIMITPOOLID_XYJJ：主体已在这些池则不出
     * （规则挂在禁止库且禁止库也在名单内时，该池不会自动出任何人）。</p>
     *
     * @param poolId        目标池 ID
     * @param limitPoolIds  禁投拦截池；空则不追加「已在禁投池则跳过」
     * @return 待出池主体（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyByNotLowOuterRatingInPool(@Param("poolId") Long poolId,
                                                              @Param("limitPoolIds") List<Long> limitPoolIds);

    /**
     * 查询主体旗下当前已在指定池的债券（bond 大类），供外评出池时顺带出同池债。
     *
     * <p>对应老系统 {@code findSecurityByCompanyCode(..., 4000)} 后再按在池过滤。
     *
     * @param companyCode 主体代码
     * @param poolId      与主体相同的目标池
     * @return 在池债券（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyBondInSamePoolForAutoOut(@Param("companyCode") String companyCode,
                                                             @Param("poolId") Long poolId);

    /**
     * 查询 CRMW 池中已生效且凭证到期日早于昨天（T-2）的在池组合。
     *
     * <p>对应老 IP_RULE {@code AdjustRuleCrmwDueOutPool}。到期看凭证 {@code crmw_scode}
     * 主数据 {@code maturity_date}，落地表 {@code ip_pool_status_crmw}。
     */
    List<IpAdjustLogBo> queryCrmwPoolByExpired(@Param("poolId") Long poolId);

    /**
     * 查询「债已在债券池、其发行主体不在主体池」的待自动出池债券。
     *
     * <p>对应老 {@code AutoAdjustInLimitPoolToNewBondJob}。老 Job 只排 CRMW；
     * 新系统排除 ABS / CRMW，避免无人审批出池绕过禁投 ABS 独立链路。
     *
     * @param bondPoolId    债券当前所在池
     * @param companyPoolId 主体应在的池（不在则出债）
     */
    List<IpAdjustLogBo> queryBondInPoolWhenCompanyNotIn(@Param("bondPoolId") Long bondPoolId,
                                                        @Param("companyPoolId") Long companyPoolId);
}
