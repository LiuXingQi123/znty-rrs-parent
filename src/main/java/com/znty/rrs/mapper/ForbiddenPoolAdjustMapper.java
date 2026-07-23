package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDto;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.securitypooladjust.PoolStatusDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 禁投池主体调整数据访问接口
 */
@Mapper
public interface ForbiddenPoolAdjustMapper {

    /** 分页查询公司主体 */
    List<ForbiddenPoolAdjustDto> queryCompanyPage(ForbiddenPoolAdjustReq req);

    /** 按主体代码查询公司主体 */
    ForbiddenPoolAdjustDto queryCompanyDetail(@Param("companyCode") String companyCode);

    /** 批量查询主体旗下债券数量 */
    List<ForbiddenPoolAdjustDto.CompanyBondCount> queryCompanyBondCountList(
            @Param("companyCodes") List<String> companyCodes);

    /** 查询当前主体所在池 */
    List<ForbiddenPoolAdjustDto.PoolStatus> queryCompanyPoolStatusList(
            @Param("companyCode") String companyCode);

    /** 查询主体旗下债券所在池汇总 */
    List<ForbiddenPoolAdjustDto.PoolStatus> queryCompanyBondPoolList(
            @Param("companyCode") String companyCode);

    /** 查询主体旗下债券明细 */
    List<ForbiddenPoolAdjustDto.CompanyBond> queryCompanyBondList(
            @Param("companyCode") String companyCode,
            @Param("targetPoolId") Long targetPoolId);

    /** 查询主体调入时需要同步的未到期非 ABS 债券 */
    List<SecurityInfoBo> queryCompanyInboundBondForAutoList(@Param("companyCode") String companyCode,
                                                            @Param("targetPoolId") Long targetPoolId);

    /** 查询主体调出时需要同步的当前在池非 ABS 债券 */
    List<SecurityInfoBo> queryCompanyOutboundBondForAutoList(@Param("companyCode") String companyCode,
                                                             @Param("targetPoolId") Long targetPoolId);

    /** 查询主体当前有效所在池 ID */
    List<Long> queryCompanyCurrentPoolIdList(@Param("companyCode") String companyCode);

    /** 查询主体是否存在进行中的调库流程 */
    boolean queryCompanyHasPendingProcess(@Param("companyCode") String companyCode);

    /** 查询主体当前进行中步骤名称 */
    String queryCompanyPendingProcessNodeLabel(@Param("companyCode") String companyCode);

    /** 分页查询证券列表 */
    List<SecurityInfoDto> querySecurityPage(@Param("securityCode") String securityCode,
                                           @Param("securityShortName") String securityShortName,
                                           @Param("issuer") String issuer);

    /** 根据证券代码查询证券详情 */
    SecurityInfoDetailDto querySecurityDetail(@Param("securityCode") String securityCode);

    /** 根据证券代码查询证券基础信息实体 */
    SecurityInfoBo querySecurityBoByCode(@Param("securityCode") String securityCode);

    /** 根据主体代码查询禁投池调整所需的主体基础信息 */
    SecurityInfoBo queryCompanySecurityBoByCode(@Param("companyCode") String companyCode);

    /** 新增调库记录（非直通流程，audit_status='00' 流程中） */
    int addAdjustLog(IpAdjustLogBo bo);

    /** 新增入池状态记录（直通流程，audit_status='20' 即时生效） */
    int addPoolStatus(IpAdjustLogBo bo);

    /** 软删除入池状态（直通调出流程，将目标池中有效记录标记为已删除） */
    int deletePoolStatusSoft(@Param("securityCode") String securityCode,
                             @Param("targetPoolId") Long targetPoolId);

    /** 更新调库详情页传入的证券基础信息字段 */
    int editSecurityInfoForAdjust(SecurityPoolAdjustSubmitReq req);

    /** 根据证券代码和可选目标池查询调库记录列表 */
    List<IpAdjustLogBo> queryAdjustLogList(@Param("securityCode") String securityCode,
                                           @Param("adjustBatchNo") String adjustBatchNo);

    /** 查询操作人近期有效的手工主体调库记录 */
    List<IpAdjustLogBo> queryRecentManualAdjustLogList(@Param("companyCode") String companyCode,
                                                       @Param("adjusterId") String adjusterId,
                                                       @Param("seconds") int seconds);

    /** 查询主体活动流程涉及的手工调库目标池 ID */
    List<Long> queryPendingManualTargetPoolIdList(@Param("companyCode") String companyCode,
                                                  @Param("excludeBatchNo") String excludeBatchNo);

    /** 查询当前证券所在池列表 */
    List<PoolStatusDto> querySecurityPoolStatusList(@Param("securityCode") String securityCode);

    /** 查询当前证券主体（发行人）所在池列表 */
    List<PoolStatusDto> queryIssuerPoolStatusList(@Param("securityCode") String securityCode);

    /** 查询证券当前有效所在池 ID 列表（audit_status=20） */
    List<Long> querySecurityCurrentPoolIdList(@Param("securityCode") String securityCode);

    /** 查询目标投资池当前有效证券数量 */
    int queryPoolCurrentCount(@Param("poolId") Long poolId);

    /** 查询证券在目标池的入池时间（audit_status=20），用于调出冻结期校验 */
    Date queryPoolEntryTime(@Param("securityCode") String securityCode,
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

    /** 查询同主体在目标池中已有债券的最大剩余期限天数（date_exists） */
    BigDecimal queryIssuerTargetPoolMaxRemainDays(@Param("securityCode") String securityCode,
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
}

