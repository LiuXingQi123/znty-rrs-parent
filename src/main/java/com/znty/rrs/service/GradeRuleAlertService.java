package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.util.AlertContextKey;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertDto;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertReq;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertScanDto;
import com.znty.rrs.entity.graderulealert.AbsAlertCandidateDto;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.GradeRuleAlertMapper;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 不符合主体债入池规则提醒（含ABS债，担保债取快照担保人内评）
 *
 * 执行顺序：
 * 1. 扫描已在信用债分级库且当前生效的债券
 * 2. 按当前主体债入池规则复核普通债、ABS 和担保债，其中 ABS 使用主档发行人内评，担保债按本池入库快照确定当时选定的担保人，再查询该担保人最新内评
 * 3. 新增或刷新不符合规则的待办，沿用已处理结论
 * 4. 关闭本轮不再命中的旧待办
 * 5. 返回本轮命中、失效和仍待处理数量
 *
 * 对齐老系统 InconformityMaingrade2Job，只生成提醒，不自动出池，人工处理入口为“我的事宜”分级规则提醒页签
 */
@Slf4j
@Service
public class GradeRuleAlertService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "bond_grade_inconformity_alert";

    /** 任务名称 */
    public static final String TASK_NAME = "不符合主体债入池规则提醒（含ABS债，担保债取快照担保人内评）";

    /** 待处理 */
    public static final String STATUS_OPEN = "00";

    /** 已处理 */
    public static final String STATUS_DONE = "20";

    /** ABS 主体联动提醒类型 */
    private static final String RULE_TYPE_ABS = "absCompanySync";

    private static final String PARAM_HELP =
            "参数说明：本任务无需扩展参数，请将 param_json 留空\n"
                    + "处理范围：扫描已在信用债 1～5 级且生效的债券，含 ABS 债，不含境外债\n"
                    + "复核规则：按当前主体债入池规则复核，包括特殊债、担保债、观察池和重点观察名单\n"
                    + "评级来源：ABS 的评级主体在确认权益人时已固定到主档发行人字段，直接取主档发行人内评，不查询入库快照；"
                    + "担保债可能对应多个担保人，按当前在池记录的入库快照取当时选定的担保人，再查该担保人最新内评，"
                    + "缺少担保人信息则本轮失败并回滚\n"
                    + "本轮命中：本轮仍不符合规则，新增或刷新待办\n"
                    + "沿用结论：同券同池、同档位同原因的待办已由研究员标记已处理或去调库时，本轮不再重复生成\n"
                    + "本轮失效：曾待处理但本轮已合规或不在扫描范围的待办，状态置为 99\n"
                    + "仍待处理：执行结束后 alert_status=00 的待办数量，可在“我的事宜”查看\n"
                    + "处理方式：只生成提醒，不自动出池；需由人工在“我的事宜”分级规则提醒页签处理";

    /** 待办 Mapper */
    @Resource
    private GradeRuleAlertMapper gradeRuleAlertMapper;

    /** 证券池调库（复用入库规则评估） */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /** 证券主数据 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** ABS 主体身份与提醒处理 */
    @Resource
    private AbsIdentityService absIdentityService;

    /** ABS 主体联动依据摘要计算 */
    @Resource
    private AbsCompanySyncService absCompanySyncService;

    /** ABS 提醒状态更新 */
    @Resource
    private AbsIdentityMapper absIdentityMapper;

    /** 确认主体后补写池状态时读取投资池关系 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 执行不符合主体债入池规则提醒（含ABS债，担保债取快照担保人内评）
     *
     * 执行顺序：
     * 1. 记录扫描条件并查询在池分级债券
     * 2. 逐条复核主体债入池规则并生成或刷新待办
     * 3. 关闭本轮未再命中的旧待办
     * 4. 统计仍待处理数量并返回任务结果
     *
     * @return 本轮任务执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskResult execute() {
        Date start = new Date();
        TaskDetailLog detailLog = new TaskDetailLog();
        detailLog.line("开始扫描：已在信用债 1～5 级且生效的债券是否仍符合主体债入池规则");
        detailLog.line("评级来源：ABS 取主档发行人内评；担保债按本池入库快照取当时选定的担保人，再查该担保人最新内评");
        detailLog.line("扫描条件：ip_pool_status.is_deleted=0、audit_status=20；"
                + "ip_investment_pool.is_deleted=0、pool_type=credit_bond、inner_sort BETWEEN 1 AND 5、"
                + "pool_level为空或=2");

        // 1. 查询在池分级债券样本
        List<GradeRuleAlertScanDto> inPoolList = gradeRuleAlertMapper.queryGradedBondInPoolList();
        if (inPoolList == null) {
            inPoolList = new ArrayList<GradeRuleAlertScanDto>();
        }
        detailLog.line("扫描样本（在池分级库记录）" + inPoolList.size() + " 条");

        int hitCount = 0;
        int skippedCount = 0;
        Date scanTime = new Date();
        // 2. 准备与数据库 DATETIME 精度一致的扫描时间
        String scanTimeText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(scanTime);
        // 3. 逐条复核规则并生成或刷新待办
        for (GradeRuleAlertScanDto row : inPoolList) {
            if (row.getSecurityCode() == null || row.getCurrentPoolId() == null) {
                continue;
            }
            // 3.1 ABS 用主档发行人内评，担保债按本池快照取担保人再查最新内评
            String fail = securityPoolAdjustService.evaluateGradedInboundForPool(
                    row.getSecurityCode(), row.getCurrentPoolId(), row.getAdjustLogId());
            if (fail == null || fail.isEmpty()) {
                continue;
            }
            // 3.2 按依据新增或刷新待办，沿用已处理结论
            if (upsertOpenAlert(row, fail, scanTime)) {
                hitCount++;
            } else {
                skippedCount++;
            }
        }
        detailLog.line("本轮命中（新增/刷新待办）" + hitCount + " 条");
        detailLog.line("沿用已处理结论（同一依据不再提醒）" + skippedCount + " 条");

        // 4. 关闭本轮未再命中的旧待处理待办
        int invalidCount = gradeRuleAlertMapper.editStaleOpenAlertInvalid(scanTimeText);
        detailLog.line("本轮失效（曾待处理、本轮未再命中，已置 99）" + invalidCount + " 条");

        // 5. 统计执行后仍待处理数量，供摘要与影响条数
        int openCount = gradeRuleAlertMapper.queryOpenAlertCount();
        detailLog.line("仍待处理（alert_status=00，我的事宜可见）" + openCount + " 条");

        String message = "扫描完成：本轮命中 " + hitCount + " 条，沿用已处理 " + skippedCount
                + " 条，本轮失效 " + invalidCount + " 条，仍待处理 " + openCount + " 条";
        return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message,
                openCount, start, System.currentTimeMillis() - start.getTime(), detailLog.build());
    }

    /**
     * 分页查询待办
     *
     * @param req 查询条件
     * @return 分页结果
     */
    public PageResult<GradeRuleAlertDto> queryAlertPage(GradeRuleAlertReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<IpGradeRuleAlertBo> rows = gradeRuleAlertMapper.queryAlertPage(req);
        PageInfo<IpGradeRuleAlertBo> pageInfo = new PageInfo<IpGradeRuleAlertBo>(rows);
        List<GradeRuleAlertDto> dtoList = new ArrayList<GradeRuleAlertDto>();
        for (IpGradeRuleAlertBo row : rows) {
            GradeRuleAlertDto dto = new GradeRuleAlertDto();
            BeanUtils.copyProperties(row, dto);
            dtoList.add(dto);
        }
        return new PageResult<GradeRuleAlertDto>(dtoList, pageInfo.getTotal(),
                req.getPageIndex(), req.getPageSize());
    }

    /**
     * 人工标记已处理，同时承接「标记已处理」和「去调库」两个入口
     *
     * 只关闭待办并把人工结论绑定到当前依据，不改池状态；
     * 扫描时同一依据不再生成新待办，依据变化后仍会重新提醒
     *
     * 执行顺序：
     * 1. 校验待办存在且仍待处理，ABS 主体联动提醒走确认主体或忽略入池
     * 2. 按证券、当前池、档位和不符合原因生成依据摘要
     * 3. 条件更新为已处理并回写摘要，返回最新待办
     *
     * @param req 含 id 与处理人
     * @return 更新后的待办
     */
    @Transactional(rollbackFor = Exception.class)
    public GradeRuleAlertDto editAlertProcessed(GradeRuleAlertReq req) {
        // 1. 校验请求和待办状态
        if (req.getId() == null) {
            throw new BizException("待办 ID 不能为空");
        }
        IpGradeRuleAlertBo exist = gradeRuleAlertMapper.queryAlertById(req.getId());
        if (exist == null) {
            throw new BizException("待办不存在");
        }
        if (!STATUS_OPEN.equals(exist.getAlertStatus())) {
            throw new BizException("仅待处理记录可标记已处理");
        }
        if (RULE_TYPE_ABS.equals(exist.getRuleType())) {
            throw new BizException("ABS 主体联动提醒请使用确认主体或忽略入池");
        }
        // 2. 回填处理人和处理时间
        Date now = new Date();
        exist.setDealUserId(req.getCurrentUserId());
        exist.setDealUserName(req.getCurrentUserName());
        exist.setDealTime(now);
        exist.setUpdtTime(now);
        // 3. 按当前依据绑定人工结论，下一轮同依据直接跳过
        exist.setContextKey(buildGradeContextKey(exist.getSecurityCode(), exist.getCurrentPoolId(),
                exist.getCurrentSort(), exist.getFailReason()));
        // 4. 条件更新待办为已处理并返回最新记录
        int n = gradeRuleAlertMapper.editAlertProcessed(exist);
        if (n == 0) {
            throw new BizException("待办状态已变化，请刷新后重试");
        }
        GradeRuleAlertDto dto = new GradeRuleAlertDto();
        BeanUtils.copyProperties(gradeRuleAlertMapper.queryAlertById(req.getId()), dto);
        return dto;
    }

    /** 查询 ABS 规则提醒中的普通权益人候选 */
    public List<AbsAlertCandidateDto> queryAbsCandidates(String securityCode) {
        SecurityInfoBo security = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
        if (security == null || !CreditBondSpecialInboundRule.isAbs(security)) {
            throw new BizException("证券不是 ABS 或主档不存在");
        }
        List<RelatedRatingCompanyDto> related = absIdentityService.queryRelatedRatingCompanies(securityCode);
        List<AbsAlertCandidateDto> candidates = new ArrayList<>();
        for (RelatedRatingCompanyDto item : related) {
            AbsAlertCandidateDto candidate = new AbsAlertCandidateDto();
            candidate.setCompanyCode(item.getCompanyCode());
            candidate.setCompanyName(item.getCompanyName());
            candidate.setInnerIssuerRating(item.getInnerRating());
            candidate.setRatingBondissuer(item.getRatingBondissuer());
            candidate.setRatingBondissuerAgency(item.getRatingBondissuerAgency());
            candidate.setRelationTypeCode(item.getRelationTypeCode());
            candidates.add(candidate);
        }
        return candidates;
    }

    /**
     * 处理 ABS 规则提醒：确认主体或忽略本次入池
     *
     * 执行顺序：
     * 1. 校验提醒和处理动作
     * 2. 按确认主体或忽略入池分支更新业务数据
     * 3. 更新提醒状态并返回最新待办
     *
     * @param req ABS 提醒处理请求
     * @return 更新后的待办
     */
    @Transactional(rollbackFor = Exception.class)
    public GradeRuleAlertDto processAbsAlert(GradeRuleAlertReq req) {
        // 1. 校验提醒和处理动作
        if (req.getId() == null) {
            throw new BizException("待办 ID 不能为空");
        }
        IpGradeRuleAlertBo alert = gradeRuleAlertMapper.queryAlertById(req.getId());
        if (alert == null || !RULE_TYPE_ABS.equals(alert.getRuleType())) {
            throw new BizException("ABS 主体联动提醒不存在");
        }
        if (!STATUS_OPEN.equals(alert.getAlertStatus())) {
            throw new BizException("仅待处理的 ABS 主体联动提醒可以操作");
        }
        // 2. 回填处理人和处理时间
        Date now = new Date();
        alert.setDealUserId(req.getCurrentUserId());
        alert.setDealUserName(req.getCurrentUserName());
        alert.setUpdtTime(now);
        if ("noNeed".equals(req.getDecision())) {
            IpAdjustLogBo pending = absIdentityMapper.queryPendingScheduledInboundLog(
                    alert.getSecurityCode(), alert.getCurrentPoolId());
            if (pending != null && absIdentityMapper.editPendingScheduledInboundInvalid(pending.getId()) != 1) {
                throw new BizException("ABS 待确认入池流程状态已变化，请刷新后重试");
            }
            // 2.1 清理旧版本可能已经落池的被动记录
            absIdentityMapper.deletePassiveInboundLog(alert.getSecurityCode(), alert.getCurrentPoolId());
            absIdentityMapper.deletePassiveInboundStatus(alert.getSecurityCode(), alert.getCurrentPoolId());
            alert.setIssuerCode(null);
            alert.setIssuerName(null);
            alert.setFailReason("研究员确认无需入池");
        } else if ("confirm".equals(req.getDecision())) {
            SecurityInfoBo master = securityPoolAdjustMapper.querySecurityBoByCode(alert.getSecurityCode());
            if (master == null || !CreditBondSpecialInboundRule.isAbs(master)) {
                throw new BizException("ABS 主档不存在或证券类型不正确");
            }
            absIdentityService.applySelection(master, req.getIssuerCode(), req.getIssuerName(),
                    req.getInnerIssuerRating(), req.getRatingBondissuer(), req.getRatingBondissuerAgency());
            absIdentityService.validateConfirmedIdentity(alert.getSecurityCode(), req.getIssuerCode());
            confirmPendingInbound(master, alert);
            alert.setIssuerCode(master.getIssuerCode());
            alert.setIssuerName(master.getIssuer());
            alert.setFailReason("研究员确认主体[" + master.getIssuerCode() + "]，已完成入池");
            // 2.2 按确认后的身份重算依据摘要，后续同主体同依据不再提醒
            alert.setContextKey(absCompanySyncService.resolveContextKey(
                    alert.getSecurityCode(), master.getIssuerCode(), alert.getCurrentPoolId()));
        } else {
            throw new BizException("ABS 提醒处理动作不正确");
        }
        // 3. 更新提醒状态并返回最新待办
        if (absIdentityMapper.editAbsAlertDecision(alert) != 1) {
            throw new BizException("ABS 主体联动提醒状态已变化，请刷新后重试");
        }
        GradeRuleAlertDto result = new GradeRuleAlertDto();
        BeanUtils.copyProperties(gradeRuleAlertMapper.queryAlertById(req.getId()), result);
        return result;
    }

    /**
     * 研究员确认 ABS 主体后完成主体落档、池状态落地及关系池调出
     *
     * 执行顺序：
     * 1. 查询待确认入池日志并处理历史兼容情况
     * 2. 完成主体身份和待确认日志更新
     * 3. 写入池状态并处理关系池调出
     *
     * @param master 已确认的主体主档
     * @param alert ABS 规则提醒
     */
    private void confirmPendingInbound(SecurityInfoBo master, IpGradeRuleAlertBo alert) {
        IpAdjustLogBo pending = absIdentityMapper.queryPendingScheduledInboundLog(
                alert.getSecurityCode(), alert.getCurrentPoolId());
        if (pending == null) {
            // 1. 兼容改造前已经生效的被动入池记录，只补写主档主体身份
            if (absIdentityMapper.editApprovedIdentity(master) != 1) {
                throw new BizException("ABS 主体确认保存失败");
            }
            return;
        }
        if (!StringUtils.hasText(pending.getSecurityShortName())
                && StringUtils.hasText(master.getShortName())) {
            if (absIdentityMapper.editPendingScheduledInboundSecurityShortName(
                    pending.getId(), master.getShortName()) != 1) {
                throw new BizException("ABS 待确认入池日志简称回填失败");
            }
            pending.setSecurityShortName(master.getShortName());
        }
        List<Long> currentPoolIds = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(
                pending.getSecurityCode());
        if (currentPoolIds != null && currentPoolIds.contains(pending.getTargetPoolId())) {
            // 1.1 其他链路已经先行落池时，只关闭重复的待确认日志并补写主体身份
            if (absIdentityMapper.editPendingScheduledInboundInvalid(pending.getId()) != 1) {
                throw new BizException("ABS 待确认入池流程状态已变化，请刷新后重试");
            }
            if (absIdentityMapper.editApprovedIdentity(master) != 1) {
                throw new BizException("ABS 主体确认保存失败");
            }
            return;
        }
        // 2. 更新待确认日志和确认后的主体信息
        String confirmedRemark = "（研究员确认主体：" + master.getIssuerCode() + "，已完成入池）";
        String confirmedReason = pending.getAdjustReason();
        if (!StringUtils.hasText(confirmedReason)) {
            confirmedReason = "ABS主体自动联动入池" + confirmedRemark;
        } else if (confirmedReason.contains("（待确认）")) {
            confirmedReason = confirmedReason.replace("（待确认）", confirmedRemark);
        } else if (!confirmedReason.contains("研究员确认主体")) {
            confirmedReason = confirmedReason + confirmedRemark;
        }
        pending.setAdjustReason(confirmedReason);
        if (absIdentityMapper.editPendingScheduledInboundApproved(pending.getId(), confirmedReason) != 1) {
            throw new BizException("ABS 待确认入池流程状态已变化，请刷新后重试");
        }
        // 3. 写入池状态并处理关系池调出
        pending.setAuditStatus("20");
        pending.setAdjustLogId(pending.getId());
        if (securityPoolAdjustMapper.addPoolStatus(pending) != 1) {
            throw new BizException("ABS 主体确认后入池失败");
        }
        if (absIdentityMapper.editApprovedIdentity(master) != 1) {
            throw new BizException("ABS 主体确认保存失败");
        }
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList();
        if (pools != null) {
            for (InvestmentPoolBo pool : pools) {
                if (pool != null && pool.getId() != null) {
                    poolMap.put(pool.getId(), pool);
                }
            }
        }
        List<PoolRelationBo> relations = securityPoolAdjustMapper.queryAllPoolRelationList();
        AutoAdjustRelationHelper.autoOutCurrentRelationPools(
                pending, currentPoolIds, poolMap, relations, securityPoolAdjustMapper);
    }

    /**
     * 写入或刷新待处理待办，同一依据已处理时沿用人工结论
     *
     * 执行顺序：
     * 1. 按证券、当前池、档位和不符合原因生成依据摘要
     * 2. 已有待处理待办时直接刷新，保证同券同池只有一条
     * 3. 没有待处理待办时，同一依据已处理过则跳过，否则新增
     *
     * @param row      在池行
     * @param fail     不符合原因
     * @param scanTime 本轮扫描时间
     * @return true=已新增或刷新待办；false=同一依据已处理，本轮跳过
     */
    private boolean upsertOpenAlert(IpGradeRuleAlertBo row, String fail, Date scanTime) {
        // 1. 生成本轮依据摘要
        String contextKey = buildGradeContextKey(row.getSecurityCode(), row.getCurrentPoolId(),
                row.getCurrentSort(), fail);
        // 2. 查询是否已有同券同池待处理待办
        IpGradeRuleAlertBo open = gradeRuleAlertMapper.queryOpenAlert(row.getSecurityCode(), row.getCurrentPoolId());
        // 3. 补充特殊债类型说明
        SecurityInfoBo sec = securityPoolAdjustMapper.querySecurityBoByCode(row.getSecurityCode());
        String specialDesc = buildSpecialTypeDesc(sec, row.getSecurityCode());
        if (open == null) {
            // 4. 同一依据已由研究员标记已处理或去调库时，不再重复生成待办
            if (hasProcessedSameBasis(row.getSecurityCode(), row.getCurrentPoolId(), contextKey)) {
                return false;
            }
            row.setFailReason(fail);
            row.setSpecialTypeDesc(specialDesc);
            row.setAlertStatus(STATUS_OPEN);
            row.setContextKey(contextKey);
            row.setLastScanTime(scanTime);
            row.setCrteTime(scanTime);
            row.setUpdtTime(scanTime);
            // 5. 新增待处理待办
            gradeRuleAlertMapper.addAlert(row);
            return true;
        }
        open.setSecurityShortName(row.getSecurityShortName());
        open.setIssuerCode(row.getIssuerCode());
        open.setIssuerName(row.getIssuerName());
        open.setCurrentPoolName(row.getCurrentPoolName());
        open.setCurrentSort(row.getCurrentSort());
        open.setFailReason(fail);
        open.setSpecialTypeDesc(specialDesc);
        open.setContextKey(contextKey);
        open.setLastScanTime(scanTime);
        open.setUpdtTime(scanTime);
        // 6. 刷新已有待处理待办
        gradeRuleAlertMapper.editAlertScan(open);
        return true;
    }

    /**
     * 判断同券同池最近一条待办是否已按相同依据处理
     *
     * @param securityCode  证券代码
     * @param currentPoolId 当前池
     * @param contextKey    本轮依据摘要
     * @return true=已处理且依据未变
     */
    private boolean hasProcessedSameBasis(String securityCode, Long currentPoolId, String contextKey) {
        IpGradeRuleAlertBo latest = gradeRuleAlertMapper.queryLatestGradeAlert(securityCode, currentPoolId);
        return latest != null && STATUS_DONE.equals(latest.getAlertStatus())
                && contextKey.equals(latest.getContextKey());
    }

    /**
     * 生成分级规则提醒依据摘要
     *
     * 包内可见，便于单元测试直接校验同依据复用口径
     *
     * @param securityCode  证券代码
     * @param currentPoolId 当前池
     * @param currentSort   当前档位
     * @param failReason    不符合原因
     * @return 十六进制摘要
     */
    static String buildGradeContextKey(String securityCode, Long currentPoolId,
                                       Integer currentSort, String failReason) {
        return AlertContextKey.of(securityCode, String.valueOf(currentPoolId),
                String.valueOf(currentSort), failReason);
    }

    /**
     * 拼特殊类型说明，便于列表一眼看到为何被扫到
     *
     * @param sec          证券
     * @param securityCode 证券代码
     * @return 说明
     */
    private String buildSpecialTypeDesc(SecurityInfoBo sec, String securityCode) {
        List<String> parts = new ArrayList<String>();
        if (CreditBondSpecialInboundRule.isPrivateBond(sec)) {
            parts.add("私募");
        }
        if (CreditBondSpecialInboundRule.isPerpetual(sec)) {
            parts.add("永续");
        }
        if (CreditBondSpecialInboundRule.isSubordinated(sec)) {
            parts.add("次级");
        }
        if (CreditBondSpecialInboundRule.isAbs(sec)) {
            parts.add("ABS");
        }
        if (CreditBondSpecialInboundRule.isGuaranteed(sec)) {
            parts.add("担保");
        }
        if (CreditBondSpecialInboundRule.isInright(sec)) {
            parts.add("含权");
        }
        if (securityPoolAdjustMapper.querySecurityInObservePool(securityCode)
                || securityPoolAdjustMapper.queryIssuerInObservePool(securityCode)) {
            parts.add("观察池");
        }
        if (securityPoolAdjustMapper.querySecurityInRestrictedPool(securityCode)
                || securityPoolAdjustMapper.queryIssuerInRestrictedPool(securityCode)) {
            parts.add("重点观察名单");
        }
        if (parts.isEmpty()) {
            return "普通债/内评或期限变化";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
