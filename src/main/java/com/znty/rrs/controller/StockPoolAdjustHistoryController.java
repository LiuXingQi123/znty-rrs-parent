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

/**
 * 股票池调整历史控制器
 */
@RestController
@RequestMapping("/api/v1/stockPoolAdjustHistory")
public class StockPoolAdjustHistoryController {

    /** 股票池调整历史服务 */
    @Resource
    private StockPoolAdjustHistoryService stockPoolAdjustHistoryService;

    /**
     * 分页查询股票池调整历史记录
     *
     * 按股票信息、调整时间及审核状态等条件筛选，返回全部审核状态的历史记录
     *
     * @param req 股票池调整历史的筛选和分页条件
     * @return 股票池调整历史及分页信息
     */
    @PostMapping("/queryStockPoolAdjustHistoryPage")
    public ApiResponse<PageResult<StockPoolAdjustHistoryDto>> queryStockPoolAdjustHistoryPage(
            @RequestBody StockPoolAdjustHistoryReq req) {
        return ApiResponse.success(stockPoolAdjustHistoryService.queryStockPoolAdjustHistoryPage(req));
    }

    /**
     * 按当前筛选条件导出股票池调整历史
     *
     * 筛选条件与列表一致，不传分页时导出全部命中记录
     *
     * @param req 股票池调整历史的筛选条件
     * @return Excel 文件名、内容类型、文件大小和 Base64 内容
     */
    @PostMapping("/exportStockPoolAdjustHistoryExcel")
    public ApiResponse<CommonFileDto> exportStockPoolAdjustHistoryExcel(
            @RequestBody StockPoolAdjustHistoryReq req) {
        return ApiResponse.success(stockPoolAdjustHistoryService.exportStockPoolAdjustHistoryExcel(req));
    }

    /**
     * 查询调整历史中出现的股票行业选项
     *
     * 返回存在未删除调库记录的行业，供历史筛选条件使用
     *
     * @param req 空请求体
     * @return 股票行业选项列表
     */
    @PostMapping("/queryIndustryList")
    public ApiResponse<List<StockIndustryOptionDto>> queryIndustryList(
            @RequestBody StockPoolAdjustHistoryReq req) {
        return ApiResponse.success(stockPoolAdjustHistoryService.queryIndustryList());
    }

}
