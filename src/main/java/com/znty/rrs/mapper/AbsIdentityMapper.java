package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * ABS 主体身份确认及联动规则提醒
 *
 * 负责读取主档与调库快照、查找主体在池依据，以及保存提醒和确认结果
 */
@Mapper
public interface AbsIdentityMapper {

    /**
     * 查询 ABS 已确认或被在途申请占用的主体代码
     *
     * 读取主档及普通、CRMW 在途快照，排除空白主体代码。同一主体可能来自多条记录
     *
     * @param securityCode ABS 证券代码
     * @return 已确认或被在途申请占用的主体代码列表
     */
    List<String> queryOccupiedIdentityCodes(@Param("securityCode") String securityCode);

    /**
     * 判断 ABS 是否存在主体未确认的在池记录或在途申请
     *
     * 普通池和 CRMW 池的在池记录按主档判断。在途申请没有有效快照，或快照中的主体代码为空白时，也视为未确认
     *
     * @param securityCode ABS 证券代码
     * @return 存在未确认主体的生效池状态或未结束申请时为 true
     */
    boolean queryUnconfirmedActiveOrPending(@Param("securityCode") String securityCode);

    /**
     * 查询 ABS 主档中已确认的主体代码
     *
     * @param securityCode ABS 证券代码
     * @return 非空白的主档主体代码，未找到时为 null
     */
    String queryConfirmedIdentityCode(@Param("securityCode") String securityCode);

    /**
     * 更新 ABS 主档的五个主体字段和更新时间
     *
     * 主体名称、主体代码、外评、外评机构和内评取自核验后的申请快照
     *
     * @param security 包含证券代码及五个主体字段的证券信息
     * @return 受影响的主档行数
     */
    int editApprovedIdentity(SecurityInfoBo security);

    /**
     * 按调库日志和标的证券读取有效 CRMW 主体快照
     *
     * @param adjustLogId CRMW 调库日志 ID
     * @param securityCode CRMW 标的 ABS 证券代码
     * @return 对应的未删除快照，未找到时为 null
     */
    SecurityInfoBo queryCrmwIdentitySnapshot(@Param("adjustLogId") Long adjustLogId,
                                            @Param("securityCode") String securityCode);

    /**
     * 读取 ABS 未结束申请的主体身份，供页面恢复在途选择
     *
     * 普通或 CRMW 调库快照只取一条，有多条时未指定取哪一条
     *
     * @param securityCode ABS 证券代码
     * @return 含主体五字段的申请快照，未找到时为 null
     */
    SecurityInfoBo queryPendingIdentityDetail(@Param("securityCode") String securityCode);

    /**
     * 从给定证券代码中批量读取 ABS 主档
     *
     * ABS 标志为 1 或证券类型为 abs 的主档才返回
     *
     * @param securityCodes 本批待筛选的证券代码，不能为空
     * @return 与代码匹配的 ABS 主档列表
     */
    List<SecurityInfoBo> queryAbsSecurityList(@Param("securityCodes") List<String> securityCodes);

    /**
     * 查询生成 ABS 联动依据摘要所需的主体池状态
     *
     * 合并四类关联主体和主档选定主体，判断其在指定池内的状态。最近生效调库日志跨池查询，没有日志时填 0
     *
     * @param securityCode ABS 证券代码
     * @param poolId 待核对主体在池状态的池 ID
     * @return 按主体代码排序的依据条目，格式为 主体代码:in/out:日志ID
     */
    List<String> queryCompanyPoolContext(@Param("securityCode") String securityCode,
                                         @Param("poolId") Long poolId);

    /**
     * 查询指定主体依据池内仍生效的其他关联主体
     *
     * 同时检查四类关联主体和主档选定主体
     *
     * @param securityCode ABS 证券代码
     * @param companyCode 本次触发主体代码，为 null 时不排除主体
     * @param poolId 主体依据池 ID
     * @return 去重后按代码排序的其他在池主体
     */
    List<String> queryOtherCompanyInPool(@Param("securityCode") String securityCode,
                                        @Param("companyCode") String companyCode,
                                        @Param("poolId") Long poolId);

    /**
     * 判断给定的权益人是否在指定类型的投资池中生效
     *
     * 按传入的权益人代码直接匹配未删除的投资池和生效主体记录
     *
     * @param companyCodes 待核对的权益人代码，不能为空
     * @param poolType 投资池类型代码
     * @return 至少一个权益人在该类型的池中生效时为 true
     */
    boolean queryCompanyInPoolType(@Param("companyCodes") List<String> companyCodes,
                                   @Param("poolType") String poolType);

    /**
     * 判断 ABS 在目标池中是否有债级独立入池依据
     *
     * 检查手工、批量、Excel 调入及债券批次的联动、关联或互斥记录
     *
     * @param securityCode ABS 证券代码
     * @param poolId 债券目标池 ID
     * @return 存在符合条件的生效入池记录时为 true
     */
    boolean queryIndependentInbound(@Param("securityCode") String securityCode,
                                    @Param("poolId") Long poolId);

    /**
     * 查询指定证券在目标池中的最新 ABS 主体联动提醒
     *
     * 按记录 ID 倒序取第一条未删除提醒，保留已处理的人工结论
     *
     * @param securityCode ABS 证券代码
     * @param poolId 债券目标池 ID
     * @return 最新提醒，未找到时为 null
     */
    IpGradeRuleAlertBo queryLatestSyncAlert(@Param("securityCode") String securityCode,
                                           @Param("poolId") Long poolId);

    /**
     * 批量查询目标池内每只候选 ABS 的最新主体联动提醒
     *
     * 每只候选按记录 ID 倒序取第一条未删除提醒，并保留候选原始代码
     *
     * @param securityCodes 本批候选 ABS 代码，不能为空
     * @param poolId 债券目标池 ID
     * @return 有提醒的候选记录，含原始证券代码、提醒 ID、依据摘要和状态
     */
    List<IpGradeRuleAlertBo> queryLatestSyncAlerts(@Param("securityCodes") List<String> securityCodes,
                                                  @Param("poolId") Long poolId);

    /**
     * 新增 ABS 主体联动待处理提醒，并回填提醒 ID
     *
     * @param alert 待保存的证券、主体、目标池和提醒依据
     * @return 受影响的行数
     */
    int addSyncAlert(IpGradeRuleAlertBo alert);

    /**
     * 按 ID 刷新未删除且仍待处理的联动提醒
     *
     * @param alert 包含提醒 ID 和最新触发原因的提醒对象
     * @return 受影响的行数
     */
    int editSyncAlert(IpGradeRuleAlertBo alert);

    /**
     * 将目标池中指定证券未删除的待处理联动提醒置为失效
     *
     * @param securityCode ABS 证券代码
     * @param poolId 债券目标池 ID
     * @return 受影响的提醒行数
     */
    int editSyncAlertInvalid(@Param("securityCode") String securityCode, @Param("poolId") Long poolId);

    /**
     * 按 ID 将未删除的 ABS 主体联动提醒标为已处理，原状态不限
     *
     * 记录处理人和当前依据摘要，供同一依据再次出现时复用人工结论
     *
     * @param alert 包含提醒 ID、依据摘要和处理人信息的提醒对象
     * @return 受影响的行数
     */
    int editSyncAlertProcessed(IpGradeRuleAlertBo alert);

    /**
     * 保存 ABS 待处理提醒的主体确认或无需入库结论
     *
     * 按提醒 ID 更新主体、处理原因和依据摘要，并标记为已处理
     *
     * @param alert 包含提醒 ID、主体信息、处理原因、依据摘要和处理人的提醒对象
     * @return 受影响的行数，提醒已处理或已删除时为 0
     */
    int editAbsAlertDecision(IpGradeRuleAlertBo alert);

    /**
     * 查询 ABS 在目标池的最新待确认入池日志
     *
     * 只查询未删除的自动调入待确认日志，按记录 ID 倒序取第一条
     *
     * @param securityCode ABS 证券代码
     * @param poolId 债券目标池 ID
     * @return 最新待确认日志，未找到时为 null
     */
    IpAdjustLogBo queryPendingScheduledInboundLog(@Param("securityCode") String securityCode,
                                                  @Param("poolId") Long poolId);

    /**
     * 批量查询本池内每只候选 ABS 最新的待确认入池日志
     *
     * 只匹配未删除的自动调入待确认日志，按日志 ID 倒序为每个候选取一条
     *
     * @param securityCodes 本批候选 ABS 代码，不能为空
     * @param poolId 债券目标池 ID
     * @return 存在待确认日志的候选记录，含原始证券代码、日志 ID 和证券简称
     */
    List<IpAdjustLogBo> queryPendingScheduledInboundLogs(@Param("securityCodes") List<String> securityCodes,
                                                        @Param("poolId") Long poolId);

    /**
     * 将未删除的 ABS 待确认入池日志改为审批通过，并保存确认原因
     *
     * @param id 待确认日志 ID
     * @param adjustReason 确认主体后的调库原因
     * @return 受影响的行数，日志不再待确认时为 0
     */
    int editPendingScheduledInboundApproved(@Param("id") Long id,
                                             @Param("adjustReason") String adjustReason);

    /**
     * 将未删除的 ABS 待确认入池日志置为无效
     *
     * @param id 待确认日志 ID
     * @return 受影响的行数，日志不再待确认时为 0
     */
    int editPendingScheduledInboundInvalid(@Param("id") Long id);

    /**
     * 回填未删除且仍待确认的 ABS 自动入池日志的证券简称
     *
     * @param id 待确认日志 ID
     * @param securityShortName 要写入的证券简称
     * @return 受影响的行数
     */
    int editPendingScheduledInboundSecurityShortName(@Param("id") Long id,
                                                      @Param("securityShortName") String securityShortName);

    /**
     * 软删旧版已直接落池的 ABS 自动或联动调入日志
     *
     * 根据目标池内的生效在池记录定位调库日志，保留人工调入日志
     *
     * @param securityCode ABS 证券代码
     * @param poolId 债券目标池 ID
     * @return 受影响的调库日志行数
     */
    int deletePassiveInboundLog(@Param("securityCode") String securityCode,
                                @Param("poolId") Long poolId);

    /**
     * 软删关联到已删除自动或联动调入日志的 ABS 在池记录
     *
     * 用于收回旧版直接落池记录，保留人工调入形成的池状态
     *
     * @param securityCode ABS 证券代码
     * @param poolId 债券目标池 ID
     * @return 受影响的池状态行数
     */
    int deletePassiveInboundStatus(@Param("securityCode") String securityCode,
                                   @Param("poolId") Long poolId);
}
