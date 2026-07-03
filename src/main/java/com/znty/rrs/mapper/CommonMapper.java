package com.znty.rrs.mapper;

import com.znty.rrs.entity.common.PoolTreeDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 公共查询数据访问接口
 */
@Mapper
public interface CommonMapper {

    /**
     * 查询投资池树节点列表
     *
     * @return 投资池树节点列表，包含节点名称和全路径名称
     */
    List<PoolTreeDto> queryPoolTreeList();
}
