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
import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.CompanyObserverDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CompanyObserverMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.util.ArrayList;
import java.util.Collections;
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

/**
 * 主体观察名单同步（仅主体）
 *
 * 执行顺序：
 * 1. 读取观察名单和目标池配置，按观察类型确定每个主体应在的池，缺少主体编码的记录跳过并计数
 * 2. 先调出不再匹配的主体，再调入当前匹配的主体，按池关系处理配套调整
 * 3. 命中弹性禁投只记录警告，调入调出限制或池关系校验不通过时整轮回滚
 * 4. 汇总本轮主体进出池，旗下债券由独立任务按生效主体状态同步
 */
@Slf4j
@Service
public class CompanyObserverPoolSyncService implements RrsScheduledTask {
    /** 定时任务编码 */
    public static final String TASK_CODE = "company_observer_pool_sync";

    /** 任务名称 */
    public static final String TASK_NAME = "主体观察名单同步（仅主体）";

    /** 扩展参数 JSON 解析器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 批量预读编码上限，避免为每个主体单独查询或生成过长的 IN 条件 */
    private static final int QUERY_BATCH_SIZE = 1000;

    /** 过程日志中发行主体编码为空记录的示例条数上限，避免脏数据多时刷屏 */
    private static final int MISSING_ISSUER_SAMPLE_SIZE = 5;

    /** AIS 观察名单查询 */
    @Resource
    private CompanyObserverMapper companyObserverMapper;

    /** 调库日志及普通池状态写入 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 读取观察名单同步应遵守的 Excel 入池保护记录 */
    @Resource
    private ExcelPoolExclusionService excelPoolExclusionService;

    /** 投资池配置查询 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 定时任务扩展参数查询 */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    /**
     * 返回主体观察名单同步任务编码
     *
     * 执行顺序：
     * 1. 返回当前任务定义的固定编码
     *
     * @return 任务编码
     */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /**
     * 返回主体观察名单同步的参数说明和业务规则
     *
     * 执行顺序：
     * 1. 返回当前任务定义的参数说明
     *
     * @return 任务参数说明
     */
    @Override
    public String getParamHelp() {
        return "1. 参数格式：JSON 对象，默认配置 <code>{\"observePoolId\":16,\"keyObservePoolId\":23}</code>\n"
                + "2. 目标池：observePoolId 对应2观察，keyObservePoolId 对应3重点观察；省略或null表示不管理该类池；"
                + "至少配置一个正整数池ID，两类目标池不能相同\n"
                + "3. 数据来源：ais_inv_analysis.company_observer_view；company_id 对应 t_inv_company.id，"
                + "company_code 对应 t_inv_company.wind_code 及发行人编码\n"
                + "4. 主体进出池：按观察类型进入配置的目标池；类型变更、类型1、空值或名单移除时退出原对应池；先出池后入池\n"
                + "5. 旗下债券由独立任务同步\n"
                + "6. 来源池：调入时须满足至少一个来源池，包含当前在池、本轮调整前在池及同批调入的来源池\n"
                + "7. 调入、调出限制池：命中对应方向的限制池时，本次任务失败\n"
                + "8. 调入、调出联动池：按关系配置，同批调入、调出联动池的主体\n"
                + "9. 调入、调出互斥池：调入后退出已在的调入互斥池，同批调入冲突时失败；调出时命中调出互斥池则失败\n"
                + "10. 调入、调出弹性禁投池：命中仅记录警告；互斥产生的调出也须校验对应池的关系配置\n"
                + "11. 执行结果：直接生效，不走审批；参数错误、名单错误、关系校验不通过或写入失败时，整轮回滚；"
                + "观察池/重点观察名单中发行主体编码为空的记录只跳过，并在过程日志和结果中记录条数，不阻断本轮\n"
                + "12. 执行时间及日志：默认每天06:00，调度默认关闭；记录主体调整明细";
    }

    /**
     * 执行主体观察名单同步，记录主体的调库结果
     *
     * 执行顺序：
     * 1. 记录任务开始时间和过程日志
     * 2. 查询观察名单、目标池和关系配置，按先出后入顺序同步主体
     * 3. 汇总各池主体和关系池的实际进出数量
     * 4. 发生业务或系统异常时标记整轮事务回滚并返回失败结果
     *
     * @return 本轮任务执行结果
     */
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

    /**
     * 查询观察名单，按先出后入顺序同步主体及关系池
     *
     * 执行顺序：
     * 1. 读取观察池、重点观察名单的目标池配置
     * 2. 校验目标池，查询联动池和互斥池所需的投资池信息
     * 3. 查询观察名单，校验主体信息和观察类型
     * 4. 合并观察名单主体、目标池内主体
     * 5. 批量读取本轮主体所在池，后续业务循环不再查询数据库
     * 6. 先调出不符合观察类型的主体，再调入符合观察类型的主体
     * 7. 汇总各池的主体进出池数量
     *
     * @param detail 任务过程日志
     * @return 本轮同步结果
     */
    private SyncResult doSync(TaskDetailLog detail) {
        // 1. 读取观察池、重点观察名单的目标池配置
        Map<String, Long> observerPools = queryObserverPoolMappings();
        List<Long> poolIds = new ArrayList<>(observerPools.values());
        detail.line("扫描条件：ais_inv_analysis.company_observer_view 全量；"
                + "observer_type→目标池=" + observerPools + "，1/空值/名单移除→退出配置池；"
                + "ip_pool_status.audit_status=20、is_deleted=0；"
                + "扫描ip_pool_relation中配置源池" + poolIds + "的in_linked/out_linked及各实际目标池限制/互斥关系");
        // 2. 校验目标池，查询联动池和互斥池所需的投资池信息
        Map<Long, InvestmentPoolBo> pools = queryRequiredPools(poolIds);
        // 3. 查询观察名单，校验主体信息和观察类型
        Map<String, CompanyObserverDto> observers = queryValidatedObservers();
        // 4. 合并观察名单主体、目标池内主体
        Map<String, ScheduledAdjustCandidateDto> companies = new LinkedHashMap<>();
        int missingIssuerCount = 0;
        List<String> missingIssuerSamples = new ArrayList<>();
        for (ScheduledAdjustCandidateDto company : companyObserverMapper.queryCurrentCompanyList(poolIds)) {
            // 发行主体编码为空时只记录警告并跳过，避免单条脏数据导致整轮任务失败
            if (!StringUtils.hasText(company.getSecurityCode())) {
                missingIssuerCount++;
                if (missingIssuerSamples.size() < MISSING_ISSUER_SAMPLE_SIZE) {
                    missingIssuerSamples.add(StringUtils.hasText(company.getSecurityShortName())
                            ? company.getSecurityShortName() : "名称未知");
                }
                continue;
            }
            companies.put(company.getSecurityCode(), company);
        }
        if (missingIssuerCount > 0) {
            detail.line("WARN", "跳过发行主体编码为空的在池记录 " + missingIssuerCount + " 条"
                    + (missingIssuerSamples.isEmpty() ? "" : "（示例：" + String.join("、", missingIssuerSamples) + "）")
                    + "，请补齐主体编码后重跑");
        }
        for (CompanyObserverDto observer : observers.values()) {
            ScheduledAdjustCandidateDto company = new ScheduledAdjustCandidateDto();
            company.setSecurityCode(observer.getCompanyCode());
            company.setSecurityShortName(observer.getCompanyName());
            company.setSecurityType("company");
            companies.put(company.getSecurityCode(), company);
        }
        detail.line("扫描完成：名单 " + observers.size() + " 个主体，合并在池主体发行人后 "
                + companies.size() + " 个主体"
                + (missingIssuerCount > 0 ? "，跳过发行主体编码为空记录 " + missingIssuerCount + " 条" : ""));
        // 5. 查询关系池配置，准备主体批次和进出池统计
        SyncContext context = new SyncContext(pools, poolIds, securityPoolAdjustMapper.queryAllPoolRelationList(), detail);
        // 批量读取本轮主体所在池，后续业务循环不再查询数据库
        loadSyncData(new ArrayList<>(companies.keySet()), context);
        // 6. 先调出不符合观察类型的主体，再调入符合观察类型的主体
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
        // 7. 汇总各池的主体进出池数量
        return buildSyncResult(context, missingIssuerCount);
    }

    /**
     * 按编码分批预读本轮主体池状态，数据仅在当前任务事务内使用
     *
     * 执行顺序：
     * 1. 批量取得本轮主体已生效的 Excel 入池保护记录
     * 2. 分批读取主体在池状态，写入任务上下文并记录读取次数
     *
     * @param companyCodes 主体代码列表
     * @param context 本轮同步共用数据，用于记录主体在池状态、Excel 保护池和查询明细
     */
    private void loadSyncData(List<String> companyCodes, SyncContext context) {
        List<String> codes = companyCodes;
        Set<Long> protectedStatusIds = codes.isEmpty() ? Collections.emptySet()
                : excelPoolExclusionService.loadProtectedStatusIds(null, false);
        int statusQueryCount = 0;
        for (int offset = 0; offset < codes.size(); offset += QUERY_BATCH_SIZE) {
            List<String> batch = codes.subList(offset, Math.min(offset + QUERY_BATCH_SIZE, codes.size()));
            for (IpPoolStatusBo status : companyObserverMapper.queryObserverPoolStatusList(batch)) {
                context.currentPoolIds.computeIfAbsent(status.getSecurityCode(), key -> new LinkedHashSet<>())
                        .add(status.getTargetPoolId());
                if (protectedStatusIds.contains(status.getId())) {
                    context.protectedPools.computeIfAbsent(status.getSecurityCode(), key -> new LinkedHashSet<>())
                            .add(status.getTargetPoolId());
                }
            }
            statusQueryCount++;
        }
        context.detail.line("批量预读完成：主体池状态查询" + statusQueryCount + "次，每批最多" + QUERY_BATCH_SIZE + "个编码");
    }

    /**
     * 取得本轮实时所在池的副本，避免原始来源池快照被后续调库覆盖
     *
     * 执行顺序：
     * 1. 按证券代码读取本轮已记录的池集合，没有记录时初始化为空，再复制为列表返回
     *
     * @param securityCode 证券代码
     * @param context 本轮同步共用数据，包含按主体代码整理的当前在池状态
     * @return 当前投资池ID 列表
     */
    private List<Long> getCurrentPoolIds(String securityCode, SyncContext context) {
        return new ArrayList<>(context.currentPoolIds.computeIfAbsent(securityCode, key -> new LinkedHashSet<>()));
    }

    /**
     * 直接读取本轮预分组的关系池，不在每次主体校验时遍历全量配置
     *
     * 执行顺序：
     * 1. 按来源池和关系类型从本轮索引取目标池 ID，无对应配置时返回空列表
     *
     * @param poolId 目标投资池 ID
     * @param relationType 关系类型常量（RelationType.IN_RESTRICT.getCode() / RelationType.OUT_RESTRICT.getCode() 等）
     * @param context 本轮同步共用数据，包含按池和关系类型整理的关联池 ID
     * @return 关系投资池ID 列表
     */
    private List<Long> resolveRelationPoolIds(Long poolId, String relationType, SyncContext context) {
        return context.relationPoolIds.getOrDefault(poolId, Collections.emptyMap())
                .getOrDefault(relationType, Collections.emptyList());
    }

    /**
     * 汇总各投资池的主体进出池数量
     *
     * 执行顺序：
     * 1. 按投资池统计主体和债券的实际进出池数量
     * 2. 执行结果中标注被跳过的发行主体编码为空记录，便于回查数据
     * 3. 生成本轮任务的汇总说明
     *
     * @param context 本轮同步共用数据，包含投资池名称和逐池同步统计
     * @param missingIssuerCount 因缺少发行主体代码而跳过的记录数
     * @return 本轮同步的入池、出池及跳过数量汇总
     */
    private SyncResult buildSyncResult(SyncContext context, int missingIssuerCount) {
        // 1. 按投资池统计主体和债券的实际进出池数量
        SyncResult result = new SyncResult();
        StringBuilder message = new StringBuilder();
        for (Map.Entry<Long, SyncSummary> entry : context.summaries.entrySet()) {
            SyncSummary summary = entry.getValue();
            result.affectedCount += summary.companyIn + summary.companyOut;
            if (message.length() > 0) {
                message.append("；");
            }
            message.append(context.pools.get(entry.getKey()).getPoolName()).append("：主体入").append(summary.companyIn)
                    .append("/出").append(summary.companyOut).append("，关系池出").append(summary.relationOut);
        }
        // 2. 执行结果中标注被跳过的发行主体编码为空记录，便于回查数据
        if (missingIssuerCount > 0) {
            if (message.length() > 0) {
                message.append("；");
            }
            message.append("跳过发行主体编码为空记录").append(missingIssuerCount).append("条（不阻断本轮）");
        }
        // 3. 生成本轮任务的汇总说明
        result.message = message.toString();
        return result;
    }

    /**
     * 查询本主体计划调入的目标池和调入联动池
     *
     * 执行顺序：
     * 1. 有目标池时先将该池纳入拟调入范围
     * 2. 从本轮已整理的关系配置补入调入联动池，无目标池时返回空集合
     *
     * @param desiredPoolId ID
     * @param context 本轮同步共用数据，包含目标池的调入联动关系
     * @return 本主体计划调入的目标池和调入联动池集合
     */
    private Set<Long> resolveRequestInPoolIds(Long desiredPoolId, SyncContext context) {
        // 1. 确定观察类型对应的目标池
        Set<Long> poolIds = new LinkedHashSet<>();
        if (desiredPoolId != null) {
            poolIds.add(desiredPoolId);
            // 2. 加入目标池配置的调入联动池
            poolIds.addAll(resolveRelationPoolIds(desiredPoolId, RelationType.IN_LINKED.getCode(), context));
        }
        return poolIds;
    }

    /**
     * 读取并校验观察池、重点观察名单的目标池参数
     *
     * 执行顺序：
     * 1. 查询本任务保存的扩展参数
     * 2. 校验JSON格式和参数名称
     * 3. 读取观察类型对应的目标池
     * 4. 读取重点观察类型对应的目标池
     * 5. 校验至少配置一类目标池，且两类目标池不同
     *
     * @return 按观察类型索引的目标池 ID
     */
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

    /**
     * 校验并保存单个观察类型对应的目标池 ID
     *
     * 执行顺序：
     * 1. 省略或为空的目标池不加入本轮同步范围
     * 2. 校验目标池ID为Long范围内的正整数
     * 3. 保存观察类型与目标池的对应关系
     *
     * @param mappings 按文本索引的ID
     * @param root 参数根节点
     * @param field 字段名
     * @param observerType 观察名单类型
     */
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

    /**
     * 查询投资池，校验本任务目标池是否可用
     *
     * 执行顺序：
     * 1. 查询投资池信息，供目标池、联动池和互斥池使用
     * 2. 校验目标池已启用、未删除，且支持主体进出池
     *
     * @param poolIds 待处理的投资池 ID 列表
     * @return 必填投资池映射
     */
    private Map<Long, InvestmentPoolBo> queryRequiredPools(List<Long> poolIds) {
        // 1. 查询投资池信息，供目标池、联动池和互斥池使用
        Map<Long, InvestmentPoolBo> pools = new LinkedHashMap<>();
        for (InvestmentPoolBo pool : investmentPoolMapper.queryPoolList()) {
            pools.put(pool.getId(), pool);
        }
        // 2. 校验目标池已启用、未删除，且支持主体进出池
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = pools.get(poolId);
            if (pool == null || CompanyBondSyncPolicy.isCrmwCombinationPool(pool)
                    || !PoolStatus.ENABLED.getCode().equals(pool.getStatus()) || Integer.valueOf(1).equals(pool.getIsDeleted())) {
                throw new BizException("主体观察名单同步目标池[" + poolId + "]不存在、未启用或为CRMW组合池");
            }
        }
        return pools;
    }

    /**
     * 查询观察名单，校验主体信息、观察类型及主体是否重复
     *
     * 执行顺序：
     * 1. 逐项查询主体观察名单列表
     *
     * @return 按主体代码索引的有效观察名单记录
     */
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

    /**
     * 同步观察类型对应的目标池及调入、调出联动池
     *
     * 执行顺序：
     * 1. 同一主体、目标池及调整方向只处理一次
     * 2. 生成主体和关系池共用的调库批次及调整原因
     * 3. 同步主体在目标池中的状态
     * 4. 调入时补齐联动池，调出时仅在目标池实际出池后联动调出
     *
     * @param company 入池候选（含命中标记与孰低外评）
     * @param observer 观察名单
     * @param pool 目标投资池
     * @param inbound 是否为调入
     * @param context 本轮同步共用数据，包含已处理组合、批次号和 Excel 保护池
     */
    private void syncSourcePool(ScheduledAdjustCandidateDto company, CompanyObserverDto observer,
                                InvestmentPoolBo pool, boolean inbound, SyncContext context) {
        String mode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        String companyCode = company.getSecurityCode();
        // 1. 同一主体、目标池及调整方向只处理一次
        if (!context.processed.add(companyCode + "|" + pool.getId() + "|" + mode)) {
            return;
        }
        // 2. 生成主体和关系池共用的调库批次及调整原因
        String batchNo = context.batches.resolveCompanyBatchNo(companyCode, pool.getId(), mode);
        String reason = ScheduledAdjustLogHelper.appendDetails(ScheduledAdjustLogHelper.taskLabel(TASK_NAME),
                "主体：" + companyCode,
                observer == null ? "观察名单已移除" : "observer_type=" + observer.getObserverType(),
                "目标池：" + pool.getPoolName());
        // 3. 同步主体在目标池中的状态
        boolean changed = syncCompany(company, pool, inbound, batchNo, reason, false, context);
        // 4. 调入时补齐联动池，调出时仅在目标池实际出池后联动调出
        if ((inbound || changed) && !context.protectedPools.getOrDefault(companyCode, Collections.emptySet())
                .contains(pool.getId())) {
            // 按调入、调出联动池配置同步主体
            syncLinkedPools(company, pool, inbound, batchNo, reason, context);
        }
    }

    /**
     * 按调入、调出联动池配置，同批同步主体
     *
     * 执行顺序：
     * 1. 按调整方向查询调入联动池或调出联动池
     * 2. 直接读取本轮预分组的关系池，不在每次主体校验时遍历全量配置
     * 3. 逐项追加明细
     * 4. 同步主体进出池，返回是否发生实际调整
     *
     * @param company 入池候选（含命中标记与孰低外评）
     * @param sourcePool 来源投资池
     * @param inbound 是否为调入
     * @param batchNo 当前调库批次号
     * @param reason 本次校验产生的原因或提示
     * @param context 本轮同步共用数据，包含池关系、池索引和已处理组合
     */
    private void syncLinkedPools(ScheduledAdjustCandidateDto company, InvestmentPoolBo sourcePool,
                                 boolean inbound, String batchNo, String reason, SyncContext context) {
        String mode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        // 1. 按调整方向查询调入联动池或调出联动池
        String relationType = inbound ? RelationType.IN_LINKED.getCode() : RelationType.OUT_LINKED.getCode();
        // 直接读取本轮预分组的关系池，不在每次主体校验时遍历全量配置
        List<Long> linkedPoolIds = resolveRelationPoolIds(sourcePool.getId(), relationType, context);
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
            // 4. 主体按原主体批次进出联动池
            syncCompany(company, linkedPool, inbound, batchNo, linkedReason, true, context);
        }
    }

    /**
     * 同步主体进出池，返回是否发生实际调整
     *
     * 执行顺序：
     * 1. 先取得主体及其当前在池、原始在池和保护范围
     * 2. 根据目标方向和当前状态决定是否写池记录，调入时同步互斥关系
     * 3. 返回目标池的主体状态是否实际发生变化
     *
     * @param company 入池候选（含命中标记与孰低外评）
     * @param pool 目标投资池
     * @param inbound 是否为调入
     * @param batchNo 当前调库批次号
     * @param reason 本次校验产生的原因或提示
     * @param linked 是否关联
     * @param context 本轮同步共用数据，包含主体状态、保护池、池关系及处理统计
     * @return true=实际更新了主体在池状态，false=未更新主体在池状态
     */
    private boolean syncCompany(ScheduledAdjustCandidateDto company, InvestmentPoolBo pool,
                                        boolean inbound, String batchNo, String reason, boolean linked,
                                        SyncContext context) {
        SyncSummary summary = context.summaries.computeIfAbsent(pool.getId(), key -> new SyncSummary());
        String companyCode = company.getSecurityCode();
        // 1. 查询主体当前在池情况，保留本轮调整前的来源池依据
        List<Long> currentPoolIds = getCurrentPoolIds(companyCode, context);
        context.originalPools.putIfAbsent(companyCode, new ArrayList<>(currentPoolIds));
        Set<Long> protectedPools = context.protectedPools.getOrDefault(companyCode, Collections.emptySet());
        if ((!inbound && protectedPools.contains(pool.getId()))
                || (inbound && !Collections.disjoint(protectedPools,
                context.inboundAutoOutPoolIds.getOrDefault(pool.getId(), Collections.emptyList())))) {
            context.detail.line("主体[" + companyCode + "]涉及 Excel 导入保护池，跳过当前池调整");
            return false;
        }
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
        return changed;
    }

    /**
     * 根据调入互斥池和反向调入限制池配置，调出主体及债券
     *
     * 执行顺序：
     * 1. 查询调入互斥池，以及将本次调入池配置为调入限制池的投资池
     * 2. 逐项追加明细
     * 3. 校验关系池，写入主体或债券的调库记录和池状态
     *
     * @param item 明细
     * @param inboundPool 入池投资池
     * @param batchNo 当前调库批次号
     * @param reason 本次校验产生的原因或提示
     * @param context 本轮同步共用数据，包含调入触发的关系池、当前在池状态和处理统计
     */
    private void syncInboundMutexPools(ScheduledAdjustCandidateDto item, InvestmentPoolBo inboundPool,
                                       String batchNo, String reason, SyncContext context) {
        // 1. 查询调入互斥池，以及将本次调入池配置为调入限制池的投资池
        List<Long> autoOutPoolIds = context.inboundAutoOutPoolIds.getOrDefault(inboundPool.getId(), Collections.emptyList());
        for (Long outPoolId : autoOutPoolIds) {
            // 2. 校验同一主体不能同时计划调入互斥的投资池
            if (context.requestInPoolIds.contains(outPoolId)) {
                throw new BizException("主体[" + item.getSecurityCode() + "]同批调入池与调入互斥/反向限制池冲突：" + outPoolId);
            }
            // 3. 只处理实际在池的主体，并校验调出池是否可用
            List<Long> currentPools = getCurrentPoolIds(item.getSecurityCode(), context);
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
            // 5. 统计实际调出池的主体数量
            SyncSummary outSummary = context.summaries.computeIfAbsent(outPoolId, key -> new SyncSummary());
            outSummary.companyOut++;
            context.summaries.get(inboundPool.getId()).relationOut++;
        }
    }

    /**
     * 校验关系池，写入主体或债券的调库记录和池状态
     *
     * 执行顺序：
     * 1. 校验来源池、调入调出限制池、调出互斥池、调入调出弹性禁投池
     * 2. 写入审批通过的主体或债券调库记录
     * 3. 调入时新增池状态，调出时软删除池状态
     * 4. 写入成功后立即维护本轮实时状态，后续联动、互斥及限制校验读取最新结果
     *
     * @param item 明细
     * @param pool 目标投资池
     * @param inbound 是否为调入
     * @param batchNo 当前调库批次号
     * @param reason 本次校验产生的原因或提示
     * @param adjustType 调库来源
     * @param currentPools 当前投资池列表
     * @param context 本轮同步共用数据，提供池关系校验、统一提交时间和生效状态缓存
     * @return 本次调库日志
     */
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
            throw new BizException("主体[" + item.getSecurityCode() + "]" + mode + "日志写入失败");
        }
        // 3. 调入时新增池状态，调出时软删除池状态
        adjustLog.setAdjustLogId(adjustLog.getId());
        int changed = inbound ? securityPoolAdjustMapper.addPoolStatus(adjustLog)
                : securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft(item.getSecurityCode(), pool.getId());
        if (changed != 1) {
            throw new BizException("主体[" + item.getSecurityCode() + "]" + mode + "池状态写入条数异常：" + changed);
        }
        // 写入成功后立即维护本轮实时状态，后续联动、互斥及限制校验读取最新结果
        Set<Long> currentPoolIds = context.currentPoolIds.computeIfAbsent(item.getSecurityCode(), key -> new LinkedHashSet<>());
        if (inbound) {
            currentPoolIds.add(pool.getId());
        } else {
            currentPoolIds.remove(pool.getId());
        }
        context.detail.line("处理成功：主体[" + item.getSecurityCode() + "] " + mode + " " + pool.getPoolName());
        return adjustLog;
    }

    /**
     * 来源池、调入调出限制池、调出互斥池、调入调出弹性禁投池的相关校验
     *
     * 执行顺序：
     * 1. 来源池校验：调入时须满足至少一个配置的来源池
     * 2. 调入、调出限制池校验：命中对应方向的限制池时不允许调整
     * 3. 调出互斥池校验：仍在调出互斥池时不允许调出
     * 4. 调入、调出弹性禁投池校验：命中时记录主体警告
     *
     * @param item 明细
     * @param pool 目标投资池
     * @param inbound 是否为调入
     * @param currentPools 当前投资池列表
     * @param context 本轮同步共用数据，包含调库前的池状态、本次拟调入池和池关系
     */
    private void validatePoolRelations(ScheduledAdjustCandidateDto item, InvestmentPoolBo pool, boolean inbound,
                                       List<Long> currentPools, SyncContext context) {
        // 1. 来源池校验：调入时须满足至少一个配置的来源池
        if (inbound) {
            // 直接读取本轮预分组的关系池，不在每次主体校验时遍历全量配置
            List<Long> sourcePools = resolveRelationPoolIds(pool.getId(), RelationType.SOURCE.getCode(), context);
            List<Long> originalPools = context.originalPools.get(item.getSecurityCode());
            if (!sourcePools.isEmpty() && !AutoAdjustRestrictHelper.isInAnyPool(currentPools, sourcePools)
                    && !AutoAdjustRestrictHelper.isInAnyPool(originalPools, sourcePools)
                    && !AutoAdjustRestrictHelper.isInAnyPool(new ArrayList<>(context.requestInPoolIds), sourcePools)) {
                throw new BizException("主体[" + item.getSecurityCode() + "]不满足" + pool.getPoolName() + "的来源池要求：" + sourcePools);
            }
        }
        // 2. 调入、调出限制池校验：命中对应方向的限制池时不允许调整
        String restrictType = inbound ? RelationType.IN_RESTRICT.getCode() : RelationType.OUT_RESTRICT.getCode();
        // 直接读取本轮预分组的关系池，不在每次主体校验时遍历全量配置
        List<Long> restrictPools = resolveRelationPoolIds(pool.getId(), restrictType, context);
        if (AutoAdjustRestrictHelper.isInAnyPool(currentPools, restrictPools)) {
            throw new BizException("主体[" + item.getSecurityCode() + "]命中" + pool.getPoolName()
                    + "的" + (inbound ? "调入" : "调出") + "限制池");
        }
        // 3. 调出互斥池校验：仍在调出互斥池时不允许调出
        if (!inbound) {
            // 直接读取本轮预分组的关系池，不在每次主体校验时遍历全量配置
            List<Long> outMutexPools = resolveRelationPoolIds(pool.getId(), RelationType.OUT_MUTEX.getCode(), context);
            if (AutoAdjustRestrictHelper.isInAnyPool(currentPools, outMutexPools)) {
                throw new BizException("主体[" + item.getSecurityCode() + "]命中" + pool.getPoolName() + "的调出互斥池：" + outMutexPools);
            }
        }
        // 4. 调入、调出弹性禁投池校验：命中时记录主体警告
        String softType = inbound ? RelationType.IN_SOFT_RESTRICT.getCode() : RelationType.OUT_SOFT_RESTRICT.getCode();
        // 直接读取本轮预分组的关系池，不在每次主体校验时遍历全量配置
        List<Long> softPools = resolveRelationPoolIds(pool.getId(), softType, context);
        if (AutoAdjustRestrictHelper.isInAnyPool(currentPools, softPools)) {
            context.detail.line("WARN", "主体[" + item.getSecurityCode() + "]命中" + pool.getPoolName()
                    + "的" + (inbound ? "调入" : "调出") + "弹性禁投池（警告不阻断）：" + softPools);
        }
    }

    /**
     * 执行失败时回滚本轮调库记录和池状态
     *
     * 执行顺序：
     * 1. 将当前事务标记为只回滚，没有事务时仅记调试日志
     */
    private void markTransactionRollbackOnly() {
        try {
            // 1. 标记本轮事务回滚，撤销已写入的调库记录和池状态
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            log.debug("当前无可回滚事务: {}", TASK_CODE);
        }
    }

    /**
     * 保存本轮观察池同步共用的数据
     *
     * 预先整理池关系和 Excel 保护范围，记录主体原有及本轮变更后的池状态，供来源和互斥校验复用
     * 同一主体、目标池和方向只处理一次，批次和统计随本轮同步维护
     */
    private static final class SyncContext {
        /** 全量池配置 */
        private final Map<Long, InvestmentPoolBo> pools;

        /** 按投资池和关系类型分组的有效关系 */
        private final Map<Long, Map<String, List<Long>>> relationPoolIds = new LinkedHashMap<>();

        /** 各池调入后须配套调出的互斥池及反向限制池 */
        private final Map<Long, List<Long>> inboundAutoOutPoolIds = new LinkedHashMap<>();

        /** 本轮实时池状态，数据库写入成功后同步更新 */
        private final Map<String, Set<Long>> protectedPools = new LinkedHashMap<>();

        /** 按主体代码保存本轮实时在池的投资池 ID */
        private final Map<String, Set<Long>> currentPoolIds = new LinkedHashMap<>();

        /** 按主体、原目标池和调整方向分组的调库批次 */
        private final ScheduledAdjustBatchNoContext batches = new ScheduledAdjustBatchNoContext();

        /** 已处理的主体、目标池及方向，避免重复联动 */
        private final Set<String> processed = new LinkedHashSet<>();

        /** 每个调整对象本轮首次读取的在池状态，用于先出后入时保留来源池依据 */
        private final Map<String, List<Long>> originalPools = new LinkedHashMap<>();

        /** 当前主体同批计划调入的目标池和联动池 */
        private Set<Long> requestInPoolIds = new LinkedHashSet<>();

        /** 各投资池的实际调整数量 */
        private final Map<Long, SyncSummary> summaries = new LinkedHashMap<>();

        /** 本轮过程日志 */
        private final TaskDetailLog detail;

        /**
         * 初始化一轮同步上下文
         *
         * 执行顺序：
         * 1. 保存投资池配置和本轮任务的日志记录器
         * 2. 按目标池和关系类型整理可用的关联池配置，并为各池计算调入后应自动调出的关系池
         * 3. 为本轮扫描的每个池初始化统计
         *
         * @param pools 按池 ID 整理的投资池配置
         * @param poolIds 本轮需要初始化统计的池 ID
         * @param relations 可用于本轮计算的投资池关系
         * @param detail 本轮任务的过程日志记录器
         */
        private SyncContext(Map<Long, InvestmentPoolBo> pools, List<Long> poolIds,
                            List<PoolRelationBo> relations, TaskDetailLog detail) {
            // 1. 保存本轮使用的投资池、关系池配置和过程日志
            this.pools = pools;
            this.detail = detail;
            // 1.1 按池和关系类型索引，保留直接关系原有顺序及忽略空关系的处理口径
            for (PoolRelationBo relation : relations) {
                if (relation == null || relation.getPoolId() == null || relation.getRelationType() == null
                        || relation.getRelationPoolId() == null) {
                    continue;
                }
                relationPoolIds.computeIfAbsent(relation.getPoolId(), key -> new LinkedHashMap<>())
                        .computeIfAbsent(relation.getRelationType(), key -> new ArrayList<>()).add(relation.getRelationPoolId());
            }
            // 1.2 每个池只计算一次调入互斥和反向限制关系，证券循环直接使用结果
            for (Long poolId : pools.keySet()) {
                inboundAutoOutPoolIds.put(poolId, AutoAdjustRelationHelper.resolveInboundAutoOutPoolIds(poolId, relations));
            }
            // 2. 初始化配置目标池的主体进出池统计
            for (Long poolId : poolIds) {
                summaries.put(poolId, new SyncSummary());
            }
        }
    }

    /**
     * 记录一轮观察池同步的执行摘要
     *
     * 将来源池及联动池的实际变化条数与按目标池汇总的说明一起返回
     */
    private static final class SyncResult {
        /** 包含源池及联动池实际变化的总条数 */
        private int affectedCount;

        /** 按实际目标池汇总的说明 */
        private String message;
    }

    /**
     * 记录单个投资池在观察池同步任务中的主体调库数量
     *
     * 分别累计主体调入、主体调出及由调入互斥或反向限制触发的出池，供任务汇总
     */
    private static final class SyncSummary {
        /** 主体入池数 */
        private int companyIn;

        /** 主体出池数 */
        private int companyOut;

        /** 主体因调入互斥池、反向调入限制池配置产生的出池数 */
        private int relationOut;
    }
}
