package com.znty.rrs.service;

import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.common.enums.ApprovalStrategy;

import com.znty.rrs.common.enums.FlowStatus;

import com.znty.rrs.common.enums.NodeType;

import com.znty.rrs.common.enums.ItemType;

import com.znty.rrs.common.enums.ProcessAction;

import com.znty.rrs.common.enums.StepStatus;

import com.znty.rrs.common.enums.AuditStatus;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AdjustType;
import com.znty.rrs.common.enums.CategoryType;

import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AttachmentCategory;

import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.common.enums.PoolStatus;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.util.AdjustStepHandlerExcludeUtil;
import com.znty.rrs.common.util.CreditBondRemainTermUtil;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
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
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.BatchCompanyAdjustCheckData;
import com.znty.rrs.entity.forbiddenpooladjust.BatchCompanyAdjustCheckData.CompanyState;
import com.znty.rrs.entity.forbiddenpooladjust.CompanyAdjustCheckContext;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.math.BigDecimal;
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
import java.util.stream.Collectors;

/**
 * 校验和提交发行主体的禁投池调入调出申请
 *
 * 提供候选主体、旗下债券、目标投资池和当前池状态查询，按池规则判断主项及联动互斥配套项
 * 联动池沿用主项的调入或调出方向，调入命中互斥池时追加对应调出项
 * 涉及黑名单质押库时，通过 {@link PledgeBlacklistRuleService} 按调整后的主体状态复核入池或出池条件
 * 提交时保存主体调库日志与审批步骤，直通或 {@link ForbiddenPoolAdjustFlowService} 终审通过后更新主体池状态
 */
@Service
public class ForbiddenPoolAdjustService {
    /** 统一证券市场及同券查询组件 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 短时间重复提交判定窗口（秒） */
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;

    /** 白名单池 ID 集合：主体在这些池中时符合白名单条件；当前写死空集，后续配置后补 queryIssuerInWhitelistPools 查询 */
    private static final Set<Long> WHITELIST_POOL_IDS = Collections.emptySet();

    /** 用于校验调库管理员权限的用户 ID */
    private static final String ADMIN_USER_ID = "1";

    /** 禁投池主体调库记录固定证券类型 */
    private static final String COMPANY_SECURITY_TYPE = CategoryType.COMPANY.getCode();

    /** 主体存在在途调库流程的提示 */
    static final String COMPANY_PENDING_PROCESS_FAILURE = "主体存在进行中的调库流程";

    /** 禁投池、观察池、黑名单质押库和重点观察名单 ID */
    private static final Set<Long> ALLOWED_MANUAL_POOL_IDS = new HashSet<>();
    static {
        ALLOWED_MANUAL_POOL_IDS.add(15L);
        ALLOWED_MANUAL_POOL_IDS.add(16L);
        ALLOWED_MANUAL_POOL_IDS.add(17L);
        ALLOWED_MANUAL_POOL_IDS.add(23L);
    }

    /** 禁投池主体调整数据访问组件 */
    @Resource
    private ForbiddenPoolAdjustMapper forbiddenPoolAdjustMapper;

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

    /** 评级下调判定组件（主体/展望/担保人评级下调判断，查 wind_cbondissuerrating） */
    @Resource
    private RatingDowngradeChecker ratingDowngradeChecker;

    /** 黑名单质押库三条件统一判定服务 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;

    /** 信用债白名单调入流程 Key */
    private static final String FLOW_KEY_WHITELIST_INBOUND = "bond:whitelist-inbound";

    /** 是否启用白名单调入流程；当前业务未启用，恢复时改为 true */
    private static final boolean WHITELIST_FLOW_ENABLED = false;

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
     * 分页查询可调整的发行主体，并批量回填旗下债券数量
     *
     * 数据源为全量主体公司简介 {@code wind_compintroduction}
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 批量查询当前页主体的债券数量
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 主体禁投池查询条件
     * @return 可调整的发行主体和分页信息
     */
    public PageResult<ForbiddenPoolAdjustDto> queryCompanyPage(ForbiddenPoolAdjustReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ForbiddenPoolAdjustDto> records = forbiddenPoolAdjustMapper.queryCompanyPage(req);
        PageInfo<ForbiddenPoolAdjustDto> pageInfo = new PageInfo<>(records);
        // 批量查询当前页主体的债券数量
        fillCompanyBondCount(records);
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询公司主体详情
     *
     * 执行顺序：
     * 1. 校验主体代码非空
     * 2. 查询主体旗下债券数量
     *
     * @param req 主体禁投池查询条件
     * @return 禁投投资池调库
     */
    public ForbiddenPoolAdjustDto queryCompanyDetail(ForbiddenPoolAdjustReq req) {
        // 1. 校验主体代码非空
        validateCompanyCode(req.getCompanyCode());
        ForbiddenPoolAdjustDto dto = forbiddenPoolAdjustMapper.queryCompanyDetail(req.getCompanyCode());
        if (dto == null) {
            throw new BizException(404, "公司主体不存在，companyCode=" + req.getCompanyCode());
        }
        // 2. 查询主体旗下债券数量
        fillCompanyBondCount(Collections.singletonList(dto));
        return dto;
    }

    /**
     * 查询当前主体和旗下债券所在池
     *
     * 执行顺序：
     * 1. 校验主体代码非空
     * 2. 校验主体记录存在且类型正确
     * 3. 加载投资池全路径名称映射
     * 4. 填充主体所在池全路径
     * 5. 填充旗下债券所在池全路径
     *
     * @param req 主体禁投池查询条件
     * @return 投资池状态资料包
     */
    public ForbiddenPoolAdjustDto.PoolStatusBundle queryCompanyPoolStatus(ForbiddenPoolAdjustReq req) {
        // 1. 校验主体代码非空
        validateCompanyCode(req.getCompanyCode());
        // 2. 校验主体记录存在且类型正确
        queryCompanyDetail(req);
        ForbiddenPoolAdjustDto.PoolStatusBundle bundle = new ForbiddenPoolAdjustDto.PoolStatusBundle();
        List<ForbiddenPoolAdjustDto.PoolStatus> companyPools =
                forbiddenPoolAdjustMapper.queryCompanyPoolStatusList(req.getCompanyCode());
        List<ForbiddenPoolAdjustDto.PoolStatus> bondPools =
                forbiddenPoolAdjustMapper.queryCompanyBondPoolList(req.getCompanyCode());
        // 3. 加载投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        // 4. 填充主体所在池全路径
        fillCompanyPoolFullName(companyPools, poolFullNameMap);
        // 5. 填充旗下债券所在池全路径
        fillCompanyPoolFullName(bondPools, poolFullNameMap);
        bundle.setCompanyCurrentPools(companyPools);
        bundle.setCompanyBondCurrentPools(bondPools);
        return bundle;
    }

    /**
     * 查询主体旗下债券明细
     *
     * 执行顺序：
     * 1. 校验主体代码非空
     * 2. 校验主体记录存在且类型正确
     * 3. 加载投资池全路径名称映射
     *
     * @param req 主体禁投池查询条件
     * @return 主体债券列表
     */
    public List<ForbiddenPoolAdjustDto.CompanyBond> queryCompanyBondList(ForbiddenPoolAdjustReq req) {
        // 1. 校验主体代码非空
        validateCompanyCode(req.getCompanyCode());
        if (req.getTargetPoolId() == null) {
            throw new BizException("查看旗下债券明细时投资池 ID 不能为空");
        }
        // 2. 校验主体记录存在且类型正确
        queryCompanyDetail(req);
        List<ForbiddenPoolAdjustDto.CompanyBond> bonds = forbiddenPoolAdjustMapper.queryCompanyBondList(
                req.getCompanyCode(), req.getTargetPoolId());
        // 3. 加载投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (ForbiddenPoolAdjustDto.CompanyBond bond : bonds) {
            if (bond.getTargetPoolId() != null && poolFullNameMap.containsKey(bond.getTargetPoolId())) {
                bond.setPoolName(poolFullNameMap.get(bond.getTargetPoolId()));
            }
        }
        return bonds;
    }

    /**
     * 查询当前用户可操作的三个风险池
     *
     * 执行顺序：
     * 1. 按ID 列表列表查询投资池
     * 2. 检查禁投池调整配置不完整，必须存在 poolId=15、16、17、23
     * 3. 判断当前用户是否为管理员
     * 4. 查询互斥关系列表
     * 5. 查询投资池当前数量列表
     *
     * @param req 主体禁投池查询条件
     * @return 投资池列表
     */
    public List<PoolDto> queryCompanyAdjustPoolList(ForbiddenPoolAdjustReq req) {
        List<InvestmentPoolBo> configuredPools = investmentPoolMapper.queryPoolByIdsList(
                new ArrayList<>(ALLOWED_MANUAL_POOL_IDS));
        if (configuredPools.size() != ALLOWED_MANUAL_POOL_IDS.size()) {
            throw new BizException("禁投池调整配置不完整，必须存在 poolId=15、16、17、23");
        }
        configuredPools.sort(Comparator.comparing(InvestmentPoolBo::getId));
        for (InvestmentPoolBo pool : configuredPools) {
            if (!PoolStatus.ENABLED.getCode().equals(pool.getStatus())
                    || Integer.valueOf(1).equals(pool.getIsDeleted())) {
                throw new BizException("禁投池调整目标池未启用，poolId=" + pool.getId());
            }
        }
        // 判断当前用户是否为管理员
        if (!isAdminUser(req.getCurrentUserId())) {
            // 非管理员：解析当前用户 ID 并按权限过滤可调池
            Long userId = parseCurrentUserId(req.getCurrentUserId());
            // 查询当前用户直接或通过角色拥有可调整权限的投资池 ID
            Set<Long> adjustablePoolIds = queryAdjustablePoolIdsByUser(userId);
            configuredPools = configuredPools.stream()
                    .filter(pool -> adjustablePoolIds.contains(pool.getId()))
                    .collect(Collectors.toList());
        }
        List<PoolRelationBo> relations = investmentPoolMapper.queryMutexRelationList();
        Map<Long, List<Long>> inMutexMap = new HashMap<>();
        Map<Long, List<Long>> outMutexMap = new HashMap<>();
        for (PoolRelationBo relation : relations) {
            if (RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())) {
                inMutexMap.computeIfAbsent(relation.getPoolId(), key -> new ArrayList<>())
                        .add(relation.getRelationPoolId());
            } else if (RelationType.OUT_MUTEX.getCode().equals(relation.getRelationType())) {
                outMutexMap.computeIfAbsent(relation.getPoolId(), key -> new ArrayList<>())
                        .add(relation.getRelationPoolId());
            }
        }
        Map<Long, Integer> countMap = forbiddenPoolAdjustMapper.queryPoolCurrentCountList().stream()
                .collect(Collectors.toMap(PoolDto::getId, PoolDto::getCurrentCount));
        return configuredPools.stream()
                // 转换投资池为树节点 DTO
                .map(pool -> toPoolDto(pool, inMutexMap, outMutexMap, countMap))
                .collect(Collectors.toList());
    }

    /**
     * 校验主体调整并生成关系配置对应的自动项
     *
     * 执行顺序：
     * 1. 使用实时主体和池数据校验申请，按投资池关系生成配套项
     *
     * @param req 单个主体的导入或清空调库请求
     * @return 调库校验结果及流程候选
     */
    public AdjustCheckDto checkCompanyAdjust(ForbiddenPoolAdjustCheckReq req) {
        // 单笔入口保持实时读取，批量入口显式传入本次校验的数据
        return checkCompanyAdjust(req, null);
    }

    /**
     * 基于当前批量匹配结果加载一次公共配置，不缓存池状态和流程结果
     *
     * 执行顺序：
     * 1. 查询投资池列表
     *
     * @param companies 本次识别成功并有待校验行的主体
     * @return 主体调库校验上下文
     */
    public CompanyAdjustCheckContext createCompanyCheckContext(Map<String, ForbiddenPoolAdjustDto> companies) {
        return new CompanyAdjustCheckContext(companies, investmentPoolMapper.queryPoolList(),
                forbiddenPoolAdjustMapper.queryAllPoolRelationList());
    }

    /**
     * 主体批量校验复用本次匹配资料，逐项规则与单笔入口一致
     *
     * 执行顺序：
     * 1. 复用本批预读数据，按普通校验口径拦截失败，并允许补充联动和互斥项
     *
     * @param req 单个主体的导入或清空调库请求
     * @param context 本批已查的主体与池配置，清空出库按当前数据读取
     * @return 调库校验结果及流程候选
     */
    public AdjustCheckDto checkCompanyAdjust(ForbiddenPoolAdjustCheckReq req, CompanyAdjustCheckContext context) {
        // 普通主体入口沿用原规则和拦截方式
        return checkCompanyAdjust(req, context, false, true);
    }

    /**
     * 执行主体 Excel 调库校验，保留业务失败原因供页面提示
     *
     * 执行顺序：
     * 1. 校验发行主体本次调库所需的规则和流程
     *
     * @param req 单个主体的导入或清空调库请求
     * @param context 本批已查的主体与池配置，清空出库按当前数据读取
     * @param allowLinkMutex 是否启用联动和互斥调整
     * @return 可提交状态、调整提示和流程候选
     */
    AdjustCheckDto checkCompanyExcelAdjust(ForbiddenPoolAdjustCheckReq req, CompanyAdjustCheckContext context,
                                            boolean allowLinkMutex) {
        // 运行完整主体校验，在生成流程候选前将业务失败转为提示
        return checkCompanyAdjust(req, context, true, allowLinkMutex);
    }

    /**
     * 校验发行主体本次调库所需的规则和流程
     *
     * 执行顺序：
     * 1. 校验主体代码非空
     * 2. 查询主体详情并校验存在
     * 3. 校验手工调整池 ID 合法性
     * 4. 复用本类内部的完整关系和流程校验实现
     * 5. 主体手工及联动项统一携带主数据身份，校验失败项也保留主体全称
     *
     * @param req 单个主体的导入或清空调库请求
     * @param context 本批已查的主体与池配置，清空出库按当前数据读取
     * @param excelWarningsOnly 是否将 Excel 原业务失败保留为可提交提示
     * @param allowLinkMutex 是否启用联动和互斥调整
     * @return 调库校验结果及流程候选
     */
    private AdjustCheckDto checkCompanyAdjust(ForbiddenPoolAdjustCheckReq req, CompanyAdjustCheckContext context,
                                              boolean excelWarningsOnly, boolean allowLinkMutex) {
        // 1. 校验主体代码非空
        validateCompanyCode(req.getCompanyCode());
        ForbiddenPoolAdjustReq detailReq = new ForbiddenPoolAdjustReq();
        detailReq.setCompanyCode(req.getCompanyCode());
        // 2. 查询主体详情并校验存在
        ForbiddenPoolAdjustDto company = context == null
                ? queryCompanyDetail(detailReq) : context.getCompanyMap().get(req.getCompanyCode());
        if (company == null) {
            throw new BizException(404, "公司主体不存在，companyCode=" + req.getCompanyCode());
        }
        // 3. 校验手工调整池 ID 合法性
        validateManualCheckPoolIds(req.getItems());
        AdjustCheckReq checkReq = new AdjustCheckReq();
        checkReq.setSecurityCode(company.getCompanyCode());
        checkReq.setSecurityShortName(company.getCompanyFullName());
        checkReq.setSecurityType(COMPANY_SECURITY_TYPE);
        List<AdjustCheckReq.CheckItem> items = new ArrayList<>();
        for (ForbiddenPoolAdjustCheckReq.CheckItem source : req.getItems()) {
            AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
            item.setTargetPoolId(source.getTargetPoolId());
            item.setTargetPoolName(source.getTargetPoolName());
            item.setPoolType(source.getPoolType());
            item.setAdjustMode(source.getAdjustMode());
            items.add(item);
        }
        checkReq.setItems(items);
        // 4. 复用本类内部的完整关系和流程校验实现
        AdjustCheckDto result = context == null && !excelWarningsOnly
                // 执行相同的完整校验规则，按需使用当前批次公共数据
                ? checkAdjust(checkReq) : checkAdjust(checkReq, context, excelWarningsOnly, allowLinkMutex);
        // 5. 主体手工及联动项统一携带主数据身份，校验失败项也保留主体全称
        for (AdjustCheckDto.CheckResultItem item : result.getItems()) {
            if (item.getSecurityCode() == null || item.getSecurityCode().trim().isEmpty()
                    || company.getCompanyCode().equals(item.getSecurityCode())) {
                item.setSecurityCode(company.getCompanyCode());
                item.setSecurityShortName(company.getCompanyFullName());
                item.setSecurityType(COMPANY_SECURITY_TYPE);
                item.setSourceSecurityCode(company.getCompanyCode());
            }
        }
        return result;
    }

    /**
     * 提交主体调整申请
     *
     * 执行顺序：
     * 1. 把申请和本次上传附件交给提交入口，前端原始文件名列表留空
     *
     * @param req Excel 导入服务构建的调库明细、调整来源和所选流程
     * @param files 本次提交的附件
     * @return 禁投投资池调库提交
     */
    @Transactional(rollbackFor = Exception.class)
    public ForbiddenPoolAdjustSubmitDto addCompanyAdjustLog(ForbiddenPoolAdjustSubmitReq req,
                                                             List<MultipartFile> files) {
        return addCompanyAdjustLog(req, files, null);
    }

    /**
     * 提交主体调整申请及附件（可带前端原始文件名 JSON 数组）
     *
     * 执行顺序：
     * 1. 主体 Excel 调库请通过证券池 Excel 导入提交时终止处理
     * 2. 普通主体申请使用共同的提交逻辑
     *
     * @param req Excel 导入服务构建的调库明细、调整来源和所选流程
     * @param files 本次提交的附件
     * @param originalFileNameListJson 附件原始名称
     * @return 禁投投资池调库提交
     */
    @Transactional(rollbackFor = Exception.class)
    public ForbiddenPoolAdjustSubmitDto addCompanyAdjustLog(ForbiddenPoolAdjustSubmitReq req,
                                                             List<MultipartFile> files,
                                                             String originalFileNameListJson) {
        // 1. 拦截从普通主体入口提交的 Excel 调库请求
        if (isExcelAdjustType(req.getAdjustType())) {
            throw new BizException("主体 Excel 调库请通过证券池 Excel 导入提交");
        }
        // 2. 使用主体禁投池提交逻辑处理普通申请
        return submitCompanyAdjust(req, files, originalFileNameListJson);
    }

    /**
     * 提交 Excel 导入或清空生成的主体调库申请
     *
     * 执行顺序：
     * 1. 复用主体身份校验、请求转换和调库日志写入
     *
     * @param req Excel 导入服务构建的调库明细、调整来源和所选流程
     * @return 主体调库提交结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ForbiddenPoolAdjustSubmitDto addCompanyExcelAdjustLog(ForbiddenPoolAdjustSubmitReq req) {
        // 沿用主体提交和流程写入逻辑
        return submitCompanyAdjust(req, Collections.emptyList(), null);
    }

    /**
     * 用本次批量读取的数据组装一个主体的日志和初始流程步骤
     *
     * 执行顺序：
     * 1. 校验主体代码并读取本批已加载的主体信息
     * 2. 校验主体提交数据并转换成内部调库请求
     * 3. 校验调库请求的必填项和调整方向
     * 4. 按调入、调出顺序解析流程并准备调库日志及审批步骤
     * 5. 复核可直通记录的 Excel 来源和目标池
     *
     * @param request Excel 主体提交请求
     * @param batch 主档、池和流程快照的本次提交数据
     * @return 待写入的日志和步骤，特殊流程返回 null 交给原逐组入口
     */
    List<ExcelCompanySubmitRow> planExcelCompanySubmit(ForbiddenPoolAdjustSubmitReq request,
                                                       ExcelCompanyBatchContext batch) {
        // 1. 校验主体代码并读取本批已加载的主体信息
        validateCompanyCode(request.getCompanyCode());
        ForbiddenPoolAdjustDto company = batch.companies.get(request.getCompanyCode());
        if (company == null) {
            throw new BizException(404, "公司主体不存在，companyCode=" + request.getCompanyCode());
        }
        // 2. 校验主体提交数据并转换成内部调库请求
        validateSubmitCompany(request, company);
        // 转换主体提交请求为本类内部通用调库请求
        SecurityPoolAdjustSubmitReq req = convertCompanySubmitReq(request, company);
        // 3. 校验调库请求的必填项和调整方向
        validateSubmitReq(req);
        SubmitSharedData shared = new SubmitSharedData(null, batch.pools, Collections.emptySet(),
                Collections.emptyMap(), false, false, false, batch.flows, batch.batchNoContext);
        List<ExcelCompanySubmitRow> rows = new ArrayList<>();
        Map<String, List<IpAdjustStepBo>> stepsByBatchNo = new HashMap<>();
        // 4. 按调入、调出顺序解析流程并准备调库日志及审批步骤
        for (AdjustMode mode : Arrays.asList(AdjustMode.IN, AdjustMode.OUT)) {
            for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                if (!mode.getCode().equals(item.getAdjustMode())) {
                    continue;
                }
                // 获取同组手工调库项，联动/互斥项按手工项共用流程和批次号
                SecurityPoolAdjustSubmitReq.AdjustItem manual = resolveManualSubmitItem(req, item);
                Long flowId = manual.getFlowId();
                if (flowId == null && manual.getFlowKey() != null && !manual.getFlowKey().isEmpty()) {
                    String key = manual.getFlowKey();
                    if (!batch.flowIds.containsKey(key)) {
                        // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
                        batch.flowIds.put(key, resolveFlowIdFromItem(manual));
                    }
                    flowId = batch.flowIds.get(key);
                }
                if (flowId != null && !batch.flows.containsKey(flowId)) {
                    // 为指定流程 ID 构建运行时快照（定义 + 活跃版本 + 节点索引 + 连线列表）
                    batch.flows.put(flowId, buildFlowSnapshot(flowId));
                }
                FlowSnapshot snapshot = batch.flows.get(flowId);
                boolean direct = flowId == null
                        // 判断流程是否为直通流程（开始后无需人工待办即可到结束节点）
                        || (snapshot != null && batch.directFlows.computeIfAbsent(flowId, id -> isDirectFlow(snapshot)));
                // 构建调库记录实体（ip_adjust_log），由提交申请逻辑调用
                IpAdjustLogBo log = buildAdjustLog(req, item, manual, shared);
                // 获取或生成同组调库批次号
                log.setAdjustBatchNo(resolveAdjustBatchNo(req, item, flowId == null, shared));
                log.setAuditStatus(direct ? AuditStatus.APPROVED.getCode() : AuditStatus.SUBMITTED.getCode());
                List<IpAdjustStepBo> steps = Collections.emptyList();
                // 判断提交项是否为手工调库项
                if (isManualSubmitItem(item) && snapshot != null) {
                    List<IpAdjustStepBo> groupSteps = stepsByBatchNo.computeIfAbsent(log.getAdjustBatchNo(),
                            key -> new ArrayList<>());
                    int start = groupSteps.size();
                    // 先生成步骤，批量插入日志后再填每条步骤的日志 ID
                    boolean finished = createInitialSteps(null, log.getAdjustBatchNo(), snapshot,
                            req.getAdjusterId(), req.getAdjusterName(), groupSteps, batch.handlersByNodeId);
                    if (!direct && finished) {
                        // 少数流程会在生成步骤后即时终审，继续沿用原批次状态流转
                        return null;
                    }
                    steps = new ArrayList<>(groupSteps.subList(start, groupSteps.size()));
                }
                rows.add(new ExcelCompanySubmitRow(log, steps));
            }
        }
        // 5. 复核可直通记录的 Excel 来源和目标池
        List<IpAdjustLogBo> approved = rows.stream().map(row -> row.log)
                .filter(log -> AuditStatus.APPROVED.getCode().equals(log.getAuditStatus()))
                .collect(Collectors.toList());
        // 直通复核沿用主体 Excel 的存在性校验
        Set<String> excelBatchNos = collectExcelBatchNos(approved);
        for (IpAdjustLogBo log : approved) {
            // 判断主体调库日志是否属于 Excel 导入或清空申请
            if (!isExcelCompanyLog(log, excelBatchNos)) {
                return null;
            }
            if (!batch.pools.containsKey(log.getTargetPoolId())) {
                // 抛出包含实际调整对象上下文的复核异常
                throwFinalRecheckFailure(log, "目标投资池不存在或已停用");
            }
        }
        return rows;
    }

    /**
     * 复用主体身份校验、请求转换和调库日志写入
     *
     * 执行顺序：
     * 1. 校验主体代码非空
     * 2. 查询主体详情并校验存在
     * 3. 校验提交参数与主体一致性
     * 4. 转换为主体对应的调库提交请求
     * 5. 调用本类独立复制的调库提交实现
     *
     * @param req 主体调库请求
     * @param files 本次提交的附件
     * @param originalFileNameListJson 附件原始名称
     * @return 主体调库提交结果
     */
    private ForbiddenPoolAdjustSubmitDto submitCompanyAdjust(ForbiddenPoolAdjustSubmitReq req,
                                                             List<MultipartFile> files,
                                                             String originalFileNameListJson) {
        // 1. 校验主体代码非空
        validateCompanyCode(req.getCompanyCode());
        ForbiddenPoolAdjustReq detailReq = new ForbiddenPoolAdjustReq();
        detailReq.setCompanyCode(req.getCompanyCode());
        // 2. 查询主体详情并校验存在
        ForbiddenPoolAdjustDto company = queryCompanyDetail(detailReq);
        // 3. 校验提交参数与主体一致性
        validateSubmitCompany(req, company);
        // 4. 转换为主体对应的调库提交请求
        SecurityPoolAdjustSubmitReq submitReq = convertCompanySubmitReq(req, company);
        // 5. 调用本类独立复制的调库提交实现
        AdjustSubmitDto result = addAdjustLog(submitReq, files, originalFileNameListJson);
        ForbiddenPoolAdjustSubmitDto dto = new ForbiddenPoolAdjustSubmitDto();
        dto.setCompanyCode(result.getSecurityCode());
        dto.setSubmitCount(result.getSubmitCount());
        dto.setLogIds(result.getLogIds());
        return dto;
    }

    /**
     * 查询主体调库记录
     *
     * 执行顺序：
     * 1. 转换为主体对应的调库查询请求
     * 2. 查询本模块主体调库记录
     *
     * @param req 主体禁投池查询条件
     * @return 调库日志列表
     */
    public List<AdjustLogDto> queryCompanyAdjustLogList(ForbiddenPoolAdjustReq req) {
        // 1. 转换为主体对应的调库查询请求
        SecurityPoolAdjustReq securityReq = convertCompanyQueryReq(req);
        // 2. 查询本模块主体调库记录
        return queryAdjustLogList(securityReq);
    }

    /**
     * 查询主体调库流程步骤
     *
     * 执行顺序：
     * 1. 转换为主体对应的调库查询请求
     * 2. 查询本模块主体调库流程步骤
     *
     * @param req 主体禁投池查询条件
     * @return 调库审批步骤明细列表
     */
    public List<IpAdjustStepDto> queryCompanyAdjustStepList(ForbiddenPoolAdjustReq req) {
        // 1. 转换为主体对应的调库查询请求
        SecurityPoolAdjustReq securityReq = convertCompanyQueryReq(req);
        // 2. 查询本模块主体调库流程步骤
        return queryAdjustStepList(securityReq);
    }

    /**
     * 批量回填主体旗下债券数量（主数据口径）
     *
     * 统计 {@code rrs_securityinfo} 中 {@code issuer_code} 匹配且 {@code category_type=bond}
     * 的证券总数（含 CRMW，展示不排除）。在池汇总见 {@code queryCompanyBondPoolList}（普通池 + CRMW 池）
     * 主体生效后的旗下债券同步也包含普通债、ABS、CRMW
     *
     * 执行顺序：
     * 1. 查询主体债券数量列表
     * 2. 批量回填主体旗下债券数量（主数据口径）
     *
     * @param companies 主体列表
     */
    private void fillCompanyBondCount(List<ForbiddenPoolAdjustDto> companies) {
        if (companies == null || companies.isEmpty()) {
            return;
        }
        List<String> companyCodes = companies.stream()
                .map(ForbiddenPoolAdjustDto::getCompanyCode)
                .filter(code -> code != null && !code.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (companyCodes.isEmpty()) {
            return;
        }
        Map<String, Integer> countMap = forbiddenPoolAdjustMapper.queryCompanyBondCountList(companyCodes).stream()
                .collect(Collectors.toMap(ForbiddenPoolAdjustDto.CompanyBondCount::getCompanyCode,
                        ForbiddenPoolAdjustDto.CompanyBondCount::getBondCount));
        for (ForbiddenPoolAdjustDto company : companies) {
            company.setCompanyBondCount(countMap.containsKey(company.getCompanyCode())
                    ? countMap.get(company.getCompanyCode()) : 0);
        }
    }

    /**
     * 填充主体及旗下债券所在池全路径
     *
     * 执行顺序：
     * 1. 逐条按目标池 ID 查找全路径，只用非空路径覆盖原池名
     *
     * @param pools 投资池列表
     * @param poolFullNameMap 按池 ID 整理的完整池名
     */
    private void fillCompanyPoolFullName(List<ForbiddenPoolAdjustDto.PoolStatus> pools,
                                         Map<Long, String> poolFullNameMap) {
        for (ForbiddenPoolAdjustDto.PoolStatus pool : pools) {
            String fullName = poolFullNameMap.get(pool.getTargetPoolId());
            if (fullName != null && !fullName.isEmpty()) {
                pool.setPoolName(fullName);
            }
        }
    }

    /**
     * 校验主体代码
     *
     * 执行顺序：
     * 1. 检查主体代码不能为空
     *
     * @param companyCode 发行主体代码
     */
    private void validateCompanyCode(String companyCode) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            throw new BizException("主体代码不能为空");
        }
    }

    /**
     * 检查调库项和目标池 ID 是否填写
     *
     * 执行顺序：
     * 1. 检查调库项不能为空
     * 2. 检查目标池 ID 不能为空
     *
     * @param items 明细列表
     */
    private void validateManualCheckPoolIds(List<ForbiddenPoolAdjustCheckReq.CheckItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        for (ForbiddenPoolAdjustCheckReq.CheckItem item : items) {
            if (item.getTargetPoolId() == null) {
                throw new BizException("调库项的目标投资池 ID 不能为空");
            }
        }
    }

    /**
     * 校验主体提交数据
     *
     * 执行顺序：
     * 1. 检查调库项不能为空
     *
     * @param req Excel 导入服务构建的调库明细、调整来源和所选流程
     * @param company 主体
     */
    private void validateSubmitCompany(ForbiddenPoolAdjustSubmitReq req, ForbiddenPoolAdjustDto company) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        for (ForbiddenPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if ((StringUtils.hasText(item.getSecurityCode()) && !company.getCompanyCode().equals(item.getSecurityCode()))
                    || (StringUtils.hasText(item.getSecurityType()) && !COMPANY_SECURITY_TYPE.equals(item.getSecurityType()))) {
                throw new BizException("主体调库只接受主体调整项，请重新校验后提交");
            }
        }
    }

    /**
     * 转换主体提交请求为本类内部通用调库请求
     *
     * 执行顺序：
     * 1. 把主体编码、名称及操作人信息转换成通用调库提交请求
     * 2. 逐项填入目标池、调整方向和所选流程
     *
     * @param req Excel 导入服务构建的调库明细、调整来源和所选流程
     * @param company 主体
     * @return 待提交的证券调库申请
     */
    private SecurityPoolAdjustSubmitReq convertCompanySubmitReq(ForbiddenPoolAdjustSubmitReq req,
                                                                 ForbiddenPoolAdjustDto company) {
        SecurityPoolAdjustSubmitReq target = new SecurityPoolAdjustSubmitReq();
        target.setSecurityCode(company.getCompanyCode());
        target.setSecurityShortName(company.getCompanyFullName());
        target.setSecurityType(COMPANY_SECURITY_TYPE);
        target.setAdjustType(req.getAdjustType());
        target.setAdjustReason(req.getAdjustReason());
        target.setAdjustAdvice(req.getAdjustAdvice());
        target.setAdjusterId(req.getAdjusterId());
        target.setAdjusterName(req.getAdjusterName());
        List<SecurityPoolAdjustSubmitReq.AdjustItem> items = new ArrayList<>();
        for (ForbiddenPoolAdjustSubmitReq.AdjustItem source : req.getItems()) {
            SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
            item.setSecurityCode(source.getSecurityCode());
            item.setSecurityShortName(source.getSecurityShortName());
            item.setSecurityType(source.getSecurityType());
            item.setTargetPoolId(source.getTargetPoolId());
            item.setTargetPoolName(source.getTargetPoolName());
            item.setPoolType(source.getPoolType());
            item.setAdjustMode(source.getAdjustMode());
            item.setItemTag(source.getItemTag());
            item.setAdjustGroupKey(source.getAdjustGroupKey());
            item.setFlowId(source.getFlowId());
            item.setFlowKey(source.getFlowKey());
            item.setFlowType(source.getFlowType());
            item.setAdjustmentNote(source.getAdjustmentNote());
            item.setCreditReportFileIndexes(source.getCreditReportFileIndexes());
            item.setMaterialFileIndexes(source.getMaterialFileIndexes());
            item.setCreditReportSourceAttachmentIds(source.getCreditReportSourceAttachmentIds());
            item.setMaterialSourceAttachmentIds(source.getMaterialSourceAttachmentIds());
            items.add(item);
        }
        target.setItems(items);
        return target;
    }

    /**
     * 转换主体查询请求为内部通用查询请求
     *
     * 执行顺序：
     * 1. 依次设置证券代码、目标投资池 ID、调库日志ID、调库批次号等字段
     *
     * @param req 主体禁投池查询条件
     * @return 证券投资池调库
     */
    private SecurityPoolAdjustReq convertCompanyQueryReq(ForbiddenPoolAdjustReq req) {
        SecurityPoolAdjustReq target = new SecurityPoolAdjustReq();
        target.setSecurityCode(req.getCompanyCode());
        target.setTargetPoolId(req.getTargetPoolId());
        target.setAdjustLogId(req.getAdjustLogId());
        target.setAdjustBatchNo(req.getAdjustBatchNo());
        target.setCurrentUserId(req.getCurrentUserId());
        target.setAdjustDirection(req.getAdjustDirection());
        return target;
    }

    /**
     * 查询可调入/可调出的投资池列表（含互斥关系，树结构由前端自行组装）
     *
     * 执行顺序：
     * 1. 解析当前用户 ID
     * 2. 判断当前用户是否为管理员
     * 3. 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
     * 4. 查询各投资池当前有效证券数量
     * 5. InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
     *
     * @param req 当前用户为管理员时返回全量启用池，普通用户仅返回有可调整权限的池及其祖先节点
     * @return 投资池列表
     */
    public List<PoolDto> queryAdjustPoolList(SecurityPoolAdjustReq req) {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 解析当前用户 ID
        Long currentUserId = parseCurrentUserId(req.getCurrentUserId());
        // 2. 判断当前用户是否为管理员
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
        // 4. 查询各投资池当前有效证券数量
        Map<Long, Integer> currentCountMap = forbiddenPoolAdjustMapper.queryPoolCurrentCountList().stream()
                .collect(Collectors.toMap(PoolDto::getId, PoolDto::getCurrentCount));
        // 5. InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
        return allPools.stream()
                // InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
                .map(p -> toPoolDto(p, inMutexMap, outMutexMap, currentCountMap))
                .collect(Collectors.toList());
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
     * 1. 填充当前所在池的全路径名称
     *
     * @param req 需携带 securityCode
     * @return 证券投资池状态
     */
    public SecurityPoolStatusDto querySecurityPoolStatus(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityPoolStatusDto dto = new SecurityPoolStatusDto();
        List<PoolStatusDto> securityCurrentPools = forbiddenPoolAdjustMapper.querySecurityPoolStatusList(req.getSecurityCode());
        List<PoolStatusDto> issuerCurrentPools = forbiddenPoolAdjustMapper.queryIssuerPoolStatusList(req.getSecurityCode());
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
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 提交调库申请及附件
     *
     * 执行顺序：
     * 1. 把申请和本次上传附件交给提交入口，前端原始文件名列表留空
     *
     * @param req 调库提交请求
     * @param files 本次提交的附件
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req, List<MultipartFile> files) {
        return addAdjustLog(req, files, null);
    }

    /**
     * 提交禁投池调库申请及附件（可带前端原始文件名 JSON 数组）
     *
     * 执行顺序：
     * 1. 解析前端 FormData 字段 {@code originalFileNameListJson}（JSON 数组字符串）
     * 2. 创建本次提交的文件上下文（无前端原始文件名列表）
     * 3. 提交调库申请内部实现
     *
     * @param req 调库提交请求
     * @param files 本次提交的附件
     * @param originalFileNameListJson 附件原始名称
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req, List<MultipartFile> files,
                                        String originalFileNameListJson) {
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId(), originalFileNameList);
        // 提交调库申请内部实现
        return submitAdjustLog(req, submissionFiles, new BatchNoContext());
    }

    /**
     * 提交调库申请内部实现
     *
     * 执行顺序：
     * 1. 校验主体调库申请，非 Excel 申请还需检查重复提交
     * 2. 读取本次提交共用的数据，非 Excel 申请还需检查在途流程和互斥调出项
     * 3. 提交调入项并收集需要直通的记录
     * 4. 提交调出项并继续收集需要直通的记录
     * 5. 统一复核本次直通项并落池
     * 6. 请求携带证券信息时，合并可编辑字段并更新证券基础信息表
     * 7. 汇总调库日志 ID 和提交数量
     *
     * @param req 调库提交请求
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @param batchNoContext 批次号上下文，可由整批共享
     * @return 本次组合提交结果
     */
    private AdjustSubmitDto submitAdjustLog(SecurityPoolAdjustSubmitReq req,
                                            SysAttachmentService.SubmissionFiles submissionFiles,
                                            BatchNoContext batchNoContext) {
        // 1. 校验主体调库申请，非 Excel 申请还需检查重复提交
        validateSubmitReq(req);
        // 判断调库来源是否由 Excel 导入服务生成
        boolean excelAdjust = isExcelAdjustType(req.getAdjustType());
        if (!excelAdjust) {
            // 检查短时间内是否已提交相同申请
            checkRecentDuplicateSubmit(req);
        }

        // 2. 读取本次提交共用的数据，非 Excel 申请还需检查在途流程和互斥调出项
        SubmitSharedData shared = loadSubmitSharedData(req, batchNoContext);
        if (!excelAdjust) {
            // 检查本次涉及的顶级池组是否已有活动流程
            checkSubmitPendingPoolGroups(req, shared.poolMap);
            // 检查调入需要的互斥调出是否完整且可执行
            validateRequiredMutexOutboundOnSubmit(req, shared);
        }

        List<IpAdjustLogBo> directApplyLogs = new ArrayList<>();
        // 3. 提交调入项并收集需要直通的记录
        List<Long> inboundIds = executeInboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // 4. 提交调出项并继续收集需要直通的记录
        List<Long> outboundIds = executeOutboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // 5. 统一复核本次直通项并落池
        recheckAndApplyDirectLogs(directApplyLogs, excelAdjust);

        // 6. 请求携带证券信息时，合并可编辑字段并更新证券基础信息表
        postSubmitProcess(req, shared);

        // 7. 汇总调库日志 ID 和提交数量
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
     * 校验主体禁投池调库申请的必填项
     *
     * 对请求入参进行合法性检查，均为内存操作，不涉及任何 DB 查询
     * 任意一项不通过则直接抛出异常，终止后续流程
     *
     * 执行顺序：
     * 1. 检查证券代码不能为空
     * 2. 检查调库项不能为空
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
     * 提交时校验：调入目标池若要求配套互斥调出，请求中必须包含对应调出项且调出校验通过
     *
     * 执行顺序：
     * 1. 按各调整项实际证券或主体代码归集请求中的调出池
     * 2. 逐条复核调入所需的互斥调出是否齐全且通过出池条件
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     */
    private void validateRequiredMutexOutboundOnSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        Map<String, Set<Long>> requestOutPoolIdsBySecurityCode = new HashMap<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item != null && AdjustMode.OUT.getCode().equals(item.getAdjustMode())
                    && item.getTargetPoolId() != null) {
                // 按调库项解析实际调整对象，避免主体与旗下不同债券的调出池互相干扰
                String itemSecurityCode = resolveItemSecurityCode(req, item);
                requestOutPoolIdsBySecurityCode
                        .computeIfAbsent(itemSecurityCode, key -> new HashSet<>())
                        .add(item.getTargetPoolId());
            }
        }
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
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
            // 仅使用当前调入对象自己的调出池复核互斥关系
            String itemSecurityCode = resolveItemSecurityCode(req, item);
            Set<Long> requestOutPoolIds = requestOutPoolIdsBySecurityCode.getOrDefault(
                    itemSecurityCode, Collections.emptySet());
            for (Long mutexId : inMutex) {
                if (mutexId == null || !shared.currentPoolIds.contains(mutexId)) {
                    continue;
                }
                // 构建投资池全路径名称
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
                int poolCurrentCount = forbiddenPoolAdjustMapper.queryPoolCurrentCount(mutexId);
                // 构建单个调库项的校验上下文（{@link AdjustCheckContext}）
                AdjustCheckContext ctx = buildCheckContext(fakeOut, poolCurrentCount, checkShared);
                List<String> outFailures = checkOutConditions(ctx);
                if (outFailures != null && !outFailures.isEmpty()) {
                    // 组装提交阶段配套互斥调出失败的异常文案（总述 + 编号原因）
                    throw new BizException(buildMutexOutboundFailureMessage(mutexPoolName, outFailures));
                }
            }
        }
    }

    /**
     * 校验近期是否已经提交相同的主体手工调库项
     *
     * 执行顺序：
     * 1. 调库申请已提交时终止处理
     *
     * @param req 调库提交请求
     */
    private void checkRecentDuplicateSubmit(SecurityPoolAdjustSubmitReq req) {
        List<SecurityPoolAdjustSubmitReq.AdjustItem> manualItems = req.getItems().stream()
                // 判断提交项是否为手工调库项
                .filter(this::isManualSubmitItem).collect(Collectors.toList());
        List<IpAdjustLogBo> recentLogs = forbiddenPoolAdjustMapper.queryRecentManualAdjustLogList(
                req.getSecurityCode(), req.getAdjusterId(), DUPLICATE_SUBMIT_WINDOW_SECONDS);
        if (manualItems.isEmpty() || recentLogs == null || recentLogs.isEmpty()) {
            return;
        }
        List<String> requestKeys = manualItems.stream().map(item -> item.getTargetPoolId() + "|"
                // 归一化可空文本
                + item.getAdjustMode() + "|" + item.getFlowId() + "|" + normalizeText(item.getFlowKey()))
                .sorted().collect(Collectors.toList());
        List<String> historyKeys = recentLogs.stream().map(log -> log.getTargetPoolId() + "|"
                // 归一化可空文本
                + log.getAdjustMode() + "|" + log.getFlowId() + "|" + normalizeText(log.getFlowKey()))
                .sorted().collect(Collectors.toList());
        boolean sameText = recentLogs.stream().allMatch(log ->
                // 归一化可空文本
                Objects.equals(normalizeText(req.getAdjustReason()), normalizeText(log.getAdjustReason()))
                        // 归一化可空文本
                        && Objects.equals(normalizeText(req.getAdjustAdvice()), normalizeText(log.getAdjustAdvice())));
        if (requestKeys.equals(historyKeys) && sameText) {
            throw new BizException("调库申请已提交，请勿重复操作");
        }
    }

    /**
     * 检查正式提交涉及的顶级池组是否已有活动流程
     *
     * 执行顺序：
     * 1. 将目标池列表转换为顶级投资池组 ID 集合
     * 2. 查询在途手工目标投资池 ID列表
     * 3. 逐项查找目标池所属顶级投资池
     *
     * @param req 调库提交请求
     * @param poolMap 当前投资池映射
     */
    private void checkSubmitPendingPoolGroups(SecurityPoolAdjustSubmitReq req, Map<Long, InvestmentPoolBo> poolMap) {
        // 将目标池列表转换为顶级投资池组 ID 集合
        Set<Long> pendingGroups = resolvePoolGroupIds(
                forbiddenPoolAdjustMapper.queryPendingManualTargetPoolIdList(req.getSecurityCode(), null), poolMap);
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 查找目标池所属顶级投资池
            Long groupId = resolveRootPoolId(item.getTargetPoolId(), poolMap);
            if (groupId != null && pendingGroups.contains(groupId)) {
                throw new BizException("主体在目标投资池组存在进行中的调库流程，请等待流程结束后再发起调库");
            }
        }
    }

    /**
     * 将目标池列表转换为顶级投资池组 ID 集合
     *
     * 执行顺序：
     * 1. 逐个查找给定池所属的顶级池并去重，缺少池列表时返回空集合
     *
     * @param poolIds 需要转换的池 ID 列表
     * @param poolMap 当前投资池映射
     * @return 非空根池 ID 集合
     */
    private Set<Long> resolvePoolGroupIds(List<Long> poolIds, Map<Long, InvestmentPoolBo> poolMap) {
        Set<Long> result = new HashSet<>();
        if (poolIds != null) {
            for (Long poolId : poolIds) {
                // 查找目标池所属顶级投资池
                Long rootId = resolveRootPoolId(poolId, poolMap);
                if (rootId != null) {
                    result.add(rootId);
                }
            }
        }
        return result;
    }

    /**
     * 查找目标池所属顶级投资池
     *
     * 执行顺序：
     * 1. 从目标池 ID 沿父池逐层向上，走到根池或遇到缺失记录时停止
     * 2. 发现已经访问过的池 ID 时结束查找，避免关系环导致反复遍历
     *
     * @param poolId 目标投资池 ID
     * @param poolMap 当前投资池映射
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
     * 归一化可空文本
     *
     * 执行顺序：
     * 1. 输入为 null 或去空白后为空时返回 null，其余文本保持原样
     *
     * @param value 待统一空值的文本
     * @return 用于重复提交比较的原文本，输入为 null 或只有空白时返回 null
     */
    private String normalizeText(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * 为主体禁投池调库准备提交数据和批次号上下文
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
    private SubmitSharedData loadSubmitSharedData(SecurityPoolAdjustSubmitReq req) {
        // 创建本次提交独立批次号上下文
        return loadSubmitSharedData(req, new BatchNoContext());
    }

    /**
     * 使用指定批次号上下文加载主体禁投池提交数据
     *
     * 执行顺序：
     * 1. 按调整对象读取基础信息并校验存在
     * 2. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
     * 3. 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
     * 4. 全量投资池关系配置，构建三层嵌套 Map
     * 5. 收集所有调库项中引用的唯一流程标识，批量加载流程快照
     * 6. 为每个唯一流程加载快照（定义 + 活跃版本 + 节点 + 连线）
     *
     * @param req 调库提交请求
     * @param batchNoContext 批次号上下文，可由整批共享
     * @return 封装了本次提交全量共享数据的 SubmitSharedData
     */
    private SubmitSharedData loadSubmitSharedData(SecurityPoolAdjustSubmitReq req, BatchNoContext batchNoContext) {
        // 1. 按调整对象读取基础信息并校验存在
        SecurityInfoBo securityInfo = queryAdjustSecurityInfo(req.getSecurityCode(), req.getSecurityType());
        if (securityInfo == null) {
            throw new BizException(COMPANY_SECURITY_TYPE.equals(req.getSecurityType())
                    ? "公司主体不存在" : "证券不存在");
        }

        // 2. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        for (InvestmentPoolBo p : allPools) {
            poolMap.put(p.getId(), p);
        }

        // 3. 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
        List<Long> currentPoolIdList = forbiddenPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList);

        // 4. 全量投资池关系配置，构建三层嵌套 Map
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                forbiddenPoolAdjustMapper.queryAllPoolRelationList());

        // 5. 收集所有调库项中引用的唯一流程标识，批量加载流程快照
        Set<Long> uniqueFlowIds = new HashSet<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long resolvedId = resolveFlowIdFromItem(item);
            if (resolvedId != null) {
                uniqueFlowIds.add(resolvedId);
            }
        }

        // 6. 为每个唯一流程加载快照（定义 + 活跃版本 + 节点 + 连线）
        Map<Long, FlowSnapshot> flowSnapshotMap = new HashMap<>();
        for (Long flowId : uniqueFlowIds) {
            // 为指定流程 ID 构建运行时快照（定义 + 活跃版本 + 节点索引 + 连线列表）
            FlowSnapshot snapshot = buildFlowSnapshot(flowId);
            if (snapshot != null) {
                flowSnapshotMap.put(flowId, snapshot);
            }
        }

        boolean companyAdjust = COMPANY_SECURITY_TYPE.equals(req.getSecurityType());
        return new SubmitSharedData(
                securityInfo,
                poolMap,
                currentPoolIds,
                poolRelationMap,
                forbiddenPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()),
                forbiddenPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()),
                companyAdjust ? false : forbiddenPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()),
                flowSnapshotMap,
                batchNoContext
        );
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
    private Long resolveFlowIdFromItem(SecurityPoolAdjustSubmitReq.AdjustItem item) {
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
    private boolean isManualSubmitItem(SecurityPoolAdjustSubmitReq.AdjustItem item) {
        return item.getItemTag() == null || item.getItemTag().isEmpty() || ItemType.MANUAL.getCode().equals(item.getItemTag());
    }

    /**
     * 生成调库批次号
     *
     * 执行顺序：
     * 1. 超过一千组时切换到下一段序号，避免与其他方向的批次号重叠
     *
     * @param manualItem 手工明细
     * @param noFlow 当前分组是否未配置流程
     * @param shared 本次提交的共享数据
     * @return 新生成的调库批次号
     */
    private String buildAdjustBatchNo(
            SecurityPoolAdjustSubmitReq.AdjustItem manualItem, boolean noFlow, SubmitSharedData shared) {
        int first;
        int sequence;
        if (noFlow) {
            first = 3000;
            sequence = ++shared.batchNoContext.noFlowBatchSeq;
        } else if (AdjustMode.IN.getCode().equals(manualItem.getAdjustMode())) {
            first = 1000;
            sequence = ++shared.batchNoContext.inboundBatchSeq;
        } else {
            first = 2000;
            sequence = ++shared.batchNoContext.outboundBatchSeq;
        }
        // 超过一千组时切换到下一段序号，避免与其他方向的批次号重叠
        int serial = first + sequence + (sequence - 1) / 1000 * 2000;
        return "COMP" + shared.batchNoContext.batchTimeText + String.format("%04d", serial);
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
            SecurityPoolAdjustSubmitReq req,
            SecurityPoolAdjustSubmitReq.AdjustItem item,
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
     * 判断流程能否在无需人工审批的情况下直通
     *
     * 执行顺序：
     * 1. 检查流程快照和连线，定位开始节点，缺失时判为不能直通
     * 2. 沿主路径检查节点能否自动完成，全部相关路径均可结束时判为直通
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
            // 审批节点：仅发起人自动提交或系统 auto 可无人工；其余需待办 → 非直通
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
                // 与初始步骤推进一致：不把驳回支路算作直通主路径
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
     * 规则：报告必填（in_report_restriction / out_report_restriction）
     *
     * 目标池配置了报告限制时，提交时校验报告附件：
     * none=不限制 / any=任意一篇研究报告 / internal=必须是内部研究报告
     * 对应老项目 rschDocMode/rschDocOutMode 报告校验。在提交阶段校验（checkAdjust 阶段无报告信息）
     *
     * 执行顺序：
     * 1. 同主体半年内存在真实有效调入报告时允许豁免
     * 2. 校验报告库附件真实来源、分类和有效状态
     *
     * @param item 调库项
     * @param pool 投资池实体
     * @param reportRestriction 目标池在当前调整方向的报告要求
     * @param securityCode 证券代码
     * @param adjustType 调库来源
     */
    private void checkReportRequired(SecurityPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool,
                                     String reportRestriction, String securityCode, String adjustType) {
        // 判断调库来源是否由 Excel 导入服务生成
        if (isExcelAdjustType(adjustType)) {
            // Excel 免必填报告，仍校验已填写的报告引用
            sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(), false);
            return;
        }
        if (pool == null || reportRestriction == null || reportRestriction.isEmpty() || "none".equals(reportRestriction)) {
            return;
        }
        // 1. 同主体半年内存在真实有效调入报告时允许豁免
        if (securityCode != null && !securityCode.isEmpty() && forbiddenPoolAdjustMapper.queryHasRecentInboundWithReport(securityCode)) {
            return;
        }
        boolean hasReport = (item.getCreditReportFileIndexes() != null && !item.getCreditReportFileIndexes().isEmpty())
                || (item.getCreditReportSourceAttachmentIds() != null && !item.getCreditReportSourceAttachmentIds().isEmpty());
        if (!hasReport) {
            throw new BizException("目标池[" + pool.getPoolName() + "]要求研究报告，请上传或选择报告");
        }
        boolean internalRequired = "internal".equals(reportRestriction);
        if (internalRequired && (item.getCreditReportSourceAttachmentIds() == null
                || item.getCreditReportSourceAttachmentIds().isEmpty())) {
            throw new BizException("目标池[" + pool.getPoolName() + "]要求内部研究报告，请从内部报告库选择");
        }
        // 2. 校验报告库附件真实来源、分类和有效状态
        sysAttachmentService.validateCreditReportSources(
                item.getCreditReportSourceAttachmentIds(), internalRequired);
    }

    /**
     * 通过无上传文件的入口提交主体禁投池调入申请
     *
     * 遍历请求中全部调入方向的调库项，逐项判断流程是否为直通（start→end），
     * 决定收集待统一复核落池的已生效调库记录，还是写入待审批调库记录
     *
     * 执行顺序：
     * 1. 构建无新上传文件的附件上下文及直通日志列表，再处理本次调入项
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     * @return 本次调入处理生成的所有调库记录 ID
     */
    private List<Long> executeInboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        // 执行调入提交并绑定本次 multipart 文件
        return executeInboundSubmit(req, shared,
                sysAttachmentService.createSubmissionFiles(Collections.<MultipartFile>emptyList(), req.getAdjusterId()),
                new ArrayList<IpAdjustLogBo>());
    }

    /**
     * 执行调入提交并绑定本次 multipart 文件
     *
     * 执行顺序：
     * 1. 规则：报告必填（in_report_restriction / out_report_restriction）
     * 2. 解析调库项实际调整对象：调库项代码优先，否则回退请求级主体代码
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @param directApplyLogs 用于收集待直接生效日志的列表
     * @return 生成的调入日志 ID 列表
     */
    private List<Long> executeInboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                            SysAttachmentService.SubmissionFiles submissionFiles,
                                            List<IpAdjustLogBo> directApplyLogs) {

        List<Long> generatedIds = new ArrayList<>();

        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 报告必填校验（按池 in_report_restriction，提交阶段校验）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            // 规则：报告必填（in_report_restriction / out_report_restriction）
            checkReportRequired(item, reportPool, reportPool != null ? reportPool.getInReportRestriction() : null,
                    // 解析调库项实际调整对象：调库项代码优先，否则回退请求级主体代码
                    resolveItemSecurityCode(req, item), req.getAdjustType());
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
                forbiddenPoolAdjustMapper.addAdjustLog(logBo);
                generatedIds.add(logBo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(logBo.getId(), item, submissionFiles, req.getAdjusterId());
                // 有流程定义的直通流程仍记录开始、发起、结束步骤
                if (isManualSubmitItem(item) && snapshot != null && logBo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    createInitialSteps(logBo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                }

                // 收集直通日志，待全部调库项生成后统一复核并落池
                logBo.setAdjustLogId(logBo.getId());
                directApplyLogs.add(logBo);
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，流程中）
                // 构建调库日志实体
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem, shared);
                bo.setAdjustBatchNo(adjustBatchNo);
                bo.setAuditStatus(AuditStatus.SUBMITTED.getCode());
                forbiddenPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 手工项创建初始流程步骤，联动/互斥项共用同批次流程状态
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    boolean flowFinished = createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                    if (flowFinished) {
                        bo.setAuditStatus(AuditStatus.APPROVED.getCode());
                        forbiddenPoolAdjustMapper.editAdjustLogAuditStatus(bo.getId(), adjustBatchNo, AuditStatus.APPROVED.getCode());
                        bo.setAdjustLogId(bo.getId());
                        directApplyLogs.add(bo);
                    }
                }
            }
        }

        return generatedIds;
    }

    /**
     * 提交主体禁投池调出申请并绑定上传附件
     *
     * 遍历请求中全部调出方向的调库项，逐项判断流程是否为直通（start→end），
     * 决定写入已生效调库记录并软删除 ip_pool_status，还是写入待审批调库记录
     *
     * 执行顺序：
     * 1. 逐项校验调出所需报告和材料，并确定实际调库主体
     * 2. 提交调出申请，将上传文件绑定到对应的调库日志
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     * @return 本次调出处理生成的所有调库记录 ID
     */
    private List<Long> executeOutboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        // 执行调出提交并绑定本次 multipart 文件
        return executeOutboundSubmit(req, shared,
                sysAttachmentService.createSubmissionFiles(Collections.<MultipartFile>emptyList(), req.getAdjusterId()),
                new ArrayList<IpAdjustLogBo>());
    }

    /**
     * 执行调出提交并绑定本次 multipart 文件
     *
     * 执行顺序：
     * 1. 逐项校验调出所需报告和材料，并确定实际调库主体
     * 2. 提交调出申请，将上传文件绑定到对应的调库日志
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @param directApplyLogs 用于收集待直接生效日志的列表
     * @return 生成的调出日志 ID 列表
     */
    private List<Long> executeOutboundSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                             SysAttachmentService.SubmissionFiles submissionFiles,
                                             List<IpAdjustLogBo> directApplyLogs) {

        List<Long> generatedIds = new ArrayList<>();

        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 报告必填校验（按池 out_report_restriction，提交阶段校验）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            // 规则：报告必填（in_report_restriction / out_report_restriction）
            checkReportRequired(item, reportPool, reportPool != null ? reportPool.getOutReportRestriction() : null,
                    // 解析调库项实际调整对象：调库项代码优先，否则回退请求级主体代码
                    resolveItemSecurityCode(req, item), req.getAdjustType());
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
                forbiddenPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 有流程定义的直通流程仍记录开始、发起、结束步骤
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                }

                // 收集直通日志，待全部调库项生成后统一复核并落池
                directApplyLogs.add(bo);
            } else {
                // 非直通流程：写入 ip_adjust_log（audit_status='00'，流程中）
                // 构建调库日志实体
                IpAdjustLogBo bo = buildAdjustLog(req, item, manualItem, shared);
                bo.setAdjustBatchNo(adjustBatchNo);
                bo.setAuditStatus(AuditStatus.SUBMITTED.getCode());
                forbiddenPoolAdjustMapper.addAdjustLog(bo);
                generatedIds.add(bo.getId());
                // 将提交附件绑定到新建调库日志
                bindSubmitAttachments(bo.getId(), item, submissionFiles, req.getAdjusterId());
                // 手工项创建初始流程步骤，联动/互斥项共用同批次流程状态
                if (isManualSubmitItem(item) && snapshot != null && bo.getId() != null) {
                    // 为新建的调库记录创建初始流程步骤（懒创建）  仅创建前 3 步：开始节点→提交人节点→下一审批节点（待处理）， 后续节点在审批动作执行时按需创建，因为流程走向不确定（可能通过也可能驳回）
                    boolean flowFinished = createInitialSteps(bo.getId(), adjustBatchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
                    if (flowFinished) {
                        forbiddenPoolAdjustMapper.editAdjustLogAuditStatus(bo.getId(), adjustBatchNo, AuditStatus.APPROVED.getCode());
                        bo.setAuditStatus(AuditStatus.APPROVED.getCode());
                        directApplyLogs.add(bo);
                    }
                }
            }
        }

        return generatedIds;
    }

    /**
     * 按申请中的可编辑字段更新证券基础信息
     *
     * 执行顺序：
     * 1. 请求携带证券信息时，合并当前主档与前端传入的可编辑字段
     * 2. 更新证券基础信息表中本次允许修改的字段
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据
     */
    private void postSubmitProcess(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        if (req.getSecurityInfo() == null) {
            return;
        }
        // 1. 合并数据库当前完整快照与前端传入的变更字段
        req.setSecurityInfo(buildMergedSecurityInfo(req.getSecurityCode(), req.getSecurityInfo()));
        // 2. 同步更新证券基础信息表中本次传入的字段
        forbiddenPoolAdjustMapper.editSecurityInfoForAdjust(req);
    }

    /**
     * 构建证券基础信息全量更新参数
     *
     * 前端只需要传入可能变更的字段；这里先加载数据库当前完整快照，
     * 再用前端传入字段覆盖同名字段。若前端显式传入 null，也会覆盖为 null，用于清空字段
     *
     * 执行顺序：
     * 1. 按证券代码查询证券主档
     * 2. 证券不存在时终止处理
     * 3. 使用前端本次可编辑字段覆盖数据库当前实体
     *
     * @param securityCode 证券代码
     * @param changedField 前端传入的变更字段
     * @return 本次完整快照
     */
    private SecurityInfoBo buildMergedSecurityInfo(String securityCode, SecurityInfoBo changedField) {
        SecurityInfoBo current = forbiddenPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (current == null) {
            throw new BizException("证券不存在");
        }
        // 使用前端本次可编辑字段覆盖数据库当前实体
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
     * 查询调库记录列表（全量，不分页）
     *
     * 按主体代码返回全部主体级调库历史（含终态），不以调库批次号或调库记录 ID 过滤
     * 字段回填对齐证券池详情 {@code SecurityPoolAdjustService#queryAdjustLogList}
     *
     * 执行顺序：
     * 1. 查询当前主体的全部主体级调库历史
     * 2. 查询投资池全路径名称，用于调库记录展示
     *
     * @param req securityCode 必填；adjustBatchNo 和 adjustLogId 仅供流程定位，本查询不使用
     * @return 调库日志列表
     */
    public List<AdjustLogDto> queryAdjustLogList(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        // 1. 查询当前主体的全部主体级调库历史
        List<IpAdjustLogBo> logs = forbiddenPoolAdjustMapper.queryAdjustLogList(
                req.getSecurityCode(), req.getAdjustBatchNo(), req.getAdjustLogId());
        if (logs.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 查询投资池全路径名称，用于调库记录展示
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();

        List<AdjustLogDto> result = new ArrayList<>();
        for (IpAdjustLogBo log : logs) {
            AdjustLogDto dto = new AdjustLogDto();
            dto.setId(log.getId());
            // 回填代码/简称/类型，便于详情「调整对象」与流程类型展示（对齐证券侧）
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
     * @param req 当前用户为管理员时返回全量启用池，普通用户仅返回有可调整权限的池及其祖先节点
     * @return 调库审批步骤明细，未传日志 ID 和有效批次号或没有步骤时返回空列表
     */
    public List<IpAdjustStepDto> queryAdjustStepList(SecurityPoolAdjustReq req) {
        Long adjustLogId = req.getAdjustLogId();
        String adjustBatchNo = req.getAdjustBatchNo();
        if (adjustLogId == null && (adjustBatchNo == null || adjustBatchNo.isEmpty())) {
            return Collections.emptyList();
        }
        List<IpAdjustStepBo> steps = forbiddenPoolAdjustMapper.queryAdjustStepByBatchList(adjustLogId, adjustBatchNo);
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
     * 校验证券调库可行性
     *
     * 执行顺序：
     * 1. 使用本次请求进入完整校验流程，按常规规则拦截失败并允许展开联动互斥
     *
     * @param req 包含证券代码和一个或多个用户选择的调库项
     * @return 包含手动项和自动项的完整校验结果列表
     */
    public AdjustCheckDto checkAdjust(AdjustCheckReq req) {
        // 单笔请求不复用其他调用的数据
        return checkAdjust(req, null, false, true);
    }

    /**
     * 执行相同的完整校验规则，按需使用当前批次公共数据
     *
     * 执行顺序：
     * 1. 校验主体调库请求
     * 2. 加载校验需要的主体、池和关系数据，调用方提供批次数据时复用
     * 3. 按目标池和方向登记手工项，避免生成重复自动项
     * 4. 校验调入项并补充联动和互斥项
     * 5. 校验调出项，Excel 模式按对应口径保留提示
     * 6. 匹配流程候选，返回校验结果及推荐流程
     *
     * @param req 调库校验请求
     * @param context 本批已查的主体与池配置，清空出库按当前数据读取
     * @param excelWarningsOnly 是否将 Excel 原业务失败保留为可提交提示
     * @param allowLinkMutex 是否启用联动和互斥调整
     * @return 包含手动项和自动项的完整校验结果列表
     */
    private AdjustCheckDto checkAdjust(AdjustCheckReq req, CompanyAdjustCheckContext context,
                                       boolean excelWarningsOnly, boolean allowLinkMutex) {

        // 1. 校验主体调库请求
        validateCheckAdjustReq(req);

        // 2. 加载校验需要的主体、池和关系数据，调用方提供批次数据时复用
        AdjustSharedData shared = loadSharedData(req, context);


        // 3. 按目标池和方向登记手工项，避免生成重复自动项
        Set<String> coveredKeys = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            coveredKeys.add(item.getTargetPoolId() + "_" + item.getAdjustMode());
        }

        // 4. 校验调入项并补充联动和互斥项
        List<AdjustCheckDto.CheckResultItem> resultItems = new ArrayList<>(
                // 执行调入校验并补充联动、互斥调库项
                executeInAdjustCheck(req, shared, coveredKeys));

        // 5. 校验调出项，Excel 模式按对应口径保留提示
        resultItems.addAll(executeOutAdjustCheck(req, shared, coveredKeys, excelWarningsOnly, allowLinkMutex));

        if (excelWarningsOnly) {
            // 原校验已完成，保留失败原因供 Excel 页面提示
            allowExcelImportResults(shared, resultItems);
        }

        // 6. 匹配流程候选，返回校验结果及推荐流程
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

    /**
     * 原规则执行后，将主体 Excel 的业务失败作为可提交提示
     *
     * 执行顺序：
     * 1. 主体已在加载主数据时校验，找出目标池缺失的调库组
     * 2. 其余组允许提交，保留原失败原因供页面展示
     *
     * @param shared 本次已加载的主体和投资池数据
     * @param items 原规则执行后的完整结果
     */
    private void allowExcelImportResults(AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> items) {
        Set<String> invalidGroups = new HashSet<>();
        for (AdjustCheckDto.CheckResultItem item : items) {
            if (!shared.getPoolMap().containsKey(item.getTargetPoolId())
                    || SecurityPoolCheckRules.hasExcelBlockingFailure(item, COMPANY_PENDING_PROCESS_FAILURE)) {
                invalidGroups.add(item.getAdjustGroupKey());
            }
        }
        for (AdjustCheckDto.CheckResultItem item : items) {
            item.setCanAdjust(!invalidGroups.contains(item.getAdjustGroupKey()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 四阶段实现
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验主体禁投池调库检查请求的必填项
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
    private void validateCheckAdjustReq(AdjustCheckReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
    }

    /**
     * 加载主体禁投池校验共用的主数据和池关系
     *
     * 集中执行本次校验所需的全部 DB 查询，构建 {@link AdjustSharedData}
     * 调入和调出校验共用这些数据
     * 证券存在性校验也在此阶段完成（查到 null 则抛异常）
     *
     * 执行顺序：
     * 1. 按调整对象读取基础信息并校验存在
     * 2. 提取本次请求中调入/调出各自涉及的目标池 ID 集合，供互斥冲突校验使用
     * 3. 证券级别的状态标志（整个请求期间保持不变，统一在此查询）
     *
     * @param req 调库校验请求（需携带 securityCode）
     * @param context 本批已查的主体与池配置，清空出库按当前数据读取
     * @return 封装了本次校验全量共享数据的 AdjustSharedData
     */
    private AdjustSharedData loadSharedData(AdjustCheckReq req, CompanyAdjustCheckContext context) {
        // 1. 按调整对象读取基础信息并校验存在
        SecurityInfoBo securityInfo;
        if (context != null && COMPANY_SECURITY_TYPE.equals(req.getSecurityType())) {
            ForbiddenPoolAdjustDto company = context.getCompanyMap().get(req.getSecurityCode());
            securityInfo = null;
            if (company != null) {
                securityInfo = new SecurityInfoBo();
                securityInfo.setWindCode(company.getCompanyCode());
                securityInfo.setFullName(company.getCompanyFullName());
                securityInfo.setShortName(company.getCompanyShortName());
                securityInfo.setSecurityType(COMPANY_SECURITY_TYPE);
            }
        } else {
            // 单笔及非主体对象保持按实际代码查询
            securityInfo = queryAdjustSecurityInfo(req.getSecurityCode(), req.getSecurityType());
        }
        if (securityInfo == null) {
            throw new BizException(COMPANY_SECURITY_TYPE.equals(req.getSecurityType())
                    ? "公司主体不存在" : "证券不存在");
        }

        BatchCompanyAdjustCheckData batch = context != null ? context.getBatchData() : null;
        Map<Long, InvestmentPoolBo> poolMap;
        Map<Long, Map<String, List<Long>>> poolRelationMap;
        Set<Long> currentPoolIds;
        if (batch == null) {
            List<InvestmentPoolBo> allPools = context == null ? investmentPoolMapper.queryPoolList() : context.getPools();
            poolMap = new HashMap<>();
            for (InvestmentPoolBo pool : allPools) {
                poolMap.put(pool.getId(), pool);
            }
            currentPoolIds = new HashSet<>(forbiddenPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode()));
            // 构建全量投资池关系 Map
            poolRelationMap = buildPoolRelationMap(
                    context == null ? forbiddenPoolAdjustMapper.queryAllPoolRelationList() : context.getRelations());
        } else {
            poolMap = batch.pools;
            poolRelationMap = batch.relations;
            currentPoolIds = batch.states.get(req.getSecurityCode()).currentPoolIds;
        }

        // 2. 提取本次请求中调入/调出各自涉及的目标池 ID 集合，供互斥冲突校验使用
        Set<Long> requestInPoolIds  = new HashSet<>();
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                requestInPoolIds.add(item.getTargetPoolId());
            } else if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                requestOutPoolIds.add(item.getTargetPoolId());
            }
        }

        // 3. 证券级别的状态标志（整个请求期间保持不变，统一在此查询）
        AdjustSharedData shared = new AdjustSharedData();
        shared.setSecurityInfo(securityInfo);
        shared.setPoolMap(poolMap);
        shared.setCurrentPoolIds(currentPoolIds);
        shared.setPoolRelationMap(poolRelationMap);
        shared.setBatchCompanyData(batch);
        if (batch == null) {
            shared.setHasPendingProcess(forbiddenPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()));
            shared.setPendingProcessNodeLabel(forbiddenPoolAdjustMapper.querySecurityPendingProcessNodeLabel(req.getSecurityCode()));
            // 将目标池列表转换为顶级投资池组 ID 集合
            shared.setPendingPoolGroupIds(resolvePoolGroupIds(
                    forbiddenPoolAdjustMapper.queryPendingManualTargetPoolIdList(req.getSecurityCode(), null), poolMap));
            shared.setSecurityInObservePool(forbiddenPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()));
        } else {
            CompanyState state = batch.states.get(req.getSecurityCode());
            shared.setPendingProcessNodeLabel(state.pendingNodeLabel);
            // 主体在途按手工流程占用的顶级池组判断
            shared.setPendingPoolGroupIds(resolvePoolGroupIds(new ArrayList<>(state.pendingPoolIds), poolMap));
            // 判断有效在池记录关联的未删除投资池是否包含指定类型
            shared.setSecurityInObservePool(isInPoolType(currentPoolIds, poolMap, "observe"));
        }
        boolean companyAdjust = COMPANY_SECURITY_TYPE.equals(req.getSecurityType());
        shared.setIssuerInObservePool(companyAdjust
                ? false : forbiddenPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()));
        if (companyAdjust) {
            shared.setIssuerRatingDowngraded(false);
            shared.setOutlookRatingDowngraded(false);
            shared.setGuarantorRatingDowngraded(false);
        } else {
            // 评级下调三标志：主体/展望按发行人评级判定，担保人按前端选中代码判定
            shared.setIssuerRatingDowngraded(ratingDowngradeChecker.isIssuerDowngraded(securityInfo));
            shared.setOutlookRatingDowngraded(ratingDowngradeChecker.isOutlookNegative(securityInfo));
            shared.setGuarantorRatingDowngraded(ratingDowngradeChecker.isGuarantorDowngraded(req.getGuarantorCode()));
        }
        shared.setRequestInPoolIds(requestInPoolIds);
        shared.setRequestOutPoolIds(requestOutPoolIds);
        return shared;
    }

    /**
     * 根据本行预计调库和本次取得的外评判定黑名单质押库状态
     *
     * 执行顺序：
     * 1. 有本批预取数据时使用对应的主体和评级结果，否则实时查询
     * 2. 按本次拟调整池状态复核后，返回主体是否命中质押黑名单条件
     *
     * @param code 证券代码
     * @param shared 本次校验的共享数据
     * @return true=本行调整后的主体应在黑名单质押库，false=本行调整后的主体不应在黑名单质押库
     */
    private boolean shouldBeInBlacklist(String code, AdjustSharedData shared) {
        BatchCompanyAdjustCheckData batch = shared.getBatchCompanyData();
        PledgeBlacklistRuleService.Decision decision = batch == null
                ? pledgeBlacklistRuleService.evaluate(code, shared.getRequestInPoolIds(), shared.getRequestOutPoolIds())
                : pledgeBlacklistRuleService.evaluateCompanyBatch(batch, code,
                        shared.getRequestInPoolIds(), shared.getRequestOutPoolIds());
        return decision.shouldBeInBlacklist();
    }

    /**
     * 判断有效在池记录关联的未删除投资池是否包含指定类型
     *
     * 执行顺序：
     * 1. 按在池 ID 取仍存在的池配置，任一配置的类型与指定类型一致时返回 true
     *
     * @param poolIds 清空主体所在目标池
     * @param pools 按ID索引的投资池
     * @param poolType 主体池类型
     * @return true=有效在池记录中命中指定池类型，false=有效在池记录中未命中指定池类型
     */
    private boolean isInPoolType(Set<Long> poolIds, Map<Long, InvestmentPoolBo> pools, String poolType) {
        return poolIds.stream().map(pools::get).anyMatch(pool -> pool != null && poolType.equals(pool.getPoolType()));
    }

    /**
     * 读取目标池有效数量，批量主体校验使用当前请求已查结果
     *
     * 执行顺序：
     * 1. 查询投资池当前数量
     *
     * @param poolId 目标投资池 ID
     * @param shared 本次校验的共享数据
     * @return 符合本次条件的数量
     */
    private int poolCurrentCount(Long poolId, AdjustSharedData shared) {
        BatchCompanyAdjustCheckData batch = shared.getBatchCompanyData();
        return batch == null ? forbiddenPoolAdjustMapper.queryPoolCurrentCount(poolId)
                : batch.capacities.getOrDefault(poolId, 0);
    }

    /**
     * 按调整对象查询调库框架所需的基础信息
     *
     * 执行顺序：
     * 1. 调整对象为主体时按主体代码查询，其他对象按证券代码查询主档
     *
     * @param securityCode 调整对象代码
     * @param securityType 调整对象类型
     * @return 基础信息
     */
    private SecurityInfoBo queryAdjustSecurityInfo(String securityCode, String securityType) {
        if (COMPANY_SECURITY_TYPE.equals(securityType)) {
            return forbiddenPoolAdjustMapper.queryCompanySecurityBoByCode(securityCode);
        }
        return forbiddenPoolAdjustMapper.querySecurityBoByCode(securityCode);
    }

    /**
     * 校验手工调入项和配套项
     *
     * 执行顺序：
     * 1. 按证券、目标池及方向组织校验分组，检查每个手工调入项
     * 2. 根据关系配置追加联动调入与互斥调出项，并核对同组校验结果
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
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
            int poolCurrentCount = poolCurrentCount(item.getTargetPoolId(), shared);
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
            List<AdjustCheckDto.CheckResultItem> groupLinkedItems = new ArrayList<>();
            List<Long> inLinkage = relations.get(RelationType.IN_LINKED.getCode());
            if (inLinkage != null) {
                for (Long linkedId : inLinkage) {
                    // 黑名单质押库按本行预计调库后的条件判定，已在该池的主体无需重复联动
                    if (PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(linkedId)
                            // 根据本行预计调库和本次取得的外评判定黑名单质押库状态
                            && (!shouldBeInBlacklist(req.getSecurityCode(), shared)
                            || shared.getCurrentPoolIds().contains(linkedId))) {
                        continue;
                    }
                    // coveredKeys.add 返回 true 表示该 key 是首次出现，生成自动项
                    if (coveredKeys.add(linkedId + "_调入")) {
                        // 构建自动生成的联动/互斥调整项校验结果  与手动项校验流程完全相同，区别在于：  targetPoolId 来自池关系配置，由系统自动推导，非用户选择 itemTag 标记为ItemType.LINKAGE.getCode()或ItemType.MUTEX.getCode()，前端据此区分显示样式
                        AdjustCheckDto.CheckResultItem autoItem =
                                // 构建自动生成的联动/互斥调整项校验结果
                                buildAutoResultItem(linkedId, AdjustMode.IN.getCode(), ItemType.LINKAGE.getCode(),
                                        adjustGroupKey, shared, false);
                        // 关联手工项失败时阻断自动联动项
                        inheritManualItemFailure(autoItem, resultItem);
                        groupLinkedItems.add(autoItem);
                        results.add(autoItem);
                    }
                }
            }

            // 联动池失败时反向阻断来源项，避免来源池单独生效
            propagateAutoFailureToManual(resultItem, groupLinkedItems, "配套联动调入未通过");

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
                                buildAutoResultItem(mutexId, AdjustMode.OUT.getCode(), ItemType.MUTEX.getCode(),
                                        adjustGroupKey, shared, false);
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
     * 校验手工调出项和配套项
     *
     * 执行顺序：
     * 1. 逐项构建调出校验上下文，按是否为主体 Excel 调出应用对应规则
     * 2. 允许联动时补充尚未覆盖的联动调出项，并将配套项的失败原因回传主项
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @param excelOutbound 是否为主体 Excel 调出
     * @param allowLinkage 是否展开调出联动池
     * @return 调出方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeOutAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys, boolean excelOutbound,
            boolean allowLinkage) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (!AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
                continue;
            }
            // 构建手工调库项分组 Key
            String adjustGroupKey = buildAdjustGroupKey(item);

            // 手动调出项：前置校验 + 调出校验
            int poolCurrentCount = poolCurrentCount(item.getTargetPoolId(), shared);
            // 构建调库校验上下文
            AdjustCheckContext ctx = buildCheckContext(item, poolCurrentCount, shared);
            // 校验调出规则，主体 Excel 调出跳过黑名单质押库三条件
            List<String> failures = checkOutConditions(ctx, excelOutbound);

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

            if (!allowLinkage) {
                continue;
            }
            // 联动调出：目标池调出时，其联动池（out_linked）需同步调出
            Map<String, List<Long>> relations = shared.getPoolRelationMap().getOrDefault(
                    item.getTargetPoolId(), Collections.emptyMap());
            List<AdjustCheckDto.CheckResultItem> groupLinkedItems = new ArrayList<>();
            List<Long> outLinkage = relations.get(RelationType.OUT_LINKED.getCode());
            if (outLinkage != null) {
                for (Long linkedId : outLinkage) {
                    // 普通调出仍保留命中其他黑名单条件的主体，Excel 调出按联动配置生成
                    if (!excelOutbound && PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(linkedId)
                            // 根据本行预计调库和本次取得的外评判定黑名单质押库状态
                            && shouldBeInBlacklist(req.getSecurityCode(), shared)) {
                        continue;
                    }
                    if (PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(linkedId)
                            && !shared.getCurrentPoolIds().contains(linkedId)) {
                        continue;
                    }
                    if (coveredKeys.add(linkedId + "_调出")) {
                        // 构建自动生成的联动/互斥调整项校验结果  与手动项校验流程完全相同，区别在于：  targetPoolId 来自池关系配置，由系统自动推导，非用户选择 itemTag 标记为ItemType.LINKAGE.getCode()或ItemType.MUTEX.getCode()，前端据此区分显示样式
                        AdjustCheckDto.CheckResultItem autoItem =
                                // 构建自动生成的联动/互斥调整项校验结果
                                buildAutoResultItem(linkedId, AdjustMode.OUT.getCode(), ItemType.LINKAGE.getCode(),
                                        adjustGroupKey, shared, excelOutbound);
                        // 关联手工项失败时阻断自动联动项
                        inheritManualItemFailure(autoItem, resultItem);
                        groupLinkedItems.add(autoItem);
                        results.add(autoItem);
                    }
                }
            }
            // 联动池失败时反向阻断来源项，避免来源池单独生效
            propagateAutoFailureToManual(resultItem, groupLinkedItems, "配套联动调出未通过");
        }

        return results;
    }

    /**
     * 汇总主体禁投池各手工调库项可选择的审批流程
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
            AdjustCheckReq req, AdjustSharedData shared, AdjustCheckDto.CheckResultItem item) {

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
     * @param poolMap 当前投资池映射
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
     * 伪代码口径：
     *
     * 执行顺序：
     * 1. 证券期限 &lt;= 3 年（解析 date_exists_str）；
     * 2. 排除永续债、私募债、ABS 债；
     * 3. 债券类型属于债券类；
     * 4. 债券主体在白名单配置池中；
     * 5. 债券不是担保债
     *
     * 当前版本不新增白名单配置表、不修改表结构，因此仅保留入口并返回未命中
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param matchReasons 用于收集匹配原因的列表
     * @param unmatchReasons 用于收集不匹配原因的列表
     * @return true=满足白名单流程条件，false=不满足白名单流程条件
     */
    private boolean isWhitelistFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, List<String> matchReasons, List<String> unmatchReasons) {
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
        String categoryType = forbiddenPoolAdjustMapper.queryCategoryTypeBySecurityType(sec.getSecurityType());
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
     * 当前校验使用目标池和剩余期限，评级下调校验暂未启用
     *
     * 执行顺序：
     * 1. ABS 直接判为不支持简易调入
     * 2. 检查目标池是否为信用债大库一至三级池
     * 3. 检查剩余期限并与同主体在目标池的最长剩余期限比较
     * 4. 按累计的匹配和失败原因返回结果
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param targetPool 当前调整项的目标池配置
     * @param matchReasons 用于收集匹配原因的列表
     * @param unmatchReasons 用于收集不匹配原因的列表
     * @return true=满足简易入库流程条件，false=不满足简易入库流程条件
     */
    private boolean isSimpleInboundFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, InvestmentPoolBo targetPool,
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
            BigDecimal issuerPoolMaxRemainDays = forbiddenPoolAdjustMapper.queryIssuerTargetPoolMaxRemainDays(
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

        // 4. 按累计的匹配和失败原因返回结果
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
     * @param pool 投资池实体
     * @return true=目标池属于信用债大库，false=目标池不属于信用债大库
     */
    private boolean isCreditBondPool(InvestmentPoolBo pool) {
        if (pool == null) {
            return false;
        }
        return PoolType.CREDIT_BOND.getCode().equals(pool.getPoolType());
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
     * @param pool 投资池实体
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

    /**
     * 最终审批通过前按当前业务状态复核主体调库项
     *
     * 执行顺序：
     * 1. 人工终审查询目标池并复核调库条件
     *
     * @param logList 同一批次待生效的调整日志
     */
    public void recheckBeforeFinalApproval(List<IpAdjustLogBo> logList) {
        // 人工终审查询目标池并复核调库条件
        recheckBeforeFinalApproval(logList, true);
    }

    /**
     * 使用当前池配置复核主体调库项
     *
     * 执行顺序：
     * 1. 查询投资池列表
     * 2. 构建全量投资池关系 Map
     * 3. 查询全部投资池关系列表
     * 4. 按批次识别 Excel 主项和同组配套项
     * 5. 按调整对象查询调库框架所需的基础信息
     * 6. 查找目标池所属顶级投资池
     *
     * @param logList 同一批次待生效的调整日志
     * @param queryTargetPools 终审复核时是否按目标池 ID 额外查询本批涉及的投资池
     */
    private void recheckBeforeFinalApproval(List<IpAdjustLogBo> logList, boolean queryTargetPools) {
        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 1. 读取投资池及池关系，按调用方要求查询本批目标池
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, p -> p));
        List<Long> poolIds = logList.stream().map(IpAdjustLogBo::getTargetPoolId)
                .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        if (queryTargetPools && !poolIds.isEmpty()) {
            // 按 ID 查询本次调整涉及的目标池
            investmentPoolMapper.queryExistingPoolIdList(poolIds);
        }
        // 构建全量投资池关系 Map
        Map<Long, Map<String, List<Long>>> relationMap = buildPoolRelationMap(
                forbiddenPoolAdjustMapper.queryAllPoolRelationList());
        Set<Long> projectedInPoolIds = logList.stream()
                .filter(log -> COMPANY_SECURITY_TYPE.equals(log.getSecurityType()))
                .filter(log -> AdjustMode.IN.getCode().equals(log.getAdjustMode()))
                .map(IpAdjustLogBo::getTargetPoolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> projectedOutPoolIds = logList.stream()
                .filter(log -> COMPANY_SECURITY_TYPE.equals(log.getSecurityType()))
                .filter(log -> AdjustMode.OUT.getCode().equals(log.getAdjustMode()))
                .map(IpAdjustLogBo::getTargetPoolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 2. 按批次识别 Excel 主项和配套项
        Set<String> excelBatchNos = collectExcelBatchNos(logList);
        Map<Long, Integer> inboundIncrements = new HashMap<>();
        // 3. 按主体或证券的实际情况逐条复核调入和调出
        for (IpAdjustLogBo log : logList) {
            // Excel 保留目标池和调整对象的存在性校验
            boolean excelAdjust = isExcelCompanyLog(log, excelBatchNos);
            InvestmentPoolBo pool = poolMap.get(log.getTargetPoolId());
            if (pool == null || (!excelAdjust && (!"enabled".equals(pool.getStatus())
                    || Integer.valueOf(1).equals(pool.getIsDeleted())))) {
                // 抛出包含实际调整对象上下文的复核异常
                throwFinalRecheckFailure(log, "目标投资池不存在或已停用");
            }
            // 按调整对象查询调库框架所需的基础信息
            SecurityInfoBo security = queryAdjustSecurityInfo(log.getSecurityCode(), log.getSecurityType());
            if (security == null) {
                // 抛出包含实际调整对象上下文的复核异常
                throwFinalRecheckFailure(log, "调整对象不存在");
            }
            if (excelAdjust) {
                // 查找目标池所属顶级投资池
                Long rootPoolId = resolveRootPoolId(log.getTargetPoolId(), poolMap);
                List<Long> pendingPools = forbiddenPoolAdjustMapper.queryPendingManualTargetPoolIdList(
                        log.getSecurityCode(), log.getAdjustBatchNo());
                // 将目标池列表转换为顶级投资池组 ID 集合
                if (resolvePoolGroupIds(pendingPools, poolMap).contains(rootPoolId)) {
                    // 抛出包含实际调整对象上下文的复核异常
                    throwFinalRecheckFailure(log, COMPANY_PENDING_PROCESS_FAILURE);
                }
                if (AdjustMode.IN.getCode().equals(log.getAdjustMode())
                        && forbiddenPoolAdjustMapper.querySecurityCurrentPoolIdList(log.getSecurityCode())
                        .contains(log.getTargetPoolId())) {
                    // 抛出包含实际调整对象上下文的复核异常
                    throwFinalRecheckFailure(log, SecurityPoolCheckRules.ALREADY_IN_POOL_FAILURE);
                }
                continue;
            }
            String categoryType = forbiddenPoolAdjustMapper
                    .queryCategoryTypeBySecurityType(security.getSecurityType());
            AdjustCheckContext ctx = new AdjustCheckContext();
            ctx.setSecurityInfo(security);
            ctx.setTargetPool(pool);
            ctx.setPoolMap(poolMap);
            ctx.setCurrentPoolIds(new HashSet<>(forbiddenPoolAdjustMapper
                    .querySecurityCurrentPoolIdList(log.getSecurityCode())));
            ctx.setTargetPoolRelations(relationMap.getOrDefault(pool.getId(), Collections.emptyMap()));
            ctx.setCategoryType(categoryType);
            ctx.setRequestInPoolIds(projectedInPoolIds);
            ctx.setRequestOutPoolIds(projectedOutPoolIds);
            String failure;
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                // 规则：目标池已锁定（lock_flag=1），再规则：证券是否已在目标池中
                failure = firstFinalFailure(inCheckPoolLocked(ctx), inCheckSecurityAlreadyInPool(ctx),
                        // 规则：来源池限制，再规则：调入限制池（in_restrict），再规则：全局禁止池（forbidden 禁投池）
                        inCheckSourcePool(ctx), inCheckRestrictPool(ctx), inCheckForbiddenPool(ctx));
                int increment = inboundIncrements.getOrDefault(pool.getId(), 0);
                int currentCount = forbiddenPoolAdjustMapper.queryPoolCurrentCount(pool.getId());
                if (failure == null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                        && currentCount + increment + 1 > pool.getMaxCapacity()) {
                    failure = "目标投资池容量不足";
                }
                inboundIncrements.put(pool.getId(), increment + 1);
            } else {
                ctx.setTargetPoolEntryTime(forbiddenPoolAdjustMapper.queryPoolEntryTime(
                        log.getSecurityCode(), pool.getId()));
                // 规则：目标池已锁定（lock_flag=1），再规则：证券是否不在目标池中
                failure = firstFinalFailure(outCheckPoolLocked(ctx), outCheckSecurityNotInPool(ctx),
                        // 规则：调出限制池（out_restrict），再规则：调出冻结期（frozen_period_in）
                        outCheckRestrictPool(ctx), outCheckFrozenPeriod(ctx));
            }
            if (failure == null && PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(pool.getId())) {
                failure = pledgeBlacklistRuleService.validate(log.getSecurityCode(), log.getAdjustMode(),
                        projectedInPoolIds, projectedOutPoolIds);
            }
            if (failure != null) {
                // 抛出包含实际调整对象上下文的复核异常
                throwFinalRecheckFailure(log, failure);
            }
        }
    }

    /**
     * 对直通主体调库日志执行最终复核并统一落池
     *
     * 执行顺序：
     * 1. Excel 直通提交读取当前业务状态
     * 2. 应用批次中已提交的显式调整项
     * 3. 直通落池成功后，将手工信评附件沉淀为内部报告
     *
     * @param directApplyLogs 用于收集待直接生效日志的列表
     * @param excelAdjust 是否Excel调库
     */
    private void recheckAndApplyDirectLogs(List<IpAdjustLogBo> directApplyLogs, boolean excelAdjust) {
        if (directApplyLogs == null || directApplyLogs.isEmpty()) {
            return;
        }
        // 1. Excel 直通提交读取当前业务状态
        recheckBeforeFinalApproval(directApplyLogs, !excelAdjust);
        // 2. 应用批次中已提交的显式调整项
        applyPoolStatusChanges(directApplyLogs);
        // 3. 直通落池成功后，将手工信评附件沉淀为内部报告
        reportService.addInternalReportsOnFinish(directApplyLogs);
    }

    /**
     * 应用主体池状态及历史在途批次已提交的显式调整项
     *
     * 执行顺序：
     * 1. Excel 配套项沿用主项的生效方式
     * 2. 逐项新增投资池状态
     *
     * @param logList 同一批次待生效的调整日志
     */
    public void applyPoolStatusChanges(List<IpAdjustLogBo> logList) {
        if (logList == null || logList.isEmpty()) {
            return;
        }
        // Excel 配套项沿用主项的生效方式
        Set<String> excelBatchNos = collectExcelBatchNos(logList);
        for (IpAdjustLogBo log : logList) {
            // 判断主体调库日志是否属于 Excel 导入或清空申请
            boolean excelAdjust = isExcelCompanyLog(log, excelBatchNos);
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                log.setAuditStatus(AuditStatus.APPROVED.getCode());
                log.setAdjustLogId(log.getId());
                if (forbiddenPoolAdjustMapper.addPoolStatus(log) != 1) {
                    throw new BizException("主体调入池状态写入失败，请刷新后重试");
                }
            } else if (AdjustMode.OUT.getCode().equals(log.getAdjustMode())) {
                int deleted = forbiddenPoolAdjustMapper.deletePoolStatusSoft(
                        log.getSecurityCode(), log.getTargetPoolId());
                if (deleted == 0 && !excelAdjust) {
                    throw new BizException("主体当前池状态已发生变化，请刷新后重试");
                }
            }
        }
    }

    /**
     * 识别主体 Excel 调库主项的批次，供同批次配套项使用
     *
     * 执行顺序：
     * 1. 筛出 Excel 导入或清空的主体记录，收集非空批次号并去重
     *
     * @param logList 本次待生效的调库日志
     * @return Excel 导入或清空主项的非空批次号
     */
    private Set<String> collectExcelBatchNos(List<IpAdjustLogBo> logList) {
        return logList.stream()
                .filter(log -> COMPANY_SECURITY_TYPE.equals(log.getSecurityType())
                        // 判断调库来源是否由 Excel 导入服务生成
                        && isExcelAdjustType(log.getAdjustType()))
                .map(IpAdjustLogBo::getAdjustBatchNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    /**
     * 判断主体调库日志是否属于 Excel 导入或清空申请
     *
     * 执行顺序：
     * 1. 仅主体记录参与判断，调整来源属于 Excel 或批次号在已识别的 Excel 批次中时返回 true
     *
     * @param log 待生效的调库日志
     * @param excelBatchNos Excel 主项的批次号集合
     * @return true=主体 Excel 调库主项或同批配套项，false=不属于主体 Excel 调库批次
     */
    private boolean isExcelCompanyLog(IpAdjustLogBo log, Set<String> excelBatchNos) {
        return COMPANY_SECURITY_TYPE.equals(log.getSecurityType())
                // 判断调库来源是否由 Excel 导入服务生成
                && (isExcelAdjustType(log.getAdjustType()) || excelBatchNos.contains(log.getAdjustBatchNo()));
    }

    /**
     * 判断调库来源是否由 Excel 导入服务生成
     *
     * 执行顺序：
     * 1. 调整类型等于 Excel 导入或清空目标池时返回 true
     *
     * @param adjustType 调库来源
     * @return true=Excel 导入或清空，false=其他调整类型
     */
    private boolean isExcelAdjustType(String adjustType) {
        return SecurityPoolAdjustService.EXCEL_IMPORT_ADJUST_TYPE.equals(adjustType)
                || AdjustType.CLEAR_POOL.getCode().equals(adjustType);
    }

    /**
     * 返回第一个最终审批复核失败原因
     *
     * 执行顺序：
     * 1. 按原顺序查找第一条非空的复核失败原因，全部为空时返回 null
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
     * 抛出包含实际调整对象上下文的复核异常
     *
     * 执行顺序：
     * 1. 先根据证券类型确定错误提示显示主体或证券，再拼入对象代码、调库方向、目标池和复核原因并抛出
     *
     * @param log 待生效的调库日志
     * @param reason 单条校验返回的失败原因，null 表示通过
     */
    private void throwFinalRecheckFailure(IpAdjustLogBo log, String reason) {
        String objectLabel = COMPANY_SECURITY_TYPE.equals(log.getSecurityType()) ? "主体" : "证券";
        throw new BizException(objectLabel + "[" + log.getSecurityCode() + "]" + log.getAdjustMode()
                + "投资池[" + log.getTargetPoolName() + "]失败：" + reason);
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
            // 基金类型特有调入校验（暂无，后续 P2 加基金评分）
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
     * 含池锁定、pending流程、重复入池、容量、来源池、限制池、互斥、弹性禁投
     * 主体调整不具备证券品种和市场属性，跳过对应校验
     * 不含证券到期（已拆到类型特有：债券到期 checkBondIn / 股票退市 checkStockIn）
     *
     * 执行顺序：
     * 1. 前置检查：目标池是否已锁定（最硬拦截，优先执行）
     * 2. 前置检查：是否存在待处理流程
     * 3. 入池检查：证券是否已在目标池中
     * 4. 入池检查：目标池容量是否已满
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
        if (!CategoryType.COMPANY.getCode().equals(ctx.getCategoryType())) {
            // 前置检查：证券品种是否符合目标池配置
            addIfFailed(failures, inCheckVariety(ctx));
            // 前置检查：证券市场是否符合目标池配置
            addIfFailed(failures, inCheckMarket(ctx));
        }
        // 2. 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 3. 入池检查：证券是否已在目标池中
        addIfFailed(failures, inCheckSecurityAlreadyInPool(ctx));
        // 4. 入池检查：目标池容量是否已满
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
     *
     * @param ctx 调库校验上下文
     * @return 阻断债券调入的原因
     */
    private List<String> checkBondIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 债券到期校验
        addIfFailed(failures, inCheckBondMaturity(ctx));
        return failures;
    }

    /**
     * 股票类型特有调入校验
     *
     * 含股票退市校验（delist_date 早于今日则禁止调入）
     *
     * 执行顺序：
     * 1. 股票退市校验
     * 2. 股票入池评级限制（老系统 StockResearch/investrank），当前未接入股票评级来源时跳过
     *
     * @param ctx 调库校验上下文
     * @return 阻断股票调入的原因
     */
    private List<String> checkStockIn(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 1. 股票退市校验
        addIfFailed(failures, inCheckStockDelist(ctx));
        // 2. 股票入池评级限制（老系统 StockResearch/investrank），当前未接入股票评级来源时跳过
        addIfFailed(failures, inCheckGradeAstrict(ctx));
        return failures;
    }

    /**
     * 基金类型特有调入校验（暂无，后续 P2 加基金评分）
     *
     * 执行顺序：
     * 1. 当前没有适用于基金调入的专属规则，返回空的失败原因列表
     *
     * @param ctx 调库校验上下文
     * @return 空的失败原因列表
     */
    private List<String> checkFundIn(AdjustCheckContext ctx) {
        return new ArrayList<>();
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
     * 1. 按常规调出规则检查通用条件和品种专属条件，沿用普通调出限制
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkOutConditions(AdjustCheckContext ctx) {
        // 校验调出规则，主体 Excel 调出跳过黑名单质押库三条件
        return checkOutConditions(ctx, false);
    }

    /**
     * 校验调出规则，主体 Excel 调出跳过黑名单质押库三条件
     *
     * 执行顺序：
     * 1. 通用校验（所有类型都走）
     * 2. 类型特有校验（按 categoryType 路由）
     *
     * @param ctx 调库校验上下文
     * @param excelOutbound 是否为主体 Excel 调出
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    private List<String> checkOutConditions(AdjustCheckContext ctx, boolean excelOutbound) {
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
        if (!excelOutbound) {
            // 普通调出按发行主体复核黑名单质押库三条件
            addPledgeBlacklistFailure(failures, ctx, AdjustMode.OUT.getCode());
        }
        return failures;
    }

    /**
     * 主体或债券进入/退出黑名单质押库时统一复核三条件
     *
     * 执行顺序：
     * 1. 目标池是黑名单质押库时，确定实际发行主体和本批拟发生的池变动
     * 2. 从预读数据或实时入口核对三项条件，把阻断原因并入本次校验结果
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
        String companyCode = security == null ? null
                : (CategoryType.COMPANY.getCode().equals(ctx.getCategoryType())
                ? security.getWindCode() : security.getIssuerCode());
        Set<Long> inboundPoolIds = CategoryType.COMPANY.getCode().equals(ctx.getCategoryType())
                ? ctx.getRequestInPoolIds() : Collections.<Long>emptySet();
        Set<Long> outboundPoolIds = CategoryType.COMPANY.getCode().equals(ctx.getCategoryType())
                ? ctx.getRequestOutPoolIds() : Collections.<Long>emptySet();
        BatchCompanyAdjustCheckData batch = ctx.getBatchCompanyData();
        String failure;
        if (batch == null || !StringUtils.hasText(companyCode)) {
            failure = pledgeBlacklistRuleService.validate(companyCode, adjustMode, inboundPoolIds, outboundPoolIds);
        } else {
            PledgeBlacklistRuleService.Decision decision = pledgeBlacklistRuleService.evaluateCompanyBatch(
                    batch, companyCode, inboundPoolIds, outboundPoolIds);
            failure = pledgeBlacklistRuleService.validateDecision(decision, adjustMode);
        }
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
     * 规则：调整对象是否存在进行中的调库流程
     *
     * 存在 pending 流程步骤的调库记录视为进行中，
     * 需等待该流程终结后方可再次发起调库，避免并发操作导致入池状态混乱
     *
     * 执行顺序：
     * 1. 没有在途流程时返回 null
     * 2. 有在途流程时按主体或证券选用失败提示，有节点名称时一并显示
     *
     * @param ctx 调库校验上下文
     * @return 进行中的流程提示，没有时为 null
     */
    private String preCheckPendingProcess(AdjustCheckContext ctx) {
        if (ctx.isHasPendingProcess()) {
            String reason = COMPANY_SECURITY_TYPE.equals(ctx.getSecurityInfo().getSecurityType())
                    ? COMPANY_PENDING_PROCESS_FAILURE : SecurityPoolCheckRules.PENDING_PROCESS_FAILURE;
            String nodeLabel = ctx.getPendingProcessNodeLabel();
            if (nodeLabel != null && !nodeLabel.trim().isEmpty()) {
                return reason + "（当前节点：" + nodeLabel.trim() + "）";
            }
            return reason;
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
            return SecurityPoolCheckRules.ALREADY_IN_POOL_FAILURE;
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
     * categoryType 由 dict_security_type 表按 securityType 查询得到（bond/fund/stock）
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
        String categoryType = ctx.getBatchCompanyData() == null
                ? forbiddenPoolAdjustMapper.queryCategoryTypeBySecurityType(ctx.getSecurityInfo().getSecurityType())
                : ctx.getCategoryType();
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
     * 市场 code 统一见 MarketCode，当前市场由 SecurityMarketService 查询
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
        if (!securityMarketService.matchesPoolMarkets(
                pool.getMarketCodes(), ctx.getSecurityInfo())) {
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
        boolean forbidden = ctx.getBatchCompanyData() == null
                ? forbiddenPoolAdjustMapper.querySecurityInForbiddenPool(ctx.getSecurityInfo().getWindCode())
                // 判断有效在池记录关联的未删除投资池是否包含指定类型
                : isInPoolType(ctx.getCurrentPoolIds(), ctx.getPoolMap(), "forbidden");
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
     * 当前项目无行业编码主数据，暂用 SecurityInfoBo.industryName 名称精确匹配（老项目为编码前缀层级匹配）
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
     * 1. 只对开启开放日限制的目标池执行检查
     * 2. 优先使用批量校验数据，没有批量数据时查询当天开放日，未开放则返回原因
     *
     * @param ctx 调库校验上下文
     * @return 非开放日提示，符合时为 null
     */
    private String inCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        BatchCompanyAdjustCheckData batch = ctx.getBatchCompanyData();
        boolean open = batch == null ? forbiddenPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), LocalDate.now().toString())
                : batch.openToday.contains(pool.getId());
        if (!open) {
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
     * 1. 只对开启开放日限制的目标池执行检查
     * 2. 优先使用批量校验数据，没有批量数据时查询当天开放日，未开放则返回原因
     *
     * @param ctx 调库校验上下文
     * @return 非开放日提示，符合时为 null
     */
    private String outCheckOpenDay(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool == null || pool.getOpenDayAdjust() == null || pool.getOpenDayAdjust() != 1) {
            return null;
        }
        BatchCompanyAdjustCheckData batch = ctx.getBatchCompanyData();
        boolean open = batch == null ? forbiddenPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), LocalDate.now().toString())
                : batch.openToday.contains(pool.getId());
        if (!open) {
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
     * 负责将"本次校验共享数据"与"当次调库项的目标池信息"合并填充到上下文中，
     * 供三层校验方法直接读取，无需再逐级传参
     *
     * 执行顺序：
     * 1. 从全量投资池 Map 中取出目标池及其父级池（用于构建错误消息中的池路径）
     * 2. 取出目标池自身的所有关系配置（来源池、限制池、联动池等），供关系型校验规则直接使用
     * 3. 调出时读取证券在目标池的入池时间，用于冻结期校验
     * 4. 证券品种大类用于类型特有校验路由
     *
     * @param item 当前调库项（目标池 ID、调整方向等）
     * @param poolCurrentCount 目标池当前有效证券数量（调用方按需查询）
     * @param shared 本次 checkAdjust 调用的共享数据
     * @return 当前调整项的规则检查上下文
     */
    private AdjustCheckContext buildCheckContext(AdjustCheckReq.CheckItem item, int poolCurrentCount, AdjustSharedData shared) {
        // 1. 从全量投资池 Map 中取出目标池及其父级池（用于构建错误消息中的池路径）
        InvestmentPoolBo targetPool = shared.getPoolMap().get(item.getTargetPoolId());
        InvestmentPoolBo parentPool = (targetPool != null && targetPool.getParentId() != null)
                ? shared.getPoolMap().get(targetPool.getParentId()) : null;

        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(shared.getSecurityInfo());
        ctx.setBatchCompanyData(shared.getBatchCompanyData());
        ctx.setTargetPool(targetPool);
        ctx.setParentPool(parentPool);
        ctx.setCurrentPoolIds(shared.getCurrentPoolIds());
        ctx.setPoolCurrentCount(poolCurrentCount);
        ctx.setAdjustMode(item.getAdjustMode());
        // 2. 取出目标池自身的所有关系配置（来源池、限制池、联动池等），供关系型校验规则直接使用
        ctx.setTargetPoolRelations(shared.getPoolRelationMap().getOrDefault(item.getTargetPoolId(), Collections.emptyMap()));
        ctx.setPoolMap(shared.getPoolMap());
        ctx.setHasPendingProcess(shared.getPendingPoolGroupIds() != null
                // 查找目标池所属顶级投资池
                && shared.getPendingPoolGroupIds().contains(resolveRootPoolId(item.getTargetPoolId(), shared.getPoolMap())));
        ctx.setPendingProcessNodeLabel(shared.getPendingProcessNodeLabel());
        ctx.setSecurityInObservePool(shared.isSecurityInObservePool());
        ctx.setIssuerInObservePool(shared.isIssuerInObservePool());
        ctx.setRequestInPoolIds(shared.getRequestInPoolIds());
        ctx.setRequestOutPoolIds(shared.getRequestOutPoolIds());
        // 3. 调出时读取证券在目标池的入池时间，用于冻结期校验
        BatchCompanyAdjustCheckData batch = shared.getBatchCompanyData();
        if (AdjustMode.OUT.getCode().equals(item.getAdjustMode())) {
            String code = shared.getSecurityInfo().getWindCode();
            ctx.setTargetPoolEntryTime(batch == null
                    ? forbiddenPoolAdjustMapper.queryPoolEntryTime(code, item.getTargetPoolId())
                    : batch.states.get(code).entryTimes.get(item.getTargetPoolId()));
        }
        // 4. 证券品种大类用于类型特有校验路由
        ctx.setCategoryType(batch == null ? forbiddenPoolAdjustMapper.queryCategoryTypeBySecurityType(
                shared.getSecurityInfo().getSecurityType()) : batch.categoryType);
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
     * @param excelOutbound 是否为主体 Excel 调出生成的配套项
     * @return 联动或互斥调整的检查结果
     */
    private AdjustCheckDto.CheckResultItem buildAutoResultItem(
            Long targetPoolId, String adjustMode, String itemTag, String adjustGroupKey, AdjustSharedData shared,
            boolean excelOutbound) {

        // 读取目标池有效数量，批量主体校验使用当前请求已查结果
        int poolCurrentCount = poolCurrentCount(targetPoolId, shared);
        InvestmentPoolBo pool = shared.getPoolMap().get(targetPoolId);

        // 1. 构造虚拟 CheckItem，使其可以复用 buildCheckContext 逻辑
        AdjustCheckReq.CheckItem fakeItem = new AdjustCheckReq.CheckItem();
        fakeItem.setTargetPoolId(targetPoolId);
        fakeItem.setAdjustMode(adjustMode);
        fakeItem.setPoolType(pool != null ? pool.getPoolType() : null);

        // 2. 构建调库校验上下文
        AdjustCheckContext ctx = buildCheckContext(fakeItem, poolCurrentCount, shared);

        List<String> failures = AdjustMode.IN.getCode().equals(adjustMode)
                // 校验调出规则，主体 Excel 调出跳过黑名单质押库三条件
                ? checkInConditions(ctx) : checkOutConditions(ctx, excelOutbound);

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
     * @param manualItem 来源手工调整项
     */
    private void inheritManualItemFailure(
            AdjustCheckDto.CheckResultItem autoItem, AdjustCheckDto.CheckResultItem manualItem) {
        if (manualItem.isCanAdjust()) {
            return;
        }
        List<String> failures = new ArrayList<>();
        failures.add("“" + manualItem.getPoolName() + "”校验未通过");
        failures.addAll(autoItem.getFailReasons());
        autoItem.setCanAdjust(false);
        autoItem.setFailReasons(failures);
    }

    /**
     * 互斥配套调出失败时，反阻断同组手工调入，避免只入目标池导致双池
     *
     * 执行顺序：
     * 1. 互斥子原因拆成独立 failReasons，避免「；」嵌套成一句矛盾长文
     *
     * @param manualItem 来源手工调整项
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
     * 自动联动项失败时反向阻断同组来源项
     *
     * 执行顺序：
     * 1. 主项可调整且存在自动项时，收集同组自动项未通过的原因
     * 2. 有失败原因时合并主项已有原因，并将主项标为不可调整
     *
     * @param manualItem 来源手工调整项
     * @param autoItems 同组自动联动项
     * @param failurePrefix 联动失败提示前缀
     */
    private void propagateAutoFailureToManual(AdjustCheckDto.CheckResultItem manualItem,
                                              List<AdjustCheckDto.CheckResultItem> autoItems,
                                              String failurePrefix) {
        if (manualItem == null || !manualItem.isCanAdjust() || autoItems == null || autoItems.isEmpty()) {
            return;
        }
        List<String> failures = new ArrayList<>();
        for (AdjustCheckDto.CheckResultItem autoItem : autoItems) {
            if (autoItem == null || autoItem.isCanAdjust()) {
                continue;
            }
            String poolName = autoItem.getPoolName() != null
                    ? autoItem.getPoolName() : String.valueOf(autoItem.getTargetPoolId());
            failures.add(failurePrefix + "（" + poolName + "）");
            if (autoItem.getFailReasons() != null) {
                failures.addAll(autoItem.getFailReasons());
            }
        }
        if (failures.isEmpty()) {
            return;
        }
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
    private String buildAdjustGroupKey(AdjustCheckReq.CheckItem item) {
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
     * 1. 根据日志、批次和流程快照建立初始步骤，缺省时逐次解析处理人并保存步骤
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
        // 生成原有初始步骤，批量调用先收集步骤再统一写入
        return createInitialSteps(adjustLogId, adjustBatchNo, snapshot, adjusterId, adjusterName, null, null);
    }

    /**
     * 生成原有初始步骤，批量调用先收集步骤再统一写入
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
     * @param steps 待处理的调库审批步骤列表
     * @param handlersByNodeId 按ID索引的列表
     * @return true=流程初始化时已经到达结束节点，false=流程初始化后尚未到达结束节点
     */
    private boolean createInitialSteps(Long adjustLogId, String adjustBatchNo, FlowSnapshot snapshot,
                                        String adjusterId, String adjusterName, List<IpAdjustStepBo> steps,
                                       Map<Long, List<HandlerTarget>> handlersByNodeId) {
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
                         null, null, ProcessAction.AUTO_PROCESS.getCode(), null, now, steps);

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
                                 adjusterId, adjusterName, ProcessAction.SUBMIT.getCode(), null, now, steps);
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
                                 null, null, ProcessAction.AUTO_PROCESS.getCode(), "系统自动审批通过", now, steps);
                // 查找初始步骤的下一个节点
                FlowNodeBo nextNode = findNextNodeForInitialSteps(snapshot, currentNode, prevNode);
                prevNode = currentNode;
                currentNode = nextNode;
                continue;
            }

            if (NodeType.APPROVAL.getCode().equals(currentNode.getNodeType())) {
                // 为审批节点创建待处理步骤记录（按处理人明细展开为具体人员）
                createPendingSteps(adjustLogId, adjustBatchNo, currentNode, snapshot, now, steps, handlersByNodeId);
                return false;
            }

            if (NodeType.END.getCode().equals(currentNode.getNodeType())) {
                sortOrder = currentNode.getSortOrder() != null ? currentNode.getSortOrder() : 1;
                // 插入单条步骤记录到 ip_adjust_step
                insertStepRecord(adjustLogId, adjustBatchNo, currentNode, config, sortOrder, ProcessAction.AUTO_PROCESS.getCode(),
                                 null, null, ProcessAction.AUTO_PROCESS.getCode(), null, now, steps);
                return true;
            }

            // 自动节点（node_type=auto）写入 auto_process 步骤后继续向下流转
            if (NodeType.AUTO.getCode().equals(currentNode.getNodeType())) {
                sortOrder = currentNode.getSortOrder() != null ? currentNode.getSortOrder() : 1;
                // 插入单条步骤记录到 ip_adjust_step
                insertStepRecord(adjustLogId, adjustBatchNo, currentNode, config, sortOrder, ProcessAction.AUTO_PROCESS.getCode(),
                                 null, null, ProcessAction.AUTO_PROCESS.getCode(), null, now, steps);
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
     * @param steps 待处理的调库审批步骤列表
     * @param handlersByNodeId 按ID索引的列表
     */
    private void createPendingSteps(Long adjustLogId, String adjustBatchNo, FlowNodeBo node,
                                    FlowSnapshot snapshot, Date now, List<IpAdjustStepBo> steps,
                                    Map<Long, List<HandlerTarget>> handlersByNodeId) {
        NodeApprovalConfigBo config = snapshot.approvalConfigMap.get(node.getId());
        // 1. 将审批处理人配置解析为具体人员
        List<HandlerTarget> handlers = handlersByNodeId == null ? resolveApprovalHandlers(config, snapshot)
                // 将审批处理人配置解析为具体人员，角色会递归展开到子角色下的人员并去重
                : handlersByNodeId.computeIfAbsent(node.getId(), id -> resolveApprovalHandlers(config, snapshot));
        int sortOrder = node.getSortOrder() != null ? node.getSortOrder() : 1;

        if (handlers.isEmpty()) {
            // 无配置处理人时仍创建一条空处理人的待处理记录
            insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                             null, null, null, null, now, steps);
            return;
        }
        // 2. 只排除本批次真正审核过的人，待办和抢占跳过的人仍可进入下一节点
        if (steps == null) {
            // 排除本批次真正审核过的人
            handlers = excludeParticipatedHandlers(adjustLogId, adjustBatchNo, handlers);
        } else {
            // 批量提交使用本组刚生成的步骤判断已审核人员
            Set<String> participated = AdjustStepHandlerExcludeUtil.collectParticipatedHandlerIds(steps);
            handlers = AdjustStepHandlerExcludeUtil.excludeParticipated(handlers, participated, h -> h.handlerId);
            if (handlers.isEmpty()) {
                throw new BizException(AdjustStepHandlerExcludeUtil.NO_AVAILABLE_HANDLER_MSG);
            }
        }
        for (HandlerTarget handler : handlers) {
            // 插入单条步骤记录到 ip_adjust_step
            insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                             handler.handlerId, handler.handlerName, null, null, now, steps);
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
        List<IpAdjustStepBo> existingSteps = forbiddenPoolAdjustMapper.queryAdjustStepByBatchList(
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
     * 1. 填写调库日志、批次、流程节点、处理人、动作和时间等步骤信息
     * 2. 传入步骤列表时先收集供批量写入，未传列表时直接保存
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
     * @param startTime 本审批步骤的开始时间，已处理步骤同时用作处理时间
     * @param steps 用于批量保存的审批步骤列表，传入 null 时立即写入本条步骤
     */
    private void insertStepRecord(Long adjustLogId, String adjustBatchNo, FlowNodeBo node,
                                  NodeApprovalConfigBo config, int sortOrder,
                                  String stepStatus, String handlerId, String handlerName,
                                  String processAction, String processComment, Date startTime,
                                   List<IpAdjustStepBo> steps) {
        // 1. 填写调库日志、批次、流程节点、处理人、动作和时间等步骤信息
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
        // 2. 传入步骤列表时先收集供批量写入，未传列表时直接保存
        if (steps == null) {
            forbiddenPoolAdjustMapper.addAdjustStep(step);
        } else {
            steps.add(step);
        }
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
     * 1. 自动项缺少简称或类型时，按实际调整对象回查主数据
     * 2. 根据调库项来源确定落表调整类型
     * 3. 同一次提交共用统一提交时间，避免大批量逐条 NOW() 导致历史排序同组打散
     *
     * @param req 调库申请（含证券级别信息）
     * @param item 单个调库项（含目标池、调整方向等）
     * @param flowSource 同组手工调库项，用于写入流程快照
     * @param shared 本次提交的共享数据
     * @return 待保存的 CRMW 调库日志
     */
    private IpAdjustLogBo buildAdjustLog(SecurityPoolAdjustSubmitReq req,
                                         SecurityPoolAdjustSubmitReq.AdjustItem item,
                                         SecurityPoolAdjustSubmitReq.AdjustItem flowSource,
                                         SubmitSharedData shared) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        // 解析调库项实际调整对象：调库项代码优先，否则回退请求级主体代码
        String securityCode = resolveItemSecurityCode(req, item);
        String securityShortName = item.getSecurityShortName() != null && !item.getSecurityShortName().isEmpty()
                ? item.getSecurityShortName() : req.getSecurityShortName();
        String securityType = item.getSecurityType() != null && !item.getSecurityType().isEmpty()
                ? item.getSecurityType() : req.getSecurityType();
        // 1. 自动项缺少简称或类型时，按实际调整对象回查主数据
        if ((securityShortName == null || securityShortName.isEmpty()
                || securityType == null || securityType.isEmpty())
                && securityCode != null && !securityCode.isEmpty()) {
            SecurityInfoBo securityInfo = forbiddenPoolAdjustMapper.querySecurityBoByCode(securityCode);
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
        // 2. 根据调库项来源确定落表调整类型
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
        // 3. 同一次提交共用统一提交时间，避免大批量逐条 NOW() 导致历史排序同组打散
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
    private void bindSubmitAttachments(Long adjustLogId, SecurityPoolAdjustSubmitReq.AdjustItem item,
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
     * 1. 互斥项和联动项各用对应调整类型，其他来源沿用请求中的类型
     *
     * @param req 调库提交请求
     * @param item 调库项
     * @return 写入日志的调整来源
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

    /**
     * 解析调库项实际调整对象：调库项代码优先，否则回退请求级主体代码
     *
     * 执行顺序：
     * 1. 明细有代码时使用明细代码，否则使用请求中的主体代码
     *
     * @param req 调库提交请求
     * @param item 调库项
     * @return 当前调整项的标的证券代码
     */
    private String resolveItemSecurityCode(SecurityPoolAdjustSubmitReq req,
                                           SecurityPoolAdjustSubmitReq.AdjustItem item) {
        if (item != null && item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()) {
            return item.getSecurityCode();
        }
        return req.getSecurityCode();
    }

    /**
     * 构建投资池全路径名称
     *
     * 执行顺序：
     * 1. 找不到当前池时返回空路径，根池直接使用自身名称
     * 2. 有父池时递归查找上级路径，再用斜线拼接当前名称
     *
     * @param poolId 目标投资池 ID
     * @param poolMap 当前投资池映射
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
         * 保存主体禁投池调库发起审批所需的流程快照
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
         * 记录主体禁投池调库候选审批人
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
         * 保存本次主体禁投池调库各申请项共用的数据
         *
         * 执行顺序：
         * 1. 保存主体和池资料、在途及观察池状态、流程快照，并保留批次号上下文
         *
         * @param securityInfo 用于主体调库校验的主数据
         * @param poolMap 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称
         * @param currentPoolIds 主体当前所在的池 ID
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
     * 保存一批 Excel 主体调库共用的提交数据
     *
     * 同一事务内复用主体和投资池资料、流程快照、直通判定及处理人配置，并共用批次号上下文
     */
    static final class ExcelCompanyBatchContext {
        /** 按主体代码查找本批已读取的主体详情 */
        final Map<String, ForbiddenPoolAdjustDto> companies;

        /** 按池 ID 查找本批使用的投资池配置 */
        final Map<Long, InvestmentPoolBo> pools;

        /** 本批已解析的流程 Key 与流程 ID 的对应关系 */
        final Map<String, Long> flowIds = new HashMap<>();

        /** 按流程 ID 缓存本批使用的流程快照 */
        final Map<Long, FlowSnapshot> flows = new HashMap<>();

        /** 按流程 ID 缓存是否可以直通的判定 */
        final Map<Long, Boolean> directFlows = new HashMap<>();

        /** 按审批节点 ID 保存已解析的处理人 */
        final Map<Long, List<HandlerTarget>> handlersByNodeId = new HashMap<>();

        /** 本批主体调库共用的批次号生成数据 */
        final BatchNoContext batchNoContext = new BatchNoContext();

        /**
         * 缓存同一批 Excel 主体调库所需的主数据和池配置
         *
         * 执行顺序：
         * 1. 保存已按代码整理的主体资料和按 ID 整理的池配置
         *
         * @param companies 按主体代码整理的主数据
         * @param pools 按池 ID 整理的投资池配置
         */
        ExcelCompanyBatchContext(Map<String, ForbiddenPoolAdjustDto> companies, Map<Long, InvestmentPoolBo> pools) {
            this.companies = companies;
            this.pools = pools;
        }
    }

    /**
     * 保存一行待写入的 Excel 主体调库数据
     *
     * 将该行生成的调库日志和对应的初始审批步骤一起交给批量保存
     */
    static final class ExcelCompanySubmitRow {
        /** 本行待写入的主体调库日志 */
        final IpAdjustLogBo log;

        /** 随本行调库日志写入的初始审批步骤 */
        final List<IpAdjustStepBo> steps;

        /**
         * 记录一行 Excel 主体调库待批量保存的数据
         *
         * 执行顺序：
         * 1. 保存调库日志及对应的审批步骤
         *
         * @param log 本行生成的主体调库日志
         * @param steps 本行调库日志对应的审批步骤
         */
        ExcelCompanySubmitRow(IpAdjustLogBo log, List<IpAdjustStepBo> steps) {
            this.log = log;
            this.steps = steps;
        }
    }

    /**
     * 批次号生成上下文
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

