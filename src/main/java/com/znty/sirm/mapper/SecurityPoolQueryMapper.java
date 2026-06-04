package com.znty.sirm.mapper;

import com.znty.sirm.model.SecurityPoolQueryDto;
import com.znty.sirm.model.SecurityPoolQueryReq;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 证券池查询数据访问层
 */
public interface SecurityPoolQueryMapper {

    /**
     * 分页查询证券池中的证券列表
     */
    List<SecurityPoolQueryDto> querySecurityPoolPage(SecurityPoolQueryReq req);

    /**
     * 查询所有证券类型选项
     */
    List<String> querySecurityTypeList();
}
