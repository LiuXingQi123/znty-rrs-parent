package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.crmwpoolquery.CrmwPoolQueryDto;
import com.znty.rrs.entity.crmwpoolquery.CrmwPoolQueryReq;
import com.znty.rrs.mapper.CrmwPoolQueryMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 查询 CRMW 凭证与标的证券组合的当前在池记录
 *
 * 按页面筛选条件分页查询已生效的组合，分别展示凭证和标的证券信息
 * 通过 {@link InvestmentPoolService} 补齐投资池全路径名称
 */
@Service
public class CrmwPoolQueryService {

    /** CRMW池查询数据访问组件 */
    @Resource
    private CrmwPoolQueryMapper crmwPoolQueryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询CRMW池列表
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 填充投资池全路径名称
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req CRMW 凭证及标的证券组合的筛选条件和分页参数
     * @return 查询记录及分页信息
     */
    public PageResult<CrmwPoolQueryDto> queryCrmwPoolPage(CrmwPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<CrmwPoolQueryDto> list = crmwPoolQueryMapper.queryCrmwPoolPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<CrmwPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 填充投资池全路径名称
     *
     * 执行顺序：
     * 1. 查询投资池全路径名称映射
     *
     * @param list 本次处理的数据列表
     */
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
