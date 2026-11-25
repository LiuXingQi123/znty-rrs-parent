package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.securitypooladjust.RedirectPoolOptionDto;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustLogDto;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.securitypooladjust.LastCreditReportDto;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.IpAdjustStepDto;
import com.znty.rrs.entity.securitypooladjust.IssuerFinancialDto;
import com.znty.rrs.entity.securitypooladjust.IssuerFinancialSaveReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolStatusDto;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderReq;
import com.znty.rrs.service.SecurityPoolAdjustService;
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
 * 证券池调整控制器
 *
 * 提供证券检索、详情、可操作池查询、池状态查询、调整校验、申请提交和历史查询接口
 * 申请按投资池配置的流程处理，直通流程即时生效
 */
@RestController
@RequestMapping("/api/v1/securityPoolAdjust")
public class SecurityPoolAdjustController {

    /** 证券池调库服务 */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /**
     * 分页查询证券列表，支持按证券代码、名称、类型等条件筛选，用于选择调整目标证券
     *
     * @param req 查询条件，证券代码、简称、全称、主体编码和主体全称为模糊匹配，证券类型为精确匹配
     * @return 证券列表和分页信息
     */
    @PostMapping("/querySecurityPage")
    public ApiResponse<PageResult<SecurityInfoDto>> querySecurityPage(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySecurityPage(req));
    }

    /**
     * 查询证券类型下拉选项，供列表筛选
     *
     * @param req 空请求体
     * @return 证券类型选项列表，包含编码和名称
     */
    @PostMapping("/querySecurityTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> querySecurityTypeList(
            @RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySecurityTypeList());
    }

    /**
     * 按证券代码查询证券基本信息，用于调整页面顶部展示证券名称、类型、发行主体等详情
     *
     * @param req 待查询的证券代码
     * @return 标的证券详情
     */
    @PostMapping("/querySecurityDetail")
    public ApiResponse<SecurityInfoDetailDto> querySecurityDetail(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySecurityDetail(req));
    }

    /**
     * 查询当前证券主档，仅供页面差异提示，不合并调库快照
     *
     * @param req 证券代码
     * @return 证券详细信息
     */
    @PostMapping("/querySecurityMasterDetail")
    public ApiResponse<SecurityInfoDetailDto> querySecurityMasterDetail(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySecurityMasterDetail(req));
    }

    /**
     * 查询发行主体默认四期财务指标，缺失报告期保留空值
     *
     * @param req 携带证券代码的查询请求
     * @return 按默认四列顺序返回的四条财报记录，缺失期保留日期和空指标
     */
    @PostMapping("/queryIssuerFinancialList")
    public ApiResponse<List<IssuerFinancialDto>> queryIssuerFinancialList(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryIssuerFinancialList(req));
    }

    /**
     * 查询发行主体指定报告日期的财务指标
     *
     * @param req 证券代码和报告日期
     * @return 对应报告期的财务指标，没有对应记录时数据为空
     */
    @PostMapping("/queryIssuerFinancialByReportDate")
    public ApiResponse<IssuerFinancialDto> queryIssuerFinancialByReportDate(
            @RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryIssuerFinancialByReportDate(req));
    }

    /**
     * 按报告期批量新增或覆盖发行主体财务指标，独立于调库提交
     *
     * @param req 证券代码、本次选中的评级主体和待保存的财报记录列表
     * @return 发行主体财务列表
     */
    @PostMapping("/saveIssuerFinancialList")
    public ApiResponse<List<IssuerFinancialDto>> saveIssuerFinancialList(
            @RequestBody IssuerFinancialSaveReq req) {
        return ApiResponse.success(securityPoolAdjustService.saveIssuerFinancialList(req));
    }

    /**
     * 查询证券池调库页面担保人、权益人下拉共用的四类关系主体及其最新内评
     *
     * @param req 证券代码
     * @return 关联评级主体及其关系信息列表
     */
    @PostMapping("/queryRelatedRatingCompanyList")
    public ApiResponse<List<RelatedRatingCompanyDto>> queryRelatedRatingCompanyList(
            @RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryRelatedRatingCompanyList(req));
    }

    /**
     * 分页查询 ABS 自选权益人候选
     *
     * @param req 分页与检索条件
     * @return 可选主体分页结果
     */
    @PostMapping("/querySelfSelectedRightsHolderPage")
    public ApiResponse<PageResult<SelfSelectedRightsHolderDto>> querySelfSelectedRightsHolderPage(
            @RequestBody SelfSelectedRightsHolderReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySelfSelectedRightsHolderPage(req));
    }

    /**
     * 查询当前用户有权限操作的投资池列表（可入池和可出池），树层级关系由前端组装
     *
     * 调入信用债大库时，按主体债入库矩阵过滤候选池
     *
     * @param req 当前用户、调整方向及调入时用于匹配主体债入库规则的证券信息
     * @return 当前用户可调整的投资池及用于组装树的节点
     */
    @PostMapping("/queryAdjustPoolList")
    public ApiResponse<List<PoolDto>> queryAdjustPoolList(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryAdjustPoolList(req));
    }

    /**
     * 查询近 6 个月可回填的信评报告，优先使用当前证券的报告，否则按发行主体查找
     *
     * @param req 证券代码
     * @return 可回填的最近信评报告，没有符合条件的报告时数据为空
     */
    @PostMapping("/queryLastCreditReport")
    public ApiResponse<LastCreditReportDto> queryLastCreditReport(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryLastCreditReport(req));
    }

    /**
     * 查询指定证券当前所在的投资池状态，以及其发行主体在主体池中的状态
     *
     * @param req 证券代码
     * @return 证券和发行主体的在池状态
     */
    @PostMapping("/querySecurityPoolStatus")
    public ApiResponse<SecurityPoolStatusDto> querySecurityPoolStatus(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.querySecurityPoolStatus(req));
    }

    /**
     * 提交证券入池或出池调整申请，按投资池配置的流程处理
     *
     * @param req 调库申请，包含证券信息及一个或多个调库项（手工项须携带 flowId 或 flowKey，无流程时走直通）
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @PostMapping(value = "/addAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdjustSubmitDto> addAdjustLog(@RequestBody SecurityPoolAdjustSubmitReq req) {
        return ApiResponse.success(securityPoolAdjustService.addAdjustLog(req));
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
    public ApiResponse<AdjustSubmitDto> addAdjustLogWithFiles(
            @RequestPart("request") SecurityPoolAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(securityPoolAdjustService.addAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }

    /**
     * 查询指定证券的历史调整记录列表，包含每次申请的操作人、操作时间和审批状态
     *
     * @param req 证券代码
     * @return 调库日志列表
     */
    @PostMapping("/queryAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryAdjustLogList(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryAdjustLogList(req));
    }

    /**
     * 预校验证券调整可行性，在提交申请前检查是否满足风控规则约束条件
     *
     * @param req 包含证券代码和一个或多个用户选择的调库项
     * @return 包含手动项和自动项的完整校验结果列表
     */
    @PostMapping("/checkAdjust")
    public ApiResponse<AdjustCheckDto> checkAdjust(@RequestBody AdjustCheckReq req) {
        return ApiResponse.success(securityPoolAdjustService.checkAdjust(req));
    }

    /**
     * 查询指定调库记录的审批流程步骤列表
     *
     * @param req 调库记录 ID 或批次号
     * @return 调库审批步骤列表，未传查询条件或查不到步骤时返回空列表
     */
    @PostMapping("/queryAdjustStepList")
    public ApiResponse<List<IpAdjustStepDto>> queryAdjustStepList(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryAdjustStepList(req));
    }

    /**
     * 按本笔信用债调入申请及最新主体内评查询改判候选池
     *
     * @param req 证券代码和本笔申请的调库记录 ID
     * @return 允许调入的池列表，含原名称及完整路径，空列表表示无匹配矩阵
     */
    @PostMapping("/queryRedirectPoolOptions")
    public ApiResponse<List<RedirectPoolOptionDto>> queryRedirectPoolOptions(@RequestBody SecurityPoolAdjustReq req) {
        return ApiResponse.success(securityPoolAdjustService.queryRedirectPoolOptions(
                req.getSecurityCode(), req.getAdjustLogId()));
    }
}
