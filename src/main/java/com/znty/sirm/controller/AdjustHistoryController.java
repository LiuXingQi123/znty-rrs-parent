package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.AdjustHistoryDto;
import com.znty.sirm.model.AdjustHistoryReq;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.service.AdjustHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 债券池调整历史查询接口
 */
@RestController
@RequestMapping("/api/v1/adjustHistory")
public class AdjustHistoryController {

    @Resource
    private AdjustHistoryService adjustHistoryService;

    /**
     * 分页查询调整历史列表
     */
    @PostMapping("/queryAdjustHistoryPage")
    public ApiResponse<PageResult<AdjustHistoryDto>> queryAdjustHistoryPage(@RequestBody AdjustHistoryReq req) {
        return ApiResponse.success(adjustHistoryService.queryAdjustHistoryPage(req));
    }

    /**
     * 查询投资池树列表（供前端树多选组件使用）
     */
    @PostMapping("/queryPoolTreeList")
    public ApiResponse<List<PoolDto>> queryPoolTreeList(@RequestBody AdjustHistoryReq req) {
        return ApiResponse.success(adjustHistoryService.queryPoolTreeList());
    }
}
