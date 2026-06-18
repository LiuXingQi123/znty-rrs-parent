package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.CompanyPoolAdjustHistoryMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.CompanyPoolAdjustHistoryDto;
import com.znty.sirm.model.CompanyPoolAdjustHistoryReq;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.PoolDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 主体池调整历史查询服务
 */
@Service
public class CompanyPoolAdjustHistoryService {

    /** 主体池调整历史数据访问组件 */
    @Resource
    private CompanyPoolAdjustHistoryMapper companyPoolAdjustHistoryMapper;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询主体池调整历史 */
    public PageResult<CompanyPoolAdjustHistoryDto> queryCompanyPoolAdjustHistoryPage(
            CompanyPoolAdjustHistoryReq req) {
        // 开启分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<CompanyPoolAdjustHistoryDto> list = companyPoolAdjustHistoryMapper.queryCompanyPoolAdjustHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<CompanyPoolAdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 查询投资池树列表 */
    public List<PoolDto> queryPoolTreeList() {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools == null) {
            return new ArrayList<>();
        }
        List<PoolDto> result = new ArrayList<>();
        for (InvestmentPoolBo p : allPools) {
            PoolDto dto = new PoolDto();
            dto.setId(p.getId());
            dto.setParentId(p.getParentId());
            dto.setPoolName(p.getPoolName());
            dto.setPoolCode(p.getPoolCode());
            dto.setPoolType(p.getPoolType());
            dto.setPoolLevel(p.getPoolLevel());
            result.add(dto);
        }
        return result;
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<CompanyPoolAdjustHistoryDto> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (CompanyPoolAdjustHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }
}
