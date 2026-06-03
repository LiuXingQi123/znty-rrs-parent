package com.znty.sirm.mapper;

import com.znty.sirm.model.BondPoolQueryDto;
import com.znty.sirm.model.BondPoolQueryReq;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 债券池查询数据访问层
 */
public interface BondPoolQueryMapper {

    /**
     * 分页查询债券池中的债券列表
     */
    List<BondPoolQueryDto> queryPage(BondPoolQueryReq req);

    /**
     * 查询所有债券类型选项
     */
    List<String> queryBondTypeOptions();
}
