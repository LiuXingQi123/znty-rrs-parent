package com.znty.sirm.mapper;

import com.znty.sirm.model.ForbiddenPoolQueryDto;
import com.znty.sirm.model.ForbiddenPoolQueryReq;
import com.znty.sirm.model.SecurityTypeOptionDto;

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
     * 查询禁投池中出现的证券类型下拉选项（code + name）
     */
    List<SecurityTypeOptionDto> querySecurityTypeList();
}
