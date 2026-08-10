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
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.enums.HandlerType;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.StockSecurityBatchAdjustMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockPoolTypeCountDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchCandidateDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustSubmitReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchPoolDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSourcePoolDto;
import com.znty.rrs.entity.investmentpool.InvestmentPoolDto;
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
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 存量证券批量调整业务服务
 */
@Service
public class StockSecurityBatchAdjustService {

    /** 短时间重复提交判定窗口（秒） */
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;

    /** 白名单池 ID 集合：主体在这些池中时符合白名单条件；当前写死空集，后续配置后补 queryIssuerInWhitelistPools 查询 */
    private static final Set<Long> WHITELIST_POOL_IDS = Collections.emptySet();
    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";

    /**
     * 来源池白名单 pool_code（有序，与前端下拉一致）。
     * crmw_root 走 ip_pool_status_crmw，其余走 ip_pool_status。
     * 目标池范围由 Mapper 限定 pool_code=bond_product_root 子树叶子。
     */
    private static final List<String> SOURCE_POOL_CODES = Collections.unmodifiableList(Arrays.asList(
            "crmw_root",
            "credit_bond_level_1",
            "credit_bond_level_2",
            "credit_bond_level_3",
            "convertible_bond_core",
            "convertible_bond_focus"));

    /** CRMW 来源池编码 */
    private static final String SOURCE_POOL_CODE_CRMW = "crmw_root";


    /** 存量证券批量调整数据访问组件 */
    @Resource
    private StockSecurityBatchAdjustMapper stockSecurityBatchAdjustMapper;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 证券池调库数据访问组件 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 流程定义数据访问组件 */
    @Resource
    private FlowMapper flowMapper;

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** 证券池调整服务，校验委托其 checkAdjust（与证券池批量调整一致） */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /**
     * 分页查询当前用户可调整的启用叶子投资池
     */
    public PageResult<StockSecurityBatchPoolDto> queryPoolPage(StockSecurityBatchAdjustReq req) {
        // 处理当前用户可调整投资池筛选条件
        if (!prepareAdjustablePoolIds(req)) {
            return new PageResult<>(
                    new ArrayList<>(), 0L, req.getPageIndex(), req.getPageSize());
        }

        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<StockSecurityBatchPoolDto> poolList = stockSecurityBatchAdjustMapper.queryPoolPage(req);
        PageInfo<StockSecurityBatchPoolDto> pageInfo = new PageInfo<>(poolList);
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
    /**
     * 查询来源池下拉列表（固定白名单，按 pool_code 解析当前环境 id）。
     */
    public List<StockSourcePoolDto> querySourcePoolList() {
        Map<String, InvestmentPoolBo> byCode = resolveSourcePoolMap();
        List<StockSourcePoolDto> result = new ArrayList<>();
        for (String code : SOURCE_POOL_CODES) {
            InvestmentPoolBo pool = byCode.get(code);
            if (pool == null) {
                continue;
            }
            StockSourcePoolDto dto = new StockSourcePoolDto();
            dto.setId(pool.getId());
            dto.setPoolCode(pool.getPoolCode());
            dto.setPoolName(pool.getPoolName());
            result.add(dto);
        }
        if (result.size() != SOURCE_POOL_CODES.size()) {
            throw new BizException("存量来源池配置不完整，请检查 pool_code：" + SOURCE_POOL_CODES);
        }
        fillSourcePoolFullName(result);
        return result;
    }

    /**
     * 分页查询目标池批量调整候选证券（须选来源池 + 可选发行主体）。
     */
    public PageResult<StockSecurityBatchCandidateDto> querySecurityPage(StockSecurityBatchAdjustReq req) {
        // 校验候选证券查询参数（含来源池必选与白名单）
        validateSecurityPageReq(req);
        // 拆分普通来源 / CRMW 来源供 Mapper 使用
        prepareSourcePoolIds(req);
        // 校验目标投资池调整权限
        validatePoolPermission(req);

        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<StockSecurityBatchCandidateDto> list = stockSecurityBatchAdjustMapper.querySecurityPage(req);
        PageInfo<StockSecurityBatchCandidateDto> pageInfo = new PageInfo<>(list);

        // 将 SQL 返回的市场文本转换为市场编码列表
        fillMarketCodes(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 批量调库下一步校验
     */
    public StockSecurityBatchAdjustDto checkAdjust(StockSecurityBatchAdjustSubmitReq req) {
        // 校验批量调库下一步参数
        validateAdjustCheckReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());

        StockSecurityBatchAdjustDto dto = new StockSecurityBatchAdjustDto();
        for (StockSecurityBatchAdjustSubmitReq.SecurityItem security : req.getSecurities()) {
            // 构建单证券调库校验请求
            AdjustCheckDto checkDto = checkSingleAdjust(buildSingleCheckReq(req, security));
            if (checkDto == null || checkDto.getItems() == null) {
                continue;
            }
            for (AdjustCheckDto.CheckResultItem item : checkDto.getItems()) {
                // 构建批量调库校验结果（透传单笔流程候选）
                dto.getItems().add(buildBatchCheckResult(security, item));
            }
        }
        return dto;
    }

    /**
     * 提交侧流程字段兜底：仅给「完全未带流程」的明细补目标池批量流程。
     *
     * <p><b>会补</b>：item 的 flowId 为空，且 flowKey 也为空/空白时，按本次方向写入目标池
     * batchIn（调入）或 batchOut（调出）的 id/key，并打上 batchInbound/batchOutbound。
     * 池未配置批量流程时 id/key 仍为 null，语义上等同「无审批、直通」。
     *
     * <p><b>不会改</b>：只要 flowId 或 flowKey 任一已有值（页面默认选中的批量流程、操作员改选的
     * 白名单/简易/升降级等单券候选、或只带了 key），一律保留，不做强制覆盖。
     *
     * <p><b>不是</b>校验阶段的流程推荐（推荐在 checkAdjust → injectBatchFlowOption）；
     * 本方法只处理提交 payload 漏传流程的兜底，并保证后续防重复、直通预检看到的流程字段已定稿。
     *
     * @param req  批量提交请求（会就地改写 items 中缺流程的明细）
     * @param pool 本次批量操作的目标投资池（取其 batch_in / batch_out 配置）
     */
    private void fillDefaultBatchFlowIfMissing(StockSecurityBatchAdjustSubmitReq req, InvestmentPoolBo pool) {
        boolean outbound = "out".equals(req.getDirection());
        Long flowId = outbound ? pool.getBatchOutFlowId() : pool.getBatchInFlowId();
        String flowKey = outbound ? pool.getBatchOutFlowKey() : pool.getBatchInFlowKey();
        String flowType = outbound ? FlowType.BATCH_OUTBOUND.getCode() : FlowType.BATCH_INBOUND.getCode();
        for (StockSecurityBatchAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 仅 flowId、flowKey 同时缺失时才补；任一已有则视为前端/调用方已选定
            boolean missingId = item.getFlowId() == null;
            boolean missingKey = item.getFlowKey() == null || item.getFlowKey().isEmpty();
            if (!missingId || !missingKey) {
                continue;
            }
            item.setFlowId(flowId);
            item.setFlowKey(flowKey);
            item.setFlowType(flowType);
        }
    }

    /**
     * 按提交明细实际选中的流程判断是否存在直通项；存在时落池前统一锁池复核整批状态。
     */
    private boolean needsWholeBatchDirectRecheck(StockSecurityBatchAdjustSubmitReq req) {
        if (req.getItems() == null) {
            return false;
        }
        for (StockSecurityBatchAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!isManualBatchSubmitItem(item)) {
                continue;
            }
            Long flowId = item.getFlowId();
            String flowKey = item.getFlowKey();
            if (flowId == null && (flowKey == null || flowKey.isEmpty())) {
                return true;
            }
            if (flowId == null) {
                FlowDefinitionBo definition = flowMapper.queryActiveFlowByKey(flowKey);
                flowId = definition != null ? definition.getId() : null;
            }
            if (flowId == null) {
                // 配置了 key 但解析不到定义时，按非直通走后续提交校验
                continue;
            }
            FlowSnapshot snapshot = buildFlowSnapshot(flowId);
            if (snapshot != null && isDirectFlow(snapshot)) {
                return true;
            }
        }
        return false;
    }

    /** 将批量请求转换为最终落池复核所需的日志快照。 */
    private List<IpAdjustLogBo> buildDirectRecheckLogList(StockSecurityBatchAdjustSubmitReq req) {
        List<IpAdjustLogBo> logs = new ArrayList<>();
        for (StockSecurityBatchAdjustSubmitReq.AdjustItem item : req.getItems()) {
            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setSecurityCode(item.getSecurityCode());
            log.setSecurityShortName(item.getSecurityShortName());
            log.setSecurityType(item.getSecurityType());
            log.setAdjustMode(item.getAdjustMode());
            log.setTargetPoolId(item.getTargetPoolId());
            log.setTargetPoolName(item.getTargetPoolName());
            logs.add(log);
        }
        return logs;
    }

    /** 按完整证券集合及手工调库项集合检查最近一次批量申请。 */
    private void checkRecentBatchDuplicateSubmit(StockSecurityBatchAdjustSubmitReq req) {
        List<IpAdjustLogBo> recent = securityPoolAdjustMapper.queryRecentBatchManualAdjustLogList(
                req.getAdjusterId(), DUPLICATE_SUBMIT_WINDOW_SECONDS);
        if (recent == null || recent.isEmpty()) {
            return;
        }
        List<String> requestKeys = req.getItems().stream().filter(this::isManualBatchSubmitItem)
                .map(this::buildBatchDuplicateItemKey).sorted().collect(Collectors.toList());
        List<String> historyKeys = recent.stream().map(this::buildBatchDuplicateItemKey)
                .sorted().collect(Collectors.toList());
        boolean sameText = recent.stream().allMatch(log ->
                Objects.equals(normalizeDuplicateText(req.getAdjustReason()),
                        normalizeDuplicateText(log.getAdjustReason()))
                        && Objects.equals(normalizeDuplicateText(buildBatchAdjustAdvice(req)),
                        normalizeDuplicateText(log.getAdjustAdvice())));
        if (!requestKeys.isEmpty() && requestKeys.equals(historyKeys) && sameText) {
            throw new BizException("调库申请已提交，请勿重复操作");
        }
    }

    /** 构造批量请求手工项防重复比较键。 */
    private String buildBatchDuplicateItemKey(StockSecurityBatchAdjustSubmitReq.AdjustItem item) {
        return item.getSecurityCode() + "|" + item.getTargetPoolId() + "|" + item.getAdjustMode()
                + "|" + item.getFlowId() + "|" + normalizeDuplicateText(item.getFlowKey());
    }

    /** 构造历史批量手工日志防重复比较键。 */
    private String buildBatchDuplicateItemKey(IpAdjustLogBo log) {
        return log.getSecurityCode() + "|" + log.getTargetPoolId() + "|" + log.getAdjustMode()
                + "|" + log.getFlowId() + "|" + normalizeDuplicateText(log.getFlowKey());
    }

    /** 在放开主体债矩阵时补充调整说明。 */
    private String buildBatchAdjustmentNote(StockSecurityBatchAdjustSubmitReq req,
                                             StockSecurityBatchAdjustSubmitReq.AdjustItem item) {
        if (!"yes".equals(req.getReleaseRules())) {
            return item.getAdjustmentNote();
        }
        String note = item.getAdjustmentNote();
        return (note == null || note.isEmpty()) ? "放开主体债入库矩阵规则"
                : note + "；放开主体债入库矩阵规则";
    }

    /**
     * 批量提交调库申请
     */
    @Transactional(rollbackFor = Exception.class)
    public StockSecurityBatchAdjustDto addAdjustLog(StockSecurityBatchAdjustSubmitReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 批量提交调库申请及附件。
     */
    @Transactional(rollbackFor = Exception.class)
    public StockSecurityBatchAdjustDto addAdjustLog(
            StockSecurityBatchAdjustSubmitReq req, List<MultipartFile> files) {
        // 校验批量调库提交参数
        validateAdjustSubmitReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());
        InvestmentPoolBo batchPool = investmentPoolMapper.queryPoolById(req.getPoolId());
        // 提交流程定稿（兜底，非强制）：
        // 1) 正常路径：校验页已默认选中批量流程并写入 item.flowId/flowKey → 此处不改动；
        // 2) 操作员改选了单券候选流程 → 同样不改动，尊重请求体；
        // 3) 仅当某条明细 flowId、flowKey 都为空（漏传/异常调用）→ 才补目标池 batchIn/batchOut；
        // 切勿理解成「整批强制改成批量流程」。补完后再做防重复与直通预检，保证比较/判断用的是定稿后的流程。
        fillDefaultBatchFlowIfMissing(req, batchPool);
        // 按完整证券集合和手工项集合检查近期重复批量申请
        checkRecentBatchDuplicateSubmit(req);
        // 存在直通落池项时，提交前统一锁池并复核整批状态
        if (needsWholeBatchDirectRecheck(req)) {
            recheckBeforeFinalApproval(buildDirectRecheckLogList(req));
        }

        // 按触发主券分组，确保 related 与主券同次提交、共享批次与流程步骤
        Map<String, List<StockSecurityBatchAdjustSubmitReq.AdjustItem>> itemMap = new LinkedHashMap<>();
        for (StockSecurityBatchAdjustSubmitReq.AdjustItem item : req.getItems()) {
            String groupKey = resolveBatchSubmitGroupKey(item);
            List<StockSecurityBatchAdjustSubmitReq.AdjustItem> list = itemMap.get(groupKey);
            if (list == null) {
                list = new ArrayList<>();
                itemMap.put(groupKey, list);
            }
            list.add(item);
        }

        StockSecurityBatchAdjustDto dto = new StockSecurityBatchAdjustDto();
        dto.setSecurityCount(itemMap.size());
        dto.setSubmitCount(0);
        // 创建批量提交附件上下文
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId());
        BatchNoContext batchNoContext = new BatchNoContext();
        for (Map.Entry<String, List<StockSecurityBatchAdjustSubmitReq.AdjustItem>> entry : itemMap.entrySet()) {
            // 构建单证券调库提交请求（主券 + 同组关联码）
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
     * 校验候选证券查询参数（目标须为债券产品库叶子；来源池至少一个且在白名单）。
     */
    private void validateSecurityPageReq(StockSecurityBatchAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (stockSecurityBatchAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用，或不在债券产品库范围内");
        }
        if (req.getSourcePoolIds() == null || req.getSourcePoolIds().isEmpty()) {
            throw new BizException("请至少选择一个来源池");
        }
        Set<Long> allowedIds = resolveSourcePoolMap().values().stream()
                .map(InvestmentPoolBo::getId)
                .collect(Collectors.toSet());
        for (Long sourcePoolId : req.getSourcePoolIds()) {
            if (sourcePoolId == null || !allowedIds.contains(sourcePoolId)) {
                throw new BizException("来源池不在允许范围内");
            }
        }
    }

    /**
     * 将来源池拆成普通池 / CRMW 池 ID，写入 Req 供 Mapper 动态 SQL 使用。
     */
    private void prepareSourcePoolIds(StockSecurityBatchAdjustReq req) {
        Map<String, InvestmentPoolBo> byCode = resolveSourcePoolMap();
        Long crmwPoolId = byCode.get(SOURCE_POOL_CODE_CRMW) == null
                ? null : byCode.get(SOURCE_POOL_CODE_CRMW).getId();
        List<Long> normalIds = new ArrayList<>();
        List<Long> crmwIds = new ArrayList<>();
        for (Long sourcePoolId : req.getSourcePoolIds()) {
            if (crmwPoolId != null && crmwPoolId.equals(sourcePoolId)) {
                crmwIds.add(sourcePoolId);
            } else {
                normalIds.add(sourcePoolId);
            }
        }
        req.setNormalSourcePoolIds(normalIds.isEmpty() ? null : normalIds);
        req.setCrmwSourcePoolIds(crmwIds.isEmpty() ? null : crmwIds);
        if ((req.getNormalSourcePoolIds() == null || req.getNormalSourcePoolIds().isEmpty())
                && (req.getCrmwSourcePoolIds() == null || req.getCrmwSourcePoolIds().isEmpty())) {
            throw new BizException("请至少选择一个来源池");
        }
    }

    /**
     * 按白名单 pool_code 解析来源池（缺失则抛配置异常）。
     */
    private Map<String, InvestmentPoolBo> resolveSourcePoolMap() {
        Map<String, InvestmentPoolBo> map = new LinkedHashMap<>();
        for (String code : SOURCE_POOL_CODES) {
            InvestmentPoolBo pool = investmentPoolMapper.queryPoolByCode(code);
            if (pool == null || Integer.valueOf(1).equals(pool.getIsDeleted())) {
                throw new BizException("存量来源池不存在或已删除，pool_code=" + code);
            }
            map.put(code, pool);
        }
        return map;
    }

    /** 为来源池列表回填全路径名称。 */
    private void fillSourcePoolFullName(List<StockSourcePoolDto> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<InvestmentPoolDto> fullNameList = investmentPoolMapper.queryPoolFullNameList();
        Map<Long, String> fullNameMap = new HashMap<>();
        if (fullNameList != null) {
            for (InvestmentPoolDto item : fullNameList) {
                if (item != null && item.getId() != null) {
                    fullNameMap.put(item.getId(), item.getPoolFullName());
                }
            }
        }
        for (StockSourcePoolDto dto : list) {
            if (dto.getId() != null) {
                String fullName = fullNameMap.get(dto.getId());
                dto.setPoolFullName(fullName != null ? fullName : dto.getPoolName());
            }
        }
    }

    /**
     * 校验批量调库下一步参数
     */
    private void validateAdjustCheckReq(StockSecurityBatchAdjustSubmitReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (req.getSecurities() == null || req.getSecurities().isEmpty()) {
            throw new BizException("已选证券不能为空");
        }
        if (stockSecurityBatchAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用，或不在债券产品库范围内");
        }
        for (StockSecurityBatchAdjustSubmitReq.SecurityItem security : req.getSecurities()) {
            if (security.getSecurityCode() == null || security.getSecurityCode().isEmpty()) {
                throw new BizException("已选证券代码不能为空");
            }
        }
    }

    /**
     * 校验批量调库提交参数
     */
    private void validateAdjustSubmitReq(StockSecurityBatchAdjustSubmitReq req) {
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
        if (stockSecurityBatchAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用，或不在债券产品库范围内");
        }
        // 解析批量调库中文方向
        String adjustMode = resolveAdjustMode(req);
        for (StockSecurityBatchAdjustSubmitReq.AdjustItem item : req.getItems()) {
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
        }
    }

    /**
     * 判断批量提交项是否为手工调库项。
     */
    private boolean isManualBatchSubmitItem(StockSecurityBatchAdjustSubmitReq.AdjustItem item) {
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
    private String resolveAdjustMode(StockSecurityBatchAdjustSubmitReq req) {
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        return "out".equals(req.getDirection()) ? AdjustMode.OUT.getCode() : AdjustMode.IN.getCode();
    }

    /**
     * 校验批量调库目标池权限
     */
    private void validateAdjustPoolPermission(String currentUserId, Long poolId) {
        StockSecurityBatchAdjustReq permissionReq = new StockSecurityBatchAdjustReq();
        permissionReq.setCurrentUserId(currentUserId);
        permissionReq.setPoolId(poolId);
        // 校验目标投资池调整权限
        validatePoolPermission(permissionReq);
    }

    /**
     * 构建单证券调库校验请求
     */
    private AdjustCheckReq buildSingleCheckReq(StockSecurityBatchAdjustSubmitReq req,
                                               StockSecurityBatchAdjustSubmitReq.SecurityItem security) {
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
        // 透传前端选中的担保人代码（简易流程第⑤条件担保人评级下调判断用）
        checkReq.setGuarantorCode(security.getGuarantorCode());
        checkReq.setReleaseRules("yes".equals(req.getReleaseRules()));
        checkReq.setItems(Collections.singletonList(item));
        return checkReq;
    }

    /**
     * 构建批量调库校验结果（透传单笔流程候选）。
     */
    private StockSecurityBatchAdjustDto.CheckResultItem buildBatchCheckResult(
            StockSecurityBatchAdjustSubmitReq.SecurityItem security,
            AdjustCheckDto.CheckResultItem item) {
        StockSecurityBatchAdjustDto.CheckResultItem result = new StockSecurityBatchAdjustDto.CheckResultItem();
        // 关联码项使用校验结果中的证券代码，主券项回退选中券
        String securityCode = item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()
                ? item.getSecurityCode() : security.getSecurityCode();
        String securityShortName = item.getSecurityShortName() != null && !item.getSecurityShortName().isEmpty()
                ? item.getSecurityShortName() : security.getSecurityShortName();
        String securityType = item.getSecurityType() != null && !item.getSecurityType().isEmpty()
                ? item.getSecurityType() : security.getSecurityType();
        String sourceSecurityCode = item.getSourceSecurityCode() != null && !item.getSourceSecurityCode().isEmpty()
                ? item.getSourceSecurityCode() : security.getSecurityCode();
        result.setSecurityCode(securityCode);
        result.setSecurityShortName(securityShortName);
        result.setSecurityType(securityType);
        result.setSourceSecurityCode(sourceSecurityCode);
        result.setTargetPoolId(item.getTargetPoolId());
        result.setPoolName(item.getPoolName());
        result.setPoolType(item.getPoolType());
        result.setAdjustMode(item.getAdjustMode());
        result.setItemTag(item.getItemTag());
        // 分组 Key 以触发主券为前缀，便于 related 与主券同批
        result.setAdjustGroupKey(sourceSecurityCode + "_" + item.getAdjustGroupKey());
        result.setCanAdjust(item.isCanAdjust());
        result.setFailReasons(item.getFailReasons() == null ? new ArrayList<>() : item.getFailReasons());
        // 透传单笔流程候选与推荐标识，前端默认选中 recommended 项
        result.setFlowOptions(item.getFlowOptions() == null
                ? new ArrayList<>() : new ArrayList<>(item.getFlowOptions()));
        return result;
    }

    /**
     * 构建单证券调库提交请求。
     */
    private SecurityPoolAdjustSubmitReq buildSingleSubmitReq(
            StockSecurityBatchAdjustSubmitReq req,
            List<StockSecurityBatchAdjustSubmitReq.AdjustItem> items) {
        // 请求级证券取触发主券（manual 项优先，否则 sourceSecurityCode / 首条）
        StockSecurityBatchAdjustSubmitReq.AdjustItem primary = resolveBatchPrimaryItem(items);
        SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
        submitReq.setSecurityCode(resolveBatchSubmitGroupKey(primary));
        submitReq.setSecurityShortName(primary.getSecurityShortName());
        submitReq.setSecurityType(primary.getSecurityType());
        // 若 primary 是 related，主券简称可能不对，回查主券主数据
        if (ItemType.RELATED.getCode().equals(primary.getItemTag())) {
            SecurityInfoBo primarySec = securityPoolAdjustMapper.querySecurityBoByCode(submitReq.getSecurityCode());
            if (primarySec != null) {
                submitReq.setSecurityShortName(primarySec.getShortName());
                submitReq.setSecurityType(primarySec.getSecurityType());
            }
        }
        submitReq.setCrmwName(primary.getCrmwName());
        submitReq.setCrmwScode(primary.getCrmwScode());
        submitReq.setCrmwMktcode(primary.getCrmwMktcode());
        submitReq.setCrmwStype(primary.getCrmwStype());
        submitReq.setAdjustType("手动批量调整");
        submitReq.setAdjustReason(req.getAdjustReason());
        submitReq.setAdjustAdvice(buildBatchAdjustAdvice(req));
        submitReq.setAdjusterId(req.getAdjusterId());
        submitReq.setAdjusterName(req.getAdjusterName());

        List<SecurityPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
        for (StockSecurityBatchAdjustSubmitReq.AdjustItem item : items) {
            SecurityPoolAdjustSubmitReq.AdjustItem submitItem = new SecurityPoolAdjustSubmitReq.AdjustItem();
            // 每条明细带真实证券代码（关联码独立落 log）
            submitItem.setSecurityCode(item.getSecurityCode());
            submitItem.setSecurityShortName(item.getSecurityShortName());
            submitItem.setSecurityType(item.getSecurityType());
            submitItem.setTargetPoolId(item.getTargetPoolId());
            submitItem.setTargetPoolName(item.getTargetPoolName());
            submitItem.setPoolType(item.getPoolType());
            submitItem.setAdjustMode(item.getAdjustMode());
            submitItem.setItemTag(item.getItemTag());
            submitItem.setAdjustGroupKey(item.getAdjustGroupKey());
            submitItem.setFlowId(item.getFlowId());
            submitItem.setFlowKey(item.getFlowKey());
            submitItem.setFlowType(item.getFlowType());
            submitItem.setAdjustmentNote(buildBatchAdjustmentNote(req, item));
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
     * 提交时校验：调入目标池若要求配套互斥调出，请求中必须包含对应调出项且调出校验通过。
     */
    private void validateRequiredMutexOutboundOnSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item != null && AdjustMode.OUT.getCode().equals(item.getAdjustMode())
                    && item.getTargetPoolId() != null) {
                requestOutPoolIds.add(item.getTargetPoolId());
            }
        }
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item == null || !AdjustMode.IN.getCode().equals(item.getAdjustMode())
                    || !isManualSubmitItem(item) || item.getTargetPoolId() == null) {
                continue;
            }
            Map<String, List<Long>> relations = shared.poolRelationMap.get(item.getTargetPoolId());
            if (relations == null) {
                continue;
            }
            List<Long> inMutex = relations.get(RelationType.IN_MUTEX.getCode());
            if (inMutex == null || inMutex.isEmpty()) {
                continue;
            }
            for (Long mutexId : inMutex) {
                if (mutexId == null || !shared.currentPoolIds.contains(mutexId)) {
                    continue;
                }
                String mutexPoolName = buildPoolPath(mutexId, shared.poolMap);
                if (!requestOutPoolIds.contains(mutexId)) {
                    throw new BizException("调入须同步调出互斥池，请完整提交：" + mutexPoolName);
                }
                AdjustCheckReq.CheckItem fakeOut = new AdjustCheckReq.CheckItem();
                fakeOut.setTargetPoolId(mutexId);
                fakeOut.setAdjustMode(AdjustMode.OUT.getCode());
                InvestmentPoolBo mutexPool = shared.poolMap.get(mutexId);
                fakeOut.setPoolType(mutexPool != null ? mutexPool.getPoolType() : null);
                AdjustSharedData checkShared = new AdjustSharedData();
                checkShared.setSecurityInfo(shared.securityInfo);
                checkShared.setPoolMap(shared.poolMap);
                checkShared.setCurrentPoolIds(shared.currentPoolIds);
                checkShared.setPoolRelationMap(shared.poolRelationMap);
                checkShared.setHasPendingProcess(shared.hasPendingProcess);
                checkShared.setSecurityInObservePool(shared.securityInObservePool);
                checkShared.setIssuerInObservePool(shared.issuerInObservePool);
                checkShared.setRequestInPoolIds(Collections.<Long>emptySet());
                checkShared.setRequestOutPoolIds(requestOutPoolIds);
                int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(mutexId);
                AdjustCheckContext ctx = buildCheckContext(fakeOut, poolCurrentCount, checkShared);
                List<String> outFailures = checkOutConditions(ctx);
                if (outFailures != null && !outFailures.isEmpty()) {
                    throw new BizException(buildMutexOutboundFailureMessage(mutexPoolName, outFailures));
                }
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
     * 判断流程是否为直通流程（开始后无需人工待办即可到结束节点）。
     *
     * <p>与 createInitialSteps 自动完成范围对齐：start/end/auto 节点、initiator、approval_strategy=auto
     * 可无人工到达 end；主路径存在 preempt/all 等人工审批则为非直通。
     * 例：债券特殊策略入库（发起人→多层 auto→结束）应判为直通。
     *
     * @param snapshot 流程快照（含节点索引和连线列表）
     * @return true 表示直通流程（可直接生效），false 表示需要审批
     */
    private boolean isDirectFlow(FlowSnapshot snapshot) {
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
        // 6个月内入池报告标记（对齐老系统 bondfileflag）：同主体半年内有审批通过调入且确有报告才跳过
        if (securityCode != null && !securityCode.isEmpty() && securityPoolAdjustMapper.queryHasRecentInboundWithReport(securityCode)) {
            return;
        }
        boolean hasReport = (item.getCreditReportFileIndexes() != null && !item.getCreditReportFileIndexes().isEmpty())
                || (item.getCreditReportSourceAttachmentIds() != null && !item.getCreditReportSourceAttachmentIds().isEmpty());
        if (!hasReport) {
            throw new BizException("目标池[" + pool.getPoolName() + "]要求研究报告，请上传或选择报告");
        }
        // internal 要求从内部报告库选择，手工上传或外部报告不能替代
        if ("internal".equals(reportRestriction)
                && (item.getCreditReportSourceAttachmentIds() == null || item.getCreditReportSourceAttachmentIds().isEmpty())) {
            throw new BizException("目标池[" + pool.getPoolName() + "]要求内部研究报告，请从内部报告库选择");
        }
        // 校验报告库附件真实存在、未删除且来源分类匹配
        if (item.getCreditReportSourceAttachmentIds() != null && !item.getCreditReportSourceAttachmentIds().isEmpty()) {
            sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(),
                    "internal".equals(reportRestriction));
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
        // 创建批量提交附件上下文
        return executeInboundSubmit(req, shared,
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
            // 关联码项由同组手工项承担报告要求
            if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                String itemSecurityCode = item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()
                        ? item.getSecurityCode() : req.getSecurityCode();
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getInReportRestriction() : null,
                        itemSecurityCode);
            }
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
                IpAdjustLogBo logBo = buildAdjustLog(req, item, manualItem, shared);
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
                int inserted = securityPoolAdjustMapper.addPoolStatus(logBo);
                if (inserted == 0) {
                    throw new BizException("证券当前池状态已发生变化，请刷新后重试");
                }
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，流程中）
                // 构建调库日志实体
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem, shared);
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
                        int inserted = securityPoolAdjustMapper.addPoolStatus(bo);
                        if (inserted == 0) {
                            throw new BizException("证券当前池状态已发生变化，请刷新后重试");
                        }
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
        // 创建批量提交附件上下文
        return executeOutboundSubmit(req, shared,
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
            // 关联码项由同组手工项承担报告要求
            if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                String itemSecurityCode = item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()
                        ? item.getSecurityCode() : req.getSecurityCode();
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getOutReportRestriction() : null,
                        itemSecurityCode);
            }
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
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem, shared);
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
                int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(
                        req.getSecurityCode(), item.getTargetPoolId());
                if (deleted == 0) {
                    throw new BizException("证券当前池状态已发生变化，请刷新后重试");
                }
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，流程中）
                // 构建调库日志实体
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem, shared);
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
                        int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(
                                req.getSecurityCode(), item.getTargetPoolId());
                        if (deleted == 0) {
                            throw new BizException("证券当前池状态已发生变化，请刷新后重试");
                        }
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
        current.setMaturityembeddedDesc(changedField.getMaturityembeddedDesc());
        current.setDateInrightExists(changedField.getDateInrightExists());
        current.setCarryDate(changedField.getCarryDate());
        current.setMaturityDate(changedField.getMaturityDate());
        current.setInfoPledgeRatio(changedField.getInfoPledgeRatio());
        current.setRatingBondAgency(changedField.getRatingBondAgency());
        current.setRatingBond(changedField.getRatingBond());
        current.setRatingBondissuer(changedField.getRatingBondissuer());
        current.setRatingOutlook(changedField.getRatingOutlook());
        current.setAgencyName(changedField.getAgencyName());
        current.setInnerIssuerRating(changedField.getInnerIssuerRating());
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
        // 提交时仅复核本次涉及顶级池组是否存在活动流程
        checkSubmitPendingPoolGroups(req, shared.poolMap);
        // 调入须同步调出互斥池：缺失或调出不可行则整单拒绝，防止双池
        validateRequiredMutexOutboundOnSubmit(req, shared);
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

    /** 提交时按顶级投资池组检查活动流程冲突（主券与关联码分别判断）。 */
    private void checkSubmitPendingPoolGroups(SecurityPoolAdjustSubmitReq req,
                                               Map<Long, InvestmentPoolBo> poolMap) {
        Map<String, Set<Long>> pendingGroupsBySecurity = new HashMap<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            String securityCode = item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()
                    ? item.getSecurityCode() : req.getSecurityCode();
            Set<Long> pendingGroups = pendingGroupsBySecurity.get(securityCode);
            if (pendingGroups == null) {
                pendingGroups = resolvePoolGroupIds(
                        securityPoolAdjustMapper.queryPendingManualTargetPoolIdList(securityCode, null), poolMap);
                pendingGroupsBySecurity.put(securityCode, pendingGroups);
            }
            Long groupId = resolveRootPoolId(item.getTargetPoolId(), poolMap);
            if (groupId != null && pendingGroups.contains(groupId)) {
                throw new BizException("证券[" + securityCode + "]在本次涉及的投资池组中存在待处理流程");
            }
        }
    }

    /** 将池 ID 列表转换为顶级投资池组 ID 集合。 */
    private Set<Long> resolvePoolGroupIds(List<Long> poolIds, Map<Long, InvestmentPoolBo> poolMap) {
        Set<Long> groups = new HashSet<>();
        if (poolIds == null) return groups;
        for (Long poolId : poolIds) {
            Long currentId = poolId;
            Set<Long> visited = new HashSet<>();
            while (currentId != null && visited.add(currentId)) {
                InvestmentPoolBo pool = poolMap.get(currentId);
                if (pool == null || pool.getParentId() == null) break;
                currentId = pool.getParentId();
            }
            if (currentId != null) groups.add(currentId);
        }
        return groups;
    }

    /** 沿父级关系查找目标池所属的顶级投资池。 */
    private Long resolveRootPoolId(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
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

    /** 归一化防重复比较文本。 */
    private String normalizeDuplicateText(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /** 在放开主体债矩阵时将审计说明追加到调整意见。 */
    private String buildBatchAdjustAdvice(StockSecurityBatchAdjustSubmitReq req) {
        if (!"yes".equals(req.getReleaseRules())) {
            return req.getAdjustAdvice();
        }
        String advice = req.getAdjustAdvice();
        return advice == null || advice.trim().isEmpty() ? "放开主体债入库矩阵规则"
                : advice + "；放开主体债入库矩阵规则";
    }

    /**
     * 执行单证券调库校验（委托证券池调整服务，与证券池批量调整一致）。
     */
    private AdjustCheckDto checkSingleAdjust(AdjustCheckReq req) {
        return securityPoolAdjustService.checkAdjust(req);
    }

    /**
     * 批量提交分组键：related 归到触发主券，保证同批同流程。
     */
    private String resolveBatchSubmitGroupKey(StockSecurityBatchAdjustSubmitReq.AdjustItem item) {
        if (item == null) {
            return null;
        }
        if (item.getSourceSecurityCode() != null && !item.getSourceSecurityCode().isEmpty()) {
            return item.getSourceSecurityCode();
        }
        return item.getSecurityCode();
    }

    /**
     * 在同组提交项中优先取 manual 作为主券代表。
     */
    private StockSecurityBatchAdjustSubmitReq.AdjustItem resolveBatchPrimaryItem(
            List<StockSecurityBatchAdjustSubmitReq.AdjustItem> items) {
        for (StockSecurityBatchAdjustSubmitReq.AdjustItem item : items) {
            if (item.getItemTag() == null || item.getItemTag().isEmpty()
                    || ItemType.MANUAL.getCode().equals(item.getItemTag())) {
                return item;
            }
        }
        return items.get(0);
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

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 方向校验入口（含前置规则）
    // ═══════════════════════════════════════════════════════════

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
        // 出池检查：证券当前在弹性禁投池中（out_soft_restrict，警告不阻断）
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
     * 规则：债券到期（maturity_date，调出）
     *
     * <p>债券到期日早于今日则禁止调出。对应老项目 checkOutPool 到期校验。
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
     * <p>股票摘牌日早于今日则禁止调出。对应老项目 checkOutPool 退市校验。
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
     * <p>存在 pending 流程步骤的调库记录视为进行中，
     * 需等待该流程终结后方可再次发起调库，避免并发操作导致入池状态混乱。
     */
    private String preCheckPendingProcess(AdjustCheckContext ctx) {
        if (ctx.isHasPendingProcess()) {
            String nodeLabel = ctx.getPendingProcessNodeLabel();
            if (nodeLabel != null && !nodeLabel.trim().isEmpty()) {
                return "证券存在进行中的调库流程（当前节点：" + nodeLabel.trim() + "）";
            }
            return "证券存在进行中的调库流程";
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
            return "证券已在目标投资池中";
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
            return "证券当前不在目标投资池中";
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
            return "目标投资池已锁定";
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
            return "目标投资池已锁定";
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
     * <p>目标池配置了投资品种时，证券品种（categoryType）须在配置内。
     * categoryType 由 dict_security_type 表按 securityType 查询得到（大类见 CategoryType / 演示数据）。
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
            return "证券不在本池投资品种范围内";
        }
        return null;
    }

    /**
     * 规则：全局禁止池（forbidden 禁投池）
     *
     * <p>证券当前在禁投池中则不能调入任何其他池（全局禁止，区别于池间 in_restrict）。
     * 对应老项目 Forbiddenlastpoolid 配置。
     */
    private String inCheckForbiddenPool(AdjustCheckContext ctx) {
        if (securityPoolAdjustMapper.querySecurityInForbiddenPool(ctx.getSecurityInfo().getWindCode())) {
            return "证券当前在禁止池中";
        }
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
            return "证券行业与目标池行业配置不一致";
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
        String today = LocalDate.now().toString();
        if (!securityPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
            return "当前不在本池开放日内";
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
        ctx.setHasPendingProcess(shared.getPendingPoolGroupIds() != null
                && shared.getPendingPoolGroupIds().contains(resolveRootPoolId(
                item.getTargetPoolId(), shared.getPoolMap())));
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
        // 查证券品种大类，用于类型特有校验路由（dict_security_type.category_type）
        ctx.setCategoryType(securityPoolAdjustMapper.queryCategoryTypeBySecurityType(
                shared.getSecurityInfo().getSecurityType()));
        // 基金评分（基金证券调入校验用，透传请求级 fundRate）
        ctx.setFundRate(shared.getFundRate());
        ctx.setReleaseRules(shared.isReleaseRules());
        return ctx;
    }

    /**
     * 组装提交阶段配套互斥调出失败的异常文案（总述 + 编号原因）。
     */
    private String buildMutexOutboundFailureMessage(String mutexPoolName, List<String> outFailures) {
        StringBuilder msg = new StringBuilder("配套互斥调出未通过（" + mutexPoolName + "）");
        if (outFailures == null || outFailures.isEmpty()) {
            return msg.toString();
        }
        msg.append("：");
        for (int i = 0; i < outFailures.size(); i++) {
            if (i > 0) {
                msg.append(" ");
            }
            msg.append(i + 1).append(". ").append(outFailures.get(i));
        }
        return msg.toString();
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

            // 审批策略 auto：系统自动审批节点，提交时直接记 auto_process 并继续流转
            if (NodeType.APPROVAL.getCode().equals(currentNode.getNodeType())
                    && config != null
                    && ApprovalStrategy.AUTO.getCode().equals(config.getApprovalStrategy())) {
                sortOrder = currentNode.getSortOrder() != null ? currentNode.getSortOrder() : 1;
                insertStepRecord(adjustLogId, adjustBatchNo, currentNode, config, sortOrder, ProcessAction.AUTO_PROCESS.getCode(),
                                 null, null, ProcessAction.AUTO_PROCESS.getCode(), "系统自动审批通过", now);
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

            // 自动节点（node_type=auto）写入 auto_process 步骤后继续向下流转
            if (NodeType.AUTO.getCode().equals(currentNode.getNodeType())) {
                sortOrder = currentNode.getSortOrder() != null ? currentNode.getSortOrder() : 1;
                insertStepRecord(adjustLogId, adjustBatchNo, currentNode, config, sortOrder, ProcessAction.AUTO_PROCESS.getCode(),
                                 null, null, ProcessAction.AUTO_PROCESS.getCode(), null, now);
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
                                         SecurityPoolAdjustSubmitReq.AdjustItem flowSource,
                                         SubmitSharedData shared) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        // 关联码项使用 item 级证券代码，保证独立落 log / 池状态
        String securityCode = item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()
                ? item.getSecurityCode() : req.getSecurityCode();
        String securityShortName = item.getSecurityShortName() != null && !item.getSecurityShortName().isEmpty()
                ? item.getSecurityShortName() : req.getSecurityShortName();
        String securityType = item.getSecurityType() != null && !item.getSecurityType().isEmpty()
                ? item.getSecurityType() : req.getSecurityType();
        if ((securityShortName == null || securityShortName.isEmpty()
                || securityType == null || securityType.isEmpty())
                && securityCode != null && !securityCode.isEmpty()) {
            SecurityInfoBo securityInfo = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
            if (securityInfo != null) {
                if (securityShortName == null || securityShortName.isEmpty()) {
                    securityShortName = securityInfo.getShortName();
                }
                if (securityType == null || securityType.isEmpty()) {
                    securityType = securityInfo.getSecurityType();
                }
            }
        }
        bo.setSecurityCode(securityCode);
        bo.setSecurityShortName(securityShortName);
        bo.setSecurityType(securityType);
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
        // 同一次提交共用统一提交时间，避免大批量逐条 NOW() 导致历史排序同组打散
        if (shared != null && shared.batchNoContext != null) {
            bo.setSubmitTime(shared.batchNoContext.submitTime);
        }
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
        if (ItemType.RELATED.getCode().equals(item.getItemTag())) {
            return "关联调整";
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

        /** 本次提交统一提交时间（写入 ip_adjust_log.submit_time） */
        final Date submitTime = new Date();

        /** 本次提交批次号时间片（与 submitTime 同源，避免再 new Date 漂移） */
        final String batchTimeText = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime);

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
    private void validatePoolPermission(StockSecurityBatchAdjustReq req) {
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
    private boolean prepareAdjustablePoolIds(StockSecurityBatchAdjustReq req) {
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
    private void fillMarketCodes(List<StockSecurityBatchCandidateDto> list) {
        for (StockSecurityBatchCandidateDto dto : list) {
            if (dto.getMarketCodeText() == null || dto.getMarketCodeText().isEmpty()) {
                dto.setMarketCodes(new ArrayList<>());
            } else {
                dto.setMarketCodes(Arrays.asList(dto.getMarketCodeText().split(",")));
            }
        }
    }

    /**
     * 填充当前页投资池现有证券数量（按类型分项 + 合计）。
     *
     * <p>统计 ip_pool_status 全部有效在池代码，按 company / crmw / category_type 分项，
     * 前端展示「主体：n只 / 债券：n只 …」；合计写入 currentCount。
     * 容量校验仍走单券链路全量 count，不受展示分项影响。
     */
    private void fillPoolCurrentCount(List<StockSecurityBatchPoolDto> poolList) {
        if (poolList.isEmpty()) {
            return;
        }
        List<Long> poolIds = poolList.stream()
                .map(StockSecurityBatchPoolDto::getId)
                .collect(Collectors.toList());
        List<StockPoolTypeCountDto> typeCountList =
                stockSecurityBatchAdjustMapper.queryPoolCurrentCountByTypeList(poolIds);
        Map<Long, List<StockPoolTypeCountDto>> byPoolId = new HashMap<>();
        if (typeCountList != null) {
            for (StockPoolTypeCountDto row : typeCountList) {
                if (row == null || row.getPoolId() == null || row.getCount() == null || row.getCount() <= 0) {
                    continue;
                }
                byPoolId.computeIfAbsent(row.getPoolId(), key -> new ArrayList<>()).add(row);
            }
        }
        for (StockSecurityBatchPoolDto pool : poolList) {
            // 组装有序分项列表并汇总总数
            List<StockPoolTypeCountDto> ordered = orderPoolTypeCounts(
                    byPoolId.getOrDefault(pool.getId(), Collections.emptyList()));
            pool.setCountByType(ordered);
            int total = 0;
            for (StockPoolTypeCountDto item : ordered) {
                total += item.getCount() == null ? 0 : item.getCount();
            }
            pool.setCurrentCount(total);
        }
    }

    /**
     * 按业务固定顺序排列类型分项，并去掉嵌套中的 poolId（前端只关心 typeCode/count）。
     */
    private List<StockPoolTypeCountDto> orderPoolTypeCounts(List<StockPoolTypeCountDto> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return new ArrayList<>();
        }
        // 主体 / CRMW 优先，其余按 category_type 常见顺序，未知与其它垫后
        final List<String> typeOrder = Arrays.asList(
                CategoryType.COMPANY.getCode(),
                "crmw",
                CategoryType.BOND.getCode(),
                CategoryType.STOCK.getCode(),
                CategoryType.FUND.getCode(),
                CategoryType.INDEX.getCode(),
                CategoryType.WARRANT.getCode(),
                CategoryType.TRUST.getCode(),
                CategoryType.PRIVATE_WEALTH.getCode(),
                CategoryType.UNKNOWN.getCode());
        List<StockPoolTypeCountDto> sorted = new ArrayList<>(rawList);
        sorted.sort(new Comparator<StockPoolTypeCountDto>() {
            @Override
            public int compare(StockPoolTypeCountDto a, StockPoolTypeCountDto b) {
                int ia = typeOrder.indexOf(a.getTypeCode());
                int ib = typeOrder.indexOf(b.getTypeCode());
                if (ia < 0) {
                    ia = typeOrder.size();
                }
                if (ib < 0) {
                    ib = typeOrder.size();
                }
                if (ia != ib) {
                    return Integer.compare(ia, ib);
                }
                String ca = a.getTypeCode() == null ? "" : a.getTypeCode();
                String cb = b.getTypeCode() == null ? "" : b.getTypeCode();
                return ca.compareTo(cb);
            }
        });
        List<StockPoolTypeCountDto> result = new ArrayList<>();
        for (StockPoolTypeCountDto row : sorted) {
            StockPoolTypeCountDto item = new StockPoolTypeCountDto();
            item.setTypeCode(row.getTypeCode());
            item.setCount(row.getCount());
            result.add(item);
        }
        return result;
    }

    /**
     * 填充投资池全路径名称
     */
    private void fillPoolFullName(List<StockSecurityBatchPoolDto> poolList) {
        if (poolList.isEmpty()) {
            return;
        }
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools.isEmpty()) {
            return;
        }
        Map<Long, InvestmentPoolBo> poolMap = allPools.stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, Function.identity()));
        for (StockSecurityBatchPoolDto dto : poolList) {
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

    // ═══════════════════════════════════════════════════════════
    //  以下方法自 SecurityPoolAdjustService 迁入，存量链路独立维护
    // ═══════════════════════════════════════════════════════════

    private void recheckBeforeFinalApproval(List<IpAdjustLogBo> logList) {
        if (logList == null || logList.isEmpty()) {
            return;
        }
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, p -> p));
        List<Long> poolIds = logList.stream().map(IpAdjustLogBo::getTargetPoolId)
                .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        if (!poolIds.isEmpty()) {
            // 锁定目标池，串行完成容量和池状态复核
            investmentPoolMapper.lockPoolByIdsList(poolIds);
        }
        Map<Long, Integer> inboundIncrements = new HashMap<>();
        Map<Long, Map<String, List<Long>>> relationMap = buildPoolRelationMap(
                securityPoolAdjustMapper.queryAllPoolRelationList());
        for (IpAdjustLogBo log : logList) {
            InvestmentPoolBo pool = poolMap.get(log.getTargetPoolId());
            if (pool == null || "enabled".equals(pool.getStatus()) == false
                    || (pool.getIsDeleted() != null && pool.getIsDeleted() == 1)) {
                throwApprovalRecheckFailure(log, "目标投资池不存在或已停用");
            }
            SecurityInfoBo security = securityPoolAdjustMapper.querySecurityBoByCode(log.getSecurityCode());
            if (security == null) {
                throwApprovalRecheckFailure(log, "证券不存在");
            }
            Set<Long> currentPoolIds = new HashSet<>(
                    securityPoolAdjustMapper.querySecurityCurrentPoolIdList(log.getSecurityCode()));
            AdjustCheckContext ctx = new AdjustCheckContext();
            ctx.setSecurityInfo(security);
            ctx.setTargetPool(pool);
            ctx.setPoolMap(poolMap);
            ctx.setCurrentPoolIds(currentPoolIds);
            ctx.setTargetPoolRelations(relationMap.getOrDefault(pool.getId(), Collections.emptyMap()));
            ctx.setCategoryType(securityPoolAdjustMapper.queryCategoryTypeBySecurityType(security.getSecurityType()));
            String failure;
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                failure = firstFailure(inCheckPoolLocked(ctx), inCheckSecurityAlreadyInPool(ctx),
                        inCheckVariety(ctx), inCheckSourcePool(ctx), inCheckRestrictPool(ctx),
                        inCheckForbiddenPool(ctx));
                int currentCount = securityPoolAdjustMapper.queryPoolCurrentCount(pool.getId());
                int increment = inboundIncrements.getOrDefault(pool.getId(), 0);
                if (failure == null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                        && currentCount + increment >= pool.getMaxCapacity()) {
                    failure = "目标投资池容量已满";
                }
                inboundIncrements.put(pool.getId(), increment + 1);
            } else {
                ctx.setTargetPoolEntryTime(securityPoolAdjustMapper.queryPoolEntryTime(
                        log.getSecurityCode(), pool.getId()));
                failure = firstFailure(outCheckPoolLocked(ctx), outCheckSecurityNotInPool(ctx),
                        outCheckRestrictPool(ctx), outCheckFrozenPeriod(ctx));
            }
            if (failure != null) {
                throwApprovalRecheckFailure(log, failure);
            }
        }
    }

    /** 返回第一个非空失败原因。 */
    private String firstFailure(String... failures) {
        for (String failure : failures) {
            if (failure != null && !failure.isEmpty()) {
                return failure;
            }
        }
        return null;
    }

    /** 抛出包含调库项上下文的最终审批复核异常。 */
    private void throwApprovalRecheckFailure(IpAdjustLogBo log, String reason) {
        throw new BizException("证券[" + log.getSecurityCode() + "]" + log.getAdjustMode()
                + "投资池[" + log.getTargetPoolName() + "]失败：" + reason);
    }

    /**
     * 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码。
     */
    private String resolveItemSecurityCode(SecurityPoolAdjustSubmitReq req,
                                           SecurityPoolAdjustSubmitReq.AdjustItem item) {
        if (item != null && item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()) {
            return item.getSecurityCode();
        }
        return req.getSecurityCode();
    }

}
