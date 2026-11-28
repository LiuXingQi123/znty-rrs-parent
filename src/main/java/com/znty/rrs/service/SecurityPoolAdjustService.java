package com.znty.rrs.service;

import com.znty.rrs.common.constants.BusinessSwitches;
import com.znty.rrs.common.enums.ApprovalStrategy;


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
import com.znty.rrs.common.enums.MaterialExemptionType;
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.constants.CreditBondPoolCodes;
import com.znty.rrs.common.util.AdjustStepHandlerExcludeUtil;
import com.znty.rrs.common.util.CreditBondRemainTermUtil;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.common.SecurityMarketDto;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.service.SecurityPoolCheckRules.FlowSnapshot;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustLogDto;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.mapper.BatchSecurityCheckMapper;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.springframework.beans.BeanUtils;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.RatingCompanySelectionReq;
import com.znty.rrs.entity.securitypooladjust.RedirectPoolOptionDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderReq;
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
import com.znty.rrs.entity.securitypooladjust.IssuerFinancialDto;
import com.znty.rrs.entity.securitypooladjust.IssuerFinancialSaveReq;
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
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 处理证券池调库申请的校验、提交和直通落池
 *
 * 提供候选证券、证券详情、可调整投资池及在池状态查询，使用 {@link SecurityPoolCheckRules} 校验调库条件并匹配审批流程
 * 调入时核对容量、来源池、调入限制等规则，调出时核对在池状态、冻结期及调出限制
 * 联动池按主项方向补充调整，调入命中互斥池时追加调出项，同券跨市场项按实际证券校验
 * 提交时保存调库日志、审批步骤和证券快照，直通或 {@link SecurityPoolAdjustFlowService} 终审通过后更新池状态
 */
@Service
public class SecurityPoolAdjustService {

    /** Excel 证券导入主项类型，配套项通过同组批次识别来源 */
    public static final String EXCEL_IMPORT_ADJUST_TYPE = "Excel导入";

    /** 统一证券市场及同券查询组件 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";

    /** 短时间重复提交判定窗口（秒） */
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;

    /** 证券池调库数据访问组件 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 单券和批量共用的准入规则 */
    @Resource
    private SecurityPoolCheckRules securityPoolCheckRules;

    /** 是否认可普通状态表中的 CRMW 库作为快速入库来源 */
    private boolean crmwUsePoolStatus = BusinessSwitches.CRMW_USE_POOL_STATUS;

    /** 信用债简易入库是否要求同发行人近180天有有效信评报告 */
    private boolean simpleInboundRecentReportRequired = BusinessSwitches.SIMPLE_INBOUND_RECENT_REPORT_REQUIRED;

    /** ABS 跨池主体身份及已查询候选复用 */
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

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** 读取批量提交共用的证券与池状态 */
    @Resource
    private BatchSecurityCheckMapper batchSecurityCheckMapper;

    /** 批量读取评级主体 */
    @Resource
    private BatchSecurityCheckService batchSecurityCheckService;

    /** 报告库服务，用于直通落池成功后统一生成内部报告 */
    @Resource
    private ReportService reportService;


    /** 评级下调判定组件（主体/展望/担保人评级下调判断，查 wind_cbondissuerrating） */
    @Resource
    private RatingDowngradeChecker ratingDowngradeChecker;

    /** 黑名单质押库三条件统一判定服务 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;
    /**
     * 获取规则组件，直接构造服务时沿用显式注入的依赖
     *
     * 执行顺序：
     * 1. 有已注入的规则组件时直接使用，否则用当前依赖和配置构造规则组件
     *
     * @return 共用的证券准入规则
     */
    private SecurityPoolCheckRules checkRules() {
        if (securityPoolCheckRules != null) {
            return securityPoolCheckRules;
        }
        return new SecurityPoolCheckRules(securityPoolAdjustMapper, securityMarketService, flowMapper, creditBondGradeRuleMapper,
                absIdentityService, pledgeBlacklistRuleService, ratingDowngradeChecker, crmwUsePoolStatus,
                simpleInboundRecentReportRequired);
    }

    /**
     * 设置信用债简易入库是否强制要求有效信评报告
     *
     * 执行顺序：
     * 1. 先更新服务配置，规则组件已注入时同步更新组件配置
     *
     * @param simpleInboundRecentReportRequired 信用债简易入库是否要求同发行人近 180 天有有效信评报告
     */
    void setSimpleInboundRecentReportRequired(boolean simpleInboundRecentReportRequired) {
        this.simpleInboundRecentReportRequired = simpleInboundRecentReportRequired;
        if (this.securityPoolCheckRules != null) {
            this.securityPoolCheckRules.setSimpleInboundRecentReportRequired(simpleInboundRecentReportRequired);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  查询类接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 分页查询证券列表
     *
     * 执行顺序：
     * 1. 开启分页，将完整查询条件传给 Mapper，包含证券全称和主体编码
     * 2. 将查询结果、总数和分页信息一起返回
     *
     * @param req 查询条件，证券代码、简称、全称、主体编码和主体全称为模糊匹配，证券类型为精确匹配
     * @return 证券列表和分页信息
     */
    public PageResult<SecurityInfoDto> querySecurityPage(SecurityPoolAdjustReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityInfoDto> records = securityPoolAdjustMapper.querySecurityPage(req);
        PageInfo<SecurityInfoDto> pageInfo = new PageInfo<>(records);

        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询候选证券中出现的证券类型选项（code + name）
     *
     * 执行顺序：
     * 1. 查询禁投池中出现的证券类型下拉选项（code + name）
     *
     * @return 证券类型选项列表
     */
    public List<SecurityTypeOptionDto> querySecurityTypeList() {
        return securityPoolAdjustMapper.querySecurityTypeList();
    }

    /**
     * 查询证券详情（调库页面顶部基础信息展示）
     *
     * 执行顺序：
     * 1. ① 有调库日志：该笔提交快照整包回显（审批/详情精确留痕）
     * 2. ② 无 log 或该 log 无快照：主档打底 + 该券最新快照覆盖可编辑字段（标识类始终跟主档）
     * 3. 回填证券品种大类（dict_security_type.category_type），供前端按类型差异化展示（如基金评分输入）
     * 4. 关联市场实时读取 Wind，历史快照仍保留其余业务字段
     *
     * @param req 需携带 securityCode
     * @return 标的证券详情
     */
    public SecurityInfoDetailDto querySecurityDetail(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityInfoDetailDto dto = null;
        SecurityInfoBo currentMaster = null;
        // 1. ① 有调库日志：该笔提交快照整包回显（审批/详情精确留痕）
        if (req.getAdjustLogId() != null) {
            dto = securityPoolAdjustMapper.querySecuritySnapshotDetailByAdjustLogId(
                    req.getAdjustLogId(), req.getSecurityCode());
        }
        // 2. ② 无 log 或该 log 无快照：主档打底 + 该券最新快照覆盖可编辑字段（标识类始终跟主档）
        if (dto == null) {
            SecurityInfoDetailDto master = securityPoolAdjustMapper.querySecurityDetail(req.getSecurityCode());
            if (master == null) {
                throw new BizException(404, "证券不存在");
            }
            SecurityInfoDetailDto latestSnapshot =
                    securityPoolAdjustMapper.queryLatestSecuritySnapshotDetailByWindCode(req.getSecurityCode());
            // 有历史快照时仅覆盖表单可编辑业务字段，避免只读标识与主档不一致
            // 保留已读取主档供当前身份恢复，历史表单覆盖不需要另查主档
            currentMaster = new SecurityInfoBo();
            BeanUtils.copyProperties(master, currentMaster);
            // 将快照中的「可编辑业务字段」覆盖到主档详情上
            overlayEditableFieldsFromSnapshot(master, latestSnapshot);
            // 默认带出同发行人近 180 天内最新已审批通过调入业务的证券分析
            String latestAnalysis = securityPoolAdjustMapper.queryLatestAnalysisByIssuerWithinDays(
                    req.getSecurityCode(), SecurityPoolCheckRules.SIMPLE_INBOUND_HISTORY_DAYS);
            if (latestAnalysis != null && !latestAnalysis.trim().isEmpty()) {
                master.setAnalysis(latestAnalysis);
            }
            dto = master;
        }
        // 3. 回填证券品种大类（dict_security_type.category_type），供前端按类型差异化展示（如基金评分输入）
        dto.setCategoryType(securityPoolAdjustMapper.queryCategoryTypeBySecurityType(dto.getSecurityType()));
        // 4. 关联市场实时读取 Wind，历史快照仍保留其余业务字段
        if (!"company".equals(dto.getSecurityType())) {
            dto.setMarketCode(securityMarketService.queryMarketCode(dto.getWindCode()));
            dto.setRelatedMarketCodes(securityMarketService.queryRelatedMarketCodes(dto.getWindCode()));
        }
        if (CreditBondSpecialInboundRule.isAbs(dto)) {
            dto.setAbsIdentity(absIdentityService.queryCurrentIdentity(currentMaster == null
                    ? securityPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode()) : currentMaster));
        }
        return dto;
    }

    /**
     * 查询当前证券主档，独立于历史快照和调库规则，仅供只读对比
     *
     * 执行顺序：
     * 1. 检查证券代码不能为空
     * 2. 查询证券详情（调库页面顶部基础信息展示）
     *
     * @param req 需携带 securityCode
     * @return 证券详细信息
     */
    public SecurityInfoDetailDto querySecurityMasterDetail(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().trim().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityInfoDetailDto master = securityPoolAdjustMapper.querySecurityDetail(req.getSecurityCode().trim());
        if (master == null) {
            throw new BizException(404, "证券主档不存在：" + req.getSecurityCode());
        }
        return master;
    }

    /**
     * 查询默认四期财务指标，固定年报缺失时保留空白列
     *
     * 执行顺序：
     * 1. 检查证券代码是否为空，并去掉首尾空格
     * 2. 通过证券代码关联发行主体，查询最大的 REPORTDATE；有最新报告日期时校验其格式
     * 3. 按 Asia/Shanghai 时区获取当前自然年，计算上一年年报日期
     * 4. 确定四个报告日期：最新一期为上一年年报或完全没有财报时，取前四年年报；其他情况取前三年年报，再追加数据库最新一期
     * 5. 精确查询这四个报告日期的指标，并按确定的四列顺序组装结果；缺失期补充仅含报告日期、指标全部为 null 的记录。补充记录只用于页面展示，不写入数据库
     *
     * @param req 携带证券代码的查询请求
     * @return 按默认四列顺序返回的四条财报记录，缺失期保留日期和空指标
     */
    public List<IssuerFinancialDto> queryIssuerFinancialList(SecurityPoolAdjustReq req) {
        // 1. 校验证券代码和页面主体选择，页面明确留空时返回空列表
        if (req == null || req.getSecurityCode() == null || req.getSecurityCode().trim().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        String securityCode = req.getSecurityCode().trim();
        // 页面明确传入空主体时直接留白，不回查历史主档或财报
        if (req.getSelectedRatingCompany() != null
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                && emptyToNull(req.getSelectedRatingCompany().getCompanyCode()) == null) {
            return Collections.emptyList();
        }
        // 2. 确定本次财报使用的发行主体，ABS 优先复用所选主体
        String selectedIssuer = req.getSelectedRatingCompany() == null ? emptyToNull(req.getIssuerCode())
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                : emptyToNull(req.getSelectedRatingCompany().getCompanyCode());
        // 确定 ABS 当前选择的财报主体，避免审核前借用空主档或其他身份
        if (selectedIssuer == null) selectedIssuer = resolveFinancialIssuer(securityCode, req.getRightsHolderCode(), req.getSelfSelectedRightsHolderCode());
        // 3. 确定最近报告日并计算展示日期
        Long latestDate = selectedIssuer == null
                ? securityPoolAdjustMapper.queryLatestIssuerFinancialReportDate(securityCode)
                : securityPoolAdjustMapper.queryLatestFinancialDateByIssuer(selectedIssuer);
        if (latestDate != null) {
            // 检查源数据报告期，避免错误日期进入四列表头
            validateFinancialReportDate(latestDate);
        }
        int year = LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear();
        long previousAnnual = (year - 1) * 10000L + 1231;
        List<Long> dates = new ArrayList<>();
        boolean fourAnnuals = latestDate == null || latestDate == previousAnnual;
        for (int offset = fourAnnuals ? 4 : 3; offset >= 1; offset--) {
            dates.add((year - offset) * 10000L + 1231);
        }
        if (!fourAnnuals) {
            dates.add(latestDate);
        }
        // 4. 查询主体财报并按展示日期组装结果
        List<IssuerFinancialDto> source = selectedIssuer == null
                ? securityPoolAdjustMapper.queryIssuerFinancialList(securityCode, dates)
                : securityPoolAdjustMapper.queryFinancialListByIssuer(selectedIssuer, dates);
        Map<Long, IssuerFinancialDto> byDate = source.stream().collect(
                Collectors.toMap(IssuerFinancialDto::getReportDate, item -> item));
        List<IssuerFinancialDto> result = new ArrayList<>();
        for (Long date : dates) {
            IssuerFinancialDto record = byDate.get(date);
            if (record == null) {
                record = new IssuerFinancialDto();
                record.setReportDate(date);
            }
            result.add(record);
        }
        return result;
    }

    /**
     * 独立批量保存财报；任一记录写入失败时全部回滚
     *
     * 执行顺序：
     * 1. 空选择不能借用主档中的历史主体保存财报
     * 2. 直接使用页面选择；普通债未传主体时只查一次主档
     *
     * @param req 证券代码、本次选中的评级主体和待保存的财报记录列表
     * @return 发行主体财务列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<IssuerFinancialDto> saveIssuerFinancialList(IssuerFinancialSaveReq req) {
        if (req == null || req.getSecurityCode() == null || req.getSecurityCode().trim().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        if (req.getRecords() == null) {
            throw new BizException("财报修改列表不能为空");
        }
        String securityCode = req.getSecurityCode().trim();
        // 1. 空选择不能借用主档中的历史主体保存财报
        if (req.getSelectedRatingCompany() != null
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                && emptyToNull(req.getSelectedRatingCompany().getCompanyCode()) == null) {
            throw new BizException("请选择权益人或自选权益人后保存财报");
        }
        // 2. 直接使用页面选择；普通债未传主体时只查一次主档
        String selectedIssuer = req.getSelectedRatingCompany() == null ? emptyToNull(req.getIssuerCode())
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                : emptyToNull(req.getSelectedRatingCompany().getCompanyCode());
        // 确定 ABS 当前选择的财报主体，避免审核前借用空主档或其他身份
        if (selectedIssuer == null) selectedIssuer = resolveFinancialIssuer(securityCode, req.getRightsHolderCode(), req.getSelfSelectedRightsHolderCode());
        String issuerCode = selectedIssuer;
        if (issuerCode == null) {
            SecurityInfoBo security = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
            issuerCode = security == null ? null : security.getIssuerCode();
        }
        if (issuerCode == null || issuerCode.trim().isEmpty()) {
            throw new BizException("证券不存在或未配置发行主体：" + securityCode);
        }
        Set<Long> dates = new HashSet<>();
        for (IssuerFinancialDto financial : req.getRecords()) {
            if (financial == null) {
                throw new BizException("财报记录不能为空");
            }
            // 批量预校验全部报告期，保证错误请求不会写入部分记录
            validateFinancialReportDate(financial.getReportDate());
            if (!dates.add(financial.getReportDate())) {
                throw new BizException("报告期不能重复");
            }
        }
        List<IssuerFinancialDto> saved = new ArrayList<>();
        for (IssuerFinancialDto financial : req.getRecords()) {
            boolean hasValue = Arrays.asList(financial.getTotAssets(), financial.getShareholderEquity(),
                    financial.getDebtAssetsRatio(), financial.getTotRev(), financial.getNetProfit(),
                    financial.getNetCashOper(), financial.getNetCashInv(), financial.getGrp(),
                    financial.getGeneralBudgetRev(), financial.getGeneralBudgetExp(), financial.getRoe(),
                    financial.getExpToEbitda(), financial.getEbitda(), financial.getDebtToEbitda())
                    .stream().anyMatch(Objects::nonNull);
            if (!hasValue && (selectedIssuer == null
                    ? securityPoolAdjustMapper.queryIssuerFinancialByReportDate(securityCode, financial.getReportDate())
                    : securityPoolAdjustMapper.queryFinancialByIssuerAndDate(selectedIssuer, financial.getReportDate())) == null) {
                // 空白报告没有可保存的指标，不创建仅含主体和日期的记录
                IssuerFinancialDto empty = new IssuerFinancialDto();
                empty.setReportDate(financial.getReportDate());
                saved.add(empty);
                continue;
            }
            securityPoolAdjustMapper.saveIssuerFinancial(issuerCode, financial);
            saved.add(financial);
        }
        return saved;
    }

    /**
     * 查询发行主体指定报告日期的财务指标
     *
     * 执行顺序：
     * 1. 校验报告日期格式及允许的报告期
     * 2. 空主体仅保留请求的报告期，指标全部留空
     *
     * @param req 需携带证券代码和报告日期
     * @return 对应报告期财务指标，不存在时返回空
     */
    public IssuerFinancialDto queryIssuerFinancialByReportDate(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        // 1. 校验报告日期格式及允许的报告期
        validateFinancialReportDate(req.getReportDate());
        // 2. 空主体仅保留请求的报告期，指标全部留空
        if (req.getSelectedRatingCompany() != null
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                && emptyToNull(req.getSelectedRatingCompany().getCompanyCode()) == null) {
            IssuerFinancialDto empty = new IssuerFinancialDto();
            empty.setReportDate(req.getReportDate());
            return empty;
        }
        // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
        String selectedIssuer = req.getSelectedRatingCompany() == null ? emptyToNull(req.getIssuerCode())
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                : emptyToNull(req.getSelectedRatingCompany().getCompanyCode());
        // 确定 ABS 当前选择的财报主体，避免审核前借用空主档或其他身份
        if (selectedIssuer == null) selectedIssuer = resolveFinancialIssuer(req.getSecurityCode(), req.getRightsHolderCode(), req.getSelfSelectedRightsHolderCode());
        return selectedIssuer == null
                ? securityPoolAdjustMapper.queryIssuerFinancialByReportDate(req.getSecurityCode(), req.getReportDate())
                : securityPoolAdjustMapper.queryFinancialByIssuerAndDate(selectedIssuer, req.getReportDate());
    }

    /**
     * 确定 ABS 当前选择的财报主体，避免审核前借用空主档或其他身份
     *
     * 执行顺序：
     * 1. 未传本次选择时沿用主档查询，普通债不改变原逻辑
     * 2. 直接取自选或普通权益人代码，不重复查询证券和评级
     *
     * @param securityCode 证券代码
     * @param rightsHolderCode 普通权益人
     * @param selfSelectedCode 自选权益人
     * @return ABS 本次主体，未指定时为空
     */
    private String resolveFinancialIssuer(String securityCode, String rightsHolderCode, String selfSelectedCode) {
        // 1. 先取标准化后的自选主体代码
        String selected = emptyToNull(selfSelectedCode);
        // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
        // 2. 未选自选主体时使用关联候选主体代码
        return selected != null ? selected : emptyToNull(rightsHolderCode);
    }

    /**
     * 查询证券池调库页面担保人、权益人下拉共用的四类关系主体候选
     *
     * 执行顺序：
     * 1. 证券代码为空时返回空候选
     * 2. 按去掉首尾空白的代码读取四类关联主体，并缓存本次返回的候选
     *
     * @param req 需携带 securityCode
     * @return 关联评级主体及其关系信息列表
     */
    public List<RelatedRatingCompanyDto> queryRelatedRatingCompanyList(SecurityPoolAdjustReq req) {
        if (req == null || req.getSecurityCode() == null || req.getSecurityCode().trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<RelatedRatingCompanyDto> rows = securityPoolAdjustMapper.queryRelatedRatingCompanyList(req.getSecurityCode().trim());
        absIdentityService.rememberRelated(req.getSecurityCode().trim(), rows);
        return rows;
    }

    /**
     * 分页查询全市场自选权益人候选
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
        List<SelfSelectedRightsHolderDto> records = securityPoolAdjustMapper.querySelfSelectedRightsHolderPage(
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                emptyToNull(queryReq.getCompanyCode()), emptyToNull(queryReq.getCompanyName()));
        absIdentityService.rememberSelf(records);
        PageInfo<SelfSelectedRightsHolderDto> pageInfo = new PageInfo<>(records);
        return new PageResult<>(records, pageInfo.getTotal(), queryReq.getPageIndex(), queryReq.getPageSize());
    }

    /**
     * 将快照中的「可编辑业务字段」覆盖到主档详情上；标识类字段保持主档不变
     *
     * 对齐：再次调库带出上次提交的利率/用途/分析等，同时全称/代码/发行人等只读字段始终跟主档
     *
     * 执行顺序：
     * 1. 主档和快照都存在时，用快照里的期限、评级和主体选择等可编辑字段覆盖当前详情
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
        if (!CreditBondSpecialInboundRule.isAbs(master)) {
            master.setGuarantor(snapshot.getGuarantor());
            master.setGuarantorId(snapshot.getGuarantorId());
            master.setInnerGuarantorRating(snapshot.getInnerGuarantorRating());
        }
        master.setAbsOriginatorName(snapshot.getAbsOriginatorName());
        master.setCompanySelector(snapshot.getCompanySelector());
        master.setAgencyName(snapshot.getAgencyName());
        master.setDateCallExists(snapshot.getDateCallExists());
        master.setDateExists(snapshot.getDateExists());
        master.setFundUse(snapshot.getFundUse());
        master.setPromptReason(snapshot.getPromptReason());
        master.setAnalysis(snapshot.getAnalysis());
    }

    /**
     * 查询近 6 个月最近一条可回填的信评报告（对齐老系统 getLastReprotDocs）
     *
     * 查询顺序：当前券 → 同主体；仅审批通过调入且日志挂有内部/外部库信评附件，
     * 或手工信评附件；
     * 反查报告库附件后供前端预填。前端门禁仅对 {@link CreditBondPoolCodes} 中 1～5 级库回填
     * （对齐老 COMMON.polidEnum 白名单；禁止库等不在名单内不回填）
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
    public LastCreditReportDto queryLastCreditReport(SecurityPoolAdjustReq req) {
        if (req == null || req.getSecurityCode() == null || req.getSecurityCode().trim().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        String securityCode = req.getSecurityCode().trim();
        // 1. 先按当前券查近 6 个月最近一条有信评报告的审批通过调入日志
        Long adjustLogId = securityPoolAdjustMapper.queryLastInboundLogIdWithCreditReportBySecurity(securityCode);
        // 2. 券维度无记录时，按同主体兜底（对齐老 findInvestPoolAdjustHistoryByScodeOrCompancode 主体分支）
        if (adjustLogId == null) {
            adjustLogId = securityPoolAdjustMapper.queryLastInboundLogIdWithCreditReportByIssuer(securityCode);
        }
        if (adjustLogId == null) {
            return null;
        }
        // 3. 优先手工信评附件，其次内部库、外部库，同类附件取 ID 最大的一份
        SysAttachmentBo logAttachment = securityPoolAdjustMapper.queryFirstCreditReportAttachment(adjustLogId);
        if (logAttachment == null || logAttachment.getFileName() == null || logAttachment.getFileName().isEmpty()) {
            return null;
        }
        boolean internal = AttachmentCategory.CREDIT_REPORT_IN.getCode().equals(logAttachment.getAttachmentCategory())
                || AttachmentCategory.CREDIT_REPORT_HAND.getCode().equals(logAttachment.getAttachmentCategory());
        String tableName = internal ? "rrs_report_in" : "rrs_report_out";
        String category = internal ? AttachmentCategory.REPORT_IN.getCode() : AttachmentCategory.REPORT_OUT.getCode();
        // 4. 复制绑定时复用 file_name，可反查报告库原附件供提交校验
        SysAttachmentBo libraryAttachment = securityPoolAdjustMapper.queryReportLibraryAttachmentByFileName(
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
     * 1. 解析当前用户 ID
     * 2. 判断当前用户是否为管理员
     * 3. 只保留投资品种包含债券的池，父级没有债券品种时仍保留，便于展开到下级
     * 4. 调入方向额外：releaseRules=false 时，信用债大库池需满足主体债入库矩阵才显示可调
     * 5. 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
     * 6. 查询各投资池当前有效证券数量
     * 7. InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
     *
     * @param req 当前用户为管理员时返回全量启用池，普通用户仅返回有可调整权限的池及其祖先节点； 候选池包含符合选池条件的 CRMW 池；主体相关池（禁投/观察等）保留以支持单券入池； 调入方向额外按主体内评矩阵过滤信用债大库
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

        // 3. 只保留投资品种包含债券的池，父级没有债券品种时仍保留，便于展开到下级
        allPools = retainPoolsContainingVariety(allPools, CategoryType.BOND.getCode());
        if (allPools.isEmpty()) {
            return new ArrayList<>();
        }
        // 4. 调入方向额外：releaseRules=false 时，信用债大库池需满足主体债入库矩阵才显示可调
        if ("in".equalsIgnoreCase(req.getAdjustDirection())) {
            // 选池阶段按主体内评矩阵过滤信用债 1～5 级（境外债不走这套规则）
            allPools = filterInboundByGradeRule(allPools, req);
            if (allPools.isEmpty()) {
                return new ArrayList<>();
            }
        }

        // 5. 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
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
        // 6. 查询各投资池当前有效证券数量
        Map<Long, Integer> currentCountMap = securityPoolAdjustMapper.queryPoolCurrentCountList().stream()
                .collect(Collectors.toMap(PoolDto::getId, PoolDto::getCurrentCount));
        // 7. InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
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
     * ABS：所选权益人内评（自选权益人优先）1 档只能调入一级库，缺少内评或非 1 档至少下调一级
     * 私募：发债主体内评 1 档只能调入一级库，其余至少下调一级
     * 永续：发债主体内评 1 档下调一级（矩阵最好档再降一档且只留那一档），其余至少下调一级
     * 次级：发债主体内评 1 档只能调入一级库，2+/2/2- 下调一级（只留那一档），其余至少下调一级
     * 私募/永续/次级「1 档」只看发债主体内评；ABS「1 档」只看所选权益人内评（自选权益人优先，复用 innerGuarantorRating）。担保债（覆盖永续等），或证券/主体已在观察池：不得高于矩阵最好档，从该档开到五级。普通债只保留矩阵单元格
     * 已在重点观察名单：未在分级库不得新增（开关开启时强担保豁免），已在 1～4 级只留五级。含权只改期限。releaseRules 跳过矩阵
     *
     * 执行顺序：
     * 1. releaseRules=true 或无证券代码：不卡矩阵
     * 2. 按证券类型解析本次评级主体：非 ABS 使用担保人，ABS 使用权益人或自选权益人
     * 3. 可转债 / 可交换债 / CRMW 不适用信用债 1～5，选池直接去掉
     * 4. 证券自身或发行主体任一在重点观察名单，即走重点观察名单禁新增规则
     * 5. ABS 按所选权益人内评（自选权益人优先）选口径；私募/永续/次级按发债主体内评；担保债或已在观察池从最好档开到五级；普通债只留矩阵点名的池
     * 6. 保留叶子：非信用债大库叶子，或信用债大库叶子在矩阵允许列表；父级按子级递归保留（避免丢失父级树结构）
     * 7. 向上传播：父级若至少一个子级保留则保留（含多层父级）
     *
     * @param pools 待过滤的投资池列表
     * @param req 选池请求
     * @return 过滤后的池列表
     */
    private List<InvestmentPoolBo> filterInboundByGradeRule(List<InvestmentPoolBo> pools, SecurityPoolAdjustReq req) {
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
                ? req.getSecurityInfo() : securityPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            return pools;
        }
        // 2. 按证券类型解析本次评级主体：非 ABS 使用担保人，ABS 使用权益人或自选权益人
        applySelectedRatingCompanyInternal(securityInfo, req.getGuarantorCode(), req.getRightsHolderCode(),
                req.getSelfSelectedRightsHolderCode(), false, req.getSelectedRatingCompany());
        // 3. 可转债 / 可交换债 / CRMW 不适用信用债 1～5，选池直接去掉
        if (CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(securityInfo)) {
            // 去掉信用债大库整棵树
            return excludeGradedBondPools(pools);
        }
        // 4. 证券自身或发行主体任一在重点观察名单，即走重点观察名单禁新增规则
        boolean inRestricted = securityPoolAdjustMapper.querySecurityInRestrictedPool(req.getSecurityCode())
                || queryIssuerInPoolType(securityInfo, "restricted");
        // 当前已在池，用于重点观察名单判断是否「新增入库」
        Set<Long> currentPoolIds = new HashSet<Long>();
        List<Long> currentIdList = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode());
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
        // 5. ABS 按所选权益人内评（自选权益人优先）选口径；私募/永续/次级按发债主体内评；担保债或已在观察池从最好档开到五级；普通债只留矩阵点名的池
        boolean inObserve = securityPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode())
                || queryIssuerInPoolType(securityInfo, "observe");
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
     * 从池列表中排除全部信用债大库池（pool_type=credit_bond，含根节点与 1～5 级）
     *
     * 执行顺序：
     * 1. 投资池列表为空时原样返回，逐条过滤信用债大库类型的池
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
     * 保留投资品种包含指定大类的池，并补齐这些池的祖先节点
     *
     * 执行顺序：
     * 1. variety_codes 是 JSON 数组，带引号匹配，避免 bond 命中其他编码的子串
     * 2. 父级自己可以不含该品种，只要下级命中就留下，选池树才还能展开
     * 3. 按原列表顺序输出，保持投资池既有排序
     *
     * @param pools 待过滤的投资池
     * @param varietyCode 投资品种编码，如 bond
     * @return 命中品种的池及其祖先
     */
    private List<InvestmentPoolBo> retainPoolsContainingVariety(List<InvestmentPoolBo> pools, String varietyCode) {
        if (pools == null || pools.isEmpty() || varietyCode == null || varietyCode.trim().isEmpty()) {
            return pools == null ? new ArrayList<InvestmentPoolBo>() : pools;
        }
        // 1. variety_codes 是 JSON 数组，带引号匹配，避免 bond 命中其他编码的子串
        String token = "\"" + varietyCode.trim() + "\"";
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<Long, InvestmentPoolBo>();
        Set<Long> matchedIds = new HashSet<Long>();
        for (InvestmentPoolBo pool : pools) {
            if (pool == null || pool.getId() == null) {
                continue;
            }
            poolMap.put(pool.getId(), pool);
            if (pool.getVarietyCodes() != null && pool.getVarietyCodes().contains(token)) {
                matchedIds.add(pool.getId());
            }
        }
        // 2. 父级自己可以不含该品种，只要下级命中就留下，选池树才还能展开
        Set<Long> retainedIds = new HashSet<Long>();
        for (Long poolId : matchedIds) {
            InvestmentPoolBo current = poolMap.get(poolId);
            while (current != null && retainedIds.add(current.getId())) {
                current = poolMap.get(current.getParentId());
            }
        }
        // 3. 按原列表顺序输出，保持投资池既有排序
        List<InvestmentPoolBo> result = new ArrayList<InvestmentPoolBo>();
        for (InvestmentPoolBo pool : pools) {
            if (pool != null && retainedIds.contains(pool.getId())) {
                result.add(pool);
            }
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
    public SecurityPoolStatusDto querySecurityPoolStatus(SecurityPoolAdjustReq req) {
        // 1. 校验证券代码并分别查询证券与发行主体的在池记录
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityPoolStatusDto dto = new SecurityPoolStatusDto();
        List<PoolStatusDto> securityCurrentPools = securityPoolAdjustMapper.querySecurityPoolStatusList(req.getSecurityCode());
        List<PoolStatusDto> issuerCurrentPools = securityPoolAdjustMapper.queryIssuerPoolStatusList(req.getSecurityCode());
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
     * 提交调库申请（五阶段流程）
     *
     * 分五个阶段顺序执行：
     *
     * 执行顺序：
     * 1. 前置校验：校验请求入参合法性，不通过直接抛异常
     * 2. 参数初始化：集中执行所有 DB 查询，构建 {@link SubmitSharedData}，包含证券信息、投资池索引、池关系映射、各流程的快照数据
     * 3. 调入处理：遍历全部调入方向的调库项，逐项判断是否直通流程，直通则写入 ip_adjust_log（audit_status='20'），待全部方向处理完后统一复核并写入 ip_pool_status；非直通则写入 ip_adjust_log（audit_status='00'），若初始流程步骤懒创建时即走到结束节点，则升级为 audit_status='20' 并写入 ip_pool_status
     * 4. 调出处理：遍历全部调出方向的调库项，直通则写入 ip_adjust_log（audit_status='20'），待统一复核后软删除 ip_pool_status；非直通则写入 ip_adjust_log（audit_status='00'），若初始流程步骤懒创建时即走到结束节点，则升级为 audit_status='20' 并软删除 ip_pool_status
     * 5. 后续处理：合并证券信息后按 log 写入 ip_adjust_security_snapshot（不修改 rrs_securityinfo）
     *
     * @param req 调库申请，包含证券信息及一个或多个调库项（手工项须携带 flowId 或 flowKey，无流程时走直通）
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 提交调库申请及附件（单券入口：本方法内创建附件上下文与新的批次号上下文）
     *
     * 执行顺序：
     * 1. 把申请和本次上传附件交给提交入口，前端原始文件名列表留空
     *
     * @param req 调库提交请求
     * @param files 本次提交的本地上传附件
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req, List<MultipartFile> files) {
        return addAdjustLog(req, files, null);
    }

    /**
     * 提交调库申请及附件（可带前端原始文件名 JSON 数组，兼容公司环境中文名乱码）
     *
     * 执行顺序：
     * 1. 解析前端 FormData 字段 {@code originalFileNameListJson}（JSON 数组字符串）
     * 2. 创建本次提交附件上下文
     * 3. 提交调库申请核心实现
     *
     * @param req 调库提交请求
     * @param files 本次提交的本地上传附件
     * @param originalFileNameListJson 上传文件原始名称列表的 JSON
     * @return 提交结果，包含生成的记录 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req, List<MultipartFile> files,
                                        String originalFileNameListJson) {
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        // 创建本次提交附件上下文
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId(), originalFileNameList);
        return submitAdjustLog(req, submissionFiles, new BatchNoContext());
    }

    /**
     * 判断指定流程是否为直通（无人工审批节点）
     *
     * 说明：单券提交路径内部直接使用 {@link #isDirectFlow(FlowSnapshot)}，
     * 本方法不在本类其它业务步骤中调用；主要供「证券池批量调整」在整批提交前做直通预检
     * （{@code needsWholeBatchDirectRecheck}），复用与单券一致的直通判定口径，避免批量侧再拷贝流程快照逻辑
     *
     * 判定规则：flowId 与 flowKey 均为空时按「无审批直通」处理；仅有 key 时解析活跃流程定义再判断；
     * 解析不到定义时返回 false（按非直通走后续提交校验）
     *
     * 执行顺序：
     * 1. 由共用审批规则根据流程 ID 或 Key 定位流程，判断能否直接生效
     *
     * @param flowId 流程定义 ID，可为 null
     * @param flowKey 流程 Key，可为 null/空
     * @return true=无需人工审批即可完成，包括未配置流程，false=未找到可直接完成的流程
     */
    public boolean isDirectAdjustFlow(Long flowId, String flowKey) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isDirectAdjustFlow(flowId, flowKey);
    }

    /**
     * 提交调库申请核心实现
     *
     * 与 {@link #addAdjustLog(SecurityPoolAdjustSubmitReq, List)} 的区别：
     * 调用方自行提供 {@link SysAttachmentService.SubmissionFiles} 与 {@link BatchNoContext}，
     * 供证券池批量调整按证券循环提交时整批共享附件与批次号序号
     *
     * 执行顺序：
     * 1. 完成前置校验并读取本次提交的证券、主体及流程数据
     * 2. 复用调入、调出、直通落池及快照保存步骤
     *
     * @param req 单证券调库提交请求
     * @param submissionFiles 已创建的附件上下文（可为整批共享）
     * @param batchNoContext 批次号上下文（可为整批共享）
     * @return 提交结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto submitAdjustLog(SecurityPoolAdjustSubmitReq req,
                                           SysAttachmentService.SubmissionFiles submissionFiles,
                                           BatchNoContext batchNoContext) {
        // 1. 完成前置校验并读取本次提交的证券、主体及流程数据
        SubmitSharedData shared = prepareSubmitSharedData(req, batchNoContext);
        // 2. 复用调入、调出、直通落池及快照保存步骤
        return submitPreparedAdjustLog(req, shared, submissionFiles);
    }

    /**
     * 批量页面按主券分组提交，共用池配置、证券主档及流程快照
     *
     * 执行顺序：
     * 1. 正式提交使用数据库中的评级候选和本次请求选择的主体代码
     * 2. 配套项可能是 ABS，黑名单质押库也复用本次查到的候选
     *
     * @param requests 按触发主券分组的提交请求
     * @param submissionFiles 本批附件上下文
     * @param directRecheck 有直通项时批量页面原有的整批预检明细
     * @return 各组提交结果，请求列表为空时返回空列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<AdjustSubmitDto> submitAdjustLogBatch(List<SecurityPoolAdjustSubmitReq> requests,
                                                    SysAttachmentService.SubmissionFiles submissionFiles,
                                                     List<IpAdjustLogBo> directRecheck) {
        if (requests.isEmpty()) {
            return Collections.emptyList();
        }
        // 读取批量页面和 Excel 的公共提交数据
        BatchSubmitData batch = loadBatchSubmitData(requests);
        // 1. 正式提交使用数据库中的评级候选和本次请求选择的主体代码
        Set<String> codes = requests.stream().map(SecurityPoolAdjustSubmitReq::getSecurityCode)
                .filter(Objects::nonNull).map(batch.data::resolveExcelSecurityCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 2. 配套项可能是 ABS，黑名单质押库也复用本次查到的候选
        batch.data.securities.values().stream().filter(CreditBondSpecialInboundRule::isAbs)
                .map(SecurityInfoBo::getWindCode).forEach(codes::add);
        List<String> selfSelected = requests.stream().map(SecurityPoolAdjustSubmitReq::getSelfSelectedRightsHolderCode)
                // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
                .map(this::emptyToNull).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        batchSecurityCheckService.loadRatingCandidates(batch.data, new ArrayList<>(codes), selfSelected);
        if (!directRecheck.isEmpty()) {
            // 能独立入池的普通债共用本批数据库状态
            loadIndependentInboundData(requests, batch, false);
            // 保留批量页面原有的整批预检
            recheckBeforeFinalApproval(directRecheck, batch, false);
        }
        // 按原顺序校验并保存各提交组
        return submitBatchRequests(requests, submissionFiles, batch);
    }

    /**
     * 按原分组提交 Excel 证券清空项，评级候选来自本次批量查询
     *
     * 执行顺序：
     * 1. 准备本次清空组的提交数据，带入已读取的关联主体和自选候选
     * 2. 建立本批无上传文件的附件参数，按原分组提交并返回日志结果
     *
     * @param requests 清空出库的证券请求
     * @param ratingData Excel 本次读取的评级候选
     * @return 各组提交结果
     */
    @Transactional(rollbackFor = Exception.class)
    public List<AdjustSubmitDto> addExcelClearAdjustLogList(List<SecurityPoolAdjustSubmitReq> requests,
                                                            BatchSecurityCheckData ratingData) {
        // 1. 复用本批已加载的主档，补查配套项缺少的数据
        BatchSubmitData batch = loadBatchSubmitData(requests, ratingData);
        batch.data.ratingCompanies.putAll(ratingData.ratingCompanies);
        batch.data.selfSelectedCompanies.putAll(ratingData.selfSelectedCompanies);
        // 2. 准备附件上下文并沿用清空项的批量提交步骤
        SysAttachmentService.SubmissionFiles files = sysAttachmentService.createSubmissionFiles(
                Collections.emptyList(), requests.get(0).getAdjusterId(), Collections.emptyList());
        // 复用流程和主档数据，保留清空项原有的提交步骤
        return submitBatchRequests(requests, files, batch);
    }

    /**
     * 准备 Excel 批量提交，保留每行独立的主体选择和原提交规则
     *
     * 执行顺序：
     * 1. 复用本次 Excel 清空已读取的主档，只查询配套项缺少的代码
     * 2. 按原规则校验一组提交，批量入口复用本批已读取的数据
     *
     * @param requests 按来源行或清空主券分组的请求
     * @param ratingData 本次提交已读的主档及评级候选
     * @param clear 是否为清空出库
     * @return 本批已校验的提交组
     */
    List<ExcelSubmitGroup> prepareExcelSubmitGroups(List<SecurityPoolAdjustSubmitReq> requests,
                                                    BatchSecurityCheckData ratingData, boolean clear) {
        // 复用本次 Excel 清空已读取的主档，只查询配套项缺少的代码
        BatchSubmitData batch = loadBatchSubmitData(requests, ratingData);
        batch.excelBulk = true;
        batch.data.ratingCompanies.putAll(ratingData.ratingCompanies);
        batch.data.selfSelectedCompanies.putAll(ratingData.selfSelectedCompanies);
        BatchNoContext batchNo = new BatchNoContext();
        Map<String, String> absIdentities = new HashMap<>();
        List<ExcelSubmitGroup> groups = new ArrayList<>();
        for (SecurityPoolAdjustSubmitReq req : requests) {
            // 创建任何流程前先复核本行的证券、评级主体和关联码
            SubmitSharedData shared = prepareSubmitSharedData(req, batchNo, batch);
            if (!clear) {
                if (CreditBondSpecialInboundRule.isAbs(shared.securityInfo)) {
                    String previous = absIdentities.put(req.getSecurityCode(), shared.securityInfo.getIssuerCode());
                    if (previous != null && !previous.equals(shared.securityInfo.getIssuerCode())) {
                        throw new BizException("同一 ABS[" + req.getSecurityCode() + "]在同批申请中选择了不同主体");
                    }
                }
                req.setSecurityInfo(shared.securityInfo);
            }
            groups.add(new ExcelSubmitGroup(req, shared, batch));
        }
        return groups;
    }

    /**
     * 生成直通组的日志和步骤，待审批或需复核材料来源的组使用原提交流程
     *
     * 执行顺序：
     * 1. 逐项获取同组手工调库项，联动/互斥项按手工项共用流程和批次号
     * 2. 从提交阶段已加载的快照中取得流程 ID，避免按 Key 再查一遍
     * 3. 从本次完整调库组中识别 Excel 主项批次，让配套项沿用同一放行口径
     *
     * @param group 已通过前置检查的提交组
     */
    void planExcelDirectGroup(ExcelSubmitGroup group) {
        SecurityPoolAdjustSubmitReq req = group.request;
        SubmitSharedData shared = group.shared;
        // 1. 识别 Excel 导入及清空来源
        if (!isExcelAdjustType(req.getAdjustType()) || req.getItems().stream().anyMatch(item ->
                MaterialExemptionType.PRODUCT_SOURCE.getCode().equals(item.getMaterialExemptionType())
                        || !CollectionUtils.isEmpty(item.getCreditReportFileIndexes())
                        || !CollectionUtils.isEmpty(item.getMaterialFileIndexes())
                        || !CollectionUtils.isEmpty(item.getCreditReportSourceAttachmentIds())
                        || !CollectionUtils.isEmpty(item.getMaterialSourceAttachmentIds()))) {
            return;
        }
        // 2. 准备同组调入调出日志及审批步骤
        List<ExcelSubmitRow> rows = new ArrayList<>();
        boolean ordinary = true;
        for (AdjustMode mode : Arrays.asList(AdjustMode.IN, AdjustMode.OUT)) {
            for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                if (!mode.getCode().equals(item.getAdjustMode())) {
                    continue;
                }
                // 获取同组手工调库项，联动/互斥项按手工项共用流程和批次号
                SecurityPoolAdjustSubmitReq.AdjustItem manual = resolveManualSubmitItem(req, item);
                // 从提交阶段已加载的快照中取得流程 ID，避免按 Key 再查一遍
                Long flowId = resolveLoadedFlowId(manual, shared);
                FlowSnapshot flow = shared.flowSnapshotMap.get(flowId);
                // 判断流程是否为直通流程（开始后无需人工待办即可到结束节点）
                if (flowId != null && (flow == null || !isDirectFlow(flow))) {
                    return;
                }
                // 构建调库记录实体（ip_adjust_log），由提交申请逻辑调用
                IpAdjustLogBo log = buildAdjustLog(req, item, manual, shared);
                // 获取或生成同组调库批次号
                log.setAdjustBatchNo(resolveAdjustBatchNo(req, item, flowId == null, shared));
                log.setAuditStatus(AuditStatus.APPROVED.getCode());
                List<IpAdjustStepBo> steps = new ArrayList<>();
                // 判断提交项是否为手工调库项
                if (isManualSubmitItem(item) && flow != null
                        // 生成原流程初始步骤，直通批量提交先收集步骤
                        && !createInitialSteps(null, log.getAdjustBatchNo(), flow,
                                req.getAdjusterId(), req.getAdjusterName(), steps)) {
                    return;
                }
                String masterCode = group.batch.data.resolveExcelSecurityCode(log.getSecurityCode());
                SecurityInfoBo master = group.batch.data.securities.get(masterCode);
                if (master == null) {
                    // 抛出包含调库项上下文的最终审批复核异常
                    throwApprovalRecheckFailure(log, "证券不存在");
                }
                if (!shared.poolMap.containsKey(log.getTargetPoolId())) {
                    // 抛出包含调库项上下文的最终审批复核异常
                    throwApprovalRecheckFailure(log, "目标投资池不存在或已停用");
                }
                ordinary &= !CreditBondSpecialInboundRule.isAbs(master) && !"abs".equalsIgnoreCase(master.getSecurityType());
                rows.add(new ExcelSubmitRow(log, steps));
            }
        }
        List<IpAdjustLogBo> logs = rows.stream().map(row -> row.log).collect(Collectors.toList());
        // 3. 按 Excel 主项批次复核配套项，判断能否整组直通
        Set<String> excelBatches = collectExcelAdjustBatches(logs);
        for (IpAdjustLogBo log : logs) {
            // 识别证券 Excel 导入及清空来源，两者都仅提示业务限制
            if (!isExcelAdjustType(log.getAdjustType()) && !excelBatches.contains(log.getAdjustBatchNo())) {
                return;
            }
        }
        group.rows = rows;
        group.ordinary = ordinary;
    }

    /**
     * 用已回填日志主键生成证券和 ABS 申请快照
     *
     * 执行顺序：
     * 1. 快照与原保存流程共用主档合并及评级字段
     *
     * @param group 本组直通日志和初始步骤
     * @param snapshots 本次待批量写入的快照
     */
    void collectExcelSubmitSnapshots(ExcelSubmitGroup group, List<AdjustSecuritySnapshotBo> snapshots) {
        for (ExcelSubmitRow row : group.rows) {
            group.shared.submittedLogs.put(row.log.getId(), row.log);
        }
        // 快照与原保存流程共用主档合并及评级字段
        postSubmitProcess(group.request, group.shared, snapshots);
    }

    /**
     * 在池状态变更前复核本组 ABS 快照并生效主体身份
     *
     * 执行顺序：
     * 1. 先复核整组待生效的调库日志
     * 2. 复核通过后，按日志生效所选 ABS 主体身份
     *
     * @param group 已保存申请快照的直通组
     * @param logs 本组调库日志
     */
    void approveExcelAbsGroup(ExcelSubmitGroup group, List<IpAdjustLogBo> logs) {
        // 从现有规则执行最终复核，独立调入复用本批数据库状态
        recheckBeforeFinalApproval(logs, group.batch, false);
        absIdentityService.approveIdentities(logs);
    }

    /**
     * 批量落池后按原业务记录 ABS 人工结论
     *
     * 执行顺序：
     * 1. 将已完成落池的日志交给 ABS 联动服务保存人工决定
     *
     * @param logs 刚生效的直通日志
     */
    void recordExcelAbsDecisions(List<IpAdjustLogBo> logs) {
        absCompanySyncService.recordManualDecisions(logs);
    }

    /**
     * 使用原路径提交待审批或需独立处理材料的组
     *
     * 执行顺序：
     * 1. 为当前组准备不含新上传文件的附件参数
     * 2. 复用原单组提交链路继续审批或单独处理材料
     *
     * @param group 已完成前置校验的组
     * @return 本组提交结果
     */
    AdjustSubmitDto submitExcelGroupIndividually(ExcelSubmitGroup group) {
        SysAttachmentService.SubmissionFiles files = sysAttachmentService.createSubmissionFiles(
                Collections.emptyList(), group.request.getAdjusterId(), Collections.emptyList());
        // 单组保留已有审批及材料处理步骤
        return submitPreparedAdjustLog(group.request, group.shared, files, group.batch);
    }

    /**
     * 按原顺序校验并保存各提交组
     *
     * 执行顺序：
     * 1. 复用本批已读取的数据，复核本组证券、评级主体和调库明细
     * 2. 用本次复核后的证券和担保人资料作为担保债的快照来源
     * 3. 提交本组调库，按需保存快照并处理直通落池
     *
     * @param requests 本批调库请求
     * @param submissionFiles 已创建的附件上下文（可为整批共享）
     * @param batch 本批提交数据
     * @return 调库提交结果列表
     */
    private List<AdjustSubmitDto> submitBatchRequests(List<SecurityPoolAdjustSubmitReq> requests,
                                                      SysAttachmentService.SubmissionFiles submissionFiles,
                                                      BatchSubmitData batch) {
        List<AdjustSubmitDto> results = new ArrayList<>();
        BatchNoContext batchNoContext = new BatchNoContext();
        for (SecurityPoolAdjustSubmitReq req : requests) {
            // 1. 复核本组证券、评级主体和调库明细
            SubmitSharedData shared = prepareSubmitSharedData(req, batchNoContext, batch);
            // 2. 担保债快照使用本次复核的担保人和内评
            if (CreditBondSpecialInboundRule.isGuaranteed(shared.securityInfo)) {
                req.setSecurityInfo(shared.securityInfo);
            }
            // 3. 提交本组调库，按需保存快照并处理直通落池
            results.add(submitPreparedAdjustLog(req, shared, submissionFiles, batch));
        }
        return results;
    }

    /**
     * 按 Excel 导入行批量提交，各行独立使用所选评级主体并保存证券快照
     *
     * 在创建任何新流程前统一检查已有在途流程，避免同码多行相互阻断
     * 后续各行复用单券提交步骤，动态池状态及容量仍按原规则复核
     *
     * 执行顺序：
     * 1. 请求列表为空时返回空结果，否则创建整批共享的批次号上下文
     * 2. 在创建任何新流程前，逐行执行参数、重复提交、已有在途流程和配套互斥调出的前置检查
     * 3. 为各行保留独立的提交上下文，并将后端复核的证券和所选主体信息设置为本笔证券快照来源
     * 4. 创建无上传文件的共享附件上下文，再按请求顺序使用各行上下文执行调库提交
     * 5. 返回各行提交结果；非 ABS 各行独立选择，ABS 同码各行必须同一主体，任一行异常整体回滚
     *
     * @param requests 按来源导入行分组的提交请求
     * @return 每个导入行产生的提交结果
     */
    @Transactional(rollbackFor = Exception.class)
    public List<AdjustSubmitDto> addExcelImportAdjustLogList(List<SecurityPoolAdjustSubmitReq> requests) {
        // 1. 请求为空时返回空结果
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        // 2. 读取整批共用的主档、流程和在池状态
        BatchSubmitData batch = loadBatchSubmitData(requests);
        // Excel 的普通直通调入也复用本批读取的证券和在池状态
        loadIndependentInboundData(requests, batch, true);
        // 3. 逐行做前置检查并保存独立的快照来源
        BatchNoContext batchNoContext = new BatchNoContext();
        List<SubmitSharedData> sharedDataList = new ArrayList<>();
        Map<String, String> absIdentities = new HashMap<>();
        for (SecurityPoolAdjustSubmitReq req : requests) {
            // 写入新流程前检查本行既有在途流程、重复提交和配套互斥调出
            SubmitSharedData shared = prepareSubmitSharedData(req, batchNoContext, batch);
            if (CreditBondSpecialInboundRule.isAbs(shared.securityInfo)) {
                String previous = absIdentities.put(req.getSecurityCode(), shared.securityInfo.getIssuerCode());
                if (previous != null && !previous.equals(shared.securityInfo.getIssuerCode())) {
                    throw new BizException("同一 ABS[" + req.getSecurityCode() + "]在同批申请中选择了不同主体");
                }
            }
            sharedDataList.add(shared);
            // Excel 页面不编辑证券主档，本笔快照使用后端复核的证券及主体信息
            req.setSecurityInfo(shared.securityInfo);
        }
        // 4. 准备共用附件上下文并按行执行提交
        SysAttachmentService.SubmissionFiles submissionFiles = sysAttachmentService.createSubmissionFiles(
                Collections.<MultipartFile>emptyList(), requests.get(0).getAdjusterId(), Collections.<String>emptyList());
        List<AdjustSubmitDto> results = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            // 各行独立落调库记录和主体快照，行内联动、互斥、关联项仍共用流程
            results.add(submitPreparedAdjustLog(requests.get(index), sharedDataList.get(index), submissionFiles, batch));
        }
        return results;
    }

    /**
     * 完成提交前置校验，读取证券、主体和流程数据，供后续实际提交使用
     *
     * 执行顺序：
     * 1. 检查请求是否为空，并校验证券代码及调库明细的必填字段
     * 2. 检查短时间内是否存在相同调库申请，防止重复提交
     * 3. 加载证券主档并复核所选评级主体，同时读取投资池、当前池状态、池关系和所选流程快照
     * 4. 检查本次涉及的顶级池组是否已有活动流程
     * 5. 复核调入所需的互斥调出项是否完整且可执行；本阶段不创建新流程
     *
     * @param req 单个来源证券的调库提交请求，包含主体选择和调库明细
     * @param batchNoContext 本次提交使用的批次号上下文，可由整批共享
     * @return 经前置校验的证券、主体、池状态及流程数据
     */
    private SubmitSharedData prepareSubmitSharedData(SecurityPoolAdjustSubmitReq req, BatchNoContext batchNoContext) {
        // 按原规则校验一组提交，批量入口复用本批已读取的数据
        return prepareSubmitSharedData(req, batchNoContext, null);
    }

    /**
     * 读取批量页面和 Excel 的公共提交数据
     *
     * 执行顺序：
     * 1. 按本批申请读取并整理共同的提交数据，缺省的评级候选按本次提交需要加载
     *
     * @param requests 本批调库请求
     * @return 本次事务内使用的提交数据
     */
    private BatchSubmitData loadBatchSubmitData(List<SecurityPoolAdjustSubmitReq> requests) {
        // 复用本次 Excel 清空已读取的主档，只查询配套项缺少的代码
        return loadBatchSubmitData(requests, null);
    }

    /**
     * 复用本次 Excel 清空已读取的主档，只查询配套项缺少的代码
     *
     * 执行顺序：
     * 1. 逐项查询投资池列表
     * 2. 与单券使用相同的池关系口径
     * 3. 逐项解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
     * 4. 逐项查询证券
     *
     * @param requests 本批调库请求
     * @param ratingData Excel 本次读取的评级候选
     * @return 本次事务内使用的提交数据
     */
    private BatchSubmitData loadBatchSubmitData(List<SecurityPoolAdjustSubmitReq> requests,
                                                BatchSecurityCheckData ratingData) {
        BatchSubmitData batch = new BatchSubmitData();
        if (ratingData != null) {
            batch.data.securities.putAll(ratingData.securities);
            batch.data.excelSecurityCodes.putAll(ratingData.excelSecurityCodes);
        }
        for (InvestmentPoolBo pool : investmentPoolMapper.queryPoolList()) {
            batch.data.pools.put(pool.getId(), pool);
        }
        // 与单券使用相同的池关系口径
        batch.data.relations.putAll(buildPoolRelationMap(securityPoolAdjustMapper.queryAllPoolRelationList()));
        Set<String> requestedCodes = new LinkedHashSet<>();
        for (SecurityPoolAdjustSubmitReq request : requests) {
            requestedCodes.add(request.getSecurityCode());
            if (request.getItems() != null) {
                for (SecurityPoolAdjustSubmitReq.AdjustItem item : request.getItems()) {
                    // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
                    requestedCodes.add(resolveItemSecurityCode(request, item));
                }
            }
        }
        List<String> codes = requestedCodes.stream().filter(code -> code != null && !code.trim().isEmpty())
                .filter(code -> !batch.data.securities.containsKey(batch.data.resolveExcelSecurityCode(code)))
                .collect(Collectors.toList());
        int size = AutoAdjustRestrictHelper.QUERY_BATCH_SIZE;
        for (int offset = 0; offset < codes.size(); offset += size) {
            List<String> chunk = codes.subList(offset, Math.min(offset + size, codes.size()));
            for (SecurityInfoBo master : batchSecurityCheckMapper.querySecurities(chunk)) {
                batch.data.securities.put(master.getWindCode(), master);
                batch.data.excelSecurityCodes.put(master.getWindCode(), master.getWindCode());
            }
        }
        return batch;
    }

    /**
     * 独立调入的证券在本批只出现一次，各组互不改变其他证券的在池和在途状态
     *
     * 执行顺序：
     * 1. 逐项解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
     * 2. 从调库项的 flowId 或 flowKey 解析出流程定义 ID
     * 3. 本批共用一次状态读取，各组顺序处理并累计生效容量
     * 4. 逐项查询在途投资池
     *
     * @param requests 本批调库请求
     * @param batch 本批提交数据
     * @param excel 是否为 Excel 提示放行入口
     */
    private void loadIndependentInboundData(List<SecurityPoolAdjustSubmitReq> requests, BatchSubmitData batch,
                                            boolean excel) {
        Set<String> codes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<Long> poolIds = new TreeSet<>();
        boolean direct = !excel;
        for (SecurityPoolAdjustSubmitReq req : requests) {
            SecurityInfoBo master = batch.data.securities.get(req.getSecurityCode());
            if (master == null || CreditBondSpecialInboundRule.isAbs(master)
                    || "abs".equalsIgnoreCase(master.getSecurityType())
                    || req.getItems() == null || req.getItems().size() != 1) {
                return;
            }
            SecurityPoolAdjustSubmitReq.AdjustItem item = req.getItems().get(0);
            Long poolId = item.getTargetPoolId();
            // 判断提交项是否为手工调库项
            if (!AdjustMode.IN.getCode().equals(item.getAdjustMode()) || !isManualSubmitItem(item)
                    // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
                    || !req.getSecurityCode().equals(resolveItemSecurityCode(req, item))
                    || !codes.add(req.getSecurityCode()) || poolId == null
                    || (!excel && PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(poolId))) {
                return;
            }
            poolIds.add(poolId);
            if (!direct) {
                // 复用提交时实际采用的流程 ID 和快照判定
                Long flowId = resolveFlowIdFromItem(item, batch);
                // 按定义 ID 复用本次提交的流程快照
                FlowSnapshot snapshot = flowId == null ? null : loadBatchFlowSnapshot(batch, flowId);
                // 判断流程是否为直通流程（开始后无需人工待办即可到结束节点）
                direct = flowId == null || (snapshot != null && isDirectFlow(snapshot));
            }
        }
        if (!direct) {
            return;
        }
        // 本批共用一次状态读取，各组顺序处理并累计生效容量
        batch.independent = true;
        if (!excel) {
            // 普通批量落池需要复核容量和证券品种
            for (PoolDto count : batchSecurityCheckMapper.queryPoolCounts(new ArrayList<>(poolIds))) {
                batch.data.counts.put(count.getId(), count.getCurrentCount());
            }
            Set<String> ambiguousTypes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (BatchSecurityCheckData.Row row : batchSecurityCheckMapper.queryCategories()) {
                String type = row.getSecurityType();
                if (type != null && batch.categories.containsKey(type)) {
                    ambiguousTypes.add(type);
                } else if (type != null) {
                    batch.categories.put(type, row.getCategoryType());
                }
            }
            for (String type : ambiguousTypes) {
                // 字典重复时沿用原查单个品种的取值
                batch.categories.put(type, securityPoolAdjustMapper.queryCategoryTypeBySecurityType(type));
            }
        }
        Map<String, String> codeIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        codes.forEach(code -> codeIndex.put(code, code));
        List<String> securityCodes = new ArrayList<>(codes);
        int size = AutoAdjustRestrictHelper.QUERY_BATCH_SIZE;
        for (int offset = 0; offset < securityCodes.size(); offset += size) {
            List<String> chunk = securityCodes.subList(offset, Math.min(offset + size, securityCodes.size()));
            for (BatchSecurityCheckData.Row row : batchSecurityCheckMapper.queryActiveStates(chunk)) {
                String code = codeIndex.getOrDefault(row.getSecurityCode(), row.getSecurityCode());
                batch.data.activePools.computeIfAbsent(code, key -> new HashSet<>()).add(row.getPoolId());
            }
            if (excel) {
                // Excel 读取在池状态供产品库材料资格复核
                continue;
            }
            for (BatchSecurityCheckData.Row row : batchSecurityCheckMapper.queryPendingPools(chunk)) {
                batch.pendingPools.computeIfAbsent(row.getSecurityCode(), key -> new ArrayList<>()).add(row.getPoolId());
            }
        }
    }

    /**
     * 按原规则校验一组提交，批量入口复用本批已读取的数据
     *
     * 执行顺序：
     * 1. 校验证券代码和调库明细的必填字段
     * 2. 普通单券入口检查是否重复提交，批量入口复用批次业务状态
     * 3. 读取共用的证券、主体和池数据，ABS 逐一校验涉及的证券身份
     * 4. 检查同券跨市场调入项是否完整
     * 5. 普通入口检查在途流程，Excel 保留在途提示
     * 6. 普通入口复核必需的互斥调出，Excel 沿用导入校验口径
     *
     * @param req 调库提交请求
     * @param batchNoContext 批次号上下文（可为整批共享）
     * @param batch 本批提交数据
     * @return 经前置校验的证券、主体、池状态及流程数据
     */
    private SubmitSharedData prepareSubmitSharedData(SecurityPoolAdjustSubmitReq req, BatchNoContext batchNoContext,
                                                     BatchSubmitData batch) {
        if (req == null) {
            throw new BizException("调库请求不能为空");
        }

        // 1. 校验证券代码及调库明细必填字段
        validateSubmitReq(req);
        // 2. 单券仍按其入口检查历史申请，批量提交使用本批查出的业务状态
        if (batch == null && !isExcelAdjustType(req.getAdjustType())) {
            // 检查操作人短时间内是否已经提交内容相同的手工调库申请
            checkRecentDuplicateSubmit(req);
        }


        // 3. 复用页面证券信息和已选主体，准备本次共享数据
        SubmitSharedData shared = loadSubmitSharedData(req, batchNoContext, batch);
        if (CreditBondSpecialInboundRule.isAbs(shared.securityInfo)) {
            Set<String> identityCodes = new TreeSet<>();
            identityCodes.add(req.getSecurityCode());
            for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                if (item.getSecurityCode() != null) identityCodes.add(item.getSecurityCode());
            }
            for (String code : identityCodes) {
                absIdentityService.validateIdentity(code, shared.securityInfo.getIssuerCode());
            }
            if (req.getSecurityInfo() == null) {
                req.setSecurityInfo(shared.securityInfo);
            }
        }
        // 4. 核对每个目标池的同券调入清单，普通调库只提交仍可调入的代码
        validateRelatedInboundItemsOnSubmit(req, shared.securityInfo, batch);
        // 5. 普通入口检查在途，Excel 在原校验中保留在途提示后放行
        if (!isExcelAdjustType(req.getAdjustType())) {
            // 检查正式提交涉及的池组是否已有活动流程（按每条调库项的证券代码独立判断）
            checkSubmitPendingPoolGroups(req, shared.poolMap, batch);
        }
        // 6. 普通入口要求互斥调出可执行，Excel 沿用导入选项及原校验提示
        if (!isExcelAdjustType(req.getAdjustType())) {
            // 提交时校验：调入目标池若要求配套互斥调出，请求中必须包含对应调出项且调出校验通过
            validateRequiredMutexOutboundOnSubmit(req, shared, batch);
        }
        return shared;
    }

    /**
     * 使用已完成前置校验的数据执行提交，并保存本次调库证券快照
     *
     * 执行顺序：
     * 1. 提交调入项，创建对应调库记录和流程，并收集需要立即生效的直通项
     * 2. 提交调出项，按原有规则关联同组流程和批次号，并继续收集直通项
     * 3. 合并调入和调出的调库日志 ID，先保存本次主体快照；ABS 始终保留快照
     * 4. 对直通项锁定复核动态池状态及容量，审核通过后保存 ABS 主档五字段并落池
     * 5. 组装证券代码、提交记录数及日志 ID 列表，返回本次提交结果
     *
     * @param req 单个来源证券的调库提交请求
     * @param shared 已完成前置检查的独立提交上下文
     * @param submissionFiles 附件上下文，可由整批共享
     * @return 本次提交生成的调库记录数及日志 ID
     */
    private AdjustSubmitDto submitPreparedAdjustLog(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                                    SysAttachmentService.SubmissionFiles submissionFiles) {
        // 保存本组提交，独立调入共用本批落池数据
        return submitPreparedAdjustLog(req, shared, submissionFiles, null);
    }

    /**
     * 保存本组提交，独立调入共用本批落池数据
     *
     * 执行顺序：
     * 1. 提交调入项并收集需要直通的记录
     * 2. 提交调出项，沿用同组流程和批次号
     * 3. 合并调入和调出产生的调库日志 ID
     * 4. 保存本次提交使用的证券信息和评级主体快照
     * 5. 复核并应用直通项，主档五字段在审核通过的落池事务内更新
     * 6. 返回证券代码、提交数量和调库日志 ID
     *
     * @param req 调库提交请求
     * @param shared 已完成前置检查的独立提交上下文
     * @param submissionFiles 已创建的附件上下文（可为整批共享）
     * @param batch 本批提交数据
     * @return 本次提交生成的调库记录数及日志 ID
     */
    private AdjustSubmitDto submitPreparedAdjustLog(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                                    SysAttachmentService.SubmissionFiles submissionFiles,
                                                     BatchSubmitData batch) {

        List<IpAdjustLogBo> directApplyLogs = new ArrayList<>();
        // 1. 提交调入项并收集需要直通的记录
        List<Long> inboundIds = executeInboundSubmit(req, shared, submissionFiles, directApplyLogs);


        // 2. 提交调出项，沿用同组流程和批次号
        List<Long> outboundIds = executeOutboundSubmit(req, shared, submissionFiles, directApplyLogs);

        // 3. 合并调入和调出产生的调库日志 ID
        List<Long> allIds = new ArrayList<>(inboundIds);
        allIds.addAll(outboundIds);


        // 4. 保存本次提交使用的证券信息和评级主体快照
        postSubmitProcess(req, shared);
        // 5. 复核并应用直通项，主档五字段在审核通过的落池事务内更新
        recheckAndApplyDirectLogs(directApplyLogs, req, batch);

        // 6. 返回证券代码、提交数量和调库日志 ID
        AdjustSubmitDto dto = new AdjustSubmitDto();
        dto.setSecurityCode(req.getSecurityCode());
        dto.setSubmitCount(allIds.size());
        dto.setLogIds(allIds);
        return dto;
    }

    /**
     * 校验财务报告日期为 yyyyMMdd 且属于四个标准报告期
     *
     * 执行顺序：
     * 1. 检查财务报告日期不能为空
     * 2. 财务报告日期仅支持一季报、半年报、三季报或年报时终止处理
     *
     * @param reportDate 报告日期
     */
    private void validateFinancialReportDate(Long reportDate) {
        if (reportDate == null) {
            throw new BizException("财务报告日期不能为空");
        }
        String value = String.valueOf(reportDate);
        if (value.length() != 8 || !value.matches("[1-9][0-9]{7}")) {
            throw new BizException("财务报告日期格式不正确");
        }
        String suffix = value.substring(4);
        if (!Arrays.asList("0331", "0630", "0930", "1231").contains(suffix)) {
            throw new BizException("财务报告日期仅支持一季报、半年报、三季报或年报");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  调库提交 — 五阶段实现
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验证券池调库申请的必填项
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
     * 防止前端只提交可调入项、丢弃失败的互斥调出项，造成双池
     *
     * 执行顺序：
     * 1. 先收集本次请求里的调出池 ID
     * 2. 逐个检查调入项所需的互斥调出是否已提交，并复核相应调出是否合法
     *
     * @param req 调库提交请求
     * @param shared 已完成前置检查的独立提交上下文
     * @param batch 本批提交数据
     */
    private void validateRequiredMutexOutboundOnSubmit(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                                      BatchSubmitData batch) {
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item != null && AdjustMode.OUT.getCode().equals(item.getAdjustMode())
                    && item.getTargetPoolId() != null) {
                requestOutPoolIds.add(item.getTargetPoolId());
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
                checkShared.setRequestInPoolIds(Collections.<Long>emptySet());
                checkShared.setRequestOutPoolIds(requestOutPoolIds);
                int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(mutexId);
                // 构建单个调库项的校验上下文（{@link AdjustCheckContext}）
                AdjustCheckContext ctx = buildCheckContext(fakeOut, poolCurrentCount, checkShared);
                List<RelatedRatingCompanyDto> candidates = batch == null ? null
                        : batch.data.ratingCompanies.getOrDefault(shared.securityInfo.getWindCode(), Collections.emptyList());
                // 获取规则组件，直接构造服务时沿用显式注入的依赖
                List<String> outFailures = checkRules().checkOutConditions(ctx, candidates);
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
    private void checkRecentDuplicateSubmit(SecurityPoolAdjustSubmitReq req) {
        List<SecurityPoolAdjustSubmitReq.AdjustItem> manualItems = req.getItems().stream()
                // 判断提交项是否为手工调库项
                .filter(this::isManualSubmitItem)
                .collect(Collectors.toList());
        if (manualItems.isEmpty()) {
            return;
        }
        List<IpAdjustLogBo> recentLogs = securityPoolAdjustMapper.queryRecentManualAdjustLogList(
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
     * 1. 按每条调库项的实际证券代码读取并缓存已存在的手工流程池组
     * 2. 复核本次目标池所属组存在冲突时阻断提交
     *
     * @param req 调库提交请求
     * @param poolMap 已加载的投资池集合
     * @param batch 本批提交数据
     */
    private void checkSubmitPendingPoolGroups(SecurityPoolAdjustSubmitReq req,
                                              Map<Long, InvestmentPoolBo> poolMap, BatchSubmitData batch) {
        // 按证券代码缓存 pending 池组，避免同码多行重复查库
        Map<String, Set<Long>> pendingGroupsBySecurity = new HashMap<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
            String securityCode = resolveItemSecurityCode(req, item);
            Set<Long> pendingGroups = pendingGroupsBySecurity.get(securityCode);
            if (pendingGroups == null) {
                List<Long> pendingPools = batch != null && batch.independent
                        ? batch.pendingPools.getOrDefault(securityCode, Collections.emptyList())
                        : securityPoolAdjustMapper.queryPendingManualTargetPoolIdList(securityCode, null);
                // 池归组沿用单券口径
                pendingGroups = resolvePoolGroupIds(pendingPools, poolMap);
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
     * 核对同券各市场代码在同一目标池的调入清单
     *
     * 执行顺序：
     * 1. 按目标池汇总实际调入代码，明细未传代码时使用请求中的主券代码
     * 2. 普通单笔债券调入时重新查询同券代码，批量和 Excel 使用校验后的清单
     * 3. 核对各池的代码是否齐全，普通调库还复核实际调入代码的退市和到期状态
     *
     * @param req 调库提交请求
     * @param main 主券证券主档
     * @param batch 批量提交已加载的数据，未使用批量数据时为 null
     */
    private void validateRelatedInboundItemsOnSubmit(SecurityPoolAdjustSubmitReq req, SecurityInfoBo main,
                                                        BatchSubmitData batch) {
        Map<Long, Set<String>> inboundCodes = new LinkedHashMap<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (AdjustMode.IN.getCode().equals(item.getAdjustMode())) {
                // 使用明细实际代码，兼容未传明细代码的单券请求
                inboundCodes.computeIfAbsent(item.getTargetPoolId(), key -> new HashSet<>())
                        // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
                        .add(resolveItemSecurityCode(req, item));
            }
        }
        List<SecurityMarketDto> relatedCodes = req.getRelatedMarketCodes();
        boolean excelImport = isExcelAdjustType(req.getAdjustType());
        if (!inboundCodes.isEmpty() && batch == null && !excelImport
                && CategoryType.BOND.getCode().equals(securityPoolAdjustMapper.queryCategoryTypeBySecurityType(main.getSecurityType()))) {
            // 单笔债券从服务端重新确认同券范围，批量和 Excel 沿用校验后的清单
            relatedCodes = securityMarketService.queryRelatedMarketCodes(main.getWindCode());
        }
        // 确认同组调入代码齐全，普通申请另按当前主档复核到期
        RelatedMarketAdjustHelper.validateInboundGroup(req.getSecurityCode(), relatedCodes, inboundCodes, excelImport,
                code -> {
                    if (code.equals(main.getWindCode())) {
                        return main;
                    }
                    SecurityInfoBo security = batch == null ? null : batch.data.securities.get(code);
                    return security == null ? securityPoolAdjustMapper.querySecurityBoByCode(code) : security;
                },
                securityPoolAdjustMapper::queryCategoryTypeBySecurityType);
    }

    /**
     * 将目标池列表转换为顶级投资池组 ID 集合
     *
     * 执行顺序：
     * 1. 从各目标池向上查询所属顶级池，收集不同的池组 ID
     *
     * @param poolIds 待处理的投资池 ID 列表
     * @param poolMap 已加载的投资池集合
     * @return 非空根池 ID 集合
     */
    private Set<Long> resolvePoolGroupIds(List<Long> poolIds, Map<Long, InvestmentPoolBo> poolMap) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolvePoolGroupIds(poolIds, poolMap);
    }

    /**
     * 沿父级关系查找目标池所属的顶级投资池
     *
     * 执行顺序：
     * 1. 将目标池 ID 和池索引交给共用上溯规则，查出该池所属的顶级投资池
     *
     * @param poolId 目标投资池 ID
     * @param poolMap 已加载的投资池集合
     * @return 停止时的池 ID
     */
    private Long resolveRootPoolId(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        return SecurityPoolCheckRules.resolveRootPoolId(poolId, poolMap);
    }

    /**
     * 将空字符串归一化为空值，避免重复提交比较受空串表现影响
     *
     * 执行顺序：
     * 1. 将 null 或空白字符串作为同一种空值，其余文本保留原内容
     *
     * @param value 待统一空值的文本
     * @return 原文本，空字符串转换为 null
     */
    private String emptyToNull(String value) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().emptyToNull(value);
    }

    /**
     * 为证券池调库准备提交数据和批次号上下文
     *
     * 集中执行本次提交所需的全部 DB 查询，构建 {@link SubmitSharedData}
     * 包括证券基础信息、投资池索引、池关系映射、以及每个唯一流程的快照（定义+版本+节点+连线）
     * 调入和调出校验共用这些数据
     *
     * 执行顺序：
     * 1. 为本次请求新建批次号上下文，再读取提交需要的证券、池和流程数据
     *
     * @param req 调库提交请求
     * @return 封装了本次提交全量共享数据的 SubmitSharedData
     */
    private SubmitSharedData loadSubmitSharedData(SecurityPoolAdjustSubmitReq req) {
        // 创建本次提交独立批次号上下文
        return loadSubmitSharedData(req, new BatchNoContext(), null);
    }

    /**
     * 使用指定批次号上下文加载证券池调库提交数据
     *
     * 执行顺序：
     * 1. 优先使用页面提供的快照来源，再使用本次批量读取的普通债主档
     * 2. 提交时重新校验并读取最终评级主体，ABS 必须选择权益人或自选权益人
     * 3. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
     * 4. 独立调入的证券代码在批内只出现一次，共用本次读取的状态
     * 5. 加载池关系映射
     * 6. 收集所有流程ID
     * 7. 为每个唯一流程加载快照（定义 + 活跃版本 + 节点 + 连线）
     *
     * @param req 调库提交请求
     * @param batchNoContext 批次号上下文（可为整批共享）
     * @param batch 本批提交数据
     * @return 封装了本次提交全量共享数据的 SubmitSharedData
     */
    private SubmitSharedData loadSubmitSharedData(SecurityPoolAdjustSubmitReq req, BatchNoContext batchNoContext,
                                                   BatchSubmitData batch) {
        // 1. 优先使用页面提供的快照来源，再使用本次批量读取的普通债主档
        SecurityInfoBo securityInfo = req.getSecurityInfo();
        if (securityInfo == null || securityInfo.getSecurityType() == null) {
            SecurityInfoBo master = batch == null ? null : batch.data.securities.get(req.getSecurityCode());
            if (master == null || CreditBondSpecialInboundRule.isAbs(master)) {
                securityInfo = securityPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
            } else {
                securityInfo = new SecurityInfoBo();
                BeanUtils.copyProperties(master, securityInfo);
            }
        }
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }
        // 2. 提交时重新校验并读取最终评级主体，ABS 必须选择权益人或自选权益人
        SelectedRatingCompanyData selectedRatingCompany = applySelectedRatingCompanyInternal(
                securityInfo, req.getGuarantorCode(), req.getRightsHolderCode(), req.getSelfSelectedRightsHolderCode(),
                // 识别证券 Excel 导入及清空来源，两者都仅提示业务限制
                true, batch == null || isExcelAdjustType(req.getAdjustType()) ? req.getSelectedRatingCompany() : null,
                batch == null ? null : batch.data);

        // 3. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = batch == null ? investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool)) : batch.data.pools;

        // 4. 独立调入的证券代码在批内只出现一次，共用本次读取的状态
        Set<Long> currentPoolIds;
        if (batch != null && AdjustType.CLEAR_POOL.getCode().equals(req.getAdjustType())) {
            // 清空出库走 Excel 提示口径，提交阶段不使用在池列表复核业务条件
            currentPoolIds = Collections.emptySet();
        } else if (batch != null && batch.excelBulk && req.getItems().stream().noneMatch(item ->
                MaterialExemptionType.PRODUCT_SOURCE.getCode().equals(item.getMaterialExemptionType()))) {
            // Excel 的直通复核只检查存在性，只有产品快速资格仍使用当前在池列表
            currentPoolIds = Collections.emptySet();
        } else if (batch != null && batch.independent) {
            currentPoolIds = batch.data.activePools.getOrDefault(req.getSecurityCode(), Collections.emptySet());
        } else {
            currentPoolIds = new HashSet<>(securityPoolAdjustMapper.querySecurityCurrentPoolIdList(req.getSecurityCode()));
        }

        // 5. 加载池关系映射
        Map<Long, Map<String, List<Long>>> poolRelationMap = batch == null
                // 构建全量投资池关系 Map
                ? buildPoolRelationMap(securityPoolAdjustMapper.queryAllPoolRelationList()) : batch.data.relations;

        // 6. 收集所有流程ID
        Set<Long> uniqueFlowIds = new HashSet<>();
        Map<String, Long> resolvedFlowIdsByKey = new HashMap<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 只有 Key 的同一流程只解析一次，解析结果与快照是否存在分别保留
            Long resolvedId = item.getFlowId() == null && resolvedFlowIdsByKey.containsKey(item.getFlowKey())
                    // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
                    ? resolvedFlowIdsByKey.get(item.getFlowKey()) : resolveFlowIdFromItem(item, batch);
            // 将空字符串归一化为空值，避免重复提交比较受空串表现影响
            if (item.getFlowId() == null && emptyToNull(item.getFlowKey()) != null) {
                resolvedFlowIdsByKey.put(item.getFlowKey(), resolvedId);
            }
            if (resolvedId != null) {
                uniqueFlowIds.add(resolvedId);
            }
        }

        // 7. 为每个唯一流程加载快照（定义 + 活跃版本 + 节点 + 连线）
        Map<Long, FlowSnapshot> flowSnapshotMap = new HashMap<>();
        for (Long flowId : uniqueFlowIds) {
            // 为指定流程 ID 构建运行时快照（定义 + 活跃版本 + 节点索引 + 连线列表）
            FlowSnapshot snapshot;
            if (batch == null) {
                // 为指定流程构建运行时快照，包含活跃版本、节点索引及连线
                snapshot = buildFlowSnapshot(flowId);
            } else {
                // 同一流程只读取一次快照
                snapshot = loadBatchFlowSnapshot(batch, flowId);
            }
            if (snapshot != null) {
                flowSnapshotMap.put(flowId, snapshot);
            }
        }

        SubmitSharedData shared = new SubmitSharedData(
                securityInfo,
                poolMap,
                currentPoolIds,
                poolRelationMap,
                flowSnapshotMap,
                batchNoContext,
                selectedRatingCompany
        );
        shared.resolvedFlowIdsByKey.putAll(resolvedFlowIdsByKey);
        return shared;
    }

    /**
     * 从调库项的 flowId 或 flowKey 解析出流程定义 ID
     *
     * 优先使用 flowId（直接匹配），否则用 flowKey 查 DB 获取
     *
     * 执行顺序：
     * 1. 调库项直接指定流程 ID 时使用该 ID
     * 2. 仅指定流程编码时优先复用批次查询结果，未缓存时查询启用定义
     *
     * @param item 调库项
     * @param batch 本批提交数据
     * @return 调库项使用的流程 ID，未指定流程或找不到启用定义时返回 null
     */
    private Long resolveFlowIdFromItem(SecurityPoolAdjustSubmitReq.AdjustItem item, BatchSubmitData batch) {
        // 1. 调库项直接指定流程 ID 时使用该 ID
        if (item.getFlowId() != null) {
            return item.getFlowId();
        }
        // 2. 仅指定流程编码时优先复用批次查询结果，未缓存时查询启用定义
        if (item.getFlowKey() != null && !item.getFlowKey().isEmpty()) {
            String key = item.getFlowKey();
            if (batch != null && batch.flowIds.containsKey(key)) {
                return batch.flowIds.get(key);
            }
            FlowDefinitionBo def = flowMapper.queryActiveFlowByKey(key);
            Long flowId = def != null ? def.getId() : null;
            if (batch != null) {
                batch.flowIds.put(key, flowId);
            }
            return flowId;
        }
        return null;
    }

    /**
     * 从提交阶段已加载的快照中取得流程 ID，避免按 Key 再查一遍
     *
     * 执行顺序：
     * 1. 有 ID 时直接使用，否则按 Key 在本次快照中查找
     * 2. 找不到时返回空，由原提交流程处理
     *
     * @param item 所选流程项
     * @param shared 本次提交数据
     * @return 已指定或从本批流程数据匹配到的流程 ID，没有匹配项时返回 null
     */
    private Long resolveLoadedFlowId(SecurityPoolAdjustSubmitReq.AdjustItem item, SubmitSharedData shared) {
        if (item.getFlowId() != null) {
            return item.getFlowId();
        }
        if (item.getFlowKey() != null && !item.getFlowKey().isEmpty()) {
            if (shared.resolvedFlowIdsByKey.containsKey(item.getFlowKey())) {
                return shared.resolvedFlowIdsByKey.get(item.getFlowKey());
            }
            for (Map.Entry<Long, FlowSnapshot> entry : shared.flowSnapshotMap.entrySet()) {
                FlowSnapshot snapshot = entry.getValue();
                if (snapshot != null && snapshot.definition != null
                        && item.getFlowKey().equals(snapshot.definition.getFlowKey())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * 根据主项的快速资格判断本次材料豁免，与用户选择哪种审批流程无关
     *
     * 返回 true 时，调用方跳过原报告必填校验，自愿补充的报告引用仍校验有效性，材料仍正常绑定
     * 返回 false 时继续执行原材料规则，不代表不能入库，也不代表一定要求报告
     * 校验结果返回的材料豁免依据随主项提交，产品来源失效时要求重新校验
     * 本方法只使用已加载数据，不新增数据库查询，不重算信用债评级矩阵、180 天入库经历或历史报告资格
     *
     * 示例（池 ID 为假设值）：108008901.IB 当前在信用债二级库 102，申请调入稳富1号产品池 200
     * 校验通过产品快速资格后，主项携带 productSource，无论选择快速、一般、特殊或批量流程均免材料
     * 所选流程的 ID、Key 和类型仍照常用于审批流转，免材料不代表免审批
     * 同组联动、互斥和关联项跟随主项，独立调出及未获得快速资格的主项继续使用原材料规则
     *
     * 执行顺序：
     * 1. 找到当前项对应的手工主项，配套项按同一 adjustGroupKey 查找
     * 2. 独立调出不适用，调入主项继续判断校验返回的资格依据
     * 3. 优先复用本次提交已保存的主项豁免结果，避免同组重复检查
     * 4. 产品库资格使用已经加载的当前在库集合核对来源，信用债资格不重复查库
     * 5. 保存本次主项的判断结果，后续同组配套项直接复用
     *
     * @param req 本次提交请求，包含主项和同组配套项
     * @param item 当前正在处理的主项、联动项、互斥项或关联项
     * @param shared 已加载的投资池、当前在库状态、流程快照及本次计算缓存
     * @return true=享受快速材料豁免，false=继续执行原材料规则
     */
    private boolean isInboundMaterialExempt(SecurityPoolAdjustSubmitReq req,
                                             SecurityPoolAdjustSubmitReq.AdjustItem item,
                                             SubmitSharedData shared) {
        // 1. 当前项是主项时直接使用，否则按分组找到主项，找不到会报错
        // 例如 G1 的互斥调出项仍找到 G1 的稳富1号调入主项，不能靠配套项自己传流程类型获得豁免
        SecurityPoolAdjustSubmitReq.AdjustItem manual = resolveManualSubmitItem(req, item);
        // 2. 独立调出不享受入库材料豁免，配套调出仍按本组调入主项判断
        if (!AdjustMode.IN.getCode().equals(manual.getAdjustMode())) {
            return false;
        }
        // 3. 同组所有配套项以同一个主项为缓存键，判断结果只在本次提交有效
        Boolean cached = shared.materialExemptionMap.get(manual);
        if (cached != null) {
            return cached;
        }
        // 4. 读取主项的资格依据和目标池，不根据所选流程 ID、Key 或类型决定是否免材料
        InvestmentPoolBo pool = shared.poolMap.get(manual.getTargetPoolId());
        String exemptionType = manual.getMaterialExemptionType();
        boolean exempt = false;
        if (MaterialExemptionType.PRODUCT_SOURCE.getCode().equals(exemptionType)) {
            // 复核主券和同组关联调入证券的当前有效来源
            if (CreditBondSpecialInboundRule.isAbs(shared.securityInfo)
                    // 判断证券是否具备产品库快速入库来源资格
                    || !isProductInboundFlowMatched(req.getSecurityCode(), pool, shared.poolMap, shared.currentPoolIds)) {
                throw new BizException("目标池[" + (pool == null ? manual.getTargetPoolId() : pool.getPoolName())
                        + "]快速入库来源资格已失效，请重新校验");
            }
            for (SecurityPoolAdjustSubmitReq.AdjustItem related : req.getItems()) {
                if (!ItemType.RELATED.getCode().equals(related.getItemTag())
                        || !AdjustMode.IN.getCode().equals(related.getAdjustMode())
                        || !Objects.equals(manual.getAdjustGroupKey(), related.getAdjustGroupKey())) {
                    continue;
                }
                // 关联码按自己的当前在池记录判断
                String code = resolveItemSecurityCode(req, related);
                Set<Long> currentPoolIds = new HashSet<>(securityPoolAdjustMapper.querySecurityCurrentPoolIdList(code));
                // 关联码失去有效来源时整组重新校验
                if (!isProductInboundFlowMatched(code, pool, shared.poolMap, currentPoolIds)) {
                    throw new BizException("关联代码[" + code + "]快速入库来源资格已失效，请重新校验");
                }
            }
            exempt = true;
        } else if (MaterialExemptionType.CREDIT_BOND_SIMPLE.getCode().equals(exemptionType)) {
            // 信用债：沿用校验阶段已计算的资格，不重复查询矩阵、入库经历和历史报告
            exempt = isCreditBondPool(pool);
        }
        // 5. 缓存本次主项的结果，免材料不改变后续实际执行的审批流程
        shared.materialExemptionMap.put(manual, exempt);
        return exempt;
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
     * 1. 单个方向超过一千组时跳过其他方向的序号范围
     *
     * @param manualItem 手工明细
     * @param noFlow 当前分组是否未配置流程
     * @param shared 已完成前置检查的独立提交上下文
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
        // 单个方向超过一千组时跳过其他方向的序号范围
        int serial = first + sequence + (sequence - 1) / 1000 * 2000;
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
     * @param shared 已完成前置检查的独立提交上下文
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
     * 按定义 ID 复用本次提交的流程快照
     *
     * 执行顺序：
     * 1. 批次中还没有该流程的缓存记录时读取并保存流程快照，再返回本批缓存结果
     *
     * @param batch 本次提交的数据
     * @param flowId 流程定义 ID
     * @return 活跃版本快照，流程或活跃版本不存在时返回 null
     */
    private FlowSnapshot loadBatchFlowSnapshot(BatchSubmitData batch, Long flowId) {
        if (!batch.flows.containsKey(flowId)) {
            // 无有效版本时也保留查询结果
            batch.flows.put(flowId, buildFlowSnapshot(flowId));
        }
        return batch.flows.get(flowId);
    }

    /**
     * 为指定流程构建运行时快照，包含活跃版本、节点索引及连线
     *
     * 执行顺序：
     * 1. 按流程 ID 读取活跃版本的节点、连线和配置，构建运行所需的快照
     *
     * @param flowId 流程定义 ID
     * @return 活跃版本快照，流程或活跃版本不存在时返回 null
     */
    private FlowSnapshot buildFlowSnapshot(Long flowId) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().buildFlowSnapshot(flowId);
    }

    /**
     * 判断流程是否为直通流程（开始后无需人工待办即可到结束节点）
     *
     * 与 createInitialSteps 自动完成范围对齐：start/end/auto 节点、initiator、approval_strategy=auto
     * 可无人工到达 end；主路径存在 preempt/all 等人工审批则为非直通
     * 例：债券特殊策略入库（发起人→多层 auto→结束）应判为直通
     *
     * 执行顺序：
     * 1. 按流程快照沿非驳回路径核对节点，判断能否不经人工审批走到结束
     *
     * @param snapshot 流程快照（含节点索引和连线列表）
     * @return true=直通流程（可直接生效），false=需要审批
     */
    private boolean isDirectFlow(FlowSnapshot snapshot) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isDirectFlow(snapshot);
    }

    /**
     * 提交证券池调入申请并绑定上传附件
     *
     * 按所选流程分别生成待审批或直通调库记录，直通记录由本次提交统一落池
     *
     * 执行顺序：
     * 1. 逐项校验报告库来源附件真实存在且与报告限制匹配，适用于债券信评报告和基金报告
     * 2. 校验报告库来源附件真实存在且与报告限制匹配，适用于债券信评报告和基金报告
     *
     * @param req 调库提交请求
     * @param shared 已完成前置检查的独立提交上下文
     * @param submissionFiles 已创建的附件上下文（可为整批共享）
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
            // 报告必填校验（按池 in_report_restriction；关联码项由同组手工项承担报告，跳过）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            // 同组先按主项快速资格判断材料豁免，无资格时继续执行原报告要求
            if (isExcelAdjustType(req.getAdjustType())) {
                // 判断提交项是否为手工调库项
                if (isManualSubmitItem(item)
                        && MaterialExemptionType.PRODUCT_SOURCE.getCode().equals(item.getMaterialExemptionType())) {
                    // Excel 仍免必填报告，带有产品库豁免资格时复核来源
                    isInboundMaterialExempt(req, item, shared);
                }
                // Excel 的业务校验只作提示，不在提交时用报告必填要求重新阻断
                sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(), false);
            // 根据主项的快速资格判断本次材料豁免，与用户选择哪种审批流程无关
            } else if (isInboundMaterialExempt(req, item, shared)) {
                // 自愿补充的报告仍须来自有效报告库，不再强制内部来源
                sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(), false);
            } else if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                // 规则：报告必填（in_report_restriction / out_report_restriction）
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getInReportRestriction() : null,
                        // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
                        resolveItemSecurityCode(req, item));
            }
            // 获取同组手工调库项，联动/互斥/关联项按手工项共用流程和批次号
            SecurityPoolAdjustSubmitReq.AdjustItem manualItem = resolveManualSubmitItem(req, item);
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long flowId = resolveLoadedFlowId(manualItem, shared);
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
                // 直通调入也要登记到提交日志集合，供 ABS 主体快照按日志 ID 保存
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
                securityPoolAdjustMapper.addAdjustLog(bo);
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
     * 提交证券池调出申请并绑定上传附件
     *
     * 按所选流程分别生成待审批或直通调库记录，直通记录由本次提交统一落池
     *
     * 执行顺序：
     * 1. 逐项校验报告库来源附件真实存在且与报告限制匹配，适用于债券信评报告和基金报告
     * 2. 校验报告库来源附件真实存在且与报告限制匹配，适用于债券信评报告和基金报告
     *
     * @param req 调库提交请求
     * @param shared 已完成前置检查的独立提交上下文
     * @param submissionFiles 已创建的附件上下文（可为整批共享）
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
            // 报告必填校验（按池 out_report_restriction；关联码项由同组手工项承担报告，跳过）
            InvestmentPoolBo reportPool = shared.poolMap.get(item.getTargetPoolId());
            // 快速主项带出的互斥、联动调出一并免材料，独立调出不豁免
            if (isExcelAdjustType(req.getAdjustType())) {
                // Excel 配套项沿用主项的提示后放行口径，已提供的报告仍检查引用有效性
                sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(), false);
            // 根据主项的快速资格判断本次材料豁免，与用户选择哪种审批流程无关
            } else if (isInboundMaterialExempt(req, item, shared)) {
                // 自愿补充的报告仍校验引用有效性
                sysAttachmentService.validateCreditReportSources(item.getCreditReportSourceAttachmentIds(), false);
            } else if (!ItemType.RELATED.getCode().equals(item.getItemTag())) {
                // 规则：报告必填（in_report_restriction / out_report_restriction）
                checkReportRequired(item, reportPool, reportPool != null ? reportPool.getOutReportRestriction() : null,
                        // 解析调库项实际证券代码：item 级优先，否则回退请求级主券代码
                        resolveItemSecurityCode(req, item));
            }
            // 获取同组手工调库项，联动/互斥/关联项按手工项共用流程和批次号
            SecurityPoolAdjustSubmitReq.AdjustItem manualItem = resolveManualSubmitItem(req, item);
            // 从调库项的 flowId 或 flowKey 解析出流程定义 ID
            Long flowId = resolveLoadedFlowId(manualItem, shared);
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
                securityPoolAdjustMapper.addAdjustLog(bo);
                shared.submittedLogs.put(bo.getId(), bo);
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
     * 为本次调库日志保存证券信息和评级主体快照
     *
     * 合并主档与前端可编辑字段后，仅按本次提交的每个调库日志写入证券信息快照；
     * 不修改主档 {@code rrs_securityinfo}（主档仍为全站真相源，调库页改动只作本笔留痕）
     *
     * 执行顺序：
     * 1. 合并本次可编辑的证券信息与校验后的评级主体，按调库日志立即写入快照
     *
     * @param req 调库提交请求
     * @param shared 本次提交的共享数据，快照按其中的调库记录逐条写各自的证券代码
     */
    private void postSubmitProcess(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared) {
        // 合并本次证券快照数据，批量路径先收集再写入
        postSubmitProcess(req, shared, null);
    }

    /**
     * 合并本次证券快照数据，批量路径先收集再写入
     *
     * 执行顺序：
     * 1. 合并主档当前值与前端传入字段，作为本笔快照内容（不回写主档）
     * 2. 主体与评级字段按本次校验后的结果原样落快照，不在下游再按证券类型挑字段
     * 3. 按调库日志落证券信息快照
     *
     * @param req 调库提交请求
     * @param shared 已完成前置检查的独立提交上下文
     * @param snapshots 本次待批量写入的快照
     */
    private void postSubmitProcess(SecurityPoolAdjustSubmitReq req, SubmitSharedData shared,
                                   List<AdjustSecuritySnapshotBo> snapshots) {
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
        // 3. 按调库日志落证券信息快照
        if (CreditBondSpecialInboundRule.isAbs(shared.securityInfo) && snapshots == null) {
            absIdentityService.saveSnapshots(mergedSecurityInfo, shared.submittedLogs, req.getAdjusterId(),
                    shared.selectedRatingCompany.rightsHolderName, shared.selectedRatingCompany.selfSelectedRightsHolderName);
        } else {
            // 为本次提交的每个调库日志写入证券信息快照
            saveAdjustSecuritySnapshots(req, shared.submittedLogs, shared.selectedRatingCompany, snapshots);
        }
    }

    /**
     * 为本次提交的每个调库日志写入证券信息快照
     *
     * 多市场关联码各自独立落调库记录，快照必须按各自证券代码保存；
     * 若统一记成主码，按「日志 ID + 证券代码」精确回查时会查不到快照
     *
     * 执行顺序：
     * 1. 从合并后的证券信息拷贝同名字段，并补齐快照关联字段
     * 2. 用当前调库记录自身的证券代码覆盖快照代码，保证快照与日志一一对应
     * 3. 补齐本次选定评级主体的权益人留痕字段后保存快照
     *
     * @param req 调库提交请求
     * @param logs 本次提交生成的调库记录，按日志 ID 索引
     * @param selectedRatingCompany 本次提交选定的评级主体
     * @param snapshots 本次待批量写入的快照
     */
    private void saveAdjustSecuritySnapshots(SecurityPoolAdjustSubmitReq req, Map<Long, IpAdjustLogBo> logs,
                                             SelectedRatingCompanyData selectedRatingCompany,
                                             List<AdjustSecuritySnapshotBo> snapshots) {
        if (logs == null || logs.isEmpty() || req.getSecurityInfo() == null) {
            return;
        }
        Date now = new Date();
        for (IpAdjustLogBo log : logs.values()) {
            if (log == null || log.getId() == null) {
                continue;
            }
            // 从合并后的证券信息拷贝同名字段，并补齐快照关联字段
            AdjustSecuritySnapshotBo snapshot = buildAdjustSecuritySnapshot(req.getSecurityInfo(), log.getId(),
                    req.getAdjusterId(), now);
            // 快照证券代码以本条调库记录为准，关联市场代码不能记成主码
            snapshot.setWindCode(log.getSecurityCode());
            if (selectedRatingCompany != null) {
                snapshot.setAbsOriginatorName(selectedRatingCompany.rightsHolderName);
                snapshot.setCompanySelector(selectedRatingCompany.selfSelectedRightsHolderName);
            }
            if (snapshots == null) {
                securityPoolAdjustMapper.addAdjustSecuritySnapshot(snapshot);
            } else {
                snapshots.add(snapshot);
            }
        }
    }

    /**
     * 根据本次调库使用的证券资料构建快照
     *
     * 执行顺序：
     * 1. 复制证券资料和本次采用的评级字段
     * 2. 关联调库日志，填写提交人、时间和有效状态
     *
     * @param securityInfo 本次调库使用的证券资料和评级字段
     * @param adjustLogId 本次调库日志 ID
     * @param submitterId 提交人 ID
     * @param submitTime 提交时间
     * @return 待写入的证券快照
     */
    static AdjustSecuritySnapshotBo buildAdjustSecuritySnapshot(SecurityInfoBo securityInfo, Long adjustLogId,
                                                                 String submitterId, Date submitTime) {
        // 1. 保留本次调库使用的证券资料和评级字段
        AdjustSecuritySnapshotBo snapshot = new AdjustSecuritySnapshotBo();
        BeanUtils.copyProperties(securityInfo, snapshot);
        // 2. 填写快照对应的调库日志和提交信息
        snapshot.setAdjustLogId(adjustLogId);
        snapshot.setSubmitterId(submitterId);
        snapshot.setSubmitTime(submitTime);
        snapshot.setIsDeleted(0);
        snapshot.setCrteTime(submitTime);
        snapshot.setUpdtTime(submitTime);
        return snapshot;
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
        SecurityInfoBo source = loaded == null ? securityPoolAdjustMapper.querySecurityBoByCode(securityCode) : loaded;
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
     * 查询调库记录列表（全量，不分页）
     *
     * 按证券代码返回全部调库历史（含终态），不以调库批次号过滤
     *
     * 执行顺序：
     * 1. 检查证券代码不能为空
     * 2. 查询投资池全路径名称，用于调库记录展示
     *
     * @param req securityCode 必填；adjustBatchNo 仅供流程定位，本查询不使用
     * @return 调库日志列表
     */
    public List<AdjustLogDto> queryAdjustLogList(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
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
            dto.setPoolType(log.getPoolType());
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
     * 分四个阶段顺序执行：
     *
     * 执行顺序：
     * 1. 前置校验：对请求入参做合法性检查，无 DB 操作，不通过直接抛异常
     * 2. 参数初始化：集中执行所有 DB 查询，将本次调用所需数据封装为{@link AdjustSharedData}，后续阶段直接读取，不再单独查库
     * 3. 调入校验：处理全部调入方向的手动项（含前置规则 + 调入规则），并根据池关系自动追加联动调入项、互斥配套调出项
     * 4. 调出校验：处理全部调出方向的手动项（含前置规则 + 调出规则），并根据池关系自动追加联动调出项（已被第三阶段覆盖的自动项跳过，不重复生成）
     *
     * @param req 包含证券代码和一个或多个用户选择的调库项
     * @return 包含手动项和自动项的完整校验结果列表
     */
    public AdjustCheckDto checkAdjust(AdjustCheckReq req) {
        // 单笔与 Excel 共用完整资格匹配，Excel 在映射时追加批量候选
        return checkAdjustInternal(req, false, false);
    }

    /**
     * 两个批量申请入口复用单笔校验，并追加配置的批量流程
     *
     * 执行顺序：
     * 1. 执行完整单笔资格校验
     * 2. 使用已加载的目标池配置追加批量候选，不再查询投资池
     *
     * @param req 调库校验请求
     * @return 单笔匹配与额外批量候选
     */
    public AdjustCheckDto checkBatchAdjust(AdjustCheckReq req) {
        // 共用同一校验实现，仅追加批量候选
        return checkAdjustInternal(req, true, false);
    }

    /**
     * Excel 证券导入照常执行原校验，失败原因保留为提示，并允许可识别的整组数据提交
     *
     * 执行顺序：
     * 1. 执行原单笔规则和关联市场展开，不修改任何规则方法
     * 2. 在生成流程候选前统一放行有实际证券和目标池的组，保留所有原失败原因
     *
     * @param req Excel 导入行对应的单笔请求
     * @return 可提交状态、原校验原因和完整流程候选
     */
    public AdjustCheckDto checkExcelAdjust(AdjustCheckReq req) {
        // Excel 只改变校验结果的使用方式，批量候选仍由导入服务追加
        return checkAdjustInternal(req, false, true);
    }

    /**
     * 原规则全部执行后，统一将 Excel 业务失败作为可提交提示
     *
     * 执行顺序：
     * 1. 用本次已加载数据识别没有实际证券或目标池的组，这类数据仍无法落库
     * 2. 其余组统一允许提交，保留 failReasons 供页面显示和导入快照留存
     *
     * @param shared 本次已经加载的主券、关联券和投资池
     * @param items 原规则执行后的完整结果
     */
    private void allowExcelImportResults(AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> items) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        checkRules().allowExcelImportResults(shared, items);
    }

    /**
     * 按入口要求执行完整校验，统一匹配快速资格和材料豁免
     *
     * 执行顺序：
     * 1. 校验单券调库请求
     * 2. 加载本次校验共用的证券和池数据
     * 3. 执行逐券准入与关联市场校验，再生成流程候选
     *
     * @param req 调库校验请求
     * @param includeBatchFlows 是否追加目标池配置的批量流程
     * @param excelWarningsOnly 是否将 Excel 原业务失败作为可提交的调整提示
     * @return 调库校验结果和流程候选
     */
    private AdjustCheckDto checkAdjustInternal(AdjustCheckReq req, boolean includeBatchFlows, boolean excelWarningsOnly) {
        // 1. 校验单券调库请求
        validateCheckAdjustReq(req);
        // 2. 加载本次校验共用的证券和池数据
        AdjustSharedData shared = loadSharedData(req);
        // 3. 执行逐券准入与关联市场校验，再生成流程候选
        return checkRules().checkPreparedAdjust(req, shared, includeBatchFlows, excelWarningsOnly);
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 四阶段实现
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验证券池调库检查请求的必填项
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
     * 加载证券池调库校验共用的证券、评级和投资池数据
     *
     * 集中执行本次校验所需的全部 DB 查询，构建 {@link AdjustSharedData}
     * 调入和调出校验共用这些数据
     * 证券存在性校验也在此阶段完成（查到 null 则抛异常）
     *
     * 执行顺序：
     * 1. 证券基础信息（兼含存在性校验）
     * 2. 使用本次已选评级主体，ABS 必须选择权益人或自选权益人
     * 3. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
     * 4. 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
     * 5. 评级下调三标志：主体与展望按发行人评级判定，担保人按本次所选担保人代码判定
     * 6. 基金评分（基金证券调入校验用，透传请求级 fundRate）
     * 7. 是否放开规则：是=跳过主体债入库矩阵校验（对齐批量调库 releaseRules）
     *
     * @param req 调库校验请求（需携带 securityCode）
     * @return 封装了本次校验全量共享数据的 AdjustSharedData
     */
    private AdjustSharedData loadSharedData(AdjustCheckReq req) {
        // 1. 证券基础信息（兼含存在性校验）
        SecurityInfoBo securityInfo = req.getSecurityInfo() != null && req.getSecurityInfo().getSecurityType() != null
                ? req.getSecurityInfo() : securityPoolAdjustMapper.querySecurityBoByCode(req.getSecurityCode());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }
        // 2. 使用本次已选评级主体，ABS 必须选择权益人或自选权益人
        applySelectedRatingCompanyInternal(securityInfo, req.getGuarantorCode(), req.getRightsHolderCode(),
                req.getSelfSelectedRightsHolderCode(), true, req.getSelectedRatingCompany());

        // 3. 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        for (InvestmentPoolBo p : allPools) {
            poolMap.put(p.getId(), p);
        }

        // 4. 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
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
        // 将目标池列表转换为顶级投资池组 ID 集合
        shared.setPendingPoolGroupIds(resolvePoolGroupIds(
                securityPoolAdjustMapper.queryPendingManualTargetPoolIdList(req.getSecurityCode(), null), poolMap));
        // 观察池：证券或主体已在观察池则不得高于矩阵最好档；重点观察名单：禁新增，已在库只能去五级
        shared.setSecurityInObservePool(securityPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()));
        shared.setIssuerInObservePool(queryIssuerInPoolType(securityInfo, "observe"));
        shared.setSecurityInRestrictedPool(securityPoolAdjustMapper.querySecurityInRestrictedPool(req.getSecurityCode()));
        shared.setIssuerInRestrictedPool(queryIssuerInPoolType(securityInfo, "restricted"));
        // 5. 评级下调三标志：主体与展望按发行人评级判定，担保人按本次所选担保人代码判定
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
     * ABS 复用全部权益人候选检查主体池，普通债保持单发行人查询
     *
     * 执行顺序：
     * 1. ABS 使用本次已查询候选和自选代码，只重查动态池状态
     * 2. 普通债沿用原发行主体口径
     *
     * @param security 本次证券
     * @param poolType 主体池类型
     * @return true=证券发行主体命中指定类型的池，false=证券发行主体未命中指定类型的池
     */
    public boolean queryIssuerInPoolType(SecurityInfoBo security, String poolType) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().queryIssuerInPoolType(security, poolType);
    }

    /**
     * 按证券类型校验并回填本次评级主体：ABS 自选权益人优先，非 ABS 使用担保人
     *
     * 执行顺序：
     * 1. 执行共用评级主体选择后转换为现有调库结果
     *
     * @param securityInfo 当前证券信息
     * @param guarantorCode 担保人代码
     * @param rightsHolderCode 普通权益人
     * @param selfSelectedRightsHolderCode 自选权益人代码
     * @param requireAbsSelection ABS 是否需要选择主体
     * @return 本次快照使用的主体名称
     */
    private SelectedRatingCompanyData applySelectedRatingCompanyInternal(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode,
            String selfSelectedRightsHolderCode, boolean requireAbsSelection) {
        // 执行共用评级主体选择后转换为现有调库结果
        SecurityPoolCheckRules.RatingSelection selection = checkRules().applySelectedRatingCompanyInternal(
                securityInfo, guarantorCode, rightsHolderCode, selfSelectedRightsHolderCode, requireAbsSelection);
        SelectedRatingCompanyData result = new SelectedRatingCompanyData();
        result.rightsHolderName = selection.rightsHolderName;
        result.selfSelectedRightsHolderName = selection.selfSelectedRightsHolderName;
        return result;
    }

    /**
     * 直接复用页面已选主体，未携带选择对象的后台调用沿用原候选读取
     *
     * 执行顺序：
     * 1. 已传主体时直接回填五字段及矩阵评分，不重复查询名称和评级
     * 2. 未传主体对象时沿用原关系候选入口
     *
     * @param securityInfo 当前证券信息
     * @param guarantorCode 担保人代码
     * @param rightsHolderCode 普通权益人代码
     * @param selfSelectedRightsHolderCode 自选权益人代码
     * @param requireAbsSelection ABS 是否需要选择主体
     * @param selected 页面已选主体
     * @return 本次快照使用的主体名称
     */
    private SelectedRatingCompanyData applySelectedRatingCompanyInternal(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode,
            String selfSelectedRightsHolderCode, boolean requireAbsSelection, RatingCompanySelectionReq selected) {
        // 执行共用评级主体选择后转换为现有调库结果
        return applySelectedRatingCompanyInternal(securityInfo, guarantorCode, rightsHolderCode,
                selfSelectedRightsHolderCode, requireAbsSelection, selected, null);
    }

    /**
     * 批量提交使用提交时已读取的候选，Excel 使用本次按数据库复核的主体
     *
     * 执行顺序：
     * 1. 获取规则组件，直接构造服务时沿用显式注入的依赖
     *
     * @param securityInfo 当前证券信息
     * @param guarantorCode 担保人代码
     * @param rightsHolderCode 普通权益人
     * @param selfSelectedRightsHolderCode 自选权益人代码
     * @param requireAbsSelection ABS 是否需要选择主体
     * @param selected 页面已选主体
     * @param batchData 批次数据
     * @return 本次快照使用的主体名称
     */
    private SelectedRatingCompanyData applySelectedRatingCompanyInternal(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode, String selfSelectedRightsHolderCode,
            boolean requireAbsSelection, RatingCompanySelectionReq selected, BatchSecurityCheckData batchData) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        SecurityPoolCheckRules.RatingSelection selection = checkRules().applySelectedRatingCompanyInternal(
                securityInfo, guarantorCode, rightsHolderCode, selfSelectedRightsHolderCode,
                requireAbsSelection, selected, batchData);
        SelectedRatingCompanyData result = new SelectedRatingCompanyData();
        result.rightsHolderName = selection.rightsHolderName;
        result.selfSelectedRightsHolderName = selection.selfSelectedRightsHolderName;
        return result;
    }

    /**
     * 供其他调库链路复用证券池调库的评级主体选择、防伪校验与回填口径
     *
     * 执行顺序：
     * 1. 按证券类型校验并回填本次评级主体：ABS 自选权益人优先，非 ABS 使用担保人
     *
     * @param securityInfo 当前证券信息
     * @param guarantorCode 担保人代码
     * @param rightsHolderCode 普通权益人
     * @param selfSelectedRightsHolderCode 自选权益人代码
     * @param requireAbsSelection ABS 是否需要选择主体
     * @return 主体名称留痕数据
     */
    public SelectedRatingCompanyData applySelectedRatingCompany(
            SecurityInfoBo securityInfo, String guarantorCode, String rightsHolderCode,
            String selfSelectedRightsHolderCode, boolean requireAbsSelection) {
        // 按证券类型校验并回填本次评级主体：ABS 自选权益人优先，非 ABS 使用担保人
        return applySelectedRatingCompanyInternal(securityInfo, guarantorCode, rightsHolderCode,
                selfSelectedRightsHolderCode, requireAbsSelection);
    }

    /**
     * ABS 和 CRMW 共用页面已选主体，不为同一个主体重复查候选
     *
     * 执行顺序：
     * 1. 将页面选择对象传入统一回填入口
     *
     * @param securityInfo 当前证券
     * @param guarantorCode 担保人代码
     * @param rightsHolderCode 权益人代码
     * @param selfSelectedRightsHolderCode 自选主体代码
     * @param requireAbsSelection 是否要求主体
     * @param selected 页面已选主体
     * @return 主体名称留痕数据
     */
    public SelectedRatingCompanyData applySelectedRatingCompany(SecurityInfoBo securityInfo,
            String guarantorCode, String rightsHolderCode, String selfSelectedRightsHolderCode,
            boolean requireAbsSelection, RatingCompanySelectionReq selected) {
        // 复用页面数据回填本次主体
        return applySelectedRatingCompanyInternal(securityInfo, guarantorCode, rightsHolderCode,
                selfSelectedRightsHolderCode, requireAbsSelection, selected);
    }

    /**
     * 将后端重查的主体名称、代码及最新内评写入本次业务对象
     *
     * 执行顺序：
     * 1. 获取规则组件，直接构造服务时沿用显式注入的依赖
     *
     * @param securityInfo 当前证券信息
     * @param companyCode 发行主体代码
     * @param companyName 最终选择的主体名称
     * @param innerRating 主体内评
     */
    private void fillSelectedRatingCompany(
            SecurityInfoBo securityInfo, String companyCode, String companyName, String innerRating) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        checkRules().fillSelectedRatingCompany(securityInfo, companyCode, companyName, innerRating);
    }

    /**
     * 校验证券调入主项并生成联动和互斥配套项
     *
     * 遍历请求中全部调入方向的手动项，逐项执行前置校验 + 调入校验，
     * 并根据目标池的关系配置自动追加以下两类附加项：
     * 联动调入项（linkage）：目标池配置的联动池（in_linked）需同步调入
     * 互斥配套调出项（mutex）：若证券当前已在目标池的互斥池（in_mutex）中，
     * 需同步调出该互斥池，两池不可同时持有
     *
     * 执行顺序：
     * 1. 用请求、共享数据和已覆盖的键检查调入主项，并按池关系追加配套项
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调入方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeInAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().executeInAdjustCheck(req, shared, coveredKeys);
    }

    /**
     * 校验证券调出主项并生成适用的配套调出项
     *
     * 遍历请求中全部调出方向的手动项，逐项执行前置校验 + 调出校验，
     * 并根据目标池的关系配置自动追加以下附加项：
     * 联动调出项（linkage）：目标池配置的联动池（out_linked）需同步调出
     * 注意：第三阶段由互斥关系生成的调出项已加入 coveredKeys，
     * 若调出方向的手动项恰好与之重叠，以手动项为准（手动项在 coveredKeys 初始化时已注册，
     * 不会被跳过）；联动调出自动项若已被覆盖则跳过
     *
     * 执行顺序：
     * 1. 用已覆盖的键避免重复生成配套项，检查调出主项及需要同步调出的投资池
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调出方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeOutAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().executeOutAdjustCheck(req, shared, coveredKeys);
    }

    /**
     * 根据证券调库校验结果查询可选和推荐流程
     *
     * 调出时直接使用目标投资池配置的标准调出流程。调入时：信用债已在库返回上调/下调单流程，
     * 信用债未在库返回简易/默认调入候选；白名单流程开关开启时才额外返回白名单候选
     * 白名单和简易规则按伪代码保留入口，不改表结构
     *
     * 执行顺序：
     * 1. 根据调库请求和各项校验结果，计算本次可选择或推荐的流程
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param resultItems 前四阶段的校验结果
     * @return 流程候选项
     */
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptions(
            AdjustCheckReq req, AdjustSharedData shared, List<AdjustCheckDto.CheckResultItem> resultItems) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolveAdjustFlowOptions(req, shared, resultItems);
    }

    /**
     * 判断单条手工调库项可选择的审批流程
     *
     * 执行顺序：
     * 1. 只对传入的单个手工调库项计算可用流程及不匹配原因
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param item 当前主项
     * @return 当前手工项的审批流程候选列表
     */
    private List<AdjustCheckDto.FlowOption> resolveAdjustFlowOptionsForItem(
            AdjustCheckReq req, AdjustSharedData shared, AdjustCheckDto.CheckResultItem item) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolveAdjustFlowOptionsForItem(req, shared, item);
    }

    /**
     * 命中调入互斥池特殊审批时构建特殊流程候选项
     *
     * 信用债大库（pool_type=credit_bond）默认排除：互斥场景仍走白名单/简易/升降级/默认调入，
     * 不强制覆盖为 bond:special-inbound
     *
     * 执行顺序：
     * 1. 用目标池和本次共享数据核对调入互斥池的特殊审批配置，按结果建立流程候选
     *
     * @param targetPool 当前调整项的目标池配置
     * @param shared 本次校验的共享数据
     * @return 特殊调入流程候选，未命中时为 null
     */
    private AdjustCheckDto.FlowOption resolveSpecialInboundFlowOption(InvestmentPoolBo targetPool,
                                                                      AdjustSharedData shared) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolveSpecialInboundFlowOption(targetPool, shared);
    }

    /**
     * 按现有存量入口的根节点和启用叶子范围识别产品库
     *
     * 执行顺序：
     * 1. 排除停用、删除和非叶子节点
     * 2. 沿本次已加载的父子关系确认属于存量产品库树
     *
     * @param pool 目标池
     * @param poolMap 已加载的投资池集合
     * @return true=目标池在产品库快速入库范围内，false=目标池不在产品库快速入库范围内
     */
    boolean isProductInboundTarget(InvestmentPoolBo pool, Map<Long, InvestmentPoolBo> poolMap) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isProductInboundTarget(pool, poolMap);
    }

    /**
     * 判断证券是否具备产品库快速入库来源资格
     *
     * 执行顺序：
     * 1. 确认目标属于存量产品库范围
     * 2. 检查普通状态表的有效来源，CRMW 库受开关控制
     * 3. 普通来源未命中时按标的代码检查 CRMW 组合状态
     *
     * @param securityCode 证券代码
     * @param pool 目标池
     * @param poolMap 已加载的投资池集合
     * @param currentPoolIds 当前已生效的在库集合，不含本次待申请项
     * @return true=证券满足产品库快速入库资格，false=证券不满足产品库快速入库资格
     */
    private boolean isProductInboundFlowMatched(String securityCode, InvestmentPoolBo pool,
                                                 Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isProductInboundFlowMatched(securityCode, pool, poolMap, currentPoolIds);
    }

    /**
     * 批次检查产品库来源时复用已查的 CRMW 标的状态
     *
     * 执行顺序：
     * 1. 按证券代码和目标池核对产品库快速入库资格，CRMW 标的状态使用本批预读数据
     *
     * @param securityCode 证券代码
     * @param pool 目标池
     * @param poolMap 已加载的投资池集合
     * @param currentPoolIds 当前已生效的在库集合，不含本次待申请项
     * @param batchData 批次数据
     * @return true=证券满足产品库快速入库资格，false=证券不满足产品库快速入库资格
     */
    private boolean isProductInboundFlowMatched(String securityCode, InvestmentPoolBo pool,
                                                 Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                                 BatchSecurityCheckData batchData) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isProductInboundFlowMatched(securityCode, pool, poolMap, currentPoolIds, batchData);
    }

    /**
     * 用关联市场已加载的数据检查快速资格，任一关联码不符则整组改荐原流程
     *
     * 执行顺序：
     * 1. 仅检查有材料豁免依据且可选的快速候选
     * 2. 逐码复用当前校验数据检查对应快速资格
     * 3. 不满足时禁用快速候选，保留具体代码并推荐原流程
     *
     * @param shared 主券与关联市场的已加载数据
     * @param item 当前主项
     * @param options 当前流程候选
     */
    private void restrictRelatedFastFlowOptions(AdjustSharedData shared,
                                                 AdjustCheckDto.CheckResultItem item,
                                                 List<AdjustCheckDto.FlowOption> options) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        checkRules().restrictRelatedFastFlowOptions(shared, item, options);
    }

    /**
     * 判断证券能否使用白名单流程
     *
     * 执行顺序：
     * 1. 调用证券调库规则组件检查白名单条件，并记录逐项匹配原因
     *
     * @param req 调库校验请求
     * @param shared 本次校验的共享数据
     * @param matchReasons 用于收集匹配原因的列表
     * @param unmatchReasons 用于收集不匹配原因的列表
     * @return true=满足白名单流程条件，false=不满足白名单流程条件
     */
    private boolean isWhitelistFlowMatched(
            AdjustCheckReq req, AdjustSharedData shared, List<String> matchReasons, List<String> unmatchReasons) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isWhitelistFlowMatched(req, shared, matchReasons, unmatchReasons);
    }

    /**
     * 简易流程命中判断入口
     *
     * 以下五项必须全部满足，逐项记录命中或不命中的原因：
     *
     * 执行顺序：
     * 1. 当前规则唯一允许的层级为信用债一至三级，且目标池本身在准入范围内
     * 2. 同发行人债券近180天以非简易入库流程进入过信用债大库任意层级，含升库和降库
     * 3. 同发行人债券近180天实际入库的已通过调入业务中有信评附件
     * 4. 同发行人债券近180天实际入库的已通过调入业务中有有效证券分析
     * 5. 满足永续债、次级债和标准信用债各自的附加要求唯一性按完整一至五级准入范围判断，不受当前勾选池、权限或放开规则开关影响
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
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isSimpleInboundFlowMatched(req, shared, targetPool, matchReasons, unmatchReasons);
    }

    /**
     * 简易条件4：检查特殊债档位要求，以及次级债和标准信用债的期限上限
     *
     * 执行顺序：
     * 1. 获取规则组件，直接构造服务时沿用显式注入的依赖
     *
     * @param shared 本次校验的共享数据
     * @param standardBestSort 特殊债调整前的标准矩阵最佳层级，层级数字增加1表示下调一级
     * @param targetSort 本次目标层级
     * @param failures 校验失败原因
     */
    private void checkSimpleInboundSpecialConditions(
            AdjustSharedData shared, Integer standardBestSort, Integer targetSort, List<String> failures) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        checkRules().checkSimpleInboundSpecialConditions(shared, standardBestSort, targetSort, failures);
    }

    /**
     * 独立判断标准信用债，完整业务定义由上游维护，只有std_credit_flag=1时认定为标准
     *
     * 执行顺序：
     * 1. 读取证券主档的标准信用债标志，值为 1 时才判为标准信用债
     *
     * @param security 本次证券
     * @return true=标准信用债标志为 1，false=标准信用债标志未设置或不为 1
     */
    private boolean isStandardCreditBond(SecurityInfoBo security) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isStandardCreditBond(security);
    }

    /**
     * 判断目标池是否属于信用债大库
     *
     * 执行顺序：
     * 1. 读取目标池类型并核对是否属于信用债大库
     *
     * @param pool 目标池
     * @return true=目标池属于信用债大库，false=目标池不属于信用债大库
     */
    private boolean isCreditBondPool(InvestmentPoolBo pool) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isCreditBondPool(pool);
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
     * @param securityInfo 当前证券信息
     * @return true=有担保人名称或代码，false=没有担保人名称或代码
     */
    private boolean hasGuarantor(SecurityInfoBo securityInfo) {
        if (securityInfo == null) {
            return false;
        }
        return CreditBondSpecialInboundRule.isGuaranteed(securityInfo)
                && ((securityInfo.getGuarantor() != null && !securityInfo.getGuarantor().trim().isEmpty())
                || (securityInfo.getGuarantorId() != null && !securityInfo.getGuarantorId().trim().isEmpty()));
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
     * 1. 用当前校验上下文执行通用调入规则和证券品种专属规则，汇总阻断原因
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkInConditions(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().checkInConditions(ctx);
    }

    /**
     * 规则：主体债入库规则（credit_bond_pool_grade_rule 矩阵）
     *
     * 调入信用债大库池时，按债券的「主体内评分档 × 期限档」查矩阵得到允许调入的池列表
     * 担保债取主体与担保人内评孰高后再查矩阵；
     * ABS：所选权益人内评（自选权益人优先，复用 innerGuarantorRating）1 档只能调入一级库，缺少内评或非 1 档至少下调一级；
     * 私募发债主体内评 1 档只能调入一级库，其余至少下调一级；
     * 永续发债主体内评 1 档下调一级（矩阵最好档再降一档且只留那一档），其余至少下调一级；
     * 次级发债主体内评 1 档只能调入一级库，2+/2/2- 下调一级（只留那一档），其余至少下调一级
     * 担保债或已在观察池：不得高于矩阵最好档，从该档开到五级
     * 已在重点观察名单：未在分级库不得新增（开关开启时强担保豁免），已在 1～4 级只能去五级。开关开启时池间调入限制仅对强担保豁免 restricted 池，禁止池仍全局拦截。可转债/可交换债/CRMW 不适用信用债 1～5
     * 正式证券无内评禁止入信用债 1～5 级；临时代码默认档 4
     * 期限为空默认最长档（期限&gt;5）继续走矩阵，不跳过
     * 境外债目标池不走本规则。仅调入校验
     *
     * 执行顺序：
     * 1. 使用证券期限和主体内评校验信用债分级库矩阵，返回不准入的原因
     *
     * @param ctx 调库校验上下文
     * @return 分档矩阵未通过时的阻断原因，通过时为空
     */
    private String inCheckMainGradeRule(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckMainGradeRule(ctx);
    }

    /**
     * 按剩余期限（年）匹配信用债期限分组编码
     *
     * 遍历启用的 credit_bond_term_bucket，按 min_term_year/max_term_year + inclusive 标志判定区间归属
     * 入参为 {@link CreditBondRemainTermUtil#resolveRemainTermYears}（date_exists_str 解析，含权/回购已是年）
     *
     * 执行顺序：
     * 1. 按实时期限分组配置，用证券剩余年限匹配适用区间
     *
     * @param remainTermYears 剩余期限年数；null 时按最长档（期限&gt;5）兜底
     * @return 期限档编码；无可用档或年数落不进任何档时返回 null
     */
    private String matchTermBucket(BigDecimal remainTermYears) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().matchTermBucket(remainTermYears);
    }

    /**
     * 使用批次期限档或单券实时配置匹配期限
     *
     * 执行顺序：
     * 1. 有批量期限分组时使用已读取的区间，缺省时查询当前启用的配置
     *
     * @param remainTermYears 剩余期限年数；null 时按最长档（期限&gt;5）兜底
     * @param data 本批共享数据
     * @return 期限档编码；无可用档或年数落不进任何档时返回 null
     */
    private String matchTermBucket(BigDecimal remainTermYears, BatchSecurityCheckData data) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().matchTermBucket(remainTermYears, data);
    }

    /**
     * 查矩阵用内评档：先取主档发行人内评；临时代码默认 4，担保债再与担保人内评取孰高
     *
     * ABS 的评级主体固定写在发行人字段，担保人三字段为空，因此走到这里只会用到发行人内评，无需单独分支
     *
     * 执行顺序：
     * 1. 由共用规则取当前证券的内评分档，临时代码和担保债分别使用对应评级口径
     *
     * @param sec 证券主数据
     * @return 内评档编码；正式券无内评返回 null
     */
    private String resolveMatrixGradeCode(SecurityInfoBo sec) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolveMatrixGradeCode(sec);
    }

    /**
     * 按既有发行人和担保人取档规则获取矩阵内评
     *
     * 执行顺序：
     * 1. 用本批已读配置确定证券的主体及担保人适用档级，供矩阵查询
     *
     * @param sec 当前证券主档
     * @param data 本批共享数据
     * @return 内评档编码；正式券无内评返回 null
     */
    private String resolveMatrixGradeCode(SecurityInfoBo sec, BatchSecurityCheckData data) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolveMatrixGradeCode(sec, data);
    }

    /**
     * 是否信用债大库树（主体评分档套件范围）
     *
     * 执行顺序：
     * 1. 从投资池类型识别是否属于信用债分档适用范围
     *
     * @param pool 投资池
     * @return true=属于按内评分档的信用债池，false=不属于按内评分档的信用债池
     */
    private boolean isGradedBondPool(InvestmentPoolBo pool) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isGradedBondPool(pool);
    }

    /**
     * 当前已在分级库的最好档（inner_sort 最小）
     *
     * 执行顺序：
     * 1. 遍历证券当前所在池，只比较有效信用债档级并取最小排序值
     *
     * @param currentPoolIds 当前在池 ID
     * @param poolMap 全量池索引
     * @return 最好档；未在分级库返回 null
     */
    private Integer resolveCurrentGradedSort(Set<Long> currentPoolIds, Map<Long, InvestmentPoolBo> poolMap) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolveCurrentGradedSort(currentPoolIds, poolMap);
    }

    /**
     * 标准矩阵允许池的最好档
     *
     * 执行顺序：
     * 1. 从矩阵允许的池中选出信用债最好档，缺少可用档级时返回空值
     *
     * @param allowedPoolIds 矩阵允许的信用债池 ID
     * @param poolMap 全量池索引
     * @return 最小 inner_sort；无法解析返回 null
     */
    private Integer resolveBestAllowedSort(List<Long> allowedPoolIds, Map<Long, InvestmentPoolBo> poolMap) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolveBestAllowedSort(allowedPoolIds, poolMap);
    }

    /**
     * 这一级信用债库能不能调入
     * 只能调入一级库 / 下调一级：只留起始档那一级；至少下调一级 / 担保或观察池：起始档及更差；
     * 算不出起始档或普通债：只认矩阵点名的那些池 ID
     *
     * 执行顺序：
     * 1. 按准入模式比较档位或查询允许池 ID，判定目标叶子池是否可以调入
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
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isLeafAllowedByGradeRule(pool, allowedPoolIds, inboundMode, startSort, poolMap);
    }

    /**
     * 去掉信用债大库整棵树
     *
     * 执行顺序：
     * 1. 列表为空时直接返回，逐条排除信用债分级库中的节点
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
     * 按本笔申请的实际评级主体校验改判目标池
     *
     * 执行顺序：
     * 1. 复用候选池查询，按申请快照主体读取最新内评
     * 2. 校验改判目标池是否在当前允许范围内
     *
     * @param securityCode 证券代码
     * @param adjustLogId 本笔申请的调库记录 ID
     * @param redirectPoolId 改判目标池 ID
     * @throws BizException 改判池不在矩阵允许列表内或证券无评级/期限档
     */
    public void validateRedirectPool(String securityCode, Long adjustLogId, Long redirectPoolId) {
        if (redirectPoolId == null) {
            return;
        }
        List<RedirectPoolOptionDto> allowedPools = queryRedirectPoolOptions(securityCode, adjustLogId);
        for (InvestmentPoolBo pool : allowedPools) {
            if (redirectPoolId.equals(pool.getId())) {
                return;
            }
        }
        throw new BizException("改判池不在主体债入库矩阵允许列表内");
    }

    /**
     * 查询本笔信用债调入申请允许改判的目标池
     *
     * 执行顺序：
     * 1. 精确核对调库记录、证券代码、方向和流程状态
     * 2. ABS 按本笔申请快照的权益人、担保债按本笔申请快照的担保人查询最新内评
     * 3. 复用矩阵、特殊债及观察名单规则返回候选池
     *
     * @param securityCode 证券代码
     * @param adjustLogId 本笔申请的调库记录 ID
     * @return 允许调入的池列表，含原名称及完整路径，空列表表示无匹配矩阵
     */
    public List<RedirectPoolOptionDto> queryRedirectPoolOptions(String securityCode, Long adjustLogId) {
        // 1. 核对调库记录、证券代码、方向和流程状态
        if (securityCode == null || securityCode.trim().isEmpty() || adjustLogId == null) {
            throw new BizException("查询改判池必须指定证券代码和调库记录 ID");
        }
        List<IpAdjustLogBo> logs = securityPoolAdjustMapper.queryAdjustLogListForAudit(adjustLogId, null);
        if (logs.size() != 1 || !adjustLogId.equals(logs.get(0).getId())
                || !securityCode.equals(logs.get(0).getSecurityCode())) {
            throw new BizException("调库记录不存在或与当前证券不匹配");
        }
        IpAdjustLogBo log = logs.get(0);
        if (!PoolType.CREDIT_BOND.getCode().equals(log.getPoolType())
                || !AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
            throw new BizException("仅信用债大库调入申请允许改判目标池");
        }
        if (!AuditStatus.SUBMITTED.getCode().equals(log.getAuditStatus())
                && !AuditStatus.REJECT_MODIFY.getCode().equals(log.getAuditStatus())) {
            throw new BizException("调库申请已结束，无法改判目标池");
        }
        // 2. 加载证券信息，按申请快照恢复本笔所选的权益人或担保人
        SecurityInfoBo sec = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (sec == null) {
            throw new BizException("证券不存在，无法查询改判池");
        }
        if (CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(sec)) {
            return new ArrayList<>();
        }
        // ABS 沿用本笔申请选定的权益人，担保债沿用本笔申请选定的担保人
        String context = "证券[" + securityCode + "]调库申请[" + adjustLogId + "]";
        if (CreditBondSpecialInboundRule.isAbs(sec)) {
            // 从本笔申请快照恢复 ABS 权益人，并读取该权益人最新内评
            refreshAbsRatingCompanyFromSnapshot(sec, adjustLogId, context);
            if (sec.getInnerIssuerRating() == null || sec.getInnerIssuerRating().trim().isEmpty()) {
                throw new BizException("所选权益人无最新内评分（评级主体：" + sec.getIssuerCode() + "）");
            }
        } else if (CreditBondSpecialInboundRule.isGuaranteed(sec)) {
            // 从精确调库快照恢复担保债选定的担保人，并读取最新内评
            refreshSnapshotRatingCompany(sec, adjustLogId, context);
        }
        // 3. 按矩阵和证券类型查询允许改判的投资池
        List<Long> allowedPoolIds = queryAllowedPoolIdsForSecurity(sec);
        if (allowedPoolIds == null || allowedPoolIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 4. 回填池配置并组装改判候选
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        for (InvestmentPoolBo p : investmentPoolMapper.queryPoolList()) {
            poolMap.put(p.getId(), p);
        }
        List<RedirectPoolOptionDto> result = new ArrayList<>();
        for (Long pid : allowedPoolIds) {
            InvestmentPoolBo p = poolMap.get(pid);
            if (p != null) {
                RedirectPoolOptionDto option = new RedirectPoolOptionDto();
                BeanUtils.copyProperties(p, option);
                // 复用已加载的池数据构建展示路径，不额外查询，不改写原池名称
                option.setPoolFullName(buildPoolPath(pid, poolMap));
                result.add(option);
            }
        }
        return result;
    }

    /**
     * 最终审批落池前按老系统工作流口径复核动态业务状态
     *
     * 执行顺序：
     * 1. 单券与审批入口仍按其自己的日志读取动态状态
     *
     * @param logList 同批次调库记录
     */
    public void recheckBeforeFinalApproval(List<IpAdjustLogBo> logList) {
        // 单券与审批入口仍按其自己的日志读取动态状态
        recheckBeforeFinalApproval(logList, null, true);
    }

    /**
     * 从现有规则执行最终复核，独立调入复用本批数据库状态
     *
     * 执行顺序：
     * 1. 未启用独立批量标记时，先校验 ABS 在库和在途的主体身份，空批次结束处理
     * 2. 从同组手工项识别 Excel 来源，加载或复用目标池、池关系和本批拟变更的池状态
     * 3. 逐项校验目标池与证券是否存在，有 ABS 申请快照时恢复本次选定的主体
     * 4. Excel 已统一复核本批冲突时沿用既有结果，其他 Excel 入口复核在途及重复调入
     * 5. 普通调库按方向复核池规则，涉及黑名单质押库时再执行相应规则，有阻断原因时终止落池
     *
     * @param logList 同批次调库记录
     * @param batch 本批提交数据
     * @param queryTargetPools 终审复核时是否按目标池 ID 额外查询本批涉及的投资池
     */
    private void recheckBeforeFinalApproval(List<IpAdjustLogBo> logList, BatchSubmitData batch, boolean queryTargetPools) {
        boolean independent = batch != null && batch.independent;
        if (!independent) {
            // 最终审批前先检查 ABS 在库和在途身份
            absIdentityService.validateApprovalIdentities(logList);
        }

        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 配套日志保持联动、互斥、关联类型，通过同组主项批次继承 Excel 来源
        Set<String> excelBatches = collectExcelAdjustBatches(logList);
        Map<Long, InvestmentPoolBo> poolMap = batch == null ? investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, p -> p)) : batch.data.pools;
        if (queryTargetPools) {
            List<Long> poolIds = logList.stream().map(IpAdjustLogBo::getTargetPoolId)
                    .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
            if (!poolIds.isEmpty()) {
                // 按 ID 查询本次调整涉及的目标池
                investmentPoolMapper.queryExistingPoolIdList(poolIds);
            }
        }
        Map<Long, Integer> inboundIncrements = new HashMap<>();
        Map<Long, Map<String, List<Long>>> relationMap = batch == null
                // 构建全量投资池关系 Map
                ? buildPoolRelationMap(securityPoolAdjustMapper.queryAllPoolRelationList()) : batch.data.relations;
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
        for (IpAdjustLogBo log : logList) {
            // 识别证券 Excel 导入及清空来源，两者都仅提示业务限制
            boolean excelImport = isExcelAdjustType(log.getAdjustType())
                    || (log.getAdjustBatchNo() != null && excelBatches.contains(log.getAdjustBatchNo()));
            InvestmentPoolBo pool = poolMap.get(log.getTargetPoolId());
            if (pool == null || (!excelImport && (!"enabled".equals(pool.getStatus())
                    || Integer.valueOf(1).equals(pool.getIsDeleted())))) {
                // 抛出包含调库项上下文的最终审批复核异常
                throwApprovalRecheckFailure(log, "目标投资池不存在或已停用");
            }
            SecurityInfoBo security = batch == null ? null : batch.data.securities.get(log.getSecurityCode());
            if (security == null || CreditBondSpecialInboundRule.isAbs(security)) {
                // ABS 复核继续使用当前主档及申请快照
                security = securityPoolAdjustMapper.querySecurityBoByCode(log.getSecurityCode());
            }
            if (security == null) {
                // 抛出包含调库项上下文的最终审批复核异常
                throwApprovalRecheckFailure(log, "证券不存在");
            }
            // 普通申请送审后可能摘牌，Excel 沿用业务提示放行口径
            if (!excelImport && AdjustMode.IN.getCode().equals(log.getAdjustMode())
                    && RelatedMarketAdjustHelper.isExpiredBond(security, securityPoolAdjustMapper::queryCategoryTypeBySecurityType)) {
                throwApprovalRecheckFailure(log, RelatedMarketAdjustHelper.BOND_EXPIRED + "，请重新校验调入清单");
            }
            if (log.getId() != null && CreditBondSpecialInboundRule.isAbs(security)) {
                absIdentityService.restoreSnapshotSelection(security, log, false);
            }
            if (log.getId() == null && CreditBondSpecialInboundRule.isAbs(security)) {
                // 批量预检没有申请主体快照，实际提交创建快照后仍执行完整最终复核
                continue;
            }
            if (excelImport) {
                if (batch != null && batch.excelBulk) {
                    // Excel 提交和清空入口已集中复核本批冲突，直通 ABS 只需要复核身份
                    continue;
                }
                // 沿父级关系查找目标池所属的顶级投资池
                Long rootPoolId = resolveRootPoolId(log.getTargetPoolId(), poolMap);
                List<Long> pendingPools = securityPoolAdjustMapper.queryPendingManualTargetPoolIdList(
                        log.getSecurityCode(), log.getAdjustBatchNo());
                // 将目标池列表转换为顶级投资池组 ID 集合
                if (resolvePoolGroupIds(pendingPools, poolMap).contains(rootPoolId)) {
                    // 抛出包含调库项上下文的最终审批复核异常
                    throwApprovalRecheckFailure(log, SecurityPoolCheckRules.PENDING_PROCESS_FAILURE);
                }
                if (AdjustMode.IN.getCode().equals(log.getAdjustMode())
                        && securityPoolAdjustMapper.querySecurityCurrentPoolIdList(log.getSecurityCode())
                        .contains(log.getTargetPoolId())) {
                    // 抛出包含调库项上下文的最终审批复核异常
                    throwApprovalRecheckFailure(log, SecurityPoolCheckRules.ALREADY_IN_POOL_FAILURE);
                }
                continue;
            }
            Set<Long> currentPoolIds = independent
                    ? batch.data.activePools.getOrDefault(log.getSecurityCode(), Collections.emptySet())
                    : new HashSet<>(securityPoolAdjustMapper.querySecurityCurrentPoolIdList(log.getSecurityCode()));
            AdjustCheckContext ctx = new AdjustCheckContext();
            if (independent) {
                ctx.setBatchData(batch.data);
            }
            ctx.setSecurityInfo(security);
            ctx.setTargetPool(pool);
            ctx.setPoolMap(poolMap);
            ctx.setCurrentPoolIds(currentPoolIds);
            ctx.setTargetPoolRelations(relationMap.getOrDefault(pool.getId(), Collections.emptyMap()));
            String securityType = security.getSecurityType();
            if (independent && securityType != null) {
                if (!batch.categories.containsKey(securityType)) {
                    // 批量索引未找到旧品种码时按数据库的比较规则查一次
                    batch.categories.put(securityType, securityPoolAdjustMapper.queryCategoryTypeBySecurityType(securityType));
                }
                ctx.setCategoryType(batch.categories.get(securityType));
            } else if (!independent) {
                ctx.setCategoryType(securityPoolAdjustMapper.queryCategoryTypeBySecurityType(securityType));
            }
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
                int currentCount = independent ? batch.data.counts.getOrDefault(pool.getId(), 0)
                        : securityPoolAdjustMapper.queryPoolCurrentCount(pool.getId());
                int increment = inboundIncrements.getOrDefault(pool.getId(), 0);
                if (failure == null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                        && currentCount + increment >= pool.getMaxCapacity()) {
                    failure = "目标投资池容量已满";
                }
                inboundIncrements.put(pool.getId(), increment + 1);
            } else {
                ctx.setTargetPoolEntryTime(securityPoolAdjustMapper.queryPoolEntryTime(
                        log.getSecurityCode(), pool.getId()));
                // 规则：目标池已锁定（lock_flag=1），再规则：证券是否不在目标池中
                failure = firstFailure(outCheckPoolLocked(ctx), outCheckSecurityNotInPool(ctx),
                        // 规则：调出限制池（out_restrict），再规则：调出冻结期（frozen_period_in）
                        outCheckRestrictPool(ctx), outCheckFrozenPeriod(ctx));
            }
            if (failure == null && PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(pool.getId())) {
                failure = CreditBondSpecialInboundRule.isAbs(security)
                        ? pledgeBlacklistRuleService.validateCompanies(batch == null
                                ? absIdentityService.queryRiskCompanyCodes(security)
                                : absIdentityService.queryRiskCompanyCodesWithCandidates(security,
                                        batch.data.ratingCompanies.getOrDefault(security.getWindCode(), Collections.emptyList())),
                                log.getAdjustMode(), projectedInPoolIds, projectedOutPoolIds)
                        : pledgeBlacklistRuleService.validate(security.getIssuerCode(), log.getAdjustMode(),
                                projectedInPoolIds, projectedOutPoolIds);
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
     * 1. 批量与 Excel 使用本次读取的状态，单券入口沿用原终审调用
     * 2. 仍按原顺序落池，已成功调入才计入后续组的容量
     * 3. 只有本次手工上传信评附件才会生成内部报告
     *
     * @param logList 本次需要立即生效的完整调库日志集合
     * @param req 调库提交请求
     * @param batch 本批提交数据
     */
    private void recheckAndApplyDirectLogs(List<IpAdjustLogBo> logList, SecurityPoolAdjustSubmitReq req,
                                          BatchSubmitData batch) {
        if (logList == null || logList.isEmpty()) {
            return;
        }
        // 1. 批量与 Excel 使用本次读取的状态，单券入口沿用原终审调用
        if (batch == null && !isExcelAdjustType(req.getAdjustType())) {
            recheckBeforeFinalApproval(logList);
        } else {
            // 从现有规则执行最终复核，独立调入复用本批数据库状态
            recheckBeforeFinalApproval(logList, batch, false);
        }
        boolean independent = batch != null && batch.independent;
        // 2. 仍按原顺序落池，已成功调入才计入后续组的容量
        if (independent) {
            // 应用状态变更，已确认整批均为普通证券时省去 ABS 查询
            applyPoolStatusChanges(logList, true);
            for (IpAdjustLogBo log : logList) {
                batch.data.counts.merge(log.getTargetPoolId(), 1, Integer::sum);
            }
        } else {
            applyPoolStatusChanges(logList);
        }
        // 3. 只有本次手工上传信评附件才会生成内部报告
        if (batch == null || req.getItems().stream().anyMatch(item -> item.getCreditReportFileIndexes() != null
                && !item.getCreditReportFileIndexes().isEmpty())) {
            reportService.addInternalReportsOnFinish(logList);
        }
    }

    /**
     * 将已完成最终复核的调库日志应用到当前池状态
     *
     * 执行顺序：
     * 1. 通用落池入口继续核对 ABS 身份
     *
     * @param logList 已通过最终复核的调库日志集合
     */
    public void applyPoolStatusChanges(List<IpAdjustLogBo> logList) {
        // 通用落池入口继续核对 ABS 身份
        applyPoolStatusChanges(logList, false);
    }

    /**
     * 应用状态变更，已确认整批均为普通证券时省去 ABS 查询
     *
     * 执行顺序：
     * 1. 全部主体一致后，在同一事务中保存通过审核的五字段
     * 2. 与最终复核使用同一来源口径，普通手工和批量调整不受影响
     * 3. 批量保存 ABS 人工结论，不给普通债逐行增加主档查询
     *
     * @param logList 同批次调库记录
     * @param ordinaryOnly 是否按普通债批量路径落池，启用时跳过 ABS 身份落档和人工联动结论记录
     */
    private void applyPoolStatusChanges(List<IpAdjustLogBo> logList, boolean ordinaryOnly) {
        // 1. 全部主体一致后，在同一事务中保存通过审核的五字段
        if (!ordinaryOnly) {
            absIdentityService.approveIdentities(logList);
        }

        // 2. 与最终复核使用同一来源口径，普通手工和批量调整不受影响
        Set<String> excelBatches = collectExcelAdjustBatches(logList);
        for (IpAdjustLogBo log : logList) {
            // 识别证券 Excel 导入及清空来源，两者都仅提示业务限制
            boolean excelImport = isExcelAdjustType(log.getAdjustType())
                    || (log.getAdjustBatchNo() != null && excelBatches.contains(log.getAdjustBatchNo()));
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
                if (deleted == 0 && !excelImport) {
                    throw new BizException("证券[" + log.getSecurityCode() + "]已不在投资池["
                            + log.getTargetPoolName() + "]，调出失败");
                }
            }

        }
        // 3. 批量保存 ABS 人工结论，不给普通债逐行增加主档查询
        if (!ordinaryOnly) {
            absCompanySyncService.recordManualDecisions(logList);
        }
    }

    /**
     * 从本次完整调库组中识别 Excel 主项批次，让配套项沿用同一放行口径
     *
     * 执行顺序：
     * 1. 收集 Excel 导入和清空主项的非空批次，配套项沿用同一放行口径
     * 2. 返回本次集合，不修改日志类型，也不增加数据库查询
     *
     * @param logs 本次直通或审批处理的完整调库组
     * @return Excel 证券导入及清空主项的批次集合
     */
    private Set<String> collectExcelAdjustBatches(List<IpAdjustLogBo> logs) {
        Set<String> batches = new HashSet<>();
        for (IpAdjustLogBo log : logs) {
            // 识别证券 Excel 导入及清空来源，两者都仅提示业务限制
            if (isExcelAdjustType(log.getAdjustType()) && log.getAdjustBatchNo() != null) {
                batches.add(log.getAdjustBatchNo());
            }
        }
        return batches;
    }

    /**
     * 识别证券 Excel 导入及清空来源，两者都仅提示业务限制
     *
     * 执行顺序：
     * 1. 直接比较调整来源，不增加数据库查询
     *
     * @param adjustType 调整来源
     * @return true=Excel 导入或清空，false=其他调整类型
     */
    private boolean isExcelAdjustType(String adjustType) {
        return EXCEL_IMPORT_ADJUST_TYPE.equals(adjustType) || AdjustType.CLEAR_POOL.getCode().equals(adjustType);
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
     * @param reason 本次校验产生的原因或提示
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
            inRestricted = securityPoolAdjustMapper.querySecurityInRestrictedPool(sec.getWindCode())
                    || queryIssuerInPoolType(sec, "restricted");
        }
        Set<Long> currentPoolIds = new HashSet<Long>();
        if (sec.getWindCode() != null) {
            List<Long> currentIdList = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(sec.getWindCode());
            if (currentIdList != null) {
                currentPoolIds.addAll(currentIdList);
            }
        }
        // 当前已在分级库的最好档（inner_sort 最小）
        Integer currentGradedSort = resolveCurrentGradedSort(currentPoolIds, poolMap);
        // 5. 改判候选项用同一套档位规则（含观察池）
        boolean inObserve = false;
        if (sec.getWindCode() != null) {
            inObserve = securityPoolAdjustMapper.querySecurityInObservePool(sec.getWindCode())
                    || queryIssuerInPoolType(sec, "observe");
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
     * 评估证券当前所在分级库是否仍符合主体债入库规则
     *
     * ABS 用主档发行人内评，担保债从当前入库快照确定当时选定的担保人再查最新内评；
     * 两者都不沿用快照里的旧评分
     *
     * 执行顺序：
     * 1. 读取当前证券和投资池，定位本条在池状态对应的入库日志
     * 2. ABS 与普通债直接用主档发行人内评，担保债沿用快照担保人代码读取该担保人最新内评
     * 3. 查询当前在池、观察及重点观察状态，复用人工调库的矩阵和特殊债规则
     *
     * @param securityCode 证券代码
     * @param targetPoolId 当前所在分级库 ID
     * @param adjustLogId 当前在池状态对应的入库日志 ID
     * @return 不符合原因；符合返回 null
     */
    public String evaluateGradedInboundForPool(String securityCode, Long targetPoolId, Long adjustLogId) {
        if (securityCode == null || securityCode.isEmpty() || targetPoolId == null) {
            return "证券或目标池为空";
        }
        SecurityInfoBo sec = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (sec == null) {
            return "证券不存在";
        }
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<Long, InvestmentPoolBo>();
        for (InvestmentPoolBo p : investmentPoolMapper.queryPoolList()) {
            poolMap.put(p.getId(), p);
        }
        InvestmentPoolBo pool = poolMap.get(targetPoolId);
        if (pool == null) {
            return "目标投资池不存在";
        }
        if (CreditBondSpecialInboundRule.isGradedLevelPool(pool)
                && !CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(sec)) {
            // 仅担保债需要按入库快照恢复当时选定的担保人
            refreshGradedInboundRatingCompany(sec, targetPoolId, adjustLogId);
        }
        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(sec);
        ctx.setTargetPool(pool);
        ctx.setPoolMap(poolMap);
        Set<Long> currentPoolIds = new HashSet<Long>();
        List<Long> currentIdList = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(securityCode);
        if (currentIdList != null) {
            currentPoolIds.addAll(currentIdList);
        }
        ctx.setCurrentPoolIds(currentPoolIds);
        ctx.setSecurityInObservePool(securityPoolAdjustMapper.querySecurityInObservePool(securityCode));
        ctx.setIssuerInObservePool(queryIssuerInPoolType(sec, "observe"));
        ctx.setSecurityInRestrictedPool(securityPoolAdjustMapper.querySecurityInRestrictedPool(securityCode));
        ctx.setIssuerInRestrictedPool(queryIssuerInPoolType(sec, "restricted"));
        // 复用人工入库规则，缺少评级主体内评时补充主体代码便于定位
        String fail = inCheckMainGradeRule(ctx);
        if ("重点观察名单已在库债券只能调入五级库或调出".equals(fail)
                // 判断五级库是否至少有一个池满足当前债券的完整入库规则
                && !hasAllowedLevelFivePool(ctx)) {
            fail = "重点观察名单已在库债券只能调出";
        }
        if ("所选权益人无内评分".equals(fail)) {
            // ABS 的评级主体存在主档发行人字段，担保债存在快照担保人字段
            String ratingCompanyCode = CreditBondSpecialInboundRule.isAbs(sec)
                    ? sec.getIssuerCode() : sec.getGuarantorId();
            return fail + "（入库评级主体：" + ratingCompanyCode + "）";
        }
        return fail;
    }

    /**
     * 判断五级库是否至少有一个池满足当前债券的完整入库规则
     *
     * 执行顺序：
     * 1. 遍历可用池并筛选五级分级库，逐个用完整规则复核
     * 2. 任一池通过即返回允许，结束时恢复原目标池
     *
     * @param ctx 调库校验上下文
     * @return true=至少有一个五级池满足完整入库规则，false=没有五级池满足完整入库规则
     */
    private boolean hasAllowedLevelFivePool(AdjustCheckContext ctx) {
        if (ctx == null || ctx.getPoolMap() == null) {
            return false;
        }
        InvestmentPoolBo originalPool = ctx.getTargetPool();
        try {
            for (InvestmentPoolBo pool : ctx.getPoolMap().values()) {
                if (!CreditBondSpecialInboundRule.isGradedLevelPool(pool)
                        || !Integer.valueOf(CreditBondSpecialInboundRule.MAX_LEVEL).equals(pool.getInnerSort())) {
                    continue;
                }
                ctx.setTargetPool(pool);
                // 规则：主体债入库规则（credit_bond_pool_grade_rule 矩阵）
                if (inCheckMainGradeRule(ctx) == null) {
                    return true;
                }
            }
            return false;
        } finally {
            ctx.setTargetPool(originalPool);
        }
    }

    /**
     * 复核在池债券的评级主体取值：ABS 与普通债直接用主档发行人内评，仅担保债取快照选定担保人的最新内评
     *
     * ABS 确认权益人时已把该主体固定到主档发行人字段且不可更换，主档发行人内评就是唯一评级主体评分，
     * 既不回查入库快照，也不借用担保人字段承载权益人信息
     *
     * 执行顺序：
     * 1. ABS 与非担保债无需调整，主档发行人字段就是唯一评级主体
     * 2. 担保债按精确入库日志定位当时选定的单一担保人，再查该担保人最新内评
     *
     * @param sec 当前证券主档
     * @param targetPoolId 当前分级库 ID
     * @param adjustLogId 当前在池状态对应的入库日志 ID
     */
    private void refreshGradedInboundRatingCompany(SecurityInfoBo sec, Long targetPoolId, Long adjustLogId) {
        // 1. ABS 和非担保债继续使用原发行人信息
        if (CreditBondSpecialInboundRule.isAbs(sec) || !CreditBondSpecialInboundRule.isGuaranteed(sec)) {
            return;
        }
        // 2. 按精确调库日志定位获批担保人，读取最新内评
        String context = "证券[" + sec.getWindCode() + "]当前池[" + targetPoolId
                + "]入库日志[" + adjustLogId + "]";
        // 从精确调库快照恢复担保债选定的担保人，并读取最新内评
        refreshSnapshotRatingCompany(sec, adjustLogId, context);
    }

    /**
     * 从本笔申请快照恢复 ABS 权益人，并读取该权益人最新内评
     *
     * ABS 首次入库通过前主档尚未固定主体，改判只能读本笔申请快照；
     * 评级主体存放在发行人字段，不读担保人字段
     *
     * 执行顺序：
     * 1. 校验来源日志、申请快照及其中唯一的权益人代码
     * 2. 查询该权益人最新内评，回填发行人五字段
     *
     * @param sec 当前证券主档
     * @param adjustLogId 申请所属调库记录 ID
     * @param context 用于异常定位的业务上下文
     */
    private void refreshAbsRatingCompanyFromSnapshot(SecurityInfoBo sec, Long adjustLogId, String context) {
        if (adjustLogId == null) {
            throw new BizException(context + "缺少来源入库日志，无法确定评级主体");
        }
        SecurityInfoBo snapshot = securityPoolAdjustMapper.querySecuritySnapshotDetailByAdjustLogId(
                adjustLogId, sec.getWindCode());
        if (snapshot == null) {
            throw new BizException(context + "缺少入库证券快照，无法确定评级主体");
        }
        String companyCode = snapshot.getIssuerCode();
        if (companyCode == null || companyCode.trim().isEmpty()) {
            throw new BizException(context + "入库快照缺少实际评级主体代码");
        }
        if (companyCode.contains(",")) {
            throw new BizException(context + "快照评级主体不是单一代码，无法确定实际选择");
        }
        RelatedRatingCompanyDto latest = securityPoolAdjustMapper.queryLatestRatingCompanyByCode(companyCode.trim());
        sec.setIssuer(snapshot.getIssuer());
        sec.setIssuerCode(companyCode.trim());
        sec.setRatingBondissuer(snapshot.getRatingBondissuer());
        sec.setRatingBondissuerAgency(snapshot.getRatingBondissuerAgency());
        // 最新评分为空时不回退快照旧值，由现有规则判为无内评
        sec.setInnerIssuerRating(latest == null ? null : latest.getInnerRating());
    }

    /**
     * 从精确调库快照恢复担保债选定的担保人，并读取最新内评
     *
     * 担保债可能对应多个担保人，主档 guarantor_id 是逗号分隔的多值串，
     * 只能回到快照取当时批准的那一个。本方法只处理担保债，ABS 与普通债由调用方提前排除
     *
     * 执行顺序：
     * 1. 非担保债直接返回，沿用发行主体评分口径
     * 2. 校验精确快照及其中的单一担保人代码
     * 3. 查询该担保人最新评分，仅回填本次内存对象
     *
     * @param sec 当前证券主档
     * @param adjustLogId 快照所属调库记录 ID
     * @param context 用于异常定位的业务上下文
     */
    private void refreshSnapshotRatingCompany(SecurityInfoBo sec, Long adjustLogId, String context) {
        // 1. 仅处理担保债的评级主体
        if (!CreditBondSpecialInboundRule.isGuaranteed(sec)) {
            return;
        }
        // 2. 读取准确的入库快照并校验其中选定的担保人代码
        if (adjustLogId == null) {
            throw new BizException(context + "缺少来源入库日志，无法确定评级主体");
        }
        SecurityInfoDetailDto snapshot = securityPoolAdjustMapper.querySecuritySnapshotDetailByAdjustLogId(
                adjustLogId, sec.getWindCode());
        if (snapshot == null) {
            throw new BizException(context + "缺少入库证券快照，无法确定评级主体");
        }
        String companyCode = snapshot.getGuarantorId();
        if (companyCode == null || companyCode.trim().isEmpty()) {
            throw new BizException(context + "入库快照缺少实际评级主体代码");
        }
        if (companyCode.contains(",")) {
            throw new BizException(context + "快照评级主体不是单一代码，无法确定实际选择");
        }
        // 3. 查询该担保人的最新内评并回填
        RelatedRatingCompanyDto latest = securityPoolAdjustMapper.queryLatestRatingCompanyByCode(companyCode.trim());
        // 保留实际主体身份，最新评分为空时不回退旧评分，由现有规则判定
        fillSelectedRatingCompany(sec, companyCode.trim(), snapshot.getGuarantor(),
                latest == null ? null : latest.getInnerRating());
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
     * 1. 读取目标池的基金评分表达式，用本次申请的基金评分核对准入限制
     *
     * @param ctx 调库校验上下文
     * @return 基金评分未通过时的阻断原因，通过时为空
     */
    private String inCheckFundRate(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckFundRate(ctx);
    }

    /**
     * 调出校验：通用校验 + 类型特有校验
     *
     * 先执行 checkCommonOut（通用：池锁定/pending/不在池/冻结期/限制/互斥/弹性），
     * 再按 categoryType 路由类型特有校验（债券到期 checkBondOut / 股票退市 checkStockOut 等）
     *
     * 执行顺序：
     * 1. 依次检查通用调出条件和证券品种专属规则，收集阻断原因
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkOutConditions(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().checkOutConditions(ctx);
    }

    /**
     * 单券进入或退出黑名单质押库时，按发行主体复核三条件
     *
     * 执行顺序：
     * 1. 获取规则组件，直接构造服务时沿用显式注入的依赖
     *
     * @param failures 校验失败原因
     * @param ctx 调库校验上下文
     * @param adjustMode 调整方向
     */
    private void addPledgeBlacklistFailure(List<String> failures, AdjustCheckContext ctx, String adjustMode) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        checkRules().addPledgeBlacklistFailure(failures, ctx, adjustMode);
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 前置校验规则
    // ═══════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 调入校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：证券是否已在目标池中
     *
     * 以 ip_pool_status 中 audit_status='20' 的有效记录为准，重复调入无实际意义
     *
     * 执行顺序：
     * 1. 比较目标池 ID 与证券当前已生效的所在池，命中时返回重复调入提示
     *
     * @param ctx 调库校验上下文
     * @return 组合已在池的提示，未命中时为 null
     */
    private String inCheckSecurityAlreadyInPool(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckSecurityAlreadyInPool(ctx);
    }

    /**
     * 规则：来源池限制
     *
     * 若目标池配置了来源池（source），则证券必须当前已在其中至少一个来源池中，
     * 或本次请求同时调入至少一个来源池，满足"从低级库向高级库晋升"等分层调库规则
     * 未配置来源池时不做限制
     *
     * 执行顺序：
     * 1. 用当前所在池及本次拟调入池核对目标池设置的来源范围
     *
     * @param ctx 调库校验上下文
     * @return 未满足来源池要求的提示，符合时为 null
     */
    private String inCheckSourcePool(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckSourcePool(ctx);
    }

    /**
     * 规则：调入限制池（in_restrict）
     *
     * 证券当前在配置的限制池中，则不允许调入目标池；开关开启时，非 ABS、担保人内评为 1 档的强担保债仅豁免重点观察名单池
     *
     * 执行顺序：
     * 1. 检查当前在池记录是否命中目标池设置的调入限制
     *
     * @param ctx 调库校验上下文
     * @return 调入限制提示，未命中时为 null
     */
    private String inCheckRestrictPool(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckRestrictPool(ctx);
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
     * 1. 核对证券是否已有目标池生效记录，缺少时给出无法调出原因
     *
     * @param ctx 调库校验上下文
     * @return 组合不在池的提示，在池时为 null
     */
    private String outCheckSecurityNotInPool(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().outCheckSecurityNotInPool(ctx);
    }

    /**
     * 规则：调出限制池（out_restrict）
     *
     * 证券当前在配置的限制池中，则不允许从目标池调出
     *
     * 执行顺序：
     * 1. 读取目标池配置的调出限制，与证券当前所在池比对并返回阻断原因
     *
     * @param ctx 调库校验上下文
     * @return 调出限制提示，未命中时为 null
     */
    private String outCheckRestrictPool(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().outCheckRestrictPool(ctx);
    }

    /**
     * 规则：目标池已锁定（lock_flag=1）
     *
     * 池被锁定后不可调入，属于最硬的池级拦截，优先于其他调入校验执行
     *
     * 执行顺序：
     * 1. 读取目标投资池的锁定标志，命中时返回池已锁定的提示
     *
     * @param ctx 调库校验上下文
     * @return 池锁定提示，未锁定时为 null
     */
    private String inCheckPoolLocked(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckPoolLocked(ctx);
    }

    /**
     * 规则：目标池已锁定（lock_flag=1）
     *
     * 池被锁定后不可调出，属于最硬的池级拦截，优先于其他调出校验执行
     *
     * 执行顺序：
     * 1. 读取目标投资池的锁定标志，命中时返回池已锁定的提示
     *
     * @param ctx 调库校验上下文
     * @return 池锁定提示，未锁定时为 null
     */
    private String outCheckPoolLocked(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().outCheckPoolLocked(ctx);
    }

    /**
     * 规则：调出冻结期（frozen_period_in）
     *
     * 目标池配置了调入冻结期天数时，证券入池后 N 天内不可调出
     * 以 ip_pool_status.entry_time（audit_status=20）为入池时间基准，
     * entry_time + frozenPeriodIn 天 &gt; 当前时间则视为仍在冻结期
     *
     * 执行顺序：
     * 1. 根据入池时间与目标池冻结期，判断该证券当天能否调出
     *
     * @param ctx 调库校验上下文
     * @return 入池时间缺失或仍在冻结期的提示，符合时为 null
     */
    private String outCheckFrozenPeriod(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().outCheckFrozenPeriod(ctx);
    }

    /**
     * 规则：投资品种（variety_codes）
     *
     * 目标池配置了投资品种时，证券品种（categoryType）须在配置内
     * categoryType 由 dict_security_type 表按 securityType 查询得到（大类见 CategoryType / 演示数据）
     *
     * 执行顺序：
     * 1. 比较证券品种与目标池配置允许的品种范围，超出时返回原因
     *
     * @param ctx 调库校验上下文
     * @return 品种不在允许范围的提示，符合时为 null
     */
    private String inCheckVariety(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckVariety(ctx);
    }

    /**
     * 规则：全局禁止池（forbidden 禁投池）
     *
     * 证券当前在禁投池中则不能调入任何其他池（全局禁止，区别于池间 in_restrict）
     * 对应老项目 Forbiddenlastpoolid 配置
     *
     * 执行顺序：
     * 1. 用当前生效的在池记录判断证券是否命中全局禁止池
     *
     * @param ctx 调库校验上下文
     * @return 禁投提示，未命中时为 null
     */
    private String inCheckForbiddenPool(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckForbiddenPool(ctx);
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
     * 1. 核对证券行业与目标池所配行业及指数限制，未通过时返回提示
     *
     * @param ctx 调库校验上下文
     * @return 行业不一致的提示，符合或未检查时为 null
     */
    private String inCheckIndustry(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckIndustry(ctx);
    }

    /**
     * 规则：开放日（open_day_adjust，调入侧）
     *
     * 目标池启用开放日校验（open_day_adjust=1）时，调入校验当日是否落在 ip_pool_open_day 的开放区间内
     * 对应老项目 checkInPool:479（isInPoolOpenDay）
     *
     * 执行顺序：
     * 1. 目标池启用开放日检查时，查询当天是否落在开放区间内
     *
     * @param ctx 调库校验上下文
     * @return 非开放日提示，符合时为 null
     */
    private String inCheckOpenDay(AdjustCheckContext ctx) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().inCheckOpenDay(ctx);
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
     * @param pool 目标池
     * @param reportRestriction 目标池在当前调整方向的报告要求
     * @param securityCode 证券代码
     */
    private void checkReportRequired(SecurityPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool, String reportRestriction, String securityCode) {
        if (pool == null || reportRestriction == null || reportRestriction.isEmpty() || "none".equals(reportRestriction)) {
            return;
        }
        // 1. 6个月内入池报告标记（对齐老系统 bondfileflag）：同主体半年内有审批通过调入且确有报告才跳过
        if (securityCode != null && !securityCode.isEmpty() && securityPoolAdjustMapper.queryHasRecentInboundWithReport(securityCode)) {
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
     * 1. 合并当前调库项、目标池数量和本次共用的证券及池关系数据
     *
     * @param item 当前调库项（目标池 ID、调整方向等）
     * @param poolCurrentCount 目标池当前有效证券数量（调用方按需查询）
     * @param shared 本次 checkAdjust 调用的共享数据
     * @return 当前调整项的规则检查上下文
     */
    private AdjustCheckContext buildCheckContext(AdjustCheckReq.CheckItem item, int poolCurrentCount, AdjustSharedData shared) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().buildCheckContext(item, poolCurrentCount, shared);
    }

    /**
     * 组装提交阶段配套互斥调出失败的异常文案（总述 + 编号原因）
     *
     * 执行顺序：
     * 1. 写入配套互斥项对应池名
     * 2. 将每条实际失败原因按序号追加到异常文案
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
     * 构建全量投资池关系 Map
     *
     * 结构：poolId → relationType → 关联池 ID 列表，三层嵌套以支持快速按（池ID + 关系类型）查询
     *
     * 执行顺序：
     * 1. 以池 ID 和关系类型为两级索引，归集各配置对应的关联池 ID
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
     * 1. 使用新日志、批次及流程快照建立初始步骤，缺省步骤列表时按默认方式持久化
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
        // 生成原流程初始步骤，直通批量提交先收集步骤
        return createInitialSteps(adjustLogId, adjustBatchNo, snapshot, adjusterId, adjusterName, null);
    }

    /**
     * 生成原流程初始步骤，直通批量提交先收集步骤
     *
     * 执行顺序：
     * 1. 查找开始节点
     * 2. 创建开始节点步骤（auto_process）
     * 3. 插入单条步骤记录到 ip_adjust_step
     * 4. 查找初始步骤的下一个节点
     *
     * @param adjustLogId 本笔申请的调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param snapshot 流程快照
     * @param adjusterId 提交人 ID
     * @param adjusterName 提交人名称
     * @param steps 待处理的调库审批步骤列表
     * @return true=流程初始化时已经到达结束节点，false=流程初始化后尚未到达结束节点
     */
    private boolean createInitialSteps(Long adjustLogId, String adjustBatchNo, FlowSnapshot snapshot,
                                       String adjusterId, String adjusterName, List<IpAdjustStepBo> steps) {
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
                if (steps == null) {
                    // 为审批节点按处理人展开待处理步骤
                    createPendingSteps(adjustLogId, adjustBatchNo, currentNode, snapshot, now);
                }
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
     * 1. 根据当前节点策略和来路判断应否由发起人自动完成此步
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
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().isInitiatorStep(snapshot, node, config, prevNode, startNode);
    }

    /**
     * 为审批节点创建待处理步骤记录（按处理人明细展开为具体人员）
     *
     * 执行顺序：
     * 1. 将审批处理人配置解析为具体人员
     * 2. 只排除本批次真正审核过的人，待办和抢占跳过的人仍可进入下一节点
     *
     * @param adjustLogId 本笔申请的调库记录 ID
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
                             null, null, null, null, now, null);
            return;
        }
        // 2. 只排除本批次真正审核过的人，待办和抢占跳过的人仍可进入下一节点
        handlers = excludeParticipatedHandlers(adjustLogId, adjustBatchNo, handlers);
        for (HandlerTarget handler : handlers) {
            // 插入单条步骤记录到 ip_adjust_step
            insertStepRecord(adjustLogId, adjustBatchNo, node, config, sortOrder, StepStatus.PENDING.getCode(),
                             handler.handlerId, handler.handlerName, null, null, now, null);
        }
    }

    /**
     * 排除本批次真正审核过的人；配置非空但剔光时抛业务异常
     *
     * 执行顺序：
     * 1. 按批次列表查询调库审批步骤
     * 2. 排除本批次真正审核过的人
     *
     * @param adjustLogId 本笔申请的调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param handlers 下一节点配置的候选处理人
     * @return 排除已参与人员后的可用处理人
     */
    private List<HandlerTarget> excludeParticipatedHandlers(Long adjustLogId, String adjustBatchNo,
                                                            List<HandlerTarget> handlers) {
        List<IpAdjustStepBo> existingSteps = securityPoolAdjustMapper.queryAdjustStepByBatchList(
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
     * @param adjustLogId 本笔申请的调库记录 ID
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
            securityPoolAdjustMapper.addAdjustStep(step);
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
     * 1. 关联码项使用 item 级证券代码，保证独立落 log / 池状态
     * 2. 兜底：关联项未带简称/类型时按代码回查主数据
     * 3. 根据调库项来源确定落表调整类型
     * 4. 同一次提交共用统一提交时间，避免大批量逐条 NOW() 导致历史排序同组打散
     *
     * @param req 调库申请（含证券级别信息）
     * @param item 单个调库项（含目标池、调整方向等）
     * @param flowSource 同组手工调库项，用于写入流程快照
     * @param shared 已完成前置检查的独立提交上下文
     * @return 待保存的 CRMW 调库日志
     */
    private IpAdjustLogBo buildAdjustLog(SecurityPoolAdjustSubmitReq req,
                                         SecurityPoolAdjustSubmitReq.AdjustItem item,
                                         SecurityPoolAdjustSubmitReq.AdjustItem flowSource,
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
        if (ItemType.RELATED.getCode().equals(item.getItemTag())) {
            // 与临时代码转正共用关联记录构建，保留同组流程和批次信息
            return RelatedMarketAdjustHelper.buildRelatedLog(bo, securityCode, securityShortName, securityType);
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
     * @param adjustLogId 本笔申请的调库记录 ID
     * @param item 调库项
     * @param submissionFiles 已创建的附件上下文（可为整批共享）
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
     * 1. 互斥、联动和跨市场关联项使用各自的来源类型，其他调整项沿用请求类型
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
        if (ItemType.RELATED.getCode().equals(item.getItemTag())) {
            return RelatedMarketAdjustHelper.ADJUST_TYPE;
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
    private String resolveItemSecurityCode(SecurityPoolAdjustSubmitReq req,
                                           SecurityPoolAdjustSubmitReq.AdjustItem item) {
        if (item != null && item.getSecurityCode() != null && !item.getSecurityCode().isEmpty()) {
            return item.getSecurityCode();
        }
        return req.getSecurityCode();
    }

    /**
     * 从 Wind 同 ISIN 记录解析关联证券代码列表（已带后缀、去自身、去重，不递归）
     *
     * 执行顺序：
     * 1. 按 Wind 同 ISIN 候选取已带市场后缀的证券代码，去掉主券和重复代码
     *
     * @param main 证券主档信息
     * @return 本次涉及的同券跨市场代码列表
     */
    private List<String> resolveRelatedSecurityCodes(SecurityInfoBo main) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().resolveRelatedSecurityCodes(main);
    }

    /**
     * 多市场关联码扩批：对主结果中的 manual/linkage/mutex 项，按 Wind 同 ISIN 记录展开 related 项
     *
     * 关联码独立校验已在池/不在池/pending/容量等；同池同批调入累计 projected 占用容量
     *
     * 执行顺序：
     * 1. 获取规则组件，直接构造服务时沿用显式注入的依赖
     *
     * @param req 调库校验请求
     * @param mainShared 主标的的校验共用数据
     * @param resultItems 前四阶段的校验结果
     */
    private void expandRelatedMarketCodeItems(AdjustCheckReq req, AdjustSharedData mainShared,
                                              List<AdjustCheckDto.CheckResultItem> resultItems) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        checkRules().expandRelatedMarketCodeItems(req, mainShared, resultItems, false);
    }

    /**
     * 构建投资池全路径名称
     *
     * 执行顺序：
     * 1. 沿父池关系取得各级投资池名称，形成目标池的全路径
     *
     * @param poolId 目标投资池 ID
     * @param poolMap 已加载的投资池集合
     * @return 投资池全路径名称
     */
    private String buildPoolPath(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        // 获取规则组件，直接构造服务时沿用显式注入的依赖
        return checkRules().buildPoolPath(poolId, poolMap);
    }

    // ═══════════════════════════════════════════════════════════
    //  内部数据类
    // ═══════════════════════════════════════════════════════════

    /**
     * 待处理步骤的具体处理人
     */
    private static class HandlerTarget {
        /** 审批处理人的用户 ID */
        final String handlerId;

        /** 审批处理人的显示名称 */
        final String handlerName;

        /**
         * 记录证券调库候选审批人
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
     *
     * 不包含 requestInPoolIds / requestOutPoolIds（提交阶段无需互斥冲突校验）
     * 新增 flowSnapshotMap，包含每个引用流程的快照数据
     *
     */
    private static class SubmitSharedData {

        /** 本次实际证券日志，关联市场快照使用各自代码 */
        final Map<Long, IpAdjustLogBo> submittedLogs = new HashMap<>();

        /** 本次提交的材料豁免缓存，以手工主项为键，同组配套项共用，与所选流程无关 */
        final Map<SecurityPoolAdjustSubmitReq.AdjustItem, Boolean> materialExemptionMap = new HashMap<>();

        /** 流程 Key 已解析的 ID，保留快照缺失时的原流程身份，避免误当成无流程 */
        final Map<String, Long> resolvedFlowIdsByKey = new HashMap<>();

        /** 证券基础信息（来自 rrs_securityinfo） */
        final SecurityInfoBo securityInfo;

        /** 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称 */
        final Map<Long, InvestmentPoolBo> poolMap;

        /** 证券当前有效所在池 ID 集合（ip_pool_status.audit_status='20'） */
        final Set<Long> currentPoolIds;

        /** 全量投资池关系配置（poolId → relationType → 关联池 ID 列表） */
        final Map<Long, Map<String, List<Long>>> poolRelationMap;

        /** 流程快照索引（flowId → FlowSnapshot），供第三/四阶段快速判断直通流程 */
        final Map<Long, FlowSnapshot> flowSnapshotMap;

        /** 批次号生成上下文 */
        final BatchNoContext batchNoContext;

        /** 本次后端校验后的普通权益人、自选权益人留痕信息 */
        final SelectedRatingCompanyData selectedRatingCompany;

        /** 调库分组批次号索引（adjustGroupKey → adjustBatchNo），用于联动/互斥记录复用 */
        final Map<String, String> adjustBatchNoMap = new HashMap<>();

        /**
         * 保存本次证券调库各申请项共用的数据
         *
         * 执行顺序：
         * 1. 保存证券资料、池配置、当前池和池关系，以及流程快照、批次号上下文和选中的评级主体
         *
         * @param securityInfo 本组提交使用的证券主数据
         * @param poolMap 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称
         * @param currentPoolIds 证券当前所在的池 ID
         * @param poolRelationMap 全量投资池关系配置（poolId → relationType → 关联池 ID 列表）
         * @param flowSnapshotMap 按流程 ID 整理的流程快照
         * @param batchNoContext 本次提交共用的批次号上下文
         * @param selectedRatingCompany 本次后端校验后的普通权益人、自选权益人留痕信息
         */
        SubmitSharedData(SecurityInfoBo securityInfo,
                         Map<Long, InvestmentPoolBo> poolMap,
                         Set<Long> currentPoolIds,
                         Map<Long, Map<String, List<Long>>> poolRelationMap,
                         Map<Long, FlowSnapshot> flowSnapshotMap,
                         BatchNoContext batchNoContext,
                         SelectedRatingCompanyData selectedRatingCompany) {
            this.securityInfo = securityInfo;
            this.poolMap = poolMap;
            this.currentPoolIds = currentPoolIds;
            this.poolRelationMap = poolRelationMap;
            this.flowSnapshotMap = flowSnapshotMap;
            this.batchNoContext = batchNoContext;
            this.selectedRatingCompany = selectedRatingCompany;
        }
    }

    /**
     * 保存 Excel 调库一组证券的提交数据
     *
     * 将来源行或清空组申请与已核实的数据一起保存，直通时收集待批量写入的日志，其他流程按组提交
     */
    static final class ExcelSubmitGroup {
        /** 当前导入行或清空组的证券调库申请 */
        final SecurityPoolAdjustSubmitReq request;

        /** 本组已准备的证券、投资池和流程数据 */
        private final SubmitSharedData shared;

        /** 本次 Excel 提交各组共用的数据 */
        private final BatchSubmitData batch;

        /** 可直通时批量写入的调库行，需逐组处理时为空 */
        List<ExcelSubmitRow> rows;

        /** 是否可按普通证券方式合并写入池状态 */
        boolean ordinary;

        /**
         * 保存 Excel 调库一组证券的提交数据
         *
         * 执行顺序：
         * 1. 保存本组申请、该组共用的资料和整批共用的提交数据
         *
         * @param request 本组证券的调库提交请求
         * @param shared 本组各调整项共用的数据
         * @param batch 同批 Excel 调库共用的提交数据
         */
        ExcelSubmitGroup(SecurityPoolAdjustSubmitReq request, SubmitSharedData shared, BatchSubmitData batch) {
            this.request = request;
            this.shared = shared;
            this.batch = batch;
        }
    }

    /**
     * 保存一行待批量写入的证券调库记录
     *
     * 将调库日志和对应的初始审批步骤放在一起，供直通批量写入
     */
    static final class ExcelSubmitRow {
        /** 当前行的证券调库日志 */
        final IpAdjustLogBo log;

        /** 随当前日志写入的审批步骤 */
        final List<IpAdjustStepBo> steps;

        /**
         * 记录一行 Excel 证券调库待批量保存的数据
         *
         * 执行顺序：
         * 1. 保存行对应的调库日志及审批步骤
         *
         * @param log 本行生成的证券调库日志
         * @param steps 本行调库日志对应的审批步骤
         */
        ExcelSubmitRow(IpAdjustLogBo log, List<IpAdjustStepBo> steps) {
            this.log = log;
            this.steps = steps;
        }
    }

    /**
     * 保存批量证券调库在本次事务中共用的数据
     *
     * 复用证券主档、池状态和流程快照，按普通独立调入或 Excel 批量提交的需要准备复核数据
     */
    private static class BatchSubmitData {
        /** 已读取的证券、池配置及独立调入状态 */
        final BatchSecurityCheckData data = new BatchSecurityCheckData();

        /** 已解析的流程 Key */
        final Map<String, Long> flowIds = new HashMap<>();

        /** 按流程定义 ID 缓存的快照 */
        final Map<Long, FlowSnapshot> flows = new HashMap<>();

        /** 品种到大类的映射，匹配旧数据中的大小写差异 */
        final Map<String, String> categories = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        /** 本批证券的在途流程占用池 */
        final Map<String, List<Long>> pendingPools = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        /** 各证券只调入一次并共用本批读取的状态 */
        boolean independent;

        /** Excel 只为仍需复核实际来源的产品快速资格读在池状态 */
        boolean excelBulk;
    }

    /**
     * 保存证券调库快照需要留痕的权益人名称
     *
     * 记录来自当前证券关系的普通权益人，以及从全市场主体中选择的自选权益人
     */
    public static class SelectedRatingCompanyData {

        /** 当前证券关系中的普通权益人名称 */
        private String rightsHolderName;

        /** 用户从全市场主体中选择的自选权益人名称 */
        private String selfSelectedRightsHolderName;

        /**
         * 获取本次选择的权益人名称
         *
         * 执行顺序：
         * 1. 读取业务快照中已保存的权益人显示名称
         *
         * @return 已保存的权益人名称
         */
        public String getRightsHolderName() {
            return rightsHolderName;
        }

        /**
         * 获取本次选择的自选权益人名称
         *
         * 执行顺序：
         * 1. 读取业务快照中已保存的自选权益人显示名称
         *
         * @return 已保存的自选权益人名称
         */
        public String getSelfSelectedRightsHolderName() {
            return selfSelectedRightsHolderName;
        }
    }

    /**
     * 批次号生成上下文
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

}
