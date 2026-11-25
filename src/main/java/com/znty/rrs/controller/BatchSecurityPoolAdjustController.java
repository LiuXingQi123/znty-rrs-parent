package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityCandidateDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderReq;
import com.znty.rrs.service.BatchSecurityPoolAdjustService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * 证券池批量调整控制器
 *
 * 负责批量调库目标池查询、候选证券筛选、批量调库校验及批量调库申请提交
 * 支持 JSON 与 multipart 两种提交方式，multipart 场景用于随申请一并上传材料附件
 */
@RestController
@RequestMapping("/api/v1/batchSecurityPoolAdjust")
public class BatchSecurityPoolAdjustController {

    /** 证券池批量调整服务 */
    @Resource
    private BatchSecurityPoolAdjustService batchSecurityPoolAdjustService;

    /**
     * 分页查询当前用户可调整的启用叶子投资池
     *
     * @param req 当前用户、投资池筛选和分页条件
     * @return 可批量调整的投资池及分页信息
     */
    @PostMapping("/queryPoolPage")
    public ApiResponse<PageResult<BatchSecurityPoolDto>> queryPoolPage(
            @RequestBody BatchSecurityPoolAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.queryPoolPage(req));
    }

    /**
     * 分页查询目标池批量调整候选证券
     *
     * @param req 目标池、调整方向、候选筛选和分页条件
     * @return 可批量调整的证券及分页信息
     */
    @PostMapping("/querySecurityPage")
    public ApiResponse<PageResult<BatchSecurityCandidateDto>> querySecurityPage(
            @RequestBody BatchSecurityPoolAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.querySecurityPage(req));
    }

    /**
     * 分页查询批量调库可选的 ABS 权益人主体
     *
     * @param req 分页与检索条件
     * @return 可选主体分页结果
     */
    @PostMapping("/querySelfSelectedRightsHolderPage")
    public ApiResponse<PageResult<SelfSelectedRightsHolderDto>> querySelfSelectedRightsHolderPage(
            @RequestBody SelfSelectedRightsHolderReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.querySelfSelectedRightsHolderPage(req));
    }

    /**
     * 批量调库下一步校验
     *
     * @param req 当前用户、目标池、调整方向和所选证券
     * @return 逐项校验结果及可选审批流程
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<BatchSecurityInboundAdjustDto> checkAdjust(
            @RequestBody BatchSecurityInboundAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.checkAdjust(req));
    }

    /**
     * 批量提交调库申请
     *
     * @param req 经确认的证券调整明细及所选审批流程
     * @return 整批提交结果，包含提交数量和调库日志 ID
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchSecurityInboundAdjustDto> addAdjustLog(
            @RequestBody BatchSecurityInboundAdjustReq req) {
        return ApiResponse.success(batchSecurityPoolAdjustService.addAdjustLog(req));
    }

    /**
     * 以 multipart 方式批量提交调库申请及附件
     *
     * @param req 经确认的证券调整明细及所选审批流程
     * @param files 随申请上传的材料附件，可不传
     * @param originalFileNameListJson 附件原始文件名的 JSON 数组，可不传
     * @return 整批提交结果，包含提交数量和调库日志 ID
     */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BatchSecurityInboundAdjustDto> addAdjustLogWithFiles(
            @RequestPart("request") BatchSecurityInboundAdjustReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(batchSecurityPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }
}
