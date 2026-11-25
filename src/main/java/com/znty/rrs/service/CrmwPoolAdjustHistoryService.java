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
 * 查询 CRMW 池的调入调出历史
 *
 * 以凭证与标的证券组合为单位查询调库记录，为页面返回历史调整方向和审批状态
 * 通过 {@link InvestmentPoolService} 补齐投资池全路径名称，便于区分同名子池
 */
@Service
public class CrmwPoolAdjustHistoryService {

    /** CRMW池调整历史数据访问组件 */
    @Resource
    private CrmwPoolAdjustHistoryMapper crmwPoolAdjustHistoryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询CRMW池调整历史列表
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 填充投资池全路径名称
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req CRMW 调库历史的筛选条件和分页参数
     * @return 查询记录及分页信息
     */
    public PageResult<CrmwPoolAdjustHistoryDto> queryCrmwPoolAdjustHistoryPage(CrmwPoolAdjustHistoryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<CrmwPoolAdjustHistoryDto> list = crmwPoolAdjustHistoryMapper.queryCrmwPoolAdjustHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<CrmwPoolAdjustHistoryDto> pageInfo = new PageInfo<>(list);
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
