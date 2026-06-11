package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.model.SecurityPoolAdjustAuditDto;
import com.znty.sirm.model.SecurityPoolAdjustAuditReq;
import com.znty.sirm.service.SecurityPoolAdjustFlowService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 证券池调库流程控制器，负责调库审批步骤的处理与流转。
 */
@RestController
@RequestMapping("/api/v1/securityPoolAdjustFlow")
public class SecurityPoolAdjustFlowController {

    @Resource
    private SecurityPoolAdjustFlowService securityPoolAdjustFlowService;

    /**
     * 提交调库审批处理意见，更新当前步骤并按流程配置推进下一步骤。
     */
    @PostMapping("/submitAdjustAudit")
    public ApiResponse<SecurityPoolAdjustAuditDto> submitAdjustAudit(@RequestBody SecurityPoolAdjustAuditReq req) {
        return ApiResponse.success(securityPoolAdjustFlowService.submitAdjustAudit(req));
    }
}
