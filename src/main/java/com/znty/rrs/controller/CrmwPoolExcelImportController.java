package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportItemDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportPoolDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportReq;
import com.znty.rrs.service.CrmwPoolExcelImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

/**
 * CRMW 池 Excel 导入接口
 */
@RestController
@RequestMapping("/api/v1/crmwPoolExcelImport")
public class CrmwPoolExcelImportController {

    /** CRMW 池 Excel 导入业务服务 */
    @Resource
    private CrmwPoolExcelImportService crmwPoolExcelImportService;

    /**
     * 查询当前用户可 Excel 导入的启用叶子 CRMW 池
     */
    @PostMapping("/queryPoolList")
    public ApiResponse<List<CrmwPoolExcelImportPoolDto>> queryPoolList(
            @RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层按 excel_importable 过滤 CRMW 叶子池
        return ApiResponse.success(crmwPoolExcelImportService.queryPoolList(req));
    }

    /**
     * 上传 Excel 并写入临时表
     */
    @PostMapping(value = "/uploadExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CrmwPoolExcelImportDto> uploadExcel(
            @RequestPart("request") CrmwPoolExcelImportReq req,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        // 委托服务层解析 Excel 并落临时表
        return ApiResponse.success(crmwPoolExcelImportService.uploadExcel(req, file, originalFileNameListJson));
    }

    /**
     * 查询导入批次
     */
    @PostMapping("/queryTask")
    public ApiResponse<CrmwPoolExcelImportDto> queryTask(@RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层查询批次主表与校验快照
        return ApiResponse.success(crmwPoolExcelImportService.queryTask(req));
    }

    /**
     * 分页查询导入明细
     */
    @PostMapping("/queryItemPage")
    public ApiResponse<PageResult<CrmwPoolExcelImportItemDto>> queryItemPage(
            @RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层分页查询导入明细
        return ApiResponse.success(crmwPoolExcelImportService.queryItemPage(req));
    }

    /**
     * 校验导入明细（逐组合委托 CRMW 单笔校验）
     */
    @PostMapping("/checkImport")
    public ApiResponse<CrmwPoolExcelImportDto> checkImport(@RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层执行调库校验
        return ApiResponse.success(crmwPoolExcelImportService.checkImport(req));
    }

    /**
     * 提交导入（写 CRMW 调库日志）
     */
    @PostMapping("/submitImport")
    public ApiResponse<CrmwPoolExcelImportDto> submitImport(@RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层按校验结果提交调库
        return ApiResponse.success(crmwPoolExcelImportService.submitImport(req));
    }

    /**
     * 取消导入批次
     */
    @PostMapping("/cancelImport")
    public ApiResponse<CrmwPoolExcelImportDto> cancelImport(@RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层逻辑删除批次
        crmwPoolExcelImportService.cancelImport(req);
        return ApiResponse.success(new CrmwPoolExcelImportDto());
    }
}
