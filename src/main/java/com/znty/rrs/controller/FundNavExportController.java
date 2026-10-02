package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.fundnavexport.FundNavExportReq;
import com.znty.rrs.entity.fundnavexport.FundNavFundDto;
import com.znty.rrs.service.FundNavExportService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 基金净值导出控制器。 */
@RestController
@RequestMapping("/api/v1/fundNavExport")
public class FundNavExportController {
    /** 基金净值导出服务。 */
    @Resource
    private FundNavExportService fundNavExportService;

    /** 分页查询基金基础信息。 */
    @PostMapping("/queryFundNavFundPage")
    public ApiResponse<PageResult<FundNavFundDto>> queryFundNavFundPage(@RequestBody FundNavExportReq req) {
        return ApiResponse.success(fundNavExportService.queryFundNavFundPage(req));
    }

    /** 导出所选基金在指定日期范围内的净值。 */
    @PostMapping("/exportFundNavExcel")
    public ApiResponse<CommonFileDto> exportFundNavExcel(@RequestBody FundNavExportReq req) {
        return ApiResponse.success(fundNavExportService.exportFundNavExcel(req));
    }
}
