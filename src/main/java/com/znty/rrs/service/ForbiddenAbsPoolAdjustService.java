package com.znty.rrs.service;

import com.znty.rrs.common.constants.BusinessSwitches;
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
import com.znty.rrs.common.util.AdjustStepHandlerExcludeUtil;
import com.znty.rrs.common.util.CreditBondRemainTermUtil;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.common.SecurityMarketDto;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.ForbiddenAbsPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustLogDto;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustSubmitDto;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.flow.FlowOptionParam;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDto;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustReq;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolStatusDto;
import com.znty.rrs.entity.securitypooladjust.PoolStatusDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.securitypooladjust.IpAdjustStepDto;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.securitypooladjust.LastCreditReportDto;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.report.ReportDto;
import com.znty.rrs.entity.report.ReportReq;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 校验和提交 ABS 债券的禁投池调入调出申请
 *
 * 提供候选证券详情、可调整投资池和当前在池状态，根据调入调出条件及池关系生成并校验配套项
 * 联动池沿用主项的调入或调出方向，调入命中互斥池时追加对应调出项
 * 主体身份校验与申请快照由 {@link AbsIdentityService} 处理，质押黑名单库条件复用 {@link PledgeBlacklistRuleService}
 * 保存调库日志和初始审批步骤，在直通或终审通过后更新债券池状态并记录人工联动结论
 */
@Service
public class ForbiddenAbsPoolAdjustService {
    /** 统一证券市场及同券查询组件 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";

    /** 禁投池 / 观察池 / 黑名单质押库 / 重点观察名单（ABS 债手工可调范围，与主体禁投一致） */
    private static final Set<Long> ALLOWED_MANUAL_POOL_IDS = new HashSet<>();

    static {
        ALLOWED_MANUAL_POOL_IDS.add(15L);
        ALLOWED_MANUAL_POOL_IDS.add(16L);
        ALLOWED_MANUAL_POOL_IDS.add(17L);
        ALLOWED_MANUAL_POOL_IDS.add(23L);
    }
    /** 短时间重复提交判定窗口（秒） */
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;

    /** ABS禁投池调库数据访问组件 */
    @Resource
    private ForbiddenAbsPoolAdjustMapper forbiddenAbsPoolAdjustMapper;

    /** 发行主体财务数据访问组件 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 复用证券池调库的 ABS 权益人选择与防伪校验口径 */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /** ABS 唯一主体身份及审批快照 */
    @Resource
    private AbsIdentityService absIdentityService;

    /** ABS 主体联动与规则提醒 */
    @Resource
    private AbsCompanySyncService absCompanySyncService;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 流程定义数据访问组件 */
    @Resource
    private FlowMapper flowMapper;

    /** 信用债评级矩阵数据访问组件（主体债入库规则校验用） */
    @Resource
    private CreditBondGradeRuleMapper creditBondGradeRuleMapper;

    /** 公共查询服务（按担保人代码读取 AIS 最新主体内评） */
    @Resource
    private CommonService commonService;

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** 报告库服务，用于调库完成后统一生成内部报告 */
    @Resource
    private ReportService reportService;

    /** 白名单调入流程 Key */
    private static final String FLOW_KEY_WHITELIST_INBOUND = "bond:whitelist-inbound";

    /** 是否启用白名单调入流程；当前业务未启用，恢复时改为 true */
    private static final boolean WHITELIST_FLOW_ENABLED = false;

    /** 评级下调判定组件（主体/展望/担保人评级下调判断，查 wind_cbondissuerrating） */
    @Resource
    private RatingDowngradeChecker ratingDowngradeChecker;

    /** 黑名单质押库三条件统一判定服务 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;

    /**
     * TODO 白名单流程尚未迁移：老系统读取 WHITEPOOLID_XYJJ 配置的主体白名单池，并校验期限及债券类型，
     * 命中后仍由用户选择是否走白名单流程；老系统的判断不依赖 WHITESECURITYLIST 表
     */
    private static final Set<Long> WHITELIST_POOL_IDS = Collections.emptySet();

    /** 信用债标准上调流程 Key */
    private static final String FLOW_KEY_STANDARD_UPGRADE = "bond:standard-upgrade";

    /** 信用债标准下调流程 Key */
    private static final String FLOW_KEY_STANDARD_DOWNGRADE = "bond:standard-downgrade";

    /** 调入互斥池特殊审批流程 Key */
    private static final String FLOW_KEY_SPECIAL_INBOUND = "bond:special-inbound";
    // ═══════════════════════════════════════════════════════════
    //  查询类接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 分页查询证券列表
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将本页记录和查询总条数一起返回
     *
     * @param req 查询条件（证券代码、简称、发行人，均为模糊匹配）
     * @return 证券列表和分页信息
     */
    public PageResult<SecurityInfoDto> querySecurityPage(ForbiddenAbsPoolAdjustReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityInfoDto> records = forbiddenAbsPoolAdjustMapper.querySecurityPage(
                req.getSecurityCode(), req.getSecurityShortName(), req.getIssuer(), req.getBondYesFlags());
        PageInfo<SecurityInfoDto> pageInfo = new PageInfo<>(records);

        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询证券详情（调库页面顶部基础信息展示）
     *
     * 执行顺序：
     * 1. ABS 禁投调整仅允许 abs_flag=1 的债券
     * 2. 回填证券品种大类（dict_security_type.category_type），供前端按类型差异化展示（如基金评分输入）
     * 3. 关联市场实时读取 Wind，历史快照仍保留其余业务字段
     *
     * @param req 需携带 securityCode
     * @return 标的证券详情
     */
    public SecurityInfoDetailDto querySecurityDetail(ForbiddenAbsPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityInfoDetailDto dto = forbiddenAbsPoolAdjustMapper.querySecurityDetail(req.getSecurityCode());
        if (dto == null) {
            throw new BizException(404, "证券不存在");
        }
        // 1. ABS 禁投调整仅允许 abs_flag=1 的债券
        if (!Integer.valueOf(1).equals(dto.getAbsFlag())) {
            throw new BizException("非 ABS 债不允许在此调整");
        }
        // 2. 回填证券品种大类（dict_security_type.category_type），供前端按类型差异化展示（如基金评分输入）
        dto.setCategoryType(forbiddenAbsPoolAdjustMapper.queryCategoryTypeBySecurityType(dto.getSecurityType()));
        // 3. 关联市场实时读取 Wind，历史快照仍保留其余业务字段
        if (!"company".equals(dto.getSecurityType())) {
            dto.setMarketCode(securityMarketService.queryMarketCode(dto.getWindCode()));
            dto.setRelatedMarketCodes(securityMarketService.queryRelatedMarketCodes(dto.getWindCode()));
        }
        if (CreditBondSpecialInboundRule.isAbs(dto)) {
            dto.setAbsIdentity(absIdentityService.queryCurrentIdentity(dto));
        }
        return dto;
    }

    /**
     * 查询近 6 个月最近一条可回填的信评报告（对齐老系统 getLastReprotDocs）
     *
     * 查询顺序：当前券 → 同主体；仅审批通过调入且日志挂有内部/外部库信评附件，
     * 或手工信评附件；
     * 反查报告库附件后供禁投 ABS 选池页面预填
     *
     * 执行顺序：
     * 1. 先按当前券查近 6 个月最近一条有信评报告的审批通过调入日志
     * 2. 券维度无记录时，按同主体兜底（对齐老 findInvestPoolAdjustHistoryByScodeOrCompancode 主体分支）
     * 3. 优先手工信评附件，其次内部库、外部库，同类附件取 ID 最大的一份
     * 4. 复制绑定时复用 file_name，可反查报告库原附件供提交校验
     * 5. 定位到报告后读取完整附件，避免自动回填只携带一个 PDF
     *
     * @param req 需携带 securityCode
     * @return 无可回填报告时返回 null
     */
    public LastCreditReportDto queryLastCreditReport(ForbiddenAbsPoolAdjustReq req) {
        if (req == null || req.getSecurityCode() == null || req.getSecurityCode().trim().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        String securityCode = req.getSecurityCode().trim();
        // 1. 先按当前券查近 6 个月最近一条有信评报告的审批通过调入日志
        Long adjustLogId = forbiddenAbsPoolAdjustMapper.queryLastInboundLogIdWithCreditReportBySecurity(securityCode);
        // 2. 券维度无记录时，按同主体兜底（对齐老 findInvestPoolAdjustHistoryByScodeOrCompancode 主体分支）
        if (adjustLogId == null) {
            adjustLogId = forbiddenAbsPoolAdjustMapper.queryLastInboundLogIdWithCreditReportByIssuer(securityCode);
        }
        if (adjustLogId == null) {
            return null;
        }
        // 3. 优先手工信评附件，其次内部库、外部库，同类附件取 ID 最大的一份
        SysAttachmentBo logAttachment = forbiddenAbsPoolAdjustMapper.queryFirstCreditReportAttachment(adjustLogId);
        if (logAttachment == null || logAttachment.getFileName() == null || logAttachment.getFileName().isEmpty()) {
            return null;
        }
        boolean internal = AttachmentCategory.CREDIT_REPORT_IN.getCode().equals(logAttachment.getAttachmentCategory())
                || AttachmentCategory.CREDIT_REPORT_HAND.getCode().equals(logAttachment.getAttachmentCategory());
        String tableName = internal ? "rrs_report_in" : "rrs_report_out";
        String category = internal ? AttachmentCategory.REPORT_IN.getCode() : AttachmentCategory.REPORT_OUT.getCode();
        // 4. 复制绑定时复用 file_name，可反查报告库原附件供提交校验
        SysAttachmentBo libraryAttachment = forbiddenAbsPoolAdjustMapper.queryReportLibraryAttachmentByFileName(
                logAttachment.getFileName(), tableName, category);
        if (libraryAttachment == null || libraryAttachment.getMainId() == null) {
            return null;
        }
        ReportReq reportReq = new ReportReq();
        reportReq.setReportId(libraryAttachment.getMainId());
        reportReq.setSource(internal ? "internal" : "external");
        // 5. 定位到报告后读取完整附件，避免自动回填只携带一个 PDF
        ReportDto report = reportService.queryReportDetail(reportReq);
        LastCreditReportDto dto = new LastCreditReportDto();
        dto.setId(report.getId());
        dto.setTitle(report.getReportTitle());
        dto.setReportType(report.getReportType());
        dto.setSource(internal ? "internal" : "external");
        dto.setAttachments(report.getAttachments());
        return dto;
    }

    /**
     * 查询可调入/可调出的投资池列表（含互斥关系，树结构由前端自行组装）
     *
     * 执行顺序：
     * 1. ABS 禁投仅允许手工目标池 15/16/17
     * 2. 解析当前用户 ID
     * 3. 判断当前用户是否为管理员
     * 4. 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
     * 5. 查询各投资池当前有效证券数量
     * 6. InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
     *
     * @param req 当前用户为管理员时返回全量启用池，普通用户仅返回有可调整权限的池及其祖先节点； 入/出方向均排除 CRMW 池（pool_type=crmw）；主体相关池（禁投/观察等）保留以支持单券入池； 调入方向额外按主体内评矩阵过滤信用债大库
     * @return 投资池列表
     */
    public List<PoolDto> queryAdjustPoolList(ForbiddenAbsPoolAdjustReq req) {
        // 1. ABS 禁投仅允许手工目标池 15/16/17
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolByIdsList(
                new ArrayList<>(ALLOWED_MANUAL_POOL_IDS));
        if (allPools.size() != ALLOWED_MANUAL_POOL_IDS.size()) {
            throw new BizException("ABS禁投池调整配置不完整，必须存在 poolId=15、16、17、23");
        }
        for (InvestmentPoolBo pool : allPools) {
            if (pool.getStatus() == null || !"enabled".equals(pool.getStatus())
                    || Integer.valueOf(1).equals(pool.getIsDeleted())) {
                throw new BizException("ABS禁投池调整目标池未启用，poolId=" + pool.getId());
            }
        }

        // 2. 解析当前用户 ID
        Long currentUserId = parseCurrentUserId(req.getCurrentUserId());
        // 3. 判断当前用户是否为管理员
        if (!isAdminUser(req.getCurrentUserId())) {
            // 按投资池“可调整人员”配置过滤可操作池
            allPools = filterAdjustablePoolsByUser(allPools, currentUserId);
            if (allPools.isEmpty()) {
                return new ArrayList<>();
            }
        }

        // 4. 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
        List<PoolRelationBo> mutexList = investmentPoolMapper.queryMutexRelationList();
        Map<Long, List<Long>> inMutexMap  = new HashMap<>();
        Map<Long, List<Long>> outMutexMap = new HashMap<>();
        for (PoolRelationBo r : mutexList) {
            if (RelationType.IN_MUTEX.getCode().equals(r.getRelationType())) {
                inMutexMap.computeIfAbsent(r.getPoolId(), k -> new ArrayList<>()).add(r.getRelationPoolId());
            } else if (RelationType.OUT_MUTEX.getCode().equals(r.getRelationType())) {
                outMutexMap.computeIfAbsent(r.getPoolId(), k -> new ArrayList<>()).add(r.getRelationPoolId());
            }
        }
        // 5. 查询各投资池当前有效证券数量
        Map<Long, Integer> currentCountMap = forbiddenAbsPoolAdjustMapper.queryPoolCurrentCountList().stream()
                .collect(Collectors.toMap(PoolDto::getId, PoolDto::getCurrentCount));
        // 6. InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
        return allPools.stream()
                // InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
                .map(p -> toPoolDto(p, inMutexMap, outMutexMap, currentCountMap))
                .collect(Collectors.toList());
    }

    /**
     * 选池阶段按主体内评矩阵过滤信用债 1～5 级（境外债不走这套规则）
     *
     * 正式证券无内评去掉分级库；临时代码默认档 4。期限为空默认最长档继续走矩阵，不跳过
     * 可转债 / 可交换债 / CRMW 去掉信用债 1～5
     * ABS 按特殊债：担保人内评 1 档只能调入一级库，无担保人或非 1 档至少下调一级。「1 档」只看担保人内评
     * 担保债，或证券/主体已在观察池：不得高于矩阵最好档，从该档开到五级。普通债只保留矩阵单元格
     * 已在重点观察名单：未在分级库不得新增（开关开启时强担保豁免），已在 1～4 级只留五级。含权只改期限。releaseRules 跳过矩阵
     *
     * 执行顺序：
     * 1. releaseRules=true 或无证券代码：不卡矩阵
     * 2. 可转债 / 可交换债 / CRMW 不适用信用债 1～5，选池直接去掉
     * 3. 证券自身或发行主体任一在重点观察名单，即走重点观察名单禁新增规则
     * 4. 当前已在池，用于重点观察名单判断是否「新增入库」
     * 5. ABS 一律按特殊债算档（即使同时有担保）；1 档只看担保人内评
     * 6. 保留叶子：非信用债大库叶子，或信用债大库叶子在矩阵允许列表；父级按子级递归保留（避免丢失父级树结构）
     * 7. 向上传播：父级若至少一个子级保留则保留（含多层父级）
     *
     * @param pools 待过滤的投资池列表
     * @param req 选池请求
     * @return 过滤后的池列表
     */
    private List<InvestmentPoolBo> filterInboundByGradeRule(List<InvestmentPoolBo> pools, ForbiddenAbsPoolAdjustReq req) {
        if (pools == null || pools.isEmpty()) {
            return pools;
        }
        // 1. releaseRules=true 或无证券代码：不卡矩阵
        if (req.isReleaseRules() || req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            return pools;
        }
        boolean hasGradedPool = false;
        for (InvestmentPoolBo p : pools) {
            // 判断列表里是否含信用债分级库，没有则不必走矩阵
            if (isGradedBondPool(p)) {
                hasGradedPool = true;
                break;
            }
        }
        if (!hasGradedPool) {
            return pools;
        }
        SecurityInfoBo securityInfo = req.getSecurityInfo() != null && req.getSecurityInfo().getSecurityType() != null
                ? req.getSecurityInfo() : forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            return pools;
        }
        // 2. 可转债 / 可交换债 / CRMW 不适用信用债 1～5，选池直接去掉
        if (CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(securityInfo)) {
            // 去掉信用债大库整棵树
            return excludeGradedBondPools(pools);
        }
        // 3. 证券自身或发行主体任一在重点观察名单，即走重点观察名单禁新增规则
        boolean inRestricted = forbiddenAbsPoolAdjustMapper.querySecurityInRestrictedPool(req.getSecurityCode())
                || securityPoolAdjustService.queryIssuerInPoolType(securityInfo, "restricted");
        // 4. 当前已在池，用于重点观察名单判断是否「新增入库」
        Set<Long> currentPoolIds = new HashSet<Long>();
        List<Long> currentIdList = forbiddenAbsPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode());
        if (currentIdList != null) {
            currentPoolIds.addAll(currentIdList);
        }
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<Long, InvestmentPoolBo>();
        for (InvestmentPoolBo p : pools) {
            poolMap.put(p.getId(), p);
        }
        // 取当前已在分级库的最好档，供重点观察名单判断「新增还是已在库」
        Integer currentGradedSort = resolveCurrentGradedSort(currentPoolIds, poolMap);
        if (inRestricted && !CreditBondSpecialInboundRule.isStrongGuarantee(
                securityInfo, BusinessSwitches.STRONG_GUARANTEE_ENABLED)
                && currentGradedSort == null) {
            // 已在重点观察名单且未在信用债分级库：整棵信用债树不可选（境外债不受评分档套件限制）
            return excludeGradedBondPools(pools);
        }
        // 临时代码默认档 4，担保债取孰高后再查矩阵
        String gradeCode = resolveMatrixGradeCode(securityInfo);
        if (gradeCode == null) {
            // 正式券无内评：去掉分级库
            return excludeGradedBondPools(pools);
        }
        // 按含权口径取剩余期限年（date_exists_str 解析，含权/回购已是年），匹配期限档
        String bucketCode = matchTermBucket(CreditBondRemainTermUtil.resolveRemainTermYears(securityInfo));
        List<Long> allowedPoolIds = null;
        Integer bestAllowedSort = null;
        if (bucketCode != null) {
            allowedPoolIds = creditBondGradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(gradeCode, bucketCode);
            // 从矩阵允许池算出标准最好档
            bestAllowedSort = resolveBestAllowedSort(allowedPoolIds, poolMap);
        }
        // 5. ABS 一律按特殊债算档（即使同时有担保）；1 档只看担保人内评
        boolean inObserve = forbiddenAbsPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode())
                || securityPoolAdjustService.queryIssuerInPoolType(securityInfo, "observe");
        CreditBondSpecialInboundRule.GradedInboundMode inboundMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(securityInfo, inObserve);
        Integer startSort = inboundMode.resolveStartSort(bestAllowedSort);
        // 6. 保留叶子：非信用债大库叶子，或信用债大库叶子在矩阵允许列表；父级按子级递归保留（避免丢失父级树结构）
        List<Long> retained = new ArrayList<>();
        for (InvestmentPoolBo p : pools) {
            boolean isLeaf = true;
            for (InvestmentPoolBo c : pools) {
                if (p.getId() != null && p.getId().equals(c.getParentId())) {
                    isLeaf = false;
                    break;
                }
            }
            if (!isLeaf) {
                continue;
            }
            // 是否信用债大库树（主体评分档套件范围）
            if (!isGradedBondPool(p)) {
                retained.add(p.getId());
                continue;
            }
            if (!CreditBondSpecialInboundRule.isGradedLevelPool(p)) {
                continue;
            }
            String restrictedFail = CreditBondSpecialInboundRule.checkRestricted(
                    securityInfo, inRestricted, currentGradedSort, p.getInnerSort(), BusinessSwitches.STRONG_GUARANTEE_ENABLED);
            if (restrictedFail != null) {
                continue;
            }
            // 期限档匹配不上时信用债 1～5 不显示，不放开
            if (bucketCode == null) {
                continue;
            }
            // 按档位口径或矩阵点名的池判断这一级能不能留
            if (isLeafAllowedByGradeRule(p, allowedPoolIds, inboundMode, startSort, poolMap)) {
                retained.add(p.getId());
            }
        }
        // 7. 向上传播：父级若至少一个子级保留则保留（含多层父级）
        boolean changed = true;
        while (changed) {
            changed = false;
            for (InvestmentPoolBo p : pools) {
                if (retained.contains(p.getId())) {
                    continue;
                }
                for (InvestmentPoolBo c : pools) {
                    if (p.getId() != null && p.getId().equals(c.getParentId()) && retained.contains(c.getId())) {
                        retained.add(p.getId());
                        changed = true;
                        break;
                    }
                }
            }
        }
        return pools.stream().filter(p -> retained.contains(p.getId())).collect(Collectors.toList());
    }

    /**
     * 规范化主体内评分档编码：null / 空白视为无内评
     *
     * 执行顺序：
     * 1. 档级代码为空时返回空值
     *
     * @param gradeCode 档级代码
     * @return 去掉首尾空白后的档级代码，输入为空或只有空白时返回 null
     */
    private String normalizeGradeCode(String gradeCode) {
        if (gradeCode == null) {
            return null;
        }
        String trimmed = gradeCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 从池列表中排除全部信用债大库池（pool_type=credit_bond，含根节点与 1～5 级）
     *
     * 执行顺序：
     * 1. 空列表原样返回，有投资池时逐项保留信用债大库以外的池
     *
     * @param pools 待过滤的投资池列表
     * @return 待处理的投资池记录列表
     */
    private List<InvestmentPoolBo> excludeCreditBondPools(List<InvestmentPoolBo> pools) {
        if (pools == null || pools.isEmpty()) {
            return pools;
        }
        List<InvestmentPoolBo> result = new ArrayList<>();
        for (InvestmentPoolBo p : pools) {
            // 判断目标池是否属于信用债大库
            if (!isCreditBondPool(p)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 从投资池列表中排除 CRMW 类型池（pool_type=crmw）
     *
     * 注意：这是「池域」过滤，与候选证券排除 security_type=crmw/company 是不同维度
     *
     * 执行顺序：
     * 1. 空列表返回新空结果，遍历时跳过空记录和类型为 CRMW 的池
     *
     * @param pools 待过滤的投资池列表
     * @return 待处理的投资池记录列表
     */
    private List<InvestmentPoolBo> filterOutCrmwPools(List<InvestmentPoolBo> pools) {
        if (pools == null || pools.isEmpty()) {
            return new ArrayList<>();
        }
        List<InvestmentPoolBo> result = new ArrayList<>();
        for (InvestmentPoolBo pool : pools) {
            if (pool == null) {
                continue;
            }
            if (PoolType.CRMW.getCode().equals(pool.getPoolType())) {
                continue;
            }
            result.add(pool);
        }
        return result;
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
     * 按投资池“可调整人员”配置过滤可操作池，并保留祖先节点以保证前端树路径完整
     *
     * 执行顺序：
     * 1. 查询当前用户直接或通过角色拥有可调整权限的投资池 ID
     * 2. 按投资池“可调整人员”配置过滤可操作池，并保留祖先节点以保证前端树路径完整
     *
     * @param allPools 全部候选投资池
     * @param userId 当前用户 ID
     * @return 用户可见的投资池列表
     */
    private List<InvestmentPoolBo> filterAdjustablePoolsByUser(List<InvestmentPoolBo> allPools, Long userId) {
        // 查询当前用户直接或通过角色拥有可调整权限的投资池 ID
        Set<Long> adjustablePoolIds = queryAdjustablePoolIdsByUser(userId);
        if (adjustablePoolIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        for (InvestmentPoolBo pool : allPools) {
            poolMap.put(pool.getId(), pool);
        }

        Set<Long> visiblePoolIds = new HashSet<>();
        for (Long poolId : adjustablePoolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            while (pool != null) {
                visiblePoolIds.add(pool.getId());
                pool = pool.getParentId() != null ? poolMap.get(pool.getParentId()) : null;
            }
        }

        List<InvestmentPoolBo> result = new ArrayList<>();
        for (InvestmentPoolBo pool : allPools) {
            if (visiblePoolIds.contains(pool.getId())) {
                result.add(pool);
            }
        }
        return result;
    }

    /**
     * 查询当前用户直接或通过角色拥有可调整权限的投资池 ID
     *
     * 执行顺序：
     * 1. 查询用户角色ID列表
     * 2. 按类型查询权限列表
     *
     * @param userId 当前用户 ID
     * @return 可调整的投资池 ID 集合
     */
    private Set<Long> queryAdjustablePoolIdsByUser(Long userId) {
        List<Long> roleIds = investmentPoolMapper.queryUserRoleIdList(userId);
        Set<Long> roleIdSet = new HashSet<>(roleIds);
        List<PoolPermissionBo> permissions = investmentPoolMapper.queryPermissionListByType(PermissionType.ADJUSTABLE.getCode());
        Set<Long> poolIds = new HashSet<>();

        for (PoolPermissionBo permission : permissions) {
            if (permission.getPoolId() == null || permission.getHandlerId() == null) {
                continue;
            }
            if (HandlerType.USER.getCode().equals(permission.getHandlerType()) && permission.getHandlerId().equals(userId)) {
                poolIds.add(permission.getPoolId());
            } else if (HandlerType.ROLE.getCode().equals(permission.getHandlerType()) && roleIdSet.contains(permission.getHandlerId())) {
                poolIds.add(permission.getPoolId());
            }
        }
        return poolIds;
    }

    /**
     * 查询证券当前所在池及其主体（发行人）当前所在池
     *
     * 执行顺序：
     * 1. 校验证券代码并分别查询证券与发行主体的在池记录
     * 2. 读取池全路径名称，补齐两份在池记录的展示信息
     *
     * @param req 需携带 securityCode
     * @return 证券投资池状态
     */
    public SecurityPoolStatusDto querySecurityPoolStatus(ForbiddenAbsPoolAdjustReq req) {
        // 1. 校验证券代码并分别查询证券与发行主体的在池记录
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityPoolStatusDto dto = new SecurityPoolStatusDto();
        List<PoolStatusDto> securityCurrentPools = forbiddenAbsPoolAdjustMapper.querySecurityPoolStatusList(req.getSecurityCode());
        List<PoolStatusDto> issuerCurrentPools = forbiddenAbsPoolAdjustMapper.queryIssuerPoolStatusList(req.getSecurityCode());
        // 2. 读取池全路径名称，补齐两份在池记录的展示信息
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        // 填充当前所在池的全路径名称
        fillPoolStatusFullName(securityCurrentPools, poolFullNameMap);
        // 填充当前所在池的全路径名称
        fillPoolStatusFullName(issuerCurrentPools, poolFullNameMap);
        dto.setSecurityCurrentPools(securityCurrentPools);
        dto.setIssuerCurrentPools(issuerCurrentPools);
        return dto;
    }

    /**
     * 填充当前所在池的全路径名称
     *
     * 执行顺序：
     * 1. 按目标池 ID 查找全路径，只用非空路径覆盖在池记录的原名称
     *
     * @param poolStatusList 待补全池名的在池记录列表
     * @param poolFullNameMap 按池 ID 整理的完整池名
     */
    private void fillPoolStatusFullName(List<PoolStatusDto> poolStatusList, Map<Long, String> poolFullNameMap) {
        if (poolStatusList.isEmpty()) {
            return;
        }
        for (PoolStatusDto status : poolStatusList) {
            String fullName = poolFullNameMap.get(status.getTargetPoolId());
            if (fullName != null && !fullName.isEmpty()) {
                status.setPoolName(fullName);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  调库申请提交
    // ═══════════════════════════════════════════════════════════

    /**
     * 提交调库申请
     *
     * 执行顺序：
     * 1. 使用本次请求和空上传文件列表，委托支持附件的提交入口执行校验、建流程和落池
     *
     * @param req 调库申请，包含证券信息及一个或多个调库项（手工项须携带 flowId 或 flowKey，无流程时走直通）
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public ForbiddenAbsPoolAdjustSubmitDto addAdjustLog(ForbiddenAbsPoolAdjustSubmitReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 提交调库申请及附件
     *
     * 执行顺序：
     * 1. 把申请和本次上传附件交给提交入口，前端原始文件名列表留空
     *
     * @param req 调库提交请求
     * @param files 本次提交的本地上传附件
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public ForbiddenAbsPoolAdjustSubmitDto addAdjustLog(ForbiddenAbsPoolAdjustSubmitReq req, List<MultipartFile> files) {
        return addAdjustLog(req, files, null);
    }

    /**
     * 提交 ABS 禁投池调库申请及附件（可带前端原始文件名 JSON 数组）
     *
     * 执行顺序：
     * 1. 解析前端 FormData 字段 {@code originalFileNameListJson}（JSON 数组字符串）
     * 2. 创建本次提交附件上下文
     * 3. 提交调库申请内部实现
     *
     * @param req 调库提交请求
     * @param files 本次提交的本地上传附件
     * @param originalFileNameListJson 上传文件原始名称列表的 JSON
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public ForbiddenAbsPoolAdjustSubmitDto addAdjustLog(ForbiddenAbsPoolAdjustSubmitReq req, List<MultipartFile> files,
                                                        String originalFileNameListJson) {
        // 1. 解析前端传入的文件原始名称列表
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        // 2. 创建本次提交附件上下文
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId(), originalFileNameList);
        // 3. 提交调库申请内部实现
        return submitAdjustLog(req, submissionFiles, new BatchNoContext());
    }

    /**
     * 提交调库申请内部实现
     *
     * 执行顺序：
     * 1. 校验 ABS 调库提交参数
     * 2. 检查短时间内是否已提交相同申请
     * 3. 读取本次提交共用的数据并校验涉及证券的主体身份
     * 4. 检查关联市场调入、顶级池组在途流程和必需的互斥调出项
     * 5. 提交调入项并收集需要直通的记录
     * 6. 提交调出项并继续收集需要直通的记录
     * 7. 保存本次使用的主体快照，供直通与人工审批共用
     * 8. 复核并应用直通项，主档五字段在审核通过的落池事务内更新
     * 9. 汇总调库日志 ID 和提交数量
     *
     * @param req 调库提交请求
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @param batchNoContext 批次号上下文，可由整批共享
     * @return 禁投ABS投资池调库提交
     */
    private ForbiddenAbsPoolAdjustSubmitDto submitAdjustLog(ForbiddenAbsPoolAdjustSubmitReq req,
                                            SysAttachmentService.SubmissionFiles submissionFiles,
                                            BatchNoContext batchNoContext) {
        // 1. 校验 ABS 调库提交参数
        validateSubmitReq(req);
        // 2. 检查短时间内是否已提交相同申请
        checkRecentDuplicateSubmit(req);

        // 3. 读取本次提交共用的数据并校验涉及证券的主体身份
        SubmitSharedData shared = loadSubmitSharedData(req, batchNoContext);
        Set<String> identityCodes = new TreeSet<>();
        identityCodes.add(req.getSecurityCode());
        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item.getSecurityCode() != null) {
                identityCodes.add(item.getSecurityCode());
            }
        }
        for (String identityCode : identityCodes) {
            absIdentityService.validateIdentity(identityCode, shared.securityInfo.getIssuerCode());
        }
        // 4. 检查关联市场调入、顶级池组在途流程和必需的互斥调出项
        validateRelatedInboundItemsOnSubmit(req);
        // 检查本次涉及的顶级池组是否已有活动流程
        checkSubmitPendingPoolGroups(req, shared.poolMap);
        // 调入须同步调出互斥池：缺失或调出不可行则整单拒绝，防止双池
        validateRequiredMutexOutboundOnSubmit(req, shared);

        // 5. 提交调入项并收集需要直通的记录
        List<IpAdjustLogBo> directApplyLogs = new ArrayList<>();
        // 执行调入提交并绑定本次 multipart 文件
        List<Long> inboundIds = executeInboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // 6. 提交调出项并继续收集需要直通的记录
        List<Long> outboundIds = executeOutboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // 7. 保存本次使用的主体快照，供直通与人工审批共用
        postSubmitProcess(req, shared);
        // 8. 复核并应用直通项，主档五字段在审核通过的落池事务内更新
        recheckAndApplyDirectLogs(directApplyLogs);

        // 9. 汇总调库日志 ID 和提交数量
        ForbiddenAbsPoolAdjustSubmitDto dto = new ForbiddenAbsPoolAdjustSubmitDto();
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
     * 校验 ABS 禁投池调库申请的必填项和手工目标池
     *
     * 对请求入参进行合法性检查，均为内存操作，不涉及任何 DB 查询
     * 任意一项不通过则直接抛出异常，终止后续流程
     *
     * 执行顺序：
     * 1. 检查证券代码不能为空
     * 2. 检查调库项不能为空
     * 3. 逐项校验手工目标池仅允许 15/16/17
     *
     * @param req 调库提交请求
     */
    private void validateSubmitReq(ForbiddenAbsPoolAdjustSubmitReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item.getAdjustMode() == null || item.getAdjustMode().isEmpty()) {
                throw new BizException("调库项的调整方向不能为空");
            }
            if (item.getTargetPoolId() == null) {
                throw new BizException("调库项的目标投资池 ID 不能为空");
            }
            // 手工项限制目标池 15/16/17
            if (isManualSubmitItem(item)) {
                // 校验手工目标池仅允许 15/16/17
                validateAllowedManualPoolId(item.getTargetPoolId());
            }
        }
    }

    /**
     * 提交时校验：调入目标池若要求配套互斥调出，请求中必须包含对应调出项且调出校验通过
     *
     * 防止前端只提交可调入项、丢弃失败的互斥调出项，造成双池
     *
     * 执行顺序：
     * 1. 先收集本次请求里的调出池 ID
     * 2. 逐个检查调入项所需的互斥调出是否已提交，并复核相应调出是否合法
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     */
    private void validateRequiredMutexOutboundOnSubmit(ForbiddenAbsPoolAdjustSubmitReq req, SubmitSharedData shared) {
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item != null && AdjustMode.OUT.getCode().equals(item.getAdjustMode())
                    && item.getTargetPoolId() != null) {
                requestOutPoolIds.add(item.getTargetPoolId());
            }
        }
        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item == null || !AdjustMode.IN.getCode().equals(item.getAdjustMode())
                    // 判断提交项是否为手工调库项
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
                // 构建投资池全路径名称
                String mutexPoolName = buildPoolPath(mutexId, shared.poolMap);
                if (!requestOutPoolIds.contains(mutexId)) {
                    throw new BizException("调入须同步调出互斥池，请完整提交：" + mutexPoolName);
                }
                // 复核互斥池调出可行性（锁池、冻结期等）
                ForbiddenAbsPoolAdjustCheckReq.CheckItem fakeOut = new ForbiddenAbsPoolAdjustCheckReq.CheckItem();
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
                int poolCurrentCount = forbiddenAbsPoolAdjustMapper.queryPoolCurrentCount(mutexId);
                // 构建单个调库项的校验上下文（{@link AdjustCheckContext}）
                AdjustCheckContext ctx = buildCheckContext(fakeOut, poolCurrentCount, checkShared);
                List<String> outFailures = checkOutConditions(ctx);
                if (outFailures != null && !outFailures.isEmpty()) {
                    // 提交异常文案：总述 + 编号原因（与校验结果列表口径一致）
                    throw new BizException(buildMutexOutboundFailureMessage(mutexPoolName, outFailures));
                }
            }
        }
    }

    /**
     * 检查操作人短时间内是否已经提交内容相同的手工调库申请
     *
     * 执行顺序：
     * 1. 调库申请已提交时终止处理
     *
     * @param req 调库提交请求
     */
    private void checkRecentDuplicateSubmit(ForbiddenAbsPoolAdjustSubmitReq req) {
        List<ForbiddenAbsPoolAdjustSubmitReq.AdjustItem> manualItems = req.getItems().stream()
                // 判断提交项是否为手工调库项
                .filter(this::isManualSubmitItem)
                .collect(Collectors.toList());
        if (manualItems.isEmpty()) {
            return;
        }
        List<IpAdjustLogBo> recentLogs = forbiddenAbsPoolAdjustMapper.queryRecentManualAdjustLogList(
                req.getSecurityCode(), req.getAdjusterId(), DUPLICATE_SUBMIT_WINDOW_SECONDS);
        if (recentLogs == null || recentLogs.isEmpty()) {
            return;
        }
        List<String> requestKeys = manualItems.stream().map(item -> item.getTargetPoolId() + "|"
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                + item.getAdjustMode() + "|" + item.getFlowId() + "|" + emptyToNull(item.getFlowKey()))
                .sorted().collect(Collectors.toList());
        List<String> historyKeys = recentLogs.stream().map(log -> log.getTargetPoolId() + "|"
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                + log.getAdjustMode() + "|" + log.getFlowId() + "|" + emptyToNull(log.getFlowKey()))
                .sorted().collect(Collectors.toList());
        boolean sameText = recentLogs.stream().allMatch(log ->
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                Objects.equals(emptyToNull(req.getAdjustReason()), emptyToNull(log.getAdjustReason()))
                        // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                        && Objects.equals(emptyToNull(req.getAdjustAdvice()), emptyToNull(log.getAdjustAdvice())));
        if (requestKeys.equals(historyKeys) && sameText) {
            throw new BizException("调库申请已提交，请勿重复操作");
        }
    }

    /**
     * 检查正式提交涉及的池组是否已有活动流程（按每条调库项的证券代码独立判断）
     *
     * 执行顺序：
     * 1. 按证券代码缓存 pending 池组，避免同码多行重复查库
     * 2. 逐项解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
     * 3. 将目标池列表转换为顶级投资池组 ID 集合
     *
     * @param req 调库提交请求
     * @param poolMap 全量池索引
     */
    private void checkSubmitPendingPoolGroups(ForbiddenAbsPoolAdjustSubmitReq req,
                                              Map<Long, InvestmentPoolBo> poolMap) {
        // 按证券代码缓存 pending 池组，避免同码多行重复查库
        Map<String, Set<Long>> pendingGroupsBySecurity = new HashMap<>();
        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
            String securityCode = resolveItemSecurityCode(req, item);
            Set<Long> pendingGroups = pendingGroupsBySecurity.get(securityCode);
            if (pendingGroups == null) {
                // 将目标池列表转换为顶级投资池组 ID 集合
                pendingGroups = resolvePoolGroupIds(
                        forbiddenAbsPoolAdjustMapper.queryPendingManualTargetPoolIdList(securityCode, null), poolMap);
                pendingGroupsBySecurity.put(securityCode, pendingGroups);
            }
            // 沿父级关系查找目标池所属的顶级投资池
            Long groupId = resolveRootPoolId(item.getTargetPoolId(), poolMap);
            if (groupId != null && pendingGroups.contains(groupId)) {
                InvestmentPoolBo group = poolMap.get(groupId);
                throw new BizException("证券[" + securityCode + "]在投资池组["
                        + (group != null ? group.getPoolName() : groupId)
                        + "]存在进行中的调库流程，请等待流程结束后再发起调库");
            }
        }
    }

    /**
     * 同券各市场代码须同池同向调入，防止客户端仅提交部分通过项
     *
     * 执行顺序：
     * 1. 逐项解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
     *
     * @param req 调库提交请求
     */
    private void validateRelatedInboundItemsOnSubmit(ForbiddenAbsPoolAdjustSubmitReq req) {
        Map<Long, Set<String>> inboundCodes = new LinkedHashMap<>();
        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                // 使用明细实际代码，兼容未传明细代码的单券请求
                inboundCodes.computeIfAbsent(item.getTargetPoolId(), key -> new HashSet<>())
                        // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
                        .add(resolveItemSecurityCode(req, item));
            }
        }
        if (inboundCodes.isEmpty()) {
            return;
        }
        List<SecurityMarketDto> relatedCodes = req.getRelatedMarketCodes();
        if (relatedCodes == null || relatedCodes.isEmpty()) {
            return;
        }
        Set<String> requiredCodes = new HashSet<>();
        requiredCodes.add(req.getSecurityCode());
        for (SecurityMarketDto related : relatedCodes) {
            requiredCodes.add(related.getWindCode());
        }
        for (Map.Entry<Long, Set<String>> entry : inboundCodes.entrySet()) {
            if (!entry.getValue().containsAll(requiredCodes)) {
                Set<String> missingCodes = new TreeSet<>(requiredCodes);
                missingCodes.removeAll(entry.getValue());
                throw new BizException("同券跨市场调入必须整组提交，目标池[" + entry.getKey()
                        + "]缺少证券：" + String.join("、", missingCodes) + "，请重新校验");
            }
        }
    }

    /**
     * 将目标池列表转换为顶级投资池组 ID 集合
     *
     * 执行顺序：
     * 1. 逐项沿父级关系查找目标池所属的顶级投资池
     *
     * @param poolIds 需要转换的池 ID 列表
     * @param poolMap 全量池索引
     * @return 非空根池 ID 集合
     */
    private Set<Long> resolvePoolGroupIds(List<Long> poolIds, Map<Long, InvestmentPoolBo> poolMap) {
        Set<Long> result = new HashSet<>();
        if (poolIds == null) {
            return result;
        }
        for (Long poolId : poolIds) {
            // 沿父级关系查找目标池所属的顶级投资池
            Long rootId = resolveRootPoolId(poolId, poolMap);
            if (rootId != null) {
                result.add(rootId);
            }
        }
        return result;
    }

    /**
     * 沿父级关系查找目标池所属的顶级投资池
     *
     * 执行顺序：
     * 1. 从目标池 ID 沿父池逐层向上，走到根池或遇到缺失记录时停止
     * 2. 发现已经访问过的池 ID 时结束查找，避免关系环导致反复遍历
     *
     * @param poolId 目标投资池 ID
     * @param poolMap 全量池索引
     * @return 停止时的池 ID
     */
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

    /**
     * 将空字符串归一化为空值，避免重复提交比较受空串表现影响
     *
     * 执行顺序：
     * 1. 输入为 null 或去空白后为空时返回 null，其余文本保持原样
     *
     * @param value 待统一空值的文本
     * @return 原文本，空字符串转换为 null
     */
    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * 为 ABS 禁投池调库准备提交数据和批次号上下文
     *
     * 集中执行本次提交所需的全部 DB 查询，构建 {@link SubmitSharedData}
     * 包括证券基础信息、投资池索引、池关系映射、以及每个唯一流程的快照（定义+版本+节点+连线）
     * 调入和调出校验共用这些数据
     *
     * 执行顺序：
     * 1. 新建本次调库使用的批次号上下文，再准备申请需要的证券、池和流程数据
     *
     * @param req 调库提交请求
     * @return 封装了本次提交全量共享数据的 SubmitSharedData
     */
    private SubmitSharedData loadSubmitSharedData(ForbiddenAbsPoolAdjustSubmitReq req) {
        // 创建本次提交独立批次号上下文
        return loadSubmitSharedData(req, new BatchNoContext());
    }

    /**
     * 使用指定批次号上下文加载 ABS 禁投池提交数据
     *
     * 执行顺序：
     * 1. 证券基础信息（兼含存在性校验）
     * 2. 直接检查本次已加载的证券，不再为 ABS 类型重复读取主档
     * 3. ABS 按证券池调库口径校验权益人，自选权益人优先
     * 4. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
     * 5. 全量投资池关系配置，构建三层嵌套 Map
     * 6. 收集所有调库项中引用的唯一流程标识，批量加载流程快照
     * 7. 为每个唯一流程加载快照（定义 + 活跃版本 + 节点 + 连线）
     *
     * @param req 调库提交请求
     * @param batchNoContext 批次号上下文，可由整批共享
     * @return 封装了本次提交全量共享数据的 SubmitSharedData
     */
    private SubmitSharedData loadSubmitSharedData(ForbiddenAbsPoolAdjustSubmitReq req, BatchNoContext batchNoContext) {
        // 1. 证券基础信息（兼含存在性校验）
        SecurityInfoBo securityInfo = req.getSecurityInfo() != null && req.getSecurityInfo().getSecurityType() != null
                ? req.getSecurityInfo() : forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }
        // 2. 直接检查本次已加载的证券，不再为 ABS 类型重复读取主档
        validateAbsSecurity(securityInfo);
        // 3. ABS 按证券池调库口径校验权益人，自选权益人优先
        SecurityPoolAdjustService.SelectedRatingCompanyData selectedRatingCompany =
                securityPoolAdjustService.applySelectedRatingCompany(securityInfo, req.getGuarantorCode(), req.getRightsHolderCode(),
                        req.getSelfSelectedRightsHolderCode(), true, req.getSelectedRatingCompany());

        // 4. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        for (InvestmentPoolBo p : allPools) {
            poolMap.put(p.getId(), p);
        }

        // 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
        List<Long> currentPoolIdList = forbiddenAbsPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList);

        // 5. 全量投资池关系配置，构建三层嵌套 Map
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                forbiddenAbsPoolAdjustMapper.queryAllPoolRelationList());

        // 6. 收集所有调库项中引用的唯一流程标识，批量加载流程快照
        Set<Long> uniqueFlowIds = new HashSet<>();
        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long resolvedId = resolveFlowIdFromItem(item);
            if (resolvedId != null) {
                uniqueFlowIds.add(resolvedId);
            }
        }

        // 7. 为每个唯一流程加载快照（定义 + 活跃版本 + 节点 + 连线）
        Map<Long, FlowSnapshot> flowSnapshotMap = new HashMap<>();
        for (Long flowId : uniqueFlowIds) {
            // 为指定流程 ID 构建运行时快照（定义 + 活跃版本 + 节点索引 + 连线列表）
            FlowSnapshot snapshot = buildFlowSnapshot(flowId);
            if (snapshot != null) {
                flowSnapshotMap.put(flowId, snapshot);
            }
        }

        SubmitSharedData shared = new SubmitSharedData(
                securityInfo,
                poolMap,
                currentPoolIds,
                poolRelationMap,
                forbiddenAbsPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()),
                forbiddenAbsPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()),
                securityPoolAdjustService.queryIssuerInPoolType(securityInfo, "observe"),
                flowSnapshotMap,
                batchNoContext
        );
        shared.selectedRatingCompany = selectedRatingCompany;
        return shared;
    }

    /**
     * 从调库项的 flowId 或 flowKey 解析出流程定义 ID
     *
     * 优先使用 flowId（直接匹配），否则用 flowKey 查 DB 获取
     *
     * 执行顺序：
     * 1. 调库项直接指定流程 ID 时使用该 ID
     * 2. 仅指定流程编码时查询启用定义，缺少标识或查不到时返回 null
     *
     * @param item 调库项
     * @return 调库项使用的流程 ID，未指定流程或找不到启用定义时返回 null
     */
    private Long resolveFlowIdFromItem(ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item) {
        // 1. 调库项直接指定流程 ID 时使用该 ID
        if (item.getFlowId() != null) {
            return item.getFlowId();
        }
        // 2. 仅指定流程编码时查询启用定义，缺少标识或查不到时返回 null
        if (item.getFlowKey() != null && !item.getFlowKey().isEmpty()) {
            FlowDefinitionBo def = flowMapper.queryActiveFlowByKey(item.getFlowKey());
            return def != null ? def.getId() : null;
        }
        return null;
    }

    /**
     * 判断提交项是否为手工调库项
     *
     * 执行顺序：
     * 1. 检查调整项标签，未填写和标为手工的项都按手工调库处理
     *
     * @param item 调库项
     * @return true=手工调库项（含未填标签的项），false=其他类型的调库项
     */
    private boolean isManualSubmitItem(ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item) {
        return item.getItemTag() == null || item.getItemTag().isEmpty() || ItemType.MANUAL.getCode().equals(item.getItemTag());
    }

    /**
     * 生成调库批次号
     *
     * 执行顺序：
     * 1. 分别取无流程、调入或调出的批次流水号
     * 2. 拼接 BOND 前缀、批次时间和四位流水号
     *
     * @param manualItem 手工明细
     * @param noFlow 当前分组是否未配置流程
     * @param shared 本次提交的共享数据
     * @return 新生成的调库批次号
     */
    private String buildAdjustBatchNo(
            ForbiddenAbsPoolAdjustSubmitReq.AdjustItem manualItem, boolean noFlow, SubmitSharedData shared) {
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
     * 获取或生成同组调库批次号
     *
     * 执行顺序：
     * 1. 检查调库项有分组标识，并查找该组已有的批次号
     * 2. 该组还没有批次号时，先找到同组手工项，再生成批次号并保存供本组其他项复用
     *
     * @param req 调库提交请求
     * @param item 调库项
     * @param noFlow 当前分组是否未配置流程
     * @param shared 本次提交的共享数据
     * @return 调整项所属分组的批次号
     */
    private String resolveAdjustBatchNo(
            ForbiddenAbsPoolAdjustSubmitReq req,
            ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item,
            boolean noFlow,
            SubmitSharedData shared) {
        // 1. 检查调库项有分组标识，并查找该组已有的批次号
        String groupKey = item.getAdjustGroupKey();
        if (groupKey == null || groupKey.isEmpty()) {
            throw new BizException("调库分组标识不能为空");
        }
        String batchNo = shared.adjustBatchNoMap.get(groupKey);
        // 2. 该组还没有批次号时，先找到同组手工项，再生成批次号并保存供本组其他项复用
        if (batchNo == null) {
            // 生成调库批次号
            batchNo = buildAdjustBatchNo(resolveManualSubmitItem(req, item), noFlow, shared);
            shared.adjustBatchNoMap.put(groupKey, batchNo);
        }
        return batchNo;
    }

    /**
     * 获取同组手工调库项，联动/互斥项按手工项共用流程和批次号
     *
     * 执行顺序：
     * 1. 判断提交项是否为手工调库项
     *
     * @param req 调库提交请求
     * @param item 调库项
     * @return 本次调整项明细
     */
    private ForbiddenAbsPoolAdjustSubmitReq.AdjustItem resolveManualSubmitItem(
            ForbiddenAbsPoolAdjustSubmitReq req, ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item) {
        // 判断提交项是否为手工调库项
        if (isManualSubmitItem(item)) {
            return item;
        }
        String groupKey = item.getAdjustGroupKey();
        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem candidate : req.getItems()) {
            // 判断提交项是否为手工调库项
            if (isManualSubmitItem(candidate) && groupKey != null && groupKey.equals(candidate.getAdjustGroupKey())) {
                return candidate;
            }
        }
        throw new BizException("未找到调库分组对应的手工调整记录");
    }

    /**
     * 为指定流程 ID 构建运行时快照（定义 + 活跃版本 + 节点索引 + 连线列表）
     *
     * 节点按 DB ID 建立索引，供遍历连线时快速查找目标节点
     *
     * 执行顺序：
     * 1. 查询该流程的所有版本（ORDER BY ver_num DESC），取第一个 status='active' 的版本
     * 2. 加载该版本的节点和连线
     * 3. 节点按 DB ID 建立索引，供后续边遍历快速查找目标节点
     * 4. 加载该版本的审批节点配置，按 nodeId 建立索引
     *
     * @param flowId 流程定义 ID
     * @return FlowSnapshot，若流程不存在或无活跃版本则返回 null
     */
    private FlowSnapshot buildFlowSnapshot(Long flowId) {
        FlowDefinitionBo def = flowMapper.queryFlowById(flowId);
        if (def == null) {
            return null;
        }

        // 1. 查询该流程的所有版本（ORDER BY ver_num DESC），取第一个 status='active' 的版本
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

        // 2. 加载该版本的节点和连线
        List<FlowNodeBo> nodes = flowMapper.queryFlowNodeListByVersionId(activeVersion.getId());
        List<FlowEdgeBo> edges = flowMapper.queryFlowEdgeListByVersionId(activeVersion.getId());

        // 3. 节点按 DB ID 建立索引，供后续边遍历快速查找目标节点
        Map<Long, FlowNodeBo> nodeMap = new HashMap<>();
        for (FlowNodeBo node : nodes) {
            nodeMap.put(node.getId(), node);
        }

        // 4. 加载该版本的审批节点配置，按 nodeId 建立索引
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
     * 判断流程是否为直通流程（开始后无需人工待办即可到结束节点）
     *
     * 与 createInitialSteps 自动完成范围对齐：start/end/auto 节点、initiator、approval_strategy=auto
     * 可无人工到达 end；主路径存在 preempt/all 等人工审批则为非直通
     * 例：债券特殊策略入库（发起人→多层 auto→结束）应判为直通
     *
     * 执行顺序：
     * 1. 开始节点为空时返回false
     *
     * @param snapshot 流程快照（含节点索引和连线列表）
     * @return true=直通流程（可直接生效），false=需要审批
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
        // 从当前节点沿主路径（排除 reject 支路）是否仅经无需人工节点即可到达 end
        return canCompleteWithoutHumanApproval(snapshot, startNode, startNode, new HashSet<Long>());
    }

    /**
     * 从当前节点沿主路径（排除 reject 支路）是否仅经无需人工节点即可到达 end
     *
     * 执行顺序：
     * 1. 终点可直接完成，空节点或回到正在检查的节点时停止判定
     * 2. 检查审批节点能否由发起人或系统直接完成
     * 3. 沿每条非驳回连线继续检查，所有路径可结束时才判为直通
     *
     * @param snapshot 流程快照
     * @param current 当前流程节点
     * @param startNode 流程开始节点
     * @param visiting 当前流程路径已访问的节点 ID
     * @return true=从当前节点沿非驳回路径可以在无需人工审批的情况下结束，false=存在无法自动完成的路径
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
                // 判断当前审批节点是否应由流程发起人自动完成
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
                // 从当前节点沿主路径（排除 reject 支路）是否仅经无需人工节点即可到达 end
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
     * 提交ABS 禁投池调入申请并绑定上传附件
     *
     * 按所选流程分别生成待审批或直通调库记录，直通记录由本次提交统一落池
     *
     * 执行顺序：
     * 1. 规则：报告必填（in_report_restriction / out_report_restriction）
     * 2. 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @param directApplyLogs 用于收集待直接生效日志的列表
     * @return 生成的调入日志 ID 列表
     */
    private List<Long> executeInboundSubmit(ForbiddenAbsPoolAdjustSubmitReq req, SubmitSharedData shared,
                                            SysAttachmentService.SubmissionFiles submissionFiles,
                                            List<IpAdjustLogBo> directApplyLogs) {

        List<Long> generatedIds = new ArrayList<>();

        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 报告必填校验（按池 in_report_restriction；关联码项由同组手工项承担报告，跳过）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                // 规则：报告必填（in_report_restriction / out_report_restriction）
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getInReportRestriction() : null,
                        // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
                        resolveItemSecurityCode(req, item));
            }
            // 获取同组手工调库项，联动/互斥/关联项按手工项共用流程和批次号
            ForbiddenAbsPoolAdjustSubmitReq.AdjustItem manualItem = resolveManualSubmitItem(req, item);
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
                forbiddenAbsPoolAdjustMapper.addAdjustLog(logBo);
                // ABS 直通调入也要登记到提交日志集合，供主体快照按日志 ID 保存
                shared.submittedLogs.put(logBo.getId(), logBo);
                generatedIds.add(logBo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(logBo.getId(), item, submissionFiles, req.getAdjusterId());
                // 有流程定义的直通流程仍记录开始、发起、结束步骤
                if (isManualSubmitItem(item) && snapshot != null && logBo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    createInitialSteps(logBo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                }

                // 收集直通日志，待本次全部调库项生成后统一复核并落池
                logBo.setAdjustLogId(logBo.getId());
                directApplyLogs.add(logBo);
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，流程中）
                // 构建调库日志实体
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem, shared);
                bo.setAdjustBatchNo(adjustBatchNo);
                bo.setAuditStatus(AuditStatus.SUBMITTED.getCode());
                forbiddenAbsPoolAdjustMapper.addAdjustLog(bo);
                shared.submittedLogs.put(bo.getId(), bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 手工项创建初始流程步骤，联动/互斥项共用同批次流程状态
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    boolean flowFinished = createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                    if (flowFinished) {
                        bo.setAuditStatus(AuditStatus.APPROVED.getCode());
                        forbiddenAbsPoolAdjustMapper.editAdjustLogAuditStatus(bo.getId(), adjustBatchNo, AuditStatus.APPROVED.getCode());
                        bo.setAdjustLogId(bo.getId());
                        directApplyLogs.add(bo);
                    }
                }
            }
        }

        return generatedIds;
    }

    /**
     * 提交ABS 禁投池调出申请并绑定上传附件
     *
     * 按所选流程分别生成待审批或直通调库记录，直通记录由本次提交统一落池
     *
     * 执行顺序：
     * 1. 规则：报告必填（in_report_restriction / out_report_restriction）
     * 2. 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @param directApplyLogs 用于收集待直接生效日志的列表
     * @return 生成的调出日志 ID 列表
     */
    private List<Long> executeOutboundSubmit(ForbiddenAbsPoolAdjustSubmitReq req, SubmitSharedData shared,
                                             SysAttachmentService.SubmissionFiles submissionFiles,
                                             List<IpAdjustLogBo> directApplyLogs) {

        List<Long> generatedIds = new ArrayList<>();

        for (ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 报告必填校验（按池 out_report_restriction；关联码项由同组手工项承担报告，跳过）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                // 规则：报告必填（in_report_restriction / out_report_restriction）
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getOutReportRestriction() : null,
                        // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
                        resolveItemSecurityCode(req, item));
            }
            // 获取同组手工调库项，联动/互斥/关联项按手工项共用流程和批次号
            ForbiddenAbsPoolAdjustSubmitReq.AdjustItem manualItem = resolveManualSubmitItem(req, item);
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
                forbiddenAbsPoolAdjustMapper.addAdjustLog(bo);
                shared.submittedLogs.put(bo.getId(), bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 有流程定义的直通流程仍记录开始、发起、结束步骤
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                }

                // 收集直通日志，待本次全部调库项生成后统一复核并落池
                directApplyLogs.add(bo);
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，流程中）
                // 构建调库日志实体
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem, shared);
                bo.setAdjustBatchNo(adjustBatchNo);
                bo.setAuditStatus(AuditStatus.SUBMITTED.getCode());
                forbiddenAbsPoolAdjustMapper.addAdjustLog(bo);
                shared.submittedLogs.put(bo.getId(), bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 手工项创建初始流程步骤，联动/互斥项共用同批次流程状态
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    boolean flowFinished = createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                    if (flowFinished) {
                        forbiddenAbsPoolAdjustMapper.editAdjustLogAuditStatus(bo.getId(), adjustBatchNo, AuditStatus.APPROVED.getCode());
                        directApplyLogs.add(bo);
                    }
                }
            }
        }

        return generatedIds;
    }

    /**
     * 保存 ABS 调库申请快照并更新可编辑的证券基础信息
     *
     * 主体身份字段在审核通过时由 {@link AbsIdentityService} 写入主档
     *
     * 执行顺序：
     * 1. 合并前端传入的可编辑字段与已加载的证券信息，并使用本次校验后的主体字段
     * 2. 按实际调库证券代码保存申请快照
     * 3. 更新证券基础信息表中本次允许修改的其他字段
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     */
    private void postSubmitProcess(ForbiddenAbsPoolAdjustSubmitReq req, SubmitSharedData shared) {
        // 1. 合并可编辑字段并使用本次校验后的评级主体资料
        SecurityInfoBo merged = req.getSecurityInfo() == null ? shared.securityInfo
                // 使用本次已有证券信息构建申请快照，避免保存时重新查询主档
                : buildMergedSecurityInfo(req.getSecurityCode(), req.getSecurityInfo(), shared.securityInfo);
        // 主体身份取本次校验后的共享值，只落发行人五字段，担保人字段不承载 ABS 权益人；
        // 这些主体字段在提交阶段只用于申请快照
        merged.setIssuer(shared.securityInfo.getIssuer());
        merged.setIssuerCode(shared.securityInfo.getIssuerCode());
        merged.setRatingBondissuer(shared.securityInfo.getRatingBondissuer());
        merged.setRatingBondissuerAgency(shared.securityInfo.getRatingBondissuerAgency());
        merged.setInnerIssuerRating(shared.securityInfo.getInnerIssuerRating());
        merged.setGuarantor(shared.securityInfo.getGuarantor());
        merged.setGuarantorId(shared.securityInfo.getGuarantorId());
        merged.setInnerGuarantorRating(shared.securityInfo.getInnerGuarantorRating());
        req.setSecurityInfo(merged);
        // 2. 按每个实际调库证券保存申请快照
        absIdentityService.saveSnapshots(merged, shared.submittedLogs, req.getAdjusterId(),
                shared.selectedRatingCompany.getRightsHolderName(),
                shared.selectedRatingCompany.getSelfSelectedRightsHolderName());
        // 3. 更新证券基础信息表中可编辑的其他字段
        forbiddenAbsPoolAdjustMapper.editSecurityInfoForAdjust(req);
    }

    /**
     * 构建证券基础信息全量更新参数
     *
     * 前端只需要传入可能变更的字段；这里先加载数据库当前完整快照，
     * 再用前端传入字段覆盖同名字段。若前端显式传入 null，也会覆盖为 null，用于清空字段
     *
     * 执行顺序：
     * 1. 没有预读数据时按证券代码读取主档，再合并页面允许变更的基础信息
     *
     * @param securityCode 证券代码
     * @param changedField 前端传入的变更字段
     * @return 本次完整快照
     */
    private SecurityInfoBo buildMergedSecurityInfo(String securityCode, SecurityInfoBo changedField) {
        // 独立调用没有共享主档时读取一次，再交给统一合并方法
        return buildMergedSecurityInfo(securityCode, changedField, null);
    }

    /**
     * 使用本次已有证券信息构建申请快照，避免保存时重新查询主档
     *
     * 执行顺序：
     * 1. 优先取本次共享数据，没有共享数据的独立调用才读取主档
     * 2. 复制已有信息并覆盖页面编辑字段，不修改共享对象
     *
     * @param securityCode 证券代码
     * @param changedField 页面编辑字段
     * @param loaded 本次已加载的证券信息
     * @return 本次完整快照
     */
    private SecurityInfoBo buildMergedSecurityInfo(String securityCode, SecurityInfoBo changedField, SecurityInfoBo loaded) {
        SecurityInfoBo source = loaded == null ? forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(securityCode) : loaded;
        if (source == null) throw new BizException("证券不存在");
        SecurityInfoBo current = new SecurityInfoBo();
        BeanUtils.copyProperties(source, current);
        // 复用本次已加载数据覆盖页面编辑值
        mergeSecurityInfo(current, changedField);
        return current;
    }

    /**
     * 将调库详情页可编辑字段逐项覆盖到当前证券实体
     *
     * 执行顺序：
     * 1. 把表单修改的证券简称、主体、期限和评级等字段写回当前证券信息
     *
     * @param current 待写入编辑字段的证券副本
     * @param changedField 前端传入的变更字段
     */
    private void mergeSecurityInfo(SecurityInfoBo current, SecurityInfoBo changedField) {
        current.setFullName(changedField.getFullName());
        current.setShortName(changedField.getShortName());
        current.setWindCode(changedField.getWindCode());
        current.setIssuer(changedField.getIssuer());
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
        current.setDateExists(changedField.getDateExists());
        current.setFundUse(changedField.getFundUse());
        current.setPromptReason(changedField.getPromptReason());
        current.setAnalysis(changedField.getAnalysis());
        // 担保人内评改由 AIS 按所选代码查询，不回写主档；其余不可编辑字段同样保留数据库原值
    }

    // ═══════════════════════════════════════════════════════════
    //  查询类接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询调库记录列表（全量，不分页）
     *
     * 有批次号时按批次返回同批全部记录（含终态）
     * 无批次号时为「调库入口未带批次时的查询」（调库页面点「调库」、未带 adjustBatchNo）：
     * 排除终态 {@code audit_status NOT IN ('-1','20','21','99')}，只留在途——历史约定，避免无批时铺满流水；
     * 有批次的历史/事宜跳转不走该过滤。调库页面常不展示调库记录区，但仍可能调用本接口
     *
     * 执行顺序：
     * 1. 检查证券代码不能为空
     * 2. 查询投资池全路径名称，用于调库记录展示
     *
     * @param req 有批次时 adjustBatchNo 必填即可；无批次时需 securityCode
     * @return 调库日志列表
     */
    public List<AdjustLogDto> queryAdjustLogList(ForbiddenAbsPoolAdjustReq req) {
        boolean hasBatch = req.getAdjustBatchNo() != null && !req.getAdjustBatchNo().isEmpty();
        if (!hasBatch && (req.getSecurityCode() == null || req.getSecurityCode().isEmpty())) {
            throw new BizException("证券代码不能为空");
        }
        List<IpAdjustLogBo> logs = forbiddenAbsPoolAdjustMapper.queryAdjustLogList(
                req.getSecurityCode(), req.getAdjustBatchNo());
        if (logs.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询投资池全路径名称，用于调库记录展示
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();

        List<AdjustLogDto> result = new ArrayList<>();
        for (IpAdjustLogBo log : logs) {
            AdjustLogDto dto = new AdjustLogDto();
            dto.setId(log.getId());
            // 回填证券代码，便于前端展示关联调整行
            dto.setSecurityCode(log.getSecurityCode());
            dto.setSecurityShortName(log.getSecurityShortName());
            dto.setSecurityType(log.getSecurityType());
            dto.setAdjustBatchNo(log.getAdjustBatchNo());
            dto.setPoolPath(poolFullNameMap.get(log.getTargetPoolId()));
            dto.setAdjustType(log.getAdjustType());
            dto.setAdjustMode(log.getAdjustMode());
            dto.setFlowType(log.getFlowType());
            dto.setFlowName(log.getFlowName());
            dto.setAuditStatus(log.getAuditStatus());
            dto.setAdjustReason(log.getAdjustReason());
            dto.setAdjustAdvice(log.getAdjustAdvice());
            dto.setSubmitTime(log.getSubmitTime());
            result.add(dto);
        }
        return result;
    }

    /**
     * 查询指定调库记录或同批次共用流程的步骤列表
     *
     * 执行顺序：
     * 1. 按批次列表查询调库审批步骤
     * 2. 查询指定调库记录或同批次共用流程的步骤列表
     *
     * @param req 需携带 securityCode
     * @return 调库审批步骤明细，未传日志 ID 和有效批次号或没有步骤时返回空列表
     */
    public List<IpAdjustStepDto> queryAdjustStepList(ForbiddenAbsPoolAdjustReq req) {
        Long adjustLogId = req.getAdjustLogId();
        String adjustBatchNo = req.getAdjustBatchNo();
        if (adjustLogId == null && (adjustBatchNo == null || adjustBatchNo.isEmpty())) {
            return Collections.emptyList();
        }
        List<IpAdjustStepBo> steps = forbiddenAbsPoolAdjustMapper.queryAdjustStepByBatchList(adjustLogId, adjustBatchNo);
        if (steps.isEmpty()) {
            return Collections.emptyList();
        }
        List<IpAdjustStepDto> result = new ArrayList<>();
        for (IpAdjustStepBo bo : steps) {
            IpAdjustStepDto dto = new IpAdjustStepDto();
            dto.setId(bo.getId());
            dto.setAdjustLogId(bo.getAdjustLogId());
            dto.setAdjustBatchNo(bo.getAdjustBatchNo());
            dto.setFlowNodeId(bo.getFlowNodeId());
            dto.setNodeCode(bo.getNodeCode());
            dto.setNodeLabel(bo.getNodeLabel());
            dto.setNodeType(bo.getNodeType());
            dto.setApprovalStrategy(bo.getApprovalStrategy());
            dto.setSortOrder(bo.getSortOrder());
            dto.setStepStatus(bo.getStepStatus());
            dto.setHandlerId(bo.getHandlerId());
            dto.setHandlerName(bo.getHandlerName());
            dto.setProcessAction(bo.getProcessAction());
            dto.setProcessComment(bo.getProcessComment());
            dto.setStartTime(bo.getStartTime());
            dto.setProcessTime(bo.getProcessTime());
            result.add(dto);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 入口
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验 ABS 禁投池调库可行性
     *
     * 执行顺序：
     * 1. 校验 ABS 证券和允许手工调整的目标池
     * 2. 读取证券、投资池和关系等共用数据
     * 3. 按目标池和方向登记手工项，避免生成重复自动项
     * 4. 校验调入项并补充联动和互斥项
     * 5. 校验调出项并合并校验结果
     * 6. 回填主券信息，按 Wind ISIN 展开同池同向的关联市场证券
     * 7. 标记 ABS 主体身份冲突，冲突项不可调整
     * 8. 匹配流程候选和推荐项，并与逐项结果一起返回
     *
     * @param req 包含证券代码和一个或多个用户选择的调库项
     * @return 包含手动项和自动项的完整校验结果列表
     */
    public AdjustCheckDto checkAdjust(ForbiddenAbsPoolAdjustCheckReq req) {

        // 1. 校验 ABS 证券和允许手工调整的目标池
        validateCheckAdjustReq(req);

        for (ForbiddenAbsPoolAdjustCheckReq.CheckItem item : req.getItems()) {
            // 校验手工目标池仅允许 15/16/17
            validateAllowedManualPoolId(item.getTargetPoolId());
        }

        // 2. 读取证券、投资池和关系等共用数据
        AdjustSharedData shared = loadSharedData(req);


        // 3. 按目标池和方向登记手工项，避免生成重复自动项
        Set<String> coveredKeys = new HashSet<>();
        for (ForbiddenAbsPoolAdjustCheckReq.CheckItem item : req.getItems()) {
            coveredKeys.add(item.getTargetPoolId() + "_" + item.getAdjustMode());
        }

        // 4. 校验调入项并补充联动和互斥项
        List<AdjustCheckDto.CheckResultItem> resultItems = new ArrayList<>(
                // 执行调入校验并补充联动、互斥调库项
                executeInAdjustCheck(req, shared, coveredKeys));

        // 5. 校验调出项并合并校验结果
        resultItems.addAll(executeOutAdjustCheck(req, shared, coveredKeys));


        // 6. 回填主券信息，按 Wind ISIN 展开同池同向的关联市场证券
        expandRelatedMarketCodeItems(req, shared, resultItems);

        // 7. 标记 ABS 主体身份冲突，冲突项不可调整
        absIdentityService.applyIdentityCheckFailures(shared.getSecurityInfo(), resultItems);

        // 8. 匹配流程候选和推荐项，并与逐项结果一起返回
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
     * 校验 ABS 禁投池调库检查请求的必填项
     *
     * 对请求入参进行合法性检查，均为内存操作，不涉及任何 DB 查询
     * 任意一项不通过则直接抛出异常，终止后续流程
     *
     * 执行顺序：
     * 1. 检查证券代码不能为空
     * 2. 检查调库项不能为空
     *
     * @param req 调库校验请求
     */
    private void validateCheckAdjustReq(ForbiddenAbsPoolAdjustCheckReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
    }

    /**
     * 加载 ABS 禁投池校验共用的证券、评级和投资池数据
     *
     * 集中执行本次校验所需的全部 DB 查询，构建 {@link AdjustSharedData}
     * 调入和调出校验共用这些数据
     * 证券存在性校验也在此阶段完成（查到 null 则抛异常）
     *
     * 执行顺序：
     * 1. 证券基础信息（兼含存在性校验）
     * 2. 直接检查本次已加载的证券，不再为 ABS 类型重复读取主档
     * 3. ABS 按证券池调库口径校验权益人，自选权益人优先
     * 4. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
     * 5. 评级下调：主体与展望按发行人评级判定，担保人按本次所选担保人代码判定
     * 6. 基金评分（基金证券调入校验用，透传请求级 fundRate）
     * 7. 是否放开规则：是=跳过主体债入库矩阵校验（对齐批量调库 releaseRules）
     *
     * @param req 调库校验请求（需携带 securityCode）
     * @return 封装了本次校验全量共享数据的 AdjustSharedData
     */
    private AdjustSharedData loadSharedData(ForbiddenAbsPoolAdjustCheckReq req) {
        // 1. 证券基础信息（兼含存在性校验）
        SecurityInfoBo securityInfo = req.getSecurityInfo() != null && req.getSecurityInfo().getSecurityType() != null
                ? req.getSecurityInfo() : forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }
        // 2. 直接检查本次已加载的证券，不再为 ABS 类型重复读取主档
        validateAbsSecurity(securityInfo);
        // 3. ABS 按证券池调库口径校验权益人，自选权益人优先
        securityPoolAdjustService.applySelectedRatingCompany(securityInfo, req.getGuarantorCode(), req.getRightsHolderCode(),
                req.getSelfSelectedRightsHolderCode(), true, req.getSelectedRatingCompany());

        // 4. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        for (InvestmentPoolBo p : allPools) {
            poolMap.put(p.getId(), p);
        }

        // 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
        List<Long> currentPoolIdList = forbiddenAbsPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList);

        // 全量投资池关系配置，构建三层嵌套 Map（poolId → relationType → 关联池列表）
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                forbiddenAbsPoolAdjustMapper.queryAllPoolRelationList());

        // 提取本次请求中调入/调出各自涉及的目标池 ID 集合，供互斥冲突校验使用
        Set<Long> requestInPoolIds  = new HashSet<>();
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (ForbiddenAbsPoolAdjustCheckReq.CheckItem item : req.getItems()) {
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
        shared.setHasPendingProcess(forbiddenAbsPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()));
        shared.setPendingProcessNodeLabel(forbiddenAbsPoolAdjustMapper.querySecurityPendingProcessNodeLabel(req.getSecurityCode()));
        // 将目标池列表转换为顶级投资池组 ID 集合
        shared.setPendingPoolGroupIds(resolvePoolGroupIds(
                forbiddenAbsPoolAdjustMapper.queryPendingManualTargetPoolIdList(req.getSecurityCode(), null), poolMap));
        // 观察池：证券或主体已在观察池则不得高于矩阵最好档；重点观察名单：禁新增，已在库只能去五级
        shared.setSecurityInObservePool(forbiddenAbsPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()));
        shared.setIssuerInObservePool(securityPoolAdjustService.queryIssuerInPoolType(securityInfo, "observe"));
        shared.setSecurityInRestrictedPool(forbiddenAbsPoolAdjustMapper.querySecurityInRestrictedPool(req.getSecurityCode()));
        shared.setIssuerInRestrictedPool(securityPoolAdjustService.queryIssuerInPoolType(securityInfo, "restricted"));
        // 5. 评级下调：主体与展望按发行人评级判定，担保人按本次所选担保人代码判定
        shared.setIssuerRatingDowngraded(ratingDowngradeChecker.isIssuerDowngraded(securityInfo));
        shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(securityInfo));
        shared.setGuarantorRatingDowngraded(ratingDowngradeChecker.isGuarantorDowngraded(req.getGuarantorCode()));
        shared.setRequestInPoolIds(requestInPoolIds);
        shared.setRequestOutPoolIds(requestOutPoolIds);
        // 6. 基金评分（基金证券调入校验用，透传请求级 fundRate）
        shared.setFundRate(req.getFundRate());
        // 7. 是否放开规则：是=跳过主体债入库矩阵校验（对齐批量调库 releaseRules）
        shared.setReleaseRules(req.isReleaseRules());
        return shared;
    }

    /**
     * 校验 ABS 禁投池手工调入项和配套项
     *
     * 执行顺序：
     * 1. 逐项准备目标池、持仓数量和分组标识，执行调入校验
     * 2. 按目标池关系补充联动调入和必要的互斥调出项，跳过已覆盖的组合
     * 3. 从手工项和互斥项的结果确定同组是否可以调整
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调入方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeInAdjustCheck(
            ForbiddenAbsPoolAdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (ForbiddenAbsPoolAdjustCheckReq.CheckItem item : req.getItems()) {
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 构建手工调库项分组 Key
            String adjustGroupKey = buildAdjustGroupKey(item);

            // 手动调入项：前置校验 + 调入校验
            int poolCurrentCount = forbiddenAbsPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
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
                                // 构建自动生成的联动/互斥调整项校验结果
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
            if (inMutex != null) {
                for (Long mutexId : inMutex) {
                    // 证券不在该互斥池中则无需生成配套调出项
                    if (!shared.getCurrentPoolIds().contains(mutexId)) {
                        continue;
                    }
                    if (coveredKeys.add(mutexId + "_调出")) {
                        // 构建自动生成的联动/互斥调整项校验结果  与手动项校验流程完全相同，区别在于：  targetPoolId 来自池关系配置，由系统自动推导，非用户选择 itemTag 标记为ItemType.LINKAGE.getCode()或ItemType.MUTEX.getCode()，前端据此区分显示样式
                        AdjustCheckDto.CheckResultItem autoItem =
                                // 构建自动生成的联动/互斥调整项校验结果
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
     * 校验 ABS 禁投池手工调出项和配套项
     *
     * 执行顺序：
     * 1. 逐项准备目标池、持仓数量和分组标识，执行调出校验
     * 2. 按池关系补充需要同步调出的联动项，保留手工项优先并跳过重复配套项
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调出方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeOutAdjustCheck(
            ForbiddenAbsPoolAdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (ForbiddenAbsPoolAdjustCheckReq.CheckItem item : req.getItems()) {
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 构建手工调库项分组 Key
            String adjustGroupKey = buildAdjustGroupKey(item);

            // 手动调出项：前置校验 + 调出校验
            int poolCurrentCount = forbiddenAbsPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
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
                                // 构建自动生成的联动/互斥调整项校验结果
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
     * 汇总 ABS 禁投池各手工调库项可选择的审批流程
     *
     * 调出时直接使用目标投资池配置的标准调出流程。调入时：信用债已在库返回上调/下调单流程，
     * 信用债未在库返回白名单/简易/默认调入三类候选，非信用债大库返回默认调入流程
     * 白名单和简易规则按伪代码保留入口，不改表结构
     *
     * 执行顺序：
     * 1. 逐项判断单条手工调库项可选择的审批流程
     * 2. 生成流程候选项去重 key
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param resultItems 前四阶段的校验结果
     * @return 流程候选项
     */
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptions(
            ForbiddenAbsPoolAdjustCheckReq req, AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> resultItems) {

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
     * 判断单条手工调库项可选择的审批流程
     *
     * 执行顺序：
     * 1. 手工调出：使用投资池定义的标准调出流程
     * 2. 手工调入：当前已在目标池调入互斥池时优先特殊审批（信用债大库默认排除）
     * 3. ── 目标池非信用债大库：仅返回默认调入流程 ──
     * 4. ── 证券已在信用债大库中：按当前池与目标池层级判断上调/下调 ──
     * 5. 构建简易流程候选项
     * 6. 默认调入流程
     * 7. 构建默认调入流程候选项
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param item 当前主项
     * @return 当前手工项的审批流程候选列表
     */
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptionsForItem(
            ForbiddenAbsPoolAdjustCheckReq req, AdjustSharedData shared, AdjustCheckDto.CheckResultItem item) {

        if (item == null || !item.isCanAdjust() || !ItemType.MANUAL.getCode().equals(item.getItemTag())) {
            return new ArrayList<>();
        }
        InvestmentPoolBo targetPool = shared.getPoolMap().get(item.getTargetPoolId());
        if (targetPool == null) {
            return new ArrayList<>();
        }

        // 1. 手工调出：使用投资池定义的标准调出流程
        if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
            // 解析流程名称：优先使用配置名称
            String flowName = resolveFlowName(targetPool.getOutFlowName(), "调出流程");
            // 判断目标池是否未配置审批流程
            boolean noApproval = isNoApprovalFlow(targetPool.getOutFlowId(), targetPool.getOutFlowKey());
            FlowOptionParam p = new FlowOptionParam();
            p.setFlowType(FlowType.NORMAL_OUTBOUND.getCode());
            p.setFlowName(flowName);
            p.setFlowId(targetPool.getOutFlowId());
            p.setFlowKey(targetPool.getOutFlowKey());
            p.setRecommended(true);
            p.setMatched(true);
            p.setSelectable(true);
            // 构建目标池默认流程说明
            p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                    null, flowName, noApproval, "默认调出流程")));
            p.setUnmatchReasons(new ArrayList<>());
            // 构建调出流程候选项
            return Collections.singletonList(buildPoolFlowOption(p));
        }

        // 2. 手工调入：当前已在目标池调入互斥池时优先特殊审批（信用债大库默认排除）
        AdjustCheckDto.FlowOption specialOption = resolveSpecialInboundFlowOption(targetPool, shared);
        if (specialOption != null) {
            return Collections.singletonList(specialOption);
        }

        // 3. ── 目标池非信用债大库：仅返回默认调入流程 ──
        if (!isCreditBondPool(targetPool)) {
            // 解析流程名称：优先使用配置名称
            String flowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
            // 判断目标池是否未配置审批流程
            boolean noApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
            FlowOptionParam p = new FlowOptionParam();
            p.setFlowType(FlowType.NORMAL_INBOUND.getCode());
            p.setFlowName(flowName);
            p.setFlowId(targetPool.getInFlowId());
            p.setFlowKey(targetPool.getInFlowKey());
            p.setRecommended(true);
            p.setMatched(true);
            p.setSelectable(true);
            // 构建目标池默认流程说明
            p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                    "目标池不在信用债大库中", flowName, noApproval, "默认调入流程")));
            p.setUnmatchReasons(new ArrayList<>());
            // 构建默认调入流程候选项
            return Collections.singletonList(buildPoolFlowOption(p));
        }

        // 4. ── 证券已在信用债大库中：按当前池与目标池层级判断上调/下调 ──
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
            options.add(buildWhitelistFlowOption(whitelistP));
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
        // 5. 构建简易流程候选项
        options.add(buildPoolFlowOption(simpleP));
        // 6. 默认调入流程
        String normalFlowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
        // 判断目标池是否未配置审批流程
        boolean normalNoApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
        FlowOptionParam normalP = new FlowOptionParam();
        normalP.setFlowType(FlowType.NORMAL_INBOUND.getCode());
        normalP.setFlowName(normalFlowName);
        normalP.setFlowId(targetPool.getInFlowId());
        normalP.setFlowKey(targetPool.getInFlowKey());
        normalP.setRecommended(FlowType.NORMAL_INBOUND.getCode().equals(recommendedType));
        normalP.setMatched(true);
        normalP.setSelectable(true);
        // 构建目标池默认流程说明
        normalP.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                null, normalFlowName, normalNoApproval, "默认调入流程")));
        normalP.setUnmatchReasons(new ArrayList<>());
        // 7. 构建默认调入流程候选项
        options.add(buildPoolFlowOption(normalP));
        return options;
    }

    /**
     * 命中调入互斥池特殊审批时构建特殊流程候选项
     *
     * 信用债大库（pool_type=credit_bond）默认排除：互斥场景仍走白名单/简易/升降级/默认调入，
     * 不强制覆盖为 bond:special-inbound
     *
     * 执行顺序：
     * 1. 信用债大库默认不走互斥特殊审批
     * 2. 构建特殊审批流程候选项
     *
     * @param targetPool 当前调整项的目标池配置
     * @param shared 本次校验的共享数据
     * @return 特殊调入流程候选，未命中时为 null
     */
    private AdjustCheckDto.FlowOption resolveSpecialInboundFlowOption(InvestmentPoolBo targetPool,
                                                                      AdjustSharedData shared) {
        if (targetPool == null || shared == null
                || shared.getPoolRelationMap() == null || shared.getCurrentPoolIds() == null) {
            return null;
        }
        // 1. 信用债大库默认不走互斥特殊审批
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
        FlowDefinitionBo flow = flowMapper.queryActiveFlowByKey(FLOW_KEY_SPECIAL_INBOUND);
        if (flow == null) {
            return null;
        }
        FlowOptionParam p = new FlowOptionParam();
        p.setFlowType(FlowType.SPECIAL_INBOUND.getCode());
        // 解析流程名称：优先使用配置名称，为空时回退到默认名称
        p.setFlowName(resolveFlowName(flow.getName(), "特殊审批流程"));
        p.setFlowId(flow.getId());
        p.setFlowKey(FLOW_KEY_SPECIAL_INBOUND);
        p.setRecommended(true);
        p.setMatched(true);
        p.setSelectable(true);
        p.setMatchReasons(Collections.singletonList("证券当前在目标池调入互斥池中，按特殊审批流程处理："
                // 将池 ID 列表转为"父级/子级"路径名称，以顿号连接，用于构建可读的失败原因消息
                + poolNames(matchedPoolIds, shared.getPoolMap())));
        p.setUnmatchReasons(new ArrayList<>());
        // 2. 构建特殊审批流程候选项
        return buildPoolFlowOption(p);
    }

    /**
     * 根据投资池 ID 列表转换投资池名称
     *
     * 执行顺序：
     * 1. 池 ID 为空时返回空字符串
     * 2. 逐个取池名称，缺少名称时以 ID 代替，再用顿号连接
     *
     * @param poolIds 需要转换的池 ID 列表
     * @param poolMap 全量池索引
     * @return 拼接后的池名，未传池时为空字符串
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
     * 生成流程候选项去重 key
     *
     * 执行顺序：
     * 1. 缺少流程候选时返回空字符串，有候选时用竖线连接流程 ID、Key、类型和名称
     *
     * @param option 待生成去重标识的流程候选
     * @return 流程候选去重标识，未传入候选时返回空字符串
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
     * 白名单流程命中判断入口
     *
     * 执行顺序：
     * 1. 证券期限 &lt;= 3 年（解析 date_exists_str）；
     * 2. 排除永续债、私募债、ABS 债；
     * 3. 债券类型属于债券类；
     * 4. 债券主体在白名单配置池中；
     * 5. 债券不是担保债
     *
     * 当前白名单池列表为空，主体白名单条件按未命中处理
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param matchReasons 用于收集匹配原因的列表
     * @param unmatchReasons 用于收集不匹配原因的列表
     * @return true=满足白名单流程条件，false=不满足白名单流程条件
     */
    private boolean isWhitelistFlowMatched(
            ForbiddenAbsPoolAdjustCheckReq req, AdjustSharedData shared, List<String> matchReasons, List<String> unmatchReasons) {
        SecurityInfoBo sec = shared.getSecurityInfo();

        // 1. 从 date_exists_str 解析证券期限并判断是否不超过 3 年
        BigDecimal remainYears = CreditBondRemainTermUtil.parseRemainTermYears(sec.getDateExistsStr());
        if (remainYears == null) {
            unmatchReasons.add("证券期限无法解析，date_exists_str 为空或格式不正确");
        } else if (remainYears.compareTo(new BigDecimal("3")) <= 0) {
            matchReasons.add("证券期限为 " + sec.getDateExistsStr() + "，未超过 3 年");
        } else {
            unmatchReasons.add("证券期限为 " + sec.getDateExistsStr() + "，超过 3 年");
        }

        // 2. 排除永续债、私募债、ABS 债
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

        // 3. 债券类型属于债券类
        String categoryType = forbiddenAbsPoolAdjustMapper.queryCategoryTypeBySecurityType(sec.getSecurityType());
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            matchReasons.add("债券类型属于债券类");
        } else {
            unmatchReasons.add("债券类型不属于债券类");
        }

        // 4. 债券主体在白名单配置池中（当前 WHITELIST_POOL_IDS 空集，暂不查库）
        if (!WHITELIST_POOL_IDS.isEmpty()) {
            // TODO 后续接入 queryIssuerInWhitelistPools 查询主体在白名单池
            matchReasons.add("主体在白名单池中");
        } else {
            unmatchReasons.add("白名单池未配置，主体在白名单池条件不成立");
        }

        // 5. 债券不是担保债
        boolean isGuaranteed = sec.getGuarantFlag() != null && sec.getGuarantFlag() == 1;
        if (!isGuaranteed) {
            matchReasons.add("债券非担保债");
        } else {
            unmatchReasons.add("债券为担保债，不符合白名单条件");
        }

        return unmatchReasons.isEmpty();
    }

    /**
     * 判断禁投池调入项是否满足简易流程条件
     *
     * 当前校验使用目标池、剩余期限和适用的入库历史，评级下调校验暂未启用
     *
     * 执行顺序：
     * 1. ABS 直接判为不支持简易调入
     * 2. 检查目标池是否为信用债大库一至三级池
     * 3. 检查剩余期限并与同主体在目标池的最长剩余期限比较
     * 4. 判断同主体近 180 天是否以一般流程调入目标池
     * 5. 按累计的匹配和失败原因返回结果
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param targetPool 当前调整项的目标池配置
     * @param matchReasons 用于收集匹配原因的列表
     * @param unmatchReasons 用于收集不匹配原因的列表
     * @return true=满足简易入库流程条件，false=不满足简易入库流程条件
     */
    private boolean isSimpleInboundFlowMatched(
            ForbiddenAbsPoolAdjustCheckReq req, AdjustSharedData shared, InvestmentPoolBo targetPool,
            List<String> matchReasons, List<String> unmatchReasons) {

        // 1. ABS 直接判为不支持简易调入
        if (CreditBondSpecialInboundRule.isAbs(shared.getSecurityInfo())) {
            unmatchReasons.add("ABS 不适用快速入库流程");
            return false;
        }
        // 2. 检查目标池是否为信用债大库一至三级池
        if (isCreditBondLevelOneToThree(targetPool)) {
            matchReasons.add("目标池属于信用债大库一、二、三级库");
        } else {
            unmatchReasons.add("目标池不是信用债大库一、二、三级库");
        }

        // 3. 检查剩余期限并与同主体在目标池的最长剩余期限比较
        BigDecimal remainDays = shared.getSecurityInfo().getDateExists();
        if (remainDays == null) {
            unmatchReasons.add("剩余期限无法解析，date_exists 为空");
        }

        if (remainDays != null && remainDays.compareTo(BigDecimal.ZERO) >= 0) {
            BigDecimal issuerPoolMaxRemainDays = forbiddenAbsPoolAdjustMapper.queryIssuerTargetPoolMaxRemainDays(
                    req.getSecurityCode(), targetPool.getId());
            if (issuerPoolMaxRemainDays == null) {
                matchReasons.add("目标池暂无同主体有效债券，不受在池最大期限限制");
            } else if (remainDays.compareTo(issuerPoolMaxRemainDays) <= 0) {
                // 格式化剩余期限展示文本（date_exists 原始天数，不换算成年）
                matchReasons.add("剩余期限为 " + formatRemainDays(remainDays)
                        // 格式化剩余期限展示文本（date_exists 原始天数，不换算成年）
                        + "，未超过同主体在池最大期限 " + formatRemainDays(issuerPoolMaxRemainDays));
            } else {
                // 格式化剩余期限展示文本（date_exists 原始天数，不换算成年）
                unmatchReasons.add("剩余期限为 " + formatRemainDays(remainDays)
                        // 格式化剩余期限展示文本（date_exists 原始天数，不换算成年）
                        + "，超过同主体在池最大期限 " + formatRemainDays(issuerPoolMaxRemainDays));
            }
        }

        // 4. 判断同主体近 180 天是否以一般流程调入目标池
        boolean hasNonSimpleInbound = forbiddenAbsPoolAdjustMapper.queryIssuerHasNonSimpleInboundWithinDays(
                req.getSecurityCode(), targetPool.getId(), 180);
        if (hasNonSimpleInbound) {
            matchReasons.add("该主体180天内以一般流程入过目标池，满足简易流程前提条件");
        } else {
            unmatchReasons.add("该主体180天内未以一般流程入过目标池，不满足简易流程前提条件");
        }

        // 条件5：主体评级和展望评级未下调，或下调时担保人评级未下调（暂时注释，不需要此校验；评级取值仍在 shared 中计算保留）
        /*
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
        */

        // 5. 按累计的匹配和失败原因返回结果
        return unmatchReasons.isEmpty();
    }

    /**
     * 判断信用债在库调整流程是上调还是下调
     *
     * 同一父级下，目标池 innerSort 小于当前所在池为升库，大于当前所在池为降库；
     * 未找到同父级当前池时返回 null，由调用方走目标池标准调入流程
     *
     * 执行顺序：
     * 1. 检查目标池是否有父池和档级，随后查找当前已在的同父级池
     * 2. 比较两个池的档级，确定上调或下调方向，找不到可比的池时留空
     *
     * @param targetPool 当前调整项的目标池配置
     * @param shared 本次校验的共享数据
     * @return 上调或下调流程类型，无法判断时为 null
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
     * 判断目标池是否属于信用债大库
     *
     * 执行顺序：
     * 1. 投资池为空时返回false
     *
     * @param pool 投资池
     * @return true=目标池属于信用债大库，false=目标池不属于信用债大库
     */
    private boolean isCreditBondPool(InvestmentPoolBo pool) {
        if (pool == null) {
            return false;
        }
        return PoolType.CREDIT_BOND.getCode().equals(pool.getPoolType());
    }

    /**
     * 判断是否为临时代码占位证券（security_source=temporary）
     *
     * 执行顺序：
     * 1. 证券有值且来源字段为 temporary 时判为临时证券
     *
     * @param sec 证券主数据
     * @return true=证券为临时代码占位证券，false=证券不是临时代码占位证券
     */
    private boolean isTemporarySecurity(SecurityInfoBo sec) {
        return sec != null && "temporary".equals(sec.getSecuritySource());
    }

    /**
     * 判断证券当前是否已在信用债大库中
     *
     * 执行顺序：
     * 1. 用当前在池 ID 查池配置，发现任一信用债大库即返回 true，遍历完仍未发现时返回 false
     *
     * @param shared 本次校验的共享数据
     * @return true=证券当前至少在一个信用债池，false=证券当前不在任何信用债池
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
     * 判断目标池是否为信用债大库一、二、三级库
     *
     * 执行顺序：
     * 1. 池类型是信用债且层级排序值在 1 到 3 之间时返回 true
     *
     * @param pool 投资池
     * @return true=目标池为信用债大库一至三级，false=目标池不属于这三个档级
     */
    private boolean isCreditBondLevelOneToThree(InvestmentPoolBo pool) {
        return pool != null
                && PoolType.CREDIT_BOND.getCode().equals(pool.getPoolType())
                && pool.getInnerSort() != null
                && pool.getInnerSort() >= 1
                && pool.getInnerSort() <= 3;
    }

    /**
     * 格式化剩余期限展示文本（date_exists 原始天数，不换算成年）
     *
     * 执行顺序：
     * 1. 缺少天数时返回空字符串，否则去掉小数末尾的零并追加「天」
     *
     * @param remainDays 标的剩余期限的天数
     * @return 带「天」单位的剩余期限，天数为 null 时返回空字符串
     */
    private String formatRemainDays(BigDecimal remainDays) {
        if (remainDays == null) {
            return "";
        }
        return remainDays.stripTrailingZeros().toPlainString() + " 天";
    }

    /**
     * 判断证券是否存在担保人
     *
     * 执行顺序：
     * 1. 检查担保人名称和代码，任一有内容即视为有担保人
     *
     * @param securityInfo 标的证券资料
     * @return true=有担保人名称或代码，false=没有担保人名称或代码
     */
    private boolean hasGuarantor(SecurityInfoBo securityInfo) {
        if (securityInfo == null) {
            return false;
        }
        return (securityInfo.getGuarantor() != null && !securityInfo.getGuarantor().trim().isEmpty())
                || (securityInfo.getGuarantorId() != null && !securityInfo.getGuarantorId().trim().isEmpty());
    }

    /**
     * 构建白名单流程候选项
     *
     * 执行顺序：
     * 1. 按标识查询生效流程
     * 2. 构建流程候选项（统一入口）
     *
     * @param p 用于组装流程候选的参数
     * @return 白名单流程候选
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
     * 构建目标池标准调入流程候选项
     *
     * 执行顺序：
     * 1. 解析流程名称：优先使用配置名称，为空时回退到默认名称
     * 2. 构建目标池默认流程说明
     * 3. 构建默认调入流程候选项
     *
     * @param targetPool 当前调整项的目标池配置
     * @param matchReason 已知的流程匹配原因
     * @return 默认调入流程候选
     */
    private AdjustCheckDto.FlowOption buildNormalInboundFlowOption(InvestmentPoolBo targetPool, String matchReason) {
        // 1. 解析流程名称：优先使用配置名称，为空时回退到默认名称
        String flowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
        // 判断目标池是否未配置审批流程
        boolean noApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
        FlowOptionParam p = new FlowOptionParam();
        p.setFlowType(FlowType.NORMAL_INBOUND.getCode());
        p.setFlowName(flowName);
        p.setFlowId(targetPool.getInFlowId());
        p.setFlowKey(targetPool.getInFlowKey());
        p.setRecommended(true);
        p.setMatched(true);
        p.setSelectable(true);
        // 2. 构建目标池默认流程说明
        p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                matchReason, flowName, noApproval, "默认调入流程")));
        p.setUnmatchReasons(new ArrayList<>());
        // 3. 构建默认调入流程候选项
        return buildPoolFlowOption(p);
    }

    /**
     * 构建目标池默认流程说明
     *
     * 执行顺序：
     * 1. 有前置说明时保留在文案开头
     * 2. 按是否需要审批写明采用目标池默认流程或按无流程处理
     *
     * @param prefix 已知的流程原因前缀
     * @param flowName 流程展示名称
     * @param noApproval 目标池是否未配置审批流程
     * @param flowLabel 用于提示的流程类别名称
     * @return 展示给用户的流程匹配原因
     */
    private String buildTargetFlowReason(String prefix, String flowName, boolean noApproval, String flowLabel) {
        String reasonPrefix = prefix != null && !prefix.isEmpty() ? prefix + "，" : "";
        if (noApproval) {
            return reasonPrefix + "目标池未配置" + flowLabel + "，按不需要审批处理";
        }
        return reasonPrefix + "使用目标池配置的默认「" + flowName + "」";
    }

    /**
     * 使用目标池配置构建流程候选项
     *
     * 调用方需先将 {@code configuredFlowName} 与 {@code fallbackFlowName} 决议为最终的
     * {@code flowName} 后传入 {@link FlowOptionParam}
     *
     * 执行顺序：
     * 1. 构建流程候选项（统一入口）
     *
     * @param p 用于组装流程候选的参数
     * @return 用于选择的池配置流程候选
     */
    private AdjustCheckDto.FlowOption buildPoolFlowOption(FlowOptionParam p) {
        // 判断目标池是否未配置审批流程
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
     * 判断目标池是否未配置审批流程
     *
     * 执行顺序：
     * 1. 同时检查流程 ID 和流程 Key，两者都未填写时判为无流程
     *
     * @param flowId 流程定义 ID
     * @param flowKey 选择的流程编码
     * @return true=流程 ID 和流程 Key 均未填写，false=至少填写了一个流程标识
     */
    private boolean isNoApprovalFlow(Long flowId, String flowKey) {
        return flowId == null && (flowKey == null || flowKey.isEmpty());
    }

    /**
     * 解析流程名称：优先使用配置名称，为空时回退到默认名称
     *
     * 执行顺序：
     * 1. 配置名称非空时按原值使用，缺失时返回传入的默认名称
     *
     * @param configuredName 配置的流程名称
     * @param fallbackName 未配置名称时使用的默认名称
     * @return 流程展示名称
     */
    private static String resolveFlowName(String configuredName, String fallbackName) {
        return configuredName != null && !configuredName.isEmpty() ? configuredName : fallbackName;
    }

    /**
     * 构建流程候选项（统一入口）
     *
     * 执行顺序：
     * 1. 填入流程类型、ID、Key 及名称
     * 2. 填入推荐、可选及匹配标志，并带上匹配和不匹配原因
     *
     * @param p 用于组装流程候选的参数
     * @return 返回给页面的流程候选
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
     * 先执行 checkCommonIn（通用：池锁定/品种/市场/pending/重复入池/容量/来源/限制/互斥/弹性），
     * 再按 categoryType 路由类型特有校验（债券到期 checkBondIn / 股票退市 checkStockIn 等）
     *
     * 执行顺序：
     * 1. 通用校验（所有类型都走）
     * 2. 类型特有校验（按 categoryType 路由）
     * 3. 按发行主体复核黑名单质押库调入条件
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkInConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 通用校验（所有类型都走）
        failures.addAll(checkCommonIn(ctx));
        // 2. 类型特有校验（按 categoryType 路由）
        String categoryType = ctx.getCategoryType();
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            // 债券类型特有调入校验
            failures.addAll(checkBondIn(ctx));
        } else if (CategoryType.STOCK.getCode().equals(categoryType)) {
            // 股票类型特有调入校验
            failures.addAll(checkStockIn(ctx));
        } else if (CategoryType.FUND.getCode().equals(categoryType)) {
            // 基金类型特有调入校验
            failures.addAll(checkFundIn(ctx));
        } else if (CategoryType.COMPANY.getCode().equals(categoryType)) {
            // 主体类型特有调入校验（主体不校验到期，暂无）
            failures.addAll(checkCompanyIn(ctx));
        }
        // 3. 按发行主体复核黑名单质押库调入条件
        addPledgeBlacklistFailure(failures, ctx, AdjustMode.IN.getCode());
        return failures;
    }

    /**
     * 通用调入校验（所有类型都走）
     *
     * 含池锁定、品种、市场、pending流程、重复入池、容量、来源池、限制池、互斥、弹性禁投
     * 不含证券到期（已拆到类型特有：债券到期 checkBondIn / 股票退市 checkStockIn）
     *
     * 执行顺序：
     * 1. 前置检查：目标池是否已锁定（最硬拦截，优先执行）
     * 2. 前置检查：证券品种是否符合目标池配置
     * 3. 前置检查：证券市场是否符合目标池配置
     * 4. 前置检查：是否存在待处理流程
     * 5. 入池检查：证券当前在弹性禁投池中（in_soft_restrict，警告不阻断）
     * 6. 入池检查：证券是否在全局禁止池（forbidden 禁投池）
     * 7. 开放日校验（按池 open_day_adjust，调入）
     *
     * @param ctx 调库校验上下文
     * @return 阻断调入的原因，空列表表示通过
     */
    private List<String> checkCommonIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 前置检查：目标池是否已锁定（最硬拦截，优先执行）
        addIfFailed(failures, inCheckPoolLocked(ctx));
        // 2. 前置检查：证券品种是否符合目标池配置
        addIfFailed(failures, inCheckVariety(ctx));
        // 3. 前置检查：证券市场是否符合目标池配置
        addIfFailed(failures, inCheckMarket(ctx));
        // 4. 前置检查：是否存在待处理流程
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
        // 5. 入池检查：证券当前在弹性禁投池中（in_soft_restrict，警告不阻断）
        addIfWarning(ctx.getWarnings(), inCheckElasticPool(ctx));
        // 6. 入池检查：证券是否在全局禁止池（forbidden 禁投池）
        addIfFailed(failures, inCheckForbiddenPool(ctx));
        // 行业限制校验（按池 industry_code，调入）——暂时不需要，先注释
        // addIfFailed(failures, inCheckIndustry(ctx));
        // 7. 开放日校验（按池 open_day_adjust，调入）
        addIfFailed(failures, inCheckOpenDay(ctx));
        return failures;
    }

    /**
     * 债券类型特有调入校验
     *
     * 含债券到期校验（maturity_date 早于今日则禁止调入）
     *
     * 执行顺序：
     * 1. 债券到期校验
     * 2. 主体债入库规则校验（信用债大库矩阵）
     *
     * @param ctx 调库校验上下文
     * @return 阻断债券调入的原因
     */
    private List<String> checkBondIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 债券到期校验
        addIfFailed(failures, inCheckBondMaturity(ctx));
        // 2. 主体债入库规则校验（信用债大库矩阵）
        addIfFailed(failures, inCheckMainGradeRule(ctx));
        return failures;
    }

    /**
     * 规则：主体债入库规则（credit_bond_pool_grade_rule 矩阵）
     *
     * 调入信用债大库池时，按债券的「主体内评分档 × 期限档」查矩阵得到允许调入的池列表
     * 担保债取主体与担保人内评孰高后再查矩阵；ABS 按特殊债：担保人内评 1 档只能调入一级库，无担保人或非 1 档至少下调一级
     * ABS「1 档」只看担保人内评。担保债或已在观察池：不得高于矩阵最好档，从该档开到五级
     * 已在重点观察名单：未在分级库不得新增（开关开启时强担保豁免），已在 1～4 级只能去五级。可转债/可交换债/CRMW 不适用信用债 1～5
     * 正式证券无内评禁止入信用债 1～5 级；临时代码默认档 4
     * 期限为空默认最长档（期限&gt;5）继续走矩阵，不跳过
     * 境外债目标池不走本规则。仅调入校验
     *
     * 执行顺序：
     * 1. 是否放开规则：是=跳过主体债入库矩阵校验（对齐批量调库 releaseRules）
     * 2. 前置门控：仅信用债分级库校验（境外债跳过）
     * 3. 证券自身或发行主体任一在重点观察名单，即走重点观察名单禁新增规则
     * 4. 取当前已在分级库最好档，供重点观察名单区分新增/已在库
     * 5. 含权口径取剩余期限年（date_exists_str 解析，含权/回购已是年）；算不出期限时按最长档继续走矩阵，不跳过
     * 6. 标准矩阵最好档，再叠加 ABS 特殊债口径
     * 7. 目标池是否在允许范围内（档位口径或矩阵点名的池）
     *
     * @param ctx 调库校验上下文
     * @return 分档矩阵未通过时的阻断原因，通过时为空
     */
    private String inCheckMainGradeRule(AdjustCheckContext ctx) {
        // 1. 是否放开规则：是=跳过主体债入库矩阵校验（对齐批量调库 releaseRules）
        if (ctx.isReleaseRules()) {
            return null;
        }
        InvestmentPoolBo pool = ctx.getTargetPool();
        // 2. 前置门控：仅信用债分级库校验（境外债跳过）
        if (!isGradedBondPool(pool)) {
            return null;
        }
        SecurityInfoBo sec = ctx.getSecurityInfo();
        if (sec == null) {
            return null;
        }
        if (CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(sec)) {
            return "可转债、可交换债、信用风险缓释工具不适用信用债分级库";
        }
        // 3. 证券自身或发行主体任一在重点观察名单，即走重点观察名单禁新增规则
        boolean inRestricted = ctx.isSecurityInRestrictedPool() || ctx.isIssuerInRestrictedPool();
        // 4. 取当前已在分级库最好档，供重点观察名单区分新增/已在库
        Integer currentGradedSort = resolveCurrentGradedSort(ctx.getCurrentPoolIds(), ctx.getPoolMap());
        // 重点观察名单：禁止新增，已在库只能去五级
        String restrictedFail = CreditBondSpecialInboundRule.checkRestricted(
                sec, inRestricted, currentGradedSort,
                CreditBondSpecialInboundRule.isGradedLevelPool(pool) ? pool.getInnerSort() : null,
                BusinessSwitches.STRONG_GUARANTEE_ENABLED);
        if (restrictedFail != null) {
            return restrictedFail;
        }
        // 临时代码默认档 4，担保债取孰高
        String gradeCode = resolveMatrixGradeCode(sec);
        if (gradeCode == null) {
            return "主体无内评分";
        }
        // 5. 含权口径取剩余期限年（date_exists_str 解析，含权/回购已是年）；算不出期限时按最长档继续走矩阵，不跳过
        String bucketCode = matchTermBucket(CreditBondRemainTermUtil.resolveRemainTermYears(sec));
        if (bucketCode == null) {
            return "无法匹配债券期限档";
        }
        List<Long> allowedPoolIds = creditBondGradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(gradeCode, bucketCode);
        if (allowedPoolIds == null || allowedPoolIds.isEmpty()) {
            return "主体债入库矩阵未配置允许池";
        }
        // 6. 标准矩阵最好档，再叠加 ABS 特殊债口径
        Integer bestAllowedSort = resolveBestAllowedSort(allowedPoolIds, ctx.getPoolMap());
        boolean inObserve = ctx.isSecurityInObservePool() || ctx.isIssuerInObservePool();
        CreditBondSpecialInboundRule.GradedInboundMode inboundMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(sec, inObserve);
        Integer startSort = inboundMode.resolveStartSort(bestAllowedSort);
        // 7. 目标池是否在允许范围内（档位口径或矩阵点名的池）
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
     * 按剩余期限（年）匹配信用债期限分组编码
     *
     * 遍历启用的 credit_bond_term_bucket，按 min_term_year/max_term_year + inclusive 标志判定区间归属
     * 入参为 {@link CreditBondRemainTermUtil#resolveRemainTermYears}（date_exists_str 解析，含权/回购已是年）
     *
     * 执行顺序：
     * 1. 查询启用期限区间列表
     *
     * @param remainTermYears 剩余期限年数；null 时按最长档（期限&gt;5）兜底
     * @return 期限档编码；无可用档或年数落不进任何档时返回 null
     */
    private String matchTermBucket(BigDecimal remainTermYears) {
        return CreditBondRemainTermUtil.matchTermBucket(
                remainTermYears, creditBondGradeRuleMapper.queryEnabledTermBucketList());
    }

    /**
     * 取两个主体内评分档中评级较高者（sort_no 较小者；担保债按主体与担保人孰高）
     *
     * 执行顺序：
     * 1. 查询启用评级档级列表
     * 2. 取两个主体内评分档中评级较高者（sort_no 较小者
     *
     * @param gradeCode1 主体内评档
     * @param gradeCode2 担保人内评档
     * @return 更好的那档
     */
    private String pickBetterRatingGrade(String gradeCode1, String gradeCode2) {
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
        if (sort1 != null && sort2 != null && sort2 < sort1) {
            return gradeCode2;
        }
        return gradeCode1;
    }

    /**
     * 查矩阵用内评档：临时代码默认 4；担保债取主体与担保人孰高
     *
     * 执行顺序：
     * 1. 空串视为无内评
     * 2. 临时代码占位常无内评，默认最低档 4
     *
     * @param sec 证券主数据
     * @return 内评档编码；正式券无内评返回 null
     */
    private String resolveMatrixGradeCode(SecurityInfoBo sec) {
        // 1. 空串视为无内评
        String gradeCode = normalizeGradeCode(sec.getInnerIssuerRating());
        // 2. 临时代码占位常无内评，默认最低档 4
        if (gradeCode == null && isTemporarySecurity(sec)) {
            gradeCode = "4";
        }
        if (gradeCode != null && CreditBondSpecialInboundRule.isGuaranteed(sec)
                && sec.getInnerGuarantorRating() != null && !sec.getInnerGuarantorRating().isEmpty()) {
            // 担保债用更好的那档查矩阵
            gradeCode = pickBetterRatingGrade(gradeCode, sec.getInnerGuarantorRating());
        }
        return gradeCode;
    }

    /**
     * 是否信用债大库树（主体评分档套件范围）
     *
     * 执行顺序：
     * 1. 检查是否符合属于按内评分档的信用债池的条件
     *
     * @param pool 投资池
     * @return true=属于按内评分档的信用债池，false=不属于按内评分档的信用债池
     */
    private boolean isGradedBondPool(InvestmentPoolBo pool) {
        return pool != null && CreditBondSpecialInboundRule.isGradedBondPoolType(pool.getPoolType());
    }

    /**
     * 当前已在分级库的最好档（inner_sort 最小）
     *
     * 执行顺序：
     * 1. 遍历当前在池记录，只比较有档级的信用债分级池
     * 2. 从有效档位中取最小的排序值
     *
     * @param currentPoolIds 当前在池 ID
     * @param poolMap 全量池索引
     * @return 最好档；未在分级库返回 null
     */
    private Integer resolveCurrentGradedSort(Set<Long> currentPoolIds, Map<Long, InvestmentPoolBo> poolMap) {
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
     * 标准矩阵允许池的最好档
     *
     * 执行顺序：
     * 1. 允许的投资池ID 列表为空时返回空值
     *
     * @param allowedPoolIds 矩阵允许的信用债池 ID
     * @param poolMap 全量池索引
     * @return 最小 inner_sort；无法解析返回 null
     */
    private Integer resolveBestAllowedSort(List<Long> allowedPoolIds, Map<Long, InvestmentPoolBo> poolMap) {
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
     * 这一级信用债库能不能调入
     * 只能调入一级库 / 下调一级：只留起始档那一级；至少下调一级 / 担保或观察池：起始档及更差；
     * 算不出起始档或普通债：只认矩阵点名的那些池 ID
     *
     * 执行顺序：
     * 1. 需要按档级判定且有起始档级时，按准入方向比较目标档位
     * 2. 否则核对目标是否为信用债池且池 ID 在允许列表中
     *
     * @param pool 待判断叶子池
     * @param allowedPoolIds 矩阵允许的信用债池 ID
     * @param inboundMode 准入方式
     * @param startSort 档位规则的起始档（1～5）
     * @param poolMap 全量池索引（保留参数，便于调用方统一传入）
     * @return true=分档规则允许调入目标池，false=分档规则不允许调入目标池
     */
    private boolean isLeafAllowedByGradeRule(InvestmentPoolBo pool, List<Long> allowedPoolIds,
                                             CreditBondSpecialInboundRule.GradedInboundMode inboundMode,
                                             Integer startSort,
                                             Map<Long, InvestmentPoolBo> poolMap) {
        if (inboundMode.usesSort() && startSort != null) {
            return inboundMode.allows(pool.getInnerSort(), startSort);
        }
        // 判断目标池是否属于信用债大库
        if (isCreditBondPool(pool)) {
            return allowedPoolIds != null && allowedPoolIds.contains(pool.getId());
        }
        return false;
    }

    /**
     * 去掉信用债大库整棵树
     *
     * 执行顺序：
     * 1. 逐池检查是否属于信用债分级库，只保留其他投资池
     *
     * @param pools 原池列表
     * @return 不含信用债分级库的列表
     */
    private List<InvestmentPoolBo> excludeGradedBondPools(List<InvestmentPoolBo> pools) {
        if (pools == null || pools.isEmpty()) {
            return pools;
        }
        List<InvestmentPoolBo> result = new ArrayList<InvestmentPoolBo>();
        for (InvestmentPoolBo p : pools) {
            // 是否信用债大库树（主体评分档套件范围）
            if (!isGradedBondPool(p)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 校验改判目标池是否符合主体债入库矩阵（评级联动，调入审批改判用）
     *
     * 按债券的主体内评分档 × 期限档查 credit_bond_pool_grade_rule 矩阵得允许池列表，
     * 改判池须在列表内。复用 inCheckMainGradeRule 的矩阵查询逻辑
     *
     * 执行顺序：
     * 1. 按证券代码查询证券主档
     * 2. 证券不存在，无法校验改判池时终止处理
     * 3. 按矩阵和类型规则取可改判的分级库
     * 4. 该证券不符合主体债入库矩阵，无法改判时终止处理
     * 5. 改判池不在主体债入库矩阵允许列表内时终止处理
     *
     * @param securityCode 证券代码
     * @param redirectPoolId 改判目标池 ID
     * @throws BizException 改判池不在矩阵允许列表内或证券无评级/期限档
     */
    public void validateRedirectPool(String securityCode, Long redirectPoolId) {
        if (securityCode == null || securityCode.isEmpty() || redirectPoolId == null) {
            return;
        }
        SecurityInfoBo sec = forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (sec == null) {
            throw new BizException("证券不存在，无法校验改判池");
        }
        // 按矩阵和类型规则取可改判的分级库
        List<Long> allowedPoolIds = queryAllowedPoolIdsForSecurity(sec);
        if (allowedPoolIds == null || allowedPoolIds.isEmpty()) {
            throw new BizException("该证券不符合主体债入库矩阵，无法改判");
        }
        if (!allowedPoolIds.contains(redirectPoolId)) {
            throw new BizException("改判池不在主体债入库矩阵允许列表内");
        }
    }

    /**
     * 查询证券的主体债入库矩阵允许池列表（评级联动改判池候选项）
     *
     * 执行顺序：
     * 1. 按矩阵和类型规则展开改判候选项
     * 2. 全量池 Map 取池详情
     *
     * @param securityCode 证券代码
     * @return 允许调入的池列表（含池名），空列表表示无匹配矩阵
     */
    public List<InvestmentPoolBo> queryRedirectPoolOptions(String securityCode) {
        if (securityCode == null || securityCode.isEmpty()) {
            return new ArrayList<>();
        }
        SecurityInfoBo sec = forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (sec == null) {
            return new ArrayList<>();
        }
        // 1. 按矩阵和类型规则展开改判候选项
        List<Long> allowedPoolIds = queryAllowedPoolIdsForSecurity(sec);
        if (allowedPoolIds == null || allowedPoolIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 2. 全量池 Map 取池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        for (InvestmentPoolBo p : investmentPoolMapper.queryPoolList()) {
            poolMap.put(p.getId(), p);
        }
        List<InvestmentPoolBo> result = new ArrayList<>();
        for (Long pid : allowedPoolIds) {
            InvestmentPoolBo p = poolMap.get(pid);
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 最终审批落池前按老系统工作流口径复核动态业务状态
     *
     * 执行顺序：
     * 1. ABS 身份校验覆盖所有目标池
     * 2. 查询投资池列表
     * 3. 构建全量投资池关系 Map
     * 4. 查询全部投资池关系列表
     * 5. 按证券代码查询证券主档
     * 6. 查询证券当前投资池 ID列表
     *
     * @param logList 同批次调库记录
     */
    public void recheckBeforeFinalApproval(List<IpAdjustLogBo> logList) {
        // 1. 校验整批 ABS 调库记录的主体身份
        absIdentityService.validateApprovalIdentities(logList);

        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 2. 读取并锁定涉及的投资池，准备本批池关系和调库方向
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, p -> p));
        List<Long> poolIds = logList.stream().map(IpAdjustLogBo::getTargetPoolId)
                .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        if (!poolIds.isEmpty()) {
            // 按 ID 查询本次调整涉及的目标池
            investmentPoolMapper.queryExistingPoolIdList(poolIds);
        }
        Map<Long, Integer> inboundIncrements = new HashMap<>();
        // 构建全量投资池关系 Map
        Map<Long, Map<String, List<Long>>> relationMap = buildPoolRelationMap(
                forbiddenAbsPoolAdjustMapper.queryAllPoolRelationList());
        Set<Long> projectedInPoolIds = logList.stream()
                .filter(log -> AdjustMode.IN.getCode().equals(log.getAdjustMode()))
                .map(IpAdjustLogBo::getTargetPoolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> projectedOutPoolIds = logList.stream()
                .filter(log -> AdjustMode.OUT.getCode().equals(log.getAdjustMode()))
                .map(IpAdjustLogBo::getTargetPoolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 3. 逐条按证券主档、当前池和本批变更复核池规则
        for (IpAdjustLogBo log : logList) {
            InvestmentPoolBo pool = poolMap.get(log.getTargetPoolId());
            if (pool == null || "enabled".equals(pool.getStatus()) == false
                    || (pool.getIsDeleted() != null && pool.getIsDeleted() == 1)) {
                // 抛出包含调库项上下文的最终审批复核异常
                throwApprovalRecheckFailure(log, "目标投资池不存在或已停用");
            }
            SecurityInfoBo security = forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(log.getSecurityCode());
            if (security == null) {
                // 抛出包含调库项上下文的最终审批复核异常
                throwApprovalRecheckFailure(log, "证券不存在");
            }
            if (log.getId() != null && CreditBondSpecialInboundRule.isAbs(security)) {
                absIdentityService.restoreSnapshotSelection(security, log, false);
            }
            Set<Long> currentPoolIds = new HashSet<>(
                    forbiddenAbsPoolAdjustMapper.querySecurityCurrentPoolIdList(log.getSecurityCode()));
            AdjustCheckContext ctx = new AdjustCheckContext();
            ctx.setSecurityInfo(security);
            ctx.setTargetPool(pool);
            ctx.setPoolMap(poolMap);
            ctx.setCurrentPoolIds(currentPoolIds);
            ctx.setTargetPoolRelations(relationMap.getOrDefault(pool.getId(), Collections.emptyMap()));
            ctx.setCategoryType(forbiddenAbsPoolAdjustMapper.queryCategoryTypeBySecurityType(security.getSecurityType()));
            ctx.setRequestInPoolIds(projectedInPoolIds);
            ctx.setRequestOutPoolIds(projectedOutPoolIds);
            String failure;
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                // 规则：目标池已锁定（lock_flag=1），再规则：证券是否已在目标池中
                failure = firstFailure(inCheckPoolLocked(ctx), inCheckSecurityAlreadyInPool(ctx),
                        // 规则：投资品种（variety_codes），再规则：来源池限制，再规则：调入限制池（in_restrict）
                        inCheckVariety(ctx), inCheckSourcePool(ctx), inCheckRestrictPool(ctx),
                        // 规则：全局禁止池（forbidden 禁投池）
                        inCheckForbiddenPool(ctx));
                int currentCount = forbiddenAbsPoolAdjustMapper.queryPoolCurrentCount(pool.getId());
                int increment = inboundIncrements.getOrDefault(pool.getId(), 0);
                if (failure == null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                        && currentCount + increment >= pool.getMaxCapacity()) {
                    failure = "目标投资池容量已满";
                }
                inboundIncrements.put(pool.getId(), increment + 1);
            } else {
                ctx.setTargetPoolEntryTime(forbiddenAbsPoolAdjustMapper.queryPoolEntryTime(
                        log.getSecurityCode(), pool.getId()));
                // 规则：目标池已锁定（lock_flag=1），再规则：证券是否不在目标池中
                failure = firstFailure(outCheckPoolLocked(ctx), outCheckSecurityNotInPool(ctx),
                        // 规则：调出限制池（out_restrict），再规则：调出冻结期（frozen_period_in）
                        outCheckRestrictPool(ctx), outCheckFrozenPeriod(ctx));
            }
            if (failure == null && PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(pool.getId())) {
                failure = pledgeBlacklistRuleService.validateCompanies(absIdentityService.queryRiskCompanyCodes(security),
                        log.getAdjustMode(), projectedInPoolIds, projectedOutPoolIds);
            }
            if (failure != null) {
                // 抛出包含调库项上下文的最终审批复核异常
                throwApprovalRecheckFailure(log, failure);
            }
        }
    }

    /**
     * 对直通调库日志执行最终复核并立即落池
     *
     * 执行顺序：
     * 1. 按最终审批口径复核动态业务状态
     * 2. 将复核通过的直通调库日志统一应用到当前池状态
     * 3. ABS 直通完成后调用统一的内部报告生成逻辑
     *
     * @param logList 本次需要立即生效的完整调库日志集合
     */
    private void recheckAndApplyDirectLogs(List<IpAdjustLogBo> logList) {
        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 1. 按最终审批口径复核动态业务状态
        recheckBeforeFinalApproval(logList);
        // 2. 将复核通过的直通调库日志统一应用到当前池状态
        applyPoolStatusChanges(logList);
        // 3. ABS 直通完成后调用统一的内部报告生成逻辑
        reportService.addInternalReportsOnFinish(logList);
    }

    /**
     * 将已完成最终复核的调库日志应用到当前池状态
     *
     * 执行顺序：
     * 1. ABS 身份校验覆盖所有目标池
     * 2. 批量保存 ABS 人工结论，不给普通债逐行增加主档查询
     *
     * @param logList 已通过最终复核的调库日志集合
     */
    public void applyPoolStatusChanges(List<IpAdjustLogBo> logList) {
        // 1. ABS 身份校验覆盖所有目标池
        absIdentityService.approveIdentities(logList);

        for (IpAdjustLogBo log : logList) {
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                log.setAuditStatus(AuditStatus.APPROVED.getCode());
                log.setAdjustLogId(log.getId());
                int inserted = forbiddenAbsPoolAdjustMapper.addPoolStatus(log);
                if (inserted != 1) {
                    throw new BizException("证券[" + log.getSecurityCode() + "]调入投资池["
                            + log.getTargetPoolName() + "]失败：入池状态写入异常");
                }
            } else if (AdjustMode.OUT.getCode().equals(log.getAdjustMode())) {
                int deleted = forbiddenAbsPoolAdjustMapper.deletePoolStatusSoft(
                        log.getSecurityCode(), log.getTargetPoolId());
                if (deleted == 0) {
                    throw new BizException("证券[" + log.getSecurityCode() + "]已不在投资池["
                            + log.getTargetPoolName() + "]，调出失败");
                }
            }

        }
        // 2. 批量保存 ABS 人工结论，不给普通债逐行增加主档查询
        absCompanySyncService.recordManualDecisions(logList);
    }

    /**
     * 返回第一个非空失败原因
     *
     * 执行顺序：
     * 1. 按原顺序查找第一条非空文本，全部为空时返回 null
     *
     * @param failures 用于收集阻断原因的列表
     * @return 第一条非空失败原因，没有失败原因时返回 null
     */
    private String firstFailure(String... failures) {
        for (String failure : failures) {
            if (failure != null && !failure.isEmpty()) {
                return failure;
            }
        }
        return null;
    }

    /**
     * 抛出包含调库项上下文的最终审批复核异常
     *
     * 执行顺序：
     * 1. 将证券代码、调库方向、目标池名称和复核原因合成异常消息后抛出
     *
     * @param log 当前调库日志
     * @param reason 单条校验返回的失败原因，null 表示通过
     */
    private void throwApprovalRecheckFailure(IpAdjustLogBo log, String reason) {
        throw new BizException("证券[" + log.getSecurityCode() + "]" + log.getAdjustMode()
                + "投资池[" + log.getTargetPoolName() + "]失败：" + reason);
    }

    /**
     * 查该券可调入的信用债 1～5 级池 ID（按档位口径或矩阵点名的池，含重点观察名单）
     *
     * 执行顺序：
     * 1. 临时代码默认档 4，担保债取孰高
     * 2. 含权口径取剩余期限年匹配期限档；算不出期限时按最长档继续走矩阵
     * 3. 标准最好档再按特殊债/担保/观察池口径展开信用债分级叶子
     * 4. 改判候选项：证券自身或发行主体已在重点观察名单
     * 5. 改判候选项用同一套档位规则（含观察池）
     *
     * @param sec 证券主数据
     * @return 允许调入的信用债分级库 ID；无匹配返回空列表
     */
    private List<Long> queryAllowedPoolIdsForSecurity(SecurityInfoBo sec) {
        if (CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(sec)) {
            return new ArrayList<Long>();
        }
        // 1. 临时代码默认档 4，担保债取孰高
        String gradeCode = resolveMatrixGradeCode(sec);
        if (gradeCode == null) {
            return new ArrayList<Long>();
        }
        // 2. 含权口径取剩余期限年匹配期限档；算不出期限时按最长档继续走矩阵
        String bucketCode = matchTermBucket(CreditBondRemainTermUtil.resolveRemainTermYears(sec));
        if (bucketCode == null) {
            return new ArrayList<Long>();
        }
        List<Long> matrixIds = creditBondGradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(gradeCode, bucketCode);
        if (matrixIds == null || matrixIds.isEmpty()) {
            return new ArrayList<Long>();
        }
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<Long, InvestmentPoolBo>();
        for (InvestmentPoolBo p : investmentPoolMapper.queryPoolList()) {
            poolMap.put(p.getId(), p);
        }
        // 3. 标准最好档再按特殊债/担保/观察池口径展开信用债分级叶子
        Integer bestAllowedSort = resolveBestAllowedSort(matrixIds, poolMap);
        // 4. 改判候选项：证券自身或发行主体已在重点观察名单
        boolean inRestricted = false;
        if (sec.getWindCode() != null) {
            inRestricted = forbiddenAbsPoolAdjustMapper.querySecurityInRestrictedPool(sec.getWindCode())
                    || securityPoolAdjustService.queryIssuerInPoolType(sec, "restricted");
        }
        Set<Long> currentPoolIds = new HashSet<Long>();
        if (sec.getWindCode() != null) {
            List<Long> currentIdList = forbiddenAbsPoolAdjustMapper.querySecurityCurrentPoolIdList(sec.getWindCode());
            if (currentIdList != null) {
                currentPoolIds.addAll(currentIdList);
            }
        }
        // 当前已在分级库的最好档（inner_sort 最小）
        Integer currentGradedSort = resolveCurrentGradedSort(currentPoolIds, poolMap);
        // 5. 改判候选项用同一套档位规则（含观察池）
        boolean inObserve = false;
        if (sec.getWindCode() != null) {
            inObserve = forbiddenAbsPoolAdjustMapper.querySecurityInObservePool(sec.getWindCode())
                    || securityPoolAdjustService.queryIssuerInPoolType(sec, "observe");
        }
        CreditBondSpecialInboundRule.GradedInboundMode inboundMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(sec, inObserve);
        Integer startSort = inboundMode.resolveStartSort(bestAllowedSort);
        List<Long> result = new ArrayList<Long>();
        for (InvestmentPoolBo p : poolMap.values()) {
            if (!CreditBondSpecialInboundRule.isGradedLevelPool(p)) {
                continue;
            }
            // 改判同样套重点观察名单：禁新增、已在 1～4 只能去五级
            String restrictedFail = CreditBondSpecialInboundRule.checkRestricted(
                    sec, inRestricted, currentGradedSort, p.getInnerSort(), BusinessSwitches.STRONG_GUARANTEE_ENABLED);
            if (restrictedFail != null) {
                continue;
            }
            // 按档位口径或矩阵点名的池收集改判候选项
            if (isLeafAllowedByGradeRule(p, matrixIds, inboundMode, startSort, poolMap)) {
                result.add(p.getId());
            }
        }
        return result;
    }

    /**
     * 股票类型特有调入校验
     *
     * 含股票退市校验（delist_date 早于今日则禁止调入）
     *
     * 执行顺序：
     * 1. 检查股票退市限制，记录未通过原因
     * 2. 检查目标池评级限制，汇总未通过原因
     *
     * @param ctx 调库校验上下文
     * @return 阻断股票调入的原因
     */
    private List<String> checkStockIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 检查股票退市限制，记录未通过原因
        addIfFailed(failures, inCheckStockDelist(ctx));
        // TODO 股票分管人限制及 StockResearch/investrank 评级来源尚未迁移，当前仅保留评级入口
        // 2. 检查目标池评级限制，汇总未通过原因
        addIfFailed(failures, inCheckGradeAstrict(ctx));
        return failures;
    }

    /**
     * 基金类型特有调入校验
     *
     * 含基金评分校验（fund_rate_limit）：池配置评分限制表达式时，校验请求传入的 fundRate 是否满足
     * 对应老项目 checkInPool:1092-1120
     *
     * 执行顺序：
     * 1. 用本次传入的评分核对目标池规则，并收集未通过的原因
     *
     * @param ctx 调库校验上下文
     * @return 基金评分未通过时的原因列表，通过时返回空列表
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
     * 池配置了基金评分限制表达式（如 3&lt;=#rate&lt;=8）时，调入校验请求传入的 fundRate 是否满足
     * #rate 为基金评分占位。表达式支持 &lt;=#rate / &lt;#rate（下限）、#rate&lt;= / #rate&lt;（上限）及组合，
     * 各条件须同时满足。fundRate 为空或解析失败则校验失败
     * 对应老项目 checkInPool:1092-1120。仅调入校验（老代码 checkOutPool 无）
     *
     * 执行顺序：
     * 1. 未传基金评分或解析失败，校验失败
     * 2. 下限：&lt;=#rate（下限 &lt;= fundRate）
     * 3. 下限：&lt;#rate（下限 &lt; fundRate）
     * 4. 上限：#rate&lt;=（fundRate &lt;= 上限）优先于 #rate&lt;（fundRate &lt; 上限）
     *
     * @param ctx 调库校验上下文
     * @return 基金评分未通过时的阻断原因，通过时为空
     */
    private String inCheckFundRate(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getFundRateLimit() == null || pool.getFundRateLimit().trim().isEmpty()) {
            return null;
        }
        String rate = pool.getFundRateLimit().replaceAll(" ", "");
        String fundRateStr = ctx.getFundRate();
        // 1. 未传基金评分或解析失败，校验失败
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
        // 2. 下限：<=#rate（下限 <= fundRate）
        if (rate.indexOf("<=#rate") > 0
                // 安全解析基金评分表达式边界值，解析失败返回 0.0（对齐老项目 NumberTool.safeToDouble 默认 0.0）
                && !(parseRateBound(rate.split("<=#rate")[0]) <= fundRate)) {
            ok = false;
        }
        // 3. 下限：<#rate（下限 < fundRate）
        if (rate.indexOf("<#rate") > 0
                // 安全解析基金评分表达式边界值，解析失败返回 0.0（对齐老项目 NumberTool.safeToDouble 默认 0.0）
                && !(parseRateBound(rate.split("<#rate")[0]) < fundRate)) {
            ok = false;
        }
        // 4. 上限：#rate<=（fundRate <= 上限）优先于 #rate<（fundRate < 上限）
        if (rate.indexOf("#rate<=") > 0) {
            String[] parts = rate.split("#rate<=");
            // 安全解析基金评分表达式边界值，解析失败返回 0.0（对齐老项目 NumberTool.safeToDouble 默认 0.0）
            if (!(fundRate <= parseRateBound(parts[parts.length - 1]))) {
                ok = false;
            }
        } else if (rate.indexOf("#rate<") > 0) {
            String[] parts = rate.split("#rate<");
            // 安全解析基金评分表达式边界值，解析失败返回 0.0（对齐老项目 NumberTool.safeToDouble 默认 0.0）
            if (!(fundRate < parseRateBound(parts[parts.length - 1]))) {
                ok = false;
            }
        }
        if (!ok) {
            return pool.getPoolName() + "的评分，必须在" + rate.replace("#rate", "基金评分");
        }
        return null;
    }

    /**
     * 安全解析基金评分表达式边界值，解析失败返回 0.0（对齐老项目 NumberTool.safeToDouble 默认 0.0）
     *
     * 执行顺序：
     * 1. 去掉文本首尾空白后按 Double 解析，解析异常时使用 0.0
     *
     * @param s 待处理的文本
     * @return 用于评分比较的数值边界
     */
    private double parseRateBound(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 主体类型特有调入校验（主体不校验到期，暂无）
     *
     * 执行顺序：
     * 1. 当前没有适用于主体调入的专属规则，返回空的失败原因列表
     *
     * @param ctx 调库校验上下文
     * @return 空的失败原因列表
     */
    private List<String> checkCompanyIn(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    /**
     * 调出校验：通用校验 + 类型特有校验
     *
     * 先执行 checkCommonOut（通用：池锁定/pending/不在池/冻结期/限制/互斥/弹性），
     * 再按 categoryType 路由类型特有校验（债券到期 checkBondOut / 股票退市 checkStockOut 等）
     *
     * 执行顺序：
     * 1. 通用校验（所有类型都走）
     * 2. 类型特有校验（按 categoryType 路由）
     * 3. 按发行主体复核黑名单质押库调出条件
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkOutConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 通用校验（所有类型都走）
        failures.addAll(checkCommonOut(ctx));
        // 2. 类型特有校验（按 categoryType 路由）
        String categoryType = ctx.getCategoryType();
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            // 债券类型特有调出校验
            failures.addAll(checkBondOut(ctx));
        } else if (CategoryType.STOCK.getCode().equals(categoryType)) {
            // 股票类型特有调出校验
            failures.addAll(checkStockOut(ctx));
        } else if (CategoryType.FUND.getCode().equals(categoryType)) {
            // 基金类型特有调出校验（暂无，后续 P2 加基金评分）
            failures.addAll(checkFundOut(ctx));
        } else if (CategoryType.COMPANY.getCode().equals(categoryType)) {
            // 主体类型特有调出校验（主体不校验到期，暂无）
            failures.addAll(checkCompanyOut(ctx));
        }
        // 3. 按发行主体复核黑名单质押库调出条件
        addPledgeBlacklistFailure(failures, ctx, AdjustMode.OUT.getCode());
        return failures;
    }

    /**
     * ABS 债进入或退出黑名单质押库时，按发行主体复核三条件
     *
     * 执行顺序：
     * 1. ABS 按全部关联主体检查原有黑名单质押库硬条件
     * 2. 汇总禁止类池校验需要的主体，已确认身份后不再受其他权益人影响
     * 3. 汇总黑名单质押库校验失败原因
     *
     * @param failures 校验失败原因
     * @param ctx 调库校验上下文
     * @param adjustMode 调整方向
     */
    private void addPledgeBlacklistFailure(List<String> failures, AdjustCheckContext ctx, String adjustMode) {
        if (ctx == null || ctx.getTargetPool() == null
                || !PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(ctx.getTargetPool().getId())) {
            return;
        }
        SecurityInfoBo security = ctx.getSecurityInfo();
        String companyCode = security == null ? null : security.getIssuerCode();
        String failure = CreditBondSpecialInboundRule.isAbs(security)
                ? pledgeBlacklistRuleService.validateCompanies(absIdentityService.queryRiskCompanyCodes(security), adjustMode,
                        ctx.getRequestInPoolIds(), ctx.getRequestOutPoolIds())
                : pledgeBlacklistRuleService.validate(companyCode, adjustMode,
                        ctx.getRequestInPoolIds(), ctx.getRequestOutPoolIds());
        // 汇总黑名单质押库校验失败原因
        addIfFailed(failures, failure);
    }

    /**
     * 通用调出校验（所有类型都走）
     *
     * 含池锁定、pending流程、不在池、冻结期、限制池、互斥、弹性禁投
     * 不含证券到期（已拆到类型特有：债券到期 checkBondOut / 股票退市 checkStockOut）
     *
     * 执行顺序：
     * 1. 前置检查：目标池是否已锁定（最硬拦截，优先执行）
     * 2. 前置检查：是否存在待处理流程
     * 3. 出池检查：证券是否不在来源池中
     * 4. 出池检查：是否在冻结期内（入池后N天不可调出，须在确认在池后校验）
     * 5. 出池检查：本次请求中是否同时勾选了互斥池（不可同时调出）
     * 6. 出池检查：证券当前在弹性禁投池中（out_soft_restrict，警告不阻断）
     * 7. 开放日校验（按池 open_day_adjust，调出）
     *
     * @param ctx 调库校验上下文
     * @return 阻断调出的原因，空列表表示通过
     */
    private List<String> checkCommonOut(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 前置检查：目标池是否已锁定（最硬拦截，优先执行）
        addIfFailed(failures, outCheckPoolLocked(ctx));
        // 2. 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 3. 出池检查：证券是否不在来源池中
        addIfFailed(failures, outCheckSecurityNotInPool(ctx));
        // 4. 出池检查：是否在冻结期内（入池后N天不可调出，须在确认在池后校验）
        addIfFailed(failures, outCheckFrozenPeriod(ctx));
        // 出池检查：证券当前在调出限制池中（out_restrict）
        addIfFailed(failures, outCheckRestrictPool(ctx));
        // 出池检查：是否与互斥池冲突（证券当前在互斥池中）
        addIfFailed(failures, outCheckMutexPool(ctx));
        // 5. 出池检查：本次请求中是否同时勾选了互斥池（不可同时调出）
        addIfFailed(failures, outCheckMutexConflict(ctx));
        // 6. 出池检查：证券当前在弹性禁投池中（out_soft_restrict，警告不阻断）
        addIfWarning(ctx.getWarnings(), outCheckElasticPool(ctx));
        // 7. 开放日校验（按池 open_day_adjust，调出）
        addIfFailed(failures, outCheckOpenDay(ctx));
        return failures;
    }

    /**
     * 债券类型特有调出校验
     *
     * 含债券到期校验（maturity_date 早于今日则禁止调出）
     *
     * 执行顺序：
     * 1. 债券到期校验
     *
     * @param ctx 调库校验上下文
     * @return 阻断债券调出的原因
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
     * 含股票退市校验（delist_date 早于今日则禁止调出）
     *
     * 执行顺序：
     * 1. 股票退市校验
     *
     * @param ctx 调库校验上下文
     * @return 阻断股票调出的原因
     */
    private List<String> checkStockOut(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 股票退市校验
        addIfFailed(failures, outCheckStockDelist(ctx));
        return failures;
    }

    /**
     * 基金类型特有调出校验（暂无，后续 P2 加基金评分）
     *
     * 执行顺序：
     * 1. 当前没有适用于基金调出的专属规则，返回空的失败原因列表
     *
     * @param ctx 调库校验上下文
     * @return 空的失败原因列表
     */
    private List<String> checkFundOut(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    /**
     * 主体类型特有调出校验（主体不校验到期，暂无）
     *
     * 执行顺序：
     * 1. 当前没有适用于主体调出的专属规则，返回空的失败原因列表
     *
     * @param ctx 调库校验上下文
     * @return 空的失败原因列表
     */
    private List<String> checkCompanyOut(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 前置校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：债券到期（delist_date）
     *
     * 债券摘牌日早于今日则禁止调入。对应老项目 checkInPool:544（ptype=4000 债券分支）
     *
     * 执行顺序：
     * 1. 读取债券摘牌日，有值且早于当天时返回到期调入提示
     *
     * @param ctx 调库校验上下文
     * @return 债券到期提示，未命中时为 null
     */
    private String inCheckBondMaturity(AdjustCheckContext ctx) {
        String delistDate = ctx.getSecurityInfo().getDelistDate();
        if (delistDate != null && !delistDate.isEmpty()) {
            String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
            if (delistDate.compareTo(today) < 0) {
                return "债券已到期";
            }
        }
        return null;
    }

    /**
     * 规则：股票退市（delist_date）
     *
     * 股票摘牌日早于今日则禁止调入。对应老项目 checkInPool:956（ptype=2000 股票分支）
     *
     * 执行顺序：
     * 1. 读取股票摘牌日，有值且早于当天时返回退市调入提示
     *
     * @param ctx 调库校验上下文
     * @return 股票退市提示，未命中时为 null
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
     * 规则：债券到期（delist_date，调出）
     *
     * 债券摘牌日早于今日则禁止调出。对应老项目 checkOutPool 到期校验
     *
     * 执行顺序：
     * 1. 将债券摘牌日与当天比较，已过摘牌日时返回调出校验提示
     *
     * @param ctx 调库校验上下文
     * @return 债券到期提示，未命中时为 null
     */
    private String outCheckBondMaturity(AdjustCheckContext ctx) {
        String delistDate = ctx.getSecurityInfo().getDelistDate();
        if (delistDate != null && !delistDate.isEmpty()) {
            String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
            if (delistDate.compareTo(today) < 0) {
                return "债券已到期";
            }
        }
        return null;
    }

    /**
     * 规则：股票退市（delist_date，调出）
     *
     * 股票摘牌日早于今日则禁止调出。对应老项目 checkOutPool 退市校验
     *
     * 执行顺序：
     * 1. 将股票摘牌日与当天比较，已过摘牌日时返回调出校验提示
     *
     * @param ctx 调库校验上下文
     * @return 股票退市提示，未命中时为 null
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
     * 需等待该流程终结后方可再次发起调库，避免并发操作导致入池状态混乱
     *
     * 执行顺序：
     * 1. 没有在途流程时返回 null
     * 2. 有在途流程时返回阻止原因，拿到节点名称时一并显示
     *
     * @param ctx 调库校验上下文
     * @return 进行中的流程提示，没有时为 null
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
     * 以 ip_pool_status 中 audit_status='20' 的有效记录为准，重复调入无实际意义
     *
     * 执行顺序：
     * 1. 从目标池取 ID，再查证券的当前在池 ID，已经在该池时返回重复调入提示
     *
     * @param ctx 调库校验上下文
     * @return 组合已在池的提示，未命中时为 null
     */
    private String inCheckSecurityAlreadyInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId != null && ctx.getCurrentPoolIds().contains(poolId)) {
            return "证券已在目标投资池中";
        }
        return null;
    }

    /**
     * 规则：目标池是否已达持仓上限
     *
     * maxCapacity 为 null 或 0 时表示不限制数量；
     * 当前有效证券数量（poolCurrentCount）≥ 上限时拒绝调入
     *
     * 执行顺序：
     * 1. 检查目标池是否配置正数容量上限
     * 2. 当前持仓数量已经达到上限时返回容量提示
     *
     * @param ctx 调库校验上下文
     * @return 达到容量上限的提示，未命中时为 null
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
     * 或本次请求同时调入至少一个来源池，满足"从低级库向高级库晋升"等分层调库规则
     * 未配置来源池时不做限制
     *
     * 执行顺序：
     * 1. 读取目标池的来源池配置，未配置时通过
     * 2. 以当前所在池和本批拟调入池为依据，至少命中一个来源池时允许调入
     *
     * @param ctx 调库校验上下文
     * @return 未满足来源池要求的提示，符合时为 null
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
     * 证券当前在配置的限制池中，则不允许调入目标池
     *
     * 执行顺序：
     * 1. 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * @param ctx 调库校验上下文
     * @return 调入限制提示，未命中时为 null
     */
    private String inCheckRestrictPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.IN_RESTRICT.getCode(), "调入限制池");
    }

    /**
     * 规则：调入弹性禁投池（in_soft_restrict）
     *
     * 弹性禁投池与硬性限制池的校验逻辑相同，但语义上表示"柔性"限制，
     * 业务层面可由特定角色绕过（绕过逻辑在审批流程中处理，此处只做初步拦截）
     *
     * 执行顺序：
     * 1. 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * @param ctx 调库校验上下文
     * @return 弹性限制提示，未命中时为 null
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
     * 不可同时勾选"调入信用债大库/一级库"和"调入专户产品/一级库"
     *
     * 执行顺序：
     * 1. 找出本次拟调入池中与目标池互斥的池
     * 2. 命中时用池名生成不能同时调入的提示
     *
     * @param ctx 调库校验上下文
     * @return 同时调入互斥池的提示，未命中时为 null
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
     * 不在池中则无法调出，属于基础前置判断，避免操作无效数据
     *
     * 执行顺序：
     * 1. 目标池缺失或证券的当前在池记录未包含该池时，返回不能调出的提示
     *
     * @param ctx 调库校验上下文
     * @return 组合不在池的提示，在池时为 null
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
     * 证券当前在配置的限制池中，则不允许从目标池调出
     *
     * 执行顺序：
     * 1. 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * @param ctx 调库校验上下文
     * @return 调出限制提示，未命中时为 null
     */
    private String outCheckRestrictPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.OUT_RESTRICT.getCode(), "调出限制池");
    }

    /**
     * 规则：调出互斥池（out_mutex）
     *
     * 证券当前在配置的互斥池中，则不允许从目标池调出
     * 注意：此处校验的是"调出方向的互斥"，与调入互斥池（in_mutex）含义不同：
     * in_mutex 用于在第三阶段自动生成配套调出项；out_mutex 用于阻止调出操作本身
     *
     * 执行顺序：
     * 1. 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * @param ctx 调库校验上下文
     * @return 调出互斥提示，未命中时为 null
     */
    private String outCheckMutexPool(AdjustCheckContext ctx) {
        // 通用阻断校验：检查证券是否在指定关系类型的池中
        return checkBlockedByPools(ctx, RelationType.OUT_MUTEX.getCode(), "调出互斥池");
    }

    /**
     * 规则：调出弹性禁投池（out_soft_restrict）
     *
     * 证券当前在配置的弹性禁投池中，则不允许从目标池调出
     *
     * 执行顺序：
     * 1. 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * @param ctx 调库校验上下文
     * @return 弹性限制提示，未命中时为 null
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
     * 因此也不应在同一批次中同时对两池执行调出操作
     *
     * 执行顺序：
     * 1. 检查本次拟调出池是否包括目标池配置的调入互斥池
     * 2. 存在冲突时列出不能同时调出的投资池
     *
     * @param ctx 调库校验上下文
     * @return 同时调出互斥池的提示，未命中时为 null
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
     * 池被锁定后不可调入，属于最硬的池级拦截，优先于其他调入校验执行
     *
     * 执行顺序：
     * 1. 取目标池的锁定标志，值为 1 时返回池已锁定的提示
     *
     * @param ctx 调库校验上下文
     * @return 池锁定提示，未锁定时为 null
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
     * 池被锁定后不可调出，属于最硬的池级拦截，优先于其他调出校验执行
     *
     * 执行顺序：
     * 1. 取目标池的锁定标志，值为 1 时返回池已锁定的提示
     *
     * @param ctx 调库校验上下文
     * @return 池锁定提示，未锁定时为 null
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
     * 目标池配置了调入冻结期天数时，证券入池后 N 天内不可调出
     * 以 ip_pool_status.entry_time（audit_status=20）为入池时间基准，
     * entry_time + frozenPeriodIn 天 &gt; 当前时间则视为仍在冻结期
     *
     * 执行顺序：
     * 1. 取证券在目标池的入池时间
     * 2. 计算冻结期截止时间 = 入池时间 + N 天
     *
     * @param ctx 调库校验上下文
     * @return 入池时间缺失或仍在冻结期的提示，符合时为 null
     */
    private String outCheckFrozenPeriod(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getFrozenPeriodIn() == null || pool.getFrozenPeriodIn() <= 0) {
            return null;
        }
        // 1. 取证券在目标池的入池时间
        Date entryTime = ctx.getTargetPoolEntryTime();
        if (entryTime == null) {
            return "证券入池生效时间缺失";
        }
        // 2. 计算冻结期截止时间 = 入池时间 + N 天
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
     * 目标池配置了投资品种时，证券品种（categoryType）须在配置内
     * categoryType 由 dict_security_type 表按 securityType 查询得到（大类见 CategoryType / 演示数据）
     *
     * 执行顺序：
     * 1. 查证券品种大类
     * 2. 池配置品种为 JSON 数组（如 ["bond"]），判断是否包含证券品种
     *
     * @param ctx 调库校验上下文
     * @return 品种不在允许范围的提示，符合时为 null
     */
    private String inCheckVariety(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getVarietyCodes() == null || pool.getVarietyCodes().isEmpty()) {
            return null;
        }
        // 1. 查证券品种大类
        String categoryType = forbiddenAbsPoolAdjustMapper.queryCategoryTypeBySecurityType(ctx.getSecurityInfo().getSecurityType());
        // 2. 池配置品种为 JSON 数组（如 ["bond"]），判断是否包含证券品种
        if (categoryType == null || !pool.getVarietyCodes().contains("\"" + categoryType + "\"")) {
            return "证券不在本池投资品种范围内";
        }
        return null;
    }

    /**
     * 规则：投资市场（market_codes）
     *
     * 目标池配置了投资市场时，当前证券代码所属市场须在配置内
     * 市场 code 统一见 {@link com.znty.rrs.common.enums.MarketCode}，
     * 市场由统一 SecurityMarketService 权威查询
     *
     * 执行顺序：
     * 1. 仅比较当前证券代码所属市场
     *
     * @param ctx 调库校验上下文
     * @return 市场不在允许范围的提示，符合时为 null
     */
    private String inCheckMarket(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getMarketCodes() == null || pool.getMarketCodes().isEmpty()) {
            return null;
        }
        // 仅比较当前证券代码所属市场
        if (!securityMarketService.matchesPoolMarkets(pool.getMarketCodes(), ctx.getSecurityInfo())) {
            return "证券不在本池投资市场范围内";
        }
        return null;
    }

    /**
     * 规则：全局禁止池（forbidden 禁投池）
     *
     * 证券当前在禁投池中则不能调入任何其他池（全局禁止，区别于池间 in_restrict）
     * 对应老项目 Forbiddenlastpoolid 配置
     *
     * 执行顺序：
     * 1. 查询证券调入禁投投资池
     *
     * @param ctx 调库校验上下文
     * @return 禁投提示，未命中时为 null
     */
    private String inCheckForbiddenPool(AdjustCheckContext ctx) {
        if (forbiddenAbsPoolAdjustMapper.querySecurityInForbiddenPool(ctx.getSecurityInfo().getWindCode())) {
            return "证券当前在禁止池中";
        }
        return null;
    }

    /**
     * 规则：股票入池评级限制（grade_astrict）
     *
     * 老系统该字段对应“股票入池评级限制”，评级来源为 StockResearch/investrank
     * （买入、增持、中性、卖出等），不是债券 ratingBond。当前项目尚未接入股票研究评级来源，
     * 因此仅保留股票分支入口，配置了 grade_astrict 时先跳过，不对债券评级做误拦截
     *
     * 执行顺序：
     * 1. 后续接入股票研究评级来源后，按 pool.gradeAstrict 校验买入/增持/中性/卖出等评级 code
     *
     * @param ctx 调库校验上下文
     * @return 当前固定返回 null
     */
    private String inCheckGradeAstrict(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getGradeAstrict() == null || pool.getGradeAstrict().trim().isEmpty()) {
            return null;
        }
        // 后续接入股票研究评级来源后，按 pool.gradeAstrict 校验买入/增持/中性/卖出等评级 code
        return null;
    }

    /**
     * 规则：行业限制（industry_code / industry_exponent）
     *
     * 目标池配置了行业限制时，调入校验证券行业是否匹配：
     * 池 industry_code 非空且 industry_exponent=0（非行业指数模式）时，证券 industry_name 须等于池配置值
     * 对应老项目 checkInPool:485（industryPartition.industrycode 比对 pool.IndustryCode），
     * TODO 投资池当前配置的是农业、城投、电力等业务行业值，尚未完成与证券行业数据的可靠映射；
     * 当前暂用 SecurityInfoBo.industryName 名称精确匹配，不作为最终行业校验口径（老项目为编码前缀层级匹配）
     * 仅调入校验（老代码 checkOutPool 无）
     *
     * 执行顺序：
     * 1. 行业指数模式（industry_exponent != 0）跳过行业校验
     *
     * @param ctx 调库校验上下文
     * @return 行业不一致的提示，符合或未检查时为 null
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
     * 规则：开放日（open_day_adjust，调入侧）
     *
     * 目标池启用开放日校验（open_day_adjust=1）时，调入校验当日是否落在 ip_pool_open_day 的开放区间内
     * 对应老项目 checkInPool:479（isInPoolOpenDay）
     *
     * 执行顺序：
     * 1. 只对启用开放日限制的池检查当天日期
     * 2. 当天未落在开放区间内时返回开放日限制提示
     *
     * @param ctx 调库校验上下文
     * @return 非开放日提示，符合时为 null
     */
    private String inCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        String today = LocalDate.now().toString();
        if (!forbiddenAbsPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
            return "当前不在本池开放日内";
        }
        return null;
    }

    /**
     * 规则：开放日（open_day_adjust，调出侧）
     *
     * 目标池启用开放日校验时，调出校验当日是否落在开放区间内。对应老项目 checkOutPool:139
     *
     * 执行顺序：
     * 1. 只对启用开放日限制的池检查当天日期
     * 2. 当天未落在开放区间内时返回开放日限制提示
     *
     * @param ctx 调库校验上下文
     * @return 非开放日提示，符合时为 null
     */
    private String outCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        String today = LocalDate.now().toString();
        if (!forbiddenAbsPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
            return "当前不在本池开放日内";
        }
        return null;
    }

    /**
     * 规则：报告必填（in_report_restriction / out_report_restriction）
     *
     * 目标池配置了报告限制时，提交时校验报告附件：
     * none=不限制 / any=任意一篇研究报告 / internal=必须是内部研究报告
     * 对应老项目 rschDocMode 报告校验。在 addAdjustLog 提交阶段校验（checkAdjust 阶段无报告信息）
     *
     * 执行顺序：
     * 1. 6个月内入池报告标记（对齐老系统 bondfileflag）：同主体半年内有审批通过调入且确有报告才跳过
     * 2. internal 要求从内部报告库选择，手工上传或外部报告不能替代
     * 3. 校验报告库附件真实存在、未删除且来源分类匹配
     *
     * @param item 调库项
     * @param pool 投资池
     * @param reportRestriction 目标池在当前调整方向的报告要求
     * @param securityCode 证券代码
     */
    private void checkReportRequired(ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool, String reportRestriction, String securityCode) {
        if (pool == null || reportRestriction == null || reportRestriction.isEmpty() || "none".equals(reportRestriction)) {
            return;
        }
        // 1. 6个月内入池报告标记（对齐老系统 bondfileflag）：同主体半年内有审批通过调入且确有报告才跳过
        if (securityCode != null && !securityCode.isEmpty() && forbiddenAbsPoolAdjustMapper.queryHasRecentInboundWithReport(securityCode)) {
            return;
        }
        boolean hasReport = (item.getCreditReportFileIndexes() != null && !item.getCreditReportFileIndexes().isEmpty())
                || (item.getCreditReportSourceAttachmentIds() != null && !item.getCreditReportSourceAttachmentIds().isEmpty());
        if (!hasReport) {
            throw new BizException("目标池[" + pool.getPoolName() + "]要求研究报告，请上传或选择报告");
        }
        // 2. internal 要求从内部报告库选择，手工上传或外部报告不能替代
        if ("internal".equals(reportRestriction)
                && (item.getCreditReportSourceAttachmentIds() == null || item.getCreditReportSourceAttachmentIds().isEmpty())) {
            throw new BizException("目标池[" + pool.getPoolName() + "]要求内部研究报告，请从内部报告库选择");
        }
        // 3. 校验报告库附件真实存在、未删除且来源分类匹配
        if (item.getCreditReportSourceAttachmentIds() != null && !item.getCreditReportSourceAttachmentIds().isEmpty()) {
            sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(),
                    "internal".equals(reportRestriction));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 私有工具方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建单个调库项的校验上下文（{@link AdjustCheckContext}）
     *
     * 负责将"本次校验共享数据"与"当次调库项的目标池信息"合并填充到上下文中，
     * 供三层校验方法直接读取，无需再逐级传参
     *
     * 执行顺序：
     * 1. 从全量投资池 Map 中取出目标池及其父级池（用于构建错误消息中的池路径）
     * 2. 取出目标池自身的所有关系配置（来源池、限制池、联动池等），供关系型校验规则直接使用
     * 3. 调出时查询证券在目标池的入池时间，用于冻结期校验（调入时不需要）
     * 4. 查证券品种大类，用于类型特有校验路由（dict_security_type.category_type）
     * 5. 基金评分（基金证券调入校验用，透传请求级 fundRate）
     * 6. 是否放开规则（透传 shared，主体债入库矩阵校验用）
     *
     * @param item 当前调库项（目标池 ID、调整方向等）
     * @param poolCurrentCount 目标池当前有效证券数量（调用方按需查询）
     * @param shared 本次 checkAdjust 调用的共享数据
     * @return 当前调整项的规则检查上下文
     */
    private AdjustCheckContext buildCheckContext(ForbiddenAbsPoolAdjustCheckReq.CheckItem item, int poolCurrentCount, AdjustSharedData shared) {
        // 1. 从全量投资池 Map 中取出目标池及其父级池（用于构建错误消息中的池路径）
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
        // 2. 取出目标池自身的所有关系配置（来源池、限制池、联动池等），供关系型校验规则直接使用
        ctx.setTargetPoolRelations(shared.getPoolRelationMap().getOrDefault(item.getTargetPoolId(), Collections.emptyMap()));
        ctx.setPoolMap(shared.getPoolMap());
        ctx.setHasPendingProcess(shared.getPendingPoolGroupIds() != null
                // 沿父级关系查找目标池所属的顶级投资池
                && shared.getPendingPoolGroupIds().contains(resolveRootPoolId(item.getTargetPoolId(), shared.getPoolMap())));
        ctx.setPendingProcessNodeLabel(shared.getPendingProcessNodeLabel());
        ctx.setSecurityInObservePool(shared.isSecurityInObservePool());
        ctx.setIssuerInObservePool(shared.isIssuerInObservePool());
        ctx.setSecurityInRestrictedPool(shared.isSecurityInRestrictedPool());
        ctx.setIssuerInRestrictedPool(shared.isIssuerInRestrictedPool());
        ctx.setRequestInPoolIds(shared.getRequestInPoolIds());
        ctx.setRequestOutPoolIds(shared.getRequestOutPoolIds());
        // 3. 调出时查询证券在目标池的入池时间，用于冻结期校验（调入时不需要）
        if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
            ctx.setTargetPoolEntryTime(forbiddenAbsPoolAdjustMapper.queryPoolEntryTime(
                    shared.getSecurityInfo().getWindCode(), item.getTargetPoolId()));
        }
        // 4. 查证券品种大类，用于类型特有校验路由（dict_security_type.category_type）
        ctx.setCategoryType(forbiddenAbsPoolAdjustMapper.queryCategoryTypeBySecurityType(
                shared.getSecurityInfo().getSecurityType()));
        // 5. 基金评分（基金证券调入校验用，透传请求级 fundRate）
        ctx.setFundRate(shared.getFundRate());
        // 6. 是否放开规则（透传 shared，主体债入库矩阵校验用）
        ctx.setReleaseRules(shared.isReleaseRules());
        return ctx;
    }

    /**
     * 生成联动或互斥配套项的校验结果
     *
     * 执行顺序：
     * 1. 读取关系配置指定的目标池及持仓数量，构建该项的校验上下文
     * 2. 按调入或调出方向校验，回填池路径、来源标签和失败原因
     *
     * @param targetPoolId 自动生成项的目标池 ID
     * @param adjustMode 调整方向（AdjustMode.IN.getCode()或AdjustMode.OUT.getCode()）
     * @param itemTag 来源标签：linkage（联动）/ mutex（互斥）
     * @param adjustGroupKey 触发该自动项的手工调库分组 Key
     * @param shared 本次 checkAdjust 调用的共享数据
     * @return 联动或互斥调整的检查结果
     */
    private AdjustCheckDto.CheckResultItem buildAutoResultItem(
            Long targetPoolId, String adjustMode, String itemTag, String adjustGroupKey, AdjustSharedData shared) {

        int poolCurrentCount = forbiddenAbsPoolAdjustMapper.queryPoolCurrentCount(targetPoolId);
        InvestmentPoolBo pool = shared.getPoolMap().get(targetPoolId);

        // 1. 构造虚拟 CheckItem，使其可以复用 buildCheckContext 逻辑
        ForbiddenAbsPoolAdjustCheckReq.CheckItem fakeItem = new ForbiddenAbsPoolAdjustCheckReq.CheckItem();
        fakeItem.setTargetPoolId(targetPoolId);
        fakeItem.setAdjustMode(adjustMode);
        fakeItem.setPoolType(pool != null ? pool.getPoolType() : null);

        // 2. 构建调库校验上下文
        AdjustCheckContext ctx = buildCheckContext(fakeItem, poolCurrentCount, shared);

        List<String> failures = AdjustMode.IN.getCode().equals(adjustMode) ? checkInConditions(ctx) : checkOutConditions(ctx);

        AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
        resultItem.setTargetPoolId(targetPoolId);
        // 3. 构建投资池全路径名称
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
     * 手工调库项失败时，将失败状态传递给同组自动生成项
     *
     * 执行顺序：
     * 1. 手工项可调整时无需修改同组自动项
     * 2. 手工项受阻时保留自动项原有失败原因，追加来源项未通过的说明并标为不可调整
     *
     * @param autoItem 待处理的自动调整结果
     * @param manualItem 本组的手工调整项
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
     * 互斥配套调出失败时，反阻断同组手工调入，避免只入目标池导致双池
     *
     * 仅处理 itemTag=mutex 的配套调出；联动失败不反绑手工项
     *
     * 执行顺序：
     * 1. 互斥子原因拆成独立 failReasons，避免「；」嵌套成一句矛盾长文
     *
     * @param manualItem 本组的手工调整项
     * @param groupMutexItems 本组配套互斥调整结果
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
     * 组装提交阶段配套互斥调出失败的异常文案（总述 + 编号原因）
     *
     * 执行顺序：
     * 1. 先写配套互斥调出失败的池名称
     * 2. 有具体失败原因时按原顺序编号追加到异常文案
     *
     * @param mutexPoolName 配套调出的互斥池全名
     * @param outFailures 配套互斥调出的失败原因
     * @return 包含互斥池及失败明细的提示
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
     * 构建手工调库项分组 Key
     *
     * 执行顺序：
     * 1. 获取目标投资池 ID
     *
     * @param item 当前调库项（目标池 ID、调整方向等）
     * @return 手工项和配套自动项共用的分组键
     */
    private String buildAdjustGroupKey(ForbiddenAbsPoolAdjustCheckReq.CheckItem item) {
        return "manual_" + item.getTargetPoolId() + "_" + item.getAdjustMode();
    }

    /**
     * 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * 执行顺序：
     * 1. 取出"证券当前在其中"的阻断池
     *
     * @param ctx 调库校验上下文
     * @param relationType 关系类型常量（RelationType.IN_RESTRICT.getCode() / RelationType.OUT_RESTRICT.getCode() 等）
     * @param label 用于错误消息的中文标签（如"调入限制池"）
     * @return 命中的限制池提示，未命中时为 null
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
     * 结构：poolId → relationType → 关联池 ID 列表，三层嵌套以支持快速按（池ID + 关系类型）查询
     *
     * 执行顺序：
     * 1. 按主池 ID 和关系类型两级建索引，逐条加入对应的关联池 ID
     *
     * @param relations 从数据库查出的原始关系记录列表
     * @return 按池和关系类型查询关联池的索引
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
     * 执行顺序：
     * 1. 按传入顺序取得每个池的路径名称，再用顿号连接供错误提示使用
     *
     * @param poolIds 需要转换的池 ID 列表
     * @param ctx 上下文（内含全量投资池 Map）
     * @return 拼接后的池名，未传池时为空字符串
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
     * 执行顺序：
     * 1. 收到非 null 的失败原因时追加到原列表，空值直接跳过
     *
     * @param failures 失败原因集合
     * @param reason 单条校验返回的失败原因，null 表示通过
     */
    private void addIfFailed(List<String> failures, String reason) {
        if (reason != null) {
            failures.add(reason);
        }
    }

    /**
     * 单条校验返回的警告原因非空时加入警告列表（不阻断调库，如弹性禁投池）
     *
     * 执行顺序：
     * 1. 收到非 null 的警告原因时追加到原列表，空值直接跳过
     *
     * @param warnings 用于收集非阻断提示的列表
     * @param reason 单条校验返回的失败原因，null 表示通过
     */
    private void addIfWarning(List<String> warnings, String reason) {
        if (reason != null) {
            warnings.add(reason);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  对象转换方法
    // ═══════════════════════════════════════════════════════════

    /**
     * InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
     *
     * 执行顺序：
     * 1. 复制投资池树节点、池类型、容量和现有持仓数量
     * 2. 附上调入互斥和调出互斥池 ID，未配置时对应列表留空
     *
     * @param pool 投资池实体
     * @param inMutexMap 调入互斥关系（poolId → 互斥池 ID 列表）
     * @param outMutexMap 调出互斥关系（poolId → 互斥池 ID 列表）
     * @param currentCountMap 投资池当前有效证券数量映射
     * @return 供页面选择的 CRMW 池数据
     */
    private PoolDto toPoolDto(InvestmentPoolBo pool,
                              Map<Long, List<Long>> inMutexMap,
                              Map<Long, List<Long>> outMutexMap,
                              Map<Long, Integer> currentCountMap) {
        PoolDto dto = new PoolDto();
        dto.setId(pool.getId());
        dto.setParentId(pool.getParentId());
        dto.setPoolName(pool.getPoolName());
        dto.setPoolCode(pool.getPoolCode());
        dto.setPoolType(pool.getPoolType());
        dto.setPoolLevel(pool.getPoolLevel());
        dto.setMaxCapacity(pool.getMaxCapacity());
        dto.setCurrentCount(currentCountMap.getOrDefault(pool.getId(), 0));
        dto.setInMutexPoolIds(inMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
        dto.setOutMutexPoolIds(outMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
        return dto;
    }

    // ═══════════════════════════════════════════════════════════
    //  流程步骤记录（ip_adjust_step）
    // ═══════════════════════════════════════════════════════════

    /**
     * 为新建的调库记录创建初始流程步骤（懒创建）
     *
     * 仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理），
     * 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
     *
     * 执行顺序：
     * 1. 查找开始节点
     * 2. 创建开始节点步骤（auto_process）
     * 3. 插入单条步骤记录到 ip_adjust_step
     * 4. 查找初始步骤的下一个节点
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param snapshot 流程快照
     * @param adjusterId 提交人 ID
     * @param adjusterName 提交人名称
     * @return true=初始步骤已走到结束节点，false=仍需后续人工处理
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
                // 插入单条步骤记录到 ip_adjust_step
                insertStepRecord(adjustLogId, adjustBatchNo, currentNode, config, sortOrder, ProcessAction.AUTO_PROCESS.getCode(),
                                 null, null, ProcessAction.AUTO_PROCESS.getCode(), "系统自动审批通过", now);
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

            // 自动节点（node_type=auto）写入 auto_process 步骤后继续向下流转
            if (NodeType.AUTO.getCode().equals(currentNode.getNodeType())) {
                sortOrder = currentNode.getSortOrder() != null ? currentNode.getSortOrder() : 1;
                // 插入单条步骤记录到 ip_adjust_step
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
     * 判断当前审批节点是否应由流程发起人自动完成
     *
     * 执行顺序：
     * 1. 发起节点通过 approval_strategy=initiator + 出边 route_action=submit 标记
     *
     * @param snapshot 流程快照
     * @param node 待判断的流程节点
     * @param config 节点审批配置
     * @param prevNode 前一个节点（用于排除回退边），可为 null
     * @param startNode 流程开始节点
     * @return true=当前节点符合由发起人提交的条件，false=当前节点不符合由发起人提交的条件
     */
    private boolean isInitiatorStep(FlowSnapshot snapshot, FlowNodeBo node, NodeApprovalConfigBo config,
                                    FlowNodeBo prevNode, FlowNodeBo startNode) {
        if (node == null || !NodeType.APPROVAL.getCode().equals(node.getNodeType())) {
            return false;
        }
        // 发起节点通过 approval_strategy=initiator + 出边 route_action=submit 标记
        return config != null
                && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())
                // 判断节点是否存在指定流转动作出边
                && hasOutgoingRouteAction(snapshot, node, ProcessAction.SUBMIT.getCode());
    }

    /**
     * 判断节点是否存在指定流转动作出边
     *
     * 执行顺序：
     * 1. 快照、节点或动作缺失时直接判为不匹配
     * 2. 逐条核对连线起点和动作，任一同时匹配时通过
     *
     * @param snapshot 流程快照
     * @param node 待判断的流程节点
     * @param routeAction 当前处理动作对应的路由动作
     * @return true=节点存在指定动作的出线，false=节点没有指定动作的出线
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
     *
     * 执行顺序：
     * 1. 将审批处理人配置解析为具体人员
     * 2. 只排除本批次真正审核过的人，待办和抢占跳过的人仍可进入下一节点
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param node 待判断的流程节点
     * @param snapshot 流程快照
     * @param now 本次操作时间
     */
    private void createPendingSteps(Long adjustLogId, String adjustBatchNo, FlowNodeBo node,
                                    FlowSnapshot snapshot, Date now) {
        NodeApprovalConfigBo config = snapshot.approvalConfigMap.get(node.getId());
        // 1. 将审批处理人配置解析为具体人员
        List<HandlerTarget> handlers = resolveApprovalHandlers(config, snapshot);
        int sortOrder = node.getSortOrder() != null ? node.getSortOrder() : 1;

        if (handlers.isEmpty()) {
            // 无配置处理人时仍创建一条空处理人的待处理记录
            insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                             null, null, null, null, now);
            return;
        }
        // 2. 只排除本批次真正审核过的人，待办和抢占跳过的人仍可进入下一节点
        handlers = excludeParticipatedHandlers(adjustLogId, adjustBatchNo, handlers);
        for (HandlerTarget handler : handlers) {
            // 插入单条步骤记录到 ip_adjust_step
            insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                             handler.handlerId, handler.handlerName, null, null, now);
        }
    }

    /**
     * 排除本批次真正审核过的人；配置非空但剔光时抛业务异常
     *
     * 执行顺序：
     * 1. 按批次列表查询调库审批步骤
     * 2. 排除本批次真正审核过的人
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param handlers 下一节点配置的候选处理人
     * @return 排除已参与人员后的可用处理人
     */
    private List<HandlerTarget> excludeParticipatedHandlers(Long adjustLogId, String adjustBatchNo,
                                                            List<HandlerTarget> handlers) {
        List<IpAdjustStepBo> existingSteps = forbiddenAbsPoolAdjustMapper.queryAdjustStepByBatchList(
                adjustLogId, adjustBatchNo);
        Set<String> participated = AdjustStepHandlerExcludeUtil.collectParticipatedHandlerIds(existingSteps);
        List<HandlerTarget> filtered = AdjustStepHandlerExcludeUtil.excludeParticipated(
                handlers, participated, h -> h.handlerId);
        if (filtered.isEmpty()) {
            throw new BizException(AdjustStepHandlerExcludeUtil.NO_AVAILABLE_HANDLER_MSG);
        }
        return filtered;
    }

    /**
     * 插入单条步骤记录到 ip_adjust_step
     *
     * 执行顺序：
     * 1. 新增调库审批步骤
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param node 待判断的流程节点
     * @param config 节点审批配置
     * @param sortOrder 审批步骤排序值
     * @param stepStatus 已写入的步骤状态
     * @param handlerId 新步骤处理人 ID
     * @param handlerName 新步骤处理人名称
     * @param processAction 当前处理动作对应的路由动作
     * @param processComment 本步骤的处理意见
     * @param startTime 增量开始时间；全量模式仅用于参数统一
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
        forbiddenAbsPoolAdjustMapper.addAdjustStep(step);
    }

    /**
     * 按节点类型查找节点
     *
     * 执行顺序：
     * 1. 按快照中的节点顺序比较节点类型，命中时返回该节点，未命中时返回 null
     *
     * @param snapshot 流程快照
     * @param nodeType 需要查找的节点类型
     * @return 找到的节点，不存在时为 null
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
     * 从当前节点出发，遍历所有出边（连线），排除指向已访问节点的边，
     * 优先选择非 reject 的主路径连线，
     * 返回目标节点
     *
     * 执行顺序：
     * 1. 收集当前节点所有的出边
     * 2. 排除指向已访问节点的边（防止走上驳回回路）
     * 3. 优先选择非 reject 的主路径出边
     * 4. 兜底：返回第一条出边的目标节点
     *
     * @param snapshot 流程快照
     * @param currentNode 当前节点
     * @param prevNode 前一个节点（用于排除回退边），可为 null
     * @return 下一节点，无出边时返回 null
     */
    private FlowNodeBo findNextNodeOnMainPath(FlowSnapshot snapshot, FlowNodeBo currentNode, FlowNodeBo prevNode) {
        // 1. 收集当前节点所有的出边
        List<FlowEdgeBo> outEdges = new ArrayList<>();
        for (FlowEdgeBo edge : snapshot.edges) {
            if (edge.getFromNodeId().equals(currentNode.getId())) {
                outEdges.add(edge);
            }
        }

        if (outEdges.isEmpty()) {
            return null;
        }

        // 2. 排除指向已访问节点的边（防止走上驳回回路）
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

        // 3. 优先选择非 reject 的主路径出边
        for (FlowEdgeBo edge : outEdges) {
            if (!ProcessAction.REJECT.getCode().equals(edge.getRouteAction())) {
                return snapshot.nodeMap.get(edge.getToNodeId());
            }
        }

        // 4. 兜底：返回第一条出边的目标节点
        FlowEdgeBo firstEdge = outEdges.get(0);
        return snapshot.nodeMap.get(firstEdge.getToNodeId());
    }

    /**
     * 查找初始步骤的下一个节点
     *
     * 执行顺序：
     * 1. 沿流程主路径查找下一节点
     *
     * @param snapshot 流程快照
     * @param currentNode 当前节点
     * @param prevNode 前一个节点（用于排除回退边），可为 null
     * @return 下一流程节点
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
     * 将审批处理人配置解析为具体人员，角色会递归展开到子角色下的人员并去重
     *
     * 执行顺序：
     * 1. 逐项递归收集角色及其子角色 ID
     * 2. 查询角色字典
     *
     * @param config 节点审批配置
     * @param snapshot 流程快照
     * @return 去重后的候选处理人，未配置审批或处理人时返回空列表
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
     * 递归收集角色及其子角色 ID
     *
     * 执行顺序：
     * 1. 先将当前角色 ID 加入结果
     * 2. 遍历所有角色，对直接子角色继续递归收集
     *
     * @param roleId 当前角色 ID
     * @param roleIds 收集到的角色 ID 列表
     * @param allRoles 全部角色及其父子关系
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
     * 执行顺序：
     * 1. 关联码项使用 item 级证券代码，保证独立落 log / 池状态
     * 2. 兜底：关联项未带简称/类型时按代码回查主数据
     * 3. 根据调库项来源确定落表调整类型
     * 4. 同一次提交共用统一提交时间，避免大批量逐条 NOW() 导致历史排序同组打散
     *
     * @param req 调库申请（含证券级别信息）
     * @param item 单个调库项（含目标池、调整方向等）
     * @param flowSource 同组手工调库项，用于写入流程快照
     * @param shared 本次提交的共享数据
     * @return 待保存的 CRMW 调库日志
     */
    private IpAdjustLogBo buildAdjustLog(ForbiddenAbsPoolAdjustSubmitReq req,
                                         ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item,
                                         ForbiddenAbsPoolAdjustSubmitReq.AdjustItem flowSource,
                                         SubmitSharedData shared) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        // 1. 关联码项使用 item 级证券代码，保证独立落 log / 池状态
        String securityCode = resolveItemSecurityCode(req, item);
        String securityShortName = item.getSecurityShortName() != null && !item.getSecurityShortName().isEmpty()
                ? item.getSecurityShortName() : req.getSecurityShortName();
        String securityType = item.getSecurityType() != null && !item.getSecurityType().isEmpty()
                ? item.getSecurityType() : req.getSecurityType();
        // 2. 兜底：关联项未带简称/类型时按代码回查主数据
        if ((securityShortName == null || securityShortName.isEmpty()
                || securityType == null || securityType.isEmpty())
                && securityCode != null && !securityCode.isEmpty()) {
            SecurityInfoBo securityInfo = forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(securityCode);
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
        // 3. 根据调库项来源确定落表调整类型
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
        // 4. 同一次提交共用统一提交时间，避免大批量逐条 NOW() 导致历史排序同组打散
        if (shared != null && shared.batchNoContext != null) {
            bo.setSubmitTime(shared.batchNoContext.submitTime);
        }
        return bo;
    }

    /**
     * 将调库项携带的提交附件绑定到新建调库日志
     *
     * 执行顺序：
     * 1. 绑定信评报告附件
     * 2. 绑定其他材料附件
     * 3. 复制报告库附件为信评报告附件
     * 4. 复制报告库附件为其他材料附件
     *
     * @param adjustLogId 调库记录 ID
     * @param item 调库项
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @param uploaderId 上传人 ID
     */
    private void bindSubmitAttachments(Long adjustLogId, ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item,
                                       SysAttachmentService.SubmissionFiles submissionFiles, String uploaderId) {
        // 1. 绑定信评报告附件
        sysAttachmentService.bindAttachments(adjustLogId, item.getCreditReportFileIndexes(),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), submissionFiles);
        // 2. 绑定其他材料附件
        sysAttachmentService.bindAttachments(adjustLogId, item.getMaterialFileIndexes(),
                AttachmentCategory.MATERIAL_HAND.getCode(), submissionFiles);
        // 3. 复制报告库附件为信评报告附件
        sysAttachmentService.copyReportAttachments(adjustLogId, item.getCreditReportSourceAttachmentIds(),
                AttachmentPurpose.CREDIT_REPORT.getCode(), uploaderId);
        // 4. 复制报告库附件为其他材料附件
        sysAttachmentService.copyReportAttachments(adjustLogId, item.getMaterialSourceAttachmentIds(),
                AttachmentPurpose.MATERIAL.getCode(), uploaderId);
    }

    /**
     * 根据调库项来源确定落表调整类型
     *
     * 执行顺序：
     * 1. 互斥、联动和同券关联项分别写入对应的调整类型，其他项沿用请求类型
     *
     * @param req 调库提交请求
     * @param item 调库项
     * @return 写入日志的调整来源
     */
    private String resolveAdjustType(ForbiddenAbsPoolAdjustSubmitReq req, ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item) {
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

    /**
     * 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
     *
     * 执行顺序：
     * 1. 明细有证券代码时使用明细值，否则读取请求中的主券代码
     *
     * @param req 调库提交请求
     * @param item 调库项
     * @return 当前调整项的标的证券代码
     */
    private String resolveItemSecurityCode(ForbiddenAbsPoolAdjustSubmitReq req,
                                           ForbiddenAbsPoolAdjustSubmitReq.AdjustItem item) {
        if (item != null && item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()) {
            return item.getSecurityCode();
        }
        return req.getSecurityCode();
    }

    /**
     * 从 Wind 同 ISIN 记录解析关联证券代码列表（已带后缀、去自身、去重，不递归）
     *
     * 执行顺序：
     * 1. 按共用市场集合读取同券代码
     * 2. 从 Wind 同 ISIN 记录解析关联证券代码列表（已带后缀、去自身、去重，不递归）
     *
     * @param main 证券主档信息
     * @return 本次涉及的同券跨市场代码列表
     */
    private List<String> resolveRelatedSecurityCodes(SecurityInfoBo main) {
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
     * 按关联证券代码加载校验共享数据（复用主请求的池关系/请求池集合）
     *
     * 执行顺序：
     * 1. 观察池：证券或主体已在观察池则不得高于矩阵最好档；重点观察名单：禁新增，已在库只能去五级
     * 2. 评级下调标志按关联券自身主体信息判定
     * 3. 同券各市场沿用本次申请的矩阵放开选择，其他规则仍逐券校验
     *
     * @param securityCode 证券代码
     * @param securityInfo 标的证券资料
     * @param mainShared 主标的的校验共用数据
     * @param mainReq 禁投ABS投资池调库校验
     * @return 关联标的的校验共用数据
     */
    private AdjustSharedData loadSharedDataForSecurity(String securityCode, SecurityInfoBo securityInfo,
                                                       AdjustSharedData mainShared, ForbiddenAbsPoolAdjustCheckReq mainReq) {
        AdjustSharedData shared = new AdjustSharedData();
        shared.setSecurityInfo(securityInfo);
        shared.setPoolMap(mainShared.getPoolMap());
        shared.setCurrentPoolIds(new HashSet<>(
                forbiddenAbsPoolAdjustMapper.querySecurityCurrentPoolIdList(securityCode)));
        shared.setPoolRelationMap(mainShared.getPoolRelationMap());
        shared.setHasPendingProcess(forbiddenAbsPoolAdjustMapper.querySecurityHasPendingProcess(securityCode));
        shared.setPendingProcessNodeLabel(
                forbiddenAbsPoolAdjustMapper.querySecurityPendingProcessNodeLabel(securityCode));
        // 将目标池列表转换为顶级投资池组 ID 集合
        shared.setPendingPoolGroupIds(resolvePoolGroupIds(
                forbiddenAbsPoolAdjustMapper.queryPendingManualTargetPoolIdList(securityCode, null),
                mainShared.getPoolMap()));
        // 1. 观察池：证券或主体已在观察池则不得高于矩阵最好档；重点观察名单：禁新增，已在库只能去五级
        shared.setSecurityInObservePool(forbiddenAbsPoolAdjustMapper.querySecurityInObservePool(securityCode));
        shared.setIssuerInObservePool(forbiddenAbsPoolAdjustMapper.queryIssuerInObservePool(securityCode));
        shared.setSecurityInRestrictedPool(forbiddenAbsPoolAdjustMapper.querySecurityInRestrictedPool(securityCode));
        shared.setIssuerInRestrictedPool(forbiddenAbsPoolAdjustMapper.queryIssuerInRestrictedPool(securityCode));
        // 2. 评级下调标志按关联券自身主体信息判定
        shared.setIssuerRatingDowngraded(ratingDowngradeChecker.isIssuerDowngraded(securityInfo));
        shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(securityInfo));
        shared.setGuarantorRatingDowngraded(
                ratingDowngradeChecker.isGuarantorDowngraded(mainReq.getGuarantorCode()));
        shared.setRequestInPoolIds(mainShared.getRequestInPoolIds());
        shared.setRequestOutPoolIds(mainShared.getRequestOutPoolIds());
        shared.setFundRate(mainReq.getFundRate());
        // 3. 同券各市场沿用本次申请的矩阵放开选择，其他规则仍逐券校验
        shared.setReleaseRules(mainShared.isReleaseRules());
        return shared;
    }

    /**
     * 多市场关联码扩批：对主结果中的 manual/linkage/mutex 项，按 Wind 同 ISIN 记录展开 related 项
     *
     * 关联码独立校验已在池/不在池/pending/容量等；同池同批调入累计 projected 占用容量
     *
     * 执行顺序：
     * 1. 回填主券证券信息到已有结果项
     * 2. 仅对主券母项扩批（manual/linkage/mutex）
     * 3. 已通过的调入项占用 projected 容量，避免主+关联同池全部通过却超容
     * 4. 任一市场失败反向阻断整个调库组，避免只提交部分市场
     *
     * @param req 调库校验请求
     * @param mainShared 主标的的校验共用数据
     * @param resultItems 前四阶段的校验结果
     */
    private void expandRelatedMarketCodeItems(ForbiddenAbsPoolAdjustCheckReq req, AdjustSharedData mainShared,
                                              List<AdjustCheckDto.CheckResultItem> resultItems) {
        SecurityInfoBo mainSec = mainShared.getSecurityInfo();
        if (mainSec == null || resultItems == null || resultItems.isEmpty()) {
            return;
        }
        // 1. 回填主券证券信息到已有结果项
        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            if (item.getSecurityCode() == null || item.getSecurityCode().isEmpty()) {
                item.setSecurityCode(mainSec.getWindCode());
                item.setSecurityShortName(mainSec.getShortName());
                item.setSecurityType(mainSec.getSecurityType());
                item.setSourceSecurityCode(mainSec.getWindCode());
            }
        }

        // 从 Wind 同 ISIN 记录解析关联证券代码列表（已带后缀、去自身、去重，不递归）
        List<String> relatedCodes = resolveRelatedSecurityCodes(mainSec);
        if (relatedCodes.isEmpty()) {
            return;
        }

        // 2. 仅对主券母项扩批（manual/linkage/mutex）
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

        // 3. 已通过的调入项占用 projected 容量，避免主+关联同池全部通过却超容
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
            SecurityInfoBo relatedSec = forbiddenAbsPoolAdjustMapper.querySecurityBoByCode(relatedCode);
            AdjustSharedData relatedShared = relatedSec == null ? null
                    // 按关联证券代码加载校验共享数据（复用主请求的池关系/请求池集合）
                    : loadSharedDataForSecurity(relatedCode, relatedSec, mainShared, req);

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
                    int baseCount = forbiddenAbsPoolAdjustMapper.queryPoolCurrentCount(mother.getTargetPoolId());
                    // 调入时把本批已通过项计入有效持仓，再跑容量校验
                    int effectiveCount = AdjustMode.IN.getCode().equals(mother.getAdjustMode())
                            ? baseCount + projectedInbound.getOrDefault(mother.getTargetPoolId(), 0)
                            : baseCount;

                    ForbiddenAbsPoolAdjustCheckReq.CheckItem fakeItem = new ForbiddenAbsPoolAdjustCheckReq.CheckItem();
                    fakeItem.setTargetPoolId(mother.getTargetPoolId());
                    fakeItem.setAdjustMode(mother.getAdjustMode());
                    fakeItem.setPoolType(mother.getPoolType());
                    // 构建关联券校验上下文
                    AdjustCheckContext ctx = buildCheckContext(fakeItem, effectiveCount, relatedShared);
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
        // 4. 任一市场失败反向阻断整个调库组，避免只提交部分市场
        propagateRelatedMarketFailure(resultItems);
    }

    /**
     * 汇总跨市场失败原因后统一阻断同组，保留原始失败项的具体原因
     *
     * 执行顺序：
     * 1. 按批次分组汇总跨市场失败项的原因
     * 2. 同组仍可调整的项统一标为失败，已失败的项保留原原因
     *
     * @param resultItems 前四阶段的校验结果
     */
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

    /**
     * 构建投资池全路径名称
     *
     * 执行顺序：
     * 1. 找不到当前池时返回空路径，根池直接使用自身名称
     * 2. 有父池时递归查找上级路径，再用斜线拼接当前名称
     *
     * @param poolId 目标投资池 ID
     * @param poolMap 全量池索引
     * @return 投资池全路径名称
     */
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
     * 单个流程的运行时快照，聚合流程定义、活跃版本、节点索引和连线列表
     *
     * 用于 {@link #isDirectFlow(FlowSnapshot)} 方法快速判断流程是否为直通（start→end）模式，
     * 避免在第三/四阶段逐项重复查询流程定义、版本、节点和连线
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

        /**
         * 保存 ABS 调库发起审批所需的流程快照
         *
         * 执行顺序：
         * 1. 保存流程定义、启用版本、节点、连线及审批配置索引
         *
         * @param definition 流程定义
         * @param activeVersion 当前活跃版本
         * @param nodeMap 节点索引（DB ID → FlowNodeBo），用于连线遍历时快速定位目标节点
         * @param edges 该版本的全量连线列表
         * @param approvalConfigMap 审批配置索引（nodeId → NodeApprovalConfigBo），用于创建步骤时获取审批策略和处理人
         * @param approvalHandlerMap 审批处理人明细索引（approvalConfigId → handler 列表）
         */
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
     * 待处理步骤的具体处理人
     */
    private static class HandlerTarget {
        /** 审批处理人的用户 ID */
        final String handlerId;

        /** 审批处理人的显示名称 */
        final String handlerName;

        /**
         * 记录 ABS 调库候选审批人
         *
         * 执行顺序：
         * 1. 保存处理人 ID 和姓名
         *
         * @param handlerId 处理人 ID
         * @param handlerName 处理人姓名
         */
        HandlerTarget(String handlerId, String handlerName) {
            this.handlerId = handlerId;
            this.handlerName = handlerName;
        }
    }

    /**
     * 本次调库提交（addAdjustLog）的共享数据载体
     *
     * addAdjustLog 每次调用需要加载多项证券/池/流程的基础数据，这些数据在整个调用过程中保持不变
     * 将其封装为此类后，第三、四阶段的处理方法只需接收一个 shared 参数即可获取所需数据，
     * 避免逐级传递大量参数
     *
     * 与 {@link AdjustSharedData} 的区别：
     * 不包含 requestInPoolIds / requestOutPoolIds（提交阶段无需互斥冲突校验）
     * 新增 flowSnapshotMap，包含每个引用流程的快照数据
     */
    private static class SubmitSharedData {

        /** 本次实际调库日志 */
        final Map<Long, IpAdjustLogBo> submittedLogs = new HashMap<>();

        /** 后端核验的权益人选择 */
        SecurityPoolAdjustService.SelectedRatingCompanyData selectedRatingCompany;

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

        /** 发行主体是否在观察池（主体级记录：ip_pool_status.security_code = issuer_code） */
        final boolean issuerInObservePool;

        /** 流程快照索引（flowId → FlowSnapshot），供第三/四阶段快速判断直通流程 */
        final Map<Long, FlowSnapshot> flowSnapshotMap;

        /** 批次号生成上下文 */
        final BatchNoContext batchNoContext;

        /** 调库分组批次号索引（adjustGroupKey → adjustBatchNo），用于联动/互斥记录复用 */
        final Map<String, String> adjustBatchNoMap = new HashMap<>();

        /**
         * 保存本次 ABS 调库各申请项共用的数据
         *
         * 执行顺序：
         * 1. 保存证券和池资料、在途及观察池状态、流程快照，并保留批次号上下文
         *
         * @param securityInfo ABS 证券主数据
         * @param poolMap 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称
         * @param currentPoolIds ABS 当前所在的池 ID
         * @param poolRelationMap 全量投资池关系配置（poolId → relationType → 关联池 ID 列表）
         * @param hasPendingProcess 是否存在进行中的调库流程
         * @param securityInObservePool 证券是否在观察池
         * @param issuerInObservePool 发行主体是否在观察池
         * @param flowSnapshotMap 按流程 ID 整理的流程快照
         * @param batchNoContext 本次提交共用的批次号上下文
         */
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
     * 批次号生成上下文
     *
     * 同一请求内共用 submitTime，保证大批量落库时历史列表按时间+批次能同组相邻
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
     * 检查本次已加载的证券是否为 ABS 债
     *
     * 执行顺序：
     * 1. 检查证券的 ABS 标志，普通证券不允许在此调整
     *
     * @param security 已加载的证券信息
     */
    private void validateAbsSecurity(SecurityInfoBo security) {
        if (!Integer.valueOf(1).equals(security.getAbsFlag())) {
            throw new BizException("非 ABS 债不允许在此调整");
        }
    }

    /**
     * 校验手工目标池仅允许 15/16/17
     *
     * 执行顺序：
     * 1. ABS禁投池调整手工目标池仅允许 15、16、17、23时终止处理
     *
     * @param targetPoolId 目标池 ID
     */
    private void validateAllowedManualPoolId(Long targetPoolId) {
        if (targetPoolId == null || !ALLOWED_MANUAL_POOL_IDS.contains(targetPoolId)) {
            throw new BizException("ABS禁投池调整手工目标池仅允许 15、16、17、23");
        }
    }


}
