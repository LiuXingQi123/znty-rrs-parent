package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.mapper.CompanyPoolQueryMapper;
import com.znty.rrs.entity.companypool.CompanyPoolQueryDto;
import com.znty.rrs.entity.companypool.CompanyPoolQueryReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 主体池查询服务。
 * <p>负责发行主体池（issuer pool）证券的分页查询，支持按主体名称、评级等条件筛选。</p>
 */
@Service
public class CompanyPoolQueryService {

    /** 主体池查询数据访问组件 */
    @Resource
    private CompanyPoolQueryMapper companyPoolQueryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询主体池列表 */
    public PageResult<CompanyPoolQueryDto> queryCompanyPoolPage(CompanyPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<CompanyPoolQueryDto> list = companyPoolQueryMapper.queryCompanyPoolPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<CompanyPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<CompanyPoolQueryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (CompanyPoolQueryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }
}
