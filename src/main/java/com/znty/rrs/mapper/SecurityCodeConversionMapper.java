package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SecurityCodeConversionBo;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 独立证券代码转换数据访问接口
 */
@Mapper
public interface SecurityCodeConversionMapper {

    /**
     * 按代码顺序锁定并查询临时及正式证券主数据
     *
     * @param securityCodeList 证券代码列表
     * @return 已加锁的临时和正式证券主档，包含代码、全称、简称及证券类型
     */
    List<SecurityCodeConversionBo> querySecurityInfoListForUpdate(
            @Param("securityCodeList") List<String> securityCodeList);

    /**
     * 查询正式证券类型所属大类
     *
     * @param securityType 证券类型代码
     * @return 证券大类代码，无匹配类型时为 null
     */
    String querySecurityCategoryType(@Param("securityType") String securityType);

    /**
     * 锁定并查询仍由原临时代码管理流程负责的登记记录
     *
     * @param tempSecurityCode 临时证券代码
     * @return 仍由原临时代码管理流程负责且已锁定的登记 ID 列表
     */
    List<Long> queryActiveTempSecurityRegistrationIdListForUpdate(
            @Param("tempSecurityCode") String tempSecurityCode);

    /**
     * 查询代码转换后会形成重复业务对象的在途调库日志数量
     *
     * @param bo 用于匹配记录的临时证券代码、正式证券代码、正式证券类型
     * @return 转换后会形成重复业务对象的在途调库日志数量
     */
    int queryPendingAdjustLogConflictCount(SecurityCodeConversionBo bo);

    /**
     * 锁定并查询受代码转换影响的未提交导入批次
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 受影响且已锁定的未提交导入批次 ID 列表
     */
    List<Long> queryAffectedImportBatchIdListForUpdate(SecurityCodeConversionBo bo);

    /**
     * 锁定并查询受影响批次的全部有效导入明细
     *
     * @param batchIdList 导入批次主键列表
     * @return 受影响且已锁定的有效导入明细 ID 列表
     */
    List<Long> queryAffectedImportDetailIdListForUpdate(
            @Param("batchIdList") List<Long> batchIdList);

    /**
     * 作废受代码转换影响的导入明细
     *
     * @param batchIdList 导入批次主键列表
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int deleteAffectedImportDetailListSoft(
            @Param("batchIdList") List<Long> batchIdList,
            @Param("updateTime") Date updateTime);

    /**
     * 作废受代码转换影响的导入批次
     *
     * @param batchIdList 导入批次主键列表
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int deleteAffectedImportBatchListSoft(
            @Param("batchIdList") List<Long> batchIdList,
            @Param("updateTime") Date updateTime);

    /**
     * 查询在途调库日志证券引用主键列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 以该临时代码为证券代码的在途调库日志 ID 列表
     */
    List<Long> queryPendingAdjustLogSecurityReferenceIdList(SecurityCodeConversionBo bo);

    /**
     * 锁定并查询改码后的在途日志，按最终证券及凭证信息生成新快照
     *
     * @param idList 调库日志 ID 列表
     * @return 匹配且已加锁的在途调库日志列表
     */
    List<SecurityCodeConversionBo> queryPendingAdjustLogListForSnapshot(@Param("idList") List<Long> idList);

    /**
     * 替换在途调库日志证券引用
     *
     * @param bo 原临时代码及要写回的正式证券信息
     * @param idList 调库日志 ID 列表
     * @return 受影响的行数
     */
    int editAdjustLogSecurityReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /**
     * 查询在途调库日志 CRMW 凭证引用主键列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 以该临时代码为 CRMW 凭证代码的在途调库日志 ID 列表
     */
    List<Long> queryPendingAdjustLogCrmwReferenceIdList(SecurityCodeConversionBo bo);

    /**
     * 替换在途调库日志 CRMW 凭证引用
     *
     * @param bo 原临时代码及要写回的正式证券信息
     * @param idList 调库日志 ID 列表
     * @return 受影响的行数
     */
    int editAdjustLogCrmwReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /**
     * 查询有效普通池状态列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 临时证券当前生效且已加锁的普通池状态列表
     */
    List<SecurityCodeConversionBo> queryActivePoolStatusList(SecurityCodeConversionBo bo);

    /**
     * 查询正式证券是否已在指定普通池
     *
     * 对匹配的记录加行锁
     *
     * @param securityCode 证券代码
     * @param targetPoolId 目标投资池 ID
     * @return 指定普通池中的有效在池记录数
     */
    int queryActivePoolStatusCount(
            @Param("securityCode") String securityCode,
            @Param("targetPoolId") Long targetPoolId);

    /**
     * 软删除指定普通池状态
     *
     * @param id 普通证券或主体池状态 ID
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int deletePoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /**
     * 新增调库日志
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的调库日志
     * @return 受影响的行数
     */
    int addAdjustLog(SecurityCodeConversionBo bo);

    /**
     * 从原普通调库快照复制代码转换快照
     *
     * @param sourceAdjustLogId 原调库日志 ID
     * @param targetAdjustLogId 新调库日志 ID
     * @param submitterId 提交申请的用户 ID
     * @param formal 转换后的正式证券主档
     * @param bo 转换前后的证券代码及新快照所需的正式证券信息
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int addSecuritySnapshotFromSource(
            @Param("sourceAdjustLogId") Long sourceAdjustLogId,
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("submitterId") String submitterId,
            @Param("formal") boolean formal,
            @Param("bo") SecurityCodeConversionBo bo,
            @Param("updateTime") Date updateTime);

    /**
     * 从目标证券主档新增普通快照，证券代码决定读取正式还是临时主档
     *
     * @param targetAdjustLogId 新调库日志 ID
     * @param submitterId 提交申请的用户 ID
     * @param securityCode 证券代码
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int addSecuritySnapshotFromMaster(
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("submitterId") String submitterId,
            @Param("securityCode") String securityCode,
            @Param("updateTime") Date updateTime);

    /**
     * 从原 CRMW 调库快照复制代码转换快照
     *
     * @param sourceAdjustLogId 原调库日志 ID
     * @param targetAdjustLogId 新调库日志 ID
     * @param submitterId 提交申请的用户 ID
     * @param formal 转换后的正式证券主档
     * @param bo 转换前后的证券代码及新快照所需的正式证券信息
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int addCrmwSecuritySnapshotFromSource(
            @Param("sourceAdjustLogId") Long sourceAdjustLogId,
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("submitterId") String submitterId,
            @Param("formal") boolean formal,
            @Param("bo") SecurityCodeConversionBo bo,
            @Param("updateTime") Date updateTime);

    /**
     * 按日志中的证券代码读取主档新增 CRMW 快照，凭证字段也取本笔日志
     *
     * @param log 用于生成 CRMW 快照的目标调库日志信息
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int addCrmwSecuritySnapshotFromMaster(
            @Param("log") SecurityCodeConversionBo log,
            @Param("updateTime") Date updateTime);

    /**
     * 锁定并查询原入池日志的有效附件主键
     *
     * @param adjustLogId 调库日志 ID
     * @return 原调库日志的有效附件 ID 列表，匹配的附件行已加锁
     */
    List<Long> queryActiveAttachmentIdListForUpdate(
            @Param("adjustLogId") Long adjustLogId);

    /**
     * 将原入池日志附件关联复制到正式代码调入日志
     *
     * @param sourceAdjustLogId 原调库日志 ID
     * @param targetAdjustLogId 新调库日志 ID
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int addFormalInAttachmentList(
            @Param("sourceAdjustLogId") Long sourceAdjustLogId,
            @Param("targetAdjustLogId") Long targetAdjustLogId,
            @Param("updateTime") Date updateTime);

    /**
     * 新增普通池状态
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的普通证券或主体池状态
     * @return 受影响的行数
     */
    int addPoolStatus(SecurityCodeConversionBo bo);

    /**
     * 查询普通池当前状态中的 CRMW 凭证引用主键列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 以该临时代码为 CRMW 凭证代码的普通池状态 ID 列表
     */
    List<Long> queryPoolStatusCrmwReferenceIdList(SecurityCodeConversionBo bo);

    /**
     * 替换普通池当前状态中的 CRMW 凭证引用
     *
     * @param bo 原临时代码及要写回的正式证券信息
     * @param idList 普通证券或主体池状态 ID 列表
     * @return 受影响的行数
     */
    int editPoolStatusCrmwReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /**
     * 查询 CRMW 池有效状态列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 临时证券当前生效且已加锁的 CRMW 池状态列表
     */
    List<SecurityCodeConversionBo> queryActiveCrmwPoolStatusList(SecurityCodeConversionBo bo);

    /**
     * 查询正式证券是否已在指定 CRMW 池组合
     *
     * 对匹配的记录加行锁
     *
     * @param securityCode 证券代码
     * @param crmwScode CRMW 凭证代码
     * @param crmwStype CRMW 凭证的证券类型代码
     * @param targetPoolId 目标投资池 ID
     * @return 符合条件的有效 CRMW 在池记录数
     */
    int queryActiveCrmwPoolStatusCount(
            @Param("securityCode") String securityCode,
            @Param("crmwScode") String crmwScode,
            @Param("crmwStype") String crmwStype,
            @Param("targetPoolId") Long targetPoolId);

    /**
     * 软删除指定 CRMW 池状态
     *
     * @param id CRMW 组合池状态 ID
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int deleteCrmwPoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /**
     * 新增 CRMW 池状态
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的CRMW 组合池状态
     * @return 受影响的行数
     */
    int addCrmwPoolStatus(SecurityCodeConversionBo bo);

    /**
     * 查询 CRMW 池当前状态中的 CRMW 凭证引用主键列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 以该临时代码为 CRMW 凭证代码的 CRMW 池状态 ID 列表
     */
    List<Long> queryCrmwPoolStatusCrmwReferenceIdList(SecurityCodeConversionBo bo);

    /**
     * 替换 CRMW 池当前状态中的 CRMW 凭证引用
     *
     * @param bo 原临时代码及要写回的正式证券信息
     * @param idList CRMW 组合池状态 ID 列表
     * @return 受影响的行数
     */
    int editCrmwPoolStatusCrmwReference(
            @Param("bo") SecurityCodeConversionBo bo, @Param("idList") List<Long> idList);

    /**
     * 查询有效个人收藏引用主键列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 引用临时代码的有效个人自选记录 ID 列表
     */
    List<Long> queryActiveMySecurityPoolReferenceIdList(SecurityCodeConversionBo bo);

    /**
     * 合并并替换有效个人收藏引用
     *
     * @param bo 原临时代码及要写回的正式证券信息
     * @return 受影响的行数
     */
    int editActiveMySecurityPoolReference(SecurityCodeConversionBo bo);

    /**
     * 查询未删除内部报告引用主键列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 引用临时代码的未删除内部报告 ID 列表
     */
    List<Long> queryActiveInReportReferenceIdList(SecurityCodeConversionBo bo);

    /**
     * 替换未删除内部报告引用
     *
     * @param bo 原临时代码及要写回的正式证券信息
     * @return 受影响的行数
     */
    int editActiveInReportReference(SecurityCodeConversionBo bo);

    /**
     * 查询未删除外部报告引用主键列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 引用临时代码的未删除外部报告 ID 列表
     */
    List<Long> queryActiveOutReportReferenceIdList(SecurityCodeConversionBo bo);

    /**
     * 替换未删除外部报告引用
     *
     * @param bo 原临时代码及要写回的正式证券信息
     * @return 受影响的行数
     */
    int editActiveOutReportReference(SecurityCodeConversionBo bo);

    /**
     * 查询待处理评级提醒引用主键列表
     *
     * 按主键升序对匹配记录加行锁
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 引用临时代码的待处理评级提醒 ID 列表
     */
    List<Long> queryOpenGradeRuleAlertReferenceIdList(SecurityCodeConversionBo bo);

    /**
     * 合并并替换待处理评级提醒引用
     *
     * @param bo 原临时代码及要写回的正式证券信息
     * @return 受影响的行数
     */
    int editOpenGradeRuleAlertReference(SecurityCodeConversionBo bo);

    /**
     * 查询仍引用临时代码的运行态记录数量
     *
     * @param bo 用于匹配记录的临时证券代码
     * @return 仍引用临时代码的运行态记录总数
     */
    int queryRuntimeReferenceCount(SecurityCodeConversionBo bo);

    /**
     * 新增证券代码转换日志
     *
     * @param bo 待写入的证券代码转换记录及引用替换结果
     * @return 受影响的行数
     */
    int addSecurityCodeConversionLog(SecurityCodeConversionBo bo);
}
