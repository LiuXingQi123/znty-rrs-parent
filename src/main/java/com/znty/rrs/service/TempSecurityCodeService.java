package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.common.enums.TempOperationType;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时代码管理服务，处理临时代码新增、查询、更新正式证券、取消发行和删除
 */
@Service
public class TempSecurityCodeService {

    /** 转正时临时出库原因（对齐老系统「债券临时代码调出」） */
    private static final String REASON_TEMP_OUT = "债券临时代码调出";
    /** 系统操作人 ID（对齐老系统 inputId=0） */
    private static final String SYSTEM_ADJUSTER_ID = "0";
    /** 系统操作人名称 */
    private static final String SYSTEM_ADJUSTER_NAME = "系统";
    /** 系统调出类型（对齐老系统 adjustType=5 语义） */
    private static final String ADJUST_TYPE_TEMP_OUT = "临时代码调出";

    /** 临时代码管理数据访问组件 */
    @Resource
    private TempSecurityCodeMapper tempSecurityCodeMapper;

    /** 证券池调库数据访问组件（复用写调库日志/池状态） */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /**
     * 分页查询临时代码列表
     */
    public PageResult<TempSecurityCodeDto> queryTempSecurityCodePage(TempSecurityCodeReq req) {
        if (req == null) {
            req = new TempSecurityCodeReq();
        }
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        // 查询临时代码分页列表
        java.util.List<TempSecurityCodeDto> list = tempSecurityCodeMapper.queryTempSecurityCodePage(req);
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
        bo.setTempSecurityMarket(req.getTempSecurityMarket());
        bo.setTempSecurityType(req.getTempSecurityType());
        bo.setTempMitigationCode(req.getTempMitigationCode());
        bo.setTempCompanyCode(req.getTempCompanyCode());
        // 解析主体展示名称
        bo.setTempCompanyNameSnapshot(resolveCompanyName(company));
        bo.setTempIssueDate(req.getTempIssueDate());
        bo.setTempMaturityDate(req.getTempMaturityDate());
        bo.setStatus(TempStatus.TEMPORARY.getCode());
        bo.setOperationType(TempOperationType.ADD.getCode());
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
        return tempSecurityCodeMapper.queryFormalSecurityOptionList(req);
    }

    /**
     * 更新临时代码为正式证券。
     * <p>对齐老系统 TempSecurityInfoTableAction#refreshTempSecurity 分叉逻辑：
     * <ul>
     *   <li>在途调库日志（audit_status=00/11）：只改证券字段，不写出/入库业务单</li>
     *   <li>已在池状态（audit_status=20）：每个池「临时出库 +（正式未在池则）正式入库」并写调库日志</li>
     *   <li>池/日志上 CRMW 字段指向临时码：仅字段替换</li>
     * </ul>
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
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
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
        bo.setOperationType(TempOperationType.UPDATE.getCode());
        bo.setUpdtTime(now);
        // 按老系统分叉处理在途日志与已在池状态
        convertTempSecurityBusinessData(oldBo, bo);
        // 禁用原临时代码占位证券主数据
        disableTempSecurityInfo(oldBo, now);
        tempSecurityCodeMapper.editTempSecurityCodeToUpdated(bo);
        // 查询更新后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
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
        bo.setOperationType(TempOperationType.CANCEL_ISSUE.getCode());
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
        bo.setOperationType(TempOperationType.DELETE.getCode());
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
     * 校验证券市场（仅允许 MarketCode 标准 8 码）
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
        bo.setSecurityMarket(sourceBo.getTempSecurityMarket());
        bo.setSecurityType(sourceBo.getTempSecurityType());
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
     * 转正时按老系统分叉处理业务数据。
     * <p>在途日志只改字段；已在池做出/入业务日志；CRMW 字段仅替换。
     */
    private void convertTempSecurityBusinessData(TempSecurityCodeBo oldBo, TempSecurityCodeBo newBo) {
        // 构建替换/日志公共参数
        TempSecurityCodeBo replaceBo = buildReplaceBo(oldBo, newBo);

        // 情况 A：在途调库日志证券引用 — 仅字段替换
        List<Long> pendingLogIds = tempSecurityCodeMapper.queryPendingAdjustLogSecurityReferenceIdList(replaceBo);
        if (!pendingLogIds.isEmpty()) {
            tempSecurityCodeMapper.editAdjustLogSecurityReference(replaceBo, pendingLogIds);
            // 记录在途调库日志替换
            addReplaceLogList(replaceBo, "ip_adjust_log", pendingLogIds);
        }

        // 情况 A-CRMW：在途调库日志 CRMW 字段 — 仅字段替换
        List<Long> pendingCrmwLogIds = tempSecurityCodeMapper.queryPendingAdjustLogCrmwReferenceIdList(replaceBo);
        if (!pendingCrmwLogIds.isEmpty()) {
            tempSecurityCodeMapper.editAdjustLogCrmwReference(replaceBo, pendingCrmwLogIds);
            // 记录在途 CRMW 日志替换
            addReplaceLogList(replaceBo, "ip_adjust_log.crmw", pendingCrmwLogIds);
        }

        // 情况 B：已在池 — 临时出库 +（条件）正式入库
        List<IpPoolStatusBo> poolStatusList = tempSecurityCodeMapper.queryActivePoolStatusList(replaceBo);
        if (poolStatusList != null) {
            for (IpPoolStatusBo poolStatus : poolStatusList) {
                // 单池执行出/入
                convertOnePoolStatus(poolStatus, replaceBo, false);
            }
        }

        // 情况 C：池状态上 CRMW 指向临时码 — 仅字段替换
        List<Long> poolCrmwIds = tempSecurityCodeMapper.queryPoolStatusCrmwReferenceIdList(replaceBo);
        if (!poolCrmwIds.isEmpty()) {
            tempSecurityCodeMapper.editPoolStatusCrmwReference(replaceBo, poolCrmwIds);
            // 记录池状态 CRMW 字段替换
            addReplaceLogList(replaceBo, "ip_pool_status.crmw", poolCrmwIds);
        }

        // CRMW 独立池表：security 为临时码 — 按在池出/入
        List<IpPoolStatusBo> crmwPoolList = tempSecurityCodeMapper.queryActiveCrmwPoolStatusList(replaceBo);
        if (crmwPoolList != null) {
            for (IpPoolStatusBo poolStatus : crmwPoolList) {
                // CRMW 池单条出/入
                convertOnePoolStatus(poolStatus, replaceBo, true);
            }
        }

        // CRMW 独立池表：crmw 字段指向临时码 — 仅字段替换
        List<Long> crmwPoolCrmwIds = tempSecurityCodeMapper.queryCrmwPoolStatusCrmwReferenceIdList(replaceBo);
        if (!crmwPoolCrmwIds.isEmpty()) {
            tempSecurityCodeMapper.editCrmwPoolStatusCrmwReference(replaceBo, crmwPoolCrmwIds);
            // 记录 CRMW 池 CRMW 字段替换
            addReplaceLogList(replaceBo, "ip_pool_status_crmw.crmw", crmwPoolCrmwIds);
        }
    }

    /**
     * 单个池状态：临时出库 + 正式未在池则入库（对齐老系统 poolstatus 循环）。
     *
     * @param crmwPool true 时写 ip_pool_status_crmw，否则写 ip_pool_status
     */
    private void convertOnePoolStatus(IpPoolStatusBo poolStatus, TempSecurityCodeBo replaceBo, boolean crmwPool) {
        Date now = replaceBo.getUpdateTime();
        // ① 写临时出库调库日志（直通通过）
        IpAdjustLogBo outLog = buildTempOutAdjustLog(poolStatus, now);
        securityPoolAdjustMapper.addAdjustLog(outLog);

        // ② 软删临时在池记录
        if (crmwPool) {
            tempSecurityCodeMapper.deleteCrmwPoolStatusSoftById(poolStatus.getId(), now);
        } else {
            tempSecurityCodeMapper.deletePoolStatusSoftById(poolStatus.getId(), now);
        }
        // 记录池状态替换日志
        addReplaceLogList(replaceBo, crmwPool ? "ip_pool_status_crmw" : "ip_pool_status",
                java.util.Collections.singletonList(poolStatus.getId()));

        // ③ 正式码已在该池则不再入库
        int formalCount = crmwPool
                ? tempSecurityCodeMapper.queryActiveCrmwPoolStatusCount(
                        replaceBo.getSecurityCode(), replaceBo.getSecurityType(), poolStatus.getTargetPoolId())
                : tempSecurityCodeMapper.queryActivePoolStatusCount(
                        replaceBo.getSecurityCode(), replaceBo.getSecurityType(), poolStatus.getTargetPoolId());
        if (formalCount > 0) {
            return;
        }

        // ④ 写正式入库调库日志（继承原原因/调整人等）
        IpAdjustLogBo inLog = buildFormalInAdjustLog(poolStatus, replaceBo, now);
        securityPoolAdjustMapper.addAdjustLog(inLog);

        // ⑤ 新建正式在池状态（关联刚写入的入库日志）
        if (crmwPool) {
            IpPoolStatusBo newStatus = buildFormalPoolStatus(poolStatus, replaceBo, inLog.getId(), now);
            tempSecurityCodeMapper.addCrmwPoolStatus(newStatus);
        } else {
            // addPoolStatus 以 IpAdjustLogBo 入参写 ip_pool_status，adjustLogId 指向入库日志
            inLog.setAdjustLogId(inLog.getId());
            securityPoolAdjustMapper.addPoolStatus(inLog);
        }
    }

    /**
     * 构建临时出库调库日志
     */
    private IpAdjustLogBo buildTempOutAdjustLog(IpPoolStatusBo poolStatus, Date now) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setSecurityCode(poolStatus.getSecurityCode());
        log.setSecurityShortName(poolStatus.getSecurityShortName());
        log.setSecurityType(poolStatus.getSecurityType());
        log.setCrmwName(poolStatus.getCrmwName());
        log.setCrmwScode(poolStatus.getCrmwScode());
        log.setCrmwMktcode(poolStatus.getCrmwMktcode());
        log.setCrmwStype(poolStatus.getCrmwStype());
        log.setAdjustType(ADJUST_TYPE_TEMP_OUT);
        log.setAdjustMode(AdjustMode.OUT.getCode());
        log.setAdjustBatchNo(UUID.randomUUID().toString().replace("-", ""));
        log.setTargetPoolId(poolStatus.getTargetPoolId());
        log.setTargetPoolName(poolStatus.getTargetPoolName());
        log.setPoolType(poolStatus.getPoolType());
        log.setAuditStatus(AuditStatus.APPROVED.getCode());
        log.setAdjusterId(SYSTEM_ADJUSTER_ID);
        log.setAdjusterName(SYSTEM_ADJUSTER_NAME);
        log.setAdjustReason(REASON_TEMP_OUT);
        log.setAdjustAdvice(REASON_TEMP_OUT);
        log.setSubmitTime(now);
        return log;
    }

    /**
     * 构建正式入库调库日志（继承原池状态业务字段）
     */
    private IpAdjustLogBo buildFormalInAdjustLog(IpPoolStatusBo poolStatus, TempSecurityCodeBo replaceBo, Date now) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setSecurityCode(replaceBo.getSecurityCode());
        log.setSecurityShortName(replaceBo.getSecurityName());
        log.setSecurityType(replaceBo.getSecurityType());
        log.setCrmwName(poolStatus.getCrmwName());
        log.setCrmwScode(poolStatus.getCrmwScode());
        log.setCrmwMktcode(poolStatus.getCrmwMktcode());
        log.setCrmwStype(poolStatus.getCrmwStype());
        log.setAdjustType(poolStatus.getAdjustType());
        log.setAdjustMode(AdjustMode.IN.getCode());
        log.setAdjustBatchNo(UUID.randomUUID().toString().replace("-", ""));
        log.setTargetPoolId(poolStatus.getTargetPoolId());
        log.setTargetPoolName(poolStatus.getTargetPoolName());
        log.setPoolType(poolStatus.getPoolType());
        log.setAuditStatus(AuditStatus.APPROVED.getCode());
        // 调整人/原因继承原在池记录
        log.setAdjusterId(poolStatus.getAdjusterId() != null ? poolStatus.getAdjusterId() : SYSTEM_ADJUSTER_ID);
        log.setAdjusterName(poolStatus.getAdjusterName() != null ? poolStatus.getAdjusterName() : SYSTEM_ADJUSTER_NAME);
        log.setAdjustReason(poolStatus.getAdjustReason());
        log.setAdjustAdvice(poolStatus.getAdjustAdvice());
        log.setSubmitTime(now);
        return log;
    }

    /**
     * 构建正式在池状态（CRMW 池表用）
     */
    private IpPoolStatusBo buildFormalPoolStatus(IpPoolStatusBo oldStatus, TempSecurityCodeBo replaceBo,
                                                 Long adjustLogId, Date now) {
        IpPoolStatusBo status = new IpPoolStatusBo();
        status.setSecurityCode(replaceBo.getSecurityCode());
        status.setSecurityShortName(replaceBo.getSecurityName());
        status.setSecurityType(replaceBo.getSecurityType());
        status.setCrmwName(oldStatus.getCrmwName());
        status.setCrmwScode(oldStatus.getCrmwScode());
        status.setCrmwMktcode(oldStatus.getCrmwMktcode());
        status.setCrmwStype(oldStatus.getCrmwStype());
        status.setAdjustType(oldStatus.getAdjustType());
        status.setAdjustMode(AdjustMode.IN.getCode());
        status.setAdjustBatchNo(UUID.randomUUID().toString().replace("-", ""));
        status.setAdjustLogId(adjustLogId);
        status.setTargetPoolId(oldStatus.getTargetPoolId());
        status.setTargetPoolName(oldStatus.getTargetPoolName());
        status.setPoolType(oldStatus.getPoolType());
        status.setAuditStatus(AuditStatus.APPROVED.getCode());
        status.setAdjusterId(oldStatus.getAdjusterId() != null ? oldStatus.getAdjusterId() : SYSTEM_ADJUSTER_ID);
        status.setAdjusterName(oldStatus.getAdjusterName() != null ? oldStatus.getAdjusterName() : SYSTEM_ADJUSTER_NAME);
        status.setAdjustReason(oldStatus.getAdjustReason());
        status.setAdjustAdvice(oldStatus.getAdjustAdvice());
        status.setSubmitTime(now);
        status.setEntryTime(now);
        status.setIsDeleted(0);
        status.setCrteTime(now);
        status.setUpdtTime(now);
        return status;
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
        bo.setReplaceStatus("success");
        return bo;
    }

    /**
     * 批量写入替换日志
     */
    private void addReplaceLogList(TempSecurityCodeBo replaceBo, String tableName, List<Long> recordIds) {
        for (Long recordId : recordIds) {
            replaceBo.setReplaceTableName(tableName);
            replaceBo.setReplaceRecordId(recordId);
            tempSecurityCodeMapper.addTempSecurityCodeUpdateLog(replaceBo);
        }
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
        return new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
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
