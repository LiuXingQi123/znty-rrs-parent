package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.ForbiddenPoolHistoryMapper;
import com.znty.sirm.model.ForbiddenPoolHistoryDto;
import com.znty.sirm.model.ForbiddenPoolHistoryReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 禁投池历史查询服务
 */
@Service
public class ForbiddenPoolHistoryService {

    /** 禁投池历史数据访问组件 */
    @Resource
    private ForbiddenPoolHistoryMapper forbiddenPoolHistoryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询禁投池调整历史 */
    public PageResult<ForbiddenPoolHistoryDto> queryForbiddenPoolHistoryPage(ForbiddenPoolHistoryReq req) {
        // 开启分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ForbiddenPoolHistoryDto> list = forbiddenPoolHistoryMapper.queryForbiddenPoolHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<ForbiddenPoolHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<ForbiddenPoolHistoryDto> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (ForbiddenPoolHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }
}
