package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

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

    /** 查询所有已有生效主体的普通池，供补债任务自动覆盖同池债券。 */
    List<Long> queryCompanyCurrentPoolIds();

    /**
     * 查询主体当前是否已在指定池生效。
     *
     * @param companyCode 发行主体代码
     * @param poolId      投资池 ID
     * @return {@code true}=主体当前已在指定池生效
     */
    boolean queryCompanyInPool(@Param("companyCode") String companyCode, @Param("poolId") Long poolId);

    /**
     * 查询主体近一年认可机构外评是否存在 AA- 及以下档位。
     *
     * @param companyCode 发行主体代码
     * @param agencyCodes 有效外部评级机构编码
     * @return {@code true}=近一年认可机构外评孰低为 AA- 及以下
     */
    boolean queryCompanyHasLowOuterRating(@Param("companyCode") String companyCode,
                                          @Param("agencyCodes") List<String> agencyCodes);

    /**
     * 查询投资池关系配置中绑定了指定定时任务与调入/调出类型的池 ID。
     *
     * @param taskCode 定时任务编码（sys_scheduled_task.task_code）
     * @param ruleType 规则类型：auto_in / auto_out
     * @return 池 ID 列表（去重）
     */
    List<Long> queryBoundPoolIds(@Param("taskCode") String taskCode, @Param("ruleType") String ruleType);

    /** 按日期偏移天数查询目标池内已生效的到期债券和股票，返回实际到期日供调库日志使用 */
    List<ScheduledAdjustCandidateDto> queryPoolSecurityByExpired(@Param("poolId") Long poolId,
                                                               @Param("maturityOffsetDays") int maturityOffsetDays);

    /** 按已启用目标池的市场配置查询未在目标池的旗下债券，调度任务传入范围以排除 ABS */
    List<ScheduledAdjustCandidateDto> queryCompanyNewBondForAutoIn(
            @Param("companyPoolId") Long companyPoolId,
            @Param("targetPoolId") Long targetPoolId,
            @Param("targetMarketCodes") String targetMarketCodes,
            @Param("bondTypeScope") CompanyBondTypeScopeBo bondTypeScope);

    /**
     * 查询当前已在来源池、尚未在目标池的主体。
     * <p>供条款（一）禁止库、条款（三）重点观察名单复用；security_type 固定 company。</p>
     *
     * @param sourcePoolId 来源池 ID
     * @param targetPoolId 入池目标池 ID
     * @return 待入池主体
     */
    List<ScheduledAdjustCandidateDto> queryCompanyInPoolNotInTarget(
            @Param("sourcePoolId") Long sourcePoolId,
            @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询近一年认可外评孰低为 AA-及以下、且尚未在目标池的主体。
     * <p>仅条款（二）；一年以前忽略；认可机构由外部评级机构配置表控制。</p>
     *
     * @param poolId 入池目标池 ID
     * @param agencyCodes 有效外部评级机构编码
     * @return 待入池主体（含 outerRating）
     */
    List<ScheduledAdjustCandidateDto> queryCompanyByLowOuterRatingNotInPool(
            @Param("poolId") Long poolId,
            @Param("agencyCodes") List<String> agencyCodes);

    /**
     * 查询已在目标池、近一年认可外评孰低不属于 AA-及以下的主体。
     * <p>仅条款（二）的反面；无认可外评不返回。条款（一）（三）由调用方按在池名单排除。
     * {@code limitPoolIds} 为额外拦截池，空则不加这段。</p>
     *
     * @param poolId       出池目标池 ID
     * @param limitPoolIds 额外拦截池；空则不追加
     * @param agencyCodes  有效外部评级机构编码
     * @return 待出池主体（含 outerRating）
     */
    List<ScheduledAdjustCandidateDto> queryCompanyByNotLowOuterRatingInPool(
            @Param("poolId") Long poolId,
            @Param("limitPoolIds") List<Long> limitPoolIds,
            @Param("agencyCodes") List<String> agencyCodes);

    /**
     * 查询指定池中当前已生效的主体代码。
     *
     * @param poolId 池 ID
     * @return 主体代码列表
     */
    List<String> queryCompanyCodeListInPool(@Param("poolId") Long poolId);

    /** 按凭证到期日和日期偏移天数查询 ip_pool_status_crmw 内已生效的到期组合 */
    List<ScheduledAdjustCandidateDto> queryCrmwPoolByExpired(@Param("poolId") Long poolId,
                                                           @Param("maturityOffsetDays") int maturityOffsetDays);

    /** 查询曾有已生效主体的池，覆盖最后一个主体退出后的清理 */
    List<Long> queryCompanyHistoricalPoolIds();

    /** 查询已失去全部主体依据的普通债跟随记录 */
    List<ScheduledAdjustCandidateDto> queryCompanyFollowedBondsForAutoOut(
            @Param("bondPoolId") Long bondPoolId,
            @Param("companyPoolIds") Set<Long> companyPoolIds);

    /** 撤销指定的债券跟随记录 */
    int deleteCompanyFollowedBondStatus(@Param("poolStatusId") Long poolStatusId);
}
