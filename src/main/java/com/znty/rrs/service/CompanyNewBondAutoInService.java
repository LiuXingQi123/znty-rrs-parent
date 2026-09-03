package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在池主体旗下债券自动入池任务实现
 * <p>
 * 扫描「主体已在指定池」的发行主体，将其旗下未到期、尚未在目标池的债券自动入池
 * （排除临时代码已更新为正式代码的记录，并排除 ABS / CRMW）；adjust_type=自动调整，不走审批。
 * 对应老系统 AutoAdjustInNewBondToLimitPoolJob。名称/cron/启停/扩展参数由库表维护。
 * 同池-only、带 market_codes 的 IP_RULE「主体下债券自动入库」见
 * {@link CompanySamePoolBondAutoInService}。
 * </p>
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
    /** 自动入池原因（写入调库日志） */
    private static final String REASON_COMPANY_NEW_BOND_IN = "在池主体旗下债券自动入池";
    /** 批次号规则后缀 */
    private static final String BATCH_SUFFIX = "3002";
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
                    + "扫描范围：扩展参数 poolIds/mappings 与投资池关系配置绑定本任务的池（按同池映射）取并集；并集为空时本轮失败\n"
                    + "处理规则：主体已在主体池且生效时，将其旗下未到期且未在债券目标池的债券自动入池\n"
                    + "排除范围：已更新正式代码的临时代码、ABS、CRMW 不处理\n"
                    + "关系调出：入池成功后，按目标池调入互斥关系及反向调入限制关系自动调出债券原所在池并记录日志\n"
                    + "范围说明：不检查调入限制池，也不按目标池 market_codes 过滤；需要同池市场/限制校验时请使用“主体下债券自动入库”任务\n"
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
     * 执行在池主体旗下债券自动入池：按库表 param_json 映射扫描待入池债券并落地在池状态
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        // 展示名读库
        String taskName = resolveTaskName();
        // 记录开始（控制台 + 过程日志）
        infoDetail(detail, taskName + " 开始");
        try {
            // 执行主体新债自动入池（扩展参数非法时抛 BizException → 记失败）
            int total = doAutoInCompanyNewBond(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共入池 " + total + " 条";
            infoDetail(detail, taskName + " 结束，" + message);
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, total, startTime, duration,
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
     * 按扩展参数中的「主体所在池 → 债券写入池」映射扫描待入池新债，写调入日志并落在池状态
     */
    private int doAutoInCompanyNewBond(String taskName, TaskDetailLog detail) {
        // 读取扩展参数原文
        String paramJson = resolveParamJson();
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        // 解析为 [主体所在池ID, 债券写入池ID] 列表（非法则抛业务异常）
        List<long[]> pairList = poolScopeHelper.unionSamePoolMappings(
                parseParamMappings(paramJson, taskName), TASK_CODE, RuleType.AUTO_IN.getCode(), detail);
        infoDetail(detail, "有效映射组数 " + pairList.size());
        // 构建池 ID → 池信息映射
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        infoDetail(detail, "本轮批次号 " + batchNo);
        int total = 0;
        for (long[] pair : pairList) {
            Long companyInPoolId = pair[0];
            Long bondTargetPoolId = pair[1];
            InvestmentPoolBo targetPool = poolMap.get(bondTargetPoolId);
            if (targetPool == null) {
                warnDetail(detail, "债券写入池[" + bondTargetPoolId + "]不存在，跳过映射 "
                        + companyInPoolId + "→" + bondTargetPoolId);
                continue;
            }
            List<IpAdjustLogBo> bondList = autoAdjustMapper.queryCompanyNewBondForAutoIn(
                    companyInPoolId, bondTargetPoolId);
            if (bondList == null || bondList.isEmpty()) {
                infoDetail(detail, "主体所在池[" + companyInPoolId + "]→债券写入池["
                        + targetPool.getPoolName() + "](" + bondTargetPoolId + ") 无待入池新债");
                continue;
            }
            int pairCount = 0;
            for (IpAdjustLogBo bond : bondList) {
                List<Long> currentPoolIds = securityPoolAdjustMapper
                        .querySecurityCurrentPoolIdList(bond.getSecurityCode());
                bond.setAdjustType("自动调整");
                bond.setAdjustMode(AdjustMode.IN.getCode());
                bond.setTargetPoolId(bondTargetPoolId);
                bond.setTargetPoolName(targetPool.getPoolName());
                bond.setPoolType(targetPool.getPoolType());
                bond.setAuditStatus(AuditStatus.APPROVED.getCode());
                bond.setAdjusterId(AUTO_ADJUSTER_ID);
                bond.setAdjusterName(AUTO_ADJUSTER_NAME);
                bond.setAdjustReason(REASON_COMPANY_NEW_BOND_IN);
                bond.setAdjustAdvice(REASON_COMPANY_NEW_BOND_IN);
                bond.setAdjustBatchNo(batchNo);
                bond.setSubmitTime(submitTime);
                // 写自动调入日志
                securityPoolAdjustMapper.addAdjustLog(bond);
                bond.setAdjustLogId(bond.getId());
                // 写入在池状态
                int inserted = securityPoolAdjustMapper.addPoolStatus(bond);
                if (inserted != 1) {
                    warnDetail(detail, "债券[" + bond.getSecurityCode() + "]写入池状态失败（可能并发已入池），跳过");
                    continue;
                }
                pairCount++;
                total++;
                // 入池成功后从当前实际所在的互斥/受限池调出，并写同批自动调出日志
                int autoOutCount = AutoAdjustRelationHelper.autoOutCurrentRelationPools(
                        bond, currentPoolIds, poolMap, allRelations, securityPoolAdjustMapper);
                if (autoOutCount > 0) {
                    infoDetail(detail, "债券[" + bond.getSecurityCode() + "]自动调出关系池 "
                            + autoOutCount + " 个");
                }
            }
            infoDetail(detail, "主体所在池[" + companyInPoolId + "]→债券写入池["
                    + targetPool.getPoolName() + "](" + bondTargetPoolId + ") 入池 " + pairCount + " 条");
        }
        infoDetail(detail, "批次号 " + batchNo + "，合计入池 " + total + " 条");
        return total;
    }

    /**
     * 写 INFO 级过程日志并同步控制台
     */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line("INFO", line);
        }
    }

    /**
     * 写 WARN 级过程日志并同步控制台
     */
    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line("WARN", line);
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
     * 从库表读取任务展示名称，缺失时回退为任务编码
     */
    private String resolveTaskName() {
        try {
            SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
            if (conf != null && StringUtils.hasText(conf.getTaskName())) {
                return conf.getTaskName();
            }
        } catch (Exception e) {
            log.debug("读取任务名称失败: {}", TASK_CODE);
        }
        return TASK_CODE;
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
     * 解析扩展参数为池映射列表：每项 [companyInPoolId, bondTargetPoolId]（仅 JSON）
     * <p>
     * 支持：{@code poolIds} 数组（主体与债同一池，可多个）、{@code poolId} 单个、
     * {@code mappings} 显式「主体所在池→债券写入池」。
     * 非法 JSON 抛 {@link BizException}；无有效映射时返回空列表，由并集解析再决定是否失败。
     * </p>
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
        // 同池多个：poolIds
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
        // 同池单个：poolId（与 poolIds 可并存）
        if (root.has("poolId") && !root.get("poolId").isNull()) {
            Long id = readNumberNode(root.get("poolId"));
            if (id == null) {
                throw new BizException("poolId 须为数字，示例 {\"poolId\":15}");
            }
            result.add(new long[]{id, id});
        }
        // 不同池映射
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
