package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.sirm.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.sirm.entity.crmwpooladjust.AdjustLogDto;
import com.znty.sirm.entity.crmwpooladjust.AdjustSubmitDto;
import com.znty.sirm.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.sirm.entity.crmwpooladjust.SecurityInfoDto;
import com.znty.sirm.entity.crmwpooladjust.CrmwPoolAdjustReq;
import com.znty.sirm.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.sirm.entity.crmwpooladjust.IpAdjustStepDto;
import com.znty.sirm.entity.crmwpooladjust.SecurityPoolStatusDto;
import com.znty.sirm.entity.crmwpooladjust.PoolDto;
import com.znty.sirm.service.CrmwPoolAdjustService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * CRMW池调整控制器
 * <p>
 * 负责证券入池/出池调整申请的完整业务流程，包括：
 * 证券检索与详情查看、可操作投资池范围查询、当前池状态展示、
 * 调整可行性校验、提交调整申请以及申请记录追溯。
 * 调整申请提交后须经过投资池配置的审批流程完成审核。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/crmwPoolAdjust")
public class CrmwPoolAdjustController {

    /** CRMW池调整服务 */
    @Resource
    private CrmwPoolAdjustService crmwPoolAdjustService;

    /**
     * 分页查询证券列表，支持按证券代码、名称、类型等条件筛选，用于选择调整目标证券
     */
    @PostMapping("/queryCrmwPage")
    public ApiResponse<PageResult<SecurityInfoDto>> queryCrmwPage(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwPage(req));
    }

    /**
     * 分页查询可绑定证券列表，排除 CRMW 凭证
     */
    @PostMapping("/queryBindableSecurityPage")
    public ApiResponse<PageResult<SecurityInfoDto>> queryBindableSecurityPage(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryBindableSecurityPage(req));
    }

    /**
     * 按证券代码查询证券基本信息，用于调整页面顶部展示证券名称、类型、发行主体等详情
     */
    @PostMapping("/queryCrmwDetail")
    public ApiResponse<SecurityInfoDetailDto> queryCrmwDetail(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwDetail(req));
    }

    /**
     * 按证券代码查询标的证券基本信息
     */
    @PostMapping("/querySecurityDetail")
    public ApiResponse<SecurityInfoDetailDto> querySecurityDetail(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.querySecurityDetail(req));
    }

    /**
     * 查询当前用户有权限操作的投资池列表（可入池和可出池），树层级关系由前端组装
     */
    @PostMapping("/queryCrmwAdjustPoolList")
    public ApiResponse<List<PoolDto>> queryCrmwAdjustPoolList(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwAdjustPoolList(req));
    }

    /**
     * 查询指定证券当前所在的投资池状态，以及其发行主体在主体池中的状态
     */
    @PostMapping("/queryCrmwPoolStatus")
    public ApiResponse<SecurityPoolStatusDto> queryCrmwPoolStatus(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwPoolStatus(req));
    }

    /**
     * 提交证券入池/出池调整申请，申请记录入库后触发对应投资池的审批流程
     */
    @PostMapping(value = "/addCrmwAdjustLog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdjustSubmitDto> addCrmwAdjustLog(@RequestBody CrmwPoolAdjustSubmitReq req) {
        return ApiResponse.success(crmwPoolAdjustService.addCrmwAdjustLog(req));
    }

    /**
     * 以 multipart 方式提交调库申请及附件
     */
    @PostMapping(value = "/addCrmwAdjustLogWithFiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdjustSubmitDto> addCrmwAdjustLogWithFiles(
            @RequestPart("request") CrmwPoolAdjustSubmitReq req,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        return ApiResponse.success(crmwPoolAdjustService.addCrmwAdjustLog(
                req, files == null ? null : Arrays.asList(files)));
    }

    /**
     * 查询指定证券的历史调整记录列表，包含每次申请的操作人、操作时间和审批状态
     */
    @PostMapping("/queryCrmwAdjustLogList")
    public ApiResponse<List<AdjustLogDto>> queryCrmwAdjustLogList(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwAdjustLogList(req));
    }

    /**
     * 预校验证券调整可行性，在提交申请前检查是否满足风控规则约束条件
     */
    @PostMapping("/checkCrmwAdjust")
    public ApiResponse<AdjustCheckDto> checkCrmwAdjust(@RequestBody AdjustCheckReq req) {
        return ApiResponse.success(crmwPoolAdjustService.checkCrmwAdjust(req));
    }

    /**
     * 查询指定调库记录的审批流程步骤列表
     */
    @PostMapping("/queryCrmwAdjustStepList")
    public ApiResponse<List<IpAdjustStepDto>> queryCrmwAdjustStepList(@RequestBody CrmwPoolAdjustReq req) {
        return ApiResponse.success(crmwPoolAdjustService.queryCrmwAdjustStepList(req));
    }
}
