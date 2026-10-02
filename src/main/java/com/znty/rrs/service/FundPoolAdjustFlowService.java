package com.znty.rrs.service;

import com.znty.rrs.common.enums.ApprovalStrategy;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.NodeType;
import com.znty.rrs.common.enums.ProcessAction;
import com.znty.rrs.common.enums.StepStatus;
import com.znty.rrs.common.util.AdjustStepHandlerExcludeUtil;
import com.znty.rrs.common.util.AdminUserIdUtil;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustAuditDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustAuditReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 基金池调库审批流程服务，负责审核步骤处理与流程流转。 */
@Service
public class FundPoolAdjustFlowService {
    /** 基金池调整数据访问组件 */
    @Resource
    private FundPoolAdjustMapper fundPoolAdjustMapper;
    /** 通用流程定义数据访问组件 */
    @Resource
    private FlowMapper flowMapper;
    /** 基金调库业务校验与当前池落地服务 */
    @Resource
    private FundPoolAdjustService fundPoolAdjustService;

    /**
     * 提交基金调库审批处理意见并按流程配置推进。
     *
     * @param req 当前步骤、处理人及审批动作
     * @return 审批后的批次状态与流程流转结果
     */
    @Transactional(rollbackFor = Exception.class)
    public FundPoolAdjustAuditDto submitAdjustAudit(FundPoolAdjustAuditReq req) {
        // 校验审批提交参数
        validateAuditReq(req);
        FundAdjustStepBo step = fundPoolAdjustMapper.queryAdjustStepById(req.getStepId());
        // 管理员同时是处理人时定位其本人的待办步骤
        step = resolveActualProcessStep(req, step);
        // 校验步骤状态和当前处理人
        validatePendingStep(req, step);
        // 阻止发起人处理后续审批步骤
        validateSubmitterCannotProcess(req, step);

        // 加载当前步骤所属流程版本的快照
        FlowSnapshot snapshot = buildFlowSnapshot(step.getFlowNodeId());
        if (snapshot == null) {
            throw new BizException("审批流程快照不存在");
        }
        FlowNodeBo currentNode = snapshot.nodeMap.get(step.getFlowNodeId());
        if (currentNode == null) {
            throw new BizException("审批流程节点不存在");
        }
        // 处理当前步骤并按流程配置推进
        return processAdjustAudit(req, step, snapshot, currentNode);
    }

    /**
     * 校验审批提交参数。
     *
     * @param req 待校验的审批请求
     */
    private void validateAuditReq(FundPoolAdjustAuditReq req) {
        if (req.getStepId() == null) {
            throw new BizException("流程步骤 ID 不能为空");
        }
        if (!ProcessAction.APPROVE.getCode().equals(req.getProcessAction())
                && !ProcessAction.REJECT.getCode().equals(req.getProcessAction())) {
            throw new BizException("审批动作不合法");
        }
        // 驳回时校验处理意见已填写
        if (ProcessAction.REJECT.getCode().equals(req.getProcessAction()) && !hasText(req.getProcessComment())) {
            throw new BizException("驳回时处理意见不能为空");
        }
        // 校验当前处理人 ID 非空
        if (!hasText(req.getHandlerId())) {
            throw new BizException("当前处理人 ID 不能为空");
        }
    }

    /**
     * 管理员同时在节点处理人列表中时，优先处理管理员本人的待办步骤。
     *
     * @param req 当前处理人的审批请求
     * @param step 请求指定的待办步骤
     * @return 实际由当前处理人办理的步骤
     */
    private FundAdjustStepBo resolveActualProcessStep(FundPoolAdjustAuditReq req, FundAdjustStepBo step) {
        if (step == null || !AdminUserIdUtil.isAdminUser(req.getHandlerId())
                || req.getHandlerId().equals(step.getHandlerId())) {
            return step;
        }
        // 查找管理员本人在同一节点上的待办步骤
        FundAdjustStepBo adminStep = fundPoolAdjustMapper.queryPendingStepByHandler(
                step.getAdjustLogId(), step.getAdjustBatchNo(), step.getFlowNodeId(), req.getHandlerId());
        return adminStep == null ? step : adminStep;
    }

    /**
     * 校验当前步骤及处理人，并补齐步骤关联的调库记录和批次。
     *
     * @param req 待补齐批次与日志 ID 的审批请求
     * @param step 当前待处理步骤
     */
    private void validatePendingStep(FundPoolAdjustAuditReq req, FundAdjustStepBo step) {
        if (step == null) {
            throw new BizException("流程步骤不存在");
        }
        if (!StepStatus.PENDING.getCode().equals(step.getStepStatus())) {
            throw new BizException("当前流程步骤已处理，请刷新后重试");
        }
        // 已指定处理人的步骤只允许本人或管理员处理
        if (hasText(step.getHandlerId()) && !step.getHandlerId().equals(req.getHandlerId())
                && !AdminUserIdUtil.isAdminUser(req.getHandlerId())) {
            throw new BizException("当前用户不是该步骤处理人");
        }
        // 未传批次号时从步骤补齐
        if (!hasText(req.getAdjustBatchNo())) {
            req.setAdjustBatchNo(step.getAdjustBatchNo());
        }
        // 未传调库日志 ID 时从步骤补齐
        if (req.getAdjustLogId() == null) {
            req.setAdjustLogId(step.getAdjustLogId());
        }
    }

    /**
     * 发起人不能处理后续审批节点，管理员和发起人语义节点除外。
     *
     * @param req 当前处理人的审批请求
     * @param step 待审核的流程步骤
     */
    private void validateSubmitterCannotProcess(FundPoolAdjustAuditReq req, FundAdjustStepBo step) {
        // 管理员和发起人语义节点不受发起人回避规则限制
        if (AdminUserIdUtil.isAdminUser(req.getHandlerId()) || isInitiatorSemanticStep(step)) {
            return;
        }
        // 查询同批调库记录并核对发起人身份
        for (FundAdjustLogBo log : queryBatchLogs(step)) {
            if (req.getHandlerId().equals(log.getAdjusterId())) {
                throw new BizException("发起人不能参与后续流程操作");
            }
        }
    }

    /**
     * 处理当前审批步骤，并按审批策略和流程路由推进。
     *
     * @param req 当前处理动作与意见
     * @param step 当前待处理步骤
     * @param snapshot 当前流程版本的快照
     * @param currentNode 当前审批节点
     * @return 审批后的批次状态与流程流转结果
     */
    private FundPoolAdjustAuditDto processAdjustAudit(FundPoolAdjustAuditReq req, FundAdjustStepBo step,
                                                       FlowSnapshot snapshot, FlowNodeBo currentNode) {
        String action = req.getProcessAction();
        // 将发起人语义节点的通过动作转换为提交步骤状态
        String stepStatus = resolveStepStatusForProcess(step, snapshot, currentNode, action);
        String storedAction = StepStatus.SUBMIT.getCode().equals(stepStatus)
                ? ProcessAction.SUBMIT.getCode() : action;
        // 构建管理员代办意见并保存当前步骤处理结果
        int updated = fundPoolAdjustMapper.editAdjustStepProcess(step.getId(), stepStatus, storedAction, buildProcessComment(req, step));
        if (updated == 0) {
            throw new BizException("当前流程步骤已处理，请刷新后重试");
        }

        // 判断会签节点是否仍有待处理人员
        if (!completeCurrentApprovalNodeIfNeeded(step, action, stepStatus)) {
            // 返回当前批次状态和仍待会签的处理结果
            return buildAuditDto(step, queryCurrentAuditStatus(step), false, false,
                    "当前会签节点仍有待处理人员");
        }

        // 将修改节点通过动作映射为重新提交路由
        String routeAction = resolveRouteAction(snapshot, currentNode, action);
        // 沿流程路由创建下一步骤或到达结束节点
        FlowAdvanceResult result = advanceToNextAvailableStep(step, snapshot, currentNode, routeAction);
        if (ProcessAction.REJECT.getCode().equals(action)) {
            // 依据原状态判断终止驳回是撤回还是审批驳回
            String auditStatus = result.finished ? resolveTerminalRejectAuditStatus(queryCurrentAuditStatus(step))
                    : AuditStatus.REJECT_MODIFY.getCode();
            // 更新同批调库记录的审核状态
            editBatchAuditStatus(step, auditStatus);
            // 返回驳回后的流程处理结果
            return buildAuditDto(step, auditStatus, result.finished, result.nextStepCreated,
                    result.finished ? "审批流程已结束" : "审批已驳回，已流转到修改步骤");
        }
        if (result.finished) {
            // 终审通过后落地同批基金池状态
            finishAdjustBatch(step);
            // 返回调库生效的审批结果
            return buildAuditDto(step, AuditStatus.APPROVED.getCode(), true, false, "审批已通过，调库结果已生效");
        }
        // 下一审批步骤已创建，保持批次为流程中
        editBatchAuditStatus(step, AuditStatus.SUBMITTED.getCode());
        // 返回继续流转的审批结果
        return buildAuditDto(step, AuditStatus.SUBMITTED.getCode(), false, result.nextStepCreated,
                "审批已处理，已流转到下一步骤");
    }

    /**
     * 会签节点需全部通过，其余审批策略由首位处理人决定节点结果。
     *
     * @param step 刚完成处理的审批步骤
     * @param action 当前处理动作
     * @param stepStatus 已写入的步骤状态
     * @return 当前审批节点是否可以结束并继续流转
     */
    private boolean completeCurrentApprovalNodeIfNeeded(FundAdjustStepBo step, String action, String stepStatus) {
        if (ApprovalStrategy.ALL.getCode().equals(step.getApprovalStrategy())
                && ProcessAction.APPROVE.getCode().equals(action)) {
            // 会签通过时等待同节点其他处理人完成
            return fundPoolAdjustMapper.queryPendingStepCountByNode(
                    step.getAdjustLogId(), step.getAdjustBatchNo(), step.getFlowNodeId()) == 0;
        }
        // 非会签完成或驳回时跳过同节点其余待办
        fundPoolAdjustMapper.editOtherPendingStepSkipped(step.getId(), step.getAdjustLogId(),
                step.getAdjustBatchNo(), step.getFlowNodeId(), stepStatus);
        return true;
    }

    /**
     * 提交或修改语义节点通过时，步骤状态写为 submit。
     *
     * @param step 当前审批步骤
     * @param snapshot 当前流程版本的快照
     * @param node 当前审批节点
     * @param action 当前处理动作
     * @return 应写入的步骤状态
     */
    private String resolveStepStatusForProcess(FundAdjustStepBo step, FlowSnapshot snapshot,
                                               FlowNodeBo node, String action) {
        // 判断通过动作是否发生在发起提交或驳回修改节点
        if (ProcessAction.APPROVE.getCode().equals(action) && isInitiatorSemanticNode(snapshot, node,
                snapshot.configMap.get(node.getId()))) {
            return StepStatus.SUBMIT.getCode();
        }
        return action;
    }

    /**
     * 修改节点通过时，使用 resubmit 路由动作重新进入审批流程。
     *
     * @param snapshot 当前流程版本的快照
     * @param node 当前审批节点
     * @param action 当前处理动作
     * @return 下一条流程连线的路由动作
     */
    private String resolveRouteAction(FlowSnapshot snapshot, FlowNodeBo node, String action) {
        // 判断修改节点通过后是否应按重新提交路由流转
        if (ProcessAction.APPROVE.getCode().equals(action) && isModifyNode(snapshot, node, snapshot.configMap.get(node.getId()))) {
            return ProcessAction.RESUBMIT.getCode();
        }
        return action;
    }

    /**
     * 管理员代办他人步骤时追加代办标识。
     *
     * @param req 当前审批意见与处理人
     * @param step 实际办理的步骤
     * @return 按代办情况处理后的意见
     */
    private String buildProcessComment(FundPoolAdjustAuditReq req, FundAdjustStepBo step) {
        String comment = req.getProcessComment() == null ? "" : req.getProcessComment().trim();
        // 管理员处理他人步骤时标记代办意见
        if (AdminUserIdUtil.isAdminUser(req.getHandlerId()) && hasText(step.getHandlerId())
                && !step.getHandlerId().equals(req.getHandlerId())) {
            return comment + "（由管理员操作）";
        }
        return comment;
    }

    /**
     * 按流程快照推进自动节点，或为下一人工节点创建待办步骤。
     *
     * @param step 当前已处理步骤
     * @param snapshot 当前流程版本的快照
     * @param currentNode 当前审批节点
     * @param routeAction 当前处理动作对应的路由动作
     * @return 流程结束或创建后续待办的结果
     */
    private FlowAdvanceResult advanceToNextAvailableStep(FundAdjustStepBo step, FlowSnapshot snapshot,
                                                          FlowNodeBo currentNode, String routeAction) {
        FlowAdvanceResult result = new FlowAdvanceResult();
        Set<Long> visitedNodeIds = new HashSet<>();
        visitedNodeIds.add(currentNode.getId());
        // 按当前处理动作查找下一节点
        FlowNodeBo nextNode = findNextNode(snapshot, currentNode, null, routeAction);
        while (nextNode != null) {
            if (nextNode.getId() == null || !visitedNodeIds.add(nextNode.getId())) {
                throw new BizException("流程配置异常：存在循环节点");
            }
            NodeApprovalConfigBo config = snapshot.configMap.get(nextNode.getId());
            if (NodeType.END.getCode().equals(nextNode.getNodeType())) {
                // 记录流程结束节点的自动处理步骤
                insertStep(step, nextNode, config, StepStatus.AUTO_PROCESS.getCode(), null, null,
                        ProcessAction.AUTO_PROCESS.getCode(), "", new Date());
                result.finished = true;
                return result;
            }
            if (NodeType.APPROVAL.getCode().equals(nextNode.getNodeType())) {
                // 根据节点配置区分自动审批与人工待办
                if (isAutoApprovalNode(config)) {
                    // 为自动审批节点记录已处理步骤
                    createAutoProcessSteps(step, nextNode, config, snapshot);
                } else {
                    // 为人工审批节点创建待办步骤
                    createPendingSteps(step, nextNode, config, snapshot);
                    result.nextStepCreated = true;
                    return result;
                }
            } else {
                // 记录非审批节点的自动处理步骤
                insertStep(step, nextNode, config, StepStatus.AUTO_PROCESS.getCode(), null, null,
                        ProcessAction.AUTO_PROCESS.getCode(), "", new Date());
            }
            FlowNodeBo processedNode = nextNode;
            String nextAction = NodeType.AUTO.getCode().equals(nextNode.getNodeType())
                    || NodeType.NOTIFY.getCode().equals(nextNode.getNodeType())
                    ? "auto" : ProcessAction.APPROVE.getCode();
            // 按自动处理或通过动作继续查找后继节点
            nextNode = findNextNode(snapshot, processedNode, currentNode, nextAction);
            currentNode = processedNode;
        }
        throw new BizException("流程配置异常：未找到流程结束节点");
    }

    /**
     * 最终审批通过时将同批日志置为通过并落地基金池状态。
     *
     * @param step 当前终审步骤，用于定位调库批次
     */
    private void finishAdjustBatch(FundAdjustStepBo step) {
        // 查询同批调库日志以执行终审校验和落地
        List<FundAdjustLogBo> logs = queryBatchLogs(step);
        if (logs.isEmpty()) {
            throw new BizException("基金调库批次记录不存在");
        }
        // 终审前再次校验同批基金调库记录
        fundPoolAdjustService.recheckBeforeFinalApproval(logs);
        // 将同批日志更新为审批通过并核对更新数量
        int updated = fundPoolAdjustMapper.editAdjustLogAuditStatus(
                step.getAdjustBatchNo(), AuditStatus.APPROVED.getCode());
        if (updated != logs.size()) {
            throw new BizException("基金调库申请状态已发生变化，请刷新后重试");
        }
        // 审批通过后将调整结果写入基金池当前状态
        fundPoolAdjustService.applyPoolStatusChanges(logs);
    }

    /**
     * 为下一审批节点创建人工待办步骤。
     *
     * @param currentStep 当前已处理步骤
     * @param node 下一人工审批节点
     * @param config 下一节点的审批配置
     * @param snapshot 当前流程版本的快照
     */
    private void createPendingSteps(FundAdjustStepBo currentStep, FlowNodeBo node,
                                    NodeApprovalConfigBo config, FlowSnapshot snapshot) {
        // 发起人语义节点需要将待办指派给原发起人
        if (isInitiatorSemanticNode(snapshot, node, config)) {
            // 从同批首条调库日志取得发起人信息
            FundAdjustLogBo initiator = queryFirstBatchLog(currentStep);
            // 校验发起人信息可用于创建待办
            if (initiator == null || !hasText(initiator.getAdjusterId())) {
                throw new BizException("基金调库发起人信息不存在");
            }
            // 创建发起人的待办步骤
            insertStep(currentStep, node, config, StepStatus.PENDING.getCode(),
                    initiator.getAdjusterId(), initiator.getAdjusterName(), null, null, new Date());
            return;
        }
        // 解析节点配置中的用户与角色处理人
        List<HandlerTarget> handlers = resolveApprovalHandlers(config, snapshot);
        if (handlers.isEmpty()) {
            // 无指定处理人时创建公共待办步骤
            insertStep(currentStep, node, config, StepStatus.PENDING.getCode(), null, null, null, null, new Date());
            return;
        }
        // 排除同批已实际参与处理的人员
        handlers = excludeParticipatedHandlers(currentStep, handlers);
        for (HandlerTarget handler : handlers) {
            // 为每位可用处理人创建待办步骤
            insertStep(currentStep, node, config, StepStatus.PENDING.getCode(),
                    handler.id, handler.name, null, null, new Date());
        }
    }

    /**
     * 自动审批节点写入可追溯步骤后继续流转。
     *
     * @param currentStep 当前已处理步骤
     * @param node 自动审批节点
     * @param config 自动审批配置
     * @param snapshot 当前流程版本的快照
     */
    private void createAutoProcessSteps(FundAdjustStepBo currentStep, FlowNodeBo node,
                                        NodeApprovalConfigBo config, FlowSnapshot snapshot) {
        // 解析自动审批节点配置的处理人
        List<HandlerTarget> handlers = resolveApprovalHandlers(config, snapshot);
        if (!handlers.isEmpty()) {
            // 过滤同批已参与处理的人员
            handlers = filterParticipatedHandlers(currentStep, handlers);
        }
        if (handlers.isEmpty()) {
            // 无可记录人员时写入系统自动审批步骤
            insertStep(currentStep, node, config, StepStatus.AUTO_PROCESS.getCode(), null, null,
                    ProcessAction.AUTO_PROCESS.getCode(), "系统自动审批通过", new Date());
            return;
        }
        for (HandlerTarget handler : handlers) {
            // 为每位可记录人员写入自动审批步骤
            insertStep(currentStep, node, config, StepStatus.AUTO_PROCESS.getCode(), handler.id, handler.name,
                    ProcessAction.AUTO_PROCESS.getCode(), "系统自动审批通过", new Date());
        }
    }

    /**
     * 排除已在同一调库批次实际参与处理的处理人。
     *
     * @param currentStep 当前已处理步骤，用于定位调库批次
     * @param handlers 下一节点配置的候选处理人
     * @return 排除已参与人员后的可用处理人
     */
    private List<HandlerTarget> excludeParticipatedHandlers(FundAdjustStepBo currentStep,
                                                             List<HandlerTarget> handlers) {
        // 筛出同批次尚未参与处理的人员
        List<HandlerTarget> available = filterParticipatedHandlers(currentStep, handlers);
        if (available.isEmpty()) {
            throw new BizException(AdjustStepHandlerExcludeUtil.NO_AVAILABLE_HANDLER_MSG);
        }
        return available;
    }

    /**
     * 按整批步骤筛选尚未实际参与过该批次的人员。
     *
     * @param currentStep 当前已处理步骤，用于定位调库批次
     * @param handlers 待筛选的候选处理人
     * @return 尚未参与该批次的处理人
     */
    private List<HandlerTarget> filterParticipatedHandlers(FundAdjustStepBo currentStep,
                                                            List<HandlerTarget> handlers) {
        Set<String> participatedIds = new HashSet<>();
        for (FundAdjustStepBo step : fundPoolAdjustMapper.queryAdjustStepByBatchList(
                currentStep.getAdjustLogId(), currentStep.getAdjustBatchNo())) {
            // 仅统计有处理人且实际提交、通过或驳回的步骤
            if (hasText(step.getHandlerId()) && isActualParticipation(step)) {
                participatedIds.add(step.getHandlerId().trim());
            }
        }
        List<HandlerTarget> available = new ArrayList<>();
        for (HandlerTarget handler : handlers) {
            if (!participatedIds.contains(handler.id.trim())) {
                available.add(handler);
            }
        }
        return available;
    }

    /**
     * 只将 submit、approve、reject 动作计为实际参与。
     *
     * @param step 待判断的批次步骤
     * @return 是否实际参与过该批次
     */
    private boolean isActualParticipation(FundAdjustStepBo step) {
        // 无处理动作的步骤不计入实际参与
        if (!hasText(step.getProcessAction())) {
            return false;
        }
        String action = step.getProcessAction().trim();
        return ProcessAction.SUBMIT.getCode().equals(action)
                || ProcessAction.APPROVE.getCode().equals(action)
                || ProcessAction.REJECT.getCode().equals(action);
    }

    /**
     * 将审批配置解析为人员及角色下的全部人员。
     *
     * @param config 下一审批节点的处理人配置
     * @param snapshot 当前流程版本的快照
     * @return 去重后的候选处理人
     */
    private List<HandlerTarget> resolveApprovalHandlers(NodeApprovalConfigBo config, FlowSnapshot snapshot) {
        if (config == null || config.getId() == null) {
            return Collections.emptyList();
        }
        List<NodeApprovalHandlerBo> configuredHandlers = snapshot.handlerMap.get(config.getId());
        if (configuredHandlers == null || configuredHandlers.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, HandlerTarget> targets = new LinkedHashMap<>();
        List<RoleBo> allRoles = null;
        for (NodeApprovalHandlerBo handler : configuredHandlers) {
            if (handler == null || handler.getHandlerId() == null) {
                continue;
            }
            if (HandlerType.USER.getCode().equals(handler.getHandlerType())) {
                String id = String.valueOf(handler.getHandlerId());
                targets.put(id, new HandlerTarget(id, handler.getHandlerName()));
            } else if (HandlerType.ROLE.getCode().equals(handler.getHandlerType())) {
                if (allRoles == null) {
                    allRoles = flowMapper.queryRoleList();
                }
                List<Long> roleIds = new ArrayList<>();
                // 收集配置角色及其子角色以查询全部处理人
                collectDescendantRoleIds(handler.getHandlerId(), roleIds, allRoles, new HashSet<Long>());
                // 查询配置角色及其子角色对应的处理人
                List<UserBo> users = flowMapper.queryUserList(roleIds, null);
                if (users == null) {
                    continue;
                }
                for (UserBo user : users) {
                    if (user != null && user.getId() != null) {
                        String id = String.valueOf(user.getId());
                        targets.put(id, new HandlerTarget(id, user.getName()));
                    }
                }
            }
        }
        return new ArrayList<>(targets.values());
    }

    /**
     * 递归收集角色本身及其子角色 ID。
     *
     * @param roleId 当前角色 ID
     * @param roleIds 收集到的角色 ID 列表
     * @param allRoles 全部角色及其父子关系
     * @param visitedRoleIds 已遍历角色 ID，用于避免循环
     */
    private void collectDescendantRoleIds(Long roleId, List<Long> roleIds, List<RoleBo> allRoles,
                                          Set<Long> visitedRoleIds) {
        if (roleId == null || !visitedRoleIds.add(roleId)) {
            return;
        }
        roleIds.add(roleId);
        if (allRoles == null) {
            return;
        }
        for (RoleBo role : allRoles) {
            if (role != null && roleId.equals(role.getParentId())) {
                // 递归纳入当前角色的子角色
                collectDescendantRoleIds(role.getId(), roleIds, allRoles, visitedRoleIds);
            }
        }
    }

    /**
     * 插入单条基金流程步骤。
     *
     * @param currentStep 当前步骤，用于关联调库记录和批次
     * @param node 待记录的流程节点
     * @param config 节点审批配置
     * @param status 新步骤状态
     * @param handlerId 新步骤处理人 ID
     * @param handlerName 新步骤处理人名称
     * @param action 自动处理或审批动作
     * @param comment 步骤处理意见
     * @param now 步骤创建与处理时间
     */
    private void insertStep(FundAdjustStepBo currentStep, FlowNodeBo node, NodeApprovalConfigBo config,
                            String status, String handlerId, String handlerName, String action,
                            String comment, Date now) {
        FundAdjustStepBo step = new FundAdjustStepBo();
        step.setAdjustLogId(currentStep.getAdjustLogId());
        step.setAdjustBatchNo(currentStep.getAdjustBatchNo());
        step.setFlowNodeId(node.getId());
        step.setNodeCode(node.getNodeId());
        step.setNodeLabel(node.getLabel());
        step.setNodeType(node.getNodeType());
        step.setApprovalStrategy(config == null ? null : config.getApprovalStrategy());
        step.setSortOrder(node.getSortOrder() == null ? 1 : node.getSortOrder());
        step.setStepStatus(status);
        step.setHandlerId(handlerId);
        step.setHandlerName(handlerName);
        step.setProcessAction(action);
        step.setProcessComment(comment);
        step.setStartTime(now);
        step.setProcessTime(StepStatus.PENDING.getCode().equals(status) ? null : now);
        fundPoolAdjustMapper.addAdjustStep(step);
    }

    /**
     * 按节点与动作查找下一流程节点。
     *
     * @param snapshot 当前流程版本的快照
     * @param currentNode 当前流程节点
     * @param previousNode 刚经过的上一节点，用于避免原路返回
     * @param processAction 当前处理动作对应的路由动作
     * @return 匹配的下一流程节点
     */
    private FlowNodeBo findNextNode(FlowSnapshot snapshot, FlowNodeBo currentNode,
                                    FlowNodeBo previousNode, String processAction) {
        // 收集当前节点的所有出口连线
        List<FlowEdgeBo> outgoingEdges = new ArrayList<>();
        for (FlowEdgeBo edge : snapshot.edges) {
            if (currentNode.getId().equals(edge.getFromNodeId())) {
                outgoingEdges.add(edge);
            }
        }
        // 有多条出口时排除返回上一节点的连线
        if (previousNode != null && outgoingEdges.size() > 1) {
            List<FlowEdgeBo> forwardEdges = new ArrayList<>();
            for (FlowEdgeBo edge : outgoingEdges) {
                if (!previousNode.getId().equals(edge.getToNodeId())) {
                    forwardEdges.add(edge);
                }
            }
            if (!forwardEdges.isEmpty()) {
                outgoingEdges = forwardEdges;
            }
        }
        // 唯一出口无需按动作区分路由
        if (outgoingEdges.size() == 1) {
            FlowNodeBo nextNode = snapshot.nodeMap.get(outgoingEdges.get(0).getToNodeId());
            if (nextNode != null) {
                return nextNode;
            }
        }
        // 多条出口按当前处理动作选择连线
        for (FlowEdgeBo edge : outgoingEdges) {
            if (processAction.equals(edge.getRouteAction())) {
                FlowNodeBo nextNode = snapshot.nodeMap.get(edge.getToNodeId());
                if (nextNode != null) {
                    return nextNode;
                }
            }
        }
        throw new BizException("流程配置异常：节点[" + currentNode.getLabel() + "]缺少匹配审批动作的下一步连线");
    }

    /**
     * 根据流程节点加载其所属版本的流程快照。
     *
     * @param flowNodeId 当前流程节点 ID
     * @return 当前版本的节点、连线与审批配置快照
     */
    private FlowSnapshot buildFlowSnapshot(Long flowNodeId) {
        if (flowNodeId == null) {
            return null;
        }
        // 读取当前节点以确定所属流程版本
        FlowNodeBo currentNode = flowMapper.queryFlowNodeById(flowNodeId);
        if (currentNode == null || currentNode.getVersionId() == null) {
            return null;
        }
        // 加载当前流程版本的节点和连线
        List<FlowNodeBo> nodes = flowMapper.queryFlowNodeListByVersionId(currentNode.getVersionId());
        List<FlowEdgeBo> edges = flowMapper.queryFlowEdgeListByVersionId(currentNode.getVersionId());
        // 按节点 ID 建立流程节点索引
        Map<Long, FlowNodeBo> nodeMap = new HashMap<>();
        for (FlowNodeBo node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        // 按节点建立审批配置索引
        Map<Long, NodeApprovalConfigBo> configMap = new HashMap<>();
        for (NodeApprovalConfigBo config : flowMapper.queryApprovalConfigListByVersionId(currentNode.getVersionId())) {
            configMap.put(config.getNodeId(), config);
        }
        // 按审批配置建立处理人索引
        Map<Long, List<NodeApprovalHandlerBo>> handlerMap = new HashMap<>();
        for (NodeApprovalHandlerBo handler : flowMapper.queryApprovalHandlerListByVersionId(currentNode.getVersionId())) {
            handlerMap.computeIfAbsent(handler.getApprovalConfigId(), key -> new ArrayList<>()).add(handler);
        }
        return new FlowSnapshot(nodeMap, edges, configMap, handlerMap);
    }

    /**
     * 查询当前批次未删除的基金调库日志。
     *
     * @param step 当前步骤，用于取得调库批次号
     * @return 同批次调库日志
     */
    private List<FundAdjustLogBo> queryBatchLogs(FundAdjustStepBo step) {
        return fundPoolAdjustMapper.queryAdjustLogListForAudit(step.getAdjustBatchNo());
    }

    /**
     * 查询同批次首条基金调库日志。
     *
     * @param step 当前步骤，用于取得调库批次号
     * @return 首条日志；批次为空时返回 null
     */
    private FundAdjustLogBo queryFirstBatchLog(FundAdjustStepBo step) {
        // 取得同批次日志并返回首条记录
        List<FundAdjustLogBo> logs = queryBatchLogs(step);
        return logs.isEmpty() ? null : logs.get(0);
    }

    /**
     * 读取当前批次审核状态。
     *
     * @param step 当前步骤，用于取得调库批次号
     * @return 批次审核状态；批次为空时返回空字符串
     */
    private String queryCurrentAuditStatus(FundAdjustStepBo step) {
        // 读取同批首条日志中的当前审核状态
        FundAdjustLogBo log = queryFirstBatchLog(step);
        return log == null ? "" : log.getAuditStatus();
    }

    /**
     * 更新仍处于流程中的调库批次状态。
     *
     * @param step 当前步骤，用于取得调库批次号
     * @param auditStatus 目标批次审核状态
     */
    private void editBatchAuditStatus(FundAdjustStepBo step, String auditStatus) {
        int updated = fundPoolAdjustMapper.editAdjustLogAuditStatus(step.getAdjustBatchNo(), auditStatus);
        if (updated == 0) {
            // 更新数量为零时复查同批日志是否已处于目标状态
            List<FundAdjustLogBo> logs = queryBatchLogs(step);
            boolean alreadySet = !logs.isEmpty();
            for (FundAdjustLogBo log : logs) {
                if (!auditStatus.equals(log.getAuditStatus())) {
                    alreadySet = false;
                    break;
                }
            }
            if (!alreadySet) {
                throw new BizException("基金调库申请状态已发生变化，请刷新后重试");
            }
        }
    }

    /**
     * 判断当前步骤是否为发起人提交或驳回修改语义节点。
     *
     * @param step 待判断的流程步骤
     * @return 是否属于发起人语义节点
     */
    private boolean isInitiatorSemanticStep(FundAdjustStepBo step) {
        if (step == null || !ApprovalStrategy.INITIATOR.getCode().equals(step.getApprovalStrategy())) {
            return false;
        }
        // 加载当前步骤的流程快照以识别发起人语义
        FlowSnapshot snapshot = buildFlowSnapshot(step.getFlowNodeId());
        FlowNodeBo node = snapshot == null ? null : snapshot.nodeMap.get(step.getFlowNodeId());
        // 判断当前节点是否为发起提交或驳回修改节点
        return node != null && isInitiatorSemanticNode(snapshot, node, snapshot.configMap.get(node.getId()));
    }

    /**
     * 判断节点是否为发起提交或驳回修改语义节点。
     *
     * @param snapshot 当前流程版本的快照
     * @param node 待判断的流程节点
     * @param config 节点审批配置
     * @return 是否属于发起人语义节点
     */
    private boolean isInitiatorSemanticNode(FlowSnapshot snapshot, FlowNodeBo node,
                                            NodeApprovalConfigBo config) {
        // 同时识别发起提交和驳回修改两种发起人语义
        return isInitiatorNode(snapshot, node, config) || isModifyNode(snapshot, node, config);
    }

    /**
     * 判断节点是否为发起人提交语义节点。
     *
     * @param snapshot 当前流程版本的快照
     * @param node 待判断的流程节点
     * @param config 节点审批配置
     * @return 是否属于发起人提交节点
     */
    private boolean isInitiatorNode(FlowSnapshot snapshot, FlowNodeBo node, NodeApprovalConfigBo config) {
        // 通过提交路由识别发起人提交节点
        return hasInitiatorRoute(snapshot, node, config, ProcessAction.SUBMIT.getCode());
    }

    /**
     * 判断节点是否为驳回后的发起人修改语义节点。
     *
     * @param snapshot 当前流程版本的快照
     * @param node 待判断的流程节点
     * @param config 节点审批配置
     * @return 是否属于发起人修改节点
     */
    private boolean isModifyNode(FlowSnapshot snapshot, FlowNodeBo node, NodeApprovalConfigBo config) {
        // 通过重新提交路由识别驳回修改节点
        return hasInitiatorRoute(snapshot, node, config, ProcessAction.RESUBMIT.getCode());
    }

    /**
     * 判断发起人语义审批节点是否存在相应路由。
     *
     * @param snapshot 当前流程版本的快照
     * @param node 待判断的流程节点
     * @param config 节点审批配置
     * @param routeAction 待匹配的路由动作
     * @return 是否存在匹配的发起人语义连线
     */
    private boolean hasInitiatorRoute(FlowSnapshot snapshot, FlowNodeBo node,
                                     NodeApprovalConfigBo config, String routeAction) {
        if (snapshot == null || node == null || config == null
                || !NodeType.APPROVAL.getCode().equals(node.getNodeType())
                || !ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())) {
            return false;
        }
        for (FlowEdgeBo edge : snapshot.edges) {
            if (node.getId().equals(edge.getFromNodeId()) && routeAction.equals(edge.getRouteAction())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断节点是否为系统自动审批策略。
     *
     * @param config 节点审批配置
     * @return 是否由系统自动处理
     */
    private boolean isAutoApprovalNode(NodeApprovalConfigBo config) {
        return config != null && (ApprovalStrategy.AUTO.getCode().equals(config.getApprovalStrategy())
                || ApprovalStrategy.O32.getCode().equals(config.getApprovalStrategy()));
    }

    /**
     * 将拒绝状态映射为审批驳回或发起人撤回。
     *
     * @param currentAuditStatus 驳回前的批次审核状态
     * @return 最终驳回或撤回状态
     */
    private String resolveTerminalRejectAuditStatus(String currentAuditStatus) {
        return AuditStatus.REJECT_MODIFY.getCode().equals(currentAuditStatus)
                ? AuditStatus.REVOKED.getCode() : AuditStatus.REJECTED.getCode();
    }

    /**
     * 构建基金审批处理结果。
     *
     * @param step 当前审批步骤
     * @param auditStatus 调库批次审核状态
     * @param finished 流程是否结束
     * @param nextStepCreated 是否已创建后续待办
     * @param message 返回给调用方的处理信息
     * @return 审批处理结果
     */
    private FundPoolAdjustAuditDto buildAuditDto(FundAdjustStepBo step, String auditStatus,
                                                  boolean finished, boolean nextStepCreated, String message) {
        FundPoolAdjustAuditDto dto = new FundPoolAdjustAuditDto();
        dto.setAdjustLogId(step.getAdjustLogId());
        dto.setAdjustBatchNo(step.getAdjustBatchNo());
        dto.setStepId(step.getId());
        dto.setAuditStatus(auditStatus);
        dto.setFinished(finished);
        dto.setNextStepCreated(nextStepCreated);
        dto.setMessage(message);
        return dto;
    }

    /**
     * 判断字符串是否包含有效内容。
     *
     * @param value 待判断的字符串
     * @return 是否含有非空白字符
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 一次流程推进的结果。 */
    private static class FlowAdvanceResult {
        /** 流程是否已结束 */
        private boolean finished;
        /** 是否创建了后续人工待办 */
        private boolean nextStepCreated;
    }

    /** 当前流程版本的审批快照。 */
    private static class FlowSnapshot {
        /** 流程节点索引 */
        private final Map<Long, FlowNodeBo> nodeMap;
        /** 流程连线 */
        private final List<FlowEdgeBo> edges;
        /** 节点审批配置索引 */
        private final Map<Long, NodeApprovalConfigBo> configMap;
        /** 审批配置处理人索引 */
        private final Map<Long, List<NodeApprovalHandlerBo>> handlerMap;

        /**
         * 保存当前流程版本的节点、连线、审批配置和处理人索引。
         *
         * @param nodeMap 流程节点索引
         * @param edges 流程连线
         * @param configMap 节点审批配置索引
         * @param handlerMap 审批配置处理人索引
         */
        FlowSnapshot(Map<Long, FlowNodeBo> nodeMap, List<FlowEdgeBo> edges,
                     Map<Long, NodeApprovalConfigBo> configMap,
                     Map<Long, List<NodeApprovalHandlerBo>> handlerMap) {
            this.nodeMap = nodeMap;
            this.edges = edges;
            this.configMap = configMap;
            this.handlerMap = handlerMap;
        }
    }

    /** 审批处理人。 */
    private static class HandlerTarget {
        /** 用户 ID */
        private final String id;
        /** 用户名称 */
        private final String name;

        /**
         * 保存可办理审批的人员信息。
         *
         * @param id 处理人 ID
         * @param name 处理人名称
         */
        HandlerTarget(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
