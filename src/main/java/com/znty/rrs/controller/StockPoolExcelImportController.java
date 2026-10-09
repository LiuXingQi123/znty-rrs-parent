package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.stockpoolexcelimport.StockPoolExcelImportDto;
import com.znty.rrs.entity.stockpoolexcelimport.StockPoolExcelImportItemDto;
import com.znty.rrs.entity.stockpoolexcelimport.StockPoolExcelImportReq;
import com.znty.rrs.service.StockPoolExcelImportService;
import javax.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 股票池 Excel 导入接口。 */
@RestController
@RequestMapping("/api/v1/stockPoolExcelImport")
public class StockPoolExcelImportController {
    /** 股票池 Excel 导入业务组件。 */
    @Resource
    private StockPoolExcelImportService stockPoolExcelImportService;

    /**
     * 上传四列股票模板并创建导入批次。
     *
     * @param req 导入方向、清空参数及操作人
     * @param file 四列股票导入 Excel 文件
     * @param originalFileNameListJson 上传文件原始名称列表 JSON，可为空
     */
    @PostMapping(value = "/uploadExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StockPoolExcelImportDto> uploadExcel(
            @RequestPart("request") StockPoolExcelImportReq req, @RequestPart("file") MultipartFile file,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(stockPoolExcelImportService.uploadExcel(req, file, originalFileNameListJson));
    }

    /**
     * 查询导入批次及校验快照。
     *
     * @param req 导入批次号及原始明细分页条件
     */
    @PostMapping("/queryTask")
    public ApiResponse<StockPoolExcelImportDto> queryTask(@RequestBody StockPoolExcelImportReq req) {
        return ApiResponse.success(stockPoolExcelImportService.queryTask(req));
    }

    /**
     * 分页查询股票原始明细。
     *
     * @param req 导入批次号、筛选条件及分页参数
     */
    @PostMapping("/queryItemPage")
    public ApiResponse<PageResult<StockPoolExcelImportItemDto>> queryItemPage(
            @RequestBody StockPoolExcelImportReq req) {
        return ApiResponse.success(stockPoolExcelImportService.queryItemPage(req));
    }

    /**
     * 校验导入行及清空出库项。
     *
     * @param req 待校验批次号及当前操作人
     */
    @PostMapping("/checkImport")
    public ApiResponse<StockPoolExcelImportDto> checkImport(@RequestBody StockPoolExcelImportReq req) {
        return ApiResponse.success(stockPoolExcelImportService.checkImport(req));
    }

    /**
     * 从服务器快照提交已通过的来源组。
     *
     * @param req 批次号、已有候选流程选择及提交说明
     */
    @PostMapping("/submitImport")
    public ApiResponse<StockPoolExcelImportDto> submitImport(@RequestBody StockPoolExcelImportReq req) {
        return ApiResponse.success(stockPoolExcelImportService.submitImport(req));
    }

    /**
     * 取消尚未提交的导入批次。
     *
     * @param req 待取消的未提交批次号
     */
    @PostMapping("/cancelImport")
    public ApiResponse<StockPoolExcelImportDto> cancelImport(@RequestBody StockPoolExcelImportReq req) {
        return ApiResponse.success(stockPoolExcelImportService.cancelImport(req));
    }
}
