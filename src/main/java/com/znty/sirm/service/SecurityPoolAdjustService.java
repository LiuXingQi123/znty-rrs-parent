package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.FlowMapper;
import com.znty.sirm.mapper.SecurityPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.AdjustCheckContext;
import com.znty.sirm.model.AdjustCheckDto;
import com.znty.sirm.model.AdjustCheckReq;
import com.znty.sirm.model.AdjustLogDto;
import com.znty.sirm.model.AdjustSubmitDto;
import com.znty.sirm.model.FlowDefinitionBo;
import com.znty.sirm.model.FlowEdgeBo;
import com.znty.sirm.model.FlowNodeBo;
import com.znty.sirm.model.FlowVersionBo;
import com.znty.sirm.model.SecurityInfoBo;
import com.znty.sirm.model.SecurityInfoDetailDto;
import com.znty.sirm.model.SecurityInfoDto;
import com.znty.sirm.model.SecurityPoolAdjustReq;
import com.znty.sirm.model.SecurityPoolAdjustSubmitReq;
import com.znty.sirm.model.SecurityPoolStatusDto;
import com.znty.sirm.model.PoolStatusDto;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.IpAdjustLogBo;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.model.PoolRelationBo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 证券池调库业务逻辑
 *
 * <p>核心功能：
 * <ul>
 *   <li>证券/投资池查询：证券列表分页、证券详情、可调投资池列表、当前入池状态</li>
 *   <li>调库申请提交：写入调库记录，初始状态为"待审核"</li>
 *   <li>调库可行性校验（checkAdjust）：对用户选择的每个调库项执行三层校验，
 *       并根据投资池关系配置自动生成联动/互斥项追加校验</li>
 * </ul>
 *
 * <p>校验结构：
 * <ul>
 *   <li>调入校验（checkInConditions）：前置规则（到期、流程进行中）+ 调入方向规则
 *       （重复入池、容量上限、来源池、限制池、弹性禁投池）</li>
 *   <li>调出校验（checkOutConditions）：前置规则（到期、流程进行中）+ 调出方向规则
 *       （未入池、限制池、互斥池、弹性禁投池）</li>
 * </ul>
 *
 * <p>自动生成项规则：
 * <ul>
 *   <li>联动池（linkage）：调入/调出某池时，其配置的联动池需同步调入/调出，由系统自动附加</li>
 *   <li>互斥池（mutex）：调入某池时，若证券当前已在该池的互斥池中，系统自动附加对互斥池的调出项</li>
 * </ul>
 */
@Service
public class SecurityPoolAdjustService {

    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    @Resource
    private FlowMapper flowMapper;

    // ─── 投资池关系类型常量（对应 ip_pool_relation.relation_type 字段值） ───
    private static final String REL_SOURCE       = "source";           // 来源池：调入目标池须先在来源池中
    private static final String REL_IN_RESTRICT  = "in_restrict";      // 调入限制池：证券在此池则不可调入目标池
    private static final String REL_OUT_RESTRICT = "out_restrict";     // 调出限制池：证券在此池则不可调出目标池
    private static final String REL_IN_LINKAGE   = "in_linked";        // 调入联动池：调入目标池时需同步调入此池
    private static final String REL_OUT_LINKAGE  = "out_linked";       // 调出联动池：调出目标池时需同步调出此池
    private static final String REL_IN_MUTEX     = "in_mutex";         // 调入互斥池：目标池与此池互斥，调入前需先调出互斥池
    private static final String REL_OUT_MUTEX    = "out_mutex";        // 调出互斥池：证券在互斥池中则不可调出目标池
    private static final String REL_IN_ELASTIC   = "in_soft_restrict"; // 调入弹性禁投池：证券在此池则不可调入目标池
    private static final String REL_OUT_ELASTIC  = "out_soft_restrict";// 调出弹性禁投池：证券在此池则不可调出目标池

    private static final String FLOW_TYPE_WHITELIST_INBOUND = "whitelistInbound";
    private static final String FLOW_TYPE_SIMPLE_INBOUND = "simpleInbound";
    private static final String FLOW_TYPE_NORMAL_INBOUND = "normalInbound";
    private static final String FLOW_TYPE_UPGRADE_INBOUND = "upgradeInbound";
    private static final String FLOW_TYPE_DOWNGRADE_INBOUND = "downgradeInbound";
    private static final String FLOW_KEY_WHITELIST_INBOUND = "bond:whitelist-inbound";
    private static final String CREDIT_BOND_ROOT_CODE = "credit_bond_root";
    private static final String CREDIT_BOND_POOL_TYPE = "credit_bond";

    // ═══════════════════════════════════════════════════════════
    //  查询类接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 分页查询证券列表
     *
     * @param req 查询条件（证券代码、简称、发行人，均为模糊匹配）
     */
    public PageResult<SecurityInfoDto> querySecurityPage(SecurityPoolAdjustReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityInfoBo> entities = securityPoolAdjustMapper.querySecurityPage(
                req.getSecurityCode(), req.getSecurityShortName(), req.getIssuer());
        PageInfo<SecurityInfoBo> pageInfo = new PageInfo<>(entities);

        List<SecurityInfoDto> records = entities.stream().map(this::toSecurityInfoDto).collect(Collectors.toList());
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询证券详情（调库页面顶部基础信息展示）
     *
     * @param req 需携带 securityId
     */
    public SecurityInfoDetailDto querySecurityDetail(SecurityPoolAdjustReq req) {
        if (req.getSecurityId() == null) {
            throw new BizException("证券ID不能为空");
        }
        SecurityInfoBo bo = securityPoolAdjustMapper.querySecurityDetail(req.getSecurityId());
        if (bo == null) {
            throw new BizException(404, "证券不存在");
        }
        return toSecurityInfoDetailDto(bo);
    }

    /**
     * 查询可调入/可调出的投资池列表（含互斥关系，树结构由前端自行组装）
     *
     * @param req 暂无过滤条件，返回全量启用池
     */
    public List<PoolDto> queryAdjustPoolList(SecurityPoolAdjustReq req) {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools == null || allPools.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
        List<PoolRelationBo> mutexList = investmentPoolMapper.queryMutexRelationList();
        Map<Long, List<Long>> inMutexMap  = new HashMap<>();
        Map<Long, List<Long>> outMutexMap = new HashMap<>();
        if (mutexList != null) {
            for (PoolRelationBo r : mutexList) {
                if ("in_mutex".equals(r.getRelationType())) {
                    inMutexMap.computeIfAbsent(r.getPoolId(), k -> new ArrayList<>()).add(r.getRelationPoolId());
                } else if ("out_mutex".equals(r.getRelationType())) {
                    outMutexMap.computeIfAbsent(r.getPoolId(), k -> new ArrayList<>()).add(r.getRelationPoolId());
                }
            }
        }
        return allPools.stream().map(p -> toPoolDto(p, inMutexMap, outMutexMap)).collect(Collectors.toList());
    }

    /**
     * 查询证券当前所在池及其主体（发行人）当前所在池
     *
     * @param req 需携带 securityCode
     */
    public SecurityPoolStatusDto querySecurityPoolStatus(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityPoolStatusDto dto = new SecurityPoolStatusDto();
        dto.setSecurityCurrentPools(securityPoolAdjustMapper.querySecurityPoolStatus(req.getSecurityCode()));
        dto.setIssuerCurrentPools(securityPoolAdjustMapper.queryIssuerPoolStatus(req.getSecurityCode()));
        return dto;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库申请提交
    // ═══════════════════════════════════════════════════════════

    /**
     * 提交调库申请（五阶段流程）
     *
     * <p>分五个阶段顺序执行：
     * <ol>
     *   <li><b>前置校验</b>：校验请求入参合法性，不通过直接抛异常</li>
     *   <li><b>参数初始化</b>：集中执行所有 DB 查询，构建 {@link SubmitSharedData}，
     *       包含证券信息、投资池索引、池关系映射、各流程的快照数据</li>
     *   <li><b>调入处理</b>：遍历全部调入方向的调库项，逐项判断是否直通流程，
     *       直通则直接写入 ip_pool_status（audit_status='20'），否则写入 ip_adjust_log（audit_status='00'）</li>
     *   <li><b>调出处理</b>：遍历全部调出方向的调库项，直通则软删除 ip_pool_status 记录，
     *       否则写入 ip_adjust_log（audit_status='00'）</li>
     *   <li><b>后续处理</b>：预留扩展点，当前为空实现</li>
     * </ol>
     *
     * @param req 调库申请，包含证券信息及一个或多个调库项（每个项须携带 flowId 或 flowKey）
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req) {

        // ══ 第一阶段：前置校验 ══
        validateSubmitReq(req);

        // ══ 第二阶段：参数初始化 ══
        SubmitSharedData shared = loadSubmitSharedData(req);

        // ══ 第三阶段：调入处理 ══
        List<Long> inboundIds = executeInboundSubmit(req, shared);

        // ══ 第四阶段：调出处理 ══
        List<Long> outboundIds = executeOutboundSubmit(req, shared);

        // ══ 第五阶段：后续处理 ══
        postSubmitProcess(req, shared);

        // 组装返回结果
        AdjustSubmitDto dto = new AdjustSubmitDto();
        dto.setSecurityCode(req.getSecurityCode());
        List<Long> allIds = new ArrayList<>(inboundIds);
        allIds.addAll(outboundIds);
        dto.setSubmitCount(allIds.size());
        dto.setLogIds(allIds);
        return dto;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库提交 — 五阶段实现
    // ═══════════════════════════════════════════════════════════

    /**
     * 第一阶段：前置校验
     *
     * <p>对请求入参进行合法性检查，均为内存操作，不涉及任何 DB 查询。
     * 任意一项不通过则直接抛出异常，终止后续流程。
     *
     * @param req 调库提交请求
     */
    private void validateSubmitReq(SecurityPoolAdjustSubmitReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item.getAdjustMode() == null || item.getAdjustMode().isEmpty()) {
                throw new BizException("调库项的调整方向不能为空");
            }
            if (item.getTargetPoolId() == null) {
                throw new BizException("调库项的目标投资池 ID 不能为空");
            }
            if (item.getFlowId() == null && (item.getFlowKey() == null || item.getFlowKey().isEmpty())) {
                throw new BizException("调库项 [" + item.getTargetPoolName() + "] 缺少流程标识（flowId 或 flowKey）");
            }
        }
    }

    /**
     * 第二阶段：参数初始化
     *
     * <p>集中执行本次提交所需的全部 DB 查询，构建 {@link SubmitSharedData}。
     * 包括证券基础信息、投资池索引、池关系映射、以及每个唯一流程的快照（定义+版本+节点+连线）。
     * 第三、四阶段直接从 shared 中读取数据，无需重复查库。
     *
     * @param req 调库提交请求
     * @return 封装了本次提交全量共享数据的 SubmitSharedData
     */
    private SubmitSharedData loadSubmitSharedData(SecurityPoolAdjustSubmitReq req) {
        // 证券基础信息（兼含存在性校验）
        SecurityInfoBo securityInfo = securityPoolAdjustMapper.querySecurityDetail(req.getSecurityId());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }

        // 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools != null) {
            for (InvestmentPoolBo p : allPools) {
                poolMap.put(p.getId(), p);
            }
        }

        // 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
        List<Long> currentPoolIdList = securityPoolAdjustMapper.querySecurityCurrentPoolIds(req.getSecurityCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList != null ? currentPoolIdList : Collections.emptyList());

        // 全量投资池关系配置，构建三层嵌套 Map
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                securityPoolAdjustMapper.queryAllPoolRelations());

        // 收集所有调库项中引用的唯一流程标识，批量加载流程快照
        Set<Long> uniqueFlowIds = new HashSet<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            Long resolvedId = resolveFlowIdFromItem(item);
            if (resolvedId != null) {
                uniqueFlowIds.add(resolvedId);
            }
        }

        // 为每个唯一流程加载快照（定义 + 活跃版本 + 节点 + 连线）
        Map<Long, FlowSnapshot> flowSnapshotMap = new HashMap<>();
        for (Long flowId : uniqueFlowIds) {
            FlowSnapshot snapshot = buildFlowSnapshot(flowId);
            if (snapshot != null) {
                flowSnapshotMap.put(flowId, snapshot);
            }
        }

        return new SubmitSharedData(
                securityInfo,
                poolMap,
                currentPoolIds,
                poolRelationMap,
                securityPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()),
                securityPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()),
                securityPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()),
                flowSnapshotMap
        );
    }

    /**
     * 从调库项的 flowId 或 flowKey 解析出流程定义 ID。
     *
     * <p>优先使用 flowId（直接匹配），否则用 flowKey 查 DB 获取。
     *
     * @param item 调库项
     * @return 流程定义 ID，解析失败返回 null
     */
    private Long resolveFlowIdFromItem(SecurityPoolAdjustSubmitReq.AdjustItem item) {
        if (item.getFlowId() != null) {
            return item.getFlowId();
        }
        if (item.getFlowKey() != null && !item.getFlowKey().isEmpty()) {
            FlowDefinitionBo def = flowMapper.queryActiveFlowByKey(item.getFlowKey());
            return def != null ? def.getId() : null;
        }
        return null;
    }

    /**
     * 为指定流程 ID 构建运行时快照（定义 + 活跃版本 + 节点索引 + 连线列表）。
     *
     * <p>节点按 DB ID 建立索引，供遍历连线时快速查找目标节点。
     *
     * @param flowId 流程定义 ID
     * @return FlowSnapshot，若流程不存在或无活跃版本则返回 null
     */
    private FlowSnapshot buildFlowSnapshot(Long flowId) {
        FlowDefinitionBo def = flowMapper.queryFlowById(flowId);
        if (def == null) {
            return null;
        }

        // 查询该流程的所有版本（ORDER BY ver_num DESC），取第一个 status='active' 的版本
        List<FlowVersionBo> versions = flowMapper.queryFlowVersionListByFlowId(flowId, null);
        FlowVersionBo activeVersion = null;
        if (versions != null) {
            for (FlowVersionBo v : versions) {
                if ("active".equals(v.getStatus())) {
                    activeVersion = v;
                    break;
                }
            }
        }
        if (activeVersion == null) {
            return null;
        }

        // 加载该版本的节点和连线
        List<FlowNodeBo> nodes = flowMapper.queryFlowNodeListByVersionId(activeVersion.getId());
        List<FlowEdgeBo> edges = flowMapper.queryFlowEdgeListByVersionId(activeVersion.getId());

        // 节点按 DB ID 建立索引，供后续边遍历快速查找目标节点
        Map<Long, FlowNodeBo> nodeMap = new HashMap<>();
        if (nodes != null) {
            for (FlowNodeBo node : nodes) {
                nodeMap.put(node.getId(), node);
            }
        }

        return new FlowSnapshot(def, activeVersion, nodeMap, edges != null ? edges : Collections.emptyList());
    }

    /**
     * 判断流程是否为直通流程（start 节点的所有出边直接指向 end 节点）。
     *
     * <p>判定逻辑：
     * <ol>
     *   <li>在节点列表中查找 nodeType='start' 的节点</li>
     *   <li>在连线列表中查找 fromNodeId 等于 start 节点 DB ID 的所有出边</li>
     *   <li>逐一检查每个出边的目标节点的 nodeType 是否都为 'end'</li>
     *   <li>若所有目标节点都是 end → 直通流程；若存在非 end 目标节点 → 非直通（需审批）</li>
     * </ol>
     *
     * @param snapshot 流程快照（含节点索引和连线列表）
     * @return true 表示直通流程（可直接生效），false 表示需要审批
     */
    private boolean isDirectFlow(FlowSnapshot snapshot) {
        // 查找 start 节点
        FlowNodeBo startNode = null;
        for (FlowNodeBo node : snapshot.nodeMap.values()) {
            if ("start".equals(node.getNodeType())) {
                startNode = node;
                break;
            }
        }
        if (startNode == null) {
            return false;
        }

        // 收集所有从 start 节点出发的边
        List<FlowEdgeBo> outEdges = new ArrayList<>();
        for (FlowEdgeBo edge : snapshot.edges) {
            if (edge.getFromNodeId().equals(startNode.getId())) {
                outEdges.add(edge);
            }
        }
        if (outEdges.isEmpty()) {
            return false;
        }

        // 检查所有出边的目标节点是否都是 end 类型
        for (FlowEdgeBo edge : outEdges) {
            FlowNodeBo targetNode = snapshot.nodeMap.get(edge.getToNodeId());
            if (targetNode == null || !"end".equals(targetNode.getNodeType())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 第三阶段：调入处理
     *
     * <p>遍历请求中全部调入方向的调库项，逐项判断流程是否为直通（start→end），
     * 决定写入 ip_pool_status（直接生效）还是 ip_adjust_log（需审批）。
     *
     * @param req    调库提交请求
     * @param shared 本次提交的共享数据
     * @return 本次调入处理生成的所有记录 ID（含 pool_status 和 adjust_log）
     */
    private List<Long> executeInboundSubmit(
            SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {

        List<Long> generatedIds = new ArrayList<>();

        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!"调入".equals(item.getAdjustMode())) {
                continue;
            }

            // 解析该调库项对应的流程快照
            Long flowId = resolveFlowIdFromItem(item);
            FlowSnapshot snapshot = flowId != null ? shared.flowSnapshotMap.get(flowId) : null;

            // 判断是否为直通流程
            boolean isDirect = snapshot != null && isDirectFlow(snapshot);

            if (isDirect) {
                // 直通流程：直接写入 ip_pool_status（audit_status='20'，即时生效）
                IpAdjustLogBo bo = buildAdjustLog(req, item);
                bo.setAuditStatus("20");
                securityPoolAdjustMapper.addPoolStatus(bo);
                generatedIds.add(bo.getId());
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，待审核）
                IpAdjustLogBo bo = buildAdjustLog(req, item);
                bo.setAuditStatus("00");
                securityPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
            }
        }

        return generatedIds;
    }

    /**
     * 第四阶段：调出处理
     *
     * <p>遍历请求中全部调出方向的调库项，逐项判断流程是否为直通（start→end），
     * 决定软删除 ip_pool_status 记录（直接生效）还是写入 ip_adjust_log（需审批）。
     *
     * @param req    调库提交请求
     * @param shared 本次提交的共享数据
     * @return 本次调出处理生成的所有记录 ID（直通无新 ID，仅含 adjust_log 的 ID）
     */
    private List<Long> executeOutboundSubmit(
            SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {

        List<Long> generatedIds = new ArrayList<>();

        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!"调出".equals(item.getAdjustMode())) {
                continue;
            }

            // 解析该调库项对应的流程快照
            Long flowId = resolveFlowIdFromItem(item);
            FlowSnapshot snapshot = flowId != null ? shared.flowSnapshotMap.get(flowId) : null;

            // 判断是否为直通流程
            boolean isDirect = snapshot != null && isDirectFlow(snapshot);

            if (isDirect) {
                // 直通流程：软删除 ip_pool_status 中该证券在目标池的有效记录
                securityPoolAdjustMapper.softDeletePoolStatus(
                        req.getSecurityCode(), item.getTargetPoolId());
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，待审核）
                IpAdjustLogBo bo = buildAdjustLog(req, item);
                bo.setAuditStatus("00");
                securityPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
            }
        }

        return generatedIds;
    }

    /**
     * 第五阶段：后续处理（预留扩展点）
     *
     * <p>当前为空实现，预留给未来可能的扩展需求：通知发送、缓存刷新、事件记录等。
     *
     * @param req    调库提交请求
     * @param shared 本次提交的共享数据
     */
    private void postSubmitProcess(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        // 预留扩展点，当前无操作
    }

    // ═══════════════════════════════════════════════════════════
    //  查询类接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询证券的历史调库记录列表（全量，不分页）
     *
     * @param req 需携带 securityCode
     */
    public List<AdjustLogDto> queryAdjustLogList(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        List<IpAdjustLogBo> logs = securityPoolAdjustMapper.queryAdjustLogList(req.getSecurityCode());
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        // 加载全量投资池用于构建"父级/子级"路径名称
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools != null) {
            for (InvestmentPoolBo p : allPools) {
                poolMap.put(p.getId(), p);
            }
        }

        List<AdjustLogDto> result = new ArrayList<>();
        for (IpAdjustLogBo log : logs) {
            AdjustLogDto dto = new AdjustLogDto();
            dto.setId(log.getId());
            dto.setPoolPath(buildPoolPath(log.getTargetPoolId(), poolMap));
            dto.setAdjustType(log.getAdjustType());
            dto.setAdjustMode(log.getAdjustMode());
            dto.setAttachmentFiles(log.getAttachmentFiles());
            dto.setMaterialFiles(log.getMaterialFiles());
            dto.setAuditStatus(log.getAuditStatus());
            dto.setAdjustReason(log.getAdjustReason());
            dto.setAdjustAdvice(log.getAdjustAdvice());
            dto.setSubmitTime(log.getSubmitTime());
            result.add(dto);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 入口
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验证券调库可行性（核心入口）
     *
     * <p>分四个阶段顺序执行：
     * <ol>
     *   <li><b>前置校验</b>：对请求入参做合法性检查，无 DB 操作，不通过直接抛异常</li>
     *   <li><b>参数初始化</b>：集中执行所有 DB 查询，将本次调用所需数据封装为
     *       {@link AdjustSharedData}，后续阶段直接读取，不再单独查库</li>
     *   <li><b>调入校验</b>：处理全部调入方向的手动项（含前置规则 + 调入规则），
     *       并根据池关系自动追加联动调入项、互斥配套调出项</li>
     *   <li><b>调出校验</b>：处理全部调出方向的手动项（含前置规则 + 调出规则），
     *       并根据池关系自动追加联动调出项（已被第三阶段覆盖的自动项跳过，不重复生成）</li>
     * </ol>
     *
     * @param req 包含证券代码和一个或多个用户选择的调库项
     * @return 包含手动项和自动项的完整校验结果列表
     */
    public AdjustCheckDto checkAdjust(AdjustCheckReq req) {

        // ══ 第一阶段：前置校验 ══
        validateCheckAdjustReq(req);

        // ══ 第二阶段：参数初始化 ══
        AdjustSharedData shared = loadSharedData(req);

        // 预先将全部手动选择项注册到"已覆盖"集合，确保第三、四阶段生成的自动项
        // 不会与用户手动选择的项产生重复结果（手动项优先，自动项跳过已覆盖的 key）
        Set<String> coveredKeys = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            coveredKeys.add(item.getTargetPoolId() + "_" + item.getAdjustMode());
        }

        // ══ 第三阶段：调入校验 ══
        List<AdjustCheckDto.CheckResultItem> resultItems = new ArrayList<>(
                executeInAdjustCheck(req, shared, coveredKeys));

        // ══ 第四阶段：调出校验 ══
        resultItems.addAll(executeOutAdjustCheck(req, shared, coveredKeys));

        // ══ 第五阶段：流程类型判断 ══
        List<AdjustCheckDto.FlowOption> flowOptions = resolveAdjustFlowOptions(req, shared, resultItems);

        AdjustCheckDto dto = new AdjustCheckDto();
        dto.setItems(resultItems);
        dto.setFlowOptions(flowOptions);
        for (AdjustCheckDto.FlowOption option : flowOptions) {
            if (option.isRecommended()) {
                dto.setRecommendedFlowId(option.getFlowId());
                dto.setRecommendedFlowKey(option.getFlowKey());
                dto.setRecommendedFlowType(option.getFlowType());
                break;
            }
        }
        return dto;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 四阶段实现
    // ═══════════════════════════════════════════════════════════

    /**
     * 第一阶段：前置校验
     *
     * <p>对请求入参进行合法性检查，均为内存操作，不涉及任何 DB 查询。
     * 任意一项不通过则直接抛出异常，终止后续流程。
     *
     * @param req 调库校验请求
     */
    private void validateCheckAdjustReq(AdjustCheckReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
    }

    /**
     * 第二阶段：参数初始化
     *
     * <p>集中执行本次校验所需的全部 DB 查询，构建 {@link AdjustSharedData}。
     * 第三、四阶段直接从 shared 中读取数据，无需重复查库。
     * 证券存在性校验也在此阶段完成（查到 null 则抛异常）。
     *
     * @param req 调库校验请求（需携带 securityId、securityCode）
     * @return 封装了本次校验全量共享数据的 AdjustSharedData
     */
    private AdjustSharedData loadSharedData(AdjustCheckReq req) {
        // 证券基础信息（兼含存在性校验）
        SecurityInfoBo securityInfo = securityPoolAdjustMapper.querySecurityDetail(req.getSecurityId());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }

        // 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools != null) {
            for (InvestmentPoolBo p : allPools) {
                poolMap.put(p.getId(), p);
            }
        }

        // 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
        List<Long> currentPoolIdList = securityPoolAdjustMapper.querySecurityCurrentPoolIds(req.getSecurityCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList != null ? currentPoolIdList : Collections.emptyList());

        // 全量投资池关系配置，构建三层嵌套 Map（poolId → relationType → 关联池列表）
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                securityPoolAdjustMapper.queryAllPoolRelations());

        // 提取本次请求中调入/调出各自涉及的目标池 ID 集合，供互斥冲突校验使用
        Set<Long> requestInPoolIds  = new HashSet<>();
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if ("调入".equals(item.getAdjustMode())) {
                requestInPoolIds.add(item.getTargetPoolId());
            } else if ("调出".equals(item.getAdjustMode())) {
                requestOutPoolIds.add(item.getTargetPoolId());
            }
        }

        // 证券级别的状态标志（整个请求期间保持不变，统一在此查询）
        return new AdjustSharedData(
                securityInfo,
                poolMap,
                currentPoolIds,
                poolRelationMap,
                securityPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()),
                securityPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()),
                securityPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()),
                requestInPoolIds,
                requestOutPoolIds
        );
    }

    /**
     * 第三阶段：调入校验
     *
     * <p>遍历请求中全部调入方向的手动项，逐项执行前置校验 + 调入校验，
     * 并根据目标池的关系配置自动追加以下两类附加项：
     * <ul>
     *   <li><b>联动调入项（linkage）</b>：目标池配置的联动池（in_linked）需同步调入</li>
     *   <li><b>互斥配套调出项（mutex）</b>：若证券当前已在目标池的互斥池（in_mutex）中，
     *       需同步调出该互斥池，两池不可同时持有</li>
     * </ul>
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调入方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeInAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (!"调入".equals(item.getAdjustMode())) {
                continue;
            }

            // 手动调入项：前置校验 + 调入校验
            int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
            AdjustCheckContext ctx = buildCheckContext(item, poolCurrentCount, shared);
            List<String> failures = checkInConditions(ctx);

            AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
            resultItem.setTargetPoolId(item.getTargetPoolId());
            resultItem.setPoolName(buildPoolPath(item.getTargetPoolId(), shared.poolMap));
            resultItem.setPoolType(item.getPoolType());
            resultItem.setAdjustMode("调入");
            resultItem.setItemTag("manual");
            resultItem.setCanAdjust(failures.isEmpty());
            resultItem.setFailReasons(failures);
            results.add(resultItem);

            // 取出目标池的关系配置，用于生成自动项
            Map<String, List<Long>> relations = shared.poolRelationMap.getOrDefault(
                    item.getTargetPoolId(), Collections.emptyMap());

            // 联动调入：目标池调入时，其联动池（in_linked）需同步调入
            List<Long> inLinkage = relations.get(REL_IN_LINKAGE);
            if (inLinkage != null) {
                for (Long linkedId : inLinkage) {
                    // coveredKeys.add 返回 true 表示该 key 是首次出现，生成自动项
                    if (coveredKeys.add(linkedId + "_调入")) {
                        results.add(buildAutoResultItem(linkedId, "调入", "linkage", shared));
                    }
                }
            }

            // 互斥配套调出：目标池调入时，若证券已在其互斥池（in_mutex）中，
            // 则需同步调出该互斥池，由系统自动追加调出校验项
            List<Long> inMutex = relations.get(REL_IN_MUTEX);
            if (inMutex != null) {
                for (Long mutexId : inMutex) {
                    // 证券不在该互斥池中则无需生成配套调出项
                    if (!shared.currentPoolIds.contains(mutexId)) {
                        continue;
                    }
                    if (coveredKeys.add(mutexId + "_调出")) {
                        results.add(buildAutoResultItem(mutexId, "调出", "mutex", shared));
                    }
                }
            }
        }

        return results;
    }

    /**
     * 第四阶段：调出校验
     *
     * <p>遍历请求中全部调出方向的手动项，逐项执行前置校验 + 调出校验，
     * 并根据目标池的关系配置自动追加以下附加项：
     * <ul>
     *   <li><b>联动调出项（linkage）</b>：目标池配置的联动池（out_linked）需同步调出</li>
     * </ul>
     *
     * <p>注意：第三阶段由互斥关系生成的调出项已加入 coveredKeys，
     * 若调出方向的手动项恰好与之重叠，以手动项为准（手动项在 coveredKeys 初始化时已注册，
     * 不会被跳过）；联动调出自动项若已被覆盖则跳过。
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调出方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeOutAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (!"调出".equals(item.getAdjustMode())) {
                continue;
            }

            // 手动调出项：前置校验 + 调出校验
            int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
            AdjustCheckContext ctx = buildCheckContext(item, poolCurrentCount, shared);
            List<String> failures = checkOutConditions(ctx);

            AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
            resultItem.setTargetPoolId(item.getTargetPoolId());
            resultItem.setPoolName(buildPoolPath(item.getTargetPoolId(), shared.poolMap));
            resultItem.setPoolType(item.getPoolType());
            resultItem.setAdjustMode("调出");
            resultItem.setItemTag("manual");
            resultItem.setCanAdjust(failures.isEmpty());
            resultItem.setFailReasons(failures);
            results.add(resultItem);

            // 联动调出：目标池调出时，其联动池（out_linked）需同步调出
            Map<String, List<Long>> relations = shared.poolRelationMap.getOrDefault(
                    item.getTargetPoolId(), Collections.emptyMap());
            List<Long> outLinkage = relations.get(REL_OUT_LINKAGE);
            if (outLinkage != null) {
                for (Long linkedId : outLinkage) {
                    if (coveredKeys.add(linkedId + "_调出")) {
                        results.add(buildAutoResultItem(linkedId, "调出", "linkage", shared));
                    }
                }
            }
        }

        return results;
    }

    /**
     * 第五阶段：判断本次调库可选择的审批流程。
     *
     * <p>当前只考虑债券调入：信用债已在库返回上调/下调单流程，信用债未在库返回白名单/简易/普通三类候选，
     * 非信用债大库返回普通流程。白名单和简易规则按伪代码保留入口，不改表结构。</p>
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param resultItems 前四阶段的校验结果
     * @return 流程候选项
     */
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptions(
            AdjustCheckReq req, AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> resultItems) {

        InvestmentPoolBo targetPool = findFirstValidManualInboundTargetPool(resultItems, shared);
        if (targetPool == null) {
            return new ArrayList<>();
        }

        if (!isCreditBondPool(targetPool, shared)) {
            return Collections.singletonList(buildPoolFlowOption(
                    FLOW_TYPE_NORMAL_INBOUND,
                    "普通流程",
                    targetPool.getInFlowId(),
                    targetPool.getInFlowKey(),
                    targetPool.getInFlowName(),
                    true,
                    true,
                    true,
                    Collections.singletonList("目标池非信用债大库，走普通流程"),
                    new ArrayList<>()));
        }

        if (isSecurityInCreditBondPool(shared)) {
            String flowType = resolveCreditBondAdjustFlowType(targetPool, shared);
            String flowName = FLOW_TYPE_DOWNGRADE_INBOUND.equals(flowType) ? "下调流程" : "上调流程";
            return Collections.singletonList(buildPoolFlowOption(
                    flowType,
                    flowName,
                    targetPool.getInFlowId(),
                    targetPool.getInFlowKey(),
                    targetPool.getInFlowName(),
                    true,
                    true,
                    true,
                    Collections.singletonList("证券已在信用债大库中，按当前库位与目标库位判断上调/下调"),
                    new ArrayList<>()));
        }

        List<String> whitelistMatchReasons = new ArrayList<>();
        List<String> whitelistUnmatchReasons = new ArrayList<>();
        boolean whitelistMatched = isWhitelistFlowMatched(req, shared, whitelistMatchReasons, whitelistUnmatchReasons);

        List<String> simpleMatchReasons = new ArrayList<>();
        List<String> simpleUnmatchReasons = new ArrayList<>();
        boolean simpleMatched = isSimpleInboundFlowMatched(req, shared, simpleMatchReasons, simpleUnmatchReasons);

        String recommendedType = whitelistMatched ? FLOW_TYPE_WHITELIST_INBOUND
                : (simpleMatched ? FLOW_TYPE_SIMPLE_INBOUND : FLOW_TYPE_NORMAL_INBOUND);

        List<AdjustCheckDto.FlowOption> options = new ArrayList<>();
        options.add(buildWhitelistFlowOption(
                FLOW_TYPE_WHITELIST_INBOUND.equals(recommendedType),
                whitelistMatched,
                whitelistMatchReasons,
                whitelistUnmatchReasons));
        options.add(buildPoolFlowOption(
                FLOW_TYPE_SIMPLE_INBOUND,
                "简易流程",
                targetPool.getSimpleInFlowId(),
                targetPool.getSimpleInFlowKey(),
                targetPool.getSimpleInFlowName(),
                FLOW_TYPE_SIMPLE_INBOUND.equals(recommendedType),
                simpleMatched,
                simpleMatched,
                simpleMatchReasons,
                simpleUnmatchReasons));
        options.add(buildPoolFlowOption(
                FLOW_TYPE_NORMAL_INBOUND,
                "普通流程",
                targetPool.getInFlowId(),
                targetPool.getInFlowKey(),
                targetPool.getInFlowName(),
                FLOW_TYPE_NORMAL_INBOUND.equals(recommendedType),
                true,
                true,
                Collections.singletonList("证券不在信用债大库中，普通流程始终可选"),
                new ArrayList<>()));
        return options;
    }

    /**
     * 查找第一个可提交的手工调入目标池。
     */
    private InvestmentPoolBo findFirstValidManualInboundTargetPool(
            List<AdjustCheckDto.CheckResultItem> resultItems, AdjustSharedData shared) {
        if (resultItems == null || resultItems.isEmpty()) {
            return null;
        }
        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            if ("manual".equals(item.getItemTag()) && "调入".equals(item.getAdjustMode()) && item.isCanAdjust()) {
                return shared.poolMap.get(item.getTargetPoolId());
            }
        }
        return null;
    }

    /**
     * 白名单流程命中判断入口。
     *
     * <p>伪代码口径：
     * 1. 剩余期限 <= 3 年；
     * 2. 排除永续债、私募债、ABS 债；
     * 3. 债券类型属于债券类；
     * 4. 债券主体在白名单配置池中；
     * 5. 债券不是担保债。</p>
     *
     * <p>当前版本不新增白名单配置表、不修改表结构，因此仅保留入口并返回未命中。</p>
     */
    private boolean isWhitelistFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, List<String> matchReasons, List<String> unmatchReasons) {

        unmatchReasons.add("白名单流程伪代码入口已保留：优先回售/行权日，否则到期日，剩余期限需 <= 1895 天");
        unmatchReasons.add("待接入真实 SQL：排除永续债/私募债/ABS，主体在 WHITEPOOLID_XYJJ，ptype=4000，bonddt52='0' 或为空");
        return false;
    }

    /**
     * 简易流程命中判断入口。
     *
     * <p>伪代码口径：
     * 1. 剩余期限合理；
     * 2. 处于一年有效期内；
     * 3. 主体评级和展望评级均未下调；
     * 4. 无担保人，或担保人评级也未下调。</p>
     *
     * <p>当前证券表没有评级历史和评级有效期字段，因此仅保留入口并返回未命中。</p>
     */
    private boolean isSimpleInboundFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, List<String> matchReasons, List<String> unmatchReasons) {

        unmatchReasons.add("简易流程伪代码入口已保留：待接入 IP_SIMPLEWORKFLOWDATE 一年有效期校验");
        unmatchReasons.add("待接入 sdc_sirm_bondcompanylevel 主体/展望/担保人评级历史对比，确认评级未下调");
        return false;
    }

    /**
     * 判断信用债在库调整流程是上调还是下调。
     *
     * <p>同一父级下，目标池 innerSort 小于当前所在池为升库，大于当前所在池为降库；
     * 未找到同父级当前池时，默认按升库入库流程处理。</p>
     */
    private String resolveCreditBondAdjustFlowType(InvestmentPoolBo targetPool, AdjustSharedData shared) {
        if (targetPool == null || targetPool.getParentId() == null || targetPool.getInnerSort() == null) {
            return FLOW_TYPE_UPGRADE_INBOUND;
        }

        for (Long currentPoolId : shared.currentPoolIds) {
            InvestmentPoolBo currentPool = shared.poolMap.get(currentPoolId);
            if (currentPool == null || currentPool.getParentId() == null || currentPool.getInnerSort() == null) {
                continue;
            }
            if (!targetPool.getParentId().equals(currentPool.getParentId())) {
                continue;
            }
            if (targetPool.getInnerSort() > currentPool.getInnerSort()) {
                return FLOW_TYPE_DOWNGRADE_INBOUND;
            }
            if (targetPool.getInnerSort() < currentPool.getInnerSort()) {
                return FLOW_TYPE_UPGRADE_INBOUND;
            }
        }
        return FLOW_TYPE_UPGRADE_INBOUND;
    }

    /**
     * 判断目标池是否属于信用债大库。
     */
    private boolean isCreditBondPool(InvestmentPoolBo pool, AdjustSharedData shared) {
        if (pool == null) {
            return false;
        }
        if (CREDIT_BOND_ROOT_CODE.equals(pool.getPoolCode()) || CREDIT_BOND_POOL_TYPE.equals(pool.getPoolType())) {
            return true;
        }
        InvestmentPoolBo parent = pool.getParentId() != null ? shared.poolMap.get(pool.getParentId()) : null;
        return parent != null && CREDIT_BOND_ROOT_CODE.equals(parent.getPoolCode());
    }

    /**
     * 判断证券当前是否已在信用债大库中。
     */
    private boolean isSecurityInCreditBondPool(AdjustSharedData shared) {
        for (Long currentPoolId : shared.currentPoolIds) {
            InvestmentPoolBo currentPool = shared.poolMap.get(currentPoolId);
            if (isCreditBondPool(currentPool, shared)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建白名单流程候选项。
     */
    private AdjustCheckDto.FlowOption buildWhitelistFlowOption(
            boolean recommended,
            boolean matched,
            List<String> matchReasons,
            List<String> unmatchReasons) {

        FlowDefinitionBo flow = flowMapper.queryActiveFlowByKey(FLOW_KEY_WHITELIST_INBOUND);
        List<String> reasons = unmatchReasons != null ? new ArrayList<>(unmatchReasons) : new ArrayList<>();
        reasons.add("当前投资池未配置白名单流程字段，暂按已启用流程 Key 查询：" + FLOW_KEY_WHITELIST_INBOUND);
        if (flow == null) {
            reasons.add("未找到已启用流程定义：" + FLOW_KEY_WHITELIST_INBOUND);
        }
        return buildFlowOption(
                FLOW_TYPE_WHITELIST_INBOUND,
                flow != null && flow.getName() != null ? flow.getName() : "白名单流程",
                flow != null ? flow.getId() : null,
                FLOW_KEY_WHITELIST_INBOUND,
                recommended,
                matched,
                matched,
                matchReasons,
                reasons);
    }

    /**
     * 使用目标池配置构建流程候选项。
     */
    private AdjustCheckDto.FlowOption buildPoolFlowOption(
            String flowType,
            String fallbackFlowName,
            Long flowId,
            String flowKey,
            String configuredFlowName,
            boolean recommended,
            boolean matched,
            boolean selectable,
            List<String> matchReasons,
            List<String> unmatchReasons) {

        List<String> reasons = unmatchReasons != null ? new ArrayList<>(unmatchReasons) : new ArrayList<>();
        if (flowId == null && (flowKey == null || flowKey.isEmpty())) {
            reasons.add("目标池未配置该流程，按不需要审批处理");
        }
        return buildFlowOption(
                flowType,
                configuredFlowName != null && !configuredFlowName.isEmpty() ? configuredFlowName : fallbackFlowName,
                flowId,
                flowKey,
                recommended,
                matched,
                selectable,
                matchReasons,
                reasons);
    }

    /**
     * 构建流程候选项。
     */
    private AdjustCheckDto.FlowOption buildFlowOption(
            String flowType,
            String fallbackFlowName,
            Long flowId,
            String flowKey,
            boolean recommended,
            boolean matched,
            boolean selectable,
            List<String> matchReasons,
            List<String> unmatchReasons) {

        AdjustCheckDto.FlowOption option = new AdjustCheckDto.FlowOption();
        option.setFlowType(flowType);
        option.setFlowId(flowId);
        option.setFlowKey(flowKey);
        option.setFlowName(fallbackFlowName);
        option.setRecommended(recommended);
        option.setMatched(matched);
        option.setSelectable(selectable);
        option.setMatchReasons(matchReasons != null ? matchReasons : new ArrayList<>());
        List<String> reasons = unmatchReasons != null ? new ArrayList<>(unmatchReasons) : new ArrayList<>();
        option.setUnmatchReasons(reasons);
        return option;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 方向校验入口（含前置规则）
    // ═══════════════════════════════════════════════════════════

    /**
     * 调入校验：前置规则 + 调入方向规则
     *
     * <p>校验顺序：证券到期 → 流程进行中 → 重复入池 → 容量上限 → 来源池 → 调入限制池 → 调入弹性禁投池
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkInConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 前置检查：证券是否已到期
        addIfFailed(failures, preCheckSecurityExpired(ctx));
        // 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 入池检查：证券是否已在目标池中
        addIfFailed(failures, inCheckSecurityAlreadyInPool(ctx));
        // 入池检查：目标池容量是否已满
        addIfFailed(failures, inCheckPoolCapacity(ctx));
        // 入池检查：来源池是否满足要求
        addIfFailed(failures, inCheckSourcePool(ctx));
        // 入池检查：是否触碰禁投池限制
        addIfFailed(failures, inCheckRestrictPool(ctx));
        // 入池检查：本次请求中是否同时勾选了互斥池（不可同时调入）
        addIfFailed(failures, inCheckMutexConflict(ctx));
        // 入池检查：是否满足弹性池条件
        addIfFailed(failures, inCheckElasticPool(ctx));
        return failures;
    }

    /**
     * 调出校验：前置规则 + 调出方向规则
     *
     * <p>校验顺序：证券到期 → 流程进行中 → 未入池 → 调出限制池 → 调出互斥池 → 调出弹性禁投池
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkOutConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 前置检查：证券是否已到期
        addIfFailed(failures, preCheckSecurityExpired(ctx));
        // 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 出池检查：证券是否不在来源池中
        addIfFailed(failures, outCheckSecurityNotInPool(ctx));
        // 出池检查：是否触碰禁投池限制
        addIfFailed(failures, outCheckRestrictPool(ctx));
        // 出池检查：是否与互斥池冲突（证券当前在互斥池中）
        addIfFailed(failures, outCheckMutexPool(ctx));
        // 出池检查：本次请求中是否同时勾选了互斥池（不可同时调出）
        addIfFailed(failures, outCheckMutexConflict(ctx));
        // 出池检查：是否满足弹性池条件
        addIfFailed(failures, outCheckElasticPool(ctx));
        return failures;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 前置校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：证券是否已到期
     *
     * <p>到期日（b_info_maturitydate）早于今日则视为已到期，禁止发起任何调库操作。
     */
    private String preCheckSecurityExpired(AdjustCheckContext ctx) {
        String maturityDate = ctx.getSecurityInfo().getBInfoMaturitydate();
        if (maturityDate != null && !maturityDate.isEmpty()) {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (maturityDate.compareTo(today) < 0) {
                return "证券已到期，不支持调库操作";
            }
        }
        return null;
    }

    /**
     * 规则：证券是否存在进行中的调库流程
     *
     * <p>audit_status 为"00（待审核）"或"11（驳回待修改）"的记录视为进行中，
     * 需等待该流程终结后方可再次发起调库，避免并发操作导致入池状态混乱。
     */
    private String preCheckPendingProcess(AdjustCheckContext ctx) {
        if (ctx.isHasPendingProcess()) {
            return "证券存在进行中的调库流程（待审核或驳回待修改），请等待流程结束后再发起调库";
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 调入校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：证券是否已在目标池中
     *
     * <p>以 ip_pool_status 中 audit_status='20' 的有效记录为准，重复调入无实际意义。
     */
    private String inCheckSecurityAlreadyInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId != null && ctx.getCurrentPoolIds().contains(poolId)) {
            return "证券已在目标投资池中，无需重复调入";
        }
        return null;
    }

    /**
     * 规则：目标池是否已达持仓上限
     *
     * <p>maxCapacity 为 null 或 0 时表示不限制数量；
     * 当前有效证券数量（poolCurrentCount）≥ 上限时拒绝调入。
     */
    private String inCheckPoolCapacity(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool != null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                && ctx.getPoolCurrentCount() >= pool.getMaxCapacity()) {
            return "目标投资池已达持仓上限（" + pool.getMaxCapacity() + "），无法调入";
        }
        return null;
    }

    /**
     * 规则：来源池限制
     *
     * <p>若目标池配置了来源池（source），则证券必须当前已在其中至少一个来源池中，
     * 满足"从低级库向高级库晋升"等分层调库规则。未配置来源池时不做限制。
     */
    private String inCheckSourcePool(AdjustCheckContext ctx) {
        List<Long> sourcePools = ctx.getTargetPoolRelations().get(REL_SOURCE);
        if (sourcePools == null || sourcePools.isEmpty()) {
            return null;
        }
        boolean inAny = sourcePools.stream().anyMatch(ctx.getCurrentPoolIds()::contains);
        if (!inAny) {
            return "目标池配置了来源池限制，证券须先在以下池中：" + poolNames(sourcePools, ctx);
        }
        return null;
    }

    /**
     * 规则：调入限制池（in_restrict）
     *
     * <p>证券当前在配置的限制池中，则不允许调入目标池。
     */
    private String inCheckRestrictPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_IN_RESTRICT, "调入限制池");
    }

    /**
     * 规则：调入弹性禁投池（in_soft_restrict）
     *
     * <p>弹性禁投池与硬性限制池的校验逻辑相同，但语义上表示"柔性"限制，
     * 业务层面可由特定角色绕过（绕过逻辑在审批流程中处理，此处只做初步拦截）。
     */
    private String inCheckElasticPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_IN_ELASTIC, "调入弹性禁投池");
    }

    /**
     * 规则：同一请求中同时勾选了互斥调入项
     *
     * <p>若目标池配置了互斥池（in_mutex），且本次请求中同时存在对这些互斥池的调入操作，
     * 则两者不可并存，均应失败。例如：信用债大库/一级库与专户产品/一级库互斥，
     * 不可同时勾选"调入信用债大库/一级库"和"调入专户产品/一级库"。
     */
    private String inCheckMutexConflict(AdjustCheckContext ctx) {
        List<Long> inMutex = ctx.getTargetPoolRelations().get(REL_IN_MUTEX);
        if (inMutex == null || inMutex.isEmpty()) {
            return null;
        }
        List<Long> conflicting = inMutex.stream()
                .filter(id -> ctx.getRequestInPoolIds().contains(id))
                .collect(Collectors.toList());
        if (!conflicting.isEmpty()) {
            return "与以下互斥池不可同时调入：" + poolNames(conflicting, ctx);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 调出校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：证券是否不在目标池中
     *
     * <p>不在池中则无法调出，属于基础前置判断，避免操作无效数据。
     */
    private String outCheckSecurityNotInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId == null || !ctx.getCurrentPoolIds().contains(poolId)) {
            return "证券当前不在该投资池中，无法调出";
        }
        return null;
    }

    /**
     * 规则：调出限制池（out_restrict）
     *
     * <p>证券当前在配置的限制池中，则不允许从目标池调出。
     */
    private String outCheckRestrictPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_RESTRICT, "调出限制池");
    }

    /**
     * 规则：调出互斥池（out_mutex）
     *
     * <p>证券当前在配置的互斥池中，则不允许从目标池调出。
     * 注意：此处校验的是"调出方向的互斥"，与调入互斥池（in_mutex）含义不同：
     * in_mutex 用于在第三阶段自动生成配套调出项；out_mutex 用于阻止调出操作本身。
     */
    private String outCheckMutexPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_MUTEX, "调出互斥池");
    }

    /**
     * 规则：调出弹性禁投池（out_soft_restrict）
     *
     * <p>证券当前在配置的弹性禁投池中，则不允许从目标池调出。
     */
    private String outCheckElasticPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_ELASTIC, "调出弹性禁投池");
    }

    /**
     * 规则：同一请求中同时勾选了互斥调出项
     *
     * <p>若目标池配置了互斥池（in_mutex），且本次请求中同时存在对这些互斥池的调出操作，
     * 则两者不可并存，均应失败。互斥池对代表"不可同时持有"，
     * 因此也不应在同一批次中同时对两池执行调出操作。
     */
    private String outCheckMutexConflict(AdjustCheckContext ctx) {
        List<Long> inMutex = ctx.getTargetPoolRelations().get(REL_IN_MUTEX);
        if (inMutex == null || inMutex.isEmpty()) {
            return null;
        }
        List<Long> conflicting = inMutex.stream()
                .filter(id -> ctx.getRequestOutPoolIds().contains(id))
                .collect(Collectors.toList());
        if (!conflicting.isEmpty()) {
            return "与以下互斥池不可同时调出：" + poolNames(conflicting, ctx);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 私有工具方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建单个调库项的校验上下文（{@link AdjustCheckContext}）
     *
     * <p>负责将"本次校验共享数据"与"当次调库项的目标池信息"合并填充到上下文中，
     * 供三层校验方法直接读取，无需再逐级传参。
     *
     * @param item             当前调库项（目标池 ID、调整方向等）
     * @param poolCurrentCount 目标池当前有效证券数量（调用方按需查询）
     * @param shared           本次 checkAdjust 调用的共享数据
     */
    private AdjustCheckContext buildCheckContext(AdjustCheckReq.CheckItem item, int poolCurrentCount, AdjustSharedData shared) {
        // 从全量投资池 Map 中取出目标池及其父级池（用于构建错误消息中的池路径）
        InvestmentPoolBo targetPool = shared.poolMap.get(item.getTargetPoolId());
        InvestmentPoolBo parentPool = (targetPool != null && targetPool.getParentId() != null)
                ? shared.poolMap.get(targetPool.getParentId()) : null;

        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(shared.securityInfo);
        ctx.setTargetPool(targetPool);
        ctx.setParentPool(parentPool);
        ctx.setCurrentPoolIds(shared.currentPoolIds);
        ctx.setPoolCurrentCount(poolCurrentCount);
        ctx.setAdjustMode(item.getAdjustMode());
        // 取出目标池自身的所有关系配置（来源池、限制池、联动池等），供关系型校验规则直接使用
        ctx.setTargetPoolRelations(shared.poolRelationMap.getOrDefault(item.getTargetPoolId(), Collections.emptyMap()));
        ctx.setPoolMap(shared.poolMap);
        ctx.setHasPendingProcess(shared.hasPendingProcess);
        ctx.setSecurityInObservePool(shared.securityInObservePool);
        ctx.setIssuerInObservePool(shared.issuerInObservePool);
        ctx.setRequestInPoolIds(shared.requestInPoolIds);
        ctx.setRequestOutPoolIds(shared.requestOutPoolIds);
        return ctx;
    }

    /**
     * 构建自动生成的联动/互斥调整项校验结果
     *
     * <p>与手动项校验流程完全相同，区别在于：
     * <ul>
     *   <li>targetPoolId 来自池关系配置，由系统自动推导，非用户选择</li>
     *   <li>itemTag 标记为"linkage"或"mutex"，前端据此区分显示样式</li>
     * </ul>
     *
     * @param targetPoolId 自动生成项的目标池 ID
     * @param adjustMode   调整方向（"调入"或"调出"）
     * @param itemTag      来源标签：linkage（联动）/ mutex（互斥）
     * @param shared       本次 checkAdjust 调用的共享数据
     */
    private AdjustCheckDto.CheckResultItem buildAutoResultItem(
            Long targetPoolId, String adjustMode, String itemTag, AdjustSharedData shared) {

        int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(targetPoolId);
        InvestmentPoolBo pool = shared.poolMap.get(targetPoolId);

        // 构造虚拟 CheckItem，使其可以复用 buildCheckContext 逻辑
        AdjustCheckReq.CheckItem fakeItem = new AdjustCheckReq.CheckItem();
        fakeItem.setTargetPoolId(targetPoolId);
        fakeItem.setAdjustMode(adjustMode);
        fakeItem.setPoolType(pool != null ? pool.getPoolType() : null);

        AdjustCheckContext ctx = buildCheckContext(fakeItem, poolCurrentCount, shared);

        List<String> failures = "调入".equals(adjustMode) ? checkInConditions(ctx) : checkOutConditions(ctx);

        AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
        resultItem.setTargetPoolId(targetPoolId);
        resultItem.setPoolName(buildPoolPath(targetPoolId, shared.poolMap));
        resultItem.setPoolType(pool != null ? pool.getPoolType() : null);
        resultItem.setAdjustMode(adjustMode);
        resultItem.setItemTag(itemTag);
        resultItem.setCanAdjust(failures.isEmpty());
        resultItem.setFailReasons(failures);
        return resultItem;
    }

    /**
     * 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * @param relationType 关系类型常量（REL_IN_RESTRICT / REL_OUT_RESTRICT 等）
     * @param label        用于错误消息的中文标签（如"调入限制池"）
     */
    private String checkBlockedByPools(AdjustCheckContext ctx, String relationType, String label) {
        List<Long> relPools = ctx.getTargetPoolRelations().get(relationType);
        if (relPools == null || relPools.isEmpty()) {
            return null;
        }
        // 取出"证券当前在其中"的阻断池
        List<Long> blocked = relPools.stream()
                .filter(ctx.getCurrentPoolIds()::contains)
                .collect(Collectors.toList());
        if (!blocked.isEmpty()) {
            return "证券在" + label + "中，无法操作：" + poolNames(blocked, ctx);
        }
        return null;
    }

    /**
     * 构建全量投资池关系 Map
     *
     * <p>结构：poolId → relationType → 关联池 ID 列表，三层嵌套以支持快速按（池ID + 关系类型）查询。
     *
     * @param relations 从数据库查出的原始关系记录列表
     */
    private Map<Long, Map<String, List<Long>>> buildPoolRelationMap(List<PoolRelationBo> relations) {
        Map<Long, Map<String, List<Long>>> map = new HashMap<>();
        if (relations == null) {
            return map;
        }
        for (PoolRelationBo r : relations) {
            map.computeIfAbsent(r.getPoolId(), k -> new HashMap<>())
               .computeIfAbsent(r.getRelationType(), k -> new ArrayList<>())
               .add(r.getRelationPoolId());
        }
        return map;
    }

    /**
     * 将池 ID 列表转为"父级/子级"路径名称，以顿号连接，用于构建可读的失败原因消息
     *
     * @param poolIds 需要转换的池 ID 列表
     * @param ctx     上下文（内含全量投资池 Map）
     */
    private String poolNames(List<Long> poolIds, AdjustCheckContext ctx) {
        return poolIds.stream()
                .map(id -> buildPoolPath(id, ctx.getPoolMap()))
                .collect(Collectors.joining("、"));
    }

    /**
     * 若 reason 非 null 则追加到失败原因列表（避免调用方每次判空）
     *
     * @param failures 失败原因集合
     * @param reason   单条校验返回的失败原因，null 表示通过
     */
    private void addIfFailed(List<String> failures, String reason) {
        if (reason != null) {
            failures.add(reason);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  对象转换方法
    // ═══════════════════════════════════════════════════════════

    /**
     * SecurityInfoBo → SecurityInfoDto（证券列表行数据，字段精简）
     */
    private SecurityInfoDto toSecurityInfoDto(SecurityInfoBo bo) {
        SecurityInfoDto d = new SecurityInfoDto();
        d.setId(bo.getId());
        d.setSecurityCode(bo.getSInfoCode());
        d.setSecurityShortName(bo.getSInfoName());
        d.setIssuer(bo.getBInfoIssuer());
        d.setFullName(bo.getBInfoFullname());
        d.setIssueAmount(bo.getBIssueAmountplan());
        d.setCarryDate(bo.getBInfoCarrydate());
        d.setMaturityDate(bo.getBInfoMaturitydate());
        d.setSecurityRating(bo.getRatingSecurity());
        d.setIssuerRating(bo.getRatingSecurityissuer());
        d.setSecurityType(bo.getDSecurityType() != null ? String.valueOf(bo.getDSecurityType()) : null);
        d.setCurrentRate(null);
        d.setTermStr(bo.getDateExists());
        return d;
    }

    /**
     * SecurityInfoBo → SecurityInfoDetailDto（调库页面顶部详情，字段完整）
     */
    private SecurityInfoDetailDto toSecurityInfoDetailDto(SecurityInfoBo bo) {
        SecurityInfoDetailDto d = new SecurityInfoDetailDto();
        d.setFullName(bo.getBInfoFullname());
        d.setSecurityShortName(bo.getSInfoName());
        d.setSecurityCode(bo.getSInfoCode());
        d.setIssuerName(bo.getBInfoIssuer());
        d.setNibCode(bo.getSWindcodeNib());
        // 交易所代码：优先沪市（SH），其次深市（SZ）
        if (bo.getSWindcodeSh() != null && !bo.getSWindcodeSh().isEmpty()) {
            d.setExchangeCode(bo.getSWindcodeSh());
        } else {
            d.setExchangeCode(bo.getSWindcodeSz());
        }
        d.setIssueAmount(bo.getBIssueAmountplan());
        d.setCurrentRate(null);
        d.setRemExeTerm(bo.getDateInrightExists());
        d.setCarryDate(bo.getBInfoCarrydate());
        d.setMaturityDate(bo.getBInfoMaturitydate());
        d.setPledgeRatio(bo.getBInfoPledgeRatio());
        d.setRatingAgency(bo.getRatingSecurityAgency());
        d.setSecurityRating(bo.getRatingSecurity());
        d.setIssuerRating(bo.getRatingSecurityissuer());
        d.setRatingOutlook(bo.getRatingOutlook());
        d.setGuaranteeStatus(bo.getBAgencyGrnttype());
        d.setLeadUnderwriter(bo.getBAgencyName());
        d.setInnerIssuerRating(bo.getInnerIssuerRating());
        d.setSecurityType(bo.getDSecurityType() != null ? String.valueOf(bo.getDSecurityType()) : null);
        d.setPutExeTerm(bo.getDateRedemtionExists());
        d.setCallRemTerm(bo.getDateCallExists());
        d.setInnerGuarantorRating(bo.getInnerGuarantorRating());
        d.setOptRemTerm(bo.getDateInrightExists());
        d.setTermStr(bo.getDateExists());
        d.setFundUsage(bo.getBFundUsage());
        d.setPromptReason(bo.getBPromptReason());
        d.setAnalysis(bo.getBAnalysis());
        return d;
    }

    /**
     * InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
     *
     * @param pool        投资池实体
     * @param inMutexMap  调入互斥关系（poolId → 互斥池 ID 列表）
     * @param outMutexMap 调出互斥关系（poolId → 互斥池 ID 列表）
     */
    private PoolDto toPoolDto(InvestmentPoolBo pool,
                              Map<Long, List<Long>> inMutexMap,
                              Map<Long, List<Long>> outMutexMap) {
        PoolDto dto = new PoolDto();
        dto.setId(pool.getId());
        dto.setParentId(pool.getParentId());
        dto.setPoolName(pool.getPoolName());
        dto.setPoolCode(pool.getPoolCode());
        dto.setPoolType(pool.getPoolType());
        dto.setPoolLevel(pool.getPoolLevel());
        dto.setMaxCapacity(pool.getMaxCapacity());
        dto.setCurrentCount(0);
        dto.setInMutexPoolIds(inMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
        dto.setOutMutexPoolIds(outMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
        return dto;
    }

    /**
     * 构建调库记录实体（ip_adjust_log），由提交申请逻辑调用
     *
     * @param req  调库申请（含证券级别信息）
     * @param item 单个调库项（含目标池、调整方向等）
     */
    private IpAdjustLogBo buildAdjustLog(SecurityPoolAdjustSubmitReq req, SecurityPoolAdjustSubmitReq.AdjustItem item) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        // item 中的 flowId/flowKey/flowType 当前仅作为流程实例接入预留参数，不写入 ip_adjust_log。
        bo.setSecurityCode(req.getSecurityCode());
        bo.setSecurityShortName(req.getSecurityShortName());
        bo.setSecurityType(req.getSecurityType());
        bo.setAdjustType(req.getAdjustType());
        bo.setAdjustMode(item.getAdjustMode());
        bo.setTargetPoolId(item.getTargetPoolId());
        bo.setTargetPoolName(item.getTargetPoolName());
        bo.setPoolType(item.getPoolType());
        bo.setAuditStatus("00");  // 初始状态：待审核
        bo.setAdjusterId(req.getAdjusterId());
        bo.setAdjusterName(req.getAdjusterName());
        bo.setAdjustReason(req.getAdjustReason());
        bo.setAdjustAdvice(req.getAdjustAdvice());
        bo.setAttachmentFiles(item.getAttachmentFiles());
        bo.setMaterialFiles(item.getMaterialFiles());
        return bo;
    }

    /**
     * 构建投资池显示路径（格式：父级名称/子级名称，顶级池只返回池名称）
     *
     * @param poolId  目标池 ID
     * @param poolMap 全量投资池索引 Map
     */
    private String buildPoolPath(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        InvestmentPoolBo pool = poolMap.get(poolId);
        if (pool == null) {
            return "";
        }
        if (pool.getParentId() != null) {
            InvestmentPoolBo parent = poolMap.get(pool.getParentId());
            if (parent != null && parent.getPoolName() != null) {
                return parent.getPoolName() + "/" + (pool.getPoolName() != null ? pool.getPoolName() : "");
            }
        }
        return pool.getPoolName() != null ? pool.getPoolName() : "";
    }

    // ═══════════════════════════════════════════════════════════
    //  内部数据类
    // ═══════════════════════════════════════════════════════════

    /**
     * 本次调库校验（checkAdjust）的共享数据载体
     *
     * <p>checkAdjust 每次调用需要加载多项证券/池的基础数据，这些数据在整个调用过程中保持不变。
     * 将其封装为此类后，buildCheckContext、buildAutoResultItem 等辅助方法只需接收一个
     * shared 参数，既能保持方法签名简洁（≤4 个参数），也便于后续扩展新的校验参数——
     * 新增字段只需改动此类和 loadSharedData 的数据加载部分，辅助方法签名无需调整。
     */
    private static class AdjustSharedData {

        /** 证券基础信息（来自 sirm_securityinfo） */
        final SecurityInfoBo securityInfo;

        /** 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称 */
        final Map<Long, InvestmentPoolBo> poolMap;

        /** 证券当前有效所在池 ID 集合（ip_pool_status.audit_status='20'） */
        final Set<Long> currentPoolIds;

        /** 全量投资池关系配置（poolId → relationType → 关联池 ID 列表） */
        final Map<Long, Map<String, List<Long>>> poolRelationMap;

        /** 证券是否存在进行中的调库流程（audit_status IN ('00','11')） */
        final boolean hasPendingProcess;

        /** 当前证券自身是否在观察池（pool_type='observe'，audit_status='20'） */
        final boolean securityInObservePool;

        /** 证券主体公司（发行人）旗下是否有证券在观察池中 */
        final boolean issuerInObservePool;

        /** 本次请求中所有调入操作涉及的目标池 ID 集合，用于互斥池同时勾选校验 */
        final Set<Long> requestInPoolIds;

        /** 本次请求中所有调出操作涉及的目标池 ID 集合，用于互斥池同时勾选校验 */
        final Set<Long> requestOutPoolIds;

        AdjustSharedData(SecurityInfoBo securityInfo,
                         Map<Long, InvestmentPoolBo> poolMap,
                         Set<Long> currentPoolIds,
                         Map<Long, Map<String, List<Long>>> poolRelationMap,
                         boolean hasPendingProcess,
                         boolean securityInObservePool,
                         boolean issuerInObservePool,
                         Set<Long> requestInPoolIds,
                         Set<Long> requestOutPoolIds) {
            this.securityInfo = securityInfo;
            this.poolMap = poolMap;
            this.currentPoolIds = currentPoolIds;
            this.poolRelationMap = poolRelationMap;
            this.hasPendingProcess = hasPendingProcess;
            this.securityInObservePool = securityInObservePool;
            this.issuerInObservePool = issuerInObservePool;
            this.requestInPoolIds = requestInPoolIds;
            this.requestOutPoolIds = requestOutPoolIds;
        }
    }

    /**
     * 单个流程的运行时快照，聚合流程定义、活跃版本、节点索引和连线列表。
     *
     * <p>用于 {@link #isDirectFlow(FlowSnapshot)} 方法快速判断流程是否为直通（start→end）模式，
     * 避免在第三/四阶段逐项重复查询流程定义、版本、节点和连线。
     */
    private static class FlowSnapshot {

        /** 流程定义 */
        final FlowDefinitionBo definition;

        /** 当前活跃版本 */
        final FlowVersionBo activeVersion;

        /** 节点索引（DB ID → FlowNodeBo），用于连线遍历时快速定位目标节点 */
        final Map<Long, FlowNodeBo> nodeMap;

        /** 该版本的全量连线列表 */
        final List<FlowEdgeBo> edges;

        FlowSnapshot(FlowDefinitionBo definition,
                     FlowVersionBo activeVersion,
                     Map<Long, FlowNodeBo> nodeMap,
                     List<FlowEdgeBo> edges) {
            this.definition = definition;
            this.activeVersion = activeVersion;
            this.nodeMap = nodeMap;
            this.edges = edges;
        }
    }

    /**
     * 本次调库提交（addAdjustLog）的共享数据载体
     *
     * <p>addAdjustLog 每次调用需要加载多项证券/池/流程的基础数据，这些数据在整个调用过程中保持不变。
     * 将其封装为此类后，第三、四阶段的处理方法只需接收一个 shared 参数即可获取所需数据，
     * 避免逐级传递大量参数。
     *
     * <p>与 {@link AdjustSharedData} 的区别：
     * <ul>
     *   <li>不包含 requestInPoolIds / requestOutPoolIds（提交阶段无需互斥冲突校验）</li>
     *   <li>新增 flowSnapshotMap，包含每个引用流程的快照数据</li>
     * </ul>
     */
    private static class SubmitSharedData {

        /** 证券基础信息（来自 sirm_securityinfo） */
        final SecurityInfoBo securityInfo;

        /** 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称 */
        final Map<Long, InvestmentPoolBo> poolMap;

        /** 证券当前有效所在池 ID 集合（ip_pool_status.audit_status='20'） */
        final Set<Long> currentPoolIds;

        /** 全量投资池关系配置（poolId → relationType → 关联池 ID 列表） */
        final Map<Long, Map<String, List<Long>>> poolRelationMap;

        /** 证券是否存在进行中的调库流程（audit_status IN ('00','11')） */
        final boolean hasPendingProcess;

        /** 当前证券自身是否在观察池（pool_type='observe'，audit_status='20'） */
        final boolean securityInObservePool;

        /** 证券主体公司（发行人）旗下是否有证券在观察池中 */
        final boolean issuerInObservePool;

        /** 流程快照索引（flowId → FlowSnapshot），供第三/四阶段快速判断直通流程 */
        final Map<Long, FlowSnapshot> flowSnapshotMap;

        SubmitSharedData(SecurityInfoBo securityInfo,
                         Map<Long, InvestmentPoolBo> poolMap,
                         Set<Long> currentPoolIds,
                         Map<Long, Map<String, List<Long>>> poolRelationMap,
                         boolean hasPendingProcess,
                         boolean securityInObservePool,
                         boolean issuerInObservePool,
                         Map<Long, FlowSnapshot> flowSnapshotMap) {
            this.securityInfo = securityInfo;
            this.poolMap = poolMap;
            this.currentPoolIds = currentPoolIds;
            this.poolRelationMap = poolRelationMap;
            this.hasPendingProcess = hasPendingProcess;
            this.securityInObservePool = securityInObservePool;
            this.issuerInObservePool = issuerInObservePool;
            this.flowSnapshotMap = flowSnapshotMap;
        }
    }

}
