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
@RequestMapping("/api/v1")
public class FlowController {

    @Resource
    private FlowService flowService;

    // ==================== 流程定义管理 ====================

    /** 查询流程分页。 */
    @PostMapping("/flows/queryFlowPage")
    public ApiResponse<PageResult<FlowDto>> queryFlowPage(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowPage(req));
    }

    /** 查询流程列表（不分页，用于下拉选项）。 */
    @PostMapping("/flows/queryFlowList")
    public ApiResponse<List<FlowOptionDto>> queryFlowList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowList(req));
    }

    /** 新建流程。 */
    @PostMapping("/flows/createFlow")
    public ApiResponse<FlowDto> createFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.createFlow(req));
    }

    /** 查询流程详情。 */
    @PostMapping("/flows/queryFlowDetail")
    public ApiResponse<FlowDto> queryFlowDetail(@RequestBody IdRequest req) {
        return ApiResponse.success(flowService.queryFlowDetail(req));
    }

    /** 更新流程基础信息。 */
    @PostMapping("/flows/updateFlow")
    public ApiResponse<Void> updateFlow(@RequestBody FlowReq req) {
        flowService.updateFlow(req);
        return ApiResponse.success();
    }

    /** 删除流程。 */
    @PostMapping("/flows/deleteFlow")
    public ApiResponse<Void> deleteFlow(@RequestBody IdRequest req) {
        flowService.deleteFlow(req);
        return ApiResponse.success();
    }

    /** 停用流程。 */
    @PostMapping("/flows/disableFlow")
    public ApiResponse<Void> disableFlow(@RequestBody IdRequest req) {
        flowService.disableFlow(req);
        return ApiResponse.success();
    }

    // ==================== 流程设计器 ====================

    /** 保存流程草稿。 */
    @PostMapping("/flows/saveFlowDraft")
    public ApiResponse<FlowDto> saveFlowDraft(@RequestBody DesignerReq req) {
        return ApiResponse.success(flowService.saveFlowDraft(req));
    }

    /** 发布流程。 */
    @PostMapping("/flows/publishFlow")
    public ApiResponse<FlowDto> publishFlow(@RequestBody DesignerReq req) {
        return ApiResponse.success(flowService.publishFlow(req));
    }

    // ==================== 版本管理 ====================

    /** 查询流程版本列表，可按版本号筛选。 */
    @PostMapping("/flows/queryFlowVersionList")
    public ApiResponse<List<VersionDto>> queryFlowVersionList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowVersionList(req));
    }

    /** 查询流程版本详情。 */
    @PostMapping("/flows/queryFlowVersionDetail")
    public ApiResponse<VersionDto> queryFlowVersionDetail(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowVersionDetail(req));
    }

    // ==================== 字典 ====================

    /** 查询角色字典。 */
    @PostMapping("/dict/roles")
    public ApiResponse<List<DictDto>> roles() {
        return ApiResponse.success(flowService.roles());
    }

    /** 查询自动任务选项。 */
    @PostMapping("/dict/auto-tasks")
    public ApiResponse<List<DictDto>> autoTasks() {
        return ApiResponse.success(flowService.autoTasks());
    }

    /** 查询条件字段选项。 */
    @PostMapping("/dict/cond-fields")
    public ApiResponse<List<DictDto>> condFields() {
        return ApiResponse.success(flowService.condFields());
    }
}
