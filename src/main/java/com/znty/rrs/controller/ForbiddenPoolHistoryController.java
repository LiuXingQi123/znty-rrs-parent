package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryDto;
import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryReq;
import com.znty.rrs.service.ForbiddenPoolHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 禁投池历史查询控制器。
 * <p>提供禁投池调整日志分页查询与导出接口。</p>
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

    /**
     * 按当前查询条件导出禁投池历史。
     * <p>筛选条件与列表一致，不传分页时导出全部命中记录。</p>
     */
    @PostMapping("/exportForbiddenPoolHistoryExcel")
    public ApiResponse<CommonFileDto> exportForbiddenPoolHistoryExcel(@RequestBody ForbiddenPoolHistoryReq req) {
        return ApiResponse.success(forbiddenPoolHistoryService.exportForbiddenPoolHistoryExcel(req));
    }
}
