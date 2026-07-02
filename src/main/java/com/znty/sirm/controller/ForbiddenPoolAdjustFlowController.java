package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.entity.securitypooladjustflow.SecurityPoolAdjustAuditDto;
import com.znty.sirm.entity.securitypooladjustflow.SecurityPoolAdjustAuditReq;
import com.znty.sirm.service.ForbiddenPoolAdjustFlowService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * 禁投池主体调整流程控制器，负责调库审批步骤的处理与流转。
 */
@RestController
@RequestMapping("/api/v1/forbiddenPoolAdjustFlow")
public class ForbiddenPoolAdjustFlowController {

    /** 禁投池主体调整流程服务 */
    @Resource
    private ForbiddenPoolAdjustFlowService forbiddenPoolAdjustFlowService;

    /**
     * 提交调库审批处理意见，更新当前步骤并按流程配置推进下一步骤。
     */
    @PostMapping(value = "/submitAdjustAudit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SecurityPoolAdjustAuditDto> submitAdjustAudit(@RequestBody SecurityPoolAdjustAuditReq req) {
        return ApiResponse.success(forbiddenPoolAdjustFlowService.submitAdjustAudit(req));
    }

    /**
     * 以 multipart 方式提交调库审批处理意见和驳回修改附件变更。
     */
    @PostMapping(value = "/submitAdjustAuditWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SecurityPoolAdjustAuditDto> submitAdjustAuditWithFiles(
            @RequestPart("request") SecurityPoolAdjustAuditReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        return ApiResponse.success(forbiddenPoolAdjustFlowService.submitAdjustAudit(
                req, files == null ? null : Arrays.asList(files)));
    }
}
