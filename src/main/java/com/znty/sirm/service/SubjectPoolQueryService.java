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
import java.util.Map;

/**
 * 主体池查询服务。
 * <p>负责发行主体池（issuer pool）证券的分页查询，支持按主体名称、评级等条件筛选。</p>
 */
@Service
public class SubjectPoolQueryService {

    /** 主体池查询数据访问组件 */
    @Resource
    private SubjectPoolQueryMapper subjectPoolQueryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询主体池列表 */
    public PageResult<SubjectPoolQueryDto> querySubjectPoolPage(SubjectPoolQueryReq req) {
        // 开启分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SubjectPoolQueryDto> list = subjectPoolQueryMapper.querySubjectPoolPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<SubjectPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<SubjectPoolQueryDto> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (SubjectPoolQueryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }
}
