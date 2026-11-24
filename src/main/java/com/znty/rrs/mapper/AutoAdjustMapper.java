package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.schedule.PoolSecurityRowDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 到期出池、主体外评调整及旗下债券同步
 *
 * 负责查询到期证券及 CRMW 组合、主体外评和旗下债券同步数据，并按记录 ID 撤销债券跟随记录
 */
@Mapper
public interface AutoAdjustMapper {

    /**
     * 批量查询证券审批通过且未删除的池状态
     *
     * 同一证券在同一池的多条记录分别返回，供跟随出池判断是否仍有其他入池依据
     *
     * @param securityCodes 本批待查询的证券代码，不能为空
     * @return 生效池状态列表，每条记录包含候选原始代码和所在池 ID
     */
    List<PoolSecurityRowDto> queryActiveSecurityPoolRows(@Param("securityCodes") List<String> securityCodes);

    /**
     * 批量查询存在待处理调库步骤的证券
     *
     * 调库日志未删除且同一调整批次存在待处理步骤时才算在途
     *
     * @param securityCodes 本批待查询的证券代码，不能为空
     * @return 存在在途流程的候选证券代码
     */
    List<String> queryPendingSecurityCodes(@Param("securityCodes") List<String> securityCodes);

    /**
     * 批量查询命中黑名单质押库任一条件的发行主体
     *
     * 主体在禁止库或重点观察名单生效，或者近一年存在认可机构给出的 AA- 及以下外评，即视为命中
     *
     * @param companyCodes 本批待判断的发行主体代码，不能为空
     * @param agencyCodes 认可评级机构代码，空时只判断条件池
     * @param forbiddenPoolId 债券禁止库 ID
     * @param keyWatchPoolId 重点观察名单池 ID
     * @return 命中至少一项条件的候选主体原始代码
     */
    List<String> queryScheduledBlacklistCompanyCodes(@Param("companyCodes") List<String> companyCodes,
                                                    @Param("agencyCodes") List<String> agencyCodes,
                                                    @Param("forbiddenPoolId") long forbiddenPoolId,
                                                    @Param("keyWatchPoolId") long keyWatchPoolId);

    /**
     * 查询当前已有生效主体的普通池
     *
     * 只返回已启用且未删除的普通池，供补债任务识别同池映射
     *
     * @return 去重后的普通池 ID，未找到时返回空列表
     */
    List<Long> queryCompanyCurrentPoolIds();

    /**
     * 判断发行主体当前是否在指定池生效
     *
     * @param companyCode 待检查的发行主体代码
     * @param poolId 目标池 ID
     * @return 存在审批通过且未删除的主体记录时为 true
     */
    boolean queryCompanyInPool(@Param("companyCode") String companyCode, @Param("poolId") Long poolId);

    /**
     * 判断发行主体近一年是否存在认可机构给出的 AA- 及以下外评
     *
     * @param companyCode 待检查的发行主体代码
     * @param agencyCodes 认可评级机构代码，空时没有命中
     * @return 存在符合评级和日期条件的外评时为 true
     */
    boolean queryCompanyHasLowOuterRating(@Param("companyCode") String companyCode,
                                          @Param("agencyCodes") List<String> agencyCodes);

    /**
     * 查询任务与调库方向对应的池绑定
     *
     * @param taskCode 定时任务编码
     * @param ruleType 调库方向类型，如 auto_in 或 auto_out
     * @return 未删除绑定中的池 ID，去重后按 ID 升序返回
     */
    List<Long> queryBoundPoolIds(@Param("taskCode") String taskCode, @Param("ruleType") String ruleType);

    /**
     * 查询 ip_pool_status 中目标池已生效的到期债券，包含符合条件的 ABS 和 CRMW
     *
     * 主档状态为 D 直接命中，其余按摘牌日是否严格早于截止日判断
     *
     * @param poolId 扫描的目标池 ID
     * @param maturityOffsetDays 摘牌日的截止日期偏移天数，日期比较不含截止日当天
     * @param outRestrictPoolIds 调出限制池 ID，空时不匹配限制池
     * @return 含主档状态、摘牌日和调出限制命中结果的候选债券，Excel 保护记录由服务过滤
     */
    List<ScheduledAdjustCandidateDto> queryPoolSecurityByExpired(@Param("poolId") Long poolId,
                                                                 @Param("maturityOffsetDays") int maturityOffsetDays,
                                                                 @Param("outRestrictPoolIds") List<Long> outRestrictPoolIds);

    /**
     * 查询已在来源池生效且尚未在目标池生效的主体
     *
     * 供债券禁止库和重点观察名单两项条件复用
     *
     * @param sourcePoolId 主体所在的来源池 ID
     * @param targetPoolId 拟调入的目标池 ID
     * @return 待调入的主体候选
     */
    List<ScheduledAdjustCandidateDto> queryCompanyInPoolNotInTarget(
            @Param("sourcePoolId") Long sourcePoolId,
            @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询近一年认可外评孰低为 AA- 及以下且尚未在目标池生效的主体
     *
     * @param poolId 主体调入的目标池 ID
     * @param agencyCodes 认可评级机构代码
     * @return 带孰低外评的待调入主体候选
     */
    List<ScheduledAdjustCandidateDto> queryCompanyByLowOuterRatingNotInPool(
            @Param("poolId") Long poolId,
            @Param("agencyCodes") List<String> agencyCodes);

    /**
     * 查询目标池内近一年认可外评孰低高于 AA- 的生效主体
     *
     * 近一年没有有效认可外评的主体不返回
     *
     * @param poolId 主体调出的目标池 ID
     * @param limitPoolIds 额外拦截池 ID，为空时不检查额外拦截池
     * @param agencyCodes 认可评级机构代码
     * @return 带孰低外评的待调出主体候选
     */
    List<ScheduledAdjustCandidateDto> queryCompanyByNotLowOuterRatingInPool(
            @Param("poolId") Long poolId,
            @Param("limitPoolIds") List<Long> limitPoolIds,
            @Param("agencyCodes") List<String> agencyCodes);

    /**
     * 查询指定池中当前已生效的主体
     *
     * @param poolId 待查询的池 ID
     * @return 审批通过且未删除的主体代码，去重后返回
     */
    List<String> queryCompanyCodeListInPool(@Param("poolId") Long poolId);

    /**
     * 查询目标池内按凭证状态或摘牌日判定的到期 CRMW 组合
     *
     * 调出限制按标的证券代码、凭证代码和凭证类型匹配
     *
     * @param poolId 扫描的 CRMW 目标池 ID
     * @param maturityOffsetDays 凭证摘牌日的截止日期偏移天数，日期比较不含截止日当天
     * @param outRestrictPoolIds 调出限制池 ID，为空时不匹配限制池
     * @return 含凭证状态、摘牌日和调出限制命中结果的候选组合
     */
    List<ScheduledAdjustCandidateDto> queryCrmwPoolByExpired(@Param("poolId") Long poolId,
                                                           @Param("maturityOffsetDays") int maturityOffsetDays,
                                                           @Param("outRestrictPoolIds") List<Long> outRestrictPoolIds);

    /**
     * 查询曾有主体生效的池
     *
     * 包含后来已被软删的生效记录，用于覆盖最后一个主体退出后的债券清理
     *
     * @return 去重后的历史主体池 ID
     */
    List<Long> queryCompanyHistoricalPoolIds();

    /**
     * 读取当前数据库日期
     *
     * @return 数据库当天的自然日
     */
    LocalDate queryDatabaseDate();

    /**
     * 查询来源池内生效主体的待补入债券
     *
     * 排除退市债券、已转正临时代码和目标池已有生效记录的债券，返回摘牌日供服务按主体来源判断
     *
     * @param companyPoolId 主体依据池 ID
     * @param targetPoolId 债券目标池 ID
     * @param bondTypeScope 债券类型排除范围，定时补债时用于排除 ABS
     * @return 待补入的债券及发行主体信息，按债券代码升序返回
     */
    List<ScheduledAdjustCandidateDto> queryCompanyNewBondForAutoIn(
            @Param("companyPoolId") Long companyPoolId,
            @Param("targetPoolId") Long targetPoolId,
            @Param("bondTypeScope") CompanyBondTypeScopeBo bondTypeScope);

    /**
     * 查询当前有效的普通债跟随记录
     *
     * @param bondPoolId 待检查的债券目标池 ID
     * @return 跟随记录，含池状态记录 ID 和发行主体信息
     */
    List<ScheduledAdjustCandidateDto> queryCompanyFollowedBondsForAutoOut(@Param("bondPoolId") Long bondPoolId);

    /**
     * 按池批量读取生效主体
     *
     * @param poolIds 需要查询的主体依据池
     * @return 各池中的有效主体
     */
    List<PoolSecurityRowDto> queryActiveCompanyPoolRows(@Param("poolIds") Collection<Long> poolIds);

    /**
     * 按记录 ID 软删一条仍生效的债券跟随记录
     *
     * @param poolStatusId 要撤销的池状态记录 ID
     * @return 受影响的行数，记录不存在或已失效时为 0
     */
    int deleteCompanyFollowedBondStatus(@Param("poolStatusId") Long poolStatusId);
}
