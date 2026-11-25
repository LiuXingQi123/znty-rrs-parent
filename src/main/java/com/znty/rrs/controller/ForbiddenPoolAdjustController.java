package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustLogDto;
import com.znty.rrs.entity.securitypooladjust.IpAdjustStepDto;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.service.ForbiddenPoolAdjustService;
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
import java.util.List;

/**
 * 禁投池主体调整控制器
 */
@RestController
@RequestMapping("/api/v1/forbiddenPoolAdjust")
public class ForbiddenPoolAdjustController {

    /** 禁投池主体调整服务 */
    @Resource
    private ForbiddenPoolAdjustService forbiddenPoolAdjustService;

    /**
     * 分页查询公司主体
     *
     * @param req 发行主体筛选和分页条件
     * @return 可调整的发行主体和分页信息
     */
    @PostMapping("/queryCompanyPage")
    public ApiResponse<PageResult<ForbiddenPoolAdjustDto>> queryCompanyPage(
            @RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyPage(req));
    }

    /**
     * 查询公司主体详情
     *
     * @param req 待查询的主体代码
     * @return 公司主体详情
     */
    @PostMapping("/queryCompanyDetail")
    public ApiResponse<ForbiddenPoolAdjustDto> queryCompanyDetail(@RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyDetail(req));
    }

    /**
     * 查询主体和旗下债券当前所在池
     *
     * @param req 主体代码
     * @return 主体及旗下债券的在池状态
     */
    @PostMapping("/queryCompanyPoolStatus")
    public ApiResponse<ForbiddenPoolAdjustDto.PoolStatusBundle> queryCompanyPoolStatus(
            @RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyPoolStatus(req));
    }

    /**
     * 查询主体旗下债券明细
     *
     * @param req 主体代码和目标投资池 ID
     * @return 主体债券列表
     */
    @PostMapping("/queryCompanyBondList")
    public ApiResponse<List<ForbiddenPoolAdjustDto.CompanyBond>> queryCompanyBondList(
            @RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyBondList(req));
    }

    /**
     * 查询可操作风险池
     *
     * @param req 当前用户 ID
     * @return 当前用户可调整的风险池列表
     */
    @PostMapping("/queryAdjustPoolList")
    public ApiResponse<List<PoolDto>> queryAdjustPoolList(@RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyAdjustPoolList(req));
    }

    /**
     * 校验主体调库
     *
     * @param req 待校验的主体及调整明细
     * @return 调库校验结果及流程候选
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<AdjustCheckDto> checkAdjust(@RequestBody ForbiddenPoolAdjustCheckReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.checkCompanyAdjust(req));
    }

    /**
     * 以 JSON 方式提交主体调库
     *
     * @param req 主体调库明细及所选流程
     * @return 提交结果，包含生成的调库记录 ID
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ForbiddenPoolAdjustSubmitDto> addAdjustLog(
            @RequestBody ForbiddenPoolAdjustSubmitReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.addCompanyAdjustLog(req, null));
    }

    /**
     * 以 multipart 方式提交主体调库和附件
     *
     * @param req 主体调库明细及所选流程
     * @param files 随申请上传的材料附件，可不传
     * @param originalFileNameListJson 附件原始文件名的 JSON 数组，可不传
     * @return 提交结果，包含生成的调库记录 ID
     */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ForbiddenPoolAdjustSubmitDto> addAdjustLogWithFiles(
            @RequestPart("request") ForbiddenPoolAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(forbiddenPoolAdjustService.addCompanyAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }

    /**
     * 查询主体调库记录
     *
     * @param req 主体调库记录的查询条件
     * @return 调库日志列表
     */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryAdjustLogList(@RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyAdjustLogList(req));
    }

    /**
     * 查询主体调库流程步骤
     *
     * @param req 调库记录 ID 或批次号
     * @return 调库审批步骤列表，未传查询条件或查不到步骤时返回空列表
     */
    @PostMapping("/queryAdjustStepList")
    public ApiResponse<List<IpAdjustStepDto>> queryAdjustStepList(@RequestBody ForbiddenPoolAdjustReq req) {
        return ApiResponse.success(forbiddenPoolAdjustService.queryCompanyAdjustStepList(req));
    }
}
