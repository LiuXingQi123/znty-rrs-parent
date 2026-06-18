package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.ForbiddenPoolHistoryDto;
import com.znty.sirm.model.ForbiddenPoolHistoryReq;
import com.znty.sirm.service.ForbiddenPoolHistoryService;
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
