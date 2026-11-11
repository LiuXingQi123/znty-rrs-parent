package com.znty.rrs.service;

import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.common.util.CrmwBatchAdjustFlowOptionHelper;
import com.znty.rrs.entity.securitypooladjust.RatingCompanySelectionReq;
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

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckContext;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.AdjustLogDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustSharedData;
import com.znty.rrs.entity.crmwpooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.flow.FlowOptionParam;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotCrmwBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import org.springframework.beans.BeanUtils;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDto;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.entity.crmwpooladjust.SecurityPoolStatusDto;
import com.znty.rrs.entity.crmwpooladjust.PoolStatusDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.crmwpooladjust.IpAdjustStepDto;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.crmwpooladjust.PoolDto;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import org.springframework.stereotype.Service;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 处理 CRMW 凭证与标的证券组合的调库，为单笔、批量和 Excel 导入提供共用的校验和提交能力
 *
 * 主要职责
 * 1. 查询可选凭证、标的证券、投资池以及组合的在池状态和调整记录
 * 2. 按入口检查调入和调出规则，补充联动、互斥及同券跨市场调整项，并匹配审批流程
 * 3. 提交时保存调库日志、证券快照和初始审批步骤，无需人工审批的记录复核通过后更新池状态
 * 4. 为人工终审提供生效前复核和池状态更新
 *
 * 校验结构
 * 1. 调入先检查池锁定、品种、市场和进行中的流程，再检查重复入池、容量、来源池、限制池、互斥冲突、禁投状态和开放日
 * 2. 调出先检查池锁定和进行中的流程，再检查在池状态、冻结期、限制池、互斥池、互斥冲突和开放日
 * 3. 通用规则之后按标的品种补充检查，例如债券到期和股票退市
 * 4. 调入和调出命中弹性禁投池时只记录提示
 *
 * 自动生成项规则
 * 1. 联动池（linkage）按目标池配置补充同方向调整项，调入时追加联动调入，调出时追加联动调出
 * 2. 互斥池（mutex）在调入时检查组合是否已在目标池配置的调入互斥池中，已在池时追加该互斥池的调出项
 * 3. 同券跨市场项（related）按同一 ISIN 对应的跨市场证券代码扩展调整项，沿用原凭证并按实际标的逐项校验
 */
@Service
public class CrmwPoolAdjustService {
    /** 统一证券市场及同券查询组件。 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 短时间重复提交判定窗口（秒） */
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;

    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";
    /** CRMW 证券类型编码 */
    private static final String CRMW_SECURITY_TYPE = "crmw";
    /** Excel 主项来源，同组配套项通过批次继承提示放行口径 */
    private static final String EXCEL_IMPORT_ADJUST_TYPE = "Excel导入";
    /** CRMW池调整数据访问组件 */
    @Resource
    private CrmwPoolAdjustMapper crmwPoolAdjustMapper;

    /** 发行主体财务数据访问组件 */
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

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** 报告库服务，用于调库完成后统一生成内部报告 */
    @Resource
    private ReportService reportService;
    /** 白名单调入流程 Key */
    private static final String FLOW_KEY_WHITELIST_INBOUND = "bond:whitelist-inbound";
    /** 是否启用白名单调入流程；当前业务未启用，恢复时改为 true。 */
    private static final boolean WHITELIST_FLOW_ENABLED = false;

    /** 通用查询服务 */
    @Resource
    private CommonService commonService;

    /** 复用证券池调库的评级主体选择与防伪校验口径 */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /** 标的 ABS 与普通池共用唯一主体身份 */
    @Resource
    private AbsIdentityService absIdentityService;

    /** 评级下调判定组件（主体/展望/担保人评级下调判断，查 wind_cbondissuerrating） */
    @Resource
    private RatingDowngradeChecker ratingDowngradeChecker;
    /** 白名单池 ID 集合：主体在这些池中时符合白名单条件；当前写死空集，后续配置后补 queryIssuerInWhitelistPools 查询 */
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
     * 分页查询可选 CRMW 凭证
     *
     * 执行顺序：
     * 1. 按代码、简称和发行人条件分页查询凭证
     *
     * @param req 凭证代码、简称、发行人及分页条件
     * @return 凭证分页结果
     */
    public PageResult<SecurityInfoDto> queryCrmwPage(CrmwPoolAdjustReq req) {
        // 1. 按分页参数查询符合条件的 CRMW 凭证
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityInfoDto> records = crmwPoolAdjustMapper.queryCrmwPage(
                req.getSecurityCode(), req.getSecurityShortName(), req.getIssuer());
        // 2. 将查询记录和总条数一起返回
        PageInfo<SecurityInfoDto> pageInfo = new PageInfo<>(records);

        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询 CRMW 可绑定的标的证券
     *
     * 执行顺序：
     * 1. 按代码、简称、发行人和债券标记分页查询可绑定证券
     *
     * @param req 标的证券的筛选和分页条件
     * @return 标的证券分页结果
     */
    public PageResult<SecurityInfoDto> queryBindableSecurityPage(CrmwPoolAdjustReq req) {
        // 1. 按分页参数查询符合条件的可绑定标的
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityInfoDto> records = crmwPoolAdjustMapper.queryBindableSecurityPage(
                req.getSecurityCode(), req.getSecurityShortName(), req.getIssuer(), req.getBondYesFlags());
        // 2. 将查询记录和总条数一起返回
        PageInfo<SecurityInfoDto> pageInfo = new PageInfo<>(records);

        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询 CRMW 凭证详情
     *
     * 执行顺序：
     * 1. 按代码查询证券并确认其为 CRMW 凭证
     * 2. 补充市场代码和同券跨市场代码
     *
     * @param req 待查询的凭证代码
     * @return CRMW 凭证详情
     */
    public SecurityInfoDetailDto queryCrmwDetail(CrmwPoolAdjustReq req) {
        // 1. 按代码查询证券并确认其为 CRMW 凭证
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("CRMW代码不能为空");
        }
        SecurityInfoDetailDto dto = crmwPoolAdjustMapper.querySecurityDetail(req.getSecurityCode());
        if (dto == null) {
            throw new BizException(404, "CRMW凭证不存在");
        }
        if (!CRMW_SECURITY_TYPE.equals(dto.getSecurityType())) {
            throw new BizException("所选证券不是 CRMW 凭证");
        }
        // 2. 补充市场代码和同券跨市场代码
        dto.setMarketCode(securityMarketService.queryMarketCode(dto.getWindCode()));
        dto.setRelatedMarketCodes(securityMarketService.queryRelatedMarketCodes(dto.getWindCode()));
        return dto;
    }

    /**
     * 查询 CRMW 标的证券详情
     *
     * 历史申请优先显示当时的证券快照，新申请仍保留主数据中未开放编辑的字段
     *
     * 执行顺序：
     * 1. 校验证券代码，有调库日志 ID 时优先读取该日志的证券快照
     * 2. 没有对应快照时读取主数据，并用最近快照覆盖可编辑字段
     * 3. 确认标的不是 CRMW 凭证，新发起的 ABS 补充当前身份
     * 4. 非主体类标的补充市场和同券代码
     *
     * @param req 标的证券代码和可选的调库日志 ID
     * @return 标的证券详情
     */
    public SecurityInfoDetailDto querySecurityDetail(CrmwPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityInfoDetailDto dto = null;
        SecurityInfoBo currentMaster = null;
        // 1. 指定日志 ID 时，先读取该次申请保存的证券快照
        if (req.getAdjustLogId() != null) {
            dto = crmwPoolAdjustMapper.querySecuritySnapshotDetailByAdjustLogId(
                    req.getAdjustLogId(), req.getSecurityCode());
        }
        // 2. 没有对应快照时，以主数据为底补入最近快照的可编辑字段
        if (dto == null) {
            SecurityInfoDetailDto master = crmwPoolAdjustMapper.querySecurityDetail(req.getSecurityCode());
            if (master == null) {
                throw new BizException(404, "证券不存在");
            }
            if (CRMW_SECURITY_TYPE.equals(master.getSecurityType())) {
                throw new BizException("标的证券不能是 CRMW 凭证");
            }
            currentMaster = new SecurityInfoBo();
            BeanUtils.copyProperties(master, currentMaster);
            SecurityInfoDetailDto latestSnapshot =
                    crmwPoolAdjustMapper.queryLatestSecuritySnapshotDetailByWindCode(req.getSecurityCode());
            // 用最近的调库快照回填可编辑字段
            overlayEditableFieldsFromSnapshot(master, latestSnapshot);
            dto = master;
        }
        if (dto == null) {
            throw new BizException(404, "证券不存在");
        }
        if (CRMW_SECURITY_TYPE.equals(dto.getSecurityType())) {
            throw new BizException("标的证券不能是 CRMW 凭证");
        }
        // 3. 新发起的 ABS 调库补充当前身份
        if (req.getAdjustLogId() == null && CreditBondSpecialInboundRule.isAbs(dto)) {
            dto.setAbsIdentity(absIdentityService.queryCurrentIdentity(currentMaster));
        }
        // 4. 非主体标的补充实时市场代码，保留快照中的其他字段
        if (!"company".equals(dto.getSecurityType())) {
            dto.setMarketCode(securityMarketService.queryMarketCode(dto.getWindCode()));
            dto.setRelatedMarketCodes(securityMarketService.queryRelatedMarketCodes(dto.getWindCode()));
        }
        return dto;
    }

    /**
     * 用最近的调库快照回填可编辑字段
     *
     * 执行顺序：
     * 1. 主数据和快照都存在时，将快照中的期限、评级、分析和主体选择等可编辑字段写入详情
     *
     * @param master 待回填可编辑字段的证券详情
     * @param snapshot 最近一次调库保存的证券快照
     */
    private void overlayEditableFieldsFromSnapshot(SecurityInfoDetailDto master, SecurityInfoDetailDto snapshot) {
        if (master == null || snapshot == null) {
            return;
        }
        master.setIssueAmountplan(snapshot.getIssueAmountplan());
        master.setCouponRate(snapshot.getCouponRate());
        master.setMaturityembeddedDesc(snapshot.getMaturityembeddedDesc());
        master.setDateInrightExists(snapshot.getDateInrightExists());
        master.setCarryDate(snapshot.getCarryDate());
        master.setMaturityDate(snapshot.getMaturityDate());
        master.setInfoPledgeRatio(snapshot.getInfoPledgeRatio());
        master.setRatingBondAgency(snapshot.getRatingBondAgency());
        master.setRatingBond(snapshot.getRatingBond());
        master.setRatingBondissuer(snapshot.getRatingBondissuer());
        master.setRatingOutlook(snapshot.getRatingOutlook());
        master.setGuarantor(snapshot.getGuarantor());
        master.setGuarantorId(snapshot.getGuarantorId());
        master.setInnerGuarantorRating(snapshot.getInnerGuarantorRating());
        master.setAgencyName(snapshot.getAgencyName());
        master.setDateCallExists(snapshot.getDateCallExists());
        master.setDateExists(snapshot.getDateExists());
        master.setFundUse(snapshot.getFundUse());
        master.setPromptReason(snapshot.getPromptReason());
        master.setAnalysis(snapshot.getAnalysis());
        master.setAbsOriginatorName(snapshot.getAbsOriginatorName());
        master.setCompanySelector(snapshot.getCompanySelector());
    }

    /**
     * 查询当前用户可见的 CRMW 调库池
     *
     * 执行顺序：
     * 1. 加载投资池并筛出 CRMW 池
     * 2. 普通用户仅保留可调整池及其上级池
     * 3. 整理调入和调出互斥关系，并统计各池当前数量
     * 4. 组装池列表
     *
     * @param req 含当前用户 ID 的投资池查询条件
     * @return 包含互斥关系和在池数量的 CRMW 池列表
     */
    public List<PoolDto> queryCrmwAdjustPoolList(CrmwPoolAdjustReq req) {
        // 1. 加载投资池并筛出 CRMW 池
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools.isEmpty()) {
            return new ArrayList<>();
        }

        // 仅保留 CRMW 池，避免与普通证券池调库范围互相影响
        allPools = allPools.stream()
                .filter(p -> PoolType.CRMW.getCode().equals(p.getPoolType()))
                .collect(Collectors.toList());
        if (allPools.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 普通用户只保留可调整池及其上级池
        Long currentUserId = parseCurrentUserId(req.getCurrentUserId());
        // 判断当前用户是否为管理员
        if (!isAdminUser(req.getCurrentUserId())) {
            // 按投资池“可调整人员”配置过滤可操作池
            allPools = filterAdjustablePoolsByUser(allPools, currentUserId);
            if (allPools.isEmpty()) {
                return new ArrayList<>();
            }
        }

        // 3. 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
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
        Map<Long, Integer> currentCountMap = crmwPoolAdjustMapper.queryPoolCurrentCountList().stream()
                .collect(Collectors.toMap(PoolDto::getId, PoolDto::getCurrentCount));
        // 4. 组装池的基本信息、互斥关系和在池数量
        return allPools.stream()
                // 转换 CRMW 池列表中的池数据
                .map(p -> toPoolDto(p, inMutexMap, outMutexMap, currentCountMap))
                .collect(Collectors.toList());
    }

    /**
     * 判断当前用户是否为管理员
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
     * 解析当前用户 ID
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
     * 保留用户可调整的池和展示所需的上级池
     *
     * 保留上级池是为了让页面能展示完整的池层级
     *
     * 执行顺序：
     * 1. 查询用户可调整的池 ID
     * 2. 沿各池的父级向上收集可见池 ID
     * 3. 按原顺序筛选可见池
     *
     * @param allPools 全部候选投资池
     * @param userId 当前用户的数字 ID
     * @return 用户可见的投资池列表
     */
    private List<InvestmentPoolBo> filterAdjustablePoolsByUser(List<InvestmentPoolBo> allPools, Long userId) {
        // 1. 查询当前用户直接或通过角色拥有可调整权限的投资池 ID
        Set<Long> adjustablePoolIds = queryAdjustablePoolIdsByUser(userId);
        if (adjustablePoolIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 沿各池的父级向上收集可见池 ID
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

        // 3. 按原顺序筛选可见池
        List<InvestmentPoolBo> result = new ArrayList<>();
        for (InvestmentPoolBo pool : allPools) {
            if (visiblePoolIds.contains(pool.getId())) {
                result.add(pool);
            }
        }
        return result;
    }

    /**
     * 查询普通用户可调整的池 ID
     *
     * 执行顺序：
     * 1. 查询用户角色及调库权限配置
     * 2. 匹配直接授权给用户或授权给其角色的池
     *
     * @param userId 当前用户的数字 ID
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
     * 查询 CRMW 组合和标的发行人的在池状态
     *
     * 执行顺序：
     * 1. 检查标的证券代码和凭证信息
     * 2. 分别查询凭证与标的组合的在池状态、发行人的在池状态
     * 3. 补全池全名后组装结果
     *
     * @param req 凭证与标的组合的查询条件
     * @return 组合和发行人的在池状态
     */
    public SecurityPoolStatusDto queryCrmwPoolStatus(CrmwPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        // 1. 校验并规范 CRMW 组合查询参数
        validateCrmwIdentity(req.getCrmwScode(), req.getCrmwStype());
        // 2. 分别查询凭证与标的组合的在池状态、发行人的在池状态
        SecurityPoolStatusDto dto = new SecurityPoolStatusDto();
        List<PoolStatusDto> securityCurrentPools = crmwPoolAdjustMapper.querySecurityPoolStatusList(
                req.getSecurityCode(), req.getCrmwScode(), req.getCrmwStype());
        List<PoolStatusDto> issuerCurrentPools = crmwPoolAdjustMapper.queryIssuerPoolStatusList(req.getSecurityCode());
        // 3. 加载投资池全路径名称映射
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
     * 补全在池记录的池全名
     *
     * 执行顺序：
     * 1. 按目标池 ID 查找全名，找到非空全名时替换原池名
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
     * 提交不含上传附件的 CRMW 调库申请
     *
     * 执行顺序：
     * 1. 传入空附件列表，交给带附件的提交入口处理
     *
     * @param req 凭证与标的组合的调库提交请求
     * @return 提交条数及生成的调库日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addCrmwAdjustLog(CrmwPoolAdjustSubmitReq req) {
        return addCrmwAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 提交带上传附件的 CRMW 调库申请
     *
     * 执行顺序：
     * 1. 将申请和文件交给支持原文件名的提交入口处理
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param files 本次提交上传的文件列表
     * @return 提交条数及生成的调库日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addCrmwAdjustLog(CrmwPoolAdjustSubmitReq req, List<MultipartFile> files) {
        return addCrmwAdjustLog(req, files, null);
    }

    /**
     * 提交 CRMW 调库申请并接收附件
     *
     * 执行顺序：
     * 1. 解析原文件名列表并准备本次提交的附件
     * 2. 建立批次号上下文，进入调库提交流程
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param files 本次提交上传的文件列表
     * @param originalFileNameListJson 上传文件原始名称列表的 JSON
     * @return 提交条数及生成的调库日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addCrmwAdjustLog(CrmwPoolAdjustSubmitReq req, List<MultipartFile> files,
                                            String originalFileNameListJson) {
        // 1. 解析上传文件的原始名称
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        // 2. 创建本次提交附件上下文
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId(), originalFileNameList);
        // 3. 建立本次提交的批次号上下文并提交申请
        return submitAdjustLog(req, submissionFiles, new BatchNoContext());
    }

    /**
     * 判断所选 CRMW 调库流程能否直接完成
     *
     * 执行顺序：
     * 1. 流程 ID 和编码都未配置时按无需审批处理
     * 2. 仅有流程编码时查找启用的流程定义
     * 3. 加载流程快照并判断流程是否无需人工审批
     *
     * @param flowId 选择的流程 ID
     * @param flowKey 选择的流程编码
     * @return 无需人工审批即可完成时返回 true
     */
    public boolean isDirectAdjustFlow(Long flowId, String flowKey) {
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
        // 加载启用流程版本及其审批配置
        FlowSnapshot snapshot = buildFlowSnapshot(resolvedId);
        // 判断流程快照是否无需人工审批
        return snapshot != null && isDirectFlow(snapshot);
    }

    /**
     * 提交一个 CRMW 凭证与标的证券组合的调库申请
     *
     * 批量及 Excel 复用此方法，共享附件和批次号上下文；Excel 原业务失败仅作提示，数据错误和 ABS 身份冲突仍阻断
     *
     * 执行顺序：
     * 1. 校验必填参数，普通入口检查近期重复业务申请，Excel 由导入批次防止重复提交
     * 2. 加载证券、所选评级主体、投资池及流程快照，Excel 使用已加载证券信息准备申请快照
     * 3. 检查 ABS 主体身份和关联市场项完整性，普通入口继续检查在途流程及配套互斥调出
     * 4. 处理调入项，沿用所选流程生成日志和步骤，并收集直通项
     * 5. 处理调出项，同组联动、互斥和关联项跟随手工主项的流程与批次
     * 6. 汇总调库日志 ID，保存本次申请快照，不修改证券主档
     * 7. 对直通项执行最终复核并落池，需要人工审批的申请继续保持流程中
     * 8. 返回证券代码、提交条数和日志 ID
     *
     * @param req 当前组合及其完整调库明细
     * @param submissionFiles 本次提交使用的附件上下文，可由整批共享
     * @param batchNoContext 批次号上下文，可由整批共享
     * @return 本次组合提交结果
     */
    public AdjustSubmitDto submitAdjustLog(CrmwPoolAdjustSubmitReq req,
                                           SysAttachmentService.SubmissionFiles submissionFiles,
                                           BatchNoContext batchNoContext) {
        // 1. 校验请求，普通入口继续检查近期重复申请
        validateSubmitReq(req);
        if (!EXCEL_IMPORT_ADJUST_TYPE.equals(req.getAdjustType())) {
            // 普通入口检查近期重复申请，Excel 由导入批次防止重复提交
            checkRecentDuplicateSubmit(req);
        }

        // 2. 加载本次共享数据，Excel 使用已加载数据准备快照
        SubmitSharedData shared = loadSubmitSharedData(req, batchNoContext);
        if (EXCEL_IMPORT_ADJUST_TYPE.equals(req.getAdjustType())) {
            // Excel 不编辑主档，沿用证券导入口径保存后端已加载的申请快照
            req.setSecurityInfo(shared.securityInfo);
        }
        // 3. 检查 ABS 身份、关联项完整性及普通入口的在途和互斥限制
        if (CreditBondSpecialInboundRule.isAbs(shared.securityInfo)) {
            Set<String> identityCodes = new TreeSet<>();
            identityCodes.add(req.getSecurityCode());
            for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                if (item.getSecurityCode() != null) identityCodes.add(item.getSecurityCode());
            }
            for (String identityCode : identityCodes) {
                absIdentityService.validateIdentity(identityCode, shared.securityInfo.getIssuerCode());
            }
            if (req.getSecurityInfo() == null) {
                req.setSecurityInfo(shared.securityInfo);
            }
        }
        // 同券跨市场调入必须完整提交，禁止跳过校验失败的关联券
        validateRelatedInboundItemsOnSubmit(req);
        if (!EXCEL_IMPORT_ADJUST_TYPE.equals(req.getAdjustType())) {
            // 普通入口阻断活动流程，Excel 已在校验中保留提示
            checkSubmitPendingPoolGroups(req, shared.poolMap);
        }
        if (!EXCEL_IMPORT_ADJUST_TYPE.equals(req.getAdjustType())) {
            // 普通入口要求配套互斥调出可执行，Excel 采用提示放行口径
            validateRequiredMutexOutboundOnSubmit(req, shared);
        }

        // 4. 提交调入项，并收集需要立即生效的直通日志
        List<IpAdjustLogBo> directApplyLogs = new ArrayList<>();
        // 执行调入提交并绑定本次 multipart 文件
        List<Long> inboundIds = executeInboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // 5. 提交调出项，同组配套项跟随手工主项流程
        List<Long> outboundIds = executeOutboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // 6. 汇总日志 ID，并保存本次申请快照
        List<Long> allIds = new ArrayList<>(inboundIds);
        allIds.addAll(outboundIds);

        // 保存日志对应的证券快照，不修改主档
        postSubmitProcess(req, shared, allIds);
        // 7. 快照保存后执行直通最终复核和落池
        recheckAndApplyDirectLogs(directApplyLogs);

        // 8. 返回本次组合提交条数和日志 ID
        AdjustSubmitDto dto = new AdjustSubmitDto();
        dto.setSecurityCode(req.getSecurityCode());
        dto.setSubmitCount(allIds.size());
        dto.setLogIds(allIds);
        return dto;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库提交 — 参数检查、数据准备与分组处理
    // ═══════════════════════════════════════════════════════════

    /**
     * 检查 CRMW 调库提交的必填信息
     *
     * 执行顺序：
     * 1. 检查标的代码和凭证代码、名称及类型
     * 2. 检查提交人与调库项等申请信息
     * 3. 逐项检查目标池 ID 和 CRMW 池类型
     *
     * @param req 凭证与标的组合的调库提交请求
     */
    private void validateSubmitReq(CrmwPoolAdjustSubmitReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        // 校验 CRMW 组合身份字段
        validateCrmwIdentity(req.getCrmwScode(), req.getCrmwStype());
        if (req.getCrmwScode() == null || req.getCrmwScode().isEmpty()) {
            throw new BizException("CRMW代码不能为空");
        }
        if (req.getCrmwName() == null || req.getCrmwName().isEmpty()) {
            throw new BizException("CRMW名称不能为空");
        }
        if (!CRMW_SECURITY_TYPE.equals(req.getCrmwStype())) {
            throw new BizException("CRMW证券类型必须为 crmw");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item.getAdjustMode() == null || item.getAdjustMode().isEmpty()) {
                throw new BizException("调库项的调整方向不能为空");
            }
            if (item.getTargetPoolId() == null) {
                throw new BizException("调库项的目标投资池 ID 不能为空");
            }
            if (!PoolType.CRMW.getCode().equals(item.getPoolType())) {
                throw new BizException("CRMW池调整仅支持选择 CRMW库");
            }
        }
    }

    /**
     * 检查 CRMW 凭证代码和类型
     *
     * 执行顺序：
     * 1. 检查凭证代码是否为空
     * 2. 确认类型为 crmw
     *
     * @param crmwScode CRMW 凭证代码
     * @param crmwStype CRMW 凭证的证券类型
     */
    private void validateCrmwIdentity(String crmwScode, String crmwStype) {
        if (crmwScode == null || crmwScode.trim().isEmpty()) {
            throw new BizException("CRMW代码不能为空");
        }
        if (!CRMW_SECURITY_TYPE.equals(crmwStype)) {
            throw new BizException("CRMW证券类型必须为 crmw");
        }
    }

    /**
     * 确认调入时需要配套的互斥调出已提交且可执行
     *
     * 互斥池调出是本次调入的配套动作，不能只提交调入而漏掉仍在池的组合
     *
     * 执行顺序：
     * 1. 收集本次申请中的调出池 ID
     * 2. 找出手工调入目标池的互斥池，仅处理组合当前所在的池
     * 3. 确认申请包含相应调出项，再按调出规则校验，失败时提示具体原因
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     */
    private void validateRequiredMutexOutboundOnSubmit(CrmwPoolAdjustSubmitReq req, SubmitSharedData shared) {
        // 1. 收集本次申请中的调出池 ID
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item != null && AdjustMode.OUT.getCode().equals(item.getAdjustMode())
                    && item.getTargetPoolId() != null) {
                requestOutPoolIds.add(item.getTargetPoolId());
            }
        }
        // 2. 找出手工调入目标池的互斥池，仅处理组合当前所在的池
        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item == null || !AdjustMode.IN.getCode().equals(item.getAdjustMode())
                    // 只有手工调入项需要补查配套互斥调出
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
                // 生成投资池的层级全名
                String mutexPoolName = buildPoolPath(mutexId, shared.poolMap);
                // 3. 确认申请包含相应调出项，再按调出规则校验，失败时提示具体原因
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
                int poolCurrentCount = crmwPoolAdjustMapper.queryPoolCurrentCount(mutexId);
                // 准备当前调整项执行校验所需的数据
                AdjustCheckContext ctx = buildCheckContext(fakeOut, poolCurrentCount, checkShared);
                // 按调出规则确认配套项可执行
                List<String> outFailures = checkOutConditions(ctx);
                if (outFailures != null && !outFailures.isEmpty()) {
                    // 整理配套互斥调出的失败说明
                    throw new BizException(buildMutexOutboundFailureMessage(mutexPoolName, outFailures));
                }
            }
        }
    }

    /**
     * 拦截短时间内内容相同的单笔提交
     *
     * 执行顺序：
     * 1. 提取手工项并查询当前提交人近期提交的同一组合日志
     * 2. 排序比较目标池、方向和流程，并核对原因及意见
     * 3. 全部相同时提示重复提交
     *
     * @param req 凭证与标的组合的调库提交请求
     */
    private void checkRecentDuplicateSubmit(CrmwPoolAdjustSubmitReq req) {
        List<CrmwPoolAdjustSubmitReq.AdjustItem> manualItems = req.getItems().stream()
                // 识别手工调整项，以便使用主项信息
                .filter(this::isManualSubmitItem).collect(Collectors.toList());
        List<IpAdjustLogBo> recentLogs = crmwPoolAdjustMapper.queryRecentManualAdjustLogList(
                req.getSecurityCode(), req.getCrmwScode(), req.getCrmwStype(),
                req.getAdjusterId(), DUPLICATE_SUBMIT_WINDOW_SECONDS);
        if (manualItems.isEmpty() || recentLogs == null || recentLogs.isEmpty()) {
            return;
        }
        List<String> requestKeys = manualItems.stream().map(item -> item.getTargetPoolId() + "|"
                // 将空值和纯空白统一后再比较
                + item.getAdjustMode() + "|" + item.getFlowId() + "|" + normalizeText(item.getFlowKey()))
                .sorted().collect(Collectors.toList());
        List<String> historyKeys = recentLogs.stream().map(log -> log.getTargetPoolId() + "|"
                // 将空值和纯空白统一后再比较
                + log.getAdjustMode() + "|" + log.getFlowId() + "|" + normalizeText(log.getFlowKey()))
                .sorted().collect(Collectors.toList());
        boolean sameText = recentLogs.stream().allMatch(log ->
                // 将空值和纯空白统一后再比较
                Objects.equals(normalizeText(req.getAdjustReason()), normalizeText(log.getAdjustReason()))
                        // 将空值和纯空白统一后再比较
                        && Objects.equals(normalizeText(req.getAdjustAdvice()), normalizeText(log.getAdjustAdvice())));
        if (requestKeys.equals(historyKeys) && sameText) {
            throw new BizException("调库申请已提交，请勿重复操作");
        }
    }

    /**
     * 拦截同一组合在同一池组重复发起流程
     *
     * 跨市场关联项使用各自的实际标的代码，不共用主标的的进行中状态
     *
     * 执行顺序：
     * 1. 按标的代码查询该凭证组合的进行中流程，并将目标池归到根池
     * 2. 逐项检查本次目标池所属根池，已有进行中流程时提示等待
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param poolMap 按池 ID 整理的投资池配置
     */
    private void checkSubmitPendingPoolGroups(CrmwPoolAdjustSubmitReq req, Map<Long, InvestmentPoolBo> poolMap) {
        Map<String, Set<Long>> pendingByCode = new HashMap<>();
        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 使用实际标的代码检查同一凭证下的活动流程
            String securityCode = resolveItemSecurityCode(req, item);
            Set<Long> pendingGroups = pendingByCode.get(securityCode);
            if (pendingGroups == null) {
                // 归并该组合全部活动批次占用的顶级池组
                pendingGroups = resolvePoolGroupIds(crmwPoolAdjustMapper.queryPendingManualTargetPoolIdList(
                        securityCode, req.getCrmwScode(), req.getCrmwStype(), null), poolMap);
                pendingByCode.put(securityCode, pendingGroups);
            }
            // 定位当前目标池所属池组
            Long groupId = resolveRootPoolId(item.getTargetPoolId(), poolMap);
            if (groupId != null && pendingGroups.contains(groupId)) {
                throw new BizException("CRMW 组合[" + req.getCrmwScode() + "/" + securityCode
                        + "]在目标投资池组存在进行中的调库流程，请等待流程结束后再发起调库");
            }
        }
    }

    /**
     * 确认同券跨市场调入按目标池整组提交
     *
     * 执行顺序：
     * 1. 按目标池汇总本次调入的标的代码
     * 2. 整理请求中的同券代码与主标的代码
     * 3. 逐个目标池核对是否齐全，缺项时列出遗漏代码
     *
     * @param req 凭证与标的组合的调库提交请求
     */
    private void validateRelatedInboundItemsOnSubmit(CrmwPoolAdjustSubmitReq req) {
        Map<Long, Set<String>> inboundCodes = new LinkedHashMap<>();
        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                // 使用明细实际代码，兼容未传明细代码的单券请求
                inboundCodes.computeIfAbsent(item.getTargetPoolId(), key -> new HashSet<>())
                        // 取得当前调整项的实际标的代码
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
     * 把多个投资池归并到各自的根池
     *
     * 执行顺序：
     * 1. 逐个查找池的根池 ID 并去重
     *
     * @param poolIds 待处理的投资池 ID 列表
     * @param poolMap 按池 ID 整理的投资池配置
     * @return 非空根池 ID 集合
     */
    private Set<Long> resolvePoolGroupIds(List<Long> poolIds, Map<Long, InvestmentPoolBo> poolMap) {
        Set<Long> result = new HashSet<>();
        if (poolIds != null) {
            for (Long poolId : poolIds) {
                // 查找投资池所属的根池
                Long rootId = resolveRootPoolId(poolId, poolMap);
                if (rootId != null) {
                    result.add(rootId);
                }
            }
        }
        return result;
    }

    /**
     * 查找投资池所属的根池
     *
     * 执行顺序：
     * 1. 从当前池沿父级向上查找，遇到没有父级、资料缺失或已访问的池时停止
     *
     * @param poolId 投资池 ID
     * @param poolMap 按池 ID 整理的投资池配置
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
     * 统一重复提交比较中的空文本
     *
     * 执行顺序：
     * 1. 将空值和纯空白统一成 null，其余文本保留原值
     *
     * @param value 待统一空值的文本
     * @return 用于比较的文本
     */
    private String normalizeText(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * 准备一笔独立提交所需的共用数据
     *
     * 执行顺序：
     * 1. 新建批次号上下文并读取提交所需的数据
     *
     * @param req 凭证与标的组合的调库提交请求
     * @return 本次提交共用的数据
     */
    private SubmitSharedData loadSubmitSharedData(CrmwPoolAdjustSubmitReq req) {
        // 创建本次提交独立批次号上下文
        return loadSubmitSharedData(req, new BatchNoContext());
    }

    /**
     * 准备提交阶段共用的证券、池和流程数据
     *
     * 执行顺序：
     * 1. 取得标的证券，确认不是凭证，再应用选中的评级主体
     * 2. 整理池配置、组合在池状态和池关系，并检查目标池
     * 3. 收集并加载所选流程的快照
     * 4. 汇总证券和主体的状态标记，与批次号上下文一起返回
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param batchNoContext 本次提交共用的批次号上下文
     * @return 提交阶段共用的数据
     */
    private SubmitSharedData loadSubmitSharedData(CrmwPoolAdjustSubmitReq req, BatchNoContext batchNoContext) {
        // 1. 取得标的证券并回填本次选择的评级主体
        SecurityInfoBo securityInfo = req.getSecurityInfo() != null && req.getSecurityInfo().getSecurityType() != null
                ? req.getSecurityInfo() : crmwPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }
        if (CRMW_SECURITY_TYPE.equals(securityInfo.getSecurityType())) {
            throw new BizException("调库对象不能是 CRMW 凭证");
        }
        // 按证券池调库口径校验并回填担保人 / 权益人 / 自选权益人
        SecurityPoolAdjustService.SelectedRatingCompanyData selectedRatingCompany =
                // 直接使用页面已选主体，后台没有页面数据时沿用原入口
                applySelectedRatingCompany(securityInfo, req.getGuarantorCode(),
                        req.getRightsHolderCode(), req.getSelfSelectedRightsHolderCode(), true, req.getSelectedRatingCompany());
        req.setAbsOriginatorName(selectedRatingCompany.getRightsHolderName());
        req.setCompanySelector(selectedRatingCompany.getSelfSelectedRightsHolderName());

        // 2. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        for (InvestmentPoolBo p : allPools) {
            poolMap.put(p.getId(), p);
        }
        // 校验目标池均为 CRMW 池
        validateSubmitTargetPools(req, poolMap);

        // 查询当前 CRMW 组合所在的池
        List<Long> currentPoolIdList = crmwPoolAdjustMapper.querySecurityCurrentPoolIdList(
                req.getSecurityCode(), req.getCrmwScode(), req.getCrmwStype());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList);

        // 全量投资池关系配置，构建三层嵌套 Map
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                crmwPoolAdjustMapper.queryAllPoolRelationList());

        // 3. 收集并加载所选流程的快照
        Set<Long> uniqueFlowIds = new HashSet<>();
        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
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

        // 4. 汇总状态标记和批次号上下文
        return new SubmitSharedData(
                securityInfo,
                poolMap,
                currentPoolIds,
                poolRelationMap,
                crmwPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()),
                crmwPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()),
                crmwPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()),
                flowSnapshotMap,
                batchNoContext
        );
    }

    /**
     * 确认提交项选择的实际目标池为 CRMW 池
     *
     * 执行顺序：
     * 1. 逐项按目标池 ID 查找池配置
     * 2. 池不存在或不是 CRMW 池时拒绝提交
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param poolMap 按池 ID 整理的投资池配置
     */
    private void validateSubmitTargetPools(CrmwPoolAdjustSubmitReq req, Map<Long, InvestmentPoolBo> poolMap) {
        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            InvestmentPoolBo pool = poolMap.get(item.getTargetPoolId());
            if (pool == null) {
                throw new BizException("目标投资池不存在：" + item.getTargetPoolId());
            }
            if (!PoolType.CRMW.getCode().equals(pool.getPoolType())) {
                throw new BizException("CRMW池调整仅支持选择 CRMW库：" + pool.getPoolName());
            }
        }
    }

    /**
     * 解析调整项选中的流程 ID
     *
     * 执行顺序：
     * 1. 优先使用调整项给出的流程 ID，仅有编码时查询当前启用的流程
     *
     * @param item 当前调整项
     * @return 流程 ID，未配置或未找到时为 null
     */
    private Long resolveFlowIdFromItem(CrmwPoolAdjustSubmitReq.AdjustItem item) {
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
     * 判断提交项是否是手工调整项
     *
     * 执行顺序：
     * 1. 将未填写来源标记和明确标为手工的调整项视为手工项
     *
     * @param item 当前调整项
     * @return 调整项为手工项时返回 true
     */
    private boolean isManualSubmitItem(CrmwPoolAdjustSubmitReq.AdjustItem item) {
        return item.getItemTag() == null || item.getItemTag().isEmpty() || ItemType.MANUAL.getCode().equals(item.getItemTag());
    }

    /**
     * 生成一个 CRMW 调库分组的批次号
     *
     * 执行顺序：
     * 1. 按无流程、调入和调出分别递增流水号
     * 2. 拼接 CRMW 前缀、批次时间和四位流水号
     *
     * @param manualItem 本组的手工调整项
     * @param noFlow 当前分组是否未配置流程
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 新生成的调库批次号
     */
    private String buildAdjustBatchNo(
            CrmwPoolAdjustSubmitReq.AdjustItem manualItem, boolean noFlow, SubmitSharedData shared) {
        int serial;
        if (noFlow) {
            serial = 3000 + ++shared.batchNoContext.noFlowBatchSeq;
        } else if (AdjustMode.IN.getCode().equals(manualItem.getAdjustMode())) {
            serial = 1000 + ++shared.batchNoContext.inboundBatchSeq;
        } else {
            serial = 2000 + ++shared.batchNoContext.outboundBatchSeq;
        }
        return "CRMW" + shared.batchNoContext.batchTimeText + String.format("%04d", serial);
    }

    /**
     * 取得本次提交中调库分组共用的批次号
     *
     * 执行顺序：
     * 1. 检查调整项是否有分组标识
     * 2. 优先复用该组已有批次号，否则按该组手工项生成并记录
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param item 当前调整项
     * @param noFlow 当前分组是否未配置流程
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 调整项所属分组的批次号
     */
    private String resolveAdjustBatchNo(
            CrmwPoolAdjustSubmitReq req,
            CrmwPoolAdjustSubmitReq.AdjustItem item,
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
     * 找到决定本组流程和批次号的手工项
     *
     * 联动、互斥和关联项跟随本组手工项选择的审批流程
     *
     * 执行顺序：
     * 1. 当前项是手工项时直接使用
     * 2. 否则在请求中找同组手工项，找不到时拒绝提交
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param item 当前调整项
     * @return 当前调整项所属分组的手工项
     */
    private CrmwPoolAdjustSubmitReq.AdjustItem resolveManualSubmitItem(
            CrmwPoolAdjustSubmitReq req, CrmwPoolAdjustSubmitReq.AdjustItem item) {
        // 判断提交项是否为手工调库项
        if (isManualSubmitItem(item)) {
            return item;
        }
        String groupKey = item.getAdjustGroupKey();
        for (CrmwPoolAdjustSubmitReq.AdjustItem candidate : req.getItems()) {
            // 判断提交项是否为手工调库项
            if (isManualSubmitItem(candidate) && groupKey != null && groupKey.equals(candidate.getAdjustGroupKey())) {
                return candidate;
            }
        }
        throw new BizException("未找到调库分组对应的手工调整记录");
    }

    /**
     * 加载启用流程版本及其审批配置
     *
     * 执行顺序：
     * 1. 查询流程定义并找到启用版本
     * 2. 读取该版本的节点、连线和审批配置
     * 3. 按节点和配置整理索引并组装快照
     *
     * @param flowId 选择的流程 ID
     * @return 流程快照，定义或启用版本不存在时为 null
     */
    private FlowSnapshot buildFlowSnapshot(Long flowId) {
        // 1. 查询流程定义并找到启用版本
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

        // 2. 加载该版本的节点和连线
        List<FlowNodeBo> nodes = flowMapper.queryFlowNodeListByVersionId(activeVersion.getId());
        List<FlowEdgeBo> edges = flowMapper.queryFlowEdgeListByVersionId(activeVersion.getId());

        // 节点按 DB ID 建立索引，供后续边遍历快速查找目标节点
        Map<Long, FlowNodeBo> nodeMap = new HashMap<>();
        for (FlowNodeBo node : nodes) {
            nodeMap.put(node.getId(), node);
        }

        // 3. 加载该版本的审批节点配置，按 nodeId 建立索引
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
     * 判断流程快照是否无需人工审批
     *
     * 执行顺序：
     * 1. 检查快照结构并找到开始节点
     * 2. 从开始节点检查各条非驳回路径能否自动结束
     *
     * @param snapshot 当前操作使用的快照
     * @return 所有非驳回路径都无需人工审批时返回 true
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
        // 沿非驳回连线判断流程能否自动完成
        return canCompleteWithoutHumanApproval(snapshot, startNode, startNode, new HashSet<Long>());
    }

    /**
     * 沿非驳回连线判断流程能否自动完成
     *
     * 只要有一条非驳回路径需要人工处理，就不能把整个流程当作直通流程
     *
     * 执行顺序：
     * 1. 识别结束节点，并排除无效节点和当前路径中的循环
     * 2. 检查审批节点是否为自动审批或发起人提交节点
     * 3. 逐条检查非驳回连线，缺少后续连线或任一分支不能自动完成时返回 false
     * 4. 离开当前分支时清除访问标记
     *
     * @param snapshot 当前操作使用的快照
     * @param current 正在检查的流程节点
     * @param startNode 流程开始节点
     * @param visiting 当前流程路径已访问的节点 ID
     * @return 该节点及后续非驳回路径都能自动完成时返回 true
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
                // 判断审批节点是否为发起人提交节点
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
                // 沿非驳回连线判断流程能否自动完成
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
     * 检查调整项是否满足目标池的研究报告要求
     *
     * 执行顺序：
     * 1. 没有报告限制或存在近期带报告的调入记录时直接通过
     * 2. 检查是否已上传或选取报告，要求内部报告时必须从内部报告库选取
     * 3. 校验所选报告来源是否符合要求
     *
     * @param item 当前调整项
     * @param pool 投资池配置
     * @param reportRestriction 目标池在当前调整方向的报告要求
     * @param securityCode 标的证券代码
     */
    private void checkReportRequired(CrmwPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool, String reportRestriction, String securityCode) {
        // 1. 没有报告限制或存在近期带报告的调入记录时直接通过
        if (pool == null || reportRestriction == null || reportRestriction.isEmpty() || "none".equals(reportRestriction)) {
            return;
        }
        // 同主体半年内有审批通过调入且真实关联有效报告时跳过本次报告校验
        if (securityCode != null && !securityCode.isEmpty() && crmwPoolAdjustMapper.queryHasRecentInboundWithReport(securityCode)) {
            return;
        }
        // 2. 检查是否已上传或选取报告，要求内部报告时必须从内部报告库选取
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
        // 3. 校验报告库附件真实存在、未删除且来源分类匹配
        if (item.getCreditReportSourceAttachmentIds() != null && !item.getCreditReportSourceAttachmentIds().isEmpty()) {
            sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(),
                    "internal".equals(reportRestriction));
        }
    }

    /**
     * 阻止已在目标池的 CRMW 组合重复调入
     *
     * 执行顺序：
     * 1. 按凭证、实际标的和目标池查找组合状态，已在池时提示重复调入
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param item 当前调整项
     */
    private void checkCrmwInboundCombination(CrmwPoolAdjustSubmitReq req, CrmwPoolAdjustSubmitReq.AdjustItem item) {
        String crmwScode = req.getCrmwScode();
        // 组合已在池：同一凭证+标的已在目标池，不可重复调入
        if (crmwPoolAdjustMapper.queryCrmwComboInPool(crmwScode, req.getCrmwStype(),
                // 取得当前调整项的实际标的代码
                resolveItemSecurityCode(req, item), item.getTargetPoolId())) {
            throw new BizException("信用缓释凭证代码（" + crmwScode + "）和债券代码（"
                    // 取得当前调整项的实际标的代码
                    + resolveItemSecurityCode(req, item) + "）组合已经在池！");
        }
    }

    /**
     * 阻止不在目标池的 CRMW 组合调出
     *
     * 执行顺序：
     * 1. 按凭证、实际标的和目标池查找组合状态，不在池时提示无法调出
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param item 当前调整项
     */
    private void checkCrmwOutboundCombination(CrmwPoolAdjustSubmitReq req, CrmwPoolAdjustSubmitReq.AdjustItem item) {
        String crmwScode = req.getCrmwScode();
        // 组合在池：CRMW 凭证 + 标的证券组合须在目标池，否则不可调出
        if (!crmwPoolAdjustMapper.queryCrmwComboInPool(crmwScode, req.getCrmwStype(),
                // 取得当前调整项的实际标的代码
                resolveItemSecurityCode(req, item), item.getTargetPoolId())) {
            // 取得当前调整项的实际标的代码
            throw new BizException("信用缓释凭证代码（" + crmwScode + "）和债券代码（" + resolveItemSecurityCode(req, item) + "）组合不在池！");
        }
    }

    /**
     * 在没有上传附件的情况下处理 CRMW 调入提交
     *
     * 执行顺序：
     * 1. 准备空附件和空待生效日志列表，交给调入提交方法处理
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 生成的调入日志 ID 列表
     */
    private List<Long> executeInboundSubmit(CrmwPoolAdjustSubmitReq req, SubmitSharedData shared) {
        // 创建本次提交附件上下文
        return executeInboundSubmit(req, shared,
                sysAttachmentService.createSubmissionFiles(Collections.<MultipartFile>emptyList(), req.getAdjusterId()),
                new ArrayList<IpAdjustLogBo>());
    }

    /**
     * 保存 CRMW 调入申请并发起审批
     *
     * 直接生效的日志留到统一复核通过后再更新池状态
     *
     * 执行顺序：
     * 1. 逐项检查调入报告；Excel 导入只验证已选报告来源，其他调整还检查组合是否已在池
     * 2. 按本组手工项确定流程、批次号及是否需要人工审批
     * 3. 写入日志并绑定附件，手工项有流程时初始化审批步骤
     * 4. 收集已通过且待生效的日志
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param submissionFiles 本次提交共用的上传附件数据
     * @param directApplyLogs 用于收集待直接生效日志的列表
     * @return 生成的调入日志 ID 列表
     */
    private List<Long> executeInboundSubmit(CrmwPoolAdjustSubmitReq req, SubmitSharedData shared,
                                            SysAttachmentService.SubmissionFiles submissionFiles,
                                            List<IpAdjustLogBo> directApplyLogs) {

        List<Long> generatedIds = new ArrayList<>();

        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 1. 按提交来源检查报告要求及组合在池状态
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            // 关联码项由同组手工项承担报告要求，与证券池调库一致
            if (EXCEL_IMPORT_ADJUST_TYPE.equals(req.getAdjustType())) {
                sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(), false);
            } else if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                // 普通入口按目标池要求检查报告必填
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getInReportRestriction() : null,
                        // 取得当前调整项的实际标的代码
                        resolveItemSecurityCode(req, item));
            }
            if (!EXCEL_IMPORT_ADJUST_TYPE.equals(req.getAdjustType())) {
                // 普通入口阻断已在池的组合，Excel 允许重复调入后替换旧状态
                checkCrmwInboundCombination(req, item);
            }
            // 2. 获取同组手工调库项，联动/互斥项按手工项共用流程和批次号
            CrmwPoolAdjustSubmitReq.AdjustItem manualItem = resolveManualSubmitItem(req, item);
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long flowId = resolveFlowIdFromItem(manualItem);
            FlowSnapshot snapshot = flowId != null ? shared.flowSnapshotMap.get(flowId) : null;
            boolean noFlow = flowId == null;
            // 获取或生成同组调库批次号
            String adjustBatchNo = resolveAdjustBatchNo(req, item, noFlow, shared);
            // 判断流程是否为直通流程（开始后无需人工处理即可到结束节点）
            boolean isDirect = noFlow || (snapshot != null && isDirectFlow(snapshot));

            // 3. 按是否需要人工审批写入日志和初始步骤，收集待生效日志
            if (isDirect) {
                // 直通流程先写入审批通过的日志，池状态在统一复核后更新
                IpAdjustLogBo logBo = buildAdjustLog(req, item, manualItem, shared);
                logBo.setAdjustBatchNo(adjustBatchNo);
                logBo.setAuditStatus(AuditStatus.APPROVED.getCode());
                crmwPoolAdjustMapper.addAdjustLog(logBo);
                generatedIds.add(logBo.getId());
                shared.submittedLogs.put(logBo.getId(), logBo);
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(logBo.getId(), item, submissionFiles, req.getAdjusterId());
                // 有流程定义的直通流程仍记录开始、发起、结束步骤
                if (isManualSubmitItem(item) && snapshot != null && logBo.getId() != null) {
                    // 记录开始、发起和自动步骤，遇到首个人工审批节点时创建待办
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
                crmwPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                shared.submittedLogs.put(bo.getId(), bo);
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 手工项创建初始流程步骤，联动/互斥项共用同批次流程状态
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 记录开始、发起和自动步骤，遇到首个人工审批节点时创建待办
                    boolean flowFinished = createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                    if (flowFinished) {
                        bo.setAuditStatus(AuditStatus.APPROVED.getCode());
                        crmwPoolAdjustMapper.editAdjustLogAuditStatus(bo.getId(), adjustBatchNo, AuditStatus.APPROVED.getCode());
                        bo.setAdjustLogId(bo.getId());
                        directApplyLogs.add(bo);
                    }
                }
            }
        }

        return generatedIds;
    }

    /**
     * 在没有上传附件的情况下处理 CRMW 调出提交
     *
     * 执行顺序：
     * 1. 准备空附件和空待生效日志列表，交给调出提交方法处理
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 生成的调出日志 ID 列表
     */
    private List<Long> executeOutboundSubmit(CrmwPoolAdjustSubmitReq req, SubmitSharedData shared) {
        // 创建本次提交附件上下文
        return executeOutboundSubmit(req, shared,
                sysAttachmentService.createSubmissionFiles(Collections.<MultipartFile>emptyList(), req.getAdjusterId()),
                new ArrayList<IpAdjustLogBo>());
    }

    /**
     * 保存 CRMW 调出申请并发起审批
     *
     * 直接生效的日志留到统一复核通过后再更新池状态
     *
     * 执行顺序：
     * 1. 逐项检查调出报告；Excel 导入只验证已选报告来源，其他调整还检查组合是否在池
     * 2. 按本组手工项确定流程、批次号及是否需要人工审批
     * 3. 写入日志并绑定附件，手工项有流程时初始化审批步骤
     * 4. 收集已通过且待生效的日志
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param submissionFiles 本次提交共用的上传附件数据
     * @param directApplyLogs 用于收集待直接生效日志的列表
     * @return 生成的调出日志 ID 列表
     */
    private List<Long> executeOutboundSubmit(CrmwPoolAdjustSubmitReq req, SubmitSharedData shared,
                                             SysAttachmentService.SubmissionFiles submissionFiles,
                                             List<IpAdjustLogBo> directApplyLogs) {

        List<Long> generatedIds = new ArrayList<>();

        for (CrmwPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 1. 按提交来源检查报告要求及组合在池状态
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            // 关联码项由同组手工项承担报告要求，与证券池调库一致
            if (EXCEL_IMPORT_ADJUST_TYPE.equals(req.getAdjustType())) {
                sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(), false);
            } else if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                // 普通入口按目标池要求检查报告必填
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getOutReportRestriction() : null,
                        // 取得当前调整项的实际标的代码
                        resolveItemSecurityCode(req, item));
            }
            if (!EXCEL_IMPORT_ADJUST_TYPE.equals(req.getAdjustType())) {
                // 普通入口要求组合在池，Excel 调出已不在池的组合按完成处理
                checkCrmwOutboundCombination(req, item);
            }
            // 2. 获取同组手工调库项，联动/互斥项按手工项共用流程和批次号
            CrmwPoolAdjustSubmitReq.AdjustItem manualItem = resolveManualSubmitItem(req, item);
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long flowId = resolveFlowIdFromItem(manualItem);
            FlowSnapshot snapshot = flowId != null ? shared.flowSnapshotMap.get(flowId) : null;
            boolean noFlow = flowId == null;
            // 获取或生成同组调库批次号
            String adjustBatchNo = resolveAdjustBatchNo(req, item, noFlow, shared);
            // 判断流程是否为直通流程（开始后无需人工处理即可到结束节点）
            boolean isDirect = noFlow || (snapshot != null && isDirectFlow(snapshot));

            // 3. 按是否需要人工审批写入日志和初始步骤，收集待生效日志
            if (isDirect) {
                // 直通流程先写入审批通过的日志，池状态在统一复核后更新
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem, shared);
                bo.setAdjustBatchNo(adjustBatchNo);
                bo.setAuditStatus(AuditStatus.APPROVED.getCode());
                crmwPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                shared.submittedLogs.put(bo.getId(), bo);
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 有流程定义的直通流程仍记录开始、发起、结束步骤
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 记录开始、发起和自动步骤，遇到首个人工审批节点时创建待办
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
                crmwPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                shared.submittedLogs.put(bo.getId(), bo);
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 手工项创建初始流程步骤，联动/互斥项共用同批次流程状态
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 记录开始、发起和自动步骤，遇到首个人工审批节点时创建待办
                    boolean flowFinished = createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                    if (flowFinished) {
                        crmwPoolAdjustMapper.editAdjustLogAuditStatus(bo.getId(), adjustBatchNo, AuditStatus.APPROVED.getCode());
                        directApplyLogs.add(bo);
                    }
                }
            }
        }

        return generatedIds;
    }

    /**
     * 在提交后保存审批使用的证券快照
     *
     * 执行顺序：
     * 1. 请求带证券资料时，将可编辑字段与已加载的主数据合并
     * 2. 以本次选中的主体和担保人信息更新合并结果
     * 3. 用合并结果保存各条调库日志的快照
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param logIds 本次提交生成的调库日志 ID
     */
    private void postSubmitProcess(CrmwPoolAdjustSubmitReq req, SubmitSharedData shared, List<Long> logIds) {
        if (req.getSecurityInfo() == null) {
            return;
        }
        // 1. 合并主档当前值与前端传入字段，作为本笔快照内容（不回写主档）
        SecurityInfoBo mergedSecurityInfo = buildMergedSecurityInfo(req.getSecurityCode(), req.getSecurityInfo(), shared.securityInfo);
        // 2. 主体与评级字段按本次校验后的结果原样落快照，不在下游再按证券类型挑字段
        mergedSecurityInfo.setIssuer(shared.securityInfo.getIssuer());
        mergedSecurityInfo.setIssuerCode(shared.securityInfo.getIssuerCode());
        mergedSecurityInfo.setInnerIssuerRating(shared.securityInfo.getInnerIssuerRating());
        mergedSecurityInfo.setRatingBondissuer(shared.securityInfo.getRatingBondissuer());
        mergedSecurityInfo.setRatingBondissuerAgency(shared.securityInfo.getRatingBondissuerAgency());
        mergedSecurityInfo.setGuarantor(shared.securityInfo.getGuarantor());
        mergedSecurityInfo.setGuarantorId(shared.securityInfo.getGuarantorId());
        mergedSecurityInfo.setInnerGuarantorRating(shared.securityInfo.getInnerGuarantorRating());
        req.setSecurityInfo(mergedSecurityInfo);
        // 3. 按调库日志落 CRMW 证券信息快照
        saveAdjustSecuritySnapshotsCrmw(req, logIds, shared);
    }

    /**
     * 为各条 CRMW 调库日志保存证券快照
     *
     * 执行顺序：
     * 1. 确认有日志 ID 和证券资料后，为每条日志生成快照
     * 2. 已取得日志时用其标的代码、简称和类型覆盖快照，保证跨市场记录使用实际标的
     * 3. 写入证券快照
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param logIds 本次提交生成的调库日志 ID
     * @param shared 当前申请共用的证券、投资池和状态数据
     */
    private void saveAdjustSecuritySnapshotsCrmw(CrmwPoolAdjustSubmitReq req, List<Long> logIds,
                                                SubmitSharedData shared) {
        if (logIds == null || logIds.isEmpty() || req.getSecurityInfo() == null) {
            return;
        }
        Date now = new Date();
        for (Long logId : logIds) {
            if (logId == null) {
                continue;
            }
            // 从合并后的标的证券信息拷贝同名字段，并补齐 CRMW 凭证四元组与快照关联字段
            AdjustSecuritySnapshotCrmwBo snapshot = buildAdjustSecuritySnapshotCrmw(req, logId, now);
            IpAdjustLogBo log = shared.submittedLogs.get(logId);
            if (log != null) {
                snapshot.setWindCode(log.getSecurityCode());
                snapshot.setShortName(log.getSecurityShortName());
                snapshot.setSecurityType(log.getSecurityType());
            }
            crmwPoolAdjustMapper.addAdjustSecuritySnapshotCrmw(snapshot);
        }
    }

    /**
     * 组装一条 CRMW 调库证券快照
     *
     * 执行顺序：
     * 1. 复制本次提交的证券字段
     * 2. 补充日志、提交人、凭证、ABS 主体和时间信息
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param adjustLogId 调库日志 ID
     * @param submitTime 本次提交时间
     * @return 待保存的证券快照
     */
    private AdjustSecuritySnapshotCrmwBo buildAdjustSecuritySnapshotCrmw(CrmwPoolAdjustSubmitReq req,
                                                                         Long adjustLogId, Date submitTime) {
        AdjustSecuritySnapshotCrmwBo snapshot = new AdjustSecuritySnapshotCrmwBo();
        BeanUtils.copyProperties(req.getSecurityInfo(), snapshot);
        snapshot.setAdjustLogId(adjustLogId);
        snapshot.setSubmitterId(req.getAdjusterId());
        snapshot.setSubmitTime(submitTime);
        snapshot.setCrmwName(req.getCrmwName());
        snapshot.setCrmwScode(req.getCrmwScode());
        // 提交请求未带 mktcode，快照侧预留列允许为空
        snapshot.setCrmwStype(req.getCrmwStype());
        snapshot.setAbsOriginatorName(req.getAbsOriginatorName());
        snapshot.setCompanySelector(req.getCompanySelector());
        snapshot.setIsDeleted(0);
        snapshot.setCrteTime(submitTime);
        snapshot.setUpdtTime(submitTime);
        return snapshot;
    }

    /**
     * 加载主数据并合并本次填写的证券信息
     *
     * 执行顺序：
     * 1. 不传已加载的资料，交给合并方法查询主数据并覆盖可编辑字段
     *
     * @param securityCode 标的证券代码
     * @param changedField 本次填写的可编辑证券字段
     * @return 合并后的证券资料
     */
    private SecurityInfoBo buildMergedSecurityInfo(String securityCode, SecurityInfoBo changedField) {
        // 独立调用没有共享主档时读取一次，再交给统一合并方法
        return buildMergedSecurityInfo(securityCode, changedField, null);
    }

    /**
     * 使用本次已有证券信息构建申请快照，避免保存时重新查询主档
     * 执行顺序：
     * 1. 优先取本次共享数据，没有共享数据的独立调用才读取主档
     * 2. 复制已有信息并覆盖页面编辑字段，不修改共享对象
     * @param securityCode 证券代码
     * @param changedField 页面编辑字段
     * @param loaded 本次已加载的证券信息
     * @return 本次完整快照
     */
    private SecurityInfoBo buildMergedSecurityInfo(String securityCode, SecurityInfoBo changedField, SecurityInfoBo loaded) {
        SecurityInfoBo source = loaded == null ? crmwPoolAdjustMapper.querySecurityBoByCode(securityCode) : loaded;
        if (source == null) throw new BizException("证券不存在");
        SecurityInfoBo current = new SecurityInfoBo();
        BeanUtils.copyProperties(source, current);
        // 复用本次已加载数据覆盖页面编辑值
        mergeSecurityInfo(current, changedField);
        return current;
    }

    /**
     * 把本次填写的可编辑字段写入证券副本
     *
     * 执行顺序：
     * 1. 覆盖证券名称、期限、评级及分析等允许在调库时编辑的字段
     *
     * @param current 待写入编辑字段的证券副本
     * @param changedField 本次填写的可编辑证券字段
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
     * 查询指定 CRMW 组合的调库日志
     *
     * 执行顺序：
     * 1. 检查证券代码和 CRMW 凭证信息
     * 2. 按组合及批次号查询日志
     * 3. 补全目标池全名并转换为日志列表
     *
     * @param req 组合代码和可选的调库批次号
     * @return CRMW 调库日志列表
     */
    public List<AdjustLogDto> queryCrmwAdjustLogList(CrmwPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        // 校验 CRMW 组合查询参数
        validateCrmwIdentity(req.getCrmwScode(), req.getCrmwStype());
        List<IpAdjustLogBo> logs = crmwPoolAdjustMapper.queryAdjustLogList(
                req.getSecurityCode(), req.getCrmwScode(), req.getCrmwStype(),
                req.getAdjustBatchNo());
        if (logs.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询投资池全路径名称，用于调库记录展示
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();

        List<AdjustLogDto> result = new ArrayList<>();
        for (IpAdjustLogBo log : logs) {
            AdjustLogDto dto = new AdjustLogDto();
            dto.setId(log.getId());
            dto.setAdjustBatchNo(log.getAdjustBatchNo());
            dto.setSecurityCode(log.getSecurityCode());
            dto.setSecurityShortName(log.getSecurityShortName());
            dto.setCrmwScode(log.getCrmwScode());
            dto.setCrmwName(log.getCrmwName());
            dto.setPoolPath(poolFullNameMap.get(log.getTargetPoolId()));
            dto.setAdjustType(log.getAdjustType());
            dto.setAdjustMode(log.getAdjustMode());
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
     * 查询 CRMW 调库的审批步骤
     *
     * 执行顺序：
     * 1. 按日志 ID 或调库批次号查询审批步骤，未指定查询标识时返回空列表
     * 2. 将步骤记录转换成包含节点、处理人、动作和时间的展示数据
     *
     * @param req 调库日志 ID 或调库批次号
     * @return 审批步骤列表
     */
    public List<IpAdjustStepDto> queryCrmwAdjustStepList(CrmwPoolAdjustReq req) {
        Long adjustLogId = req.getAdjustLogId();
        String adjustBatchNo = req.getAdjustBatchNo();
        if (adjustLogId == null && (adjustBatchNo == null || adjustBatchNo.isEmpty())) {
            return Collections.emptyList();
        }
        List<IpAdjustStepBo> steps = crmwPoolAdjustMapper.queryAdjustStepByBatchList(adjustLogId, adjustBatchNo);
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
     * 校验 CRMW 单笔调库申请并匹配可选流程
     *
     * 执行顺序：
     * 1. 调用共用校验入口，执行原业务规则、联动互斥及关联市场展开
     * 2. 保留业务失败阻断和原单笔流程候选，不追加批量流程
     *
     * @param req 凭证、标的证券及目标池调整明细
     * @return 单笔校验结果及流程候选
     */
    public AdjustCheckDto checkCrmwAdjust(AdjustCheckReq req) {
        // 1. 单笔继续使用原业务阻断和流程候选
        return checkAdjustInternal(req, false, false);
    }

    /**
     * 校验 CRMW 批量申请中的一个组合并追加批量流程
     *
     * 执行顺序：
     * 1. 通过共用入口执行完整单笔校验和关联市场展开，业务失败仍阻断
     * 2. 保留单笔候选，追加目标池已配置的批量流程并推荐，未配置时保留原推荐
     *
     * @param req 批量申请中的一个组合及其调整明细
     * @return 校验结果和单笔、批量流程候选
     */
    public AdjustCheckDto checkBatchAdjust(AdjustCheckReq req) {
        // 1. 批量仅追加流程，不放行业务失败
        return checkAdjustInternal(req, true, false);
    }

    /**
     * 校验 CRMW Excel 导入组合，将原业务失败保留为调整提示
     *
     * 执行顺序：
     * 1. 通过共用入口执行完整组合规则及联动、互斥、关联市场展开
     * 2. 允许可识别的整组数据提交，保留原原因，同时继续阻断数据错误及 ABS 身份冲突
     * 3. 保留单笔候选，追加已配置的批量流程并推荐
     *
     * @param req 导入行对应的凭证、标的和调整明细
     * @return 调整提示、可提交状态及流程候选
     */
    public AdjustCheckDto checkExcelAdjust(AdjustCheckReq req) {
        // 1. Excel 只改变业务失败的处理方式
        return checkAdjustInternal(req, true, true);
    }

    /**
     * 按单笔、批量或 Excel 入口要求执行 CRMW 共用校验
     *
     * 执行顺序：
     * 1. 校验请求参数，加载证券、投资池、组合在池状态和评级信息
     * 2. 登记手工选择项，执行调入和调出规则，并生成联动及互斥项
     * 3. 回填实际凭证与标的身份，再展开标的关联市场项并执行逐码校验
     * 4. Excel 将可识别组的业务失败转为提示，再统一检查 ABS 身份冲突
     * 5. 按手工主项匹配单笔流程，批量及 Excel 额外追加已配置的批量候选
     * 6. 组装逐项结果和汇总候选，回填推荐流程后返回
     *
     * @param req 组合及其调整明细
     * @param includeBatchFlows 是否追加目标池已配置的批量流程
     * @param excelWarningsOnly 是否将 Excel 原业务失败保留为可提交提示
     * @return 当前入口的完整校验结果
     */
    private AdjustCheckDto checkAdjustInternal(AdjustCheckReq req, boolean includeBatchFlows, boolean excelWarningsOnly) {

        // 1. 校验请求并加载本次组合共用数据
        validateCheckAdjustReq(req);

        // 加载证券、投资池和组合当前状态
        AdjustSharedData shared = loadSharedData(req);
        shared.setBatchFlowOptionsEnabled(includeBatchFlows);

        // 2. 登记手工项后执行调入、调出校验，避免联动和互斥项重复生成
        Set<String> coveredKeys = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            coveredKeys.add(item.getTargetPoolId() + "_" + item.getAdjustMode());
        }

        // 执行调入校验，补充联动调入和互斥调出项
        List<AdjustCheckDto.CheckResultItem> resultItems = new ArrayList<>(
                // 执行调入校验并补充联动、互斥调库项
                executeInAdjustCheck(req, shared, coveredKeys));

        // 执行调出校验，补充联动调出项
        resultItems.addAll(executeOutAdjustCheck(req, shared, coveredKeys));

        // 3. 回填实际组合身份，再按标的同 ISIN 代码展开关联市场项
        fillCheckResultIdentity(req, shared, resultItems);

        // 按标的债券同 ISIN 代码扩批，凭证代码保持不变
        expandRelatedMarketCodeItems(req, shared, resultItems);

        // 4. Excel 转换业务提示后，再检查 ABS 身份，身份冲突不能放行
        if (excelWarningsOnly) {
            Set<String> invalidGroups = new HashSet<>();
            for (AdjustCheckDto.CheckResultItem item : resultItems) {
                if (!shared.getKnownSecurityCodes().contains(item.getSecurityCode())
                        || !shared.getPoolMap().containsKey(item.getTargetPoolId())) {
                    invalidGroups.add(item.getAdjustGroupKey());
                }
            }
            for (AdjustCheckDto.CheckResultItem item : resultItems) {
                item.setCanAdjust(!invalidGroups.contains(item.getAdjustGroupKey()));
            }
        }

        // 身份冲突作为不可调整原因展示，Excel 提示放行不能覆盖此结果
        applyAbsIdentityCheckFailures(shared.getSecurityInfo(), resultItems);

        // 5. 匹配单笔候选，按入口追加已配置的批量流程
        List<AdjustCheckDto.FlowOption> flowOptions = resolveAdjustFlowOptions(req, shared, resultItems);

        // 6. 组装逐项结果、汇总候选和推荐流程后返回
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

    /**
     * 将标的 ABS 主体冲突写入 CRMW 调整说明，不提前中断下一步校验
     * 执行顺序：
     * 1. 按组汇总实际标的代码，复用统一身份校验
     * 2. 保留原失败原因，冲突组及其关联项统一标记不可调整
     * @param security 已回填本次主体的标的证券
     * @param items 完整校验结果
     */
    private void applyAbsIdentityCheckFailures(SecurityInfoBo security, List<AdjustCheckDto.CheckResultItem> items) {
        if (!CreditBondSpecialInboundRule.isAbs(security)) return;
        Map<String, Set<String>> codesByGroup = new HashMap<>();
        for (AdjustCheckDto.CheckResultItem item : items) {
            String code = item.getSecurityCode() == null ? security.getWindCode() : item.getSecurityCode();
            codesByGroup.computeIfAbsent(item.getAdjustGroupKey(), key -> new HashSet<>()).add(code);
        }
        Map<String, Set<String>> failures = absIdentityService.queryIdentityGroupFailures(security, codesByGroup);
        for (AdjustCheckDto.CheckResultItem item : items) {
            Set<String> groupFailures = failures.get(item.getAdjustGroupKey());
            if (groupFailures == null) continue;
            Set<String> reasons = new LinkedHashSet<>();
            if (item.getFailReasons() != null) reasons.addAll(item.getFailReasons());
            reasons.addAll(groupFailures);
            item.setFailReasons(new ArrayList<>(reasons));
            item.setCanAdjust(false);
            item.setFlowOptions(Collections.emptyList());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 原规则、关联展开与流程匹配
    // ═══════════════════════════════════════════════════════════

    /**
     * 检查 CRMW 调库校验请求
     *
     * 执行顺序：
     * 1. 检查标的代码和凭证信息
     * 2. 确认请求至少包含一条调整项，并逐项检查目标池 ID 和池类型
     *
     * @param req 凭证与标的组合及其目标池的检查请求
     */
    private void validateCheckAdjustReq(AdjustCheckReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        // 校验 CRMW 组合身份字段
        validateCrmwIdentity(req.getCrmwScode(), req.getCrmwStype());
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (item.getTargetPoolId() == null) {
                throw new BizException("调库项的目标投资池 ID 不能为空");
            }
            if (!PoolType.CRMW.getCode().equals(item.getPoolType())) {
                throw new BizException("CRMW池调整仅支持选择 CRMW库");
            }
        }
    }

    /**
     * 集中加载一次组合校验需要的证券、投资池和当前状态
     *
     * 执行顺序：
     * 1. 读取标的主档并校验证券类型，按本次选择回填评级主体
     * 2. 加载投资池并检查手工目标池类型，再读取组合当前所在池和池关系
     * 3. 汇总本次调入、调出目标池，建立共享对象并记录已识别的主券代码
     * 4. 补充在途池组、观察池及评级变化标志，返回本次共享数据
     *
     * @param req 含凭证、标的及手工调整项的校验请求
     * @return 供后续规则和流程匹配复用的共享数据
     */
    private AdjustSharedData loadSharedData(AdjustCheckReq req) {
        // 1. 读取标的主档，校验类型并回填本次所选评级主体
        SecurityInfoBo securityInfo = req.getSecurityInfo() != null && req.getSecurityInfo().getSecurityType() != null
                ? req.getSecurityInfo() : crmwPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }
        if (CRMW_SECURITY_TYPE.equals(securityInfo.getSecurityType())) {
            throw new BizException("调库对象不能是 CRMW 凭证");
        }
        // 校验口径与证券池调库页面一致
        applySelectedRatingCompany(securityInfo, req.getGuarantorCode(),
                req.getRightsHolderCode(), req.getSelfSelectedRightsHolderCode(), true, req.getSelectedRatingCompany());
        // 2. 加载并校验目标池，读取组合当前所在池及池关系
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        for (InvestmentPoolBo p : allPools) {
            poolMap.put(p.getId(), p);
        }
        // 校验目标池均为 CRMW 池
        validateCheckTargetPools(req, poolMap);

        // 查询当前 CRMW 组合所在的池
        List<Long> currentPoolIdList = crmwPoolAdjustMapper.querySecurityCurrentPoolIdList(
                req.getSecurityCode(), req.getCrmwScode(), req.getCrmwStype());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList);

        // 全量投资池关系配置，构建三层嵌套 Map（poolId → relationType → 关联池列表）
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                crmwPoolAdjustMapper.queryAllPoolRelationList());

        // 3. 汇总请求中的调入和调出目标池，建立共享数据对象
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
        shared.getKnownSecurityCodes().add(securityInfo.getWindCode());
        shared.setCrmwScode(req.getCrmwScode());
        shared.setCrmwStype(req.getCrmwStype());
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(currentPoolIds);
        shared.setPoolRelationMap(poolRelationMap);
        // 4. 补充在途池组、观察池及评级变化标志后返回
        shared.setHasPendingProcess(crmwPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()));
        shared.setPendingProcessNodeLabel(crmwPoolAdjustMapper.querySecurityPendingProcessNodeLabel(req.getSecurityCode()));
        // 将目标池列表转换为顶级投资池组 ID 集合
        shared.setPendingPoolGroupIds(resolvePoolGroupIds(crmwPoolAdjustMapper.queryPendingManualTargetPoolIdList(
                req.getSecurityCode(), req.getCrmwScode(), req.getCrmwStype(), null), poolMap));
        // 观察池标志：老项目用于跳过债大库校验（ASTRICTPOOLS→openrule=1/isgcc=true），
        // 当前项目主体债入库规则未落地（P2 阻塞），暂仅加载不使用，待主体债入库规则接入后启用
        shared.setSecurityInObservePool(crmwPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()));
        shared.setIssuerInObservePool(crmwPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()));
        // 评级下调：主体与展望按发行人评级判定，担保人按本次所选担保人代码判定
        shared.setIssuerRatingDowngraded(ratingDowngradeChecker.isIssuerDowngraded(securityInfo));
        shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(securityInfo));
        shared.setGuarantorRatingDowngraded(ratingDowngradeChecker.isGuarantorDowngraded(req.getGuarantorCode()));
        shared.setRequestInPoolIds(requestInPoolIds);
        shared.setRequestOutPoolIds(requestOutPoolIds);
        return shared;
    }

    /**
     * 按本次选择回填证券的评级主体信息
     *
     * 执行顺序：
     * 1. 将证券、担保人和受益主体选择交给证券池调整服务处理
     *
     * @param securityInfo 标的证券资料
     * @param guarantorCode 本次选中的担保人代码
     * @param rightsHolderCode 本次选中的权益人代码
     * @param selfSelectedRightsHolderCode 本次选择的自选权益人代码
     * @param requireAbsSelection 是否要求选择 ABS 评级主体
     * @return 实际选中的评级主体信息
     */
    private SecurityPoolAdjustService.SelectedRatingCompanyData applySelectedRatingCompany(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode,
            String selfSelectedRightsHolderCode, boolean requireAbsSelection) {
        return securityPoolAdjustService.applySelectedRatingCompany(securityInfo, guarantorCode,
                rightsHolderCode, selfSelectedRightsHolderCode, requireAbsSelection);
    }

    /**
     * 直接使用页面已选主体，后台没有页面数据时沿用原入口
     * 执行顺序：
     * 1. 有页面选择时透传，不查询评级候选
     * @param securityInfo 当前标的证券
     * @param guarantorCode 担保人代码
     * @param rightsHolderCode 权益人代码
     * @param selfSelectedRightsHolderCode 自选主体代码
     * @param requireAbsSelection 是否必须选择主体
     * @param selected 页面已选主体
     * @return 本次快照主体名称
     */
    private SecurityPoolAdjustService.SelectedRatingCompanyData applySelectedRatingCompany(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode,
            String selfSelectedRightsHolderCode, boolean requireAbsSelection, RatingCompanySelectionReq selected) {
        if (selected == null) {
            // 后台及旧调用没有页面选择对象时沿用已有入口
            return applySelectedRatingCompany(securityInfo, guarantorCode, rightsHolderCode,
                    selfSelectedRightsHolderCode, requireAbsSelection);
        }
        return securityPoolAdjustService.applySelectedRatingCompany(securityInfo, guarantorCode,
                rightsHolderCode, selfSelectedRightsHolderCode, requireAbsSelection, selected);
    }

    /**
     * 确认校验项选择的实际目标池为 CRMW 池
     *
     * 执行顺序：
     * 1. 逐项按目标池 ID 查找池配置
     * 2. 池不存在或不是 CRMW 池时停止校验
     *
     * @param req 凭证与标的组合及其目标池的检查请求
     * @param poolMap 按池 ID 整理的投资池配置
     */
    private void validateCheckTargetPools(AdjustCheckReq req, Map<Long, InvestmentPoolBo> poolMap) {
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            InvestmentPoolBo pool = poolMap.get(item.getTargetPoolId());
            if (pool == null) {
                throw new BizException("目标投资池不存在：" + item.getTargetPoolId());
            }
            if (!PoolType.CRMW.getCode().equals(pool.getPoolType())) {
                throw new BizException("CRMW池调整仅支持选择 CRMW库：" + pool.getPoolName());
            }
        }
    }

    /**
     * 检查手工调入及其联动和互斥调整项
     *
     * 执行顺序：
     * 1. 逐项校验手工调入并保存失败原因和提示
     * 2. 补充尚未覆盖的联动调入项，并让联动项继承手工项失败
     * 3. 为组合当前所在的互斥池补充调出项
     * 4. 配套互斥调出失败时将原因回写手工调入项
     *
     * @param req 凭证与标的组合及其目标池的检查请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param coveredKeys 已覆盖的目标池和方向标识，用于避免重复生成自动项
     * @return 手工调入、联动调入和互斥调出结果
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

            // 1. 手动调入项：前置校验 + 调入校验
            int poolCurrentCount = crmwPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
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

            // 2. 联动调入：目标池调入时，其联动池（in_linked）需同步调入
            List<Long> inLinkage = relations.get(RelationType.IN_LINKED.getCode());
            if (inLinkage != null) {
                for (Long linkedId : inLinkage) {
                    // coveredKeys.add 返回 true 表示该 key 是首次出现，生成自动项
                    if (coveredKeys.add(linkedId + "_调入")) {
                        // 按自动项的目标池和方向执行校验，并保留所属手工分组
                        AdjustCheckDto.CheckResultItem autoItem =
                                // 检查联动或互斥调整并生成结果
                                buildAutoResultItem(linkedId, AdjustMode.IN.getCode(), ItemType.LINKAGE.getCode(), adjustGroupKey, shared);
                        // 关联手工项失败时阻断自动联动项
                        inheritManualItemFailure(autoItem, resultItem);
                        results.add(autoItem);
                    }
                }
            }

            // 3. 组合已在调入互斥池时，追加该池的调出校验项
            List<AdjustCheckDto.CheckResultItem> groupMutexItems = new ArrayList<>();
            List<Long> inMutex = relations.get(RelationType.IN_MUTEX.getCode());
            if (inMutex != null) {
                for (Long mutexId : inMutex) {
                    // 证券不在该互斥池中则无需生成配套调出项
                    if (!shared.getCurrentPoolIds().contains(mutexId)) {
                        continue;
                    }
                    if (coveredKeys.add(mutexId + "_调出")) {
                        // 按自动项的目标池和方向执行校验，并保留所属手工分组
                        AdjustCheckDto.CheckResultItem autoItem =
                                // 检查联动或互斥调整并生成结果
                                buildAutoResultItem(mutexId, AdjustMode.OUT.getCode(), ItemType.MUTEX.getCode(), adjustGroupKey, shared);
                        // 关联手工项失败时阻断自动互斥项
                        inheritManualItemFailure(autoItem, resultItem);
                        groupMutexItems.add(autoItem);
                        results.add(autoItem);
                    }
                }
            }
            // 4. 互斥调出失败时反阻断同组手工调入，避免只入目标池导致双池
            propagateMutexFailureToManual(resultItem, groupMutexItems);
        }

        return results;
    }

    /**
     * 检查手工调出及其联动调整项
     *
     * 执行顺序：
     * 1. 逐项检查调出规则和组合是否在目标池
     * 2. 保存手工调出的失败原因和提示
     * 3. 补充尚未覆盖的联动调出项，并让联动项继承手工项失败
     *
     * @param req 凭证与标的组合及其目标池的检查请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param coveredKeys 已覆盖的目标池和方向标识，用于避免重复生成自动项
     * @return 手工调出和联动调出结果
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

            // 1. 手动调出项：前置校验 + 调出校验
            int poolCurrentCount = crmwPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
            // 构建调库校验上下文
            AdjustCheckContext ctx = buildCheckContext(item, poolCurrentCount, shared);
            List<String> failures = checkOutConditions(ctx);
            if (!crmwPoolAdjustMapper.queryCrmwComboInPool(req.getCrmwScode(),
                    req.getCrmwStype(), req.getSecurityCode(), item.getTargetPoolId())) {
                failures.add("信用缓释凭证与标的证券组合不在目标投资池中");
            }

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

            // 2. 联动调出：目标池调出时，其联动池（out_linked）需同步调出
            Map<String, List<Long>> relations = shared.getPoolRelationMap().getOrDefault(
                    item.getTargetPoolId(), Collections.emptyMap());
            List<Long> outLinkage = relations.get(RelationType.OUT_LINKED.getCode());
            if (outLinkage != null) {
                for (Long linkedId : outLinkage) {
                    if (coveredKeys.add(linkedId + "_调出")) {
                        // 按自动项的目标池和方向执行校验，并保留所属手工分组
                        AdjustCheckDto.CheckResultItem autoItem =
                                // 检查联动或互斥调整并生成结果
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
     * 为可调整的手工主项匹配流程，并汇总本次候选
     *
     * 联动、互斥和关联项跟随同组主项，不单独选择流程
     *
     * 执行顺序：
     * 1. 准备候选集合，无校验结果时直接返回空集合
     * 2. 逐项匹配单笔流程，批量及 Excel 为可调整手工项追加已配置的批量流程
     * 3. 将候选写回各行，并按唯一键汇总去重后返回
     *
     * @param req 当前组合校验请求
     * @param shared 已加载的证券、池配置和入口标志
     * @param resultItems 原规则和身份检查后的完整校验项
     * @return 本次调库汇总流程候选
     */
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptions(
            AdjustCheckReq req, AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> resultItems) {

        // 1. 准备汇总集合，无校验结果时直接返回
        List<AdjustCheckDto.FlowOption> allOptions = new ArrayList<>();
        Set<String> optionKeys = new HashSet<>();
        if (resultItems == null || resultItems.isEmpty()) {
            return allOptions;
        }

        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            // 2. 逐项匹配单笔候选，并按入口追加目标池批量流程
            List<AdjustCheckDto.FlowOption> itemOptions = resolveAdjustFlowOptionsForItem(req, shared, item);
            if (shared.isBatchFlowOptionsEnabled() && item.isCanAdjust()
                    && ItemType.MANUAL.getCode().equals(item.getItemTag())) {
                itemOptions = CrmwBatchAdjustFlowOptionHelper.appendConfiguredBatchFlow(
                        itemOptions, shared.getPoolMap().get(item.getTargetPoolId()), item.getAdjustMode());
            }
            // 3. 回填当前行候选，再按唯一键汇总去重
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
     * 为通过检查的手工项匹配可选审批流程
     *
     * 执行顺序：
     * 1. 跳过不能调整的结果、自动项和目标池缺失的结果
     * 2. 调出使用池配置的默认调出流程
     * 3. 调入优先匹配特殊流程，非信用债池使用池配置的默认调入流程
     * 4. 已在信用债池时按层级匹配升降级，无法判断时回到默认调入流程
     * 5. 其余情况比较白名单、简易和默认调入流程，标出推荐项
     *
     * @param req 凭证与标的组合及其目标池的检查请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param item 当前调整项
     * @return 当前手工项的审批流程候选列表
     */
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptionsForItem(
            AdjustCheckReq req, AdjustSharedData shared, AdjustCheckDto.CheckResultItem item) {

        // 1. 只为通过校验的手工项匹配流程
        if (item == null || !item.isCanAdjust() || !ItemType.MANUAL.getCode().equals(item.getItemTag())) {
            return new ArrayList<>();
        }
        InvestmentPoolBo targetPool = shared.getPoolMap().get(item.getTargetPoolId());
        if (targetPool == null) {
            return new ArrayList<>();
        }

        // 2. 手工调出：使用投资池定义的标准调出流程
        if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
            // 解析流程名称：优先使用配置名称
            String flowName = resolveFlowName(targetPool.getOutFlowName(), "调出流程");
            // 判断池配置是否为空流程
            boolean noApproval = isNoApprovalFlow(targetPool.getOutFlowId(), targetPool.getOutFlowKey());
            FlowOptionParam p = new FlowOptionParam();
            p.setFlowType(FlowType.NORMAL_OUTBOUND.getCode());
            p.setFlowName(flowName);
            p.setFlowId(targetPool.getOutFlowId());
            p.setFlowKey(targetPool.getOutFlowKey());
            p.setRecommended(true);
            p.setMatched(true);
            p.setSelectable(true);
            // 生成目标池默认流程的匹配说明
            p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                    null, flowName, noApproval, "默认调出流程")));
            p.setUnmatchReasons(new ArrayList<>());
            // 构建调出流程候选项
            return Collections.singletonList(buildPoolFlowOption(p));
        }

        // 3. 调入优先匹配特殊流程，非信用债池使用默认调入流程
        AdjustCheckDto.FlowOption specialOption = resolveSpecialInboundFlowOption(targetPool, shared);
        if (specialOption != null) {
            return Collections.singletonList(specialOption);
        }

        // ── 目标池非信用债大库：仅返回默认调入流程 ──
        if (!isCreditBondPool(targetPool)) {
            // 解析流程名称：优先使用配置名称
            String flowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
            // 判断池配置是否为空流程
            boolean noApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
            FlowOptionParam p = new FlowOptionParam();
            p.setFlowType(FlowType.NORMAL_INBOUND.getCode());
            p.setFlowName(flowName);
            p.setFlowId(targetPool.getInFlowId());
            p.setFlowKey(targetPool.getInFlowKey());
            p.setRecommended(true);
            p.setMatched(true);
            p.setSelectable(true);
            // 生成目标池默认流程的匹配说明
            p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                    "目标池不在信用债大库中", flowName, noApproval, "默认调入流程")));
            p.setUnmatchReasons(new ArrayList<>());
            // 构建默认调入流程候选项
            return Collections.singletonList(buildPoolFlowOption(p));
        }

        // 4. 已在信用债池的标的按同级池的排序判断升降级
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

        // 5. 其余情况比较白名单、简易和默认调入流程
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
        // 构建简易流程候选项
        options.add(buildPoolFlowOption(simpleP));
        // 默认调入流程
        String normalFlowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
        // 判断池配置是否为空流程
        boolean normalNoApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
        FlowOptionParam normalP = new FlowOptionParam();
        normalP.setFlowType(FlowType.NORMAL_INBOUND.getCode());
        normalP.setFlowName(normalFlowName);
        normalP.setFlowId(targetPool.getInFlowId());
        normalP.setFlowKey(targetPool.getInFlowKey());
        normalP.setRecommended(FlowType.NORMAL_INBOUND.getCode().equals(recommendedType));
        normalP.setMatched(true);
        normalP.setSelectable(true);
        // 生成目标池默认流程的匹配说明
        normalP.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                null, normalFlowName, normalNoApproval, "默认调入流程")));
        normalP.setUnmatchReasons(new ArrayList<>());
        // 构建默认调入流程候选项
        options.add(buildPoolFlowOption(normalP));
        return options;
    }

    /**
     * 匹配非信用债池的特殊调入流程
     *
     * 执行顺序：
     * 1. 检查目标池和在池数据是否齐全，信用债池不走此分支
     * 2. 找出组合当前所在的调入互斥池
     * 3. 找到启用的特殊流程后，生成推荐候选并列出命中的互斥池
     *
     * @param targetPool 当前调整项的目标池配置
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 特殊调入流程候选，未命中时为 null
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
        // 取得流程展示名称
        p.setFlowName(resolveFlowName(flow.getName(), "特殊审批流程"));
        p.setFlowId(flow.getId());
        p.setFlowKey(FLOW_KEY_SPECIAL_INBOUND);
        p.setRecommended(true);
        p.setMatched(true);
        p.setSelectable(true);
        p.setMatchReasons(Collections.singletonList("证券当前在目标池调入互斥池中，按特殊审批流程处理："
                // 列出命中的投资池，便于定位原因
                + poolNames(matchedPoolIds, shared.getPoolMap())));
        p.setUnmatchReasons(new ArrayList<>());
        // 构建特殊审批流程候选项
        return buildPoolFlowOption(p);
    }

    /**
     * 把池 ID 列表转成便于阅读的池名
     *
     * 执行顺序：
     * 1. 逐个取池名，找不到时使用 ID，再用顿号拼接
     *
     * @param poolIds 待处理的投资池 ID 列表
     * @param poolMap 按池 ID 整理的投资池配置
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
     * 生成流程候选的去重标识
     *
     * 执行顺序：
     * 1. 拼接流程 ID、编码、类型和名称，空候选使用空字符串
     *
     * @param option 待生成去重标识的流程候选
     * @return 流程候选去重标识
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
     * 记录白名单流程各项条件是否满足
     *
     * 当前只按白名单池配置是否为空记录结果，还没有查询主体是否在白名单池
     *
     * 执行顺序：
     * 1. 检查剩余期限是否不超过三年
     * 2. 排查永续债、ABS、私募债，并确认属于债券类
     * 3. 检查是否配置白名单池以及是否为担保债
     * 4. 分别记录满足和不满足的原因
     *
     * @param req 凭证与标的组合及其目标池的检查请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param matchReasons 用于收集匹配原因的列表
     * @param unmatchReasons 用于收集不匹配原因的列表
     * @return 没有不匹配原因时返回 true
     */
    private boolean isWhitelistFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, List<String> matchReasons, List<String> unmatchReasons) {
        SecurityInfoBo sec = shared.getSecurityInfo();

        // 1. 判断剩余期限是否不超过三年
        BigDecimal remainYears = CreditBondRemainTermUtil.parseRemainTermYears(sec.getDateExistsStr());
        if (remainYears == null) {
            unmatchReasons.add("证券期限无法解析，date_exists_str 为空或格式不正确");
        } else if (remainYears.compareTo(new BigDecimal("3")) <= 0) {
            matchReasons.add("证券期限为 " + sec.getDateExistsStr() + "，未超过 3 年");
        } else {
            unmatchReasons.add("证券期限为 " + sec.getDateExistsStr() + "，超过 3 年");
        }

        // 2. 排除永续债、私募债和 ABS 债
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

        // 3. 确认标的属于债券类
        String categoryType = crmwPoolAdjustMapper.queryCategoryTypeBySecurityType(sec.getSecurityType());
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            matchReasons.add("债券类型属于债券类");
        } else {
            unmatchReasons.add("债券类型不属于债券类");
        }

        // 4. 检查是否配置白名单池，当前未查询主体实际在池状态
        if (!WHITELIST_POOL_IDS.isEmpty()) {
            // TODO 后续接入 queryIssuerInWhitelistPools 查询主体在白名单池
            matchReasons.add("主体在白名单池中");
        } else {
            unmatchReasons.add("白名单池未配置，主体在白名单池条件不成立");
        }

        // 5. 排除担保债
        boolean isGuaranteed = sec.getGuarantFlag() != null && sec.getGuarantFlag() == 1;
        if (!isGuaranteed) {
            matchReasons.add("债券非担保债");
        } else {
            unmatchReasons.add("债券为担保债，不符合白名单条件");
        }

        return unmatchReasons.isEmpty();
    }

    /**
     * 检查标的是否符合简易调入流程
     *
     * 执行顺序：
     * 1. ABS 不走简易调入流程
     * 2. 检查目标池是否为信用债大库一至三级池
     * 3. 检查剩余期限，并与同主体在目标池的最长剩余期限比较
     * 4. 根据记录的原因判断是否匹配
     *
     * @param req 凭证与标的组合及其目标池的检查请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param targetPool 当前调整项的目标池配置
     * @param matchReasons 用于收集匹配原因的列表
     * @param unmatchReasons 用于收集不匹配原因的列表
     * @return 没有不匹配原因时返回 true
     */
    private boolean isSimpleInboundFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, InvestmentPoolBo targetPool,
            List<String> matchReasons, List<String> unmatchReasons) {

        // ABS 只能走一般入库流程，不提供快速入库候选
        if (CreditBondSpecialInboundRule.isAbs(shared.getSecurityInfo())) {
            unmatchReasons.add("ABS 不适用快速入库流程");
            return false;
        }
        // 判断目标池是否属于信用债大库一至三级
        if (isCreditBondLevelOneToThree(targetPool)) {
            matchReasons.add("目标池属于信用债大库一、二、三级库");
        } else {
            unmatchReasons.add("目标池不是信用债大库一、二、三级库");
        }

        BigDecimal remainDays = shared.getSecurityInfo().getDateExists();
        if (remainDays == null) {
            unmatchReasons.add("剩余期限无法解析，date_exists 为空");
        }

        if (remainDays != null && remainDays.compareTo(BigDecimal.ZERO) >= 0) {
            BigDecimal issuerPoolMaxRemainDays = crmwPoolAdjustMapper.queryIssuerTargetPoolMaxRemainDays(
                    req.getSecurityCode(), targetPool.getId());
            if (issuerPoolMaxRemainDays == null) {
                matchReasons.add("目标池暂无同主体有效债券，不受在池最大期限限制");
            } else if (remainDays.compareTo(issuerPoolMaxRemainDays) <= 0) {
                // 把剩余天数整理成提示文本
                matchReasons.add("剩余期限为 " + formatRemainDays(remainDays)
                        // 把剩余天数整理成提示文本
                        + "，未超过同主体在池最大期限 " + formatRemainDays(issuerPoolMaxRemainDays));
            } else {
                // 把剩余天数整理成提示文本
                unmatchReasons.add("剩余期限为 " + formatRemainDays(remainDays)
                        // 把剩余天数整理成提示文本
                        + "，超过同主体在池最大期限 " + formatRemainDays(issuerPoolMaxRemainDays));
            }
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
     * 按同一父池下的池层级判断升降级
     *
     * 执行顺序：
     * 1. 确认目标池有父池和排序值
     * 2. 找到同父池的当前池，比较其与目标池的排序值
     *
     * @param targetPool 当前调整项的目标池配置
     * @param shared 当前申请共用的证券、投资池和状态数据
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
     * 判断目标池是否为信用债池
     *
     * 执行顺序：
     * 1. 确认池存在且类型为信用债池
     *
     * @param pool 投资池配置
     * @return 是信用债池时返回 true
     */
    private boolean isCreditBondPool(InvestmentPoolBo pool) {
        if (pool == null) {
            return false;
        }
        return PoolType.CREDIT_BOND.getCode().equals(pool.getPoolType());
    }

    /**
     * 判断标的当前是否在信用债池
     *
     * 执行顺序：
     * 1. 逐个查看当前所在池的类型，找到信用债池即返回 true
     *
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 当前至少在一个信用债池时返回 true
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
     * 判断目标池是否属于信用债大库一至三级
     *
     * 执行顺序：
     * 1. 检查池类型和池内排序值是否在一至三之间
     *
     * @param pool 投资池配置
     * @return 目标池为信用债大库一至三级时返回 true
     */
    private boolean isCreditBondLevelOneToThree(InvestmentPoolBo pool) {
        return pool != null
                && PoolType.CREDIT_BOND.getCode().equals(pool.getPoolType())
                && pool.getInnerSort() != null
                && pool.getInnerSort() >= 1
                && pool.getInnerSort() <= 3;
    }

    /**
     * 把剩余天数整理成提示文本
     *
     * 执行顺序：
     * 1. 去掉小数末尾多余的零并加上天数单位，无天数时返回空字符串
     *
     * @param remainDays 标的剩余期限的天数
     * @return 带单位的剩余天数
     */
    private String formatRemainDays(BigDecimal remainDays) {
        if (remainDays == null) {
            return "";
        }
        return remainDays.stripTrailingZeros().toPlainString() + " 天";
    }

    /**
     * 判断标的是否填写担保人
     *
     * 执行顺序：
     * 1. 检查证券资料中的担保人名称或代码是否非空
     *
     * @param securityInfo 标的证券资料
     * @return 填有担保人名称或代码时返回 true
     */
    private boolean hasGuarantor(SecurityInfoBo securityInfo) {
        if (securityInfo == null) {
            return false;
        }
        return (securityInfo.getGuarantor() != null && !securityInfo.getGuarantor().trim().isEmpty())
                || (securityInfo.getGuarantorId() != null && !securityInfo.getGuarantorId().trim().isEmpty());
    }

    /**
     * 组装白名单审批流程候选
     *
     * 执行顺序：
     * 1. 按固定编码查找启用的白名单流程，未找到时补充原因
     * 2. 填充候选的类型、名称、流程标识和选择状态
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
     * 组装目标池默认调入流程候选
     *
     * 执行顺序：
     * 1. 取得流程名称并判断是否配置审批流程
     * 2. 设置默认调入为推荐且可选，并附上匹配原因
     *
     * @param targetPool 当前调整项的目标池配置
     * @param matchReason 已知的流程匹配原因
     * @return 默认调入流程候选
     */
    private AdjustCheckDto.FlowOption buildNormalInboundFlowOption(InvestmentPoolBo targetPool, String matchReason) {
        // 取得流程展示名称
        String flowName = resolveFlowName(targetPool.getInFlowName(), "默认调入流程");
        // 判断池配置是否为空流程
        boolean noApproval = isNoApprovalFlow(targetPool.getInFlowId(), targetPool.getInFlowKey());
        FlowOptionParam p = new FlowOptionParam();
        p.setFlowType(FlowType.NORMAL_INBOUND.getCode());
        p.setFlowName(flowName);
        p.setFlowId(targetPool.getInFlowId());
        p.setFlowKey(targetPool.getInFlowKey());
        p.setRecommended(true);
        p.setMatched(true);
        p.setSelectable(true);
        // 生成目标池默认流程的匹配说明
        p.setMatchReasons(Collections.singletonList(buildTargetFlowReason(
                matchReason, flowName, noApproval, "默认调入流程")));
        p.setUnmatchReasons(new ArrayList<>());
        // 构建默认调入流程候选项
        return buildPoolFlowOption(p);
    }

    /**
     * 生成目标池默认流程的匹配说明
     *
     * 执行顺序：
     * 1. 保留已给出的原因前缀
     * 2. 按是否配置审批流程生成对应说明
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
     * 组装来自目标池配置的流程候选
     *
     * 执行顺序：
     * 1. 按流程 ID 和编码判断是否配置审批
     * 2. 未配置时用不需要审批作展示名称，保留其他候选信息
     * 3. 转换为流程候选
     *
     * @param p 用于组装流程候选的参数
     * @return 用于选择的池配置流程候选
     */
    private AdjustCheckDto.FlowOption buildPoolFlowOption(FlowOptionParam p) {
        // 判断池配置是否为空流程
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
     * 判断池配置是否为空流程
     *
     * 执行顺序：
     * 1. 检查流程 ID 和流程编码是否都未填写
     *
     * @param flowId 选择的流程 ID
     * @param flowKey 选择的流程编码
     * @return 两者都未填写时返回 true
     */
    private boolean isNoApprovalFlow(Long flowId, String flowKey) {
        return flowId == null && (flowKey == null || flowKey.isEmpty());
    }

    /**
     * 取得流程展示名称
     *
     * 执行顺序：
     * 1. 配置名称非空时使用配置名称，否则使用默认名称
     *
     * @param configuredName 配置的流程名称
     * @param fallbackName 未配置名称时使用的默认名称
     * @return 流程展示名称
     */
    private static String resolveFlowName(String configuredName, String fallbackName) {
        return configuredName != null && !configuredName.isEmpty() ? configuredName : fallbackName;
    }

    /**
     * 把流程候选参数转换成返回对象
     *
     * 执行顺序：
     * 1. 复制流程标识、匹配状态和原因，并为不匹配原因建立独立列表
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

    /**
     * 在直通或人工终审落池前复核 CRMW 调库记录
     *
     * 只有 Excel 原业务失败可以放行，数据不存在、类型错误及 ABS 身份冲突仍阻断
     *
     * 执行顺序：
     * 1. 复核 ABS 主体身份，日志为空时结束处理
     * 2. 识别 Excel 主项批次，加载池配置并按 ID 顺序锁池，准备池关系和容量增量
     * 3. 逐条检查目标池及标的存在性，必要时恢复申请中的 ABS 主体选择
     * 4. Excel 检查真实凭证后沿用业务提示放行口径，同批配套项继承主项来源
     * 5. 普通入口重新检查组合在池状态、限制池、容量及冻结期，失败时抛出含组合定位的异常
     *
     * @param logList 当前需要复核的完整调库组
     */
    public void recheckBeforeFinalApproval(List<IpAdjustLogBo> logList) {
        // 1. 复核 ABS 主体身份，日志为空时结束处理
        absIdentityService.processCrmwApproval(logList, false);

        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 2. 识别 Excel 来源，加载并锁定目标池，准备池关系和容量增量
        Set<String> excelBatches = collectExcelImportBatches(logList);
        Set<String> checkedCertificates = new HashSet<>();
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, p -> p));
        List<Long> poolIds = logList.stream().map(IpAdjustLogBo::getTargetPoolId)
                .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        if (!poolIds.isEmpty()) {
            // 按 ID 顺序锁定目标池，串行完成容量和池状态复核
            investmentPoolMapper.lockPoolByIdsList(poolIds);
        }
        // 构建全量投资池关系 Map
        Map<Long, Map<String, List<Long>>> relationMap = buildPoolRelationMap(
                crmwPoolAdjustMapper.queryAllPoolRelationList());
        Map<Long, Integer> inboundIncrements = new HashMap<>();
        for (IpAdjustLogBo log : logList) {
            boolean excelImport = EXCEL_IMPORT_ADJUST_TYPE.equals(log.getAdjustType())
                    || (log.getAdjustBatchNo() != null && excelBatches.contains(log.getAdjustBatchNo()));
            // 3. 逐条检查目标池和标的，恢复本次申请的 ABS 主体选择
            InvestmentPoolBo pool = poolMap.get(log.getTargetPoolId());
            if (pool == null || (excelImport && !PoolType.CRMW.getCode().equals(pool.getPoolType()))
                    || (!excelImport && (!"enabled".equals(pool.getStatus())
                    || Integer.valueOf(1).equals(pool.getIsDeleted())))) {
                // 抛出包含 CRMW 调库项上下文的复核异常
                throwFinalRecheckFailure(log, "目标投资池不存在或已停用");
            }
            SecurityInfoBo security = crmwPoolAdjustMapper.querySecurityBoByCode(log.getSecurityCode());
            if (security == null) {
                // 抛出包含 CRMW 调库项上下文的复核异常
                throwFinalRecheckFailure(log, "标的证券不存在");
            }
            if (log.getId() != null && CreditBondSpecialInboundRule.isAbs(security)) {
                absIdentityService.restoreSnapshotSelection(security, log, true);
            }
            // 4. Excel 确认凭证主档有效后，沿用业务提示放行口径
            if (excelImport) {
                if (checkedCertificates.add(log.getCrmwScode())) {
                    SecurityInfoBo certificate = crmwPoolAdjustMapper.querySecurityBoByCode(log.getCrmwScode());
                    if (certificate == null || !CRMW_SECURITY_TYPE.equals(certificate.getSecurityType())) {
                        // 凭证主档缺失或类型错误仍属于无法落库的数据错误
                        throwFinalRecheckFailure(log, "CRMW凭证不存在或类型错误");
                    }
                }
                // 原业务失败已保留为提示，直通及人工终审使用相同口径
                continue;
            }
            // 5. 普通入口重新检查组合状态、池限制、容量和冻结期
            AdjustCheckContext ctx = new AdjustCheckContext();
            ctx.setSecurityInfo(security);
            ctx.setTargetPool(pool);
            ctx.setPoolMap(poolMap);
            ctx.setCurrentPoolIds(new HashSet<>(crmwPoolAdjustMapper
                    .querySecurityCurrentPoolIdList(log.getSecurityCode(), log.getCrmwScode(),
                            log.getCrmwStype())));
            ctx.setTargetPoolRelations(relationMap.getOrDefault(pool.getId(), Collections.emptyMap()));
            ctx.setRequestInPoolIds(Collections.<Long>emptySet());
            String failure;
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                boolean alreadyIn = crmwPoolAdjustMapper.queryCrmwComboInPool(log.getCrmwScode(),
                        log.getCrmwStype(), log.getSecurityCode(), pool.getId());
                // 返回第一个最终审批复核失败原因
                failure = alreadyIn ? "信用缓释凭证与标的证券组合已在目标投资池中" : firstFinalFailure(
                        // 依次检查锁池、投资品种和来源池限制
                        inCheckPoolLocked(ctx), inCheckVariety(ctx), inCheckSourcePool(ctx),
                        // 依次检查调入限制池和全局禁止池
                        inCheckRestrictPool(ctx), inCheckForbiddenPool(ctx));
                int increment = inboundIncrements.getOrDefault(pool.getId(), 0);
                int currentCount = crmwPoolAdjustMapper.queryPoolCurrentCount(pool.getId());
                if (failure == null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                        && currentCount + increment >= pool.getMaxCapacity()) {
                    failure = "目标投资池容量已满";
                }
                inboundIncrements.put(pool.getId(), increment + 1);
            } else {
                boolean comboInPool = crmwPoolAdjustMapper.queryCrmwComboInPool(log.getCrmwScode(),
                        log.getCrmwStype(), log.getSecurityCode(), pool.getId());
                ctx.setTargetPoolEntryTime(crmwPoolAdjustMapper.queryCrmwPoolEntryTime(
                        log.getSecurityCode(), log.getCrmwScode(), log.getCrmwStype(), pool.getId()));
                // 返回第一个最终审批复核失败原因
                failure = !comboInPool ? "信用缓释凭证与标的证券组合已不在目标池" : firstFinalFailure(
                        // 依次检查锁池、调出限制池和冻结期
                        outCheckPoolLocked(ctx), outCheckRestrictPool(ctx), outCheckFrozenPeriod(ctx));
            }
            if (failure != null) {
                // 抛出包含 CRMW 调库项上下文的复核异常
                throwFinalRecheckFailure(log, failure);
            }
        }
    }

    /**
     * 对无需人工审批的 CRMW 调库记录执行最终复核并落池
     *
     * 执行顺序：
     * 1. 直通日志为空时结束，否则按最终审批口径复核整组
     * 2. 应用通过复核的组合池状态变更
     * 3. 落池成功后将手工信评附件沉淀为内部报告
     *
     * @param logList 本次提交收集的直通日志
     */
    private void recheckAndApplyDirectLogs(List<IpAdjustLogBo> logList) {
        // 1. 无直通日志时结束，否则执行最终复核
        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 按最终审批口径锁池并复核动态业务状态
        recheckBeforeFinalApproval(logList);
        // 2. 将复核通过的直通日志应用到当前池状态
        applyPoolStatusChanges(logList);
        // 3. 落池成功后将手工信评附件沉淀为内部报告
        reportService.addInternalReportsOnFinish(logList);
    }

    /**
     * 将通过最终复核的 CRMW 调库组写入当前池状态
     *
     * 仅处理当前凭证、实际标的和目标池，不改变其他组合；数据库异常由调用方事务回滚
     *
     * 执行顺序：
     * 1. 确认 ABS 主体身份，在本次事务内处理已通过审批的主体信息
     * 2. 收集 Excel 主项批次，让同组配套项沿用相同的落池口径
     * 3. 处理调入：Excel 先结束同凭证、实际标的和目标池的旧有效状态，再写入新状态
     * 4. 处理调出：只软删除当前组合，Excel 已不在池时按完成处理，普通入口仍报错
     *
     * @param logList 已通过最终复核的完整调库日志组
     */
    public void applyPoolStatusChanges(List<IpAdjustLogBo> logList) {
        // 1. 确认本次审批通过的 ABS 主体身份
        absIdentityService.processCrmwApproval(logList, true);

        // 2. 识别 Excel 主项批次，让配套项沿用同一落池口径
        Set<String> excelBatches = collectExcelImportBatches(logList);
        for (IpAdjustLogBo log : logList) {
            boolean excelImport = EXCEL_IMPORT_ADJUST_TYPE.equals(log.getAdjustType())
                    || (log.getAdjustBatchNo() != null && excelBatches.contains(log.getAdjustBatchNo()));
            // 3. 调入写新状态，Excel 先结束同一组合的旧有效状态
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                if (excelImport) {
                    // 仅结束同凭证、实际标的及目标池的旧状态，保留其他组合
                    crmwPoolAdjustMapper.deletePoolStatusSoft(log.getSecurityCode(), log.getCrmwScode(),
                            log.getCrmwStype(), log.getTargetPoolId());
                }
                log.setAuditStatus(AuditStatus.APPROVED.getCode());
                log.setAdjustLogId(log.getId());
                int inserted = crmwPoolAdjustMapper.addPoolStatus(log);
                if (inserted != 1) {
                    throw new BizException("CRMW[" + log.getCrmwScode() + "]调入投资池["
                            + log.getTargetPoolName() + "]失败：入池状态写入异常");
                }
            } else if (AdjustMode.OUT.getCode().equals(log.getAdjustMode())) {
                // 4. 调出只删除当前组合，Excel 已不在池时按完成处理
                int deleted = crmwPoolAdjustMapper.deletePoolStatusSoft(log.getSecurityCode(), log.getCrmwScode(),
                        log.getCrmwStype(), log.getTargetPoolId());
                if (deleted == 0 && !excelImport) {
                    throw new BizException("CRMW 当前池状态已发生变化，请刷新后重试");
                }
            }
        }
    }

    /**
     * 收集当前调库组中 Excel 主项的批次号
     *
     * 执行顺序：
     * 1. 遍历日志，只收集 Excel 主项的非空批次号并去重
     * 2. 返回批次集合，供联动、互斥和关联项继承来源，不查询数据库或改变日志类型
     *
     * @param logs 当前处理的完整调库组
     * @return Excel 主项批次号集合
     */
    private Set<String> collectExcelImportBatches(List<IpAdjustLogBo> logs) {
        // 1. 只收集 Excel 主项的非空批次号并去重
        Set<String> batches = new HashSet<>();
        for (IpAdjustLogBo log : logs) {
            if (EXCEL_IMPORT_ADJUST_TYPE.equals(log.getAdjustType()) && log.getAdjustBatchNo() != null) {
                batches.add(log.getAdjustBatchNo());
            }
        }
        // 2. 返回批次集合，供同组配套项识别来源
        return batches;
    }

    /**
     * 取得第一条有效的最终校验失败原因
     *
     * 执行顺序：
     * 1. 按校验顺序查找首条非空失败原因
     *
     * @param failures 用于收集阻断原因的列表
     * @return 首条失败原因，全部为空时为 null
     */
    private String firstFinalFailure(String... failures) {
        for (String failure : failures) {
            if (failure != null && !failure.isEmpty()) {
                return failure;
            }
        }
        return null;
    }

    /**
     * 用调库日志信息报告最终校验失败
     *
     * 执行顺序：
     * 1. 拼接凭证代码、调整方向、目标池名和失败原因并抛出业务异常
     *
     * @param log 当前调库日志
     * @param reason 本次校验产生的原因或提示
     */
    private void throwFinalRecheckFailure(IpAdjustLogBo log, String reason) {
        throw new BizException("CRMW[" + log.getCrmwScode() + "]" + log.getAdjustMode()
                + "投资池[" + log.getTargetPoolName() + "]失败：" + reason);
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 方向校验入口（含前置规则）
    // ═══════════════════════════════════════════════════════════

    /**
     * 汇总 CRMW 调入的阻断原因
     *
     * 执行顺序：
     * 1. 先执行通用调入检查
     * 2. 再按标的品种执行各自的调入检查并合并失败原因
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 阻断调入的原因，空列表表示通过
     */
    public List<String> checkInConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 通用校验（所有类型都走）
        failures.addAll(checkCommonIn(ctx));
        // 2. 类型特有校验（按 categoryType 路由）
        String categoryType = ctx.getCategoryType();
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            // 检查债券调入的品种规则
            failures.addAll(checkBondIn(ctx));
        } else if (CategoryType.STOCK.getCode().equals(categoryType)) {
            // 检查股票调入的品种规则
            failures.addAll(checkStockIn(ctx));
        } else if (CategoryType.FUND.getCode().equals(categoryType)) {
            // 返回基金调入的品种检查结果
            failures.addAll(checkFundIn(ctx));
        } else if (CategoryType.COMPANY.getCode().equals(categoryType)) {
            // 返回主体调入的品种检查结果
            failures.addAll(checkCompanyIn(ctx));
        }
        return failures;
    }

    /**
     * 检查 CRMW 调入的通用规则
     *
     * 执行顺序：
     * 1. 依次检查锁定、投资品种、市场、进行中流程、重复入池和容量
     * 2. 检查来源池、限制池及同次调入的互斥冲突，弹性限制单独记录为提示
     * 3. 最后检查禁投状态和开放日
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 阻断调入的原因，空列表表示通过
     */
    private List<String> checkCommonIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 依次检查池锁定、品种、市场、进行中流程、在池状态和容量
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
        // 2. 检查来源、限制和互斥关系，将弹性限制记为提示
        addIfFailed(failures, inCheckSourcePool(ctx));
        // 入池检查：证券当前在调入限制池中（in_restrict）
        addIfFailed(failures, inCheckRestrictPool(ctx));
        // 入池检查：本次请求中是否同时勾选了互斥池（不可同时调入）
        addIfFailed(failures, inCheckMutexConflict(ctx));
        // 入池检查：证券当前在弹性禁投池中（in_soft_restrict，警告不阻断）
        addIfWarning(ctx.getWarnings(), inCheckElasticPool(ctx));
        // 3. 检查标的禁投状态和目标池开放日
        addIfFailed(failures, inCheckForbiddenPool(ctx));
        // 行业限制校验（按池 industry_code，调入）——暂时不需要，先注释
        // addIfFailed(failures, inCheckIndustry(ctx));
        // 开放日校验（按池 open_day_adjust，调入）
        addIfFailed(failures, inCheckOpenDay(ctx));
        return failures;
    }

    /**
     * 检查债券调入的品种规则
     *
     * 执行顺序：
     * 1. 检查债券是否已到期，将到期原因加入失败列表
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 阻断债券调入的原因
     */
    private List<String> checkBondIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 债券到期校验
        addIfFailed(failures, inCheckBondMaturity(ctx));
        return failures;
    }

    /**
     * 检查股票调入的品种规则
     *
     * 执行顺序：
     * 1. 依次执行退市和评级限制检查，收集失败原因
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 阻断股票调入的原因
     */
    private List<String> checkStockIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 股票退市校验
        addIfFailed(failures, inCheckStockDelist(ctx));
        // 股票入池评级限制（老系统 StockResearch/investrank），当前未接入股票评级来源时跳过
        addIfFailed(failures, inCheckGradeAstrict(ctx));
        return failures;
    }

    /**
     * 返回基金调入的品种检查结果
     *
     * 执行顺序：
     * 1. 基金目前没有额外调入规则，返回空列表
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 空的失败原因列表
     */
    private List<String> checkFundIn(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    /**
     * 返回主体调入的品种检查结果
     *
     * 执行顺序：
     * 1. 主体目前没有额外调入规则，返回空列表
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 空的失败原因列表
     */
    private List<String> checkCompanyIn(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    /**
     * 汇总 CRMW 调出的阻断原因
     *
     * 执行顺序：
     * 1. 先执行通用调出检查
     * 2. 再按标的品种执行各自的调出检查并合并失败原因
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 阻断调出的原因，空列表表示通过
     */
    public List<String> checkOutConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 通用校验（所有类型都走）
        failures.addAll(checkCommonOut(ctx));
        // 2. 类型特有校验（按 categoryType 路由）
        String categoryType = ctx.getCategoryType();
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            // 检查债券调出的品种规则
            failures.addAll(checkBondOut(ctx));
        } else if (CategoryType.STOCK.getCode().equals(categoryType)) {
            // 检查股票调出的品种规则
            failures.addAll(checkStockOut(ctx));
        } else if (CategoryType.FUND.getCode().equals(categoryType)) {
            // 返回基金调出的品种检查结果
            failures.addAll(checkFundOut(ctx));
        } else if (CategoryType.COMPANY.getCode().equals(categoryType)) {
            // 返回主体调出的品种检查结果
            failures.addAll(checkCompanyOut(ctx));
        }
        return failures;
    }

    /**
     * 检查 CRMW 调出的通用规则
     *
     * 执行顺序：
     * 1. 依次检查锁定、进行中流程、在池状态和冻结期
     * 2. 检查调出限制池、调出互斥池及同次调出的互斥冲突，弹性限制单独记录为提示
     * 3. 最后检查开放日
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 阻断调出的原因，空列表表示通过
     */
    private List<String> checkCommonOut(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 依次检查池锁定、进行中流程、在池状态和冻结期
        addIfFailed(failures, outCheckPoolLocked(ctx));
        // 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 出池检查：证券是否不在来源池中
        addIfFailed(failures, outCheckSecurityNotInPool(ctx));
        // 出池检查：是否在冻结期内（入池后N天不可调出，须在确认在池后校验）
        addIfFailed(failures, outCheckFrozenPeriod(ctx));
        // 2. 检查限制和互斥关系，将弹性限制记为提示
        addIfFailed(failures, outCheckRestrictPool(ctx));
        // 出池检查：是否与互斥池冲突（证券当前在互斥池中）
        addIfFailed(failures, outCheckMutexPool(ctx));
        // 出池检查：本次请求中是否同时勾选了互斥池（不可同时调出）
        addIfFailed(failures, outCheckMutexConflict(ctx));
        // 出池检查：证券当前在弹性禁投池中（out_soft_restrict，警告不阻断）
        addIfWarning(ctx.getWarnings(), outCheckElasticPool(ctx));
        // 3. 检查目标池开放日
        addIfFailed(failures, outCheckOpenDay(ctx));
        return failures;
    }

    /**
     * 检查债券调出的品种规则
     *
     * 执行顺序：
     * 1. 检查债券是否已到期，将到期原因加入失败列表
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 阻断债券调出的原因
     */
    private List<String> checkBondOut(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 债券到期校验
        addIfFailed(failures, outCheckBondMaturity(ctx));
        return failures;
    }

    /**
     * 检查股票调出的品种规则
     *
     * 执行顺序：
     * 1. 检查股票是否已退市，将退市原因加入失败列表
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 阻断股票调出的原因
     */
    private List<String> checkStockOut(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 股票退市校验
        addIfFailed(failures, outCheckStockDelist(ctx));
        return failures;
    }

    /**
     * 返回基金调出的品种检查结果
     *
     * 执行顺序：
     * 1. 基金目前没有额外调出规则，返回空列表
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 空的失败原因列表
     */
    private List<String> checkFundOut(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    /**
     * 返回主体调出的品种检查结果
     *
     * 执行顺序：
     * 1. 主体目前没有额外调出规则，返回空列表
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 空的失败原因列表
     */
    private List<String> checkCompanyOut(AdjustCheckContext ctx) {
        return new ArrayList<>();
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 前置校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 检查已到期债券能否调入
     *
     * 执行顺序：
     * 1. 有到期日时按 yyyyMMdd 与当天比较，到期日早于当天则提示已到期
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 债券到期提示，未命中时为 null
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
     * 检查已退市股票能否调入
     *
     * 执行顺序：
     * 1. 有退市日时按 yyyyMMdd 与当天比较，退市日早于当天则提示已退市
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查已到期债券能否调出
     *
     * 执行顺序：
     * 1. 有到期日时按 yyyyMMdd 与当天比较，到期日早于当天则提示已到期
     *
     * @param ctx 含标的到期日的调出检查数据
     * @return 债券到期提示，未命中时为 null
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
     * 检查已退市股票能否调出
     *
     * 执行顺序：
     * 1. 有退市日时按 yyyyMMdd 与当天比较，退市日早于当天则提示已退市
     *
     * @param ctx 含标的退市日的调出检查数据
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
     * 检查标的是否已有进行中的调库流程
     *
     * 执行顺序：
     * 1. 根据上下文的流程状态生成提示，有节点名称时一并展示
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查 CRMW 组合是否已在调入目标池
     *
     * 执行顺序：
     * 1. 用目标池 ID 检查组合当前所在的池
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 组合已在池的提示，未命中时为 null
     */
    private String inCheckSecurityAlreadyInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId != null && ctx.getCurrentPoolIds().contains(poolId)) {
            return "信用缓释凭证与标的证券组合已在目标投资池中";
        }
        return null;
    }

    /**
     * 检查调入目标池是否已满
     *
     * 执行顺序：
     * 1. 配置正数上限时，用在池数量与上限比较
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查调入是否满足来源池要求
     *
     * 执行顺序：
     * 1. 读取目标池配置的来源池
     * 2. 组合当前所在的池或本次申请调入的池至少命中一个来源池，否则列出要求的来源池
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查调入限制池是否阻止本次调入
     *
     * 执行顺序：
     * 1. 按调入限制关系检查组合是否已在配置的限制池中
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 调入限制提示，未命中时为 null
     */
    private String inCheckRestrictPool(AdjustCheckContext ctx) {
        // 按当前关系类型检查组合所在的池
        return checkBlockedByPools(ctx, RelationType.IN_RESTRICT.getCode(), "调入限制池");
    }

    /**
     * 检查调入是否命中弹性禁投池
     *
     * 执行顺序：
     * 1. 按调入弹性限制关系检查组合当前所在的池
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 弹性限制提示，未命中时为 null
     */
    private String inCheckElasticPool(AdjustCheckContext ctx) {
        // 命中弹性禁投池时只生成提示
        return checkBlockedByPools(ctx, RelationType.IN_SOFT_RESTRICT.getCode(), "调入弹性禁投池");
    }

    /**
     * 检查本次是否同时调入互斥池
     *
     * 执行顺序：
     * 1. 用目标池的调入互斥关系筛出本次也要调入的池
     * 2. 有冲突时列出互斥池全名
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查 CRMW 组合是否在调出目标池
     *
     * 执行顺序：
     * 1. 用目标池 ID 检查组合当前所在的池，目标池缺失或组合不在池时生成提示
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查调出限制池是否阻止本次调出
     *
     * 执行顺序：
     * 1. 按调出限制关系检查组合是否已在配置的限制池中
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 调出限制提示，未命中时为 null
     */
    private String outCheckRestrictPool(AdjustCheckContext ctx) {
        // 按当前关系类型检查组合所在的池
        return checkBlockedByPools(ctx, RelationType.OUT_RESTRICT.getCode(), "调出限制池");
    }

    /**
     * 检查调出互斥池是否阻止本次调出
     *
     * 执行顺序：
     * 1. 按调出互斥关系检查组合是否已在配置的互斥池中
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 调出互斥提示，未命中时为 null
     */
    private String outCheckMutexPool(AdjustCheckContext ctx) {
        // 按当前关系类型检查组合所在的池
        return checkBlockedByPools(ctx, RelationType.OUT_MUTEX.getCode(), "调出互斥池");
    }

    /**
     * 检查调出是否命中弹性禁投池
     *
     * 执行顺序：
     * 1. 按调出弹性限制关系检查组合当前所在的池
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 弹性限制提示，未命中时为 null
     */
    private String outCheckElasticPool(AdjustCheckContext ctx) {
        // 命中弹性禁投池时只生成提示
        return checkBlockedByPools(ctx, RelationType.OUT_SOFT_RESTRICT.getCode(), "调出弹性禁投池");
    }

    /**
     * 检查本次是否同时调出互斥池
     *
     * 执行顺序：
     * 1. 用目标池的调入互斥关系筛出本次也要调出的池
     * 2. 有冲突时列出互斥池全名
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查锁定状态是否阻止调入
     *
     * 执行顺序：
     * 1. 读取目标池锁定标记，已锁定时生成提示
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查锁定状态是否阻止调出
     *
     * 执行顺序：
     * 1. 读取目标池锁定标记，已锁定时生成提示
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查调出是否仍处于入池冻结期
     *
     * 执行顺序：
     * 1. 确认目标池配置了冻结天数
     * 2. 读取入池生效时间，缺失时生成提示
     * 3. 用生效时间加冻结天数，与当前时间比较
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 入池时间缺失或仍在冻结期的提示，符合时为 null
     */
    private String outCheckFrozenPeriod(AdjustCheckContext ctx) {
        // 1. 确认目标池配置了冻结天数
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getFrozenPeriodIn() == null || pool.getFrozenPeriodIn() <= 0) {
            return null;
        }
        // 2. 取证券在目标池的入池时间
        Date entryTime = ctx.getTargetPoolEntryTime();
        if (entryTime == null) {
            return "证券入池生效时间缺失";
        }
        // 3. 计算冻结期截止时间 = 入池时间 + N 天
        long frozenMs = pool.getFrozenPeriodIn() * 24L * 60L * 60L * 1000L;
        Date frozenDeadline = new Date(entryTime.getTime() + frozenMs);
        if (new Date().before(frozenDeadline)) {
            return "证券仍在目标投资池冻结期内";
        }
        return null;
    }

    /**
     * 检查标的品种是否允许调入
     *
     * 执行顺序：
     * 1. 目标池未限定投资品种时直接通过
     * 2. 查询标的证券类型对应的品种并与配置核对
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 品种不在允许范围的提示，符合时为 null
     */
    private String inCheckVariety(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getVarietyCodes() == null || pool.getVarietyCodes().isEmpty()) {
            return null;
        }
        // 查证券品种大类
        String categoryType = crmwPoolAdjustMapper.queryCategoryTypeBySecurityType(ctx.getSecurityInfo().getSecurityType());
        // 池配置品种为 JSON 数组（如 ["bond"]），判断是否包含证券品种
        if (categoryType == null || !pool.getVarietyCodes().contains("\"" + categoryType + "\"")) {
            return "证券不在本池投资品种范围内";
        }
        return null;
    }

    /**
     * 检查标的市场是否允许调入
     *
     * 执行顺序：
     * 1. 目标池未限定市场时直接通过
     * 2. 按市场匹配规则核对标的是否属于允许市场
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 市场不在允许范围的提示，符合时为 null
     */
    private String inCheckMarket(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getMarketCodes() == null || pool.getMarketCodes().isEmpty()) {
            return null;
        }
        // 按标的证券的市场匹配目标池配置
        if (!securityMarketService.matchesPoolMarkets(
                pool.getMarketCodes(), ctx.getSecurityInfo())) {
            return "证券不在本池投资市场范围内";
        }
        return null;
    }

    /**
     * 检查标的证券是否在禁投池
     *
     * 执行顺序：
     * 1. 按标的证券代码查询禁投状态，命中时生成提示
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 禁投提示，未命中时为 null
     */
    private String inCheckForbiddenPool(AdjustCheckContext ctx) {
        if (crmwPoolAdjustMapper.querySecurityInForbiddenPool(ctx.getSecurityInfo().getWindCode())) {
            return "证券当前在禁止池中";
        }
        return null;
    }

    /**
     * 读取目标池的股票评级限制配置
     *
     * 执行顺序：
     * 1. 读取目标池评级限制；当前没有执行评级比对，返回通过
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查标的行业是否符合目标池行业限制
     *
     * 执行顺序：
     * 1. 确认目标池配置了行业且适用行业限制
     * 2. 证券有行业信息时，与目标池行业配置比较
     *
     * @param ctx 当前组合和目标池的校验数据
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
     * 检查调入是否处于目标池开放日
     *
     * 执行顺序：
     * 1. 仅在目标池启用开放日控制时，查询当天是否在开放区间
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 非开放日提示，符合时为 null
     */
    private String inCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        String today = LocalDate.now().toString();
        if (!crmwPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
            return "当前不在本池开放日内";
        }
        return null;
    }

    /**
     * 检查调出是否处于目标池开放日
     *
     * 执行顺序：
     * 1. 仅在目标池启用开放日控制时，查询当天是否在开放区间
     *
     * @param ctx 当前组合和目标池的校验数据
     * @return 非开放日提示，符合时为 null
     */
    private String outCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        String today = LocalDate.now().toString();
        if (!crmwPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
            return "当前不在本池开放日内";
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 私有工具方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 整理单个调整项的规则检查数据
     *
     * 执行顺序：
     * 1. 取得目标池、父池和共用证券资料，填入当前池状态、关系及本次请求范围
     * 2. 调出时查询该组合入池时间
     * 3. 补充标的所属投资品种
     *
     * @param item 当前调整项
     * @param poolCurrentCount 目标池当前在池数量
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 当前调整项的规则检查上下文
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
                // 查找投资池所属的根池
                && shared.getPendingPoolGroupIds().contains(resolveRootPoolId(item.getTargetPoolId(), shared.getPoolMap())));
        ctx.setPendingProcessNodeLabel(shared.getPendingProcessNodeLabel());
        ctx.setSecurityInObservePool(shared.isSecurityInObservePool());
        ctx.setIssuerInObservePool(shared.isIssuerInObservePool());
        ctx.setRequestInPoolIds(shared.getRequestInPoolIds());
        ctx.setRequestOutPoolIds(shared.getRequestOutPoolIds());
        // 调出时查询证券在目标池的入池时间，用于冻结期校验（调入时不需要）
        if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
            ctx.setTargetPoolEntryTime(crmwPoolAdjustMapper.queryCrmwPoolEntryTime(
                    shared.getSecurityInfo().getWindCode(), shared.getCrmwScode(),
                    shared.getCrmwStype(), item.getTargetPoolId()));
        }
        // 查证券品种大类，用于类型特有校验路由（dict_security_type.category_type）
        ctx.setCategoryType(crmwPoolAdjustMapper.queryCategoryTypeBySecurityType(
                shared.getSecurityInfo().getSecurityType()));
        return ctx;
    }

    /**
     * 补齐校验结果的组合和来源标识
     *
     * 执行顺序：
     * 1. 优先从已加载证券资料取得标的身份，缺失时使用请求信息
     * 2. 为结果补齐缺失的标的、凭证和来源标识
     *
     * @param req 凭证与标的组合及其目标池的检查请求
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @param resultItems 待处理的调库校验结果
     */
    private void fillCheckResultIdentity(AdjustCheckReq req, AdjustSharedData shared,
                                         List<AdjustCheckDto.CheckResultItem> resultItems) {
        if (resultItems == null || resultItems.isEmpty()) {
            return;
        }
        SecurityInfoBo securityInfo = shared.getSecurityInfo();
        String securityCode = securityInfo != null && securityInfo.getWindCode() != null
                ? securityInfo.getWindCode() : req.getSecurityCode();
        String securityShortName = securityInfo != null && securityInfo.getShortName() != null
                ? securityInfo.getShortName() : req.getSecurityShortName();
        String securityType = securityInfo != null && securityInfo.getSecurityType() != null
                ? securityInfo.getSecurityType() : req.getSecurityType();
        String crmwScode = shared.getCrmwScode() != null ? shared.getCrmwScode() : req.getCrmwScode();
        String crmwStype = shared.getCrmwStype() != null ? shared.getCrmwStype() : req.getCrmwStype();
        String crmwName = req.getCrmwName();
        String sourceKey = (crmwScode == null ? "" : crmwScode) + "|" + (securityCode == null ? "" : securityCode);
        for (AdjustCheckDto.CheckResultItem item : resultItems) {
            if (item.getSecurityCode() == null || item.getSecurityCode().isEmpty()) {
                item.setSecurityCode(securityCode);
                item.setSecurityShortName(securityShortName);
                item.setSecurityType(securityType);
            }
            if (item.getCrmwScode() == null || item.getCrmwScode().isEmpty()) {
                item.setCrmwScode(crmwScode);
                item.setCrmwName(crmwName);
                item.setCrmwStype(crmwStype);
            }
            if (item.getSourceSecurityCode() == null || item.getSourceSecurityCode().isEmpty()) {
                item.setSourceSecurityCode(sourceKey);
            }
        }
    }

    /**
     * 检查联动或互斥调整并生成结果
     *
     * 执行顺序：
     * 1. 取得目标池和在池数量，组装该自动项的校验数据
     * 2. 按方向检查规则，调出还要确认组合在池
     * 3. 记录所属手工分组和校验结果
     *
     * @param targetPoolId 目标投资池 ID
     * @param adjustMode 调整方向
     * @param itemTag 调整项来源标记
     * @param adjustGroupKey 当前调整组的分组标识
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 联动或互斥调整的检查结果
     */
    private AdjustCheckDto.CheckResultItem buildAutoResultItem(
            Long targetPoolId, String adjustMode, String itemTag, String adjustGroupKey, AdjustSharedData shared) {

        int poolCurrentCount = crmwPoolAdjustMapper.queryPoolCurrentCount(targetPoolId);
        InvestmentPoolBo pool = shared.getPoolMap().get(targetPoolId);

        // 构造虚拟 CheckItem，使其可以复用 buildCheckContext 逻辑
        AdjustCheckReq.CheckItem fakeItem = new AdjustCheckReq.CheckItem();
        fakeItem.setTargetPoolId(targetPoolId);
        fakeItem.setAdjustMode(adjustMode);
        fakeItem.setPoolType(pool != null ? pool.getPoolType() : null);

        // 构建调库校验上下文
        AdjustCheckContext ctx = buildCheckContext(fakeItem, poolCurrentCount, shared);

        List<String> failures = AdjustMode.IN.getCode().equals(adjustMode) ? checkInConditions(ctx) : checkOutConditions(ctx);
        if (AdjustMode.OUT.getCode().equals(adjustMode)
                && !crmwPoolAdjustMapper.queryCrmwComboInPool(shared.getCrmwScode(),
                shared.getCrmwStype(), shared.getSecurityInfo().getWindCode(), targetPoolId)) {
            failures.add("信用缓释凭证与标的证券组合不在目标投资池中");
        }

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
     * 把手工项失败传给所属自动项
     *
     * 执行顺序：
     * 1. 手工项通过时不修改自动项
     * 2. 手工项失败时在自动项原有原因前加入关联失败提示，并标为不可调整
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
     * 在配套互斥调出失败时阻止手工调入
     *
     * 执行顺序：
     * 1. 找出同组未通过的互斥调出项并整理失败说明
     * 2. 有互斥失败时，将手工项标为不可调整并保留原失败原因
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
     * 整理配套互斥调出的失败说明
     *
     * 执行顺序：
     * 1. 先写明互斥池名称
     * 2. 有多条失败原因时按原顺序加编号并拼接
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
     * 生成手工调整组的分组标识
     *
     * 执行顺序：
     * 1. 用手工项的目标池 ID 和调整方向拼接分组键
     *
     * @param item 当前调整项
     * @return 手工项和配套自动项共用的分组键
     */
    private String buildAdjustGroupKey(AdjustCheckReq.CheckItem item) {
        return "manual_" + item.getTargetPoolId() + "_" + item.getAdjustMode();
    }

    /**
     * 检查组合是否在指定关系的限制池中
     *
     * 执行顺序：
     * 1. 按关系类型取得目标池关联的池
     * 2. 找出组合当前在其中的池并拼接全名
     *
     * @param ctx 当前组合和目标池的校验数据
     * @param relationType 要检查的池关系类型
     * @param label 用于错误提示的关系名称
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
     * 按池和关系类型整理投资池关联配置
     *
     * 执行顺序：
     * 1. 依次将每条关系归到来源池 ID 和关系类型下，并记录关联池 ID
     *
     * @param relations 全部投资池关系配置
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
     * 把池 ID 列表转换成完整池名
     *
     * 执行顺序：
     * 1. 逐个生成包含上级池的完整名称，再用顿号拼接
     *
     * @param poolIds 待处理的投资池 ID 列表
     * @param ctx 当前组合和目标池的校验数据
     * @return 拼接后的完整池名
     */
    private String poolNames(List<Long> poolIds, AdjustCheckContext ctx) {
        return poolIds.stream()
                // 构建投资池全路径名称
                .map(id -> buildPoolPath(id, ctx.getPoolMap()))
                .collect(Collectors.joining("、"));
    }

    /**
     * 收集一条阻断调库的原因
     *
     * 执行顺序：
     * 1. 检查原因是否为 null，有原因时加入失败列表
     *
     * @param failures 用于收集阻断原因的列表
     * @param reason 本次校验产生的原因或提示
     */
    private void addIfFailed(List<String> failures, String reason) {
        if (reason != null) {
            failures.add(reason);
        }
    }

    /**
     * 收集一条不阻断调库的提示
     *
     * 执行顺序：
     * 1. 检查原因是否为 null，有提示时加入提示列表
     *
     * @param warnings 用于收集非阻断提示的列表
     * @param reason 本次校验产生的原因或提示
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
     * 转换 CRMW 池列表中的池数据
     *
     * 执行顺序：
     * 1. 复制池的基本信息
     * 2. 按池 ID 补充在池数量和调入、调出互斥池，缺失时使用默认值
     *
     * @param pool 投资池配置
     * @param inMutexMap 按池 ID 整理的调入互斥关系
     * @param outMutexMap 按池 ID 整理的调出互斥关系
     * @param currentCountMap 按池 ID 整理的在池数量
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
     * 写入申请提交后的第一段审批步骤
     *
     * 提交时只写到首个人工审批待办，后续步骤随审批操作创建
     *
     * 执行顺序：
     * 1. 找到开始节点并记录自动开始
     * 2. 沿主线处理发起人提交和自动处理节点
     * 3. 遇到人工审批时创建待办并停止，走到结束节点时记录结束
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param snapshot 当前操作使用的快照
     * @param adjusterId 调库发起人 ID
     * @param adjusterName 调库发起人姓名
     * @return 初始化时流程已经走完则返回 true
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
        // 2. 沿主线处理发起人提交和自动审批节点
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
                // 保存一条调库审批步骤
                insertStepRecord(adjustLogId, adjustBatchNo, currentNode, config, sortOrder, ProcessAction.AUTO_PROCESS.getCode(),
                                 null, null, ProcessAction.AUTO_PROCESS.getCode(), "系统自动审批通过", now);
                // 取得初始化审批步骤所需的下一节点
                FlowNodeBo nextNode = findNextNodeForInitialSteps(snapshot, currentNode, prevNode);
                prevNode = currentNode;
                currentNode = nextNode;
                continue;
            }

            // 3. 遇到人工审批节点时创建待办
            if (NodeType.APPROVAL.getCode().equals(currentNode.getNodeType())) {
                // 为审批节点创建待处理步骤记录（按处理人明细展开为具体人员）
                createPendingSteps(adjustLogId, adjustBatchNo, currentNode, snapshot, now);
                return false;
            }

            // 4. 到达结束节点时保存结束步骤
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
                // 保存一条调库审批步骤
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
     * 判断审批节点是否为发起人提交节点
     *
     * 执行顺序：
     * 1. 确认节点是审批节点且使用发起人策略
     * 2. 检查该节点是否配置提交连线
     *
     * @param snapshot 当前操作使用的快照
     * @param node 当前流程节点
     * @param config 当前节点的审批配置
     * @param prevNode 上一个流程节点，当前判断不使用
     * @param startNode 流程开始节点，当前判断不使用
     * @return 满足发起人提交条件时返回 true
     */
    private boolean isInitiatorStep(FlowSnapshot snapshot, FlowNodeBo node, NodeApprovalConfigBo config,
                                    FlowNodeBo prevNode, FlowNodeBo startNode) {
        if (node == null || !NodeType.APPROVAL.getCode().equals(node.getNodeType())) {
            return false;
        }
        // 发起节点通过 approval_strategy=initiator + 出边 route_action=submit 标记
        return config != null
                && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())
                // 检查节点是否配置指定动作的出线
                && hasOutgoingRouteAction(snapshot, node, ProcessAction.SUBMIT.getCode());
    }

    /**
     * 检查节点是否配置指定动作的出线
     *
     * 执行顺序：
     * 1. 逐条检查节点的出线及其路由动作
     *
     * @param snapshot 当前操作使用的快照
     * @param node 当前流程节点
     * @param routeAction 需要匹配的流程动作编码
     * @return 存在对应动作的出线时返回 true
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
     * 为当前审批节点创建待办
     *
     * 执行顺序：
     * 1. 根据节点配置查找审批人
     * 2. 没有配置审批人时保存一条未指定审批人的待办
     * 3. 有候选人时排除本批次已参与的人员，并逐个创建待办
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param node 当前流程节点
     * @param snapshot 当前操作使用的快照
     * @param now 本次操作时间
     */
    private void createPendingSteps(Long adjustLogId, String adjustBatchNo, FlowNodeBo node,
                                    FlowSnapshot snapshot, Date now) {
        NodeApprovalConfigBo config = snapshot.approvalConfigMap.get(node.getId());
        // 1. 将审批处理人配置解析为具体人员
        List<HandlerTarget> handlers = resolveApprovalHandlers(config, snapshot);
        int sortOrder = node.getSortOrder() != null ? node.getSortOrder() : 1;

        if (handlers.isEmpty()) {
            // 2. 无配置处理人时仍创建一条空处理人的待处理记录
            insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                             null, null, null, null, now);
            return;
        }
        // 3. 只排除本批次真正审核过的人，待办和抢占跳过的人仍可进入下一节点
        handlers = excludeParticipatedHandlers(adjustLogId, adjustBatchNo, handlers);
        for (HandlerTarget handler : handlers) {
            // 插入单条步骤记录到 ip_adjust_step
            insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                             handler.handlerId, handler.handlerName, null, null, now);
        }
    }

    /**
     * 从候选审批人中排除本批次已参与的人员
     *
     * 执行顺序：
     * 1. 查询本批次已有步骤并收集已参与人员
     * 2. 从候选名单中移除这些人，无可用人选时提示错误
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param handlers 候选审批人列表
     * @return 仍可处理本批次的审批人
     */
    private List<HandlerTarget> excludeParticipatedHandlers(Long adjustLogId, String adjustBatchNo,
                                                            List<HandlerTarget> handlers) {
        List<IpAdjustStepBo> existingSteps = crmwPoolAdjustMapper.queryAdjustStepByBatchList(
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
     * 保存一条调库审批步骤
     *
     * 执行顺序：
     * 1. 填充日志批次、节点、处理人及动作信息
     * 2. 待办不填处理时间，其余使用步骤开始时间
     * 3. 写入步骤记录
     *
     * @param adjustLogId 调库日志 ID
     * @param adjustBatchNo 调库批次号
     * @param node 当前流程节点
     * @param config 当前节点的审批配置
     * @param sortOrder 审批步骤排序值
     * @param stepStatus 审批步骤状态
     * @param handlerId 处理人 ID
     * @param handlerName 处理人姓名
     * @param processAction 本步骤执行的处理动作
     * @param processComment 本步骤的处理意见
     * @param startTime 审批步骤开始时间
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
        crmwPoolAdjustMapper.addAdjustStep(step);
    }

    /**
     * 从流程快照中查找指定类型的节点
     *
     * 执行顺序：
     * 1. 遍历节点，返回第一个类型匹配的节点
     *
     * @param snapshot 当前操作使用的快照
     * @param nodeType 要查找的流程节点类型
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
     * 按流程主线寻找下一节点
     *
     * 执行顺序：
     * 1. 找出当前节点的所有出线
     * 2. 在仍有其他出线时排除返回上一节点的连线
     * 3. 优先使用非驳回连线，没有时使用剩余出线中的第一条
     *
     * @param snapshot 当前操作使用的快照
     * @param currentNode 当前流程节点
     * @param prevNode 上一个流程节点
     * @return 下一节点，没有可用连线时为 null
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

        // 2. 有其他出线可选时，排除返回上一节点的连线
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

        // 兜底：返回第一条出边的目标节点
        FlowEdgeBo firstEdge = outEdges.get(0);
        return snapshot.nodeMap.get(firstEdge.getToNodeId());
    }

    /**
     * 取得初始化审批步骤所需的下一节点
     *
     * 执行顺序：
     * 1. 按主线规则查找下一节点，找不到时提示当前节点缺少下一步连线
     *
     * @param snapshot 当前操作使用的快照
     * @param currentNode 当前流程节点
     * @param prevNode 上一个流程节点
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
     * 根据审批配置展开实际处理人
     *
     * 执行顺序：
     * 1. 按审批配置读取人员和角色候选
     * 2. 直接加入指定用户，角色则展开其下级角色后查找用户
     * 3. 按用户 ID 去重并保留查找顺序
     *
     * @param config 当前节点的审批配置
     * @param snapshot 当前操作使用的快照
     * @return 实际审批人列表
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
     * 收集指定角色及其下级角色
     *
     * 执行顺序：
     * 1. 记录当前角色 ID
     * 2. 递归查找父级为当前角色的角色并收集其下级
     *
     * @param roleId 当前角色 ID
     * @param roleIds 用于收集角色及其下级 ID 的列表
     * @param allRoles 可查询的角色列表
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
     * 组装一条 CRMW 调库日志
     *
     * 执行顺序：
     * 1. 确定实际标的，关联标的使用对应证券主数据
     * 2. 写入凭证、标的、池、方向和所选流程等申请信息
     * 3. 补充调整来源、原因、意见和批次提交时间
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param item 当前调整项
     * @param flowSource 提供本组所选流程的手工项
     * @param shared 当前申请共用的证券、投资池和状态数据
     * @return 待保存的 CRMW 调库日志
     */
    private IpAdjustLogBo buildAdjustLog(CrmwPoolAdjustSubmitReq req,
                                         CrmwPoolAdjustSubmitReq.AdjustItem item,
                                         CrmwPoolAdjustSubmitReq.AdjustItem flowSource,
                                         SubmitSharedData shared) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        // 按关联标的的主数据保存日志，确保后续按实际标的落池
        String securityCode = resolveItemSecurityCode(req, item);
        SecurityInfoBo securityInfo = shared.securityInfo;
        if (!securityCode.equals(req.getSecurityCode())) {
            securityInfo = crmwPoolAdjustMapper.querySecurityBoByCode(securityCode);
            if (securityInfo == null || CRMW_SECURITY_TYPE.equals(securityInfo.getSecurityType())) {
                throw new BizException("关联标的证券不存在或类型不正确：" + securityCode);
            }
        }
        bo.setSecurityCode(securityCode);
        bo.setSecurityShortName(securityInfo.getShortName());
        bo.setSecurityType(securityInfo.getSecurityType());
        bo.setCrmwName(req.getCrmwName());
        bo.setCrmwScode(req.getCrmwScode());
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
     * 把上传和已选材料绑定到调库日志
     *
     * 执行顺序：
     * 1. 按文件索引绑定本次上传的报告和材料
     * 2. 把从报告库选取的报告及材料复制到该日志
     *
     * @param adjustLogId 调库日志 ID
     * @param item 当前调整项
     * @param submissionFiles 本次提交共用的上传附件数据
     * @param uploaderId 绑定附件的提交人 ID
     */
    private void bindSubmitAttachments(Long adjustLogId, CrmwPoolAdjustSubmitReq.AdjustItem item,
                                       SysAttachmentService.SubmissionFiles submissionFiles, String uploaderId) {
        // 1. 绑定信评报告附件
        sysAttachmentService.bindAttachments(adjustLogId, item.getCreditReportFileIndexes(),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), submissionFiles);
        // 绑定其他材料附件
        sysAttachmentService.bindAttachments(adjustLogId, item.getMaterialFileIndexes(),
                AttachmentCategory.MATERIAL_HAND.getCode(), submissionFiles);
        // 2. 复制报告库附件为信评报告附件
        sysAttachmentService.copyReportAttachments(adjustLogId, item.getCreditReportSourceAttachmentIds(),
                AttachmentPurpose.CREDIT_REPORT.getCode(), uploaderId);
        // 复制报告库附件为其他材料附件
        sysAttachmentService.copyReportAttachments(adjustLogId, item.getMaterialSourceAttachmentIds(),
                AttachmentPurpose.MATERIAL.getCode(), uploaderId);
    }

    /**
     * 取得调库日志使用的调整来源
     *
     * 执行顺序：
     * 1. 按调整项的标记区分互斥、联动和跨市场关联，手工项使用申请来源
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param item 当前调整项
     * @return 写入日志的调整来源
     */
    private String resolveAdjustType(CrmwPoolAdjustSubmitReq req, CrmwPoolAdjustSubmitReq.AdjustItem item) {
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
     * 准备同券跨市场标的的调库校验数据
     *
     * 执行顺序：
     * 1. 把主标的选定的主体和担保人信息带到关联标的
     * 2. 沿用池配置并查询关联标的的在池状态及规则所需数据
     * 3. 补充评级状态和本次请求的调整范围
     *
     * @param securityCode 标的证券代码
     * @param securityInfo 标的证券资料
     * @param mainShared 主标的的校验共用数据
     * @param mainReq 主标的的校验请求
     * @return 关联标的的校验共用数据
     */
    private AdjustSharedData loadSharedDataForSecurity(String securityCode, SecurityInfoBo securityInfo,
                                                       AdjustSharedData mainShared, AdjustCheckReq mainReq) {
        AdjustSharedData shared = new AdjustSharedData();
        // 关联码沿用主券已校验的评级主体，字段原样拷贝
        SecurityInfoBo mainSecurityInfo = mainShared.getSecurityInfo();
        if (securityInfo != null && mainSecurityInfo != null) {
            securityInfo.setIssuer(mainSecurityInfo.getIssuer());
            securityInfo.setIssuerCode(mainSecurityInfo.getIssuerCode());
            securityInfo.setInnerIssuerRating(mainSecurityInfo.getInnerIssuerRating());
            securityInfo.setRatingBondissuer(mainSecurityInfo.getRatingBondissuer());
            securityInfo.setRatingBondissuerAgency(mainSecurityInfo.getRatingBondissuerAgency());
            securityInfo.setGuarantor(mainSecurityInfo.getGuarantor());
            securityInfo.setGuarantorId(mainSecurityInfo.getGuarantorId());
            securityInfo.setInnerGuarantorRating(mainSecurityInfo.getInnerGuarantorRating());
        }
        shared.setSecurityInfo(securityInfo);
        shared.setCrmwScode(mainShared.getCrmwScode());
        shared.setCrmwStype(mainShared.getCrmwStype());
        shared.setPoolMap(mainShared.getPoolMap());
        shared.setCurrentPoolIds(new HashSet<>(crmwPoolAdjustMapper.querySecurityCurrentPoolIdList(
                securityCode, shared.getCrmwScode(), shared.getCrmwStype())));
        shared.setPoolRelationMap(mainShared.getPoolRelationMap());
        shared.setPendingProcessNodeLabel(crmwPoolAdjustMapper.querySecurityPendingProcessNodeLabel(securityCode));
        // 关联码同样按凭证与标的组合检查当前池组的活动流程
        shared.setPendingPoolGroupIds(resolvePoolGroupIds(crmwPoolAdjustMapper.queryPendingManualTargetPoolIdList(
                securityCode, shared.getCrmwScode(), shared.getCrmwStype(), null), mainShared.getPoolMap()));
        shared.setSecurityInObservePool(crmwPoolAdjustMapper.querySecurityInObservePool(securityCode));
        shared.setIssuerInObservePool(crmwPoolAdjustMapper.queryIssuerInObservePool(securityCode));
        shared.setIssuerRatingDowngraded(ratingDowngradeChecker.isIssuerDowngraded(securityInfo));
        shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(securityInfo));
        shared.setGuarantorRatingDowngraded(ratingDowngradeChecker.isGuarantorDowngraded(mainReq.getGuarantorCode()));
        shared.setRequestInPoolIds(mainShared.getRequestInPoolIds());
        shared.setRequestOutPoolIds(mainShared.getRequestOutPoolIds());
        return shared;
    }

    /**
     * 取得调整项实际使用的标的代码
     *
     * 执行顺序：
     * 1. 有调整项代码时使用该代码，否则使用请求的主标的代码
     *
     * @param req 凭证与标的组合的调库提交请求
     * @param item 当前调整项
     * @return 当前调整项的标的证券代码
     */
    private String resolveItemSecurityCode(CrmwPoolAdjustSubmitReq req, CrmwPoolAdjustSubmitReq.AdjustItem item) {
        return item.getSecurityCode() == null || item.getSecurityCode().isEmpty()
                ? req.getSecurityCode() : item.getSecurityCode();
    }

    /**
     * 按标的债券同 ISIN 代码展开关联市场调整项，凭证保持不变
     *
     * 执行顺序：
     * 1. 查询标的同券代码，没有关联代码或待扩展项时结束
     * 2. 收集主券的手工、联动和互斥项，登记去重键及已通过调入项的容量占用
     * 3. 逐个读取关联标的主档，复用已加载池配置并查询该实际组合的当前状态
     * 4. 按母项生成关联项，逐码执行原规则并累计通过项容量，主项失败时同步阻断关联项
     * 5. 合并关联结果，任一市场失败时反向阻断同组；Excel 随后由共用出口统一转换业务提示
     *
     * @param req 当前凭证与标的的校验请求
     * @param mainShared 主券已加载的证券、池配置和组合状态
     * @param resultItems 待追加关联项的主券校验结果列表
     */
    private void expandRelatedMarketCodeItems(AdjustCheckReq req, AdjustSharedData mainShared,
                                              List<AdjustCheckDto.CheckResultItem> resultItems) {
        // 1. 读取主券同 ISIN 代码，没有关联或待扩展项时结束
        SecurityInfoBo mainSec = mainShared.getSecurityInfo();
        if (mainSec == null || resultItems == null || resultItems.isEmpty()) {
            return;
        }
        List<String> relatedCodes = new ArrayList<>();
        for (SecurityMarketDto related : securityMarketService.queryRelatedMarketCodes(mainSec.getWindCode())) {
            relatedCodes.add(related.getWindCode());
        }
        if (relatedCodes.isEmpty()) {
            return;
        }

        // 2. 收集主券母项，登记去重键和已通过调入项的容量占用
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
        // 3. 逐个读取关联标的主档，并查询其与原凭证组合的当前状态
        for (String relatedCode : relatedCodes) {
            SecurityInfoBo relatedSec = crmwPoolAdjustMapper.querySecurityBoByCode(relatedCode);
            if (relatedSec != null) {
                mainShared.getKnownSecurityCodes().add(relatedCode);
            }
            // 按关联标的与原凭证组合加载独立在池状态及校验标志
            AdjustSharedData relatedShared = relatedSec == null ? null
                    // 按标的关联码加载组合状态，沿用主券已校验的评级主体和本次选池集合
                    : loadSharedDataForSecurity(relatedCode, relatedSec, mainShared, req);

            // 4. 按母项生成关联项并逐码校验，继承主项失败及累计容量
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
                relatedItem.setSourceSecurityCode(mainShared.getCrmwScode() + "|" + mainSec.getWindCode());
                relatedItem.setCrmwScode(mainShared.getCrmwScode());
                relatedItem.setCrmwStype(mainShared.getCrmwStype());
                relatedItem.setCrmwName(req.getCrmwName());

                if (relatedSec == null || relatedShared == null) {
                    relatedItem.setCanAdjust(false);
                    relatedItem.setFailReasons(Collections.singletonList(
                            "关联证券代码" + relatedCode + "在系统中不存在"));
                } else {
                    relatedItem.setSecurityShortName(relatedSec.getShortName());
                    relatedItem.setSecurityType(relatedSec.getSecurityType());
                    int baseCount = crmwPoolAdjustMapper.queryPoolCurrentCount(mother.getTargetPoolId());
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
        // 5. 合并关联项，任一市场失败时反向阻断整个调库组
        resultItems.addAll(relatedResults);
        // 任一市场失败反向阻断整个调库组，避免只提交部分市场
        propagateRelatedMarketFailure(resultItems);
    }

    /**
     * 将同券跨市场的失败原因传给整组
     *
     * 同券跨市场调整需要整组通过，避免只调整其中一个市场
     *
     * 执行顺序：
     * 1. 按分组收集所有失败项及其具体原因
     * 2. 将同组原本通过的结果标为不可调整并写入原因
     *
     * @param resultItems 待处理的调库校验结果
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
     * 生成投资池的层级全名
     *
     * 执行顺序：
     * 1. 按 ID 取得池名称，找不到池时返回空字符串
     * 2. 有父池时递归取得父池全名，并用斜线连接当前池名称
     *
     * @param poolId 投资池 ID
     * @param poolMap 按池 ID 整理的投资池配置
     * @return 包含上级池的投资池全名
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
     * 保存启用的流程版本及其定义、节点、连线和审批配置
     * 同一次提交复用这份数据，判断流程能否无需人工审批完成，并生成初始审批步骤
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
         * 保存发起审批时用到的流程配置快照
         *
         * 执行顺序：
         * 1. 保存流程定义、启用版本、节点、连线及审批配置索引
         *
         * @param definition 流程定义
         * @param activeVersion 启用的流程版本
         * @param nodeMap 按节点 ID 整理的流程节点
         * @param edges 流程节点之间的连线
         * @param approvalConfigMap 按节点 ID 整理的审批配置
         * @param approvalHandlerMap 按审批配置 ID 整理的处理人配置
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
     * 保存候选审批人的 ID 和姓名，供生成审批待办时使用
     */
    private static class HandlerTarget {
        final String handlerId;
        final String handlerName;

        /**
         * 记录候选审批人的身份
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
     * 保存一次组合提交共用的标的资料、投资池配置、在池状态和流程快照
     * 调入和调出处理共用这些数据，并在提交期间记录各调整分组的批次号和已生成的调库日志
     */
    private static class SubmitSharedData {

        /** 证券基础信息（来自 rrs_securityinfo） */
        final SecurityInfoBo securityInfo;

        /** 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称 */
        final Map<Long, InvestmentPoolBo> poolMap;

        /** 证券当前有效所在池 ID 集合（ip_pool_status_crmw.audit_status='20'） */
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

        /** 本次生成的日志，供快照使用实际标的身份 */
        final Map<Long, IpAdjustLogBo> submittedLogs = new HashMap<>();

        /**
         * 保存提交阶段各调整项共用的数据
         *
         * 执行顺序：
         * 1. 保存标的和池数据、流程状态及流程快照，并保留本次提交的批次号上下文
         *
         * @param securityInfo 标的证券资料
         * @param poolMap 按池 ID 整理的投资池配置
         * @param currentPoolIds 当前组合所在的投资池 ID
         * @param poolRelationMap 按池及关系类型整理的关联池配置
         * @param hasPendingProcess 标的是否存在进行中的调库流程
         * @param securityInObservePool 标的证券是否在观察池
         * @param issuerInObservePool 标的发行人是否在观察池
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
     * 记录同一次提交共用的提交时间和批次号流水
     * 无审批流程、调入和调出分别计数，批量调整和 Excel 导入的各组共用这份数据
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
