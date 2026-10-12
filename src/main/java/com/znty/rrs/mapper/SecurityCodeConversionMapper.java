package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SecurityCodeConversionBo;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 独立证券代码转换数据访问接口。
 */
@Mapper
public interface SecurityCodeConversionMapper {

    /** 按代码顺序锁定并查询临时及正式证券主数据 */
    List<SecurityCodeConversionBo> querySecurityInfoListForUpdate(
            @Param("securityCodeList") List<String> securityCodeList);

    /** 查询正式证券类型所属大类 */
    String querySecurityCategoryType(@Param("securityType") String securityType);

    /** 锁定并查询仍由原临时代码管理流程负责的登记记录 */
    List<Long> queryActiveTempSecurityRegistrationIdListForUpdate(
            @Param("tempSecurityCode") String tempSecurityCode);

    /** 查询代码转换后会形成重复业务对象的在途调库日志数量 */
    int queryPendingAdjustLogConflictCount(SecurityCodeConversionBo bo);

    /** 锁定并查询受代码转换影响的未提交导入批次 */
    List<Long> queryAffectedImportBatchIdListForUpdate(SecurityCodeConversionBo bo);

    /** 锁定并查询受影响批次的全部有效导入明细 */
    List<Long> queryAffectedImportDetailIdListForUpdate(
            @Param("batchIdList") List<Long> batchIdList);

    /** 作废受代码转换影响的导入明细 */
    int deleteAffectedImportDetailListSoft(
            @Param("batchIdList") List<Long> batchIdList,
            @Param("updateTime") Date updateTime);

    /** 作废受代码转换影响的导入批次 */
    int deleteAffectedImportBatchListSoft(
            @Param("batchIdList") List<Long> batchIdList,
            @Param("updateTime") Date updateTime);

    /** 查询在途调库日志证券引用主键列表 */
    List<Long> queryPendingAdjustLogSecurityReferenceIdList(SecurityCodeConversionBo bo);

    /** 查询在途普通调库证券快照引用主键列表 */
    List<Long> queryPendingSecuritySnapshotReferenceIdList(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 查询在途 CRMW 调库标的证券快照引用主键列表 */
    List<Long> queryPendingCrmwSecuritySnapshotReferenceIdList(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 替换在途调库日志证券引用 */
    int editAdjustLogSecurityReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 替换在途普通调库证券快照引用 */
    int editPendingSecuritySnapshotReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 替换在途 CRMW 调库标的证券快照引用 */
    int editPendingCrmwSecuritySnapshotReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 查询在途调库日志 CRMW 凭证引用主键列表 */
    List<Long> queryPendingAdjustLogCrmwReferenceIdList(SecurityCodeConversionBo bo);

    /** 查询在途 CRMW 凭证快照引用主键列表 */
    List<Long> queryPendingCrmwSnapshotReferenceIdList(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 替换在途调库日志 CRMW 凭证引用 */
    int editAdjustLogCrmwReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 替换在途 CRMW 凭证快照引用 */
    int editPendingCrmwSnapshotReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 查询有效普通池状态列表 */
    List<SecurityCodeConversionBo> queryActivePoolStatusList(SecurityCodeConversionBo bo);

    /** 查询正式证券是否已在指定普通池 */
    int queryActivePoolStatusCount(
            @Param("securityCode") String securityCode,
            @Param("targetPoolId") Long targetPoolId);

    /** 软删除指定普通池状态 */
    int deletePoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /** 新增调库日志 */
    int addAdjustLog(SecurityCodeConversionBo bo);

    /** 从原普通调库快照复制代码转换快照 */
    int addSecuritySnapshotFromSource(
            @Param("sourceAdjustLogId") Long sourceAdjustLogId,
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("submitterId") String submitterId,
            @Param("formal") boolean formal,
            @Param("bo") SecurityCodeConversionBo bo,
            @Param("updateTime") Date updateTime);

    /** 从临时证券主数据补建代码转换普通快照 */
    int addSecuritySnapshotFromMaster(
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("submitterId") String submitterId,
            @Param("formal") boolean formal,
            @Param("bo") SecurityCodeConversionBo bo,
            @Param("updateTime") Date updateTime);

    /** 从原 CRMW 调库快照复制代码转换快照 */
    int addCrmwSecuritySnapshotFromSource(
            @Param("sourceAdjustLogId") Long sourceAdjustLogId,
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("submitterId") String submitterId,
            @Param("formal") boolean formal,
            @Param("bo") SecurityCodeConversionBo bo,
            @Param("updateTime") Date updateTime);

    /** 从临时证券主数据补建代码转换 CRMW 快照 */
    int addCrmwSecuritySnapshotFromMaster(
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("submitterId") String submitterId,
            @Param("formal") boolean formal,
            @Param("bo") SecurityCodeConversionBo bo,
            @Param("poolStatus") SecurityCodeConversionBo poolStatus,
            @Param("updateTime") Date updateTime);

    /** 锁定并查询原入池日志的有效附件主键 */
    List<Long> queryActiveAttachmentIdListForUpdate(
            @Param("adjustLogId") Long adjustLogId);

    /** 将原入池日志附件关联复制到正式代码调入日志 */
    int addFormalInAttachmentList(
            @Param("sourceAdjustLogId") Long sourceAdjustLogId,
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("updateTime") Date updateTime);

    /** 新增普通池状态 */
    int addPoolStatus(SecurityCodeConversionBo bo);

    /** 查询普通池当前状态中的 CRMW 凭证引用主键列表 */
    List<Long> queryPoolStatusCrmwReferenceIdList(SecurityCodeConversionBo bo);

    /** 替换普通池当前状态中的 CRMW 凭证引用 */
    int editPoolStatusCrmwReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 查询 CRMW 池有效状态列表 */
    List<SecurityCodeConversionBo> queryActiveCrmwPoolStatusList(SecurityCodeConversionBo bo);

    /** 查询正式证券是否已在指定 CRMW 池组合 */
    int queryActiveCrmwPoolStatusCount(
            @Param("securityCode") String securityCode,
            @Param("crmwScode") String crmwScode,
            @Param("crmwStype") String crmwStype,
            @Param("targetPoolId") Long targetPoolId);

    /** 软删除指定 CRMW 池状态 */
    int deleteCrmwPoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /** 新增 CRMW 池状态 */
    int addCrmwPoolStatus(SecurityCodeConversionBo bo);

    /** 查询 CRMW 池当前状态中的 CRMW 凭证引用主键列表 */
    List<Long> queryCrmwPoolStatusCrmwReferenceIdList(SecurityCodeConversionBo bo);

    /** 替换 CRMW 池当前状态中的 CRMW 凭证引用 */
    int editCrmwPoolStatusCrmwReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /** 查询有效个人收藏引用主键列表 */
    List<Long> queryActiveMySecurityPoolReferenceIdList(SecurityCodeConversionBo bo);

    /** 合并并替换有效个人收藏引用 */
    int editActiveMySecurityPoolReference(SecurityCodeConversionBo bo);

    /** 查询未删除内部报告引用主键列表 */
    List<Long> queryActiveInReportReferenceIdList(SecurityCodeConversionBo bo);

    /** 替换未删除内部报告引用 */
    int editActiveInReportReference(SecurityCodeConversionBo bo);

    /** 查询未删除外部报告引用主键列表 */
    List<Long> queryActiveOutReportReferenceIdList(SecurityCodeConversionBo bo);

    /** 替换未删除外部报告引用 */
    int editActiveOutReportReference(SecurityCodeConversionBo bo);

    /** 查询待处理评级提醒引用主键列表 */
    List<Long> queryOpenGradeRuleAlertReferenceIdList(SecurityCodeConversionBo bo);

    /** 合并并替换待处理评级提醒引用 */
    int editOpenGradeRuleAlertReference(SecurityCodeConversionBo bo);

    /** 查询仍引用临时代码的运行态记录数量 */
    int queryRuntimeReferenceCount(SecurityCodeConversionBo bo);

    /** 新增证券代码转换日志 */
    int addSecurityCodeConversionLog(SecurityCodeConversionBo bo);
}
