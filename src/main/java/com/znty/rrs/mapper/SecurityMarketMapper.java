package com.znty.rrs.mapper;

import com.znty.rrs.entity.common.SecurityMarketMappingDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 统一证券市场及同券关联查询。 */
@Mapper
public interface SecurityMarketMapper {
    /** 批量查询原始映射，保留重复行以便识别错误源数据。 */
    List<SecurityMarketMappingDto> queryMarketMappingList(@Param("windCodes") List<String> windCodes);
    /** 按非空 ISIN 和共用市场集合查询同券代码，不递归，范围内缺少主档仍保留。 */
    List<String> queryRelatedWindCodeList(@Param("windCode") String windCode,
                                         @Param("marketCodes") List<String> marketCodes);
}
