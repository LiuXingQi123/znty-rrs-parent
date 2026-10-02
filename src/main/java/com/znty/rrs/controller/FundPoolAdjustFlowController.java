package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustAuditDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustAuditReq;
import com.znty.rrs.service.FundPoolAdjustFlowService;
import javax.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 基金池调库流程控制器，负责审批处理入口。 */
@RestController
@RequestMapping("/api/v1/fundPoolAdjust")
public class FundPoolAdjustFlowController {
    /** 基金池调库流程服务 */
    @Resource
    private FundPoolAdjustFlowService fundPoolAdjustFlowService;

    /**
     * 提交基金调库审批处理意见并推进流程。
     *
     * @param req 调库记录、处理人及审批动作
     */
    @PostMapping(value = "/submitAdjustAudit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<FundPoolAdjustAuditDto> submitAdjustAudit(@RequestBody FundPoolAdjustAuditReq req) {
        return ApiResponse.success(fundPoolAdjustFlowService.submitAdjustAudit(req));
    }
}
