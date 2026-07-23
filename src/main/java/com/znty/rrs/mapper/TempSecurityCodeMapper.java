package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpPoolStatusBo;
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

    /** 根据 ID 查询临时代码详情 */
    TempSecurityCodeDto queryTempSecurityCodeDetail(@Param("id") Long id);

    /** 查询临时代码未删除记录数量 */
    int queryTempSecurityCodeCount(@Param("tempSecurityCode") String tempSecurityCode, @Param("excludeId") Long excludeId);

    /**
     * 判断证券代码是否为有效临时代码（status=temporary，按 temp_security_code 匹配）。
     * <p>对齐老系统 sdc_sirm_tempsecurity.lscode 识别方式。
     */
    int queryTemporaryCodeCountBySecurityCode(@Param("securityCode") String securityCode);

    /** 按关键字查询发行主体选项（wind_cbondissuer 按 s_info_compcode 去重，最多 50 条） */
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

    /** 更新临时代码为已更新 */
    void editTempSecurityCodeToUpdated(TempSecurityCodeBo bo);

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
    void deleteCrmwPoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /** 查询 CRMW 池状态 CRMW 引用 ID 列表 */
    List<Long> queryCrmwPoolStatusCrmwReferenceIdList(TempSecurityCodeBo bo);

    /** 批量替换在途调库日志证券引用 */
    void editAdjustLogSecurityReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 批量替换在途调库日志 CRMW 引用 */
    void editAdjustLogCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 批量替换当前池状态 CRMW 引用 */
    void editPoolStatusCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 批量替换 CRMW 池状态 CRMW 引用 */
    void editCrmwPoolStatusCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 新增 CRMW 池状态 */
    void addCrmwPoolStatus(IpPoolStatusBo bo);

    /** 新增临时代码替换日志 */
    void addTempSecurityCodeUpdateLog(TempSecurityCodeBo bo);
}
