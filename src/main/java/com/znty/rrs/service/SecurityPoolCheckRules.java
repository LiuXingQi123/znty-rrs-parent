package com.znty.rrs.service;

import com.znty.rrs.common.constants.BusinessSwitches;
import com.znty.rrs.common.enums.ApprovalStrategy;
import com.znty.rrs.common.enums.FlowStatus;
import com.znty.rrs.common.enums.NodeType;
import com.znty.rrs.common.enums.ItemType;
import com.znty.rrs.common.enums.ProcessAction;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.CategoryType;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.common.enums.MaterialExemptionType;
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.common.constants.ProductInboundPoolCodes;
import com.znty.rrs.common.util.BatchAdjustFlowOptionHelper;
import com.znty.rrs.common.util.CreditBondRemainTermUtil;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.common.SecurityMarketMappingDto;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.common.util.MarketCodeMatchUtil;
import com.znty.rrs.common.enums.MarketCode;
import org.springframework.util.StringUtils;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.flow.FlowOptionParam;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.springframework.beans.BeanUtils;
import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.RatingCompanySelectionReq;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** 单券页面和批量服务共用的证券准入条件与流程判定 */
@Service
class SecurityPoolCheckRules {

    static final String PENDING_PROCESS_FAILURE = "证券存在进行中的调库流程";
    static final String ALREADY_IN_POOL_FAILURE = "证券已在目标投资池中";

    /** 判断 Excel 导入仍需拦截的业务校验结果 */
    static boolean hasExcelBlockingFailure(AdjustCheckDto.CheckResultItem item, String pendingProcessFailure) {
        return item.getFailReasons() != null && item.getFailReasons().stream().anyMatch(reason ->
                reason.startsWith(pendingProcessFailure) || ALREADY_IN_POOL_FAILURE.equals(reason));
    }

    /** 是否认可普通池状态中的 CRMW 作为快速入库来源 */
    private boolean crmwUsePoolStatus = BusinessSwitches.CRMW_USE_POOL_STATUS;

    /** 简易入库的历史回看天数 */
    static final int SIMPLE_INBOUND_HISTORY_DAYS = 180;
    /** 白名单入库流程 */
    private static final String FLOW_KEY_WHITELIST_INBOUND = "bond:whitelist-inbound";
    /** 是否启用白名单入库流程 */
    private static final boolean WHITELIST_FLOW_ENABLED = false;
    /** 白名单资格对应的池 ID */
    private static final Set<Long> WHITELIST_POOL_IDS = Collections.emptySet();
    /** 标准入库升级流程 */
    private static final String FLOW_KEY_STANDARD_UPGRADE = "bond:standard-upgrade";
    /** 标准入库降级流程 */
    private static final String FLOW_KEY_STANDARD_DOWNGRADE = "bond:standard-downgrade";
    /** 特殊入库流程 */
    private static final String FLOW_KEY_SPECIAL_INBOUND = "bond:special-inbound";

    /** 单券校验及相关评级主体查询 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 单券市场和同券关系查询 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 流程定义与快照查询 */
    @Resource
    private FlowMapper flowMapper;

    /** 信用债内评矩阵 */
    @Resource
    private CreditBondGradeRuleMapper creditBondGradeRuleMapper;

    /** ABS 主体选择与冲突判定 */
    @Resource
    private AbsIdentityService absIdentityService;

    /** 黑名单质押库三条件判定 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;

    /** 发行主体及担保人评级下调判定 */
    @Resource
    private RatingDowngradeChecker ratingDowngradeChecker;

    SecurityPoolCheckRules() {
    }

    /** 由已有单券依赖组装判定器，供直接构造服务的调用方使用 */
    SecurityPoolCheckRules(SecurityPoolAdjustMapper securityPoolAdjustMapper,
                           SecurityMarketService securityMarketService,
                           FlowMapper flowMapper,
                           CreditBondGradeRuleMapper creditBondGradeRuleMapper,
                           AbsIdentityService absIdentityService,
                           PledgeBlacklistRuleService pledgeBlacklistRuleService,
                           RatingDowngradeChecker ratingDowngradeChecker,
                           boolean crmwUsePoolStatus) {
        this.securityPoolAdjustMapper = securityPoolAdjustMapper;
        this.securityMarketService = securityMarketService;
        this.flowMapper = flowMapper;
        this.creditBondGradeRuleMapper = creditBondGradeRuleMapper;
        this.absIdentityService = absIdentityService;
        this.pledgeBlacklistRuleService = pledgeBlacklistRuleService;
        this.ratingDowngradeChecker = ratingDowngradeChecker;
        this.crmwUsePoolStatus = crmwUsePoolStatus;
    }

    /**
     * 规范化主体内评分档编码：null / 空白视为无内评。
     */
    private String normalizeGradeCode(String gradeCode) {
        if (gradeCode == null) {
            return null;
        }
        String trimmed = gradeCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 判断指定流程是否为直通（无人工审批节点）。
     *
     * 说明：单券提交路径内部直接使用 {@link #isDirectFlow(FlowSnapshot)}，
     * 本方法不在本类其它业务步骤中调用；主要供「证券池批量调整」在整批提交前做直通预检
     * （{@code needsWholeBatchDirectRecheck}），复用与单券一致的直通判定口径，避免批量侧再拷贝流程快照逻辑。
     *
     * 判定规则：flowId 与 flowKey 均为空时按「无审批直通」处理；仅有 key 时解析活跃流程定义再判断；
     * 解析不到定义时返回 false（按非直通走后续提交校验）。
     *
     * @param flowId  流程定义 ID，可为 null
     * @param flowKey 流程 Key，可为 null/空
     * @return true=直通或无流程
     */
    boolean isDirectAdjustFlow(Long flowId, String flowKey) {
        if (flowId == null && (flowKey == null || flowKey.isEmpty())) {
            return true;
        }
        Long resolvedId = flowId;
        if (resolvedId == null) {
            FlowDefinitionBo definition = flowMapper.queryActiveFlowByKey(flowKey);
            resolvedId = definition != null ? definition.getId() : null;
        }
        if (resolvedId == null) {
            // 配置了 key 但解析不到定义时，按非直通走后续提交校验
            return false;
        }
        FlowSnapshot snapshot = buildFlowSnapshot(resolvedId);
        return snapshot != null && isDirectFlow(snapshot);
    }

    /** 将目标池列表转换为顶级投资池组 ID 集合。 */
    Set<Long> resolvePoolGroupIds(List<Long> poolIds, Map<Long, InvestmentPoolBo> poolMap) {
        Set<Long> result = new HashSet<>();
        if (poolIds == null) {
            return result;
        }
        for (Long poolId : poolIds) {
            Long rootId = resolveRootPoolId(poolId, poolMap);
            if (rootId != null) {
                result.add(rootId);
            }
        }
        return result;
    }

    /** 沿父级关系查找目标池所属的顶级投资池。 */
    static Long resolveRootPoolId(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        Long currentId = poolId;
        Set<Long> visited = new HashSet<>();
        while (currentId != null && visited.add(currentId)) {
            InvestmentPoolBo pool = poolMap.get(currentId);
            if (pool == null || pool.getParentId() == null) {
                return currentId;
            }
            currentId = pool.getParentId();
        }
        return currentId;
    }

    /** 将空字符串归一化为空值，避免重复提交比较受空串表现影响。 */
    String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * 为指定流程 ID 构建运行时快照（定义 + 活跃版本 + 节点索引 + 连线列表）。
     *
     * 节点按 DB ID 建立索引，供遍历连线时快速查找目标节点。
     *
     * @param flowId 流程定义 ID
     * @return FlowSnapshot，若流程不存在或无活跃版本则返回 null
     */
    FlowSnapshot buildFlowSnapshot(Long flowId) {
        FlowDefinitionBo def = flowMapper.queryFlowById(flowId);
        if (def == null) {
            return null;
        }

        // 查询该流程的所有版本（ORDER BY ver_num DESC），取第一个 status='active' 的版本
        List<FlowVersionBo> versions = flowMapper.queryFlowVersionByFlowIdList(flowId, null);
        FlowVersionBo activeVersion = null;
        for (FlowVersionBo v : versions) {
            if (FlowStatus.ACTIVE.getCode().equals(v.getStatus())) {
                activeVersion = v;
                break;
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
        for (FlowNodeBo node : nodes) {
            nodeMap.put(node.getId(), node);
        }

        // 加载该版本的审批节点配置，按 nodeId 建立索引
        List<NodeApprovalConfigBo> approvalConfigs = flowMapper.queryApprovalConfigListByVersionId(activeVersion.getId());
        Map<Long, NodeApprovalConfigBo> approvalConfigMap = new HashMap<>();
        for (NodeApprovalConfigBo cfg : approvalConfigs) {
            approvalConfigMap.put(cfg.getNodeId(), cfg);
        }

        List<NodeApprovalHandlerBo> approvalHandlers = flowMapper.queryApprovalHandlerListByVersionId(activeVersion.getId());
        Map<Long, List<NodeApprovalHandlerBo>> approvalHandlerMap = new HashMap<>();
        for (NodeApprovalHandlerBo handler : approvalHandlers) {
            List<NodeApprovalHandlerBo> list = approvalHandlerMap.get(handler.getApprovalConfigId());
            if (list == null) {
                list = new ArrayList<>();
                approvalHandlerMap.put(handler.getApprovalConfigId(), list);
            }
            list.add(handler);
        }

        return new FlowSnapshot(def, activeVersion, nodeMap, edges, approvalConfigMap, approvalHandlerMap);
    }

    /**
     * 判断流程是否为直通流程（开始后无需人工待办即可到结束节点）。
     *
     * 与 createInitialSteps 自动完成范围对齐：start/end/auto 节点、initiator、approval_strategy=auto
     * 可无人工到达 end；主路径存在 preempt/all 等人工审批则为非直通。
     * 例：债券特殊策略入库（发起人→多层 auto→结束）应判为直通。
     *
     * @param snapshot 流程快照（含节点索引和连线列表）
     * @return true 表示直通流程（可直接生效），false 表示需要审批
     */
    boolean isDirectFlow(FlowSnapshot snapshot) {
        if (snapshot == null || snapshot.nodeMap == null || snapshot.edges == null) {
            return false;
        }
        FlowNodeBo startNode = null;
        for (FlowNodeBo node : snapshot.nodeMap.values()) {
            if (NodeType.START.getCode().equals(node.getNodeType())) {
                startNode = node;
                break;
            }
        }
        if (startNode == null) {
            return false;
        }
        return canCompleteWithoutHumanApproval(snapshot, startNode, startNode, new HashSet<Long>());
    }

    /**
     * 从当前节点沿主路径（排除 reject 支路）是否仅经无需人工节点即可到达 end。
     */
    private boolean canCompleteWithoutHumanApproval(FlowSnapshot snapshot, FlowNodeBo current,
                                                    FlowNodeBo startNode, Set<Long> visiting) {
        if (current == null || current.getId() == null) {
            return false;
        }
        if (NodeType.END.getCode().equals(current.getNodeType())) {
            return true;
        }
        if (!visiting.add(current.getId())) {
            return false;
        }
        try {
            if (NodeType.APPROVAL.getCode().equals(current.getNodeType())) {
                NodeApprovalConfigBo config = snapshot.approvalConfigMap != null
                        ? snapshot.approvalConfigMap.get(current.getId()) : null;
                boolean autoStrategy = config != null
                        && ApprovalStrategy.AUTO.getCode().equals(config.getApprovalStrategy());
                boolean initiator = isInitiatorStep(snapshot, current, config, current, startNode);
                if (!autoStrategy && !initiator) {
                    return false;
                }
            }
            List<FlowEdgeBo> outEdges = new ArrayList<>();
            for (FlowEdgeBo edge : snapshot.edges) {
                if (edge.getFromNodeId() == null || !edge.getFromNodeId().equals(current.getId())) {
                    continue;
                }
                if (ProcessAction.REJECT.getCode().equals(edge.getRouteAction())) {
                    continue;
                }
                outEdges.add(edge);
            }
            if (outEdges.isEmpty()) {
                return false;
            }
            for (FlowEdgeBo edge : outEdges) {
                FlowNodeBo next = snapshot.nodeMap.get(edge.getToNodeId());
                if (!canCompleteWithoutHumanApproval(snapshot, next, startNode, visiting)) {
                    return false;
                }
            }
            return true;
        } finally {
            visiting.remove(current.getId());
        }
    }

    /**
     * 原规则全部执行后，统一将 Excel 业务失败作为可提交提示
     *
     * 执行顺序：
     * 1. 用本次已加载数据识别没有实际证券或目标池的组，这类数据仍无法落库
     * 2. 其余组统一允许提交，保留 failReasons 供页面显示和导入快照留存
     *
     * @param shared 本次已经加载的主券、关联券和投资池
     * @param items 原规则执行后的完整结果
     */
    void allowExcelImportResults(AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> items) {
        Set<String> invalidGroups = new HashSet<>();
        for (AdjustCheckDto.CheckResultItem item : items) {
            boolean knownSecurity = shared.getSecurityInfo() != null
                    && Objects.equals(item.getSecurityCode(), shared.getSecurityInfo().getWindCode());
            knownSecurity = knownSecurity || shared.getRelatedSecurityData().containsKey(item.getSecurityCode());
            if (!knownSecurity || !shared.getPoolMap().containsKey(item.getTargetPoolId())
                    || hasExcelBlockingFailure(item, PENDING_PROCESS_FAILURE)) {
                invalidGroups.add(item.getAdjustGroupKey());
            }
        }
        for (AdjustCheckDto.CheckResultItem item : items) {
            item.setCanAdjust(!invalidGroups.contains(item.getAdjustGroupKey()));
        }
    }

    /**
     * 从本批数据构建一只证券的规则上下文
     *
     * @param req 当前证券及本次选中的目标池
     * @param data 本批共享数据
     * @return 当前证券的校验上下文
     */
    AdjustSharedData loadBatchSharedData(AdjustCheckReq req, BatchSecurityCheckData data) {
        SecurityInfoBo master = data.securities.get(req.getSecurityCode());
        if (master == null) {
            throw new BizException("证券不存在");
        }
        SecurityInfoBo securityInfo = new SecurityInfoBo();
        BeanUtils.copyProperties(master, securityInfo);
        // 批量上下文复用已读取的主体候选
        applySelectedRatingCompanyInternal(securityInfo, req.getGuarantorCode(), req.getRightsHolderCode(),
                req.getSelfSelectedRightsHolderCode(), true, req.getSelectedRatingCompany(), data);
        // 将批次状态转为单券规则原本使用的结构
        AdjustSharedData shared = buildBatchSecurityFacts(securityInfo, data, req.getGuarantorCode());
        shared.setRequestInPoolIds(req.getItems().stream()
                .filter(item -> AdjustMode.IN.getCode().equals(item.getAdjustMode()))
                .map(AdjustCheckReq.CheckItem::getTargetPoolId).collect(Collectors.toSet()));
        shared.setRequestOutPoolIds(req.getItems().stream()
                .filter(item -> AdjustMode.OUT.getCode().equals(item.getAdjustMode()))
                .map(AdjustCheckReq.CheckItem::getTargetPoolId).collect(Collectors.toSet()));
        shared.setFundRate(req.getFundRate());
        shared.setReleaseRules(req.isReleaseRules());
        return shared;
    }

    /** 组装主券和关联券共用的证券状态及名单事实 */
    private AdjustSharedData buildBatchSecurityFacts(SecurityInfoBo security, BatchSecurityCheckData data,
                                                      String guarantorCode) {
        String code = security.getWindCode();
        String issuer = data.securities.get(code).getIssuerCode();
        AdjustSharedData shared = new AdjustSharedData();
        shared.setBatchData(data);
        shared.setSecurityInfo(security);
        shared.setPoolMap(data.pools);
        shared.setPoolRelationMap(data.relations);
        shared.setCurrentPoolIds(data.activePools.getOrDefault(code, Collections.emptySet()));
        shared.setHasPendingProcess(data.pendingLabels.containsKey(code));
        shared.setPendingProcessNodeLabel(data.pendingLabels.get(code));
        // 手工校验按顶级池组认定在途占用
        shared.setPendingPoolGroupIds(resolvePoolGroupIds(new ArrayList<>(
                data.pendingPools.getOrDefault(code, Collections.emptySet())), data.pools));
        // 证券名单由已查到的池状态判断
        shared.setSecurityInObservePool(isInBatchPoolType(data, code, "observe"));
        shared.setSecurityInRestrictedPool(isInBatchPoolType(data, code, "restricted"));
        if (CreditBondSpecialInboundRule.isAbs(security)) {
            // ABS 名单仅使用主体级生效记录
            List<String> riskCompanies = batchAbsRiskCompanies(security, data);
            shared.setIssuerInObservePool(riskCompanies.stream().anyMatch(codeForCompany ->
                    isInBatchCompanyPoolType(data, codeForCompany, "observe")));
            shared.setIssuerInRestrictedPool(riskCompanies.stream().anyMatch(codeForCompany ->
                    isInBatchCompanyPoolType(data, codeForCompany, "restricted")));
        } else {
            shared.setIssuerInObservePool(isInBatchPoolType(data, issuer, "observe"));
            shared.setIssuerInRestrictedPool(isInBatchPoolType(data, issuer, "restricted"));
        }
        shared.setIssuerRatingDowngraded(data.downgradedCompanies.contains(security.getIssuerCode()));
        shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(security));
        shared.setGuarantorRatingDowngraded(data.downgradedCompanies.contains(guarantorCode));
        return shared;
    }

    /** 按原名单查询口径检查证券或主体是否在指定类型池生效 */
    private boolean isInBatchPoolType(BatchSecurityCheckData data, String code, String type) {
        return data.activePools.getOrDefault(code, Collections.emptySet()).stream()
                .map(data.pools::get).anyMatch(pool -> pool != null && Integer.valueOf(0).equals(pool.getIsDeleted())
                        && type.equals(pool.getPoolType()));
    }

    /** 有已确认主体时使用该主体，其余使用全部候选及本次选择 */
    private List<String> batchAbsRiskCompanies(SecurityInfoBo security, BatchSecurityCheckData data) {
        String code = security.getWindCode();
        SecurityInfoBo master = data.securities.get(code);
        if (master != null && StringUtils.hasText(master.getIssuerCode())) {
            return Collections.singletonList(master.getIssuerCode().trim());
        }
        Set<String> companies = data.ratingCompanies.getOrDefault(code, Collections.emptyList()).stream()
                .map(RelatedRatingCompanyDto::getCompanyCode).filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (StringUtils.hasText(security.getIssuerCode())) {
            companies.add(security.getIssuerCode());
        }
        return new ArrayList<>(companies);
    }

    /** 按已加载的主体级生效记录判断名单 */
    private boolean isInBatchCompanyPoolType(BatchSecurityCheckData data, String code, String type) {
        return data.blacklistCompanyPools.getOrDefault(code, Collections.emptySet()).stream()
                .map(data.pools::get).anyMatch(pool -> pool != null && Integer.valueOf(0).equals(pool.getIsDeleted())
                        && type.equals(pool.getPoolType()));
    }

    /**
     * ABS 复用全部权益人候选检查主体池，普通债保持单发行人查询
     * 执行顺序：
     * 1. ABS 使用本次已查询候选和自选代码，只重查动态池状态
     * 2. 普通债沿用原发行主体口径
     * @param security 本次证券
     * @param poolType 主体池类型
     * @return 是否命中
     */
    boolean queryIssuerInPoolType(SecurityInfoBo security, String poolType) {
        if (CreditBondSpecialInboundRule.isAbs(security)) {
            return absIdentityService.isRelatedInPool(security, poolType);
        }
        return "observe".equals(poolType)
                ? securityPoolAdjustMapper.queryIssuerInObservePool(security.getWindCode())
                : securityPoolAdjustMapper.queryIssuerInRestrictedPool(security.getWindCode());
    }

    /**
     * 按证券类型校验并回填本次评级主体：ABS 自选权益人优先，非 ABS 使用担保人。
     */
    RatingSelection applySelectedRatingCompanyInternal(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode,
            String selfSelectedRightsHolderCode, boolean requireAbsSelection) {
        // 原入口没有携带已选数据时按既有候选来源读取
        return applySelectedRatingCompanyInternal(securityInfo, guarantorCode, rightsHolderCode,
                selfSelectedRightsHolderCode, requireAbsSelection, null);
    }

    /**
     * 直接复用页面已选主体，未携带选择对象的后台调用沿用原候选读取
     * 执行顺序：
     * 1. 已传主体时直接回填五字段及矩阵评分，不重复查询名称和评级
     * 2. 未传主体对象时沿用原关系候选入口
     * @param securityInfo 当前证券信息
     * @param guarantorCode 担保人代码
     * @param rightsHolderCode 普通权益人代码
     * @param selfSelectedRightsHolderCode 自选权益人代码
     * @param requireAbsSelection ABS 是否需要选择主体
     * @param selected 页面已选主体
     * @return 本次快照使用的主体名称
     */
    RatingSelection applySelectedRatingCompanyInternal(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode,
            String selfSelectedRightsHolderCode, boolean requireAbsSelection, RatingCompanySelectionReq selected) {
        // 单券选择按原有候选来源读取
        return applySelectedRatingCompanyInternal(securityInfo, guarantorCode, rightsHolderCode,
                selfSelectedRightsHolderCode, requireAbsSelection, selected, null);
    }

    /** 根据对应入口提供的候选数据校验并回填主体 */
    RatingSelection applySelectedRatingCompanyInternal(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode,
            String selfSelectedRightsHolderCode, boolean requireAbsSelection, RatingCompanySelectionReq selected,
            BatchSecurityCheckData batchData) {
        boolean abs = CreditBondSpecialInboundRule.isAbs(securityInfo);
        boolean guaranteed = Integer.valueOf(1).equals(securityInfo.getGuarantFlag());
        if (guaranteed && guarantorCode == null && (abs || selected == null)) {
            // 只为未传选择的请求初始化，显式传空不再套用默认首项
            RelatedRatingCompanyDto guarantor = defaultGuarantor(securityInfo,
                    relatedRatingCompanies(securityInfo.getWindCode(), batchData));
            if (guarantor != null) {
                fillSelectedRatingCompany(securityInfo, guarantor.getCompanyCode(),
                        guarantor.getCompanyName(), guarantor.getInnerRating());
            }
        }
        if (abs && guaranteed && emptyToNull(guarantorCode) != null) {
            // ABS 的担保人独立写入快照，后续权益人选择仍只回填发行主体字段
            RelatedRatingCompanyDto guarantor = findRelatedRatingCompany(
                    securityInfo.getWindCode(), guarantorCode.trim(), "所选担保人不属于当前证券", batchData);
            fillSelectedRatingCompany(securityInfo, guarantor.getCompanyCode(),
                    guarantor.getCompanyName(), guarantor.getInnerRating());
        }
        if (selected != null) {
            RatingSelection result = new RatingSelection();
            // 预览允许空选择，下一步和正式提交才要求主体必填
            if (abs && emptyToNull(selected.getCompanyCode()) == null) {
                if (requireAbsSelection) {
                    throw new BizException("请选择权益人或自选权益人");
                }
                // 预览允许暂时清空，只修改本次对象，不改动已生效主档身份
                securityInfo.setIssuer(null);
                securityInfo.setIssuerCode(null);
                securityInfo.setRatingBondissuer(null);
                securityInfo.setRatingBondissuerAgency(null);
                securityInfo.setInnerIssuerRating(null);
                return result;
            }
            result.rightsHolderName = selected.getRightsHolderName();
            result.selfSelectedRightsHolderName = selected.getSelfSelectedRightsHolderName();
            if (abs) {
                // ABS 的评级主体是权益人，只写发行人五字段，不借用担保人字段承载
                absIdentityService.applySelection(securityInfo, selected.getCompanyCode(), selected.getCompanyName(),
                        selected.getInnerRating(), selected.getRatingBondissuer(), selected.getRatingBondissuerAgency());
            } else {
                // 非 ABS 的评级主体是担保人，直接取页面已查询的数据，空评级也保持原值为空
                fillSelectedRatingCompany(securityInfo, selected.getCompanyCode(), selected.getCompanyName(),
                        selected.getInnerRating());
            }
            return result;
        }
        RatingSelection result = new RatingSelection();
        if (abs) {
            RelatedRatingCompanyDto rightsHolder = null;
            SelfSelectedRightsHolderDto selfSelected = null;
            if (rightsHolderCode != null && !rightsHolderCode.trim().isEmpty()) {
                rightsHolder = findRelatedRatingCompany(
                        securityInfo.getWindCode(), rightsHolderCode.trim(), "所选权益人不属于当前证券", batchData);
                result.rightsHolderName = rightsHolder.getCompanyName();
            }
            if (selfSelectedRightsHolderCode != null && !selfSelectedRightsHolderCode.trim().isEmpty()) {
                String code = selfSelectedRightsHolderCode.trim();
                if (batchData != null) {
                    selfSelected = batchData.selfSelectedCompanies.get(code);
                } else {
                    selfSelected = absIdentityService.findSelf(code);
                    if (selfSelected == null) {
                        selfSelected = securityPoolAdjustMapper.querySelfSelectedRightsHolderByCode(code);
                        if (selfSelected != null) {
                            absIdentityService.rememberSelf(Collections.singletonList(selfSelected));
                        }
                    }
                }
                if (selfSelected == null) {
                    throw new BizException("所选自选权益人不存在或已失效");
                }
                result.selfSelectedRightsHolderName = selfSelected.getCompanyName();
            }
            if (rightsHolder == null && selfSelected == null) {
                // 前端未传选择时，直接采用关联评级主体接口返回的首条，不按关系类型二次筛选
                rightsHolder = queryFirstRelatedRatingCompany(securityInfo.getWindCode(), batchData);
                if (rightsHolder != null) {
                    result.rightsHolderName = rightsHolder.getCompanyName();
                }
            }
            if (requireAbsSelection && rightsHolder == null && selfSelected == null) {
                throw new BizException("请选择权益人或自选权益人");
            }
            if (selfSelected != null) {
                absIdentityService.applySelection(securityInfo, selfSelected.getCompanyCode(), selfSelected.getCompanyName(),
                        selfSelected.getInnerRating(), selfSelected.getRatingBondissuer(), selfSelected.getRatingBondissuerAgency());
            } else if (rightsHolder != null) {
                absIdentityService.applySelection(securityInfo, rightsHolder.getCompanyCode(),
                        rightsHolder.getCompanyName(), rightsHolder.getInnerRating(),
                        rightsHolder.getRatingBondissuer(), rightsHolder.getRatingBondissuerAgency());
            }
            return result;
        }
        if (emptyToNull(guarantorCode) != null) {
            // 普通担保债使用明确选择的担保人，空选择不取首个候选
            RelatedRatingCompanyDto guarantor = findRelatedRatingCompany(
                    securityInfo.getWindCode(), guarantorCode.trim(), "所选担保人不属于当前证券", batchData);
            fillSelectedRatingCompany(securityInfo, guarantor.getCompanyCode(),
                    guarantor.getCompanyName(), guarantor.getInnerRating());
        } else if (!guaranteed) {
            fillSelectedRatingCompany(securityInfo, null, null, null);
        }
        return result;
    }

    /**
     * 担保债优先回显候选中的已保存担保人，否则使用首个候选
     *
     * @param security 当前证券
     * @param candidates 已按展示顺序排列的关联主体
     * @return 默认担保人，无候选或非担保债时返回空
     */
    static RelatedRatingCompanyDto defaultGuarantor(SecurityInfoBo security, List<RelatedRatingCompanyDto> candidates) {
        if (!Integer.valueOf(1).equals(security.getGuarantFlag()) || candidates.isEmpty()) {
            return null;
        }
        String savedCode = security.getGuarantorId();
        return candidates.stream().filter(candidate -> candidate.getCompanyCode().equals(savedCode))
                .findFirst().orElse(candidates.get(0));
    }

    /** 查询并校验当前证券的相关评级主体。 */
    private RelatedRatingCompanyDto findRelatedRatingCompany(
            String securityCode, String companyCode, String errorMessage, BatchSecurityCheckData batchData) {
        // 使用当前入口已准备的候选
        List<RelatedRatingCompanyDto> records = relatedRatingCompanies(securityCode, batchData);
        for (RelatedRatingCompanyDto record : records) {
            if (companyCode.equals(record.getCompanyCode())) {
                return record;
            }
        }
        throw new BizException(errorMessage);
    }

    /** 查询关联评级主体接口返回的首条记录，不按关系类型二次筛选。 */
    private RelatedRatingCompanyDto queryFirstRelatedRatingCompany(String securityCode, BatchSecurityCheckData batchData) {
        // 使用当前入口已准备的候选
        List<RelatedRatingCompanyDto> records = relatedRatingCompanies(securityCode, batchData);
        return records.isEmpty() ? null : records.get(0);
    }

    /** 批量入口直接读取候选索引，单券入口使用原缓存和查询 */
    private List<RelatedRatingCompanyDto> relatedRatingCompanies(String securityCode, BatchSecurityCheckData batchData) {
        if (batchData != null) {
            return batchData.ratingCompanies.getOrDefault(securityCode, Collections.emptyList());
        }
        List<RelatedRatingCompanyDto> records = absIdentityService.findRelated(securityCode);
        if (records == null) {
            records = securityPoolAdjustMapper.queryRelatedRatingCompanyList(securityCode);
            absIdentityService.rememberRelated(securityCode, records);
        }
        return records == null ? Collections.emptyList() : records;
    }

    /** 将后端重查的主体名称、代码及最新内评写入本次业务对象。 */
    void fillSelectedRatingCompany(
            SecurityInfoBo securityInfo, String companyCode, String companyName, String innerRating) {
        securityInfo.setGuarantor(companyName);
        securityInfo.setGuarantorId(companyCode);
        securityInfo.setInnerGuarantorRating(innerRating);
    }

    /**
     * 第三阶段：调入校验
     *
     * 遍历请求中全部调入方向的手动项，逐项执行前置校验 + 调入校验，
     * 并根据目标池的关系配置自动追加以下两类附加项：
     *
     *   - 联动调入项（linkage）：目标池配置的联动池（in_linked）需同步调入
     *   - 互斥配套调出项（mutex）：若证券当前已在目标池的互斥池（in_mutex）中，
     *       需同步调出该互斥池，两池不可同时持有
     *
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调入方向的全部校验结果（手动项 + 自动项）
     */
    List<AdjustCheckDto.CheckResultItem> executeInAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 构建手工调库项分组 Key
            String adjustGroupKey = buildAdjustGroupKey(item);

            // 手动调入项：前置校验 + 调入校验
            int poolCurrentCount = checkPoolCount(shared, item.getTargetPoolId());
            // 构建调库校验上下文
            AdjustCheckContext ctx = buildCheckContext(item, poolCurrentCount, shared);
            List<String> failures = checkInConditions(ctx);

            AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
            resultItem.setTargetPoolId(item.getTargetPoolId());
            // 构建投资池全路径名称
            resultItem.setPoolName(buildPoolPath(item.getTargetPoolId(), shared.getPoolMap()));
            resultItem.setPoolType(item.getPoolType());
            resultItem.setAdjustMode(AdjustMode.IN.getCode());
            resultItem.setItemTag(ItemType.MANUAL.getCode());
            resultItem.setAdjustGroupKey(adjustGroupKey);
            resultItem.setCanAdjust(failures.isEmpty());
            resultItem.setFailReasons(failures);
            resultItem.setWarnings(ctx.getWarnings());
            results.add(resultItem);

            // 取出目标池的关系配置，用于生成自动项
            Map<String, List<Long>> relations = shared.getPoolRelationMap().getOrDefault(
                    item.getTargetPoolId(), Collections.emptyMap());

            // 联动调入：目标池调入时，其联动池（in_linked）需同步调入
            List<Long> inLinkage = relations.get(RelationType.IN_LINKED.getCode());
            if (inLinkage != null) {
                for (Long linkedId : inLinkage) {
                    // coveredKeys.add 返回 true 表示该 key 是首次出现，生成自动项
                    if (coveredKeys.add(linkedId + "_调入")) {
                        // 构建自动生成的联动/互斥调整项校验结果  与手动项校验流程完全相同，区别在于：  targetPoolId 来自池关系配置，由系统自动推导，非用户选择 itemTag 标记为ItemType.LINKAGE.getCode()或ItemType.MUTEX.getCode()，前端据此区分显示样式
                        AdjustCheckDto.CheckResultItem autoItem =
                                buildAutoResultItem(linkedId, AdjustMode.IN.getCode(), ItemType.LINKAGE.getCode(), adjustGroupKey, shared);
                        // 关联手工项失败时阻断自动联动项
                        inheritManualItemFailure(autoItem, resultItem);
                        results.add(autoItem);
                    }
                }
            }

            // 互斥配套调出：目标池调入时，若证券已在其互斥池（in_mutex）中，
            // 则需同步调出该互斥池，由系统自动追加调出校验项
            List<AdjustCheckDto.CheckResultItem> groupMutexItems = new ArrayList<>();
            List<Long> inMutex = relations.get(RelationType.IN_MUTEX.getCode());
            if (shared.getBatchData() != null && shared.getBatchData().scheduled) {
                // 自动调库历史口径中的反向限制池也作为配套调出项校验
                Set<Long> scheduledOut = new LinkedHashSet<>(inMutex == null ? Collections.emptyList() : inMutex);
                shared.getPoolRelationMap().forEach((poolId, poolRelations) -> {
                    if (poolRelations.getOrDefault(RelationType.IN_RESTRICT.getCode(), Collections.emptyList())
                            .contains(item.getTargetPoolId())) {
                        scheduledOut.add(poolId);
                    }
                });
                scheduledOut.remove(item.getTargetPoolId());
                inMutex = new ArrayList<>(scheduledOut);
            }
            if (inMutex != null) {
                for (Long mutexId : inMutex) {
                    // 证券不在该互斥池中则无需生成配套调出项
                    if (!shared.getCurrentPoolIds().contains(mutexId)) {
                        continue;
                    }
                    if (coveredKeys.add(mutexId + "_调出")) {
                        // 构建自动生成的联动/互斥调整项校验结果  与手动项校验流程完全相同，区别在于：  targetPoolId 来自池关系配置，由系统自动推导，非用户选择 itemTag 标记为ItemType.LINKAGE.getCode()或ItemType.MUTEX.getCode()，前端据此区分显示样式
                        AdjustCheckDto.CheckResultItem autoItem =
                                buildAutoResultItem(mutexId, AdjustMode.OUT.getCode(), ItemType.MUTEX.getCode(), adjustGroupKey, shared);
                        // 关联手工项失败时阻断自动互斥项
                        inheritManualItemFailure(autoItem, resultItem);
                        groupMutexItems.add(autoItem);
                        results.add(autoItem);
                    }
                }
            }
            // 互斥调出失败时反阻断同组手工调入，避免只入目标池导致双池
            propagateMutexFailureToManual(resultItem, groupMutexItems);
        }

        return results;
    }

    /**
     * 第四阶段：调出校验
     *
     * 遍历请求中全部调出方向的手动项，逐项执行前置校验 + 调出校验，
     * 并根据目标池的关系配置自动追加以下附加项：
     *
     *   - 联动调出项（linkage）：目标池配置的联动池（out_linked）需同步调出
     *
     *
     * 注意：第三阶段由互斥关系生成的调出项已加入 coveredKeys，
     * 若调出方向的手动项恰好与之重叠，以手动项为准（手动项在 coveredKeys 初始化时已注册，
     * 不会被跳过）；联动调出自动项若已被覆盖则跳过。
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调出方向的全部校验结果（手动项 + 自动项）
     */
    List<AdjustCheckDto.CheckResultItem> executeOutAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 构建手工调库项分组 Key
            String adjustGroupKey = buildAdjustGroupKey(item);

            // 手动调出项：前置校验 + 调出校验
            int poolCurrentCount = checkPoolCount(shared, item.getTargetPoolId());
            // 构建调库校验上下文
            AdjustCheckContext ctx = buildCheckContext(item, poolCurrentCount, shared);
            List<String> failures = checkOutConditions(ctx);

            AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
            resultItem.setTargetPoolId(item.getTargetPoolId());
            // 构建投资池全路径名称
            resultItem.setPoolName(buildPoolPath(item.getTargetPoolId(), shared.getPoolMap()));
            resultItem.setPoolType(item.getPoolType());
            resultItem.setAdjustMode(AdjustMode.OUT.getCode());
            resultItem.setItemTag(ItemType.MANUAL.getCode());
            resultItem.setAdjustGroupKey(adjustGroupKey);
            resultItem.setCanAdjust(failures.isEmpty());
            resultItem.setFailReasons(failures);
            resultItem.setWarnings(ctx.getWarnings());
            results.add(resultItem);

            // 联动调出：目标池调出时，其联动池（out_linked）需同步调出
            Map<String, List<Long>> relations = shared.getPoolRelationMap().getOrDefault(
                    item.getTargetPoolId(), Collections.emptyMap());
            List<Long> outLinkage = relations.get(RelationType.OUT_LINKED.getCode());
            if (outLinkage != null) {
                for (Long linkedId : outLinkage) {
                    if (coveredKeys.add(linkedId + "_调出")) {
                        // 构建自动生成的联动/互斥调整项校验结果  与手动项校验流程完全相同，区别在于：  targetPoolId 来自池关系配置，由系统自动推导，非用户选择 itemTag 标记为ItemType.LINKAGE.getCode()或ItemType.MUTEX.getCode()，前端据此区分显示样式
                        AdjustCheckDto.CheckResultItem autoItem =
                                buildAutoResultItem(linkedId, AdjustMode.OUT.getCode(), ItemType.LINKAGE.getCode(), adjustGroupKey, shared);
                        // 关联手工项失败时阻断自动联动项
                        inheritManualItemFailure(autoItem, resultItem);
                        results.add(autoItem);
                    }
                }
            }
        }

        return results;
    }

    /**
     * 第五阶段：判断本次调库可选择的审批流程。
     *
     * 调出时直接使用目标投资池配置的标准调出流程。调入时：信用债已在库返回上调/下调单流程，
     * 信用债未在库返回简易/默认调入候选；白名单流程开关开启时才额外返回白名单候选。
     * 白名单和简易规则按伪代码保留入口，不改表结构。
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param resultItems 前四阶段的校验结果
     * @return 流程候选项
     */
    List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptions(
            AdjustCheckReq req, AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> resultItems) {

        List<AdjustCheckDto.FlowOption> allOptions = new ArrayList<>();
        Set<String> optionKeys = new HashSet<>();
        if (resultItems == null || resultItems.isEmpty()) {
            return allOptions;
        }

        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            // 判断单条手工调库项可选择的审批流程
            List<AdjustCheckDto.FlowOption> itemOptions = resolveAdjustFlowOptionsForItem(req, shared, item);
            // 使用关联市场已加载的数据核验整组快速资格，不重新查询当前池状态
            restrictRelatedFastFlowOptions(shared, item, itemOptions);
            item.setMaterialExemptionType(null);
            for (AdjustCheckDto.FlowOption option : itemOptions) {
                if (option.isSelectable() && option.getMaterialExemptionType() != null) {
                    // 资格属于本条主项，用户之后选择一般、特殊或批量流程时仍保留
                    item.setMaterialExemptionType(option.getMaterialExemptionType());
                    break;
                }
            }
            if (shared.isBatchFlowOptionsEnabled() && item.isCanAdjust()
                    && ItemType.MANUAL.getCode().equals(item.getItemTag())) {
                // 使用本次已经加载的池配置调用公共追加方法
                itemOptions = BatchAdjustFlowOptionHelper.appendConfiguredBatchFlow(
                        itemOptions, shared.getPoolMap().get(item.getTargetPoolId()), item.getAdjustMode());
            }
            item.setFlowOptions(itemOptions);
            for (AdjustCheckDto.FlowOption option : itemOptions) {
                // 生成流程候选项去重 key
                String key = buildFlowOptionUniqueKey(option);
                if (optionKeys.add(key)) {
                    allOptions.add(option);
                }
            }
        }
        return allOptions;
    }

    /**
     * 判断单条手工调库项可选择的审批流程。
     */
    List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptionsForItem(
            AdjustCheckReq req, AdjustSharedData shared, AdjustCheckDto.CheckResultItem item) {

        if (item == null || !item.isCanAdjust() || !ItemType.MANUAL.getCode().equals(item.getItemTag())) {
            return new ArrayList<>();
        }
        InvestmentPoolBo targetPool = shared.getPoolMap().get(item.getTargetPoolId());
        if (targetPool == null) {
            return new ArrayList<>();
        }

        // 手工调出：使用投资池定义的标准调出流程
        if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
            // 解析流程名称：优先使用配置名称
            String flowName = resolveFlowName(targetPool.getOutFlowName(), "调出流程");
            boolean noApproval = isNoApprovalFlow(targetPool.getOutFlowId(), targetPool.getOutFlowKey());
            FlowOptionParam p = new FlowOptionParam();
            p.setFlowType(FlowType.NORMAL_OUTBOUND.getCode());
            p.setFlowName(flowName);
            p.setFlowId(targetPool.getOutFlowId());
            p.setFlowKey(targetPool.getOutFlowKey());
            p.setRecommended(true);
            p.setMatched(true);
            p.setSelectable(true);
            p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                    null, flowName, noApproval, "默认调出流程")));
            p.setUnmatchReasons(new ArrayList<>());
            // 构建调出流程候选项
            return Collections.singletonList(buildPoolFlowOption(p));
        }

        // 产品库来源资格独立判断，命中时优先快速，仍保留原普通或特殊流程
        if (isProductInboundTarget(targetPool, shared.getPoolMap())) {
            return resolveProductInboundFlowOptions(req.getSecurityCode(), shared, targetPool);
        }

        // 手工调入：当前已在目标池调入互斥池时优先特殊审批（信用债大库默认排除）
        AdjustCheckDto.FlowOption specialOption = resolveSpecialInboundFlowOption(targetPool, shared);
        if (specialOption != null) {
            return Collections.singletonList(specialOption);
        }

        // ── 目标池非信用债大库：仅返回默认调入流程 ──
        if (!isCreditBondPool(targetPool)) {
            // 解析流程名称：优先使用配置名称
            String flowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
            boolean noApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
            FlowOptionParam p = new FlowOptionParam();
            p.setFlowType(FlowType.NORMAL_INBOUND.getCode());
            p.setFlowName(flowName);
            p.setFlowId(targetPool.getInFlowId());
            p.setFlowKey(targetPool.getInFlowKey());
            p.setRecommended(true);
            p.setMatched(true);
            p.setSelectable(true);
            p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                    "目标池不在信用债大库中", flowName, noApproval, "默认调入流程")));
            p.setUnmatchReasons(new ArrayList<>());
            // 构建默认调入流程候选项
            return Collections.singletonList(buildPoolFlowOption(p));
        }

        // ── 证券已在信用债大库中：按当前池与目标池层级判断上调/下调 ──
        if (isSecurityInCreditBondPool(shared)) {
            // 判断信用债在库调整流程是上调还是下调
            String flowType = resolveCreditBondAdjustFlowType(targetPool, shared);
            if (flowType == null) {
                // 构建默认调入流程候选项
                return Collections.singletonList(buildNormalInboundFlowOption(
                        targetPool, "当前池与目标池不适用升降级判断"));
            }
            String fallbackName = FlowType.DOWNGRADE_INBOUND.getCode().equals(flowType) ? "下调流程" : "上调流程";
            String flowKey = FlowType.DOWNGRADE_INBOUND.getCode().equals(flowType)
                    ? FLOW_KEY_STANDARD_DOWNGRADE : FLOW_KEY_STANDARD_UPGRADE;
            FlowDefinitionBo flow = queryBatchFlow(flowKey, shared.getBatchData());
            FlowOptionParam p = new FlowOptionParam();
            p.setFlowType(flowType);
            p.setFlowName(flow != null && flow.getName() != null ? flow.getName() : fallbackName);
            p.setFlowId(flow != null ? flow.getId() : null);
            p.setFlowKey(flowKey);
            p.setRecommended(true);
            p.setMatched(true);
            p.setSelectable(true);
            p.setMatchReasons(Collections.singletonList("按当前池与目标池层级变化匹配升降级流程"));
            p.setUnmatchReasons(new ArrayList<>());
            // 构建上调/下调流程候选项
            return Collections.singletonList(buildPoolFlowOption(p));
        }

        // ── 证券不在信用债大库中：按开关评估白名单，再评估简易和默认调入流程 ──
        // 白名单流程命中判断
        List<String> whitelistMatchReasons = Collections.emptyList();
        List<String> whitelistUnmatchReasons = Collections.emptyList();
        boolean whitelistMatched = false;
        if (WHITELIST_FLOW_ENABLED) {
            whitelistMatchReasons = new ArrayList<>();
            whitelistUnmatchReasons = new ArrayList<>();
            // 判断白名单流程是否命中
            whitelistMatched = isWhitelistFlowMatched(req, shared, whitelistMatchReasons, whitelistUnmatchReasons);
        }

        // 简易流程命中判断
        List<String> simpleMatchReasons = new ArrayList<>();
        List<String> simpleUnmatchReasons = new ArrayList<>();
        // 判断简易流程是否命中
        boolean simpleMatched = isSimpleInboundFlowMatched(
                req, shared, targetPool, simpleMatchReasons, simpleUnmatchReasons);

        // 推荐优先级：白名单 > 简易 > 默认调入
        String recommendedType = WHITELIST_FLOW_ENABLED && whitelistMatched ? FlowType.WHITELIST_INBOUND.getCode()
                : (simpleMatched ? FlowType.SIMPLE_INBOUND.getCode() : FlowType.NORMAL_INBOUND.getCode());

        // 构建启用的流程候选项返回前端，由前端根据 recommended 标识决定默认选中项
        List<AdjustCheckDto.FlowOption> options = new ArrayList<>();
        if (WHITELIST_FLOW_ENABLED) {
            // 白名单流程
            FlowOptionParam whitelistP = new FlowOptionParam();
            whitelistP.setRecommended(FlowType.WHITELIST_INBOUND.getCode().equals(recommendedType));
            whitelistP.setMatched(whitelistMatched);
            whitelistP.setMatchReasons(whitelistMatchReasons);
            whitelistP.setUnmatchReasons(whitelistUnmatchReasons);
            // 构建白名单流程候选项
            options.add(buildWhitelistFlowOption(whitelistP, shared.getBatchData()));
        }
        // 简易流程
        String simpleFlowName = resolveFlowName(targetPool.getSimpleInFlowName(), "简易流程");
        FlowOptionParam simpleP = new FlowOptionParam();
        simpleP.setFlowType(FlowType.SIMPLE_INBOUND.getCode());
        simpleP.setFlowName(simpleFlowName);
        simpleP.setFlowId(targetPool.getSimpleInFlowId());
        simpleP.setFlowKey(targetPool.getSimpleInFlowKey());
        simpleP.setRecommended(FlowType.SIMPLE_INBOUND.getCode().equals(recommendedType));
        simpleP.setMatched(simpleMatched);
        simpleP.setSelectable(simpleMatched);
        simpleP.setMatchReasons(simpleMatchReasons);
        simpleP.setUnmatchReasons(simpleUnmatchReasons);
        // 构建简易流程候选项
        AdjustCheckDto.FlowOption simpleOption = buildPoolFlowOption(simpleP);
        if (simpleMatched) {
            simpleOption.setMaterialExemptionType(MaterialExemptionType.CREDIT_BOND_SIMPLE.getCode());
        }
        options.add(simpleOption);
        // 默认调入流程
        String normalFlowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
        boolean normalNoApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
        FlowOptionParam normalP = new FlowOptionParam();
        normalP.setFlowType(FlowType.NORMAL_INBOUND.getCode());
        normalP.setFlowName(normalFlowName);
        normalP.setFlowId(targetPool.getInFlowId());
        normalP.setFlowKey(targetPool.getInFlowKey());
        normalP.setRecommended(FlowType.NORMAL_INBOUND.getCode().equals(recommendedType));
        normalP.setMatched(true);
        normalP.setSelectable(true);
        normalP.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                null, normalFlowName, normalNoApproval, "默认调入流程")));
        normalP.setUnmatchReasons(new ArrayList<>());
        // 构建默认调入流程候选项
        options.add(buildPoolFlowOption(normalP));
        return options;
    }

    /**
     * 命中调入互斥池特殊审批时构建特殊流程候选项。
     *
     * 信用债大库（pool_type=credit_bond）默认排除：互斥场景仍走白名单/简易/升降级/默认调入，
     * 不强制覆盖为 bond:special-inbound。
     */
    AdjustCheckDto.FlowOption resolveSpecialInboundFlowOption(InvestmentPoolBo targetPool,
                                                                      AdjustSharedData shared) {
        if (targetPool == null || shared == null
                || shared.getPoolRelationMap() == null || shared.getCurrentPoolIds() == null) {
            return null;
        }
        // 信用债大库默认不走互斥特殊审批
        if (isCreditBondPool(targetPool)) {
            return null;
        }
        Map<String, List<Long>> targetRelations = shared.getPoolRelationMap().get(targetPool.getId());
        if (targetRelations == null) {
            return null;
        }
        List<Long> mutexPoolIds = targetRelations.get(RelationType.IN_MUTEX.getCode());
        if (mutexPoolIds == null || mutexPoolIds.isEmpty()) {
            return null;
        }
        List<Long> matchedPoolIds = mutexPoolIds.stream()
                .filter(shared.getCurrentPoolIds()::contains)
                .collect(Collectors.toList());
        if (matchedPoolIds.isEmpty()) {
            return null;
        }
        FlowDefinitionBo flow = queryBatchFlow(FLOW_KEY_SPECIAL_INBOUND, shared.getBatchData());
        if (flow == null) {
            return null;
        }
        FlowOptionParam p = new FlowOptionParam();
        p.setFlowType(FlowType.SPECIAL_INBOUND.getCode());
        p.setFlowName(resolveFlowName(flow.getName(), "特殊审批流程"));
        p.setFlowId(flow.getId());
        p.setFlowKey(FLOW_KEY_SPECIAL_INBOUND);
        p.setRecommended(true);
        p.setMatched(true);
        p.setSelectable(true);
        p.setMatchReasons(Collections.singletonList("证券当前在目标池调入互斥池中，按特殊审批流程处理："
                + poolNames(matchedPoolIds, shared.getPoolMap())));
        p.setUnmatchReasons(new ArrayList<>());
        // 构建特殊审批流程候选项
        return buildPoolFlowOption(p);
    }

    /**
     * 按现有存量入口的根节点和启用叶子范围识别产品库
     *
     * 执行顺序：
     * 1. 排除停用、删除和非叶子节点
     * 2. 沿本次已加载的父子关系确认属于存量产品库树
     *
     * @param pool 目标池
     * @param poolMap 已加载的投资池集合
     * @return 是否属于产品库快速入库范围
     */
    boolean isProductInboundTarget(InvestmentPoolBo pool, Map<Long, InvestmentPoolBo> poolMap) {
        if (pool == null || !"enabled".equals(pool.getStatus()) || Integer.valueOf(1).equals(pool.getIsDeleted())) {
            return false;
        }
        for (InvestmentPoolBo candidate : poolMap.values()) {
            if (pool.getId().equals(candidate.getParentId()) && "enabled".equals(candidate.getStatus())
                    && !Integer.valueOf(1).equals(candidate.getIsDeleted())) {
                return false;
            }
        }
        Long current = pool.getParentId();
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            InvestmentPoolBo parent = poolMap.get(current);
            if (parent == null) {
                return false;
            }
            if (ProductInboundPoolCodes.ROOT.equals(parent.getPoolCode())) {
                return true;
            }
            current = parent.getParentId();
        }
        return false;
    }

    /**
     * 判断证券是否具备产品库快速入库来源资格
     *
     * 执行顺序：
     * 1. 确认目标属于存量产品库范围
     * 2. 检查普通状态表的有效来源，CRMW 库受开关控制
     * 3. 普通来源未命中时按标的代码检查 CRMW 组合状态
     *
     * @param securityCode 证券代码
     * @param pool 目标池
     * @param poolMap 已加载的投资池集合
     * @param currentPoolIds 当前已生效的在库集合，不含本次待申请项
     * @return 是否满足产品库快速资格
     */
    boolean isProductInboundFlowMatched(String securityCode, InvestmentPoolBo pool,
                                                 Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds) {
        return isProductInboundFlowMatched(securityCode, pool, poolMap, currentPoolIds, null);
    }

    /** 批次检查产品库来源时复用已查的 CRMW 标的状态 */
    boolean isProductInboundFlowMatched(String securityCode, InvestmentPoolBo pool,
                                                 Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                                 BatchSecurityCheckData batchData) {
        // 复用目标范围判断，不按池名或池类型扩大范围
        if (!isProductInboundTarget(pool, poolMap) || currentPoolIds == null) {
            return false;
        }
        for (Long id : currentPoolIds) {
            InvestmentPoolBo source = poolMap.get(id);
            if (source != null && ProductInboundPoolCodes.SOURCES.contains(source.getPoolCode())
                    && (crmwUsePoolStatus || !ProductInboundPoolCodes.CRMW.equals(source.getPoolCode()))
                    && !Integer.valueOf(1).equals(source.getIsDeleted())) {
                return true;
            }
        }
        for (InvestmentPoolBo source : poolMap.values()) {
            if (ProductInboundPoolCodes.CRMW.equals(source.getPoolCode())
                    && Integer.valueOf(0).equals(source.getIsDeleted())) {
                return batchData == null ? securityPoolAdjustMapper.querySecurityInCrmwSourcePool(
                        securityCode, source.getId()) : batchData.crmwSources.contains(securityCode);
            }
        }
        return false;
    }

    /**
     * 为产品库构建快速与原流程候选，来源资格满足时优先快速
     *
     * 执行顺序：
     * 1. 判断证券的当前有效来源
     * 2. 构建 simple_in 快速候选并标记材料豁免依据
     * 3. 保留原特殊或普通流程，未命中快速时仍推荐原流程
     *
     * @param securityCode 证券代码
     * @param shared 本次校验数据
     * @param pool 产品库目标池
     * @return 当前目标池的流程候选
     */
    private List<AdjustCheckDto.FlowOption> resolveProductInboundFlowOptions(String securityCode, AdjustSharedData shared,
                                                                           InvestmentPoolBo pool) {
        // 产品库资格独立于信用债简易的评级和历史报告条件，ABS 只提供一般入库流程
        boolean abs = CreditBondSpecialInboundRule.isAbs(shared.getSecurityInfo());
        boolean matched = !abs && isProductInboundFlowMatched(
                securityCode, pool, shared.getPoolMap(), shared.getCurrentPoolIds(), shared.getBatchData());
        FlowOptionParam fast = new FlowOptionParam();
        fast.setFlowType(FlowType.SIMPLE_INBOUND.getCode());
        fast.setFlowName(resolveFlowName(pool.getSimpleInFlowName(), "产品库快速入库流程"));
        fast.setFlowId(pool.getSimpleInFlowId());
        fast.setFlowKey(pool.getSimpleInFlowKey());
        fast.setMatched(matched);
        fast.setSelectable(matched);
        fast.setRecommended(matched);
        fast.setMatchReasons(matched ? Collections.singletonList("证券当前已在产品库快速入库允许的来源库")
                : Collections.<String>emptyList());
        fast.setUnmatchReasons(matched ? Collections.<String>emptyList()
                : Collections.singletonList(abs ? "ABS 不适用快速入库流程"
                : "证券当前不在指定来源库，不能使用产品库快速入库流程"));
        // 统一构建快速候选并返回原始依据编码，中文提示由页面展示
        AdjustCheckDto.FlowOption fastOption = buildPoolFlowOption(fast);
        if (matched) {
            fastOption.setMaterialExemptionType(MaterialExemptionType.PRODUCT_SOURCE.getCode());
        }
        // 原特殊流程仍可选择，避免快速候选覆盖用户的普通办理路径
        AdjustCheckDto.FlowOption original = resolveSpecialInboundFlowOption(pool, shared);
        if (original == null) {
            original = buildNormalInboundFlowOption(pool, "使用产品库原调入流程");
        }
        original.setRecommended(!matched);
        return new ArrayList<>(Arrays.asList(fastOption, original));
    }

    /**
     * 用关联市场已加载的数据检查快速资格，任一关联码不符则整组改荐原流程
     *
     * 执行顺序：
     * 1. 仅检查有材料豁免依据且可选的快速候选
     * 2. 逐码复用当前校验数据检查对应快速资格
     * 3. 不满足时禁用快速候选，保留具体代码并推荐原流程
     *
     * @param shared 主券与关联市场的已加载数据
     * @param item 当前主项
     * @param options 当前流程候选
     */
    void restrictRelatedFastFlowOptions(AdjustSharedData shared,
                                                 AdjustCheckDto.CheckResultItem item,
                                                 List<AdjustCheckDto.FlowOption> options) {
        if (item == null) {
            return;
        }
        InvestmentPoolBo pool = shared.getPoolMap().get(item.getTargetPoolId());
        for (AdjustCheckDto.FlowOption option : options) {
            if (!option.isSelectable() || option.getMaterialExemptionType() == null) {
                continue;
            }
            List<String> failures = new ArrayList<>();
            for (Map.Entry<String, AdjustSharedData> entry : shared.getRelatedSecurityData().entrySet()) {
                AdjustSharedData related = entry.getValue();
                boolean matched;
                if (MaterialExemptionType.PRODUCT_SOURCE.getCode().equals(option.getMaterialExemptionType())) {
                    // 按关联码判断产品库来源资格
                    matched = isProductInboundFlowMatched(entry.getKey(), pool, related.getPoolMap(),
                            related.getCurrentPoolIds(), related.getBatchData());
                } else {
                    AdjustCheckReq relatedReq = new AdjustCheckReq();
                    relatedReq.setSecurityCode(entry.getKey());
                    // 简易资格在下一步按实际代码判断，提交阶段不重跑
                    matched = isSimpleInboundFlowMatched(relatedReq, related, pool,
                            new ArrayList<String>(), new ArrayList<String>());
                }
                if (!matched) {
                    failures.add("关联代码[" + entry.getKey() + "]不满足快速入库资格");
                }
            }
            if (!failures.isEmpty()) {
                option.setMatched(false);
                option.setSelectable(false);
                option.setRecommended(false);
                option.setMaterialExemptionType(null);
                option.getUnmatchReasons().addAll(failures);
                for (AdjustCheckDto.FlowOption original : options) {
                    if (original.isSelectable()) {
                        original.setRecommended(true);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 根据投资池 ID 列表转换投资池名称。
     */
    private String poolNames(List<Long> poolIds, Map<Long, InvestmentPoolBo> poolMap) {
        if (poolIds == null || poolIds.isEmpty()) {
            return "";
        }
        return poolIds.stream()
                .map(id -> {
                    InvestmentPoolBo pool = poolMap != null ? poolMap.get(id) : null;
                    return pool != null && pool.getPoolName() != null ? pool.getPoolName() : String.valueOf(id);
                })
                .collect(Collectors.joining("、"));
    }

    /**
     * 生成流程候选项去重 key。
     */
    private String buildFlowOptionUniqueKey(AdjustCheckDto.FlowOption option) {
        if (option == null) {
            return "";
        }
        return String.valueOf(option.getFlowId()) + "|"
                + String.valueOf(option.getFlowKey()) + "|"
                + String.valueOf(option.getFlowType()) + "|"
                + String.valueOf(option.getFlowName());
    }

    /**
     * 白名单流程命中判断入口。
     *
     * 伪代码口径：
     * 1. 证券期限 <= 3 年（解析 date_exists_str）；
     * 2. 排除永续债、私募债、ABS 债；
     * 3. 债券类型属于债券类；
     * 4. 债券主体在白名单配置池中；
     * 5. 债券不是担保债。
     *
     * TODO 当前版本不新增白名单配置表、不修改表结构，因此仅保留入口并返回未命中。
     * 老系统实际使用 WHITEPOOLID_XYJJ 指定的主体白名单池，并排除永续债、私募债、ABS、次级债，
     * 满足剩余期限不超过 3 年后由用户确认是否选择白名单流程。
     */
    boolean isWhitelistFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, List<String> matchReasons, List<String> unmatchReasons) {
        SecurityInfoBo sec = shared.getSecurityInfo();

        // 条件1：从 date_exists_str 解析证券期限并判断是否不超过 3 年
        BigDecimal remainYears = CreditBondRemainTermUtil.parseRemainTermYears(sec.getDateExistsStr());
        if (remainYears == null) {
            unmatchReasons.add("证券期限无法解析，date_exists_str 为空或格式不正确");
        } else if (remainYears.compareTo(new BigDecimal("3")) <= 0) {
            matchReasons.add("证券期限为 " + sec.getDateExistsStr() + "，未超过 3 年");
        } else {
            unmatchReasons.add("证券期限为 " + sec.getDateExistsStr() + "，超过 3 年");
        }

        // 条件2：排除永续债、私募债、ABS 债
        boolean isPerpetual = sec.getYxFlag() != null && sec.getYxFlag() == 1;
        boolean isAbs = sec.getAbsFlag() != null && sec.getAbsFlag() == 1;
        boolean isPrivatePlacement = (sec.getIssueType() != null && sec.getIssueType().contains("私募"))
                || (sec.getInnerClass() != null && sec.getInnerClass().contains("私募"));
        if (isPerpetual) {
            unmatchReasons.add("债券为永续债，不符合白名单条件");
        } else if (isAbs) {
            unmatchReasons.add("债券为 ABS 债，不符合白名单条件");
        } else if (isPrivatePlacement) {
            unmatchReasons.add("债券为私募债，不符合白名单条件");
        } else {
            matchReasons.add("债券非永续债、私募债、ABS 债");
        }

        // 条件3：债券类型属于债券类
        String categoryType = shared.getBatchData() == null
                ? securityPoolAdjustMapper.queryCategoryTypeBySecurityType(sec.getSecurityType())
                : shared.getBatchData().categories.get(sec.getSecurityType());
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            matchReasons.add("债券类型属于债券类");
        } else {
            unmatchReasons.add("债券类型不属于债券类");
        }

        // 条件4：债券主体在白名单配置池中（当前 WHITELIST_POOL_IDS 空集，暂不查库）
        if (!WHITELIST_POOL_IDS.isEmpty()) {
            // TODO 后续接入 queryIssuerInWhitelistPools 查询主体在白名单池
            matchReasons.add("主体在白名单池中");
        } else {
            unmatchReasons.add("白名单池未配置，主体在白名单池条件不成立");
        }

        // 条件5：债券不是担保债
        boolean isGuaranteed = sec.getGuarantFlag() != null && sec.getGuarantFlag() == 1;
        if (!isGuaranteed) {
            matchReasons.add("债券非担保债");
        } else {
            unmatchReasons.add("债券为担保债，不符合白名单条件");
        }

        return unmatchReasons.isEmpty();
    }

    /**
     * 简易流程命中判断入口。
     *
     * 以下四项必须全部满足，逐项记录命中或不命中的原因：
     *
     *   1. 当前规则唯一允许的层级为信用债一至三级，且目标池本身在准入范围内。
     *   2. 同发行人债券近180天以非简易入库流程进入过信用债大库任意层级，含升库和降库。
     *   3. 同发行人债券近180天实际入库的已通过调入业务中有信评附件。
     *   4. 按永续、次级、标准信用债的顺序匹配一类附加要求。
     *
     * 唯一性按完整一至五级准入范围判断，不受当前勾选池、权限或放开规则开关影响。
     */
    boolean isSimpleInboundFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, InvestmentPoolBo targetPool,
            List<String> matchReasons, List<String> unmatchReasons) {

        SecurityInfoBo security = shared.getSecurityInfo();
        // ABS 只能走一般入库流程，不提供快速入库候选
        if (CreditBondSpecialInboundRule.isAbs(security)) {
            unmatchReasons.add("ABS 不适用快速入库流程");
            return false;
        }
        // 1. 唯一准入层级：完整一至五级只能允许一个层级，且该层级和目标池须满足一至三级要求
        // 简易流程只能用于信用债一至三级库
        if (isCreditBondLevelOneToThree(targetPool)) {
            matchReasons.add("目标池属于信用债大库一、二、三级库");
        } else {
            unmatchReasons.add("目标池不是信用债大库一、二、三级库");
        }

        // 读取标准矩阵结果，保留特殊债降级前的基准层级
        String gradeCode = resolveMatrixGradeCode(security, shared.getBatchData());
        List<Long> matrixPoolIds = Collections.emptyList();
        if (gradeCode != null) {
            // 简易资格沿用当前入库规则的年期限口径
            String bucketCode = matchTermBucket(CreditBondRemainTermUtil.resolveRemainTermYears(security), shared.getBatchData());
            if (bucketCode != null) {
                matrixPoolIds = queryInboundMatrix(gradeCode, bucketCode, shared.getBatchData());
            }
        }
        // 先按特殊债及观察名单展开全部一至五级，再判断是否唯一
        Integer standardBestSort = resolveBestAllowedSort(matrixPoolIds, shared.getPoolMap());
        Set<Integer> allowedLevels = resolveSimpleInboundAllowedLevels(shared, matrixPoolIds, standardBestSort);
        if (allowedLevels.size() == 1) {
            Integer onlyLevel = allowedLevels.iterator().next();
            // 同一层级可能存在多个池，目标池本身也必须在实际准入范围内
            boolean targetAllowed = isSimpleInboundTargetAllowed(shared, matrixPoolIds, standardBestSort, targetPool);
            if (onlyLevel <= 3 && targetAllowed && onlyLevel.equals(targetPool.getInnerSort())) {
                matchReasons.add("当前入库规则仅允许调入信用债" + onlyLevel + "级库");
            } else {
                unmatchReasons.add("当前唯一可入层级须为一至三级库，且与目标池一致");
            }
        } else if (allowedLevels.isEmpty()) {
            unmatchReasons.add("当前入库规则未匹配可入层级库");
        } else {
            unmatchReasons.add("当前入库规则允许多个层级库（" + allowedLevels + "），不满足唯一层级条件");
        }

        // 2. 非简易入库经历：同发行人近180天以简易之外的流程审批通过入库，包含升库、降库等
        // 已出池的历史仍计入，不要求与本次目标池同层级
        BatchSecurityCheckData batchData = shared.getBatchData();
        String issuer = batchData == null ? null : batchData.securities.get(req.getSecurityCode()).getIssuerCode();
        boolean hasNonSimpleInbound = batchData == null
                ? securityPoolAdjustMapper.queryIssuerHasNonSimpleCreditBondInboundWithinDays(
                        req.getSecurityCode(), SIMPLE_INBOUND_HISTORY_DAYS)
                : batchData.nonSimpleInboundIssuers.contains(issuer);
        if (hasNonSimpleInbound) {
            matchReasons.add("同发行人半年内以非简易入库流程进入过信用债大库");
        } else {
            unmatchReasons.add("同发行人半年内未以非简易入库流程进入信用债大库");
        }

        // 3. 信评附件按所属调入业务的实际入库时间计算180天，手工上传和内外部引用均计入
        // 不检查原报告主体，不要求与上面的非简易信用债入库经历来自同一笔业务
        if (batchData == null ? securityPoolAdjustMapper.queryIssuerHasRecentCreditReportWithinDays(
                req.getSecurityCode(), SIMPLE_INBOUND_HISTORY_DAYS) : batchData.recentReportIssuers.contains(issuer)) {
            matchReasons.add("同发行人180天内已入库的调入业务有有效信评附件");
        } else {
            unmatchReasons.add("同发行人180天内已入库的调入业务没有有效信评附件");
        }

        // 4. 债券类型要求：按永续、次级、标准信用债的顺序匹配一类
        int failureCount = unmatchReasons.size();
        // 增加对应类型未满足的原因，未增加失败原因时记录本项命中
        checkSimpleInboundSpecialConditions(shared, standardBestSort,
                targetPool != null ? targetPool.getInnerSort() : null, unmatchReasons);
        if (failureCount == unmatchReasons.size()) {
            matchReasons.add("满足债券类型对应的简易入库附加条件");
        }
        return unmatchReasons.isEmpty();
    }

    /** 根据完整准入规则计算一至五级可入层级，避免只看目标池造成唯一性误判。 */
    private Set<Integer> resolveSimpleInboundAllowedLevels(
            AdjustSharedData shared, List<Long> matrixPoolIds, Integer standardBestSort) {
        Set<Integer> levels = new TreeSet<>();
        SecurityInfoBo security = shared.getSecurityInfo();
        // 1. 无标准矩阵结果或属于分级库排除类型时，不存在可入层级
        if (matrixPoolIds == null || matrixPoolIds.isEmpty() || shared.getPoolMap() == null
                || CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(security)) {
            return levels;
        }
        // 2. 在标准最佳档位基础上应用特殊债及观察名单规则，确定实际准入起始层级
        boolean inObserve = shared.isSecurityInObservePool() || shared.isIssuerInObservePool();
        boolean inRestricted = shared.isSecurityInRestrictedPool() || shared.isIssuerInRestrictedPool();
        CreditBondSpecialInboundRule.GradedInboundMode mode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(security, inObserve);
        Integer startSort = mode.resolveStartSort(standardBestSort);
        // 重点观察名单按当前所在层级执行既有准入限制
        Integer currentSort = resolveCurrentGradedSort(shared.getCurrentPoolIds(), shared.getPoolMap());
        // 3. 遍历全部分级库并按层级去重，不能只筛一至三级或只看本次勾选池
        for (InvestmentPoolBo pool : shared.getPoolMap().values()) {
            if (!CreditBondSpecialInboundRule.isGradedLevelPool(pool)) {
                continue;
            }
            // 保留当前准入规则对重点观察名单的处理
            if (CreditBondSpecialInboundRule.checkRestricted(security, inRestricted, currentSort,
                    pool.getInnerSort(), BusinessSwitches.STRONG_GUARANTEE_ENABLED) != null) {
                continue;
            }
            // 按矩阵或特殊债调整后的范围收集完整层级
            if (isLeafAllowedByGradeRule(pool, matrixPoolIds, mode, startSort, shared.getPoolMap())) {
                levels.add(pool.getInnerSort());
            }
        }
        return levels;
    }

    /** 目标池须为规则实际允许的分级库，不能仅凭与另一个允许池同档认定可入。 */
    private boolean isSimpleInboundTargetAllowed(
            AdjustSharedData shared, List<Long> matrixPoolIds, Integer standardBestSort, InvestmentPoolBo targetPool) {
        if (!CreditBondSpecialInboundRule.isGradedLevelPool(targetPool)) {
            return false;
        }
        boolean inObserve = shared.isSecurityInObservePool() || shared.isIssuerInObservePool();
        CreditBondSpecialInboundRule.GradedInboundMode mode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(shared.getSecurityInfo(), inObserve);
        // 按与完整准入集合相同的档位规则核对目标池
        return isLeafAllowedByGradeRule(targetPool, matrixPoolIds, mode,
                mode.resolveStartSort(standardBestSort), shared.getPoolMap());
    }

    /**
     * 简易条件4：检查特殊债档位要求，以及次级债和标准信用债的期限上限。
     *
     * @param standardBestSort 特殊债调整前的标准矩阵最佳层级，层级数字增加1表示下调一级
     * @param targetSort 本次目标层级
     */
    void checkSimpleInboundSpecialConditions(
            AdjustSharedData shared, Integer standardBestSort, Integer targetSort, List<String> failures) {
        SecurityInfoBo security = shared.getSecurityInfo();
        // 特殊债要求只看发债主体内评，不用担保人内评替代
        String issuerGrade = normalizeGradeCode(security.getInnerIssuerRating());
        BigDecimal remainYears = CreditBondRemainTermUtil.resolveRemainTermYears(security);

        // 4.1 永续债：内评1档恰好下调一级，其他档位至少下调一级
        if (CreditBondSpecialInboundRule.isPerpetual(security)) {
            boolean levelMatched = false;
            if (standardBestSort != null && targetSort != null) {
                if ("1".equals(issuerGrade)) {
                    levelMatched = targetSort == standardBestSort + 1;
                } else {
                    levelMatched = targetSort >= standardBestSort + 1;
                }
            }
            if (!levelMatched) {
                failures.add("永续债内评1档须按标准入库等级下调一级，其余须至少下调一级");
            }
        } else if (CreditBondSpecialInboundRule.isSubordinated(security)) {
            // 4.2 次级债：先检查目标档位，再检查内评2-档的五年期限上限
            boolean gradeTwoBand = "2+".equals(issuerGrade) || "2".equals(issuerGrade) || "2-".equals(issuerGrade);
            boolean levelMatched;
            if ("1".equals(issuerGrade)) {
                // 内评1档直接要求一级库，不按标准矩阵最佳档位下调
                levelMatched = Integer.valueOf(1).equals(targetSort);
            } else if (standardBestSort == null || targetSort == null) {
                levelMatched = false;
            } else if (gradeTwoBand) {
                // 内评2+/2/2-档恰好下调一级
                levelMatched = targetSort == standardBestSort + 1;
            } else {
                // 其余档位至少下调一级
                levelMatched = targetSort >= standardBestSort + 1;
            }
            if (!levelMatched) {
                failures.add("次级债内评1档须调入一级库，2+/2/2-须下调一级，其余须至少下调一级");
            }
            // 内评2-档还须期限不超过5年；等于5年可通过，空值或负数不通过
            if ("2-".equals(issuerGrade)
                    && (remainYears == null || remainYears.signum() < 0
                    || remainYears.compareTo(BigDecimal.valueOf(5)) > 0)) {
                failures.add("内评2-档的次级债期限须在5年及以内");
            }
        } else if (isStandardCreditBond(security) && "3".equals(issuerGrade)
                && (remainYears == null || remainYears.signum() < 0
                || remainYears.compareTo(BigDecimal.ONE) > 0)) {
            // 4.3 标准信用债：内评3档期限须在1年及以内，空值或负数不通过
            failures.add("内评3档的标准信用债期限须在1年及以内");
        }
    }

    /**
     * 独立判断标准信用债，完整业务定义由上游维护，只有std_credit_flag=1时认定为标准。
     */
    boolean isStandardCreditBond(SecurityInfoBo security) {
        return security != null && Integer.valueOf(1).equals(security.getStdCreditFlag());
    }

    /**
     * 判断信用债在库调整流程是上调还是下调。
     *
     * 同一父级下，目标池 innerSort 小于当前所在池为升库，大于当前所在池为降库；
     * 未找到同父级当前池时返回 null，由调用方走目标池标准调入流程。
     */
    private String resolveCreditBondAdjustFlowType(InvestmentPoolBo targetPool, AdjustSharedData shared) {
        if (targetPool == null || targetPool.getParentId() == null || targetPool.getInnerSort() == null) {
            return null;
        }

        for (Long currentPoolId : shared.getCurrentPoolIds()) {
            InvestmentPoolBo currentPool = shared.getPoolMap().get(currentPoolId);
            if (currentPool == null || currentPool.getParentId() == null || currentPool.getInnerSort() == null) {
                continue;
            }
            if (!targetPool.getParentId().equals(currentPool.getParentId())) {
                continue;
            }
            if (targetPool.getInnerSort() > currentPool.getInnerSort()) {
                return FlowType.DOWNGRADE_INBOUND.getCode();
            }
            if (targetPool.getInnerSort() < currentPool.getInnerSort()) {
                return FlowType.UPGRADE_INBOUND.getCode();
            }
        }
        return null;
    }

    /**
     * 判断目标池是否属于信用债大库。
     */
    boolean isCreditBondPool(InvestmentPoolBo pool) {
        if (pool == null) {
            return false;
        }
        return PoolType.CREDIT_BOND.getCode().equals(pool.getPoolType());
    }

    /**
     * 判断是否为临时代码占位证券（security_source=temporary）。
     */
    private boolean isTemporarySecurity(SecurityInfoBo sec) {
        return sec != null && "temporary".equals(sec.getSecuritySource());
    }

    /**
     * 判断证券当前是否已在信用债大库中。
     */
    private boolean isSecurityInCreditBondPool(AdjustSharedData shared) {
        for (Long currentPoolId : shared.getCurrentPoolIds()) {
            InvestmentPoolBo currentPool = shared.getPoolMap().get(currentPoolId);
            // 判断当前池是否属于信用债大库
            if (isCreditBondPool(currentPool)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断目标池是否为信用债大库一、二、三级库。
     */
    private boolean isCreditBondLevelOneToThree(InvestmentPoolBo pool) {
        return pool != null
                && PoolType.CREDIT_BOND.getCode().equals(pool.getPoolType())
                && pool.getInnerSort() != null
                && pool.getInnerSort() >= 1
                && pool.getInnerSort() <= 3;
    }

    /**
     * 构建白名单流程候选项。
     */
    private AdjustCheckDto.FlowOption buildWhitelistFlowOption(FlowOptionParam p, BatchSecurityCheckData batchData) {

        FlowDefinitionBo flow = queryBatchFlow(FLOW_KEY_WHITELIST_INBOUND, batchData);
        List<String> reasons = new ArrayList<>(p.getUnmatchReasons());
        if (flow == null) {
            reasons.add("未找到已启用流程定义：" + FLOW_KEY_WHITELIST_INBOUND);
        }
        p.setFlowType(FlowType.WHITELIST_INBOUND.getCode());
        p.setFlowName(flow != null && flow.getName() != null ? flow.getName() : "白名单流程");
        p.setFlowId(flow != null ? flow.getId() : null);
        p.setFlowKey(FLOW_KEY_WHITELIST_INBOUND);
        p.setSelectable(p.isMatched());
        p.setUnmatchReasons(reasons);
        // 构建流程候选项（统一入口）
        return buildFlowOption(p);
    }

    /**
     * 构建目标池标准调入流程候选项。
     */
    private AdjustCheckDto.FlowOption buildNormalInboundFlowOption(InvestmentPoolBo targetPool, String matchReason) {
        String flowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
        boolean noApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
        FlowOptionParam p = new FlowOptionParam();
        p.setFlowType(FlowType.NORMAL_INBOUND.getCode());
        p.setFlowName(flowName);
        p.setFlowId(targetPool.getInFlowId());
        p.setFlowKey(targetPool.getInFlowKey());
        p.setRecommended(true);
        p.setMatched(true);
        p.setSelectable(true);
        p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                matchReason, flowName, noApproval, "默认调入流程")));
        p.setUnmatchReasons(new ArrayList<>());
        // 构建默认调入流程候选项
        return buildPoolFlowOption(p);
    }

    /**
     * 构建目标池默认流程说明。
     */
    private String buildTargetFlowReason(String prefix, String flowName, boolean noApproval, String flowLabel) {
        String reasonPrefix = prefix != null && !prefix.isEmpty() ? prefix + "，" : "";
        if (noApproval) {
            return reasonPrefix + "目标池未配置" + flowLabel + "，按不需要审批处理";
        }
        return reasonPrefix + "使用目标池配置的默认「" + flowName + "」";
    }

    /**
     * 使用目标池配置构建流程候选项。
     *
     * 调用方需先将 {@code configuredFlowName} 与 {@code fallbackFlowName} 决议为最终的
     * {@code flowName} 后传入 {@link FlowOptionParam}。
     */
    private AdjustCheckDto.FlowOption buildPoolFlowOption(FlowOptionParam p) {
        boolean noApproval = isNoApprovalFlow(p.getFlowId(), p.getFlowKey());
        FlowOptionParam result = new FlowOptionParam();
        result.setFlowType(p.getFlowType());
        result.setFlowName(noApproval ? "不需要审批" : p.getFlowName());
        result.setFlowId(p.getFlowId());
        result.setFlowKey(p.getFlowKey());
        result.setRecommended(p.isRecommended());
        result.setMatched(p.isMatched());
        result.setSelectable(p.isSelectable());
        result.setMatchReasons(p.getMatchReasons());
        result.setUnmatchReasons(p.getUnmatchReasons());
        // 构建流程候选项（统一入口）
        return buildFlowOption(result);
    }

    /**
     * 判断目标池是否未配置审批流程。
     */
    private boolean isNoApprovalFlow(Long flowId, String flowKey) {
        return flowId == null && (flowKey == null || flowKey.isEmpty());
    }

    /**
     * 解析流程名称：优先使用配置名称，为空时回退到默认名称。
     */
    private static String resolveFlowName(String configuredName, String fallbackName) {
        return configuredName != null && !configuredName.isEmpty() ? configuredName : fallbackName;
    }

    /**
     * 构建流程候选项（统一入口）。
     */
    private AdjustCheckDto.FlowOption buildFlowOption(FlowOptionParam p) {

        AdjustCheckDto.FlowOption option = new AdjustCheckDto.FlowOption();
        option.setFlowType(p.getFlowType());
        option.setFlowId(p.getFlowId());
        option.setFlowKey(p.getFlowKey());
        option.setFlowName(p.getFlowName());
        option.setRecommended(p.isRecommended());
        option.setMatched(p.isMatched());
        option.setSelectable(p.isSelectable());
        option.setMatchReasons(p.getMatchReasons());
        option.setUnmatchReasons(new ArrayList<>(p.getUnmatchReasons()));
        return option;
    }

    /**
     * 调入校验：通用校验 + 类型特有校验
     *
     * 先执行 checkCommonIn（通用：池锁定/品种/市场/pending/重复入池/容量/来源/限制/互斥/弹性），
     * 再按 categoryType 路由类型特有校验（债券到期 checkBondIn / 股票退市 checkStockIn 等）。
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    List<String> checkInConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        if (ctx.isExcelCompanyFollow()) {
            // 线下主体决定优先，池关系和在途冲突仍需执行
            addIfFailed(failures, preCheckPendingProcess(ctx));
            addIfFailed(failures, inCheckRestrictPool(ctx));
            addIfFailed(failures, inCheckMutexConflict(ctx));
            addIfWarning(ctx.getWarnings(), inCheckElasticPool(ctx));
            return failures;
        }
        // 通用校验（所有类型都走）
        failures.addAll(checkCommonIn(ctx));
        // 类型特有校验（按 categoryType 路由）
        String categoryType = ctx.getCategoryType();
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            failures.addAll(checkBondIn(ctx));
        } else if (CategoryType.STOCK.getCode().equals(categoryType)) {
            failures.addAll(checkStockIn(ctx));
        } else if (CategoryType.FUND.getCode().equals(categoryType)) {
            failures.addAll(checkFundIn(ctx));
        } else if (CategoryType.COMPANY.getCode().equals(categoryType)) {
            failures.addAll(checkCompanyIn(ctx));
        }
        // 按发行主体复核黑名单质押库调入条件
        addPledgeBlacklistFailure(failures, ctx, AdjustMode.IN.getCode());
        return failures;
    }

    /**
     * 通用调入校验（所有类型都走）
     *
     * 含池锁定、品种、市场、pending流程、重复入池、容量、来源池、限制池、互斥、弹性禁投。
     * 不含证券到期（已拆到类型特有：债券到期 checkBondIn / 股票退市 checkStockIn）。
     */
    private List<String> checkCommonIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 前置检查：目标池是否已锁定（最硬拦截，优先执行）
        addIfFailed(failures, inCheckPoolLocked(ctx));
        // 前置检查：证券品种是否符合目标池配置
        addIfFailed(failures, inCheckVariety(ctx));
        // 前置检查：证券市场是否符合目标池配置
        addIfFailed(failures, inCheckMarket(ctx));
        // 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 入池检查：证券是否已在目标池中
        addIfFailed(failures, inCheckSecurityAlreadyInPool(ctx));
        // 入池检查：目标池容量是否已满
        addIfFailed(failures, inCheckPoolCapacity(ctx));
        // 入池检查：来源池是否满足要求
        addIfFailed(failures, inCheckSourcePool(ctx));
        // 入池检查：证券当前在调入限制池中（in_restrict）
        addIfFailed(failures, inCheckRestrictPool(ctx));
        // 入池检查：本次请求中是否同时勾选了互斥池（不可同时调入）
        addIfFailed(failures, inCheckMutexConflict(ctx));
        // 入池检查：证券当前在弹性禁投池中（in_soft_restrict，警告不阻断）
        addIfWarning(ctx.getWarnings(), inCheckElasticPool(ctx));
        // 入池检查：证券是否在全局禁止池（forbidden 禁投池）
        addIfFailed(failures, inCheckForbiddenPool(ctx));
        // 行业限制校验（按池 industry_code，调入）——暂时不需要，先注释
        // addIfFailed(failures, inCheckIndustry(ctx));
        // 开放日校验（按池 open_day_adjust，调入）
        addIfFailed(failures, inCheckOpenDay(ctx));
        return failures;
    }

    /**
     * 债券类型特有调入校验
     *
     * 含债券到期校验（maturity_date 早于今日则禁止调入）。
     */
    private List<String> checkBondIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 债券到期校验
        addIfFailed(failures, inCheckBondMaturity(ctx));
        // 主体债入库规则校验（信用债大库矩阵）
        addIfFailed(failures, inCheckMainGradeRule(ctx));
        return failures;
    }

    /**
     * 规则：主体债入库规则（credit_bond_pool_grade_rule 矩阵）
     *
     * 调入信用债大库池时，按债券的「主体内评分档 × 期限档」查矩阵得到允许调入的池列表。
     * 担保债取主体与担保人内评孰高后再查矩阵；
     * ABS：所选权益人内评（自选权益人优先，复用 innerGuarantorRating）1 档只能调入一级库，缺少内评或非 1 档至少下调一级；
     * 私募发债主体内评 1 档只能调入一级库，其余至少下调一级；
     * 永续发债主体内评 1 档下调一级（矩阵最好档再降一档且只留那一档），其余至少下调一级；
     * 次级发债主体内评 1 档只能调入一级库，2+/2/2- 下调一级（只留那一档），其余至少下调一级。
     * 担保债或已在观察池：不得高于矩阵最好档，从该档开到五级。
     * 已在重点观察名单：未在分级库不得新增（开关开启时强担保豁免），已在 1～4 级只能去五级。开关开启时池间调入限制仅对强担保豁免 restricted 池，禁止池仍全局拦截。可转债/可交换债/CRMW 不适用信用债 1～5。
     * 正式证券无内评禁止入信用债 1～5 级；临时代码默认档 4。
     * 期限为空默认最长档（期限>5）继续走矩阵，不跳过。
     * 境外债目标池不走本规则。仅调入校验。
     */
    String inCheckMainGradeRule(AdjustCheckContext ctx) {
        // 是否放开规则：是=跳过主体债入库矩阵校验（对齐批量调库 releaseRules）
        if (ctx.isReleaseRules()) {
            return null;
        }
        InvestmentPoolBo pool = ctx.getTargetPool();
        // 前置门控：仅信用债分级库校验（境外债跳过）
        if (!isGradedBondPool(pool)) {
            return null;
        }
        SecurityInfoBo sec = ctx.getSecurityInfo();
        if (sec == null) {
            return null;
        }
        // 可转债 / 可交换债 / CRMW 不能调入信用债分级库
        if (CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(sec)) {
            return "可转债、可交换债、信用风险缓释工具不适用信用债分级库";
        }
        // 证券自身或发行主体任一在重点观察名单，即走重点观察名单禁新增规则
        boolean inRestricted = ctx.isSecurityInRestrictedPool() || ctx.isIssuerInRestrictedPool();
        // 取当前已在分级库最好档，供重点观察名单区分新增/已在库
        Integer currentGradedSort = resolveCurrentGradedSort(ctx.getCurrentPoolIds(), ctx.getPoolMap());
        // 重点观察名单：未在分级库不得新增；已在 1～4 级只能去五级
        String restrictedFail = CreditBondSpecialInboundRule.checkRestricted(
                sec, inRestricted, currentGradedSort,
                CreditBondSpecialInboundRule.isGradedLevelPool(pool) ? pool.getInnerSort() : null,
                BusinessSwitches.STRONG_GUARANTEE_ENABLED);
        if (restrictedFail != null) {
            return restrictedFail;
        }
        // 临时代码默认档 4，担保债取孰高
        String gradeCode = resolveMatrixGradeCode(sec, ctx.getBatchData());
        if (gradeCode == null) {
            return CreditBondSpecialInboundRule.isAbs(sec)
                    ? "所选权益人无内评分" : "主体无内评分";
        }
        // 含权口径取剩余期限年（date_exists_str 解析，含权/回购已是年）；算不出期限时按最长档继续走矩阵，不跳过
        String bucketCode = matchTermBucket(CreditBondRemainTermUtil.resolveRemainTermYears(sec), ctx.getBatchData());
        if (bucketCode == null) {
            return "无法匹配债券期限档";
        }
        List<Long> allowedPoolIds = queryInboundMatrix(gradeCode, bucketCode, ctx.getBatchData());
        if (allowedPoolIds == null || allowedPoolIds.isEmpty()) {
            return "主体债入库矩阵未配置允许池";
        }
        // 标准矩阵最好档，再叠加特殊债/担保/观察池口径
        Integer bestAllowedSort = resolveBestAllowedSort(allowedPoolIds, ctx.getPoolMap());
        boolean inObserve = ctx.isSecurityInObservePool() || ctx.isIssuerInObservePool();
        CreditBondSpecialInboundRule.GradedInboundMode inboundMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(sec, inObserve);
        Integer startSort = inboundMode.resolveStartSort(bestAllowedSort);
        // 目标池是否在允许范围内（档位口径或矩阵点名的池）
        if (isLeafAllowedByGradeRule(pool, allowedPoolIds, inboundMode, startSort, ctx.getPoolMap())) {
            return null;
        }
        String targetName = pool.getPoolName() != null ? pool.getPoolName() : String.valueOf(pool.getId());
        if (inboundMode.usesSort() && startSort != null) {
            return "目标池「" + targetName + "」不在特殊债调整后的允许范围内（"
                    + inboundMode.describeRange(startSort) + "）";
        }
        StringBuilder names = new StringBuilder();
        for (Long pid : allowedPoolIds) {
            InvestmentPoolBo p = ctx.getPoolMap() == null ? null : ctx.getPoolMap().get(pid);
            String name = p != null ? p.getPoolName() : String.valueOf(pid);
            if (names.length() > 0) {
                names.append("、");
            }
            names.append(name);
        }
        return "目标池「" + targetName + "」不在入库矩阵允许范围内（允许：" + names + "）";
    }

    /**
     * 按剩余期限（年）匹配信用债期限分组编码。
     *
     * 遍历启用的 credit_bond_term_bucket，按 min_term_year/max_term_year + inclusive 标志判定区间归属。
     * 入参为 {@link CreditBondRemainTermUtil#resolveRemainTermYears}（date_exists_str 解析，含权/回购已是年）。
     *
     * @param remainTermYears 剩余期限年数；null 时按最长档（期限>5）兜底
     * @return 期限档编码；无可用档或年数落不进任何档时返回 null
     */
    String matchTermBucket(BigDecimal remainTermYears) {
        return matchTermBucket(remainTermYears, null);
    }

    /** 使用批次期限档或单券实时配置匹配期限 */
    String matchTermBucket(BigDecimal remainTermYears, BatchSecurityCheckData data) {
        return CreditBondRemainTermUtil.matchTermBucket(
                remainTermYears, data == null ? creditBondGradeRuleMapper.queryEnabledTermBucketList()
                        : data.termBuckets);
    }

    /**
     * 取两个主体内评分档中评级较高者（sort_no 较小者；担保债按主体与担保人孰高）。
     *
     * 评分档未命中字典时保持主体评级档不变。
     *
     * @param gradeCode1 主体内评档
     * @param gradeCode2 担保人内评档
     * @return 更好的那档
     */
    private String pickBetterRatingGrade(String gradeCode1, String gradeCode2, BatchSecurityCheckData data) {
        List<CreditBondInnerRatingGradeBo> grades = data == null
                ? creditBondGradeRuleMapper.queryEnabledRatingGradeList() : data.ratingGrades;
        Integer sort1 = null;
        Integer sort2 = null;
        for (CreditBondInnerRatingGradeBo g : grades) {
            if (g.getGradeCode().equals(gradeCode1)) {
                sort1 = g.getSortNo();
            }
            if (g.getGradeCode().equals(gradeCode2)) {
                sort2 = g.getSortNo();
            }
        }
        if (sort1 != null && sort2 != null && sort2 < sort1) {
            return gradeCode2;
        }
        return gradeCode1;
    }

    /**
     * 查矩阵用内评档：先取主档发行人内评；临时代码默认 4，担保债再与担保人内评取孰高。
     *
     * ABS 的评级主体固定写在发行人字段，担保人三字段为空，因此走到这里只会用到发行人内评，无需单独分支
     *
     * @param sec 证券主数据
     * @return 内评档编码；正式券无内评返回 null
     */
    String resolveMatrixGradeCode(SecurityInfoBo sec) {
        return resolveMatrixGradeCode(sec, null);
    }

    /** 按既有发行人和担保人取档规则获取矩阵内评 */
    String resolveMatrixGradeCode(SecurityInfoBo sec, BatchSecurityCheckData data) {
        // 空串视为无内评
        String gradeCode = normalizeGradeCode(sec.getInnerIssuerRating());
        // 临时代码占位常无内评，默认最低档 4
        if (gradeCode == null && isTemporarySecurity(sec)) {
            gradeCode = "4";
        }
        if (gradeCode != null && CreditBondSpecialInboundRule.isGuaranteed(sec)
                && sec.getInnerGuarantorRating() != null && !sec.getInnerGuarantorRating().isEmpty()) {
            // 担保债用更好的那档查矩阵
            gradeCode = pickBetterRatingGrade(gradeCode, sec.getInnerGuarantorRating(), data);
        }
        return gradeCode;
    }

    /** 同批相同评级档和期限档共用允许投资池 */
    private List<Long> queryInboundMatrix(String gradeCode, String bucketCode, BatchSecurityCheckData data) {
        if (data == null) {
            return creditBondGradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(gradeCode, bucketCode);
        }
        return data.matrix.computeIfAbsent(gradeCode + ":" + bucketCode,
                key -> creditBondGradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(gradeCode, bucketCode));
    }

    /** 同一批次只解析一次相同流程 Key */
    private FlowDefinitionBo queryBatchFlow(String flowKey, BatchSecurityCheckData data) {
        if (data == null) {
            return flowMapper.queryActiveFlowByKey(flowKey);
        }
        if (!data.flows.containsKey(flowKey)) {
            data.flows.put(flowKey, flowMapper.queryActiveFlowByKey(flowKey));
        }
        return data.flows.get(flowKey);
    }

    /** 获取当前规则应使用的持仓数量 */
    private int checkPoolCount(AdjustSharedData shared, Long poolId) {
        return shared.getBatchData() == null ? securityPoolAdjustMapper.queryPoolCurrentCount(poolId)
                : shared.getBatchData().counts.getOrDefault(poolId, 0);
    }

    /** 使用已加载的市场映射执行原主档市场校验 */
    private String resolveBatchMarket(BatchSecurityCheckData data, String securityCode) {
        List<SecurityMarketMappingDto> records = data.markets.getOrDefault(securityCode, Collections.emptyList());
        return SecurityMarketService.requireMarketCode(securityCode, records);
    }

    /**
     * 是否信用债大库树（主体评分档套件范围）。
     *
     * @param pool 投资池
     * @return true=信用债
     */
    boolean isGradedBondPool(InvestmentPoolBo pool) {
        return pool != null && CreditBondSpecialInboundRule.isGradedBondPoolType(pool.getPoolType());
    }

    /**
     * 当前已在分级库的最好档（inner_sort 最小）。
     *
     * @param currentPoolIds 当前在池 ID
     * @param poolMap        全量池索引
     * @return 最好档；未在分级库返回 null
     */
    Integer resolveCurrentGradedSort(Set<Long> currentPoolIds, Map<Long, InvestmentPoolBo> poolMap) {
        Integer best = null;
        if (currentPoolIds == null || poolMap == null) {
            return null;
        }
        for (Long id : currentPoolIds) {
            InvestmentPoolBo p = poolMap.get(id);
            if (!CreditBondSpecialInboundRule.isGradedLevelPool(p)) {
                continue;
            }
            if (best == null || p.getInnerSort() < best) {
                best = p.getInnerSort();
            }
        }
        return best;
    }

    /**
     * 标准矩阵允许池的最好档。
     *
     * @param allowedPoolIds 矩阵允许的信用债池 ID
     * @param poolMap        全量池索引
     * @return 最小 inner_sort；无法解析返回 null
     */
    Integer resolveBestAllowedSort(List<Long> allowedPoolIds, Map<Long, InvestmentPoolBo> poolMap) {
        Integer best = null;
        if (allowedPoolIds == null) {
            return null;
        }
        for (Long id : allowedPoolIds) {
            InvestmentPoolBo p = poolMap == null ? null : poolMap.get(id);
            if (p == null || p.getInnerSort() == null) {
                continue;
            }
            if (best == null || p.getInnerSort() < best) {
                best = p.getInnerSort();
            }
        }
        return best;
    }

    /**
     * 这一级信用债库能不能调入。
     * 只能调入一级库 / 下调一级：只留起始档那一级；至少下调一级 / 担保或观察池：起始档及更差；
     * 算不出起始档或普通债：只认矩阵点名的那些池 ID。
     *
     * @param pool           待判断叶子池
     * @param allowedPoolIds 矩阵允许的信用债池 ID
     * @param inboundMode    准入方式
     * @param startSort      档位规则的起始档（1～5）
     * @param poolMap        全量池索引（保留参数，便于调用方统一传入）
     * @return true=允许调入
     */
    boolean isLeafAllowedByGradeRule(InvestmentPoolBo pool, List<Long> allowedPoolIds,
                                             CreditBondSpecialInboundRule.GradedInboundMode inboundMode,
                                             Integer startSort,
                                             Map<Long, InvestmentPoolBo> poolMap) {
        if (inboundMode.usesSort() && startSort != null) {
            return inboundMode.allows(pool.getInnerSort(), startSort);
        }
        if (isCreditBondPool(pool)) {
            return allowedPoolIds != null && allowedPoolIds.contains(pool.getId());
        }
        return false;
    }

    /**
     * 股票类型特有调入校验
     *
     * 含股票退市校验（delist_date 早于今日则禁止调入）。
     */
    private List<String> checkStockIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 股票退市校验
        addIfFailed(failures, inCheckStockDelist(ctx));
        // TODO 股票分管人限制及 StockResearch/investrank 评级来源尚未迁移，当前仅保留评级入口
        addIfFailed(failures, inCheckGradeAstrict(ctx));
        return failures;
    }

    /**
     * 基金类型特有调入校验
     *
     * 含基金评分校验（fund_rate_limit）：池配置评分限制表达式时，校验请求传入的 fundRate 是否满足。
     * 对应老项目 checkInPool:1092-1120。
     */
    private List<String> checkFundIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // TODO 基金评分当前仅在 checkAdjust 校验，正式提交不携带 fundRate 且不再次校验
        addIfFailed(failures, inCheckFundRate(ctx));
        return failures;
    }

    /**
     * 规则：基金评分（fund_rate_limit）
     *
     * 池配置了基金评分限制表达式（如 3<=#rate<=8）时，调入校验请求传入的 fundRate 是否满足。
     * #rate 为基金评分占位。表达式支持 <=#rate / <#rate（下限）、#rate<= / #rate<（上限）及组合，
     * 各条件须同时满足。fundRate 为空或解析失败则校验失败。
     * 对应老项目 checkInPool:1092-1120。仅调入校验（老代码 checkOutPool 无）。
     */
    String inCheckFundRate(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getFundRateLimit() == null || pool.getFundRateLimit().trim().isEmpty()) {
            return null;
        }
        String rate = pool.getFundRateLimit().replaceAll(" ", "");
        String fundRateStr = ctx.getFundRate();
        // 未传基金评分或解析失败，校验失败
        double fundRate;
        if (fundRateStr == null || fundRateStr.trim().isEmpty()) {
            return pool.getPoolName() + "的评分，必须在" + rate.replace("#rate", "基金评分");
        }
        try {
            fundRate = Double.parseDouble(fundRateStr.trim());
        } catch (NumberFormatException e) {
            return pool.getPoolName() + "的评分，必须在" + rate.replace("#rate", "基金评分");
        }
        boolean ok = true;
        // 下限：<=#rate（下限 <= fundRate）
        if (rate.indexOf("<=#rate") > 0
                && !(parseRateBound(rate.split("<=#rate")[0]) <= fundRate)) {
            ok = false;
        }
        // 下限：<#rate（下限 < fundRate）
        if (rate.indexOf("<#rate") > 0
                && !(parseRateBound(rate.split("<#rate")[0]) < fundRate)) {
            ok = false;
        }
        // 上限：#rate<=（fundRate <= 上限）优先于 #rate<（fundRate < 上限）
        if (rate.indexOf("#rate<=") > 0) {
            String[] parts = rate.split("#rate<=");
            if (!(fundRate <= parseRateBound(parts[parts.length - 1]))) {
                ok = false;
            }
        } else if (rate.indexOf("#rate<") > 0) {
            String[] parts = rate.split("#rate<");
            if (!(fundRate < parseRateBound(parts[parts.length - 1]))) {
                ok = false;
            }
        }
        if (!ok) {
            return pool.getPoolName() + "的评分，必须在" + rate.replace("#rate", "基金评分");
        }
        return null;
    }

    /** 安全解析基金评分表达式边界值，解析失败返回 0.0（对齐老项目 NumberTool.safeToDouble 默认 0.0）。 */
    private double parseRateBound(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** 主体类型特有调入校验（主体不校验到期，暂无） */
    private List<String> checkCompanyIn(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    /**
     * 调出校验：通用校验 + 类型特有校验
     *
     * 先执行 checkCommonOut（通用：池锁定/pending/不在池/冻结期/限制/互斥/弹性），
     * 再按 categoryType 路由类型特有校验（债券到期 checkBondOut / 股票退市 checkStockOut 等）。
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    List<String> checkOutConditions(AdjustCheckContext ctx) {
        return checkOutConditions(ctx, null);
    }

    /** 复核互斥调出时，批量提交使用本次已读到的关联主体 */
    List<String> checkOutConditions(AdjustCheckContext ctx, List<RelatedRatingCompanyDto> candidates) {
        List<String> failures = new ArrayList<>();
        if (ctx.isExcelCompanyFollow()) {
            addIfFailed(failures, preCheckPendingProcess(ctx));
            addIfFailed(failures, outCheckRestrictPool(ctx));
            addIfFailed(failures, outCheckMutexPool(ctx));
            addIfFailed(failures, outCheckMutexConflict(ctx));
            addIfWarning(ctx.getWarnings(), outCheckElasticPool(ctx));
            return failures;
        }
        // 通用校验（所有类型都走）
        failures.addAll(checkCommonOut(ctx));
        // 类型特有校验（按 categoryType 路由）
        String categoryType = ctx.getCategoryType();
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            failures.addAll(checkBondOut(ctx));
        } else if (CategoryType.STOCK.getCode().equals(categoryType)) {
            failures.addAll(checkStockOut(ctx));
        } else if (CategoryType.FUND.getCode().equals(categoryType)) {
            failures.addAll(checkFundOut(ctx));
        } else if (CategoryType.COMPANY.getCode().equals(categoryType)) {
            failures.addAll(checkCompanyOut(ctx));
        }
        // 按发行主体复核黑名单质押库调出条件
        addPledgeBlacklistFailure(failures, ctx, AdjustMode.OUT.getCode(), candidates);
        return failures;
    }

    /**
     * 单券进入或退出黑名单质押库时，按发行主体复核三条件。
     *
     * @param failures   校验失败原因
     * @param ctx        调库校验上下文
     * @param adjustMode 调整方向
     */
    void addPledgeBlacklistFailure(List<String> failures, AdjustCheckContext ctx, String adjustMode) {
        addPledgeBlacklistFailure(failures, ctx, adjustMode, null);
    }

    /** 按调用入口提供的候选复核黑名单质押条件 */
    private void addPledgeBlacklistFailure(List<String> failures, AdjustCheckContext ctx, String adjustMode,
                                           List<RelatedRatingCompanyDto> candidates) {
        if (ctx == null || ctx.getTargetPool() == null
                || !PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(ctx.getTargetPool().getId())) {
            return;
        }
        SecurityInfoBo security = ctx.getSecurityInfo();
        String companyCode = security == null ? null : security.getIssuerCode();
        String failure;
        if (CreditBondSpecialInboundRule.isAbs(security)) {
            if (ctx.getBatchData() == null) {
                List<String> companies = candidates == null ? absIdentityService.queryRiskCompanyCodes(security)
                        : absIdentityService.queryRiskCompanyCodesWithCandidates(security, candidates);
                failure = pledgeBlacklistRuleService.validateCompanies(companies,
                        adjustMode, ctx.getRequestInPoolIds(), ctx.getRequestOutPoolIds());
            } else {
                // 根据已加载的各主体状态执行同一组合规则
                failure = pledgeBlacklistRuleService.validateCompanies(batchAbsRiskCompanies(security, ctx.getBatchData()),
                        adjustMode, code -> batchBlacklistDecision(code, ctx));
            }
        } else if (ctx.getBatchData() == null) {
            failure = pledgeBlacklistRuleService.validate(companyCode, adjustMode,
                    ctx.getRequestInPoolIds(), ctx.getRequestOutPoolIds());
        } else if (!StringUtils.hasText(companyCode)) {
            failure = "无法识别发行主体，不能调整黑名单质押库";
        } else {
            // 失败原因与单券复用同一判断
            failure = pledgeBlacklistRuleService.validateDecision(batchBlacklistDecision(companyCode, ctx), adjustMode);
        }
        // 汇总黑名单质押库校验失败原因
        addIfFailed(failures, failure);
    }

    /** 从本批索引读取名单与外评，并合并本次的预期池变动 */
    private PledgeBlacklistRuleService.Decision batchBlacklistDecision(String companyCode, AdjustCheckContext ctx) {
        BatchSecurityCheckData data = ctx.getBatchData();
        if (!data.ratingAgenciesConfigured) {
            throw new BizException("未配置有效外部评级机构");
        }
        Set<Long> current = data.blacklistCompanyPools.getOrDefault(companyCode, Collections.emptySet());
        Set<Long> inbound = ctx.getRequestInPoolIds();
        Set<Long> outbound = ctx.getRequestOutPoolIds();
        Long forbidden = PledgeBlacklistRuleService.FORBIDDEN_POOL_ID;
        Long keyWatch = PledgeBlacklistRuleService.KEY_WATCH_POOL_ID;
        return new PledgeBlacklistRuleService.Decision(
                inbound.contains(forbidden) || (current.contains(forbidden) && !outbound.contains(forbidden)),
                data.lowRatedCompanies.contains(companyCode),
                inbound.contains(keyWatch) || (current.contains(keyWatch) && !outbound.contains(keyWatch)));
    }

    /**
     * 通用调出校验（所有类型都走）
     *
     * 含池锁定、pending流程、不在池、冻结期、限制池、互斥、弹性禁投。
     * 不含证券到期（已拆到类型特有：债券到期 checkBondOut / 股票退市 checkStockOut）。
     */
    private List<String> checkCommonOut(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 前置检查：目标池是否已锁定（最硬拦截，优先执行）
        addIfFailed(failures, outCheckPoolLocked(ctx));
        // 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 出池检查：证券是否不在来源池中
        addIfFailed(failures, outCheckSecurityNotInPool(ctx));
        // 出池检查：是否在冻结期内（入池后N天不可调出，须在确认在池后校验）
        addIfFailed(failures, outCheckFrozenPeriod(ctx));
        // 出池检查：证券当前在调出限制池中（out_restrict）
        addIfFailed(failures, outCheckRestrictPool(ctx));
        // 出池检查：是否与互斥池冲突（证券当前在互斥池中）
        addIfFailed(failures, outCheckMutexPool(ctx));
        // 出池检查：本次请求中是否同时勾选了互斥池（不可同时调出）
        addIfFailed(failures, outCheckMutexConflict(ctx));
        // 出池检查：证券当前在弹性禁投池中（out_soft_restrict，警告不阻断）
        addIfWarning(ctx.getWarnings(), outCheckElasticPool(ctx));
        // 开放日校验（按池 open_day_adjust，调出）
        addIfFailed(failures, outCheckOpenDay(ctx));
        return failures;
    }

    /**
     * 债券类型特有调出校验
     *
     * 含债券到期校验（maturity_date 早于今日则禁止调出）。
     */
    private List<String> checkBondOut(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 债券到期校验
        addIfFailed(failures, outCheckBondMaturity(ctx));
        return failures;
    }

    /**
     * 股票类型特有调出校验
     *
     * 含股票退市校验（delist_date 早于今日则禁止调出）。
     */
    private List<String> checkStockOut(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 股票退市校验
        addIfFailed(failures, outCheckStockDelist(ctx));
        return failures;
    }

    /** 基金类型特有调出校验（暂无，后续 P2 加基金评分） */
    private List<String> checkFundOut(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    /** 主体类型特有调出校验（主体不校验到期，暂无） */
    private List<String> checkCompanyOut(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    /**
     * 规则：债券到期（maturity_date）
     *
     * 债券到期日早于今日则禁止调入。对应老项目 checkInPool:544（ptype=4000 债券分支）。
     */
    private String inCheckBondMaturity(AdjustCheckContext ctx) {
        String maturityDate = ctx.getSecurityInfo().getMaturityDate();
        if (maturityDate != null && !maturityDate.isEmpty()) {
            String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
            if (maturityDate.compareTo(today) < 0) {
                return "债券已到期";
            }
        }
        return null;
    }

    /**
     * 规则：股票退市（delist_date）
     *
     * 股票摘牌日早于今日则禁止调入。对应老项目 checkInPool:956（ptype=2000 股票分支）。
     */
    private String inCheckStockDelist(AdjustCheckContext ctx) {
        String delistDate = ctx.getSecurityInfo().getDelistDate();
        if (delistDate != null && !delistDate.isEmpty()) {
            String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
            if (delistDate.compareTo(today) < 0) {
                return "股票已退市";
            }
        }
        return null;
    }

    /**
     * 规则：债券到期（maturity_date，调出）
     *
     * 债券到期日早于今日则禁止调出。对应老项目 checkOutPool 到期校验。
     */
    private String outCheckBondMaturity(AdjustCheckContext ctx) {
        String maturityDate = ctx.getSecurityInfo().getMaturityDate();
        if (maturityDate != null && !maturityDate.isEmpty()) {
            String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
            if (maturityDate.compareTo(today) < 0) {
                return "债券已到期";
            }
        }
        return null;
    }

    /**
     * 规则：股票退市（delist_date，调出）
     *
     * 股票摘牌日早于今日则禁止调出。对应老项目 checkOutPool 退市校验。
     */
    private String outCheckStockDelist(AdjustCheckContext ctx) {
        String delistDate = ctx.getSecurityInfo().getDelistDate();
        if (delistDate != null && !delistDate.isEmpty()) {
            String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
            if (delistDate.compareTo(today) < 0) {
                return "股票已退市";
            }
        }
        return null;
    }

    /**
     * 规则：证券是否存在进行中的调库流程
     *
     * 存在 pending 流程步骤的调库记录视为进行中，
     * 需等待该流程终结后方可再次发起调库，避免并发操作导致入池状态混乱。
     */
    private String preCheckPendingProcess(AdjustCheckContext ctx) {
        if (ctx.isHasPendingProcess()) {
            String nodeLabel = ctx.getPendingProcessNodeLabel();
            if (nodeLabel != null && !nodeLabel.trim().isEmpty()) {
                return PENDING_PROCESS_FAILURE + "（当前节点：" + nodeLabel.trim() + "）";
            }
            return PENDING_PROCESS_FAILURE;
        }
        return null;
    }

    /**
     * 规则：证券是否已在目标池中
     *
     * 以 ip_pool_status 中 audit_status='20' 的有效记录为准，重复调入无实际意义。
     */
    String inCheckSecurityAlreadyInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId != null && ctx.getCurrentPoolIds().contains(poolId)) {
            return ALREADY_IN_POOL_FAILURE;
        }
        return null;
    }

    /**
     * 规则：目标池是否已达持仓上限
     *
     * maxCapacity 为 null 或 0 时表示不限制数量；
     * 当前有效证券数量（poolCurrentCount）≥ 上限时拒绝调入。
     */
    private String inCheckPoolCapacity(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool != null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                && ctx.getPoolCurrentCount() >= pool.getMaxCapacity()) {
            return "目标投资池已达持仓上限（" + pool.getMaxCapacity() + "）";
        }
        return null;
    }

    /**
     * 规则：来源池限制
     *
     * 若目标池配置了来源池（source），则证券必须当前已在其中至少一个来源池中，
     * 或本次请求同时调入至少一个来源池，满足"从低级库向高级库晋升"等分层调库规则。
     * 未配置来源池时不做限制。
     */
    String inCheckSourcePool(AdjustCheckContext ctx) {
        List<Long> sourcePools = ctx.getTargetPoolRelations().get(RelationType.SOURCE.getCode());
        if (sourcePools == null || sourcePools.isEmpty()) {
            return null;
        }
        boolean inCurrentPool = ctx.getCurrentPoolIds() != null
                && sourcePools.stream().anyMatch(ctx.getCurrentPoolIds()::contains);
        boolean inRequestPool = ctx.getRequestInPoolIds() != null
                && sourcePools.stream().anyMatch(ctx.getRequestInPoolIds()::contains);
        boolean inAny = inCurrentPool || inRequestPool;
        if (!inAny) {
            // 构建关联池路径名称
            return "目标池配置了来源池限制，证券须先在以下池中：" + poolNames(sourcePools, ctx);
        }
        return null;
    }

    /**
     * 规则：调入限制池（in_restrict）
     *
     * 证券当前在配置的限制池中，则不允许调入目标池；开关开启时，非 ABS、担保人内评为 1 档的强担保债仅豁免重点观察名单池。
     */
    String inCheckRestrictPool(AdjustCheckContext ctx) {
        List<Long> relPools = ctx.getTargetPoolRelations().get(RelationType.IN_RESTRICT.getCode());
        if (relPools == null || relPools.isEmpty()) {
            return null;
        }
        // 开关开启时，强担保只豁免重点观察名单的池间限制，其他调入限制池仍然拦截
        List<Long> blocked = relPools.stream()
                .filter(ctx.getCurrentPoolIds()::contains)
                .filter(poolId -> !isStrongGuaranteeRestrictedPool(ctx, poolId))
                .collect(Collectors.toList());
        if (!blocked.isEmpty()) {
            return "证券当前在调入限制池中：" + poolNames(blocked, ctx);
        }
        return null;
    }

    /**
     * 判断当前限制池是否为强担保可豁免的重点观察名单池
     *
     * @param ctx 调库校验上下文
     * @param poolId 当前限制池 ID
     * @return true=强担保可豁免的重点观察名单池
     */
    private boolean isStrongGuaranteeRestrictedPool(AdjustCheckContext ctx, Long poolId) {
        if (!CreditBondSpecialInboundRule.isStrongGuarantee(
                ctx.getSecurityInfo(), BusinessSwitches.STRONG_GUARANTEE_ENABLED)
                || ctx.getPoolMap() == null) {
            return false;
        }
        InvestmentPoolBo pool = ctx.getPoolMap().get(poolId);
        return pool != null && PoolType.RESTRICTED.getCode().equals(pool.getPoolType());
    }

    /**
     * 规则：调入弹性禁投池（in_soft_restrict）
     *
     * 弹性禁投池与硬性限制池的校验逻辑相同，但语义上表示"柔性"限制，
     * 业务层面可由特定角色绕过（绕过逻辑在审批流程中处理，此处只做初步拦截）。
     */
    private String inCheckElasticPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.IN_SOFT_RESTRICT.getCode(), "调入弹性禁投池");
    }

    /**
     * 规则：同一请求中同时勾选了互斥调入项
     *
     * 若目标池配置了互斥池（in_mutex），且本次请求中同时存在对这些互斥池的调入操作，
     * 则两者不可并存，均应失败。例如：信用债大库/一级库与专户产品/一级库互斥，
     * 不可同时勾选"调入信用债大库/一级库"和"调入专户产品/一级库"。
     */
    private String inCheckMutexConflict(AdjustCheckContext ctx) {
        List<Long> inMutex = ctx.getTargetPoolRelations().get(RelationType.IN_MUTEX.getCode());
        if (inMutex == null || inMutex.isEmpty()) {
            return null;
        }
        List<Long> conflicting = inMutex.stream()
                .filter(id -> ctx.getRequestInPoolIds().contains(id))
                .collect(Collectors.toList());
        if (!conflicting.isEmpty()) {
            // 构建关联池路径名称
            return "与以下互斥池不可同时调入：" + poolNames(conflicting, ctx);
        }
        return null;
    }

    /**
     * 规则：证券是否不在目标池中
     *
     * 不在池中则无法调出，属于基础前置判断，避免操作无效数据。
     */
    String outCheckSecurityNotInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId == null || !ctx.getCurrentPoolIds().contains(poolId)) {
            return "证券当前不在目标投资池中";
        }
        return null;
    }

    /**
     * 规则：调出限制池（out_restrict）
     *
     * 证券当前在配置的限制池中，则不允许从目标池调出。
     */
    String outCheckRestrictPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.OUT_RESTRICT.getCode(), "调出限制池");
    }

    /**
     * 规则：调出互斥池（out_mutex）
     *
     * 证券当前在配置的互斥池中，则不允许从目标池调出。
     * 注意：此处校验的是"调出方向的互斥"，与调入互斥池（in_mutex）含义不同：
     * in_mutex 用于在第三阶段自动生成配套调出项；out_mutex 用于阻止调出操作本身。
     */
    private String outCheckMutexPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.OUT_MUTEX.getCode(), "调出互斥池");
    }

    /**
     * 规则：调出弹性禁投池（out_soft_restrict）
     *
     * 证券当前在配置的弹性禁投池中，则不允许从目标池调出。
     */
    private String outCheckElasticPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.OUT_SOFT_RESTRICT.getCode(), "调出弹性禁投池");
    }

    /**
     * 规则：同一请求中同时勾选了互斥调出项
     *
     * 若目标池配置了互斥池（in_mutex），且本次请求中同时存在对这些互斥池的调出操作，
     * 则两者不可并存，均应失败。互斥池对代表"不可同时持有"，
     * 因此也不应在同一批次中同时对两池执行调出操作。
     */
    private String outCheckMutexConflict(AdjustCheckContext ctx) {
        List<Long> inMutex = ctx.getTargetPoolRelations().get(RelationType.IN_MUTEX.getCode());
        if (inMutex == null || inMutex.isEmpty()) {
            return null;
        }
        List<Long> conflicting = inMutex.stream()
                .filter(id -> ctx.getRequestOutPoolIds().contains(id))
                .collect(Collectors.toList());
        if (!conflicting.isEmpty()) {
            // 构建关联池路径名称
            return "与以下互斥池不可同时调出：" + poolNames(conflicting, ctx);
        }
        return null;
    }

    /**
     * 规则：目标池已锁定（lock_flag=1）
     *
     * 池被锁定后不可调入，属于最硬的池级拦截，优先于其他调入校验执行。
     */
    String inCheckPoolLocked(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool != null && pool.getLockFlag() != null && pool.getLockFlag() == 1) {
            return "目标投资池已锁定";
        }
        return null;
    }

    /**
     * 规则：目标池已锁定（lock_flag=1）
     *
     * 池被锁定后不可调出，属于最硬的池级拦截，优先于其他调出校验执行。
     */
    String outCheckPoolLocked(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool != null && pool.getLockFlag() != null && pool.getLockFlag() == 1) {
            return "目标投资池已锁定";
        }
        return null;
    }

    /**
     * 规则：调出冻结期（frozen_period_in）
     *
     * 目标池配置了调入冻结期天数时，证券入池后 N 天内不可调出。
     * 以 ip_pool_status.entry_time（audit_status=20）为入池时间基准，
     * entry_time + frozenPeriodIn 天 > 当前时间则视为仍在冻结期。
     */
    String outCheckFrozenPeriod(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getFrozenPeriodIn() == null || pool.getFrozenPeriodIn() <= 0) {
            return null;
        }
        // 取证券在目标池的入池时间
        Date entryTime = ctx.getTargetPoolEntryTime();
        if (entryTime == null) {
            return "证券入池生效时间缺失";
        }
        // 计算冻结期截止时间 = 入池时间 + N 天
        long frozenMs = pool.getFrozenPeriodIn() * 24L * 60L * 60L * 1000L;
        Date frozenDeadline = new Date(entryTime.getTime() + frozenMs);
        if (new Date().before(frozenDeadline)) {
            return "证券仍在目标投资池冻结期内";
        }
        return null;
    }

    /**
     * 规则：投资品种（variety_codes）
     *
     * 目标池配置了投资品种时，证券品种（categoryType）须在配置内。
     * categoryType 由 dict_security_type 表按 securityType 查询得到（大类见 CategoryType / 演示数据）。
     */
    String inCheckVariety(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getVarietyCodes() == null || pool.getVarietyCodes().isEmpty()) {
            return null;
        }
        // 查证券品种大类
        String categoryType = ctx.getBatchData() == null
                ? securityPoolAdjustMapper.queryCategoryTypeBySecurityType(ctx.getSecurityInfo().getSecurityType())
                : ctx.getCategoryType();
        // 池配置品种为 JSON 数组（如 ["bond"]），判断是否包含证券品种
        if (categoryType == null || !pool.getVarietyCodes().contains("\"" + categoryType + "\"")) {
            return "证券不在本池投资品种范围内";
        }
        return null;
    }

    /**
     * 规则：投资市场（market_codes）
     *
     * 目标池配置了投资市场时，当前证券代码所属市场须在配置内。
     * 市场 code 统一见 {@link com.znty.rrs.common.enums.MarketCode}，
     * 市场由统一 SecurityMarketService 权威查询。
     */
    private String inCheckMarket(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getMarketCodes() == null || pool.getMarketCodes().isEmpty()) {
            return null;
        }
        // 仅比较当前证券代码所属市场
        boolean matches = ctx.getBatchData() == null
                ? securityMarketService.matchesPoolMarkets(pool.getMarketCodes(), ctx.getSecurityInfo())
                : MarketCodeMatchUtil.matchesPoolMarkets(pool.getMarketCodes(),
                        "company".equals(ctx.getSecurityInfo().getSecurityType()) ? MarketCode.COMPANY.getCode()
                                : resolveBatchMarket(ctx.getBatchData(), ctx.getSecurityInfo().getWindCode()));
        if (!matches) {
            return "证券不在本池投资市场范围内";
        }
        return null;
    }

    /**
     * 规则：全局禁止池（forbidden 禁投池）
     *
     * 证券当前在禁投池中则不能调入任何其他池（全局禁止，区别于池间 in_restrict）。
     * 对应老项目 Forbiddenlastpoolid 配置。
     */
    String inCheckForbiddenPool(AdjustCheckContext ctx) {
        String code = ctx.getSecurityInfo().getWindCode();
        boolean forbidden = ctx.getBatchData() == null ? securityPoolAdjustMapper.querySecurityInForbiddenPool(code)
                : isInBatchPoolType(ctx.getBatchData(), code, "forbidden");
        if (forbidden) {
            return "证券当前在禁止池中";
        }
        return null;
    }

    /**
     * 规则：股票入池评级限制（grade_astrict）
     *
     * 老系统该字段对应“股票入池评级限制”，评级来源为 StockResearch/investrank
     * （买入、增持、中性、卖出等），不是债券 ratingBond。当前项目尚未接入股票研究评级来源，
     * 因此仅保留股票分支入口，配置了 grade_astrict 时先跳过，不对债券评级做误拦截。
     */
    private String inCheckGradeAstrict(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getGradeAstrict() == null || pool.getGradeAstrict().trim().isEmpty()) {
            return null;
        }
        // 后续接入股票研究评级来源后，按 pool.gradeAstrict 校验买入/增持/中性/卖出等评级 code。
        return null;
    }

    /**
     * 规则：行业限制（industry_code / industry_exponent）
     *
     * 目标池配置了行业限制时，调入校验证券行业是否匹配：
     * 池 industry_code 非空且 industry_exponent=0（非行业指数模式）时，证券 industry_name 须等于池配置值。
     * 对应老项目 checkInPool:485（industryPartition.industrycode 比对 pool.IndustryCode），
     * TODO 投资池当前配置的是农业、城投、电力等业务行业值，尚未完成与证券行业数据的可靠映射；
     * 当前暂用 SecurityInfoBo.industryName 名称精确匹配，不作为最终行业校验口径（老项目为编码前缀层级匹配）。
     * 仅调入校验（老代码 checkOutPool 无）。
     */
    String inCheckIndustry(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getIndustryCode() == null || pool.getIndustryCode().isEmpty()) {
            return null;
        }
        // 行业指数模式（industry_exponent != 0）跳过行业校验
        Integer exponent = pool.getIndustryExponent();
        if (exponent != null && exponent != 0) {
            return null;
        }
        String securityIndustry = ctx.getSecurityInfo().getIndustryName();
        if (securityIndustry == null || securityIndustry.isEmpty()) {
            // 证券无行业信息，跳过（老逻辑同）
            return null;
        }
        if (!securityIndustry.equals(pool.getIndustryCode())) {
            return "证券行业与目标池行业配置不一致";
        }
        return null;
    }

    /**
     * 规则：开放日（open_day_adjust，调入侧）
     *
     * 目标池启用开放日校验（open_day_adjust=1）时，调入校验当日是否落在 ip_pool_open_day 的开放区间内。
     * 对应老项目 checkInPool:479（isInPoolOpenDay）。
     */
    String inCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        String today = LocalDate.now().toString();
        boolean open = ctx.getBatchData() == null ? securityPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)
                : ctx.getBatchData().openPools.contains(pool.getId());
        if (!open) {
            return "当前不在本池开放日内";
        }
        return null;
    }

    /**
     * 规则：开放日（open_day_adjust，调出侧）
     *
     * 目标池启用开放日校验时，调出校验当日是否落在开放区间内。对应老项目 checkOutPool:139。
     */
    private String outCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        String today = LocalDate.now().toString();
        boolean open = ctx.getBatchData() == null ? securityPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)
                : ctx.getBatchData().openPools.contains(pool.getId());
        if (!open) {
            return "当前不在本池开放日内";
        }
        return null;
    }

    /**
     * 构建单个调库项的校验上下文（{@link AdjustCheckContext}）
     *
     * 负责将"本次校验共享数据"与"当次调库项的目标池信息"合并填充到上下文中，
     * 供三层校验方法直接读取，无需再逐级传参。
     *
     * @param item             当前调库项（目标池 ID、调整方向等）
     * @param poolCurrentCount 目标池当前有效证券数量（调用方按需查询）
     * @param shared           本次 checkAdjust 调用的共享数据
     */
    AdjustCheckContext buildCheckContext(AdjustCheckReq.CheckItem item, int poolCurrentCount, AdjustSharedData shared) {
        // 从全量投资池 Map 中取出目标池及其父级池（用于构建错误消息中的池路径）
        InvestmentPoolBo targetPool = shared.getPoolMap().get(item.getTargetPoolId());
        InvestmentPoolBo parentPool = (targetPool != null && targetPool.getParentId() != null)
                ? shared.getPoolMap().get(targetPool.getParentId()) : null;

        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setBatchData(shared.getBatchData());
        ctx.setExcelCompanyFollow(shared.isExcelCompanyFollow());
        ctx.setSecurityInfo(shared.getSecurityInfo());
        ctx.setTargetPool(targetPool);
        ctx.setParentPool(parentPool);
        ctx.setCurrentPoolIds(shared.getCurrentPoolIds());
        ctx.setPoolCurrentCount(poolCurrentCount);
        ctx.setAdjustMode(item.getAdjustMode());
        // 取出目标池自身的所有关系配置（来源池、限制池、联动池等），供关系型校验规则直接使用
        ctx.setTargetPoolRelations(shared.getPoolRelationMap().getOrDefault(item.getTargetPoolId(), Collections.emptyMap()));
        ctx.setPoolMap(shared.getPoolMap());
        ctx.setHasPendingProcess(shared.getPendingPoolGroupIds() != null
                && shared.getPendingPoolGroupIds().contains(resolveRootPoolId(item.getTargetPoolId(), shared.getPoolMap())));
        ctx.setPendingProcessNodeLabel(shared.getPendingProcessNodeLabel());
        ctx.setSecurityInObservePool(shared.isSecurityInObservePool());
        ctx.setIssuerInObservePool(shared.isIssuerInObservePool());
        ctx.setSecurityInRestrictedPool(shared.isSecurityInRestrictedPool());
        ctx.setIssuerInRestrictedPool(shared.isIssuerInRestrictedPool());
        ctx.setRequestInPoolIds(shared.getRequestInPoolIds());
        ctx.setRequestOutPoolIds(shared.getRequestOutPoolIds());
        // 调出时查询证券在目标池的入池时间，用于冻结期校验（调入时不需要）
        if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
            ctx.setTargetPoolEntryTime(shared.getBatchData() == null ? securityPoolAdjustMapper.queryPoolEntryTime(
                    shared.getSecurityInfo().getWindCode(), item.getTargetPoolId())
                    : shared.getBatchData().entryTimes.getOrDefault(shared.getSecurityInfo().getWindCode(),
                            Collections.emptyMap()).get(item.getTargetPoolId()));
        }
        // 查证券品种大类，用于类型特有校验路由（dict_security_type.category_type）
        ctx.setCategoryType(shared.getBatchData() == null ? securityPoolAdjustMapper.queryCategoryTypeBySecurityType(
                shared.getSecurityInfo().getSecurityType()) : shared.getBatchData().categories
                .get(shared.getSecurityInfo().getSecurityType()));
        // 基金评分（基金证券调入校验用，透传请求级 fundRate）
        ctx.setFundRate(shared.getFundRate());
        // 是否放开规则（透传 shared，主体债入库矩阵校验用）
        ctx.setReleaseRules(shared.isReleaseRules());
        return ctx;
    }

    /**
     * 构建自动生成的联动/互斥调整项校验结果
     *
     * 与手动项校验流程完全相同，区别在于：
     *
     *   - targetPoolId 来自池关系配置，由系统自动推导，非用户选择
     *   - itemTag 标记为ItemType.LINKAGE.getCode()或ItemType.MUTEX.getCode()，前端据此区分显示样式
     *
     *
     * @param targetPoolId 自动生成项的目标池 ID
     * @param adjustMode   调整方向（AdjustMode.IN.getCode()或AdjustMode.OUT.getCode()）
     * @param itemTag      来源标签：linkage（联动）/ mutex（互斥）
     * @param adjustGroupKey 触发该自动项的手工调库分组 Key
     * @param shared       本次 checkAdjust 调用的共享数据
     */
    private AdjustCheckDto.CheckResultItem buildAutoResultItem(
            Long targetPoolId, String adjustMode, String itemTag, String adjustGroupKey, AdjustSharedData shared) {

        int poolCurrentCount = checkPoolCount(shared, targetPoolId);
        InvestmentPoolBo pool = shared.getPoolMap().get(targetPoolId);

        // 构造虚拟 CheckItem，使其可以复用 buildCheckContext 逻辑
        AdjustCheckReq.CheckItem fakeItem = new AdjustCheckReq.CheckItem();
        fakeItem.setTargetPoolId(targetPoolId);
        fakeItem.setAdjustMode(adjustMode);
        fakeItem.setPoolType(pool != null ? pool.getPoolType() : null);

        // 构建调库校验上下文
        AdjustCheckContext ctx = buildCheckContext(fakeItem, poolCurrentCount, shared);

        List<String> failures = AdjustMode.IN.getCode().equals(adjustMode) ? checkInConditions(ctx) : checkOutConditions(ctx);

        AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
        resultItem.setTargetPoolId(targetPoolId);
        // 构建投资池全路径名称
        resultItem.setPoolName(buildPoolPath(targetPoolId, shared.getPoolMap()));
        resultItem.setPoolType(pool != null ? pool.getPoolType() : null);
        resultItem.setAdjustMode(adjustMode);
        resultItem.setItemTag(itemTag);
        resultItem.setAdjustGroupKey(adjustGroupKey);
        resultItem.setCanAdjust(failures.isEmpty());
        resultItem.setFailReasons(failures);
        return resultItem;
    }

    /**
     * 手工调库项失败时，将失败状态传递给同组自动生成项。
     */
    private void inheritManualItemFailure(
            AdjustCheckDto.CheckResultItem autoItem, AdjustCheckDto.CheckResultItem manualItem) {
        if (manualItem.isCanAdjust()) {
            return;
        }
        List<String> failures = new ArrayList<>();
        failures.add("关联手工" + manualItem.getAdjustMode() + "项“" + manualItem.getPoolName()
                + "”校验未通过");
        failures.addAll(autoItem.getFailReasons());
        autoItem.setCanAdjust(false);
        autoItem.setFailReasons(failures);
    }

    /**
     * 互斥配套调出失败时，反阻断同组手工调入，避免只入目标池导致双池。
     *
     * 仅处理 itemTag=mutex 的配套调出；联动失败不反绑手工项。
     */
    private void propagateMutexFailureToManual(AdjustCheckDto.CheckResultItem manualItem,
                                               List<AdjustCheckDto.CheckResultItem> groupMutexItems) {
        if (manualItem == null || !manualItem.isCanAdjust()
                || groupMutexItems == null || groupMutexItems.isEmpty()) {
            return;
        }
        // 互斥子原因拆成独立 failReasons，避免「；」嵌套成一句矛盾长文
        List<String> mutexFailures = new ArrayList<>();
        for (AdjustCheckDto.CheckResultItem mutexItem : groupMutexItems) {
            if (mutexItem == null || mutexItem.isCanAdjust()) {
                continue;
            }
            String poolName = mutexItem.getPoolName() != null
                    ? mutexItem.getPoolName()
                    : String.valueOf(mutexItem.getTargetPoolId());
            mutexFailures.add("配套互斥调出未通过（" + poolName + "）");
            if (mutexItem.getFailReasons() != null) {
                for (String reason : mutexItem.getFailReasons()) {
                    if (reason != null && !reason.isEmpty()) {
                        mutexFailures.add(reason);
                    }
                }
            }
        }
        if (mutexFailures.isEmpty()) {
            return;
        }
        List<String> failures = new ArrayList<>(mutexFailures);
        if (manualItem.getFailReasons() != null) {
            failures.addAll(manualItem.getFailReasons());
        }
        manualItem.setCanAdjust(false);
        manualItem.setFailReasons(failures);
    }

    /**
     * 构建手工调库项分组 Key。
     */
    private String buildAdjustGroupKey(AdjustCheckReq.CheckItem item) {
        return "manual_" + item.getTargetPoolId() + "_" + item.getAdjustMode();
    }

    /**
     * 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * @param relationType 关系类型常量（RelationType.IN_RESTRICT.getCode() / RelationType.OUT_RESTRICT.getCode() 等）
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
            // 构建关联池路径名称
            return "证券当前在" + label + "中：" + poolNames(blocked, ctx);
        }
        return null;
    }

    /**
     * 将池 ID 列表转为"父级/子级"路径名称，以顿号连接，用于构建可读的失败原因消息
     *
     * @param poolIds 需要转换的池 ID 列表
     * @param ctx     上下文（内含全量投资池 Map）
     */
    private String poolNames(List<Long> poolIds, AdjustCheckContext ctx) {
        return poolIds.stream()
                // 构建投资池全路径名称
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

    /** 单条校验返回的警告原因非空时加入警告列表（不阻断调库，如弹性禁投池）。 */
    private void addIfWarning(List<String> warnings, String reason) {
        if (reason != null) {
            warnings.add(reason);
        }
    }

    /**
     * 判断当前审批节点是否应由流程发起人自动完成。
     */
    boolean isInitiatorStep(FlowSnapshot snapshot, FlowNodeBo node, NodeApprovalConfigBo config,
                                    FlowNodeBo prevNode, FlowNodeBo startNode) {
        if (node == null || !NodeType.APPROVAL.getCode().equals(node.getNodeType())) {
            return false;
        }
        // 发起节点通过 approval_strategy=initiator + 出边 route_action=submit 标记
        return config != null
                && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())
                && hasOutgoingRouteAction(snapshot, node, ProcessAction.SUBMIT.getCode());
    }

    /**
     * 判断节点是否存在指定流转动作出边。
     */
    private boolean hasOutgoingRouteAction(FlowSnapshot snapshot, FlowNodeBo node, String routeAction) {
        if (snapshot == null || node == null || routeAction == null) {
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
     * 从 Wind 同 ISIN 记录解析关联证券代码列表（已带后缀、去自身、去重，不递归）。
     */
    List<String> resolveRelatedSecurityCodes(SecurityInfoBo main) {
        List<String> codes = new ArrayList<>();
        if (main == null) {
            return codes;
        }
        for (SecurityMarketDto related : securityMarketService.queryRelatedMarketCodes(main.getWindCode())) {
            codes.add(related.getWindCode());
        }
        return codes;
    }

    /**
     * 按关联证券代码加载校验共享数据（复用主请求的池关系/请求池集合）。
     */
    private AdjustSharedData loadSharedDataForSecurity(String securityCode, SecurityInfoBo securityInfo,
                                                       AdjustSharedData mainShared, AdjustCheckReq mainReq) {
        // 多市场代码属于同一证券，沿用主券已完成防伪校验的最终评级主体，字段原样拷贝
        if (securityInfo != null && mainShared.getSecurityInfo() != null) {
            SecurityInfoBo mainSecurity = mainShared.getSecurityInfo();
            securityInfo.setIssuer(mainSecurity.getIssuer());
            securityInfo.setIssuerCode(mainSecurity.getIssuerCode());
            securityInfo.setInnerIssuerRating(mainSecurity.getInnerIssuerRating());
            securityInfo.setRatingBondissuer(mainSecurity.getRatingBondissuer());
            securityInfo.setRatingBondissuerAgency(mainSecurity.getRatingBondissuerAgency());
            securityInfo.setGuarantor(mainSecurity.getGuarantor());
            securityInfo.setGuarantorId(mainSecurity.getGuarantorId());
            securityInfo.setInnerGuarantorRating(mainSecurity.getInnerGuarantorRating());
        }
        if (mainShared.getBatchData() != null) {
            // 关联市场沿用已选评级主体，其他动态状态按实际证券代码读取
            AdjustSharedData shared = buildBatchSecurityFacts(securityInfo, mainShared.getBatchData(),
                    mainReq.getGuarantorCode());
            shared.setRequestInPoolIds(mainShared.getRequestInPoolIds());
            shared.setRequestOutPoolIds(mainShared.getRequestOutPoolIds());
            shared.setFundRate(mainReq.getFundRate());
            shared.setReleaseRules(mainShared.isReleaseRules());
            return shared;
        }
        AdjustSharedData shared = new AdjustSharedData();
        shared.setSecurityInfo(securityInfo);
        shared.setPoolMap(mainShared.getPoolMap());
        shared.setCurrentPoolIds(new HashSet<>(
                securityPoolAdjustMapper.querySecurityCurrentPoolIdList(securityCode)));
        shared.setPoolRelationMap(mainShared.getPoolRelationMap());
        shared.setHasPendingProcess(securityPoolAdjustMapper.querySecurityHasPendingProcess(securityCode));
        shared.setPendingProcessNodeLabel(
                securityPoolAdjustMapper.querySecurityPendingProcessNodeLabel(securityCode));
        shared.setPendingPoolGroupIds(resolvePoolGroupIds(
                securityPoolAdjustMapper.queryPendingManualTargetPoolIdList(securityCode, null),
                mainShared.getPoolMap()));
        // 观察池：证券或主体已在观察池则不得高于矩阵最好档；重点观察名单：禁新增，已在库只能去五级
        shared.setSecurityInObservePool(securityPoolAdjustMapper.querySecurityInObservePool(securityCode));
        shared.setIssuerInObservePool(queryIssuerInPoolType(securityInfo, "observe"));
        shared.setSecurityInRestrictedPool(securityPoolAdjustMapper.querySecurityInRestrictedPool(securityCode));
        shared.setIssuerInRestrictedPool(queryIssuerInPoolType(securityInfo, "restricted"));
        // 评级下调标志按关联券自身主体信息判定
        shared.setIssuerRatingDowngraded(ratingDowngradeChecker.isIssuerDowngraded(securityInfo));
        shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(securityInfo));
        shared.setGuarantorRatingDowngraded(
                ratingDowngradeChecker.isGuarantorDowngraded(mainReq.getGuarantorCode()));
        shared.setRequestInPoolIds(mainShared.getRequestInPoolIds());
        shared.setRequestOutPoolIds(mainShared.getRequestOutPoolIds());
        shared.setFundRate(mainReq.getFundRate());
        // 同券各市场沿用本次申请的矩阵放开选择，其他规则仍逐券校验
        shared.setReleaseRules(mainShared.isReleaseRules());
        return shared;
    }

    /**
     * 多市场关联码扩批：对主结果中的 manual/linkage/mutex 项，按 Wind 同 ISIN 记录展开 related 项。
     *
     * 关联码独立校验已在池/不在池/pending/容量等；同池同批调入累计 projected 占用容量。
     */
    void expandRelatedMarketCodeItems(AdjustCheckReq req, AdjustSharedData mainShared,
                                              List<AdjustCheckDto.CheckResultItem> resultItems) {
        SecurityInfoBo mainSec = mainShared.getSecurityInfo();
        if (mainSec == null || resultItems == null || resultItems.isEmpty()) {
            return;
        }
        // 回填主券证券信息到已有结果项
        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            if (item.getSecurityCode() == null || item.getSecurityCode().isEmpty()) {
                item.setSecurityCode(mainSec.getWindCode());
                item.setSecurityShortName(mainSec.getShortName());
                item.setSecurityType(mainSec.getSecurityType());
                item.setSourceSecurityCode(mainSec.getWindCode());
            }
        }

        List<String> relatedCodes;
        if (mainShared.getBatchData() == null) {
            // 单券按现有入口查询同券关系
            relatedCodes = resolveRelatedSecurityCodes(mainSec);
        } else {
            // 保留主码市场有效性检查
            resolveBatchMarket(mainShared.getBatchData(), mainSec.getWindCode());
            relatedCodes = mainShared.getBatchData().relatedCodes.getOrDefault(mainSec.getWindCode(),
                    Collections.emptyList());
            for (String code : relatedCodes) {
                // 保留原关联市场完整性校验
                resolveBatchMarket(mainShared.getBatchData(), code);
            }
        }
        if (relatedCodes.isEmpty()) {
            return;
        }

        // 仅对主券母项扩批（manual/linkage/mutex）
        List<AdjustCheckDto.CheckResultItem> motherItems = new ArrayList<>();
        Map<String, AdjustCheckDto.CheckResultItem> manualByGroup = new HashMap<>();
        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            String tag = item.getItemTag();
            if (ItemType.MANUAL.getCode().equals(tag)
                    || ItemType.LINKAGE.getCode().equals(tag)
                    || ItemType.MUTEX.getCode().equals(tag)) {
                motherItems.add(item);
            }
            if (ItemType.MANUAL.getCode().equals(tag)) {
                manualByGroup.put(item.getAdjustGroupKey(), item);
            }
        }
        if (motherItems.isEmpty()) {
            return;
        }

        // 已通过的调入项占用 projected 容量，避免主+关联同池全部通过却超容
        Map<Long, Integer> projectedInbound = new HashMap<>();
        Set<String> coveredKeys = new HashSet<>();
        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            if (item.getSecurityCode() != null) {
                coveredKeys.add(item.getSecurityCode() + "_" + item.getTargetPoolId() + "_" + item.getAdjustMode());
            }
            if (item.isCanAdjust() && AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                projectedInbound.merge(item.getTargetPoolId(), 1, Integer::sum);
            }
        }

        List<AdjustCheckDto.CheckResultItem> relatedResults = new ArrayList<>();
        for (String relatedCode : relatedCodes) {
            SecurityInfoBo relatedSec;
            if (mainShared.getBatchData() == null) {
                relatedSec = securityPoolAdjustMapper.querySecurityBoByCode(relatedCode);
            } else {
                SecurityInfoBo master = mainShared.getBatchData().securities.get(relatedCode);
                relatedSec = master == null ? null : new SecurityInfoBo();
                if (master != null) {
                    BeanUtils.copyProperties(master, relatedSec);
                }
            }
            AdjustSharedData relatedShared = relatedSec == null ? null
                    : loadSharedDataForSecurity(relatedCode, relatedSec, mainShared, req);
            if (relatedShared != null) {
                mainShared.getRelatedSecurityData().put(relatedCode, relatedShared);
            }

            for (AdjustCheckDto.CheckResultItem mother : motherItems) {
                String dedupeKey = relatedCode + "_" + mother.getTargetPoolId() + "_" + mother.getAdjustMode();
                if (!coveredKeys.add(dedupeKey)) {
                    continue;
                }

                AdjustCheckDto.CheckResultItem relatedItem = new AdjustCheckDto.CheckResultItem();
                relatedItem.setTargetPoolId(mother.getTargetPoolId());
                relatedItem.setPoolName(mother.getPoolName());
                relatedItem.setPoolType(mother.getPoolType());
                relatedItem.setAdjustMode(mother.getAdjustMode());
                relatedItem.setItemTag(ItemType.RELATED.getCode());
                relatedItem.setAdjustGroupKey(mother.getAdjustGroupKey());
                relatedItem.setSecurityCode(relatedCode);
                relatedItem.setSourceSecurityCode(mainSec.getWindCode());

                if (relatedSec == null || relatedShared == null) {
                    relatedItem.setCanAdjust(false);
                    relatedItem.setFailReasons(Collections.singletonList(
                            "关联证券代码" + relatedCode + "在系统中不存在"));
                } else {
                    relatedItem.setSecurityShortName(relatedSec.getShortName());
                    relatedItem.setSecurityType(relatedSec.getSecurityType());
                    int baseCount = checkPoolCount(relatedShared, mother.getTargetPoolId());
                    // 调入时把本批已通过项计入有效持仓，再跑容量校验
                    int effectiveCount = AdjustMode.IN.getCode().equals(mother.getAdjustMode())
                            ? baseCount + projectedInbound.getOrDefault(mother.getTargetPoolId(), 0)
                            : baseCount;

                    AdjustCheckReq.CheckItem fakeItem = new AdjustCheckReq.CheckItem();
                    fakeItem.setTargetPoolId(mother.getTargetPoolId());
                    fakeItem.setAdjustMode(mother.getAdjustMode());
                    fakeItem.setPoolType(mother.getPoolType());
                    // 构建关联券校验上下文
                    AdjustCheckContext ctx = buildCheckContext(fakeItem, effectiveCount, relatedShared);
                    ctx.setExcelCompanyFollow(mainShared.isExcelCompanyFollow());
                    List<String> failures = AdjustMode.IN.getCode().equals(mother.getAdjustMode())
                            ? checkInConditions(ctx) : checkOutConditions(ctx);
                    relatedItem.setCanAdjust(failures.isEmpty());
                    relatedItem.setFailReasons(failures);
                    relatedItem.setWarnings(ctx.getWarnings());
                    if (relatedItem.isCanAdjust() && AdjustMode.IN.getCode().equals(mother.getAdjustMode())) {
                        projectedInbound.merge(mother.getTargetPoolId(), 1, Integer::sum);
                    }
                }

                AdjustCheckDto.CheckResultItem manualItem = manualByGroup.get(mother.getAdjustGroupKey());
                if (manualItem != null) {
                    // 同组手工项失败时阻断关联项
                    inheritManualItemFailure(relatedItem, manualItem);
                }
                relatedResults.add(relatedItem);
            }
        }
        resultItems.addAll(relatedResults);
        // 任一市场失败反向阻断整个调库组，避免只提交部分市场
        propagateRelatedMarketFailure(resultItems);
    }

    /** 汇总跨市场失败原因后统一阻断同组，保留原始失败项的具体原因。 */
    private void propagateRelatedMarketFailure(List<AdjustCheckDto.CheckResultItem> resultItems) {
        Map<String, List<String>> failuresByGroup = new HashMap<>();
        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            if (item.isCanAdjust()) {
                continue;
            }
            List<String> failures = failuresByGroup.computeIfAbsent(item.getAdjustGroupKey(), key -> new ArrayList<>());
            failures.add("同券跨市场证券[" + item.getSecurityCode() + "]在投资池[" + item.getPoolName()
                    + "]" + item.getAdjustMode() + "校验未通过，整组不可调整");
            failures.addAll(item.getFailReasons());
        }
        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            List<String> failures = failuresByGroup.get(item.getAdjustGroupKey());
            if (item.isCanAdjust() && failures != null) {
                item.setCanAdjust(false);
                item.setFailReasons(new ArrayList<>(failures));
            }
        }
    }

    /** 构建投资池全路径名称 */
    String buildPoolPath(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        InvestmentPoolBo pool = poolMap.get(poolId);
        if (pool == null) {
            return "";
        }
        String poolName = pool.getPoolName() != null ? pool.getPoolName() : "";
        if (pool.getParentId() == null) {
            return poolName;
        }
        // 构建投资池全路径名称
        String parentName = buildPoolPath(pool.getParentId(), poolMap);
        return parentName == null || parentName.isEmpty() ? poolName : parentName + "/" + poolName;
    }

    /** 校验一只证券的主项、联动、互斥和关联市场，并匹配流程候选 */
    AdjustCheckDto checkPreparedAdjust(AdjustCheckReq req, AdjustSharedData shared, boolean includeBatchFlows,
                                         boolean excelWarningsOnly) {
        shared.setBatchFlowOptionsEnabled(includeBatchFlows);

        // 预先将全部手动选择项注册到"已覆盖"集合，确保第三、四阶段生成的自动项
        // 不会与用户手动选择的项产生重复结果（手动项优先，自动项跳过已覆盖的 key）
        Set<String> coveredKeys = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            coveredKeys.add(item.getTargetPoolId() + "_" + item.getAdjustMode());
        }

        // ══ 第三阶段：调入校验 ══
        List<AdjustCheckDto.CheckResultItem> resultItems = new ArrayList<>(
                // 执行调入校验并补充联动、互斥调库项
                executeInAdjustCheck(req, shared, coveredKeys));

        // ══ 第四阶段：调出校验 ══
        resultItems.addAll(executeOutAdjustCheck(req, shared, coveredKeys));

        // ══ 第四点五阶段：多市场关联码扩批（itemTag=related） ══
        // 回填主券证券信息，并按 Wind 非空 ISIN 展开同池同向关联项
        expandRelatedMarketCodeItems(req, shared, resultItems);

        if (shared.isExcelCompanyFollow()) {
            // 已达到目标状态的关系项与关联券不重复执行
            resultItems.removeIf(item -> {
                Set<Long> current = shared.getBatchData().activePools
                        .getOrDefault(item.getSecurityCode(), Collections.emptySet());
                return current.contains(item.getTargetPoolId()) == AdjustMode.IN.getCode().equals(item.getAdjustMode());
            });
        }

        if (excelWarningsOnly) {
            // 原校验已经完整执行，只在 Excel 出口改变是否允许提交，不能清空失败原因
            allowExcelImportResults(shared, resultItems);
        }

        // 身份冲突作为不可调整原因展示，Excel 提示放行不能覆盖此结果
        if (shared.getBatchData() == null) {
            absIdentityService.applyIdentityCheckFailures(shared.getSecurityInfo(), resultItems);
        } else {
            absIdentityService.applyIdentityCheckFailures(shared.getSecurityInfo(), resultItems,
                    shared.getBatchData().absIdentities);
        }

        // ══ 第五阶段：流程类型判断 ══
        List<AdjustCheckDto.FlowOption> flowOptions = shared.isExcelCompanyFollow()
                ? Collections.emptyList() : resolveAdjustFlowOptions(req, shared, resultItems);

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

    /** 使用本批数据检查指定证券，适用普通调库阻断规则 */
    AdjustCheckDto checkBatchItem(AdjustCheckReq request, boolean includeBatchFlows, BatchSecurityCheckData data) {
        // 页面及任务按业务规则阻断
        return checkBatchItem(request, includeBatchFlows, data, false);
    }

    /** 使用本批数据检查指定证券，Excel 可保留业务失败为调整提示 */
    AdjustCheckDto checkBatchItem(AdjustCheckReq request, boolean includeBatchFlows, BatchSecurityCheckData data,
                                  boolean excelWarningsOnly) {
        // 用本批数据构建单券规则上下文
        AdjustSharedData shared = loadBatchSharedData(request, data);
        // 执行共用准入规则和审批流程匹配
        return checkPreparedAdjust(request, shared, includeBatchFlows, excelWarningsOnly);
    }

    /** 执行主体 Excel 决定派生的调库规则和池关系 */
    AdjustCheckDto checkExcelCompanyFollowItem(AdjustCheckReq request, BatchSecurityCheckData data) {
        AdjustSharedData shared = loadBatchSharedData(request, data);
        shared.setExcelCompanyFollow(true);
        return checkPreparedAdjust(request, shared, false, false);
    }

    /** 共用审批判断所需的流程版本和图数据 */
    static class FlowSnapshot {

        /** 流程定义 */
        final FlowDefinitionBo definition;

        /** 当前活跃版本 */
        final FlowVersionBo activeVersion;

        /** 节点索引（DB ID → FlowNodeBo），用于连线遍历时快速定位目标节点 */
        final Map<Long, FlowNodeBo> nodeMap;

        /** 该版本的全量连线列表 */
        final List<FlowEdgeBo> edges;

        /** 审批配置索引（nodeId → NodeApprovalConfigBo），用于创建步骤时获取审批策略和处理人 */
        final Map<Long, NodeApprovalConfigBo> approvalConfigMap;

        /** 审批处理人明细索引（approvalConfigId → handler 列表） */
        final Map<Long, List<NodeApprovalHandlerBo>> approvalHandlerMap;

        FlowSnapshot(FlowDefinitionBo definition,
                     FlowVersionBo activeVersion,
                     Map<Long, FlowNodeBo> nodeMap,
                     List<FlowEdgeBo> edges,
                     Map<Long, NodeApprovalConfigBo> approvalConfigMap,
                     Map<Long, List<NodeApprovalHandlerBo>> approvalHandlerMap) {
            this.definition = definition;
            this.activeVersion = activeVersion;
            this.nodeMap = nodeMap;
            this.edges = edges;
            this.approvalConfigMap = approvalConfigMap;
            this.approvalHandlerMap = approvalHandlerMap;
        }
    }

    /** 规则判定使用的权益人选择结果 */
    static final class RatingSelection {
        /** 当前关系中的普通权益人 */
        String rightsHolderName;
        /** 从全市场选择的自选权益人 */
        String selfSelectedRightsHolderName;
    }
}
