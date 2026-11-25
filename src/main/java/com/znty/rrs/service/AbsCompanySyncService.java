package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AdjustType;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.util.AlertContextKey;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 处理 ABS 债券随主体入池和出池的联动校验
 *
 * 定时任务 {@link CompanyAbsBondAutoInService} 在实际调库前调用本类，判断 ABS 能否自动跟随主体进出池
 * 已确认主体的 ABS 只跟随该主体，在库或在途的身份冲突由 {@link AbsIdentityService#validateIdentity(String, String)} 校验
 * 未确认主体的 ABS 触发入池时，只生成规则提醒和待确认日志，等研究员确认主体
 * ABS 有债券独立入池依据或未结束的调库流程等出池保护时，只提醒研究员处理
 * 人工调库通过后记录研究员的结论，联动依据未变时沿用，避免重复提醒或反向自动调库
 */
@Service
public class AbsCompanySyncService {

    /** 未确认 ABS 的待确认入池日志类型，人工主体调库与定时任务共用，不进入普通审批流程 */
    public static final String ABS_PENDING_FLOW_TYPE = "absIdentityPending";

    /** 自动任务操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";

    /** 自动任务操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";

    /** ABS 身份与联动查询 */
    @Resource
    private AbsIdentityMapper absIdentityMapper;

    /** 已有调库快照查询 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 定时对账批量读取在途步骤 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;

    /** 投资池查询，用于补写待确认日志的池类型 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 原有黑名单质押库硬限制，人工和自动调整共用 */
    @Resource
    private PledgeBlacklistRuleService pledgeBlacklistRuleService;

    /** 身份判定与关联主体计算 */
    @Resource
    private AbsIdentityService absIdentityService;

    /**
     * 主体联动前决定自动处理还是生成规则提醒，适用于任意目标池
     *
     * 执行顺序：
     * 1. 将债券目标池同时作为主体依据池，交给联动判断入口处理
     *
     * @param security 关联证券
     * @param triggerCompanyCode 触发本次联动的主体
     * @param poolId 实际目标池
     * @param poolName 实际目标池名称
     * @param inbound 是否调入
     * @return true=允许继续自动联动，false=跳过本次自动联动
     */
    public boolean allowCompanySync(SecurityInfoBo security, String triggerCompanyCode,
                                    Long poolId, String poolName, boolean inbound) {
        return allowCompanySync(security, triggerCompanyCode, poolId, poolName, inbound, poolId);
    }

    /**
     * 按实际主体来源池决定 ABS 是否可以联动，兼容主体池与债券池不同的映射
     *
     * 执行顺序：
     * 1. 将主体依据池和债券目标池分别传给统一联动判定，按当前证券查询提醒和在途记录
     *
     * @param security 关联证券
     * @param triggerCompanyCode 触发主体
     * @param poolId 债券实际目标池
     * @param poolName 债券实际目标池名称
     * @param inbound 是否调入
     * @param companyPoolId 主体依据所在池
     * @return true=允许继续自动联动，false=跳过本次自动联动
     */
    public boolean allowCompanySync(SecurityInfoBo security, String triggerCompanyCode,
                                    Long poolId, String poolName, boolean inbound, Long companyPoolId) {
        // 按当前证券读取依据并执行统一联动判定
        return resolveAbsCompanySync(security, triggerCompanyCode, poolId, poolName, inbound, companyPoolId, null);
    }

    /**
     * 判断 ABS 是否允许继续入池或出池，并维护对应提醒和待确认日志
     *
     * 入池或出池仍由调用方执行，定时对账可复用同一目标池和阶段已批量读取的记录
     *
     * 执行顺序：
     * 1. 普通证券直接放行，ABS 核对主档和触发主体，相同依据已有人工结论时跳过联动
     * 2. 检查主体身份、出池保护及黑名单质押库条件，确定是否需要提醒
     * 3. 可以联动时关闭已解除的待处理提醒，入池时关闭旧待确认日志
     * 4. 不能联动时更新或新增提醒，未确认主体的入池请求补齐待确认日志
     *
     * @param security 本次处理的证券
     * @param triggerCompanyCode 触发联动的主体代码
     * @param poolId 债券目标池 ID
     * @param poolName 债券目标池名称
     * @param inbound 是否为入池
     * @param companyPoolId 主体依据所在池 ID
     * @param reads 本阶段已读的提醒、待确认日志或在途记录，未批量读取时为空
     * @return true=允许继续调整池状态，false=跳过本次池状态调整
     */
    private boolean resolveAbsCompanySync(SecurityInfoBo security, String triggerCompanyCode,
                                     Long poolId, String poolName, boolean inbound, Long companyPoolId,
                                     ScheduledReads reads) {
        // 1. 普通证券直接放行，ABS 核对主档和触发主体，再检查已有人工结论
        if (!CreditBondSpecialInboundRule.isAbs(security)) {
            return true;
        }
        SecurityInfoBo master = security.getIssuerCode() != null && security.getIssuer() != null
                ? security : securityPoolAdjustMapper.querySecurityBoByCode(security.getWindCode());
        if (master == null) {
            throw new BizException("ABS[" + security.getWindCode() + "]主档不存在");
        }
        // 已确认身份后，其他关联主体进出时不用再处理这只 ABS
        String confirmedCode = StringUtils.hasText(master.getIssuerCode()) ? master.getIssuerCode().trim() : null;
        boolean confirmed = confirmedCode != null;
        if (confirmed && !confirmedCode.equals(triggerCompanyCode)) {
            return false;
        }
        // 将身份和主体在池版本一起作为人工结论的适用范围
        String contextKey = resolveContextKey(master.getWindCode(), confirmedCode, poolId);
        // 未确认身份的 ABS 任一关联主体入池都只生成提醒和待确认日志，人工与定时完全一致，不直接落池
        boolean pendingInbound = !confirmed && inbound;
        IpGradeRuleAlertBo previous = reads == null ? absIdentityMapper.queryLatestSyncAlert(master.getWindCode(), poolId)
                : reads.latestAlerts.get(security.getWindCode());
        // 相同主体依据已经被研究员处理时，不再次生成待确认流程或反向调库
        if (previous != null && contextKey.equals(previous.getContextKey())
                && "20".equals(previous.getAlertStatus())) {
            return false;
        }
        // 2. 检查身份、出池保护和 17 池条件，确定提醒原因
        String reason = null;
        if (confirmed) {
            try {
                // 自动任务同样核对在库和在途身份
                absIdentityService.validateIdentity(master.getWindCode(), confirmedCode);
            } catch (BizException ex) {
                reason = ex.getMessage();
            }
        }
        if (!inbound && reason == null) {
            if (confirmed) {
                // 已确认身份只判断该 ABS 自身的独立依据和在途流程，其他权益人不再构成保留理由
                if (absIdentityMapper.queryIndependentInbound(master.getWindCode(), poolId)) {
                    reason = "主体退出，但 ABS 存在债级独立入库依据，请研究员确认保留或发起出库";
                // 在途调库未结束时只提醒，避免自动出池
                } else if (hasPendingProcess(master, security.getWindCode(), reads)) {
                    reason = "主体退出，但 ABS 存在未结束的调库流程，请研究员确认出库时点";
                }
            } else {
                // 未确认身份时全部关联主体都参与判断，任一仍在池则不自动出库
                List<String> other = absIdentityMapper.queryOtherCompanyInPool(
                        master.getWindCode(), triggerCompanyCode, companyPoolId);
                if (other != null && !other.isEmpty()) {
                    reason = "触发主体[" + triggerCompanyCode + "]退出，其他关联主体[" + String.join("、", other)
                            + "]仍在该池，请研究员确认保留或发起出库";
                } else if (absIdentityMapper.queryIndependentInbound(master.getWindCode(), poolId)) {
                    reason = "主体退出，但 ABS 存在债级独立入库依据，请研究员确认保留或发起出库";
                // 在其他主体和独立入池依据之外，再检查在途调库保护
                } else if (hasPendingProcess(master, security.getWindCode(), reads)) {
                    reason = "主体退出，但 ABS 存在未结束的调库流程，请研究员确认出库时点";
                } else {
                    reason = "ABS 尚未确认主体身份，触发主体退出时请研究员确认是否出库";
                }
            }
        }
        if (reason == null && PledgeBlacklistRuleService.BLACKLIST_POOL_ID.equals(poolId)) {
            // 自动联动也不能绕过原有17三条件，已确认身份只校验确认主体
            reason = pledgeBlacklistRuleService.validateCompanies(
                    absIdentityService.queryRiskCompanyCodes(master, confirmedCode),
                    inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode());
        }
        if (reason == null && pendingInbound) {
            reason = "主体[" + triggerCompanyCode + "]入池触发 ABS 联动，ABS 主体身份尚未确认，请研究员选择权益人";
        }
        // 3. 可以继续联动时，关闭不再需要的提醒和入池待确认日志
        if (reason == null) {
            if (previous != null && "00".equals(previous.getAlertStatus())) {
                absIdentityMapper.editSyncAlertInvalid(master.getWindCode(), poolId);
            }
            // 主体已明确或触发依据已消失时，关闭遗留的待确认日志
            if (inbound) {
                IpAdjustLogBo pending = reads == null
                        ? absIdentityMapper.queryPendingScheduledInboundLog(master.getWindCode(), poolId)
                        : reads.pendingInboundLogs.get(security.getWindCode());
                if (pending != null) {
                    absIdentityMapper.editPendingScheduledInboundInvalid(pending.getId());
                }
            }
            return true;
        }
        // 4. 写入当前依据对应的提醒，并维护未确认主体的待确认入池日志
        IpGradeRuleAlertBo alert = new IpGradeRuleAlertBo();
        alert.setSecurityCode(master.getWindCode());
        alert.setSecurityShortName(master.getShortName());
        alert.setIssuerCode(master.getIssuerCode());
        alert.setIssuerName(master.getIssuer());
        alert.setCurrentPoolId(poolId);
        alert.setCurrentPoolName(poolName);
        alert.setContextKey(contextKey);
        alert.setFailReason(reason);
        if (previous != null && contextKey.equals(previous.getContextKey()) && "00".equals(previous.getAlertStatus())) {
            alert.setId(previous.getId());
            absIdentityMapper.editSyncAlert(alert);
        } else {
            absIdentityMapper.editSyncAlertInvalid(master.getWindCode(), poolId);
            absIdentityMapper.addSyncAlert(alert);
        }
        if (pendingInbound) {
            // 未确认 ABS 只保存待确认日志，人工调库和定时任务写同一条，都不提前落池
            IpAdjustLogBo existing = reads == null
                    ? absIdentityMapper.queryPendingScheduledInboundLog(security.getWindCode(), poolId)
                    : reads.pendingInboundLogs.get(security.getWindCode());
            // 沿用本池已有的待确认日志，缺失时才创建
            createPendingInboundLog(security, triggerCompanyCode, poolId, poolName, companyPoolId, existing);
        }
        return false;
    }

    /**
     * 检查证券是否存在待处理的调库步骤
     *
     * 执行顺序：
     * 1. 定时对账使用候选代码查已读结果，未批量读取时按主档证券代码查询
     *
     * @param master 证券主档，人工入口使用其中的证券代码查询
     * @param candidateCode 定时任务传入的候选代码，用于读取批量结果
     * @param reads 定时对账已读的在途记录，未批量读取时为空
     * @return true=存在待处理的调库步骤，false=没有待处理的调库步骤
     */
    private boolean hasPendingProcess(SecurityInfoBo master, String candidateCode, ScheduledReads reads) {
        return reads == null ? securityPoolAdjustMapper.querySecurityHasPendingProcess(master.getWindCode())
                : reads.pendingProcessCodes.contains(candidateCode);
    }

    /**
     * 为未确认主体的 ABS 补齐待确认入池日志
     *
     * 日志标记为系统自动调整，供研究员确认主体或忽略入池
     *
     * 执行顺序：
     * 1. 已有待确认日志时，仅在旧简称为空且本次有简称时回填
     * 2. 没有待确认日志时，按证券和目标池写入一条记录，写入失败则报错
     *
     * @param security 关联证券
     * @param triggerCompanyCode 触发主体
     * @param poolId 债券目标池
     * @param poolName 债券目标池名称
     * @param companyPoolId 主体依据所在池
     * @param existing 当前证券在本池已有的待确认日志
     */
    private void createPendingInboundLog(SecurityInfoBo security, String triggerCompanyCode,
                                         Long poolId, String poolName, Long companyPoolId, IpAdjustLogBo existing) {
        // 1. 已有待确认日志时仅回填缺失的证券简称
        if (existing != null) {
            // 兼容修复前已生成但简称为空的待确认日志
            if (!StringUtils.hasText(existing.getSecurityShortName())
                    && StringUtils.hasText(security.getShortName())) {
                absIdentityMapper.editPendingScheduledInboundSecurityShortName(
                        existing.getId(), security.getShortName());
            }
            return;
        }
        // 2. 用当前证券和目标池信息建立待确认日志
        IpAdjustLogBo pending = new IpAdjustLogBo();
        pending.setSecurityCode(security.getWindCode());
        pending.setSecurityShortName(security.getShortName());
        pending.setSecurityType(security.getSecurityType());
        pending.setAdjustType(AdjustType.AUTO.getCode());
        pending.setAdjustMode(AdjustMode.IN.getCode());
        pending.setTargetPoolId(poolId);
        pending.setTargetPoolName(poolName);
        InvestmentPoolBo pool = investmentPoolMapper == null ? null : investmentPoolMapper.queryPoolById(poolId);
        if (pool != null) {
            pending.setPoolType(pool.getPoolType());
        }
        pending.setFlowType(ABS_PENDING_FLOW_TYPE);
        pending.setAuditStatus(AuditStatus.SUBMITTED.getCode());
        pending.setAdjusterId(AUTO_ADJUSTER_ID);
        pending.setAdjusterName(AUTO_ADJUSTER_NAME);
        pending.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(
                ScheduledAdjustLogHelper.labelCurrentTask("ABS主体自动联动入池（待确认）"),
                StringUtils.hasText(triggerCompanyCode) ? "触发主体：" + triggerCompanyCode : null,
                companyPoolId == null ? null : "主体依据池：" + companyPoolId,
                StringUtils.hasText(poolName) ? "目标池：" + poolName : null));
        pending.setAdjustBatchNo(new ScheduledAdjustBatchNoContext().resolveBondBatchNo(
                security.getWindCode(), poolId, AdjustMode.IN.getCode()));
        pending.setSubmitTime(new Date());
        if (securityPoolAdjustMapper.addAdjustLog(pending) != 1) {
            throw new BizException("ABS[" + security.getWindCode() + "]待确认入池日志写入失败");
        }
    }

    /**
     * 为单条已通过的调库日志记录 ABS 人工结论
     *
     * 执行顺序：
     * 1. 查询该证券的主档，交给人工结论处理方法判断是否需要记录
     *
     * @param log 已审核通过的人工调库日志
     */
    public void recordManualDecision(IpAdjustLogBo log) {
        // 用查到的证券主档判断并记录人工结论
        recordManualDecision(log, securityPoolAdjustMapper.querySecurityBoByCode(log.getSecurityCode()));
    }

    /**
     * 用已读取的证券主档记录研究员对 ABS 在池状态的结论
     *
     * 结论只在主体依据未变时沿用，避免定时任务立即反向调库
     *
     * 执行顺序：
     * 1. 只处理研究员发起的 ABS 调库
     * 2. 计算当前联动依据，已有同依据提醒时沿用，否则关闭旧待处理提醒并新增记录
     * 3. 保存处理人，将当前依据对应的提醒标为已处理
     *
     * @param log 已审核通过的调库日志
     * @param security 已读取的证券主档
     */
    private void recordManualDecision(IpAdjustLogBo log, SecurityInfoBo security) {
        // 1. 确认这次调库是研究员发起的 ABS 调库
        if (!isManualAdjustType(log.getAdjustType())) return;
        if (!CreditBondSpecialInboundRule.isAbs(security)) return;
        // 2. 用当前主体依据定位提醒，依据变了就另存一条
        String contextKey = resolveContextKey(log.getSecurityCode(), security.getIssuerCode(), log.getTargetPoolId());
        IpGradeRuleAlertBo alert = absIdentityMapper.queryLatestSyncAlert(log.getSecurityCode(), log.getTargetPoolId());
        if (alert == null || !contextKey.equals(alert.getContextKey())) {
            absIdentityMapper.editSyncAlertInvalid(log.getSecurityCode(), log.getTargetPoolId());
            alert = new IpGradeRuleAlertBo();
            alert.setSecurityCode(log.getSecurityCode());
            alert.setSecurityShortName(log.getSecurityShortName());
            alert.setIssuerCode(security.getIssuerCode());
            alert.setIssuerName(security.getIssuer());
            alert.setCurrentPoolId(log.getTargetPoolId());
            alert.setCurrentPoolName(log.getTargetPoolName());
            alert.setContextKey(contextKey);
            alert.setFailReason("研究员已通过人工" + log.getAdjustMode() + "确认该 ABS 的在池状态");
            absIdentityMapper.addSyncAlert(alert);
        }
        // 3. 把实际调库人记为提醒处理人
        alert.setContextKey(contextKey);
        alert.setDealUserId(log.getAdjusterId());
        alert.setDealUserName(log.getAdjusterName());
        absIdentityMapper.editSyncAlertProcessed(alert);
    }

    /**
     * 判断调库是否由研究员发起
     *
     * 执行顺序：
     * 1. 核对调库类型是否为人工单券、人工批量或 Excel 导入
     *
     * @param adjustType 调库类型代码
     * @return true=人工发起，false=非人工发起
     */
    private boolean isManualAdjustType(String adjustType) {
        return AdjustType.MANUAL.getCode().equals(adjustType)
                || AdjustType.MANUAL_BATCH.getCode().equals(adjustType)
                || AdjustType.EXCEL_IMPORT.getCode().equals(adjustType);
    }

    /**
     * 批量记录本次已通过的 ABS 人工调库结论
     *
     * 执行顺序：
     * 1. 收集本批去重后的证券代码，批量读取其中的 ABS 主档
     * 2. 用已读取的主档逐条判断并记录人工结论
     *
     * @param logs 本批已审核通过的调库日志
     */
    public void recordManualDecisions(List<IpAdjustLogBo> logs) {
        // 1. 按去重后的代码批量读取 ABS 主档
        List<String> codes = new ArrayList<>();
        for (IpAdjustLogBo log : logs) {
            if (!codes.contains(log.getSecurityCode())) codes.add(log.getSecurityCode());
        }
        Map<String, SecurityInfoBo> abs = absIdentityService.queryAbsMasters(codes);
        for (IpAdjustLogBo log : logs) {
            if (abs.containsKey(log.getSecurityCode())) {
                // 2. 用本批已读的 ABS 主档判断并记录人工结论
                recordManualDecision(log, abs.get(log.getSecurityCode()));
            }
        }
    }

    /**
     * 判断定时任务候选能否按同一个池做主体联动
     *
     * 执行顺序：
     * 1. 将债券目标池同时作为主体依据池，交给定时联动入口判断
     *
     * @param candidate 自动任务候选
     * @param triggerCompanyCode 触发主体
     * @param poolId 目标池
     * @param poolName 目标池名称
     * @param inbound 调整方向
     * @return true=允许继续自动联动，false=跳过本次自动联动
     */
    public boolean allowScheduledSync(ScheduledAdjustCandidateDto candidate, String triggerCompanyCode,
                                      Long poolId, String poolName, boolean inbound) {
        return allowScheduledSync(candidate, triggerCompanyCode, poolId, poolName, inbound, poolId);
    }

    /**
     * 判断未批量读取数据的定时任务候选能否联动，支持跨池
     *
     * 执行顺序：
     * 1. 分别传入主体依据池和债券目标池，由定时联动入口按当前证券读取所需记录
     *
     * @param candidate 自动任务候选
     * @param triggerCompanyCode 触发主体
     * @param poolId 债券目标池
     * @param poolName 债券目标池名称
     * @param inbound 调整方向
     * @param companyPoolId 主体依据所在池
     * @return true=允许继续自动联动，false=跳过本次自动联动
     */
    public boolean allowScheduledSync(ScheduledAdjustCandidateDto candidate, String triggerCompanyCode,
                                      Long poolId, String poolName, boolean inbound, Long companyPoolId) {
        return allowScheduledSync(candidate, triggerCompanyCode, poolId, poolName, inbound, companyPoolId, null);
    }

    /**
     * 用目标池当前阶段的批量读取结果判断 ABS 能否联动
     *
     * 定时任务和人工入口共用 ABS 联动判断
     *
     * 执行顺序：
     * 1. 非 ABS 直接放行，ABS 候选转换为联动校验所需的证券信息
     * 2. 用统一的 ABS 校验判断是否允许调整池状态
     *
     * @param candidate 自动调库候选
     * @param triggerCompanyCode 触发联动的主体代码
     * @param poolId 债券目标池 ID
     * @param poolName 债券目标池名称
     * @param inbound 是否为入池
     * @param companyPoolId 主体依据所在池 ID
     * @param reads 本阶段已读的提醒、待确认日志或在途记录，空时按证券查询
     * @return true=允许继续调整池状态，false=跳过本次池状态调整
     */
    boolean allowScheduledSync(ScheduledAdjustCandidateDto candidate, String triggerCompanyCode,
                               Long poolId, String poolName, boolean inbound, Long companyPoolId, ScheduledReads reads) {
        // 1. 只转换需要联动判断的 ABS 候选
        if (!"abs".equals(candidate.getSecurityType()) && !Integer.valueOf(1).equals(candidate.getAbsFlag())) {
            return true;
        }
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode(candidate.getSecurityCode());
        security.setShortName(candidate.getSecurityShortName());
        security.setSecurityType(candidate.getSecurityType());
        security.setAbsFlag(candidate.getAbsFlag());
        security.setIssuerCode(candidate.getIssuerCode());
        security.setIssuer(candidate.getIssuerName());
        // 2. 按与人工入口相同的规则判断 ABS 能否联动
        return resolveAbsCompanySync(security, triggerCompanyCode, poolId, poolName, inbound, companyPoolId, reads);
    }

    /**
     * 分批读取目标池当前阶段需要的 ABS 联动数据
     *
     * 同一阶段每只证券只处理一次，下一阶段或下一个目标池重新读取
     *
     * 执行顺序：
     * 1. 将候选代码分批，每批先读取本池最新联动提醒
     * 2. 每批入池时读取本池待确认日志，出池时读取候选证券的在途流程
     *
     * @param codes 当前阶段去重后的候选证券代码
     * @param poolId 债券目标池 ID
     * @param inbound 是否为入池阶段
     * @return 按证券代码查询的本阶段联动数据
     */
    ScheduledReads loadScheduledReads(List<String> codes, Long poolId, boolean inbound) {
        ScheduledReads reads = new ScheduledReads();
        for (int offset = 0; offset < codes.size(); offset += AutoAdjustRestrictHelper.QUERY_BATCH_SIZE) {
            List<String> batch = codes.subList(offset, Math.min(offset + AutoAdjustRestrictHelper.QUERY_BATCH_SIZE,
                    codes.size()));
            // 1. 读取包含人工处理结论在内的最新联动提醒
            for (IpGradeRuleAlertBo alert : absIdentityMapper.queryLatestSyncAlerts(batch, poolId)) {
                reads.latestAlerts.put(alert.getSecurityCode(), alert);
            }
            // 2. 按阶段读取待确认日志或在途调库流程
            if (inbound) {
                for (IpAdjustLogBo pending : absIdentityMapper.queryPendingScheduledInboundLogs(batch, poolId)) {
                    reads.pendingInboundLogs.put(pending.getSecurityCode(), pending);
                }
            } else {
                reads.pendingProcessCodes.addAll(autoAdjustMapper.queryPendingSecurityCodes(batch));
            }
        }
        return reads;
    }

    /**
     * 保存一个目标池在当前入池或出池阶段批量读取的联动数据
     */
    static final class ScheduledReads {
        /** 候选证券的最新联动提醒 */
        private final Map<String, IpGradeRuleAlertBo> latestAlerts = new HashMap<>();

        /** 入池候选已有的待确认日志 */
        private final Map<String, IpAdjustLogBo> pendingInboundLogs = new HashMap<>();

        /** 出池候选中的在途证券 */
        private final Set<String> pendingProcessCodes = new HashSet<>();
    }

    /**
     * 生成用于判断能否沿用人工结论的联动依据摘要
     *
     * 执行顺序：
     * 1. 读取该 ABS 各关联主体在目标池的状态及最近一次生效调库记录
     * 2. 与当前主体身份一起生成摘要
     *
     * @param securityCode 证券代码
     * @param issuerCode 当前确认的主体代码，未确认时为空
     * @param poolId 债券目标池 ID
     * @return 标识当前联动依据的十六进制摘要
     */
    public String resolveContextKey(String securityCode, String issuerCode, Long poolId) {
        // 1. 读取关联主体在目标池的当前依据
        List<String> context = absIdentityMapper.queryCompanyPoolContext(securityCode, poolId);
        // 2. 将确认主体和在池依据合并成摘要
        return AlertContextKey.of(issuerCode, String.join("|", context));
    }
}
