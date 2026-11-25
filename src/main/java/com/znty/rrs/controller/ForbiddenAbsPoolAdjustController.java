package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustLogDto;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustSubmitDto;
import com.znty.rrs.entity.securitypooladjust.LastCreditReportDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDto;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustReq;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.IpAdjustStepDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolStatusDto;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.service.ForbiddenAbsPoolAdjustService;
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
import java.util.List;

/**
 * ABS 禁投池调整控制器
 *
 * 提供证券检索、详情、可操作池查询、池状态查询、调整校验、申请提交和历史查询接口
 * 申请按投资池配置的流程处理，直通流程即时生效
 */
@RestController
@RequestMapping("/api/v1/forbiddenAbsPoolAdjust")
public class ForbiddenAbsPoolAdjustController {

    /** ABS禁投池调库服务 */
    @Resource
    private ForbiddenAbsPoolAdjustService forbiddenAbsPoolAdjustService;

    /**
     * 分页查询证券列表，支持按证券代码、名称、类型等条件筛选，用于选择调整目标证券
     *
     * @param req 证券代码、简称、发行人、债券类型及分页条件
     * @return 证券列表和分页信息
     */
    @PostMapping("/querySecurityPage")
    public ApiResponse<PageResult<SecurityInfoDto>> querySecurityPage(@RequestBody ForbiddenAbsPoolAdjustReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.querySecurityPage(req));
    }

    /**
     * 按证券代码查询证券基本信息，用于调整页面顶部展示证券名称、类型、发行主体等详情
     *
     * @param req 待查询的证券代码
     * @return 标的证券详情
     */
    @PostMapping("/querySecurityDetail")
    public ApiResponse<SecurityInfoDetailDto> querySecurityDetail(@RequestBody ForbiddenAbsPoolAdjustReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.querySecurityDetail(req));
    }

    /**
     * 查询当前用户可手工调整的 ABS 禁投相关投资池
     *
     * @param req 当前用户 ID
     * @return 可调整的投资池及池间互斥关系
     */
    @PostMapping("/queryAdjustPoolList")
    public ApiResponse<List<PoolDto>> queryAdjustPoolList(@RequestBody ForbiddenAbsPoolAdjustReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.queryAdjustPoolList(req));
    }

    /**
     * 查询近 6 个月可回填的信评报告，优先使用当前证券的报告，否则按发行主体查找
     *
     * @param req 证券代码
     * @return 可回填的最近信评报告，没有符合条件的报告时数据为空
     */
    @PostMapping("/queryLastCreditReport")
    public ApiResponse<LastCreditReportDto> queryLastCreditReport(@RequestBody ForbiddenAbsPoolAdjustReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.queryLastCreditReport(req));
    }

    /**
     * 查询指定证券当前所在的投资池状态，以及其发行主体在主体池中的状态
     *
     * @param req 证券代码
     * @return 证券和发行主体的在池状态
     */
    @PostMapping("/querySecurityPoolStatus")
    public ApiResponse<SecurityPoolStatusDto> querySecurityPoolStatus(@RequestBody ForbiddenAbsPoolAdjustReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.querySecurityPoolStatus(req));
    }

    /**
     * 提交证券入池或出池调整申请，按投资池配置的流程处理
     *
     * @param req 调库申请，包含证券信息及一个或多个调库项（手工项须携带 flowId 或 flowKey，无流程时走直通）
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ForbiddenAbsPoolAdjustSubmitDto> addAdjustLog(@RequestBody ForbiddenAbsPoolAdjustSubmitReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.addAdjustLog(req));
    }

    /**
     * 以 multipart 方式提交调库申请及附件
     *
     * @param req 调库提交请求
     * @param files 随申请上传的材料附件，可不传
     * @param originalFileNameListJson 附件原始文件名的 JSON 数组，可不传
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @PostMapping(value = "/addAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ForbiddenAbsPoolAdjustSubmitDto> addAdjustLogWithFiles(
            @RequestPart("request") ForbiddenAbsPoolAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }

    /**
     * 查询指定证券的历史调整记录列表，包含每次申请的操作人、操作时间和审批状态
     *
     * @param req 调库批次号，按证券查询时传证券代码
     * @return 调库日志列表
     */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryAdjustLogList(@RequestBody ForbiddenAbsPoolAdjustReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.queryAdjustLogList(req));
    }

    /**
     * 预校验证券调整可行性，在提交申请前检查是否满足风控规则约束条件
     *
     * @param req 包含证券代码和一个或多个用户选择的调库项
     * @return 包含手动项和自动项的完整校验结果列表
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<AdjustCheckDto> checkAdjust(@RequestBody ForbiddenAbsPoolAdjustCheckReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.checkAdjust(req));
    }

    /**
     * 查询指定调库记录的审批流程步骤列表
     *
     * @param req 调库记录 ID 或批次号
     * @return 调库审批步骤列表，未传查询条件或查不到步骤时返回空列表
     */
    @PostMapping("/queryAdjustStepList")
    public ApiResponse<List<IpAdjustStepDto>> queryAdjustStepList(@RequestBody ForbiddenAbsPoolAdjustReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.queryAdjustStepList(req));
    }

    /**
     * 查询主体债入库矩阵允许的改判池列表（评级联动，调入审批改判用）
     *
     * 按主体内评分档和期限档匹配矩阵，供审批页面选择改判目标池
     *
     * @param req 待查询改判池的证券代码
     * @return 允许调入的池列表（含池名），空列表表示无匹配矩阵
     */
    @PostMapping("/queryRedirectPoolOptions")
    public ApiResponse<List<InvestmentPoolBo>> queryRedirectPoolOptions(@RequestBody ForbiddenAbsPoolAdjustReq req) {
        return ApiResponse.success(forbiddenAbsPoolAdjustService.queryRedirectPoolOptions(req.getSecurityCode()));
    }
}
