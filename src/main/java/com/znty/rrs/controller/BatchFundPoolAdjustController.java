package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundAdjustDto;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundAdjustReq;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolAdjustReq;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolDto;
import com.znty.rrs.entity.fundpooladjust.FundInfoDto;
import com.znty.rrs.service.BatchFundPoolAdjustService;
import java.util.Arrays;
import javax.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 基金池批量调整控制器
 */
@RestController
@RequestMapping("/api/v1/batchFundPoolAdjust")
public class BatchFundPoolAdjustController {

    /** 基金池批量调整服务 */
    @Resource
    private BatchFundPoolAdjustService batchFundPoolAdjustService;

    /**
     * 分页查询当前用户可调整的基金池
     *
     * @param req 当前用户、投资池筛选和分页条件
     * @return 可批量调整的投资池及分页信息
     */
    @PostMapping("/queryPoolPage")
    public ApiResponse<PageResult<BatchFundPoolDto>> queryPoolPage(@RequestBody BatchFundPoolAdjustReq req) {
        return ApiResponse.success(batchFundPoolAdjustService.queryPoolPage(req));
    }

    /**
     * 分页查询批量调整候选基金
     *
     * @param req 目标池、调整方向、候选筛选和分页条件
     * @return 可批量调整的基金及分页信息
     */
    @PostMapping("/queryFundPage")
    public ApiResponse<PageResult<FundInfoDto>> queryFundPage(@RequestBody BatchFundPoolAdjustReq req) {
        return ApiResponse.success(batchFundPoolAdjustService.queryFundPage(req));
    }

    /**
     * 校验整批基金并展开每只基金的关系项
     *
     * @param req 当前用户、目标池、调整方向和所选基金
     * @return 逐项校验结果及可选审批流程
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<BatchFundAdjustDto> checkAdjust(@RequestBody BatchFundAdjustReq req) {
        return ApiResponse.success(batchFundPoolAdjustService.checkAdjust(req));
    }

    /**
     * 原子提交整批基金调整申请
     *
     * @param req 经确认的基金调整明细及所选审批流程
     * @return 整批提交结果，包含提交数量和调库日志 ID
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BatchFundAdjustDto> addAdjustLog(@RequestBody BatchFundAdjustReq req) {
        return ApiResponse.success(batchFundPoolAdjustService.addAdjustLog(req));
    }

    /**
     * 原子提交基金批量申请及共用报告、材料附件
     *
     * @param req 经确认的基金调整明细及所选审批流程
     * @param files 随申请上传的材料附件，可不传
     * @param originalFileNameListJson 附件原始文件名的 JSON 数组，可不传
     * @return 整批提交结果，包含提交数量和调库日志 ID
     */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BatchFundAdjustDto> addAdjustLogWithFiles(
            @RequestPart("request") BatchFundAdjustReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(batchFundPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }
}
