package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.mapper.SecurityPoolAdjustHistoryMapper;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 证券池调整历史服务。
 * <p>负责证券池调整操作审计记录的分页查询，以及筛选条件所需的证券类型选项查询。</p>
 */
@Service
public class SecurityPoolAdjustHistoryService {

    /** 证券池调库历史数据访问组件 */
    @Resource
    private SecurityPoolAdjustHistoryMapper securityPoolAdjustHistoryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询证券池调整历史列表
     */
    public PageResult<SecurityPoolAdjustHistoryDto> querySecurityPoolAdjustHistoryPage(SecurityPoolAdjustHistoryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityPoolAdjustHistoryDto> list = securityPoolAdjustHistoryMapper.querySecurityPoolAdjustHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<SecurityPoolAdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<SecurityPoolAdjustHistoryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (SecurityPoolAdjustHistoryDto dto : list) {
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
        return securityPoolAdjustHistoryMapper.querySecurityTypeList();
    }

}
