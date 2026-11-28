package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.common.enums.TempOprtSource;
import com.znty.rrs.common.enums.WindCodeChangeResult;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
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
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理证券临时代码的录入、转正和取消发行
 *
 * 人工转正和定时任务使用共用的换码业务校验，先确认正式证券主档及代码映射
 * 由 {@link TempSecurityCodeBusinessConverter} 处理普通池和 CRMW 池内的在池状态及未结束调库申请
 */
@Service
public class TempSecurityCodeService {

    /** 临时证券 ID 前缀 */
    private static final String TEMP_SEC_ID_PREFIX = "TEMP_";

    /** 写入临时代码登记备注的 Wind 换码事件前缀 */
    private static final String WIND_EVENT_MARKER = "[wind_code_sync:";

    /** 统一证券市场查询组件 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 临时代码转正业务转换组件，统一处理普通池和 CRMW */
    @Resource
    private TempSecurityCodeBusinessConverter businessConverter;

    /** 临时代码管理数据访问组件 */
    @Resource
    private TempSecurityCodeMapper tempSecurityCodeMapper;

    /** 查询变更记录、证券信息和临时代码记录 */
    @Resource
    private WindCodeSyncMapper windCodeSyncMapper;

    /**
     * 分页查询临时代码列表
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将本页记录和查询总条数一起返回
     *
     * @param req 证券临时代码查询或维护请求
     * @return 临时证券代码及分页信息
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
     *
     * 执行顺序：
     * 1. 查询发行主体下拉选项
     * 2. 查询证券类型下拉选项
     *
     * @param req 证券临时代码查询或维护请求
     * @return 选项资料包
     */
    public TempSecurityCodeDto.OptionBundle queryTempSecurityCodeOptions(TempSecurityCodeReq req) {
        if (req == null) {
            req = new TempSecurityCodeReq();
        }
        TempSecurityCodeDto.OptionBundle bundle = new TempSecurityCodeDto.OptionBundle();
        // 1. 查询发行主体下拉选项
        bundle.setCompanies(tempSecurityCodeMapper.queryCompanyOptionList(req));
        // 2. 查询证券类型下拉选项
        bundle.setSecurityTypes(tempSecurityCodeMapper.querySecurityTypeList());
        return bundle;
    }

    /**
     * 查询新增临时代码可选的发行主体
     *
     * 执行顺序：
     * 1. 按弹窗条件查询公司简介，最多取 100 条匹配记录
     *
     * @param req 主体代码、全称和统一社会信用代码筛选条件
     * @return 发行主体选项列表
     */
    public List<TempSecurityCodeDto.IssuerOption> queryIssuerOptionList(TempSecurityCodeReq.IssuerQueryReq req) {
        return tempSecurityCodeMapper.queryIssuerOptionList(req);
    }

    /**
     * 新增临时代码
     *
     * 执行顺序：
     * 1. 校验新增请求，临时代码已登记时拒绝
     * 2. 按代码查找发行主体，查不到时拒绝
     * 3. 保存临时代码登记，主体名称优先取全称
     * 4. 同步临时证券占位主档和主体内评分
     * 5. 返回新增后的登记详情
     *
     * @param req 临时代码新增请求
     * @return 新增后的临时代码详情
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto addTempSecurityCode(TempSecurityCodeReq req) {
        // 1. 校验新增请求参数
        validateAddReq(req);
        Date now = new Date();
        // 2. 按代码查找发行主体，查不到时拒绝新增
        TempSecurityCodeDto.CompanyOption company = tempSecurityCodeMapper.queryCompanyByCode(req.getTempCompanyCode());
        if (company == null) {
            throw new BizException("发行主体不存在，tempCompanyCode=" + req.getTempCompanyCode());
        }
        // 3. 准备并保存人工新增的临时代码登记
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityName(req.getTempSecurityName());
        bo.setTempSecurityCode(req.getTempSecurityCode());
        bo.setSecId(TEMP_SEC_ID_PREFIX + req.getTempSecurityCode());
        bo.setTempSecurityMarket(req.getTempSecurityMarket());
        bo.setTempSecurityType(req.getTempSecurityType());
        bo.setTempMitigationCode(req.getTempMitigationCode());
        bo.setTempCompanyCode(req.getTempCompanyCode());
        // 优先用全称记录主体名称，没有全称时用简称
        bo.setTempCompanyNameSnapshot(resolveCompanyName(company));
        bo.setTempIssueDate(req.getTempIssueDate());
        bo.setTempMaturityDate(req.getTempMaturityDate());
        bo.setStatus(TempStatus.TEMPORARY.getCode());
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setIsDeleted(0);
        bo.setCrteTime(now);
        bo.setUpdtTime(now);
        tempSecurityCodeMapper.addTempSecurityCode(bo);
        // 4. 同步临时证券占位主档和主体内评分
        addTempSecurityInfo(bo);
        // 5. 查询并返回新增后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 远程查询正式证券选项（转正选券，代码/名称模糊，最多 50 条）
     *
     * 执行顺序：
     * 1. 查询正式证券远程选项
     * 2. 批量查询当前代码市场，避免列表与导出逐行查询
     *
     * @param req 证券临时代码查询或维护请求
     * @return 正式证券选项列表
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
     * 更新临时代码为正式证券
     * 对齐老系统 TempSecurityInfoTableAction#refreshTempSecurity 分叉逻辑：
     *
     * 普通池流程中调库日志（audit_status=00/11）：替换主项并补同券市场关联项，沿用原审批
     * 普通池已在池状态（audit_status=20）：每个池「临时出库 +（正式组未在池则）整组入库」
     * CRMW 池在同一凭证下按标的全部市场代码转换，流程中沿用原审批
     * 池/日志上 CRMW 字段指向临时码：仅字段替换
     *
     * 临时证券信息只读；正式证券必须从主数据远程选择
     *
     * 执行顺序：
     * 1. 校验更新请求参数
     * 2. 查询并校验临时代码当前状态
     * 3. 校验正式证券存在且非临时代码占位
     * 4. 使用人工来源执行转正时的各项处理
     *
     * @param req 证券临时代码查询或维护请求
     * @return 转正后的临时代码记录详情
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToUpdated(TempSecurityCodeReq req) {
        // 1. 校验更新请求参数
        validateUpdateReq(req);
        // 2. 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(req.getId());
        // 3. 校验正式证券存在且非临时代码占位
        TempSecurityCodeDto.FormalSecurityOption formalSecurity = queryRequiredFormalSecurity(req.getSecurityCode(), false);
        if (formalSecurity.getSecurityCode().equals(oldBo.getTempSecurityCode())) {
            throw new BizException("正式证券代码不能与临时代码相同");
        }
        // 4. 使用人工来源执行转正时的各项处理
        return editTempSecurityCodeToUpdated(oldBo, formalSecurity, TempOprtSource.MANUAL.getCode());
    }

    /**
     * 定时任务将已取得正式码映射的临时代码转为正式证券
     *
     * 执行顺序：
     * 1. 查询并校验临时代码当前状态
     * 2. 使用外部数据预先写入的正式证券代码查询证券表里的数据
     * 3. 使用定时任务来源执行转正时的各项处理
     *
     * @param id 临时代码记录 ID
     * @return 更新后的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToUpdatedByJob(Long id) {
        if (id == null) {
            throw new BizException("临时代码记录 ID 不能为空");
        }
        // 1. 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(id);
        // 2. 使用外部数据预先写入的正式证券代码查询证券表里的数据
        TempSecurityCodeDto.FormalSecurityOption formalSecurity =
                // 查询并校验正式证券主数据
                queryRequiredFormalSecurity(oldBo.getSecurityCode(), false);
        if (formalSecurity.getSecurityCode().equals(oldBo.getTempSecurityCode())) {
            throw new BizException("正式证券代码不能与临时代码相同，id=" + id);
        }
        // 3. 使用定时任务来源执行转正时的各项处理
        return editTempSecurityCodeToUpdated(oldBo, formalSecurity, TempOprtSource.JOB.getCode());
    }

    /**
     * 按 SEC_ID 找到证券，保留失效旧主档，新增新码主档或重建已失效的临时目标主档，再改相关调库记录
     * 证券表、临时代码记录、普通池和 CRMW 一起改；任何一步失败，这条换码的修改全部撤销
     * 找不到证券、主档已经是最新代码、这条变更已经处理过时，分别返回对应的跳过结果
     *
     * 执行顺序：
     * 1. 检查变更记录，SEC_ID、旧代码、新代码和变更日期都不能为空
     * 2. 检查 SEC_ID，后面靠它找到要换码的证券
     * 3. 检查变更记录中的旧代码，后面写入本条变更的标记
     * 4. 检查新代码，后面要把证券信息和调库记录改成这个代码
     * 5. 用原正式 SEC_ID 插入目标主档
     * 6. 读取换码后的主档和市场，退市状态仍可同步代码
     * 7. 更新临时代码记录及相关调库记录，只处理本次旧新代码，其他市场代码不处理
     *
     * @param change 变更记录，包含事件 ID、SEC_ID、旧新代码和八位变更日期
     * @return 本条记录的换码成功或跳过结果；校验、写入失败时抛出异常并回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public WindCodeChangeResult editWindCodeChangeByJob(WindCodeChangeDto change) {
        // 1. 检查变更记录，SEC_ID、旧代码、新代码和变更日期都不能为空
        if (change == null) {
            throw new BizException("Wind 变更事件不能为空");
        }
        // 检查 SEC_ID，后面靠它找到要换码的证券
        validateRequired(change.getSecId(), "Wind 事件 SEC_ID 不能为空");
        // 检查变更记录中的旧代码，后面写入本条变更的标记
        validateRequired(change.getOldWindCode(), "Wind 旧代码不能为空");
        // 检查新代码，后面要把证券信息和调库记录改成这个代码
        validateRequired(change.getNewWindCode(), "Wind 新代码不能为空");
        // 检查变更日期，必须是八位 yyyyMMdd；新旧代码也不能相同
        parseWindDate(change.getChangeDate(), true);
        if (change.getOldWindCode().equals(change.getNewWindCode())) {
            throw new BizException("Wind 新旧代码不能相同");
        }
        // 2. 用源事件生成标记，同日再次发生的相同换码用操作时间区分
        String opDate = change.getOpDate();
        String eventKey = WIND_EVENT_MARKER + change.getSecId() + ":" + change.getOldWindCode()
                + ":" + change.getNewWindCode() + ":" + change.getChangeDate()
                + ":" + (opDate == null ? "" : opDate) + "]";
        // 3. 按 SEC_ID 查询当前证券，找不到就返回跳过
        SecurityInfoBo master = windCodeSyncMapper.querySecurityBySecId(change.getSecId());
        if (master == null) {
            return WindCodeChangeResult.MISSING_MASTER;
        }
        // 4. 检查这条变更是否已经成功处理过，处理过就跳过
        if (windCodeSyncMapper.queryCompletedWindCodeChangeCount(eventKey) > 0) {
            return WindCodeChangeResult.ALREADY_COMPLETED;
        }
        // 5. 主档已经是最新代码时直接跳过，不重复生成出入库记录
        if (change.getNewWindCode().equals(master.getWindCode())) {
            return WindCodeChangeResult.ALREADY_CURRENT;
        }
        // 检查主档当前代码，实际出库和临时代码记录都使用这个代码
        validateRequired(master.getWindCode(), "证券主档缺少代码，SEC_ID=" + change.getSecId());
        WindCodeChangeDto masterChange = new WindCodeChangeDto();
        masterChange.setObjectId(change.getObjectId());
        masterChange.setSecId(change.getSecId());
        masterChange.setWindCode(change.getWindCode());
        masterChange.setOldWindCode(master.getWindCode());
        masterChange.setNewWindCode(change.getNewWindCode());
        masterChange.setChangeDate(change.getChangeDate());
        // 6. 检查证券来源、证券类型和目标主档状态
        String source = master.getSecuritySource();
        if (!"official".equals(source) && !"temporary".equals(source) && !"temp_converted".equals(source)) {
            throw new BizException("证券来源不支持 Wind 换码，security_source=" + source);
        }
        // 检查证券类型，后面写临时代码记录和检查调库条件时要用
        validateRequired(master.getSecurityType(), "Wind 旧主档缺少证券类型");
        // 只允许重建已失效的临时目标主档
        SecurityInfoBo targetMaster = windCodeSyncMapper.querySecurityByWindCode(change.getNewWindCode());
        if (targetMaster != null && (!"temporary".equals(targetMaster.getSecuritySource())
                || !"D".equals(targetMaster.getSecurityStatus()))) {
            throw new BizException("Wind 新代码已存在，仅允许重建失效临时主档，securityCode=" + change.getNewWindCode());
        }
        // 来源是 temp_converted 时，核对之前的转正登记
        if ("temp_converted".equals(source)
                && windCodeSyncMapper.queryConvertedRegistrationList(master.getWindCode()).isEmpty()) {
            throw new BizException("已转正证券在临时代码表中没有之前转正的记录，securityCode=" + master.getWindCode());
        }
        // 7. 查出旧代码的市场，准备本次修改时间
        String market = securityMarketService.queryMarketCode(master.getWindCode());
        Date now = new Date();
        // 8. 查旧代码有没有临时代码记录，有就继续用，没有就按旧证券信息新增
        TempSecurityCodeBo registration = prepareWindRegistration(masterChange, master, market, now, eventKey);
        // 9. 从旧主档复制新主档所需的数据和正式 SEC_ID
        String newSource = "temporary".equals(source) ? "temp_converted" : source;
        SecurityInfoBo newMaster = new SecurityInfoBo();
        BeanUtils.copyProperties(master, newMaster);
        newMaster.setWindCode(change.getNewWindCode());
        newMaster.setSecuritySource(newSource);
        if ("temporary".equals(source)) {
            newMaster.setMarket(market);
        }
        newMaster.setTs(now);
        // 先删除失效的临时目标主档，并保留原创建时间
        if (targetMaster != null) {
            newMaster.setCreateTime(targetMaster.getCreateTime());
            if (windCodeSyncMapper.deleteWindHistoricalMaster(targetMaster.getWindCode()) != 1) {
                throw new BizException("Wind 目标历史主档删除失败，securityCode=" + targetMaster.getWindCode());
            }
        }
        // 将旧主档改用临时 SEC_ID，释放正式 SEC_ID
        if (windCodeSyncMapper.editSecurityWindCodeToTemporary(masterChange, source,
                registration.getSecId(), now) != 1) {
            throw new BizException("Wind 旧代码临时主档失效失败，SEC_ID=" + change.getSecId());
        }
        // 用原正式 SEC_ID 插入目标主档
        if (windCodeSyncMapper.addSecurityWindCodeMaster(newMaster) != 1) {
            throw new BizException("Wind 新代码主档新增失败，SEC_ID=" + change.getSecId());
        }
        // 10. 读取换码后的主档和市场，退市状态仍可同步代码
        TempSecurityCodeDto.FormalSecurityOption formal = queryRequiredFormalSecurity(change.getNewWindCode(), true);
        // 11. 更新临时代码记录及相关调库记录，只处理本次旧新代码，其他市场代码不处理
        editTempSecurityCodeToUpdated(registration, formal, TempOprtSource.JOB.getCode(), true);
        return WindCodeChangeResult.UPDATED;
    }

    /**
     * 根据旧代码准备本次换码登记，保留已经完成的历史登记
     * 有未完成登记时检查状态、目标代码、证券类型和市场，通过后复用
     * 没有未完成登记时按旧证券信息新增，主档来源为 temporary 时须有原登记
     * 把这条变更记录的标记加到备注中；换码全部保存成功且临时代码记录变为 updated 后，下次才能据此跳过
     *
     * 执行顺序：
     * 1. 查询旧代码未删除且未完成的登记，多条未完成登记时报错
     * 2. 检查备注长度，不能超过表字段允许的 500 个字符
     *
     * @param change 本次变更记录，包含旧代码和新代码
     * @param master 已按 SEC_ID 查到并核对旧代码的证券主档
     * @param market 从主档或临时代码记录查询并校验通过的市场编码
     * @param now 新增临时代码记录使用的创建、修改时间
     * @param eventKey 这条变更记录的标记，保存到备注后，用来判断它是否已经处理成功
     * @return 这次要用的临时代码记录，带 ID、旧证券信息和本条变更的标记
     */
    private TempSecurityCodeBo prepareWindRegistration(WindCodeChangeDto change, SecurityInfoBo master,
                                                       String market, Date now, String eventKey) {
        // 1. 查询旧代码未删除且未完成的登记，多条未完成登记时报错
        List<TempSecurityCodeBo> registrations = windCodeSyncMapper.queryOldCodeRegistrationList(change.getOldWindCode());
        if (registrations.size() > 1) {
            throw new BizException("旧代码在临时代码表中有多条未完成的记录，securityCode=" + change.getOldWindCode());
        }
        TempSecurityCodeBo registration;
        if (registrations.isEmpty()) {
            // 2. 没有记录时，按旧证券的信息新增；来源是 temporary 却没有原记录，就报错
            if ("temporary".equals(master.getSecuritySource())) {
                throw new BizException("临时代码在 rrs_temp_security_code 表中没有对应记录，securityCode=" + master.getWindCode());
            }
            registration = new TempSecurityCodeBo();
            registration.setSecId(TEMP_SEC_ID_PREFIX + master.getWindCode());
            registration.setTempSecurityCode(master.getWindCode());
            registration.setTempSecurityName(master.getShortName() == null ? master.getFullName() : master.getShortName());
            // 名称使用证券简称或全称，两者都没有就报错
            validateRequired(registration.getTempSecurityName(), "Wind 旧主档缺少证券名称");
            registration.setTempSecurityMarket(market);
            registration.setTempSecurityType(master.getSecurityType());
            registration.setTempCompanyCode(master.getIssuerCode());
            registration.setTempCompanyNameSnapshot(master.getIssuer());
            // 使用旧证券的发行日期，为 NULL 就留空，有值时必须是 yyyyMMdd
            registration.setTempIssueDate(parseWindDate(master.getFirstissueDate(), false));
            // 使用旧证券的到期日期，按发行日期相同的规则检查
            registration.setTempMaturityDate(parseWindDate(master.getMaturityDate(), false));
            registration.setStatus(TempStatus.TEMPORARY.getCode());
            registration.setOprtSource(TempOprtSource.JOB.getCode());
            registration.setIsDeleted(0);
            registration.setCrteTime(now);
            registration.setUpdtTime(now);
            registration.setMemo(eventKey);
            // 保存这条临时代码记录，备注中带上本次变更的标记
            tempSecurityCodeMapper.addTempSecurityCode(registration);
            if (registration.getId() == null) {
                throw new BizException("新增临时代码记录失败");
            }
        } else {
            // 3. 有原记录时，检查它是否还没转正，以及新代码、类型和市场是否对得上
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
            // 保留原备注，加上本条变更的标记，后面更新记录状态时一起保存
            registration.setMemo(registration.getMemo() == null ? eventKey : registration.getMemo() + "\n" + eventKey);
        }
        // 4. 检查备注长度，不能超过表字段允许的 500 个字符
        if (registration.getMemo().length() > 500) {
            throw new BizException("变更记录标记加上原备注后超过 500 字符，id=" + registration.getId());
        }
        return registration;
    }

    /**
     * 将八位 yyyyMMdd 日期解析为上海时区当天零点
     *
     * 执行顺序：
     * 1. 允许缺省且日期为 null 时直接返回 null
     * 2. 校验八位数字并解析日期，按上海时区转换成当天零点
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
     * 按人工或定时任务来源执行临时代码转正，包含正式证券的同券市场代码
     *
     * 执行顺序：
     * 1. 查询正式代码是否已被其他临时代码关联
     * 2. 检查正式证券及同券市场代码是否已有调库日志
     * 3. 检查通过后沿用原来的整组转换逻辑
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
        // 1. 查询正式代码是否已被其他临时代码关联
        validateFormalSecurityAssociation(oldBo.getId(), formalSecurity.getSecurityCode());
        // 2. 检查正式证券及同券市场代码，已有调库日志时不进入后续转换
        validateFormalSecurityAdjustHistory(formalSecurity.getSecurityCode());
        // 3. 原人工和债券临时代码任务继续使用同券整组模式
        return editTempSecurityCodeToUpdated(oldBo, formalSecurity, oprtSource, false);
    }

    /**
     * 检查正式代码是否已被其他临时代码成功关联
     *
     * 执行顺序：
     * 1. 排除当前登记，普通查询同一正式代码的已更新记录
     * 2. 已有关联时提示占用的临时代码，不执行后续转换
     *
     * @param tempCodeId 当前临时代码登记 ID
     * @param securityCode 本次选定的正式证券代码
     */
    private void validateFormalSecurityAssociation(Long tempCodeId, String securityCode) {
        TempSecurityCodeBo associated = tempSecurityCodeMapper.queryAssociatedTempSecurityCode(
                securityCode, tempCodeId);
        if (associated != null) {
            throw new BizException("正式证券已被临时代码关联，不能重复关联，证券代码：" + securityCode
                    + "，已关联临时代码：" + associated.getTempSecurityCode()
                    + "，登记ID：" + associated.getId());
        }
    }

    /**
     * 正式证券及同券市场代码存在任何调库日志时，拒绝临时代码转正
     *
     * 执行顺序：
     * 1. 按共用市场集合读取同券代码
     * 2. 正式证券及同券市场代码存在任何调库日志时，拒绝临时代码转正
     *
     * @param securityCode 本次选定的正式证券代码
     */
    private void validateFormalSecurityAdjustHistory(String securityCode) {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(securityCode);
        for (SecurityMarketDto related : securityMarketService.queryRelatedMarketCodes(securityCode)) {
            codes.add(related.getWindCode());
        }
        IpAdjustLogBo history = tempSecurityCodeMapper.queryFormalSecurityAdjustHistory(new ArrayList<>(codes));
        if (history != null) {
            throw new BizException("正式证券已有调库操作记录，不能用于临时代码转正，证券代码："
                    + history.getSecurityCode() + "，调库记录ID：" + history.getId());
        }
    }

    /**
     * 把新代码的信息写入临时代码记录，完成相关调库记录的换码，再返回更新后的临时代码记录详情
     *
     * 执行顺序：
     * 1. 准备要保存的临时代码记录，保留原记录 ID、备注和旧证券信息
     * 2. 旧证券的名称、代码、市场等信息继续使用原记录中的值
     * 3. 填入新证券的名称、代码、市场和类型，状态改为已更新
     * 4. 记录这次是人工操作还是定时任务，以及修改时间
     * 5. 更新临时代码记录、普通池和 CRMW 中的旧代码及相关调库记录
     * 6. 临时代码转正时禁用原来的临时证券；Wind 换码后保留同一条证券
     * 7. 查询并返回更新后的临时代码记录详情
     *
     * @param oldBo 旧代码在临时代码表中的原记录，包含需要保留的资料和备注
     * @param formalSecurity 本次转换使用的正式证券主数据
     * @param oprtSource 操作来源，manual 为人工、job 为定时任务
     * @param windCodeChange true 表示 Wind 单码换码并保留已更名主档；false 表示临时转正， 扩展同券市场代码并禁用原临时占位主档
     * @return 转换完成后的临时代码记录详情
     */
    private TempSecurityCodeDto editTempSecurityCodeToUpdated(TempSecurityCodeBo oldBo,
            TempSecurityCodeDto.FormalSecurityOption formalSecurity, String oprtSource, boolean windCodeChange) {
        // 1. 准备要保存的临时代码记录，保留原记录 ID、备注和旧证券信息
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setMemo(oldBo.getMemo());
        // 旧证券的名称、代码、市场等信息继续使用原记录中的值
        bo.setTempSecurityName(oldBo.getTempSecurityName());
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        bo.setTempSecurityMarket(oldBo.getTempSecurityMarket());
        bo.setTempSecurityType(oldBo.getTempSecurityType());
        bo.setTempMitigationCode(oldBo.getTempMitigationCode());
        bo.setTempCompanyCode(oldBo.getTempCompanyCode());
        bo.setTempCompanyNameSnapshot(oldBo.getTempCompanyNameSnapshot());
        bo.setTempIssueDate(oldBo.getTempIssueDate());
        bo.setTempMaturityDate(oldBo.getTempMaturityDate());
        // 2. 填入新证券的名称、代码、市场和类型，状态改为已更新
        bo.setSecurityName(formalSecurity.getSecurityName());
        bo.setSecurityCode(formalSecurity.getSecurityCode());
        bo.setSecurityMarket(formalSecurity.getSecurityMarket());
        bo.setSecurityType(formalSecurity.getSecurityType());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.UPDATED.getCode());
        // 记录这次是人工操作还是定时任务，以及修改时间
        bo.setOprtSource(oprtSource);
        bo.setUpdtTime(now);
        Long tempCodeId = oldBo.getId();
        // 3. 更新临时代码记录、普通池和 CRMW 中的旧代码及相关调库记录
        convertTempSecurityBusinessData(oldBo, bo, windCodeChange);
        // 4. 临时代码转正时禁用原来的临时证券；Wind 换码后保留同一条证券
        if (!windCodeChange) {
            // 禁用原临时证券，避免它继续被选中调库
            disableTempSecurityInfo(oldBo, now);
        }
        // 5. 查询并返回更新后的临时代码记录详情
        return queryTempSecurityCodeDetail(tempCodeId);
    }

    /**
     * 取消发行临时代码
     *
     * 执行顺序：
     * 1. 校验主键 ID
     * 2. 查询并校验临时代码当前状态
     * 3. 页面人工取消发行
     * 4. 计算取消发行日期
     * 5. 查询取消后的临时代码详情
     *
     * @param req 证券临时代码查询或维护请求
     * @return 临时证券代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToCancelled(TempSecurityCodeReq req) {
        // 1. 校验主键 ID
        validateIdReq(req);
        // 2. 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(req.getId());
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.CANCELLED.getCode());
        // 3. 页面人工取消发行
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setUpdtTime(now);
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        // 4. 计算取消发行日期
        bo.setCancelDate(queryCancelIssueDate());
        tempSecurityCodeMapper.editSecurityInfoToCancelled(bo);
        tempSecurityCodeMapper.editTempSecurityCodeToCancelled(bo);
        // 5. 查询取消后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 删除临时代码
     *
     * 执行顺序：
     * 1. 校验主键 ID
     * 2. 查询并校验临时代码存在
     * 3. 对齐老系统：仅临时状态允许删除
     * 4. 校验临时代码未被核心调库业务引用
     * 5. 删除临时代码登记及临时占位主档，释放代码供重新新增或 Wind 换入
     *
     * @param req 包含待删除记录 ID 的临时代码维护请求
     * @return 删除成功时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto deleteTempSecurityCode(TempSecurityCodeReq req) {
        // 1. 校验主键 ID
        validateIdReq(req);
        // 2. 查询并校验临时代码存在
        TempSecurityCodeBo oldBo = queryExistingTempSecurityCode(req.getId());
        // 3. 对齐老系统：仅临时状态允许删除
        if (!TempStatus.TEMPORARY.getCode().equals(oldBo.getStatus())) {
            throw new BizException("只有临时状态可以删除，id=" + req.getId() + "，status=" + oldBo.getStatus());
        }
        // 4. 校验临时代码未被核心调库业务引用
        validateNoCoreReference(oldBo);
        // 5. 删除临时代码登记及临时占位主档
        if (tempSecurityCodeMapper.deleteTempSecurityCode(oldBo.getId()) != 1) {
            throw new BizException("删除临时代码登记失败，id=" + oldBo.getId());
        }
        // 同步删除临时占位主档，释放代码供重新新增或 Wind 换入
        tempSecurityCodeMapper.deleteTemporarySecurityInfo(oldBo.getTempSecurityCode());
        return null;
    }

    /**
     * 新增参数校验
     *
     * 执行顺序：
     * 1. 校验临时证券名称必填
     * 2. 校验临时证券代码必填
     * 3. 校验临时证券市场必填
     * 4. 校验临时证券类型必填
     * 5. 校验临时证券市场合法性
     * 6. 校验临时证券类型合法性
     *
     * @param req 证券临时代码查询或维护请求
     */
    private void validateAddReq(TempSecurityCodeReq req) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        // 1. 校验临时证券名称必填
        validateRequired(req.getTempSecurityName(), "临时证券名称不能为空");
        // 2. 校验临时证券代码必填
        validateRequired(req.getTempSecurityCode(), "临时证券代码不能为空");
        // 3. 校验临时证券市场必填
        validateRequired(req.getTempSecurityMarket(), "临时证券市场不能为空");
        // 4. 校验临时证券类型必填
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
        // 5. 校验临时证券市场合法性
        validateMarket(req.getTempSecurityMarket(), "临时证券市场不合法");
        // 6. 校验临时证券类型合法性
        validateSecurityType(req.getTempSecurityType());
        int count = tempSecurityCodeMapper.queryTempSecurityCodeCount(req.getTempSecurityCode(), null);
        if (count > 0) {
            throw new BizException("临时证券代码已存在，tempSecurityCode=" + req.getTempSecurityCode());
        }
    }

    /**
     * 更新参数校验（临时信息只读不校验入参；正式证券必须选定主数据中的代码）
     *
     * 执行顺序：
     * 1. 校验主键 ID
     * 2. 校验正式证券代码必填（名称/市场/类型由主数据带出）
     *
     * @param req 证券临时代码查询或维护请求
     */
    private void validateUpdateReq(TempSecurityCodeReq req) {
        // 1. 校验主键 ID
        validateIdReq(req);
        // 2. 校验正式证券代码必填（名称/市场/类型由主数据带出）
        validateRequired(req.getSecurityCode(), "请选择正式证券");
    }

    /**
     * 查询并校验正式证券主数据
     *
     * 执行顺序：
     * 1. 按代码查询正式证券
     * 2. 查询当前代码的唯一市场
     *
     * @param securityCode 证券代码
     * @param allowDelisted Wind 换码允许读取退市证券，以原主档状态同步代码
     * @return 正式证券选项和市场
     */
    private TempSecurityCodeDto.FormalSecurityOption queryRequiredFormalSecurity(String securityCode, boolean allowDelisted) {
        TempSecurityCodeDto.FormalSecurityOption formalSecurity =
                tempSecurityCodeMapper.queryFormalSecurityByCode(securityCode, allowDelisted);
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
     *
     * 执行顺序：
     * 1. 检查主键 ID 不能为空
     *
     * @param req 证券临时代码查询或维护请求
     */
    private void validateIdReq(TempSecurityCodeReq req) {
        if (req == null || req.getId() == null) {
            throw new BizException("主键 ID 不能为空");
        }
    }

    /**
     * 校验必填字符串
     *
     * 执行顺序：
     * 1. 校验必填字符串，条件不满足时终止处理
     *
     * @param value 待解析的日期字符串
     * @param message 返回给调用方的处理信息
     */
    private void validateRequired(String value, String message) {
        if (value == null || value.trim().length() == 0) {
            throw new BizException(message);
        }
    }

    /**
     * 校验证券市场（仅允许 MarketCode 标准 11 码）
     *
     * 执行顺序：
     * 1. 校验证券市场（仅允许 MarketCode 标准 11 码），条件不满足时终止处理
     *
     * @param market 从主档或临时代码记录查询并校验通过的市场编码
     * @param message 返回给调用方的处理信息
     */
    private void validateMarket(String market, String message) {
        if (!MarketCode.isValid(market)) {
            throw new BizException(message + "，market=" + market);
        }
    }

    /**
     * 校验证券类型
     *
     * 执行顺序：
     * 1. 查询证券类型数量
     *
     * @param securityType 页面已知证券类型
     */
    private void validateSecurityType(String securityType) {
        int count = tempSecurityCodeMapper.querySecurityTypeCount(securityType);
        if (count <= 0) {
            throw new BizException("证券类型不存在或已删除，securityType=" + securityType);
        }
    }

    /**
     * 查询可操作临时代码
     *
     * 执行顺序：
     * 1. 查询临时代码原始记录
     *
     * @param id 临时代码记录 ID
     * @return 临时证券代码
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
     *
     * 执行顺序：
     * 1. 按ID查询临时证券代码
     *
     * @param id 临时代码记录 ID
     * @return 临时证券代码
     */
    private TempSecurityCodeBo queryExistingTempSecurityCode(Long id) {
        TempSecurityCodeBo bo = tempSecurityCodeMapper.queryTempSecurityCodeById(id);
        if (bo == null) {
            throw new BizException("临时代码不存在或已删除，id=" + id);
        }
        return bo;
    }

    /**
     * 新增临时证券占位主档
     *
     * 主体内评分以提交时查到的结果为准，查不到评分时留空
     *
     * 执行顺序：
     * 1. 临时代码已被证券主档占用时拒绝新增
     * 2. 按发行主体代码查询最新内评分
     * 3. 保存临时证券占位主档
     *
     * @param sourceBo 已保存的临时代码登记
     */
    private void addTempSecurityInfo(TempSecurityCodeBo sourceBo) {
        // 1. 临时代码已被证券主档占用时拒绝新增
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
        // 2. 按发行主体代码查询最新内评分
        bo.setInnerIssuerRating(tempSecurityCodeMapper.queryIssuerInnerRating(sourceBo.getTempCompanyCode()));
        bo.setTempIssueDate(sourceBo.getTempIssueDate());
        bo.setTempMaturityDate(sourceBo.getTempMaturityDate());
        bo.setUpdateTime(sourceBo.getCrteTime());
        bo.setSecuritySource("temporary");
        // 3. 写入占位主档，供后续调库查询
        tempSecurityCodeMapper.addSecurityInfo(bo);
    }

    /**
     * 禁用临时代码占位证券主数据
     *
     * 执行顺序：
     * 1. 从临时代码登记取得旧证券代码和本次更新时间
     * 2. 按旧临时代码更新占位主档的停用状态
     *
     * @param oldBo 原临时代码记录
     * @param updateTime 本次处理使用的日期或时间
     */
    private void disableTempSecurityInfo(TempSecurityCodeBo oldBo, Date updateTime) {
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        bo.setUpdateTime(updateTime);
        tempSecurityCodeMapper.editTempSecurityInfoToDisabled(bo);
    }

    /**
     * 更新临时代码记录，把普通池和 CRMW 中用到旧代码的记录改好
     * 临时代码转正按以下六种业务情况处理；Wind 换码复用相同处理步骤，目标仅为事件新码
     * 以下 T 表示旧临时代码，P 表示投资池，正式组表示目标正式券的全部市场代码
     *
     * ① T 只有临时代码记录，不在池里，也没有待审批申请：只完成转正，不新增调库记录
     * ② T申请调入P，尚未审批：主项换正式码，其他市场补关联项，沿用原审批
     * ③ T已在P，正式组不在P：T退出，全部正式市场代码统一进入，逐码记账
     * ④ T与正式组都在P：仅移除T，不重复调入正式组
     * ⑤ T已在P且申请调出：当前状态先转为正式组；原调出申请同步换码，等待原审批
     * ⑥ T在P1、P2等多个池：逐原池执行整组转换，同次转正统一提交或回滚
     *
     * 六种情况由 businessConverter 分为“当前状态”和“流程中申请”两个阶段处理，
     * 不重复执行情况⑤的当前状态更新；历史终态日志保留原证券代码和名称
     * 所有步骤沿用调用方事务，任一步骤失败时整次转正回滚
     *
     * 执行顺序：
     * 1. 整理旧代码、新代码、修改时间和操作来源，供后面的换码和替换日志使用
     * 2. 查普通池里的记录，检查待审批申请是否冲突、新代码能否进入这些池
     * 3. 查 CRMW 中使用旧代码的在池记录和待审批申请，检查新代码能否接着使用
     * 4. 检查通过后，把临时代码记录改为已更新并保存备注；后面失败时一起撤销
     * 5. 普通池中旧代码出库、新代码入库，更新待审批申请和快照，保存替换日志
     * 6. 按凭证更新 CRMW 标的的在池记录和待审批申请，保存快照和替换日志
     * 7. 每次成功转换都保存登记映射，无业务引用时也能追溯旧新代码
     *
     * @param oldBo 转正前的临时代码记录信息
     * @param newBo 本次选中的正式证券及操作来源
     * @param windCodeChange true 仅更新 Wind 事件的旧新码；false 处理目标正式券的全部市场代码
     */
    private void convertTempSecurityBusinessData(TempSecurityCodeBo oldBo, TempSecurityCodeBo newBo, boolean windCodeChange) {
        // 1. 整理旧代码、新代码、修改时间和操作来源，供后面的换码和替换日志使用
        TempSecurityCodeBo replaceBo = buildReplaceBo(oldBo, newBo);

        // 2. 查普通池里的记录，检查待审批申请是否冲突、新代码能否进入这些池
        TempSecurityCodeBusinessConverter.ConversionPlan conversionPlan = windCodeChange
                ? businessConverter.prepareWindCodeConversion(replaceBo)
                : businessConverter.prepareOrdinaryPoolConversion(replaceBo);
        // 3. 查 CRMW 中使用旧代码的在池记录和待审批申请，检查新代码能否接着使用
        businessConverter.prepareCrmwConversion(replaceBo, conversionPlan);
        // 4. 检查通过后，把临时代码记录改为已更新并保存备注；后面失败时一起撤销
        int updatedRows = tempSecurityCodeMapper.editTempSecurityCodeToUpdated(newBo);
        if (updatedRows != 1) {
            throw new BizException("更新临时代码失败，记录不存在或已不是临时状态，id=" + oldBo.getId());
        }

        // 5. 普通池中旧代码出库、新代码入库，更新待审批申请和快照，保存替换日志
        businessConverter.convertOrdinaryPoolBusinessData(conversionPlan, replaceBo);

        // 6. 按凭证更新 CRMW 标的的在池记录和待审批申请，保存快照和替换日志
        businessConverter.convertCrmwBusinessData(conversionPlan, replaceBo);

        // 7. 每次成功转换都保存登记映射，无业务引用时也能追溯旧新代码
        replaceBo.setReplaceTableName("rrs_temp_security_code");
        replaceBo.setReplaceRecordId(oldBo.getId());
        if (tempSecurityCodeMapper.addTempSecurityCodeUpdateLog(replaceBo) != 1) {
            throw new BizException("代码转换映射日志写入失败，登记ID=" + oldBo.getId()
                    + "，旧代码=" + oldBo.getTempSecurityCode() + "，新代码=" + newBo.getSecurityCode());
        }
    }

    /**
     * 组合原临时代码记录的旧码资料与目标证券信息，供业务转换和替换日志使用
     *
     * 执行顺序：
     * 1. 向后续日志构建传递人工或定时任务来源
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
     *
     * 执行顺序：
     * 1. 该临时代码已被调库业务使用，无法删除时终止处理
     *
     * @param bo 待转换的数据库记录
     */
    private void validateNoCoreReference(TempSecurityCodeBo bo) {
        int count = tempSecurityCodeMapper.queryCoreReferenceCount(bo);
        if (count > 0) {
            throw new BizException("该临时代码已被调库业务使用，无法删除");
        }
    }

    /**
     * 查询取消发行日期
     *
     * 执行顺序：
     * 1. 用当前日期减一天，按 yyyyMMdd 格式返回取消发行日期
     *
     * @return 取消发行日期
     */
    private String queryCancelIssueDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        return new SimpleDateFormat("yyyyMMdd").format(calendar.getTime());
    }

    /**
     * 查询临时代码详情
     *
     * 执行顺序：
     * 1. 按登记记录 ID 读取临时代码详情
     *
     * @param id 临时代码记录 ID
     * @return 临时证券代码
     */
    private TempSecurityCodeDto queryTempSecurityCodeDetail(Long id) {
        return tempSecurityCodeMapper.queryTempSecurityCodeDetail(id);
    }

    /**
     * 解析主体展示名称
     *
     * 执行顺序：
     * 1. 优先取已有主体全称，其次取简称
     * 2. 两者都为空时提示该主体缺少名称
     *
     * @param company 主体
     * @return 主体名称
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
