package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryDto;
import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryReq;
import com.znty.rrs.service.ForbiddenPoolHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 禁投池历史查询控制器
 */
@RestController
@RequestMapping("/api/v1/forbiddenPoolHistory")
public class ForbiddenPoolHistoryController {

    /** 禁投池历史查询服务 */
    @Resource
    private ForbiddenPoolHistoryService forbiddenPoolHistoryService;

    /** 分页查询禁投池调整历史 */
    @PostMapping("/queryForbiddenPoolHistoryPage")
    public ApiResponse<PageResult<ForbiddenPoolHistoryDto>> queryForbiddenPoolHistoryPage(
            @RequestBody ForbiddenPoolHistoryReq req) {
        return ApiResponse.success(forbiddenPoolHistoryService.queryForbiddenPoolHistoryPage(req));
    }
}
