package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwAdjustDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwAdjustReq;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwCandidateDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolAdjustReq;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolDto;
import com.znty.rrs.service.BatchCrmwPoolAdjustService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * CRMW 池批量调整控制器
 */
@RestController
@RequestMapping("/api/v1/batchCrmwPoolAdjust")
public class BatchCrmwPoolAdjustController {

    /** CRMW 池批量调整服务 */
    @Resource
    private BatchCrmwPoolAdjustService batchCrmwPoolAdjustService;

    /**
     * 分页查询当前用户可调整且已启用的 CRMW 叶子池
     *
     * @param req 当前用户、池筛选和分页条件
     * @return 符合条件的 CRMW 叶子池分页结果
     */
    @PostMapping("/queryPoolPage")
    public ApiResponse<PageResult<BatchCrmwPoolDto>> queryPoolPage(
            @RequestBody BatchCrmwPoolAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.queryPoolPage(req));
    }

    /**
     * 分页查询可调入目标池的候选组合
     *
     * @param req 当前用户、目标池、候选筛选和分页条件
     * @return 可调入目标池的 CRMW 组合分页结果
     */
    @PostMapping("/queryInboundCandidatePage")
    public ApiResponse<PageResult<BatchCrmwCandidateDto>> queryInboundCandidatePage(
            @RequestBody BatchCrmwPoolAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.queryInboundCandidatePage(req));
    }

    /**
     * 分页查询可从目标池调出的候选组合
     *
     * @param req 当前用户、目标池、候选筛选和分页条件
     * @return 可从目标池调出的 CRMW 组合分页结果
     */
    @PostMapping("/queryOutboundCandidatePage")
    public ApiResponse<PageResult<BatchCrmwCandidateDto>> queryOutboundCandidatePage(
            @RequestBody BatchCrmwPoolAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.queryOutboundCandidatePage(req));
    }

    /**
     * 校验批量调库选中的 CRMW 组合
     *
     * @param req 目标池、调整方向和选中的凭证与标的组合
     * @return 调整明细及各项的校验结果和流程候选
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<BatchCrmwAdjustDto> checkAdjust(
            @RequestBody BatchCrmwAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.checkAdjust(req));
    }

    /**
     * 以 JSON 方式批量提交调库申请
     *
     * @param req 批量调库申请
     * @return 整批提交结果，包含提交数量和调库日志 ID
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchCrmwAdjustDto> addAdjustLog(
            @RequestBody BatchCrmwAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.addAdjustLog(req));
    }

    /**
     * 以 multipart 方式批量提交调库申请，可随申请上传材料附件
     *
     * @param req 批量调库申请
     * @param files 随申请上传的材料附件，可不传
     * @param originalFileNameListJson 附件原始文件名的 JSON 数组，可不传
     * @return 整批提交结果，包含提交数量和调库日志 ID
     */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BatchCrmwAdjustDto> addAdjustLogWithFiles(
            @RequestPart("request") BatchCrmwAdjustReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(batchCrmwPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }
}
