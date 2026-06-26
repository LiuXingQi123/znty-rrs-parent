package com.znty.sirm.mapper;

import com.znty.sirm.entity.bo.TempSecurityCodeBo;
import com.znty.sirm.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.sirm.entity.tempsecuritycode.TempSecurityCodeReq;
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

    /** 新增临时代码 */
    void addTempSecurityCode(TempSecurityCodeBo bo);

    /** 更新临时代码为已更新 */
    void editTempSecurityCodeToUpdated(TempSecurityCodeBo bo);

    /** 取消发行临时代码 */
    void editTempSecurityCodeToCancelled(TempSecurityCodeBo bo);

    /** 删除临时代码 */
    void deleteTempSecurityCode(TempSecurityCodeBo bo);
}
