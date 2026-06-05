package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.SubjectPoolQueryMapper;
import com.znty.sirm.model.SubjectPoolQueryDto;
import com.znty.sirm.model.SubjectPoolQueryReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 主体池查询服务。
 * <p>负责发行主体池（issuer pool）证券的分页查询，支持按主体名称、评级等条件筛选。</p>
 */
@Service
public class SubjectPoolQueryService {

    @Resource
    private SubjectPoolQueryMapper subjectPoolQueryMapper;

    /** 分页查询主体池列表 */
    public PageResult<SubjectPoolQueryDto> querySubjectPoolPage(SubjectPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SubjectPoolQueryDto> list = subjectPoolQueryMapper.querySubjectPoolPage(req);
        PageInfo<SubjectPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }
}
