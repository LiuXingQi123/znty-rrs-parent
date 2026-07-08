package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
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

    /** 查询发行主体选项 */
    List<TempSecurityCodeDto.CompanyOption> queryCompanyOptionList(TempSecurityCodeReq req);

    /** 根据主体 ID 查询发行主体 */
    TempSecurityCodeDto.CompanyOption queryCompanyById(@Param("companyId") Long companyId);

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

    /** 查询调库日志证券引用 ID 列表 */
    List<Long> queryAdjustLogSecurityReferenceIdList(TempSecurityCodeBo bo);

    /** 查询当前池状态证券引用 ID 列表 */
    List<Long> queryPoolStatusSecurityReferenceIdList(TempSecurityCodeBo bo);

    /** 查询调库日志 CRMW 引用 ID 列表 */
    List<Long> queryAdjustLogCrmwReferenceIdList(TempSecurityCodeBo bo);

    /** 查询当前池状态 CRMW 引用 ID 列表 */
    List<Long> queryPoolStatusCrmwReferenceIdList(TempSecurityCodeBo bo);

    /** 查询 CRMW 池状态证券引用 ID 列表 */
    List<Long> queryCrmwPoolStatusSecurityReferenceIdList(TempSecurityCodeBo bo);

    /** 查询 CRMW 池状态 CRMW 引用 ID 列表 */
    List<Long> queryCrmwPoolStatusCrmwReferenceIdList(TempSecurityCodeBo bo);

    /** 批量替换调库日志证券引用 */
    void editAdjustLogSecurityReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 批量替换当前池状态证券引用 */
    void editPoolStatusSecurityReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 批量替换调库日志 CRMW 引用 */
    void editAdjustLogCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 批量替换当前池状态 CRMW 引用 */
    void editPoolStatusCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 批量替换 CRMW 池状态证券引用 */
    void editCrmwPoolStatusSecurityReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 批量替换 CRMW 池状态 CRMW 引用 */
    void editCrmwPoolStatusCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("ids") List<Long> ids);

    /** 新增临时代码替换日志 */
    void addTempSecurityCodeUpdateLog(TempSecurityCodeBo bo);
}
