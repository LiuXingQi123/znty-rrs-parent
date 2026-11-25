package com.znty.rrs.service;

import com.znty.rrs.common.enums.FlowStatus;
import com.znty.rrs.common.enums.NodeType;
import com.znty.rrs.common.enums.EventType;
import com.znty.rrs.common.enums.ApprovalStrategy;
import com.znty.rrs.common.enums.ProcessAction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.entity.bo.EdgeCondRuleBo;
import com.znty.rrs.entity.bo.EdgeCondRuleEvtBo;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowDefinitionEvtBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowEdgeEvtBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowNodeEvtBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.FlowVersionEvtBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigEvtBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerEvtBo;
import com.znty.rrs.entity.bo.NodeAutoConfigBo;
import com.znty.rrs.entity.bo.NodeAutoConfigEvtBo;
import com.znty.rrs.entity.bo.NodeConditionConfigBo;
import com.znty.rrs.entity.bo.NodeConditionConfigEvtBo;
import com.znty.rrs.entity.bo.NodeNotifyConfigBo;
import com.znty.rrs.entity.bo.NodeNotifyConfigEvtBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.common.RoleDto;
import com.znty.rrs.entity.common.UserDto;
import com.znty.rrs.entity.flow.AutoTaskItemDto;
import com.znty.rrs.entity.flow.CanvasEdgeDto;
import com.znty.rrs.entity.flow.CanvasNodeDto;
import com.znty.rrs.entity.flow.CondRuleItemDto;
import com.znty.rrs.entity.flow.DesignerReq;
import com.znty.rrs.entity.flow.DictDto;
import com.znty.rrs.entity.flow.FlowDto;
import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.flow.FlowReq;
import com.znty.rrs.entity.flow.VersionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 维护审批流程定义、版本和设计器配置
 *
 * 管理流程草稿、发布和停用，保存画布时把节点、连线和处理人等配置整理到对应版本
 * 为设计器提供角色、人员、自动任务和条件字段选项，并保留流程配置的操作记录
 */
@Service
public class FlowService {

    /** 流程定义数据访问组件 */
    @Resource
    private FlowMapper flowMapper;

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(FlowService.class);

    /** JSON 序列化组件 */
    private static final ObjectMapper om = new ObjectMapper();

    // ==================== 流程列表 ====================

    /**
     * 查询流程分页
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将本页记录和查询总条数一起返回
     *
     * @param req 审批流程的查询或维护请求
     * @return 审批流程信息及分页信息
     */
    public PageResult<FlowDto> queryFlowPage(FlowReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<FlowDefinitionBo> entities = flowMapper.queryFlowPage(
                req.getKeyword(), req.getStatus(), req.getCategory());
        long total = new PageInfo<>(entities).getTotal();

        List<FlowDto> records = entities.stream().map(e -> {
            FlowDto d = new FlowDto();
            d.setId(e.getId());
            d.setName(e.getName());
            d.setFlowKey(e.getFlowKey());
            d.setCategory(e.getCategory());
            d.setDescription(e.getDescription());
            d.setRemark(e.getRemark());
            d.setCurrentVer(e.getCurrentVer());
            d.setStatus(e.getStatus());
            d.setHasPublishedVersion(e.getHasPublishedVersion());
            d.setCreatedBy(e.getCreatedBy());
            d.setCrteTime(e.getCrteTime());
            d.setUpdtTime(e.getUpdtTime());
            return d;
        }).collect(Collectors.toList());

        return new PageResult<>(records, total, req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询流程列表（不分页，用于下拉选项），仅返回 id/name/key/description
     *
     * 执行顺序：
     * 1. 按关键字、状态和分类查询流程
     * 2. 把查询结果转成只含 ID、Key、名称和描述的选项
     *
     * @param req 审批流程的查询或维护请求
     * @return 流程选项列表
     */
    public List<FlowOptionDto> queryFlowList(FlowReq req) {
        List<FlowDefinitionBo> entities = flowMapper.queryFlowList(
                req.getKeyword(), req.getStatus(), req.getCategory());
        return entities.stream().map(e -> {
            FlowOptionDto d = new FlowOptionDto();
            d.setFlowId(e.getId());
            d.setFlowKey(e.getFlowKey());
            d.setFlowName(e.getName());
            d.setDescription(e.getDescription());
            return d;
        }).collect(Collectors.toList());
    }

    // ==================== 新建流程 ====================

    /**
     * 新建流程定义，同时创建初始草稿版本（verNum=1）
     *
     * 执行顺序：
     * 1. 校验操作人、流程名称和流程 Key，确认 Key 未被使用
     * 2. 保存草稿状态的流程定义并记录新增审计
     * 3. 新建第一个草稿版本并记录版本审计，返回流程和版本信息
     *
     * @param req 新建流程请求，包含流程名称、Key 和操作人
     * @return 审批流程信息
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto addFlow(FlowReq req) {
        // 1. 校验操作人、流程名称和流程 Key，确认 Key 未被使用
        Long operatorId = req.getOperatorId();
        if (operatorId == null || operatorId <= 0) {
            throw new BizException("当前操作人不能为空");
        }
        String opterId = String.valueOf(operatorId);
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BizException("流程名称不能为空");
        }
        // flowKey 必填校验
        if (req.getFlowKey() == null || req.getFlowKey().trim().isEmpty()) {
            throw new BizException("流程 Key 不能为空");
        }
        String flowKey = req.getFlowKey().trim();
        // flowKey 唯一性校验
        if (flowMapper.countByFlowKey(flowKey, null) > 0) {
            throw new BizException("流程 Key 已存在：" + flowKey);
        }

        Date now = new Date();

        // 2. 保存草稿状态的流程定义并记录新增审计
        FlowDefinitionBo def = new FlowDefinitionBo();
        def.setName(req.getName().trim());
        def.setFlowKey(flowKey);
        def.setCategory(req.getCategory());
        def.setDescription(req.getDescription());
        def.setRemark(req.getRemark());
        def.setStatus(FlowStatus.DRAFT.getCode());
        def.setCreatedBy(operatorId);
        def.setUpdatedBy(operatorId);
        def.setCrteTime(now);
        def.setUpdtTime(now);
        flowMapper.addFlowDefinition(def);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, opterId, now, EventType.INSERT.getCode()));

        // 3. 新建第一个草稿版本并记录版本审计
        FlowVersionBo ver = new FlowVersionBo();
        ver.setFlowId(def.getId());
        ver.setFlowKey(flowKey);
        ver.setVerNum(1);
        ver.setStatus(FlowStatus.DRAFT.getCode());
        ver.setCreatedBy(operatorId);
        ver.setCrteTime(now);
        ver.setUpdtTime(now);
        flowMapper.addFlowVersion(ver);
        // 构建流程版本事件
        flowMapper.addFlowVersionEvt(toFlowVerEvt(ver, opterId, now, EventType.INSERT.getCode()));

        FlowDto result = new FlowDto();
        result.setFlowId(def.getId());
        result.setId(def.getId());
        result.setVersionId(ver.getId());
        result.setVerNum(1);
        result.setStatus(FlowStatus.DRAFT.getCode());
        return result;
    }

    // ==================== 流程详情（进入设计器） ====================

    /**
     * 查询流程详情（进入设计器，只读不写）
     *
     * 执行顺序：
     * 1. 优先返回已有草稿
     * 2. 无草稿时，加载最新版本画布数据作为编辑基础（不创建草稿记录）
     *
     * @param req 审批流程的查询或维护请求
     * @return 审批流程信息
     */
    public FlowDto queryFlowDetail(FlowReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }

        FlowDto dto = new FlowDto();
        dto.setId(def.getId());
        dto.setName(def.getName());
        dto.setFlowKey(def.getFlowKey());
        dto.setCategory(def.getCategory());
        dto.setDescription(def.getDescription());
        dto.setRemark(def.getRemark());
        dto.setStatus(def.getStatus());
        dto.setCurrentVer(def.getCurrentVer() != null ? def.getCurrentVer() : 0);

        // 1. 优先返回已有草稿
        FlowVersionBo draftVersion = flowMapper.queryDraftFlowVersion(def.getId());
        if (draftVersion != null) {
            // 转换草稿版本信息
            dto.setDraft(toDraftInfo(draftVersion));
            return dto;
        }

        // 2. 无草稿时，加载最新版本画布数据作为编辑基础（不创建草稿记录）
        FlowVersionBo latest = flowMapper.queryLatestFlowVersion(def.getId());
        if (latest != null) {
            // 转换草稿版本信息
            dto.setDraft(toDraftInfo(latest));
        }

        return dto;
    }

    // ==================== 编辑基础信息 ====================

    /**
     * 更新流程基础信息（名称、flowKey、分类、描述、备注）
     *
     * 执行顺序：
     * 1. flowKey 必填校验
     * 2. 仅 draft 状态允许修改 flowKey
     * 3. flowKey 唯一性校验（排除自身）
     * 4. 构建流程定义事件
     * 5. 直接复用入参 FlowReq 回查详情
     *
     * @param req 审批流程的查询或维护请求
     * @return 审批流程信息
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto editFlow(FlowReq req) {
        Long operatorId = req.getOperatorId();
        if (operatorId == null || operatorId <= 0) {
            throw new BizException("当前操作人不能为空");
        }
        String opterId = String.valueOf(operatorId);
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        if (req.getName() != null && req.getName().trim().isEmpty()) {
            throw new BizException("流程名称不能为空");
        }
        // 1. flowKey 必填校验
        if (req.getFlowKey() == null || req.getFlowKey().trim().isEmpty()) {
            throw new BizException("流程 Key 不能为空");
        }
        String newFlowKey = req.getFlowKey().trim();
        // 2. 仅 draft 状态允许修改 flowKey
        if (!FlowStatus.DRAFT.getCode().equals(def.getStatus()) && !newFlowKey.equals(def.getFlowKey())) {
            throw new BizException("仅未发布状态的流程可修改流程 Key");
        }
        // 3. flowKey 唯一性校验（排除自身）
        if (!newFlowKey.equals(def.getFlowKey()) && flowMapper.countByFlowKey(newFlowKey, def.getId()) > 0) {
            throw new BizException("流程 Key 已存在：" + newFlowKey);
        }
        def.setFlowKey(newFlowKey);

        if (req.getName() != null) {
            def.setName(req.getName().trim());
        }
        if (req.getCategory() != null) {
            def.setCategory(req.getCategory());
        }
        if (req.getDescription() != null) {
            def.setDescription(req.getDescription());
        }
        if (req.getRemark() != null) {
            def.setRemark(req.getRemark());
        }
        Date now = new Date();
        def.setUpdatedBy(operatorId);
        def.setUpdtTime(now);
        flowMapper.editFlowDefinition(def);
        // 4. 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, opterId, now, EventType.UPDATE.getCode()));
        // 5. 直接复用入参 FlowReq 回查详情
        return queryFlowDetail(req);
    }

    // ==================== 删除流程（软删除） ====================

    /**
     * 逻辑删除流程（is_deleted 置为 1），返回被删除的流程信息
     *
     * 执行顺序：
     * 1. 删除前查出实体一并返回，便于前端展示
     * 2. 构建流程定义事件
     *
     * @param req 审批流程的查询或维护请求
     * @return 审批流程信息
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto deleteFlow(FlowReq req) {
        Long operatorId = req.getOperatorId();
        if (operatorId == null || operatorId <= 0) {
            throw new BizException("当前操作人不能为空");
        }
        String opterId = String.valueOf(operatorId);
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        // 1. 删除前查出实体一并返回，便于前端展示
        FlowDto deleted = queryFlowDetail(req);
        Date now = new Date();
        flowMapper.deleteFlowLogical(req.getId(), operatorId, now);
        def.setIsDeleted(1); def.setUpdatedBy(operatorId); def.setUpdtTime(now);
        // 2. 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, opterId, now, EventType.DELETE.getCode()));
        return deleted;
    }

    // ==================== 停用 ====================

    /**
     * 停用流程：将定义状态由 active 改为 disabled，同时将当前活跃版本标为 disabled 以保留历史轨迹
     *
     * 执行顺序：
     * 1. 查询待停用的流程，校验其状态及操作请求
     * 2. 将流程定义和当前活跃版本标为停用，记录操作历史
     *
     * @param req 审批流程的查询或维护请求
     * @return 审批流程信息
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto editFlowStatus(FlowReq req) {
        // 1. 校验操作人并确认流程已发布
        Long operatorId = req.getOperatorId();
        if (operatorId == null || operatorId <= 0) {
            throw new BizException("当前操作人不能为空");
        }
        String opterId = String.valueOf(operatorId);
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        if (!FlowStatus.ACTIVE.getCode().equals(def.getStatus())) {
            throw new BizException("仅已发布状态可停用");
        }

        // TODO 检查是否有其他数据引用此流程（如运行中实例），有则不允许停用

        // 2. 停用流程定义并记录操作历史
        Date now = new Date();
        flowMapper.editFlowDefinitionStatus(req.getId(), operatorId, now);
        def.setStatus(FlowStatus.DISABLED.getCode()); def.setUpdatedBy(operatorId); def.setUpdtTime(now);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, opterId, now, EventType.UPDATE.getCode()));

        // 将当前活跃版本标为停用，历史中可追溯
        // 遍历全部版本，找到版本号最大的 active 版本，标记为 disabled
        // 3. 停用最新的启用版本并记录版本审计，再返回流程详情
        FlowVersionBo latestActive = null;
        for (FlowVersionBo v : flowMapper.queryFlowVersionByFlowIdList(def.getId(), null)) {
            if (FlowStatus.ACTIVE.getCode().equals(v.getStatus())) {
                if (latestActive == null || v.getVerNum() > latestActive.getVerNum()) {
                    latestActive = v;
                }
            }
        }
        if (latestActive != null) {
            flowMapper.editFlowVersionStatus(latestActive.getId(), FlowStatus.DISABLED.getCode(), now);
            latestActive.setStatus(FlowStatus.DISABLED.getCode()); latestActive.setUpdtTime(now);
            // 构建流程版本事件
            flowMapper.addFlowVersionEvt(toFlowVerEvt(latestActive, opterId, now, EventType.UPDATE.getCode()));
        }
        return queryFlowDetail(req);
    }

    // ==================== 保存草稿 ====================

    /**
     * 保存审批流程画布草稿
     *
     * 同步将画布节点/连线写入归一化表
     *
     * 执行顺序：
     * 1. 校验操作人并锁定待保存的流程定义
     * 2. 核对名称和 Key 的变更，更新流程定义并记录审计
     * 3. 将画布节点和连线分别转成 JSON
     * 4. 更新已有草稿或新建草稿版本，记录版本审计
     * 5. 按请求的节点和连线同步流程配置并返回草稿信息
     *
     * @param req 流程草稿保存请求，包含流程 ID、操作人和画布内容
     * @return 审批流程信息
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto editFlowDraft(DesignerReq req) {
        // 1. 校验操作人并锁定待保存的流程定义
        Long operatorId = req.getOperatorId();
        if (operatorId == null || operatorId <= 0) {
            throw new BizException("当前操作人不能为空");
        }
        String opterId = String.valueOf(operatorId);
        FlowDefinitionBo def = flowMapper.queryFlowByIdForUpdate(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }

        // 2. 核对名称和 Key 的变更，更新流程定义并记录审计
        Date now = new Date();
        boolean nameChanged = req.getName() != null && !req.getName().equals(def.getName());
        boolean keyChanged = req.getFlowKey() != null && !req.getFlowKey().equals(def.getFlowKey());
        if (keyChanged) {
            // 仅 draft 状态允许修改 flowKey
            if (!FlowStatus.DRAFT.getCode().equals(def.getStatus())) {
                throw new BizException("仅未发布状态的流程可修改流程 Key");
            }
            // flowKey 唯一性校验（排除自身）
            if (flowMapper.countByFlowKey(req.getFlowKey(), def.getId()) > 0) {
                throw new BizException("流程 Key 已存在：" + req.getFlowKey());
            }
        }
        if (nameChanged || keyChanged) {
            def.setName(req.getName());
            def.setFlowKey(req.getFlowKey());
        }
        def.setUpdatedBy(operatorId);
        def.setUpdtTime(now);
        flowMapper.editFlowDefinition(def);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, opterId, now, EventType.UPDATE.getCode()));

        // 3. 将画布节点和连线分别转成 JSON
        String nodesJson = toJson(req.getNodes());
        // 序列化业务数据为 JSON
        String edgesJson = toJson(req.getEdges());

        // 4. 更新已有草稿或新建草稿版本，记录版本审计
        FlowVersionBo latest = flowMapper.queryDraftFlowVersion(def.getId());
        if (latest == null) {
            // 查最新版本
            latest = flowMapper.queryLatestFlowVersion(def.getId());
        }
        Long versionId;
        int verNum;

        if (latest != null && FlowStatus.DRAFT.getCode().equals(latest.getStatus())) {
            // 更新已有草稿
            latest.setCanvasNodes(nodesJson);
            latest.setCanvasEdges(edgesJson);
            latest.setCanvasPanX(req.getPanX());
            latest.setCanvasPanY(req.getPanY());
            latest.setCanvasZoom(req.getZoom());
            latest.setFlowKey(def.getFlowKey()); // 同步主表变更后的 Key
            latest.setUpdtTime(now);
            flowMapper.editFlowVersion(latest);
            // 构建流程版本事件
            flowMapper.addFlowVersionEvt(toFlowVerEvt(latest, opterId, now, EventType.UPDATE.getCode()));
            versionId = latest.getId();
            verNum = latest.getVerNum();
        } else {
            // 新建草稿版本
            verNum = (latest != null ? latest.getVerNum() : 0) + 1;
            FlowVersionBo ver = new FlowVersionBo();
            ver.setFlowId(def.getId());
            ver.setFlowKey(def.getFlowKey());
            ver.setVerNum(verNum);
            ver.setStatus(FlowStatus.DRAFT.getCode());
            ver.setCanvasNodes(nodesJson);
            ver.setCanvasEdges(edgesJson);
            ver.setCanvasPanX(req.getPanX());
            ver.setCanvasPanY(req.getPanY());
            ver.setCanvasZoom(req.getZoom());
            ver.setCreatedBy(operatorId);
            ver.setCrteTime(now);
            ver.setUpdtTime(now);
            flowMapper.addFlowVersion(ver);
            // 构建流程版本事件
            flowMapper.addFlowVersionEvt(toFlowVerEvt(ver, opterId, now, EventType.INSERT.getCode()));
            versionId = ver.getId();

            // active 流程首次创建新草稿时，同步定义状态为 draft
            if (FlowStatus.ACTIVE.getCode().equals(def.getStatus())) {
                def.setStatus(FlowStatus.DRAFT.getCode());
                def.setUpdatedBy(operatorId);
                def.setUpdtTime(now);
                flowMapper.editFlowDefinition(def);
                // 构建流程定义事件
                flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, opterId, now, EventType.UPDATE.getCode()));
            }
        }

        // 5. 按请求的节点和连线同步流程配置并返回草稿信息
        syncNormalized(def.getId(), versionId, req.getNodes(), req.getEdges(), now, opterId);

        FlowDto result = new FlowDto();
        result.setVersionId(versionId);
        result.setVerNum(verNum);
        result.setStatus(FlowStatus.DRAFT.getCode());
        return result;
    }

    // ==================== 发布流程 ====================

    /**
     * 发布审批流程及其画布配置
     *
     * 若无草稿则直接新建正式版本
     *
     * 执行顺序：
     * 1. 校验操作人并锁定待发布的流程定义
     * 2. 校验画布节点和连线是否满足发布要求
     * 3. 将画布节点和连线分别转成 JSON
     * 4. 发布已有草稿或新建正式版本，记录版本审计
     * 5. 将流程定义标为已发布并记录审计
     * 6. 按请求的节点和连线同步流程配置并返回发布结果
     *
     * @param req 流程发布请求，包含流程 ID、操作人和待发布的节点及连线
     * @return 审批流程信息
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto editFlowToPublished(DesignerReq req) {
        // 1. 校验操作人并锁定待发布的流程定义
        Long operatorId = req.getOperatorId();
        if (operatorId == null || operatorId <= 0) {
            throw new BizException("当前操作人不能为空");
        }
        String opterId = String.valueOf(operatorId);
        FlowDefinitionBo def = flowMapper.queryFlowByIdForUpdate(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }

        List<CanvasNodeDto> nodes = req.getNodes();
        List<CanvasEdgeDto> edges = req.getEdges();

        // 2. 校验画布节点和连线是否满足发布要求
        validateBeforePublish(nodes, edges);

        Date now = new Date();
        // 3. 将画布节点和连线分别转成 JSON
        String nodesJson = toJson(nodes);
        // 序列化业务数据为 JSON
        String edgesJson = toJson(edges);

        // 4. 发布已有草稿或新建正式版本，记录版本审计
        FlowVersionBo latest = flowMapper.queryDraftFlowVersion(def.getId());
        if (latest == null) {
            // 查最新版本
            latest = flowMapper.queryLatestFlowVersion(def.getId());
        }
        Long versionId;
        int verNum;

        if (latest != null && FlowStatus.DRAFT.getCode().equals(latest.getStatus())) {
            // 草稿转正式
            latest.setStatus(FlowStatus.ACTIVE.getCode());
            latest.setPublishNote(req.getPublishNote());
            latest.setCanvasNodes(nodesJson);
            latest.setCanvasEdges(edgesJson);
            latest.setCanvasPanX(req.getPanX());
            latest.setCanvasPanY(req.getPanY());
            latest.setCanvasZoom(req.getZoom());
            latest.setPublishedBy(operatorId);
            latest.setPublishedTime(now);
            latest.setUpdtTime(now);
            flowMapper.editFlowVersion(latest);
            flowMapper.editFlowVersionStatus(latest.getId(), FlowStatus.ACTIVE.getCode(), now);
            // 构建流程版本事件
            flowMapper.addFlowVersionEvt(toFlowVerEvt(latest, opterId, now, EventType.UPDATE.getCode()));
            versionId = latest.getId();
            verNum = latest.getVerNum();
        } else {
            // 新建正式版本
            verNum = (latest != null ? latest.getVerNum() : 0) + 1;
            FlowVersionBo ver = new FlowVersionBo();
            ver.setFlowId(def.getId());
            ver.setFlowKey(def.getFlowKey());
            ver.setVerNum(verNum);
            ver.setStatus(FlowStatus.ACTIVE.getCode());
            ver.setPublishNote(req.getPublishNote());
            ver.setCanvasNodes(nodesJson);
            ver.setCanvasEdges(edgesJson);
            ver.setCanvasPanX(req.getPanX());
            ver.setCanvasPanY(req.getPanY());
            ver.setCanvasZoom(req.getZoom());
            ver.setPublishedBy(operatorId);
            ver.setPublishedTime(now);
            ver.setCreatedBy(operatorId);
            ver.setCrteTime(now);
            ver.setUpdtTime(now);
            flowMapper.addFlowVersion(ver);
            // 构建流程版本事件
            flowMapper.addFlowVersionEvt(toFlowVerEvt(ver, opterId, now, EventType.INSERT.getCode()));
            versionId = ver.getId();
        }

        // 5. 将流程定义标为已发布并记录审计
        def.setStatus(FlowStatus.ACTIVE.getCode());
        def.setUpdatedBy(operatorId);
        def.setUpdtTime(now);
        flowMapper.editFlowDefinition(def);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, opterId, now, EventType.UPDATE.getCode()));

        // 6. 按请求的节点和连线同步流程配置并返回发布结果
        syncNormalized(def.getId(), versionId, nodes, edges, now, opterId);

        FlowDto result = new FlowDto();
        result.setVersionId(versionId);
        result.setVerNum(verNum);
        result.setStatus(FlowStatus.ACTIVE.getCode());
        result.setPublishedTime(now);
        return result;
    }

    // ==================== 版本历史 ====================

    /**
     * 查询流程版本列表，可按版本号筛选
     *
     * 执行顺序：
     * 1. 按流程 ID列表查询流程版本
     *
     * @param req 审批流程的查询或维护请求
     * @return 版本列表
     */
    public List<VersionDto> queryFlowVersionList(FlowReq req) {
        List<FlowVersionBo> vers = flowMapper.queryFlowVersionByFlowIdList(req.getId(), req.getVerNum());
        return vers.stream().map(v -> {
            VersionDto d = new VersionDto();
            d.setVersionId(v.getId());
            d.setVerNum(v.getVerNum());
            d.setStatus(v.getStatus());
            d.setPublishNote(v.getPublishNote());
            d.setPublishedBy(v.getPublishedBy());
            d.setPublishedTime(v.getPublishedTime());
            d.setCrteTime(v.getCrteTime());
            return d;
        }).collect(Collectors.toList());
    }

    // ==================== 版本详情（只读） ====================

    /**
     * 查询流程版本详情
     *
     * 执行顺序：
     * 1. 安全解析画布 JSON
     *
     * @param req 审批流程的查询或维护请求
     * @return 版本
     */
    public VersionDto queryFlowVersionDetail(FlowReq req) {
        FlowVersionBo ver = flowMapper.queryFlowVersionById(req.getVersionId());
        if (ver == null || !ver.getFlowId().equals(req.getFlowId())) {
            throw new BizException(404, "版本不存在");
        }
        VersionDto d = new VersionDto();
        d.setVersionId(ver.getId());
        d.setVerNum(ver.getVerNum());
        d.setStatus(ver.getStatus());
        d.setPublishNote(ver.getPublishNote());
        d.setPublishedBy(ver.getPublishedBy());
        d.setPublishedTime(ver.getPublishedTime());
        // 安全解析画布 JSON
        d.setNodes(parseJsonSafe(ver.getCanvasNodes()));
        // 安全解析画布 JSON
        d.setEdges(parseJsonSafe(ver.getCanvasEdges()));
        d.setPanX(ver.getCanvasPanX() != null ? ver.getCanvasPanX() : 0);
        d.setPanY(ver.getCanvasPanY() != null ? ver.getCanvasPanY() : 0);
        d.setZoom(ver.getCanvasZoom() != null ? ver.getCanvasZoom() : 1);
        return d;
    }

    // ==================== 字典 ====================

    /**
     * 查询角色字典
     *
     * 执行顺序：
     * 1. 读取角色记录，将每条转换为角色选项后返回列表
     *
     * @param req 审批流程的查询或维护请求
     * @return 角色列表
     */
    public List<RoleDto> queryRoleList(FlowReq req) {
        return flowMapper.queryRoleList().stream()
                // 将角色持久化对象转换为接口返回对象
                .map(this::convertRole)
                .collect(Collectors.toList());
    }

    /**
     * 查询人员列表，支持按角色及子角色过滤
     *
     * 执行顺序：
     * 1. 指定角色时先读取角色字典，再收集该角色及子角色的 ID
     * 2. 按角色范围和用户关键字查询人员，并转换成页面返回数据
     *
     * @param req 角色 ID 和人员关键字，可省略角色筛选
     * @return 用户列表
     */
    public List<UserDto> queryUserList(FlowReq req) {
        List<Long> roleIds = null;
        // 1. 指定角色时先读取角色字典，再收集该角色及子角色的 ID
        if (req.getRoleId() != null) {
            roleIds = new ArrayList<>();
            // 递归收集角色及其子角色 ID
            collectDescendantRoleIds(req.getRoleId(), roleIds, flowMapper.queryRoleList());
        }
        // 2. 按角色范围和用户关键字查询人员，并转换成页面返回数据
        return flowMapper.queryUserList(roleIds, req.getUserKeyword()).stream()
                // 将用户持久化对象转换为接口返回对象
                .map(this::convertUser)
                .collect(Collectors.toList());
    }

    /**
     * 查询自动任务选项
     *
     * 执行顺序：
     * 1. 根据任务编码构建自动任务字典项
     *
     * @param req 审批流程的查询或维护请求
     * @return 自动任务选项列表
     */
    public List<DictDto> queryAutoTaskList(FlowReq req) {
        List<DictDto> list = new ArrayList<>();
        // 根据任务编码构建自动任务字典项
        list.add(buildTask("createAccount"));
        // 根据任务编码构建自动任务字典项
        list.add(buildTask("updatePosition"));
        // 根据任务编码构建自动任务字典项
        list.add(buildTask("syncSettlement"));
        // 根据任务编码构建自动任务字典项
        list.add(buildTask("riskCheck"));
        // 根据任务编码构建自动任务字典项
        list.add(buildTask("sendNotify"));
        // 根据任务编码构建自动任务字典项
        list.add(buildTask("archiveRecord"));
        return list;
    }

    /**
     * 查询条件字段选项
     *
     * 执行顺序：
     * 1. 构建条件字段字典项
     *
     * @param req 审批流程的查询或维护请求
     * @return 条件字段选项列表
     */
    public List<DictDto> queryCondFieldList(FlowReq req) {
        List<DictDto> groups = new ArrayList<>();

        DictDto g1 = new DictDto();
        g1.setGroupCode("approvalResult");
        g1.setFields(Arrays.asList(
                // 构建条件字段字典项
                buildField("auditStatus"),
                // 构建条件字段字典项
                buildField("auditComment")));
        groups.add(g1);

        DictDto g2 = new DictDto();
        g2.setGroupCode("businessFlag");
        g2.setFields(Arrays.asList(
                // 构建条件字段字典项
                buildField("isDebtSimple"),
                // 构建条件字段字典项
                buildField("isWhitelist"),
                // 构建条件字段字典项
                buildField("isSimple"),
                // 构建条件字段字典项
                buildField("isRestricted"),
                // 构建条件字段字典项
                buildField("isLargeAmount")));
        groups.add(g2);

        DictDto g3 = new DictDto();
        g3.setGroupCode("flowVariable");
        g3.setFields(Arrays.asList(
                // 构建条件字段字典项
                buildField("applyAmount"),
                // 构建条件字段字典项
                buildField("creditRating"),
                // 构建条件字段字典项
                buildField("investType")));
        groups.add(g3);

        return groups;
    }

    // ==================== 私有方法 ====================

    /**
     * 转换草稿版本信息
     *
     * 执行顺序：
     * 1. 安全解析画布 JSON
     *
     * @param ver 流程版本
     * @return 流程草稿版本信息
     */
    private FlowDto.DraftInfo toDraftInfo(FlowVersionBo ver) {
        FlowDto.DraftInfo draft = new FlowDto.DraftInfo();
        draft.setVersionId(ver.getId());
        draft.setVerNum(ver.getVerNum());
        // 安全解析画布 JSON
        draft.setNodes(parseJsonSafe(ver.getCanvasNodes()));
        // 安全解析画布 JSON
        draft.setEdges(parseJsonSafe(ver.getCanvasEdges()));
        draft.setPanX(ver.getCanvasPanX() != null ? ver.getCanvasPanX() : 0);
        draft.setPanY(ver.getCanvasPanY() != null ? ver.getCanvasPanY() : 0);
        draft.setZoom(ver.getCanvasZoom() != null ? ver.getCanvasZoom() : 1);
        return draft;
    }

    /**
     * 校验发布前画布数据
     *
     * 执行顺序：
     * 1. 必须有唯一开始节点
     * 2. 必须有结束节点
     * 3. 不允许孤立节点（任何节点都必须有连线连接）
     * 4. 条件节点的出线应至少有一条配置了条件
     * 5. 校验发起/修改节点语义，避免运行时依赖节点显示名称
     * 6. 校验每条连线均配置动作，避免运行时主路径选择不确定
     *
     * @param nodes 画布节点列表
     * @param edges 画布连线列表
     */
    private void validateBeforePublish(List<CanvasNodeDto> nodes, List<CanvasEdgeDto> edges) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BizException("画布没有节点，无法发布");
        }

        // 1. 必须有唯一开始节点
        long startCount = nodes.stream().filter(n -> NodeType.START.getCode().equals(n.getType())).count();
        if (startCount == 0) {
            throw new BizException("缺少开始节点");
        }
        if (startCount > 1) {
            throw new BizException("只能有一个开始节点");
        }

        // 2. 必须有结束节点
        long endCount = nodes.stream().filter(n -> NodeType.END.getCode().equals(n.getType())).count();
        if (endCount == 0) {
            throw new BizException("缺少结束节点");
        }

        // 3. 不允许孤立节点（任何节点都必须有连线连接）
        if (edges != null && !edges.isEmpty()) {
            Set<String> connectedIds = new HashSet<>();
            for (CanvasEdgeDto e : edges) {
                connectedIds.add(e.getFrom());
                connectedIds.add(e.getTo());
            }
            for (CanvasNodeDto n : nodes) {
                if (!connectedIds.contains(n.getId())) {
                    throw new BizException("节点 [" + n.getLabel() + "] 未连接任何连线");
                }
            }
        } else if (nodes.size() > 1) {
            // 无连线但存在多个节点 → 全部孤立
            throw new BizException("画布缺少连线，所有节点均未连接");
        }

        // 4. 条件节点的出线应至少有一条配置了条件
        for (CanvasNodeDto n : nodes) {
            if (NodeType.CONDITION.getCode().equals(n.getType())) {
                boolean hasCond = false;
                if (edges != null) {
                    for (CanvasEdgeDto e : edges) {
                        if (n.getId().equals(e.getFrom())
                                && e.getCondRules() != null && !e.getCondRules().isEmpty()) {
                            hasCond = true;
                            break;
                        }
                    }
                }
                if (!hasCond) {
                    // 仅警告，不阻止发布（可能存在默认分支）
                }
            }
        }

        // 校验发起/修改节点语义，避免运行时依赖节点显示名称
        validateInitiatorRouteAction(nodes, edges);
        // 校验每条连线均配置动作，避免运行时主路径选择不确定
        validateRouteActionRequired(edges);
    }

    /**
     * 校验发起人节点的 routeAction 配置
     *
     * 执行顺序：
     * 1. 按 ID 整理画布节点
     * 2. 检查提交和重新提交连线只能从发起人节点发出，并禁止该节点配置通过连线
     * 3. 检查每个发起人节点是否配置了提交或重新提交连线，且两类连线不能同时存在
     *
     * @param nodes 画布节点列表
     * @param edges 画布连线列表
     */
    private void validateInitiatorRouteAction(List<CanvasNodeDto> nodes, List<CanvasEdgeDto> edges) {
        // 1. 按 ID 整理画布节点
        Map<String, CanvasNodeDto> nodeMap = nodes.stream()
                .collect(Collectors.toMap(CanvasNodeDto::getId, n -> n, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<CanvasEdgeDto>> outEdgeMap = new LinkedHashMap<>();
        // 2. 检查提交和重新提交连线只能从发起人节点发出，并禁止该节点配置通过连线
        if (edges != null) {
            for (CanvasEdgeDto edge : edges) {
                CanvasNodeDto fromNode = nodeMap.get(edge.getFrom());
                // 判断连线动作，再判断节点是否为发起人审批节点
                if (isRouteAction(edge, ProcessAction.SUBMIT.getCode()) && !isInitiatorApprovalNode(fromNode)) {
                    // 获取连线显示名称
                    throw new BizException("连线 [" + edgeLabel(edge) + "] 的提交动作只能配置在发起人节点");
                }
                // 判断连线动作，再判断节点是否为发起人审批节点
                if (isRouteAction(edge, ProcessAction.RESUBMIT.getCode()) && !isInitiatorApprovalNode(fromNode)) {
                    // 获取连线显示名称
                    throw new BizException("连线 [" + edgeLabel(edge) + "] 的重新提交动作只能配置在发起人节点");
                }
                // 判断连线动作，再判断节点是否为发起人审批节点
                if (isRouteAction(edge, ProcessAction.APPROVE.getCode()) && isInitiatorApprovalNode(fromNode)) {
                    throw new BizException("发起人节点 [" + fromNode.getLabel() + "] 不能配置通过出边，请使用提交或重新提交");
                }
                outEdgeMap.computeIfAbsent(edge.getFrom(), k -> new ArrayList<>()).add(edge);
            }
        }

        // 3. 检查每个发起人节点是否配置了提交或重新提交连线，且两类连线不能同时存在
        for (CanvasNodeDto node : nodes) {
            // 判断节点是否为发起人审批节点
            if (!isInitiatorApprovalNode(node)) {
                continue;
            }
            List<CanvasEdgeDto> outEdges = outEdgeMap.getOrDefault(node.getId(), Collections.emptyList());
            long submitCount = outEdges.stream()
                    // 判断连线动作
                    .filter(edge -> isRouteAction(edge, ProcessAction.SUBMIT.getCode()))
                    .count();
            long resubmitCount = outEdges.stream()
                    // 判断连线动作
                    .filter(edge -> isRouteAction(edge, ProcessAction.RESUBMIT.getCode()))
                    .count();
            if (submitCount > 0 && resubmitCount > 0) {
                throw new BizException("发起人节点 [" + node.getLabel() + "] 不能同时配置提交和重新提交出边");
            }
            if (submitCount + resubmitCount == 0) {
                throw new BizException("发起人节点 [" + node.getLabel() + "] 必须配置提交或重新提交出边");
            }
            if (submitCount > 1 || resubmitCount > 1) {
                throw new BizException("发起人节点 [" + node.getLabel() + "] 只能配置一条提交或重新提交出边");
            }
        }
    }

    /**
     * 校验每条连线均显式配置 routeAction
     *
     * 执行顺序：
     * 1. 遍历已配置的连线，流转动作缺失或只有空白时提示具体连线
     *
     * @param edges 画布连线列表
     */
    private void validateRouteActionRequired(List<CanvasEdgeDto> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        for (CanvasEdgeDto edge : edges) {
            if (edge.getRouteAction() == null || edge.getRouteAction().trim().length() == 0) {
                // 获取连线显示名称
                throw new BizException("连线 [" + edgeLabel(edge) + "] 必须配置流转动作");
            }
        }
    }

    /**
     * 判断节点是否为发起人审批节点
     *
     * 执行顺序：
     * 1. 同时检查节点类型是审批节点、审批策略是发起人，请求节点为空时判为 false
     *
     * @param node 流程节点
     * @return true=节点是发起人审批节点，false=节点不是发起人审批节点
     */
    private boolean isInitiatorApprovalNode(CanvasNodeDto node) {
        return node != null
                && NodeType.APPROVAL.getCode().equals(node.getType())
                && ApprovalStrategy.INITIATOR.getCode().equals(node.getApprovalStrategy());
    }

    /**
     * 判断连线动作
     *
     * 执行顺序：
     * 1. 连线和待匹配动作都有值时比较动作编码，否则判为 false
     *
     * @param edge 待检查的流程连线
     * @param routeAction 当前处理动作对应的路由动作
     * @return true=连线动作与指定动作相同，false=连线动作与指定动作不同或缺失
     */
    private boolean isRouteAction(CanvasEdgeDto edge, String routeAction) {
        return edge != null && routeAction != null && routeAction.equals(edge.getRouteAction());
    }

    /**
     * 获取连线显示名称
     *
     * 执行顺序：
     * 1. 连线为空时返回空字符串，已配置名称时直接展示
     * 2. 没有名称时用起点和终点组合显示
     *
     * @param edge 待检查的流程连线
     * @return 显示名称
     */
    private String edgeLabel(CanvasEdgeDto edge) {
        if (edge == null) {
            return "";
        }
        if (edge.getLabel() != null && edge.getLabel().trim().length() > 0) {
            return edge.getLabel();
        }
        return edge.getFrom() + "->" + edge.getTo();
    }

    /**
     * 解析画布 JSON，写入归一化表（全量替换模式）
     *
     * 执行顺序：
     * 1. 删除前：捕获旧行记录 DELETE 事件
     * 2. 删除已有归一化数据（按依赖倒序）
     * 3. 插入节点，建立 businessId → surrogateId 映射
     * 4. 插入连线，建立 edgeId → surrogateId 映射
     *
     * @param flowId 流程定义 ID
     * @param versionId 版本ID
     * @param nodes 画布节点列表
     * @param edges 画布连线列表
     * @param now 本次操作时间
     * @param opterId 经办人 ID
     */
    private void syncNormalized(Long flowId, Long versionId,
                                List<CanvasNodeDto> nodes,
                                List<CanvasEdgeDto> edges,
                                Date now,
                                String opterId) {
        // 1. 删除前：捕获旧行记录 DELETE 事件
        logSyncDeletes(versionId, now, opterId);

        // 2. 删除已有归一化数据（按依赖倒序）
        flowMapper.deleteCondRuleByVersionId(versionId);
        flowMapper.deleteFlowEdgeByVersionId(versionId);
        flowMapper.deleteConditionConfigByVersionId(versionId);
        flowMapper.deleteNotifyConfigByVersionId(versionId);
        flowMapper.deleteAutoConfigByVersionId(versionId);
        flowMapper.deleteApprovalHandlerByVersionId(versionId);
        flowMapper.deleteApprovalConfigByVersionId(versionId);
        flowMapper.deleteFlowNodeByVersionId(versionId);

        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        // 3. 插入节点，建立 businessId → surrogateId 映射
        Map<String, Long> nodeIdMap = new LinkedHashMap<>();
        int sort = 1;
        for (CanvasNodeDto cn : nodes) {
            FlowNodeBo fn = new FlowNodeBo();
            fn.setVersionId(versionId);
            fn.setFlowId(flowId);
            fn.setNodeId(cn.getId());
            fn.setNodeType(cn.getType());
            fn.setLabel(cn.getLabel());
            fn.setShape(cn.getShape());
            fn.setPosX(cn.getX());
            fn.setPosY(cn.getY());
            fn.setSubLabel(cn.getSub());
            fn.setSortOrder(sort++);
            fn.setCrteTime(now);
            fn.setUpdtTime(now);
            flowMapper.addFlowNode(fn);
            // 构建流程节点事件
            flowMapper.addFlowNodeEvt(toFlowNodeEvt(fn, opterId, now, EventType.INSERT.getCode()));
            nodeIdMap.put(cn.getId(), fn.getId());

            // 写入类型专属配置
            if (NodeType.APPROVAL.getCode().equals(cn.getType())) {
                NodeApprovalConfigBo cfg = new NodeApprovalConfigBo();
                cfg.setNodeId(fn.getId());
                cfg.setApprovalStrategy(cn.getApprovalStrategy());
                cfg.setApprovalRemark(cn.getApprovalRemark());
                cfg.setCrteTime(now);
                cfg.setUpdtTime(now);
                flowMapper.addApprovalConfig(cfg);
                // 构建审批配置事件
                flowMapper.addApprovalConfigEvt(toApprovalCfgEvt(cfg, opterId, now, EventType.INSERT.getCode()));
                if (cn.getApprovalPersons() != null) {
                    int seq = 1;
                    for (PoolPermissionBo person : cn.getApprovalPersons()) {
                        if (person == null || person.getHandlerType() == null || person.getHandlerId() == null) {
                            continue;
                        }
                        NodeApprovalHandlerBo handler = new NodeApprovalHandlerBo();
                        handler.setApprovalConfigId(cfg.getId());
                        handler.setHandlerType(person.getHandlerType());
                        handler.setHandlerId(person.getHandlerId());
                        handler.setHandlerName(person.getHandlerName());
                        handler.setSortOrder(seq++);
                        handler.setCrteTime(now);
                        handler.setUpdtTime(now);
                        flowMapper.addApprovalHandler(handler);
                        // 构建审批处理人明细事件
                        flowMapper.addApprovalHandlerEvt(toApprovalHandlerEvt(handler, opterId, now, EventType.INSERT.getCode()));
                    }
                }
            } else if (NodeType.AUTO.getCode().equals(cn.getType())) {
                if (cn.getAutoTasks() != null) {
                    int seq = 1;
                    for (AutoTaskItemDto task : cn.getAutoTasks()) {
                        NodeAutoConfigBo cfg = new NodeAutoConfigBo();
                        cfg.setNodeId(fn.getId());
                        cfg.setTaskSeq(seq++);
                        cfg.setTaskCode(task.getTask());
                        cfg.setAutoRemark(cn.getAutoRemark()); // 节点级 → 行级（已知冗余）
                        cfg.setCrteTime(now);
                        cfg.setUpdtTime(now);
                        flowMapper.addAutoConfig(cfg);
                        // 构建自动任务配置事件
                        flowMapper.addAutoConfigEvt(toAutoCfgEvt(cfg, opterId, now, EventType.INSERT.getCode()));
                    }
                }
            } else if (NodeType.NOTIFY.getCode().equals(cn.getType())) {
                NodeNotifyConfigBo cfg = new NodeNotifyConfigBo();
                cfg.setNodeId(fn.getId());
                // 序列化业务数据为 JSON
                cfg.setNotifyChannels(toJson(cn.getNotifyChannels()));
                cfg.setNotifyTarget(cn.getNotifyTarget());
                // 序列化业务数据为 JSON
                cfg.setNotifyPersons(toJson(cn.getNotifyPersons()));
                cfg.setNotifyTpl(cn.getNotifyTpl());
                cfg.setNotifyRemark(cn.getNotifyRemark());
                cfg.setCrteTime(now);
                cfg.setUpdtTime(now);
                flowMapper.addNotifyConfig(cfg);
                // 构建通知配置事件
                flowMapper.addNotifyConfigEvt(toNotifyCfgEvt(cfg, opterId, now, EventType.INSERT.getCode()));
            } else if (NodeType.CONDITION.getCode().equals(cn.getType())) {
                NodeConditionConfigBo cfg = new NodeConditionConfigBo();
                cfg.setNodeId(fn.getId());
                cfg.setConditionRemark(cn.getConditionRemark());
                cfg.setCrteTime(now);
                cfg.setUpdtTime(now);
                flowMapper.addConditionConfig(cfg);
                // 构建条件配置事件
                flowMapper.addConditionConfigEvt(toCondCfgEvt(cfg, opterId, now, EventType.INSERT.getCode()));
            }
        }

        // 4. 插入连线，建立 edgeId → surrogateId 映射
        if (edges != null) {
            Map<String, Long> edgeIdMap = new LinkedHashMap<>();
            for (CanvasEdgeDto ce : edges) {
                Long fromSid = nodeIdMap.get(ce.getFrom());
                Long toSid = nodeIdMap.get(ce.getTo());
                if (fromSid == null || toSid == null) {
                    log.warn("syncNormalized: 边 {} 引用了不存在的节点 from={} to={}，已跳过",
                            ce.getId(), ce.getFrom(), ce.getTo());
                    continue;
                }
                FlowEdgeBo fe = new FlowEdgeBo();
                fe.setVersionId(versionId);
                fe.setFlowId(flowId);
                fe.setEdgeId(ce.getId());
                fe.setFromNodeId(fromSid);
                fe.setToNodeId(toSid);
                fe.setLabel(ce.getLabel());
                fe.setRouteAction(ce.getRouteAction());
                fe.setCondLogic(ce.getCondLogic());
                fe.setRemark(ce.getRemark());
                fe.setCrteTime(now);
                fe.setUpdtTime(now);
                flowMapper.addFlowEdge(fe);
                // 构建流程连线事件
                flowMapper.addFlowEdgeEvt(toFlowEdgeEvt(fe, opterId, now, EventType.INSERT.getCode()));
                edgeIdMap.put(ce.getId(), fe.getId());

                // 写入条件规则
                if (ce.getCondRules() != null) {
                    int seq = 1;
                    for (CondRuleItemDto cr : ce.getCondRules()) {
                        EdgeCondRuleBo rule = new EdgeCondRuleBo();
                        rule.setEdgeId(fe.getId());
                        rule.setSeq(seq++);
                        rule.setFieldCode(cr.getField());
                        rule.setOperator(cr.getOp());
                        rule.setFieldVal(cr.getVal());
                        rule.setCrteTime(now);
                        rule.setUpdtTime(now);
                        flowMapper.addCondRule(rule);
                        // 构建连线条件规则事件
                        flowMapper.addCondRuleEvt(toCondRuleEvt(rule, opterId, now, EventType.INSERT.getCode()));
                    }
                }
            }
        }
    }

    // ==================== 事件记录辅助方法 ====================

    /**
     * 构建流程定义事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到流程定义审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param def 原流程定义记录
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 流程定义审计记录
     */
    private FlowDefinitionEvtBo toFlowDefEvt(FlowDefinitionBo def, String opterId, Date now, String oprtType) {
        FlowDefinitionEvtBo e = new FlowDefinitionEvtBo();
        e.setId(def.getId()); e.setName(def.getName()); e.setFlowKey(def.getFlowKey());
        e.setCategory(def.getCategory()); e.setDescription(def.getDescription());
        e.setRemark(def.getRemark()); e.setStatus(def.getStatus());
        e.setCreatedBy(def.getCreatedBy()); e.setUpdatedBy(def.getUpdatedBy());
        e.setIsDeleted(def.getIsDeleted()); e.setCrteTime(def.getCrteTime()); e.setUpdtTime(def.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建流程版本事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到流程版本审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param ver 流程版本
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 流程版本审计记录
     */
    private FlowVersionEvtBo toFlowVerEvt(FlowVersionBo ver, String opterId, Date now, String oprtType) {
        FlowVersionEvtBo e = new FlowVersionEvtBo();
        e.setId(ver.getId()); e.setFlowId(ver.getFlowId()); e.setFlowKey(ver.getFlowKey());
        e.setVerNum(ver.getVerNum()); e.setStatus(ver.getStatus()); e.setPublishNote(ver.getPublishNote());
        e.setCanvasNodes(ver.getCanvasNodes()); e.setCanvasEdges(ver.getCanvasEdges());
        e.setCanvasPanX(ver.getCanvasPanX()); e.setCanvasPanY(ver.getCanvasPanY()); e.setCanvasZoom(ver.getCanvasZoom());
        e.setPublishedBy(ver.getPublishedBy()); e.setPublishedTime(ver.getPublishedTime());
        e.setCreatedBy(ver.getCreatedBy()); e.setCrteTime(ver.getCrteTime()); e.setUpdtTime(ver.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建流程节点事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到流程节点审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param node 待判断的流程节点
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 流程节点审计记录
     */
    private FlowNodeEvtBo toFlowNodeEvt(FlowNodeBo node, String opterId, Date now, String oprtType) {
        FlowNodeEvtBo e = new FlowNodeEvtBo();
        e.setId(node.getId()); e.setVersionId(node.getVersionId()); e.setFlowId(node.getFlowId());
        e.setNodeId(node.getNodeId()); e.setNodeType(node.getNodeType()); e.setLabel(node.getLabel());
        e.setShape(node.getShape()); e.setPosX(node.getPosX()); e.setPosY(node.getPosY());
        e.setSubLabel(node.getSubLabel()); e.setSortOrder(node.getSortOrder());
        e.setCrteTime(node.getCrteTime()); e.setUpdtTime(node.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建审批配置事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到审批节点配置审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param cfg 该节点的原配置
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 审批节点配置审计记录
     */
    private NodeApprovalConfigEvtBo toApprovalCfgEvt(NodeApprovalConfigBo cfg, String opterId, Date now, String oprtType) {
        NodeApprovalConfigEvtBo e = new NodeApprovalConfigEvtBo();
        e.setId(cfg.getId()); e.setNodeId(cfg.getNodeId());
        e.setApprovalStrategy(cfg.getApprovalStrategy());
        e.setApprovalRemark(cfg.getApprovalRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建审批处理人明细事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到审批处理人配置审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param handler 处理人
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 审批处理人配置审计记录
     */
    private NodeApprovalHandlerEvtBo toApprovalHandlerEvt(NodeApprovalHandlerBo handler, String opterId, Date now, String oprtType) {
        NodeApprovalHandlerEvtBo e = new NodeApprovalHandlerEvtBo();
        e.setId(handler.getId());
        e.setApprovalConfigId(handler.getApprovalConfigId());
        e.setHandlerType(handler.getHandlerType());
        e.setHandlerId(handler.getHandlerId());
        e.setHandlerName(handler.getHandlerName());
        e.setSortOrder(handler.getSortOrder());
        e.setCrteTime(handler.getCrteTime());
        e.setUpdtTime(handler.getUpdtTime());
        e.setOpterId(opterId);
        e.setOptTime(now);
        e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建自动任务配置事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到自动任务配置审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param cfg 该节点的原配置
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 自动任务配置审计记录
     */
    private NodeAutoConfigEvtBo toAutoCfgEvt(NodeAutoConfigBo cfg, String opterId, Date now, String oprtType) {
        NodeAutoConfigEvtBo e = new NodeAutoConfigEvtBo();
        e.setId(cfg.getId()); e.setNodeId(cfg.getNodeId());
        e.setTaskSeq(cfg.getTaskSeq()); e.setTaskCode(cfg.getTaskCode()); e.setAutoRemark(cfg.getAutoRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建通知配置事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到通知配置审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param cfg 该节点的原配置
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 通知配置审计记录
     */
    private NodeNotifyConfigEvtBo toNotifyCfgEvt(NodeNotifyConfigBo cfg, String opterId, Date now, String oprtType) {
        NodeNotifyConfigEvtBo e = new NodeNotifyConfigEvtBo();
        e.setId(cfg.getId()); e.setNodeId(cfg.getNodeId());
        e.setNotifyChannels(cfg.getNotifyChannels()); e.setNotifyTarget(cfg.getNotifyTarget());
        e.setNotifyPersons(cfg.getNotifyPersons()); e.setNotifyTpl(cfg.getNotifyTpl()); e.setNotifyRemark(cfg.getNotifyRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建条件配置事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到条件配置审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param cfg 该节点的原配置
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 条件配置审计记录
     */
    private NodeConditionConfigEvtBo toCondCfgEvt(NodeConditionConfigBo cfg, String opterId, Date now, String oprtType) {
        NodeConditionConfigEvtBo e = new NodeConditionConfigEvtBo();
        e.setId(cfg.getId()); e.setNodeId(cfg.getNodeId());
        e.setConditionRemark(cfg.getConditionRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建流程连线事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到流程连线审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param edge 待检查的流程连线
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 流程连线审计记录
     */
    private FlowEdgeEvtBo toFlowEdgeEvt(FlowEdgeBo edge, String opterId, Date now, String oprtType) {
        FlowEdgeEvtBo e = new FlowEdgeEvtBo();
        e.setId(edge.getId()); e.setVersionId(edge.getVersionId()); e.setFlowId(edge.getFlowId());
        e.setEdgeId(edge.getEdgeId()); e.setFromNodeId(edge.getFromNodeId()); e.setToNodeId(edge.getToNodeId());
        e.setLabel(edge.getLabel()); e.setRouteAction(edge.getRouteAction()); e.setCondLogic(edge.getCondLogic()); e.setRemark(edge.getRemark());
        e.setCrteTime(edge.getCrteTime()); e.setUpdtTime(edge.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 构建连线条件规则事件
     *
     * 执行顺序：
     * 1. 将当前配置的字段复制到连线条件审计记录
     * 2. 写入操作人、操作时间和操作类型
     *
     * @param rule 规则
     * @param opterId 经办人 ID
     * @param now 本次操作时间
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @return 连线条件审计记录
     */
    private EdgeCondRuleEvtBo toCondRuleEvt(EdgeCondRuleBo rule, String opterId, Date now, String oprtType) {
        EdgeCondRuleEvtBo e = new EdgeCondRuleEvtBo();
        e.setId(rule.getId()); e.setEdgeId(rule.getEdgeId());
        e.setSeq(rule.getSeq()); e.setFieldCode(rule.getFieldCode());
        e.setOperator(rule.getOperator()); e.setFieldVal(rule.getFieldVal());
        e.setCrteTime(rule.getCrteTime()); e.setUpdtTime(rule.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /**
     * 为即将删除的旧流程配置记录审计事件
     *
     * 执行顺序：
     * 1. 查询旧流程节点并记录删除事件
     * 2. 查询旧审批处理人并记录删除事件
     * 3. 查询旧审批配置并记录删除事件
     * 4. 查询旧自动任务配置并记录删除事件
     * 5. 查询旧通知配置并记录删除事件
     * 6. 查询旧条件配置并记录删除事件
     * 7. 查询旧流程连线并记录删除事件
     * 8. 查询旧连线条件规则并记录删除事件
     *
     * @param versionId 版本ID
     * @param now 本次操作时间
     * @param opterId 经办人 ID
     */
    private void logSyncDeletes(Long versionId, Date now, String opterId) {
        for (FlowNodeBo node : flowMapper.queryFlowNodeListByVersionId(versionId)) {
            // 1. 记录旧流程节点的删除事件
            flowMapper.addFlowNodeEvt(toFlowNodeEvt(node, opterId, now, EventType.DELETE.getCode()));
        }
        for (NodeApprovalHandlerBo handler : flowMapper.queryApprovalHandlerListByVersionId(versionId)) {
            // 2. 记录旧审批处理人的删除事件
            flowMapper.addApprovalHandlerEvt(toApprovalHandlerEvt(handler, opterId, now, EventType.DELETE.getCode()));
        }
        for (NodeApprovalConfigBo cfg : flowMapper.queryApprovalConfigListByVersionId(versionId)) {
            // 3. 记录旧审批配置的删除事件
            flowMapper.addApprovalConfigEvt(toApprovalCfgEvt(cfg, opterId, now, EventType.DELETE.getCode()));
        }
        for (NodeAutoConfigBo cfg : flowMapper.queryAutoConfigListByVersionId(versionId)) {
            // 4. 记录旧自动任务配置的删除事件
            flowMapper.addAutoConfigEvt(toAutoCfgEvt(cfg, opterId, now, EventType.DELETE.getCode()));
        }
        for (NodeNotifyConfigBo cfg : flowMapper.queryNotifyConfigListByVersionId(versionId)) {
            // 5. 记录旧通知配置的删除事件
            flowMapper.addNotifyConfigEvt(toNotifyCfgEvt(cfg, opterId, now, EventType.DELETE.getCode()));
        }
        for (NodeConditionConfigBo cfg : flowMapper.queryConditionConfigListByVersionId(versionId)) {
            // 6. 记录旧条件配置的删除事件
            flowMapper.addConditionConfigEvt(toCondCfgEvt(cfg, opterId, now, EventType.DELETE.getCode()));
        }
        for (FlowEdgeBo edge : flowMapper.queryFlowEdgeListByVersionId(versionId)) {
            // 7. 记录旧流程连线的删除事件
            flowMapper.addFlowEdgeEvt(toFlowEdgeEvt(edge, opterId, now, EventType.DELETE.getCode()));
        }
        for (EdgeCondRuleBo rule : flowMapper.queryCondRuleListByVersionId(versionId)) {
            // 8. 记录旧连线条件规则的删除事件
            flowMapper.addCondRuleEvt(toCondRuleEvt(rule, opterId, now, EventType.DELETE.getCode()));
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 序列化对象为 JSON
     *
     * 执行顺序：
     * 1. 空对象直接返回 null，有值时序列化为 JSON，转换失败时抛出业务异常
     *
     * @param obj 待序列化的流程画布、参数或其他配置对象，可为 null
     * @return 序列化后的 JSON，输入为 null 时返回 null
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return om.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BizException("JSON 序列化失败: " + e.getMessage());
        }
    }

    /**
     * 解析流程配置中的 JSON 数据
     *
     * 执行顺序：
     * 1. 输入为空或仅有空白时，直接返回空列表
     * 2. 解析有效 JSON，解析失败时回退为空列表
     *
     * @param json 待解析的流程配置或快照 JSON，可为空
     * @return 解析后的 JSON 树，输入为空或解析失败时返回空列表
     */
    private Object parseJsonSafe(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return om.readTree(json);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * RoleBo 转 RoleDto
     *
     * 执行顺序：
     * 1. 复制角色 ID、名称、父角色 ID 和排序号，生成页面角色数据
     *
     * @param bo 待转换的数据库记录
     * @return 角色
     */
    private RoleDto convertRole(RoleBo bo) {
        RoleDto dto = new RoleDto();
        dto.setId(bo.getId());
        dto.setRoleName(bo.getName());
        dto.setParentId(bo.getParentId());
        dto.setSortOrder(bo.getSortOrder());
        return dto;
    }

    /**
     * UserBo 转 UserDto
     *
     * 执行顺序：
     * 1. 复制用户 ID、姓名和角色名称，生成页面人员数据
     *
     * @param bo 待转换的数据库记录
     * @return 用户
     */
    private UserDto convertUser(UserBo bo) {
        UserDto dto = new UserDto();
        dto.setId(bo.getId());
        dto.setUserName(bo.getName());
        dto.setRoleName(bo.getRoleName());
        return dto;
    }

    /**
     * 递归收集角色及其子角色 ID
     *
     * 执行顺序：
     * 1. 先将当前角色 ID 加入结果
     * 2. 遍历所有角色，对直接子角色继续递归收集
     *
     * @param roleId 当前角色 ID
     * @param roleIds 收集到的角色 ID 列表
     * @param allRoles 全部角色及其父子关系
     */
    private void collectDescendantRoleIds(Long roleId, List<Long> roleIds, List<RoleBo> allRoles) {
        roleIds.add(roleId);
        for (RoleBo role : allRoles) {
            if (roleId.equals(role.getParentId())) {
                // 递归收集角色及其子角色 ID
                collectDescendantRoleIds(role.getId(), roleIds, allRoles);
            }
        }
    }

    /**
     * 根据任务编码构建自动任务字典项
     *
     * 执行顺序：
     * 1. 把传入任务编码填到字典项的任务编码字段
     *
     * @param code 证券代码
     * @return 任务
     */
    private DictDto buildTask(String code) {
        DictDto d = new DictDto();
        d.setTaskCode(code);
        return d;
    }

    /**
     * 构建条件字段字典项
     *
     * 执行顺序：
     * 1. 把传入编码填到字典项的条件字段编码字段
     *
     * @param code 证券代码
     * @return 字段
     */
    private DictDto buildField(String code) {
        DictDto d = new DictDto();
        d.setFieldCode(code);
        return d;
    }
}
