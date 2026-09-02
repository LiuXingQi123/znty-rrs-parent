package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.CategoryType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.ItemType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchPoolTypeCountDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityCandidateDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
 * 证券池批量调整业务服务
 */
@Service
public class BatchSecurityPoolAdjustService {

    /** 短时间重复提交判定窗口（秒） */
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;
    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";
    /** 证券池批量调整数据访问组件 */
    @Resource
    private BatchSecurityPoolAdjustMapper batchSecurityPoolAdjustMapper;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 证券池调库数据访问组件 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** 证券池调整服务：校验/提交/直通判断与单券共用 */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

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
     * 批量调库下一步校验。
     *
     * <p>逐券委托单笔 {@link SecurityPoolAdjustService#checkAdjust}，流程候选与推荐规则与单券调库一致，
     * 不再注入目标池 batchIn/batchOut 专用流程。
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
                // 构建批量调库校验结果（透传单笔流程候选）
                dto.getItems().add(buildBatchCheckResult(security, item));
            }
        }
        return dto;
    }

    /**
     * 按提交明细实际选中的流程判断是否存在直通项；存在时落池前统一锁池复核整批状态。
     */
    private boolean needsWholeBatchDirectRecheck(BatchSecurityInboundAdjustReq req) {
        if (req.getItems() == null) {
            return false;
        }
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            if (!isManualBatchSubmitItem(item)) {
                continue;
            }
            // 直通判断复用单券服务（与单券提交 isDirectFlow 口径一致）
            if (securityPoolAdjustService.isDirectAdjustFlow(item.getFlowId(), item.getFlowKey())) {
                return true;
            }
        }
        return false;
    }

    /** 将批量请求转换为最终落池复核所需的日志快照。 */
    private List<IpAdjustLogBo> buildDirectRecheckLogList(BatchSecurityInboundAdjustReq req) {
        List<IpAdjustLogBo> logs = new ArrayList<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
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
    private void checkRecentBatchDuplicateSubmit(BatchSecurityInboundAdjustReq req) {
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
    private String buildBatchDuplicateItemKey(BatchSecurityInboundAdjustReq.AdjustItem item) {
        return item.getSecurityCode() + "|" + item.getTargetPoolId() + "|" + item.getAdjustMode()
                + "|" + item.getFlowId() + "|" + normalizeDuplicateText(item.getFlowKey());
    }

    /** 构造历史批量手工日志防重复比较键。 */
    private String buildBatchDuplicateItemKey(IpAdjustLogBo log) {
        return log.getSecurityCode() + "|" + log.getTargetPoolId() + "|" + log.getAdjustMode()
                + "|" + log.getFlowId() + "|" + normalizeDuplicateText(log.getFlowKey());
    }

    /** 在放开主体债矩阵时补充调整说明。 */
    private String buildBatchAdjustmentNote(BatchSecurityInboundAdjustReq req,
                                             BatchSecurityInboundAdjustReq.AdjustItem item) {
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
    public BatchSecurityInboundAdjustDto addAdjustLog(BatchSecurityInboundAdjustReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 批量提交调库申请及附件。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(
            BatchSecurityInboundAdjustReq req, List<MultipartFile> files) {
        return addAdjustLog(req, files, null);
    }

    /**
     * 批量提交调库申请及附件（可带前端原始文件名 JSON 数组）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(
            BatchSecurityInboundAdjustReq req, List<MultipartFile> files, String originalFileNameListJson) {
        // 校验批量调库提交参数
        validateAdjustSubmitReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());
        // 流程完全沿用前端按单笔 checkAdjust 候选所选（与单券调库一致），不再回填 batchIn/batchOut
        // 按完整证券集合和手工项集合检查近期重复批量申请
        checkRecentBatchDuplicateSubmit(req);
        // 存在直通落池项时，提交前统一锁池并复核整批状态
        if (needsWholeBatchDirectRecheck(req)) {
            securityPoolAdjustService.recheckBeforeFinalApproval(buildDirectRecheckLogList(req));
        }

        // 按触发主券分组，确保 related 与主券同次提交、共享批次与流程步骤
        Map<String, List<BatchSecurityInboundAdjustReq.AdjustItem>> itemMap = new LinkedHashMap<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            String groupKey = resolveBatchSubmitGroupKey(item);
            List<BatchSecurityInboundAdjustReq.AdjustItem> list = itemMap.get(groupKey);
            if (list == null) {
                list = new ArrayList<>();
                itemMap.put(groupKey, list);
            }
            list.add(item);
        }

        BatchSecurityInboundAdjustDto dto = new BatchSecurityInboundAdjustDto();
        dto.setSecurityCount(itemMap.size());
        dto.setSubmitCount(0);
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        // 创建批量提交附件上下文（整批共用，避免重复落盘）
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId(), originalFileNameList);
        // 整批共用单券批次号上下文，保证多证券批次号序号连续
        SecurityPoolAdjustService.BatchNoContext batchNoContext = new SecurityPoolAdjustService.BatchNoContext();
        for (Map.Entry<String, List<BatchSecurityInboundAdjustReq.AdjustItem>> entry : itemMap.entrySet()) {
            // 构建单证券调库提交请求后，委托单券 submitAdjustLog 落库（整批共享附件与批次号）
            AdjustSubmitDto submitDto = securityPoolAdjustService.submitAdjustLog(
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
        // 透传前端选中的担保人代码（简易流程第⑤条件担保人评级下调判断用）
        checkReq.setGuarantorCode(security.getGuarantorCode());
        checkReq.setReleaseRules("yes".equals(req.getReleaseRules()));
        checkReq.setItems(Collections.singletonList(item));
        return checkReq;
    }

    /**
     * 将单笔校验结果项映射为批量结果行。
     *
     * <p>流程候选直接透传单笔 {@code checkAdjust} 结果（白名单/简易/默认/特殊/升降级等），
     * 与证券池单券调库一致，不做批量专用流程注入。
     */
    private BatchSecurityInboundAdjustDto.CheckResultItem buildBatchCheckResult(
            BatchSecurityInboundAdjustReq.SecurityItem security,
            AdjustCheckDto.CheckResultItem item) {
        BatchSecurityInboundAdjustDto.CheckResultItem result = new BatchSecurityInboundAdjustDto.CheckResultItem();
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
            BatchSecurityInboundAdjustReq req,
            List<BatchSecurityInboundAdjustReq.AdjustItem> items) {
        // 请求级证券取触发主券（manual 项优先，否则 sourceSecurityCode / 首条）
        BatchSecurityInboundAdjustReq.AdjustItem primary = resolveBatchPrimaryItem(items);
        SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
        submitReq.setSecurityCode(resolveBatchSubmitGroupKey(primary));
        submitReq.setSecurityShortName(primary.getSecurityShortName());
        submitReq.setSecurityType(primary.getSecurityType());
        submitReq.setGuarantorCode(primary.getGuarantorCode());
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
        for (BatchSecurityInboundAdjustReq.AdjustItem item : items) {
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

    /** 归一化防重复比较文本。 */
    private String normalizeDuplicateText(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /** 在放开主体债矩阵时将审计说明追加到调整意见。 */
    private String buildBatchAdjustAdvice(BatchSecurityInboundAdjustReq req) {
        if (!"yes".equals(req.getReleaseRules())) {
            return req.getAdjustAdvice();
        }
        String advice = req.getAdjustAdvice();
        return advice == null || advice.trim().isEmpty() ? "放开主体债入库矩阵规则"
                : advice + "；放开主体债入库矩阵规则";
    }

    /**
     * 执行单证券调库校验（委托单笔服务，复用联动/互斥/多市场关联码扩批）。
     */
    private AdjustCheckDto checkSingleAdjust(AdjustCheckReq req) {
        return securityPoolAdjustService.checkAdjust(req);
    }

    /**
     * 批量提交分组键：related 归到触发主券，保证同批同流程。
     */
    private String resolveBatchSubmitGroupKey(BatchSecurityInboundAdjustReq.AdjustItem item) {
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
    private BatchSecurityInboundAdjustReq.AdjustItem resolveBatchPrimaryItem(
            List<BatchSecurityInboundAdjustReq.AdjustItem> items) {
        for (BatchSecurityInboundAdjustReq.AdjustItem item : items) {
            if (item.getItemTag() == null || item.getItemTag().isEmpty()
                    || ItemType.MANUAL.getCode().equals(item.getItemTag())) {
                return item;
            }
        }
        return items.get(0);
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
     * 填充当前页投资池现有证券数量（按类型分项 + 合计）。
     *
     * <p>统计 ip_pool_status 全部有效在池代码，按池状态 security_type 分 company / crmw / category_type，
     * 前端展示「主体：n只 / 债券：n只 …」；合计写入 currentCount。
     * 容量校验仍走单券链路全量 count，不受展示分项影响。
     */
    private void fillPoolCurrentCount(List<BatchSecurityPoolDto> poolList) {
        if (poolList.isEmpty()) {
            return;
        }
        List<Long> poolIds = poolList.stream()
                .map(BatchSecurityPoolDto::getId)
                .collect(Collectors.toList());
        List<BatchPoolTypeCountDto> typeCountList =
                batchSecurityPoolAdjustMapper.queryPoolCurrentCountByTypeList(poolIds);
        Map<Long, List<BatchPoolTypeCountDto>> byPoolId = new HashMap<>();
        if (typeCountList != null) {
            for (BatchPoolTypeCountDto row : typeCountList) {
                if (row == null || row.getPoolId() == null || row.getCount() == null || row.getCount() <= 0) {
                    continue;
                }
                byPoolId.computeIfAbsent(row.getPoolId(), key -> new ArrayList<>()).add(row);
            }
        }
        for (BatchSecurityPoolDto pool : poolList) {
            // 组装有序分项列表并汇总总数
            List<BatchPoolTypeCountDto> ordered = orderPoolTypeCounts(
                    byPoolId.getOrDefault(pool.getId(), Collections.emptyList()));
            pool.setCountByType(ordered);
            int total = 0;
            for (BatchPoolTypeCountDto item : ordered) {
                total += item.getCount() == null ? 0 : item.getCount();
            }
            pool.setCurrentCount(total);
        }
    }

    /**
     * 按业务固定顺序排列类型分项，并去掉嵌套中的 poolId（前端只关心 typeCode/count）。
     */
    private List<BatchPoolTypeCountDto> orderPoolTypeCounts(List<BatchPoolTypeCountDto> rawList) {
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
        List<BatchPoolTypeCountDto> sorted = new ArrayList<>(rawList);
        sorted.sort(new Comparator<BatchPoolTypeCountDto>() {
            @Override
            public int compare(BatchPoolTypeCountDto a, BatchPoolTypeCountDto b) {
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
        List<BatchPoolTypeCountDto> result = new ArrayList<>();
        for (BatchPoolTypeCountDto row : sorted) {
            BatchPoolTypeCountDto item = new BatchPoolTypeCountDto();
            item.setTypeCode(row.getTypeCode());
            item.setCount(row.getCount());
            result.add(item);
        }
        return result;
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
