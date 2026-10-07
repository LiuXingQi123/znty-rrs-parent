package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportItemDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportReq;
import com.znty.rrs.service.FundPoolExcelImportService;
import javax.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 基金池 Excel 导入接口。 */
@RestController
@RequestMapping("/api/v1/fundPoolExcelImport")
public class FundPoolExcelImportController {
    /** 基金池 Excel 导入业务组件。 */
    @Resource
    private FundPoolExcelImportService fundPoolExcelImportService;

    /**
     * 上传七列基金模板并创建导入批次。
     *
     * @param req 导入方向、清空参数及操作人
     * @param file 七列基金导入 Excel 文件
     * @param originalFileNameListJson 上传文件原始名称列表 JSON，可为空
     */
    @PostMapping(value = "/uploadExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FundPoolExcelImportDto> uploadExcel(
            @RequestPart("request") FundPoolExcelImportReq req, @RequestPart("file") MultipartFile file,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(fundPoolExcelImportService.uploadExcel(req, file, originalFileNameListJson));
    }

    /**
     * 查询导入批次及校验快照。
     *
     * @param req 导入批次号及原始明细分页条件
     */
    @PostMapping("/queryTask")
    public ApiResponse<FundPoolExcelImportDto> queryTask(@RequestBody FundPoolExcelImportReq req) {
        return ApiResponse.success(fundPoolExcelImportService.queryTask(req));
    }

    /**
     * 分页查询基金原始明细。
     *
     * @param req 导入批次号、筛选条件及分页参数
     */
    @PostMapping("/queryItemPage")
    public ApiResponse<PageResult<FundPoolExcelImportItemDto>> queryItemPage(
            @RequestBody FundPoolExcelImportReq req) {
        return ApiResponse.success(fundPoolExcelImportService.queryItemPage(req));
    }

    /**
     * 校验导入行及清空出库项。
     *
     * @param req 待校验批次号及当前操作人
     */
    @PostMapping("/checkImport")
    public ApiResponse<FundPoolExcelImportDto> checkImport(@RequestBody FundPoolExcelImportReq req) {
        return ApiResponse.success(fundPoolExcelImportService.checkImport(req));
    }

    /**
     * 从服务器快照提交已通过的来源组。
     *
     * @param req 批次号、已有候选流程选择及提交说明
     */
    @PostMapping("/submitImport")
    public ApiResponse<FundPoolExcelImportDto> submitImport(@RequestBody FundPoolExcelImportReq req) {
        return ApiResponse.success(fundPoolExcelImportService.submitImport(req));
    }

    /**
     * 取消尚未提交的导入批次。
     *
     * @param req 待取消的未提交批次号
     */
    @PostMapping("/cancelImport")
    public ApiResponse<FundPoolExcelImportDto> cancelImport(@RequestBody FundPoolExcelImportReq req) {
        return ApiResponse.success(fundPoolExcelImportService.cancelImport(req));
    }
}
