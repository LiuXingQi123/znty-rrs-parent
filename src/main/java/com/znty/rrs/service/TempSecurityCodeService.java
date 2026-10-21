package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.common.enums.TempOprtSource;
import com.znty.rrs.common.enums.WindCodeChangeResult;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时代码管理服务，处理临时代码新增、查询、更新正式证券、取消发行和删除
 */
@Service
public class TempSecurityCodeService {

    /** 临时证券 ID 前缀 */
    private static final String TEMP_SEC_ID_PREFIX = "TEMP_";

    /** 统一证券市场查询组件。 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 临时代码转正业务转换组件，统一处理普通池和 CRMW。 */
    @Resource
    private TempSecurityCodeBusinessConverter businessConverter;

    /** 临时代码管理数据访问组件 */
    @Resource
    private TempSecurityCodeMapper tempSecurityCodeMapper;

    /** Wind 事件、主档及转换登记访问组件。 */
    @Resource
    private WindCodeSyncMapper windCodeSyncMapper;

    /**
     * 分页查询临时代码列表
     */
    public PageResult<TempSecurityCodeDto> queryTempSecurityCodePage(TempSecurityCodeReq req) {
        if (req == null) {
            req = new TempSecurityCodeReq();
        }
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        // 查询临时代码分页列表
        List<TempSecurityCodeDto> list = tempSecurityCodeMapper.queryTempSecurityCodePage(req);
        PageInfo<TempSecurityCodeDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询新增页面下拉选项
     */
    public TempSecurityCodeDto.OptionBundle queryTempSecurityCodeOptions(TempSecurityCodeReq req) {
        if (req == null) {
            req = new TempSecurityCodeReq();
        }
        TempSecurityCodeDto.OptionBundle bundle = new TempSecurityCodeDto.OptionBundle();
        // 查询发行主体下拉选项
        bundle.setCompanies(tempSecurityCodeMapper.queryCompanyOptionList(req));
        // 查询证券类型下拉选项
        bundle.setSecurityTypes(tempSecurityCodeMapper.querySecurityTypeList());
        return bundle;
    }

    /**
     * 新增临时代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto addTempSecurityCode(TempSecurityCodeReq req) {
        // 校验新增请求参数
        validateAddReq(req);
        Date now = new Date();
        // 查询发行主体并生成快照
        TempSecurityCodeDto.CompanyOption company = tempSecurityCodeMapper.queryCompanyByCode(req.getTempCompanyCode());
        if (company == null) {
            throw new BizException("发行主体不存在，tempCompanyCode=" + req.getTempCompanyCode());
        }
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityName(req.getTempSecurityName());
        bo.setTempSecurityCode(req.getTempSecurityCode());
        bo.setSecId(TEMP_SEC_ID_PREFIX + req.getTempSecurityCode());
        bo.setTempSecurityMarket(req.getTempSecurityMarket());
        bo.setTempSecurityType(req.getTempSecurityType());
        bo.setTempMitigationCode(req.getTempMitigationCode());
        bo.setTempCompanyCode(req.getTempCompanyCode());
        // 解析主体展示名称
        bo.setTempCompanyNameSnapshot(resolveCompanyName(company));
        bo.setTempIssueDate(req.getTempIssueDate());
        bo.setTempMaturityDate(req.getTempMaturityDate());
        bo.setStatus(TempStatus.TEMPORARY.getCode());
        // 页面人工新增
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setIsDeleted(0);
        bo.setCrteTime(now);
        bo.setUpdtTime(now);
        tempSecurityCodeMapper.addTempSecurityCode(bo);
        // 同步临时代码证券主数据
        addTempSecurityInfo(bo);
        // 查询新增后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 远程查询正式证券选项（转正选券，代码/名称模糊，最多 50 条）
     */
    public List<TempSecurityCodeDto.FormalSecurityOption> queryFormalSecurityOptionList(TempSecurityCodeReq req) {
        if (req == null) {
            req = new TempSecurityCodeReq();
        }
        // 查询正式证券远程选项
        List<TempSecurityCodeDto.FormalSecurityOption> options = tempSecurityCodeMapper.queryFormalSecurityOptionList(req);
        List<String> codes = new ArrayList<>();
        for (TempSecurityCodeDto.FormalSecurityOption option : options) {
            codes.add(option.getSecurityCode());
        }
        Map<String, String> markets = securityMarketService.queryMarketCodeMap(codes);
        for (TempSecurityCodeDto.FormalSecurityOption option : options) {
            option.setSecurityMarket(markets.get(option.getSecurityCode()));
        }
        return options;
    }

    /**
     * 更新临时代码为正式证券。
     * 对齐老系统 TempSecurityInfoTableAction#refreshTempSecurity 分叉逻辑：
     *
     *   - 普通池流程中调库日志（audit_status=00/11）：替换主项并补同券市场关联项，沿用原审批
     *   - 普通池已在池状态（audit_status=20）：每个池「临时出库 +（正式组未在池则）整组入库」
     *   - CRMW 池在同一凭证下按标的全部市场代码转换，流程中沿用原审批
     *   - 池/日志上 CRMW 字段指向临时码：仅字段替换
     *
     * 临时证券信息只读；正式证券必须从主数据远程选择。
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToUpdated(TempSecurityCodeReq req) {
        // 校验更新请求参数
        validateUpdateReq(req);
        // 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(req.getId());
        // 校验正式证券存在且非临时代码占位
        TempSecurityCodeDto.FormalSecurityOption formalSecurity = queryRequiredFormalSecurity(req.getSecurityCode());
        if (formalSecurity.getSecurityCode().equals(oldBo.getTempSecurityCode())) {
            throw new BizException("正式证券代码不能与临时代码相同");
        }
        // 使用人工来源执行统一转正分叉
        return editTempSecurityCodeToUpdated(oldBo, formalSecurity, TempOprtSource.MANUAL.getCode());
    }

    /**
     * 定时任务将已取得正式码映射的临时代码转为正式证券。
     *
     * @param id 临时代码记录 ID
     * @return 更新后的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToUpdatedByJob(Long id) {
        if (id == null) {
            throw new BizException("临时代码记录 ID 不能为空");
        }
        // 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(id);
        // 使用外部数据预先写入的正式证券代码查询权威主数据
        TempSecurityCodeDto.FormalSecurityOption formalSecurity =
                queryRequiredFormalSecurity(oldBo.getSecurityCode());
        if (formalSecurity.getSecurityCode().equals(oldBo.getTempSecurityCode())) {
            throw new BizException("正式证券代码不能与临时代码相同，id=" + id);
        }
        // 使用定时任务来源执行统一转正分叉
        return editTempSecurityCodeToUpdated(oldBo, formalSecurity, TempOprtSource.JOB.getCode());
    }

    /** 单条 Wind 换码独立事务，原地更新主档并复用普通池、CRMW 和在途申请转换。 */
    @Transactional(rollbackFor = Exception.class)
    public WindCodeChangeResult editWindCodeChangeByJob(WindCodeChangeDto change) {
        if (change == null) {
            throw new BizException("Wind 变更事件不能为空");
        }
        // 校验身份及八位日期，避免错误事件改动主档。
        validateRequired(change.getObjectId(), "Wind 事件 OBJECT_ID 不能为空");
        // SEC_ID 是唯一主档定位依据，禁止空值查询。
        validateRequired(change.getSecId(), "Wind 事件 SEC_ID 不能为空");
        // 明确原证券身份。
        validateRequired(change.getOldWindCode(), "Wind 旧代码不能为空");
        // 明确目标证券身份。
        validateRequired(change.getNewWindCode(), "Wind 新代码不能为空");
        // 严格校验变更日期，登记日期可为空但事件日期不可为空。
        parseWindDate(change.getChangeDate(), true);
        if (change.getOldWindCode().equals(change.getNewWindCode())) {
            throw new BizException("Wind 新旧代码不能相同");
        }
        String eventKey = "[wind_code_sync:" + change.getSecId() + ":" + change.getObjectId()
                + ":" + change.getOldWindCode() + ":" + change.getNewWindCode() + ":" + change.getChangeDate() + "]";
        SecurityInfoBo master = windCodeSyncMapper.querySecurityBySecIdForUpdate(change.getSecId());
        if (master == null) {
            return WindCodeChangeResult.MISSING_MASTER;
        }
        if (windCodeSyncMapper.queryCompletedWindCodeChangeCount(eventKey) > 0) {
            return WindCodeChangeResult.ALREADY_COMPLETED;
        }
        if (!change.getOldWindCode().equals(master.getWindCode())) {
            throw new BizException("Wind 旧代码与当前主档不一致，SEC_ID=" + change.getSecId()
                    + "，事件旧码=" + change.getOldWindCode() + "，当前代码=" + master.getWindCode());
        }
        String source = master.getSecuritySource();
        if (!"official".equals(source) && !"temporary".equals(source) && !"temp_converted".equals(source)) {
            throw new BizException("证券来源不支持 Wind 换码，security_source=" + source);
        }
        // 登记复用和新码主数据查询都需要明确证券类型。
        validateRequired(master.getSecurityType(), "Wind 旧主档缺少证券类型");
        if (tempSecurityCodeMapper.querySecurityInfoCount(change.getNewWindCode()) != 0) {
            throw new BizException("Wind 新代码已存在，违反换码数据约束，securityCode=" + change.getNewWindCode());
        }
        if ("temp_converted".equals(source)
                && windCodeSyncMapper.queryConvertedRegistrationList(change.getOldWindCode()).isEmpty()) {
            throw new BizException("已转正主档缺少对应历史登记，securityCode=" + master.getWindCode());
        }
        String market = securityMarketService.queryMarketCode(master.getWindCode());
        Date now = new Date();
        // 原临时记录复用；其他来源只补登记，不新增占位主档。
        TempSecurityCodeBo registration = prepareWindRegistration(change, master, market, now, eventKey);
        String newSource = "temporary".equals(source) ? "temp_converted" : source;
        if (windCodeSyncMapper.editSecurityWindCode(change, source, newSource, market, now) != 1) {
            throw new BizException("Wind 主档更名失败，SEC_ID=" + change.getSecId());
        }
        // 新码此时已由原主档原地更名生成，继续执行既有主数据及市场校验。
        TempSecurityCodeDto.FormalSecurityOption formal = queryRequiredFormalSecurity(change.getNewWindCode());
        // 单码模式复用完整转换事务，不扩展其他市场，也不禁用已更名的主档。
        editTempSecurityCodeToUpdated(registration, formal, TempOprtSource.JOB.getCode(), true);
        return WindCodeChangeResult.UPDATED;
    }

    /** 准备 Wind 转换登记，同时保存事件完成标记；任何后续失败均随外层事务回滚。 */
    private TempSecurityCodeBo prepareWindRegistration(WindCodeChangeDto change, SecurityInfoBo master,
                                                       String market, Date now, String eventKey) {
        List<TempSecurityCodeBo> registrations = windCodeSyncMapper.queryOldCodeRegistrationList(change.getOldWindCode());
        if (registrations.size() > 1) {
            throw new BizException("Wind 旧代码存在多条有效登记，securityCode=" + change.getOldWindCode());
        }
        TempSecurityCodeBo registration;
        if (registrations.isEmpty()) {
            if ("temporary".equals(master.getSecuritySource())) {
                throw new BizException("临时占位主档缺少有效登记，securityCode=" + master.getWindCode());
            }
            registration = new TempSecurityCodeBo();
            registration.setSecId(master.getSecId());
            registration.setTempSecurityCode(master.getWindCode());
            registration.setTempSecurityName(master.getShortName() == null ? master.getFullName() : master.getShortName());
            // 缺少名称或类型时明确报错，不使用代码或默认类型补值。
            validateRequired(registration.getTempSecurityName(), "Wind 旧主档缺少证券名称");
            registration.setTempSecurityMarket(market);
            registration.setTempSecurityType(master.getSecurityType());
            registration.setTempCompanyCode(master.getIssuerCode());
            registration.setTempCompanyNameSnapshot(master.getIssuer());
            // 按真实主档日期建立登记，缺省日期保持空。
            registration.setTempIssueDate(parseWindDate(master.getFirstissueDate(), false));
            // 到期日期同样严格解析，禁止猜测日期格式。
            registration.setTempMaturityDate(parseWindDate(master.getMaturityDate(), false));
            registration.setStatus(TempStatus.TEMPORARY.getCode());
            registration.setOprtSource(TempOprtSource.JOB.getCode());
            registration.setIsDeleted(0);
            registration.setCrteTime(now);
            registration.setUpdtTime(now);
            registration.setMemo(eventKey);
            tempSecurityCodeMapper.addTempSecurityCode(registration);
            if (registration.getId() == null) {
                throw new BizException("Wind 转换登记新增失败");
            }
        } else {
            registration = registrations.get(0);
            if (!TempStatus.TEMPORARY.getCode().equals(registration.getStatus())) {
                throw new BizException("Wind 旧代码登记不是待转换状态，id=" + registration.getId());
            }
            if (registration.getSecurityCode() != null && !registration.getSecurityCode().trim().isEmpty()
                    && !change.getNewWindCode().equals(registration.getSecurityCode())) {
                throw new BizException("Wind 目标代码与原登记冲突，id=" + registration.getId());
            }
            if (!master.getSecurityType().equals(registration.getTempSecurityType())
                    || !market.equals(registration.getTempSecurityMarket())) {
                throw new BizException("Wind 主档与原登记的类型或市场不一致，id=" + registration.getId());
            }
            registration.setMemo(registration.getMemo() == null ? eventKey : registration.getMemo() + "\n" + eventKey);
        }
        if (registration.getMemo().length() > 500) {
            throw new BizException("Wind 事件标记与原登记备注合计超过 500 字符，id=" + registration.getId());
        }
        return registration;
    }

    /** 严格解析 yyyyMMdd 字符串；事件日期必填，主档日期缺省则保留空。 */
    private Date parseWindDate(String value, boolean required) {
        if (!required && value == null) {
            return null;
        }
        try {
            if (value == null || !value.matches("\\d{8}")) {
                throw new BizException("日期必须为八位 yyyyMMdd，value=" + value);
            }
            return Date.from(LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        } catch (DateTimeParseException e) {
            throw new BizException("无效的 yyyyMMdd 日期，value=" + value);
        }
    }

    /** 按指定来源执行原有同券整组转正。 */
    private TempSecurityCodeDto editTempSecurityCodeToUpdated(
            TempSecurityCodeBo oldBo,
            TempSecurityCodeDto.FormalSecurityOption formalSecurity,
            String oprtSource) {
        // 原人工和债券临时代码任务继续使用同券整组模式。
        return editTempSecurityCodeToUpdated(oldBo, formalSecurity, oprtSource, false);
    }

    /** Wind 使用单码模式，其余入口保留原整组与占位禁用行为。 */
    private TempSecurityCodeDto editTempSecurityCodeToUpdated(TempSecurityCodeBo oldBo,
            TempSecurityCodeDto.FormalSecurityOption formalSecurity, String oprtSource, boolean windCodeChange) {
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setMemo(oldBo.getMemo());
        // 临时证券字段：转正时不可改，一律使用库内原值
        bo.setTempSecurityName(oldBo.getTempSecurityName());
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        bo.setTempSecurityMarket(oldBo.getTempSecurityMarket());
        bo.setTempSecurityType(oldBo.getTempSecurityType());
        bo.setTempMitigationCode(oldBo.getTempMitigationCode());
        bo.setTempCompanyCode(oldBo.getTempCompanyCode());
        bo.setTempCompanyNameSnapshot(oldBo.getTempCompanyNameSnapshot());
        bo.setTempIssueDate(oldBo.getTempIssueDate());
        bo.setTempMaturityDate(oldBo.getTempMaturityDate());
        // 正式证券字段：以主数据查询结果为准（名称/代码/市场/类型）
        bo.setSecurityName(formalSecurity.getSecurityName());
        bo.setSecurityCode(formalSecurity.getSecurityCode());
        bo.setSecurityMarket(formalSecurity.getSecurityMarket());
        bo.setSecurityType(formalSecurity.getSecurityType());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.UPDATED.getCode());
        // 记录人工或定时任务来源
        bo.setOprtSource(oprtSource);
        bo.setUpdtTime(now);
        Long tempCodeId = oldBo.getId();
        // 统一初始化、校验并执行普通池和 CRMW 转换，校验通过后才写入转正状态
        convertTempSecurityBusinessData(oldBo, bo, windCodeChange);
        // 禁用原临时代码占位证券主数据
        if (!windCodeChange) {
            // 原转正仍禁用临时占位；Wind 主档已经原地更名，不再禁用。
            disableTempSecurityInfo(oldBo, now);
        }
        // 查询更新后的临时代码详情
        return queryTempSecurityCodeDetail(tempCodeId);
    }

    /**
     * 取消发行临时代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToCancelled(TempSecurityCodeReq req) {
        // 校验主键 ID
        validateIdReq(req);
        // 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(req.getId());
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.CANCELLED.getCode());
        // 页面人工取消发行
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setUpdtTime(now);
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        // 计算取消发行日期
        bo.setCancelDate(queryCancelIssueDate());
        tempSecurityCodeMapper.editSecurityInfoToCancelled(bo);
        tempSecurityCodeMapper.editTempSecurityCodeToCancelled(bo);
        // 查询取消后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 删除临时代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto deleteTempSecurityCode(TempSecurityCodeReq req) {
        // 校验主键 ID
        validateIdReq(req);
        // 查询并校验临时代码存在
        TempSecurityCodeBo oldBo = queryExistingTempSecurityCode(req.getId());
        // 对齐老系统：仅临时状态允许删除
        if (!TempStatus.TEMPORARY.getCode().equals(oldBo.getStatus())) {
            throw new BizException("只有临时状态可以删除，id=" + req.getId() + "，status=" + oldBo.getStatus());
        }
        // 校验临时代码未被核心调库业务引用
        validateNoCoreReference(oldBo);
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.DELETED.getCode());
        // 页面人工删除
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setIsDeleted(1);
        bo.setUpdtTime(now);
        tempSecurityCodeMapper.deleteTempSecurityCode(bo);
        return null;
    }

    /**
     * 新增参数校验
     */
    private void validateAddReq(TempSecurityCodeReq req) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        // 校验临时证券名称必填
        validateRequired(req.getTempSecurityName(), "临时证券名称不能为空");
        // 校验临时证券代码必填
        validateRequired(req.getTempSecurityCode(), "临时证券代码不能为空");
        // 校验临时证券市场必填
        validateRequired(req.getTempSecurityMarket(), "临时证券市场不能为空");
        // 校验临时证券类型必填
        validateRequired(req.getTempSecurityType(), "临时证券类型不能为空");
        if (req.getTempCompanyCode() == null || req.getTempCompanyCode().trim().isEmpty()) {
            throw new BizException("发行主体不能为空");
        }
        if (req.getTempIssueDate() == null) {
            throw new BizException("发行日期不能为空");
        }
        if (req.getTempMaturityDate() == null) {
            throw new BizException("到期日期不能为空");
        }
        if (!req.getTempMaturityDate().after(req.getTempIssueDate())) {
            throw new BizException("到期日期必须晚于发行日期");
        }
        // 校验临时证券市场合法性
        validateMarket(req.getTempSecurityMarket(), "临时证券市场不合法");
        // 校验临时证券类型合法性
        validateSecurityType(req.getTempSecurityType());
        int count = tempSecurityCodeMapper.queryTempSecurityCodeCount(req.getTempSecurityCode(), null);
        if (count > 0) {
            throw new BizException("临时证券代码已存在，tempSecurityCode=" + req.getTempSecurityCode());
        }
    }

    /**
     * 更新参数校验（临时信息只读不校验入参；正式证券必须选定主数据中的代码）
     */
    private void validateUpdateReq(TempSecurityCodeReq req) {
        // 校验主键 ID
        validateIdReq(req);
        // 校验正式证券代码必填（名称/市场/类型由主数据带出）
        validateRequired(req.getSecurityCode(), "请选择正式证券");
    }

    /**
     * 查询并校验正式证券主数据
     */
    private TempSecurityCodeDto.FormalSecurityOption queryRequiredFormalSecurity(String securityCode) {
        TempSecurityCodeDto.FormalSecurityOption formalSecurity =
                tempSecurityCodeMapper.queryFormalSecurityByCode(securityCode);
        if (formalSecurity == null) {
            throw new BizException("正式证券不存在或不可用，securityCode=" + securityCode);
        }
        if (formalSecurity.getSecurityType() == null || formalSecurity.getSecurityType().trim().isEmpty()) {
            throw new BizException("正式证券缺少证券类型，securityCode=" + securityCode);
        }
        formalSecurity.setSecurityMarket(securityMarketService.queryMarketCode(formalSecurity.getSecurityCode()));
        return formalSecurity;
    }

    /**
     * ID 参数校验
     */
    private void validateIdReq(TempSecurityCodeReq req) {
        if (req == null || req.getId() == null) {
            throw new BizException("主键 ID 不能为空");
        }
    }

    /**
     * 校验必填字符串
     */
    private void validateRequired(String value, String message) {
        if (value == null || value.trim().length() == 0) {
            throw new BizException(message);
        }
    }

    /**
     * 校验证券市场（仅允许 MarketCode 标准 11 码）
     */
    private void validateMarket(String market, String message) {
        if (!MarketCode.isValid(market)) {
            throw new BizException(message + "，market=" + market);
        }
    }

    /**
     * 校验证券类型
     */
    private void validateSecurityType(String securityType) {
        int count = tempSecurityCodeMapper.querySecurityTypeCount(securityType);
        if (count <= 0) {
            throw new BizException("证券类型不存在或已删除，securityType=" + securityType);
        }
    }

    /**
     * 查询可操作临时代码
     */
    private TempSecurityCodeBo queryOperableTempSecurityCode(Long id) {
        // 查询临时代码原始记录
        TempSecurityCodeBo bo = queryExistingTempSecurityCode(id);
        if (!TempStatus.TEMPORARY.getCode().equals(bo.getStatus())) {
            throw new BizException("只有临时状态可以执行该操作，id=" + id + "，status=" + bo.getStatus());
        }
        return bo;
    }

    /**
     * 查询未删除临时代码
     */
    private TempSecurityCodeBo queryExistingTempSecurityCode(Long id) {
        TempSecurityCodeBo bo = tempSecurityCodeMapper.queryTempSecurityCodeById(id);
        if (bo == null) {
            throw new BizException("临时代码不存在或已删除，id=" + id);
        }
        return bo;
    }

    /**
     * 同步新增临时代码证券主数据
     */
    private void addTempSecurityInfo(TempSecurityCodeBo sourceBo) {
        int count = tempSecurityCodeMapper.querySecurityInfoCount(sourceBo.getTempSecurityCode());
        if (count > 0) {
            throw new BizException("临时证券代码已存在于证券主数据，tempSecurityCode=" + sourceBo.getTempSecurityCode());
        }
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setSecurityName(sourceBo.getTempSecurityName());
        bo.setSecurityCode(sourceBo.getTempSecurityCode());
        bo.setSecId(sourceBo.getSecId());
        bo.setSecurityMarket(sourceBo.getTempSecurityMarket());
        bo.setSecurityType(sourceBo.getTempSecurityType());
        bo.setTempCompanyCode(sourceBo.getTempCompanyCode());
        bo.setTempCompanyNameSnapshot(sourceBo.getTempCompanyNameSnapshot());
        bo.setTempIssueDate(sourceBo.getTempIssueDate());
        bo.setTempMaturityDate(sourceBo.getTempMaturityDate());
        bo.setUpdateTime(sourceBo.getCrteTime());
        bo.setSecuritySource("temporary");
        tempSecurityCodeMapper.addSecurityInfo(bo);
    }

    /**
     * 禁用临时代码占位证券主数据
     */
    private void disableTempSecurityInfo(TempSecurityCodeBo oldBo, Date updateTime) {
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        bo.setUpdateTime(updateTime);
        tempSecurityCodeMapper.editTempSecurityInfoToDisabled(bo);
    }

    /**
     * 按四个阶段执行临时代码转正：初始化数据、校验、普通池处理、CRMW 处理。
     * 普通池按以下六种业务情况处理，CRMW 在同一凭证下按标的正式代码组处理。
     *
     * ① T仅登记，无当前在池或流程中申请：只完成转正身份，不新增调库记录。
     * ② T申请调入P，尚未审批：主项换正式码，其他市场补关联项，沿用原审批。
     * ③ T已在P，正式组不在P：T退出，全部正式市场代码统一进入，逐码记账。
     * ④ T与正式组都在P：仅移除T，不重复调入正式组。
     * ⑤ T已在P且申请调出：当前状态先转为正式组；原调出申请同步换码，等待原审批。
     * ⑥ T在P1、P2等多个池：逐原池执行整组转换，同次转正统一提交或回滚。
     *
     * 六种情况由 businessConverter 分为“当前状态”和“流程中申请”两个阶段处理，
     * 不重复执行情况⑤的当前状态迁移；历史终态日志保留原证券身份。
     * 所有步骤沿用调用方事务，任一步骤失败时整次转正回滚。
     *
     * @param oldBo 转正前的临时代码登记信息
     * @param newBo 本次选中的正式证券及操作来源
     */
    private void convertTempSecurityBusinessData(TempSecurityCodeBo oldBo, TempSecurityCodeBo newBo, boolean windCodeChange) {
        // 1. 初始化数据：准备临时代码、选中正式代码、操作时间及人工/定时来源
        TempSecurityCodeBo replaceBo = buildReplaceBo(oldBo, newBo);

        // 2. 校验：准备普通池和 CRMW 整组计划，检查流程中冲突、市场、池关系及数据一致性
        TempSecurityCodeBusinessConverter.ConversionPlan conversionPlan = windCodeChange
                ? businessConverter.prepareWindCodeConversion(replaceBo)
                : businessConverter.prepareOrdinaryPoolConversion(replaceBo);
        // 同步准备 CRMW 标的整组计划，全部校验通过后再开始写入转正状态
        businessConverter.prepareCrmwConversion(replaceBo, conversionPlan);
        // 校验通过后先更新转正登记；登记参数与替换审计参数分开，避免审计插入回填覆盖登记 ID
        int updatedRows = tempSecurityCodeMapper.editTempSecurityCodeToUpdated(newBo);
        if (updatedRows != 1) {
            throw new BizException("更新临时代码失败，记录不存在或已不是临时状态，id=" + oldBo.getId());
        }

        // 3. 非 CRMW 的处理：按六种情况先转换当前在池状态，再迁移流程中申请，同步快照和逐码审计
        businessConverter.convertOrdinaryPoolBusinessData(conversionPlan, replaceBo);

        // 4. CRMW 的处理：同一凭证下标的代码整组转换，同步流程中申请和已在池记录
        businessConverter.convertCrmwBusinessData(conversionPlan, replaceBo);
    }

    /**
     * 构建临时代码替换参数
     */
    private TempSecurityCodeBo buildReplaceBo(TempSecurityCodeBo oldBo, TempSecurityCodeBo newBo) {
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityName(oldBo.getTempSecurityName());
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        bo.setTempSecurityMarket(oldBo.getTempSecurityMarket());
        bo.setTempSecurityType(oldBo.getTempSecurityType());
        bo.setSecurityName(newBo.getSecurityName());
        bo.setSecurityCode(newBo.getSecurityCode());
        bo.setSecurityMarket(newBo.getSecurityMarket());
        bo.setSecurityType(newBo.getSecurityType());
        bo.setUpdateTime(newBo.getUpdateTime());
        // 向后续日志构建传递人工或定时任务来源
        bo.setOprtSource(newBo.getOprtSource());
        bo.setReplaceStatus("success");
        return bo;
    }

    /**
     * 校验临时代码未被核心调库业务引用
     */
    private void validateNoCoreReference(TempSecurityCodeBo bo) {
        int count = tempSecurityCodeMapper.queryCoreReferenceCount(bo);
        if (count > 0) {
            throw new BizException("该临时代码已被调库业务使用，无法删除");
        }
    }

    /**
     * 查询取消发行日期
     */
    private String queryCancelIssueDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        return new SimpleDateFormat("yyyyMMdd").format(calendar.getTime());
    }

    /**
     * 查询临时代码详情
     */
    private TempSecurityCodeDto queryTempSecurityCodeDetail(Long id) {
        return tempSecurityCodeMapper.queryTempSecurityCodeDetail(id);
    }

    /**
     * 解析主体展示名称
     */
    private String resolveCompanyName(TempSecurityCodeDto.CompanyOption company) {
        if (company.getFullName() != null && company.getFullName().trim().length() > 0) {
            return company.getFullName();
        }
        if (company.getShortName() != null && company.getShortName().trim().length() > 0) {
            return company.getShortName();
        }
        throw new BizException("发行主体缺少名称，companyCode=" + company.getCompanyCode());
    }
}
