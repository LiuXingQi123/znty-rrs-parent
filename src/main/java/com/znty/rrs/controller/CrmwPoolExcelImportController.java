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
     *
     * @param req 含当前用户 ID 的导入池查询请求
     * @return 允许当前用户导入的 CRMW 池列表
     */
    @PostMapping("/queryPoolList")
    public ApiResponse<List<CrmwPoolExcelImportPoolDto>> queryPoolList(
            @RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层按 excel_importable 过滤 CRMW 叶子池
        return ApiResponse.success(crmwPoolExcelImportService.queryPoolList(req));
    }

    /**
     * 上传 Excel 并写入临时表
     *
     * @param req 含目标池、方向和操作人的导入请求
     * @param file 上传的 Excel 文件
     * @param originalFileNameListJson Excel 文件原始名称的 JSON 数组，可不传
     * @return 上传后的导入任务
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
     * 保存导入行的评级主体并清除旧校验结果
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @return 保存后的批次任务及当前页明细，主体修改后须重新校验
     */
    @PostMapping("/editRatingCompany")
    public ApiResponse<CrmwPoolExcelImportDto> editRatingCompany(@RequestBody CrmwPoolExcelImportReq req) {
        return ApiResponse.success(crmwPoolExcelImportService.editRatingCompany(req));
    }

    /**
     * 查询导入批次
     *
     * @param req 导入批次号和可选的明细分页条件
     * @return 导入任务详情
     */
    @PostMapping("/queryTask")
    public ApiResponse<CrmwPoolExcelImportDto> queryTask(@RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层查询批次主表与校验快照
        return ApiResponse.success(crmwPoolExcelImportService.queryTask(req));
    }

    /**
     * 分页查询导入明细
     *
     * @param req 批次号、校验状态、关键字和分页条件
     * @return 导入明细分页结果
     */
    @PostMapping("/queryItemPage")
    public ApiResponse<PageResult<CrmwPoolExcelImportItemDto>> queryItemPage(
            @RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层分页查询导入明细
        return ApiResponse.success(crmwPoolExcelImportService.queryItemPage(req));
    }

    /**
     * 校验导入明细（逐组合委托 CRMW 单笔校验）
     *
     * @param req 批次号、当前用户及分页条件
     * @return 包含逐行结果、调整提示和流程候选的导入任务
     */
    @PostMapping("/checkImport")
    public ApiResponse<CrmwPoolExcelImportDto> checkImport(@RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层执行调库校验
        return ApiResponse.success(crmwPoolExcelImportService.checkImport(req));
    }

    /**
     * 提交导入（写 CRMW 调库日志）
     *
     * @param req 批次号、经办人、原因意见及已选择流程的校验项
     * @return 提交后的导入任务及调库日志 ID
     */
    @PostMapping("/submitImport")
    public ApiResponse<CrmwPoolExcelImportDto> submitImport(@RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层按校验结果提交调库
        return ApiResponse.success(crmwPoolExcelImportService.submitImport(req));
    }

    /**
     * 取消导入批次
     *
     * @param req 待取消任务的导入批次号
     * @return 取消成功响应，数据为新建的空导入结果对象
     */
    @PostMapping("/cancelImport")
    public ApiResponse<CrmwPoolExcelImportDto> cancelImport(@RequestBody CrmwPoolExcelImportReq req) {
        // 委托服务层逻辑删除批次
        crmwPoolExcelImportService.cancelImport(req);
        return ApiResponse.success(new CrmwPoolExcelImportDto());
    }
}
