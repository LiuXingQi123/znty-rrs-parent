package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** ABS 跨池主体身份和主体联动依据查询 */
public interface AbsIdentityMapper {

    /** 查询全部有效在库及未结束申请占用的主体代码 */
    List<String> queryOccupiedIdentityCodes(@Param("securityCode") String securityCode);

    /** 查询 ABS 是否存在未确认主体的在库或在途记录 */
    boolean queryUnconfirmedActiveOrPending(@Param("securityCode") String securityCode);

    /** 查询主档已确认的主体代码，未确认时返回 null */
    String queryConfirmedIdentityCode(@Param("securityCode") String securityCode);

    /** 审批通过后仅更新 ABS 的五个主体字段 */
    int editApprovedIdentity(SecurityInfoBo security);

    /** 按精确 CRMW 申请读取标的 ABS 主体快照 */
    SecurityInfoBo queryCrmwIdentitySnapshot(@Param("adjustLogId") Long adjustLogId,
                                            @Param("securityCode") String securityCode);

    /** 读取尚未审核通过的主体信息供页面恢复在途选择 */
    SecurityInfoBo queryPendingIdentityDetail(@Param("securityCode") String securityCode);

    /** 批量筛出导入证券中的 ABS，普通债不逐行查询身份 */
    List<SecurityInfoBo> queryAbsSecurityList(@Param("securityCodes") List<String> securityCodes);

    /** 查询四类关联主体及已选主体在指定池的当前状态和最近生效日志 */
    List<String> queryCompanyPoolContext(@Param("securityCode") String securityCode,
                                         @Param("poolId") Long poolId);

    /** 查询指定池内仍命中的其他关联主体 */
    List<String> queryOtherCompanyInPool(@Param("securityCode") String securityCode,
                                        @Param("companyCode") String companyCode,
                                        @Param("poolId") Long poolId);

    /** 使用已查询的权益人代码集合实时检查主体所在池，不再次查询 Wind 候选 */
    boolean queryCompanyInPoolType(@Param("companyCodes") List<String> companyCodes,
                                   @Param("poolType") String poolType);

    /** 查询当前在池记录是否来源于债级独立申请 */
    boolean queryIndependentInbound(@Param("securityCode") String securityCode,
                                    @Param("poolId") Long poolId);

    /** 查询最近一条 ABS 主体联动提醒及人工处理结论 */
    IpGradeRuleAlertBo queryLatestSyncAlert(@Param("securityCode") String securityCode,
                                           @Param("poolId") Long poolId);

    /** 新增任意池的 ABS 主体联动提醒 */
    int addSyncAlert(IpGradeRuleAlertBo alert);

    /** 刷新相同依据的待处理提醒 */
    int editSyncAlert(IpGradeRuleAlertBo alert);

    /** 条件已明确解除时失效旧提醒 */
    int editSyncAlertInvalid(@Param("securityCode") String securityCode, @Param("poolId") Long poolId);

    /** 人工审批通过后保存当前依据下的处理结论 */
    int editSyncAlertProcessed(IpGradeRuleAlertBo alert);

    /** 更新 ABS 提醒的主体确认或无需入库处理结果 */
    int editAbsAlertDecision(IpGradeRuleAlertBo alert);

    /** 查询未确认 ABS 的待确认入池日志 */
    IpAdjustLogBo queryPendingScheduledInboundLog(@Param("securityCode") String securityCode,
                                                  @Param("poolId") Long poolId);

    /** 将未确认 ABS 的待确认入池日志转为已生效并更新确认原因 */
    int editPendingScheduledInboundApproved(@Param("id") Long id,
                                             @Param("adjustReason") String adjustReason);

    /** 将未确认 ABS 的待确认入池日志置为无效 */
    int editPendingScheduledInboundInvalid(@Param("id") Long id);

    /** 回填未确认 ABS 待确认入池日志的证券简称 */
    int editPendingScheduledInboundSecurityShortName(@Param("id") Long id,
                                                      @Param("securityShortName") String securityShortName);

    /** 兼容旧版直接落池记录，忽略自动入池时软删除对应调库日志 */
    int deletePassiveInboundLog(@Param("securityCode") String securityCode,
                                @Param("poolId") Long poolId);

    /** 兼容旧版直接落池记录，忽略自动入池时软删除对应在池状态 */
    int deletePassiveInboundStatus(@Param("securityCode") String securityCode,
                                   @Param("poolId") Long poolId);
}
