package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckReq;
import com.znty.rrs.entity.fundpooladjust.FundAdjustSubmitDto;
import com.znty.rrs.entity.fundpooladjust.FundInfoDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustReq;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.entity.fundpooladjust.FundPoolDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolStatusDto;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.service.FundPoolAdjustService;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 基金池调整申请控制器 */
@RestController
@RequestMapping("/api/v1/fundPoolAdjust")
public class FundPoolAdjustController {
    /** 基金池调整服务 */
    @Resource
    private FundPoolAdjustService fundPoolAdjustService;

    /**
     * 分页查询可申请调库的基金。
     *
     * @param req 基金筛选条件及分页参数
     */
    @PostMapping("/queryFundPage")
    public ApiResponse<PageResult<FundInfoDto>> queryFundPage(@RequestBody FundPoolAdjustReq req) {
        return ApiResponse.success(fundPoolAdjustService.queryFundPage(req));
    }

    /**
     * 查询有效基金产品类型。
     *
     * @param req 请求体，保持统一接口入参格式
     */
    @PostMapping("/queryFundTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> queryFundTypeList(@RequestBody FundPoolAdjustReq req) {
        return ApiResponse.success(fundPoolAdjustService.queryFundTypeList());
    }

    /**
     * 查询基金只读基础信息。
     *
     * @param req 包含基金代码的查询请求
     */
    @PostMapping("/queryFundDetail")
    public ApiResponse<FundInfoDto> queryFundDetail(@RequestBody FundPoolAdjustReq req) {
        return ApiResponse.success(fundPoolAdjustService.queryFundDetail(req));
    }

    /**
     * 查询当前用户可调整的基金池。
     *
     * @param req 当前用户及调整方向等筛选条件
     */
    @PostMapping("/queryAdjustPoolList")
    public ApiResponse<List<FundPoolDto>> queryAdjustPoolList(@RequestBody FundPoolAdjustReq req) {
        return ApiResponse.success(fundPoolAdjustService.queryAdjustPoolList(req));
    }

    /**
     * 查询基金当前所在池。
     *
     * @param req 包含基金代码的查询请求
     */
    @PostMapping("/queryFundPoolStatus")
    public ApiResponse<List<FundPoolStatusDto>> queryFundPoolStatus(@RequestBody FundPoolAdjustReq req) {
        return ApiResponse.success(fundPoolAdjustService.queryFundPoolStatus(req));
    }

    /**
     * 查询基金调库记录列表，用于审核与详情上下文展示。
     *
     * @param req 调库批次号或调库记录筛选条件
     */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<FundAdjustLogBo>> queryAdjustLogList(@RequestBody FundPoolAdjustReq req) {
        return ApiResponse.success(fundPoolAdjustService.queryAdjustLogList(req));
    }

    /**
     * 查询基金调库流程步骤。
     *
     * @param req 调库批次号或调库记录 ID
     */
    @PostMapping("/queryAdjustStepList")
    public ApiResponse<List<FundAdjustStepBo>> queryAdjustStepList(@RequestBody FundPoolAdjustReq req) {
        return ApiResponse.success(fundPoolAdjustService.queryAdjustStepList(req));
    }

    /**
     * 校验基金调库申请。
     *
     * @param req 基金代码与待调整池明细
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<FundAdjustCheckDto> checkAdjust(@RequestBody FundAdjustCheckReq req) {
        return ApiResponse.success(fundPoolAdjustService.checkAdjust(req));
    }

    /**
     * 提交不含本地文件的基金调库申请。
     *
     * @param req 基金调库明细及流程选择
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<FundAdjustSubmitDto> addAdjustLog(@RequestBody FundPoolAdjustSubmitReq req) {
        return ApiResponse.success(fundPoolAdjustService.addAdjustLog(req));
    }

    /**
     * 以 multipart 方式提交基金调库申请及附件。
     *
     * @param req 基金调库明细及附件引用
     * @param files 本次上传的附件文件
     * @param originalFileNameListJson 附件原始文件名列表的 JSON 字符串
     */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FundAdjustSubmitDto> addAdjustLogWithFiles(
            @RequestPart("request") FundPoolAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(fundPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }

}
