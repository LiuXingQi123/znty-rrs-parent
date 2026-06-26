package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.AdjustHistoryMapper;
import com.znty.sirm.entity.adjusthistory.AdjustHistoryDto;
import com.znty.sirm.entity.adjusthistory.AdjustHistoryReq;
import com.znty.sirm.entity.common.SecurityTypeOptionDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 调整历史服务。
 * <p>负责证券池调整操作审计记录的分页查询，以及筛选条件所需的证券类型选项查询。</p>
 */
@Service
public class AdjustHistoryService {

    /** 调库历史数据访问组件 */
    @Resource
    private AdjustHistoryMapper adjustHistoryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询调整历史列表
     */
    public PageResult<AdjustHistoryDto> queryAdjustHistoryPage(AdjustHistoryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<AdjustHistoryDto> list = adjustHistoryMapper.queryAdjustHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<AdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<AdjustHistoryDto> list) {
        if (list.isEmpty()) {
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

}
