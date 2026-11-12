package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AdjustType;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.common.util.MarketCodeMatchUtil;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.AbsBondCandidateDto;
import com.znty.rrs.entity.schedule.AbsCompanyRelationDto;
import com.znty.rrs.entity.schedule.PoolSecurityRowDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AbsPoolReconcileMapper;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 主体下ABS债券自动入库（区分已确认发行人和未确认发行人）
 *
 * ABS 的进出池由本任务统一按池做全量对账，先算出按规则应在池的记录，再与实际在池记录求差集
 * 1. 已确认发行主体的 ABS 直接跟随发行主体入池、出池
 * 2. 未确认发行主体的 ABS 只生成 absCompanySync 规则提醒与待确认调库日志，等研究员确认身份后由下一轮对账落池
 * 3. 判定统一复用 AbsCompanySyncService.allowScheduledSync，债级独立入库依据、未结束调库流程、其他关联主体是否仍在池、黑名单质押库三条件和提醒幂等都由该入口处理
 * 4. 数据通过 ABS 主档、四类关联主体、各池生效主体、各池生效 ABS 四条批量查询装载，在内存中完成差集计算
 */
@Slf4j
@Service
public class CompanyAbsBondAutoInService implements RrsScheduledTask {

    /** 任务编码，与库表 task_code 绑定 */
    public static final String TASK_CODE = "company_abs_bond_auto_in";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";

    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";

    /** 任务默认展示名称 */
    private static final String DEFAULT_TASK_NAME = "主体下ABS债券自动入库（区分已确认发行人和未确认发行人）";

    /** 自动入池原因 */
    private static final String REASON_IN = "主体下ABS债券自动入库";

    /** 自动出池原因 */
    private static final String REASON_OUT = "主体下ABS债券自动出池";

    /** 距到期最小剩余天数默认值，0 表示到期当天仍可入池 */
    private static final int DEFAULT_MIN_DAYS_TO_MATURITY = 0;

    /** 到期日格式，与主档 maturity_date 一致 */
    private static final String DATE_PATTERN = "yyyyMMdd";

    /** 到期日解析格式，用于计算距到期的剩余天数 */
    private static final DateTimeFormatter MATURITY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /** 扩展参数 JSON 解析器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 扩展参数说明 */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，可全部省略，例如 <code>{}</code>；常用写法 <code>{\"enableIn\":false,\"enableOut\":true}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "入池开关：<code>{\"enableIn\":false}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：只做出池、不补入 ABS；省略时默认关闭\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "出池开关：<code>{\"enableOut\":false}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：只补入 ABS、不自动出池；省略时默认开启。首次上线建议先置 false 观察一轮\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "到期保护：<code>{\"minDaysToMaturity\":30}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：距到期不足该天数的 ABS 不再自动补入池；0 表示到期当天仍可入，省略时默认 0。已在池的不受影响，仍由到期出池任务统一清理\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "指定扫描池：<code>{\"poolIds\":[15,17]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：在这些池内做同池对账；省略时自动并入所有已有生效主体的普通池、关系配置绑定本任务的池，以及当前已有 ABS 在池的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "跨池映射：<code>{\"mappings\":[{\"bondPoolId\":16,\"companyPoolId\":15}]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：主体在 15（债券禁止库）时，旗下 ABS 写入 16（观察池）；配置后该债券池不再按同池口径对账\n"
                    + "扫描范围：扩展参数 poolIds、关系配置绑定本任务的池、已有生效主体的普通池、当前已有 ABS 在池的池，四者并集；并集为空时本轮失败\n"
                    + "已确认发行人：主档 issuer_code 有值时视为已确认，直接跟随该主体在该池的进出状态自动入池、出池\n"
                    + "未确认发行人：主档 issuer_code 为空时，按担保人、差额支付承诺人、债务主体、原始权益人四类关系匹配池内主体，只生成规则提醒与待确认日志，不自动落池\n"
                    + "出池保护：出池前先判断债级独立入库依据（手工、批量、Excel 入池）、未结束调库流程，以及未确认 ABS 的其他关联主体是否仍在池，命中则只提醒不自动出池\n"
                    + "17 特别规则：黑名单质押库的入池、出池均再次校验主体三条件\n"
                    + "市场规则：目标池 market_codes 未配置（null 或 []）时不限制；有配置时 ABS 须命中允许市场，市场缺失或不受支持的记录跳过并记告警\n"
                    + "限制规则：ABS 已在目标池配置的调入限制池时跳过入池；入池成功后按互斥与反向调入限制关系自动调出原所在池\n"
                    + "事务边界：按池提交，单池失败只回滚该池，不影响其他池\n"
                    + "参数格式错误时，本轮任务失败";

    /** ABS 对账批量查询 */
    @Resource
    private AbsPoolReconcileMapper absPoolReconcileMapper;

    /** 调库落地 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 自动调库查询 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;

    /** 投资池 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 定时任务配置 */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    /** 扫描池并集，合并参数与关系配置 */
    @Resource
    private AutoAdjustPoolScopeHelper poolScopeHelper;

    /** ABS 主体联动判定，人工与定时共用 */
    @Resource
    private AbsCompanySyncService absCompanySyncService;

    /** 当前证券市场唯一映射校验组件 */
    @Resource
    private SecurityMarketService securityMarketService;

    /** 事务模板，用于按池提交 */
    @Resource
    private PlatformTransactionManager transactionManager;

    /** 返回主体下ABS债券自动入库任务编码 */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** 返回主体下ABS债券自动入库任务参数说明 */
    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 任务入口，只负责计时、记录过程日志和把异常转成失败结果
     *
     * 执行顺序：
     * 1. 读取任务配置，取得任务名与扩展参数
     * 2. 调用主流程按池对账
     * 3. 成功返回入池、出池与提醒汇总，业务异常与系统异常都转成失败结果
     *
     * @return 本轮任务执行结果
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        String taskName = TASK_CODE;
        try {
            // 1. 读取任务名与扩展参数，配置表只查一次
            TaskConfig config = loadTaskConfig();
            taskName = config.taskName;
            infoDetail(detail, taskName + " 开始");
            // 2. 按池对账 ABS 进出
            AbsSyncSummary summary = ScheduledAdjustLogHelper.runWithTaskName(config.taskName,
                    () -> doSync(config, detail));
            long duration = System.currentTimeMillis() - begin;
            String message = summary.buildMessage();
            infoDetail(detail, taskName + " 结束，" + message);
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, summary.total(), startTime, duration,
                    detail.build());
        } catch (BizException e) {
            long duration = System.currentTimeMillis() - begin;
            warnDetail(detail, taskName + " 失败: " + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName, e.getMessage(), startTime, duration,
                    detail.build());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", taskName, e);
            detail.line("ERROR", taskName + " 异常: " + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName,
                    "执行异常: " + e.getMessage(), startTime, duration, detail.build());
        }
    }

    /**
     * 按池对账 ABS 进出，是整条任务的主干
     *
     * 每只 ABS 的进出只由「主体依据池」决定，因此先算出本轮要扫的池，再逐池开事务处理，
     * 单池失败不会影响其他池
     *
     * 执行顺序：
     * 1. 解析扩展参数，入池与出池开关同时关闭时本轮失败
     * 2. 四条批量查询装载对账快照，并加载全部池与池关系
     * 3. 合并四个来源得到扫描池，生成「债券池 ← 主体依据池」映射组
     * 4. 逐池开事务对账，失败池只回滚自己
     * 5. 输出本轮汇总
     *
     * @param config 任务配置，含任务名与扩展参数原文
     * @param detail 任务过程日志
     * @return 本轮对账汇总
     */
    private AbsSyncSummary doSync(TaskConfig config, TaskDetailLog detail) {
        // 1. 解析扩展参数并校验开关
        AbsSyncParams params = parseParams(config.paramJson);
        infoDetail(detail, "扩展参数：enableIn=" + params.enableIn + "，enableOut=" + params.enableOut
                + "，minDaysToMaturity=" + params.minDaysToMaturity
                + "，poolIds=" + params.poolIds + "，mappings=" + joinMappings(params.mappings));
        checkSwitches(params);

        // 2. 装载对账快照与池配置
        AbsSnapshot snapshot = loadSnapshot(detail);
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        AbsSyncContext context = new AbsSyncContext(poolMap, snapshot, allRelations, params,
                new SimpleDateFormat(DATE_PATTERN).format(new Date()));
        infoDetail(detail, "扫描条件：ABS 主档未退市且属 bond 大类；已确认发行人按主档 issuer_code 与池内主体等值匹配；"
                + "未确认发行人按四类关系匹配池内主体并只生成提醒；"
                + "ABS 主档 " + snapshot.absList.size() + " 条");

        // 3. 合并扫描池并生成映射组
        List<long[]> pairs = resolvePairs(params, snapshot, detail);
        infoDetail(detail, "有效映射组 " + pairs.size() + " 组");

        // 4. 逐池对账
        AbsSyncSummary summary = reconcilePools(pairs, context, detail);

        // 5. 输出本轮汇总
        infoDetail(detail, summary.buildMessage());
        return summary;
    }

    /**
     * 逐池对账，每池独立事务
     *
     * 单池写入失败只回滚该池，其余池正常提交；池不存在或为 CRMW 组合池时直接跳过，
     * 不当作失败
     *
     * 执行顺序：
     * 1. 债券池不存在时跳过该映射
     * 2. 债券池是 CRMW 组合池时跳过，ABS 不能通过普通池状态写入组合池
     * 3. 在单池事务内执行出池与入池，异常只回滚该池
     * 4. 合并该池结果到本轮汇总
     *
     * @param pairs 债券池与主体依据池的映射组
     * @param context 本轮共享上下文
     * @param detail 任务过程日志
     * @return 本轮对账汇总
     */
    private AbsSyncSummary reconcilePools(List<long[]> pairs, AbsSyncContext context, TaskDetailLog detail) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        AbsSyncSummary summary = new AbsSyncSummary();
        for (long[] pair : pairs) {
            Long bondPoolId = pair[0];
            Long companyPoolId = pair[1];
            InvestmentPoolBo bondPool = context.poolMap.get(bondPoolId);
            if (bondPool == null) {
                warnDetail(detail, "债券池[" + bondPoolId + "]不存在，跳过映射 "
                        + companyPoolId + "→" + bondPoolId);
                continue;
            }
            if (CompanyBondSyncPolicy.isCrmwCombinationPool(bondPool)) {
                warnDetail(detail, "债券池[" + bondPool.getPoolName() + "](" + bondPoolId
                        + ") 为 CRMW 组合池，ABS 不能通过普通池状态写入，跳过该映射");
                continue;
            }
            // 3. 组装单池上下文，池内主体与池内 ABS 直接取快照
            PoolContext pool = new PoolContext(bondPool, companyPoolId, context);
            PoolResult result;
            try {
                // 4. 单池一个事务，避免一只 ABS 失败拖垮整轮
                result = template.execute(status -> processPool(pool, context, detail));
            } catch (BizException e) {
                warnDetail(detail, "池[" + bondPool.getPoolName() + "](" + bondPoolId + ") 处理失败已回滚："
                        + e.getMessage());
                summary.addFailedPool(bondPoolId, bondPool.getPoolName());
                continue;
            }
            if (result != null) {
                summary.merge(bondPoolId, bondPool.getPoolName(), result);
            }
        }
        return summary;
    }

    /**
     * 单池对账，固定先出池后入池
     *
     * 出池要求主体已不在依据池，入池要求主体仍在依据池，两者条件互斥，
     * 因此先出后入不会让同一只 ABS 在本轮来回搬动
     *
     * 执行顺序：
     * 1. 记录本池的主体依据与池内现状
     * 2. 出池开关开启时先处理出池
     * 3. 入池开关开启时再处理入池
     * 4. 记录本池入池、出池与提醒数量
     *
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param detail 任务过程日志
     * @return 本池处理结果
     */
    private PoolResult processPool(PoolContext pool, AbsSyncContext context, TaskDetailLog detail) {
        PoolResult result = new PoolResult();
        infoDetail(detail, "池[" + pool.bondPool.getPoolName() + "](" + pool.bondPoolId
                + ") 主体依据池=" + pool.companyPoolId
                + "，池内生效主体 " + pool.companySet.size() + " 个，池内生效 ABS " + pool.absInPool.size() + " 只");

        // 1. 出池方向先执行，避免先补入再踢出
        if (context.params.enableOut) {
            outPhase(pool, context, result, detail);
        }

        // 2. 入池方向后执行
        if (context.params.enableIn) {
            inPhase(pool, context, result, detail);
        }

        // 3. 记录本池结果
        infoDetail(detail, "池[" + pool.bondPool.getPoolName() + "](" + pool.bondPoolId + ") 入池 "
                + result.inCount + " 条、出池 " + result.outCount + " 条、规则提醒 " + result.reminderCount + " 条");
        return result;
    }

    /**
     * 出池阶段：池内已有 ABS 但已失去留池依据
     *
     * 执行顺序：
     * 1. 挑出本轮待出池的 ABS 候选
     * 2. 逐只过出池保护后执行出池
     *
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param result 本池处理结果
     * @param detail 任务过程日志
     */
    private void outPhase(PoolContext pool, AbsSyncContext context, PoolResult result, TaskDetailLog detail) {
        // 1. 挑出已失去留池依据的池内 ABS
        List<AbsBondCandidateDto> candidates = collectOutCandidates(pool, context, detail);
        if (candidates.isEmpty()) {
            return;
        }
        // 2. 过保护后执行出池
        executeOut(candidates, pool, context, result, detail);
    }

    /**
     * 挑选待出池候选：只要还有依据支撑就继续留池
     *
     * 已确认发行人只看主档发行主体，未确认时只要还有任一关联主体在池就保留，
     * 出池交给后续人工结论，与 AbsCompanySyncService 的提醒口径一致
     *
     * 执行顺序：
     * 1. 逐只检查池内生效 ABS 是否还在主档中
     * 2. 主档缺失或已退市的保留现状，不自动出池
     * 3. 仍具备留池依据的跳过，其余进入候选
     *
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param detail 任务过程日志
     * @return 待出池的 ABS 主档列表
     */
    private List<AbsBondCandidateDto> collectOutCandidates(PoolContext pool, AbsSyncContext context,
                                                          TaskDetailLog detail) {
        List<AbsBondCandidateDto> candidates = new ArrayList<>();
        for (String securityCode : pool.absInPool) {
            AbsBondCandidateDto abs = context.snapshot.absMap.get(securityCode);
            if (abs == null) {
                warnDetail(detail, "ABS[" + securityCode + "]在主档中不存在或已退市，保留现状不自动出池");
                continue;
            }
            // 1. 依据还在时继续留池，不进入出池候选
            if (shouldStayInPool(abs, pool.companySet, context.snapshot.relationMap)) {
                continue;
            }
            candidates.add(abs);
        }
        return candidates;
    }

    /**
     * 执行出池，逐只过保护后落库
     *
     * 执行顺序：
     * 1. 逐只走统一联动判定，命中债级独立依据、在途流程或其他关联主体仍在池时只留提醒
     * 2. 软删池状态，并发已出池的跳过
     * 3. 写自动调库日志并累计出池数量
     *
     * @param candidates 待出池的 ABS 主档列表
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param result 本池处理结果
     * @param detail 任务过程日志
     */
    private void executeOut(List<AbsBondCandidateDto> candidates, PoolContext pool, AbsSyncContext context,
                            PoolResult result, TaskDetailLog detail) {
        for (AbsBondCandidateDto abs : candidates) {
            // 1.1 出池时主体都已不在依据池，联动触发主体为空，日志只记主体所在池
            String trigger = null;
            // 1.2 生成出池方向的调库日志对象，原因与批次号一并填好
            ScheduledAdjustCandidateDto candidate = buildAdjustCandidate(abs, pool, context,
                    AdjustMode.OUT.getCode(), REASON_OUT, trigger);
            // 1.3 统一联动判定，命中保护时只生成提醒
            if (!absCompanySyncService.allowScheduledSync(candidate, trigger, pool.bondPoolId,
                    pool.bondPool.getPoolName(), false, pool.companyPoolId)) {
                result.reminderCount++;
                infoDetail(detail, "ABS[" + abs.getSecurityCode() + "]存在出池保护依据，已生成或保留规则提醒，本轮不自动出池");
                continue;
            }
            // 1.4 软删池状态，返回 0 说明并发已出池
            int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(abs.getSecurityCode(), pool.bondPoolId);
            if (deleted == 0) {
                warnDetail(detail, "ABS[" + abs.getSecurityCode() + "]软删失败（可能并发已出池），跳过");
                continue;
            }
            // 1.5 写自动出池日志
            securityPoolAdjustMapper.addAdjustLog(candidate);
            result.outCount++;
        }
    }

    /**
     * 入池阶段：主档中未在池、且主体依据已命中的 ABS
     *
     * 执行顺序：
     * 1. 挑出本轮待入池的 ABS 候选与触发主体
     * 2. 逐只过入池规则后落池
     *
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param result 本池处理结果
     * @param detail 任务过程日志
     */
    private void inPhase(PoolContext pool, AbsSyncContext context, PoolResult result, TaskDetailLog detail) {
        // 1. 挑出未在池且主体依据命中的 ABS，同时确定联动触发主体
        Map<String, String> candidates = collectInCandidates(pool, context, detail);
        if (candidates.isEmpty()) {
            return;
        }
        // 2. 过规则后执行入池
        executeIn(candidates, pool, context, result, detail);
    }

    /**
     * 挑选待入池候选，返回「ABS 代码 → 联动触发主体」
     *
     * 已确认发行人取主档发行主体，未确认时取第一个仍在池的关联主体；
     * 没有任何主体依据的 ABS 不参与本池入池
     *
     * 执行顺序：
     * 1. 跳过代码为空、已在目标池、已到期或距到期不足保护天数的 ABS
     * 2. 跳过市场不满足目标池限制的 ABS
     * 3. 取联动触发主体，取不到说明本轮没有主体依据
     *
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param detail 任务过程日志
     * @return ABS 代码到联动触发主体的映射，按代码升序
     */
    private Map<String, String> collectInCandidates(PoolContext pool, AbsSyncContext context,
                                                    TaskDetailLog detail) {
        Map<String, String> candidates = new LinkedHashMap<>();
        for (AbsBondCandidateDto abs : context.snapshot.absList) {
            String securityCode = abs.getSecurityCode();
            if (!StringUtils.hasText(securityCode) || pool.absInPool.contains(securityCode)) {
                continue;
            }
            // 1. 已到期或距到期不足保护天数的 ABS 不再补入
            if (isMaturityTooClose(abs, context.today, context.params.minDaysToMaturity)) {
                if (context.params.minDaysToMaturity > 0) {
                    infoDetail(detail, "ABS[" + securityCode + "]到期日 " + abs.getMaturityDate()
                            + "，距到期不足 " + context.params.minDaysToMaturity + " 天，跳过自动入池");
                }
                continue;
            }
            // 2. 目标池配置了市场限制时按当前市场准入
            if (!marketAllowed(pool, context, abs, detail)) {
                continue;
            }
            // 3. 解析本轮联动触发主体，取不到说明主体依据不在该池
            String trigger = resolveTriggerCompany(abs, context.snapshot.relationMap, pool.companySet);
            if (trigger == null) {
                continue;
            }
            candidates.put(securityCode, trigger);
        }
        return candidates;
    }

    /**
     * 执行入池，逐只过规则后写日志与池状态
     *
     * 每只 ABS 的处理顺序固定：先统一联动判定，再查调入限制池与当前所在池，
     * 通过后才写调库日志和池状态，最后按互斥关系调出原所在池
     *
     * 执行顺序：
     * 1. 解析目标池配置的调入限制池
     * 2. 逐只走统一联动判定，未确认主体或已有结论时只留提醒
     * 3. 已在调入限制池的跳过，已在目标池生效的跳过
     * 4. 写自动入池日志与池状态，两条都必须成功
     * 5. 入池成功后按互斥与反向限制关系调出原所在池
     *
     * @param candidates ABS 代码到联动触发主体的映射
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param result 本池处理结果
     * @param detail 任务过程日志
     */
    private void executeIn(Map<String, String> candidates, PoolContext pool, AbsSyncContext context,
                           PoolResult result, TaskDetailLog detail) {
        // 1. 解析目标池的调入限制池关系
        List<Long> inRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                pool.bondPoolId, RelationType.IN_RESTRICT.getCode(), context.allRelations);
        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            String securityCode = entry.getKey();
            String trigger = entry.getValue();
            AbsBondCandidateDto abs = context.snapshot.absMap.get(securityCode);
            if (abs == null) {
                continue;
            }
            // 2.1 生成入池方向的调库日志对象，原因与批次号一并填好
            ScheduledAdjustCandidateDto candidate = buildAdjustCandidate(abs, pool, context,
                    AdjustMode.IN.getCode(), REASON_IN, trigger);
            // 2.2 统一联动判定，未确认主体或已有处理结论时只生成提醒
            if (!absCompanySyncService.allowScheduledSync(candidate, trigger, pool.bondPoolId,
                    pool.bondPool.getPoolName(), true, pool.companyPoolId)) {
                result.reminderCount++;
                infoDetail(detail, "ABS[" + securityCode + "]主体未确认或存在入池规则提醒，本轮跳过自动入池");
                continue;
            }
            // 2.3 查当前实际所在池，用于限制池阻断与并发复核
            List<Long> currentPoolIds = securityPoolAdjustMapper.querySecurityCurrentPoolIdList(securityCode);
            if (AutoAdjustRestrictHelper.isInAnyPool(currentPoolIds, inRestrictPoolIds)) {
                warnDetail(detail, "ABS[" + securityCode + "]当前在调入限制池中，跳过");
                continue;
            }
            if (currentPoolIds != null && currentPoolIds.contains(pool.bondPoolId)) {
                infoDetail(detail, "ABS[" + securityCode + "]已在目标池生效，跳过");
                continue;
            }
            // 2.4 先写调库日志，再用日志主键写池状态
            if (securityPoolAdjustMapper.addAdjustLog(candidate) != 1) {
                throw new BizException("ABS[" + securityCode + "]自动调入日志写入失败");
            }
            candidate.setAdjustLogId(candidate.getId());
            if (securityPoolAdjustMapper.addPoolStatus(candidate) != 1) {
                throw new BizException("ABS[" + securityCode + "]写入池状态失败（可能并发已入池）");
            }
            result.inCount++;
            // 2.5 入池成功后调出互斥与反向限制池
            int autoOutCount = AutoAdjustRelationHelper.autoOutCurrentRelationPools(
                    candidate, currentPoolIds, context.poolMap, context.allRelations, securityPoolAdjustMapper);
            if (autoOutCount > 0) {
                infoDetail(detail, "ABS[" + securityCode + "]自动调出关系池 " + autoOutCount + " 个");
            }
        }
    }

    /**
     * 判断 ABS 是否可以继续留在依据池
     *
     * 已确认发行人只看主档发行主体；未确认时只要还有任一关联主体在池就保留
     *
     * 执行顺序：
     * 1. 已确认发行人时，判断发行主体是否仍在池
     * 2. 未确认时，判断四类关联主体与池内主体是否有交集
     *
     * @param abs ABS 主档
     * @param companySet 池内生效主体
     * @param relationMap ABS 到四类关联主体的映射
     * @return true 表示仍可留池
     */
    private boolean shouldStayInPool(AbsBondCandidateDto abs, Set<String> companySet,
                                     Map<String, Set<String>> relationMap) {
        if (StringUtils.hasText(abs.getIssuerCode())) {
            return companySet.contains(abs.getIssuerCode().trim());
        }
        Set<String> related = relationMap.get(abs.getSecurityCode());
        return related != null && !Collections.disjoint(related, companySet);
    }

    /**
     * 解析本轮联动触发主体
     *
     * 已确认取主档发行主体，未确认取第一个仍在池的关联主体
     *
     * 执行顺序：
     * 1. 已确认发行人时，发行主体在池即作为触发主体，否则没有依据
     * 2. 未确认时按关联主体顺序找第一个在池的主体
     *
     * @param abs ABS 主档
     * @param relationMap ABS 到四类关联主体的映射
     * @param companySet 池内生效主体
     * @return 触发主体代码，null 表示本轮没有主体依据
     */
    private String resolveTriggerCompany(AbsBondCandidateDto abs, Map<String, Set<String>> relationMap,
                                         Set<String> companySet) {
        if (StringUtils.hasText(abs.getIssuerCode())) {
            String issuerCode = abs.getIssuerCode().trim();
            return companySet.contains(issuerCode) ? issuerCode : null;
        }
        Set<String> related = relationMap.get(abs.getSecurityCode());
        if (related == null || related.isEmpty()) {
            return null;
        }
        for (String companyCode : related) {
            if (companySet.contains(companyCode)) {
                return companyCode;
            }
        }
        return null;
    }

    /**
     * 判断 ABS 是否已到期，或距到期不足保护天数
     *
     * 到期日为空时不限制；保护天数为 0 时等价于「到期日早于今天才跳过」，
     * 即到期当天仍可补入，与改造前的行为保持一致
     *
     * 执行顺序：
     * 1. 到期日为空直接放行
     * 2. 按 yyyyMMdd 解析后算剩余天数，不足保护天数则不再补入
     * 3. 日期格式异常时退回字符串比较，至少挡住已过期的记录
     *
     * @param abs ABS 主档
     * @param today 当天日期，格式 yyyyMMdd
     * @param minDays 距到期最小剩余天数
     * @return true 表示不再补入
     */
    private boolean isMaturityTooClose(AbsBondCandidateDto abs, String today, int minDays) {
        String maturity = abs.getMaturityDate() == null ? null : abs.getMaturityDate().trim();
        if (!StringUtils.hasText(maturity)) {
            return false;
        }
        try {
            long remainDays = ChronoUnit.DAYS.between(
                    LocalDate.parse(today, MATURITY_FORMATTER), LocalDate.parse(maturity, MATURITY_FORMATTER));
            return remainDays < minDays;
        } catch (DateTimeParseException e) {
            log.warn("ABS[{}]到期日格式异常: {}，按字符串比较处理", abs.getSecurityCode(), maturity);
            return maturity.compareTo(today) < 0;
        }
    }

    /**
     * 目标池市场准入判断
     *
     * 池未配置 market_codes 时不限制；配置了则按当前唯一市场比对，
     * 市场缺失或不受支持的记录跳过并记告警，不中断整轮
     *
     * 执行顺序：
     * 1. 池没有市场限制时直接放行
     * 2. 读取 ABS 当前市场，缺失时记告警并跳过
     * 3. 按池配置的市场列表比对
     *
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param abs ABS 主档
     * @param detail 任务过程日志
     * @return true 表示市场允许入池
     */
    private boolean marketAllowed(PoolContext pool, AbsSyncContext context, AbsBondCandidateDto abs,
                                  TaskDetailLog detail) {
        String marketCodes = pool.bondPool.getMarketCodes();
        if (!hasMarketRestriction(marketCodes)) {
            return true;
        }
        // 1. 市场查询失败时回退主档，避免一只脏数据毁掉整轮
        String market = resolveMarketQuietly(context, abs, detail);
        if (!StringUtils.hasText(market)) {
            warnDetail(detail, "ABS[" + abs.getSecurityCode() + "]缺少可用市场，池["
                    + pool.bondPool.getPoolName() + "]配置了市场限制，跳过");
            return false;
        }
        boolean matched = MarketCodeMatchUtil.matchesPoolMarkets(marketCodes, market);
        if (!matched) {
            infoDetail(detail, "ABS[" + abs.getSecurityCode() + "]市场=" + market
                    + " 不在池[" + pool.bondPool.getPoolName() + "]允许市场内，跳过");
        }
        return matched;
    }

    /**
     * 读取 ABS 当前市场，失败时回退主档并只告警一次
     *
     * 同一只 ABS 在多个池对账时只查一次市场，结果缓存在本轮上下文中
     *
     * 执行顺序：
     * 1. 缓存命中时直接返回
     * 2. 调用统一市场校验，抛异常时回退主档 market 并记告警
     * 3. 结果写入缓存
     *
     * @param context 本轮共享上下文
     * @param abs ABS 主档
     * @param detail 任务过程日志
     * @return 当前市场 code，取不到时返回 null
     */
    private String resolveMarketQuietly(AbsSyncContext context, AbsBondCandidateDto abs, TaskDetailLog detail) {
        String securityCode = abs.getSecurityCode();
        if (context.marketCache.containsKey(securityCode)) {
            return context.marketCache.get(securityCode);
        }
        String market;
        try {
            market = securityMarketService.queryMarketCode(securityCode);
        } catch (BizException e) {
            warnDetail(detail, "ABS[" + securityCode + "]市场校验异常，回退主档 market：" + e.getMessage());
            market = abs.getMarket() == null ? null : abs.getMarket().trim();
        }
        context.marketCache.put(securityCode, market);
        return market;
    }

    /**
     * 判断池是否配置了市场限制
     *
     * market_codes 为 null、空串或空数组时都视为不限制
     *
     * @param marketCodes 池 market_codes JSON 原文
     * @return true 表示需要按市场过滤
     */
    private boolean hasMarketRestriction(String marketCodes) {
        return StringUtils.hasText(marketCodes) && !"[]".equals(marketCodes.trim());
    }

    /**
     * 用 ABS 主档生成一条自动调库日志对象
     *
     * 出池与入池共用同一套字段填充，避免两条分支各写一遍导致批次号或原因格式不一致
     *
     * 执行顺序：
     * 1. 复制主档的代码、简称、类型、ABS 标志、发行主体与到期日
     * 2. 填自动调整类型、调整方向、目标池、审核状态与系统操作人
     * 3. 追加发行主体、主体所在池、联动触发主体三段原因说明
     * 4. 按债券和目标池生成批次号，提交时间取本轮统一时间
     *
     * @param abs ABS 主档
     * @param pool 单池上下文
     * @param context 本轮共享上下文
     * @param adjustMode 调整方向
     * @param reasonPrefix 原因前缀
     * @param trigger 联动触发主体，没有时为空
     * @return 可直接落库的调库日志对象
     */
    private ScheduledAdjustCandidateDto buildAdjustCandidate(AbsBondCandidateDto abs, PoolContext pool,
                                                             AbsSyncContext context, String adjustMode,
                                                             String reasonPrefix, String trigger) {
        ScheduledAdjustCandidateDto candidate = new ScheduledAdjustCandidateDto();
        candidate.setSecurityCode(abs.getSecurityCode());
        candidate.setSecurityShortName(abs.getSecurityShortName());
        candidate.setSecurityType(abs.getSecurityType());
        candidate.setAbsFlag(abs.getAbsFlag());
        candidate.setIssuerCode(abs.getIssuerCode());
        candidate.setIssuerName(abs.getIssuerName());
        candidate.setMaturityDate(abs.getMaturityDate());
        candidate.setTriggerCompanyCode(trigger);
        candidate.setAdjustType(AdjustType.AUTO.getCode());
        candidate.setAdjustMode(adjustMode);
        candidate.setTargetPoolId(pool.bondPoolId);
        candidate.setTargetPoolName(pool.bondPool.getPoolName());
        candidate.setPoolType(pool.bondPool.getPoolType());
        candidate.setAuditStatus(AuditStatus.APPROVED.getCode());
        candidate.setAdjusterId(AUTO_ADJUSTER_ID);
        candidate.setAdjusterName(AUTO_ADJUSTER_NAME);
        candidate.setAdjustReason(ScheduledAdjustLogHelper.appendDetails(
                ScheduledAdjustLogHelper.taskLabel(DEFAULT_TASK_NAME) + reasonPrefix,
                ScheduledAdjustLogHelper.issuerDetail(candidate),
                ScheduledAdjustLogHelper.poolDetail("主体所在池", pool.companyPool, pool.companyPoolId),
                trigger == null ? null : "联动触发主体：" + trigger));
        candidate.setAdjustBatchNo(context.batchNoContext.resolveBondBatchNo(
                abs.getSecurityCode(), pool.bondPoolId, adjustMode));
        candidate.setSubmitTime(context.batchNoContext.getSubmitTime());
        return candidate;
    }

    /**
     * 装载对账快照，四条查询各扫一次表
     *
     * 四条查询之间没有关联条件，读回后在内存里按代码和池分组，
     * 后续所有差集计算都只查内存
     *
     * 执行顺序：
     * 1. 读取 ABS 主档并按代码升序整理成列表与索引
     * 2. 读取四类关联主体，只保留主档中存在的 ABS
     * 3. 读取各池生效主体，按池分组
     * 4. 读取各池生效 ABS，按池分组
     *
     * @param detail 任务过程日志
     * @return 对账快照
     */
    private AbsSnapshot loadSnapshot(TaskDetailLog detail) {
        AbsSnapshot snapshot = new AbsSnapshot();

        // 1. ABS 主档，排序保证每轮处理顺序稳定
        List<AbsBondCandidateDto> absList = absPoolReconcileMapper.queryAbsBondList();
        if (absList != null) {
            absList.sort((left, right) -> {
                String leftCode = left.getSecurityCode() == null ? "" : left.getSecurityCode();
                String rightCode = right.getSecurityCode() == null ? "" : right.getSecurityCode();
                return leftCode.compareTo(rightCode);
            });
            for (AbsBondCandidateDto abs : absList) {
                if (abs != null && StringUtils.hasText(abs.getSecurityCode())) {
                    snapshot.absList.add(abs);
                    snapshot.absMap.put(abs.getSecurityCode(), abs);
                }
            }
        }

        // 2. 四类关联主体，主档没有的 ABS 直接丢掉
        List<AbsCompanyRelationDto> relationRows = absPoolReconcileMapper.queryAbsCompanyRelationList();
        if (relationRows != null) {
            for (AbsCompanyRelationDto row : relationRows) {
                if (row == null || !StringUtils.hasText(row.getAbsCode())
                        || !StringUtils.hasText(row.getCompanyCode())) {
                    continue;
                }
                if (!snapshot.absMap.containsKey(row.getAbsCode())) {
                    continue;
                }
                snapshot.relationMap.computeIfAbsent(row.getAbsCode(), key -> new TreeSet<>())
                        .add(row.getCompanyCode());
            }
        }

        // 3. 各池已生效主体
        fillPoolMap(snapshot.companyPoolMap, absPoolReconcileMapper.queryEffectiveCompanyPoolRows());

        // 4. 各池已生效 ABS
        fillPoolMap(snapshot.absPoolMap, absPoolReconcileMapper.queryEffectiveAbsPoolRows());

        infoDetail(detail, "快照装载完成：ABS 主档 " + snapshot.absList.size()
                + " 条，四类关系覆盖 " + snapshot.relationMap.size() + " 只 ABS，"
                + "主体生效池 " + snapshot.companyPoolMap.size() + " 个，ABS 生效池 " + snapshot.absPoolMap.size() + " 个");
        return snapshot;
    }

    /**
     * 把「池 → 证券代码」的查询结果按池分组
     *
     * 执行顺序：
     * 1. 跳过池 ID 或证券代码为空的脏行
     * 2. 按池累积证券代码
     *
     * @param target 目标分组容器
     * @param rows 查询结果
     */
    private void fillPoolMap(Map<Long, Set<String>> target, List<PoolSecurityRowDto> rows) {
        if (rows == null) {
            return;
        }
        for (PoolSecurityRowDto row : rows) {
            if (row == null || row.getPoolId() == null || !StringUtils.hasText(row.getSecurityCode())) {
                continue;
            }
            target.computeIfAbsent(row.getPoolId(), key -> new HashSet<>()).add(row.getSecurityCode());
        }
    }

    /**
     * 计算扫描池并生成映射组
     *
     * 执行顺序：
     * 1. 合并四个来源得到扫描池
     * 2. 生成同池映射与显式跨池映射
     *
     * @param params 扩展参数
     * @param snapshot 对账快照
     * @param detail 任务过程日志
     * @return 债券池与主体依据池的映射组
     */
    private List<long[]> resolvePairs(AbsSyncParams params, AbsSnapshot snapshot, TaskDetailLog detail) {
        // 1. 合并参数、关系绑定、已有生效主体、已有 ABS 在池四类池
        Set<Long> scanPoolIds = resolveScanPoolIds(params, snapshot, detail);
        // 2. 生成同池与跨池映射组
        return buildPairs(scanPoolIds, params.mappings);
    }

    /**
     * 合并四个来源得到本轮扫描池
     *
     * 第四项「已有 ABS 在池的池」专门为出池方向准备，否则池内还有 ABS、
     * 但池内已无生效主体的池永远扫不到
     *
     * 执行顺序：
     * 1. 并入扩展参数指定的池
     * 2. 并入关系配置绑定本任务的池，入池与出池两个方向都要
     * 3. 并入当前已有生效主体的普通池
     * 4. 并入当前已有 ABS 在池的池，并集为空时本轮失败
     *
     * @param params 扩展参数
     * @param snapshot 对账快照
     * @param detail 任务过程日志
     * @return 去重后的扫描池
     */
    private Set<Long> resolveScanPoolIds(AbsSyncParams params, AbsSnapshot snapshot, TaskDetailLog detail) {
        Set<Long> scanPoolIds = new LinkedHashSet<>();
        scanPoolIds.addAll(params.poolIds);
        addAll(scanPoolIds, poolScopeHelper.queryBoundPoolIds(TASK_CODE, RuleType.AUTO_IN.getCode()));
        addAll(scanPoolIds, poolScopeHelper.queryBoundPoolIds(TASK_CODE, RuleType.AUTO_OUT.getCode()));
        addAll(scanPoolIds, autoAdjustMapper.queryCompanyCurrentPoolIds());
        addAll(scanPoolIds, snapshot.absPoolMap.keySet());
        if (scanPoolIds.isEmpty()) {
            throw new BizException(AutoAdjustPoolScopeHelper.EMPTY_POOL_SCOPE_MESSAGE);
        }
        infoDetail(detail, "扫描池（参数、关系绑定、已有生效主体、已有 ABS 在池）=" + new ArrayList<>(scanPoolIds));
        return scanPoolIds;
    }

    /**
     * 生成「债券池 ← 主体依据池」映射组
     *
     * 默认同池对账；显式配置的跨池映射覆盖同债券池的同池口径，
     * 避免同一个债券池既按自身又按别的池重复处理
     *
     * 执行顺序：
     * 1. 记录显式跨池映射涉及的债券池
     * 2. 未被跨池映射覆盖的池生成同池映射
     * 3. 追加显式跨池映射并去重
     *
     * @param scanPoolIds 扫描池
     * @param mappings 显式跨池映射
     * @return 债券池与主体依据池的映射组
     */
    private List<long[]> buildPairs(Set<Long> scanPoolIds, List<long[]> mappings) {
        Set<Long> mappingBondPoolIds = new HashSet<>();
        for (long[] mapping : mappings) {
            mappingBondPoolIds.add(mapping[0]);
        }
        List<long[]> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Long poolId : scanPoolIds) {
            if (mappingBondPoolIds.contains(poolId)) {
                continue;
            }
            if (seen.add(poolId + "-" + poolId)) {
                pairs.add(new long[]{poolId, poolId});
            }
        }
        for (long[] mapping : mappings) {
            if (seen.add(mapping[0] + "-" + mapping[1])) {
                pairs.add(mapping);
            }
        }
        return pairs;
    }

    /**
     * 加载全部投资池，供池存在性校验和跨池调出使用
     *
     * @return 池 ID 到池对象的映射
     */
    private Map<Long, InvestmentPoolBo> buildPoolMap() {
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> poolList = investmentPoolMapper.queryPoolList();
        if (poolList != null) {
            for (InvestmentPoolBo pool : poolList) {
                poolMap.put(pool.getId(), pool);
            }
        }
        return poolMap;
    }

    /**
     * 读取任务名与扩展参数，配置表只查一次
     *
     * 任务名缺失时使用默认任务名称，保证过程日志仍然可读
     *
     * @return 任务配置
     */
    private TaskConfig loadTaskConfig() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        String taskName = conf != null && StringUtils.hasText(conf.getTaskName())
                ? conf.getTaskName() : DEFAULT_TASK_NAME;
        String paramJson = conf != null && StringUtils.hasText(conf.getParamJson())
                ? conf.getParamJson().trim() : null;
        return new TaskConfig(taskName, paramJson);
    }

    /**
     * 校验入池与出池开关，两者都关闭时本轮没有可做的事
     *
     * @param params 扩展参数
     */
    private void checkSwitches(AbsSyncParams params) {
        if (!params.enableIn && !params.enableOut) {
            throw new BizException("入池与出池开关同时关闭，本任务无事可做，请至少开启一个");
        }
    }

    /**
     * 解析扩展参数，全部字段都可省略
     *
     * 参数格式错误时直接失败，不做静默兜底，避免配置写错却以为已经生效
     *
     * 执行顺序：
     * 1. 空配置返回默认值，非 JSON 对象直接报错
     * 2. 读取指定扫描池
     * 3. 读取跨池映射
     * 4. 读取两个开关与到期保护天数
     *
     * @param raw 扩展参数原文
     * @return 解析后的参数
     */
    AbsSyncParams parseParams(String raw) {
        AbsSyncParams params = new AbsSyncParams();
        if (!StringUtils.hasText(raw)) {
            return params;
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            throw new BizException("扩展参数须为 JSON 对象，示例 {\"enableIn\":true}，当前: " + text);
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage());
        }
        // 1. 读取指定扫描池
        readPoolIds(root, params);
        // 2. 读取跨池映射
        readMappings(root, params);
        params.enableIn = readBoolean(root, "enableIn", false);
        params.enableOut = readBoolean(root, "enableOut", true);
        Integer minDays = readInt(root, "minDaysToMaturity");
        if (minDays != null) {
            if (minDays < 0) {
                throw new BizException("minDaysToMaturity 须为非负整数，当前: " + minDays);
            }
            params.minDaysToMaturity = minDays;
        }
        return params;
    }

    /**
     * 读取 poolIds，必须是数字数组
     *
     * @param root 参数根节点
     * @param params 解析结果
     */
    private void readPoolIds(JsonNode root, AbsSyncParams params) {
        JsonNode node = root.get("poolIds");
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw new BizException("poolIds 须为数字数组，示例 {\"poolIds\":[15]}");
        }
        for (JsonNode item : node) {
            if (item == null || !item.isNumber()) {
                throw new BizException("poolIds 元素须为数字，非法值: " + item);
            }
            params.poolIds.add(item.asLong());
        }
    }

    /**
     * 读取 mappings，每项必须同时包含债券池与主体依据池
     *
     * @param root 参数根节点
     * @param params 解析结果
     */
    private void readMappings(JsonNode root, AbsSyncParams params) {
        JsonNode node = root.get("mappings");
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw new BizException("mappings 须为对象数组，示例 {\"mappings\":[{\"bondPoolId\":16,\"companyPoolId\":15}]}");
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            JsonNode bondNode = item.get("bondPoolId");
            JsonNode companyNode = item.get("companyPoolId");
            if (bondNode == null || !bondNode.isNumber() || companyNode == null || !companyNode.isNumber()) {
                throw new BizException("mappings 项须含 bondPoolId、companyPoolId 数字，非法项: " + item);
            }
            params.mappings.add(new long[]{bondNode.asLong(), companyNode.asLong()});
        }
    }

    /**
     * 读取布尔开关，缺失时使用默认值
     *
     * @param root 参数根节点
     * @param field 字段名
     * @param defaultValue 默认值
     * @return 开关值
     */
    private boolean readBoolean(JsonNode root, String field, boolean defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isBoolean()) {
            throw new BizException(field + " 须为布尔值 true/false，非法值: " + node);
        }
        return node.asBoolean();
    }

    /**
     * 读取整数参数，缺失时返回 null 由调用方决定默认值
     *
     * @param root 参数根节点
     * @param field 字段名
     * @return 整数值，未配置时返回 null
     */
    private Integer readInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new BizException(field + " 须为数字，非法值: " + node);
        }
        return node.asInt();
    }

    /**
     * 把跨池映射拼成「主体池→债券池」的日志文案
     *
     * @param mappings 跨池映射
     * @return 可直接打日志的文本
     */
    private String joinMappings(List<long[]> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return "[]";
        }
        StringBuilder text = new StringBuilder("[");
        for (int index = 0; index < mappings.size(); index++) {
            if (index > 0) {
                text.append(",");
            }
            text.append(mappings.get(index)[1]).append("→").append(mappings.get(index)[0]);
        }
        return text.append("]").toString();
    }

    /**
     * 把池 ID 批量并入扫描池，跳过空值
     *
     * @param target 扫描池
     * @param source 待并入的池 ID
     */
    private void addAll(Set<Long> target, Collection<Long> source) {
        if (source == null) {
            return;
        }
        for (Long id : source) {
            if (id != null) {
                target.add(id);
            }
        }
    }

    /**
     * 记录一条 INFO 过程日志，同时写应用日志
     *
     * @param detail 任务过程日志
     * @param line 日志内容
     */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line("INFO", line);
        }
    }

    /**
     * 记录一条 WARN 过程日志，同时写应用日志
     *
     * @param detail 任务过程日志
     * @param line 日志内容
     */
    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line("WARN", line);
        }
    }

    /** 任务配置：任务名与扩展参数原文 */
    private static final class TaskConfig {

        /** 任务名称，配置缺失时为任务编码 */
        private final String taskName;

        /** 扩展参数原文，未配置时为 null */
        private final String paramJson;

        private TaskConfig(String taskName, String paramJson) {
            this.taskName = taskName;
            this.paramJson = paramJson;
        }
    }

    /** 扩展参数 */
    static final class AbsSyncParams {

        /** 指定扫描池 */
        final List<Long> poolIds = new ArrayList<>();

        /** 跨池映射：[债券池, 主体依据池] */
        final List<long[]> mappings = new ArrayList<>();

        /** 入池开关 */
        boolean enableIn = false;

        /** 出池开关 */
        boolean enableOut = true;

        /** 距到期最小剩余天数，不足该天数不再补入 */
        int minDaysToMaturity = DEFAULT_MIN_DAYS_TO_MATURITY;
    }

    /** 本轮跨池共享的上下文与缓存 */
    private static final class AbsSyncContext {

        /** 全部投资池 */
        private final Map<Long, InvestmentPoolBo> poolMap;

        /** 对账快照 */
        private final AbsSnapshot snapshot;

        /** 池关系 */
        private final List<PoolRelationBo> allRelations;

        /** 扩展参数 */
        private final AbsSyncParams params;

        /** 证券当前市场缓存，同一只 ABS 只查一次 */
        private final Map<String, String> marketCache = new HashMap<>();

        /** 当天日期，用于到期判断 */
        private final String today;

        /** 自动调库批次号上下文，整轮共用一个提交时间 */
        private final ScheduledAdjustBatchNoContext batchNoContext = new ScheduledAdjustBatchNoContext();

        private AbsSyncContext(Map<Long, InvestmentPoolBo> poolMap, AbsSnapshot snapshot,
                               List<PoolRelationBo> allRelations, AbsSyncParams params, String today) {
            this.poolMap = poolMap;
            this.snapshot = snapshot;
            this.allRelations = allRelations;
            this.params = params;
            this.today = today;
        }
    }

    /** 对账快照，四条批量查询在内存中的索引 */
    private static final class AbsSnapshot {

        /** ABS 主档，按代码升序 */
        private final List<AbsBondCandidateDto> absList = new ArrayList<>();

        /** ABS 代码到主档 */
        private final Map<String, AbsBondCandidateDto> absMap = new HashMap<>();

        /** ABS 代码到四类关联主体 */
        private final Map<String, Set<String>> relationMap = new HashMap<>();

        /** 池到生效主体 */
        private final Map<Long, Set<String>> companyPoolMap = new HashMap<>();

        /** 池到生效 ABS */
        private final Map<Long, Set<String>> absPoolMap = new HashMap<>();

        /** 取某池的生效主体，池内没有时返回空集合 */
        private Set<String> companiesOf(Long poolId) {
            return companyPoolMap.getOrDefault(poolId, Collections.emptySet());
        }

        /** 取某池的生效 ABS，池内没有时返回空集合 */
        private Set<String> absOf(Long poolId) {
            return absPoolMap.getOrDefault(poolId, Collections.emptySet());
        }
    }

    /** 单池上下文 */
    private static final class PoolContext {

        /** 债券目标池 */
        private final InvestmentPoolBo bondPool;

        /** 债券目标池 ID */
        private final Long bondPoolId;

        /** 主体依据池 ID */
        private final Long companyPoolId;

        /** 主体依据池 */
        private final InvestmentPoolBo companyPool;

        /** 依据池内的生效主体 */
        private final Set<String> companySet;

        /** 目标池内已生效的 ABS */
        private final Set<String> absInPool;

        private PoolContext(InvestmentPoolBo bondPool, Long companyPoolId, AbsSyncContext context) {
            this.bondPool = bondPool;
            this.bondPoolId = bondPool.getId();
            this.companyPoolId = companyPoolId;
            this.companyPool = context.poolMap.get(companyPoolId);
            this.companySet = context.snapshot.companiesOf(companyPoolId);
            this.absInPool = context.snapshot.absOf(bondPool.getId());
        }
    }

    /** 单池处理结果 */
    private static final class PoolResult {

        /** 实际入池数 */
        private int inCount;

        /** 实际出池数 */
        private int outCount;

        /** 规则提醒数 */
        private int reminderCount;
    }

    /** 本轮对账汇总 */
    private static final class AbsSyncSummary {

        /** 入池总数 */
        private int inTotal;

        /** 出池总数 */
        private int outTotal;

        /** 提醒总数 */
        private int reminderTotal;

        /** 失败池数量 */
        private int failedPoolCount;

        /** 按池统计：入池与出池数量 */
        private final Map<Long, int[]> poolCounts = new LinkedHashMap<>();

        /** 池 ID 到池名称 */
        private final Map<Long, String> poolNames = new LinkedHashMap<>();

        /** 合并单池结果 */
        private void merge(Long poolId, String poolName, PoolResult result) {
            poolNames.put(poolId, poolName);
            poolCounts.put(poolId, new int[]{result.inCount, result.outCount});
            inTotal += result.inCount;
            outTotal += result.outCount;
            reminderTotal += result.reminderCount;
        }

        /** 记录一个已回滚的失败池 */
        private void addFailedPool(Long poolId, String poolName) {
            poolNames.put(poolId, poolName);
            failedPoolCount++;
        }

        /** 本轮实际处理条数，入池与出池之和 */
        private int total() {
            return inTotal + outTotal;
        }

        /** 构建本轮结果文案 */
        private String buildMessage() {
            StringBuilder message = new StringBuilder("本轮自动入池 ")
                    .append(inTotal).append(" 条 ABS、自动出池 ").append(outTotal)
                    .append(" 条 ABS；规则提醒 ").append(reminderTotal).append(" 条");
            if (failedPoolCount > 0) {
                message.append("；失败已回滚池 ").append(failedPoolCount).append(" 个");
            }
            if (!poolCounts.isEmpty()) {
                message.append("；目标池明细：");
                int index = 0;
                for (Map.Entry<Long, int[]> entry : poolCounts.entrySet()) {
                    if (index++ > 0) {
                        message.append("；");
                    }
                    message.append(poolNames.get(entry.getKey())).append("(").append(entry.getKey()).append(")：入 ")
                            .append(entry.getValue()[0]).append(" 条、出 ")
                            .append(entry.getValue()[1]).append(" 条");
                }
            }
            return message.toString();
        }
    }
}
