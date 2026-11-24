package com.znty.rrs.mapper;

import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryDto;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 禁投池查询数据访问层
 */
@Mapper
public interface ForbiddenPoolQueryMapper {

    /**
     * 分页查询禁投池证券列表
     *
     * @param req 禁投池在池记录查询条件
     * @return 符合条件的禁投池在池记录列表
     */
    List<ForbiddenPoolQueryDto> queryForbiddenPoolPage(ForbiddenPoolQueryReq req);

    /**
     * 查询禁投池中出现的证券类型下拉选项（code + name）
     *
     * @param req 用于限定选项范围的证券池筛选条件
     * @return 符合条件的证券类型选项列表
     */
    List<SecurityTypeOptionDto> querySecurityTypeList(ForbiddenPoolQueryReq req);
}
