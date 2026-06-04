package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.AdjustCheckDto;
import com.znty.sirm.model.AdjustCheckReq;
import com.znty.sirm.model.AdjustLogDto;
import com.znty.sirm.model.AdjustSubmitDto;
import com.znty.sirm.model.SecurityInfoDetailDto;
import com.znty.sirm.model.SecurityInfoDto;
import com.znty.sirm.model.SecurityPoolAdjustReq;
import com.znty.sirm.model.SecurityPoolAdjustSubmitReq;
import com.znty.sirm.model.SecurityPoolStatusDto;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.service.SecurityPoolAdjustService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 证券池调库模块
 */
@RestController
@RequestMapping("/api/v1/securityPoolAdjust")
public class SecurityPoolAdjustController {

    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /** 分页查询证券列表 */
    @PostMapping("/querySecurityPage")
    public ApiResponse<PageResult<SecurityInfoDto>> querySecurityPage(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySecurityPage(req));
    }

    /** 查询证券详情（调库页面顶部信息） */
    @PostMapping("/querySecurityDetail")
    public ApiResponse<SecurityInfoDetailDto> querySecurityDetail(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySecurityDetail(req));
    }

    /** 查询可调入/可调出投资池列表（树结构由前端组装） */
    @PostMapping("/queryAdjustPoolList")
    public ApiResponse<List<PoolDto>> queryAdjustPoolList(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryAdjustPoolList(req));
    }

    /** 查询证券当前所在池及主体所在池 */
    @PostMapping("/querySecurityPoolStatus")
    public ApiResponse<SecurityPoolStatusDto> querySecurityPoolStatus(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySecurityPoolStatus(req));
    }

    /** 提交调库申请 */
    @PostMapping("/addAdjustLog")
    public ApiResponse<AdjustSubmitDto> addAdjustLog(@RequestBody SecurityPoolAdjustSubmitReq req) {
        return ApiResponse.success(securityPoolAdjustService.addAdjustLog(req));
    }

    /** 查询证券的调库记录列表 */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryAdjustLogList(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryAdjustLogList(req));
    }

    /** 校验证券调库可行性 */
    @PostMapping("/checkAdjust")
    public ApiResponse<AdjustCheckDto> checkAdjust(@RequestBody AdjustCheckReq req) {
        return ApiResponse.success(securityPoolAdjustService.checkAdjust(req));
    }
}
