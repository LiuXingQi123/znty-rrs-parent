package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDto;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.crmwpooladjust.PoolDto;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.crmwpooladjust.PoolStatusDto;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CRMW池调整数据访问接口
 */
@Mapper
public interface CrmwPoolAdjustMapper {

    /** 分页查询 CRMW 凭证列表 */
    List<SecurityInfoDto> queryCrmwPage(@Param("securityCode") String securityCode,
                                        @Param("securityShortName") String securityShortName,
                                        @Param("issuer") String issuer);

    /** 分页查询可绑定证券列表 */
    List<SecurityInfoDto> queryBindableSecurityPage(@Param("securityCode") String securityCode,
                                                    @Param("securityShortName") String securityShortName,
                                                    @Param("issuer") String issuer);

    /** 根据证券代码查询证券详情 */
    SecurityInfoDetailDto querySecurityDetail(@Param("securityCode") String securityCode);

    /** 根据证券代码查询证券基础信息实体 */
    SecurityInfoBo querySecurityBoByCode(@Param("securityCode") String securityCode);

    /** 新增调库记录（非直通流程，audit_status='00' 流程中） */
    int addAdjustLog(IpAdjustLogBo bo);

    /** 新增入池状态记录（直通流程，audit_status='20' 即时生效） */
    int addPoolStatus(IpAdjustLogBo bo);

    /** 软删除指定凭证与标的证券组合的入池状态（直通调出流程） */
    int deletePoolStatusSoft(@Param("securityCode") String securityCode,
                             @Param("crmwScode") String crmwScode,
                             @Param("crmwStype") String crmwStype,
                             @Param("targetPoolId") Long targetPoolId);

    /** 更新调库详情页传入的证券基础信息字段 */
    int editSecurityInfoForAdjust(CrmwPoolAdjustSubmitReq req);

    /** 根据证券代码和可选目标池查询调库记录列表 */
    List<IpAdjustLogBo> queryAdjustLogList(@Param("securityCode") String securityCode,
                                           @Param("crmwScode") String crmwScode,
                                           @Param("crmwStype") String crmwStype,
                                           @Param("adjustBatchNo") String adjustBatchNo);

    /** 查询操作人近期有效的手工 CRMW 调库记录 */
    List<IpAdjustLogBo> queryRecentManualAdjustLogList(@Param("securityCode") String securityCode,
                                                       @Param("crmwScode") String crmwScode,
                                                       @Param("crmwStype") String crmwStype,
                                                       @Param("adjusterId") String adjusterId,
                                                       @Param("seconds") int seconds);

    /** 查询 CRMW 组合活动流程涉及的手工调库目标池 ID */
    List<Long> queryPendingManualTargetPoolIdList(@Param("securityCode") String securityCode,
                                                  @Param("crmwScode") String crmwScode,
                                                  @Param("crmwStype") String crmwStype,
                                                  @Param("excludeBatchNo") String excludeBatchNo);

    /** 查询当前证券所在池列表 */
    List<PoolStatusDto> querySecurityPoolStatusList(@Param("securityCode") String securityCode,
                                                     @Param("crmwScode") String crmwScode,
                                                     @Param("crmwStype") String crmwStype);

    /** 查询当前证券主体（发行人）所在池列表 */
    List<PoolStatusDto> queryIssuerPoolStatusList(@Param("securityCode") String securityCode);

    /** 查询证券当前有效所在池 ID 列表（audit_status=20） */
    List<Long> querySecurityCurrentPoolIdList(@Param("securityCode") String securityCode,
                                              @Param("crmwScode") String crmwScode,
                                              @Param("crmwStype") String crmwStype);

    /** 查询目标投资池当前有效证券数量 */
    int queryPoolCurrentCount(@Param("poolId") Long poolId);

    /** 查询 CRMW 凭证与标的组合的实际入池时间。 */
    java.util.Date queryCrmwPoolEntryTime(@Param("securityCode") String securityCode,
                                          @Param("crmwScode") String crmwScode,
                                          @Param("crmwStype") String crmwStype,
                                          @Param("targetPoolId") Long targetPoolId);

    /** 查询证券是否在全局禁投池（forbidden/blacklist，audit_status=20），用于调入禁投池校验 */
    boolean querySecurityInForbiddenPool(@Param("securityCode") String securityCode);

    /** 查询指定日期是否在目标池的开放区间内（ip_pool_open_day，open_day_adjust=1 时用） */
    boolean queryPoolInOpenDay(@Param("poolId") Long poolId, @Param("today") String today);

    /** 查询各投资池当前有效证券数量 */
    List<PoolDto> queryPoolCurrentCountList();

    /** 查询全量投资池关系配置（不限关系类型） */
    List<PoolRelationBo> queryAllPoolRelationList();

    /** 查询证券是否存在进行中的调库流程（以是否存在待处理步骤为准） */
    boolean querySecurityHasPendingProcess(@Param("securityCode") String securityCode);

    /** 查询证券当前进行中流程所在步骤名称 */
    String querySecurityPendingProcessNodeLabel(@Param("securityCode") String securityCode);

    /** 查询当前证券是否在观察池（pool_type='observe'，audit_status='20'） */
    boolean querySecurityInObservePool(@Param("securityCode") String securityCode);

    /** 查询证券主体公司是否在观察池（同发行人的任意证券在观察池中） */
    boolean queryIssuerInObservePool(@Param("securityCode") String securityCode);

    /** 查询同主体在目标池中已有债券的最大剩余期限天数 */
    Integer queryIssuerTargetPoolMaxRemainDays(@Param("securityCode") String securityCode,
                                               @Param("targetPoolId") Long targetPoolId);

    /** 查询6个月内同主体有审批通过调入记录（对齐老系统 bondfileflag，6个月） */
    boolean queryHasRecentInboundWithReport(@Param("securityCode") String securityCode);

    /** 新增流程步骤记录 */
    int addAdjustStep(IpAdjustStepBo bo);

    /** 查询指定调库记录的流程步骤列表 */
    List<IpAdjustStepBo> queryAdjustStepList(@Param("adjustLogId") Long adjustLogId);

    /** 查询指定批次或调库记录的流程步骤列表 */
    List<IpAdjustStepBo> queryAdjustStepByBatchList(@Param("adjustLogId") Long adjustLogId,
                                                       @Param("adjustBatchNo") String adjustBatchNo);

    /** 根据 ID 查询流程步骤 */
    IpAdjustStepBo queryAdjustStepById(@Param("id") Long id);

    /** 按处理人查询同批次同节点待处理步骤 */
    IpAdjustStepBo queryPendingStepByHandler(@Param("adjustLogId") Long adjustLogId,
                                             @Param("adjustBatchNo") String adjustBatchNo,
                                             @Param("flowNodeId") Long flowNodeId,
                                             @Param("handlerId") String handlerId);

    /** 更新当前待处理步骤的处理结果 */
    int editAdjustStepProcess(@Param("id") Long id,
                              @Param("stepStatus") String stepStatus,
                              @Param("processAction") String processAction,
                              @Param("processComment") String processComment);

    /** 跳过同批次同节点的其他待处理步骤 */
    int editOtherPendingStepSkipped(@Param("id") Long id,
                                    @Param("adjustLogId") Long adjustLogId,
                                    @Param("adjustBatchNo") String adjustBatchNo,
                                    @Param("flowNodeId") Long flowNodeId,
                                    @Param("stepStatus") String stepStatus);

    /** 查询同批次同节点剩余待处理步骤数量 */
    int queryPendingStepCountByNode(@Param("adjustLogId") Long adjustLogId,
                                    @Param("adjustBatchNo") String adjustBatchNo,
                                    @Param("flowNodeId") Long flowNodeId);

    /** 查询指定批次或调库记录的调库日志列表 */
    List<IpAdjustLogBo> queryAdjustLogListForAudit(@Param("adjustLogId") Long adjustLogId,
                                                   @Param("adjustBatchNo") String adjustBatchNo);

    /** 更新指定批次或调库记录的审核状态 */
    int editAdjustLogAuditStatus(@Param("adjustLogId") Long adjustLogId,
                                 @Param("adjustBatchNo") String adjustBatchNo,
                                 @Param("auditStatus") String auditStatus);

    /** 将活动调库记录更新为最终状态 */
    int editActiveAdjustLogAuditStatus(@Param("adjustLogId") Long adjustLogId,
                                       @Param("adjustBatchNo") String adjustBatchNo,
                                       @Param("auditStatus") String auditStatus);

    /** 根据证券类型编码查询所属大类（dict_security_type.category_type） */
    String queryCategoryTypeBySecurityType(@Param("securityType") String securityType);

    /** 查询 CRMW 凭证是否已在目标池（audit_status=20），用于调入「凭证已在池」校验 */
    boolean queryCrmwAlreadyInPool(@Param("crmwScode") String crmwScode,
                                   @Param("crmwStype") String crmwStype,
                                   @Param("targetPoolId") Long targetPoolId);

    /** 查询 CRMW 凭证与标的证券组合是否在目标池（audit_status=20），用于调出「组合在池」校验 */
    boolean queryCrmwComboInPool(@Param("crmwScode") String crmwScode,
                                 @Param("crmwStype") String crmwStype,
                                 @Param("securityCode") String securityCode,
                                 @Param("targetPoolId") Long targetPoolId);
}
