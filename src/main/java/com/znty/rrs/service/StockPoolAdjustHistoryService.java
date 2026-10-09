package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.util.QueryListExportHelper;
import com.znty.rrs.common.util.StockQueryFilterHelper;
import com.znty.rrs.entity.stockpooladjusthistory.StockIndustryOptionDto;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockPoolAdjustHistoryDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockPoolAdjustHistoryExportDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockPoolAdjustHistoryReq;
import com.znty.rrs.mapper.StockPoolAdjustHistoryMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 股票池调整历史服务。
 * <p>负责股票调库历史分页查询、导出，以及筛选条件所需的行业查询。</p>
 */
@Service
public class StockPoolAdjustHistoryService {

    /** 股票池调整历史数据访问组件。 */
    @Resource
    private StockPoolAdjustHistoryMapper stockPoolAdjustHistoryMapper;

    /** 投资池服务。 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询股票池调整历史列表。
     *
     * @param req 股票池调整历史筛选条件
     * @return 分页后的股票池调整历史记录
     */
    public PageResult<StockPoolAdjustHistoryDto> queryStockPoolAdjustHistoryPage(StockPoolAdjustHistoryReq req) {
        // 开启分页并查询股票池调整历史列表。
        req.setAdjustTimeEndExclusive(StockQueryFilterHelper.endExclusive(req.getAdjustTimeStart(), req.getAdjustTimeEnd()));
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<StockPoolAdjustHistoryDto> list = stockPoolAdjustHistoryMapper.queryStockPoolAdjustHistoryPage(req);
        // 回填投资池全路径名称，保持页面展示完整。
        fillPoolFullName(list);
        // 读取分页总数并组装分页结果。
        PageInfo<StockPoolAdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按当前筛选条件导出全部命中的股票池调整历史。
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 可下载的 Excel 文件
     */
    public CommonFileDto exportStockPoolAdjustHistoryExcel(StockPoolAdjustHistoryReq req) {
        // 复用列表 SQL 查询全部命中记录，不受当前分页限制。
        req.setAdjustTimeEndExclusive(StockQueryFilterHelper.endExclusive(req.getAdjustTimeStart(), req.getAdjustTimeEnd()));
        List<StockPoolAdjustHistoryDto> list = stockPoolAdjustHistoryMapper.queryStockPoolAdjustHistoryPage(req);
        // 回填投资池全路径名称，保证导出与页面展示一致。
        fillPoolFullName(list);
        // 将查询记录转换为模板所需的导出行。
        List<StockPoolAdjustHistoryExportDto> exportRows = buildExportRows(list);
        // 按股票历史模板生成 Excel 文件。
        return QueryListExportHelper.fillTemplate(
                "/xlsx/stock_pool_adjust_history_export_template.xlsx",
                "股票池调整历史",
                "股票池调整历史导出模板不存在",
                "生成股票池调整历史 Excel 失败：",
                exportRows);
    }

    /**
     * 查询调整历史中出现的股票行业选项。
     *
     * @return 股票行业选项列表
     */
    public List<StockIndustryOptionDto> queryIndustryList() {
        return stockPoolAdjustHistoryMapper.queryIndustryList();
    }

    /**
     * 将页面展示字段转换为 Excel 行。
     *
     * @param list 股票池调整历史记录
     * @return Excel 导出行
     */
    private List<StockPoolAdjustHistoryExportDto> buildExportRows(List<StockPoolAdjustHistoryDto> list) {
        List<StockPoolAdjustHistoryExportDto> result = new ArrayList<>();
        for (StockPoolAdjustHistoryDto source : list) {
            StockPoolAdjustHistoryExportDto target = new StockPoolAdjustHistoryExportDto();
            target.setAdjusterName(source.getAdjusterName());
            // 提交时间按页面展示格式写入 Excel。
            target.setSubmitTime(QueryListExportHelper.formatDateTime(source.getSubmitTime()));
            target.setStockName(source.getStockName());
            target.setStockCode(source.getStockCode());
            target.setIndustryName(source.getIndustryName());
            target.setAdjustType(source.getAdjustType());
            target.setAdjustMode(source.getAdjustMode());
            target.setTargetPoolPath(source.getTargetPoolPath());
            // 审核状态按统一字典转换为导出文案。
            target.setAuditStatusLabel(QueryListExportHelper.auditStatusLabel(source.getAuditStatus()));
            result.add(target);
        }
        return result;
    }

    /**
     * 填充投资池全路径名称。
     *
     * @param list 查询结果
     */
    private void fillPoolFullName(List<StockPoolAdjustHistoryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射。
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (StockPoolAdjustHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolPath(fullName);
            }
        }
    }
}
