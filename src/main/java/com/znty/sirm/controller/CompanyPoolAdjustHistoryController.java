package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.entity.companypool.CompanyPoolAdjustHistoryDto;
import com.znty.sirm.entity.companypool.CompanyPoolAdjustHistoryReq;
import com.znty.sirm.service.CompanyPoolAdjustHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 主体池调整历史查询控制器
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

}
