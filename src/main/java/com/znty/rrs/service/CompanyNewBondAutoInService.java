package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AdjustType;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
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
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在池主体旗下债券自动入池（已排除ABS债）
 *
 * 执行顺序：
 * 1. 合并扩展参数、关系配置和已有主体所在池的映射
 * 2. 查询主体已在池且债券尚未在目标池的普通债和 CRMW
 * 3. 排除退市债、已更新正式代码的临时代码和 ABS
 * 4. 写入债券入池记录并处理互斥出池
 * 5. 返回映射、实际入池和未处理数量
 */
@Slf4j
@Service
public class CompanyNewBondAutoInService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "company_inpool_bond_auto_in";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 任务默认展示名称（名称即口径提示：ABS 债已整体移出本任务） */
    private static final String DEFAULT_TASK_NAME = "在池主体旗下债券自动入池（已排除ABS债）";
    /** 解析 param_json 用 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 本任务扩展参数说明（配置页按行拆成列表展示，勿写 1) 2) 序号）
     */
    private static final String PARAM_HELP =
            "参数格式：须填写 JSON 对象；poolIds、poolId、mappings 可组合，例如 <code>{\"poolIds\":[15]}</code>\n"
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
                    + PARAM_HELP_TOOLTIP_PREFIX + "关系配置：在投资池「自动调入规则」中绑定本任务的池，会按同池映射并入扫描范围\n"
                    + "扫描范围：所有已有生效主体的普通池自动追加同池映射，再与参数及关系绑定池取并集；并集为空时本轮失败\n"
                    + "处理规则：主体已在主体池且生效时，将其旗下未退市、未到期且未在债券目标池的债券自动入池\n"
                    + "类型范围：普通债、CRMW；排除退市债、已更新正式代码的临时代码以及全部 ABS\n"
                    + "ABS 说明：ABS 的进出池与未确认主体的待确认提醒由「主体下ABS债券自动入库（区分已确认发行人和未确认发行人）」统一处理，本任务不再生成 ABS 提醒\n"
                    + "CRMW 说明：CRMW 证券跟随主体进入普通目标池时写 ip_pool_status，不写 CRMW 组合状态表\n"
                    + "关系调出：入池成功后，按目标池调入互斥关系及反向调入限制关系自动调出债券原所在池并记录日志\n"
                    + "范围说明：不检查调入限制池，也不按目标池 market_codes 过滤；需要同池市场/限制校验时请使用“主体下债券自动入池”任务\n"
                    + "参数格式错误时，本轮任务失败";

    /** 自动调库查询 Mapper */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 证券池调库落地 Mapper */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

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
     * 1. 读取扩展参数并合并同池、跨池映射
     * 2. 逐个映射查询待入池债券
     * 3. 对不满足单条业务条件的债券记录原因后跳过
     * 4. 写入债券入池记录并处理关系池出池
     * 5. 返回任务成功或任务级失败结果
     *
     * @return 本轮任务执行结果
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        // 1. 读取任务展示名称
        String taskName = resolveTaskName();
        // 2. 记录任务开始
        infoDetail(detail, "任务开始：" + taskName);
        try {
            // 3. 执行在池主体旗下债券自动入池
            AutoInSummary summary = doAutoInCompanyNewBond(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = summary.buildMessage();
            // 4. 记录任务结果并返回成功结果
            infoDetail(detail, "任务结束：" + message + "；耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, summary.total, startTime, duration,
                    detail.build());
        } catch (BizException e) {
            long duration = System.currentTimeMillis() - begin;
            warnDetail(detail, "任务未完成：" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, taskName, e.getMessage(), startTime, duration,
                    detail.build());
        } catch (Exception e) {
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
     * 1. 解析扩展参数并补充已有主体所在池的同池映射
     * 2. 校验目标池并查询待入池债券
     * 3. 对不满足单条业务条件的债券记录原因后跳过
     * 4. 写入债券入池记录并处理关系池出池
     * 5. 汇总每组映射的处理结果
     *
     * @param taskName 任务展示名称
     * @param detail 过程日志
     * @return 本轮入池汇总
     */
    private AutoInSummary doAutoInCompanyNewBond(String taskName, TaskDetailLog detail) {
        // 1. 读取扩展参数原文
        String paramJson = resolveParamJson();
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        // 2. 解析主体池到债券池的映射
        List<long[]> mappings = new ArrayList<>(parseParamMappings(paramJson, taskName));
        // 3. 补充已有主体所在池的同池映射
        for (Long poolId : autoAdjustMapper.queryCompanyCurrentPoolIds()) {
            mappings.add(new long[]{poolId, poolId});
        }
        List<long[]> pairList = poolScopeHelper.unionSamePoolMappings(
                mappings, TASK_CODE, RuleType.AUTO_IN.getCode(), detail);
        infoDetail(detail, "扫描范围：有效主体池到债券目标池的映射 " + pairList.size() + " 组");
        infoDetail(detail, "处理条件：普通债或 CRMW，未退市、未到期、排除 ABS，且尚未在债券目标池");
        // 4. 构建池信息映射并读取关系配置
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        ScheduledAdjustBatchNoContext batchNoContext = new ScheduledAdjustBatchNoContext();
        Date submitTime = batchNoContext.getSubmitTime();
        AutoInSummary summary = new AutoInSummary();
        // 5. 逐个映射查询和处理债券
        for (long[] pair : pairList) {
            Long companyInPoolId = pair[0];
            Long bondTargetPoolId = pair[1];
            InvestmentPoolBo companyPool = poolMap.get(companyInPoolId);
            InvestmentPoolBo targetPool = poolMap.get(bondTargetPoolId);
            if (targetPool == null) {
                summary.addSkipped(1);
                skipDetail(detail, "债券目标池【" + bondTargetPoolId + "】不存在，映射 "
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
            if (bondList == null || bondList.isEmpty()) {
                summary.addMapping(companyInPoolId, companyPool == null ? null : companyPool.getPoolName(),
                        bondTargetPoolId, targetPool.getPoolName(), 0, 0, 0);
                infoDetail(detail, "主体所在池[" + companyInPoolId + "]→债券写入池["
                        + targetPool.getPoolName() + "](" + bondTargetPoolId + ") 无待入池新债");
                continue;
            }
            int pairCount = 0;
            int pairSkippedCount = 0;
            for (ScheduledAdjustCandidateDto bond : bondList) {
                if (bond == null || !StringUtils.hasText(bond.getSecurityCode())) {
                    pairSkippedCount++;
                    skipDetail(detail, "映射 " + companyInPoolId + "→" + bondTargetPoolId
                            + " 的扫描结果缺少证券编码，无法处理");
                    continue;
                }
                // 5.1 读取债券当前所在池
                List<Long> currentPoolIds = securityPoolAdjustMapper
                        .querySecurityCurrentPoolIdList(bond.getSecurityCode());
                // 5.2 准备债券入池日志字段
                bond.setAdjustType(AdjustType.AUTO.getCode());
                bond.setAdjustMode(AdjustMode.IN.getCode());
                bond.setTargetPoolId(bondTargetPoolId);
                bond.setTargetPoolName(targetPool.getPoolName());
                bond.setPoolType(targetPool.getPoolType());
                bond.setAuditStatus(AuditStatus.APPROVED.getCode());
                bond.setAdjusterId(AUTO_ADJUSTER_ID);
                bond.setAdjusterName(AUTO_ADJUSTER_NAME);
                String reason = ScheduledAdjustLogHelper.appendDetails(ScheduledAdjustLogHelper.taskLabel(taskName),
                        ScheduledAdjustLogHelper.issuerDetail(bond),
                        ScheduledAdjustLogHelper.poolDetail("主体所在池", companyPool, companyInPoolId));
                if (bond.getTriggerCompanyCode() != null) {
                    reason = ScheduledAdjustLogHelper.appendDetails(reason, "联动触发主体：" + bond.getTriggerCompanyCode());
                }
                bond.setAdjustReason(reason);
                // 5.3 生成批次号并写入债券入池日志
                String batchNo = batchNoContext.resolveBondBatchNo(
                        bond.getSecurityCode(), bondTargetPoolId, AdjustMode.IN.getCode());
                bond.setAdjustBatchNo(batchNo);
                bond.setSubmitTime(submitTime);
                // 5.4 写债券入池日志并更新池状态
                securityPoolAdjustMapper.addAdjustLog(bond);
                bond.setAdjustLogId(bond.getId());
                int inserted = securityPoolAdjustMapper.addPoolStatus(bond);
                if (inserted != 1) {
                    pairSkippedCount++;
                    skipDetail(detail, "证券【" + bond.getSecurityCode() + "】：当前已在债券目标池，本轮不处理");
                    continue;
                }
                pairCount++;
                // 5.5 入池成功后处理债券原所在的关系池
                int autoOutCount = AutoAdjustRelationHelper.autoOutCurrentRelationPools(
                        bond, currentPoolIds, poolMap, allRelations, securityPoolAdjustMapper);
                if (autoOutCount > 0) {
                    infoDetail(detail, "债券[" + bond.getSecurityCode() + "]自动调出关系池 "
                            + autoOutCount + " 个");
                }
            }
            summary.addMapping(companyInPoolId, companyPool == null ? null : companyPool.getPoolName(),
                    bondTargetPoolId, targetPool.getPoolName(), bondList.size(), pairCount, pairSkippedCount);
            infoDetail(detail, "处理结果：主体所在池【" + companyInPoolId + "】→债券池【"
                    + targetPool.getPoolName() + "】实际入池 " + pairCount + " 条，未处理 " + pairSkippedCount + " 条");
        }
        return summary;
    }

    /** 本轮在池主体旗下债券入池汇总 */
    private static final class AutoInSummary {

        /** 本轮实际入池总数 */
        private int total;
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
     * 从库表读取本任务扩展参数原文，未配置则返回 null
     */
    private String resolveParamJson() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf != null && StringUtils.hasText(conf.getParamJson())) {
            return conf.getParamJson().trim();
        }
        return null;
    }

    /**
     * 从库表读取任务展示名称，缺失时使用默认名称
     */
    private String resolveTaskName() {
        try {
            SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
            if (conf != null && StringUtils.hasText(conf.getTaskName())) {
                return conf.getTaskName().trim();
            }
        } catch (Exception e) {
            log.debug("读取任务名称失败: {}", TASK_CODE);
        }
        return DEFAULT_TASK_NAME;
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
     * 解析扩展参数中的主体池到债券池映射
     *
     * 支持 poolIds、poolId 和 mappings 三种写法，非法 JSON 直接返回业务错误
     *
     * @param raw 扩展参数原文
     * @param taskName 任务展示名称
     * @return 每项为主体所在池 ID 和债券写入池 ID 的映射
     */
    List<long[]> parseParamMappings(String raw, String taskName) {
        List<long[]> result = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return result;
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            throw new BizException("扩展参数须为 JSON 对象，示例 {\"poolIds\":[15]}，当前: " + text);
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            log.warn("{}：JSON 扩展参数解析失败: {}，原因: {}", taskName, text, e.getMessage());
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage()
                    + "；请使用标准 JSON，示例 {\"poolIds\":[15]}");
        }
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
        return parseParamMappings(raw, TASK_CODE);
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
