package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自动调库数据访问组件。
 *
 * <p>支持到期证券自动出池、在池主体旗下债券自动入池、
 * 外评 AA- 及以下主体自动入池等定时任务查询，
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
     * 查询最新主体外评落在「AA-及以下」列表、且尚未在目标池生效在池的主体。
     * <p>对应老系统 {@code AdjustRuleInAA}：数据源为 Wind 表 ais_inv_ods.wind_cbondissuerrating；
     * 每主体取最新一条外评；评级命中 AA-/A/BBB… 等列表；security_type 固定 company。</p>
     *
     * @param poolId 目标池 ID
     * @return 待入池主体（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyByLowOuterRatingNotInPool(@Param("poolId") Long poolId);
}
