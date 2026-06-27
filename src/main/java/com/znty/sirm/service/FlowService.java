package com.znty.sirm.service;

import com.znty.sirm.common.enums.FlowStatus;
import com.znty.sirm.common.enums.NodeType;
import com.znty.sirm.common.enums.EventType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.FlowMapper;
import com.znty.sirm.entity.bo.EdgeCondRuleBo;
import com.znty.sirm.entity.bo.EdgeCondRuleEvtBo;
import com.znty.sirm.entity.bo.FlowDefinitionBo;
import com.znty.sirm.entity.bo.FlowDefinitionEvtBo;
import com.znty.sirm.entity.bo.FlowEdgeBo;
import com.znty.sirm.entity.bo.FlowEdgeEvtBo;
import com.znty.sirm.entity.bo.FlowNodeBo;
import com.znty.sirm.entity.bo.FlowNodeEvtBo;
import com.znty.sirm.entity.bo.FlowVersionBo;
import com.znty.sirm.entity.bo.FlowVersionEvtBo;
import com.znty.sirm.entity.bo.NodeApprovalConfigBo;
import com.znty.sirm.entity.bo.NodeApprovalConfigEvtBo;
import com.znty.sirm.entity.bo.NodeApprovalHandlerBo;
import com.znty.sirm.entity.bo.NodeApprovalHandlerEvtBo;
import com.znty.sirm.entity.bo.NodeAutoConfigBo;
import com.znty.sirm.entity.bo.NodeAutoConfigEvtBo;
import com.znty.sirm.entity.bo.NodeConditionConfigBo;
import com.znty.sirm.entity.bo.NodeConditionConfigEvtBo;
import com.znty.sirm.entity.bo.NodeNotifyConfigBo;
import com.znty.sirm.entity.bo.NodeNotifyConfigEvtBo;
import com.znty.sirm.entity.bo.PoolPermissionBo;
import com.znty.sirm.entity.bo.RoleBo;
import com.znty.sirm.entity.bo.UserBo;
import com.znty.sirm.entity.common.RoleDto;
import com.znty.sirm.entity.common.UserDto;
import com.znty.sirm.entity.flow.AutoTaskItemDto;
import com.znty.sirm.entity.flow.CanvasEdgeDto;
import com.znty.sirm.entity.flow.CanvasNodeDto;
import com.znty.sirm.entity.flow.CondRuleItemDto;
import com.znty.sirm.entity.flow.DesignerReq;
import com.znty.sirm.entity.flow.DictDto;
import com.znty.sirm.entity.flow.FlowDto;
import com.znty.sirm.entity.flow.FlowOptionDto;
import com.znty.sirm.entity.flow.FlowReq;
import com.znty.sirm.entity.flow.VersionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审批流程服务。
 * <p>负责流程定义的 CRUD、版本管理（草稿/发布/停用）、画布数据的保存与归一化持久化，
 * 以及流程相关字典（角色、自动任务、条件字段）的查询。</p>
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
    private static final String L_OPTER = "1"; // TODO 对接用户系统后替换

    // ==================== 流程列表 ====================

    /** 查询流程分页。 */
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

    /** 查询流程列表（不分页，用于下拉选项），仅返回 id/name/key/description。 */
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
     * 新建流程定义，同时创建初始草稿版本（verNum=1）。
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto addFlow(FlowReq req) {
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

        FlowDefinitionBo def = new FlowDefinitionBo();
        def.setName(req.getName().trim());
        def.setFlowKey(flowKey);
        def.setCategory(req.getCategory());
        def.setDescription(req.getDescription());
        def.setRemark(req.getRemark());
        def.setStatus(FlowStatus.DRAFT.getCode());
        def.setCreatedBy(1L); // TODO 对接用户系统后替换
        def.setUpdatedBy(1L);
        def.setCrteTime(now);
        def.setUpdtTime(now);
        flowMapper.addFlowDefinition(def);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, EventType.INSERT.getCode()));

        // 创建初始草稿版本
        FlowVersionBo ver = new FlowVersionBo();
        ver.setFlowId(def.getId());
        ver.setFlowKey(flowKey);
        ver.setVerNum(1);
        ver.setStatus(FlowStatus.DRAFT.getCode());
        ver.setCreatedBy(1L);
        ver.setCrteTime(now);
        ver.setUpdtTime(now);
        flowMapper.addFlowVersion(ver);
        // 构建流程版本事件
        flowMapper.addFlowVersionEvt(toFlowVerEvt(ver, L_OPTER, now, EventType.INSERT.getCode()));

        FlowDto result = new FlowDto();
        result.setFlowId(def.getId());
        result.setId(def.getId());
        result.setVersionId(ver.getId());
        result.setVerNum(1);
        result.setStatus(FlowStatus.DRAFT.getCode());
        return result;
    }

    // ==================== 流程详情（进入设计器） ====================

    /** 查询流程详情（进入设计器，只读不写）。 */
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

        // 优先返回已有草稿
        FlowVersionBo draftVersion = flowMapper.queryDraftFlowVersion(def.getId());
        if (draftVersion != null) {
            // 转换草稿版本信息
            dto.setDraft(toDraftInfo(draftVersion));
            return dto;
        }

        // 无草稿时，加载最新版本画布数据作为编辑基础（不创建草稿记录）
        FlowVersionBo latest = flowMapper.queryLatestFlowVersion(def.getId());
        if (latest != null) {
            // 转换草稿版本信息
            dto.setDraft(toDraftInfo(latest));
        }

        return dto;
    }

    // ==================== 编辑基础信息 ====================

    /**
     * 更新流程基础信息（名称、flowKey、分类、描述、备注）。
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto editFlow(FlowReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        if (req.getName() != null && req.getName().trim().isEmpty()) {
            throw new BizException("流程名称不能为空");
        }
        // flowKey 必填校验
        if (req.getFlowKey() == null || req.getFlowKey().trim().isEmpty()) {
            throw new BizException("流程 Key 不能为空");
        }
        String newFlowKey = req.getFlowKey().trim();
        // 仅 draft 状态允许修改 flowKey
        if (!FlowStatus.DRAFT.getCode().equals(def.getStatus()) && !newFlowKey.equals(def.getFlowKey())) {
            throw new BizException("仅未发布状态的流程可修改流程 Key");
        }
        // flowKey 唯一性校验（排除自身）
        if (!newFlowKey.equals(def.getFlowKey()) && flowMapper.countByFlowKey(newFlowKey, def.getId()) > 0) {
            throw new BizException("流程 Key 已存在：" + newFlowKey);
        }
        def.setFlowKey(newFlowKey);

        if (req.getName() != null) def.setName(req.getName().trim());
        if (req.getCategory() != null) def.setCategory(req.getCategory());
        if (req.getDescription() != null) def.setDescription(req.getDescription());
        if (req.getRemark() != null) def.setRemark(req.getRemark());
        def.setUpdatedBy(1L);
        def.setUpdtTime(new Date());
        flowMapper.editFlowDefinition(def);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, new Date(), EventType.UPDATE.getCode()));
        // 直接复用入参 FlowReq 回查详情
        return queryFlowDetail(req);
    }

    // ==================== 删除流程（软删除） ====================

    /**
     * 逻辑删除流程（is_deleted 置为 1），返回被删除的流程信息。
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto deleteFlow(FlowReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        // 删除前查出实体一并返回，便于前端展示
        FlowDto deleted = queryFlowDetail(req);
        Date now = new Date();
        flowMapper.deleteFlowLogical(req.getId(), now);
        def.setIsDeleted(1); def.setUpdtTime(now);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, EventType.DELETE.getCode()));
        return deleted;
    }

    // ==================== 停用 ====================

    /**
     * 停用流程：将定义状态由 active 改为 disabled，同时将当前活跃版本标为 disabled 以保留历史轨迹。
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto editFlowStatus(FlowReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        if (!FlowStatus.ACTIVE.getCode().equals(def.getStatus())) {
            throw new BizException("仅已发布状态可停用");
        }

        // TODO 检查是否有其他数据引用此流程（如运行中实例），有则不允许停用

        Date now = new Date();
        flowMapper.editFlowDefinitionStatus(req.getId(), now);
        def.setStatus(FlowStatus.DISABLED.getCode()); def.setUpdtTime(now);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, EventType.UPDATE.getCode()));

        // 将当前活跃版本标为停用，历史中可追溯
        // 遍历全部版本，找到版本号最大的 active 版本，标记为 disabled
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
            flowMapper.addFlowVersionEvt(toFlowVerEvt(latestActive, L_OPTER, now, EventType.UPDATE.getCode()));
        }
        return queryFlowDetail(req);
    }

    // ==================== 保存草稿 ====================

    /**
     * 保存流程画布草稿：若已有草稿版本则更新，否则新建草稿版本；
     * 同步将画布节点/连线写入归一化表。
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto editFlowDraft(DesignerReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowByIdForUpdate(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }

        // 如果修改了 name / flowKey，同步更新主表
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
            def.setUpdatedBy(1L);
            def.setUpdtTime(now);
            flowMapper.editFlowDefinition(def);
            // 构建流程定义事件
            flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, EventType.UPDATE.getCode()));
        }

        // 序列化业务数据为 JSON
        String nodesJson = toJson(req.getNodes());
        // 序列化业务数据为 JSON
        String edgesJson = toJson(req.getEdges());

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
            flowMapper.addFlowVersionEvt(toFlowVerEvt(latest, L_OPTER, now, EventType.UPDATE.getCode()));
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
            ver.setCreatedBy(1L);
            ver.setCrteTime(now);
            ver.setUpdtTime(now);
            flowMapper.addFlowVersion(ver);
            // 构建流程版本事件
            flowMapper.addFlowVersionEvt(toFlowVerEvt(ver, L_OPTER, now, EventType.INSERT.getCode()));
            versionId = ver.getId();

            // active 流程首次创建新草稿时，同步定义状态为 draft
            if (FlowStatus.ACTIVE.getCode().equals(def.getStatus())) {
                def.setStatus(FlowStatus.DRAFT.getCode());
                def.setUpdatedBy(1L);
                def.setUpdtTime(now);
                flowMapper.editFlowDefinition(def);
                // 构建流程定义事件
                flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, EventType.UPDATE.getCode()));
            }
        }

        // 解析 JSON → 归一化表
        syncNormalized(def.getId(), versionId, req.getNodes(), req.getEdges(), now);

        FlowDto result = new FlowDto();
        result.setVersionId(versionId);
        result.setVerNum(verNum);
        result.setStatus(FlowStatus.DRAFT.getCode());
        return result;
    }

    // ==================== 发布流程 ====================

    /**
     * 发布流程：将草稿版本转为正式版本（status=active），并通过校验后同步归一化表；
     * 若无草稿则直接新建正式版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDto editFlowToPublished(DesignerReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowByIdForUpdate(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }

        List<CanvasNodeDto> nodes = req.getNodes();
        List<CanvasEdgeDto> edges = req.getEdges();

        // ---- 发布前校验 ----
        validateBeforePublish(nodes, edges);

        Date now = new Date();
        // 序列化业务数据为 JSON
        String nodesJson = toJson(nodes);
        // 序列化业务数据为 JSON
        String edgesJson = toJson(edges);

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
            latest.setPublishedBy(1L);
            latest.setPublishedTime(now);
            latest.setUpdtTime(now);
            flowMapper.editFlowVersion(latest);
            flowMapper.editFlowVersionStatus(latest.getId(), FlowStatus.ACTIVE.getCode(), now);
            // 构建流程版本事件
            flowMapper.addFlowVersionEvt(toFlowVerEvt(latest, L_OPTER, now, EventType.UPDATE.getCode()));
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
            ver.setPublishedBy(1L);
            ver.setPublishedTime(now);
            ver.setCreatedBy(1L);
            ver.setCrteTime(now);
            ver.setUpdtTime(now);
            flowMapper.addFlowVersion(ver);
            // 构建流程版本事件
            flowMapper.addFlowVersionEvt(toFlowVerEvt(ver, L_OPTER, now, EventType.INSERT.getCode()));
            versionId = ver.getId();
        }

        // 更新主表状态
        def.setStatus(FlowStatus.ACTIVE.getCode());
        def.setUpdatedBy(1L);
        def.setUpdtTime(now);
        flowMapper.editFlowDefinition(def);
        // 构建流程定义事件
        flowMapper.addFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, EventType.UPDATE.getCode()));

        // 解析 JSON → 归一化表
        syncNormalized(def.getId(), versionId, nodes, edges, now);

        FlowDto result = new FlowDto();
        result.setVersionId(versionId);
        result.setVerNum(verNum);
        result.setStatus(FlowStatus.ACTIVE.getCode());
        result.setPublishedTime(now);
        return result;
    }

    // ==================== 版本历史 ====================

    /** 查询流程版本列表，可按版本号筛选。 */
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

    /** 查询流程版本详情。 */
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

    /** 查询角色字典。 */
    public List<RoleDto> queryRoleList(FlowReq req) {
        return flowMapper.queryRoleList().stream()
                // 将角色持久化对象转换为接口返回对象
                .map(this::convertRole)
                .collect(Collectors.toList());
    }

    /** 查询人员列表，支持按角色及子角色过滤。 */
    public List<UserDto> queryUserList(FlowReq req) {
        List<Long> roleIds = null;
        if (req.getRoleId() != null) {
            roleIds = new ArrayList<>();
            // 递归收集角色及其子角色 ID
            collectDescendantRoleIds(req.getRoleId(), roleIds, flowMapper.queryRoleList());
        }
        return flowMapper.queryUserList(roleIds, req.getUserKeyword()).stream()
                // 将用户持久化对象转换为接口返回对象
                .map(this::convertUser)
                .collect(Collectors.toList());
    }

    /** 查询自动任务选项。 */
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

    /** 查询条件字段选项。 */
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

    /** 转换草稿版本信息。 */
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

    /** 校验发布前画布数据。 */
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
    }

    /** 解析画布 JSON，写入归一化表（全量替换模式）。 */
    private void syncNormalized(Long flowId, Long versionId,
                                List<CanvasNodeDto> nodes,
                                List<CanvasEdgeDto> edges,
                                Date now) {
        // 0. 删除前：捕获旧行记录 DELETE 事件
        logSyncDeletes(versionId, now);

        // 1. 删除已有归一化数据（按依赖倒序）
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

        // 2. 插入节点，建立 businessId → surrogateId 映射
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
            flowMapper.addFlowNodeEvt(toFlowNodeEvt(fn, L_OPTER, now, EventType.INSERT.getCode()));
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
                flowMapper.addApprovalConfigEvt(toApprovalCfgEvt(cfg, L_OPTER, now, EventType.INSERT.getCode()));
                if (cn.getApprovalPersons() != null) {
                    int seq = 1;
                    for (PoolPermissionBo person : cn.getApprovalPersons()) {
                        if (person == null || person.getSubjectType() == null || person.getSubjectId() == null) {
                            continue;
                        }
                        NodeApprovalHandlerBo handler = new NodeApprovalHandlerBo();
                        handler.setApprovalConfigId(cfg.getId());
                        handler.setSubjectType(person.getSubjectType());
                        handler.setSubjectId(person.getSubjectId());
                        handler.setSubjectName(person.getSubjectName());
                        handler.setSortOrder(seq++);
                        handler.setCrteTime(now);
                        handler.setUpdtTime(now);
                        flowMapper.addApprovalHandler(handler);
                        // 构建审批处理人明细事件
                        flowMapper.addApprovalHandlerEvt(toApprovalHandlerEvt(handler, L_OPTER, now, EventType.INSERT.getCode()));
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
                        flowMapper.addAutoConfigEvt(toAutoCfgEvt(cfg, L_OPTER, now, EventType.INSERT.getCode()));
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
                flowMapper.addNotifyConfigEvt(toNotifyCfgEvt(cfg, L_OPTER, now, EventType.INSERT.getCode()));
            } else if (NodeType.CONDITION.getCode().equals(cn.getType())) {
                NodeConditionConfigBo cfg = new NodeConditionConfigBo();
                cfg.setNodeId(fn.getId());
                cfg.setConditionRemark(cn.getConditionRemark());
                cfg.setCrteTime(now);
                cfg.setUpdtTime(now);
                flowMapper.addConditionConfig(cfg);
                // 构建条件配置事件
                flowMapper.addConditionConfigEvt(toCondCfgEvt(cfg, L_OPTER, now, EventType.INSERT.getCode()));
            }
        }

        // 3. 插入连线，建立 edgeId → surrogateId 映射
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
                flowMapper.addFlowEdgeEvt(toFlowEdgeEvt(fe, L_OPTER, now, EventType.INSERT.getCode()));
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
                        flowMapper.addCondRuleEvt(toCondRuleEvt(rule, L_OPTER, now, EventType.INSERT.getCode()));
                    }
                }
            }
        }
    }

    // ==================== 事件记录辅助方法 ====================

    /** 构建流程定义事件。 */
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

    /** 构建流程版本事件。 */
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

    /** 构建流程节点事件。 */
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

    /** 构建审批配置事件。 */
    private NodeApprovalConfigEvtBo toApprovalCfgEvt(NodeApprovalConfigBo cfg, String opterId, Date now, String oprtType) {
        NodeApprovalConfigEvtBo e = new NodeApprovalConfigEvtBo();
        e.setId(cfg.getId()); e.setNodeId(cfg.getNodeId());
        e.setApprovalStrategy(cfg.getApprovalStrategy());
        e.setApprovalRemark(cfg.getApprovalRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /** 构建审批处理人明细事件。 */
    private NodeApprovalHandlerEvtBo toApprovalHandlerEvt(NodeApprovalHandlerBo handler, String opterId, Date now, String oprtType) {
        NodeApprovalHandlerEvtBo e = new NodeApprovalHandlerEvtBo();
        e.setId(handler.getId());
        e.setApprovalConfigId(handler.getApprovalConfigId());
        e.setSubjectType(handler.getSubjectType());
        e.setSubjectId(handler.getSubjectId());
        e.setSubjectName(handler.getSubjectName());
        e.setSortOrder(handler.getSortOrder());
        e.setCrteTime(handler.getCrteTime());
        e.setUpdtTime(handler.getUpdtTime());
        e.setOpterId(opterId);
        e.setOptTime(now);
        e.setOprtType(oprtType);
        return e;
    }

    /** 构建自动任务配置事件。 */
    private NodeAutoConfigEvtBo toAutoCfgEvt(NodeAutoConfigBo cfg, String opterId, Date now, String oprtType) {
        NodeAutoConfigEvtBo e = new NodeAutoConfigEvtBo();
        e.setId(cfg.getId()); e.setNodeId(cfg.getNodeId());
        e.setTaskSeq(cfg.getTaskSeq()); e.setTaskCode(cfg.getTaskCode()); e.setAutoRemark(cfg.getAutoRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /** 构建通知配置事件。 */
    private NodeNotifyConfigEvtBo toNotifyCfgEvt(NodeNotifyConfigBo cfg, String opterId, Date now, String oprtType) {
        NodeNotifyConfigEvtBo e = new NodeNotifyConfigEvtBo();
        e.setId(cfg.getId()); e.setNodeId(cfg.getNodeId());
        e.setNotifyChannels(cfg.getNotifyChannels()); e.setNotifyTarget(cfg.getNotifyTarget());
        e.setNotifyPersons(cfg.getNotifyPersons()); e.setNotifyTpl(cfg.getNotifyTpl()); e.setNotifyRemark(cfg.getNotifyRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /** 构建条件配置事件。 */
    private NodeConditionConfigEvtBo toCondCfgEvt(NodeConditionConfigBo cfg, String opterId, Date now, String oprtType) {
        NodeConditionConfigEvtBo e = new NodeConditionConfigEvtBo();
        e.setId(cfg.getId()); e.setNodeId(cfg.getNodeId());
        e.setConditionRemark(cfg.getConditionRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /** 构建流程连线事件。 */
    private FlowEdgeEvtBo toFlowEdgeEvt(FlowEdgeBo edge, String opterId, Date now, String oprtType) {
        FlowEdgeEvtBo e = new FlowEdgeEvtBo();
        e.setId(edge.getId()); e.setVersionId(edge.getVersionId()); e.setFlowId(edge.getFlowId());
        e.setEdgeId(edge.getEdgeId()); e.setFromNodeId(edge.getFromNodeId()); e.setToNodeId(edge.getToNodeId());
        e.setLabel(edge.getLabel()); e.setRouteAction(edge.getRouteAction()); e.setCondLogic(edge.getCondLogic()); e.setRemark(edge.getRemark());
        e.setCrteTime(edge.getCrteTime()); e.setUpdtTime(edge.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /** 构建连线条件规则事件。 */
    private EdgeCondRuleEvtBo toCondRuleEvt(EdgeCondRuleBo rule, String opterId, Date now, String oprtType) {
        EdgeCondRuleEvtBo e = new EdgeCondRuleEvtBo();
        e.setId(rule.getId()); e.setEdgeId(rule.getEdgeId());
        e.setSeq(rule.getSeq()); e.setFieldCode(rule.getFieldCode());
        e.setOperator(rule.getOperator()); e.setFieldVal(rule.getFieldVal());
        e.setCrteTime(rule.getCrteTime()); e.setUpdtTime(rule.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
        return e;
    }

    /** 捕获旧行并记录 DELETE 事件。 */
    private void logSyncDeletes(Long versionId, Date now) {
        for (FlowNodeBo node : flowMapper.queryFlowNodeListByVersionId(versionId)) {
            // 构建流程节点事件
            flowMapper.addFlowNodeEvt(toFlowNodeEvt(node, L_OPTER, now, EventType.DELETE.getCode()));
        }
        for (NodeApprovalHandlerBo handler : flowMapper.queryApprovalHandlerListByVersionId(versionId)) {
            // 构建审批处理人明细事件
            flowMapper.addApprovalHandlerEvt(toApprovalHandlerEvt(handler, L_OPTER, now, EventType.DELETE.getCode()));
        }
        for (NodeApprovalConfigBo cfg : flowMapper.queryApprovalConfigListByVersionId(versionId)) {
            // 构建审批配置事件
            flowMapper.addApprovalConfigEvt(toApprovalCfgEvt(cfg, L_OPTER, now, EventType.DELETE.getCode()));
        }
        for (NodeAutoConfigBo cfg : flowMapper.queryAutoConfigListByVersionId(versionId)) {
            // 构建自动任务配置事件
            flowMapper.addAutoConfigEvt(toAutoCfgEvt(cfg, L_OPTER, now, EventType.DELETE.getCode()));
        }
        for (NodeNotifyConfigBo cfg : flowMapper.queryNotifyConfigListByVersionId(versionId)) {
            // 构建通知配置事件
            flowMapper.addNotifyConfigEvt(toNotifyCfgEvt(cfg, L_OPTER, now, EventType.DELETE.getCode()));
        }
        for (NodeConditionConfigBo cfg : flowMapper.queryConditionConfigListByVersionId(versionId)) {
            // 构建条件配置事件
            flowMapper.addConditionConfigEvt(toCondCfgEvt(cfg, L_OPTER, now, EventType.DELETE.getCode()));
        }
        for (FlowEdgeBo edge : flowMapper.queryFlowEdgeListByVersionId(versionId)) {
            // 构建流程连线事件
            flowMapper.addFlowEdgeEvt(toFlowEdgeEvt(edge, L_OPTER, now, EventType.DELETE.getCode()));
        }
        for (EdgeCondRuleBo rule : flowMapper.queryCondRuleListByVersionId(versionId)) {
            // 构建连线条件规则事件
            flowMapper.addCondRuleEvt(toCondRuleEvt(rule, L_OPTER, now, EventType.DELETE.getCode()));
        }
    }

    // ==================== 工具方法 ====================

    /** 序列化对象为 JSON。 */
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return om.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BizException("JSON 序列化失败: " + e.getMessage());
        }
    }

    /** 安全解析 JSON。 */
    private Object parseJsonSafe(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            return om.readTree(json);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** RoleBo 转 RoleDto。 */
    private RoleDto convertRole(RoleBo bo) {
        RoleDto dto = new RoleDto();
        dto.setId(bo.getId());
        dto.setRoleName(bo.getName());
        dto.setParentId(bo.getParentId());
        dto.setSortOrder(bo.getSortOrder());
        return dto;
    }

    /** UserBo 转 UserDto。 */
    private UserDto convertUser(UserBo bo) {
        UserDto dto = new UserDto();
        dto.setId(bo.getId());
        dto.setUserName(bo.getName());
        dto.setRoleName(bo.getRoleName());
        return dto;
    }

    /** 递归收集角色及其子角色 ID。 */
    private void collectDescendantRoleIds(Long roleId, List<Long> roleIds, List<RoleBo> allRoles) {
        roleIds.add(roleId);
        for (RoleBo role : allRoles) {
            if (roleId.equals(role.getParentId())) {
                // 递归收集角色及其子角色 ID
                collectDescendantRoleIds(role.getId(), roleIds, allRoles);
            }
        }
    }

    /** 根据任务编码构建自动任务字典项 */
    private DictDto buildTask(String code) {
        DictDto d = new DictDto();
        d.setTaskCode(code);
        return d;
    }

    /** 构建条件字段字典项。 */
    private DictDto buildField(String code) {
        DictDto d = new DictDto();
        d.setFieldCode(code);
        return d;
    }
}
