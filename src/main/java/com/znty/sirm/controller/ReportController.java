package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.entity.report.ReportDto;
import com.znty.sirm.entity.report.ReportReq;
import com.znty.sirm.service.ReportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 报告库查询控制器
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    /** 报告库查询服务 */
    @Resource
    private ReportService reportService;

    /**
     * 分页查询内部报告列表
     */
    @PostMapping("/queryInReportPage")
    public ApiResponse<PageResult<ReportDto>> queryInReportPage(@RequestBody ReportReq req) {
        return ApiResponse.success(reportService.queryInReportPage(req));
    }

    /**
     * 分页查询外部报告列表
     */
    @PostMapping("/queryOutReportPage")
    public ApiResponse<PageResult<ReportDto>> queryOutReportPage(@RequestBody ReportReq req) {
        return ApiResponse.success(reportService.queryOutReportPage(req));
    }
}
