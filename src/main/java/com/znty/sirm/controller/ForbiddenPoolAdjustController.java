package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.sirm.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.sirm.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.sirm.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitDto;
import com.znty.sirm.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.sirm.entity.securitypooladjust.AdjustCheckDto;
import com.znty.sirm.entity.securitypooladjust.AdjustLogDto;
import com.znty.sirm.entity.securitypooladjust.IpAdjustStepDto;
import com.znty.sirm.entity.securitypooladjust.PoolDto;
import com.znty.sirm.service.ForbiddenPoolAdjustService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * 禁投池主体调整控制器。
 */
@RestController
@RequestMapping("/api/v1/forbiddenPoolAdjust")
public class ForbiddenPoolAdjustController {

    /** 禁投池主体调整服务 */
    @Resource
    private ForbiddenPoolAdjustService forbiddenPoolAdjustService;

    /** 分页查询公司主体 */
    @PostMapping("/queryCompanyPage")
    public ApiResponse<PageResult<ForbiddenPoolAdjustDto>> queryCompanyPage(
            @RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyPage(req));
    }

    /** 查询公司主体详情 */
    @PostMapping("/queryCompanyDetail")
    public ApiResponse<ForbiddenPoolAdjustDto> queryCompanyDetail(@RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyDetail(req));
    }

    /** 查询主体和旗下债券当前所在池 */
    @PostMapping("/queryCompanyPoolStatus")
    public ApiResponse<ForbiddenPoolAdjustDto.PoolStatusBundle> queryCompanyPoolStatus(
            @RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyPoolStatus(req));
    }

    /** 查询主体旗下债券明细 */
    @PostMapping("/queryCompanyBondList")
    public ApiResponse<List<ForbiddenPoolAdjustDto.CompanyBond>> queryCompanyBondList(
            @RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyBondList(req));
    }

    /** 查询可操作风险池 */
    @PostMapping("/queryAdjustPoolList")
    public ApiResponse<List<PoolDto>> queryAdjustPoolList(@RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyAdjustPoolList(req));
    }

    /** 校验主体调库 */
    @PostMapping("/checkAdjust")
    public ApiResponse<AdjustCheckDto> checkAdjust(@RequestBody ForbiddenPoolAdjustCheckReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.checkCompanyAdjust(req));
    }

    /** 以 JSON 方式提交主体调库 */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ForbiddenPoolAdjustSubmitDto> addAdjustLog(
            @RequestBody ForbiddenPoolAdjustSubmitReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.addCompanyAdjustLog(req, null));
    }

    /** 以 multipart 方式提交主体调库和附件 */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ForbiddenPoolAdjustSubmitDto> addAdjustLogWithFiles(
            @RequestPart("request") ForbiddenPoolAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        return ApiResponse.success(forbiddenPoolAdjustService.addCompanyAdjustLog(
                req, files == null ? null : Arrays.asList(files)));
    }

    /** 查询主体调库记录 */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryAdjustLogList(@RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyAdjustLogList(req));
    }

    /** 查询主体调库流程步骤 */
    @PostMapping("/queryAdjustStepList")
    public ApiResponse<List<IpAdjustStepDto>> queryAdjustStepList(@RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyAdjustStepList(req));
    }
}
