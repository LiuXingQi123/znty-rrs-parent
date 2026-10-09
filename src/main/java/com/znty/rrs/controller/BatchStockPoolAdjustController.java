package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustDto;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolDto;
import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import com.znty.rrs.service.BatchStockPoolAdjustService;
import java.util.Arrays;
import javax.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 股票池批量调整控制器 */
@RestController
@RequestMapping("/api/v1/batchStockPoolAdjust")
public class BatchStockPoolAdjustController {
    /** 股票池批量调整服务 */
    @Resource
    private BatchStockPoolAdjustService batchStockPoolAdjustService;

    /** 分页查询当前用户可调整的股票池 */
    @PostMapping("/queryPoolPage")
    public ApiResponse<PageResult<BatchStockPoolDto>> queryPoolPage(@RequestBody BatchStockPoolAdjustReq req) {
        return ApiResponse.success(batchStockPoolAdjustService.queryPoolPage(req));
    }

    /** 分页查询批量调整候选股票 */
    @PostMapping("/queryStockPage")
    public ApiResponse<PageResult<StockInfoDto>> queryStockPage(@RequestBody BatchStockPoolAdjustReq req) {
        return ApiResponse.success(batchStockPoolAdjustService.queryStockPage(req));
    }

    /** 校验整批股票并展开每只股票的关系项 */
    @PostMapping("/checkAdjust")
    public ApiResponse<BatchStockAdjustDto> checkAdjust(@RequestBody BatchStockAdjustReq req) {
        return ApiResponse.success(batchStockPoolAdjustService.checkAdjust(req));
    }

    /** 原子提交整批股票调整申请 */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchStockAdjustDto> addAdjustLog(@RequestBody BatchStockAdjustReq req) {
        return ApiResponse.success(batchStockPoolAdjustService.addAdjustLog(req));
    }

    /** 原子提交股票批量申请及共用报告、材料附件 */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BatchStockAdjustDto> addAdjustLogWithFiles(
            @RequestPart("request") BatchStockAdjustReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(batchStockPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }
}
