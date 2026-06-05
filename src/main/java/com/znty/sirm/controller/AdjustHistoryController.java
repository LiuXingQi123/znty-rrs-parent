package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.AdjustHistoryDto;
import com.znty.sirm.model.AdjustHistoryReq;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.model.SecurityTypeOptionDto;
import com.znty.sirm.service.AdjustHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 证券池调整历史控制器
 * <p>
 * 负责证券入池/出池调整操作的审计记录查询，提供分页检索、
 * 证券类型下拉及投资池树列表等辅助查询接口，用于操作追溯与合规审查。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/adjustHistory")
public class AdjustHistoryController {

    @Resource
    private AdjustHistoryService adjustHistoryService;

    /**
     * 分页查询证券池调整历史记录
     * <p>支持按证券代码、调整类型、操作时间区间等条件筛选，结果按操作时间倒序排列。</p>
     */
    @PostMapping("/queryAdjustHistoryPage")
    public ApiResponse<PageResult<AdjustHistoryDto>> queryAdjustHistoryPage(@RequestBody AdjustHistoryReq req) {
        return ApiResponse.success(adjustHistoryService.queryAdjustHistoryPage(req));
    }

    /**
     * 查询证券类型下拉选项（用于历史记录筛选条件）
     * <p>返回系统中存在调整历史的证券类型列表，供前端筛选条件使用。</p>
     */
    @PostMapping("/querySecurityTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> querySecurityTypeList() {
        return ApiResponse.success(adjustHistoryService.querySecurityTypeList());
    }

    /**
     * 查询投资池树列表（供前端树多选组件使用）
     * <p>返回全量投资池层级数据，前端据此渲染树形多选筛选控件。</p>
     */
    @PostMapping("/queryPoolTreeList")
    public ApiResponse<List<PoolDto>> queryPoolTreeList(@RequestBody AdjustHistoryReq req) {
        return ApiResponse.success(adjustHistoryService.queryPoolTreeList());
    }
}
