package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.hspoolexport.HsPoolManualExportReq;
import com.znty.rrs.service.HsPoolManualExportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/** 恒生格式手动导出接口。 */
@RestController
@RequestMapping("/api/v1/hsPoolManualExport")
public class HsPoolManualExportController {
    /** 恒生格式手动导出服务。 */
    @Resource
    private HsPoolManualExportService hsPoolManualExportService;

    /** 按页面条件生成并下载恒生格式 Excel。 */
    @PostMapping("/exportHsPoolExcel")
    public ApiResponse<CommonFileDto> exportHsPoolExcel(@RequestBody HsPoolManualExportReq req) {
        // 校验导出条件并生成前端可下载的 Base64 文件。
        return ApiResponse.success(hsPoolManualExportService.exportHsPoolExcel(req));
    }
}
