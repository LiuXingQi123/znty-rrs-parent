package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.AdjustLogDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDto;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.entity.crmwpooladjust.IpAdjustStepDto;
import com.znty.rrs.entity.crmwpooladjust.SecurityPoolStatusDto;
import com.znty.rrs.entity.crmwpooladjust.PoolDto;
import com.znty.rrs.service.CrmwPoolAdjustService;
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
 * CRMW 池调整控制器
 *
 * 提供证券检索、详情、可操作池查询、池状态查询、调整校验、申请提交和历史查询接口
 * 申请按投资池配置的流程处理，直通流程即时生效
 */
@RestController
@RequestMapping("/api/v1/crmwPoolAdjust")
public class CrmwPoolAdjustController {

    /** CRMW池调整服务 */
    @Resource
    private CrmwPoolAdjustService crmwPoolAdjustService;

    /**
     * 分页查询可选 CRMW 凭证，支持按代码、简称和发行人筛选
     *
     * @param req 凭证代码、简称、发行人及分页条件
     * @return 凭证分页结果
     */
    @PostMapping("/queryCrmwPage")
    public ApiResponse<PageResult<SecurityInfoDto>> queryCrmwPage(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwPage(req));
    }

    /**
     * 分页查询可绑定证券列表，排除 CRMW 凭证
     *
     * @param req 标的证券的筛选和分页条件
     * @return 标的证券分页结果
     */
    @PostMapping("/queryBindableSecurityPage")
    public ApiResponse<PageResult<SecurityInfoDto>> queryBindableSecurityPage(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryBindableSecurityPage(req));
    }

    /**
     * 按凭证代码查询 CRMW 凭证基本信息，供调整页面展示凭证名称、类型和发行主体等详情
     *
     * @param req 待查询的凭证代码
     * @return CRMW 凭证详情
     */
    @PostMapping("/queryCrmwDetail")
    public ApiResponse<SecurityInfoDetailDto> queryCrmwDetail(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwDetail(req));
    }

    /**
     * 按证券代码查询标的证券基本信息
     *
     * @param req 标的证券代码和可选的调库日志 ID
     * @return 标的证券详情
     */
    @PostMapping("/querySecurityDetail")
    public ApiResponse<SecurityInfoDetailDto> querySecurityDetail(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.querySecurityDetail(req));
    }

    /**
     * 查询当前用户有权限操作的投资池列表（可入池和可出池），树层级关系由前端组装
     *
     * @param req 含当前用户 ID 的投资池查询条件
     * @return 包含互斥关系和在池数量的 CRMW 池列表
     */
    @PostMapping("/queryCrmwAdjustPoolList")
    public ApiResponse<List<PoolDto>> queryCrmwAdjustPoolList(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwAdjustPoolList(req));
    }

    /**
     * 查询指定凭证与标的组合的在池状态及发行主体的在池状态
     *
     * @param req 凭证与标的组合的查询条件
     * @return 组合和发行人的在池状态
     */
    @PostMapping("/queryCrmwPoolStatus")
    public ApiResponse<SecurityPoolStatusDto> queryCrmwPoolStatus(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwPoolStatus(req));
    }

    /**
     * 提交凭证与标的组合的调库申请，按投资池配置的流程处理
     *
     * @param req 凭证与标的组合的调库提交请求
     * @return 提交条数及生成的调库日志 ID
     */
    @PostMapping(value = "/addCrmwAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdjustSubmitDto> addCrmwAdjustLog(@RequestBody CrmwPoolAdjustSubmitReq req) {
        return ApiResponse.success(crmwPoolAdjustService.addCrmwAdjustLog(req));
    }

    /**
     * 以 multipart 方式提交调库申请及附件
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param files 随申请上传的材料附件，可不传
     * @param originalFileNameListJson 附件原始文件名的 JSON 数组，可不传
     * @return 提交条数及生成的调库日志 ID
     */
    @PostMapping(value = "/addCrmwAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdjustSubmitDto> addCrmwAdjustLogWithFiles(
            @RequestPart("request") CrmwPoolAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "originalFileNameListJson", required = false) String originalFileNameListJson) {
        return ApiResponse.success(crmwPoolAdjustService.addCrmwAdjustLog(
                req, files == null ? null : Arrays.asList(files), originalFileNameListJson));
    }

    /**
     * 查询凭证与标的组合的调整记录，包含操作人、操作时间和审批状态
     *
     * @param req 组合代码和可选的调库批次号
     * @return CRMW 调库日志列表
     */
    @PostMapping("/queryCrmwAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryCrmwAdjustLogList(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwAdjustLogList(req));
    }

    /**
     * 提交前校验凭证与标的组合的调整明细是否满足风控规则
     *
     * @param req 凭证、标的证券及目标池调整明细
     * @return 单笔校验结果及流程候选
     */
    @PostMapping("/checkCrmwAdjust")
    public ApiResponse<AdjustCheckDto> checkCrmwAdjust(@RequestBody AdjustCheckReq req) {
        return ApiResponse.success(crmwPoolAdjustService.checkCrmwAdjust(req));
    }

    /**
     * 查询指定调库记录的审批流程步骤列表
     *
     * @param req 调库记录 ID 或批次号
     * @return 调库审批步骤列表，未传查询条件或查不到步骤时返回空列表
     */
    @PostMapping("/queryCrmwAdjustStepList")
    public ApiResponse<List<IpAdjustStepDto>> queryCrmwAdjustStepList(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwAdjustStepList(req));
    }
}
