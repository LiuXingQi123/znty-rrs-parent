package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.securitypoolexcelimport.PoolClearSummaryDto;
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
import java.util.List;

/**
 * 证券、主体 Excel 导入接口
 */
@RestController
@RequestMapping("/api/v1/securityPoolExcelImport")
public class SecurityPoolExcelImportController {

    /** 证券/主体 Excel 导入业务服务 */
    @Resource
    private SecurityPoolExcelImportService securityPoolExcelImportService;

    /**
     * 上传 Excel 并写入临时表
     *
     * @param req 导入类型、调整方向和操作人
     * @param file 上传的 Excel 文件
     * @param originalFileNameListJson Excel 文件原始名称的 JSON 数组，可不传
     * @return 导入批次和原始明细预览
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
     *
     * @param req 导入批次号和可选的明细分页条件
     * @return 证券或主体 Excel 导入结果
     */
    @PostMapping("/queryTask")
    public ApiResponse<SecurityPoolExcelImportDto> queryTask(@RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层查询批次主表与校验快照
        return ApiResponse.success(securityPoolExcelImportService.queryTask(req));
    }

    /**
     * 分页查询导入明细
     *
     * @param req 导入批次号、校验状态、关键字和分页条件
     * @return 证券或主体 Excel 导入明细及分页信息
     */
    @PostMapping("/queryItemPage")
    public ApiResponse<PageResult<SecurityPoolExcelImportItemDto>> queryItemPage(
            @RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层分页查询导入明细
        return ApiResponse.success(securityPoolExcelImportService.queryItemPage(req));
    }

    /**
     * 修改证券导入明细的页面评级主体选择
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @return 保存后的批次任务及当前页明细，主体修改后须重新校验
     */
    @PostMapping("/editRatingCompany")
    public ApiResponse<SecurityPoolExcelImportDto> editRatingCompany(@RequestBody SecurityPoolExcelImportReq req) {
        // 保存同一证券各导入行的评级主体，并清除旧校验结果
        return ApiResponse.success(securityPoolExcelImportService.editRatingCompany(req));
    }

    /**
     * 校验导入明细（证券、主体分支）
     *
     * @param req 待校验的导入批次号和当前用户 ID
     * @return 证券或主体 Excel 导入结果
     */
    @PostMapping("/checkImport")
    public ApiResponse<SecurityPoolExcelImportDto> checkImport(@RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层执行调库校验
        return ApiResponse.success(securityPoolExcelImportService.checkImport(req));
    }

    /**
     * 提交导入（写调库日志）
     *
     * @param req 携带批次号、经办人、原因意见及可提交校验项的提交请求
     * @return 提交后的批次任务及调库记录信息
     */
    @PostMapping("/submitImport")
    public ApiResponse<SecurityPoolExcelImportDto> submitImport(@RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层按校验结果提交调库
        return ApiResponse.success(securityPoolExcelImportService.submitImport(req));
    }

    /**
     * 查询可清空投资池的成员数量
     *
     * @param req 当前操作人
     * @return 可清空池列表
     */
    @PostMapping("/queryClearPoolList")
    public ApiResponse<List<PoolClearSummaryDto>> queryClearPoolList(@RequestBody SecurityPoolExcelImportReq req) {
        return ApiResponse.success(securityPoolExcelImportService.queryClearPoolList(req));
    }

    /**
     * 清空选中的投资池
     *
     * @param req 所选叶子池和操作人
     * @return 已生效的调库日志 ID
     */
    @PostMapping("/clearPools")
    public ApiResponse<List<Long>> clearPools(@RequestBody SecurityPoolExcelImportReq req) {
        return ApiResponse.success(securityPoolExcelImportService.clearPools(req));
    }

    /**
     * 取消导入批次
     *
     * @param req 待取消的导入批次号
     * @return 取消成功响应，数据为空
     */
    @PostMapping("/cancelImport")
    public ApiResponse<Void> cancelImport(@RequestBody SecurityPoolExcelImportReq req) {
        // 委托服务层逻辑删除批次
        securityPoolExcelImportService.cancelImport(req);
        return ApiResponse.success();
    }
}
