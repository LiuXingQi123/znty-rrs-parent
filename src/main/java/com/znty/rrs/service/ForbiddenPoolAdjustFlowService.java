package com.znty.rrs.service;

import com.znty.rrs.common.enums.CategoryType;

import com.znty.rrs.common.enums.ReportType;

import com.znty.rrs.common.enums.HandlerType;

import com.znty.rrs.common.enums.NodeType;

import com.znty.rrs.common.enums.ProcessAction;
import com.znty.rrs.common.enums.StepStatus;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.ApprovalStrategy;
import com.znty.rrs.common.enums.AdjustMode;

import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AttachmentCategory;

import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.EdgeCondRuleBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.flow.FlowSnapshot;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.ReportInBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjustflow.SecurityPoolAdjustAuditDto;
import com.znty.rrs.entity.securitypooladjustflow.SecurityPoolAdjustAuditReq;
import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.bo.UserBo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 禁投池主体调整流程服务，负责调库审批步骤处理、流程推进和最终池状态落地。
 */
@Service
public class ForbiddenPoolAdjustFlowService {

    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";

    /** 证券池调库数据库操作 */
    @Resource
    private ForbiddenPoolAdjustMapper forbiddenPoolAdjustMapper;

    /** 流程定义数据库操作 */
    @Resource
    private FlowMapper flowMapper;

    /** 系统附件服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** 临时代码数据访问组件（O32 自动审批节点判断临时代码用） */
    @Resource
    private TempSecurityCodeMapper tempSecurityCodeMapper;

    /** 投资池服务，用于查询投资池全路径名称 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 报告库服务，用于审批通过后写入内部报告 */
    @Resource
    private ReportService reportService;

    /**
     * 提交调库审批处理意见，更新当前步骤并按审批流程推进。
     *
     * @param req 审批处理请求
     * @return 审批处理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolAdjustAuditDto submitAdjustAudit(SecurityPoolAdjustAuditReq req) {
        return submitAdjustAudit(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 提交调库审批处理意见，并在驳回待修改提交时保存附件变更。
     *
     * @param req 审批处理请求
     * @param files 本次提交的本地上传附件
     * @return 审批处理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolAdjustAuditDto submitAdjustAudit(SecurityPoolAdjustAuditReq req, List<MultipartFile> files) {
        // 校验审批提交参数
        validateAuditReq(req);

        // 查询并校验当前待处理步骤
        IpAdjustStepBo step = forbiddenPoolAdjustMapper.queryAdjustStepById(req.getStepId());
        // 管理员也是处理人时，优先定位到管理员自己的待处理步骤
        step = resolveActualProcessStep(req, step);
        // 校验当前步骤是否可处理
        validatePendingStep(req, step);
        // 校验发起人不能参与后续流程操作
        validateSubmitterCannotProcess(req, step);

        // 驳回待修改提交时保存调库记录附件变更
        applyAttachmentChangesForModifySubmit(req, step, files);

        // 处理当前步骤并按流程配置推进
        return processAdjustAudit(req, step);
    }

    /**
     * 解析实际处理步骤：管理员本人也在当前节点处理人列表中时，优先处理管理员自己的步骤。
     */
    private IpAdjustStepBo resolveActualProcessStep(SecurityPoolAdjustAuditReq req, IpAdjustStepBo step) {
        // 判断字符串是否有有效内容；判断当前操作人是否为管理员
        if (step == null || !isAdminOperator(req) || !hasText(req.getHandlerId())) {
            return step;
        }
        if (req.getHandlerId().equals(step.getHandlerId())) {
            return step;
        }
        // 查询当前节点中管理员自己的待处理步骤
        IpAdjustStepBo adminStep = forbiddenPoolAdjustMapper.queryPendingStepByHandler(
                step.getAdjustLogId(), step.getAdjustBatchNo(), step.getFlowNodeId(), req.getHandlerId());
        return adminStep != null ? adminStep : step;
    }

    /**
     * 校验审批提交参数。
     */
    private void validateAuditReq(SecurityPoolAdjustAuditReq req) {
        if (req.getStepId() == null) {
            throw new BizException("流程步骤 ID 不能为空");
        }
        if (!ProcessAction.APPROVE.getCode().equals(req.getProcessAction()) && !ProcessAction.REJECT.getCode().equals(req.getProcessAction())) {
            throw new BizException("审批动作不合法");
        }
        // 判断驳回意见是否已填写
        if (ProcessAction.REJECT.getCode().equals(req.getProcessAction()) && !hasText(req.getProcessComment())) {
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
        if (!StepStatus.PENDING.getCode().equals(step.getStepStatus())) {
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
     * 校验发起人不能参与后续流程操作，管理员除外。
     */
    private void validateSubmitterCannotProcess(SecurityPoolAdjustAuditReq req, IpAdjustStepBo step) {
        if (isAdminOperator(req) || step == null || !hasText(req.getHandlerId())) {
            return;
        }
        if (isSubmitSemanticStep(step)) {
            return;
        }
        // 查询当前批次调库记录，识别原始发起人
        List<IpAdjustLogBo> adjustLogList = forbiddenPoolAdjustMapper.queryAdjustLogListForAudit(
                step.getAdjustLogId(), step.getAdjustBatchNo());
        if (adjustLogList.isEmpty()) {
            return;
        }
        for (IpAdjustLogBo log : adjustLogList) {
            if (log != null && req.getHandlerId().equals(log.getAdjusterId())) {
                throw new BizException("发起人不能参与后续流程操作");
            }
        }
    }

    /**
     * 驳回待修改节点提交时保存附件新增、报告附件复制和删除。
     */
    private void applyAttachmentChangesForModifySubmit(SecurityPoolAdjustAuditReq req, IpAdjustStepBo step,
                                                       List<MultipartFile> files) {
        if (req.getAttachmentChanges() == null || req.getAttachmentChanges().isEmpty()) {
            return;
        }
        if (!ProcessAction.APPROVE.getCode().equals(req.getProcessAction()) || !isModifyStep(step)) {
            throw new BizException("仅驳回待修改提交时允许修改附件");
        }
        List<IpAdjustLogBo> logList = forbiddenPoolAdjustMapper.queryAdjustLogListForAudit(
                step.getAdjustLogId(), step.getAdjustBatchNo());
        Set<Long> allowedLogIds = new HashSet<>();
        for (IpAdjustLogBo log : logList) {
            if (log != null && log.getId() != null) {
                allowedLogIds.add(log.getId());
            }
        }
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getHandlerId());
        for (SecurityPoolAdjustAuditReq.AttachmentChange change : req.getAttachmentChanges()) {
            if (change == null || change.getAdjustLogId() == null) {
                throw new BizException("附件变更调库记录 ID 不能为空");
            }
            if (!allowedLogIds.contains(change.getAdjustLogId())) {
                throw new BizException("附件变更不属于当前调库批次，调库记录 ID：" + change.getAdjustLogId());
            }
            // 删除已移除的调库记录附件
            sysAttachmentService.deleteAdjustLogAttachments(change.getAdjustLogId(), change.getDeleteAttachmentIds());
            // 绑定本地上传的信评报告附件
            sysAttachmentService.bindAttachments(change.getAdjustLogId(), change.getCreditReportFileIndexes(),
                    AttachmentCategory.CREDIT_REPORT_HAND.getCode(), submissionFiles);
            // 绑定本地上传的其他材料附件
            sysAttachmentService.bindAttachments(change.getAdjustLogId(), change.getMaterialFileIndexes(),
                    AttachmentCategory.MATERIAL_HAND.getCode(), submissionFiles);
            // 复制报告库附件为信评报告附件
            sysAttachmentService.copyReportAttachments(change.getAdjustLogId(),
                    change.getCreditReportSourceAttachmentIds(),
                    AttachmentPurpose.CREDIT_REPORT.getCode(), req.getHandlerId());
            // 复制报告库附件为其他材料附件
            sysAttachmentService.copyReportAttachments(change.getAdjustLogId(),
                    change.getMaterialSourceAttachmentIds(),
                    AttachmentPurpose.MATERIAL.getCode(), req.getHandlerId());
        }
    }

    /**
     * 处理审批动作并推进流程。
     */
    private SecurityPoolAdjustAuditDto processAdjustAudit(SecurityPoolAdjustAuditReq req, IpAdjustStepBo step) {
        // 构建实际写入的处理意见
        String processComment = buildProcessComment(req, step);
        // 根据本次处理节点语义解析步骤整体结果
        String stepStatus = resolveStepStatusForProcess(step, req.getProcessAction());
        // 根据本次处理节点语义解析个人处理动作
        String processAction = resolveProcessActionForStore(req.getProcessAction(), stepStatus);
        int updated = forbiddenPoolAdjustMapper.editAdjustStepProcess(
                step.getId(), stepStatus, processAction, processComment);
        if (updated == 0) {
            throw new BizException("当前流程步骤已处理，请刷新后重试");
        }

        // 判断当前审批节点是否已完成
        boolean nodeCompleted = completeCurrentApprovalNodeIfNeeded(step, req.getProcessAction(), stepStatus);
        if (!nodeCompleted) {
            // 查询当前调库记录审核状态
            String currentAuditStatus = queryCurrentAuditStatus(step);
            // 构建审批处理返回对象
            return buildAuditDto(step, currentAuditStatus, false, false, "当前会签节点仍有待处理人员");
        }

        // 查询当前调库记录审核状态
        String currentAuditStatus = queryCurrentAuditStatus(step);
        // 构建当前流程版本快照
        FlowSnapshot snapshot = buildFlowSnapshot(step.getFlowNodeId());
        FlowNodeBo processingNode = snapshot != null ? snapshot.getNodeMap().get(step.getFlowNodeId()) : null;

        // 推进到下一可处理节点
        FlowAdvanceResult advanceResult = advanceToNextAvailableStep(step, snapshot, processingNode, req.getProcessAction());
        if (ProcessAction.REJECT.getCode().equals(req.getProcessAction())) {
            // 根据终止前状态解析驳回终态
            String auditStatus = advanceResult.finished ? resolveTerminalRejectAuditStatus(currentAuditStatus)
                    : AuditStatus.REJECT_MODIFY.getCode();
            forbiddenPoolAdjustMapper.editAdjustLogAuditStatus(step.getAdjustLogId(), step.getAdjustBatchNo(), auditStatus);
            // 构建驳回处理返回对象
            return buildAuditDto(step, auditStatus, advanceResult.finished, advanceResult.nextStepCreated,
                    advanceResult.finished ? "审批流程已结束" : "审批已驳回，已流转到修改步骤");
        }

        if (advanceResult.finished) {
            // 落地同批次调库结果
            finishAdjustBatch(step);
            // 构建审批处理返回对象
            return buildAuditDto(step, AuditStatus.APPROVED.getCode(), true, advanceResult.nextStepCreated, "审批已通过，调库结果已生效");
        }

        forbiddenPoolAdjustMapper.editAdjustLogAuditStatus(step.getAdjustLogId(), step.getAdjustBatchNo(), AuditStatus.SUBMITTED.getCode());
        // 构建审批处理返回对象
        return buildAuditDto(step, AuditStatus.SUBMITTED.getCode(), false, advanceResult.nextStepCreated, "审批已处理，已流转到下一步骤");
    }

    /**
     * 根据审批策略判断当前节点是否已经完成。
     */
    private boolean completeCurrentApprovalNodeIfNeeded(IpAdjustStepBo step, String processAction, String stepStatus) {
        if (ApprovalStrategy.ALL.getCode().equals(step.getApprovalStrategy()) && ProcessAction.APPROVE.getCode().equals(processAction)) {
            int pendingCount = forbiddenPoolAdjustMapper.queryPendingStepCountByNode(
                    step.getAdjustLogId(), step.getAdjustBatchNo(), step.getFlowNodeId());
            return pendingCount == 0;
        }

        // 非会签通过场景：抢占/发起人策略、未配置策略，或任意策略下的驳回动作，单人处理即完成当前节点
        forbiddenPoolAdjustMapper.editOtherPendingStepSkipped(
                step.getId(), step.getAdjustLogId(), step.getAdjustBatchNo(), step.getFlowNodeId(), stepStatus);
        return true;
    }

    /**
     * 根据处理节点语义解析步骤整体结果，修改/发起节点的正向动作展示为提交。
     */
    private String resolveStepStatusForProcess(IpAdjustStepBo step, String processAction) {
        // 判断当前步骤是否是提交语义节点
        if (ProcessAction.APPROVE.getCode().equals(processAction) && isSubmitSemanticStep(step)) {
            return ProcessAction.SUBMIT.getCode();
        }
        return processAction;
    }

    /**
     * 根据步骤整体结果解析实际存储的个人处理动作。
     */
    private String resolveProcessActionForStore(String processAction, String stepStatus) {
        if (ProcessAction.SUBMIT.getCode().equals(stepStatus)) {
            return ProcessAction.SUBMIT.getCode();
        }
        return processAction;
    }

    /**
     * 判断当前步骤是否是提交语义节点。
     */
    private boolean isSubmitSemanticStep(IpAdjustStepBo step) {
        // 提交语义步骤通过 approval_strategy=initiator 标记，不再依赖中文 label
        return step != null && ApprovalStrategy.INITIATOR.getCode().equals(step.getApprovalStrategy());
    }

    /**
     * 判断当前步骤是否为驳回修改节点。
     */
    private boolean isModifyStep(IpAdjustStepBo step) {
        // 修改节点同为 initiator 策略，首次 SUBMIT 不会走 APPROVE 分支，合并判断无副作用
        return step != null && ApprovalStrategy.INITIATOR.getCode().equals(step.getApprovalStrategy());
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

            // 判断是否为自动处理节点
            if (isAutoApprovalNode(nextNode, config)) {
                // O32 自动审批节点：临时代码转人工，非临时代码系统代审
                if (isTemporaryCode(step)) {
                    // 临时代码：完全当作审批节点，为每个处理人建 PENDING 步骤等人工点击
                    createPendingSteps(step, nextNode, snapshot, now);
                    result.nextStepCreated = true;
                    result.finished = false;
                    return result;
                }
                // 非临时代码：和审批节点一样为每个处理人建步骤，但系统自动处理（AUTO_PROCESS）
                createAutoProcessSteps(step, nextNode, snapshot, now);
                // 查找审批通过主路径上的下一节点
                FlowNodeBo afterNode = findNextNode(snapshot, nextNode, prevNode, processAction);
                prevNode = nextNode;
                nextNode = afterNode;
                continue;
            }

            if (NodeType.APPROVAL.getCode().equals(nextNode.getNodeType())) {
                // 为下一审批节点创建待处理步骤
                createPendingSteps(step, nextNode, snapshot, now);
                result.nextStepCreated = true;
                result.finished = false;
                return result;
            }

            // 插入自动完成节点步骤
            insertStepRecord(step.getAdjustLogId(), step.getAdjustBatchNo(), nextNode, config, sortOrder,
                    ProcessAction.AUTO_PROCESS.getCode(), null, null, ProcessAction.AUTO_PROCESS.getCode(), null, now);

            if (NodeType.END.getCode().equals(nextNode.getNodeType())) {
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
     * 流程最终通过后，更新同批调库日志并落地当前池状态。
     */
    private void finishAdjustBatch(IpAdjustStepBo step) {
        List<IpAdjustLogBo> logList = forbiddenPoolAdjustMapper.queryAdjustLogListForAudit(
                step.getAdjustLogId(), step.getAdjustBatchNo());
        if (logList.isEmpty()) {
            forbiddenPoolAdjustMapper.editAdjustLogAuditStatus(step.getAdjustLogId(), step.getAdjustBatchNo(), AuditStatus.APPROVED.getCode());
            return;
        }

        forbiddenPoolAdjustMapper.editAdjustLogAuditStatus(step.getAdjustLogId(), step.getAdjustBatchNo(), AuditStatus.APPROVED.getCode());
        for (IpAdjustLogBo log : logList) {
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                log.setAuditStatus(AuditStatus.APPROVED.getCode());
                log.setAdjustLogId(log.getId());
                forbiddenPoolAdjustMapper.addPoolStatus(log);
            } else if (AdjustMode.OUT.getCode().equals(log.getAdjustMode())) {
                forbiddenPoolAdjustMapper.deletePoolStatusSoft(log.getSecurityCode(), log.getTargetPoolId());
            }
            // 主体调整生效后同步调整旗下全部债券
            syncCompanyBonds(log);
        }
        // 审批通过结束后，将手工上传的信评报告沉淀为内部报告
        generateInternalReportsOnFinish(logList);
    }

    /**
     * 主体调整生效后，同步调整旗下全部债券并记录自动调整日志。
     */
    private void syncCompanyBonds(IpAdjustLogBo companyLog) {
        String categoryType = forbiddenPoolAdjustMapper.queryCategoryTypeBySecurityType(companyLog.getSecurityType());
        if (!CategoryType.COMPANY.getCode().equals(categoryType)) {
            return;
        }
        // 查询主体旗下全部债券
        List<SecurityInfoBo> bonds = forbiddenPoolAdjustMapper.queryCompanyBondForAutoList(
                companyLog.getSecurityCode());
        for (SecurityInfoBo bond : bonds) {
            List<Long> currentPoolIds = forbiddenPoolAdjustMapper.querySecurityCurrentPoolIdList(bond.getWindCode());
            boolean currentlyInPool = currentPoolIds.contains(companyLog.getTargetPoolId());
            boolean inbound = AdjustMode.IN.getCode().equals(companyLog.getAdjustMode());
            if ((inbound && currentlyInPool) || (!inbound && !currentlyInPool)) {
                continue;
            }
            // 构建旗下债券自动调整日志
            IpAdjustLogBo autoLog = buildCompanyBondAutoLog(companyLog, bond);
            forbiddenPoolAdjustMapper.addAdjustLog(autoLog);
            if (inbound) {
                autoLog.setAdjustLogId(autoLog.getId());
                forbiddenPoolAdjustMapper.addPoolStatus(autoLog);
            } else {
                forbiddenPoolAdjustMapper.deletePoolStatusSoft(bond.getWindCode(), companyLog.getTargetPoolId());
            }
        }
    }

    /**
     * 构建主体旗下债券自动调整日志。
     */
    private IpAdjustLogBo buildCompanyBondAutoLog(IpAdjustLogBo companyLog, SecurityInfoBo bond) {
        IpAdjustLogBo autoLog = new IpAdjustLogBo();
        autoLog.setSecurityCode(bond.getWindCode());
        autoLog.setSecurityShortName(bond.getShortName());
        autoLog.setSecurityType(bond.getSecurityType());
        autoLog.setAdjustType("自动调整");
        autoLog.setAdjustMode(companyLog.getAdjustMode());
        autoLog.setAdjustBatchNo(companyLog.getAdjustBatchNo());
        autoLog.setTargetPoolId(companyLog.getTargetPoolId());
        autoLog.setTargetPoolName(companyLog.getTargetPoolName());
        autoLog.setPoolType(companyLog.getPoolType());
        autoLog.setAuditStatus(AuditStatus.APPROVED.getCode());
        autoLog.setAdjusterId(companyLog.getAdjusterId());
        autoLog.setAdjusterName(companyLog.getAdjusterName());
        autoLog.setAdjustReason("主体“" + companyLog.getSecurityShortName() + "”"
                + companyLog.getAdjustMode() + "，旗下债券自动同步调整");
        autoLog.setAdjustAdvice(companyLog.getAdjustAdvice());
        return autoLog;
    }

    /**
     * 审批通过结束后，逐条调库记录将手工上传的信评报告附件沉淀为内部报告库记录。
     * 每条调库记录生成 1 条 rrs_report_in，其下所有手工上传信评报告附件复制为该报告的 report_in 附件。
     * 无手工上传信评报告附件的调库记录跳过。
     *
     * @param logList 同批次调库记录列表
     */
    private void generateInternalReportsOnFinish(List<IpAdjustLogBo> logList) {
        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 整批只查一次投资池全路径映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (IpAdjustLogBo log : logList) {
            // 查询该调库记录下手工上传的信评报告附件
            List<SysAttachmentBo> handAttachments = sysAttachmentService.queryHandCreditReportAttachments(log.getId());
            if (handAttachments == null || handAttachments.isEmpty()) {
                continue;
            }
            // 查询证券基础信息，取证券全称与主体编码
            SecurityInfoBo securityInfo = forbiddenPoolAdjustMapper.querySecurityBoByCode(log.getSecurityCode());
            // 查询证券所属大类，用于映射报告类型
            String categoryType = forbiddenPoolAdjustMapper.queryCategoryTypeBySecurityType(log.getSecurityType());
            // 组装报告标题：证券全称 + 调入/调出 + 投资池全路径名称 + 报告
            String securityFullName = securityInfo != null && securityInfo.getFullName() != null
                    ? securityInfo.getFullName() : log.getSecurityShortName();
            String poolFullName = poolFullNameMap.get(log.getTargetPoolId());
            if (poolFullName == null || poolFullName.isEmpty()) {
                poolFullName = log.getTargetPoolName();
            }
            String reportTitle = securityFullName + log.getAdjustMode() + poolFullName + "报告";
            // 构建内部报告记录
            ReportInBo reportInBo = new ReportInBo();
            reportInBo.setAuthorName(log.getAdjusterName());
            reportInBo.setReportTitle(reportTitle);
            reportInBo.setReportType(resolveReportType(categoryType, log.getAdjustMode()));
            reportInBo.setSecurityCode(log.getSecurityCode());
            reportInBo.setCompanyCode(CategoryType.COMPANY.getCode().equals(categoryType)
                    ? log.getSecurityCode() : (securityInfo != null ? securityInfo.getIssuerCode() : null));
            reportInBo.setSecurityType(resolveReportSecurityType(categoryType));
            reportInBo.setDataSource("uploaded");
            // 写入内部报告并回填主键 ID
            Long reportId = reportService.addInReport(reportInBo);
            // 将手工上传信评报告附件复制为该内部报告的附件
            sysAttachmentService.bindReportFileAttachments(reportId, handAttachments);
        }
    }

    /**
     * 根据证券大类与调入/调出方向映射内部报告类型。
     *
     * @param categoryType 证券大类（bond/fund/stock/company 等）
     * @param adjustMode   调整方向（调入/调出）
     */
    private String resolveReportType(String categoryType, String adjustMode) {
        boolean outbound = AdjustMode.OUT.getCode().equals(adjustMode);
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            return outbound ? ReportType.BOND_OUT_REPORT.getCode() : ReportType.BOND_IN_REPORT.getCode();
        }
        if (CategoryType.FUND.getCode().equals(categoryType)) {
            return outbound ? ReportType.FUND_OUT_REPORT.getCode() : ReportType.FUND_IN_REPORT.getCode();
        }
        if (CategoryType.STOCK.getCode().equals(categoryType)) {
            return outbound ? ReportType.STOCK_OUT_REPORT.getCode() : ReportType.STOCK_IN_REPORT.getCode();
        }
        return ReportType.OTHER_REPORT.getCode();
    }

    /**
     * 根据证券大类映射内部报告证券类型，未匹配归为其他。
     *
     * @param categoryType 证券大类（bond/fund/stock/company 等）
     */
    private String resolveReportSecurityType(String categoryType) {
        if (CategoryType.BOND.getCode().equals(categoryType) || CategoryType.FUND.getCode().equals(categoryType)
                || CategoryType.STOCK.getCode().equals(categoryType) || CategoryType.COMPANY.getCode().equals(categoryType)) {
            return categoryType;
        }
        return "other";
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
        // 构建节点 ID 到节点对象的索引，便于后续通过连线快速定位下一节点
        for (FlowNodeBo node : nodes) {
            nodeMap.put(node.getId(), node);
        }

        // 查询并整理节点审批配置
        List<NodeApprovalConfigBo> configs = flowMapper.queryApprovalConfigListByVersionId(currentNode.getVersionId());
        Map<Long, NodeApprovalConfigBo> approvalConfigMap = new HashMap<>();
        // 构建节点 ID 到审批配置的索引
        for (NodeApprovalConfigBo config : configs) {
            approvalConfigMap.put(config.getNodeId(), config);
        }

        // 查询并整理节点审批处理人配置
        List<NodeApprovalHandlerBo> handlers = flowMapper.queryApprovalHandlerListByVersionId(currentNode.getVersionId());
        Map<Long, List<NodeApprovalHandlerBo>> approvalHandlerMap = new HashMap<>();
        // 按审批配置 ID 分组处理人，便于创建待处理步骤
        for (NodeApprovalHandlerBo handler : handlers) {
            List<NodeApprovalHandlerBo> list = approvalHandlerMap.get(handler.getApprovalConfigId());
            if (list == null) {
                list = new ArrayList<>();
                approvalHandlerMap.put(handler.getApprovalConfigId(), list);
            }
            list.add(handler);
        }

        Map<Long, List<EdgeCondRuleBo>> condRuleMap = new HashMap<>();
        // 按连线 ID 分组条件规则，便于按审批动作判断流转方向
        for (EdgeCondRuleBo rule : condRules) {
            List<EdgeCondRuleBo> list = condRuleMap.get(rule.getEdgeId());
            if (list == null) {
                list = new ArrayList<>();
                condRuleMap.put(rule.getEdgeId(), list);
            }
            list.add(rule);
        }

        // 组装流程运行时快照，供本次审批流转复用
        return new FlowSnapshot(definition, version, nodeMap,
                edges, approvalConfigMap, approvalHandlerMap, condRuleMap);
    }

    /**
     * 为审批节点创建待处理步骤。
     */
    private void createPendingSteps(IpAdjustStepBo currentStep, FlowNodeBo node, FlowSnapshot snapshot, Date now) {
        NodeApprovalConfigBo config = snapshot.getApprovalConfigMap().get(node.getId());
        // 判断是否需要回到发起人处理
        if (isModifyNode(node, config) || isInitiatorNode(node, config)) {
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
                    StepStatus.PENDING.getCode(), null, null, null, null, now);
            return;
        }
        for (HandlerTarget handler : handlers) {
            // 按处理人创建待处理步骤
            insertStepRecord(currentStep.getAdjustLogId(), currentStep.getAdjustBatchNo(), node, config, sortOrder,
                    StepStatus.PENDING.getCode(), handler.handlerId, handler.handlerName, null, null, now);
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
                StepStatus.PENDING.getCode(), handlerId, handlerName, null, null, now);
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
        nextStep.setProcessTime(StepStatus.PENDING.getCode().equals(stepStatus) ? null : startTime);
        forbiddenPoolAdjustMapper.addAdjustStep(nextStep);
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
            if (handler == null || handler.getHandlerType() == null || handler.getHandlerId() == null) {
                continue;
            }
            if (HandlerType.USER.getCode().equals(handler.getHandlerType())) {
                String userId = String.valueOf(handler.getHandlerId());
                resultMap.put(userId, new HandlerTarget(userId, handler.getHandlerName()));
            } else if (HandlerType.ROLE.getCode().equals(handler.getHandlerType())) {
                List<Long> roleIds = new ArrayList<>();
                // 递归收集角色及其子角色
                collectDescendantRoleIds(handler.getHandlerId(), roleIds, flowMapper.queryRoleList());
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

        if (outEdges.size() == 1) {
            return snapshot.getNodeMap().get(outEdges.get(0).getToNodeId());
        }

        for (FlowEdgeBo edge : outEdges) {
            // 预留条件路由扩展入口
            if (matchesConditionRoute(edge, snapshot, processAction)) {
                return snapshot.getNodeMap().get(edge.getToNodeId());
            }
            // 根据审批动作匹配流转连线
            if (matchesActionRoute(edge, processAction)) {
                return snapshot.getNodeMap().get(edge.getToNodeId());
            }
        }
        throw new BizException("流程配置异常：节点[" + currentNode.getLabel() + "]缺少匹配审批动作的流转连线");
    }

    /**
     * 判断条件路由是否匹配当前审批动作。
     */
    private boolean matchesConditionRoute(FlowEdgeBo edge, FlowSnapshot snapshot, String processAction) {
        // 后续如启用条件引擎，在这里统一支持 condLogic 的 AND/OR 和多字段条件判断
        return false;
    }

    /**
     * 判断连线动作是否匹配当前审批动作。
     */
    private boolean matchesActionRoute(FlowEdgeBo edge, String processAction) {
        if (edge == null || edge.getRouteAction() == null || processAction == null) {
            return false;
        }
        return processAction.equals(edge.getRouteAction());
    }

    /**
     * 根据终止前状态解析驳回终态。
     */
    private String resolveTerminalRejectAuditStatus(String currentAuditStatus) {
        if (AuditStatus.REJECT_MODIFY.getCode().equals(currentAuditStatus)) {
            return AuditStatus.REVOKED.getCode();
        }
        return AuditStatus.REJECTED.getCode();
    }

    /**
     * 判断是否为自动审批节点。
     */
    private boolean isAutoApprovalNode(FlowNodeBo node, NodeApprovalConfigBo config) {
        // O32 节点通过审批策略 approval_strategy=o32 标记（节点类型为 approval）
        return config != null && ApprovalStrategy.O32.getCode().equals(config.getApprovalStrategy());
    }

    /**
     * 为 O32 节点的每个处理人建自动处理步骤（非临时代码系统代审，每个处理人都有步骤记录可追溯）。
     */
    private void createAutoProcessSteps(IpAdjustStepBo currentStep, FlowNodeBo node, FlowSnapshot snapshot, Date now) {
        NodeApprovalConfigBo config = snapshot.getApprovalConfigMap().get(node.getId());
        List<HandlerTarget> handlers = resolveApprovalHandlers(config, snapshot);
        int sortOrder = node.getSortOrder() != null ? node.getSortOrder() : 1;
        if (handlers.isEmpty()) {
            insertStepRecord(currentStep.getAdjustLogId(), currentStep.getAdjustBatchNo(), node, config, sortOrder,
                    StepStatus.AUTO_PROCESS.getCode(), null, null,
                    ProcessAction.AUTO_PROCESS.getCode(), "系统自动审批通过", now);
            return;
        }
        for (HandlerTarget handler : handlers) {
            insertStepRecord(currentStep.getAdjustLogId(), currentStep.getAdjustBatchNo(), node, config, sortOrder,
                    StepStatus.AUTO_PROCESS.getCode(), handler.handlerId, handler.handlerName,
                    ProcessAction.AUTO_PROCESS.getCode(), "系统自动审批通过", now);
        }
    }

    /**
     * 判断当前调库的证券是否临时代码。
     */
    private boolean isTemporaryCode(IpAdjustStepBo step) {
        IpAdjustLogBo log = queryFirstAdjustLog(step);
        if (log == null || log.getSecurityCode() == null) {
            return false;
        }
        return tempSecurityCodeMapper.queryTemporaryCodeCountBySecurityCode(log.getSecurityCode()) > 0;
    }

    /**
     * 判断是否为发起节点。
     */
    private boolean isInitiatorNode(FlowNodeBo node, NodeApprovalConfigBo config) {
        // 发起人节点通过 approval_strategy=initiator 标记，不再依赖中文 label
        return config != null && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy());
    }

    /**
     * 判断是否为修改节点。
     */
    private boolean isModifyNode(FlowNodeBo node, NodeApprovalConfigBo config) {
        // 修改节点同为 initiator 策略，与发起人节点合并判断（createPendingSteps 中 || 使用）
        return config != null && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy());
    }

    /**
     * 查询同批次第一条调库记录。
     */
    private IpAdjustLogBo queryFirstAdjustLog(IpAdjustStepBo step) {
        List<IpAdjustLogBo> logList = forbiddenPoolAdjustMapper.queryAdjustLogListForAudit(
                step.getAdjustLogId(), step.getAdjustBatchNo());
        if (logList.isEmpty()) {
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
        return req != null && ADMIN_USER_ID.equals(req.getHandlerId());
    }

    /**
     * 管理员代办其他处理人的步骤时，在原处理意见后追加代办标识。
     */
    private String buildProcessComment(SecurityPoolAdjustAuditReq req, IpAdjustStepBo step) {
        String comment = req.getProcessComment() != null ? req.getProcessComment().trim() : "";
        // 判断是否为管理员代办其他处理人的步骤
        boolean adminOperateOther = isAdminOperator(req)
                && step != null
                // 判断字符串是否有有效内容
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
