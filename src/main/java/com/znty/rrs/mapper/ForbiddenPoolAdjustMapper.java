package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
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

    /**
     * 分页查询公司主体
     *
     * @param req 主体代码和名称等筛选条件
     * @return 符合条件的禁投池调整候选列表
     */
    List<ForbiddenPoolAdjustDto> queryCompanyPage(ForbiddenPoolAdjustReq req);

    /**
     * 按主体代码查询公司主体
     *
     * @param companyCode 主体代码
     * @return 禁投池调整候选，未找到时为 null
     */
    ForbiddenPoolAdjustDto queryCompanyDetail(@Param("companyCode") String companyCode);

    /**
     * 批量查询主体旗下债券数量
     *
     * @param companyCodes 主体代码列表
     * @return 符合条件的各发行主体的关联债券数量列表
     */
    List<ForbiddenPoolAdjustDto.CompanyBondCount> queryCompanyBondCountList(
            @Param("companyCodes") List<String> companyCodes);

    /**
     * 查询当前主体所在池
     *
     * @param companyCode 主体代码
     * @return 符合条件的主体在池状态列表
     */
    List<ForbiddenPoolAdjustDto.PoolStatus> queryCompanyPoolStatusList(
            @Param("companyCode") String companyCode);

    /**
     * 查询主体旗下债券所在池汇总
     *
     * @param companyCode 主体代码
     * @return 符合条件的主体在池状态列表
     */
    List<ForbiddenPoolAdjustDto.PoolStatus> queryCompanyBondPoolList(
            @Param("companyCode") String companyCode);

    /**
     * 查询主体旗下债券明细
     *
     * @param companyCode 主体代码
     * @param targetPoolId 目标投资池 ID
     * @return 符合条件的发行主体关联债券列表
     */
    List<ForbiddenPoolAdjustDto.CompanyBond> queryCompanyBondList(
            @Param("companyCode") String companyCode,
            @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询主体当前有效所在池 ID
     *
     * @param companyCode 主体代码
     * @return 主体当前有效所在池 ID 列表
     */
    List<Long> queryCompanyCurrentPoolIdList(@Param("companyCode") String companyCode);

    /**
     * 查询主体是否存在进行中的调库流程
     *
     * @param companyCode 主体代码
     * @return 主体存在带待处理步骤的调库流程时返回 true
     */
    boolean queryCompanyHasPendingProcess(@Param("companyCode") String companyCode);

    /**
     * 查询主体当前进行中步骤名称
     *
     * @param companyCode 主体代码
     * @return 主体当前待处理步骤名称，无匹配步骤时为 null
     */
    String queryCompanyPendingProcessNodeLabel(@Param("companyCode") String companyCode);

    /**
     * 根据证券代码查询证券基础信息实体
     *
     * @param securityCode 证券代码
     * @return 证券主档信息，未找到时为 null
     */
    SecurityInfoBo querySecurityBoByCode(@Param("securityCode") String securityCode);

    /**
     * 根据主体代码查询禁投池调整所需的主体基础信息
     *
     * @param companyCode 主体代码
     * @return 证券主档信息，未找到时为 null
     */
    SecurityInfoBo queryCompanySecurityBoByCode(@Param("companyCode") String companyCode);

    /**
     * 新增调库记录（非直通流程，audit_status='00' 流程中）
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的调库日志
     * @return 受影响的行数
     */
    int addAdjustLog(IpAdjustLogBo bo);

    /**
     * 新增入池状态记录（直通流程，audit_status='20' 即时生效）
     *
     * @param bo 待写入的调库日志
     * @return 受影响的行数
     */
    int addPoolStatus(IpAdjustLogBo bo);

    /**
     * 软删除入池状态（直通调出流程，将目标池中有效记录标记为已删除）
     *
     * @param securityCode 证券代码
     * @param targetPoolId 目标投资池 ID
     * @return 受影响的行数
     */
    int deletePoolStatusSoft(@Param("securityCode") String securityCode,
                             @Param("targetPoolId") Long targetPoolId);

    /**
     * 更新调库详情页传入的证券基础信息字段
     *
     * @param req 证券代码及本次调整需要写回主档的字段
     * @return 受影响的行数
     */
    int editSecurityInfoForAdjust(SecurityPoolAdjustSubmitReq req);

    /**
     * 查询调库记录列表
     *
     * @param securityCode 主体代码（无批无 id 时必填；在途查询用）
     * @param adjustBatchNo 有则返回同批全部调整对象（优先于 adjustLogId）
     * @param adjustLogId 无批时按主键回看单条兜底
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryAdjustLogList(@Param("securityCode") String securityCode,
                                           @Param("adjustBatchNo") String adjustBatchNo,
                                           @Param("adjustLogId") Long adjustLogId);

    /**
     * 查询操作人近期有效的手工主体调库记录
     *
     * @param companyCode 主体代码
     * @param adjusterId 发起调库的用户 ID
     * @param seconds 查找近期重复提交的秒数
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryRecentManualAdjustLogList(@Param("companyCode") String companyCode,
                                                       @Param("adjusterId") String adjusterId,
                                                       @Param("seconds") int seconds);

    /**
     * 查询主体活动流程涉及的手工调库目标池 ID
     *
     * @param companyCode 主体代码
     * @param excludeBatchNo 需要排除的调库批次号，空值时不排除
     * @return 在途手工调库流程占用的目标池 ID 列表
     */
    List<Long> queryPendingManualTargetPoolIdList(@Param("companyCode") String companyCode,
                                                  @Param("excludeBatchNo") String excludeBatchNo);

    /**
     * 查询当前证券所在池列表
     *
     * @param securityCode 证券代码
     * @return 符合条件的有效在池状态列表
     */
    List<PoolStatusDto> querySecurityPoolStatusList(@Param("securityCode") String securityCode);

    /**
     * 查询当前证券主体（发行人）所在池列表
     *
     * @param securityCode 证券代码
     * @return 符合条件的有效在池状态列表
     */
    List<PoolStatusDto> queryIssuerPoolStatusList(@Param("securityCode") String securityCode);

    /**
     * 查询证券当前有效所在池 ID 列表（audit_status=20）
     *
     * @param securityCode 证券代码
     * @return 证券当前有效所在池 ID 列表
     */
    List<Long> querySecurityCurrentPoolIdList(@Param("securityCode") String securityCode);

    /**
     * 查询目标投资池当前有效证券数量
     *
     * @param poolId 投资池 ID
     * @return 目标池当前有效在池数量
     */
    int queryPoolCurrentCount(@Param("poolId") Long poolId);

    /**
     * 查询证券在目标池的入池时间（audit_status=20），用于调出冻结期校验
     *
     * @param securityCode 证券代码
     * @param targetPoolId 目标投资池 ID
     * @return 证券当前在目标池的入池时间，无匹配记录时为 null
     */
    Date queryPoolEntryTime(@Param("securityCode") String securityCode,
                                      @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询证券是否在全局禁投池（forbidden，audit_status=20），用于调入禁投池校验
     *
     * @param securityCode 证券代码
     * @return 在全局禁投池中有生效记录时返回 true
     */
    boolean querySecurityInForbiddenPool(@Param("securityCode") String securityCode);

    /**
     * 查询指定日期是否在目标池的开放区间内（ip_pool_open_day，open_day_adjust=1 时用）
     *
     * @param poolId 投资池 ID
     * @param today 用于校验的当前日期
     * @return 指定日期落在目标池开放区间内时返回 true
     */
    boolean queryPoolInOpenDay(@Param("poolId") Long poolId, @Param("today") String today);

    /**
     * 查询各投资池当前有效证券数量
     *
     * @return 符合条件的投资池与当前在池数量列表
     */
    List<PoolDto> queryPoolCurrentCountList();

    /**
     * 查询全量投资池关系配置（不限关系类型）
     *
     * @return 符合条件的投资池关系配置列表
     */
    List<PoolRelationBo> queryAllPoolRelationList();

    /**
     * 查询证券是否存在进行中的调库流程（以是否存在待处理步骤为准）
     *
     * @param securityCode 证券代码
     * @return 存在带待处理步骤的调库流程时返回 true
     */
    boolean querySecurityHasPendingProcess(@Param("securityCode") String securityCode);

    /**
     * 查询证券当前进行中流程所在步骤名称
     *
     * @param securityCode 证券代码
     * @return 当前待处理步骤名称，无匹配步骤时为 null
     */
    String querySecurityPendingProcessNodeLabel(@Param("securityCode") String securityCode);

    /**
     * 查询当前证券是否在观察池（pool_type='observe'，audit_status='20'）
     *
     * @param securityCode 证券代码
     * @return 证券在观察池中有生效记录时返回 true
     */
    boolean querySecurityInObservePool(@Param("securityCode") String securityCode);

    /**
     * 查询证券发行主体是否在观察池（主体级 ip_pool_status.security_code = issuer_code）
     *
     * @param securityCode 证券代码
     * @return 发行主体在观察池中有生效记录时返回 true
     */
    boolean queryIssuerInObservePool(@Param("securityCode") String securityCode);

    /**
     * 查询同主体在目标池中已有债券的最大剩余期限天数（date_exists）
     *
     * @param securityCode 证券代码
     * @param targetPoolId 目标投资池 ID
     * @return 同主体债券在目标池的最大剩余天数，无可用天数时为 null
     */
    BigDecimal queryIssuerTargetPoolMaxRemainDays(@Param("securityCode") String securityCode,
                                               @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询指定主体近 6 个月已通过且关联有效信评附件的主体调入记录
     *
     * @param securityCode 主体代码
     * @return 存在近 6 个月已通过且关联有效信评附件的调入记录时返回 true
     */
    boolean queryHasRecentInboundWithReport(@Param("securityCode") String securityCode);

    /**
     * 新增流程步骤记录
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的调库流程步骤
     * @return 受影响的行数
     */
    int addAdjustStep(IpAdjustStepBo bo);

    /**
     * 查询指定调库记录的流程步骤列表
     *
     * @param adjustLogId 调库日志 ID
     * @return 符合条件的调库流程步骤列表
     */
    List<IpAdjustStepBo> queryAdjustStepList(@Param("adjustLogId") Long adjustLogId);

    /**
     * 查询指定批次或调库记录的流程步骤列表
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @return 符合条件的调库流程步骤列表
     */
    List<IpAdjustStepBo> queryAdjustStepByBatchList(@Param("adjustLogId") Long adjustLogId,
                                                       @Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 根据 ID 查询流程步骤
     *
     * @param id 调库步骤 ID
     * @return 调库流程步骤，未找到时为 null
     */
    IpAdjustStepBo queryAdjustStepById(@Param("id") Long id);

    /**
     * 按处理人查询同批次同节点待处理步骤
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param flowNodeId 流程节点 ID
     * @param handlerId 流程步骤处理人 ID
     * @return 调库流程步骤，未找到时为 null
     */
    IpAdjustStepBo queryPendingStepByHandler(@Param("adjustLogId") Long adjustLogId,
                                             @Param("adjustBatchNo") String adjustBatchNo,
                                             @Param("flowNodeId") Long flowNodeId,
                                             @Param("handlerId") String handlerId);

    /**
     * 更新当前待处理步骤的处理结果
     *
     * @param id 调库步骤 ID
     * @param stepStatus 流程步骤状态代码
     * @param processAction 步骤处理动作
     * @param processComment 步骤处理意见
     * @return 受影响的行数
     */
    int editAdjustStepProcess(@Param("id") Long id,
                              @Param("stepStatus") String stepStatus,
                              @Param("processAction") String processAction,
                              @Param("processComment") String processComment);

    /**
     * 跳过同批次同节点的其他待处理步骤
     *
     * @param id 已处理的流程步骤 ID，用于排除该步骤
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param flowNodeId 流程节点 ID
     * @param stepStatus 流程步骤状态代码
     * @return 受影响的行数
     */
    int editOtherPendingStepSkipped(@Param("id") Long id,
                                    @Param("adjustLogId") Long adjustLogId,
                                    @Param("adjustBatchNo") String adjustBatchNo,
                                    @Param("flowNodeId") Long flowNodeId,
                                    @Param("stepStatus") String stepStatus);

    /**
     * 查询同批次同节点剩余待处理步骤数量
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param flowNodeId 流程节点 ID
     * @return 同批次同节点剩余的待处理步骤数
     */
    int queryPendingStepCountByNode(@Param("adjustLogId") Long adjustLogId,
                                    @Param("adjustBatchNo") String adjustBatchNo,
                                    @Param("flowNodeId") Long flowNodeId);

    /**
     * 查询指定批次或调库记录的调库日志列表
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryAdjustLogListForAudit(@Param("adjustLogId") Long adjustLogId,
                                                   @Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 更新驳回待修改批次的调整原因和意见，未传字段保持原值
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param adjustReason 调库原因
     * @param adjustAdvice 调库建议
     * @return 受影响的行数
     */
    int editAdjustLogReasonAdvice(@Param("adjustLogId") Long adjustLogId,
                                  @Param("adjustBatchNo") String adjustBatchNo,
                                  @Param("adjustReason") String adjustReason,
                                  @Param("adjustAdvice") String adjustAdvice);

    /**
     * 更新指定批次或调库记录的审核状态
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param auditStatus 审批状态代码
     * @return 受影响的行数
     */
    int editAdjustLogAuditStatus(@Param("adjustLogId") Long adjustLogId,
                                 @Param("adjustBatchNo") String adjustBatchNo,
                                 @Param("auditStatus") String auditStatus);

    /**
     * 将活动调库记录更新为最终状态
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param auditStatus 审批状态代码
     * @return 受影响的行数
     */
    int editActiveAdjustLogAuditStatus(@Param("adjustLogId") Long adjustLogId,
                                       @Param("adjustBatchNo") String adjustBatchNo,
                                       @Param("auditStatus") String auditStatus);

    /**
     * 根据证券类型编码查询所属大类（dict_security_type.category_type）
     *
     * @param securityType 证券类型代码
     * @return 证券大类代码，无匹配类型时为 null
     */
    String queryCategoryTypeBySecurityType(@Param("securityType") String securityType);
}

