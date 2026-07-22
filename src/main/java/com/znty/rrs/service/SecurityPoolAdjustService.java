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
import com.znty.rrs.common.util.CreditBondRemainTermUtil;
import com.znty.rrs.common.util.MarketCodeMatchUtil;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustLogDto;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.flow.FlowOptionParam;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolStatusDto;
import com.znty.rrs.entity.securitypooladjust.PoolStatusDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.securitypooladjust.IpAdjustStepDto;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 证券池调库业务逻辑
 *
 * <p>核心功能：
 * <ul>
 *   <li>证券/投资池查询：证券列表分页、证券详情、可调投资池列表、当前入池状态</li>
 *   <li>调库申请提交：写入调库记录，初始状态为"流程中"</li>
 *   <li>调库可行性校验（checkAdjust）：对用户选择的每个调库项执行三层校验，
 *       并根据投资池关系配置自动生成联动/互斥项追加校验</li>
 * </ul>
 *
 * <p>校验结构：
 * <ul>
 *   <li>调入校验（checkInConditions）：前置规则（到期、流程进行中）+ 调入方向规则
 *       （重复入池、容量上限、来源池、限制池、互斥冲突、弹性禁投池）</li>
 *   <li>调出校验（checkOutConditions）：前置规则（到期、流程进行中）+ 调出方向规则
 *       （未入池、限制池、互斥池、互斥冲突、弹性禁投池）</li>
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

    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";
    /** 短时间重复提交判定窗口（秒） */
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;
    /** 证券池调库数据访问组件 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

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

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;
    /** 白名单调入流程 Key */
    private static final String FLOW_KEY_WHITELIST_INBOUND = "bond:whitelist-inbound";

    /** 评级下调判定组件（主体/展望/担保人评级下调判断，查 wind_cbondissuerrating） */
    @Resource
    private RatingDowngradeChecker ratingDowngradeChecker;
    /**
     * TODO 白名单流程尚未迁移：老系统读取 WHITEPOOLID_XYJJ 配置的主体白名单池，并校验期限及债券类型，
     * 命中后仍由用户选择是否走白名单流程；老系统的判断不依赖 WHITESECURITYLIST 表。
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
     * @param req 查询条件（证券代码、简称、发行人，均为模糊匹配）
     */
    public PageResult<SecurityInfoDto> querySecurityPage(SecurityPoolAdjustReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityInfoDto> records = securityPoolAdjustMapper.querySecurityPage(
                req.getSecurityCode(), req.getSecurityShortName(), req.getIssuer());
        PageInfo<SecurityInfoDto> pageInfo = new PageInfo<>(records);

        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询证券详情（调库页面顶部基础信息展示）
     *
     * @param req 需携带 securityCode
     */
    public SecurityInfoDetailDto querySecurityDetail(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityInfoDetailDto dto = securityPoolAdjustMapper.querySecurityDetail(req.getSecurityCode());
        if (dto == null) {
            throw new BizException(404, "证券不存在");
        }
        // 回填证券品种大类（bond/fund/stock/company），供前端按类型差异化展示（如基金评分输入）
        dto.setCategoryType(securityPoolAdjustMapper.queryCategoryTypeBySecurityType(dto.getSecurityType()));
        return dto;
    }

    /**
     * 查询可调入/可调出的投资池列表（含互斥关系，树结构由前端自行组装）
     *
     * @param req 当前用户为管理员时返回全量启用池，普通用户仅返回有可调整权限的池及其祖先节点；
     *            调入方向（adjustDirection=in）额外排除 CRMW 池，主体相关池（禁投/观察等）保留以支持单券入池
     */
    public List<PoolDto> queryAdjustPoolList(SecurityPoolAdjustReq req) {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools.isEmpty()) {
            return new ArrayList<>();
        }

        // 解析当前用户 ID
        Long currentUserId = parseCurrentUserId(req.getCurrentUserId());
        // 判断当前用户是否为管理员
        if (!isAdminUser(req.getCurrentUserId())) {
            // 按投资池“可调整人员”配置过滤可操作池
            allPools = filterAdjustablePoolsByUser(allPools, currentUserId);
            if (allPools.isEmpty()) {
                return new ArrayList<>();
            }
        }

        // 可调入列表排除 CRMW 池（CRMW 走独立调库链路）；主体禁投/观察等池不排除
        if ("in".equalsIgnoreCase(req.getAdjustDirection())) {
            // 过滤掉 CRMW 类型投资池
            allPools = filterOutCrmwPools(allPools);
            if (allPools.isEmpty()) {
                return new ArrayList<>();
            }
            // 是否放开规则：releaseRules=false 时，信用债大库池需满足主体债入库矩阵才显示可调
            allPools = filterInboundByGradeRule(allPools, req);
            if (allPools.isEmpty()) {
                return new ArrayList<>();
            }
        }

        // 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
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
        // 查询各投资池当前有效证券数量
        Map<Long, Integer> currentCountMap = securityPoolAdjustMapper.queryPoolCurrentCountList().stream()
                .collect(Collectors.toMap(PoolDto::getId, PoolDto::getCurrentCount));
        // InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
        return allPools.stream()
                .map(p -> toPoolDto(p, inMutexMap, outMutexMap, currentCountMap))
                .collect(Collectors.toList());
    }

    /**
     * 是否放开规则过滤：releaseRules=false 时，信用债大库池需满足主体债入库矩阵才保留。
     *
     * <p>对齐老系统 openrule 在选池阶段过滤可调入池列表（InvestPoolAdjustManageAction:855-859：
     * 遍历可调入池，信用债大库 ptype=4000 未放开规则时查 findMainGradeRuleList，矩阵不满足的池移除）。
     * releaseRules=true / 可转债 / 评级或期限档无法判定时不卡矩阵（对齐 inCheckMainGradeRule 跳过逻辑）。
     */
    private List<InvestmentPoolBo> filterInboundByGradeRule(List<InvestmentPoolBo> pools, SecurityPoolAdjustReq req) {
        if (pools == null || pools.isEmpty()) {
            return pools;
        }
        // releaseRules=true 或无证券代码：不卡矩阵
        if (req.isReleaseRules() || req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            return pools;
        }
        // 无信用债大库池：无需过滤
        boolean hasCreditBondPool = false;
        for (InvestmentPoolBo p : pools) {
            if (isCreditBondPool(p)) {
                hasCreditBondPool = true;
                break;
            }
        }
        if (!hasCreditBondPool) {
            return pools;
        }
        SecurityInfoBo securityInfo = securityPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            return pools;
        }
        // 可转债不适用主体债入库矩阵
        if ("convertible_bond".equals(securityInfo.getSecurityType())) {
            return pools;
        }
        // 观察池命中时跳过矩阵过滤（对齐 inCheckMainGradeRule 和老系统 ASTRICTPOOLS->openrule=1）
        if (securityPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode())
                || securityPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode())) {
            return pools;
        }
        // 主体内评分档（临时代码占位无内评默认最低档 4；担保债取主体与担保人较低者）
        String gradeCode = securityInfo.getInnerIssuerRating();
        if (gradeCode == null && "temporary".equals(securityInfo.getSecuritySource())) {
            gradeCode = "4";
        }
        if (securityInfo.getGuarantFlag() != null && securityInfo.getGuarantFlag() == 1
                && securityInfo.getInnerGuarantorRating() != null && !securityInfo.getInnerGuarantorRating().isEmpty()) {
            gradeCode = pickLowerRatingGrade(gradeCode, securityInfo.getInnerGuarantorRating());
        }
        // 期限档：date_exists（天）÷365 → 年，匹配 credit_bond_term_bucket
        String bucketCode = matchTermBucket(CreditBondRemainTermUtil.resolveRemainTermYears(securityInfo));
        // 评级/期限档无法判定：不卡矩阵（对齐 inCheckMainGradeRule return null）
        if (gradeCode == null || bucketCode == null) {
            return pools;
        }
        List<Long> allowedPoolIds = creditBondGradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(gradeCode, bucketCode);
        // 保留叶子：非信用债大库叶子，或信用债大库叶子在矩阵允许列表；父级按子级递归保留（避免丢失父级树结构）
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
            if (!isCreditBondPool(p)) {
                retained.add(p.getId());
            } else if (allowedPoolIds != null && allowedPoolIds.contains(p.getId())) {
                retained.add(p.getId());
            }
        }
        // 向上传播：父级若至少一个子级保留则保留（含多层父级）
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
     * 从投资池列表中排除 CRMW 类型池（pool_type=crmw）。
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
     * 判断当前用户是否为管理员。
     */
    private boolean isAdminUser(String currentUserId) {
        return ADMIN_USER_ID.equals(currentUserId);
    }

    /**
     * 解析当前用户 ID。
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
     * 按投资池“可调整人员”配置过滤可操作池，并保留祖先节点以保证前端树路径完整。
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
     * 查询当前用户直接或通过角色拥有可调整权限的投资池 ID。
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
     * @param req 需携带 securityCode
     */
    public SecurityPoolStatusDto querySecurityPoolStatus(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityPoolStatusDto dto = new SecurityPoolStatusDto();
        List<PoolStatusDto> securityCurrentPools = securityPoolAdjustMapper.querySecurityPoolStatusList(req.getSecurityCode());
        List<PoolStatusDto> issuerCurrentPools = securityPoolAdjustMapper.queryIssuerPoolStatusList(req.getSecurityCode());
        // 加载投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        // 填充当前所在池的全路径名称
        fillPoolStatusFullName(securityCurrentPools, poolFullNameMap);
        // 填充当前所在池的全路径名称
        fillPoolStatusFullName(issuerCurrentPools, poolFullNameMap);
        dto.setSecurityCurrentPools(securityCurrentPools);
        dto.setIssuerCurrentPools(issuerCurrentPools);
        return dto;
    }

    /** 填充当前所在池的全路径名称 */
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
     * 提交调库申请（五阶段流程）
     *
     * <p>分五个阶段顺序执行：
     * <ol>
     *   <li><b>前置校验</b>：校验请求入参合法性，不通过直接抛异常</li>
     *   <li><b>参数初始化</b>：集中执行所有 DB 查询，构建 {@link SubmitSharedData}，
     *       包含证券信息、投资池索引、池关系映射、各流程的快照数据</li>
     *   <li><b>调入处理</b>：遍历全部调入方向的调库项，逐项判断是否直通流程，
     *       直通则写入 ip_adjust_log（audit_status='20'），待全部方向处理完后统一复核并写入 ip_pool_status；
     *       非直通则写入 ip_adjust_log（audit_status='00'），若初始流程步骤懒创建时即走到结束节点，
     *       则升级为 audit_status='20' 并写入 ip_pool_status</li>
     *   <li><b>调出处理</b>：遍历全部调出方向的调库项，直通则写入 ip_adjust_log（audit_status='20'），待统一复核后软删除 ip_pool_status；
     *       非直通则写入 ip_adjust_log（audit_status='00'），若初始流程步骤懒创建时即走到结束节点，
     *       则升级为 audit_status='20' 并软删除 ip_pool_status</li>
     *   <li><b>后续处理</b>：合并并同步更新调库详情页传入的证券基础信息字段（editSecurityInfoForAdjust）</li>
     * </ol>
     *
     * @param req 调库申请，包含证券信息及一个或多个调库项（手工项须携带 flowId 或 flowKey，无流程时走直通）
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 提交调库申请及附件。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req, List<MultipartFile> files) {
        SysAttachmentService.SubmissionFiles submissionFiles =
                // 创建本次提交附件上下文
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId());
        return submitAdjustLog(req, submissionFiles, new BatchNoContext());
    }

    /**
     * 提交调库申请内部实现。
     */
    private AdjustSubmitDto submitAdjustLog(SecurityPoolAdjustSubmitReq req,
                                            SysAttachmentService.SubmissionFiles submissionFiles,
                                            BatchNoContext batchNoContext) {
        // ══ 第一阶段：前置校验 ══
        validateSubmitReq(req);
        // 检查短时间内是否已提交相同申请
        checkRecentDuplicateSubmit(req);

        // ══ 第二阶段：参数初始化 ══
        SubmitSharedData shared = loadSubmitSharedData(req, batchNoContext);
        // 检查本次涉及的顶级池组是否已有活动流程
        checkSubmitPendingPoolGroups(req, shared.poolMap);
        // 调入须同步调出互斥池：缺失或调出不可行则整单拒绝，防止双池
        validateRequiredMutexOutboundOnSubmit(req, shared);

        // ══ 第三阶段：调入处理 ══
        List<IpAdjustLogBo> directApplyLogs = new ArrayList<>();
        List<Long> inboundIds = executeInboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // ══ 第四阶段：调出处理 ══
        List<Long> outboundIds = executeOutboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // 对本次全部直通项统一锁池、复核并落地，确保同池容量按整次申请累计
        recheckAndApplyDirectLogs(directApplyLogs);

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
        }
    }

    /**
     * 提交时校验：调入目标池若要求配套互斥调出，请求中必须包含对应调出项且调出校验通过。
     *
     * <p>防止前端只提交可调入项、丢弃失败的互斥调出项，造成双池。
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
                // 复核互斥池调出可行性（锁池、冻结期等）
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
                    throw new BizException("配套互斥调出失败，无法完成调入（" + mutexPoolName + "）："
                            + String.join("；", outFailures));
                }
            }
        }
    }

    /**
     * 检查操作人短时间内是否已经提交内容相同的手工调库申请。
     */
    private void checkRecentDuplicateSubmit(SecurityPoolAdjustSubmitReq req) {
        List<SecurityPoolAdjustSubmitReq.AdjustItem> manualItems = req.getItems().stream()
                .filter(this::isManualSubmitItem)
                .collect(Collectors.toList());
        if (manualItems.isEmpty()) {
            return;
        }
        List<IpAdjustLogBo> recentLogs = securityPoolAdjustMapper.queryRecentManualAdjustLogList(
                req.getSecurityCode(), req.getAdjusterId(), DUPLICATE_SUBMIT_WINDOW_SECONDS);
        if (recentLogs == null || recentLogs.isEmpty()) return;
        List<String> requestKeys = manualItems.stream().map(item -> item.getTargetPoolId() + "|"
                + item.getAdjustMode() + "|" + item.getFlowId() + "|" + emptyToNull(item.getFlowKey()))
                .sorted().collect(Collectors.toList());
        List<String> historyKeys = recentLogs.stream().map(log -> log.getTargetPoolId() + "|"
                + log.getAdjustMode() + "|" + log.getFlowId() + "|" + emptyToNull(log.getFlowKey()))
                .sorted().collect(Collectors.toList());
        boolean sameText = recentLogs.stream().allMatch(log ->
                Objects.equals(emptyToNull(req.getAdjustReason()), emptyToNull(log.getAdjustReason()))
                        && Objects.equals(emptyToNull(req.getAdjustAdvice()), emptyToNull(log.getAdjustAdvice())));
        if (requestKeys.equals(historyKeys) && sameText) {
            throw new BizException("调库申请已提交，请勿重复操作");
        }
    }

    /** 检查正式提交涉及的池组是否已有活动流程（按每条调库项的证券代码独立判断）。 */
    private void checkSubmitPendingPoolGroups(SecurityPoolAdjustSubmitReq req,
                                              Map<Long, InvestmentPoolBo> poolMap) {
        // 按证券代码缓存 pending 池组，避免同码多行重复查库
        Map<String, Set<Long>> pendingGroupsBySecurity = new HashMap<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            String securityCode = resolveItemSecurityCode(req, item);
            Set<Long> pendingGroups = pendingGroupsBySecurity.get(securityCode);
            if (pendingGroups == null) {
                pendingGroups = resolvePoolGroupIds(
                        securityPoolAdjustMapper.queryPendingManualTargetPoolIdList(securityCode, null), poolMap);
                pendingGroupsBySecurity.put(securityCode, pendingGroups);
            }
            Long groupId = resolveRootPoolId(item.getTargetPoolId(), poolMap);
            if (groupId != null && pendingGroups.contains(groupId)) {
                InvestmentPoolBo group = poolMap.get(groupId);
                throw new BizException("证券[" + securityCode + "]在投资池组["
                        + (group != null ? group.getPoolName() : groupId)
                        + "]存在进行中的调库流程，请等待流程结束后再发起调库");
            }
        }
    }

    /** 将目标池列表转换为顶级投资池组 ID 集合。 */
    private Set<Long> resolvePoolGroupIds(List<Long> poolIds, Map<Long, InvestmentPoolBo> poolMap) {
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

    /** 将空字符串归一化为空值，避免重复提交比较受空串表现影响。 */
    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
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
     * 第三阶段：调入处理
     *
     * <p>遍历请求中全部调入方向的调库项，逐项判断流程是否为直通（start→end），
     * 决定写入已生效调库记录并加入统一落池集合，还是写入待审批调库记录。
     *
     * @param req    调库提交请求
     * @param shared 本次提交的共享数据
     * @return 本次调入处理生成的所有调库记录 ID
     */
    /**
     * 执行调入提交并绑定本次 multipart 文件。
     */
    private List<Long> executeInboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                            SysAttachmentService.SubmissionFiles submissionFiles,
                                            List<IpAdjustLogBo> directApplyLogs) {

        List<Long> generatedIds = new ArrayList<>();

        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 报告必填校验（按池 in_report_restriction；关联码项由同组手工项承担报告，跳过）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getInReportRestriction() : null,
                        resolveItemSecurityCode(req, item));
            }
            // 获取同组手工调库项，联动/互斥/关联项按手工项共用流程和批次号
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

                // 收集直通日志，待本次全部调库项生成后统一复核并落池
                logBo.setAdjustLogId(logBo.getId());
                directApplyLogs.add(logBo);
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
                        directApplyLogs.add(bo);
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
     * 决定写入已生效调库记录并加入统一落池集合，还是写入待审批调库记录。
     *
     * @param req    调库提交请求
     * @param shared 本次提交的共享数据
     * @return 本次调出处理生成的所有调库记录 ID
     */
    /**
     * 执行调出提交并绑定本次 multipart 文件。
     */
    private List<Long> executeOutboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                             SysAttachmentService.SubmissionFiles submissionFiles,
                                             List<IpAdjustLogBo> directApplyLogs) {

        List<Long> generatedIds = new ArrayList<>();

        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 报告必填校验（按池 out_report_restriction；关联码项由同组手工项承担报告，跳过）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getOutReportRestriction() : null,
                        resolveItemSecurityCode(req, item));
            }
            // 获取同组手工调库项，联动/互斥/关联项按手工项共用流程和批次号
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

                // 收集直通日志，待本次全部调库项生成后统一复核并落池
                directApplyLogs.add(bo);
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
                        directApplyLogs.add(bo);
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
        current.setAgencyName(changedField.getAgencyName());
        current.setInnerIssuerRating(changedField.getInnerIssuerRating());
        current.setDateCallExists(changedField.getDateCallExists());
        current.setInnerGuarantorRating(changedField.getInnerGuarantorRating());
        current.setDateExists(changedField.getDateExists());
        current.setFundUse(changedField.getFundUse());
        current.setPromptReason(changedField.getPromptReason());
        current.setAnalysis(changedField.getAnalysis());
        // dateRepurchaseExists、guarantFlag、guarantType、absFlag 当前页面不可编辑，保留数据库原值
    }

    // ═══════════════════════════════════════════════════════════
    //  查询类接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询调库记录列表（全量，不分页）。
     *
     * <p>有批次号时按批次返回同批全部记录（含关联码不同 security_code 的关联调整）；
     * 无批次号时按证券代码返回未终结流程（申请页在途展示）。
     *
     * @param req 有批次时 adjustBatchNo 必填即可；无批次时需 securityCode
     */
    public List<AdjustLogDto> queryAdjustLogList(SecurityPoolAdjustReq req) {
        boolean hasBatch = req.getAdjustBatchNo() != null && !req.getAdjustBatchNo().isEmpty();
        if (!hasBatch && (req.getSecurityCode() == null || req.getSecurityCode().isEmpty())) {
            throw new BizException("证券代码不能为空");
        }
        List<IpAdjustLogBo> logs = securityPoolAdjustMapper.queryAdjustLogList(
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

    /** 查询指定调库记录或同批次共用流程的步骤列表 */
    public List<IpAdjustStepDto> queryAdjustStepList(SecurityPoolAdjustReq req) {
        Long adjustLogId = req.getAdjustLogId();
        String adjustBatchNo = req.getAdjustBatchNo();
        if (adjustLogId == null && (adjustBatchNo == null || adjustBatchNo.isEmpty())) {
            return Collections.emptyList();
        }
        List<IpAdjustStepBo> steps = securityPoolAdjustMapper.queryAdjustStepByBatchList(adjustLogId, adjustBatchNo);
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
                // 执行调入校验并补充联动、互斥调库项
                executeInAdjustCheck(req, shared, coveredKeys));

        // ══ 第四阶段：调出校验 ══
        resultItems.addAll(executeOutAdjustCheck(req, shared, coveredKeys));

        // ══ 第四点五阶段：多市场关联码扩批（itemTag=related） ══
        // 回填主券证券信息，并按 wind_code_sh/sz/nib/bj/nbc 展开同池同向关联项
        expandRelatedMarketCodeItems(req, shared, resultItems);

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
        shared.setPendingPoolGroupIds(resolvePoolGroupIds(
                securityPoolAdjustMapper.queryPendingManualTargetPoolIdList(req.getSecurityCode(), null), poolMap));
        // 观察池标志：老项目用于跳过债大库校验（ASTRICTPOOLS→openrule=1/isgcc=true），
        // 当前项目主体债入库规则未落地（P2 阻塞），暂仅加载不使用，待主体债入库规则接入后启用
        shared.setSecurityInObservePool(securityPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()));
        shared.setIssuerInObservePool(securityPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()));
        // 评级下调三标志：主体/展望按发行人评级判定，担保人按前端选中代码判定（查 wind_cbondissuerrating）
        shared.setIssuerRatingDowngraded(ratingDowngradeChecker.isIssuerDowngraded(securityInfo));
        shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(securityInfo));
        shared.setGuarantorRatingDowngraded(ratingDowngradeChecker.isGuarantorDowngraded(req.getGuarantorCode()));
        shared.setRequestInPoolIds(requestInPoolIds);
        shared.setRequestOutPoolIds(requestOutPoolIds);
        // 基金评分（基金证券调入校验用，透传请求级 fundRate）
        shared.setFundRate(req.getFundRate());
        // 是否放开规则：是=跳过主体债入库矩阵校验（对齐批量调库 releaseRules）
        shared.setReleaseRules(req.isReleaseRules());
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
     *
     * <p>信用债大库（pool_type=credit_bond）默认排除：互斥场景仍走白名单/简易/升降级/默认调入，
     * 不强制覆盖为 bond:special-inbound。
     */
    private AdjustCheckDto.FlowOption resolveSpecialInboundFlowOption(InvestmentPoolBo targetPool,
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
     * 1. 剩余期限 <= 3 年（取 date_exists 天数）；
     * 2. 排除永续债、私募债、ABS 债；
     * 3. 债券类型属于债券类；
     * 4. 债券主体在白名单配置池中；
     * 5. 债券不是担保债。</p>
     *
     * <p>TODO 当前版本不新增白名单配置表、不修改表结构，因此仅保留入口并返回未命中。
     * 老系统实际使用 WHITEPOOLID_XYJJ 指定的主体白名单池，并排除永续债、私募债、ABS、次级债，
     * 满足剩余期限不超过 3 年后由用户确认是否选择白名单流程。</p>
     */
    private boolean isWhitelistFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, List<String> matchReasons, List<String> unmatchReasons) {
        SecurityInfoBo sec = shared.getSecurityInfo();

        // 条件1：剩余期限 ≤ 3 年（rrs_securityinfo.date_exists，天）
        Integer remainDays = sec.getDateExists();
        if (remainDays == null) {
            unmatchReasons.add("剩余期限无法解析，date_exists 为空");
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
     * 2. 剩余期限可解析（date_exists 天数）；
     * 3. 剩余期限不超过同主体在目标池已有债券的最大剩余期限（MAX date_exists）；
     * 4. 该主体180天内以一般流程入过目标池；
     * 5. 主体评级和展望评级未下调，或下调时担保人评级未下调。</p>
     *
     * <p>评级下调标识由 RatingDowngradeChecker 查 wind_cbondissuerrating 真实判定：主体评级比较 b_info_creditrating 与 b_info_precreditrating，展望读 rrs_securityinfo.rating_outlook 是否负面，担保人按前端选中代码查 wind。</p>
     */
    private boolean isSimpleInboundFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, InvestmentPoolBo targetPool,
            List<String> matchReasons, List<String> unmatchReasons) {

        if (isCreditBondLevelOneToThree(targetPool)) {
            matchReasons.add("目标池属于信用债大库一、二、三级库");
        } else {
            unmatchReasons.add("目标池不是信用债大库一、二、三级库");
        }

        Integer remainDays = shared.getSecurityInfo().getDateExists();
        if (remainDays == null) {
            unmatchReasons.add("剩余期限无法解析，date_exists 为空");
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
     * 格式化剩余期限展示文本（date_exists 天数）。
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
        // 入池检查：证券当前在弹性禁投池中（in_soft_restrict，警告不阻断）
        addIfWarning(ctx.getWarnings(), inCheckElasticPool(ctx));
        // 入池检查：证券是否在全局禁止池（forbidden 禁投池/blacklist 黑名单）
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
     * SecurityInfoBo.innerIssuerRating 替代老项目 lon，用 date_exists（剩余期限天）÷365 + credit_bond_term_bucket
     * 替代 bondDurationId。担保债取主体与担保人评级较低者（老项目 lon=min(主体,担保人)）。可转债不适用矩阵跳过。
     * 仅调入校验（老代码 checkOutPool 无）。
     */
    private String inCheckMainGradeRule(AdjustCheckContext ctx) {
        // 是否放开规则：是=跳过主体债入库矩阵校验（对齐批量调库 releaseRules）
        if (ctx.isReleaseRules()) {
            return null;
        }
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
            // 临时代码占位证券常无内评：对齐老系统 lon 默认 8.0（对应本矩阵最低档 grade_code=4）
            if (isTemporarySecurity(sec)) {
                gradeCode = "4";
            } else {
                return "未配置主体内评分档，不符合入库条件";
            }
        }
        // 担保债取严：主体与担保人评级较低者（sort_no 大者评级低）
        if (sec.getGuarantFlag() != null && sec.getGuarantFlag() == 1
                && sec.getInnerGuarantorRating() != null && !sec.getInnerGuarantorRating().isEmpty()) {
            gradeCode = pickLowerRatingGrade(gradeCode, sec.getInnerGuarantorRating());
        }
        // 期限档：date_exists（天）÷365 匹配 credit_bond_term_bucket
        String bucketCode = matchTermBucket(CreditBondRemainTermUtil.resolveRemainTermYears(sec));
        if (bucketCode == null) {
            // 无法判定期限档（date_exists 为空或不在任何区间），跳过
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
     * 按剩余期限（年）匹配信用债期限分组编码。
     *
     * <p>遍历启用的 credit_bond_term_bucket，按 min_term_year/max_term_year + inclusive 标志判定区间归属。
     * 入参为 {@link CreditBondRemainTermUtil#resolveRemainTermYears}（date_exists 天 ÷365）。
     */
    private String matchTermBucket(java.math.BigDecimal remainTermYears) {
        if (remainTermYears == null) {
            return null;
        }
        List<CreditBondTermBucketBo> buckets = creditBondGradeRuleMapper.queryEnabledTermBucketList();
        for (CreditBondTermBucketBo b : buckets) {
            boolean minOk = b.getMinTermYear() == null
                    || (b.getMinInclusive() != null && b.getMinInclusive() == 1
                        ? remainTermYears.compareTo(b.getMinTermYear()) >= 0
                        : remainTermYears.compareTo(b.getMinTermYear()) > 0);
            boolean maxOk = b.getMaxTermYear() == null
                    || (b.getMaxInclusive() != null && b.getMaxInclusive() == 1
                        ? remainTermYears.compareTo(b.getMaxTermYear()) <= 0
                        : remainTermYears.compareTo(b.getMaxTermYear()) < 0);
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
     * 校验改判目标池是否符合主体债入库矩阵（评级联动，调入审批改判用）。
     *
     * <p>按债券的主体内评分档 × 期限档查 credit_bond_pool_grade_rule 矩阵得允许池列表，
     * 改判池须在列表内。复用 inCheckMainGradeRule 的矩阵查询逻辑。
     *
     * @param securityCode    证券代码
     * @param redirectPoolId  改判目标池 ID
     * @throws BizException 改判池不在矩阵允许列表内或证券无评级/期限档
     */
    public void validateRedirectPool(String securityCode, Long redirectPoolId) {
        if (securityCode == null || securityCode.isEmpty() || redirectPoolId == null) {
            return;
        }
        SecurityInfoBo sec = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (sec == null) {
            throw new BizException("证券不存在，无法校验改判池");
        }
        List<Long> allowedPoolIds = queryAllowedPoolIdsForSecurity(sec);
        if (allowedPoolIds == null || allowedPoolIds.isEmpty()) {
            throw new BizException("该证券不符合主体债入库矩阵，无法改判");
        }
        if (!allowedPoolIds.contains(redirectPoolId)) {
            throw new BizException("改判池不在主体债入库矩阵允许列表内");
        }
    }

    /**
     * 查询证券的主体债入库矩阵允许池列表（评级联动改判池候选项）。
     *
     * @param securityCode 证券代码
     * @return 允许调入的池列表（含池名），空列表表示无匹配矩阵
     */
    public List<InvestmentPoolBo> queryRedirectPoolOptions(String securityCode) {
        if (securityCode == null || securityCode.isEmpty()) {
            return new ArrayList<>();
        }
        SecurityInfoBo sec = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (sec == null) {
            return new ArrayList<>();
        }
        List<Long> allowedPoolIds = queryAllowedPoolIdsForSecurity(sec);
        if (allowedPoolIds == null || allowedPoolIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 全量池 Map 取池详情
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
     * 最终审批落池前按老系统工作流口径复核动态业务状态。
     *
     * @param logList 同批次调库记录
     */
    public void recheckBeforeFinalApproval(List<IpAdjustLogBo> logList) {
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

    /**
     * 对直通调库日志执行最终复核并立即落池。
     *
     * @param logList 本次需要立即生效的完整调库日志集合
     */
    private void recheckAndApplyDirectLogs(List<IpAdjustLogBo> logList) {
        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 按最终审批口径锁池并复核动态业务状态
        recheckBeforeFinalApproval(logList);
        // 将复核通过的直通调库日志统一应用到当前池状态
        applyPoolStatusChanges(logList);
    }

    /**
     * 将已完成最终复核的调库日志应用到当前池状态。
     *
     * @param logList 已通过最终复核的调库日志集合
     */
    public void applyPoolStatusChanges(List<IpAdjustLogBo> logList) {
        for (IpAdjustLogBo log : logList) {
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                log.setAuditStatus(AuditStatus.APPROVED.getCode());
                log.setAdjustLogId(log.getId());
                int inserted = securityPoolAdjustMapper.addPoolStatus(log);
                if (inserted != 1) {
                    throw new BizException("证券[" + log.getSecurityCode() + "]调入投资池["
                            + log.getTargetPoolName() + "]失败：入池状态写入异常");
                }
            } else if (AdjustMode.OUT.getCode().equals(log.getAdjustMode())) {
                int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(
                        log.getSecurityCode(), log.getTargetPoolId());
                if (deleted == 0) {
                    throw new BizException("证券[" + log.getSecurityCode() + "]已不在投资池["
                            + log.getTargetPoolName() + "]，调出失败");
                }
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
     * 查证券的主体债入库矩阵允许池 ID 列表（复用 inCheckMainGradeRule 的评级/期限档逻辑）。
     */
    private List<Long> queryAllowedPoolIdsForSecurity(SecurityInfoBo sec) {
        String gradeCode = sec.getInnerIssuerRating();
        if (gradeCode == null || gradeCode.isEmpty()) {
            return new ArrayList<>();
        }
        // 担保债取严
        if (sec.getGuarantFlag() != null && sec.getGuarantFlag() == 1
                && sec.getInnerGuarantorRating() != null && !sec.getInnerGuarantorRating().isEmpty()) {
            gradeCode = pickLowerRatingGrade(gradeCode, sec.getInnerGuarantorRating());
        }
        // 期限档：date_exists（天）÷365
        String bucketCode = matchTermBucket(CreditBondRemainTermUtil.resolveRemainTermYears(sec));
        if (bucketCode == null) {
            return new ArrayList<>();
        }
        return creditBondGradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(gradeCode, bucketCode);
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
        // TODO 股票分管人限制及 StockResearch/investrank 评级来源尚未迁移，当前仅保留评级入口
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
        // TODO 基金评分当前仅在 checkAdjust 校验，正式提交不携带 fundRate 且不再次校验
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
     * 规则：债券到期（maturity_date）
     *
     * <p>债券到期日早于今日则禁止调入。对应老项目 checkInPool:544（ptype=4000 债券分支）。
     */
    private String inCheckBondMaturity(AdjustCheckContext ctx) {
        String maturityDate = ctx.getSecurityInfo().getMaturityDate();
        if (maturityDate != null && !maturityDate.isEmpty()) {
            String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
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
            String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
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
            String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
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
            String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
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
            return "入池生效时间缺失，无法校验冻结期";
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
     * 市场 code 统一见 {@link com.znty.rrs.common.enums.MarketCode}，
     * 推导规则见 {@link com.znty.rrs.common.util.MarketCodeMatchUtil}。
     */
    private String inCheckMarket(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getMarketCodes() == null || pool.getMarketCodes().isEmpty()) {
            return null;
        }
        // 证券任一可推导市场落在池配置内即可
        if (!MarketCodeMatchUtil.matchesPoolMarkets(pool.getMarketCodes(), ctx.getSecurityInfo())) {
            return "该证券不在[" + pool.getPoolName() + "]所设定的投资市场内";
        }
        return null;
    }

    /**
     * 规则：全局禁止池（forbidden 禁投池/blacklist 黑名单）
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
     * TODO 投资池当前配置的是农业、城投、电力等业务行业值，尚未完成与证券行业数据的可靠映射；
     * 当前暂用 SecurityInfoBo.industryName 名称精确匹配，不作为最终行业校验口径（老项目为编码前缀层级匹配）。
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

    /**
     * 规则：报告必填（in_report_restriction / out_report_restriction）
     *
     * <p>目标池配置了报告限制时，提交时校验报告附件：
     * none=不限制 / any=任意一篇研究报告 / internal=必须是内部研究报告。
     * 对应老项目 rschDocMode 报告校验。在 addAdjustLog 提交阶段校验（checkAdjust 阶段无报告信息）。
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
                && shared.getPendingPoolGroupIds().contains(resolveRootPoolId(item.getTargetPoolId(), shared.getPoolMap())));
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
        // 是否放开规则（透传 shared，主体债入库矩阵校验用）
        ctx.setReleaseRules(shared.isReleaseRules());
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
     * 互斥配套调出失败时，反阻断同组手工调入，避免只入目标池导致双池。
     *
     * <p>仅处理 itemTag=mutex 的配套调出；联动失败不反绑手工项。
     */
    private void propagateMutexFailureToManual(AdjustCheckDto.CheckResultItem manualItem,
                                               List<AdjustCheckDto.CheckResultItem> groupMutexItems) {
        if (manualItem == null || !manualItem.isCanAdjust()
                || groupMutexItems == null || groupMutexItems.isEmpty()) {
            return;
        }
        List<String> mutexFailures = new ArrayList<>();
        for (AdjustCheckDto.CheckResultItem mutexItem : groupMutexItems) {
            if (mutexItem == null || mutexItem.isCanAdjust()) {
                continue;
            }
            String poolName = mutexItem.getPoolName() != null
                    ? mutexItem.getPoolName()
                    : String.valueOf(mutexItem.getTargetPoolId());
            String detail = "";
            if (mutexItem.getFailReasons() != null && !mutexItem.getFailReasons().isEmpty()) {
                detail = "：" + String.join("；", mutexItem.getFailReasons());
            }
            mutexFailures.add("配套互斥调出失败，无法完成调入（" + poolName + "）" + detail);
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
    //  对象转换方法
    // ═══════════════════════════════════════════════════════════

    /**
     * InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
     *
     * @param pool        投资池实体
     * @param inMutexMap  调入互斥关系（poolId → 互斥池 ID 列表）
     * @param outMutexMap 调出互斥关系（poolId → 互斥池 ID 列表）
     * @param currentCountMap 投资池当前有效证券数量映射
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
                                         SecurityPoolAdjustSubmitReq.AdjustItem flowSource,
                                         SubmitSharedData shared) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        // 关联码项使用 item 级证券代码，保证独立落 log / 池状态
        String securityCode = resolveItemSecurityCode(req, item);
        String securityShortName = item.getSecurityShortName() != null && !item.getSecurityShortName().isEmpty()
                ? item.getSecurityShortName() : req.getSecurityShortName();
        String securityType = item.getSecurityType() != null && !item.getSecurityType().isEmpty()
                ? item.getSecurityType() : req.getSecurityType();
        // 兜底：关联项未带简称/类型时按代码回查主数据
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

    /**
     * 从主券 market 代码字段解析关联证券代码列表（已带后缀、去自身、去重，不递归）。
     */
    private List<String> resolveRelatedSecurityCodes(SecurityInfoBo main) {
        List<String> codes = new ArrayList<>();
        if (main == null) {
            return codes;
        }
        String mainCode = main.getWindCode() == null ? "" : main.getWindCode().trim();
        Set<String> seen = new HashSet<>();
        for (String raw : Arrays.asList(
                main.getWindCodeSh(), main.getWindCodeSz(), main.getWindCodeNib(),
                main.getWindCodeBj(), main.getWindCodeNbc())) {
            if (raw == null) {
                continue;
            }
            String code = raw.trim();
            if (code.isEmpty() || code.equals(mainCode) || !seen.add(code)) {
                continue;
            }
            codes.add(code);
        }
        return codes;
    }

    /**
     * 按关联证券代码加载校验共享数据（复用主请求的池关系/请求池集合）。
     */
    private AdjustSharedData loadSharedDataForSecurity(String securityCode, SecurityInfoBo securityInfo,
                                                       AdjustSharedData mainShared, AdjustCheckReq mainReq) {
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
        shared.setSecurityInObservePool(securityPoolAdjustMapper.querySecurityInObservePool(securityCode));
        shared.setIssuerInObservePool(securityPoolAdjustMapper.queryIssuerInObservePool(securityCode));
        // 评级下调标志按关联券自身主体信息判定
        shared.setIssuerRatingDowngraded(ratingDowngradeChecker.isIssuerDowngraded(securityInfo));
        shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(securityInfo));
        shared.setGuarantorRatingDowngraded(
                ratingDowngradeChecker.isGuarantorDowngraded(mainReq.getGuarantorCode()));
        shared.setRequestInPoolIds(mainShared.getRequestInPoolIds());
        shared.setRequestOutPoolIds(mainShared.getRequestOutPoolIds());
        shared.setFundRate(mainReq.getFundRate());
        return shared;
    }

    /**
     * 多市场关联码扩批：对主结果中的 manual/linkage/mutex 项，按主券市场代码字段展开 related 项。
     *
     * <p>关联码独立校验已在池/不在池/pending/容量等；同池同批调入累计 projected 占用容量。
     */
    private void expandRelatedMarketCodeItems(AdjustCheckReq req, AdjustSharedData mainShared,
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

        List<String> relatedCodes = resolveRelatedSecurityCodes(mainSec);
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
            SecurityInfoBo relatedSec = securityPoolAdjustMapper.querySecurityBoByCode(relatedCode);
            AdjustSharedData relatedShared = relatedSec == null ? null
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
                    int baseCount = securityPoolAdjustMapper.queryPoolCurrentCount(mother.getTargetPoolId());
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
     * <p>同一请求内共用 submitTime，保证大批量落库时历史列表按时间+批次能同组相邻。
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

}
