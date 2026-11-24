package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDto;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.securitypooladjust.PoolStatusDto;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustSubmitReq;
import com.znty.rrs.entity.report.ReportDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 证券池调库数据访问接口
 */
@Mapper
public interface ForbiddenAbsPoolAdjustMapper {

    /**
     * 分页查询证券列表
     *
     * @param securityCode 证券代码
     * @param securityShortName 证券简称关键字，为空时不筛选
     * @param issuer 发行主体名称关键字，为空时不筛选
     * @param bondYesFlags 需要筛选的债券特征
     * @return 符合条件的证券基本信息列表
     */
    List<SecurityInfoDto> querySecurityPage(@Param("securityCode") String securityCode,
                                           @Param("securityShortName") String securityShortName,
                                           @Param("issuer") String issuer,
                                           @Param("bondYesFlags") List<String> bondYesFlags);

    /**
     * 根据证券代码查询证券详情
     *
     * @param securityCode 证券代码
     * @return 证券详细信息，未找到时为 null
     */
    SecurityInfoDetailDto querySecurityDetail(@Param("securityCode") String securityCode);

    /**
     * 根据证券代码查询证券基础信息实体
     *
     * @param securityCode 证券代码
     * @return 证券主档信息，未找到时为 null
     */
    SecurityInfoBo querySecurityBoByCode(@Param("securityCode") String securityCode);

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
    int editSecurityInfoForAdjust(ForbiddenAbsPoolAdjustSubmitReq req);

    /**
     * 根据证券代码和可选目标池查询调库记录列表
     *
     * @param securityCode 证券代码
     * @param adjustBatchNo 调库批次号
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryAdjustLogList(@Param("securityCode") String securityCode,
                                           @Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 查询操作人近期有效的手工调库记录，用于防止短时间重复提交
     *
     * @param securityCode 证券代码
     * @param adjusterId 发起调库的用户 ID
     * @param seconds 查找近期重复提交的秒数
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryRecentManualAdjustLogList(@Param("securityCode") String securityCode,
                                                       @Param("adjusterId") String adjusterId,
                                                       @Param("seconds") int seconds);

    /**
     * 查询操作人最近一次批量提交的全部手工调库记录
     *
     * @param adjusterId 发起调库的用户 ID
     * @param seconds 查找近期重复提交的秒数
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryRecentBatchManualAdjustLogList(@Param("adjusterId") String adjusterId,
                                                            @Param("seconds") int seconds);

    /**
     * 查询证券活动流程涉及的手工调库目标池 ID
     *
     * @param securityCode 证券代码
     * @param excludeBatchNo 需要排除的调库批次号，空值时不排除
     * @return 在途手工调库流程占用的目标池 ID 列表
     */
    List<Long> queryPendingManualTargetPoolIdList(@Param("securityCode") String securityCode,
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
     * 查询当前证券是否在重点观察名单（pool_type='restricted'，audit_status='20'）
     *
     * @param securityCode 证券代码
     * @return 证券在重点观察名单中有生效记录时返回 true
     */
    boolean querySecurityInRestrictedPool(@Param("securityCode") String securityCode);

    /**
     * 查询证券发行主体是否在重点观察名单（主体级 ip_pool_status.security_code = issuer_code）
     *
     * @param securityCode 证券代码
     * @return 发行主体在重点观察名单中有生效记录时返回 true
     */
    boolean queryIssuerInRestrictedPool(@Param("securityCode") String securityCode);

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
     * 查询6个月内同主体有审批通过调入记录且真实关联有效信评报告
     *
     * @param securityCode 证券代码
     * @return 存在近 6 个月已通过且关联有效信评附件的调入记录时返回 true
     */
    boolean queryHasRecentInboundWithReport(@Param("securityCode") String securityCode);

    /**
     * 查询近6个月当前券最近一条「审批通过+调入+挂有信评报告」的调库日志 ID
     * （对齐老系统 findInvestPoolAdjustHistoryByScodeOrCompancode 券维度）
     *
     * @param securityCode 证券代码
     * @return 当前券最新符合条件的调入日志 ID，无匹配记录时为 null
     */
    Long queryLastInboundLogIdWithCreditReportBySecurity(@Param("securityCode") String securityCode);

    /**
     * 查询近6个月同主体最近一条「审批通过+调入+挂有信评报告」的调库日志 ID
     * （对齐老系统主体兜底；不含当前券时的其他主体债）
     *
     * @param securityCode 证券代码
     * @return 同主体其他券最新符合条件的调入日志 ID，无匹配记录时为 null
     */
    Long queryLastInboundLogIdWithCreditReportByIssuer(@Param("securityCode") String securityCode);

    /**
     * 查询调库日志下首选信评报告附件（手工、内部库、外部库依次优先，同类附件按 ID 降序）
     *
     * @param adjustLogId 调库日志 ID
     * @return 附件记录，未找到时为 null
     */
    SysAttachmentBo queryFirstCreditReportAttachment(@Param("adjustLogId") Long adjustLogId);

    /**
     * 按物理文件路径反查报告库附件（复制到调库日志时复用 file_name）
     *
     * @param fileName 附件文件名
     * @param tableName 关联业务表名
     * @param attachmentCategory 附件类别代码
     * @return 附件记录，未找到时为 null
     */
    SysAttachmentBo queryReportLibraryAttachmentByFileName(
            @Param("fileName") String fileName,
            @Param("tableName") String tableName,
            @Param("attachmentCategory") String attachmentCategory);

    /**
     * 按主键查询内部报告简要信息
     *
     * @param id 内部报告 ID
     * @return 匹配的未删除内部报告，未找到时为 null
     */
    ReportDto queryInReportById(@Param("id") Long id);

    /**
     * 按主键查询外部报告简要信息
     *
     * @param id 外部报告 ID
     * @return 匹配的未删除外部报告，未找到时为 null
     */
    ReportDto queryOutReportById(@Param("id") Long id);

    /**
     * 查询指定天数内同主体+目标池有非简易入库记录（简易流程前提条件，新需求180天）
     *
     * @param securityCode 证券代码
     * @param targetPoolId 目标投资池 ID
     * @param days 向前查询的天数
     * @return 指定天数内存在该主体在目标池的非简易调入记录时返回 true
     */
    boolean queryIssuerHasNonSimpleInboundWithinDays(@Param("securityCode") String securityCode,
                                                      @Param("targetPoolId") Long targetPoolId,
                                                      @Param("days") int days);

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
     * 将流程中或驳回待修改的调库记录更新为最终状态
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
     * 更新指定批次调入调库记录的目标池（评级联动改判用，仅更新 adjust_mode=调入 的记录）
     *
     * @param adjustBatchNo 调库批次号
     * @param targetPoolId 目标投资池 ID
     * @param targetPoolName 目标投资池名称
     * @return 受影响的行数
     */
    int editAdjustLogTargetPool(@Param("adjustBatchNo") String adjustBatchNo,
                                @Param("targetPoolId") Long targetPoolId,
                                @Param("targetPoolName") String targetPoolName);

    /**
     * 根据证券类型编码查询所属大类（dict_security_type.category_type）
     *
     * @param securityType 证券类型代码
     * @return 证券大类代码，无匹配类型时为 null
     */
    String queryCategoryTypeBySecurityType(@Param("securityType") String securityType);
}
