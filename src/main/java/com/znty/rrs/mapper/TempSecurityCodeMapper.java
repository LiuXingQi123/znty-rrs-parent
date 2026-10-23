package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotCrmwBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 临时代码管理数据访问接口
 */
@Mapper
public interface TempSecurityCodeMapper {

    /** 分页查询临时代码列表 */
    List<TempSecurityCodeDto> queryTempSecurityCodePage(TempSecurityCodeReq req);

    /** 根据 ID 查询临时代码 */
    TempSecurityCodeBo queryTempSecurityCodeById(@Param("id") Long id);

    /** 查询本次转正涉及的普通池，用于加载池配置和校验转换计划。 */
    List<Long> queryOrdinaryConversionPoolIdList(TempSecurityCodeBo bo);

    /** 查询临时证券的普通池流程中申请，CRMW 申请由对应查询单独读取。 */
    List<IpAdjustLogBo> queryOrdinaryPendingLogList(TempSecurityCodeBo bo);

    /** 查询临时代码作为 CRMW 标的的流程中申请。 */
    List<IpAdjustLogBo> queryCrmwPendingLogList(TempSecurityCodeBo bo);

    /** 查询同一凭证下正式标的代码组的当前在池记录。 */
    List<IpPoolStatusBo> queryFormalGroupCrmwPoolStatusList(@Param("codes") List<String> codes,
            @Param("crmwScode") String crmwScode, @Param("crmwStype") String crmwStype);

    /** 查询同一凭证下正式标的代码组的流程中申请。 */
    List<IpAdjustLogBo> queryFormalGroupCrmwPendingLogList(@Param("codes") List<String> codes,
            @Param("crmwScode") String crmwScode, @Param("crmwStype") String crmwStype);

    /** 查询 CRMW 标的证券快照，保留原申请填写的业务信息。 */
    AdjustSecuritySnapshotCrmwBo queryCrmwConversionSnapshot(@Param("logId") Long logId);

    /** 更新原 CRMW 申请快照的标的证券身份。 */
    int editCrmwConversionSnapshotIdentity(AdjustSecuritySnapshotCrmwBo bo);

    /** 查询正式代码组当前全部普通池状态。 */
    List<IpPoolStatusBo> queryFormalGroupPoolStatusList(@Param("codes") List<String> codes);

    /** 查询正式代码组在信用债大库（含各级库）的独立流程中申请。 */
    List<IpAdjustLogBo> queryFormalGroupPendingLogList(@Param("codes") List<String> codes);

    /** 查询本次转换涉及的普通池配置。 */
    List<InvestmentPoolBo> queryConversionPoolList(@Param("ids") List<Long> ids);

    /** 替换普通池或 CRMW 流程中主项的证券身份与原因，保留原审批信息。 */
    int editConversionPendingLog(@Param("log") IpAdjustLogBo log, @Param("tempCode") String tempCode);

    /** 查询证券快照，保留本笔调库已填写的业务信息。 */
    AdjustSecuritySnapshotBo queryConversionSnapshot(@Param("logId") Long logId);

    /** 更新在途证券快照的实际证券身份。 */
    int editConversionSnapshotIdentity(AdjustSecuritySnapshotBo bo);

    /** 转正时条件删除普通池当前状态并返回实际影响行数。 */
    int deleteOrdinaryPoolStatusForConversion(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /** 根据 ID 查询临时代码详情 */
    TempSecurityCodeDto queryTempSecurityCodeDetail(@Param("id") Long id);

    /** 查询已由外部数据补齐正式证券代码、等待定时替换的临时代码 */
    List<TempSecurityCodeBo> queryJobReadyTempSecurityCodeList();

    /** 查询临时代码未删除记录数量 */
    int queryTempSecurityCodeCount(@Param("tempSecurityCode") String tempSecurityCode, @Param("excludeId") Long excludeId);

    /**
     * 判断证券代码是否为有效临时代码（status=temporary，按 temp_security_code 匹配）。
     * 对齐老系统 sdc_sirm_tempsecurity.lscode 识别方式。
     */
    int queryTemporaryCodeCountBySecurityCode(@Param("securityCode") String securityCode);

    /** 按关键字查询发行主体选项（wind_compintroduction 按 COMP_ID 查询，最多 50 条） */
    List<TempSecurityCodeDto.CompanyOption> queryCompanyOptionList(TempSecurityCodeReq req);

    /** 根据主体代码查询发行主体 */
    TempSecurityCodeDto.CompanyOption queryCompanyByCode(@Param("companyCode") String companyCode);

    /** 按关键字远程查询正式证券（rrs_securityinfo，最多 50 条） */
    List<TempSecurityCodeDto.FormalSecurityOption> queryFormalSecurityOptionList(TempSecurityCodeReq req);

    /** 根据正式证券代码查询正式证券选项 */
    TempSecurityCodeDto.FormalSecurityOption queryFormalSecurityByCode(@Param("securityCode") String securityCode);

    /** 查询证券类型选项 */
    List<TempSecurityCodeDto.SecurityTypeOption> querySecurityTypeList();

    /** 查询证券类型数量 */
    int querySecurityTypeCount(@Param("securityType") String securityType);

    /** 根据正式证券代码查询证券信息数量 */
    int querySecurityInfoCount(@Param("securityCode") String securityCode);

    /** 新增证券信息 */
    void addSecurityInfo(TempSecurityCodeBo bo);

    /** 更新证券信息 */
    void editSecurityInfo(TempSecurityCodeBo bo);

    /** 取消发行时更新临时证券主数据 */
    void editSecurityInfoToCancelled(TempSecurityCodeBo bo);

    /** 转正式时禁用临时证券主数据 */
    void editTempSecurityInfoToDisabled(TempSecurityCodeBo bo);

    /** 新增临时代码 */
    void addTempSecurityCode(TempSecurityCodeBo bo);

    /** 更新临时代码为已更新，返回影响行数 */
    int editTempSecurityCodeToUpdated(@Param("bo") TempSecurityCodeBo bo);

    /** 取消发行临时代码 */
    void editTempSecurityCodeToCancelled(TempSecurityCodeBo bo);

    /** 删除临时代码 */
    void deleteTempSecurityCode(TempSecurityCodeBo bo);

    /** 查询核心业务表引用数量 */
    int queryCoreReferenceCount(TempSecurityCodeBo bo);

    /** 查询在途调库日志证券引用 ID（audit_status in 00/11） */
    List<Long> queryPendingAdjustLogSecurityReferenceIdList(TempSecurityCodeBo bo);

    /** 查询有效在池状态列表（临时证券） */
    List<IpPoolStatusBo> queryActivePoolStatusList(TempSecurityCodeBo bo);

    /** 查询正式证券是否已在指定池 */
    int queryActivePoolStatusCount(@Param("securityCode") String securityCode,
                                   @Param("securityType") String securityType,
                                   @Param("targetPoolId") Long targetPoolId);

    /** 按主键软删除池状态 */
    void deletePoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /** 查询在途调库日志 CRMW 引用 ID 列表 */
    List<Long> queryPendingAdjustLogCrmwReferenceIdList(TempSecurityCodeBo bo);

    /** 查询当前池状态 CRMW 引用 ID 列表 */
    List<Long> queryPoolStatusCrmwReferenceIdList(TempSecurityCodeBo bo);

    /** 查询 CRMW 池有效在池状态（security 为临时码） */
    List<IpPoolStatusBo> queryActiveCrmwPoolStatusList(TempSecurityCodeBo bo);

    /** 查询正式证券是否已在 CRMW 池 */
    int queryActiveCrmwPoolStatusCount(@Param("securityCode") String securityCode,
                                       @Param("securityType") String securityType,
                                       @Param("targetPoolId") Long targetPoolId);

    /** 按主键软删除 CRMW 池状态 */
    int deleteCrmwPoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /** 查询 CRMW 池状态 CRMW 引用 ID 列表 */
    List<Long> queryCrmwPoolStatusCrmwReferenceIdList(TempSecurityCodeBo bo);

    /** 批量替换在途调库日志证券引用 */
    void editAdjustLogSecurityReference(@Param("bo") TempSecurityCodeBo bo, @Param("idList") List<Long> idList);

    /** 批量替换在途调库日志 CRMW 引用 */
    void editAdjustLogCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("idList") List<Long> idList);

    /** 批量替换当前池状态 CRMW 引用 */
    void editPoolStatusCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("idList") List<Long> idList);

    /** 批量替换 CRMW 池状态 CRMW 引用 */
    void editCrmwPoolStatusCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("idList") List<Long> idList);

    /** 新增 CRMW 池状态 */
    void addCrmwPoolStatus(IpPoolStatusBo bo);

    /** 新增临时代码替换日志，返回实际写入行数 */
    int addTempSecurityCodeUpdateLog(TempSecurityCodeBo bo);
}
