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
import java.util.Map;

/**
 * 调整历史服务。
 * <p>负责证券池调整操作审计记录的分页查询，以及筛选条件所需的证券类型选项和投资池树列表查询。</p>
 */
@Service
public class AdjustHistoryService {

    /** 调库历史数据访问组件 */
    @Resource
    private AdjustHistoryMapper adjustHistoryMapper;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询调整历史列表
     */
    public PageResult<AdjustHistoryDto> queryAdjustHistoryPage(AdjustHistoryReq req) {
        // 开启分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<AdjustHistoryDto> list = adjustHistoryMapper.queryAdjustHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<AdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<AdjustHistoryDto> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (AdjustHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolPath(fullName);
            }
        }
    }

    /**
     * 查询调整历史中出现的证券类型选项
     */
    public List<SecurityTypeOptionDto> querySecurityTypeList() {
        return adjustHistoryMapper.querySecurityTypeList();
    }

    /**
     * 查询投资池树列表（用于前端树多选筛选组件，仅返回树节点基础字段）
     */
    public List<PoolDto> queryPoolTreeList() {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools == null) {
            return new ArrayList<>();
        }
        List<PoolDto> result = new ArrayList<>();
        // 将全量投资池 Bo 转换为精简 Dto，仅保留树组件所需字段
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
