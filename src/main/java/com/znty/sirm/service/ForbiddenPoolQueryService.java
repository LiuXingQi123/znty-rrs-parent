package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.ForbiddenPoolQueryMapper;
import com.znty.sirm.model.ForbiddenPoolQueryDto;
import com.znty.sirm.model.ForbiddenPoolQueryReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 禁投池查询业务逻辑
 */
@Service
public class ForbiddenPoolQueryService {

    @Resource
    private ForbiddenPoolQueryMapper forbiddenPoolQueryMapper;

    /** 分页查询禁投池证券列表 */
    public PageResult<ForbiddenPoolQueryDto> queryForbiddenPoolPage(ForbiddenPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ForbiddenPoolQueryDto> list = forbiddenPoolQueryMapper.queryForbiddenPoolPage(req);
        PageInfo<ForbiddenPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 查询证券类型下拉选项 */
    public List<String> queryBondTypeList() {
        return forbiddenPoolQueryMapper.queryBondTypeList();
    }
}
