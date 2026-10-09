package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckDto;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckReq;
import com.znty.rrs.entity.stockpooladjust.StockAdjustSubmitDto;
import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockIndustryOptionDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustReq;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustSubmitReq;
import com.znty.rrs.entity.stockpooladjust.StockPoolDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolStatusDto;
import com.znty.rrs.entity.bo.StockAdjustLogBo;
import com.znty.rrs.entity.bo.StockAdjustStepBo;
import com.znty.rrs.service.StockPoolAdjustService;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 股票池调整申请控制器 */
@RestController
@RequestMapping("/api/v1/stockPoolAdjust")
public class StockPoolAdjustController {
    /** 股票池调整服务 */
    @Resource
    private StockPoolAdjustService stockPoolAdjustService;

    /**
     * 分页查询可申请调库的股票。
     *
     * @param req 股票筛选条件及分页参数
     */
    @PostMapping("/queryStockPage")
    public ApiResponse<PageResult<StockInfoDto>> queryStockPage(@RequestBody StockPoolAdjustReq req) {
        return ApiResponse.success(stockPoolAdjustService.queryStockPage(req));
    }

    /**
     * 查询有效股票产品类型。
     *
     * @param req 请求体，保持统一接口入参格式
     */
    @PostMapping("/queryStockTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> queryStockTypeList(@RequestBody StockPoolAdjustReq req) {
        return ApiResponse.success(stockPoolAdjustService.queryStockTypeList());
    }

    /** 查询实际股票主档行业选项。 */
    @PostMapping("/queryIndustryList")
    public ApiResponse<List<StockIndustryOptionDto>> queryIndustryList(@RequestBody StockPoolAdjustReq req) {
        return ApiResponse.success(stockPoolAdjustService.queryIndustryList());
    }

    /**
     * 查询股票只读基础信息。
     *
     * @param req 包含股票代码的查询请求
     */
    @PostMapping("/queryStockDetail")
    public ApiResponse<StockInfoDto> queryStockDetail(@RequestBody StockPoolAdjustReq req) {
        return ApiResponse.success(stockPoolAdjustService.queryStockDetail(req));
    }

    /**
     * 查询当前用户可调整的股票池。
     *
     * @param req 当前用户及调整方向等筛选条件
     */
    @PostMapping("/queryAdjustPoolList")
    public ApiResponse<List<StockPoolDto>> queryAdjustPoolList(@RequestBody StockPoolAdjustReq req) {
        return ApiResponse.success(stockPoolAdjustService.queryAdjustPoolList(req));
    }

    /**
     * 查询股票当前所在池。
     *
     * @param req 包含股票代码的查询请求
     */
    @PostMapping("/queryStockPoolStatus")
    public ApiResponse<List<StockPoolStatusDto>> queryStockPoolStatus(@RequestBody StockPoolAdjustReq req) {
        return ApiResponse.success(stockPoolAdjustService.queryStockPoolStatus(req));
    }

    /**
     * 查询股票调库记录列表，用于审核与详情上下文展示。
     *
     * @param req 调库批次号或调库记录筛选条件
     */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<StockAdjustLogBo>> queryAdjustLogList(@RequestBody StockPoolAdjustReq req) {
        return ApiResponse.success(stockPoolAdjustService.queryAdjustLogList(req));
    }

    /**
     * 查询股票调库流程步骤。
     *
     * @param req 调库批次号或调库记录 ID
     */
    @PostMapping("/queryAdjustStepList")
    public ApiResponse<List<StockAdjustStepBo>> queryAdjustStepList(@RequestBody StockPoolAdjustReq req) {
        return ApiResponse.success(stockPoolAdjustService.queryAdjustStepList(req));
    }

    /**
     * 校验股票调库申请。
     *
     * @param req 股票代码与待调整池明细
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<StockAdjustCheckDto> checkAdjust(@RequestBody StockAdjustCheckReq req) {
        return ApiResponse.success(stockPoolAdjustService.checkAdjust(req));
    }

    /**
     * 提交不含本地文件的股票调库申请。
     *
     * @param req 股票调库明细及流程选择
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StockAdjustSubmitDto> addAdjustLog(@RequestBody StockPoolAdjustSubmitReq req) {
        return ApiResponse.success(stockPoolAdjustService.addAdjustLog(req));
    }

    /**
     * 以 multipart 方式提交股票调库申请及附件。
     *
     * @param req 股票调库明细及附件引用
     * @param files 本次上传的附件文件
     * @param originalFileNameListJson 附件原始文件名列表的 JSON 字符串
     */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StockAdjustSubmitDto> addAdjustLogWithFiles(
            @RequestPart("request") StockPoolAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestPart(value = "originalFileNameListJson", required = false) byte[] originalFileNameListJson) {
        return ApiResponse.success(stockPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files),
                originalFileNameListJson == null ? null : new String(originalFileNameListJson, StandardCharsets.UTF_8)));
    }

}
