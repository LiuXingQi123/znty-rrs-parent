package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustAuditDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustAuditReq;
import com.znty.rrs.service.StockPoolAdjustFlowService;
import javax.annotation.Resource;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 股票池调库流程控制器，负责审批处理入口。 */
@RestController
@RequestMapping("/api/v1/stockPoolAdjust")
public class StockPoolAdjustFlowController {
    /** 股票池调库流程服务 */
    @Resource
    private StockPoolAdjustFlowService stockPoolAdjustFlowService;

    /**
     * 提交股票调库审批处理意见并推进流程。
     *
     * @param req 调库记录、处理人及审批动作
     */
    @PostMapping(value = "/submitAdjustAudit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StockPoolAdjustAuditDto> submitAdjustAudit(@RequestBody StockPoolAdjustAuditReq req) {
        return ApiResponse.success(stockPoolAdjustFlowService.submitAdjustAudit(req));
    }
    /** 修改待办同时提交审核及新增附件，原文件名通过文件上下文保存。 */
    @PostMapping(value = "/submitAdjustAuditWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StockPoolAdjustAuditDto> submitAdjustAuditWithFiles(
            @RequestPart("request") StockPoolAdjustAuditReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestPart(value = "originalFileNameListJson", required = false) byte[] originalFileNameListJson) {
        return ApiResponse.success(stockPoolAdjustFlowService.submitAdjustAudit(req,
                files == null ? null : Arrays.asList(files),
                originalFileNameListJson == null ? null : new String(originalFileNameListJson, StandardCharsets.UTF_8)));
    }
}
