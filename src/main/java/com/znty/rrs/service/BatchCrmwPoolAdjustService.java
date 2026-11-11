package com.znty.rrs.service;

import com.znty.rrs.entity.common.SecurityMarketDto;
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
import com.znty.rrs.entity.bo.SecurityInfoBo;
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
 * 处理页面发起的 CRMW 池批量调入和调出
 *
 * 1. 按用户权限查询可调整的池和候选组合
 * 2. 逐个组合执行调库校验，保留单笔流程候选并补充目标池配置的批量流程
 * 3. 提交前检查权限和重复申请，有直通项时先复核整批，再按来源组合分组提交
 *
 * 各组由 {@link CrmwPoolAdjustService} 执行校验和提交，共用本次上传的附件及批次号流水
 * 提交在同一事务中完成，任一组提交失败时整批回滚
 */
@Service
public class BatchCrmwPoolAdjustService {

    /** ABS 固定主体及在途身份查询 */
    @Resource
    private AbsIdentityService absIdentityService;

    /** 统一证券市场查询组件。 */
    @Resource
    private SecurityMarketService securityMarketService;

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
     * 分页查询可批量调整的 CRMW 池
     *
     * 执行顺序：
     * 1. 按用户权限确定可查询的池范围，范围为空时返回空页
     * 2. 分页查询投资池
     * 3. 补充在池数量和池全名
     *
     * @param req 当前用户、池筛选和分页条件
     * @return CRMW 池分页结果
     */
    public PageResult<BatchCrmwPoolDto> queryPoolPage(BatchCrmwPoolAdjustReq req) {
        // 1. 处理当前用户可调整投资池筛选条件
        if (!prepareAdjustablePoolIds(req)) {
            return new PageResult<>(
                    new ArrayList<>(), 0L, req.getPageIndex(), req.getPageSize());
        }

        // 2. 分页查询投资池
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchCrmwPoolDto> poolList = batchCrmwPoolAdjustMapper.queryPoolPage(req);
        PageInfo<BatchCrmwPoolDto> pageInfo = new PageInfo<>(poolList);
        // 3. 填充当前页投资池现有组合数量
        fillPoolCurrentCount(poolList);
        // 填充投资池全路径名称
        fillPoolFullName(poolList);
        return new PageResult<>(
                poolList, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询批量调入可选的 CRMW 组合
     *
     * 执行顺序：
     * 1. 校验目标池及用户调整权限
     * 2. 按筛选条件分页查询调入候选
     * 3. 补齐候选展示字段
     *
     * @param req 调入候选筛选和分页条件
     * @return 批量调入候选分页结果
     */
    public PageResult<BatchCrmwCandidateDto> queryInboundCandidatePage(BatchCrmwPoolAdjustReq req) {
        // 1. 校验候选组合查询参数
        validateCandidatePageReq(req);
        // 校验目标投资池调整权限
        validatePoolPermission(req);

        // 2. 按筛选条件分页查询调入候选
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchCrmwCandidateDto> list = batchCrmwPoolAdjustMapper.queryInboundCandidatePage(req);
        PageInfo<BatchCrmwCandidateDto> pageInfo = new PageInfo<>(list);

        // 3. 补齐 ABS 身份、市场代码和凭证类型
        fillCandidateFields(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询批量调出可选的 CRMW 组合
     *
     * 执行顺序：
     * 1. 校验目标池及用户调整权限
     * 2. 按筛选条件分页查询目标池内的组合
     * 3. 补齐候选展示字段
     *
     * @param req 调出候选筛选和分页条件
     * @return 批量调出候选分页结果
     */
    public PageResult<BatchCrmwCandidateDto> queryOutboundCandidatePage(BatchCrmwPoolAdjustReq req) {
        // 1. 校验候选组合查询参数
        validateCandidatePageReq(req);
        // 校验目标投资池调整权限
        validatePoolPermission(req);

        // 2. 按筛选条件分页查询目标池内的组合
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchCrmwCandidateDto> list = batchCrmwPoolAdjustMapper.queryOutboundCandidatePage(req);
        PageInfo<BatchCrmwCandidateDto> pageInfo = new PageInfo<>(list);

        // 3. 补齐 ABS 身份、市场代码和凭证类型
        fillCandidateFields(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 执行 CRMW 批量调整的下一步校验
     *
     * 执行顺序：
     * 1. 校验批量参数和当前用户的目标池调整权限
     * 2. 逐组合调用共用批量入口，执行单笔规则并追加已配置的批量流程
     * 3. 映射实际凭证、标的、分组和流程候选，保留业务失败及警告
     * 4. 按实际组合、目标池和方向去重，显式手工项优先，返回校验结果
     *
     * @param req 目标池、方向和选中的凭证标的组合
     * @return 批量校验结果及逐项流程候选
     */
    public BatchCrmwAdjustDto checkAdjust(BatchCrmwAdjustReq req) {
        // 1. 校验批量参数及当前用户目标池权限
        validateAdjustCheckReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());

        BatchCrmwAdjustDto dto = new BatchCrmwAdjustDto();
        // 2. 逐组合复用完整单笔规则，并追加已配置的批量流程
        for (BatchCrmwAdjustReq.SecurityItem security : req.getSecurities()) {
            // 构建单组合调库校验请求
            AdjustCheckDto checkDto = checkSingleAdjust(buildSingleCheckReq(req, security));
            if (checkDto == null || checkDto.getItems() == null) {
                continue;
            }
            for (AdjustCheckDto.CheckResultItem item : checkDto.getItems()) {
                // 3. 映射实际组合身份、失败原因和已生成的流程候选
                dto.getItems().add(buildBatchCheckResult(security, item));
            }
        }
        // 4. 按实际组合、目标池和方向去重，显式手工项优先
        Map<String, BatchCrmwAdjustDto.CheckResultItem> uniqueItems = new LinkedHashMap<>();
        for (BatchCrmwAdjustDto.CheckResultItem item : dto.getItems()) {
            String key = item.getCrmwScode() + "|" + item.getSecurityCode() + "|"
                    + item.getTargetPoolId() + "|" + item.getAdjustMode();
            BatchCrmwAdjustDto.CheckResultItem existing = uniqueItems.get(key);
            if (existing == null || ItemType.MANUAL.getCode().equals(item.getItemTag())) {
                uniqueItems.put(key, item);
            }
        }
        dto.setItems(new ArrayList<>(uniqueItems.values()));
        return dto;
    }

    /**
     * 提交不含上传文件的 CRMW 批量调库申请
     *
     * 执行顺序：
     * 1. 使用空文件列表调用带附件的批量提交入口
     *
     * @param req 批量调库申请
     * @return 整批提交条数和日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchCrmwAdjustDto addAdjustLog(BatchCrmwAdjustReq req) {
        // 1. 使用空文件列表委托带附件的批量提交入口
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 提交包含上传文件的 CRMW 批量调库申请
     *
     * 执行顺序：
     * 1. 将申请、文件和空原始文件名列表交给完整提交入口
     *
     * @param req 批量调库申请
     * @param files 本次上传的附件列表
     * @return 整批提交条数和日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchCrmwAdjustDto addAdjustLog(
            BatchCrmwAdjustReq req, List<MultipartFile> files) {
        // 1. 将上传文件及空原始文件名列表交给完整提交入口
        return addAdjustLog(req, files, null);
    }

    /**
     * 提交 CRMW 批量申请及附件，并保留用户实际选择的流程
     *
     * 整批在一个事务内处理，任一组异常全部回滚；普通批量不采用 Excel 业务提示放行口径
     *
     * 执行顺序：
     * 1. 校验提交参数、目标池权限及近期重复批量申请
     * 2. 存在直通项时，按最终审批口径提前复核整批动态状态
     * 3. 按触发主组合分组，使关联项与主组合同次提交
     * 4. 准备整批共享的附件和批次号上下文，避免文件重复保存
     * 5. 逐组构造单组合请求并委托核心服务提交，汇总条数和日志 ID
     *
     * @param req 含实际流程选择的批量申请
     * @param files 本次上传的附件列表
     * @param originalFileNameListJson 前端原始文件名 JSON 数组
     * @return 整批提交结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchCrmwAdjustDto addAdjustLog(
            BatchCrmwAdjustReq req, List<MultipartFile> files, String originalFileNameListJson) {
        // 1. 校验提交参数、目标池权限和近期重复批量申请
        validateAdjustSubmitReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());
        // 保留前端实际选择的单笔或批量流程，提交时不覆盖用户选择
        // 按完整组合集合和手工项集合检查近期重复批量申请
        checkRecentBatchDuplicateSubmit(req);
        // 2. 存在直通项时，提交前统一复核整批状态
        if (needsWholeBatchDirectRecheck(req)) {
            // 将批量请求转换为最终落池复核所需的日志快照
            crmwPoolAdjustService.recheckBeforeFinalApproval(buildDirectRecheckLogList(req));
        }

        // 3. 按触发主组合分组，使关联项与主组合同次提交
        Map<String, List<BatchCrmwAdjustReq.AdjustItem>> itemMap = new LinkedHashMap<>();
        for (BatchCrmwAdjustReq.AdjustItem item : req.getItems()) {
            // 批量提交分组键：related 归到触发主组合
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
        // 4. 准备整批共享的附件和批次号上下文
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        // 创建批量提交附件上下文（整批共用，避免重复落盘）
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId(), originalFileNameList);
        // 整批共用单笔批次号上下文，保证多组合批次号序号连续
        CrmwPoolAdjustService.BatchNoContext batchNoContext = new CrmwPoolAdjustService.BatchNoContext();
        // 5. 逐组委托核心服务提交，并汇总条数和日志 ID
        for (Map.Entry<String, List<BatchCrmwAdjustReq.AdjustItem>> entry : itemMap.entrySet()) {
            // 构建单组合调库提交请求后，委托单笔 submitAdjustLog 落库（整批共享附件与批次号）
            AdjustSubmitDto submitDto = crmwPoolAdjustService.submitAdjustLog(
                    // 构建单组合调库提交请求
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
     * 判断本次批量提交是否需要整批提前复核
     *
     * 执行顺序：
     * 1. 遍历手工调整项并判断其所选流程能否直接完成，找到一项即可触发
     *
     * @param req 批量调整请求
     * @return 存在无需人工审批即可完成的手工项时返回 true
     */
    private boolean needsWholeBatchDirectRecheck(BatchCrmwAdjustReq req) {
        if (req.getItems() == null) {
            return false;
        }
        for (BatchCrmwAdjustReq.AdjustItem item : req.getItems()) {
            // 判断批量提交项是否为手工调整项
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

    /**
     * 准备整批直接生效前的复核记录
     *
     * 执行顺序：
     * 1. 将待提交项的凭证、标的、池和方向转为复核所需的日志数据
     *
     * @param req 批量调整请求
     * @return 仅供复核使用的日志列表
     */
    private List<IpAdjustLogBo> buildDirectRecheckLogList(BatchCrmwAdjustReq req) {
        List<IpAdjustLogBo> logs = new ArrayList<>();
        for (BatchCrmwAdjustReq.AdjustItem item : req.getItems()) {
            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setSecurityCode(item.getSecurityCode());
            log.setSecurityShortName(item.getSecurityShortName());
            log.setSecurityType(item.getSecurityType());
            log.setCrmwName(item.getCrmwName());
            log.setCrmwScode(item.getCrmwScode());
            // 凭证类型缺失时按 crmw 处理
            log.setCrmwStype(resolveCrmwStype(item.getCrmwStype()));
            log.setAdjustMode(item.getAdjustMode());
            log.setTargetPoolId(item.getTargetPoolId());
            log.setTargetPoolName(item.getTargetPoolName());
            logs.add(log);
        }
        return logs;
    }

    /**
     * 拦截短时间内内容相同的批量提交
     *
     * 执行顺序：
     * 1. 查询提交人近期的批量调整日志
     * 2. 对照排序后的手工项组合、目标池、方向和流程
     * 3. 调整项、原因及意见均相同时提示重复提交
     *
     * @param req 用于比较调整项、原因和意见的批量提交请求
     */
    private void checkRecentBatchDuplicateSubmit(BatchCrmwAdjustReq req) {
        List<IpAdjustLogBo> recent = batchCrmwPoolAdjustMapper.queryRecentBatchManualAdjustLogList(
                req.getAdjusterId(), DUPLICATE_SUBMIT_WINDOW_SECONDS);
        if (recent == null || recent.isEmpty()) {
            return;
        }
        // 判断批量提交项是否为手工调整项
        List<String> requestKeys = req.getItems().stream().filter(this::isManualBatchSubmitItem)
                // 生成批量历史日志的重复提交比对键
                .map(this::buildBatchDuplicateItemKey).sorted().collect(Collectors.toList());
        // 生成批量历史日志的重复提交比对键
        List<String> historyKeys = recent.stream().map(this::buildBatchDuplicateItemKey)
                .sorted().collect(Collectors.toList());
        boolean sameText = recent.stream().allMatch(log ->
                // 去掉首尾空白，统一空值后再比较
                Objects.equals(normalizeDuplicateText(req.getAdjustReason()),
                        // 去掉首尾空白，统一空值后再比较
                        normalizeDuplicateText(log.getAdjustReason()))
                        // 去掉首尾空白，统一空值后再比较，并生成写入批量调整日志的调整意见
                        && Objects.equals(normalizeDuplicateText(buildBatchAdjustAdvice(req)),
                        // 去掉首尾空白，统一空值后再比较
                        normalizeDuplicateText(log.getAdjustAdvice())));
        if (!requestKeys.isEmpty() && requestKeys.equals(historyKeys) && sameText) {
            throw new BizException("调库申请已提交，请勿重复操作");
        }
    }

    /**
     * 生成批量请求项的重复提交比对键
     *
     * 执行顺序：
     * 1. 按统一顺序拼接凭证、标的、目标池、方向和流程信息
     *
     * @param item 当前调整项
     * @return 当前请求项的比对键
     */
    private String buildBatchDuplicateItemKey(BatchCrmwAdjustReq.AdjustItem item) {
        // 凭证代码缺失时按空字符串处理
        return resolveCrmwScode(item.getCrmwScode()) + "|" + item.getSecurityCode() + "|"
                + item.getTargetPoolId() + "|" + item.getAdjustMode()
                // 去掉首尾空白，统一空值后再比较
                + "|" + item.getFlowId() + "|" + normalizeDuplicateText(item.getFlowKey());
    }

    /**
     * 生成批量历史日志的重复提交比对键
     *
     * 执行顺序：
     * 1. 按与请求项相同的顺序拼接凭证、标的、目标池、方向和流程信息
     *
     * @param log 当前调库日志
     * @return 当前历史日志的比对键
     */
    private String buildBatchDuplicateItemKey(IpAdjustLogBo log) {
        // 凭证代码缺失时按空字符串处理
        return resolveCrmwScode(log.getCrmwScode()) + "|" + log.getSecurityCode() + "|"
                + log.getTargetPoolId() + "|" + log.getAdjustMode()
                // 去掉首尾空白，统一空值后再比较
                + "|" + log.getFlowId() + "|" + normalizeDuplicateText(log.getFlowKey());
    }

    /**
     * 整理批量校验结果中的调整说明
     *
     * 执行顺序：
     * 1. 未选择放开矩阵规则时保留原说明，选择后补上该选项的文字
     *
     * @param req 含放开矩阵规则选项的批量调整请求
     * @param item 当前调整项
     * @return 批量调整项的展示说明
     */
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
     * 检查批量候选查询的目标池
     *
     * 执行顺序：
     * 1. 检查目标池 ID 是否已填
     * 2. 确认它是启用的叶子 CRMW 池
     *
     * @param req 含目标池 ID 的候选查询条件
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
     * 检查批量调库校验请求
     *
     * 执行顺序：
     * 1. 检查目标池、方向和候选列表是否已填
     * 2. 确认目标池是启用的叶子 CRMW 池
     * 3. 逐项检查凭证及标的代码是否齐全
     *
     * @param req 含目标池、方向和候选组合的检查请求
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
     * 检查批量调库提交请求
     *
     * 执行顺序：
     * 1. 检查目标池、方向、调整列表和发起人信息
     * 2. 确认目标池是启用的叶子 CRMW 池
     * 3. 逐项检查组合代码、目标池和方向，手工项方向须与批量方向一致
     *
     * @param req 含方向、调整项和发起人信息的批量提交请求
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
     * 判断批量提交项是否为手工调整项
     *
     * 执行顺序：
     * 1. 将未填写来源标记和明确标为手工的提交项视为手工项
     *
     * @param item 当前调整项
     * @return 提交项为手工项时返回 true
     */
    private boolean isManualBatchSubmitItem(BatchCrmwAdjustReq.AdjustItem item) {
        return item.getItemTag() == null || item.getItemTag().isEmpty() || ItemType.MANUAL.getCode().equals(item.getItemTag());
    }

    /**
     * 检查批量调整方向是否合法
     *
     * 执行顺序：
     * 1. 只接受调入和调出对应的方向编码，其他值提示错误
     *
     * @param direction 请求中的调整方向编码
     */
    private void validateAdjustDirection(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
    }

    /**
     * 将批量请求方向转换为调库方向
     *
     * 执行顺序：
     * 1. 校验方向编码后，将其转换成调入或调出
     *
     * @param req 含方向编码的批量请求
     * @return 调库校验使用的调整方向
     */
    private String resolveAdjustMode(BatchCrmwAdjustReq req) {
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        return "out".equals(req.getDirection()) ? AdjustMode.OUT.getCode() : AdjustMode.IN.getCode();
    }

    /**
     * 确认当前用户可以批量调整目标池
     *
     * 执行顺序：
     * 1. 用当前用户和目标池构造权限查询条件
     * 2. 按投资池调整权限验证该用户
     *
     * @param currentUserId 当前用户 ID
     * @param poolId 投资池 ID
     */
    private void validateAdjustPoolPermission(String currentUserId, Long poolId) {
        BatchCrmwPoolAdjustReq permissionReq = new BatchCrmwPoolAdjustReq();
        permissionReq.setCurrentUserId(currentUserId);
        permissionReq.setPoolId(poolId);
        // 校验目标投资池调整权限
        validatePoolPermission(permissionReq);
    }

    /**
     * 把一条批量候选转为单笔调库校验请求
     *
     * 执行顺序：
     * 1. 取得批量请求指定的目标池和调整方向
     * 2. 带入凭证、标的及选中的评级主体资料
     *
     * @param req 批量调整请求
     * @param security 当前 CRMW 组合候选
     * @return 用于检查当前组合的单笔调库请求
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
        // 凭证类型缺失时按 crmw 处理
        checkReq.setCrmwStype(resolveCrmwStype(security.getCrmwStype()));
        checkReq.setSelectedRatingCompany(security.getSelectedRatingCompany());
        checkReq.setGuarantorCode(security.getGuarantorCode());
        checkReq.setRightsHolderCode(security.getRightsHolderCode());
        checkReq.setSelfSelectedRightsHolderCode(security.getSelfSelectedRightsHolderCode());
        checkReq.setItems(Collections.singletonList(item));
        return checkReq;
    }

    /**
     * 将共用校验结果转换为批量页面的一行
     *
     * 执行顺序：
     * 1. 优先使用校验返回的实际标的和凭证身份，主项缺省展示值取选中的组合
     * 2. 以触发主组合补充分组键，保留方向、来源、可调整状态、失败原因和警告
     * 3. 透传已生成的单笔及批量候选和推荐标志，不重复追加流程
     *
     * @param security 当前选中的来源组合
     * @param item 共用校验返回的实际调整项
     * @return 批量页面校验结果行
     */
    private BatchCrmwAdjustDto.CheckResultItem buildBatchCheckResult(
            BatchCrmwAdjustReq.SecurityItem security,
            AdjustCheckDto.CheckResultItem item) {
        BatchCrmwAdjustDto.CheckResultItem result = new BatchCrmwAdjustDto.CheckResultItem();
        // 1. 优先使用校验返回的实际标的及凭证身份，缺省展示值取来源组合
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
                // 空凭证类型回退为 crmw
                ? item.getCrmwStype() : resolveCrmwStype(security.getCrmwStype());
        String sourceSecurityCode = item.getSourceSecurityCode() != null && !item.getSourceSecurityCode().isEmpty()
                ? item.getSourceSecurityCode()
                // 组合业务键：凭证代码 + 标的代码
                : buildCombinationKey(crmwScode, securityCode);
        result.setSecurityCode(securityCode);
        result.setSecurityShortName(securityShortName);
        result.setSecurityType(securityType);
        result.setCrmwName(crmwName);
        result.setCrmwScode(crmwScode);
        // 空凭证类型回退为 crmw
        result.setCrmwStype(resolveCrmwStype(crmwStype));
        result.setSourceSecurityCode(sourceSecurityCode);
        result.setTargetPoolId(item.getTargetPoolId());
        result.setPoolName(item.getPoolName());
        result.setPoolType(item.getPoolType());
        result.setAdjustMode(item.getAdjustMode());
        result.setItemTag(item.getItemTag());
        // 2. 补充分组键，保留调整方向、来源、失败原因和警告
        result.setAdjustGroupKey(sourceSecurityCode + "_" + item.getAdjustGroupKey());
        result.setCanAdjust(item.isCanAdjust());
        result.setFailReasons(item.getFailReasons() == null ? new ArrayList<>() : item.getFailReasons());
        result.setWarnings(item.getWarnings() == null ? new ArrayList<>() : item.getWarnings());
        // 3. 透传已生成的单笔和批量候选及推荐标志
        result.setFlowOptions(item.getFlowOptions() == null
                ? new ArrayList<>() : new ArrayList<>(item.getFlowOptions()));
        return result;
    }

    /**
     * 把同一组合的批量结果转为单笔提交请求
     *
     * 组内关联项跟随触发它的手工主项提交，保留每项实际的标的代码
     *
     * 执行顺序：
     * 1. 以组内手工主项填充组合及评级主体信息，标记为手动批量
     * 2. 带入申请说明和提交人，并逐项复制池、方向、分组、流程及材料信息
     * 3. 整理组内同券跨市场代码
     *
     * @param req 批量调整请求
     * @param items 同一来源组合的调整项列表
     * @return 一组批量结果对应的调库提交请求
     */
    private CrmwPoolAdjustSubmitReq buildSingleSubmitReq(
            BatchCrmwAdjustReq req,
            List<BatchCrmwAdjustReq.AdjustItem> items) {
        // 1. 请求级组合取触发主项（manual 项优先）
        BatchCrmwAdjustReq.AdjustItem primary = resolveBatchPrimaryItem(items);
        // 2. 构造提交请求并保留各项所选流程及材料
        CrmwPoolAdjustSubmitReq submitReq = new CrmwPoolAdjustSubmitReq();
        submitReq.setSecurityCode(primary.getSecurityCode());
        submitReq.setSecurityShortName(primary.getSecurityShortName());
        submitReq.setSecurityType(primary.getSecurityType());
        submitReq.setCrmwName(primary.getCrmwName());
        submitReq.setCrmwScode(primary.getCrmwScode());
        // 凭证类型缺失时按 crmw 处理
        submitReq.setCrmwStype(resolveCrmwStype(primary.getCrmwStype()));
        submitReq.setSelectedRatingCompany(primary.getSelectedRatingCompany());
        submitReq.setGuarantorCode(primary.getGuarantorCode());
        submitReq.setRightsHolderCode(primary.getRightsHolderCode());
        submitReq.setSelfSelectedRightsHolderCode(primary.getSelfSelectedRightsHolderCode());
        submitReq.setAdjustType("手动批量调整");
        submitReq.setAdjustReason(req.getAdjustReason());
        // 生成写入批量调整日志的调整意见
        submitReq.setAdjustAdvice(buildBatchAdjustAdvice(req));
        submitReq.setAdjusterId(req.getAdjusterId());
        submitReq.setAdjusterName(req.getAdjusterName());

        List<CrmwPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
        for (BatchCrmwAdjustReq.AdjustItem item : items) {
            CrmwPoolAdjustSubmitReq.AdjustItem submitItem = new CrmwPoolAdjustSubmitReq.AdjustItem();
            submitItem.setSecurityCode(item.getSecurityCode());
            submitItem.setSecurityShortName(item.getSecurityShortName());
            submitItem.setSecurityType(item.getSecurityType());
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
            // 整理批量校验结果中的调整说明
            submitItem.setAdjustmentNote(buildBatchAdjustmentNote(req, item));
            submitItem.setCreditReportFileIndexes(item.getCreditReportFileIndexes());
            submitItem.setMaterialFileIndexes(item.getMaterialFileIndexes());
            submitItem.setCreditReportSourceAttachmentIds(item.getCreditReportSourceAttachmentIds());
            submitItem.setMaterialSourceAttachmentIds(item.getMaterialSourceAttachmentIds());
            submitItems.add(submitItem);
        }
        // 3. 复用本次校验后传入的实际代码集合，提交阶段不再查询同券关系
        submitReq.setRelatedMarketCodes(items.stream().map(item -> item.getSecurityCode())
                .filter(code -> code != null && !code.equals(submitReq.getSecurityCode())).distinct()
                .map(code -> {
                    SecurityMarketDto related = new SecurityMarketDto();
                    related.setWindCode(code);
                    return related;
                }).collect(Collectors.toList()));
        submitReq.setItems(submitItems);
        return submitReq;
    }

    /**
     * 统一重复提交比较中的文本
     *
     * 执行顺序：
     * 1. 去掉首尾空白，空值或纯空白统一为 null
     *
     * @param value 待统一空值的文本
     * @return 去除首尾空白后的文本
     */
    private String normalizeDuplicateText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * 生成写入批量调整日志的调整意见
     *
     * 执行顺序：
     * 1. 未选放开矩阵规则时保留原意见，选中时补上该选项的文字
     *
     * @param req 含调整意见和放开矩阵规则选项的批量请求
     * @return 本次批量调整的意见
     */
    private String buildBatchAdjustAdvice(BatchCrmwAdjustReq req) {
        if (!"yes".equals(req.getReleaseRules())) {
            return req.getAdjustAdvice();
        }
        String advice = req.getAdjustAdvice();
        return advice == null || advice.trim().isEmpty() ? "放开主体债入库矩阵规则"
                : advice + "；放开主体债入库矩阵规则";
    }

    /**
     * 调用共用入口校验一个 CRMW 批量组合
     *
     * 执行顺序：
     * 1. 委托核心服务执行完整单笔规则和关联市场展开，并追加已配置的批量流程
     *
     * @param req 当前组合校验请求
     * @return 包含单笔和批量候选的校验结果
     */
    private AdjustCheckDto checkSingleAdjust(AdjustCheckReq req) {
        // 1. 复用核心批量校验，保留单笔规则并追加配置的批量流程
        return crmwPoolAdjustService.checkBatchAdjust(req);
    }

    /**
     * 确定批量提交中同一来源组合的分组键
     *
     * 跨市场关联项归到触发它的主组合，避免拆成独立申请
     *
     * 执行顺序：
     * 1. 优先使用候选结果中的来源标识，缺失时用凭证和标的代码拼接
     *
     * @param item 当前调整项
     * @return 来源组合分组键，调整项为空时为 null
     */
    private String resolveBatchSubmitGroupKey(BatchCrmwAdjustReq.AdjustItem item) {
        if (item == null) {
            return null;
        }
        if (item.getSourceSecurityCode() != null && !item.getSourceSecurityCode().isEmpty()) {
            return item.getSourceSecurityCode();
        }
        // 用凭证和标的代码标识一个 CRMW 组合
        return buildCombinationKey(item.getCrmwScode(), item.getSecurityCode());
    }

    /**
     * 找到一组批量调整中的主项
     *
     * 执行顺序：
     * 1. 优先选第一条手工项，没有手工项时使用组内第一条
     *
     * @param items 同一来源组合的调整项列表
     * @return 用于填充组合及评级主体信息的主项
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
     * 检查当前用户是否有目标池的调整权限
     *
     * 执行顺序：
     * 1. 管理员直接通过
     * 2. 其他用户查询可调整池，目标池不在其中时提示无权限
     *
     * @param req 含当前用户和目标池 ID 的权限查询条件
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
     * 按权限缩小批量池查询的范围
     *
     * 执行顺序：
     * 1. 管理员保留原查询范围
     * 2. 普通用户查询可调整池，未指定范围时使用全部可调整池，指定时与可调整池取交集
     *
     * @param req 待补充可调整池范围的查询条件
     * @return 有可查询范围或用户为管理员时返回 true
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
     * 查询普通用户有调整权限的池
     *
     * 执行顺序：
     * 1. 解析用户 ID 并查询其角色
     * 2. 匹配直接授予用户或授予角色的调整权限
     *
     * @param currentUserId 当前用户 ID
     * @return 用户可调整的池 ID 集合
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
     * 解析批量调整的当前用户 ID
     *
     * 执行顺序：
     * 1. 检查用户 ID 是否为空
     * 2. 去掉首尾空白后转换成数字，格式不对时提示参数错误
     *
     * @param currentUserId 当前用户 ID
     * @return 数字格式的用户 ID
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
     * 判断批量调整用户是否为管理员
     *
     * 执行顺序：
     * 1. 将当前用户 ID 与管理员 ID 比较
     *
     * @param currentUserId 当前用户 ID
     * @return 用户为管理员时返回 true
     */
    private boolean isAdminUser(String currentUserId) {
        return ADMIN_USER_ID.equals(currentUserId);
    }

    /**
     * 补全批量候选的展示字段
     *
     * 执行顺序：
     * 1. 批量查询 ABS 标的当前身份并回填
     * 2. 批量查询标的市场代码，补充市场和凭证类型
     *
     * @param list 待补充展示信息的批量候选
     */
    private void fillCandidateFields(List<BatchCrmwCandidateDto> list) {
        List<String> absCodes = list.stream()
                .filter(row -> "abs".equals(row.getSecurityType()) || Integer.valueOf(1).equals(row.getAbsFlag()))
                .map(BatchCrmwCandidateDto::getSecurityCode).distinct().collect(Collectors.toList());
        if (!absCodes.isEmpty()) {
            Map<String, SecurityInfoBo> identities = absIdentityService.queryCurrentIdentities(absCodes);
            for (BatchCrmwCandidateDto row : list) row.setAbsIdentity(identities.get(row.getSecurityCode()));
        }
        List<String> codes = new ArrayList<>();
        for (BatchCrmwCandidateDto dto : list) {
            codes.add(dto.getSecurityCode());
        }
        Map<String, String> markets = securityMarketService.queryMarketCodeMap(codes);
        for (BatchCrmwCandidateDto dto : list) {
            String market = markets.get(dto.getSecurityCode());
            dto.setMarketCodeText(market);
            dto.setMarketCodes(Collections.singletonList(market));
            // 凭证类型缺失时按 crmw 处理
            dto.setCrmwStype(resolveCrmwStype(dto.getCrmwStype()));
        }
    }

    /**
     * 补全批量池列表的在池数量
     *
     * 执行顺序：
     * 1. 按池批量查询各标的类型的在池数量
     * 2. 汇总各类型数量并填入每个池的明细和总数
     *
     * @param poolList 待补充展示字段的投资池列表
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
     * 补全批量池列表的层级名称
     *
     * 执行顺序：
     * 1. 读取全部池并按 ID 建立索引
     * 2. 逐个生成包含上级池的全名
     *
     * @param poolList 待补充展示字段的投资池列表
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
     * 生成批量池的层级全名
     *
     * 执行顺序：
     * 1. 按 ID 查池，找不到时返回空字符串
     * 2. 沿父池递归拼接各级名称
     *
     * @param poolId 投资池 ID
     * @param poolMap 按池 ID 整理的投资池配置
     * @return 用斜线连接的投资池全名
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

    /**
     * 用凭证和标的代码标识一个 CRMW 组合
     *
     * 执行顺序：
     * 1. 将缺失代码按空字符串处理，再以竖线拼接两个代码
     *
     * @param crmwScode CRMW 凭证代码
     * @param securityCode 标的证券代码
     * @return CRMW 组合标识
     */
    private String buildCombinationKey(String crmwScode, String securityCode) {
        // 凭证代码缺失时按空字符串处理
        return resolveCrmwScode(crmwScode) + "|" + (securityCode == null ? "" : securityCode);
    }

    /**
     * 取得批量组合使用的凭证代码
     *
     * 执行顺序：
     * 1. 缺失时使用空字符串，其余保留原值
     *
     * @param crmwScode CRMW 凭证代码
     * @return 凭证代码
     */
    private String resolveCrmwScode(String crmwScode) {
        return crmwScode == null ? "" : crmwScode;
    }

    /**
     * 取得批量组合使用的凭证类型
     *
     * 执行顺序：
     * 1. 未填凭证类型时使用 crmw，其余保留原值
     *
     * @param crmwStype CRMW 凭证的证券类型
     * @return 凭证证券类型
     */
    private String resolveCrmwStype(String crmwStype) {
        return crmwStype == null || crmwStype.isEmpty() ? CRMW_SECURITY_TYPE : crmwStype;
    }
}
