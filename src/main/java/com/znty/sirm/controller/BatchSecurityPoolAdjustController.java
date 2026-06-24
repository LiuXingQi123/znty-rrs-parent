package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.BatchSecurityCandidateDto;
import com.znty.sirm.model.BatchSecurityInboundAdjustDto;
import com.znty.sirm.model.BatchSecurityInboundAdjustReq;
import com.znty.sirm.model.BatchSecurityPoolAdjustReq;
import com.znty.sirm.model.BatchSecurityPoolDto;
import com.znty.sirm.service.BatchSecurityPoolAdjustService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;
/**
 * 证券池批量调整控制器
 */
@RestController
@RequestMapping("/api/v1/batchSecurityPoolAdjust")
public class BatchSecurityPoolAdjustController {

    /** 证券池批量调整服务 */
    @Resource
    private BatchSecurityPoolAdjustService batchSecurityPoolAdjustService;

    /**
     * 分页查询当前用户可调整的启用叶子投资池
     */
    @PostMapping("/queryPoolPage")
    public ApiResponse<PageResult<BatchSecurityPoolDto>> queryPoolPage(
            @RequestBody BatchSecurityPoolAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.queryPoolPage(req));
    }

    /**
     * 分页查询目标池批量调整候选证券
     */
    @PostMapping("/querySecurityPage")
    public ApiResponse<PageResult<BatchSecurityCandidateDto>> querySecurityPage(
            @RequestBody BatchSecurityPoolAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.querySecurityPage(req));
    }

    /**
     * 批量调库下一步校验
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<BatchSecurityInboundAdjustDto> checkAdjust(
            @RequestBody BatchSecurityInboundAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.checkAdjust(req));
    }

    /**
     * 批量提交调库申请
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchSecurityInboundAdjustDto> addAdjustLog(
            @RequestBody BatchSecurityInboundAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.addAdjustLog(req));
    }

    /**
     * 以 multipart 方式批量提交调库申请及附件
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BatchSecurityInboundAdjustDto> addAdjustLogWithFiles(
            @RequestPart("request") BatchSecurityInboundAdjustReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        return ApiResponse.success(batchSecurityPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files)));
    }
}
