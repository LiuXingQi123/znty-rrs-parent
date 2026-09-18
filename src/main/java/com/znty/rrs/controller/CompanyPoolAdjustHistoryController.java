package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.companypool.CompanyPoolAdjustHistoryDto;
import com.znty.rrs.entity.companypool.CompanyPoolAdjustHistoryReq;
import com.znty.rrs.service.CompanyPoolAdjustHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 主体池调整历史查询控制器。
 * <p>提供主体池调整日志分页查询与导出接口。</p>
 */
@RestController
@RequestMapping("/api/v1/companyPoolAdjustHistory")
public class CompanyPoolAdjustHistoryController {

    /** 主体池调整历史服务 */
    @Resource
    private CompanyPoolAdjustHistoryService companyPoolAdjustHistoryService;

    /** 分页查询主体池调整历史 */
    @PostMapping("/queryCompanyPoolAdjustHistoryPage")
    public ApiResponse<PageResult<CompanyPoolAdjustHistoryDto>> queryCompanyPoolAdjustHistoryPage(
            @RequestBody CompanyPoolAdjustHistoryReq req) {
        return ApiResponse.success(companyPoolAdjustHistoryService.queryCompanyPoolAdjustHistoryPage(req));
    }

    /**
     * 按当前查询条件导出主体池调整历史。
     * <p>筛选条件与列表一致，不传分页时导出全部命中记录。</p>
     */
    @PostMapping("/exportCompanyPoolAdjustHistoryExcel")
    public ApiResponse<CommonFileDto> exportCompanyPoolAdjustHistoryExcel(
            @RequestBody CompanyPoolAdjustHistoryReq req) {
        return ApiResponse.success(companyPoolAdjustHistoryService.exportCompanyPoolAdjustHistoryExcel(req));
    }

}
