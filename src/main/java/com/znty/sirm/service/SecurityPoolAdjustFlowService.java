package com.znty.sirm.service;

import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.FlowMapper;
import com.znty.sirm.mapper.SecurityPoolAdjustMapper;
import com.znty.sirm.model.FlowDefinitionBo;
import com.znty.sirm.model.EdgeCondRuleBo;
import com.znty.sirm.model.FlowEdgeBo;
import com.znty.sirm.model.FlowNodeBo;
import com.znty.sirm.model.FlowSnapshot;
import com.znty.sirm.model.FlowVersionBo;
import com.znty.sirm.model.IpAdjustLogBo;
import com.znty.sirm.model.IpAdjustStepBo;
import com.znty.sirm.model.NodeApprovalConfigBo;
import com.znty.sirm.model.NodeApprovalHandlerBo;
import com.znty.sirm.model.RoleBo;
import com.znty.sirm.model.SecurityPoolAdjustAuditDto;
import com.znty.sirm.model.SecurityPoolAdjustAuditReq;
import com.znty.sirm.model.UserBo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 证券池调库流程服务，负责调库审批步骤处理、流程推进和最终池状态落地。
 */
@Service
public class SecurityPoolAdjustFlowService {

    /** 证券池调库数据库操作 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 流程定义数据库操作 */
    @Resource
    private FlowMapper flowMapper;

    /**
     * 提交调库审批处理意见，更新当前步骤并按审批流程推进。
     *
     * @param req 审批处理请求
     * @return 审批处理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolAdjustAuditDto submitAdjustAudit(SecurityPoolAdjustAuditReq req) {
        // 校验审批提交参数
        validateAuditReq(req);

        // 查询并校验当前待处理步骤
        IpAdjustStepBo step = securityPoolAdjustMapper.queryAdjustStepById(req.getStepId());
        // 校验当前步骤是否可处理
        validatePendingStep(req, step);

        // 处理当前步骤并按流程配置推进
        return processAdjustAudit(req, step);
    }

    /**
     * 校验审批提交参数。
     */
    private void validateAuditReq(SecurityPoolAdjustAuditReq req) {
        if (req == null) {
            throw new BizException("审批参数不能为空");
        }
        if (req.getStepId() == null) {
            throw new BizException("流程步骤 ID 不能为空");
        }
        if (!"approve".equals(req.getProcessAction()) && !"reject".equals(req.getProcessAction())) {
            throw new BizException("审批动作不合法");
        }
        // 判断驳回意见是否已填写
        if ("reject".equals(req.getProcessAction()) && !hasText(req.getProcessComment())) {
            throw new BizException("驳回时处理意见不能为空");
        }
    }

    /**
     * 校验当前步骤是否可由当前处理人处理。
     */
    private void validatePendingStep(SecurityPoolAdjustAuditReq req, IpAdjustStepBo step) {
        if (step == null) {
            throw new BizException("流程步骤不存在");
        }
        if (!"pending".equals(step.getStepStatus())) {
            throw new BizException("当前流程步骤已处理，请刷新后重试");
        }
        // 判断当前处理人是否可处理该步骤
        if (hasText(step.getHandlerId()) && !step.getHandlerId().equals(req.getHandlerId()) && !isAdminOperator(req)) {
            throw new BizException("当前用户不是该步骤处理人");
        }
        // 补齐调库批次号
        if (!hasText(req.getAdjustBatchNo()) && hasText(step.getAdjustBatchNo())) {
            req.setAdjustBatchNo(step.getAdjustBatchNo());
        }
        if (req.getAdjustLogId() == null) {
            req.setAdjustLogId(step.getAdjustLogId());
        }
    }

    /**
     * 处理审批动作并推进流程。
     */
    private SecurityPoolAdjustAuditDto processAdjustAudit(SecurityPoolAdjustAuditReq req, IpAdjustStepBo step) {
        // 构建实际写入的处理意见
        String processComment = buildProcessComment(req, step);
        String stepStatus = "approve".equals(req.getProcessAction()) ? "approved" : "rejected";
        int updated = securityPoolAdjustMapper.editAdjustStepProcess(
                step.getId(), stepStatus, req.getProcessAction(), processComment);
        if (updated == 0) {
            throw new BizException("当前流程步骤已处理，请刷新后重试");
        }

        // 判断当前审批节点是否已完成
        boolean nodeCompleted = completeCurrentApprovalNodeIfNeeded(step, req.getProcessAction());
        if (!nodeCompleted) {
            String currentAuditStatus = queryCurrentAuditStatus(step);
            // 构建审批处理返回对象
            return buildAuditDto(step, currentAuditStatus, false, false, "当前会签节点仍有待处理人员");
        }

        // 构建当前流程版本快照
        FlowSnapshot snapshot = buildFlowSnapshot(step.getFlowNodeId());
        FlowNodeBo processingNode = snapshot != null ? snapshot.getNodeMap().get(step.getFlowNodeId()) : null;
        // 根据本次处理的流程节点和审批动作解析调库记录状态，不能使用下一步新创建节点
        String auditStatus = resolveProcessingNodeAuditStatus(processingNode, req.getProcessAction());
        securityPoolAdjustMapper.editAdjustLogAuditStatus(step.getAdjustLogId(), step.getAdjustBatchNo(), auditStatus);

        // 判断当前节点是否已经终止流程
        if (isTerminalByCurrentNode(processingNode, req.getProcessAction())) {
            // 终止流程时按流程定义继续记录结束节点
            boolean endStepCreated = createTerminalEndStep(step, snapshot, processingNode, req.getProcessAction());
            // 构建审批处理返回对象
            return buildAuditDto(step, auditStatus, true, endStepCreated, "审批流程已结束");
        }

        // 推进到下一可处理节点
        FlowAdvanceResult advanceResult = advanceToNextAvailableStep(step, snapshot, processingNode, req.getProcessAction());

        if (advanceResult.finished) {
            // 落地同批次调库结果
            finishAdjustBatch(step);
            // 构建审批处理返回对象
            return buildAuditDto(step, "20", true, advanceResult.nextStepCreated, "审批已通过，调库结果已生效");
        }

        // 构建审批处理返回对象
        return buildAuditDto(step, auditStatus, false, advanceResult.nextStepCreated, "审批已处理，已流转到下一步骤");
    }

    /**
     * 根据审批策略判断当前节点是否已经完成。
     */
    private boolean completeCurrentApprovalNodeIfNeeded(IpAdjustStepBo step, String processAction) {
        if ("all".equals(step.getApprovalStrategy()) && "approve".equals(processAction)) {
            int pendingCount = securityPoolAdjustMapper.queryPendingStepCountByNode(
                    step.getAdjustLogId(), step.getAdjustBatchNo(), step.getFlowNodeId());
            return pendingCount == 0;
        }

        // 抢占审批或未配置策略时，一个人通过即完成当前节点
        securityPoolAdjustMapper.editOtherPendingStepSkipped(
                step.getId(), step.getAdjustLogId(), step.getAdjustBatchNo(), step.getFlowNodeId());
        return true;
    }

    /**
     * 推进到下一可处理节点，自动节点直接完成，审批节点创建待处理步骤。
     */
    private FlowAdvanceResult advanceToNextAvailableStep(IpAdjustStepBo step, FlowSnapshot snapshot,
                                                         FlowNodeBo currentNode, String processAction) {
        FlowAdvanceResult result = new FlowAdvanceResult();
        if (snapshot == null || currentNode == null) {
            result.finished = true;
            return result;
        }

        Date now = new Date();
        FlowNodeBo prevNode = currentNode;
        // 按当前审核状态条件查找下一节点
        FlowNodeBo nextNode = findNextNode(snapshot, currentNode, null, processAction);
        while (nextNode != null) {
            NodeApprovalConfigBo config = snapshot.getApprovalConfigMap().get(nextNode.getId());
            int sortOrder = nextNode.getSortOrder() != null ? nextNode.getSortOrder() : 1;

            if (isAutoApprovalNode(nextNode)) {
                // O32 自动审批节点直接记录为自动完成，并继续流转到结束节点
                insertStepRecord(step.getAdjustLogId(), step.getAdjustBatchNo(), nextNode, config, sortOrder,
                        "auto_completed", null, null, "auto_process", null, now);
                FlowNodeBo afterNode = findNextNode(snapshot, nextNode, prevNode, processAction);
                prevNode = nextNode;
                nextNode = afterNode;
                continue;
            }

            if ("approval".equals(nextNode.getNodeType())) {
                // 为下一审批节点创建待处理步骤
                createPendingSteps(step, nextNode, snapshot, now);
                result.nextStepCreated = true;
                result.finished = false;
                return result;
            }

            // 插入自动完成节点步骤
            insertStepRecord(step.getAdjustLogId(), step.getAdjustBatchNo(), nextNode, config, sortOrder,
                    "auto_completed", null, null, "auto_process", null, now);

            if ("end".equals(nextNode.getNodeType())) {
                result.finished = true;
                return result;
            }

            // 继续查找后续主路径节点
            FlowNodeBo afterNode = findNextNode(snapshot, nextNode, prevNode, processAction);
            prevNode = nextNode;
            nextNode = afterNode;
        }

        result.finished = true;
        return result;
    }

    /**
     * 终止流程时创建流程定义中的结束节点步骤。
     */
    private boolean createTerminalEndStep(IpAdjustStepBo step, FlowSnapshot snapshot,
                                          FlowNodeBo currentNode, String processAction) {
        // 判断当前节点是否为修改节点
        boolean modifyNode = isModifyNode(currentNode);
        // 判断当前节点是否为审批节点
        boolean approveNode = isApproveNode(currentNode);
        if (snapshot == null || currentNode == null || !"reject".equals(processAction)
                || (!modifyNode && !approveNode)) {
            return false;
        }
        Date now = new Date();
        // 按当前审批动作查找下一节点
        FlowNodeBo nextNode = findNextNode(snapshot, currentNode, null, processAction);
        while (nextNode != null) {
            NodeApprovalConfigBo config = snapshot.getApprovalConfigMap().get(nextNode.getId());
            int sortOrder = nextNode.getSortOrder() != null ? nextNode.getSortOrder() : 1;
            // 插入自动完成节点步骤
            insertStepRecord(step.getAdjustLogId(), step.getAdjustBatchNo(), nextNode, config, sortOrder,
                    "auto_completed", null, null, "auto_process", null, now);
            if ("end".equals(nextNode.getNodeType())) {
                return true;
            }
            if ("approval".equals(nextNode.getNodeType())) {
                return false;
            }
            // 继续沿终止分支查找结束节点
            nextNode = findNextNode(snapshot, nextNode, currentNode, processAction);
        }
        return false;
    }

    /**
     * 流程最终通过后，更新同批调库日志并落地当前池状态。
     */
    private void finishAdjustBatch(IpAdjustStepBo step) {
        List<IpAdjustLogBo> logList = securityPoolAdjustMapper.queryAdjustLogListForAudit(
                step.getAdjustLogId(), step.getAdjustBatchNo());
        if (logList == null || logList.isEmpty()) {
            securityPoolAdjustMapper.editAdjustLogAuditStatus(step.getAdjustLogId(), step.getAdjustBatchNo(), "20");
            return;
        }

        securityPoolAdjustMapper.editAdjustLogAuditStatus(step.getAdjustLogId(), step.getAdjustBatchNo(), "20");
        for (IpAdjustLogBo log : logList) {
            if ("调入".equals(log.getAdjustMode())) {
                log.setAuditStatus("20");
                securityPoolAdjustMapper.addPoolStatus(log);
            } else if ("调出".equals(log.getAdjustMode())) {
                securityPoolAdjustMapper.softDeletePoolStatus(log.getSecurityCode(), log.getTargetPoolId());
            }
        }
    }

    /**
     * 根据当前步骤所属节点构建流程快照。
     */
    private FlowSnapshot buildFlowSnapshot(Long flowNodeId) {
        if (flowNodeId == null) {
            return null;
        }
        // 查询当前流程步骤对应的流程节点
        FlowNodeBo currentNode = flowMapper.queryFlowNodeById(flowNodeId);
        if (currentNode == null || currentNode.getVersionId() == null) {
            return null;
        }
        // 查询当前节点所属的流程版本和流程定义
        FlowVersionBo version = flowMapper.queryFlowVersionById(currentNode.getVersionId());
        FlowDefinitionBo definition = currentNode.getFlowId() != null ? flowMapper.queryFlowById(currentNode.getFlowId()) : null;

        // 查询当前流程版本下的节点、连线和连线条件
        List<FlowNodeBo> nodes = flowMapper.queryFlowNodeListByVersionId(currentNode.getVersionId());
        List<FlowEdgeBo> edges = flowMapper.queryFlowEdgeListByVersionId(currentNode.getVersionId());
        List<EdgeCondRuleBo> condRules = flowMapper.queryCondRuleListByVersionId(currentNode.getVersionId());
        Map<Long, FlowNodeBo> nodeMap = new HashMap<>();
        if (nodes != null) {
            // 构建节点 ID 到节点对象的索引，便于后续通过连线快速定位下一节点
            for (FlowNodeBo node : nodes) {
                nodeMap.put(node.getId(), node);
            }
        }

        // 查询并整理节点审批配置
        List<NodeApprovalConfigBo> configs = flowMapper.queryApprovalConfigListByVersionId(currentNode.getVersionId());
        Map<Long, NodeApprovalConfigBo> approvalConfigMap = new HashMap<>();
        if (configs != null) {
            // 构建节点 ID 到审批配置的索引
            for (NodeApprovalConfigBo config : configs) {
                approvalConfigMap.put(config.getNodeId(), config);
            }
        }

        // 查询并整理节点审批处理人配置
        List<NodeApprovalHandlerBo> handlers = flowMapper.queryApprovalHandlerListByVersionId(currentNode.getVersionId());
        Map<Long, List<NodeApprovalHandlerBo>> approvalHandlerMap = new HashMap<>();
        if (handlers != null) {
            // 按审批配置 ID 分组处理人，便于创建待处理步骤
            for (NodeApprovalHandlerBo handler : handlers) {
                List<NodeApprovalHandlerBo> list = approvalHandlerMap.get(handler.getApprovalConfigId());
                if (list == null) {
                    list = new ArrayList<>();
                    approvalHandlerMap.put(handler.getApprovalConfigId(), list);
                }
                list.add(handler);
            }
        }

        Map<Long, List<EdgeCondRuleBo>> condRuleMap = new HashMap<>();
        if (condRules != null) {
            // 按连线 ID 分组条件规则，便于按审批动作判断流转方向
            for (EdgeCondRuleBo rule : condRules) {
                List<EdgeCondRuleBo> list = condRuleMap.get(rule.getEdgeId());
                if (list == null) {
                    list = new ArrayList<>();
                    condRuleMap.put(rule.getEdgeId(), list);
                }
                list.add(rule);
            }
        }

        // 组装流程运行时快照，供本次审批流转复用
        return new FlowSnapshot(definition, version, nodeMap,
                edges != null ? edges : Collections.<FlowEdgeBo>emptyList(),
                approvalConfigMap, approvalHandlerMap, condRuleMap);
    }

    /**
     * 为审批节点创建待处理步骤。
     */
    private void createPendingSteps(IpAdjustStepBo currentStep, FlowNodeBo node, FlowSnapshot snapshot, Date now) {
        NodeApprovalConfigBo config = snapshot.getApprovalConfigMap().get(node.getId());
        // 判断是否需要回到发起人处理
        if (isModifyNode(node) || isInitiatorNode(node)) {
            // 创建发起人处理步骤
            createInitiatorPendingStep(currentStep, node, config, now);
            return;
        }

        // 解析审批节点配置的具体处理人
        List<HandlerTarget> handlers = resolveApprovalHandlers(config, snapshot);
        int sortOrder = node.getSortOrder() != null ? node.getSortOrder() : 1;
        if (handlers.isEmpty()) {
            // 无处理人配置时创建空处理人待处理步骤
            insertStepRecord(currentStep.getAdjustLogId(), currentStep.getAdjustBatchNo(), node, config, sortOrder,
                    "pending", null, null, null, null, now);
            return;
        }
        for (HandlerTarget handler : handlers) {
            // 按处理人创建待处理步骤
            insertStepRecord(currentStep.getAdjustLogId(), currentStep.getAdjustBatchNo(), node, config, sortOrder,
                    "pending", handler.handlerId, handler.handlerName, null, null, now);
        }
    }

    /**
     * 为发起人或修改节点创建待处理步骤。
     */
    private void createInitiatorPendingStep(IpAdjustStepBo currentStep, FlowNodeBo node,
                                            NodeApprovalConfigBo config, Date now) {
        // 查询同批次首条调库记录获取原发起人
        IpAdjustLogBo log = queryFirstAdjustLog(currentStep);
        String handlerId = log != null ? log.getAdjusterId() : null;
        String handlerName = log != null ? log.getAdjusterName() : null;
        int sortOrder = node.getSortOrder() != null ? node.getSortOrder() : 1;
        // 插入发起人待处理步骤
        insertStepRecord(currentStep.getAdjustLogId(), currentStep.getAdjustBatchNo(), node, config, sortOrder,
                "pending", handlerId, handlerName, null, null, now);
    }

    /**
     * 插入流程步骤记录。
     */
    private void insertStepRecord(Long adjustLogId, String adjustBatchNo, FlowNodeBo node,
                                  NodeApprovalConfigBo config, int sortOrder,
                                  String stepStatus, String handlerId, String handlerName,
                                  String processAction, String processComment, Date startTime) {
        IpAdjustStepBo nextStep = new IpAdjustStepBo();
        nextStep.setAdjustLogId(adjustLogId);
        nextStep.setAdjustBatchNo(adjustBatchNo);
        nextStep.setFlowNodeId(node.getId());
        nextStep.setNodeCode(node.getNodeId());
        nextStep.setNodeLabel(node.getLabel());
        nextStep.setNodeType(node.getNodeType());
        nextStep.setApprovalStrategy(config != null ? config.getApprovalStrategy() : null);
        nextStep.setSortOrder(sortOrder);
        nextStep.setStepStatus(stepStatus);
        nextStep.setHandlerId(handlerId);
        nextStep.setHandlerName(handlerName);
        nextStep.setProcessAction(processAction);
        nextStep.setProcessComment(processComment);
        nextStep.setStartTime(startTime);
        nextStep.setProcessTime("pending".equals(stepStatus) ? null : startTime);
        securityPoolAdjustMapper.addAdjustStep(nextStep);
    }

    /**
     * 将审批处理人配置解析为具体人员。
     */
    private List<HandlerTarget> resolveApprovalHandlers(NodeApprovalConfigBo config, FlowSnapshot snapshot) {
        if (config == null || config.getId() == null) {
            return Collections.emptyList();
        }
        List<NodeApprovalHandlerBo> handlers = snapshot.getApprovalHandlerMap().get(config.getId());
        if (handlers == null || handlers.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, HandlerTarget> resultMap = new LinkedHashMap<>();
        for (NodeApprovalHandlerBo handler : handlers) {
            if (handler == null || handler.getSubjectType() == null || handler.getSubjectId() == null) {
                continue;
            }
            if ("user".equals(handler.getSubjectType())) {
                String userId = String.valueOf(handler.getSubjectId());
                resultMap.put(userId, new HandlerTarget(userId, handler.getSubjectName()));
            } else if ("role".equals(handler.getSubjectType())) {
                List<Long> roleIds = new ArrayList<>();
                // 递归收集角色及其子角色
                collectDescendantRoleIds(handler.getSubjectId(), roleIds, flowMapper.queryRoleList());
                List<UserBo> users = flowMapper.queryUserList(roleIds, null);
                if (users == null) {
                    continue;
                }
                for (UserBo user : users) {
                    if (user == null || user.getId() == null) {
                        continue;
                    }
                    String userId = String.valueOf(user.getId());
                    resultMap.put(userId, new HandlerTarget(userId, user.getName()));
                }
            }
        }
        return new ArrayList<>(resultMap.values());
    }

    /**
     * 递归收集角色及其子角色 ID。
     */
    private void collectDescendantRoleIds(Long roleId, List<Long> roleIds, List<RoleBo> allRoles) {
        roleIds.add(roleId);
        if (allRoles == null) {
            return;
        }
        for (RoleBo role : allRoles) {
            if (roleId.equals(role.getParentId())) {
                // 继续收集子角色
                collectDescendantRoleIds(role.getId(), roleIds, allRoles);
            }
        }
    }

    /**
     * 查找审批通过主路径上的下一节点。
     */
    private FlowNodeBo findNextNode(FlowSnapshot snapshot, FlowNodeBo currentNode, FlowNodeBo prevNode, String processAction) {
        // 优先按流程连线查找下一节点
        FlowNodeBo nextNode = findNextNodeOnMainPath(snapshot, currentNode, prevNode, processAction);
        if (nextNode != null) {
            return nextNode;
        }
        throw new BizException("流程配置异常：节点[" + currentNode.getLabel() + "]缺少下一步连线");
    }

    /**
     * 按流程连线查找下一节点，优先排除驳回类连线。
     */
    private FlowNodeBo findNextNodeOnMainPath(FlowSnapshot snapshot, FlowNodeBo currentNode, FlowNodeBo prevNode, String processAction) {
        List<FlowEdgeBo> outEdges = new ArrayList<>();
        for (FlowEdgeBo edge : snapshot.getEdges()) {
            if (edge.getFromNodeId().equals(currentNode.getId())) {
                outEdges.add(edge);
            }
        }
        if (outEdges.isEmpty()) {
            return null;
        }

        if (prevNode != null) {
            List<FlowEdgeBo> filtered = new ArrayList<>();
            for (FlowEdgeBo edge : outEdges) {
                if (!edge.getToNodeId().equals(prevNode.getId())) {
                    filtered.add(edge);
                }
            }
            if (!filtered.isEmpty()) {
                outEdges = filtered;
            }
        }

        for (FlowEdgeBo edge : outEdges) {
            // 判断连线是否匹配审批动作
            if (matchesProcessAction(edge, snapshot, processAction)) {
                return snapshot.getNodeMap().get(edge.getToNodeId());
            }
        }
        for (FlowEdgeBo edge : outEdges) {
            // 排除驳回或不通过方向的连线
            if (!isNegativeEdge(edge, snapshot)) {
                return snapshot.getNodeMap().get(edge.getToNodeId());
            }
        }
        return snapshot.getNodeMap().get(outEdges.get(0).getToNodeId());
    }

    /**
     * 判断连线是否匹配当前审批动作。
     */
    private boolean matchesProcessAction(FlowEdgeBo edge, FlowSnapshot snapshot, String processAction) {
        List<EdgeCondRuleBo> rules = snapshot.getCondRuleMap().get(edge.getId());
        if (rules != null) {
            for (EdgeCondRuleBo rule : rules) {
                // 判断审核状态条件是否匹配审批动作
                if (matchesAuditStatusRule(rule, processAction)) {
                    return true;
                }
            }
        }
        String label = edge.getLabel();
        // 条件缺失时判断连线标签是否匹配审批动作
        return matchesActionText(label, processAction);
    }

    /**
     * 判断审核状态条件是否匹配当前审批动作。
     */
    private boolean matchesAuditStatusRule(EdgeCondRuleBo rule, String processAction) {
        if (rule == null || !"auditStatus".equals(rule.getFieldCode())) {
            return false;
        }
        String fieldVal = rule.getFieldVal();
        if ("reject".equals(processAction)) {
            return fieldVal != null && (fieldVal.contains("驳回") || fieldVal.contains("不通过"));
        }
        return fieldVal != null && fieldVal.contains("通过") && !fieldVal.contains("不通过") && !fieldVal.contains("驳回");
    }

    /**
     * 判断流程中是否存在审核状态条件规则。
     */
    private boolean hasAuditStatusRule(FlowSnapshot snapshot) {
        for (List<EdgeCondRuleBo> rules : snapshot.getCondRuleMap().values()) {
            if (rules == null) {
                continue;
            }
            for (EdgeCondRuleBo rule : rules) {
                if (rule != null && "auditStatus".equals(rule.getFieldCode())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 保留其他流程条件类型的扩展入口。
     */
    private boolean matchesReservedConditionRule(EdgeCondRuleBo rule, String processAction) {
        return false;
    }

    /**
     * 根据本次处理的流程节点和审批动作解析调库记录审核状态。
     *
     * @param node          本次处理的流程节点，不是下一步新创建节点
     * @param processAction 本次审批动作
     * @return 调库记录审核状态
     */
    private String resolveProcessingNodeAuditStatus(FlowNodeBo node, String processAction) {
        if ("reject".equals(processAction)) {
            // 复核驳回后进入待修改状态
            if (isReviewNode(node)) {
                return "11";
            }
            // 修改节点驳回后按撤回处理
            if (isModifyNode(node)) {
                return "99";
            }
            // 审批节点驳回后终止审批
            if (isApproveNode(node)) {
                return "21";
            }
            return "21";
        }
        // 发起或修改通过后回到已提交待审核状态
        if (isInitiatorNode(node) || isModifyNode(node)) {
            return "00";
        }
        // 复核通过后进入审核通过、待后续审批状态
        if (isReviewNode(node)) {
            return "10";
        }
        // 审批、自动审批通过记录最终审批通过状态
        if (isApproveNode(node) || isAutoApprovalNode(node)) {
            return "20";
        }
        return "20";
    }

    /**
     * 判断当前节点动作是否直接结束流程。
     */
    private boolean isTerminalByCurrentNode(FlowNodeBo node, String processAction) {
        // 修改驳回或审批驳回时流程直接结束
        return "reject".equals(processAction) && (isModifyNode(node) || isApproveNode(node));
    }

    /**
     * 判断是否为自动审批节点。
     */
    private boolean isAutoApprovalNode(FlowNodeBo node) {
        // 拼接节点关键字用于识别自动审批节点
        String text = buildNodeText(node);
        return text.contains("自动审批") || text.toLowerCase().contains("o32");
    }

    /**
     * 判断是否为发起节点。
     */
    private boolean isInitiatorNode(FlowNodeBo node) {
        // 拼接节点关键字用于识别发起节点
        String text = buildNodeText(node);
        return text.contains("发起");
    }

    /**
     * 判断是否为复核节点。
     */
    private boolean isReviewNode(FlowNodeBo node) {
        // 拼接节点关键字用于识别复核节点
        String text = buildNodeText(node);
        return text.contains("复核") || text.contains("复合");
    }

    /**
     * 判断是否为修改节点。
     */
    private boolean isModifyNode(FlowNodeBo node) {
        // 拼接节点关键字用于识别修改节点
        String text = buildNodeText(node);
        return text.contains("修改");
    }

    /**
     * 判断是否为审批节点。
     */
    private boolean isApproveNode(FlowNodeBo node) {
        // 拼接节点关键字用于识别审批节点
        String text = buildNodeText(node);
        // 排除 O32 自动审批节点
        return text.contains("审批") && !isAutoApprovalNode(node);
    }

    /**
     * 拼接节点关键字识别文本。
     */
    private String buildNodeText(FlowNodeBo node) {
        if (node == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (node.getLabel() != null) {
            builder.append(node.getLabel());
        }
        if (node.getNodeId() != null) {
            builder.append(' ').append(node.getNodeId());
        }
        if (node.getNodeType() != null) {
            builder.append(' ').append(node.getNodeType());
        }
        return builder.toString();
    }

    /**
     * 查询同批次第一条调库记录。
     */
    private IpAdjustLogBo queryFirstAdjustLog(IpAdjustStepBo step) {
        List<IpAdjustLogBo> logList = securityPoolAdjustMapper.queryAdjustLogListForAudit(
                step.getAdjustLogId(), step.getAdjustBatchNo());
        if (logList == null || logList.isEmpty()) {
            return null;
        }
        return logList.get(0);
    }

    /**
     * 查询当前调库记录审核状态。
     */
    private String queryCurrentAuditStatus(IpAdjustStepBo step) {
        // 查询同批次首条调库记录获取当前审核状态
        IpAdjustLogBo log = queryFirstAdjustLog(step);
        return log != null ? log.getAuditStatus() : "";
    }

    /**
     * 判断连线是否包含非审核状态条件。
     */
    private boolean hasReservedConditionRule(FlowEdgeBo edge, FlowSnapshot snapshot) {
        List<EdgeCondRuleBo> rules = snapshot.getCondRuleMap().get(edge.getId());
        if (rules == null) {
            return false;
        }
        for (EdgeCondRuleBo rule : rules) {
            // 预留非审核状态条件的匹配入口
            if (rule != null && !"auditStatus".equals(rule.getFieldCode()) && matchesReservedConditionRule(rule, "")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断连线文本是否匹配当前审批动作。
     */
    private boolean matchesActionText(String text, String processAction) {
        if (text == null) {
            return false;
        }
        if ("reject".equals(processAction)) {
            return text.contains("驳回") || text.contains("不通过");
        }
        return text.contains("通过") && !text.contains("不通过") && !text.contains("驳回");
    }

    /**
     * 判断连线是否属于驳回或不通过方向。
     */
    private boolean isNegativeEdge(FlowEdgeBo edge, FlowSnapshot snapshot) {
        String label = edge.getLabel();
        if (label != null && (label.contains("驳回") || label.contains("不通过"))) {
            return true;
        }
        List<EdgeCondRuleBo> rules = snapshot.getCondRuleMap().get(edge.getId());
        if (rules == null) {
            return false;
        }
        for (EdgeCondRuleBo rule : rules) {
            String fieldVal = rule != null ? rule.getFieldVal() : null;
            if (fieldVal != null && (fieldVal.contains("驳回") || fieldVal.contains("不通过"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建审批处理返回对象。
     */
    private SecurityPoolAdjustAuditDto buildAuditDto(IpAdjustStepBo step, String auditStatus,
                                                     boolean finished, boolean nextStepCreated, String message) {
        SecurityPoolAdjustAuditDto dto = new SecurityPoolAdjustAuditDto();
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
     * 判断字符串是否有有效内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 判断当前操作人是否为管理员。
     */
    private boolean isAdminOperator(SecurityPoolAdjustAuditReq req) {
        return req != null && ("1001".equals(req.getHandlerId()) || "管理员".equals(req.getHandlerName()));
    }

    /**
     * 管理员代办其他处理人的步骤时，在原处理意见后追加代办标识。
     */
    private String buildProcessComment(SecurityPoolAdjustAuditReq req, IpAdjustStepBo step) {
        String comment = req.getProcessComment() != null ? req.getProcessComment().trim() : "";
        // 判断是否为管理员代办其他处理人的步骤
        boolean adminOperateOther = isAdminOperator(req)
                && step != null
                && hasText(step.getHandlerId())
                && !step.getHandlerId().equals(req.getHandlerId());
        if (!adminOperateOther) {
            return comment;
        }
        return comment + "（由管理员操作）";
    }

    /**
     * 审批处理人。
     */
    private static class HandlerTarget {
        /** 处理人 ID */
        final String handlerId;

        /** 处理人名称 */
        final String handlerName;

        HandlerTarget(String handlerId, String handlerName) {
            this.handlerId = handlerId;
            this.handlerName = handlerName;
        }
    }

    /**
     * 流程推进结果。
     */
    private static class FlowAdvanceResult {
        /** 流程是否已结束 */
        boolean finished;

        /** 是否已创建下一步待处理记录 */
        boolean nextStepCreated;
    }
}
