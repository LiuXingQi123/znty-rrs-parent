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
     * 查询指定池中已生效（audit_status=20）且已到期（maturity_date 早于今日）的在池证券。
     *
     * <p>仅回填 securityCode/securityShortName/securityType 三个字段，其余由调用方补充。
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
     * 排除已更新为正式代码的临时代码。口径与主体调入债券禁止库时的
     * {@code queryCompanyInboundBondForAutoList} 一致（含 ABS/crmw）。
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
     * 债大类未到期；尊重池 {@code market_codes}（空则不限制）；
     * <strong>不</strong>排除临时代码已更新记录（与 Job 版口径区分）。</p>
     *
     * @param poolId 主体所在池且债写入池（同一 ID）
     * @return 待入池债券（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyBondSamePoolForAutoIn(@Param("poolId") Long poolId);

    /**
     * 查询最新主体外评落在「AA-及以下」列表、且尚未在目标池生效在池的主体。
     * <p>对应老系统 {@code AdjustRuleInAA}：数据源为 Wind 表 ais_inv_ods.wind_cbondissuerrating；
     * 每主体取最新一条外评；评级命中 AA-/A/BBB… 等列表；security_type 固定 company。</p>
     *
     * @param poolId 目标池 ID
     * @return 待入池主体（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyByLowOuterRatingNotInPool(@Param("poolId") Long poolId);

    /**
     * 查询最新主体外评<strong>不在</strong>「AA-及以下」列表、且当前已在目标池生效在池的主体。
     * <p>对应老系统 {@code AdjustRuleOutAA}（自动导出外部评级不是 AA- 及以下的主体）：
     * 与 {@link #queryCompanyByLowOuterRatingNotInPool} 评级列表互为补集；
     * 仅处理 security_type=company 的在池主体（不同步旗下债）。</p>
     *
     * @param poolId 目标池 ID
     * @return 待出池主体（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyByNotLowOuterRatingInPool(@Param("poolId") Long poolId);
}
