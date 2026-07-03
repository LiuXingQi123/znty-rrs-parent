package com.znty.rrs.mapper;

import com.znty.rrs.entity.securitypoolquery.SecurityPoolQueryDto;
import com.znty.rrs.entity.securitypoolquery.SecurityPoolQueryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 证券池查询数据访问层
 */
@Mapper
public interface SecurityPoolQueryMapper {

    /**
     * 分页查询证券池中的证券列表
     */
    List<SecurityPoolQueryDto> querySecurityPoolPage(SecurityPoolQueryReq req);

    /**
     * 查询证券池中出现的证券类型选项（code + name）
     */
    List<SecurityTypeOptionDto> querySecurityTypeList();
}
