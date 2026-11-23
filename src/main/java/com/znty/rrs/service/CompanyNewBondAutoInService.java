package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AdjustType;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.PoolStatus;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Isolation;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在池主体旗下债券自动入池（已排除ABS债）（仅债券）
 *
 * 执行顺序：
 * 1. 合并扩展参数、关系配置和已有主体所在池的映射
 * 2. 查询尚未在目标池生效且符合到期日和证券类型要求的旗下债券
 * 3. 共用手工批量规则校验候选债券及配套出入池
 * 4. 整组通过后由系统直接写入生效的出入池记录
 * 5. 返回映射、实际入池和未处理数量
 */
@Slf4j
@Service
public class CompanyNewBondAutoInService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "company_inpool_bond_auto_in";

    /** 旧同池任务配置继续并入补债范围 */
    private static final String LEGACY_TASK_CODE = "company_same_pool_bond_auto_in";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 任务默认展示名称（名称即口径提示：ABS 债已整体移出本任务） */
    private static final String DEFAULT_TASK_NAME = "在池主体旗下债券自动入池（已排除ABS债）（仅债券）";
    /** 解析 param_json 用 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 本任务扩展参数说明（配置页按行拆成列表展示，勿写 1) 2) 序号）
     */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[15],\"minDaysToMaturity\":0}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单个同池）：<code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：主体在 15（债券禁止库）时，旗下符合条件的债券自动调入 15（债券禁止库）\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多个同池）：<code>{\"poolIds\":[15,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：主体在 15（债券禁止库）或 16（观察池）时，旗下符合条件的债券自动调入主体所在的同一池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "单个写法（单个同池）：<code>{\"poolId\":15}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：与 <code>{\"poolIds\":[15]}</code> 相同，主体在 15（债券禁止库）时，旗下符合条件的债券自动调入 15（债券禁止库）\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "跨池映射写法：<code>{\"mappings\":[{\"companyInPoolId\":15,\"bondTargetPoolId\":16}]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：主体在 15（债券禁止库）时，旗下符合条件的债券自动调入 16（观察池）\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "组合写法：<code>{\"poolIds\":[15],\"mappings\":[{\"companyInPoolId\":16,\"bondTargetPoolId\":15}]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：同时执行两组映射：15（债券禁止库）主体的债券入 15（债券禁止库）；16（观察池）主体的债券入 15（债券禁止库）\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体所在池 + 债券入池目标池）：可选；主体在这些池内时，旗下债券自动入同一个池；poolIds 可填写多个数字 ID\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolId（主体所在池 + 债券入池目标池）：可选；与 poolIds 作用相同，但仅配置一个池 ID\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "mappings（跨池入池映射）：可选；用于主体所在池与债券入池目标池不一致的场景，可配置多组\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "companyInPoolId（主体所在池）：主体必须已在该池内，才扫描其旗下债券\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "bondTargetPoolId（债券入池目标池）：上述主体旗下符合条件的债券将自动写入该池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "minDaysToMaturity：距到期最少天数，须为非负整数；省略或填 0 时到期当天可入，填 1 时到期当天不入\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "关系配置：在投资池「自动调入规则」中绑定本任务的池，会按同池映射并入扫描范围\n"
                    + "扫描范围：所有已有生效主体的普通池自动追加同池映射，再与参数及关系绑定池取并集；无映射时本轮无调整\n"
                    + "处理规则：主体已在主体池且生效时，将其旗下未退市、未到期且未在债券目标池的债券自动入池\n"
                    + "类型范围：普通债、CRMW；排除退市债、已更新正式代码的临时代码以及全部 ABS\n"
                    + "ABS 说明：ABS 的进出池与未确认发行人的待确认提醒由「"
                    + CompanyAbsBondAutoInService.DEFAULT_TASK_NAME + "」处理\n"
                    + "CRMW 说明：CRMW 证券跟随主体进入普通目标池时写 ip_pool_status，不写 CRMW 组合状态表\n"
                    + "准入规则：与手工批量校验共用禁止库、调入限制、市场、容量、评级及其他单券规则\n"
                    + "Excel 主体调入生效后优先补债，保留池关系和在途校验\n"
                    + "配套调整：联动调入、互斥及反向限制池调出与主券同组，整组通过后系统自动调整并直接生效\n"
                    + "参数格式错误时，本轮任务失败";

    /** 自动调库查询 Mapper */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 本轮应排除的 Excel 主体及证券记录 */
    @Resource
    private ExcelPoolExclusionService excelPoolExclusionService;
    /** 证券池调库落地 Mapper */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 证券池页面和定时任务共用的校验服务 */
    @Resource
    private BatchSecurityCheckService batchSecurityCheckService;

    /** 投资池查询 Mapper */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 定时任务配置 Mapper（param_json / 名称） */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;
    /** 扫描池并集（参数 ∪ 关系配置） */
    @Resource
    private AutoAdjustPoolScopeHelper poolScopeHelper;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /**
     * 向配置页提供本任务扩展参数填写说明（仅 JSON）
     */
    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 执行在池主体旗下债券自动入池（已排除ABS债）
     *
     * 执行顺序：
     * 1. 读取本轮任务名称和扩展参数
     * 2. 记录任务开始
     * 3. 按主体池到债券池的映射执行补债
     * 4. 返回执行结果，失败时标记本轮事务回滚
     *
     * @return 本轮任务执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        String taskName = DEFAULT_TASK_NAME;
        try {
            // 1. 同一轮共用任务配置中的名称与扩展参数
            SysScheduledTaskBo config = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
            if (config != null && StringUtils.hasText(config.getTaskName())) {
                taskName = ScheduledAdjustLogHelper.withTaskScope(TASK_CODE, config.getTaskName());
            }
            // 2. 记录任务开始
            infoDetail(detail, "任务开始：" + taskName);
            // 3. 执行在池主体旗下债券自动入池
            AutoInSummary summary = doAutoInCompanyNewBond(taskName, config == null ? null : config.getParamJson(), detail);
            long duration = System.currentTimeMillis() - begin;
            String message = summary.buildMessage();
            // 4. 记录任务结果并返回成功结果
            infoDetail(detail, "任务结束：" + message + "；耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, summary.total, startTime, duration,
                    detail.build());
        } catch (BizException e) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            long duration = System.currentTimeMillis() - begin;
            warnDetail(detail, "任务未完成：" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName, e.getMessage(), startTime, duration,
                    detail.build());
        } catch (Exception e) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", taskName, e);
            detail.line("任务未完成：系统异常，" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName,
                    "执行异常: " + e.getMessage(), startTime, duration, detail.build());
        }
    }

    /**
     * 按主体所在池到债券写入池的映射处理待入池债券
     *
     * 执行顺序：
     * 1. 合并补债参数、任务绑定和已有主体池映射
     * 2. 读取投资池、池关系和本轮批次信息
     * 3. 校验每组映射的目标池并查询候选债券
     * 4. 将各映射的候选合并后，按共用规则批量校验
     * 5. 记录失败组的券码、池和具体原因
     * 6. 同一事务保存通过组的出入池明细并汇总结果
     *
     * @param taskName 任务展示名称
     * @param paramJson 已读取的扩展参数
     * @param detail 过程日志
     * @return 本轮入池汇总
     */
    private AutoInSummary doAutoInCompanyNewBond(String taskName, String paramJson, TaskDetailLog detail) {
        // 记录原始扩展参数，便于核对本轮使用的配置
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        // 1. 合并补债参数、任务绑定和已有主体池映射
        // 逐轮读取并校验入池到期门槛
        JsonNode params = parseParamObject(paramJson, TASK_CODE);
        JsonNode minDays = params.get("minDaysToMaturity");
        int minDaysToMaturity = 0;
        if (minDays != null && !minDays.isNull()) {
            if (!minDays.isIntegralNumber() || !minDays.canConvertToInt() || minDays.intValue() < 0) {
                throw new BizException("minDaysToMaturity 须为非负整数，当前: " + minDays);
            }
            minDaysToMaturity = minDays.intValue();
        }
        // 合并扩展参数、绑定池和已有主体池映射
        List<long[]> pairList = resolveInboundMappings(params, detail);
        if (pairList.isEmpty()) {
            return new AutoInSummary();
        }
        infoDetail(detail, "扫描范围：有效主体池到债券目标池的映射 " + pairList.size() + " 组");
        infoDetail(detail, "扫描条件：普通债或 CRMW，未退市、排除 ABS，且尚未在债券目标池"
                + "；普通主体依据要求距到期至少 " + minDaysToMaturity + " 天，Excel 主体依据跳过该门槛");
        // 2. 读取投资池、池关系和本轮批次信息
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        ScheduledAdjustBatchNoContext batchNoContext = new ScheduledAdjustBatchNoContext();
        AutoInSummary summary = new AutoInSummary();
        List<InboundMapping> mappings = new ArrayList<>();
        List<AdjustCheckReq> requests = new ArrayList<>();
        Set<AdjustCheckReq> excelCompanyRequests = Collections.newSetFromMap(new IdentityHashMap<>());
        List<InboundCandidate> candidates = new ArrayList<>();
        Set<Long> decisionPoolIds = new HashSet<>();
        for (long[] pair : pairList) {
            decisionPoolIds.add(pair[0]);
            decisionPoolIds.add(pair[1]);
        }
        ExcelPoolExclusionService.CompanyDecisions decisions = excelPoolExclusionService.loadCompanyDecisions(decisionPoolIds, null);
        String maturityCutoff = autoAdjustMapper.queryDatabaseDate().plusDays(minDaysToMaturity)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        // 3. 逐组验证目标池并查询旗下债券候选
        for (long[] pair : pairList) {
            Long companyInPoolId = pair[0];
            Long bondTargetPoolId = pair[1];
            InvestmentPoolBo companyPool = poolMap.get(companyInPoolId);
            InvestmentPoolBo targetPool = poolMap.get(bondTargetPoolId);
            if (targetPool == null || !PoolStatus.ENABLED.getCode().equals(targetPool.getStatus())) {
                summary.addSkipped(1);
                skipDetail(detail, "债券目标池【" + bondTargetPoolId + "】不存在或未启用，映射 "
                        + companyInPoolId + "→" + bondTargetPoolId + " 本轮未处理");
                continue;
            }
            if (CompanyBondSyncPolicy.isCrmwCombinationPool(targetPool)) {
                summary.addSkipped(1);
                skipDetail(detail, "债券目标池【" + bondTargetPoolId
                        + "】是 CRMW 组合池，不能通过普通池状态写入，本轮未处理");
                continue;
            }
            List<ScheduledAdjustCandidateDto> bondList = autoAdjustMapper.queryCompanyNewBondForAutoIn(
                    companyInPoolId, bondTargetPoolId, CompanyBondSyncPolicy.newBondInboundScope());
            bondList = decisions.filterInbound(bondList, companyInPoolId, bondTargetPoolId, maturityCutoff);
            if (bondList.isEmpty()) {
                summary.addMapping(companyInPoolId, companyPool == null ? null : companyPool.getPoolName(),
                        bondTargetPoolId, targetPool.getPoolName(), 0, 0, 0);
                infoDetail(detail, "主体所在池[" + companyInPoolId + "]→债券写入池["
                        + targetPool.getPoolName() + "](" + bondTargetPoolId + ") 无待入池新债");
                continue;
            }
            InboundMapping mapping = new InboundMapping(companyInPoolId, companyPool, targetPool, bondList.size());
            mappings.add(mapping);
            for (ScheduledAdjustCandidateDto bond : bondList) {
                if (bond == null || !StringUtils.hasText(bond.getSecurityCode())) {
                    mapping.skippedCount++;
                    skipDetail(detail, "扫描结果缺少证券编码，无法处理");
                    continue;
                }
                candidates.add(new InboundCandidate(bond, mapping));
            }
        }

        // Excel 决定先校验、先落库，使普通候选看到其联动产生的池状态
        candidates.sort(Comparator.comparing(candidate -> !candidate.bond.isExcelCompanyAdjustment()));
        for (InboundCandidate candidate : candidates) {
            ScheduledAdjustCandidateDto bond = candidate.bond;
            InvestmentPoolBo targetPool = candidate.mapping.targetPool;
            AdjustCheckReq request = new AdjustCheckReq();
            request.setSecurityCode(bond.getSecurityCode());
            AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
            item.setTargetPoolId(targetPool.getId());
            item.setTargetPoolName(targetPool.getPoolName());
            item.setPoolType(targetPool.getPoolType());
            item.setAdjustMode(AdjustMode.IN.getCode());
            request.setItems(Collections.singletonList(item));
            requests.add(request);
            if (bond.isExcelCompanyAdjustment()) {
                excelCompanyRequests.add(request);
            }
        }

        // 4. 全部映射共用一批校验数据，与手工批量调入执行同一套规则
        List<AdjustCheckDto> results = requests.isEmpty() ? Collections.emptyList()
                : batchSecurityCheckService.checkCompanyFollowBatch(requests, maturityCutoff, excelCompanyRequests);
        Map<Long, Set<String>> appliedInbounds = new HashMap<>();
        int resultIndex = 0;
        for (InboundCandidate candidate : candidates) {
            InboundMapping mapping = candidate.mapping;
            ScheduledAdjustCandidateDto bond = candidate.bond;
            List<AdjustCheckDto.CheckResultItem> items = results.get(resultIndex++).getItems();
            if (items.isEmpty()) {
                // 已由本轮配套调整实际入池的候选无需重复处理
                if (!appliedInbounds.getOrDefault(mapping.targetPool.getId(), Collections.emptySet())
                        .contains(bond.getSecurityCode())) {
                    mapping.skippedCount++;
                    skipDetail(detail, "证券【" + bond.getSecurityCode() + "】池【" + mapping.targetPool.getPoolName()
                            + "】：无可执行的调入项，本轮未写入目标池");
                }
                continue;
            }
            // 5. 失败原因由任务按实际证券和池记录，整组失败则跳过该候选
            if (items.stream().anyMatch(item -> !item.isCanAdjust())) {
                mapping.skippedCount++;
                for (AdjustCheckDto.CheckResultItem item : items) {
                    if (!item.isCanAdjust()) {
                        skipDetail(detail, "证券【" + item.getSecurityCode() + "】池【"
                                + (item.getPoolName() == null ? item.getTargetPoolId() : item.getPoolName())
                                + "】：" + String.join("、", item.getFailReasons()));
                        item.getFailReasons().forEach(reason -> summary.failures.merge(reason, 1, Integer::sum));
                    }
                }
                continue;
            }
            for (AdjustCheckDto.CheckResultItem item : items) {
                if (item.getWarnings() != null) {
                    item.getWarnings().forEach(warning -> warnDetail(detail,
                            "证券【" + item.getSecurityCode() + "】池【" + item.getPoolName() + "】：" + warning));
                }
            }
            // 6. 同组联动调入、关联码和互斥调出共用一条业务批次
            mapping.inboundCount += applyInboundGroup(bond, items, mapping, taskName, batchNoContext, appliedInbounds);
        }
        for (InboundMapping mapping : mappings) {
            summary.addMapping(mapping.sourcePoolId, mapping.sourcePool == null ? null : mapping.sourcePool.getPoolName(),
                    mapping.targetPool.getId(), mapping.targetPool.getPoolName(), mapping.candidateCount,
                    mapping.inboundCount, mapping.skippedCount);
            infoDetail(detail, "处理结果：主体所在池【" + mapping.sourcePoolId + "】→债券池【"
                    + mapping.targetPool.getPoolName() + "】实际入池 " + mapping.inboundCount + " 条，未处理 "
                    + mapping.skippedCount + " 条");
        }
        return summary;
    }

    /**
     * 将通过校验的一组调入和配套调出写入同一事务
     *
     * @param bond 触发补债候选
     * @param items 已校验的调库项
     * @param mapping 触发本次入池的主体池至目标池映射
     * @param taskName 任务展示名称
     * @param batchContext 当前任务批次号上下文
     * @param appliedInbounds 按目标池记录本轮已实际写入且仍在池的证券
     * @return 实际新增的入池状态数量
     */
    private int applyInboundGroup(ScheduledAdjustCandidateDto bond, List<AdjustCheckDto.CheckResultItem> items,
                                  InboundMapping mapping, String taskName, ScheduledAdjustBatchNoContext batchContext,
                                  Map<Long, Set<String>> appliedInbounds) {
        String reason = ScheduledAdjustLogHelper.appendDetails(ScheduledAdjustLogHelper.taskLabel(taskName) + "【主体跟随】",
                ScheduledAdjustLogHelper.issuerDetail(bond), "依据池ID：" + mapping.sourcePoolId,
                ScheduledAdjustLogHelper.poolDetail("主体所在池", mapping.sourcePool, mapping.sourcePoolId));
        if (bond.isExcelCompanyAdjustment()) {
            reason = ScheduledAdjustLogHelper.appendDetails(reason, "【Excel主体跟随】");
        }
        if (bond.getTriggerCompanyCode() != null) {
            reason = ScheduledAdjustLogHelper.appendDetails(reason, "联动触发主体：" + bond.getTriggerCompanyCode());
        }
        String batchNo = batchContext.resolveBondBatchNo(bond.getSecurityCode(), mapping.targetPool.getId(),
                AdjustMode.IN.getCode());
        int count = 0;
        for (AdjustCheckDto.CheckResultItem item : items) {
            boolean inbound = AdjustMode.IN.getCode().equals(item.getAdjustMode());
            IpAdjustLogBo log = new IpAdjustLogBo();
            BeanUtils.copyProperties(bond, log, "id", "adjustLogId");
            log.setSecurityCode(item.getSecurityCode());
            log.setSecurityShortName(item.getSecurityShortName());
            log.setSecurityType(item.getSecurityType());
            log.setTargetPoolId(item.getTargetPoolId());
            log.setTargetPoolName(item.getPoolName());
            log.setPoolType(item.getPoolType());
            log.setAdjustType(inbound ? AdjustType.AUTO.getCode() : "互斥调整");
            log.setAdjustMode(item.getAdjustMode());
            log.setAuditStatus(AuditStatus.APPROVED.getCode());
            log.setAdjusterId(AUTO_ADJUSTER_ID);
            log.setAdjusterName(AUTO_ADJUSTER_NAME);
            log.setAdjustReason(inbound ? reason : ScheduledAdjustLogHelper.appendDetails(reason,
                    "池关系触发：调入" + mapping.targetPool.getPoolName() + "后自动调出" + item.getPoolName()));
            log.setAdjustBatchNo(batchNo);
            log.setSubmitTime(batchContext.getSubmitTime());
            if (securityPoolAdjustMapper.addAdjustLog(log) != 1) {
                throw new BizException("证券[" + item.getSecurityCode() + "]自动调库日志写入失败");
            }
            log.setAdjustLogId(log.getId());
            if (inbound) {
                if (securityPoolAdjustMapper.addPoolStatus(log) != 1) {
                    throw new BizException("证券[" + item.getSecurityCode() + "]自动调入池状态写入失败");
                }
                appliedInbounds.computeIfAbsent(item.getTargetPoolId(), id -> new HashSet<>()).add(item.getSecurityCode());
                count++;
            } else if (securityPoolAdjustMapper.deleteUnprotectedPoolStatusSoft(item.getSecurityCode(), item.getTargetPoolId()) == 0) {
                throw new BizException("证券[" + item.getSecurityCode() + "]配套调出时池状态已变化");
            } else {
                appliedInbounds.getOrDefault(item.getTargetPoolId(), Collections.emptySet()).remove(item.getSecurityCode());
            }
        }
        return count;
    }

    /** 本轮单个主体池至债券池映射的候选 */
    private static final class InboundMapping {
        /** 发行主体所在池 ID */
        private final Long sourcePoolId;
        /** 发行主体所在池 */
        private final InvestmentPoolBo sourcePool;
        /** 债券目标池 */
        private final InvestmentPoolBo targetPool;
        /** 候选数量 */
        private final int candidateCount;
        /** 实际入池数量 */
        private int inboundCount;
        /** 未处理数量 */
        private int skippedCount;

        private InboundMapping(Long sourcePoolId, InvestmentPoolBo sourcePool, InvestmentPoolBo targetPool,
                               int candidateCount) {
            this.sourcePoolId = sourcePoolId;
            this.sourcePool = sourcePool;
            this.targetPool = targetPool;
            this.candidateCount = candidateCount;
        }
    }

    /** 记录候选债券的触发映射，按 Excel 优先级执行后回填各池统计 */
    private static final class InboundCandidate {
        /** 待检查的旗下债券 */
        private final ScheduledAdjustCandidateDto bond;
        /** 当前债券的来源池和目标池 */
        private final InboundMapping mapping;

        private InboundCandidate(ScheduledAdjustCandidateDto bond, InboundMapping mapping) {
            this.bond = bond;
            this.mapping = mapping;
        }
    }

    /**
     * 读取补债任务的完整池映射，供跟随出池检查主体依据
     *
     * @param detail 过程日志
     * @return 去重后的主体池和债券目标池映射
     */
    List<long[]> resolveInboundMappings(TaskDetailLog detail) {
        SysScheduledTaskBo config = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        // 解析现有配置，出池依据只使用池映射
        JsonNode params = parseParamObject(config == null ? null : config.getParamJson(), TASK_CODE);
        // 使用已读的扩展参数合并同池和跨池映射
        return resolveInboundMappings(params, detail);
    }

    /**
     * 合并补债任务参数和当前生效的池范围
     *
     * 执行顺序：
     * 1. 解析本任务参数，加入已有生效主体的同池映射
     * 2. 合并旧同池任务的扩展参数和规则绑定
     * 3. 加入本任务的规则绑定并去重，无扫描范围时返回空列表
     *
     * @param params 已解析的补债任务扩展参数
     * @param detail 过程日志
     * @return 每项依次为主体池 ID 和债券目标池 ID
     */
    private List<long[]> resolveInboundMappings(JsonNode params, TaskDetailLog detail) {
        // 1. 解析任务参数并补入已有生效主体的同池映射
        List<long[]> mappings = parseParamMappings(params);
        for (Long poolId : autoAdjustMapper.queryCompanyCurrentPoolIds()) {
            mappings.add(new long[]{poolId, poolId});
        }
        // 2. 沿用旧同池任务中保存的参数及绑定池
        SysScheduledTaskBo legacy = scheduledTaskMapper.queryTaskByCode(LEGACY_TASK_CODE);
        if (legacy != null) {
            // 解析旧任务参数并合并其中的池映射
            JsonNode legacyParams = parseParamObject(legacy.getParamJson(), LEGACY_TASK_CODE);
            // 将旧任务参数转换成当前使用的主体池和债券池映射
            mappings.addAll(parseParamMappings(legacyParams));
        }
        for (Long poolId : poolScopeHelper.queryBoundPoolIds(LEGACY_TASK_CODE, RuleType.AUTO_IN.getCode())) {
            mappings.add(new long[]{poolId, poolId});
        }
        // 3. 有扫描范围时合并本任务的绑定池并去重
        if (mappings.isEmpty() && poolScopeHelper.queryBoundPoolIds(TASK_CODE, RuleType.AUTO_IN.getCode()).isEmpty()) {
            return mappings;
        }
        return poolScopeHelper.unionSamePoolMappings(mappings, TASK_CODE, RuleType.AUTO_IN.getCode(), detail);
    }

    /** 本轮在池主体旗下债券入池汇总 */
    private static final class AutoInSummary {

        /** 本轮实际入池总数 */
        private int total;
        /** 按跳过原因汇总失败项 */
        private final Map<String, Integer> failures = new LinkedHashMap<>();
        /** 本轮扫描到的债券候选数 */
        private int candidateCount;
        /** 本轮未处理的债券或映射数量 */
        private int skippedCount;
        /** 按主体所在池与债券目标池映射保存入池数量 */
        private final Map<String, MappingSummary> mappingSummaries = new LinkedHashMap<>();

        /** 累加未处理映射数量 */
        private void addSkipped(int count) {
            skippedCount += count;
        }

        /** 累加一个主体池与债券目标池映射的入池数量 */
        private void addMapping(Long companyPoolId, String companyPoolName, Long bondPoolId,
                                String bondPoolName, int candidateCount, int count, int skippedCount) {
            String key = companyPoolId + "-" + bondPoolId;
            mappingSummaries.put(key, new MappingSummary(companyPoolId, companyPoolName,
                    bondPoolId, bondPoolName, candidateCount, count, skippedCount));
            this.candidateCount += candidateCount;
            this.skippedCount += skippedCount;
            total += count;
        }

        /** 构建最近执行结果文案 */
        private String buildMessage() {
            StringBuilder message = new StringBuilder("任务完成：扫描候选债券 ")
                    .append(candidateCount).append(" 条，实际入池 ")
                    .append(total).append(" 条，未处理 ")
                    .append(skippedCount).append(" 条");
            if (!mappingSummaries.isEmpty()) {
                message.append("；目标池明细：");
                int index = 0;
                for (MappingSummary mapping : mappingSummaries.values()) {
                    if (index++ > 0) {
                        message.append("；");
                    }
                    message.append("主体池")
                            .append(mapping.companyPoolName == null ? "" : mapping.companyPoolName)
                            .append("（").append(mapping.companyPoolId).append("）→债券池")
                            .append(mapping.bondPoolName).append("（").append(mapping.bondPoolId)
                            .append("）：扫描 ").append(mapping.candidateCount).append(" 条，入池 ")
                            .append(mapping.count).append(" 条，未处理 ")
                            .append(mapping.skippedCount).append(" 条");
                }
            }
            if (!failures.isEmpty()) {
                message.append("；校验失败项：");
                failures.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(5).forEach(entry -> message.append("【").append(entry.getKey())
                                .append("】").append(entry.getValue()).append(" 项 "));
            }
            return message.toString();
        }
    }

    /** 单个主体池与债券目标池映射的入池统计 */
    private static final class MappingSummary {

        /** 主体所在池 ID */
        private final Long companyPoolId;
        /** 主体所在池名称 */
        private final String companyPoolName;
        /** 债券目标池 ID */
        private final Long bondPoolId;
        /** 债券目标池名称 */
        private final String bondPoolName;
        /** 扫描候选数量 */
        private final int candidateCount;
        /** 实际入池数量 */
        private final int count;
        /** 未处理数量 */
        private final int skippedCount;

        /** 创建映射统计 */
        private MappingSummary(Long companyPoolId, String companyPoolName, Long bondPoolId,
                               String bondPoolName, int candidateCount, int count, int skippedCount) {
            this.companyPoolId = companyPoolId;
            this.companyPoolName = companyPoolName;
            this.bondPoolId = bondPoolId;
            this.bondPoolName = bondPoolName;
            this.candidateCount = candidateCount;
            this.count = count;
            this.skippedCount = skippedCount;
        }
    }

    /**
     * 写 INFO 级过程日志并同步控制台
     */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line(line);
        }
    }

    /**
     * 写 WARN 级过程日志并同步控制台
     */
    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line(line);
        }
    }

    /**
     * 记录业务上未处理的债券或映射，不把可预期情况写成任务异常
     *
     * @param detail 过程日志
     * @param line 未处理原因
     */
    private void skipDetail(TaskDetailLog detail, String line) {
        log.info("定时任务未处理：{}", line);
        if (detail != null) {
            detail.line("未处理：" + line);
        }
    }

    /**
     * 构建投资池 ID 到池信息的映射，供校验债券写入池是否存在
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
     * 将扩展参数读取为 JSON 对象，参数留空时使用空对象
     *
     * @param raw 扩展参数原文
     * @param taskName 任务展示名称
     * @return 扩展参数对象
     */
    private JsonNode parseParamObject(String raw, String taskName) {
        if (!StringUtils.hasText(raw)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            throw new BizException("扩展参数须为 JSON 对象，示例 {\"poolIds\":[15]}，当前: " + text);
        }
        try {
            return OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            log.warn("{}：JSON 扩展参数解析失败: {}，原因: {}", taskName, text, e.getMessage());
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage()
                    + "；请使用标准 JSON，示例 {\"poolIds\":[15]}");
        }
    }

    /**
     * 读取 poolIds、poolId 和 mappings 中的主体池到债券池映射
     *
     * @param root 扩展参数对象
     * @return 每项为主体所在池 ID 和债券写入池 ID 的映射
     */
    private List<long[]> parseParamMappings(JsonNode root) {
        List<long[]> result = new ArrayList<>();
        // 1. 读取同池多目标池配置
        JsonNode poolIdsNode = root.get("poolIds");
        if (poolIdsNode != null && !poolIdsNode.isNull()) {
            if (!poolIdsNode.isArray()) {
                throw new BizException("poolIds 须为数字数组，示例 {\"poolIds\":[15,16]}");
            }
            for (JsonNode item : poolIdsNode) {
                Long id = readNumberNode(item);
                if (id == null) {
                    throw new BizException("poolIds 元素须为数字，非法值: " + item);
                }
                result.add(new long[]{id, id});
            }
        }
        // 2. 读取同池单目标池配置
        if (root.has("poolId") && !root.get("poolId").isNull()) {
            Long id = readNumberNode(root.get("poolId"));
            if (id == null) {
                throw new BizException("poolId 须为数字，示例 {\"poolId\":15}");
            }
            result.add(new long[]{id, id});
        }
        // 3. 读取显式跨池映射
        JsonNode mappings = root.get("mappings");
        if (mappings != null && mappings.isArray()) {
            for (JsonNode item : mappings) {
                if (item == null || item.isNull()) {
                    continue;
                }
                Long companyIn = readLong(item, "companyInPoolId");
                Long bondTarget = readLong(item, "bondTargetPoolId");
                if (companyIn == null || bondTarget == null) {
                    throw new BizException(
                            "mappings 项须含 companyInPoolId、bondTargetPoolId 数字，非法项: " + item);
                }
                result.add(new long[]{companyIn, bondTarget});
            }
        }
        return result;
    }

    /**
     * 读取 JSON 数字节点为 Long
     */
    private Long readNumberNode(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asLong();
    }

    /**
     * 单测入口：解析扩展参数
     */
    List<long[]> parseParamMappings(String raw) {
        // 从参数对象读取池映射
        return parseParamMappings(parseParamObject(raw, TASK_CODE));
    }

    /**
     * 从 JSON 节点读取 Long 字段
     */
    private Long readLong(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isNumber()) {
            return null;
        }
        return v.asLong();
    }
}
