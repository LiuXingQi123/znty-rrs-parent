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

/**
 * 基金净值导出控制器
 */
@RestController
@RequestMapping("/api/v1/fundNavExport")
public class FundNavExportController {

    /** 基金净值导出服务 */
    @Resource
    private FundNavExportService fundNavExportService;

    /**
     * 分页查询基金基础信息
     *
     * @param req 基金筛选及分页条件
     * @return 分页基金基础信息列表
     */
    @PostMapping("/queryFundNavFundPage")
    public ApiResponse<PageResult<FundNavFundDto>> queryFundNavFundPage(@RequestBody FundNavExportReq req) {
        return ApiResponse.success(fundNavExportService.queryFundNavFundPage(req));
    }

    /**
     * 导出所选基金在指定日期范围内的净值
     *
     * @param req 所选基金代码及净值日期范围
     * @return Excel 文件名、内容类型、文件大小和 Base64 内容
     */
    @PostMapping("/exportFundNavExcel")
    public ApiResponse<CommonFileDto> exportFundNavExcel(@RequestBody FundNavExportReq req) {
        return ApiResponse.success(fundNavExportService.exportFundNavExcel(req));
    }
}
