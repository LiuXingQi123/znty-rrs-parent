package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.entity.crmwpoolquery.CrmwPoolQueryDto;
import com.znty.sirm.entity.crmwpoolquery.CrmwPoolQueryReq;
import com.znty.sirm.mapper.CrmwPoolQueryMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * CRMW池查询服务
 */
@Service
public class CrmwPoolQueryService {

    /** CRMW池查询数据访问组件 */
    @Resource
    private CrmwPoolQueryMapper crmwPoolQueryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询CRMW池列表 */
    public PageResult<CrmwPoolQueryDto> queryCrmwPoolPage(CrmwPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<CrmwPoolQueryDto> list = crmwPoolQueryMapper.queryCrmwPoolPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<CrmwPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<CrmwPoolQueryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (CrmwPoolQueryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }
}
