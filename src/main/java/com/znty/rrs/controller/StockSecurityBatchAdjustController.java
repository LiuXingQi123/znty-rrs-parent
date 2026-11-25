package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchCandidateDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustSubmitReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchPoolDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSourcePoolDto;
import com.znty.rrs.service.StockSecurityBatchAdjustService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * 存量证券批量调整控制器
 *
 * 负责批量调库目标池查询、候选证券筛选、批量调库校验及批量调库申请提交
 * 支持 JSON 与 multipart 两种提交方式，multipart 场景用于随申请一并上传材料附件
 */
@RestController
@RequestMapping("/api/v1/stockSecurityBatchAdjust")
public class StockSecurityBatchAdjustController {

    /** 存量证券批量调整服务 */
    @Resource
    private StockSecurityBatchAdjustService stockSecurityBatchAdjustService;

    /**
     * 分页查询当前用户可调整的债券产品库启用叶子投资池
     *
     * @param req 当前用户、投资池筛选和分页条件
     * @return 可批量调整的投资池及分页信息
     */
    @PostMapping("/queryPoolPage")
    public ApiResponse<PageResult<StockSecurityBatchPoolDto>> queryPoolPage(
            @RequestBody StockSecurityBatchAdjustReq req) {
        return ApiResponse.success(stockSecurityBatchAdjustService.queryPoolPage(req));
    }

    /**
     * 查询固定来源池下拉（CRMW、信用债一~三级、转债核心·重点）
     *
     * @param req 可省略的空请求体
     * @return 固定来源池选项列表
     */
    @PostMapping("/querySourcePoolList")
    public ApiResponse<List<StockSourcePoolDto>> querySourcePoolList(
            @RequestBody(required = false) StockSecurityBatchAdjustReq req) {
        return ApiResponse.success(stockSecurityBatchAdjustService.querySourcePoolList());
    }

    /**
     * 分页查询目标池批量调整候选证券（须选来源池）
     *
     * @param req 目标池、来源池、候选证券筛选和分页条件
     * @return 可批量调整的存量证券及分页信息
     */
    @PostMapping("/querySecurityPage")
    public ApiResponse<PageResult<StockSecurityBatchCandidateDto>> querySecurityPage(
            @RequestBody StockSecurityBatchAdjustReq req) {
        return ApiResponse.success(stockSecurityBatchAdjustService.querySecurityPage(req));
    }

    /**
     * 批量调库下一步校验
     *
     * @param req 当前用户、目标池、调整方向和所选存量证券
     * @return 逐项校验结果及可选审批流程
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<StockSecurityBatchAdjustDto> checkAdjust(
            @RequestBody StockSecurityBatchAdjustSubmitReq req) {
        return ApiResponse.success(stockSecurityBatchAdjustService.checkAdjust(req));
    }

    /**
     * 批量提交调库申请
     *
     * @param req 经确认的存量证券调整明细及所选审批流程
     * @return 整批提交结果，包含提交数量和调库日志 ID
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StockSecurityBatchAdjustDto> addAdjustLog(
            @RequestBody StockSecurityBatchAdjustSubmitReq req) {
        return ApiResponse.success(stockSecurityBatchAdjustService.addAdjustLog(req));
    }

    /**
     * 以 multipart 方式批量提交调库申请及附件
     *
     * @param req 经确认的存量证券调整明细及所选审批流程
     * @param files 随申请上传的材料附件，可不传
     * @param originalFileNameListJson 附件原始文件名的 JSON 数组，可不传
     * @return 整批提交结果，包含提交数量和调库日志 ID
     */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StockSecurityBatchAdjustDto> addAdjustLogWithFiles(
            @RequestPart("request") StockSecurityBatchAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(stockSecurityBatchAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }
}
