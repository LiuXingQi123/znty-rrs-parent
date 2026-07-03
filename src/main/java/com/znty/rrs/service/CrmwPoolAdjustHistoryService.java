package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.crmwpoolhistory.CrmwPoolAdjustHistoryDto;
import com.znty.rrs.entity.crmwpoolhistory.CrmwPoolAdjustHistoryReq;
import com.znty.rrs.mapper.CrmwPoolAdjustHistoryMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * CRMW池调整历史查询服务
 */
@Service
public class CrmwPoolAdjustHistoryService {

    /** CRMW池调整历史数据访问组件 */
    @Resource
    private CrmwPoolAdjustHistoryMapper crmwPoolAdjustHistoryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询CRMW池调整历史列表 */
    public PageResult<CrmwPoolAdjustHistoryDto> queryCrmwPoolAdjustHistoryPage(CrmwPoolAdjustHistoryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<CrmwPoolAdjustHistoryDto> list = crmwPoolAdjustHistoryMapper.queryCrmwPoolAdjustHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<CrmwPoolAdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<CrmwPoolAdjustHistoryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (CrmwPoolAdjustHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }
}
