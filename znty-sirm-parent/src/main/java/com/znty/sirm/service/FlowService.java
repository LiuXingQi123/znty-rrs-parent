package com.znty.sirm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.IdRequest;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.FlowMapper;
import com.znty.sirm.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FlowService {

    @Resource
    private FlowMapper flowMapper;
    private static final Logger log = LoggerFactory.getLogger(FlowService.class);
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
            d.setCreatedBy(e.getCreatedBy());
            d.setCrteTime(e.getCrteTime());
            d.setUpdtTime(e.getUpdtTime());
            return d;
        }).collect(Collectors.toList());

        return new PageResult<>(records, total, req.getPageIndex(), req.getPageSize());
    }

    // ==================== 新建流程 ====================

    @Transactional(rollbackFor = Exception.class)
    /** 新建流程。 */
    public FlowDto createFlow(FlowReq req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BizException("流程名称不能为空");
        }
        // flowKey 非必填，空值转为 null
        String flowKey = (req.getFlowKey() != null && !req.getFlowKey().trim().isEmpty())
                ? req.getFlowKey().trim() : null;

        Date now = new Date();

        FlowDefinitionBo def = new FlowDefinitionBo();
        def.setName(req.getName().trim());
        def.setFlowKey(flowKey);
        def.setCategory(req.getCategory());
        def.setDescription(req.getDescription());
        def.setRemark(req.getRemark());
        def.setStatus("draft");
        def.setCreatedBy(1L); // TODO 对接用户系统后替换
        def.setUpdatedBy(1L);
        def.setCrteTime(now);
        def.setUpdtTime(now);
        flowMapper.createFlowDefinition(def);
        flowMapper.createFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, "INSERT"));

        // 创建初始草稿版本
        FlowVersionBo ver = new FlowVersionBo();
        ver.setFlowId(def.getId());
        ver.setFlowKey(flowKey);
        ver.setVerNum(1);
        ver.setStatus("draft");
        ver.setCreatedBy(1L);
        ver.setCrteTime(now);
        ver.setUpdtTime(now);
        flowMapper.createFlowVersion(ver);
        flowMapper.createFlowVersionEvt(toFlowVerEvt(ver, L_OPTER, now, "INSERT"));

        FlowDto result = new FlowDto();
        result.setFlowId(def.getId());
        result.setId(def.getId());
        result.setVersionId(ver.getId());
        result.setVerNum(1);
        result.setStatus("draft");
        return result;
    }

    // ==================== 流程详情（进入设计器） ====================

    /** 查询流程详情（进入设计器，只读不写）。 */
    public FlowDto queryFlowDetail(IdRequest req) {
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
            dto.setDraft(toDraftInfo(draftVersion));
            return dto;
        }

        // 无草稿时，加载最新版本画布数据作为编辑基础（不创建草稿记录）
        FlowVersionBo latest = flowMapper.queryLatestFlowVersion(def.getId());
        if (latest != null) {
            dto.setDraft(toDraftInfo(latest));
        }

        return dto;
    }

    // ==================== 编辑基础信息 ====================

    @Transactional(rollbackFor = Exception.class)
    /** 更新流程基础信息。 */
    public void updateFlow(FlowReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        if (req.getName() != null && req.getName().trim().isEmpty()) {
            throw new BizException("流程名称不能为空");
        }
        // flowKey 非必填，允许置空
        if (req.getFlowKey() != null) {
            String v = req.getFlowKey().trim();
            def.setFlowKey(v.isEmpty() ? null : v);
        }

        if (req.getName() != null) def.setName(req.getName().trim());
        if (req.getCategory() != null) def.setCategory(req.getCategory());
        if (req.getDescription() != null) def.setDescription(req.getDescription());
        if (req.getRemark() != null) def.setRemark(req.getRemark());
        def.setUpdatedBy(1L);
        def.setUpdtTime(new Date());
        flowMapper.updateFlowDefinition(def);
        flowMapper.createFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, new Date(), "UPDATE"));
    }

    // ==================== 删除流程（软删除） ====================

    @Transactional(rollbackFor = Exception.class)
    /** 删除流程。 */
    public void deleteFlow(IdRequest req) {
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        Date now = new Date();
        flowMapper.deleteFlowLogical(req.getId(), now);
        def.setIsDeleted(1); def.setUpdtTime(now);
        flowMapper.createFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, "DELETE"));
    }

    // ==================== 停用 / 恢复 ====================

    @Transactional(rollbackFor = Exception.class)
    /** 停用流程。 */
    public void disableFlow(IdRequest req) {
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        if (!"active".equals(def.getStatus())) {
            throw new BizException("仅已发布状态可停用");
        }

        // TODO 检查是否有其他数据引用此流程（如运行中实例），有则不允许停用

        Date now = new Date();
        flowMapper.disableFlowDefinition(req.getId(), now);
        def.setStatus("disabled"); def.setUpdtTime(now);
        flowMapper.createFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, "UPDATE"));

        // 归档当前 active 版本，避免历史中出现"已停用→已发布"的不合理链路
        for (FlowVersionBo v : flowMapper.queryFlowVersionListByFlowId(def.getId())) {
            if ("active".equals(v.getStatus())) {
                flowMapper.updateFlowVersionStatus(v.getId(), "archived", now);
                v.setStatus("archived"); v.setUpdtTime(now);
                flowMapper.createFlowVersionEvt(toFlowVerEvt(v, L_OPTER, now, "UPDATE"));
            }
        }

        // 在版本历史中记录停用事件
        FlowVersionBo latest = flowMapper.queryLatestFlowVersion(def.getId());
        FlowVersionBo rec = new FlowVersionBo();
        rec.setFlowId(def.getId());
        rec.setFlowKey(def.getFlowKey());
        rec.setVerNum(latest != null ? latest.getVerNum() + 1 : 1);
        rec.setStatus("disabled");
        rec.setPublishNote("流程停用");
        rec.setCanvasNodes(latest != null ? latest.getCanvasNodes() : null);
        rec.setCanvasEdges(latest != null ? latest.getCanvasEdges() : null);
        rec.setCanvasPanX(latest != null ? latest.getCanvasPanX() : null);
        rec.setCanvasPanY(latest != null ? latest.getCanvasPanY() : null);
        rec.setCanvasZoom(latest != null ? latest.getCanvasZoom() : null);
        rec.setPublishedBy(1L);
        rec.setPublishedTime(now);
        rec.setCreatedBy(1L);
        rec.setCrteTime(now);
        rec.setUpdtTime(now);
        flowMapper.createFlowVersion(rec);
        flowMapper.createFlowVersionEvt(toFlowVerEvt(rec, L_OPTER, now, "INSERT"));
    }

    @Transactional(rollbackFor = Exception.class)
    /** 恢复流程。 */
    public void restoreFlow(IdRequest req) {
        FlowDefinitionBo def = flowMapper.queryFlowById(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }
        if (!"disabled".equals(def.getStatus())) {
            throw new BizException("流程不是停用状态，无法恢复");
        }

        Date now = new Date();
        flowMapper.restoreFlowDefinition(req.getId(), now);
        def.setStatus("draft"); def.setUpdtTime(now);
        flowMapper.createFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, "UPDATE"));

        // 归档 disabled 版本，避免历史中残留"已停用"状态
        for (FlowVersionBo v : flowMapper.queryFlowVersionListByFlowId(def.getId())) {
            if ("disabled".equals(v.getStatus())) {
                flowMapper.updateFlowVersionStatus(v.getId(), "archived", now);
                v.setStatus("archived"); v.setUpdtTime(now);
                flowMapper.createFlowVersionEvt(toFlowVerEvt(v, L_OPTER, now, "UPDATE"));
            }
        }

        // 新建草稿版本（复制最新版本的画布数据）
        FlowVersionBo latest = flowMapper.queryLatestFlowVersion(def.getId());
        FlowVersionBo draft = flowMapper.queryDraftFlowVersion(def.getId());
        if (draft != null) {
            archiveOtherDraftVersions(def.getId(), draft.getId(), now);
            return;
        }
        int newVerNum = (latest != null ? latest.getVerNum() : 0) + 1;
        draft = new FlowVersionBo();
        draft.setFlowId(def.getId());
        draft.setFlowKey(def.getFlowKey());
        draft.setVerNum(newVerNum);
        draft.setStatus("draft");
        draft.setPublishNote("恢复为草稿");
        draft.setCanvasNodes(latest != null ? latest.getCanvasNodes() : null);
        draft.setCanvasEdges(latest != null ? latest.getCanvasEdges() : null);
        draft.setCanvasPanX(latest != null ? latest.getCanvasPanX() : null);
        draft.setCanvasPanY(latest != null ? latest.getCanvasPanY() : null);
        draft.setCanvasZoom(latest != null ? latest.getCanvasZoom() : null);
        draft.setCreatedBy(1L);
        draft.setCrteTime(now);
        draft.setUpdtTime(now);
        flowMapper.createFlowVersion(draft);
        flowMapper.createFlowVersionEvt(toFlowVerEvt(draft, L_OPTER, now, "INSERT"));
    }

    // ==================== 保存草稿 ====================

    @Transactional(rollbackFor = Exception.class)
    /** 保存流程草稿。 */
    public FlowDto saveFlowDraft(DesignerReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowByIdForUpdate(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }

        // 如果修改了 name / flowKey，同步更新主表
        Date now = new Date();
        boolean nameChanged = req.getName() != null && !req.getName().equals(def.getName());
        boolean keyChanged = req.getFlowKey() != null && !req.getFlowKey().equals(def.getFlowKey());
        if (nameChanged || keyChanged) {
            def.setName(req.getName());
            def.setFlowKey(req.getFlowKey());
            def.setUpdatedBy(1L);
            def.setUpdtTime(now);
            flowMapper.updateFlowDefinition(def);
            flowMapper.createFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, "UPDATE"));
        }

        String nodesJson = toJson(req.getNodes());
        String edgesJson = toJson(req.getEdges());

        FlowVersionBo latest = flowMapper.queryDraftFlowVersion(def.getId());
        if (latest != null) {
            archiveOtherDraftVersions(def.getId(), latest.getId(), now);
        } else {
            // 查最新版本
            latest = flowMapper.queryLatestFlowVersion(def.getId());
        }
        Long versionId;
        int verNum;

        if (latest != null && "draft".equals(latest.getStatus())) {
            // 更新已有草稿
            latest.setCanvasNodes(nodesJson);
            latest.setCanvasEdges(edgesJson);
            latest.setCanvasPanX(req.getPanX());
            latest.setCanvasPanY(req.getPanY());
            latest.setCanvasZoom(req.getZoom());
            latest.setFlowKey(def.getFlowKey()); // 同步主表变更后的 Key
            latest.setUpdtTime(now);
            flowMapper.updateFlowVersion(latest);
            flowMapper.createFlowVersionEvt(toFlowVerEvt(latest, L_OPTER, now, "UPDATE"));
            versionId = latest.getId();
            verNum = latest.getVerNum();
        } else {
            // 新建草稿版本
            verNum = (latest != null ? latest.getVerNum() : 0) + 1;
            FlowVersionBo ver = new FlowVersionBo();
            ver.setFlowId(def.getId());
            ver.setFlowKey(def.getFlowKey());
            ver.setVerNum(verNum);
            ver.setStatus("draft");
            ver.setCanvasNodes(nodesJson);
            ver.setCanvasEdges(edgesJson);
            ver.setCanvasPanX(req.getPanX());
            ver.setCanvasPanY(req.getPanY());
            ver.setCanvasZoom(req.getZoom());
            ver.setCreatedBy(1L);
            ver.setCrteTime(now);
            ver.setUpdtTime(now);
            flowMapper.createFlowVersion(ver);
            flowMapper.createFlowVersionEvt(toFlowVerEvt(ver, L_OPTER, now, "INSERT"));
            versionId = ver.getId();
        }

        // 解析 JSON → 归一化表
        syncNormalized(def.getId(), versionId, req.getNodes(), req.getEdges(), now);

        FlowDto result = new FlowDto();
        result.setVersionId(versionId);
        result.setVerNum(verNum);
        result.setStatus("draft");
        return result;
    }

    // ==================== 发布流程 ====================

    @Transactional(rollbackFor = Exception.class)
    /** 发布流程。 */
    public FlowDto publishFlow(DesignerReq req) {
        FlowDefinitionBo def = flowMapper.queryFlowByIdForUpdate(req.getId());
        if (def == null) {
            throw new BizException(404, "流程不存在");
        }

        List<CanvasNodeDto> nodes = req.getNodes();
        List<CanvasEdgeDto> edges = req.getEdges();

        // ---- 发布前校验 ----
        validateBeforePublish(nodes, edges);

        Date now = new Date();
        String nodesJson = toJson(nodes);
        String edgesJson = toJson(edges);

        FlowVersionBo latest = flowMapper.queryDraftFlowVersion(def.getId());
        if (latest != null) {
            archiveOtherDraftVersions(def.getId(), latest.getId(), now);
        } else {
            // 查最新版本
            latest = flowMapper.queryLatestFlowVersion(def.getId());
        }
        Long versionId;
        int verNum;

        if (latest != null && "draft".equals(latest.getStatus())) {
            // 草稿转正式
            latest.setStatus("active");
            latest.setPublishNote(req.getPublishNote());
            latest.setCanvasNodes(nodesJson);
            latest.setCanvasEdges(edgesJson);
            latest.setCanvasPanX(req.getPanX());
            latest.setCanvasPanY(req.getPanY());
            latest.setCanvasZoom(req.getZoom());
            latest.setPublishedBy(1L);
            latest.setPublishedTime(now);
            latest.setUpdtTime(now);
            flowMapper.updateFlowVersion(latest);
            flowMapper.updateFlowVersionStatus(latest.getId(), "active", now);
            flowMapper.createFlowVersionEvt(toFlowVerEvt(latest, L_OPTER, now, "UPDATE"));
            versionId = latest.getId();
            verNum = latest.getVerNum();
        } else {
            // 新建正式版本
            verNum = (latest != null ? latest.getVerNum() : 0) + 1;
            FlowVersionBo ver = new FlowVersionBo();
            ver.setFlowId(def.getId());
            ver.setFlowKey(def.getFlowKey());
            ver.setVerNum(verNum);
            ver.setStatus("active");
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
            flowMapper.createFlowVersion(ver);
            flowMapper.createFlowVersionEvt(toFlowVerEvt(ver, L_OPTER, now, "INSERT"));
            versionId = ver.getId();
        }

        // 更新主表状态
        def.setStatus("active");
        def.setUpdatedBy(1L);
        def.setUpdtTime(now);
        flowMapper.updateFlowDefinition(def);
        flowMapper.createFlowDefinitionEvt(toFlowDefEvt(def, L_OPTER, now, "UPDATE"));

        // 归档所有旧版本（active / draft / disabled），同一流程只保留当前版本
        List<FlowVersionBo> allVers = flowMapper.queryFlowVersionListByFlowId(def.getId());
        for (FlowVersionBo v : allVers) {
            if (!v.getId().equals(versionId)
                    && ("active".equals(v.getStatus()) || "draft".equals(v.getStatus()) || "disabled".equals(v.getStatus()))) {
                flowMapper.updateFlowVersionStatus(v.getId(), "archived", now);
                v.setStatus("archived"); v.setUpdtTime(now);
                flowMapper.createFlowVersionEvt(toFlowVerEvt(v, L_OPTER, now, "UPDATE"));
            }
        }

        // 解析 JSON → 归一化表
        syncNormalized(def.getId(), versionId, nodes, edges, now);

        FlowDto result = new FlowDto();
        result.setVersionId(versionId);
        result.setVerNum(verNum);
        result.setStatus("active");
        result.setPublishedTime(now);
        return result;
    }

    // ==================== 版本历史 ====================

    /** 查询流程版本列表。 */
    public List<VersionDto> queryFlowVersionList(IdRequest req) {
        List<FlowVersionBo> vers = flowMapper.queryFlowVersionListByFlowId(req.getId());
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
        d.setNodes(parseJsonSafe(ver.getCanvasNodes()));
        d.setEdges(parseJsonSafe(ver.getCanvasEdges()));
        d.setPanX(ver.getCanvasPanX() != null ? ver.getCanvasPanX() : 0);
        d.setPanY(ver.getCanvasPanY() != null ? ver.getCanvasPanY() : 0);
        d.setZoom(ver.getCanvasZoom() != null ? ver.getCanvasZoom() : 1);
        return d;
    }

    // ==================== 字典 ====================

    /** 查询角色字典。 */
    public List<DictDto> roles() {
        return flowMapper.roleDictSelectAll().stream().map(r -> {
            DictDto d = new DictDto();
            d.setRoleCode(r.getRoleCode());
            d.setRoleName(r.getRoleName());
            d.setSortOrder(r.getSortOrder());
            return d;
        }).collect(Collectors.toList());
    }

    /** 查询自动任务选项。 */
    public List<DictDto> autoTasks() {
        List<DictDto> list = new ArrayList<>();
        list.add(buildTask("createAccount", "创建账号"));
        list.add(buildTask("updatePosition", "更新持仓状态"));
        list.add(buildTask("syncSettlement", "同步清算数据"));
        list.add(buildTask("riskCheck", "触发风控检查"));
        list.add(buildTask("sendNotify", "发送系统通知"));
        list.add(buildTask("archiveRecord", "归档流程记录"));
        return list;
    }

    /** 查询条件字段选项。 */
    public List<DictDto> condFields() {
        List<DictDto> groups = new ArrayList<>();

        DictDto g1 = new DictDto();
        g1.setGroupName("审批结果");
        g1.setFields(Arrays.asList(
                buildField("auditStatus", "审核状态"),
                buildField("auditComment", "审核意见")));
        groups.add(g1);

        DictDto g2 = new DictDto();
        g2.setGroupName("业务标志");
        g2.setFields(Arrays.asList(
                buildField("isDebtSimple", "债大库简易流程"),
                buildField("isWhitelist", "白名单流程"),
                buildField("isSimple", "简易流程"),
                buildField("isRestricted", "禁止库标的"),
                buildField("isLargeAmount", "大额交易")));
        groups.add(g2);

        DictDto g3 = new DictDto();
        g3.setGroupName("流程变量");
        g3.setFields(Arrays.asList(
                buildField("applyAmount", "申请金额"),
                buildField("creditRating", "标的评级"),
                buildField("investType", "投资类型")));
        groups.add(g3);

        return groups;
    }

    // ==================== 私有方法 ====================

    /** 转换草稿版本信息。 */
    private FlowDto.DraftInfo toDraftInfo(FlowVersionBo ver) {
        FlowDto.DraftInfo draft = new FlowDto.DraftInfo();
        draft.setVersionId(ver.getId());
        draft.setVerNum(ver.getVerNum());
        draft.setNodes(parseJsonSafe(ver.getCanvasNodes()));
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
        long startCount = nodes.stream().filter(n -> "start".equals(n.getType())).count();
        if (startCount == 0) {
            throw new BizException("缺少开始节点");
        }
        if (startCount > 1) {
            throw new BizException("只能有一个开始节点");
        }

        // 2. 必须有结束节点
        long endCount = nodes.stream().filter(n -> "end".equals(n.getType())).count();
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
            if ("condition".equals(n.getType())) {
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
            flowMapper.createFlowNode(fn);
            flowMapper.createFlowNodeEvt(toFlowNodeEvt(fn, L_OPTER, now, "INSERT"));
            nodeIdMap.put(cn.getId(), fn.getId());

            // 写入类型专属配置
            if ("approval".equals(cn.getType())) {
                NodeApprovalConfigBo cfg = new NodeApprovalConfigBo();
                cfg.setNodeId(fn.getId());
                cfg.setApprovalStrategy(cn.getApprovalStrategy());
                cfg.setApprovalPersons(toJson(cn.getApprovalPersons()));
                cfg.setApprovalRemark(cn.getApprovalRemark());
                cfg.setCrteTime(now);
                cfg.setUpdtTime(now);
                flowMapper.createApprovalConfig(cfg);
                flowMapper.createApprovalConfigEvt(toApprovalCfgEvt(cfg, L_OPTER, now, "INSERT"));
            } else if ("auto".equals(cn.getType())) {
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
                        flowMapper.createAutoConfig(cfg);
                        flowMapper.createAutoConfigEvt(toAutoCfgEvt(cfg, L_OPTER, now, "INSERT"));
                    }
                }
            } else if ("notify".equals(cn.getType())) {
                NodeNotifyConfigBo cfg = new NodeNotifyConfigBo();
                cfg.setNodeId(fn.getId());
                cfg.setNotifyChannels(toJson(cn.getNotifyChannels()));
                cfg.setNotifyTarget(cn.getNotifyTarget());
                cfg.setNotifyPersons(toJson(cn.getNotifyPersons()));
                cfg.setNotifyTpl(cn.getNotifyTpl());
                cfg.setNotifyRemark(cn.getNotifyRemark());
                cfg.setCrteTime(now);
                cfg.setUpdtTime(now);
                flowMapper.createNotifyConfig(cfg);
                flowMapper.createNotifyConfigEvt(toNotifyCfgEvt(cfg, L_OPTER, now, "INSERT"));
            } else if ("condition".equals(cn.getType())) {
                NodeConditionConfigBo cfg = new NodeConditionConfigBo();
                cfg.setNodeId(fn.getId());
                cfg.setConditionRemark(cn.getConditionRemark());
                cfg.setCrteTime(now);
                cfg.setUpdtTime(now);
                flowMapper.createConditionConfig(cfg);
                flowMapper.createConditionConfigEvt(toCondCfgEvt(cfg, L_OPTER, now, "INSERT"));
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
                fe.setCondLogic(ce.getCondLogic());
                fe.setRemark(ce.getRemark());
                fe.setCrteTime(now);
                fe.setUpdtTime(now);
                flowMapper.createFlowEdge(fe);
                flowMapper.createFlowEdgeEvt(toFlowEdgeEvt(fe, L_OPTER, now, "INSERT"));
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
                        flowMapper.createCondRule(rule);
                        flowMapper.createCondRuleEvt(toCondRuleEvt(rule, L_OPTER, now, "INSERT"));
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
        e.setApprovalStrategy(cfg.getApprovalStrategy()); e.setApprovalPersons(cfg.getApprovalPersons());
        e.setApprovalRemark(cfg.getApprovalRemark());
        e.setCrteTime(cfg.getCrteTime()); e.setUpdtTime(cfg.getUpdtTime());
        e.setOpterId(opterId); e.setOptTime(now); e.setOprtType(oprtType);
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
        e.setLabel(edge.getLabel()); e.setCondLogic(edge.getCondLogic()); e.setRemark(edge.getRemark());
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
            flowMapper.createFlowNodeEvt(toFlowNodeEvt(node, L_OPTER, now, "DELETE"));
        }
        for (NodeApprovalConfigBo cfg : flowMapper.queryApprovalConfigListByVersionId(versionId)) {
            flowMapper.createApprovalConfigEvt(toApprovalCfgEvt(cfg, L_OPTER, now, "DELETE"));
        }
        for (NodeAutoConfigBo cfg : flowMapper.queryAutoConfigListByVersionId(versionId)) {
            flowMapper.createAutoConfigEvt(toAutoCfgEvt(cfg, L_OPTER, now, "DELETE"));
        }
        for (NodeNotifyConfigBo cfg : flowMapper.queryNotifyConfigListByVersionId(versionId)) {
            flowMapper.createNotifyConfigEvt(toNotifyCfgEvt(cfg, L_OPTER, now, "DELETE"));
        }
        for (NodeConditionConfigBo cfg : flowMapper.queryConditionConfigListByVersionId(versionId)) {
            flowMapper.createConditionConfigEvt(toCondCfgEvt(cfg, L_OPTER, now, "DELETE"));
        }
        for (FlowEdgeBo edge : flowMapper.queryFlowEdgeListByVersionId(versionId)) {
            flowMapper.createFlowEdgeEvt(toFlowEdgeEvt(edge, L_OPTER, now, "DELETE"));
        }
        for (EdgeCondRuleBo rule : flowMapper.queryCondRuleListByVersionId(versionId)) {
            flowMapper.createCondRuleEvt(toCondRuleEvt(rule, L_OPTER, now, "DELETE"));
        }
    }

    /** 归档同流程的其它草稿版本。 */
    private void archiveOtherDraftVersions(Long flowId, Long keepVersionId, Date now) {
        List<FlowVersionBo> versions = flowMapper.queryFlowVersionListByFlowId(flowId);
        for (FlowVersionBo v : versions) {
            if ("draft".equals(v.getStatus()) && !v.getId().equals(keepVersionId)) {
                flowMapper.updateFlowVersionStatus(v.getId(), "archived", now);
                v.setStatus("archived");
                v.setUpdtTime(now);
                flowMapper.createFlowVersionEvt(toFlowVerEvt(v, L_OPTER, now, "UPDATE"));
            }
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

    /** 构建自动任务字典项。 */
    private DictDto buildTask(String code, String name) {
        DictDto d = new DictDto();
        d.setTaskCode(code);
        d.setTaskName(name);
        return d;
    }

    /** 构建条件字段字典项。 */
    private DictDto buildField(String code, String name) {
        DictDto d = new DictDto();
        d.setFieldCode(code);
        d.setFieldName(name);
        return d;
    }
}
