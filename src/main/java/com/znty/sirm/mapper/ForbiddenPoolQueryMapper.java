package com.znty.sirm.mapper;

import com.znty.sirm.model.ForbiddenPoolQueryDto;
import com.znty.sirm.model.ForbiddenPoolQueryReq;

import java.util.List;

/**
 * 禁投池查询数据访问层
 */
public interface ForbiddenPoolQueryMapper {

    /**
     * 分页查询禁投池证券列表
     */
    List<ForbiddenPoolQueryDto> queryForbiddenPoolPage(ForbiddenPoolQueryReq req);

    /**
     * 查询证券类型下拉选项
     */
    List<String> querySecurityTypeList();
}
