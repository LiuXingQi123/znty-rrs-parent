package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 批量读取主体和证券的 Excel 排除依据
 */
@Mapper
public interface ExcelPoolExclusionMapper {

    /**
     * 批量查询受 Excel 保护的生效在池记录
     *
     * @param poolIds 要扫描的投资池 ID，为空引用时查询全部投资池
     * @param includeCompanyFollow 是否包含 Excel 主体触发的普通债跟随记录
     * @return 受保护的在池记录 ID
     */
    Set<Long> queryExcelProtectedPoolStatusIds(@Param("poolIds") Collection<Long> poolIds,
                                               @Param("includeCompanyFollow") boolean includeCompanyFollow);

    /**
     * 查询生效的主体 Excel 调整流水
     *
     * @param poolIds 目标池范围，空引用时按主体代码范围查询
     * @param issuerCodes 主体代码范围，空引用时按目标池范围查询
     * @return 生效的 Excel 主体主项及同批配套流水
     */
    List<IpAdjustLogBo> queryExcelCompanyActions(@Param("poolIds") Collection<Long> poolIds,
                                                @Param("issuerCodes") Collection<String> issuerCodes);
}
