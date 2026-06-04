package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.AdjustHistoryMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.AdjustHistoryDto;
import com.znty.sirm.model.AdjustHistoryReq;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.model.SecurityTypeOptionDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 调整历史业务逻辑
 */
@Service
public class AdjustHistoryService {

    @Resource
    private AdjustHistoryMapper adjustHistoryMapper;

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /**
     * 分页查询调整历史列表
     */
    public PageResult<AdjustHistoryDto> queryAdjustHistoryPage(AdjustHistoryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<AdjustHistoryDto> list = adjustHistoryMapper.queryAdjustHistoryPage(req);
        PageInfo<AdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询调整历史中出现的证券类型选项
     */
    public List<SecurityTypeOptionDto> querySecurityTypeList() {
        return adjustHistoryMapper.querySecurityTypeList();
    }

    /**
     * 查询投资池树列表（用于前端树多选组件）
     */
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
}
