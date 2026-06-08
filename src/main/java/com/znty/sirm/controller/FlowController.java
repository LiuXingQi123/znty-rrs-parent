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
 * 审批流程控制器
 * <p>
 * 负责风控审批流程的完整生命周期管理，包括：
 * 流程定义的增删改查、流程设计器草稿保存与正式发布、
 * 版本历史管理以及角色/任务/条件字段等字典数据查询。
 * 流程由节点（审批人/自动任务）和边（条件路由）组成，支持多版本并存。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/flows")
public class FlowController {

    @Resource
    private FlowService flowService;

    // ==================== 流程定义管理 ====================

    /**
     * 分页查询流程列表，支持按流程名称、状态等条件筛选
     */
    @PostMapping("/queryFlowPage")
    public ApiResponse<PageResult<FlowDto>> queryFlowPage(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowPage(req));
    }

    /**
     * 查询流程下拉选项列表（不分页，供其他模块选择关联流程使用）
     */
    @PostMapping("/queryFlowList")
    public ApiResponse<List<FlowOptionDto>> queryFlowList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowList(req));
    }

    /**
     * 新建流程定义，创建后处于草稿状态，需经过设计器配置后方可发布
     */
    @PostMapping("/addFlow")
    public ApiResponse<FlowDto> addFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.addFlow(req));
    }

    /**
     * 按流程 ID 查询流程详情，含最新版本的节点和边配置
     */
    @PostMapping("/queryFlowDetail")
    public ApiResponse<FlowDto> queryFlowDetail(@RequestBody IdRequest req) {
        return ApiResponse.success(flowService.queryFlowDetail(req));
    }

    /**
     * 更新流程基础信息（名称、描述等），不影响已发布版本
     */
    @PostMapping("/editFlow")
    public ApiResponse<FlowDto> editFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.editFlow(req));
    }

    /**
     * 删除流程定义，已有关联业务数据的流程不允许删除
     */
    @PostMapping("/deleteFlow")
    public ApiResponse<FlowDto> deleteFlow(@RequestBody IdRequest req) {
        return ApiResponse.success(flowService.deleteFlow(req));
    }

    /**
     * 切换流程启用/停用状态，停用后不可发起新的审批申请
     */
    @PostMapping("/editFlowStatus")
    public ApiResponse<FlowDto> editFlowStatus(@RequestBody IdRequest req) {
        return ApiResponse.success(flowService.editFlowStatus(req));
    }

    // ==================== 流程设计器 ====================

    /**
     * 保存流程设计器草稿（节点和边的画布配置），不触发版本变更
     */
    @PostMapping("/editFlowDraft")
    public ApiResponse<FlowDto> editFlowDraft(@RequestBody DesignerReq req) {
        return ApiResponse.success(flowService.editFlowDraft(req));
    }

    /**
     * 发布流程：将当前草稿快照固化为一个新的正式版本，版本号自动递增
     */
    @PostMapping("/editFlowToPublished")
    public ApiResponse<FlowDto> editFlowToPublished(@RequestBody DesignerReq req) {
        return ApiResponse.success(flowService.editFlowToPublished(req));
    }

    // ==================== 版本管理 ====================

    /**
     * 查询流程的历史版本列表，可按版本号筛选，用于版本对比和回溯
     */
    @PostMapping("/queryFlowVersionList")
    public ApiResponse<List<VersionDto>> queryFlowVersionList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowVersionList(req));
    }

    /**
     * 查询指定版本的详情，含该版本的完整节点和边快照数据
     */
    @PostMapping("/queryFlowVersionDetail")
    public ApiResponse<VersionDto> queryFlowVersionDetail(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowVersionDetail(req));
    }

    // ==================== 字典 ====================

    /**
     * 查询可用的审批角色字典，用于流程节点中配置审批人角色
     */
    @PostMapping("/dict/queryRoleList")
    public ApiResponse<List<RoleDto>> queryRoleList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryRoleList(req));
    }

    /**
     * 查询可配置为审批处理人的人员列表，支持按角色及其子角色过滤
     */
    @PostMapping("/dict/queryUserList")
    public ApiResponse<List<UserDto>> queryUserList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryUserList(req));
    }

    /**
     * 查询自动任务选项字典，用于流程中配置自动执行节点的任务类型
     */
    @PostMapping("/dict/queryAutoTaskList")
    public ApiResponse<List<DictDto>> queryAutoTaskList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryAutoTaskList(req));
    }

    /**
     * 查询条件字段选项字典，用于流程条件边中配置路由判断字段
     */
    @PostMapping("/dict/queryCondFieldList")
    public ApiResponse<List<DictDto>> queryCondFieldList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryCondFieldList(req));
    }
}
