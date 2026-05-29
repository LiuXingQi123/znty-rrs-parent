package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.AdjustLogDto;
import com.znty.sirm.model.BondInfoDetailDto;
import com.znty.sirm.model.BondInfoDto;
import com.znty.sirm.model.BondPoolAdjustReq;
import com.znty.sirm.model.BondPoolAdjustSubmitReq;
import com.znty.sirm.model.PoolTreeNodeDto;
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
        return ApiResponse.success(bondPoolAdjustService.queryBondDetail(req.getBondId()));
    }

    /** 查询可调库/可调出库的投资池树 */
    @PostMapping("/queryAdjustPoolTree")
    public ApiResponse<List<PoolTreeNodeDto>> queryAdjustPoolTree(@RequestBody BondPoolAdjustReq req) {
        return ApiResponse.success(bondPoolAdjustService.queryPoolTreeForAdjust(req.getBondId(), req.getAdjustDirection()));
    }

    /** 提交调库申请 */
    @PostMapping("/submitAdjust")
    public ApiResponse<String> submitAdjust(@RequestBody BondPoolAdjustSubmitReq req) {
        bondPoolAdjustService.submitAdjust(req);
        return ApiResponse.success("提交成功");
    }

    /** 查询债券的调库记录列表 */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryAdjustLogList(@RequestBody BondPoolAdjustReq req) {
        return ApiResponse.success(bondPoolAdjustService.queryAdjustLogList(req.getBondId()));
    }
}
