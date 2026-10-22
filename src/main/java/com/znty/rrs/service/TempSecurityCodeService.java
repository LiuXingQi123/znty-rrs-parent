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

    /** 查询变更记录、证券信息和临时代码记录。 */
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
        // 使用人工来源执行转正时的各项处理
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
        // 使用外部数据预先写入的正式证券代码查询证券表里的数据
        TempSecurityCodeDto.FormalSecurityOption formalSecurity =
                queryRequiredFormalSecurity(oldBo.getSecurityCode());
        if (formalSecurity.getSecurityCode().equals(oldBo.getTempSecurityCode())) {
            throw new BizException("正式证券代码不能与临时代码相同，id=" + id);
        }
        // 使用定时任务来源执行转正时的各项处理
        return editTempSecurityCodeToUpdated(oldBo, formalSecurity, TempOprtSource.JOB.getCode());
    }

    /**
     * 按 SEC_ID 找到证券，把证券表里的旧代码改成新代码，再改相关的调库记录。
     * 证券表、临时代码记录、普通池和 CRMW 一起改；任何一步失败，这条换码的修改全部撤销。
     * 找不到证券、主档已经是最新代码、这条变更已经处理过时，分别返回对应的跳过结果。
     *
     * @param change 变更记录，包含事件 ID、SEC_ID、旧新代码和八位变更日期
     * @return 本条记录的换码成功或跳过结果；校验、写入失败时抛出异常并回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public WindCodeChangeResult editWindCodeChangeByJob(WindCodeChangeDto change) {
        // 1、检查变更记录，SEC_ID、旧代码、新代码和变更日期都不能为空。
        if (change == null) {
            throw new BizException("Wind 变更事件不能为空");
        }
        // 1.1 检查 SEC_ID，后面靠它找到要换码的证券。
        validateRequired(change.getSecId(), "Wind 事件 SEC_ID 不能为空");
        // 1.2 检查变更记录中的旧代码，后面写入本条变更的标记。
        validateRequired(change.getOldWindCode(), "Wind 旧代码不能为空");
        // 1.3 检查新代码，后面要把证券信息和调库记录改成这个代码。
        validateRequired(change.getNewWindCode(), "Wind 新代码不能为空");
        // 1.4 检查变更日期，必须是八位 yyyyMMdd；新旧代码也不能相同。
        parseWindDate(change.getChangeDate(), true);
        if (change.getOldWindCode().equals(change.getNewWindCode())) {
            throw new BizException("Wind 新旧代码不能相同");
        }
        // 2、用 SEC_ID、变更记录旧代码、新代码和日期生成标记，下次用来判断是否处理过。
        String eventKey = "[wind_code_sync:" + change.getSecId() + ":" + change.getOldWindCode()
                + ":" + change.getNewWindCode() + ":" + change.getChangeDate() + "]";
        // 3、按 SEC_ID 查出并锁定证券，防止同时修改；找不到就返回跳过。
        SecurityInfoBo master = windCodeSyncMapper.querySecurityBySecIdForUpdate(change.getSecId());
        if (master == null) {
            return WindCodeChangeResult.MISSING_MASTER;
        }
        // 4、检查这条变更是否已经成功处理过，处理过就跳过。
        if (windCodeSyncMapper.queryCompletedWindCodeChangeCount(eventKey) > 0) {
            return WindCodeChangeResult.ALREADY_COMPLETED;
        }
        // 5、主档已经是最新代码时直接跳过，不重复生成出入库记录。
        if (change.getNewWindCode().equals(master.getWindCode())) {
            return WindCodeChangeResult.ALREADY_CURRENT;
        }
        // 5.1 检查主档当前代码，实际出库和临时代码记录都使用这个代码。
        validateRequired(master.getWindCode(), "证券主档缺少代码，SEC_ID=" + change.getSecId());
        WindCodeChangeDto masterChange = new WindCodeChangeDto();
        masterChange.setObjectId(change.getObjectId());
        masterChange.setSecId(change.getSecId());
        masterChange.setWindCode(change.getWindCode());
        masterChange.setOldWindCode(master.getWindCode());
        masterChange.setNewWindCode(change.getNewWindCode());
        masterChange.setChangeDate(change.getChangeDate());
        // 6、检查证券来源、证券类型，以及新代码是否已经存在。
        String source = master.getSecuritySource();
        if (!"official".equals(source) && !"temporary".equals(source) && !"temp_converted".equals(source)) {
            throw new BizException("证券来源不支持 Wind 换码，security_source=" + source);
        }
        // 6.1 检查证券类型，后面写临时代码记录和检查调库条件时要用。
        validateRequired(master.getSecurityType(), "Wind 旧主档缺少证券类型");
        if (tempSecurityCodeMapper.querySecurityInfoCount(change.getNewWindCode()) != 0) {
            throw new BizException("Wind 新代码已存在，违反换码数据约束，securityCode=" + change.getNewWindCode());
        }
        // 6.2 来源是 temp_converted 时，临时代码表中应该保留着之前转正的记录。
        if ("temp_converted".equals(source)
                && windCodeSyncMapper.queryConvertedRegistrationList(master.getWindCode()).isEmpty()) {
            throw new BizException("已转正证券在临时代码表中没有之前转正的记录，securityCode=" + master.getWindCode());
        }
        // 7、查出旧代码的市场，准备本次修改时间。
        String market = securityMarketService.queryMarketCode(master.getWindCode());
        Date now = new Date();
        // 8、查旧代码有没有临时代码记录，有就继续用，没有就按旧证券信息新增。
        TempSecurityCodeBo registration = prepareWindRegistration(masterChange, master, market, now, eventKey);
        // 9、把证券表中的旧代码改成新代码；temporary 改为 temp_converted，其他来源保持原样。
        String newSource = "temporary".equals(source) ? "temp_converted" : source;
        if (windCodeSyncMapper.editSecurityWindCode(masterChange, source, newSource, market, now) != 1) {
            throw new BizException("Wind 主档更名失败，SEC_ID=" + change.getSecId());
        }
        // 10、查出换码后的证券信息和市场，供后面更新调库记录使用。
        TempSecurityCodeDto.FormalSecurityOption formal = queryRequiredFormalSecurity(change.getNewWindCode());
        // 11、更新临时代码记录及相关调库记录，只处理本次旧新代码，其他市场代码不处理。
        editTempSecurityCodeToUpdated(registration, formal, TempOprtSource.JOB.getCode(), true);
        return WindCodeChangeResult.UPDATED;
    }

    /**
     * 查 rrs_temp_security_code 表，找出旧代码对应的记录，没有时按旧证券的信息新增一条。
     * 已经有对应记录，就检查状态、新代码、证券类型和市场，检查通过后继续用这条记录；
     * 没有对应记录，就根据证券信息新增；来源是 temporary 却没有原记录时，直接报错。
     * 把这条变更记录的标记加到备注中；换码全部保存成功且临时代码记录变为 updated 后，下次才能据此跳过。
     *
     * @param change 本次变更记录，包含旧代码和新代码
     * @param master 已按 SEC_ID 锁定并核对旧代码的证券主档
     * @param market 从主档或临时代码记录查询并校验通过的市场编码
     * @param now 新增临时代码记录使用的创建、修改时间
     * @param eventKey 这条变更记录的标记，保存到备注后，用来判断它是否已经处理成功
     * @return 这次要用的临时代码记录，带 ID、旧证券信息和本条变更的标记
     */
    private TempSecurityCodeBo prepareWindRegistration(WindCodeChangeDto change, SecurityInfoBo master,
                                                       String market, Date now, String eventKey) {
        // 1、查出并锁定旧代码未删除的临时代码记录；查出多条就报错，避免用错记录。
        List<TempSecurityCodeBo> registrations = windCodeSyncMapper.queryOldCodeRegistrationList(change.getOldWindCode());
        if (registrations.size() > 1) {
            throw new BizException("旧代码在临时代码表中有多条未删除的记录，securityCode=" + change.getOldWindCode());
        }
        TempSecurityCodeBo registration;
        if (registrations.isEmpty()) {
            // 2、没有记录时，按旧证券的信息新增；来源是 temporary 却没有原记录，就报错。
            if ("temporary".equals(master.getSecuritySource())) {
                throw new BizException("临时代码在 rrs_temp_security_code 表中没有对应记录，securityCode=" + master.getWindCode());
            }
            registration = new TempSecurityCodeBo();
            registration.setSecId(master.getSecId());
            registration.setTempSecurityCode(master.getWindCode());
            registration.setTempSecurityName(master.getShortName() == null ? master.getFullName() : master.getShortName());
            // 2.1 名称使用证券简称或全称，两者都没有就报错。
            validateRequired(registration.getTempSecurityName(), "Wind 旧主档缺少证券名称");
            registration.setTempSecurityMarket(market);
            registration.setTempSecurityType(master.getSecurityType());
            registration.setTempCompanyCode(master.getIssuerCode());
            registration.setTempCompanyNameSnapshot(master.getIssuer());
            // 2.2 使用旧证券的发行日期，为 NULL 就留空，有值时必须是 yyyyMMdd。
            registration.setTempIssueDate(parseWindDate(master.getFirstissueDate(), false));
            // 2.3 使用旧证券的到期日期，按发行日期相同的规则检查。
            registration.setTempMaturityDate(parseWindDate(master.getMaturityDate(), false));
            registration.setStatus(TempStatus.TEMPORARY.getCode());
            registration.setOprtSource(TempOprtSource.JOB.getCode());
            registration.setIsDeleted(0);
            registration.setCrteTime(now);
            registration.setUpdtTime(now);
            registration.setMemo(eventKey);
            // 2.4 保存这条临时代码记录，备注中带上本次变更的标记。
            tempSecurityCodeMapper.addTempSecurityCode(registration);
            if (registration.getId() == null) {
                throw new BizException("新增临时代码记录失败");
            }
        } else {
            // 3、有原记录时，检查它是否还没转正，以及新代码、类型和市场是否对得上。
            registration = registrations.get(0);
            if (!TempStatus.TEMPORARY.getCode().equals(registration.getStatus())) {
                throw new BizException("旧代码对应的临时代码记录已经不是待转正状态，id=" + registration.getId());
            }
            if (registration.getSecurityCode() != null && !registration.getSecurityCode().trim().isEmpty()
                    && !change.getNewWindCode().equals(registration.getSecurityCode())) {
                throw new BizException("变更记录的新代码与临时代码记录中的新代码不一致，id=" + registration.getId());
            }
            if (!master.getSecurityType().equals(registration.getTempSecurityType())
                    || !market.equals(registration.getTempSecurityMarket())) {
                throw new BizException("证券信息与临时代码记录的类型或市场不一致，id=" + registration.getId());
            }
            // 3.1 保留原备注，加上本条变更的标记，后面更新记录状态时一起保存。
            registration.setMemo(registration.getMemo() == null ? eventKey : registration.getMemo() + "\n" + eventKey);
        }
        // 4、检查备注长度，不能超过表字段允许的 500 个字符。
        if (registration.getMemo().length() > 500) {
            throw new BizException("变更记录标记加上原备注后超过 500 字符，id=" + registration.getId());
        }
        return registration;
    }

    /**
     * 将八位 yyyyMMdd 日期解析为上海时区当天零点。
     *
     * @param value 待解析的日期字符串
     * @param required 是否必填；false 时仅允许 NULL 留空，空字符串仍报错
     * @return 解析后的日期；非必填且 value 为 NULL 时返回 null
     */
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

    /**
     * 按人工或定时任务来源执行临时代码转正，包含正式证券的同券市场代码。
     *
     * @param oldBo 原临时代码记录
     * @param formalSecurity 已查询并校验通过的目标正式证券
     * @param oprtSource 操作来源，manual 为人工、job 为定时任务
     * @return 转正后的临时代码记录详情
     */
    private TempSecurityCodeDto editTempSecurityCodeToUpdated(
            TempSecurityCodeBo oldBo,
            TempSecurityCodeDto.FormalSecurityOption formalSecurity,
            String oprtSource) {
        // 原人工和债券临时代码任务继续使用同券整组模式。
        return editTempSecurityCodeToUpdated(oldBo, formalSecurity, oprtSource, false);
    }

    /**
     * 把新代码的信息写入临时代码记录，完成相关调库记录的换码，再返回更新后的临时代码记录详情。
     *
     * @param oldBo 旧代码在临时代码表中的原记录，包含需要保留的资料和备注
     * @param formalSecurity 本次转换使用的正式证券主数据
     * @param oprtSource 操作来源，manual 为人工、job 为定时任务
     * @param windCodeChange true 表示 Wind 单码换码并保留已更名主档；false 表示临时转正，
     *                       扩展同券市场代码并禁用原临时占位主档
     * @return 转换完成后的临时代码记录详情
     */
    private TempSecurityCodeDto editTempSecurityCodeToUpdated(TempSecurityCodeBo oldBo,
            TempSecurityCodeDto.FormalSecurityOption formalSecurity, String oprtSource, boolean windCodeChange) {
        // 1、准备要保存的临时代码记录，保留原记录 ID、备注和旧证券信息。
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setMemo(oldBo.getMemo());
        // 1.1 旧证券的名称、代码、市场等信息继续使用原记录中的值。
        bo.setTempSecurityName(oldBo.getTempSecurityName());
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        bo.setTempSecurityMarket(oldBo.getTempSecurityMarket());
        bo.setTempSecurityType(oldBo.getTempSecurityType());
        bo.setTempMitigationCode(oldBo.getTempMitigationCode());
        bo.setTempCompanyCode(oldBo.getTempCompanyCode());
        bo.setTempCompanyNameSnapshot(oldBo.getTempCompanyNameSnapshot());
        bo.setTempIssueDate(oldBo.getTempIssueDate());
        bo.setTempMaturityDate(oldBo.getTempMaturityDate());
        // 2、填入新证券的名称、代码、市场和类型，状态改为已更新。
        bo.setSecurityName(formalSecurity.getSecurityName());
        bo.setSecurityCode(formalSecurity.getSecurityCode());
        bo.setSecurityMarket(formalSecurity.getSecurityMarket());
        bo.setSecurityType(formalSecurity.getSecurityType());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.UPDATED.getCode());
        // 2.1 记录这次是人工操作还是定时任务，以及修改时间。
        bo.setOprtSource(oprtSource);
        bo.setUpdtTime(now);
        Long tempCodeId = oldBo.getId();
        // 3、更新临时代码记录、普通池和 CRMW 中的旧代码及相关调库记录。
        convertTempSecurityBusinessData(oldBo, bo, windCodeChange);
        // 4、临时代码转正时禁用原来的临时证券；Wind 换码后保留同一条证券。
        if (!windCodeChange) {
            // 4.1 禁用原临时证券，避免它继续被选中调库。
            disableTempSecurityInfo(oldBo, now);
        }
        // 5、查询并返回更新后的临时代码记录详情。
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
     * 更新临时代码记录，把普通池和 CRMW 中用到旧代码的记录改好。
     * 临时代码转正按以下六种业务情况处理；Wind 换码复用相同处理步骤，目标仅为事件新码。
     * 以下 T 表示旧临时代码，P 表示投资池，正式组表示目标正式券的全部市场代码。
     *
     * ① T 只有临时代码记录，不在池里，也没有待审批申请：只完成转正，不新增调库记录。
     * ② T申请调入P，尚未审批：主项换正式码，其他市场补关联项，沿用原审批。
     * ③ T已在P，正式组不在P：T退出，全部正式市场代码统一进入，逐码记账。
     * ④ T与正式组都在P：仅移除T，不重复调入正式组。
     * ⑤ T已在P且申请调出：当前状态先转为正式组；原调出申请同步换码，等待原审批。
     * ⑥ T在P1、P2等多个池：逐原池执行整组转换，同次转正统一提交或回滚。
     *
     * 六种情况由 businessConverter 分为“当前状态”和“流程中申请”两个阶段处理，
     * 不重复执行情况⑤的当前状态更新；历史终态日志保留原证券代码和名称。
     * 所有步骤沿用调用方事务，任一步骤失败时整次转正回滚。
     *
     * @param oldBo 转正前的临时代码记录信息
     * @param newBo 本次选中的正式证券及操作来源
     * @param windCodeChange true 仅更新 Wind 事件的旧新码；false 处理目标正式券的全部市场代码
     */
    private void convertTempSecurityBusinessData(TempSecurityCodeBo oldBo, TempSecurityCodeBo newBo, boolean windCodeChange) {
        // 1、整理旧代码、新代码、修改时间和操作来源，供后面的换码和替换日志使用。
        TempSecurityCodeBo replaceBo = buildReplaceBo(oldBo, newBo);

        // 2、查普通池里的记录，检查待审批申请是否冲突、新代码能否进入这些池。
        TempSecurityCodeBusinessConverter.ConversionPlan conversionPlan = windCodeChange
                ? businessConverter.prepareWindCodeConversion(replaceBo)
                : businessConverter.prepareOrdinaryPoolConversion(replaceBo);
        // 3、查 CRMW 中使用旧代码的在池记录和待审批申请，检查新代码能否接着使用。
        businessConverter.prepareCrmwConversion(replaceBo, conversionPlan);
        // 4、检查通过后，把临时代码记录改为已更新并保存备注；后面失败时一起撤销。
        int updatedRows = tempSecurityCodeMapper.editTempSecurityCodeToUpdated(newBo);
        if (updatedRows != 1) {
            throw new BizException("更新临时代码失败，记录不存在或已不是临时状态，id=" + oldBo.getId());
        }

        // 5、普通池中旧代码出库、新代码入库，更新待审批申请和快照，保存替换日志。
        businessConverter.convertOrdinaryPoolBusinessData(conversionPlan, replaceBo);

        // 6、按凭证更新 CRMW 标的的在池记录和待审批申请，保存快照和替换日志。
        businessConverter.convertCrmwBusinessData(conversionPlan, replaceBo);

        // 7、每次成功转换都保存登记映射，无业务引用时也能追溯旧新代码。
        replaceBo.setReplaceTableName("rrs_temp_security_code");
        replaceBo.setReplaceRecordId(oldBo.getId());
        if (tempSecurityCodeMapper.addTempSecurityCodeUpdateLog(replaceBo) != 1) {
            throw new BizException("代码转换映射日志写入失败，登记ID=" + oldBo.getId()
                    + "，旧代码=" + oldBo.getTempSecurityCode() + "，新代码=" + newBo.getSecurityCode());
        }
    }

    /**
     * 组合原临时代码记录的旧码资料与目标证券信息，供业务转换和替换日志使用。
     *
     * @param oldBo 旧代码在临时代码表中的原记录，提供旧名称、代码、市场和类型
     * @param newBo 本次目标证券信息，提供新码资料、操作来源和更新时间
     * @return 本次旧新代码映射及审计写入参数
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
