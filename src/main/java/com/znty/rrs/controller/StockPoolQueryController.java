package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.MySecurityPoolBo;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.stockpoolquery.*;
import com.znty.rrs.service.StockPoolQueryService;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/** 股票池查询、导出及本人股票自选接口。 */
@RestController
@RequestMapping("/api/v1/stockPoolQuery")
public class StockPoolQueryController {
    /** 股票池查询服务。 */
    @Resource private StockPoolQueryService stockPoolQueryService;
    /** 分页查询当前生效状态。 */
    @PostMapping("/queryStockPoolPage")
    public ApiResponse<PageResult<StockPoolQueryDto>> queryStockPoolPage(@RequestBody StockPoolQueryReq req) {
        return ApiResponse.success(stockPoolQueryService.queryStockPoolPage(req));
    }
    /** 按完整筛选条件导出全部记录。 */
    @PostMapping("/exportStockPoolExcel")
    public ApiResponse<CommonFileDto> exportStockPoolExcel(@RequestBody StockPoolQueryReq req) {
        return ApiResponse.success(stockPoolQueryService.exportStockPoolExcel(req));
    }
    /** 添加本人自选股票，重复请求返回原记录。 */
    @PostMapping("/addStockToMyPool")
    public ApiResponse<MySecurityPoolBo> addStockToMyPool(@RequestBody MyStockPoolReq req) {
        return ApiResponse.success(stockPoolQueryService.addStockToMyPool(req));
    }
    /** 移除本人自选股票。 */
    @PostMapping("/deleteStockFromMyPool")
    public ApiResponse<MySecurityPoolBo> deleteStockFromMyPool(@RequestBody MyStockPoolReq req) {
        return ApiResponse.success(stockPoolQueryService.deleteStockFromMyPool(req));
    }
    /** 查询当前用户有效股票收藏代码。 */
    @PostMapping("/queryFavoritedCodeList")
    public ApiResponse<List<String>> queryFavoritedCodeList(@RequestBody MyStockPoolReq req) {
        return ApiResponse.success(stockPoolQueryService.queryFavoritedCodeList(req));
    }
}
