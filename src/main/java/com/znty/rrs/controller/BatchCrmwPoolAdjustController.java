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
 * <p>
 * 负责批量调库目标池查询、候选组合筛选、批量调库校验及批量调库申请提交。
 * 支持 JSON 与 multipart 两种提交方式，multipart 场景用于随申请一并上传材料附件。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/batchCrmwPoolAdjust")
public class BatchCrmwPoolAdjustController {

    /** CRMW 池批量调整服务 */
    @Resource
    private BatchCrmwPoolAdjustService batchCrmwPoolAdjustService;

    /**
     * 分页查询当前用户可调整的启用叶子 CRMW 投资池
     */
    @PostMapping("/queryPoolPage")
    public ApiResponse<PageResult<BatchCrmwPoolDto>> queryPoolPage(
            @RequestBody BatchCrmwPoolAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.queryPoolPage(req));
    }

    /**
     * 分页查询目标池可调入候选组合
     */
    @PostMapping("/queryInboundCandidatePage")
    public ApiResponse<PageResult<BatchCrmwCandidateDto>> queryInboundCandidatePage(
            @RequestBody BatchCrmwPoolAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.queryInboundCandidatePage(req));
    }

    /**
     * 分页查询目标池可调出候选组合
     */
    @PostMapping("/queryOutboundCandidatePage")
    public ApiResponse<PageResult<BatchCrmwCandidateDto>> queryOutboundCandidatePage(
            @RequestBody BatchCrmwPoolAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.queryOutboundCandidatePage(req));
    }

    /**
     * 批量调库下一步校验
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<BatchCrmwAdjustDto> checkAdjust(
            @RequestBody BatchCrmwAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.checkAdjust(req));
    }

    /**
     * 批量提交调库申请
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchCrmwAdjustDto> addAdjustLog(
            @RequestBody BatchCrmwAdjustReq req) {
        return ApiResponse.success(batchCrmwPoolAdjustService.addAdjustLog(req));
    }

    /**
     * 以 multipart 方式批量提交调库申请及附件
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
