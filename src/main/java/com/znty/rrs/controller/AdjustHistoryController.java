package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.adjusthistory.AdjustHistoryDto;
import com.znty.rrs.entity.adjusthistory.AdjustHistoryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.service.AdjustHistoryService;
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
 * 证券类型下拉等辅助查询接口，用于操作追溯与合规审查。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/adjustHistory")
public class AdjustHistoryController {

    /** 调库历史服务 */
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
    public ApiResponse<List<SecurityTypeOptionDto>> querySecurityTypeList(
            @RequestBody AdjustHistoryReq req) {
        return ApiResponse.success(adjustHistoryService.querySecurityTypeList());
    }

}
