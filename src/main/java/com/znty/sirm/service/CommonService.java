package com.znty.sirm.service;

import com.znty.sirm.mapper.CommonMapper;
import com.znty.sirm.model.PoolTreeDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 公共查询业务服务
 */
@Service
public class CommonService {

    /** 公共查询数据访问组件 */
    @Resource
    private CommonMapper commonMapper;

    /**
     * 查询投资池树节点列表
     *
     * @return 投资池树节点列表，poolName 为节点名称，poolFullName 为全路径名称
     */
    public List<PoolTreeDto> queryPoolTreeList() {
        return commonMapper.queryPoolTreeList();
    }
}
