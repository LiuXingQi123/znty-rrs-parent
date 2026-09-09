package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.ItemType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwAdjustDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwAdjustReq;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwCandidateDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolAdjustReq;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolTypeCountDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchCrmwPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * CRMW 池批量调整业务服务。
 *
 * <p>编排层：权限 / 候选 / 防重复 / 直通预检 / 分组 / 共享附件与批次号 / 整批事务。
 * 校验与落库全部委托 {@link CrmwPoolAdjustService}，不重复实现业务规则。
 */
@Service
public class BatchCrmwPoolAdjustService {

    /** 短时间重复提交判定窗口（秒） */
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;
    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";
    /** CRMW 凭证类型 */
    private static final String CRMW_SECURITY_TYPE = "crmw";

    /** CRMW 池批量调整数据访问组件 */
    @Resource
    private BatchCrmwPoolAdjustMapper batchCrmwPoolAdjustMapper;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** CRMW 池调整服务：校验/提交/直通判断与单笔共用 */
    @Resource
    private CrmwPoolAdjustService crmwPoolAdjustService;

    /**
     * 分页查询当前用户可调整的启用叶子 CRMW 投资池
     */
    public PageResult<BatchCrmwPoolDto> queryPoolPage(BatchCrmwPoolAdjustReq req) {
        // 处理当前用户可调整投资池筛选条件
        if (!prepareAdjustablePoolIds(req)) {
            return new PageResult<>(
                    new ArrayList<>(), 0L, req.getPageIndex(), req.getPageSize());
        }

        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchCrmwPoolDto> poolList = batchCrmwPoolAdjustMapper.queryPoolPage(req);
        PageInfo<BatchCrmwPoolDto> pageInfo = new PageInfo<>(poolList);
        // 填充当前页投资池现有组合数量
        fillPoolCurrentCount(poolList);
        // 填充投资池全路径名称
        fillPoolFullName(poolList);
        return new PageResult<>(
                poolList, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询目标池可调入候选组合
     */
    public PageResult<BatchCrmwCandidateDto> queryInboundCandidatePage(BatchCrmwPoolAdjustReq req) {
        // 校验候选组合查询参数
        validateCandidatePageReq(req);
        // 校验目标投资池调整权限
        validatePoolPermission(req);

        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchCrmwCandidateDto> list = batchCrmwPoolAdjustMapper.queryInboundCandidatePage(req);
        PageInfo<BatchCrmwCandidateDto> pageInfo = new PageInfo<>(list);

        // 将 SQL 返回的市场文本转换为市场编码列表，并补齐凭证类型
        fillCandidateFields(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询目标池可调出候选组合
     */
    public PageResult<BatchCrmwCandidateDto> queryOutboundCandidatePage(BatchCrmwPoolAdjustReq req) {
        // 校验候选组合查询参数
        validateCandidatePageReq(req);
        // 校验目标投资池调整权限
        validatePoolPermission(req);

        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchCrmwCandidateDto> list = batchCrmwPoolAdjustMapper.queryOutboundCandidatePage(req);
        PageInfo<BatchCrmwCandidateDto> pageInfo = new PageInfo<>(list);

        // 将 SQL 返回的市场文本转换为市场编码列表，并补齐凭证类型
        fillCandidateFields(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 批量调库下一步校验。
     *
     * <p>逐组合委托单笔 {@link CrmwPoolAdjustService#checkCrmwAdjust}，流程候选与推荐规则与单笔一致。
     */
    public BatchCrmwAdjustDto checkAdjust(BatchCrmwAdjustReq req) {
        // 校验批量调库下一步参数
        validateAdjustCheckReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());

        BatchCrmwAdjustDto dto = new BatchCrmwAdjustDto();
        for (BatchCrmwAdjustReq.SecurityItem security : req.getSecurities()) {
            // 构建单组合调库校验请求
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
     * 批量提交调库申请
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchCrmwAdjustDto addAdjustLog(BatchCrmwAdjustReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 批量提交调库申请及附件。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchCrmwAdjustDto addAdjustLog(
            BatchCrmwAdjustReq req, List<MultipartFile> files) {
        return addAdjustLog(req, files, null);
    }

    /**
     * 批量提交调库申请及附件（可带前端原始文件名 JSON 数组）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchCrmwAdjustDto addAdjustLog(
            BatchCrmwAdjustReq req, List<MultipartFile> files, String originalFileNameListJson) {
        // 校验批量调库提交参数
        validateAdjustSubmitReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());
        // 流程完全沿用前端按单笔 checkCrmwAdjust 候选所选（与单笔调库一致），不再回填 batchIn/batchOut
        // 按完整组合集合和手工项集合检查近期重复批量申请
        checkRecentBatchDuplicateSubmit(req);
        // 存在直通落池项时，提交前统一锁池并复核整批状态
        if (needsWholeBatchDirectRecheck(req)) {
            crmwPoolAdjustService.recheckBeforeFinalApproval(buildDirectRecheckLogList(req));
        }

        // 按触发主组合分组，确保 related 与主组合同次提交、共享批次与流程步骤
        Map<String, List<BatchCrmwAdjustReq.AdjustItem>> itemMap = new LinkedHashMap<>();
        for (BatchCrmwAdjustReq.AdjustItem item : req.getItems()) {
            String groupKey = resolveBatchSubmitGroupKey(item);
            List<BatchCrmwAdjustReq.AdjustItem> list = itemMap.get(groupKey);
            if (list == null) {
                list = new ArrayList<>();
                itemMap.put(groupKey, list);
            }
            list.add(item);
        }

        BatchCrmwAdjustDto dto = new BatchCrmwAdjustDto();
        dto.setSecurityCount(itemMap.size());
        dto.setSubmitCount(0);
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        // 创建批量提交附件上下文（整批共用，避免重复落盘）
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId(), originalFileNameList);
        // 整批共用单笔批次号上下文，保证多组合批次号序号连续
        CrmwPoolAdjustService.BatchNoContext batchNoContext = new CrmwPoolAdjustService.BatchNoContext();
        for (Map.Entry<String, List<BatchCrmwAdjustReq.AdjustItem>> entry : itemMap.entrySet()) {
            // 构建单组合调库提交请求后，委托单笔 submitAdjustLog 落库（整批共享附件与批次号）
            AdjustSubmitDto submitDto = crmwPoolAdjustService.submitAdjustLog(
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
     * 按提交明细实际选中的流程判断是否存在直通项；存在时落池前统一锁池复核整批状态。
     */
    private boolean needsWholeBatchDirectRecheck(BatchCrmwAdjustReq req) {
        if (req.getItems() == null) {
            return false;
        }
        for (BatchCrmwAdjustReq.AdjustItem item : req.getItems()) {
            if (!isManualBatchSubmitItem(item)) {
                continue;
            }
            // 直通判断复用单笔服务（与单笔提交 isDirectFlow 口径一致）
            if (crmwPoolAdjustService.isDirectAdjustFlow(item.getFlowId(), item.getFlowKey())) {
                return true;
            }
        }
        return false;
    }

    /** 将批量请求转换为最终落池复核所需的日志快照。 */
    private List<IpAdjustLogBo> buildDirectRecheckLogList(BatchCrmwAdjustReq req) {
        List<IpAdjustLogBo> logs = new ArrayList<>();
        for (BatchCrmwAdjustReq.AdjustItem item : req.getItems()) {
            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setSecurityCode(item.getSecurityCode());
            log.setSecurityShortName(item.getSecurityShortName());
            log.setSecurityType(item.getSecurityType());
            log.setCrmwName(item.getCrmwName());
            log.setCrmwScode(item.getCrmwScode());
            log.setCrmwStype(resolveCrmwStype(item.getCrmwStype()));
            log.setAdjustMode(item.getAdjustMode());
            log.setTargetPoolId(item.getTargetPoolId());
            log.setTargetPoolName(item.getTargetPoolName());
            logs.add(log);
        }
        return logs;
    }

    /** 按完整组合集合及手工调库项集合检查最近一次批量申请。 */
    private void checkRecentBatchDuplicateSubmit(BatchCrmwAdjustReq req) {
        List<IpAdjustLogBo> recent = batchCrmwPoolAdjustMapper.queryRecentBatchManualAdjustLogList(
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
    private String buildBatchDuplicateItemKey(BatchCrmwAdjustReq.AdjustItem item) {
        return resolveCrmwScode(item.getCrmwScode()) + "|" + item.getSecurityCode() + "|"
                + item.getTargetPoolId() + "|" + item.getAdjustMode()
                + "|" + item.getFlowId() + "|" + normalizeDuplicateText(item.getFlowKey());
    }

    /** 构造历史批量手工日志防重复比较键。 */
    private String buildBatchDuplicateItemKey(IpAdjustLogBo log) {
        return resolveCrmwScode(log.getCrmwScode()) + "|" + log.getSecurityCode() + "|"
                + log.getTargetPoolId() + "|" + log.getAdjustMode()
                + "|" + log.getFlowId() + "|" + normalizeDuplicateText(log.getFlowKey());
    }

    /** 在放开主体债矩阵时补充调整说明。 */
    private String buildBatchAdjustmentNote(BatchCrmwAdjustReq req,
                                            BatchCrmwAdjustReq.AdjustItem item) {
        if (!"yes".equals(req.getReleaseRules())) {
            return item.getAdjustmentNote();
        }
        String note = item.getAdjustmentNote();
        return (note == null || note.isEmpty()) ? "放开主体债入库矩阵规则"
                : note + "；放开主体债入库矩阵规则";
    }

    /**
     * 校验候选组合查询参数
     */
    private void validateCandidatePageReq(BatchCrmwPoolAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        if (batchCrmwPoolAdjustMapper.queryEnabledLeafCrmwPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用、不是叶子池或不是 CRMW 池");
        }
    }

    /**
     * 校验批量调库下一步参数
     */
    private void validateAdjustCheckReq(BatchCrmwAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (req.getSecurities() == null || req.getSecurities().isEmpty()) {
            throw new BizException("已选组合不能为空");
        }
        if (batchCrmwPoolAdjustMapper.queryEnabledLeafCrmwPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用、不是叶子池或不是 CRMW 池");
        }
        for (BatchCrmwAdjustReq.SecurityItem security : req.getSecurities()) {
            if (security.getSecurityCode() == null || security.getSecurityCode().isEmpty()) {
                throw new BizException("已选证券代码不能为空");
            }
            if (security.getCrmwScode() == null || security.getCrmwScode().isEmpty()) {
                throw new BizException("已选 CRMW 凭证代码不能为空");
            }
        }
    }

    /**
     * 校验批量调库提交参数
     */
    private void validateAdjustSubmitReq(BatchCrmwAdjustReq req) {
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
        if (batchCrmwPoolAdjustMapper.queryEnabledLeafCrmwPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用、不是叶子池或不是 CRMW 池");
        }
        // 解析批量调库中文方向
        String adjustMode = resolveAdjustMode(req);
        for (BatchCrmwAdjustReq.AdjustItem item : req.getItems()) {
            if (item.getSecurityCode() == null || item.getSecurityCode().isEmpty()) {
                throw new BizException("调库明细证券代码不能为空");
            }
            if (item.getCrmwScode() == null || item.getCrmwScode().isEmpty()) {
                throw new BizException("调库明细 CRMW 凭证代码不能为空");
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
    private boolean isManualBatchSubmitItem(BatchCrmwAdjustReq.AdjustItem item) {
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
    private String resolveAdjustMode(BatchCrmwAdjustReq req) {
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        return "out".equals(req.getDirection()) ? AdjustMode.OUT.getCode() : AdjustMode.IN.getCode();
    }

    /**
     * 校验批量调库目标池权限
     */
    private void validateAdjustPoolPermission(String currentUserId, Long poolId) {
        BatchCrmwPoolAdjustReq permissionReq = new BatchCrmwPoolAdjustReq();
        permissionReq.setCurrentUserId(currentUserId);
        permissionReq.setPoolId(poolId);
        // 校验目标投资池调整权限
        validatePoolPermission(permissionReq);
    }

    /**
     * 构建单组合调库校验请求
     */
    private AdjustCheckReq buildSingleCheckReq(BatchCrmwAdjustReq req,
                                               BatchCrmwAdjustReq.SecurityItem security) {
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
        checkReq.setCrmwName(security.getCrmwName());
        checkReq.setCrmwScode(security.getCrmwScode());
        checkReq.setCrmwStype(resolveCrmwStype(security.getCrmwStype()));
        checkReq.setGuarantorCode(security.getGuarantorCode());
        checkReq.setRightsHolderCode(security.getRightsHolderCode());
        checkReq.setSelfSelectedRightsHolderCode(security.getSelfSelectedRightsHolderCode());
        checkReq.setItems(Collections.singletonList(item));
        return checkReq;
    }

    /**
     * 将单笔校验结果项映射为批量结果行。
     *
     * <p>流程候选与凭证/标的身份直接透传单笔 {@code checkCrmwAdjust} 结果，
     * 与证券池批量透传 {@code checkAdjust} 一致，不做批量专用流程注入。
     */
    private BatchCrmwAdjustDto.CheckResultItem buildBatchCheckResult(
            BatchCrmwAdjustReq.SecurityItem security,
            AdjustCheckDto.CheckResultItem item) {
        BatchCrmwAdjustDto.CheckResultItem result = new BatchCrmwAdjustDto.CheckResultItem();
        // 关联扩批项优先用单笔结果中的代码，主项回退选中组合
        String securityCode = item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()
                ? item.getSecurityCode() : security.getSecurityCode();
        String securityShortName = item.getSecurityShortName() != null && !item.getSecurityShortName().isEmpty()
                ? item.getSecurityShortName() : security.getSecurityShortName();
        String securityType = item.getSecurityType() != null && !item.getSecurityType().isEmpty()
                ? item.getSecurityType() : security.getSecurityType();
        String crmwScode = item.getCrmwScode() != null && !item.getCrmwScode().isEmpty()
                ? item.getCrmwScode() : security.getCrmwScode();
        String crmwName = item.getCrmwName() != null && !item.getCrmwName().isEmpty()
                ? item.getCrmwName() : security.getCrmwName();
        String crmwStype = item.getCrmwStype() != null && !item.getCrmwStype().isEmpty()
                ? item.getCrmwStype() : resolveCrmwStype(security.getCrmwStype());
        String sourceSecurityCode = item.getSourceSecurityCode() != null && !item.getSourceSecurityCode().isEmpty()
                ? item.getSourceSecurityCode()
                : buildCombinationKey(crmwScode, securityCode);
        result.setSecurityCode(securityCode);
        result.setSecurityShortName(securityShortName);
        result.setSecurityType(securityType);
        result.setCrmwName(crmwName);
        result.setCrmwScode(crmwScode);
        result.setCrmwStype(resolveCrmwStype(crmwStype));
        result.setSourceSecurityCode(sourceSecurityCode);
        result.setTargetPoolId(item.getTargetPoolId());
        result.setPoolName(item.getPoolName());
        result.setPoolType(item.getPoolType());
        result.setAdjustMode(item.getAdjustMode());
        result.setItemTag(item.getItemTag());
        // 分组 Key 以触发主组合为前缀，便于联动/互斥与主项同批
        result.setAdjustGroupKey(sourceSecurityCode + "_" + item.getAdjustGroupKey());
        result.setCanAdjust(item.isCanAdjust());
        result.setFailReasons(item.getFailReasons() == null ? new ArrayList<>() : item.getFailReasons());
        result.setWarnings(item.getWarnings() == null ? new ArrayList<>() : item.getWarnings());
        // 透传单笔流程候选与推荐标识，前端默认选中 recommended 项
        result.setFlowOptions(item.getFlowOptions() == null
                ? new ArrayList<>() : new ArrayList<>(item.getFlowOptions()));
        return result;
    }

    /**
     * 构建单组合调库提交请求。
     */
    private CrmwPoolAdjustSubmitReq buildSingleSubmitReq(
            BatchCrmwAdjustReq req,
            List<BatchCrmwAdjustReq.AdjustItem> items) {
        // 请求级组合取触发主项（manual 项优先）
        BatchCrmwAdjustReq.AdjustItem primary = resolveBatchPrimaryItem(items);
        CrmwPoolAdjustSubmitReq submitReq = new CrmwPoolAdjustSubmitReq();
        submitReq.setSecurityCode(primary.getSecurityCode());
        submitReq.setSecurityShortName(primary.getSecurityShortName());
        submitReq.setSecurityType(primary.getSecurityType());
        submitReq.setCrmwName(primary.getCrmwName());
        submitReq.setCrmwScode(primary.getCrmwScode());
        submitReq.setCrmwStype(resolveCrmwStype(primary.getCrmwStype()));
        submitReq.setGuarantorCode(primary.getGuarantorCode());
        submitReq.setRightsHolderCode(primary.getRightsHolderCode());
        submitReq.setSelfSelectedRightsHolderCode(primary.getSelfSelectedRightsHolderCode());
        submitReq.setAdjustType("手动批量调整");
        submitReq.setAdjustReason(req.getAdjustReason());
        submitReq.setAdjustAdvice(buildBatchAdjustAdvice(req));
        submitReq.setAdjusterId(req.getAdjusterId());
        submitReq.setAdjusterName(req.getAdjusterName());

        List<CrmwPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
        for (BatchCrmwAdjustReq.AdjustItem item : items) {
            CrmwPoolAdjustSubmitReq.AdjustItem submitItem = new CrmwPoolAdjustSubmitReq.AdjustItem();
            submitItem.setTargetPoolId(item.getTargetPoolId());
            submitItem.setTargetPoolName(item.getTargetPoolName());
            submitItem.setPoolType(item.getPoolType() == null || item.getPoolType().isEmpty()
                    ? PoolType.CRMW.getCode() : item.getPoolType());
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
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** 在放开主体债矩阵时将审计说明追加到调整意见。 */
    private String buildBatchAdjustAdvice(BatchCrmwAdjustReq req) {
        if (!"yes".equals(req.getReleaseRules())) {
            return req.getAdjustAdvice();
        }
        String advice = req.getAdjustAdvice();
        return advice == null || advice.trim().isEmpty() ? "放开主体债入库矩阵规则"
                : advice + "；放开主体债入库矩阵规则";
    }

    /**
     * 执行单组合调库校验（委托单笔服务，复用联动/互斥扩批）。
     */
    private AdjustCheckDto checkSingleAdjust(AdjustCheckReq req) {
        return crmwPoolAdjustService.checkCrmwAdjust(req);
    }

    /**
     * 批量提交分组键：related 归到触发主组合。
     */
    private String resolveBatchSubmitGroupKey(BatchCrmwAdjustReq.AdjustItem item) {
        if (item == null) {
            return null;
        }
        if (item.getSourceSecurityCode() != null && !item.getSourceSecurityCode().isEmpty()) {
            return item.getSourceSecurityCode();
        }
        return buildCombinationKey(item.getCrmwScode(), item.getSecurityCode());
    }

    /**
     * 在同组提交项中优先取 manual 作为主组合代表。
     */
    private BatchCrmwAdjustReq.AdjustItem resolveBatchPrimaryItem(
            List<BatchCrmwAdjustReq.AdjustItem> items) {
        for (BatchCrmwAdjustReq.AdjustItem item : items) {
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
    private void validatePoolPermission(BatchCrmwPoolAdjustReq req) {
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
    private boolean prepareAdjustablePoolIds(BatchCrmwPoolAdjustReq req) {
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
     * 填充候选组合市场编码与凭证类型
     */
    private void fillCandidateFields(List<BatchCrmwCandidateDto> list) {
        for (BatchCrmwCandidateDto dto : list) {
            if (dto.getMarketCodeText() == null || dto.getMarketCodeText().isEmpty()) {
                dto.setMarketCodes(new ArrayList<>());
            } else {
                dto.setMarketCodes(Arrays.asList(dto.getMarketCodeText().split(",")));
            }
            dto.setCrmwStype(resolveCrmwStype(dto.getCrmwStype()));
        }
    }

    /**
     * 填充当前页投资池现有组合数量（按类型分项 + 合计）。
     */
    private void fillPoolCurrentCount(List<BatchCrmwPoolDto> poolList) {
        if (poolList.isEmpty()) {
            return;
        }
        List<Long> poolIds = poolList.stream()
                .map(BatchCrmwPoolDto::getId)
                .collect(Collectors.toList());
        List<BatchCrmwPoolTypeCountDto> typeCountList =
                batchCrmwPoolAdjustMapper.queryPoolCurrentCountByTypeList(poolIds);
        Map<Long, List<BatchCrmwPoolTypeCountDto>> byPoolId = new HashMap<>();
        if (typeCountList != null) {
            for (BatchCrmwPoolTypeCountDto row : typeCountList) {
                if (row == null || row.getPoolId() == null || row.getCount() == null || row.getCount() <= 0) {
                    continue;
                }
                byPoolId.computeIfAbsent(row.getPoolId(), key -> new ArrayList<>()).add(row);
            }
        }
        for (BatchCrmwPoolDto pool : poolList) {
            List<BatchCrmwPoolTypeCountDto> ordered = new ArrayList<>();
            for (BatchCrmwPoolTypeCountDto row : byPoolId.getOrDefault(pool.getId(), Collections.emptyList())) {
                BatchCrmwPoolTypeCountDto item = new BatchCrmwPoolTypeCountDto();
                item.setTypeCode(row.getTypeCode());
                item.setCount(row.getCount());
                ordered.add(item);
            }
            pool.setCountByType(ordered);
            int total = 0;
            for (BatchCrmwPoolTypeCountDto item : ordered) {
                total += item.getCount() == null ? 0 : item.getCount();
            }
            pool.setCurrentCount(total);
        }
    }

    /**
     * 填充投资池全路径名称
     */
    private void fillPoolFullName(List<BatchCrmwPoolDto> poolList) {
        if (poolList.isEmpty()) {
            return;
        }
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools.isEmpty()) {
            return;
        }
        Map<Long, InvestmentPoolBo> poolMap = allPools.stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, Function.identity()));
        for (BatchCrmwPoolDto dto : poolList) {
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

    /** 组合业务键：凭证代码 + 标的代码 */
    private String buildCombinationKey(String crmwScode, String securityCode) {
        return resolveCrmwScode(crmwScode) + "|" + (securityCode == null ? "" : securityCode);
    }

    /** 空凭证代码归一化为空串 */
    private String resolveCrmwScode(String crmwScode) {
        return crmwScode == null ? "" : crmwScode;
    }

    /** 空凭证类型回退为 crmw */
    private String resolveCrmwStype(String crmwStype) {
        return crmwStype == null || crmwStype.isEmpty() ? CRMW_SECURITY_TYPE : crmwStype;
    }
}
