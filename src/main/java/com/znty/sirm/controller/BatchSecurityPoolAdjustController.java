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
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

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
     * 查询当前用户可调整的启用叶子投资池
     */
    @PostMapping("/queryPoolList")
    public ApiResponse<List<BatchSecurityPoolDto>> queryPoolList(
            @RequestBody BatchSecurityPoolAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.queryPoolList(req));
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
    @PostMapping("/addAdjustLog")
    public ApiResponse<BatchSecurityInboundAdjustDto> addAdjustLog(
            @RequestBody BatchSecurityInboundAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.addAdjustLog(req));
    }
}
