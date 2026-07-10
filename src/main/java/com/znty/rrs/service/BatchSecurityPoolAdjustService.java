package com.znty.rrs.service;
import com.znty.rrs.common.enums.ApprovalStrategy;

import com.znty.rrs.common.enums.FlowStatus;

import com.znty.rrs.common.enums.NodeType;

import com.znty.rrs.common.enums.ItemType;

import com.znty.rrs.common.enums.ProcessAction;

import com.znty.rrs.common.enums.StepStatus;

import com.znty.rrs.common.enums.AuditStatus;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.CategoryType;

import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AttachmentCategory;

import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.enums.HandlerType;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityCandidateDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolDto;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.flow.FlowOptionParam;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 证券池批量调整业务服务
 */
@Service
public class BatchSecurityPoolAdjustService {

    /** 主体评级是否下调（当前写死，后续接评级历史查询替换） */
    private static final boolean ISSUER_RATING_DOWNGRADED = false;
    /** 展望评级是否下调（当前写死，后续接评级历史查询替换） */
    private static final boolean OUTLOOK_RATING_DOWNGRADED = false;
    /** 担保人评级是否下调（当前写死，后续接评级历史查询替换） */
    private static final boolean GUARANTOR_RATING_DOWNGRADED = false;
    /** 白名单池 ID 集合：主体在这些池中时符合白名单条件；当前写死空集，后续配置后补 queryIssuerInWhitelistPools 查询 */
    private static final Set<Long> WHITELIST_POOL_IDS = Collections.emptySet();
    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";

    /** 白名单调入流程 Key */
    private static final String FLOW_KEY_WHITELIST_INBOUND = "bond:whitelist-inbound";

    /** 信用债标准上调流程 Key */
    private static final String FLOW_KEY_STANDARD_UPGRADE = "bond:standard-upgrade";

    /** 信用债标准下调流程 Key */
    private static final String FLOW_KEY_STANDARD_DOWNGRADE = "bond:standard-downgrade";
    /** 调入互斥池特殊审批流程 Key */
    private static final String FLOW_KEY_SPECIAL_INBOUND = "bond:special-inbound";
    /** 日期字段格式：yyyyMMdd */
    private static final DateTimeFormatter BASIC_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /** 证券池批量调整数据访问组件 */
    @Resource
    private BatchSecurityPoolAdjustMapper batchSecurityPoolAdjustMapper;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 证券池调库数据访问组件 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 流程定义数据访问组件 */
    @Resource
    private FlowMapper flowMapper;

    /** 信用债评级矩阵数据访问组件（主体债入库规则校验用） */
    @Resource
    private CreditBondGradeRuleMapper creditBondGradeRuleMapper;

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /**
     * 分页查询当前用户可调整的启用叶子投资池
     */
    public PageResult<BatchSecurityPoolDto> queryPoolPage(BatchSecurityPoolAdjustReq req) {
        // 处理当前用户可调整投资池筛选条件
        if (!prepareAdjustablePoolIds(req)) {
            return new PageResult<>(
                    new ArrayList<>(), 0L, req.getPageIndex(), req.getPageSize());
        }

        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchSecurityPoolDto> poolList = batchSecurityPoolAdjustMapper.queryPoolPage(req);
        PageInfo<BatchSecurityPoolDto> pageInfo = new PageInfo<>(poolList);
        // 填充当前页投资池现有证券数量
        fillPoolCurrentCount(poolList);
        // 填充投资池全路径名称
        fillPoolFullName(poolList);
        return new PageResult<>(
                poolList, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询目标池批量调整候选证券
     */
    public PageResult<BatchSecurityCandidateDto> querySecurityPage(BatchSecurityPoolAdjustReq req) {
        // 校验候选证券查询参数
        validateSecurityPageReq(req);
        // 校验目标投资池调整权限
        validatePoolPermission(req);

        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchSecurityCandidateDto> list = batchSecurityPoolAdjustMapper.querySecurityPage(req);
        PageInfo<BatchSecurityCandidateDto> pageInfo = new PageInfo<>(list);

        // 将 SQL 返回的市场文本转换为市场编码列表
        fillMarketCodes(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 批量调库下一步校验
     */
    public BatchSecurityInboundAdjustDto checkAdjust(BatchSecurityInboundAdjustReq req) {
        // 校验批量调库下一步参数
        validateAdjustCheckReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());

        BatchSecurityInboundAdjustDto dto = new BatchSecurityInboundAdjustDto();
        for (BatchSecurityInboundAdjustReq.SecurityItem security : req.getSecurities()) {
            // 构建单证券调库校验请求
            AdjustCheckDto checkDto = checkSingleAdjust(buildSingleCheckReq(req, security));
            if (checkDto == null || checkDto.getItems() == null) {
                continue;
            }
            for (AdjustCheckDto.CheckResultItem item : checkDto.getItems()) {
                // 构建批量调库校验结果
                dto.getItems().add(buildBatchCheckResult(security, item));
            }
        }
        return dto;
    }

    /**
     * 批量提交调库申请
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(BatchSecurityInboundAdjustReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 批量提交调库申请及附件。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(
            BatchSecurityInboundAdjustReq req, List<MultipartFile> files) {
        // 校验批量调库提交参数
        validateAdjustSubmitReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());

        Map<String, List<BatchSecurityInboundAdjustReq.AdjustItem>> itemMap = new HashMap<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            List<BatchSecurityInboundAdjustReq.AdjustItem> list = itemMap.get(item.getSecurityCode());
            if (list == null) {
                list = new ArrayList<>();
                itemMap.put(item.getSecurityCode(), list);
            }
            list.add(item);
        }

        BatchSecurityInboundAdjustDto dto = new BatchSecurityInboundAdjustDto();
        dto.setSecurityCount(itemMap.size());
        dto.setSubmitCount(0);
        SysAttachmentService.SubmissionFiles submissionFiles =
                // 创建批量提交附件上下文
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId());
        BatchNoContext batchNoContext = new BatchNoContext();
        for (Map.Entry<String, List<BatchSecurityInboundAdjustReq.AdjustItem>> entry : itemMap.entrySet()) {
            // 构建单证券调库提交请求
            AdjustSubmitDto submitDto = addSingleAdjustLog(
                    buildSingleSubmitReq(req, entry.getValue()), submissionFiles, batchNoContext);
            if (submitDto == null) {
                continue;
            }
            dto.setSubmitCount(dto.getSubmitCount() + (submitDto.getSubmitCount() == null ? 0 : submitDto.getSubmitCount()));
            if (submitDto.getLogIds() != null) {
                dto.getLogIds().addAll(submitDto.getLogIds());
            }
        }
        return dto;
    }

    /**
     * 校验候选证券查询参数
     */
    private void validateSecurityPageReq(BatchSecurityPoolAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (batchSecurityPoolAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用或不是叶子池");
        }
    }

    /**
     * 校验批量调库下一步参数
     */
    private void validateAdjustCheckReq(BatchSecurityInboundAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (req.getSecurities() == null || req.getSecurities().isEmpty()) {
            throw new BizException("已选证券不能为空");
        }
        if (batchSecurityPoolAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用或不是叶子池");
        }
        for (BatchSecurityInboundAdjustReq.SecurityItem security : req.getSecurities()) {
            if (security.getSecurityCode() == null || security.getSecurityCode().isEmpty()) {
                throw new BizException("已选证券代码不能为空");
            }
        }
    }

    /**
     * 校验批量调库提交参数
     */
    private void validateAdjustSubmitReq(BatchSecurityInboundAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("可提交调库明细不能为空");
        }
        if (req.getAdjusterId() == null || req.getAdjusterId().isEmpty()) {
            throw new BizException("调整人 ID 不能为空");
        }
        if (req.getAdjusterName() == null || req.getAdjusterName().isEmpty()) {
            throw new BizException("调整人名称不能为空");
        }
        if (batchSecurityPoolAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用或不是叶子池");
        }
        // 解析批量调库中文方向
        String adjustMode = resolveAdjustMode(req);
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            if (item.getSecurityCode() == null || item.getSecurityCode().isEmpty()) {
                throw new BizException("调库明细证券代码不能为空");
            }
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode()) && !AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                throw new BizException("调库明细调整方向必须为调入或调出");
            }
            // 判断批量提交项是否为手工调库项
            if (isManualBatchSubmitItem(item) && !adjustMode.equals(item.getAdjustMode())) {
                throw new BizException("调库明细调整方向必须与本次批量调整方向一致");
            }
            if (item.getTargetPoolId() == null) {
                throw new BizException("调库明细目标投资池 ID 不能为空");
            }
            // 仅手工项要求选择审批流程，联动/互斥项共用同组手工项的流程（后端 resolveManualSubmitItem 自动取用）
            if (isManualBatchSubmitItem(item)
                    && item.getFlowId() == null
                    && (item.getFlowKey() == null || item.getFlowKey().isEmpty())) {
                throw new BizException("调库明细审批流程不能为空");
            }
        }
    }

    /**
     * 判断批量提交项是否为手工调库项。
     */
    private boolean isManualBatchSubmitItem(BatchSecurityInboundAdjustReq.AdjustItem item) {
        return item.getItemTag() == null || item.getItemTag().isEmpty() || ItemType.MANUAL.getCode().equals(item.getItemTag());
    }

    /**
     * 校验批量调库方向
     */
    private void validateAdjustDirection(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
    }

    /**
     * 解析批量调库中文方向
     */
    private String resolveAdjustMode(BatchSecurityInboundAdjustReq req) {
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        return "out".equals(req.getDirection()) ? AdjustMode.OUT.getCode() : AdjustMode.IN.getCode();
    }

    /**
     * 校验批量调库目标池权限
     */
    private void validateAdjustPoolPermission(String currentUserId, Long poolId) {
        BatchSecurityPoolAdjustReq permissionReq = new BatchSecurityPoolAdjustReq();
        permissionReq.setCurrentUserId(currentUserId);
        permissionReq.setPoolId(poolId);
        // 校验目标投资池调整权限
        validatePoolPermission(permissionReq);
    }

    /**
     * 构建单证券调库校验请求
     */
    private AdjustCheckReq buildSingleCheckReq(BatchSecurityInboundAdjustReq req,
                                               BatchSecurityInboundAdjustReq.SecurityItem security) {
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(req.getPoolId());
        item.setTargetPoolName(req.getPoolName());
        item.setPoolType(req.getPoolType());
        // 解析批量调库中文方向
        item.setAdjustMode(resolveAdjustMode(req));

        AdjustCheckReq checkReq = new AdjustCheckReq();
        checkReq.setSecurityCode(security.getSecurityCode());
        checkReq.setSecurityShortName(security.getSecurityShortName());
        checkReq.setSecurityType(security.getSecurityType());
        checkReq.setItems(Collections.singletonList(item));
        return checkReq;
    }

    /**
     * 构建批量调库校验结果。
     */
    private BatchSecurityInboundAdjustDto.CheckResultItem buildBatchCheckResult(
            BatchSecurityInboundAdjustReq.SecurityItem security,
            AdjustCheckDto.CheckResultItem item) {
        BatchSecurityInboundAdjustDto.CheckResultItem result = new BatchSecurityInboundAdjustDto.CheckResultItem();
        result.setSecurityCode(security.getSecurityCode());
        result.setSecurityShortName(security.getSecurityShortName());
        result.setSecurityType(security.getSecurityType());
        result.setTargetPoolId(item.getTargetPoolId());
        result.setPoolName(item.getPoolName());
        result.setPoolType(item.getPoolType());
        result.setAdjustMode(item.getAdjustMode());
        result.setItemTag(item.getItemTag());
        result.setAdjustGroupKey(security.getSecurityCode() + "_" + item.getAdjustGroupKey());
        result.setCanAdjust(item.isCanAdjust());
        result.setFailReasons(item.getFailReasons() == null ? new ArrayList<>() : item.getFailReasons());
        result.setFlowOptions(item.getFlowOptions() == null ? new ArrayList<>() : item.getFlowOptions());
        return result;
    }

    /**
     * 构建单证券调库提交请求。
     */
    private SecurityPoolAdjustSubmitReq buildSingleSubmitReq(
            BatchSecurityInboundAdjustReq req,
            List<BatchSecurityInboundAdjustReq.AdjustItem> items) {
        BatchSecurityInboundAdjustReq.AdjustItem first = items.get(0);
        SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
        submitReq.setSecurityCode(first.getSecurityCode());
        submitReq.setSecurityShortName(first.getSecurityShortName());
        submitReq.setSecurityType(first.getSecurityType());
        submitReq.setCrmwName(first.getCrmwName());
        submitReq.setCrmwScode(first.getCrmwScode());
        submitReq.setCrmwMktcode(first.getCrmwMktcode());
        submitReq.setCrmwStype(first.getCrmwStype());
        submitReq.setAdjustType("手工调整");
        submitReq.setAdjustReason(req.getAdjustReason());
        submitReq.setAdjustAdvice(req.getAdjustAdvice());
        submitReq.setAdjusterId(req.getAdjusterId());
        submitReq.setAdjusterName(req.getAdjusterName());

        List<SecurityPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : items) {
            SecurityPoolAdjustSubmitReq.AdjustItem submitItem = new SecurityPoolAdjustSubmitReq.AdjustItem();
            submitItem.setTargetPoolId(item.getTargetPoolId());
            submitItem.setTargetPoolName(item.getTargetPoolName());
            submitItem.setPoolType(item.getPoolType());
            submitItem.setAdjustMode(item.getAdjustMode());
            submitItem.setItemTag(item.getItemTag());
            submitItem.setAdjustGroupKey(item.getAdjustGroupKey());
            submitItem.setFlowId(item.getFlowId());
            submitItem.setFlowKey(item.getFlowKey());
            submitItem.setFlowType(item.getFlowType());
            submitItem.setAdjustmentNote(item.getAdjustmentNote());
            submitItem.setCreditReportFileIndexes(item.getCreditReportFileIndexes());
            submitItem.setMaterialFileIndexes(item.getMaterialFileIndexes());
            submitItem.setCreditReportSourceAttachmentIds(item.getCreditReportSourceAttachmentIds());
            submitItem.setMaterialSourceAttachmentIds(item.getMaterialSourceAttachmentIds());
            submitItems.add(submitItem);
        }
        submitReq.setItems(submitItems);
        return submitReq;
    }

    /** 校验批量调库提交请求参数合法性 */
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
        // 创建本次提交独立批次号上下文
        return loadSubmitSharedData(req, new BatchNoContext());
    }

    /**
     * 第二阶段：参数初始化，使用指定批次号上下文。
     */
    private SubmitSharedData loadSubmitSharedData(SecurityPoolAdjustSubmitReq req, BatchNoContext batchNoContext) {
        // 证券基础信息（兼含存在性校验）
        SecurityInfoBo securityInfo = securityPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }

        // 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        for (InvestmentPoolBo p : allPools) {
            poolMap.put(p.getId(), p);
        }

        // 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
        List<Long> currentPoolIdList = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList);

        // 全量投资池关系配置，构建三层嵌套 Map
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                securityPoolAdjustMapper.queryAllPoolRelationList());

        // 收集所有调库项中引用的唯一流程标识，批量加载流程快照
        Set<Long> uniqueFlowIds = new HashSet<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long resolvedId = resolveFlowIdFromItem(item);
            if (resolvedId != null) {
                uniqueFlowIds.add(resolvedId);
            }
        }

        // 为每个唯一流程加载快照（定义 + 活跃版本 + 节点 + 连线）
        Map<Long, FlowSnapshot> flowSnapshotMap = new HashMap<>();
        for (Long flowId : uniqueFlowIds) {
            // 为指定流程 ID 构建运行时快照（定义 + 活跃版本 + 节点索引 + 连线列表）
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
                flowSnapshotMap,
                batchNoContext
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
     * 判断提交项是否为手工调库项。
     */
    private boolean isManualSubmitItem(SecurityPoolAdjustSubmitReq.AdjustItem item) {
        return item.getItemTag() == null || item.getItemTag().isEmpty() || ItemType.MANUAL.getCode().equals(item.getItemTag());
    }

    /**
     * 生成调库批次号。
     */
    private String buildAdjustBatchNo(
            SecurityPoolAdjustSubmitReq.AdjustItem manualItem, boolean noFlow, SubmitSharedData shared) {
        int serial;
        if (noFlow) {
            serial = 3000 + ++shared.batchNoContext.noFlowBatchSeq;
        } else if (AdjustMode.IN.getCode().equals(manualItem.getAdjustMode())) {
            serial = 1000 + ++shared.batchNoContext.inboundBatchSeq;
        } else {
            serial = 2000 + ++shared.batchNoContext.outboundBatchSeq;
        }
        return "BOND" + shared.batchNoContext.batchTimeText + String.format("%04d", serial);
    }

    /**
     * 获取或生成同组调库批次号。
     */
    private String resolveAdjustBatchNo(
            SecurityPoolAdjustSubmitReq req,
            SecurityPoolAdjustSubmitReq.AdjustItem item,
            boolean noFlow,
            SubmitSharedData shared) {
        String groupKey = item.getAdjustGroupKey();
        if (groupKey == null || groupKey.isEmpty()) {
            throw new BizException("调库分组标识不能为空");
        }
        String batchNo = shared.adjustBatchNoMap.get(groupKey);
        if (batchNo == null) {
            // 生成调库批次号
            batchNo = buildAdjustBatchNo(resolveManualSubmitItem(req, item), noFlow, shared);
            shared.adjustBatchNoMap.put(groupKey, batchNo);
        }
        return batchNo;
    }

    /**
     * 获取同组手工调库项，联动/互斥项按手工项共用流程和批次号。
     */
    private SecurityPoolAdjustSubmitReq.AdjustItem resolveManualSubmitItem(
            SecurityPoolAdjustSubmitReq req, SecurityPoolAdjustSubmitReq.AdjustItem item) {
        // 判断提交项是否为手工调库项
        if (isManualSubmitItem(item)) {
            return item;
        }
        String groupKey = item.getAdjustGroupKey();
        for (SecurityPoolAdjustSubmitReq.AdjustItem candidate : req.getItems()) {
            // 判断提交项是否为手工调库项
            if (isManualSubmitItem(candidate) && groupKey != null && groupKey.equals(candidate.getAdjustGroupKey())) {
                return candidate;
            }
        }
        throw new BizException("未找到调库分组对应的手工调整记录");
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
     * 判断流程是否为直通流程（开始后无需人工处理即可到结束节点）。
     *
     * <p>判定逻辑：
     * <ol>
     *   <li>在节点列表中查找 nodeType='start' 的节点</li>
     *   <li>在连线列表中查找 fromNodeId 等于 start 节点 DB ID 的所有出边</li>
     *   <li>逐一检查每个出边的目标节点是否为 end，或是否为发起人自动提交节点且继续指向 end</li>
     *   <li>若所有路径都能无需人工处理到 end → 直通流程；若存在人工待办节点 → 非直通（需审批）</li>
     * </ol>
     *
     * @param snapshot 流程快照（含节点索引和连线列表）
     * @return true 表示直通流程（可直接生效），false 表示需要审批
     */
    private boolean isDirectFlow(FlowSnapshot snapshot) {
        // 查找 start 节点
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

        // 检查所有出边是否都能直达结束节点
        for (FlowEdgeBo edge : outEdges) {
            FlowNodeBo targetNode = snapshot.nodeMap.get(edge.getToNodeId());
            if (targetNode == null) {
                return false;
            }
            if (NodeType.END.getCode().equals(targetNode.getNodeType())) {
                continue;
            }

            NodeApprovalConfigBo config = snapshot.approvalConfigMap.get(targetNode.getId());
            // 判断当前审批节点是否应由流程发起人自动完成
            if (!isInitiatorStep(snapshot, targetNode, config, startNode, startNode)) {
                return false;
            }

            boolean targetCanEnd = false;
            for (FlowEdgeBo nextEdge : snapshot.edges) {
                if (!nextEdge.getFromNodeId().equals(targetNode.getId())) {
                    continue;
                }
                FlowNodeBo nextNode = snapshot.nodeMap.get(nextEdge.getToNodeId());
                if (nextNode != null && NodeType.END.getCode().equals(nextNode.getNodeType())) {
                    targetCanEnd = true;
                    break;
                }
            }
            if (!targetCanEnd) {
                return false;
            }
        }

        return true;
    }

    /**
     * 规则：报告必填（in_report_restriction / out_report_restriction）
     *
     * <p>目标池配置了报告限制时，提交时校验报告附件：
     * none=不限制 / any=任意一篇研究报告 / internal=必须是内部研究报告。
     * 对应老项目 rschDocMode/rschDocOutMode 报告校验。在提交阶段校验（checkAdjust 阶段无报告信息）。
     */
    private void checkReportRequired(SecurityPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool, String reportRestriction, String securityCode) {
        if (pool == null || reportRestriction == null || reportRestriction.isEmpty() || "none".equals(reportRestriction)) {
            return;
        }
        // 6个月内入池报告标记（对齐老系统 bondfileflag，6个月）：同主体半年内有审批通过调入记录则跳过报告校验
        if (securityCode != null && !securityCode.isEmpty() && securityPoolAdjustMapper.queryHasRecentInboundWithReport(securityCode)) {
            return;
        }
        boolean hasReport = (item.getCreditReportFileIndexes() != null && !item.getCreditReportFileIndexes().isEmpty())
                || (item.getCreditReportSourceAttachmentIds() != null && !item.getCreditReportSourceAttachmentIds().isEmpty());
        if (!hasReport) {
            throw new BizException("目标池[" + pool.getPoolName() + "]要求研究报告，请上传或选择报告");
        }
        // internal 要求内部研究报告（简化：要求 creditReportSourceAttachmentIds 非空，后续完善内部/外部区分）
        if ("internal".equals(reportRestriction)
                && (item.getCreditReportSourceAttachmentIds() == null || item.getCreditReportSourceAttachmentIds().isEmpty())) {
            throw new BizException("目标池[" + pool.getPoolName() + "]要求内部研究报告，请从内部报告库选择");
        }
    }

    /**
     * 第三阶段：调入处理
     *
     * <p>遍历请求中全部调入方向的调库项，逐项判断流程是否为直通（start→end），
     * 决定写入已生效调库记录并直接更新 ip_pool_status，还是写入待审批调库记录。
     *
     * @param req    调库提交请求
     * @param shared 本次提交的共享数据
     * @return 本次调入处理生成的所有调库记录 ID
     */
    private List<Long> executeInboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        return executeInboundSubmit(req, shared,
                // 创建批量提交附件上下文
                sysAttachmentService.createSubmissionFiles(Collections.<MultipartFile>emptyList(), req.getAdjusterId()));
    }

    /**
     * 执行调入提交并绑定本次 multipart 文件。
     */
    private List<Long> executeInboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                            SysAttachmentService.SubmissionFiles submissionFiles) {

        List<Long> generatedIds = new ArrayList<>();

        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 报告必填校验（按池 in_report_restriction，提交阶段校验）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            checkReportRequired(item, reportPool, reportPool != null ? reportPool.getInReportRestriction() : null, req.getSecurityCode());
            // 获取同组手工调库项，联动/互斥项按手工项共用流程和批次号
            SecurityPoolAdjustSubmitReq.AdjustItem manualItem = resolveManualSubmitItem(req, item);
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long flowId = resolveFlowIdFromItem(manualItem);
            FlowSnapshot snapshot = flowId != null ? shared.flowSnapshotMap.get(flowId) : null;
            boolean noFlow = flowId == null;
            // 获取或生成同组调库批次号
            String adjustBatchNo = resolveAdjustBatchNo(req, item, noFlow, shared);
            // 判断流程是否为直通流程（开始后无需人工处理即可到结束节点）
            boolean isDirect = noFlow || (snapshot != null && isDirectFlow(snapshot));

            if (isDirect) {
                // 直通流程：先写入已生效调库记录，保留操作日志
                IpAdjustLogBo logBo = buildAdjustLog(req, item, manualItem);
                logBo.setAdjustBatchNo(adjustBatchNo);
                logBo.setAuditStatus(AuditStatus.APPROVED.getCode());
                securityPoolAdjustMapper.addAdjustLog(logBo);
                generatedIds.add(logBo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(logBo.getId(), item, submissionFiles, req.getAdjusterId());
                // 有流程定义的直通流程仍记录开始、发起、结束步骤
                if (isManualSubmitItem(item) && snapshot != null && logBo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    createInitialSteps(logBo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                }

                // 直通流程：再直接写入 ip_pool_status（audit_status='20'，即时生效）
                logBo.setAdjustLogId(logBo.getId());
                securityPoolAdjustMapper.addPoolStatus(logBo);
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，流程中）
                // 构建调库日志实体
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem);
                bo.setAdjustBatchNo(adjustBatchNo);
                bo.setAuditStatus(AuditStatus.SUBMITTED.getCode());
                securityPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 手工项创建初始流程步骤，联动/互斥项共用同批次流程状态
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    boolean flowFinished = createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                    if (flowFinished) {
                        bo.setAuditStatus(AuditStatus.APPROVED.getCode());
                        securityPoolAdjustMapper.editAdjustLogAuditStatus(bo.getId(), adjustBatchNo, AuditStatus.APPROVED.getCode());
                        bo.setAdjustLogId(bo.getId());
                        securityPoolAdjustMapper.addPoolStatus(bo);
                    }
                }
            }
        }

        return generatedIds;
    }

    /**
     * 第四阶段：调出处理
     *
     * <p>遍历请求中全部调出方向的调库项，逐项判断流程是否为直通（start→end），
     * 决定写入已生效调库记录并软删除 ip_pool_status，还是写入待审批调库记录。
     *
     * @param req    调库提交请求
     * @param shared 本次提交的共享数据
     * @return 本次调出处理生成的所有调库记录 ID
     */
    private List<Long> executeOutboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        return executeOutboundSubmit(req, shared,
                // 创建批量提交附件上下文
                sysAttachmentService.createSubmissionFiles(Collections.<MultipartFile>emptyList(), req.getAdjusterId()));
    }

    /**
     * 执行调出提交并绑定本次 multipart 文件。
     */
    private List<Long> executeOutboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                             SysAttachmentService.SubmissionFiles submissionFiles) {

        List<Long> generatedIds = new ArrayList<>();

        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 报告必填校验（按池 out_report_restriction，提交阶段校验）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            checkReportRequired(item, reportPool, reportPool != null ? reportPool.getOutReportRestriction() : null, req.getSecurityCode());
            // 获取同组手工调库项，联动/互斥项按手工项共用流程和批次号
            SecurityPoolAdjustSubmitReq.AdjustItem manualItem = resolveManualSubmitItem(req, item);
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long flowId = resolveFlowIdFromItem(manualItem);
            FlowSnapshot snapshot = flowId != null ? shared.flowSnapshotMap.get(flowId) : null;
            boolean noFlow = flowId == null;
            // 获取或生成同组调库批次号
            String adjustBatchNo = resolveAdjustBatchNo(req, item, noFlow, shared);
            // 判断流程是否为直通流程（开始后无需人工处理即可到结束节点）
            boolean isDirect = noFlow || (snapshot != null && isDirectFlow(snapshot));

            if (isDirect) {
                // 直通流程：先写入已生效调库记录，保留操作日志
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem);
                bo.setAdjustBatchNo(adjustBatchNo);
                bo.setAuditStatus(AuditStatus.APPROVED.getCode());
                securityPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 有流程定义的直通流程仍记录开始、发起、结束步骤
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                }

                // 直通流程：再软删除 ip_pool_status 中该证券在目标池的有效记录
                securityPoolAdjustMapper.deletePoolStatusSoft(
                        req.getSecurityCode(), item.getTargetPoolId());
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，流程中）
                // 构建调库日志实体
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem);
                bo.setAdjustBatchNo(adjustBatchNo);
                bo.setAuditStatus(AuditStatus.SUBMITTED.getCode());
                securityPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 手工项创建初始流程步骤，联动/互斥项共用同批次流程状态
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    boolean flowFinished = createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                    if (flowFinished) {
                        securityPoolAdjustMapper.editAdjustLogAuditStatus(bo.getId(), adjustBatchNo, AuditStatus.APPROVED.getCode());
                        securityPoolAdjustMapper.deletePoolStatusSoft(
                                req.getSecurityCode(), item.getTargetPoolId());
                    }
                }
            }
        }

        return generatedIds;
    }

    /**
     * 第五阶段：后续处理（预留扩展点）
     *
     * <p>当前用于同步更新调库详情页传入的证券基础信息字段。
     *
     * @param req    调库提交请求
     * @param shared 本次提交的共享数据
     */
    private void postSubmitProcess(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        if (req.getSecurityInfo() == null) {
            return;
        }
        // 合并数据库当前完整快照与前端传入的变更字段
        req.setSecurityInfo(buildMergedSecurityInfo(req.getSecurityCode(), req.getSecurityInfo()));
        // 同步更新证券基础信息表中本次传入的字段
        securityPoolAdjustMapper.editSecurityInfoForAdjust(req);
    }

    /**
     * 构建证券基础信息全量更新参数。
     *
     * <p>前端只需要传入可能变更的字段；这里先加载数据库当前完整快照，
     * 再用前端传入字段覆盖同名字段。若前端显式传入 null，也会覆盖为 null，用于清空字段。
     *
     * @param securityCode 证券代码
     * @param changedField 前端传入的变更字段
     */
    private SecurityInfoBo buildMergedSecurityInfo(String securityCode, SecurityInfoBo changedField) {
        SecurityInfoBo current = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (current == null) {
            throw new BizException("证券不存在");
        }
        // 使用前端本次可编辑字段覆盖数据库当前实体
        mergeSecurityInfo(current, changedField);
        return current;
    }

    /**
     * 将调库详情页可编辑字段逐项覆盖到当前证券实体。
     */
    private void mergeSecurityInfo(SecurityInfoBo current, SecurityInfoBo changedField) {
        current.setFullName(changedField.getFullName());
        current.setShortName(changedField.getShortName());
        current.setWindCode(changedField.getWindCode());
        current.setIssuer(changedField.getIssuer());
        current.setWindCodeNib(changedField.getWindCodeNib());
        current.setWindCodeSh(changedField.getWindCodeSh());
        current.setWindCodeSz(changedField.getWindCodeSz());
        current.setWindCodeBj(changedField.getWindCodeBj());
        current.setIssueAmountplan(changedField.getIssueAmountplan());
        current.setCouponRate(changedField.getCouponRate());
        current.setDateInrightExists(changedField.getDateInrightExists());
        current.setCarryDate(changedField.getCarryDate());
        current.setMaturityDate(changedField.getMaturityDate());
        current.setInfoPledgeRatio(changedField.getInfoPledgeRatio());
        current.setRatingBondAgency(changedField.getRatingBondAgency());
        current.setRatingBond(changedField.getRatingBond());
        current.setRatingBondissuer(changedField.getRatingBondissuer());
        current.setRatingOutlook(changedField.getRatingOutlook());
        current.setAgencyGrnttype(changedField.getAgencyGrnttype());
        current.setAgencyName(changedField.getAgencyName());
        current.setInnerIssuerRating(changedField.getInnerIssuerRating());
        current.setDateRedemtionExists(changedField.getDateRedemtionExists());
        current.setDateCallExists(changedField.getDateCallExists());
        current.setInnerGuarantorRating(changedField.getInnerGuarantorRating());
        current.setDateExists(changedField.getDateExists());
        current.setFundUse(changedField.getFundUse());
        current.setPromptReason(changedField.getPromptReason());
        current.setAnalysis(changedField.getAnalysis());
        current.setDateRepurchaseExists(changedField.getDateRepurchaseExists());
        current.setGuarantFlag(changedField.getGuarantFlag());
        current.setGuarantType(changedField.getGuarantType());
        current.setAbsFlag(changedField.getAbsFlag());
    }

    // ═══════════════════════════════════════════════════════════
    //  查询类接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询证券的历史调库记录列表（全量，不分页）
     *
     * @param req 需携带 securityCode
     */

    /**
     * 本类内提交单只证券的调库申请，供批量调库按证券分组调用。
     */
    private AdjustSubmitDto addSingleAdjustLog(SecurityPoolAdjustSubmitReq req,
                                               SysAttachmentService.SubmissionFiles submissionFiles,
                                               BatchNoContext batchNoContext) {
        // 第一阶段：前置校验
        validateSubmitReq(req);
        // 第二阶段：参数初始化
        SubmitSharedData shared = loadSubmitSharedData(req, batchNoContext);
        // 第三阶段：调入处理
        List<Long> inboundIds = executeInboundSubmit(req, shared, submissionFiles);
        // 第四阶段：调出处理
        List<Long> outboundIds = executeOutboundSubmit(req, shared, submissionFiles);
        // 第五阶段：后续处理
        postSubmitProcess(req, shared);

        AdjustSubmitDto dto = new AdjustSubmitDto();
        dto.setSecurityCode(req.getSecurityCode());
        List<Long> allIds = new ArrayList<>(inboundIds);
        allIds.addAll(outboundIds);
        dto.setSubmitCount(allIds.size());
        dto.setLogIds(allIds);
        return dto;
    }

    /** 执行单证券调库校验 */
    private AdjustCheckDto checkSingleAdjust(AdjustCheckReq req) {

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
                // 执行调入校验并补充联动、互斥调库项
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
     * @param req 调库校验请求（需携带 securityCode）
     * @return 封装了本次校验全量共享数据的 AdjustSharedData
     */
    private AdjustSharedData loadSharedData(AdjustCheckReq req) {
        // 证券基础信息（兼含存在性校验）
        SecurityInfoBo securityInfo = securityPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }

        // 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        for (InvestmentPoolBo p : allPools) {
            poolMap.put(p.getId(), p);
        }

        // 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
        List<Long> currentPoolIdList = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList);

        // 全量投资池关系配置，构建三层嵌套 Map（poolId → relationType → 关联池列表）
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                securityPoolAdjustMapper.queryAllPoolRelationList());

        // 提取本次请求中调入/调出各自涉及的目标池 ID 集合，供互斥冲突校验使用
        Set<Long> requestInPoolIds  = new HashSet<>();
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                requestInPoolIds.add(item.getTargetPoolId());
            } else if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                requestOutPoolIds.add(item.getTargetPoolId());
            }
        }

        // 证券级别的状态标志（整个请求期间保持不变，统一在此查询）
        AdjustSharedData shared = new AdjustSharedData();
        shared.setSecurityInfo(securityInfo);
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(currentPoolIds);
        shared.setPoolRelationMap(poolRelationMap);
        shared.setHasPendingProcess(securityPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()));
        shared.setPendingProcessNodeLabel(securityPoolAdjustMapper.querySecurityPendingProcessNodeLabel(req.getSecurityCode()));
        shared.setSecurityInObservePool(securityPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()));
        shared.setIssuerInObservePool(securityPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()));
        // 评级下调三标志：当前写死未下调，后续接入评级历史表（老项目 sdc_sirm_bondcompanylevel）后替换为真实查询
        shared.setIssuerRatingDowngraded(ISSUER_RATING_DOWNGRADED);
        shared.setOutlookRatingDowngraded(OUTLOOK_RATING_DOWNGRADED);
        shared.setGuarantorRatingDowngraded(GUARANTOR_RATING_DOWNGRADED);
        shared.setRequestInPoolIds(requestInPoolIds);
        shared.setRequestOutPoolIds(requestOutPoolIds);
        // 基金评分（基金证券调入校验用，透传请求级 fundRate）
        shared.setFundRate(req.getFundRate());
        return shared;
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
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 构建手工调库项分组 Key
            String adjustGroupKey = buildAdjustGroupKey(item);

            // 手动调入项：前置校验 + 调入校验
            int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
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
            List<Long> inMutex = relations.get(RelationType.IN_MUTEX.getCode());
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
                        results.add(autoItem);
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
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 构建手工调库项分组 Key
            String adjustGroupKey = buildAdjustGroupKey(item);

            // 手动调出项：前置校验 + 调出校验
            int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
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
     * <p>调出时直接使用目标投资池配置的标准调出流程。调入时：信用债已在库返回上调/下调单流程，
     * 信用债未在库返回白名单/简易/默认调入三类候选，非信用债大库返回默认调入流程。
     * 白名单和简易规则按伪代码保留入口，不改表结构。</p>
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param resultItems 前四阶段的校验结果
     * @return 流程候选项
     */
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptions(
            AdjustCheckReq req, AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> resultItems) {

        List<AdjustCheckDto.FlowOption> allOptions = new ArrayList<>();
        Set<String> optionKeys = new HashSet<>();
        if (resultItems == null || resultItems.isEmpty()) {
            return allOptions;
        }

        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            // 判断单条手工调库项可选择的审批流程
            List<AdjustCheckDto.FlowOption> itemOptions = resolveAdjustFlowOptionsForItem(req, shared, item);
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
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptionsForItem(
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

        // 手工调入：当前已在目标池调入互斥池时，优先走特殊审批流程
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
            FlowDefinitionBo flow = flowMapper.queryActiveFlowByKey(flowKey);
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

        // ── 证券不在信用债大库中：依次评估白名单 → 简易 → 默认调入流程 ──
        // 白名单流程命中判断
        List<String> whitelistMatchReasons = new ArrayList<>();
        List<String> whitelistUnmatchReasons = new ArrayList<>();
        // 判断白名单流程是否命中
        boolean whitelistMatched = isWhitelistFlowMatched(req, shared, whitelistMatchReasons, whitelistUnmatchReasons);

        // 简易流程命中判断
        List<String> simpleMatchReasons = new ArrayList<>();
        List<String> simpleUnmatchReasons = new ArrayList<>();
        // 判断简易流程是否命中
        boolean simpleMatched = isSimpleInboundFlowMatched(
                req, shared, targetPool, simpleMatchReasons, simpleUnmatchReasons);

        // 推荐优先级：白名单 > 简易 > 默认调入
        String recommendedType = whitelistMatched ? FlowType.WHITELIST_INBOUND.getCode()
                : (simpleMatched ? FlowType.SIMPLE_INBOUND.getCode() : FlowType.NORMAL_INBOUND.getCode());

        // 构建三种流程候选项返回前端，由前端根据 recommended 标识决定默认选中项
        List<AdjustCheckDto.FlowOption> options = new ArrayList<>();
        // 白名单流程
        FlowOptionParam whitelistP = new FlowOptionParam();
        whitelistP.setRecommended(FlowType.WHITELIST_INBOUND.getCode().equals(recommendedType));
        whitelistP.setMatched(whitelistMatched);
        whitelistP.setMatchReasons(whitelistMatchReasons);
        whitelistP.setUnmatchReasons(whitelistUnmatchReasons);
        // 构建白名单流程候选项
        options.add(buildWhitelistFlowOption(whitelistP));
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
        options.add(buildPoolFlowOption(simpleP));
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
     */
    private AdjustCheckDto.FlowOption resolveSpecialInboundFlowOption(InvestmentPoolBo targetPool,
                                                                      AdjustSharedData shared) {
        if (targetPool == null || shared == null
                || shared.getPoolRelationMap() == null || shared.getCurrentPoolIds() == null) {
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
        FlowDefinitionBo flow = flowMapper.queryActiveFlowByKey(FLOW_KEY_SPECIAL_INBOUND);
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
        SecurityInfoBo sec = shared.getSecurityInfo();

        // 条件1：剩余期限 ≤ 3 年
        Integer remainDays = parseRemainDays(sec.getDateNext());
        if (remainDays == null) {
            unmatchReasons.add("剩余期限无法解析，dateNext 需为 yyyyMMdd 格式");
        } else if (remainDays < 0) {
            unmatchReasons.add("剩余期限已小于 0 天");
        } else if (remainDays <= 365 * 3) {
            matchReasons.add("剩余期限为 " + formatRemainDays(remainDays) + "，未超过 3 年");
        } else {
            unmatchReasons.add("剩余期限为 " + formatRemainDays(remainDays) + "，超过 3 年");
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
        String categoryType = securityPoolAdjustMapper.queryCategoryTypeBySecurityType(sec.getSecurityType());
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
     * <p>伪代码口径：
     * 1. 目标池须为信用债大库一/二/三级库；
     * 2. 剩余期限合理（≤3 年且 ≥0）；
     * 3. 剩余期限不超过同主体在目标池已有债券的最大剩余期限；
     * 4. 该主体180天内以一般流程入过目标池；
     * 5. 主体评级和展望评级未下调，或下调时担保人评级未下调。</p>
     *
     * <p>评级下调标识当前由共享上下文初始化，后续可替换为真实评级历史查询。</p>
     */
    private boolean isSimpleInboundFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, InvestmentPoolBo targetPool,
            List<String> matchReasons, List<String> unmatchReasons) {

        if (isCreditBondLevelOneToThree(targetPool)) {
            matchReasons.add("目标池属于信用债大库一、二、三级库");
        } else {
            unmatchReasons.add("目标池不是信用债大库一、二、三级库");
        }

        Integer remainDays = parseRemainDays(shared.getSecurityInfo().getDateNext());
        if (remainDays == null) {
            unmatchReasons.add("剩余期限无法解析，dateNext 需为 yyyyMMdd 格式");
        }

        if (remainDays != null && remainDays >= 0) {
            Integer issuerPoolMaxRemainDays = securityPoolAdjustMapper.queryIssuerTargetPoolMaxRemainDays(
                    req.getSecurityCode(), targetPool.getId());
            if (issuerPoolMaxRemainDays == null) {
                matchReasons.add("目标池暂无同主体有效债券，不受在池最大期限限制");
            } else if (remainDays <= issuerPoolMaxRemainDays) {
                matchReasons.add("剩余期限为 " + formatRemainDays(remainDays)
                        + "，未超过同主体在池最大期限 " + formatRemainDays(issuerPoolMaxRemainDays));
            } else {
                unmatchReasons.add("剩余期限为 " + formatRemainDays(remainDays)
                        + "，超过同主体在池最大期限 " + formatRemainDays(issuerPoolMaxRemainDays));
            }
        }

        // 条件4：180天内该主体+目标池有非简易入库记录（简易流程前提条件，新需求180天）
        boolean hasNonSimpleInbound = securityPoolAdjustMapper.queryIssuerHasNonSimpleInboundWithinDays(
                req.getSecurityCode(), targetPool.getId(), 180);
        if (hasNonSimpleInbound) {
            matchReasons.add("该主体180天内以一般流程入过目标池，满足简易流程前提条件");
        } else {
            unmatchReasons.add("该主体180天内未以一般流程入过目标池，不满足简易流程前提条件");
        }

        boolean issuerOrOutlookDowngraded = shared.isIssuerRatingDowngraded() || shared.isOutlookRatingDowngraded();
        if (!issuerOrOutlookDowngraded) {
            matchReasons.add("主体评级和展望评级未下调");
        } else {
            if (!hasGuarantor(shared.getSecurityInfo())) {
                unmatchReasons.add("主体评级或展望评级已下调，且无担保人评级可补充判断");
            } else if (shared.isGuarantorRatingDowngraded()) {
                unmatchReasons.add("主体评级或展望评级已下调，且担保人评级也已下调");
            } else {
                matchReasons.add("主体评级或展望评级已下调，但担保人评级未下调");
            }
        }

        return unmatchReasons.isEmpty();
    }

    /**
     * 判断信用债在库调整流程是上调还是下调。
     *
     * <p>同一父级下，目标池 innerSort 小于当前所在池为升库，大于当前所在池为降库；
     * 未找到同父级当前池时返回 null，由调用方走目标池标准调入流程。</p>
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
    private boolean isCreditBondPool(InvestmentPoolBo pool) {
        if (pool == null) {
            return false;
        }
        return PoolType.CREDIT_BOND.getCode().equals(pool.getPoolType());
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
     * 解析 dateNext 到当前日期的剩余天数。
     */
    private Integer parseRemainDays(String dateNext) {
        if (dateNext == null || !dateNext.matches("\\d{8}")) {
            return null;
        }
        try {
            LocalDate nextDate = LocalDate.parse(dateNext, BASIC_DATE_FORMATTER);
            return (int) ChronoUnit.DAYS.between(LocalDate.now(), nextDate);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 格式化剩余期限展示文本。
     */
    private String formatRemainDays(Integer remainDays) {
        if (remainDays == null) {
            return "";
        }
        return remainDays + " 天（约 " + String.format("%.2f", remainDays / 365.0D) + " 年）";
    }

    /**
     * 判断证券是否存在担保人。
     */
    private boolean hasGuarantor(SecurityInfoBo securityInfo) {
        if (securityInfo == null) {
            return false;
        }
        return (securityInfo.getGuarantor() != null && !securityInfo.getGuarantor().trim().isEmpty())
                || (securityInfo.getGuarantorId() != null && !securityInfo.getGuarantorId().trim().isEmpty());
    }

    /**
     * 构建白名单流程候选项。
     */
    private AdjustCheckDto.FlowOption buildWhitelistFlowOption(FlowOptionParam p) {

        FlowDefinitionBo flow = flowMapper.queryActiveFlowByKey(FLOW_KEY_WHITELIST_INBOUND);
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
     * <p>调用方需先将 {@code configuredFlowName} 与 {@code fallbackFlowName} 决议为最终的
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

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 方向校验入口（含前置规则）
    // ═══════════════════════════════════════════════════════════

    /**
     * 调入校验：通用校验 + 类型特有校验
     *
     * <p>先执行 checkCommonIn（通用：池锁定/品种/市场/pending/重复入池/容量/来源/限制/互斥/弹性），
     * 再按 categoryType 路由类型特有校验（债券到期 checkBondIn / 股票退市 checkStockIn 等）。
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkInConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
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
        return failures;
    }

    /**
     * 通用调入校验（所有类型都走）
     *
     * <p>含池锁定、品种、市场、pending流程、重复入池、容量、来源池、限制池、互斥、弹性禁投。
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
        // 入池检查：是否满足弹性池条件
        addIfWarning(ctx.getWarnings(), inCheckElasticPool(ctx));
        // 入池检查：证券是否在全局禁止池（forbidden/blacklist）
        addIfFailed(failures, inCheckForbiddenPool(ctx));
        // 行业限制校验（按池 industry_code，调入）
        addIfFailed(failures, inCheckIndustry(ctx));
        // 开放日校验（按池 open_day_adjust，调入）
        addIfFailed(failures, inCheckOpenDay(ctx));
        return failures;
    }

    /**
     * 债券类型特有调入校验
     *
     * <p>含债券到期校验（maturity_date 早于今日则禁止调入）。
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
     * <p>调入信用债大库池时，按债券的「主体内评分档 × 期限档」查矩阵得到允许调入的池列表，
     * 目标池须在允许列表内。对应老项目 checkInPool:516-955（findMainGradeRuleList + sirmEnums sort 比对），
     * 当前项目用 credit_bond_pool_grade_rule 矩阵（req[23]）替代老项目 IP_MainGradeRule，用
     * SecurityInfoBo.innerIssuerRating 替代老项目 lon，用 termYear+credit_bond_term_bucket 替代 bondDurationId。
     * 担保债取主体与担保人评级较低者（老项目 lon=min(主体,担保人)）。可转债不适用矩阵跳过。
     * 仅调入校验（老代码 checkOutPool 无）。
     */
    private String inCheckMainGradeRule(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        // 前置门控：仅信用债大库池校验
        if (!isCreditBondPool(pool)) {
            return null;
        }
        SecurityInfoBo sec = ctx.getSecurityInfo();
        if (sec == null) {
            return null;
        }
        // 可转债不适用主体债入库矩阵
        if ("convertible_bond".equals(sec.getSecurityType())) {
            return null;
        }
        // 观察池命中时跳过主体债入库规则（对齐老系统 ASTRICTPOOLS->openrule=1）
        if (ctx.isSecurityInObservePool() || ctx.isIssuerInObservePool()) {
            return null;
        }
        // 主体内评分档
        String gradeCode = sec.getInnerIssuerRating();
        if (gradeCode == null || gradeCode.isEmpty()) {
            return "未配置主体内评分档，不符合入库条件";
        }
        // 担保债取严：主体与担保人评级较低者（sort_no 大者评级低）
        if (sec.getGuarantFlag() != null && sec.getGuarantFlag() == 1
                && sec.getInnerGuarantorRating() != null && !sec.getInnerGuarantorRating().isEmpty()) {
            gradeCode = pickLowerRatingGrade(gradeCode, sec.getInnerGuarantorRating());
        }
        // 期限档：按 termYear 匹配 credit_bond_term_bucket
        String bucketCode = matchTermBucket(sec.getTermYear());
        if (bucketCode == null) {
            // 无法判定期限档（termYear 缺失或不在任何区间），跳过
            return null;
        }
        // 查矩阵允许的池列表
        List<Long> allowedPoolIds = creditBondGradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(gradeCode, bucketCode);
        if (allowedPoolIds == null || allowedPoolIds.isEmpty()) {
            return "不符合条件，无法入库";
        }
        // 校验目标池在允许列表内
        if (!allowedPoolIds.contains(pool.getId())) {
            StringBuilder names = new StringBuilder();
            for (Long pid : allowedPoolIds) {
                InvestmentPoolBo p = ctx.getPoolMap().get(pid);
                String name = p != null ? p.getPoolName() : String.valueOf(pid);
                if (names.length() > 0) {
                    names.append("、");
                }
                names.append(name);
            }
            return "该债券只能调入以下池：" + names;
        }
        return null;
    }

    /**
     * 按证券期限（年）匹配信用债期限分组编码。
     *
     * <p>遍历启用的 credit_bond_term_bucket，按 min_term_year/max_term_year + inclusive 标志判定区间归属。
     */
    private String matchTermBucket(java.math.BigDecimal termYear) {
        if (termYear == null) {
            return null;
        }
        List<CreditBondTermBucketBo> buckets = creditBondGradeRuleMapper.queryEnabledTermBucketList();
        for (CreditBondTermBucketBo b : buckets) {
            boolean minOk = b.getMinTermYear() == null
                    || (b.getMinInclusive() != null && b.getMinInclusive() == 1
                        ? termYear.compareTo(b.getMinTermYear()) >= 0
                        : termYear.compareTo(b.getMinTermYear()) > 0);
            boolean maxOk = b.getMaxTermYear() == null
                    || (b.getMaxInclusive() != null && b.getMaxInclusive() == 1
                        ? termYear.compareTo(b.getMaxTermYear()) <= 0
                        : termYear.compareTo(b.getMaxTermYear()) < 0);
            if (minOk && maxOk) {
                return b.getBucketCode();
            }
        }
        return null;
    }

    /**
     * 取两个主体内评分档中评级较低者（sort_no 较大者）。
     *
     * <p>对应老项目担保债 lon=min(主体,担保人)。评分档未命中字典时保持主体评级档不变。
     */
    private String pickLowerRatingGrade(String gradeCode1, String gradeCode2) {
        List<CreditBondInnerRatingGradeBo> grades = creditBondGradeRuleMapper.queryEnabledRatingGradeList();
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
        // 取评级低者（sort_no 大者）；任一未命中保持主体评级档
        if (sort1 != null && sort2 != null && sort2 > sort1) {
            return gradeCode2;
        }
        return gradeCode1;
    }

    /**
     * 股票类型特有调入校验
     *
     * <p>含股票退市校验（delist_date 早于今日则禁止调入）。
     */
    private List<String> checkStockIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 股票退市校验
        addIfFailed(failures, inCheckStockDelist(ctx));
        // 股票入池评级限制（老系统 StockResearch/investrank），当前未接入股票评级来源时跳过
        addIfFailed(failures, inCheckGradeAstrict(ctx));
        return failures;
    }

    /** 基金类型特有调入校验（暂无，后续 P2 加基金评分） */
    /**
     * 基金类型特有调入校验
     *
     * <p>含基金评分校验（fund_rate_limit）：池配置评分限制表达式时，校验请求传入的 fundRate 是否满足。
     * 对应老项目 checkInPool:1092-1120。
     */
    private List<String> checkFundIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 基金评分校验（fund_rate_limit 表达式）
        addIfFailed(failures, inCheckFundRate(ctx));
        return failures;
    }

    /**
     * 规则：基金评分（fund_rate_limit）
     *
     * <p>池配置了基金评分限制表达式（如 3<=#rate<=8）时，调入校验请求传入的 fundRate 是否满足。
     * #rate 为基金评分占位。表达式支持 <=#rate / <#rate（下限）、#rate<= / #rate<（上限）及组合，
     * 各条件须同时满足。fundRate 为空或解析失败则校验失败。
     * 对应老项目 checkInPool:1092-1120。仅调入校验（老代码 checkOutPool 无）。
     */
    private String inCheckFundRate(AdjustCheckContext ctx) {
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
     * <p>先执行 checkCommonOut（通用：池锁定/pending/不在池/冻结期/限制/互斥/弹性），
     * 再按 categoryType 路由类型特有校验（债券到期 checkBondOut / 股票退市 checkStockOut 等）。
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkOutConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
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
        return failures;
    }

    /**
     * 通用调出校验（所有类型都走）
     *
     * <p>含池锁定、pending流程、不在池、冻结期、限制池、互斥、弹性禁投。
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
        // 出池检查：是否满足弹性池条件
        addIfWarning(ctx.getWarnings(), outCheckElasticPool(ctx));
        // 开放日校验（按池 open_day_adjust，调出）
        addIfFailed(failures, outCheckOpenDay(ctx));
        return failures;
    }

    /**
     * 债券类型特有调出校验
     *
     * <p>含债券到期校验（maturity_date 早于今日则禁止调出）。
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
     * <p>含股票退市校验（delist_date 早于今日则禁止调出）。
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

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 前置校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：债券到期（maturity_date）
     *
     * <p>债券到期日早于今日则禁止调入。对应老项目 checkInPool:544（ptype=4000 债券分支）。
     */
    private String inCheckBondMaturity(AdjustCheckContext ctx) {
        String maturityDate = ctx.getSecurityInfo().getMaturityDate();
        if (maturityDate != null && !maturityDate.isEmpty()) {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (maturityDate.compareTo(today) < 0) {
                return "该债券已经到期，无法调入";
            }
        }
        return null;
    }

    /**
     * 规则：股票退市（delist_date）
     *
     * <p>股票摘牌日早于今日则禁止调入。对应老项目 checkInPool:956（ptype=2000 股票分支）。
     */
    private String inCheckStockDelist(AdjustCheckContext ctx) {
        String delistDate = ctx.getSecurityInfo().getDelistDate();
        if (delistDate != null && !delistDate.isEmpty()) {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (delistDate.compareTo(today) < 0) {
                return "该股票已经退市，无法调入";
            }
        }
        return null;
    }

    /**
     * 规则：债券到期（maturity_date，调出）
     *
     * <p>债券到期日早于今日则禁止调出。对应老项目 checkOutPool 到期校验。
     */
    private String outCheckBondMaturity(AdjustCheckContext ctx) {
        String maturityDate = ctx.getSecurityInfo().getMaturityDate();
        if (maturityDate != null && !maturityDate.isEmpty()) {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (maturityDate.compareTo(today) < 0) {
                return "该债券已经到期，无法调出";
            }
        }
        return null;
    }

    /**
     * 规则：股票退市（delist_date，调出）
     *
     * <p>股票摘牌日早于今日则禁止调出。对应老项目 checkOutPool 退市校验。
     */
    private String outCheckStockDelist(AdjustCheckContext ctx) {
        String delistDate = ctx.getSecurityInfo().getDelistDate();
        if (delistDate != null && !delistDate.isEmpty()) {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (delistDate.compareTo(today) < 0) {
                return "该股票已经退市，无法调出";
            }
        }
        return null;
    }

    /**
     * 规则：证券是否存在进行中的调库流程
     *
     * <p>存在 pending 流程步骤的调库记录视为进行中，
     * 需等待该流程终结后方可再次发起调库，避免并发操作导致入池状态混乱。
     */
    private String preCheckPendingProcess(AdjustCheckContext ctx) {
        if (ctx.isHasPendingProcess()) {
            String nodeLabel = ctx.getPendingProcessNodeLabel();
            if (nodeLabel != null && !nodeLabel.trim().isEmpty()) {
                return "证券存在进行中的调库流程（当前节点：" + nodeLabel.trim() + "），请等待流程结束后再发起调库";
            }
            return "证券存在进行中的调库流程，请等待流程结束后再发起调库";
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
     * 或本次请求同时调入至少一个来源池，满足"从低级库向高级库晋升"等分层调库规则。
     * 未配置来源池时不做限制。
     */
    private String inCheckSourcePool(AdjustCheckContext ctx) {
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
     * <p>证券当前在配置的限制池中，则不允许调入目标池。
     */
    private String inCheckRestrictPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.IN_RESTRICT.getCode(), "调入限制池");
    }

    /**
     * 规则：调入弹性禁投池（in_soft_restrict）
     *
     * <p>弹性禁投池与硬性限制池的校验逻辑相同，但语义上表示"柔性"限制，
     * 业务层面可由特定角色绕过（绕过逻辑在审批流程中处理，此处只做初步拦截）。
     */
    private String inCheckElasticPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.IN_SOFT_RESTRICT.getCode(), "调入弹性禁投池");
    }

    /**
     * 规则：同一请求中同时勾选了互斥调入项
     *
     * <p>若目标池配置了互斥池（in_mutex），且本次请求中同时存在对这些互斥池的调入操作，
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
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.OUT_RESTRICT.getCode(), "调出限制池");
    }

    /**
     * 规则：调出互斥池（out_mutex）
     *
     * <p>证券当前在配置的互斥池中，则不允许从目标池调出。
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
     * <p>证券当前在配置的弹性禁投池中，则不允许从目标池调出。
     */
    private String outCheckElasticPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.OUT_SOFT_RESTRICT.getCode(), "调出弹性禁投池");
    }

    /**
     * 规则：同一请求中同时勾选了互斥调出项
     *
     * <p>若目标池配置了互斥池（in_mutex），且本次请求中同时存在对这些互斥池的调出操作，
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
     * <p>池被锁定后不可调入，属于最硬的池级拦截，优先于其他调入校验执行。
     */
    private String inCheckPoolLocked(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool != null && pool.getLockFlag() != null && pool.getLockFlag() == 1) {
            return "该池已经锁定，不能调入";
        }
        return null;
    }

    /**
     * 规则：目标池已锁定（lock_flag=1）
     *
     * <p>池被锁定后不可调出，属于最硬的池级拦截，优先于其他调出校验执行。
     */
    private String outCheckPoolLocked(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool != null && pool.getLockFlag() != null && pool.getLockFlag() == 1) {
            return "该池已经锁定，不能调出";
        }
        return null;
    }

    /**
     * 规则：调出冻结期（frozen_period_in）
     *
     * <p>目标池配置了调入冻结期天数时，证券入池后 N 天内不可调出。
     * 以 ip_pool_status.entry_time（audit_status=20）为入池时间基准，
     * entry_time + frozenPeriodIn 天 > 当前时间则视为仍在冻结期。
     */
    private String outCheckFrozenPeriod(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getFrozenPeriodIn() == null || pool.getFrozenPeriodIn() <= 0) {
            return null;
        }
        // 取证券在目标池的入池时间
        java.util.Date entryTime = ctx.getTargetPoolEntryTime();
        if (entryTime == null) {
            return null;
        }
        // 计算冻结期截止时间 = 入池时间 + N 天
        long frozenMs = pool.getFrozenPeriodIn() * 24L * 60L * 60L * 1000L;
        java.util.Date frozenDeadline = new java.util.Date(entryTime.getTime() + frozenMs);
        if (new java.util.Date().before(frozenDeadline)) {
            return "该证券还在投资池冻结期";
        }
        return null;
    }

    /**
     * 规则：投资品种（variety_codes）
     *
     * <p>目标池配置了投资品种时，证券品种（categoryType）须在配置内。
     * categoryType 由 dict_security_type 表按 securityType 查询得到（bond/fund/stock）。
     */
    private String inCheckVariety(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getVarietyCodes() == null || pool.getVarietyCodes().isEmpty()) {
            return null;
        }
        // 查证券品种大类
        String categoryType = securityPoolAdjustMapper.queryCategoryTypeBySecurityType(ctx.getSecurityInfo().getSecurityType());
        // 池配置品种为 JSON 数组（如 ["bond"]），判断是否包含证券品种
        if (categoryType == null || !pool.getVarietyCodes().contains("\"" + categoryType + "\"")) {
            return "该证券不在[" + pool.getPoolName() + "]所设定的投资品种内";
        }
        return null;
    }

    /**
     * 规则：投资市场（market_codes）
     *
     * <p>目标池配置了投资市场时，证券所在市场须至少有一个在配置内。
     * 证券市场从 windCodeSh/Sz/Nib 推导（Sh→SSE, Sz→SZSE, Nib→CIBM），
     * 多市场证券任一市场在池配置内即可。
     */
    private String inCheckMarket(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getMarketCodes() == null || pool.getMarketCodes().isEmpty()) {
            return null;
        }
        String marketCodes = pool.getMarketCodes();
        SecurityInfoBo sec = ctx.getSecurityInfo();
        // 推导证券所在市场，任一在池配置内即可
        boolean anyMatch = (sec.getWindCodeSh() != null && !sec.getWindCodeSh().isEmpty() && marketCodes.contains("\"SSE\""))
                || (sec.getWindCodeSz() != null && !sec.getWindCodeSz().isEmpty() && marketCodes.contains("\"SZSE\""))
                || (sec.getWindCodeNib() != null && !sec.getWindCodeNib().isEmpty() && marketCodes.contains("\"CIBM\""));
        if (!anyMatch) {
            return "该证券不在[" + pool.getPoolName() + "]所设定的投资市场内";
        }
        return null;
    }

    /**
     * 规则：全局禁止池（forbidden/blacklist）
     *
     * <p>证券当前在禁投池或黑名单中则不能调入任何其他池（全局禁止，区别于池间 in_restrict）。
     * 对应老项目 Forbiddenlastpoolid 配置。
     */
    private String inCheckForbiddenPool(AdjustCheckContext ctx) {
        if (securityPoolAdjustMapper.querySecurityInForbiddenPool(ctx.getSecurityInfo().getWindCode())) {
            return "该证券在禁止池中，不能调入";
        }
        return null;
    }

    /**
     * 规则：股票入池评级限制（grade_astrict）
     *
     * <p>老系统该字段对应“股票入池评级限制”，评级来源为 StockResearch/investrank
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
     * <p>目标池配置了行业限制时，调入校验证券行业是否匹配：
     * 池 industry_code 非空且 industry_exponent=0（非行业指数模式）时，证券 industry_name 须等于池配置值。
     * 对应老项目 checkInPool:485（industryPartition.industrycode 比对 pool.IndustryCode），
     * 当前项目无行业编码主数据，暂用 SecurityInfoBo.industryName 名称精确匹配（老项目为编码前缀层级匹配）。
     * 仅调入校验（老代码 checkOutPool 无）。
     */
    private String inCheckIndustry(AdjustCheckContext ctx) {
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
            return "请选择正确的行业;";
        }
        return null;
    }

    /**
     * 规则：开放日（open_day_adjust，调入侧）
     *
     * <p>目标池启用开放日校验（open_day_adjust=1）时，调入校验当日是否落在 ip_pool_open_day 的开放区间内。
     * 对应老项目 checkInPool:479（isInPoolOpenDay）。
     */
    private String inCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        String today = java.time.LocalDate.now().toString();
        if (!securityPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
            return "不在开放日内，不能调入;";
        }
        return null;
    }

    /**
     * 规则：开放日（open_day_adjust，调出侧）
     *
     * <p>目标池启用开放日校验时，调出校验当日是否落在开放区间内。对应老项目 checkOutPool:139。
     */
    private String outCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        String today = java.time.LocalDate.now().toString();
        if (!securityPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
            return "不在开放日内，不能调出;";
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
        InvestmentPoolBo targetPool = shared.getPoolMap().get(item.getTargetPoolId());
        InvestmentPoolBo parentPool = (targetPool != null && targetPool.getParentId() != null)
                ? shared.getPoolMap().get(targetPool.getParentId()) : null;

        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(shared.getSecurityInfo());
        ctx.setTargetPool(targetPool);
        ctx.setParentPool(parentPool);
        ctx.setCurrentPoolIds(shared.getCurrentPoolIds());
        ctx.setPoolCurrentCount(poolCurrentCount);
        ctx.setAdjustMode(item.getAdjustMode());
        // 取出目标池自身的所有关系配置（来源池、限制池、联动池等），供关系型校验规则直接使用
        ctx.setTargetPoolRelations(shared.getPoolRelationMap().getOrDefault(item.getTargetPoolId(), Collections.emptyMap()));
        ctx.setPoolMap(shared.getPoolMap());
        ctx.setHasPendingProcess(shared.isHasPendingProcess());
        ctx.setPendingProcessNodeLabel(shared.getPendingProcessNodeLabel());
        ctx.setSecurityInObservePool(shared.isSecurityInObservePool());
        ctx.setIssuerInObservePool(shared.isIssuerInObservePool());
        ctx.setRequestInPoolIds(shared.getRequestInPoolIds());
        ctx.setRequestOutPoolIds(shared.getRequestOutPoolIds());
        // 调出时查询证券在目标池的入池时间，用于冻结期校验（调入时不需要）
        if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
            ctx.setTargetPoolEntryTime(securityPoolAdjustMapper.queryPoolEntryTime(
                    shared.getSecurityInfo().getWindCode(), item.getTargetPoolId()));
        }
        // 查证券品种大类，用于类型特有校验路由（bond/fund/stock/company）
        ctx.setCategoryType(securityPoolAdjustMapper.queryCategoryTypeBySecurityType(
                shared.getSecurityInfo().getSecurityType()));
        // 基金评分（基金证券调入校验用，透传请求级 fundRate）
        ctx.setFundRate(shared.getFundRate());
        return ctx;
    }

    /**
     * 构建自动生成的联动/互斥调整项校验结果
     *
     * <p>与手动项校验流程完全相同，区别在于：
     * <ul>
     *   <li>targetPoolId 来自池关系配置，由系统自动推导，非用户选择</li>
     *   <li>itemTag 标记为ItemType.LINKAGE.getCode()或ItemType.MUTEX.getCode()，前端据此区分显示样式</li>
     * </ul>
     *
     * @param targetPoolId 自动生成项的目标池 ID
     * @param adjustMode   调整方向（AdjustMode.IN.getCode()或AdjustMode.OUT.getCode()）
     * @param itemTag      来源标签：linkage（联动）/ mutex（互斥）
     * @param adjustGroupKey 触发该自动项的手工调库分组 Key
     * @param shared       本次 checkAdjust 调用的共享数据
     */
    private AdjustCheckDto.CheckResultItem buildAutoResultItem(
            Long targetPoolId, String adjustMode, String itemTag, String adjustGroupKey, AdjustSharedData shared) {

        int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(targetPoolId);
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

    // ═══════════════════════════════════════════════════════════
    //  流程步骤记录（ip_adjust_step）
    // ═══════════════════════════════════════════════════════════

    /**
     * 为新建的调库记录创建初始流程步骤（懒创建）
     *
     * <p>仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理），
     * 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）。
     *
     * @param adjustLogId  调库记录 ID
     * @param snapshot     流程快照
     * @param adjusterId   提交人 ID
     * @param adjusterName 提交人名称
     * @return true 表示初始步骤已走到结束节点，false 表示仍需后续人工处理
     */
    private boolean createInitialSteps(Long adjustLogId, String adjustBatchNo, FlowSnapshot snapshot,
                                       String adjusterId, String adjusterName) {
        if (snapshot == null) {
            return false;
        }

        Date now = new Date();

        // 查找开始节点
        FlowNodeBo startNode = findNodeByType(snapshot, NodeType.START.getCode());
        if (startNode == null) {
            return false;
        }

        // 1. 创建开始节点步骤（auto_process）
        int sortOrder = startNode.getSortOrder() != null ? startNode.getSortOrder() : 1;
        // 插入单条步骤记录到 ip_adjust_step
        insertStepRecord(adjustLogId, adjustBatchNo, startNode, null, sortOrder, ProcessAction.AUTO_PROCESS.getCode(),
                         null, null, ProcessAction.AUTO_PROCESS.getCode(), null, now);

        FlowNodeBo prevNode = startNode;
        // 查找初始步骤的下一个节点
        FlowNodeBo currentNode = findNextNodeForInitialSteps(snapshot, startNode, null);
        while (currentNode != null) {
            NodeApprovalConfigBo config = snapshot.approvalConfigMap.get(currentNode.getId());
            if (NodeType.APPROVAL.getCode().equals(currentNode.getNodeType())
                    // 判断当前审批节点是否应由流程发起人自动完成
                    && isInitiatorStep(snapshot, currentNode, config, prevNode, startNode)) {
                sortOrder = currentNode.getSortOrder() != null ? currentNode.getSortOrder() : 1;
                // 插入单条步骤记录到 ip_adjust_step
                insertStepRecord(adjustLogId, adjustBatchNo, currentNode, config, sortOrder, ProcessAction.SUBMIT.getCode(),
                                 adjusterId, adjusterName, ProcessAction.SUBMIT.getCode(), null, now);
                // 查找初始步骤的下一个节点
                FlowNodeBo nextNode = findNextNodeForInitialSteps(snapshot, currentNode, prevNode);
                prevNode = currentNode;
                currentNode = nextNode;
                continue;
            }

            if (NodeType.APPROVAL.getCode().equals(currentNode.getNodeType())) {
                // 为审批节点创建待处理步骤记录（按处理人明细展开为具体人员）
                createPendingSteps(adjustLogId, adjustBatchNo, currentNode, snapshot, now);
                return false;
            }

            if (NodeType.END.getCode().equals(currentNode.getNodeType())) {
                sortOrder = currentNode.getSortOrder() != null ? currentNode.getSortOrder() : 1;
                // 插入单条步骤记录到 ip_adjust_step
                insertStepRecord(adjustLogId, adjustBatchNo, currentNode, config, sortOrder, ProcessAction.AUTO_PROCESS.getCode(),
                                 null, null, ProcessAction.AUTO_PROCESS.getCode(), null, now);
                return true;
            }

            // 查找初始步骤的下一个节点
            FlowNodeBo nextNode = findNextNodeForInitialSteps(snapshot, currentNode, prevNode);
            prevNode = currentNode;
            currentNode = nextNode;
        }
        return false;
    }

    /**
     * 判断当前审批节点是否应由流程发起人自动完成。
     */
    private boolean isInitiatorStep(FlowSnapshot snapshot, FlowNodeBo node, NodeApprovalConfigBo config,
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
     * 为审批节点创建待处理步骤记录（按处理人明细展开为具体人员）
     */
    private void createPendingSteps(Long adjustLogId, String adjustBatchNo, FlowNodeBo node,
                                    FlowSnapshot snapshot, Date now) {
        NodeApprovalConfigBo config = snapshot.approvalConfigMap.get(node.getId());
        // 将审批处理人配置解析为具体人员
        List<HandlerTarget> handlers = resolveApprovalHandlers(config, snapshot);
        int sortOrder = node.getSortOrder() != null ? node.getSortOrder() : 1;

        if (handlers.isEmpty()) {
            // 无配置处理人时仍创建一条空处理人的待处理记录
            insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                             null, null, null, null, now);
        } else {
            for (HandlerTarget handler : handlers) {
                // 插入单条步骤记录到 ip_adjust_step
                insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                                 handler.handlerId, handler.handlerName, null, null, now);
            }
        }
    }

    /**
     * 插入单条步骤记录到 ip_adjust_step
     */
    private void insertStepRecord(Long adjustLogId, String adjustBatchNo, FlowNodeBo node,
                                  NodeApprovalConfigBo config, int sortOrder,
                                  String stepStatus, String handlerId, String handlerName,
                                  String processAction, String processComment, Date startTime) {
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setAdjustLogId(adjustLogId);
        step.setAdjustBatchNo(adjustBatchNo);
        step.setFlowNodeId(node.getId());
        step.setNodeCode(node.getNodeId());
        step.setNodeLabel(node.getLabel());
        step.setNodeType(node.getNodeType());
        step.setApprovalStrategy(config != null ? config.getApprovalStrategy() : null);
        step.setSortOrder(sortOrder);
        step.setStepStatus(stepStatus);
        step.setHandlerId(handlerId);
        step.setHandlerName(handlerName);
        step.setProcessAction(processAction);
        step.setProcessComment(processComment);
        step.setStartTime(startTime);
        step.setProcessTime(StepStatus.PENDING.getCode().equals(stepStatus) ? null : startTime);
        securityPoolAdjustMapper.addAdjustStep(step);
    }

    /**
     * 按节点类型查找节点
     */
    private FlowNodeBo findNodeByType(FlowSnapshot snapshot, String nodeType) {
        for (FlowNodeBo node : snapshot.nodeMap.values()) {
            if (nodeType.equals(node.getNodeType())) {
                return node;
            }
        }
        return null;
    }

    /**
     * 沿流程主路径查找下一节点
     *
     * <p>从当前节点出发，遍历所有出边（连线），排除指向已访问节点的边，
     * 优先选择非 reject 的主路径连线，
     * 返回目标节点。
     *
     * @param snapshot   流程快照
     * @param currentNode 当前节点
     * @param prevNode   前一个节点（用于排除回退边），可为 null
     * @return 下一节点，无出边时返回 null
     */
    private FlowNodeBo findNextNodeOnMainPath(FlowSnapshot snapshot, FlowNodeBo currentNode, FlowNodeBo prevNode) {
        // 收集当前节点所有的出边
        List<FlowEdgeBo> outEdges = new ArrayList<>();
        for (FlowEdgeBo edge : snapshot.edges) {
            if (edge.getFromNodeId().equals(currentNode.getId())) {
                outEdges.add(edge);
            }
        }

        if (outEdges.isEmpty()) {
            return null;
        }

        // 排除指向已访问节点的边（防止走上驳回回路）
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

        // 优先选择非 reject 的主路径出边
        for (FlowEdgeBo edge : outEdges) {
            if (!ProcessAction.REJECT.getCode().equals(edge.getRouteAction())) {
                return snapshot.nodeMap.get(edge.getToNodeId());
            }
        }

        // 兜底：返回第一条出边的目标节点
        FlowEdgeBo firstEdge = outEdges.get(0);
        return snapshot.nodeMap.get(firstEdge.getToNodeId());
    }

    /**
     * 查找初始步骤的下一个节点。
     */
    private FlowNodeBo findNextNodeForInitialSteps(FlowSnapshot snapshot, FlowNodeBo currentNode, FlowNodeBo prevNode) {
        // 沿流程主路径查找下一节点
        FlowNodeBo nextNode = findNextNodeOnMainPath(snapshot, currentNode, prevNode);
        if (nextNode != null) {
            return nextNode;
        }
        throw new BizException("流程配置异常：节点[" + currentNode.getLabel() + "]缺少下一步连线");
    }

    /**
     * 将审批处理人配置解析为具体人员，角色会递归展开到子角色下的人员并去重。
     */
    private List<HandlerTarget> resolveApprovalHandlers(NodeApprovalConfigBo config, FlowSnapshot snapshot) {
        if (config == null || config.getId() == null) {
            return Collections.emptyList();
        }
        List<NodeApprovalHandlerBo> handlers = snapshot.approvalHandlerMap.get(config.getId());
        if (handlers == null || handlers.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, HandlerTarget> resultMap = new LinkedHashMap<>();
        for (NodeApprovalHandlerBo handler : handlers) {
            if (handler == null || handler.getHandlerType() == null || handler.getHandlerId() == null) {
                continue;
            }
            if ("user".equals(handler.getHandlerType())) {
                String userId = String.valueOf(handler.getHandlerId());
                resultMap.put(userId, new HandlerTarget(userId, handler.getHandlerName()));
            } else if ("role".equals(handler.getHandlerType())) {
                List<Long> roleIds = new ArrayList<>();
                // 递归收集角色及其子角色 ID
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
        for (RoleBo role : allRoles) {
            if (roleId.equals(role.getParentId())) {
                // 递归收集角色及其子角色 ID
                collectDescendantRoleIds(role.getId(), roleIds, allRoles);
            }
        }
    }

    /**
     * 构建调库记录实体（ip_adjust_log），由提交申请逻辑调用
     *
     * @param req  调库申请（含证券级别信息）
     * @param item 单个调库项（含目标池、调整方向等）
     * @param flowSource 同组手工调库项，用于写入流程快照
     */
    private IpAdjustLogBo buildAdjustLog(SecurityPoolAdjustSubmitReq req,
                                         SecurityPoolAdjustSubmitReq.AdjustItem item,
                                         SecurityPoolAdjustSubmitReq.AdjustItem flowSource) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        bo.setSecurityCode(req.getSecurityCode());
        bo.setSecurityShortName(req.getSecurityShortName());
        bo.setSecurityType(req.getSecurityType());
        bo.setCrmwName(req.getCrmwName());
        bo.setCrmwScode(req.getCrmwScode());
        bo.setCrmwMktcode(req.getCrmwMktcode());
        bo.setCrmwStype(req.getCrmwStype());
        // 根据调库项来源确定落表调整类型
        bo.setAdjustType(resolveAdjustType(req, item));
        bo.setAdjustMode(item.getAdjustMode());
        bo.setTargetPoolId(item.getTargetPoolId());
        bo.setTargetPoolName(item.getTargetPoolName());
        bo.setPoolType(item.getPoolType());
        bo.setFlowId(flowSource != null ? flowSource.getFlowId() : item.getFlowId());
        bo.setFlowKey(flowSource != null ? flowSource.getFlowKey() : item.getFlowKey());
        bo.setFlowType(flowSource != null ? flowSource.getFlowType() : item.getFlowType());
        bo.setAuditStatus(AuditStatus.SUBMITTED.getCode());  // 初始状态：流程中
        bo.setAdjusterId(req.getAdjusterId());
        bo.setAdjusterName(req.getAdjusterName());
        bo.setAdjustReason(req.getAdjustReason());
        bo.setAdjustAdvice(req.getAdjustAdvice());
        return bo;
    }

    /**
     * 将调库项携带的提交附件绑定到新建调库日志。
     */
    private void bindSubmitAttachments(Long adjustLogId, SecurityPoolAdjustSubmitReq.AdjustItem item,
                                       SysAttachmentService.SubmissionFiles submissionFiles, String uploaderId) {
        // 绑定信评报告附件
        sysAttachmentService.bindAttachments(adjustLogId, item.getCreditReportFileIndexes(),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), submissionFiles);
        // 绑定其他材料附件
        sysAttachmentService.bindAttachments(adjustLogId, item.getMaterialFileIndexes(),
                AttachmentCategory.MATERIAL_HAND.getCode(), submissionFiles);
        // 复制报告库附件为信评报告附件
        sysAttachmentService.copyReportAttachments(adjustLogId, item.getCreditReportSourceAttachmentIds(),
                AttachmentPurpose.CREDIT_REPORT.getCode(), uploaderId);
        // 复制报告库附件为其他材料附件
        sysAttachmentService.copyReportAttachments(adjustLogId, item.getMaterialSourceAttachmentIds(),
                AttachmentPurpose.MATERIAL.getCode(), uploaderId);
    }

    /**
     * 根据调库项来源确定落表调整类型。
     */
    private String resolveAdjustType(SecurityPoolAdjustSubmitReq req, SecurityPoolAdjustSubmitReq.AdjustItem item) {
        if (ItemType.MUTEX.getCode().equals(item.getItemTag())) {
            return "互斥调整";
        }
        if (ItemType.LINKAGE.getCode().equals(item.getItemTag())) {
            return "联动调整";
        }
        return req.getAdjustType();
    }

    /** 构建投资池全路径名称 */
    private String buildPoolPath(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
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

    // ═══════════════════════════════════════════════════════════
    //  内部数据类
    // ═══════════════════════════════════════════════════════════

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

    /**
     * 待处理步骤的具体处理人。
     */
    private static class HandlerTarget {
        final String handlerId;
        final String handlerName;

        HandlerTarget(String handlerId, String handlerName) {
            this.handlerId = handlerId;
            this.handlerName = handlerName;
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

        /** 证券基础信息（来自 rrs_securityinfo） */
        final SecurityInfoBo securityInfo;

        /** 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称 */
        final Map<Long, InvestmentPoolBo> poolMap;

        /** 证券当前有效所在池 ID 集合（ip_pool_status.audit_status='20'） */
        final Set<Long> currentPoolIds;

        /** 全量投资池关系配置（poolId → relationType → 关联池 ID 列表） */
        final Map<Long, Map<String, List<Long>>> poolRelationMap;

        /** 证券是否存在进行中的调库流程（以是否存在待处理步骤为准） */
        final boolean hasPendingProcess;

        /** 当前证券自身是否在观察池（pool_type='observe'，audit_status='20'） */
        final boolean securityInObservePool;

        /** 证券主体公司（发行人）旗下是否有证券在观察池中 */
        final boolean issuerInObservePool;

        /** 流程快照索引（flowId → FlowSnapshot），供第三/四阶段快速判断直通流程 */
        final Map<Long, FlowSnapshot> flowSnapshotMap;

        /** 批次号生成上下文 */
        final BatchNoContext batchNoContext;

        /** 调库分组批次号索引（adjustGroupKey → adjustBatchNo），用于联动/互斥记录复用 */
        final Map<String, String> adjustBatchNoMap = new HashMap<>();

        SubmitSharedData(SecurityInfoBo securityInfo,
                         Map<Long, InvestmentPoolBo> poolMap,
                         Set<Long> currentPoolIds,
                         Map<Long, Map<String, List<Long>>> poolRelationMap,
                         boolean hasPendingProcess,
                         boolean securityInObservePool,
                         boolean issuerInObservePool,
                         Map<Long, FlowSnapshot> flowSnapshotMap,
                         BatchNoContext batchNoContext) {
            this.securityInfo = securityInfo;
            this.poolMap = poolMap;
            this.currentPoolIds = currentPoolIds;
            this.poolRelationMap = poolRelationMap;
            this.hasPendingProcess = hasPendingProcess;
            this.securityInObservePool = securityInObservePool;
            this.issuerInObservePool = issuerInObservePool;
            this.flowSnapshotMap = flowSnapshotMap;
            this.batchNoContext = batchNoContext;
        }
    }

    /**
     * 批次号生成上下文。
     */
    static class BatchNoContext {

        /** 本次提交批次号时间片 */
        final String batchTimeText = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        /** 调入方向批次序号 */
        int inboundBatchSeq = 0;

        /** 调出方向批次序号 */
        int outboundBatchSeq = 0;

        /** 无流程批次序号 */
        int noFlowBatchSeq = 0;
    }


    /**
     * 校验当前用户是否拥有目标池调整权限
     */
    private void validatePoolPermission(BatchSecurityPoolAdjustReq req) {
        // 判断当前用户是否为管理员
        if (isAdminUser(req.getCurrentUserId())) {
            return;
        }
        // 查询当前用户拥有调整权限的投资池
        if (!queryAdjustablePoolIds(req.getCurrentUserId()).contains(req.getPoolId())) {
            throw new BizException("当前用户无权调整目标投资池");
        }
    }

    /**
     * 处理当前用户可调整投资池筛选条件
     *
     * @return 是否存在可查询的投资池
     */
    private boolean prepareAdjustablePoolIds(BatchSecurityPoolAdjustReq req) {
        // 判断当前用户是否为管理员
        if (isAdminUser(req.getCurrentUserId())) {
            return true;
        }
        // 查询当前用户拥有调整权限的投资池
        Set<Long> adjustablePoolIds = queryAdjustablePoolIds(req.getCurrentUserId());
        if (adjustablePoolIds.isEmpty()) {
            return false;
        }
        if (req.getPoolIds() == null || req.getPoolIds().isEmpty()) {
            req.setPoolIds(new ArrayList<>(adjustablePoolIds));
            return true;
        }
        List<Long> permittedPoolIds = req.getPoolIds().stream()
                .filter(adjustablePoolIds::contains)
                .collect(Collectors.toList());
        if (permittedPoolIds.isEmpty()) {
            return false;
        }
        req.setPoolIds(permittedPoolIds);
        return true;
    }

    /**
     * 查询当前用户直接或通过角色拥有调整权限的投资池 ID
     */
    private Set<Long> queryAdjustablePoolIds(String currentUserId) {
        // 解析当前用户 ID
        Long userId = parseCurrentUserId(currentUserId);
        List<Long> roleIds = investmentPoolMapper.queryUserRoleIdList(userId);
        Set<Long> roleIdSet = new HashSet<>(roleIds);
        List<PoolPermissionBo> permissions =
                investmentPoolMapper.queryPermissionListByType(PermissionType.ADJUSTABLE.getCode());
        Set<Long> poolIds = new HashSet<>();
        for (PoolPermissionBo permission : permissions) {
            if (permission.getPoolId() == null || permission.getHandlerId() == null) {
                continue;
            }
            if (HandlerType.USER.getCode().equals(permission.getHandlerType()) && permission.getHandlerId().equals(userId)) {
                poolIds.add(permission.getPoolId());
            } else if (HandlerType.ROLE.getCode().equals(permission.getHandlerType())
                    && roleIdSet.contains(permission.getHandlerId())) {
                poolIds.add(permission.getPoolId());
            }
        }
        return poolIds;
    }

    /**
     * 解析当前用户 ID
     */
    private Long parseCurrentUserId(String currentUserId) {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            throw new BizException("当前用户 ID 不能为空");
        }
        try {
            return Long.valueOf(currentUserId.trim());
        } catch (NumberFormatException e) {
            throw new BizException("当前用户 ID 不合法");
        }
    }

    /**
     * 判断当前用户是否为管理员
     */
    private boolean isAdminUser(String currentUserId) {
        return ADMIN_USER_ID.equals(currentUserId);
    }

    /**
     * 填充市场编码列表
     */
    private void fillMarketCodes(List<BatchSecurityCandidateDto> list) {
        for (BatchSecurityCandidateDto dto : list) {
            if (dto.getMarketCodeText() == null || dto.getMarketCodeText().isEmpty()) {
                dto.setMarketCodes(new ArrayList<>());
            } else {
                dto.setMarketCodes(Arrays.asList(dto.getMarketCodeText().split(",")));
            }
        }
    }

    /**
     * 填充当前页投资池现有证券数量
     */
    private void fillPoolCurrentCount(List<BatchSecurityPoolDto> poolList) {
        if (poolList.isEmpty()) {
            return;
        }
        List<Long> poolIds = poolList.stream()
                .map(BatchSecurityPoolDto::getId)
                .collect(Collectors.toList());
        List<BatchSecurityPoolDto> countList =
                batchSecurityPoolAdjustMapper.queryPoolCurrentCountList(poolIds);
        Map<Long, Integer> currentCountMap = countList.stream()
                .collect(Collectors.toMap(
                        BatchSecurityPoolDto::getId, BatchSecurityPoolDto::getCurrentCount));
        for (BatchSecurityPoolDto pool : poolList) {
            pool.setCurrentCount(currentCountMap.getOrDefault(pool.getId(), 0));
        }
    }

    /**
     * 填充投资池全路径名称
     */
    private void fillPoolFullName(List<BatchSecurityPoolDto> poolList) {
        if (poolList.isEmpty()) {
            return;
        }
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools.isEmpty()) {
            return;
        }
        Map<Long, InvestmentPoolBo> poolMap = allPools.stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, Function.identity()));
        for (BatchSecurityPoolDto dto : poolList) {
            // 构建投资池全路径名称
            dto.setPoolFullName(buildPoolFullName(dto.getId(), poolMap));
        }
    }

    /**
     * 构建投资池全路径名称
     */
    private String buildPoolFullName(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        InvestmentPoolBo pool = poolMap.get(poolId);
        if (pool == null) {
            return "";
        }
        String poolName = pool.getPoolName() == null ? "" : pool.getPoolName();
        if (pool.getParentId() == null) {
            return poolName;
        }
        // 构建父级投资池全路径
        String parentName = buildPoolFullName(pool.getParentId(), poolMap);
        return parentName == null || parentName.isEmpty() ? poolName : parentName + "/" + poolName;
    }
}
