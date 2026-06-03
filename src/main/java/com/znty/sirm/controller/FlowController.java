package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.IdRequest;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.*;
import com.znty.sirm.service.FlowService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 流程定义模块，统一使用 POST + @RequestBody。
 */
@RestController
@RequestMapping("/api/v1/flows")
public class FlowController {

    @Resource
    private FlowService flowService;

    // ==================== 流程定义管理 ====================

    /** 查询流程分页。 */
    @PostMapping("/queryFlowPage")
    public ApiResponse<PageResult<FlowDto>> queryFlowPage(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowPage(req));
    }

    /** 查询流程列表（不分页，用于下拉选项）。 */
    @PostMapping("/queryFlowList")
    public ApiResponse<List<FlowOptionDto>> queryFlowList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowList(req));
    }

    /** 新建流程。 */
    @PostMapping("/addFlow")
    public ApiResponse<FlowDto> addFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.addFlow(req));
    }

    /** 查询流程详情。 */
    @PostMapping("/queryFlowDetail")
    public ApiResponse<FlowDto> queryFlowDetail(@RequestBody IdRequest req) {
        return ApiResponse.success(flowService.queryFlowDetail(req));
    }

    /** 更新流程基础信息。 */
    @PostMapping("/editFlow")
    public ApiResponse<FlowDto> editFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.editFlow(req));
    }

    /** 删除流程。 */
    @PostMapping("/deleteFlow")
    public ApiResponse<FlowDto> deleteFlow(@RequestBody IdRequest req) {
        return ApiResponse.success(flowService.deleteFlow(req));
    }

    /** 停用流程。 */
    @PostMapping("/editFlowStatus")
    public ApiResponse<FlowDto> editFlowStatus(@RequestBody IdRequest req) {
        return ApiResponse.success(flowService.editFlowStatus(req));
    }

    // ==================== 流程设计器 ====================

    /** 保存流程草稿。 */
    @PostMapping("/editFlowDraft")
    public ApiResponse<FlowDto> editFlowDraft(@RequestBody DesignerReq req) {
        return ApiResponse.success(flowService.editFlowDraft(req));
    }

    /** 发布流程。 */
    @PostMapping("/editFlowToPublished")
    public ApiResponse<FlowDto> editFlowToPublished(@RequestBody DesignerReq req) {
        return ApiResponse.success(flowService.editFlowToPublished(req));
    }

    // ==================== 版本管理 ====================

    /** 查询流程版本列表，可按版本号筛选。 */
    @PostMapping("/queryFlowVersionList")
    public ApiResponse<List<VersionDto>> queryFlowVersionList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowVersionList(req));
    }

    /** 查询流程版本详情。 */
    @PostMapping("/queryFlowVersionDetail")
    public ApiResponse<VersionDto> queryFlowVersionDetail(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowVersionDetail(req));
    }

    // ==================== 字典 ====================

    /** 查询角色字典。 */
    @PostMapping("/dict/queryRoleList")
    public ApiResponse<List<DictDto>> queryRoleList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryRoleList(req));
    }

    /** 查询自动任务选项。 */
    @PostMapping("/dict/queryAutoTaskList")
    public ApiResponse<List<DictDto>> queryAutoTaskList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryAutoTaskList(req));
    }

    /** 查询条件字段选项。 */
    @PostMapping("/dict/queryCondFieldList")
    public ApiResponse<List<DictDto>> queryCondFieldList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryCondFieldList(req));
    }
}
