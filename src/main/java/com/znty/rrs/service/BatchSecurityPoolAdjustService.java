package com.znty.rrs.service;

import com.znty.rrs.entity.common.SecurityMarketDto;
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
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderReq;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
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
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 处理页面发起的证券池批量调入和调出
 *
 * 按用户权限查询可调整投资池和候选证券，由 {@link BatchSecurityCheckService} 批量准备校验数据并逐券判断
 * 提交前复核整批直通项，按触发主券分组，将同券跨市场调整项与主券一起交给 {@link SecurityPoolAdjustService} 保存
 * 各分组共用本次上传的附件和批次号流水
 */
@Service
public class BatchSecurityPoolAdjustService {

    /** ABS 当前主体占用 */
    @Resource
    private AbsIdentityService absIdentityService;

    /** 统一证券市场查询组件 */
    @Resource
    private SecurityMarketService securityMarketService;

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

    /** 批量页面与定时任务共用的校验服务 */
    @Resource
    private BatchSecurityCheckService batchSecurityCheckService;

    /**
     * 分页查询当前用户可调整的启用叶子投资池
     *
     * 执行顺序：
     * 1. 处理当前用户可调整投资池筛选条件
     * 2. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 3. 填充当前页投资池现有证券数量
     * 4. 填充投资池全路径名称
     * 5. 将本页记录和查询总条数一起返回
     *
     * @param req 批次证券投资池调库
     * @return 当前用户可调整的投资池和分页信息
     */
    public PageResult<BatchSecurityPoolDto> queryPoolPage(BatchSecurityPoolAdjustReq req) {
        // 1. 处理当前用户可调整投资池筛选条件
        if (!prepareAdjustablePoolIds(req)) {
            return new PageResult<>(
                    new ArrayList<>(), 0L, req.getPageIndex(), req.getPageSize());
        }

        // 2. 按页码和筛选条件查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchSecurityPoolDto> poolList = batchSecurityPoolAdjustMapper.queryPoolPage(req);
        PageInfo<BatchSecurityPoolDto> pageInfo = new PageInfo<>(poolList);
        // 3. 填充当前页投资池现有证券数量
        fillPoolCurrentCount(poolList);
        // 4. 填充投资池全路径名称
        fillPoolFullName(poolList);
        // 5. 返回本页记录和查询总条数
        return new PageResult<>(
                poolList, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询目标池批量调整候选证券
     *
     * 执行顺序：
     * 1. 校验候选证券查询参数
     * 2. 校验目标投资池调整权限
     * 3. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 4. 回填本页 ABS 主体身份，并将市场文本转换为市场编码列表
     * 5. 将本页记录和查询总条数一起返回
     *
     * @param req 批次证券投资池调库
     * @return 查询记录及分页信息
     */
    public PageResult<BatchSecurityCandidateDto> querySecurityPage(BatchSecurityPoolAdjustReq req) {
        // 1. 校验候选证券查询参数
        validateSecurityPageReq(req);
        // 2. 校验目标投资池调整权限
        validatePoolPermission(req);

        // 3. 按页码和筛选条件查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchSecurityCandidateDto> list = batchSecurityPoolAdjustMapper.querySecurityPage(req);
        PageInfo<BatchSecurityCandidateDto> pageInfo = new PageInfo<>(list);
        // 4. 回填本页 ABS 主体身份并转换市场编码
        List<String> absCodes = list.stream()
                .filter(row -> "abs".equals(row.getSecurityType()) || Integer.valueOf(1).equals(row.getAbsFlag()))
                .map(BatchSecurityCandidateDto::getSecurityCode).distinct().collect(Collectors.toList());
        Map<String, SecurityInfoBo> identities = absIdentityService.queryCurrentIdentities(absCodes);
        for (BatchSecurityCandidateDto row : list) row.setAbsIdentity(identities.get(row.getSecurityCode()));

        // 将 SQL 返回的市场文本转换为市场编码列表
        fillMarketCodes(list);
        // 5. 返回本页记录和查询总条数
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询批量调库可选的 ABS 权益人主体
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将本页记录和查询总条数一起返回
     *
     * @param req 分页与检索条件
     * @return 可选主体分页结果
     */
    public PageResult<SelfSelectedRightsHolderDto> querySelfSelectedRightsHolderPage(
            SelfSelectedRightsHolderReq req) {
        SelfSelectedRightsHolderReq queryReq = req == null ? new SelfSelectedRightsHolderReq() : req;
        PageHelper.startPage(queryReq.getPageIndex(), queryReq.getPageSize());
        List<SelfSelectedRightsHolderDto> records = batchSecurityPoolAdjustMapper.querySelfSelectedRightsHolderPage(
                StringUtils.hasText(queryReq.getCompanyCode()) ? queryReq.getCompanyCode() : null,
                StringUtils.hasText(queryReq.getCompanyName()) ? queryReq.getCompanyName() : null);
        PageInfo<SelfSelectedRightsHolderDto> pageInfo = new PageInfo<>(records);
        return new PageResult<>(records, pageInfo.getTotal(), queryReq.getPageIndex(), queryReq.getPageSize());
    }

    /**
     * 批量调库下一步校验
     *
     * 整批共享证券、投资池及状态数据，复用单笔校验和批量流程候选
     *
     * 执行顺序：
     * 1. 校验批量调库下一步参数
     * 2. 校验批量调库目标池权限
     * 3. 批量加载一次规则数据，按券保持原校验、流程和返回结构
     *
     * @param req 批量证券调库请求
     * @return 证券批量调库结果
     */
    public BatchSecurityInboundAdjustDto checkAdjust(BatchSecurityInboundAdjustReq req) {
        // 1. 校验批量调库下一步参数
        validateAdjustCheckReq(req);
        // 2. 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());

        BatchSecurityInboundAdjustDto dto = new BatchSecurityInboundAdjustDto();
        List<AdjustCheckReq> checks = req.getSecurities().stream()
                // 构建单证券调库校验请求
                .map(security -> buildSingleCheckReq(req, security)).collect(Collectors.toList());
        // 3. 批量加载一次规则数据，按券保持原校验、流程和返回结构
        List<AdjustCheckDto> results = batchSecurityCheckService.checkSecurityBatch(checks);
        for (int index = 0; index < results.size(); index++) {
            BatchSecurityInboundAdjustReq.SecurityItem security = req.getSecurities().get(index);
            AdjustCheckDto checkDto = results.get(index);
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
     * 按提交明细实际选中的流程判断是否存在直通项；存在时落池前统一复核整批状态
     *
     * 执行顺序：
     * 1. 按提交明细实际选中的流程判断是否存在直通项
     *
     * @param req 批量证券调库请求
     * @return true=至少一个手工项可直接生效，需要整批复核，false=没有可直接生效的手工项，无需整批复核
     */
    private boolean needsWholeBatchDirectRecheck(BatchSecurityInboundAdjustReq req) {
        if (req.getItems() == null) {
            return false;
        }
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            // 判断批量提交项是否为手工调库项
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

    /**
     * 将批量请求转换为最终落池复核所需的日志快照
     *
     * 执行顺序：
     * 1. 把每个调整项的证券信息、方向和目标池写成供落池复核使用的日志对象
     *
     * @param req 批量证券调库请求
     * @return 仅供复核使用的日志列表
     */
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

    /**
     * 在放开主体债矩阵时补充调整说明
     *
     * 执行顺序：
     * 1. 未放开矩阵时保留该项原说明
     * 2. 已放开时生成审计说明，有原说明则追加到末尾
     *
     * @param req 批量证券调库请求
     * @param item 明细
     * @return 批量调整项的展示说明
     */
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
     *
     * 执行顺序：
     * 1. 用空上传文件列表进入批量提交，继续处理已在请求中引用的报告和材料
     *
     * @param req 批量证券调库请求
     * @return 证券批量调库结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(BatchSecurityInboundAdjustReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 批量提交调库申请及附件
     *
     * 执行顺序：
     * 1. 把申请和本次上传附件交给提交入口，前端原始文件名列表留空
     *
     * @param req 批量证券调库请求
     * @param files 本次提交的本地上传附件
     * @return 证券批量调库结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(
            BatchSecurityInboundAdjustReq req, List<MultipartFile> files) {
        return addAdjustLog(req, files, null);
    }

    /**
     * 批量提交调库申请及附件（可带前端原始文件名 JSON 数组）
     *
     * 执行顺序：
     * 1. 校验批量调库提交参数
     * 2. 校验批量调库目标池权限
     * 3. 存在直通落池项时，提交前统一复核整批状态
     * 4. 汇总需要默认权益人的 ABS，集中读取本次提交所用的候选
     * 5. 创建批量提交附件上下文（整批共用，避免重复落盘）
     * 6. 整批共用单券批次号上下文，保证多证券批次号序号连续
     * 7. 整批共用提交数据，按原分组保存日志和流程
     *
     * @param req 批量证券调库请求
     * @param files 本次提交的本地上传附件
     * @param originalFileNameListJson 上传文件原始名称列表的 JSON
     * @return 证券批量调库结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(
            BatchSecurityInboundAdjustReq req, List<MultipartFile> files, String originalFileNameListJson) {
        // 1. 校验批量调库提交参数
        validateAdjustSubmitReq(req);
        // 2. 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());
        // 流程完全沿用前端按单笔 checkAdjust 候选所选（与单券调库一致），不再回填 batchIn/batchOut
        // 3. 存在直通落池项时，提交前统一复核整批状态
        List<IpAdjustLogBo> directRecheck = needsWholeBatchDirectRecheck(req)
                // 将批量请求转换为最终落池复核所需的日志快照
                ? buildDirectRecheckLogList(req) : Collections.emptyList();

        // 4. 汇总需要默认权益人的 ABS，集中读取本次提交所用的候选
        Set<String> defaultAbsCodes = new LinkedHashSet<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            if (BatchSecurityCheckService.needsDefaultRightsHolder(
                    item.getSecurityType(), item.getRightsHolderCode(), item.getSelfSelectedRightsHolderCode())) {
                String sourceCode = item.getSourceSecurityCode() == null || item.getSourceSecurityCode().isEmpty()
                        ? item.getSecurityCode() : item.getSourceSecurityCode();
                defaultAbsCodes.add(sourceCode);
            }
        }
        Map<String, String> defaultRightsHolders = defaultAbsCodes.isEmpty() ? Collections.emptyMap()
                : batchSecurityCheckService.loadDefaultRightsHolders(defaultAbsCodes);
        // 按触发主券分组，确保 related 与主券同次提交、共享批次与流程步骤
        Map<String, String> absSelections = new HashMap<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            String sourceCode = item.getSourceSecurityCode() == null || item.getSourceSecurityCode().isEmpty()
                    ? item.getSecurityCode() : item.getSourceSecurityCode();
            absIdentityService.validateBatchSelection(sourceCode, item.getSecurityType(), item.getRightsHolderCode(),
                    item.getSelfSelectedRightsHolderCode(), absSelections, defaultRightsHolders);
        }
        Map<String, List<BatchSecurityInboundAdjustReq.AdjustItem>> itemMap = new LinkedHashMap<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            // 批量提交分组键：related 归到触发主券，保证同批同流程
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
        // 5. 创建批量提交附件上下文（整批共用，避免重复落盘）
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId(), originalFileNameList);
        // 6. 整批共用单券批次号上下文，保证多证券批次号序号连续
        List<SecurityPoolAdjustSubmitReq> requests = new ArrayList<>();
        for (List<BatchSecurityInboundAdjustReq.AdjustItem> group : itemMap.values()) {
            // 按触发主券保留原分组和提交顺序
            requests.add(buildSingleSubmitReq(req, group));
        }
        // 7. 整批共用提交数据，按原分组保存日志和流程
        for (AdjustSubmitDto submitDto : securityPoolAdjustService.submitAdjustLogBatch(
                requests, submissionFiles, directRecheck)) {
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
     *
     * 执行顺序：
     * 1. 检查目标投资池 ID 不能为空
     * 2. 校验批量调库方向
     * 3. 目标投资池不存在、未启用或不是叶子池时终止处理
     *
     * @param req 批次证券投资池调库
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
     *
     * 执行顺序：
     * 1. 检查目标投资池 ID 不能为空
     * 2. 校验批量调库方向
     * 3. 检查已选证券不能为空
     * 4. 目标投资池不存在、未启用或不是叶子池时终止处理
     *
     * @param req 批量证券调库请求
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
     *
     * 执行顺序：
     * 1. 校验批量调库方向
     * 2. 解析批量调库中文方向
     *
     * @param req 批量证券调库请求
     */
    private void validateAdjustSubmitReq(BatchSecurityInboundAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 1. 校验批量调库方向
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
        // 2. 解析批量调库中文方向
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
     * 判断批量提交项是否为手工调库项
     *
     * 执行顺序：
     * 1. 检查调整项标签，未填写标签和明确标记为手工的项按手工项处理
     *
     * @param item 明细
     * @return true=手工调库项（含未填标签的项），false=其他类型的调库项
     */
    private boolean isManualBatchSubmitItem(BatchSecurityInboundAdjustReq.AdjustItem item) {
        return item.getItemTag() == null || item.getItemTag().isEmpty() || ItemType.MANUAL.getCode().equals(item.getItemTag());
    }

    /**
     * 校验批量调库方向
     *
     * 执行顺序：
     * 1. 检查调整方向必须为 in 或 out
     *
     * @param direction 请求中的调整方向编码
     */
    private void validateAdjustDirection(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
    }

    /**
     * 解析批量调库中文方向
     *
     * 执行顺序：
     * 1. 校验批量调库方向
     *
     * @param req 批量证券调库请求
     * @return 调库校验使用的调整方向
     */
    private String resolveAdjustMode(BatchSecurityInboundAdjustReq req) {
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        return "out".equals(req.getDirection()) ? AdjustMode.OUT.getCode() : AdjustMode.IN.getCode();
    }

    /**
     * 校验批量调库目标池权限
     *
     * 执行顺序：
     * 1. 校验目标投资池调整权限
     * 2. 校验批量调库目标池权限
     *
     * @param currentUserId 当前用户 ID
     * @param poolId 目标投资池 ID
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
     *
     * 执行顺序：
     * 1. 解析批量调库中文方向
     * 2. 透传前端选中的担保人、普通权益人和自选权益人代码
     *
     * @param req 批量证券调库请求
     * @param security 证券
     * @return 用于检查当前组合的单笔调库请求
     */
    private AdjustCheckReq buildSingleCheckReq(BatchSecurityInboundAdjustReq req,
                                               BatchSecurityInboundAdjustReq.SecurityItem security) {
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(req.getPoolId());
        item.setTargetPoolName(req.getPoolName());
        item.setPoolType(req.getPoolType());
        // 1. 解析批量调库中文方向
        item.setAdjustMode(resolveAdjustMode(req));

        AdjustCheckReq checkReq = new AdjustCheckReq();
        checkReq.setSecurityCode(security.getSecurityCode());
        checkReq.setSecurityShortName(security.getSecurityShortName());
        checkReq.setSecurityType(security.getSecurityType());
        // 2. 透传前端选中的担保人、普通权益人和自选权益人代码
        checkReq.setGuarantorCode(security.getGuarantorCode());
        checkReq.setRightsHolderCode(security.getRightsHolderCode());
        checkReq.setSelfSelectedRightsHolderCode(security.getSelfSelectedRightsHolderCode());
        checkReq.setReleaseRules("yes".equals(req.getReleaseRules()));
        checkReq.setItems(Collections.singletonList(item));
        return checkReq;
    }

    /**
     * 将单笔校验结果项映射为批量结果行
     *
     * 候选直接透传完整单笔匹配结果和推荐标记（白名单/简易/产品库快速/默认/特殊/升降级等）
     *
     * 执行顺序：
     * 1. 关联码项使用校验结果中的证券代码，主券项回退选中券
     * 2. 分组 Key 以触发主券为前缀，便于 related 与主券同批
     * 3. 透传单笔流程候选与推荐标识，前端默认选中 recommended 项
     *
     * @param security 证券
     * @param item 当前主项
     * @return 单个调库项的校验结果
     */
    private BatchSecurityInboundAdjustDto.CheckResultItem buildBatchCheckResult(
            BatchSecurityInboundAdjustReq.SecurityItem security,
            AdjustCheckDto.CheckResultItem item) {
        BatchSecurityInboundAdjustDto.CheckResultItem result = new BatchSecurityInboundAdjustDto.CheckResultItem();
        // 1. 关联码项使用校验结果中的证券代码，主券项回退选中券
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
        // 2. 分组 Key 以触发主券为前缀，便于 related 与主券同批
        result.setAdjustGroupKey(sourceSecurityCode + "_" + item.getAdjustGroupKey());
        result.setCanAdjust(item.isCanAdjust());
        result.setMaterialExemptionType(item.getMaterialExemptionType());
        result.setFailReasons(item.getFailReasons() == null ? new ArrayList<>() : item.getFailReasons());
        // 3. 透传单笔流程候选与推荐标识，前端默认选中 recommended 项
        result.setFlowOptions(item.getFlowOptions() == null
                ? new ArrayList<>() : new ArrayList<>(item.getFlowOptions()));
        return result;
    }

    /**
     * 构建单证券调库提交请求
     *
     * 执行顺序：
     * 1. 请求级证券取触发主券（manual 项优先，否则 sourceSecurityCode / 首条）
     * 2. 若 primary 是 related，主券简称可能不对，回查主券主数据
     * 3. 复用本次校验后传入的实际代码集合，提交阶段不再查询同券关系
     *
     * @param req 批量证券调库请求
     * @param items 明细列表
     * @return 待提交的证券调库申请
     */
    private SecurityPoolAdjustSubmitReq buildSingleSubmitReq(
            BatchSecurityInboundAdjustReq req,
            List<BatchSecurityInboundAdjustReq.AdjustItem> items) {
        // 1. 请求级证券取触发主券（manual 项优先，否则 sourceSecurityCode / 首条）
        BatchSecurityInboundAdjustReq.AdjustItem primary = resolveBatchPrimaryItem(items);
        SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
        // 批量提交分组键：related 归到触发主券，保证同批同流程
        submitReq.setSecurityCode(resolveBatchSubmitGroupKey(primary));
        submitReq.setSecurityShortName(primary.getSecurityShortName());
        submitReq.setSecurityType(primary.getSecurityType());
        submitReq.setGuarantorCode(primary.getGuarantorCode());
        submitReq.setRightsHolderCode(primary.getRightsHolderCode());
        submitReq.setSelfSelectedRightsHolderCode(primary.getSelfSelectedRightsHolderCode());
        // 2. 若 primary 是 related，主券简称可能不对，回查主券主数据
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
        // 在放开主体债矩阵时将审计说明追加到调整意见
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
            submitItem.setMaterialExemptionType(item.getMaterialExemptionType());
            // 在放开主体债矩阵时补充调整说明
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
     * 在放开主体债矩阵时将审计说明追加到调整意见
     *
     * 执行顺序：
     * 1. 未放开矩阵时保留请求的调整意见
     * 2. 已放开时单独使用或追加放开主体债入库矩阵的说明
     *
     * @param req 批量证券调库请求
     * @return 本次批量调整的意见
     */
    private String buildBatchAdjustAdvice(BatchSecurityInboundAdjustReq req) {
        if (!"yes".equals(req.getReleaseRules())) {
            return req.getAdjustAdvice();
        }
        String advice = req.getAdjustAdvice();
        return advice == null || advice.trim().isEmpty() ? "放开主体债入库矩阵规则"
                : advice + "；放开主体债入库矩阵规则";
    }

    /**
     * 批量提交分组键：related 归到触发主券，保证同批同流程
     *
     * 执行顺序：
     * 1. 明细为空时返回空值
     *
     * @param item 明细
     * @return 来源组合分组键，调整项为空时为 null
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
     * 选出同一批次分组的主券代表
     *
     * 执行顺序：
     * 1. 顺序查找第一个标签为空或为 manual 的调整项
     * 2. 全部未匹配时取组内第一项
     *
     * @param items 明细列表
     * @return 首个标签为空或为 manual 的调整项，没有匹配项时返回组内第一项
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
     *
     * 执行顺序：
     * 1. 判断当前用户是否为管理员
     * 2. 查询当前用户拥有调整权限的投资池
     *
     * @param req 批次证券投资池调库
     */
    private void validatePoolPermission(BatchSecurityPoolAdjustReq req) {
        // 1. 判断当前用户是否为管理员
        if (isAdminUser(req.getCurrentUserId())) {
            return;
        }
        // 2. 查询当前用户拥有调整权限的投资池
        if (!queryAdjustablePoolIds(req.getCurrentUserId()).contains(req.getPoolId())) {
            throw new BizException("当前用户无权调整目标投资池");
        }
    }

    /**
     * 处理当前用户可调整投资池筛选条件
     *
     * 执行顺序：
     * 1. 管理员直接使用请求的查询范围
     * 2. 普通用户先加载可调整的池，无权限时停止查询
     * 3. 未指定池时使用全部可调整池，否则只保留请求中有权限的池
     *
     * @param req 批次证券投资池调库
     * @return true=管理员或已确定可查询的池范围，false=没有可查询的池范围
     */
    private boolean prepareAdjustablePoolIds(BatchSecurityPoolAdjustReq req) {
        // 1. 判断当前用户是否为管理员
        if (isAdminUser(req.getCurrentUserId())) {
            return true;
        }
        // 2. 查询当前用户拥有调整权限的投资池
        Set<Long> adjustablePoolIds = queryAdjustablePoolIds(req.getCurrentUserId());
        if (adjustablePoolIds.isEmpty()) {
            return false;
        }
        // 3. 未指定池时使用全部可调整池，否则取交集
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
     *
     * 执行顺序：
     * 1. 解析当前用户 ID
     * 2. 查询用户角色ID列表
     * 3. 按类型查询权限列表
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
     * 解析当前用户 ID
     *
     * 执行顺序：
     * 1. 检查当前用户 ID 不能为空
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
     * 判断当前用户是否为管理员
     *
     * 执行顺序：
     * 1. 比较当前用户 ID 和系统管理员 ID
     *
     * @param currentUserId 当前用户 ID
     * @return true=当前用户是管理员，false=当前用户不是管理员
     */
    private boolean isAdminUser(String currentUserId) {
        return ADMIN_USER_ID.equals(currentUserId);
    }

    /**
     * 填充市场编码列表
     *
     * 执行顺序：
     * 1. 批量查询当前代码市场，避免列表与导出逐行查询
     *
     * @param list 本次处理的数据列表
     */
    private void fillMarketCodes(List<BatchSecurityCandidateDto> list) {
        List<String> codes = new ArrayList<>();
        for (BatchSecurityCandidateDto dto : list) {
            codes.add(dto.getSecurityCode());
        }
        Map<String, String> markets = securityMarketService.queryMarketCodeMap(codes);
        for (BatchSecurityCandidateDto dto : list) {
            String market = markets.get(dto.getSecurityCode());
            dto.setMarketCodeText(market);
            dto.setMarketCodes(Collections.singletonList(market));

        }
    }

    /**
     * 填充当前页投资池现有证券数量（按类型分项 + 合计）
     *
     * 统计 ip_pool_status 全部有效在池代码，按池状态 security_type 分 company / crmw / category_type，
     * 前端展示「主体：n只 / 债券：n只 …」；合计写入 currentCount
     * 容量校验仍走单券链路全量 count，不受展示分项影响
     *
     * 执行顺序：
     * 1. 按投资池 ID 批量查询各证券类型的在池数量
     * 2. 按投资池归集有效的数量统计
     * 3. 按证券类型排序并回填每个投资池的数量明细及合计
     *
     * @param poolList 需要回填各证券类型数量及在池总数的投资池列表
     */
    private void fillPoolCurrentCount(List<BatchSecurityPoolDto> poolList) {
        if (poolList.isEmpty()) {
            return;
        }
        // 1. 按投资池 ID 批量查询各证券类型的在池数量
        List<Long> poolIds = poolList.stream()
                .map(BatchSecurityPoolDto::getId)
                .collect(Collectors.toList());
        List<BatchPoolTypeCountDto> typeCountList =
                batchSecurityPoolAdjustMapper.queryPoolCurrentCountByTypeList(poolIds);
        // 2. 按投资池归集有效的数量统计
        Map<Long, List<BatchPoolTypeCountDto>> byPoolId = new HashMap<>();
        if (typeCountList != null) {
            for (BatchPoolTypeCountDto row : typeCountList) {
                if (row == null || row.getPoolId() == null || row.getCount() == null || row.getCount() <= 0) {
                    continue;
                }
                byPoolId.computeIfAbsent(row.getPoolId(), key -> new ArrayList<>()).add(row);
            }
        }
        // 3. 按证券类型排序并回填每个投资池的数量明细及合计
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
     * 按业务固定顺序排列类型分项，并去掉嵌套中的 poolId（前端只关心 typeCode/count）
     *
     * 执行顺序：
     * 1. 「现有数量」分项展示顺序：主体、CRMW、其他证券类别
     *
     * @param rawList 原始内容列表
     * @return 批次池类型数量列表
     */
    private List<BatchPoolTypeCountDto> orderPoolTypeCounts(List<BatchPoolTypeCountDto> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return new ArrayList<>();
        }
        // 「现有数量」分项展示顺序：主体、CRMW、其他证券类别
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
            /**
             * 比较两条债券类别统计的展示顺序
             *
             * 执行顺序：
             * 1. 按页面约定的类别顺序比较，未登记的类别排在末尾
             * 2. 排序位置相同时按类别编码比较，null 编码按空字符串比较
             *
             * @param a 左侧类别统计
             * @param b 右侧类别统计
             * @return 负数表示左侧靠前，0 表示顺序相同，正数表示右侧靠前
             */
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
     *
     * 执行顺序：
     * 1. 查询投资池列表
     * 2. 逐项构建投资池全路径名称
     *
     * @param poolList 投资池列表
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
     *
     * 执行顺序：
     * 1. 构建父级投资池全路径
     *
     * @param poolId 目标投资池 ID
     * @param poolMap 当前投资池映射
     * @return 用斜线连接的投资池全名，池 ID 未匹配到记录时返回空字符串
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
