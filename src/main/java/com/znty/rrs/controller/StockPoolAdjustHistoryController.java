package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.stockpooladjusthistory.StockIndustryOptionDto;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockPoolAdjustHistoryDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockPoolAdjustHistoryReq;
import com.znty.rrs.service.StockPoolAdjustHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/** 股票池调整历史控制器。 */
@RestController
@RequestMapping("/api/v1/stockPoolAdjustHistory")
public class StockPoolAdjustHistoryController {

    /** 股票池调整历史服务。 */
    @Resource
    private StockPoolAdjustHistoryService stockPoolAdjustHistoryService;

    /**
     * 分页查询股票池调整历史记录。
     * <p>按股票信息、调整时间及审核状态等条件筛选，返回全部审核状态的历史记录。</p>
     *
     * @param req 分页参数和股票池调整历史筛选条件
     */
    @PostMapping("/queryStockPoolAdjustHistoryPage")
    public ApiResponse<PageResult<StockPoolAdjustHistoryDto>> queryStockPoolAdjustHistoryPage(
            @RequestBody StockPoolAdjustHistoryReq req) {
        return ApiResponse.success(stockPoolAdjustHistoryService.queryStockPoolAdjustHistoryPage(req));
    }

    /**
     * 按当前筛选条件导出股票池调整历史。
     * <p>筛选条件与列表一致，不传分页时导出全部命中记录。</p>
     *
     * @param req 股票池调整历史筛选条件
     */
    @PostMapping("/exportStockPoolAdjustHistoryExcel")
    public ApiResponse<CommonFileDto> exportStockPoolAdjustHistoryExcel(
            @RequestBody StockPoolAdjustHistoryReq req) {
        return ApiResponse.success(stockPoolAdjustHistoryService.exportStockPoolAdjustHistoryExcel(req));
    }

    /**
     * 查询调整历史中出现的股票行业选项。
     * <p>返回存在未删除调库记录的行业，供历史筛选条件使用。</p>
     *
     * @param req 接口请求体，此查询不使用筛选条件
     */
    @PostMapping("/queryIndustryList")
    public ApiResponse<List<StockIndustryOptionDto>> queryIndustryList(
            @RequestBody StockPoolAdjustHistoryReq req) {
        return ApiResponse.success(stockPoolAdjustHistoryService.queryIndustryList());
    }

}
