package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportItemDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportReq;
import com.znty.rrs.service.SecurityPoolExcelImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

/**
 * 证券/主体 Excel 导入接口
 */
@RestController
@RequestMapping("/api/v1/securityPoolExcelImport")
public class SecurityPoolExcelImportController {

    /** 证券/主体 Excel 导入业务服务 */
    @Resource
    private SecurityPoolExcelImportService securityPoolExcelImportService;

    /**
     * 上传 Excel 并写入临时表
     */
    @PostMapping(value = "/uploadExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SecurityPoolExcelImportDto> uploadExcel(
            @RequestPart("request") SecurityPoolExcelImportReq req,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        // 委托服务层解析 Excel 并落临时表
        return ApiResponse.success(securityPoolExcelImportService.uploadExcel(req, file, originalFileNameListJson));
    }

    /**
     * 查询导入批次
     */
    @PostMapping("/queryTask")
    public ApiResponse<SecurityPoolExcelImportDto> queryTask(@RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层查询批次主表与校验快照
        return ApiResponse.success(securityPoolExcelImportService.queryTask(req));
    }

    /**
     * 分页查询导入明细
     */
    @PostMapping("/queryItemPage")
    public ApiResponse<PageResult<SecurityPoolExcelImportItemDto>> queryItemPage(
            @RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层分页查询导入明细
        return ApiResponse.success(securityPoolExcelImportService.queryItemPage(req));
    }

    /**
     * 校验导入明细（证券/主体分支）
     */
    @PostMapping("/checkImport")
    public ApiResponse<SecurityPoolExcelImportDto> checkImport(@RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层执行调库校验
        return ApiResponse.success(securityPoolExcelImportService.checkImport(req));
    }

    /**
     * 提交导入（写调库日志）
     */
    @PostMapping("/submitImport")
    public ApiResponse<SecurityPoolExcelImportDto> submitImport(@RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层按校验结果提交调库
        return ApiResponse.success(securityPoolExcelImportService.submitImport(req));
    }

    /**
     * 取消导入批次
     */
    @PostMapping("/cancelImport")
    public ApiResponse<Void> cancelImport(@RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层逻辑删除批次
        securityPoolExcelImportService.cancelImport(req);
        return ApiResponse.success();
    }
}
