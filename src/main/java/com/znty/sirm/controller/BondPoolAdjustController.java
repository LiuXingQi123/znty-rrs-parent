package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.AdjustCheckDto;
import com.znty.sirm.model.AdjustCheckReq;
import com.znty.sirm.model.AdjustLogDto;
import com.znty.sirm.model.AdjustSubmitDto;
import com.znty.sirm.model.BondInfoDetailDto;
import com.znty.sirm.model.BondInfoDto;
import com.znty.sirm.model.BondPoolAdjustReq;
import com.znty.sirm.model.BondPoolAdjustSubmitReq;
import com.znty.sirm.model.BondPoolStatusDto;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.service.BondPoolAdjustService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 债券池调库模块
 */
@RestController
@RequestMapping("/api/v1/bondPoolAdjust")
public class BondPoolAdjustController {

    @Resource
    private BondPoolAdjustService bondPoolAdjustService;

    /** 分页查询债券列表 */
    @PostMapping("/queryBondPage")
    public ApiResponse<PageResult<BondInfoDto>> queryBondPage(@RequestBody BondPoolAdjustReq req) {
        return ApiResponse.success(bondPoolAdjustService.queryBondPage(req));
    }

    /** 查询债券详情（调库页面顶部信息） */
    @PostMapping("/queryBondDetail")
    public ApiResponse<BondInfoDetailDto> queryBondDetail(@RequestBody BondPoolAdjustReq req) {
        return ApiResponse.success(bondPoolAdjustService.queryBondDetail(req));
    }

    /** 查询可调入/可调出投资池列表（树结构由前端组装） */
    @PostMapping("/queryAdjustPoolList")
    public ApiResponse<List<PoolDto>> queryAdjustPoolList(@RequestBody BondPoolAdjustReq req) {
        return ApiResponse.success(bondPoolAdjustService.queryAdjustPoolList(req));
    }

    /** 查询债券当前所在池及主体所在池 */
    @PostMapping("/queryBondPoolStatus")
    public ApiResponse<BondPoolStatusDto> queryBondPoolStatus(@RequestBody BondPoolAdjustReq req) {
        return ApiResponse.success(bondPoolAdjustService.queryBondPoolStatus(req));
    }

    /** 提交调库申请 */
    @PostMapping("/addAdjustLog")
    public ApiResponse<AdjustSubmitDto> addAdjustLog(@RequestBody BondPoolAdjustSubmitReq req) {
        return ApiResponse.success(bondPoolAdjustService.addAdjustLog(req));
    }

    /** 查询债券的调库记录列表 */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryAdjustLogList(@RequestBody BondPoolAdjustReq req) {
        return ApiResponse.success(bondPoolAdjustService.queryAdjustLogList(req));
    }

    /** 校验债券调库可行性 */
    @PostMapping("/checkAdjust")
    public ApiResponse<AdjustCheckDto> checkAdjust(@RequestBody AdjustCheckReq req) {
        return ApiResponse.success(bondPoolAdjustService.checkAdjust(req));
    }
}
