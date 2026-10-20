package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.CompanyObserverType;
import com.znty.rrs.common.enums.PoolStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.CompanyObserverDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.CompanyObserverMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

/** 根据主体观察名单同步观察池、重点观察名单、关系池及主体旗下债券。 */
@Slf4j
@Service
public class CompanyObserverPoolSyncService implements RrsScheduledTask {
    /** 定时任务编码 */
    public static final String TASK_CODE = "company_observer_pool_sync";
    /** 任务名称 */
    public static final String TASK_NAME = "主体观察名单同步";
    /** 扩展参数 JSON 解析器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** AIS 观察名单查询 */
    @Resource
    private CompanyObserverMapper companyObserverMapper;
    /** 主体旗下债券候选查询 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 调库日志及普通池状态写入 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 投资池配置查询 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 定时任务扩展参数查询 */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    /** 返回任务编码。 */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** 返回主体观察名单同步的参数说明和业务规则。 */
    @Override
    public String getParamHelp() {
        return "1. 参数格式：JSON 对象，默认配置 <code>{\"observePoolId\":16,\"keyObservePoolId\":23}</code>\n"
                + "2. 目标池：observePoolId 对应2观察，keyObservePoolId 对应3重点观察；省略或null表示不管理该类池；"
                + "至少配置一个正整数池ID，两类目标池不能相同\n"
                + "3. 数据来源：ais_inv_analysis.company_observer_view；company_id 对应 t_inv_company.id，"
                + "company_code 对应 t_inv_company.wind_code 及发行人编码\n"
                + "4. 主体进出池：按观察类型进入配置的目标池；类型变更、类型1、空值或名单移除时退出原对应池；先出池后入池\n"
                + "5. 旗下债券：未退市、未到期（含当天）的普通债、ABS、CRMW 随主体进出池；重复执行补齐缺失记录\n"
                + "6. 来源池：调入时须满足至少一个来源池，包含当前在池、本轮调整前在池及同批调入的来源池\n"
                + "7. 调入、调出限制池：命中对应方向的限制池时，本次任务失败\n"
                + "8. 调入、调出联动池：按关系配置，同批调入、调出联动池的主体及旗下债券\n"
                + "9. 调入、调出互斥池：调入后退出已在的调入互斥池，同批调入冲突时失败；调出时命中调出互斥池则失败\n"
                + "10. 调入、调出弹性禁投池：命中仅记录警告；互斥产生的调出也须校验对应池的关系配置\n"
                + "11. 执行结果：直接生效，不走审批；参数错误、名单错误、关系校验不通过或写入失败时，整轮回滚\n"
                + "12. 执行时间及日志：默认每天06:00，调度默认关闭；记录主体调整明细及旗下债券进出池数量";
    }

    /** 执行主体观察名单同步，记录任务结果。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskResult execute() {
        // 1. 记录任务开始时间和过程日志
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        detail.line("任务开始：【" + TASK_NAME + "】");
        try {
            // 2. 同步主体观察名单及旗下债券进出池
            SyncResult summary = doSync(detail);
            // 3. 记录进出池数量和任务执行结果
            long duration = System.currentTimeMillis() - begin;
            detail.line("任务结束（成功）：" + summary.message + "，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, summary.message, summary.affectedCount, startTime,
                    duration, detail.build());
        } catch (Exception e) {
            // 4. 执行失败时回滚本轮调库记录和池状态
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            log.error("{}执行失败", TASK_NAME, e);
            detail.line("ERROR", "任务结束（失败，整轮回滚）：" + e.getMessage() + "，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, e.getMessage(), startTime, duration, detail.build());
        }
    }

    /** 查询观察名单，按先出后入顺序同步主体、旗下债券及关系池。 */
    private SyncResult doSync(TaskDetailLog detail) {
        // 1. 读取观察池、重点观察名单的目标池配置
        Map<String, Long> observerPools = queryObserverPoolMappings();
        List<Long> poolIds = new ArrayList<>(observerPools.values());
        detail.line("扫描条件：ais_inv_analysis.company_observer_view 全量；"
                + "observer_type→目标池=" + observerPools + "，1/空值/名单移除→退出配置池；"
                + "ip_pool_status.audit_status=20、is_deleted=0；旗下bond未退市且到期日为空或不早于今天；"
                + "扫描ip_pool_relation中配置源池" + poolIds + "的in_linked/out_linked及各实际目标池限制/互斥关系");
        // 2. 校验目标池，查询联动池和互斥池所需的投资池信息
        Map<Long, InvestmentPoolBo> pools = queryRequiredPools(poolIds);
        // 3. 查询观察名单，校验主体信息和观察类型
        Map<String, CompanyObserverDto> observers = queryValidatedObservers();
        // 4. 合并观察名单主体、目标池内主体及在池债券的发行主体
        Map<String, ScheduledAdjustCandidateDto> companies = new LinkedHashMap<>();
        for (ScheduledAdjustCandidateDto company : companyObserverMapper.queryCurrentCompanyList(poolIds)) {
            if (!StringUtils.hasText(company.getSecurityCode())) {
                throw new BizException("观察池/重点观察名单中存在发行主体编码为空的记录");
            }
            companies.put(company.getSecurityCode(), company);
        }
        for (CompanyObserverDto observer : observers.values()) {
            ScheduledAdjustCandidateDto company = new ScheduledAdjustCandidateDto();
            company.setSecurityCode(observer.getCompanyCode());
            company.setSecurityShortName(observer.getCompanyName());
            company.setSecurityType("company");
            companies.put(company.getSecurityCode(), company);
        }
        detail.line("扫描完成：名单 " + observers.size() + " 个主体，合并在池主体/债券发行人后 "
                + companies.size() + " 个主体");
        // 5. 查询关系池配置，准备主体批次和进出池统计
        SyncContext context = new SyncContext(pools, poolIds, securityPoolAdjustMapper.queryAllPoolRelationList(), detail);
        // 6. 先调出不符合观察类型的主体及旗下债券，再调入符合观察类型的主体及旗下债券
        for (boolean inbound : new boolean[]{false, true}) {
            for (ScheduledAdjustCandidateDto company : companies.values()) {
                CompanyObserverDto observer = observers.get(company.getSecurityCode());
                Long desiredPoolId = observer == null ? null : observerPools.get(observer.getObserverType());
                // 确定本主体计划调入的目标池和联动池，用于来源池、调入互斥池校验
                context.requestInPoolIds = resolveRequestInPoolIds(desiredPoolId, context);
                for (Long poolId : poolIds) {
                    if (inbound == poolId.equals(desiredPoolId)) {
                        // 同步目标池及其调入、调出联动池
                        syncSourcePool(company, observer, pools.get(poolId), inbound, context);
                    }
                }
            }
        }
        // 7. 汇总各池的主体、旗下债券进出池数量
        return buildSyncResult(context);
    }

    /** 汇总各投资池的主体和旗下债券进出池数量。 */
    private SyncResult buildSyncResult(SyncContext context) {
        // 1. 按投资池统计主体和债券的实际进出池数量
        SyncResult result = new SyncResult();
        StringBuilder message = new StringBuilder();
        for (Map.Entry<Long, SyncSummary> entry : context.summaries.entrySet()) {
            SyncSummary summary = entry.getValue();
            result.affectedCount += summary.companyIn + summary.companyOut + summary.bondIn + summary.bondOut;
            if (message.length() > 0) {
                message.append("；");
            }
            message.append(context.pools.get(entry.getKey()).getPoolName()).append("：主体入").append(summary.companyIn)
                    .append("/出").append(summary.companyOut).append("，债券入").append(summary.bondIn)
                    .append("/出").append(summary.bondOut).append("，关系池出").append(summary.relationOut);
        }
        // 2. 生成本轮任务的汇总说明
        result.message = message.toString();
        return result;
    }

    /** 查询本主体计划调入的目标池和调入联动池。 */
    private Set<Long> resolveRequestInPoolIds(Long desiredPoolId, SyncContext context) {
        // 1. 确定观察类型对应的目标池
        Set<Long> poolIds = new LinkedHashSet<>();
        if (desiredPoolId != null) {
            poolIds.add(desiredPoolId);
            // 2. 加入目标池配置的调入联动池
            poolIds.addAll(AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    desiredPoolId, RelationType.IN_LINKED.getCode(), context.relations));
        }
        return poolIds;
    }

    /** 读取并校验观察池、重点观察名单的目标池参数。 */
    private Map<String, Long> queryObserverPoolMappings() {
        // 1. 查询本任务保存的扩展参数
        SysScheduledTaskBo task = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (task == null || !StringUtils.hasText(task.getParamJson())) {
            throw new BizException("主体观察名单同步须配置扩展参数observePoolId/keyObservePoolId，至少配置一个目标池");
        }
        // 2. 校验JSON格式和参数名称
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(task.getParamJson());
        } catch (Exception e) {
            throw new BizException("主体观察名单同步扩展参数JSON解析失败：" + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new BizException("主体观察名单同步扩展参数必须是JSON对象");
        }
        root.fieldNames().forEachRemaining(field -> {
            if (!"observePoolId".equals(field) && !"keyObservePoolId".equals(field)) {
                throw new BizException("主体观察名单同步扩展参数包含未知字段：" + field);
            }
        });
        Map<String, Long> result = new LinkedHashMap<>();
        // 3. 读取观察类型对应的目标池
        appendObserverPoolMapping(result, root, "observePoolId", CompanyObserverType.OBSERVE.getCode());
        // 读取重点观察类型对应的目标池
        appendObserverPoolMapping(result, root, "keyObservePoolId", CompanyObserverType.KEY_OBSERVE.getCode());
        // 4. 校验至少配置一类目标池，且两类目标池不同
        if (result.isEmpty()) {
            throw new BizException("主体观察名单同步至少需要配置一个目标池");
        }
        if (new LinkedHashSet<>(result.values()).size() != result.size()) {
            throw new BizException("observePoolId与keyObservePoolId不能配置同一个目标池");
        }
        return result;
    }

    /** 校验并保存单个观察类型对应的目标池ID。 */
    private void appendObserverPoolMapping(Map<String, Long> mappings, JsonNode root, String field, String observerType) {
        // 1. 省略或为空的目标池不加入本轮同步范围
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        // 2. 校验目标池ID为Long范围内的正整数
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new BizException(field + "必须是Long范围内的正整数池ID");
        }
        // 3. 保存观察类型与目标池的对应关系
        mappings.put(observerType, value.longValue());
    }

    /** 查询投资池，校验本任务目标池是否可用。 */
    private Map<Long, InvestmentPoolBo> queryRequiredPools(List<Long> poolIds) {
        // 1. 查询投资池信息，供目标池、联动池和互斥池使用
        Map<Long, InvestmentPoolBo> pools = new LinkedHashMap<>();
        for (InvestmentPoolBo pool : investmentPoolMapper.queryPoolList()) {
            pools.put(pool.getId(), pool);
        }
        // 2. 校验目标池已启用、未删除，且支持主体及旗下债券进出池
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = pools.get(poolId);
            if (pool == null || CompanyBondSyncPolicy.isCrmwCombinationPool(pool)
                    || !PoolStatus.ENABLED.getCode().equals(pool.getStatus()) || Integer.valueOf(1).equals(pool.getIsDeleted())) {
                throw new BizException("主体观察名单同步目标池[" + poolId + "]不存在、未启用或为CRMW组合池");
            }
        }
        return pools;
    }

    /** 查询观察名单，校验主体信息、观察类型及主体是否重复。 */
    private Map<String, CompanyObserverDto> queryValidatedObservers() {
        Map<String, CompanyObserverDto> result = new LinkedHashMap<>();
        for (CompanyObserverDto observer : companyObserverMapper.queryCompanyObserverList()) {
            String location = "观察名单主体ID[" + observer.getCompanyId() + "]主体[" + observer.getCompanyCode() + "]";
            // 1. 校验主体ID、主体编码和主体名称
            if (observer.getCompanyId() == null || !StringUtils.hasText(observer.getCompanyCode())
                    || !observer.getCompanyCode().equals(observer.getMasterCompanyCode())
                    || !StringUtils.hasText(observer.getCompanyName())) {
                throw new BizException(location + "的company_id/company_code与t_inv_company主数据不匹配或名称为空");
            }
            // 2. 校验观察类型为1空、2观察、3重点观察或空值
            String type = observer.getObserverType();
            if (StringUtils.hasText(type) && !CompanyObserverType.NONE.getCode().equals(type)
                    && !CompanyObserverType.OBSERVE.getCode().equals(type)
                    && !CompanyObserverType.KEY_OBSERVE.getCode().equals(type)) {
                throw new BizException(location + "的observer_type非法：" + type);
            }
            // 3. 校验每个主体只有一条观察名单记录
            if (result.put(observer.getCompanyCode(), observer) != null) {
                throw new BizException(location + "重复，请确保每个主体只有一条观察名单记录");
            }
        }
        return result;
    }

    /** 同步观察类型对应的目标池及调入、调出联动池。 */
    private void syncSourcePool(ScheduledAdjustCandidateDto company, CompanyObserverDto observer,
                                InvestmentPoolBo pool, boolean inbound, SyncContext context) {
        String mode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        String companyCode = company.getSecurityCode();
        // 1. 同一主体、目标池及调整方向只处理一次
        if (!context.processed.add(companyCode + "|" + pool.getId() + "|" + mode)) {
            return;
        }
        // 2. 生成主体、旗下债券和关系池共用的调库批次及调整原因
        String batchNo = context.batches.resolveCompanyBatchNo(companyCode, pool.getId(), mode);
        String reason = ScheduledAdjustLogHelper.appendDetails(TASK_NAME,
                "主体：" + companyCode,
                observer == null ? "观察名单已移除" : "observer_type=" + observer.getObserverType(),
                "目标池：" + pool.getPoolName());
        // 3. 同步主体及旗下债券在目标池中的状态
        boolean changed = syncCompanyAndBonds(company, pool, inbound, batchNo, reason, false, context);
        // 4. 调入时补齐联动池，调出时仅在目标池实际出池后联动调出
        if (inbound || changed) {
            // 按调入、调出联动池配置同步主体及旗下债券
            syncLinkedPools(company, pool, inbound, batchNo, reason, context);
        }
    }

    /** 按调入、调出联动池配置，同批同步主体及旗下债券。 */
    private void syncLinkedPools(ScheduledAdjustCandidateDto company, InvestmentPoolBo sourcePool,
                                 boolean inbound, String batchNo, String reason, SyncContext context) {
        String mode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        // 1. 按调整方向查询调入联动池或调出联动池
        String relationType = inbound ? RelationType.IN_LINKED.getCode() : RelationType.OUT_LINKED.getCode();
        List<Long> linkedPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(sourcePool.getId(), relationType, context.relations);
        context.detail.line("联动扫描：源池=" + sourcePool.getPoolName() + "，方向=" + mode + "，配置池=" + linkedPoolIds);
        for (Long linkedPoolId : linkedPoolIds) {
            // 2. 同一主体、联动池及调整方向只处理一次
            if (!context.processed.add(company.getSecurityCode() + "|" + linkedPoolId + "|" + mode)) {
                continue;
            }
            // 3. 校验联动池是否可用
            InvestmentPoolBo linkedPool = context.pools.get(linkedPoolId);
            if (linkedPool == null || !PoolStatus.ENABLED.getCode().equals(linkedPool.getStatus())
                    || Integer.valueOf(1).equals(linkedPool.getIsDeleted())
                    || CompanyBondSyncPolicy.isCrmwCombinationPool(linkedPool)) {
                throw new BizException("源池[" + sourcePool.getPoolName() + "]的联动池[" + linkedPoolId
                        + "]不存在、未启用或为CRMW组合池");
            }
            String linkedReason = ScheduledAdjustLogHelper.appendDetails(reason,
                    "池关系触发：" + mode + sourcePool.getPoolName() + "后联动" + mode + linkedPool.getPoolName());
            // 4. 主体及旗下债券按原主体批次进出联动池
            syncCompanyAndBonds(company, linkedPool, inbound, batchNo, linkedReason, true, context);
        }
    }

    /** 同步主体及旗下债券进出池，返回是否发生实际调整。 */
    private boolean syncCompanyAndBonds(ScheduledAdjustCandidateDto company, InvestmentPoolBo pool,
                                        boolean inbound, String batchNo, String reason, boolean linked,
                                        SyncContext context) {
        SyncSummary summary = context.summaries.computeIfAbsent(pool.getId(), key -> new SyncSummary());
        String companyCode = company.getSecurityCode();
        // 1. 查询主体当前在池情况，保留本轮调整前的来源池依据
        List<Long> currentPoolIds = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(companyCode);
        context.originalPools.putIfAbsent(companyCode, new ArrayList<>(currentPoolIds));
        boolean companyInPool = currentPoolIds.contains(pool.getId());
        boolean changed = companyInPool != inbound;
        if (companyInPool != inbound) {
            // 2. 主体需要进出池时，校验关系池并写入调库记录和池状态
            applyChange(company, pool, inbound, batchNo, reason, linked ? "联动调整" : "自动调整", currentPoolIds, context);
            if (inbound) {
                summary.companyIn++;
            } else {
                summary.companyOut++;
            }
        }
        if (inbound) {
            // 3. 主体调入时，处理调入互斥池的配套调出
            syncInboundMutexPools(company, pool, batchNo, reason, context);
        }
        // 4. 查询需要跟随主体进出池的债券，包含已在池且需要检查调入互斥池的债券
        List<ScheduledAdjustCandidateDto> bonds = queryCompanyBondCandidates(companyCode, pool.getId(), inbound);
        for (ScheduledAdjustCandidateDto bond : bonds) {
            if (!StringUtils.hasText(bond.getSecurityCode()) || !StringUtils.hasText(bond.getSecurityType())) {
                throw new BizException("主体[" + companyCode + "]的旗下债券候选缺少证券代码或类型");
            }
            List<Long> bondPools = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(bond.getSecurityCode());
            context.originalPools.putIfAbsent(bond.getSecurityCode(), new ArrayList<>(bondPools));
            if (bondPools.contains(pool.getId()) != inbound) {
                // 5. 旗下债券沿用主体的目标池、调整方向和批次进出池
                applyChange(bond, pool, inbound, batchNo, reason + "（同池旗下债）",
                        linked ? "联动调整" : "自动调整", bondPools, context);
                changed = true;
                if (inbound) {
                    summary.bondIn++;
                } else {
                    summary.bondOut++;
                }
            }
            if (inbound) {
                // 6. 旗下债券调入时，处理调入互斥池的配套调出
                syncInboundMutexPools(bond, pool, batchNo, reason + "（同池旗下债）", context);
            }
        }
        // 7. 按主体和投资池汇总旗下债券进出池数量
        writeBondProcessSummary(companyCode, context);
        return changed;
    }

    /** 记录旗下债券进出池数量及调入、调出弹性禁投池的警告数量。 */
    private void writeBondProcessSummary(String companyCode, SyncContext context) {
        for (Map.Entry<Long, BondProcessSummary> entry : context.bondProcessSummaries.entrySet()) {
            BondProcessSummary summary = entry.getValue();
            String prefix = "主体[" + companyCode + "]旗下债券：投资池=" + context.pools.get(entry.getKey()).getPoolName();
            // 1. 按主体和投资池记录旗下债券进出池数量
            if (summary.inCount > 0 || summary.outCount > 0) {
                context.detail.line(prefix + "，调入" + summary.inCount + "只，调出" + summary.outCount + "只");
            }
            // 2. 汇总调入、调出弹性禁投池命中的债券数量
            if (summary.inWarningCount > 0) {
                context.detail.line("WARN", prefix + "，调入弹性禁投池（警告不阻断）命中" + summary.inWarningCount + "只");
            }
            if (summary.outWarningCount > 0) {
                context.detail.line("WARN", prefix + "，调出弹性禁投池（警告不阻断）命中" + summary.outWarningCount + "只");
            }
        }
        // 3. 清空已记录的债券数量，供下一次主体进出池统计使用
        context.bondProcessSummaries.clear();
    }

    /** 查询主体旗下未在池债券和已在池债券。 */
    private List<ScheduledAdjustCandidateDto> queryCompanyBondCandidates(String companyCode, Long poolId, boolean inbound) {
        Map<String, ScheduledAdjustCandidateDto> bonds = new LinkedHashMap<>();
        // 1. 调入时查询主体旗下尚未进入目标池的债券
        if (inbound) {
            for (ScheduledAdjustCandidateDto bond : autoAdjustMapper.queryCompanyBondNotInSamePoolForAutoIn(
                    companyCode, poolId, CompanyBondSyncPolicy.currentTypeScope())) {
                bonds.put(bond.getSecurityCode(), bond);
            }
        }
        // 2. 查询主体旗下已在目标池的债券，用于调出或检查调入互斥池
        for (ScheduledAdjustCandidateDto bond : autoAdjustMapper.queryCompanyBondInSamePoolForAutoOut(
                companyCode, poolId, CompanyBondSyncPolicy.currentTypeScope())) {
            bonds.put(bond.getSecurityCode(), bond);
        }
        return new ArrayList<>(bonds.values());
    }

    /** 根据调入互斥池和反向调入限制池配置，调出主体及债券。 */
    private void syncInboundMutexPools(ScheduledAdjustCandidateDto item, InvestmentPoolBo inboundPool,
                                       String batchNo, String reason, SyncContext context) {
        // 1. 查询调入互斥池，以及将本次调入池配置为调入限制池的投资池
        List<Long> autoOutPoolIds = AutoAdjustRelationHelper.resolveInboundAutoOutPoolIds(inboundPool.getId(), context.relations);
        for (Long outPoolId : autoOutPoolIds) {
            // 2. 校验同一主体、债券不能同时计划调入互斥的投资池
            if (context.requestInPoolIds.contains(outPoolId)) {
                throw new BizException("主体/债券[" + item.getSecurityCode() + "]同批调入池与调入互斥/反向限制池冲突：" + outPoolId);
            }
            // 3. 只处理实际在池的主体、债券，并校验调出池是否可用
            List<Long> currentPools = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(item.getSecurityCode());
            if (!currentPools.contains(outPoolId)) {
                continue;
            }
            InvestmentPoolBo outPool = context.pools.get(outPoolId);
            if (outPool == null || !PoolStatus.ENABLED.getCode().equals(outPool.getStatus())
                    || Integer.valueOf(1).equals(outPool.getIsDeleted()) || CompanyBondSyncPolicy.isCrmwCombinationPool(outPool)) {
                throw new BizException("调入互斥配套调出池[" + outPoolId + "]不存在、未启用或为CRMW组合池");
            }
            String mutexReason = ScheduledAdjustLogHelper.appendDetails(reason,
                    "池关系触发：调入" + inboundPool.getPoolName() + "后互斥调出" + outPool.getPoolName());
            // 4. 校验调出限制池、调出互斥池、调出弹性禁投池，并写入调出记录
            applyChange(item, outPool, false, batchNo, mutexReason, "互斥调整", currentPools, context);
            // 5. 统计实际调出池的主体、债券数量
            SyncSummary outSummary = context.summaries.computeIfAbsent(outPoolId, key -> new SyncSummary());
            if ("company".equals(item.getSecurityType())) {
                outSummary.companyOut++;
            } else {
                outSummary.bondOut++;
            }
            context.summaries.get(inboundPool.getId()).relationOut++;
        }
    }

    /** 校验关系池，写入主体或债券的调库记录和池状态。 */
    private IpAdjustLogBo applyChange(ScheduledAdjustCandidateDto item, InvestmentPoolBo pool, boolean inbound,
                                      String batchNo, String reason, String adjustType, List<Long> currentPools,
                                      SyncContext context) {
        String mode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        // 1. 校验来源池、调入调出限制池、调出互斥池、调入调出弹性禁投池
        validatePoolRelations(item, pool, inbound, currentPools, context);
        // 2. 写入审批通过的主体或债券调库记录
        IpAdjustLogBo adjustLog = new IpAdjustLogBo();
        adjustLog.setSecurityCode(item.getSecurityCode());
        adjustLog.setSecurityShortName(item.getSecurityShortName());
        adjustLog.setSecurityType(item.getSecurityType());
        adjustLog.setTargetPoolId(pool.getId());
        adjustLog.setTargetPoolName(pool.getPoolName());
        adjustLog.setPoolType(pool.getPoolType());
        adjustLog.setAdjustType(adjustType);
        adjustLog.setAdjustMode(mode);
        adjustLog.setAuditStatus(AuditStatus.APPROVED.getCode());
        adjustLog.setAdjusterId("0");
        adjustLog.setAdjusterName("系统");
        adjustLog.setAdjustReason(reason);
        adjustLog.setAdjustBatchNo(batchNo);
        adjustLog.setSubmitTime(context.batches.getSubmitTime());
        if (securityPoolAdjustMapper.addAdjustLog(adjustLog) != 1) {
            throw new BizException("主体/债券[" + item.getSecurityCode() + "]" + mode + "日志写入失败");
        }
        // 3. 调入时新增池状态，调出时软删除池状态
        adjustLog.setAdjustLogId(adjustLog.getId());
        int changed = inbound ? securityPoolAdjustMapper.addPoolStatus(adjustLog)
                : securityPoolAdjustMapper.deletePoolStatusSoft(item.getSecurityCode(), pool.getId());
        if (changed != 1) {
            throw new BizException("主体/债券[" + item.getSecurityCode() + "]" + mode + "池状态写入条数异常：" + changed);
        }
        // 4. 主体记录调整明细，旗下债券累计进出池数量
        if ("company".equals(item.getSecurityType())) {
            context.detail.line("处理成功：主体[" + item.getSecurityCode() + "] " + mode + " " + pool.getPoolName());
        } else {
            BondProcessSummary summary = context.bondProcessSummaries.computeIfAbsent(pool.getId(), key -> new BondProcessSummary());
            if (inbound) {
                summary.inCount++;
            } else {
                summary.outCount++;
            }
        }
        return adjustLog;
    }

    /** 来源池、调入调出限制池、调出互斥池、调入调出弹性禁投池的相关校验。 */
    private void validatePoolRelations(ScheduledAdjustCandidateDto item, InvestmentPoolBo pool, boolean inbound,
                                       List<Long> currentPools, SyncContext context) {
        // 1. 来源池校验：调入时须满足至少一个配置的来源池
        if (inbound) {
            List<Long> sourcePools = AutoAdjustRestrictHelper.resolveRelationPoolIds(pool.getId(), RelationType.SOURCE.getCode(), context.relations);
            List<Long> originalPools = context.originalPools.get(item.getSecurityCode());
            if (!sourcePools.isEmpty() && !AutoAdjustRestrictHelper.isInAnyPool(currentPools, sourcePools)
                    && !AutoAdjustRestrictHelper.isInAnyPool(originalPools, sourcePools)
                    && !AutoAdjustRestrictHelper.isInAnyPool(new ArrayList<>(context.requestInPoolIds), sourcePools)) {
                throw new BizException("主体/债券[" + item.getSecurityCode() + "]不满足" + pool.getPoolName() + "的来源池要求：" + sourcePools);
            }
        }
        // 2. 调入、调出限制池校验：命中对应方向的限制池时不允许调整
        String restrictType = inbound ? RelationType.IN_RESTRICT.getCode() : RelationType.OUT_RESTRICT.getCode();
        List<Long> restrictPools = AutoAdjustRestrictHelper.resolveRelationPoolIds(pool.getId(), restrictType, context.relations);
        if (AutoAdjustRestrictHelper.isInAnyPool(currentPools, restrictPools)) {
            throw new BizException("主体/债券[" + item.getSecurityCode() + "]命中" + pool.getPoolName()
                    + "的" + (inbound ? "调入" : "调出") + "限制池");
        }
        // 3. 调出互斥池校验：仍在调出互斥池时不允许调出
        if (!inbound) {
            List<Long> outMutexPools = AutoAdjustRestrictHelper.resolveRelationPoolIds(pool.getId(), RelationType.OUT_MUTEX.getCode(), context.relations);
            if (AutoAdjustRestrictHelper.isInAnyPool(currentPools, outMutexPools)) {
                throw new BizException("主体/债券[" + item.getSecurityCode() + "]命中" + pool.getPoolName() + "的调出互斥池：" + outMutexPools);
            }
        }
        // 4. 调入、调出弹性禁投池校验：命中时记录主体警告或累计债券警告数量
        String softType = inbound ? RelationType.IN_SOFT_RESTRICT.getCode() : RelationType.OUT_SOFT_RESTRICT.getCode();
        List<Long> softPools = AutoAdjustRestrictHelper.resolveRelationPoolIds(pool.getId(), softType, context.relations);
        if (AutoAdjustRestrictHelper.isInAnyPool(currentPools, softPools)) {
            if ("company".equals(item.getSecurityType())) {
                context.detail.line("WARN", "主体[" + item.getSecurityCode() + "]命中" + pool.getPoolName()
                        + "的" + (inbound ? "调入" : "调出") + "弹性禁投池（警告不阻断）：" + softPools);
            } else {
                BondProcessSummary summary = context.bondProcessSummaries.computeIfAbsent(pool.getId(), key -> new BondProcessSummary());
                if (inbound) {
                    summary.inWarningCount++;
                } else {
                    summary.outWarningCount++;
                }
            }
        }
    }

    /** 执行失败时回滚本轮调库记录和池状态。 */
    private void markTransactionRollbackOnly() {
        try {
            // 标记本轮事务回滚，撤销已写入的调库记录和池状态
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            log.debug("当前无可回滚事务: {}", TASK_CODE);
        }
    }

    /** 本轮任务的投资池配置、主体批次和进出池统计。 */
    private static final class SyncContext {
        /** 全量池配置 */
        private final Map<Long, InvestmentPoolBo> pools;
        /** 全量有效池关系 */
        private final List<PoolRelationBo> relations;
        /** 按主体、原目标池和调整方向分组的调库批次 */
        private final ScheduledAdjustBatchNoContext batches = new ScheduledAdjustBatchNoContext();
        /** 已处理的主体、目标池及方向，避免重复联动 */
        private final Set<String> processed = new LinkedHashSet<>();
        /** 每个调整对象本轮首次读取的在池状态，用于先出后入时保留来源池依据 */
        private final Map<String, List<Long>> originalPools = new LinkedHashMap<>();
        /** 当前主体及旗下债券同批计划调入的目标池和联动池 */
        private Set<Long> requestInPoolIds = new LinkedHashSet<>();
        /** 各投资池的实际调整数量 */
        private final Map<Long, SyncSummary> summaries = new LinkedHashMap<>();
        /** 当前主体单次同池同步的债券过程日志计数，包含联动及互斥实际写入池 */
        private final Map<Long, BondProcessSummary> bondProcessSummaries = new LinkedHashMap<>();
        /** 本轮过程日志 */
        private final TaskDetailLog detail;

        /** 初始化一轮同步上下文。 */
        private SyncContext(Map<Long, InvestmentPoolBo> pools, List<Long> poolIds,
                            List<PoolRelationBo> relations, TaskDetailLog detail) {
            // 1. 保存本轮使用的投资池、关系池配置和过程日志
            this.pools = pools;
            this.relations = relations;
            this.detail = detail;
            // 2. 初始化配置目标池的主体、债券进出池统计
            for (Long poolId : poolIds) {
                summaries.put(poolId, new SyncSummary());
            }
        }
    }

    /** 单主体、单目标池的债券过程日志数量，不保存或输出证券代码。 */
    private static final class BondProcessSummary {
        /** 实际调入债券数量 */
        private int inCount;
        /** 实际调出债券数量 */
        private int outCount;
        /** 调入弹性禁投警告涉及的债券数量 */
        private int inWarningCount;
        /** 调出弹性禁投警告涉及的债券数量 */
        private int outWarningCount;
    }

    /** 一轮同步的结果摘要。 */
    private static final class SyncResult {
        /** 包含源池及联动池实际变化的总条数 */
        private int affectedCount;
        /** 按实际目标池汇总的说明 */
        private String message;
    }

    /** 单个目标池的主体、债券及关系池实际调整数量。 */
    private static final class SyncSummary {
        /** 主体入池数 */
        private int companyIn;
        /** 主体出池数 */
        private int companyOut;
        /** 债券入池数 */
        private int bondIn;
        /** 债券出池数 */
        private int bondOut;
        /** 主体及旗下债券因调入互斥池、反向调入限制池配置产生的出池数 */
        private int relationOut;
    }
}
