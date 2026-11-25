package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.common.RoleDto;
import com.znty.rrs.entity.common.UserDto;
import com.znty.rrs.entity.flow.DesignerReq;
import com.znty.rrs.entity.flow.DictDto;
import com.znty.rrs.entity.flow.FlowDto;
import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.flow.FlowReq;
import com.znty.rrs.entity.flow.VersionDto;
import com.znty.rrs.service.FlowService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 审批流程控制器
 *
 * 管理流程定义、画布草稿、发布和版本，提供角色、任务和条件字段的字典查询
 * 流程由审批节点、自动任务等节点与条件连线组成，支持多版本并存
 */
@RestController
@RequestMapping("/api/v1/flows")
public class FlowController {

    /** 流程定义服务 */
    @Resource
    private FlowService flowService;

    // ==================== 流程定义管理 ====================

    /**
     * 分页查询流程列表，支持按流程名称、状态等条件筛选
     *
     * @param req 流程关键字、状态、分类和分页条件
     * @return 流程定义列表及分页信息
     */
    @PostMapping("/queryFlowPage")
    public ApiResponse<PageResult<FlowDto>> queryFlowPage(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowPage(req));
    }

    /**
     * 查询流程下拉选项列表（不分页，供其他模块选择关联流程使用）
     *
     * @param req 流程关键字、状态和分类筛选
     * @return 流程选项列表
     */
    @PostMapping("/queryFlowList")
    public ApiResponse<List<FlowOptionDto>> queryFlowList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowList(req));
    }

    /**
     * 新建流程定义，创建后处于草稿状态，需经过设计器配置后方可发布
     *
     * @param req 流程名称、标识、分类、描述、备注及操作人
     * @return 新建的流程定义
     */
    @PostMapping("/addFlow")
    public ApiResponse<FlowDto> addFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.addFlow(req));
    }

    /**
     * 按流程 ID 查询流程详情，含最新版本的节点和边配置
     *
     * @param req 待查询的流程 ID
     * @return 流程详情及最新版本的节点和边配置
     */
    @PostMapping("/queryFlowDetail")
    public ApiResponse<FlowDto> queryFlowDetail(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowDetail(req));
    }

    /**
     * 更新流程基础信息（名称、描述等），不影响已发布版本
     *
     * @param req 流程 ID、待修改的基础信息和操作人
     * @return 更新后的流程详情
     */
    @PostMapping("/editFlow")
    public ApiResponse<FlowDto> editFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.editFlow(req));
    }

    /**
     * 逻辑删除流程定义并记录操作历史
     *
     * @param req 待删除的流程 ID 和操作人
     * @return 删除前的流程详情
     */
    @PostMapping("/deleteFlow")
    public ApiResponse<FlowDto> deleteFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.deleteFlow(req));
    }

    /**
     * 停用已发布的流程，并记录操作历史
     *
     * @param req 待停用的流程 ID 和操作人
     * @return 停用后的流程详情
     */
    @PostMapping("/editFlowStatus")
    public ApiResponse<FlowDto> editFlowStatus(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.editFlowStatus(req));
    }

    // ==================== 流程设计器 ====================

    /**
     * 保存流程设计器草稿（节点和边的画布配置），不触发版本变更
     *
     * @param req 流程 ID、节点、边、画布视图和操作人
     * @return 保存后的流程草稿
     */
    @PostMapping("/editFlowDraft")
    public ApiResponse<FlowDto> editFlowDraft(@RequestBody DesignerReq req) {
        return ApiResponse.success(flowService.editFlowDraft(req));
    }

    /**
     * 发布流程：将当前草稿快照固化为一个新的正式版本，版本号自动递增
     *
     * @param req 流程 ID、节点、边、画布视图、发布说明和操作人
     * @return 发布后的流程详情
     */
    @PostMapping("/editFlowToPublished")
    public ApiResponse<FlowDto> editFlowToPublished(@RequestBody DesignerReq req) {
        return ApiResponse.success(flowService.editFlowToPublished(req));
    }

    // ==================== 版本管理 ====================

    /**
     * 查询流程的历史版本列表，可按版本号筛选，用于版本对比和回溯
     *
     * @param req 流程 ID 和可选的版本号
     * @return 版本列表
     */
    @PostMapping("/queryFlowVersionList")
    public ApiResponse<List<VersionDto>> queryFlowVersionList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowVersionList(req));
    }

    /**
     * 查询指定版本的详情，含该版本的完整节点和边快照数据
     *
     * @param req 版本 ID 和用于校验归属的流程 ID
     * @return 指定版本的详情及节点和边快照
     */
    @PostMapping("/queryFlowVersionDetail")
    public ApiResponse<VersionDto> queryFlowVersionDetail(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowVersionDetail(req));
    }

    // ==================== 字典 ====================

    /**
     * 查询可用的审批角色字典，用于流程节点中配置审批人角色
     *
     * @param req 空请求体
     * @return 角色列表
     */
    @PostMapping("/dict/queryRoleList")
    public ApiResponse<List<RoleDto>> queryRoleList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryRoleList(req));
    }

    /**
     * 查询可配置为审批处理人的人员列表，支持按角色及其子角色过滤
     *
     * @param req 角色 ID 和用户关键字
     * @return 用户列表
     */
    @PostMapping("/dict/queryUserList")
    public ApiResponse<List<UserDto>> queryUserList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryUserList(req));
    }

    /**
     * 查询自动任务选项字典，用于流程中配置自动执行节点的任务类型
     *
     * @param req 空请求体
     * @return 自动任务选项列表
     */
    @PostMapping("/dict/queryAutoTaskList")
    public ApiResponse<List<DictDto>> queryAutoTaskList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryAutoTaskList(req));
    }

    /**
     * 查询条件字段选项字典，用于流程条件边中配置路由判断字段
     *
     * @param req 空请求体
     * @return 条件字段选项列表
     */
    @PostMapping("/dict/queryCondFieldList")
    public ApiResponse<List<DictDto>> queryCondFieldList(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryCondFieldList(req));
    }
}
