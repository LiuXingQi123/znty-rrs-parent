package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
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
 * 外评非 AA- 及以下主体自动出池任务
 * <p>
 * 对应老系统 {@code AdjustRuleOutAA}（配置名：自动导出外部评级不是 AA- 及以下的主体）。
 * 扫描 Wind 主体有效外评（近 12 个月取档位最高，更早取日期最新，再取更近一条）
 * 不在 AA-/A/BBB… 列表内（如 AA/AA+/AAA）、
 * 且当前已在目标池的主体，自动调出 param_json.poolIds 指定池。
 * 主体已在 limitPoolIds（默认全部 forbidden 池，对齐 LIMITPOOLID_XYJJ）则不出；
 * 主体出池成功后再出同池旗下债。adjust_type=自动调整，不走审批。
 * </p>
 */
@Slf4j
@Service
public class CompanyOuterRatingNotAaMinusAutoOutService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "company_outer_rating_not_aa_minus_auto_out";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 自动出池原因 */
    private static final String REASON = "外评非AA-及以下主体自动出池";
    /** 批次号后缀 */
    private static final String BATCH_SUFFIX = "3005";
    /** JSON 解析 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 扩展参数说明
     */
    private static final String PARAM_HELP =
            "参数格式：须填写 JSON 对象，例如 <code>{\"poolIds\":[16],\"limitPoolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（仅目标池）：<code>{\"poolIds\":[16]}</code>；未填写 limitPoolIds 时，默认使用全部禁投池拦截\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：外评已不属于 AA-及以下、当前在 16（观察池）的主体，会自动调出；但同时在任一禁投池的主体不调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（目标池 + 拦截池）：<code>{\"poolIds\":[16],\"limitPoolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：外评已不属于 AA-及以下、当前在 16（观察池）的主体，会自动调出；同时在 15（债券禁止库）的主体被排除，不从观察池调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多目标池）：<code>{\"poolIds\":[16,17],\"limitPoolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：分别扫描 16、17 两个目标池；外评已不属于 AA-及以下的在池主体自动调出，但同时在 15（债券禁止库）的主体不调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "关闭拦截写法：<code>{\"poolIds\":[16],\"limitPoolIds\":[]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：外评已不属于 AA-及以下、当前在 16（观察池）的主体均可自动调出，不再按禁投池排除（仍受目标池自身调出限制关系约束）\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体出池目标池）：可选；与投资池「关系配置 → 自动调出规则」中绑定本任务的池取并集后扫描\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "limitPoolIds（禁止出池拦截池）：可选；主体当前已在这些池中的任一池时，不从扫描目标池自动出库\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "limitPoolIds 省略：默认使用全部禁投池；填写 <code>[]</code>：关闭禁投拦截\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：有效外评为 AA、AA+、AAA 等非 AA-及以下名单，且主体已在目标池时，自动调出主体\n"
                    + "评级口径：仅认机构 2/4/5/6/7/13/14/19/20；无认可机构外评的主体不处理\n"
                    + "联动处理：主体成功出池后，继续调出该主体在同一目标池内的旗下债券\n"
                    + "限制规则：主体或旗下债已在目标池配置的调出限制池时，跳过该条记录\n"
                    + "执行方式：直接生效，不走审批；参数格式错误时，本轮任务失败";

    /** 自动调库查询 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 调库落地 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 投资池 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 定时任务配置 */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;
    /** 扫描池并集（参数 ∪ 关系配置） */
    @Resource
    private AutoAdjustPoolScopeHelper poolScopeHelper;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        String taskName = resolveTaskName();
        infoDetail(detail, taskName + " 开始");
        try {
            int total = doAutoOut(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共自动出池 " + total + " 条（含主体及同池旗下债）";
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
     * 按 poolIds 将外评非 AA- 及以下且已在池的主体自动出池，并顺带出同池旗下债
     */
    private int doAutoOut(String taskName, TaskDetailLog detail) {
        String paramJson = resolveParamJson();
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        List<Long> poolIds = poolScopeHelper.resolveUnionPoolIds(
                paramJson, TASK_CODE, RuleType.AUTO_OUT.getCode(), detail);
        infoDetail(detail, "目标池列表 poolIds=" + poolIds);
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        List<Long> limitPoolIds = resolveLimitPoolIds(paramJson, poolMap);
        infoDetail(detail, "禁投拦截池 limitPoolIds=" + limitPoolIds);
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        infoDetail(detail, "本轮批次号 " + batchNo);
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        int total = 0;
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                warnDetail(detail, "池[" + poolId + "]不存在，跳过");
                continue;
            }
            List<Long> outRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.OUT_RESTRICT.getCode(), allRelations);
            // 查询在池且外评已高于 AA- 及以下列表、且未落在禁投拦截池的主体
            List<IpAdjustLogBo> companies = autoAdjustMapper.queryCompanyByNotLowOuterRatingInPool(
                    poolId, limitPoolIds);
            if (companies == null || companies.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无待出池高外评主体");
                continue;
            }
            int poolCount = 0;
            for (IpAdjustLogBo company : companies) {
                if (company == null || !StringUtils.hasText(company.getSecurityCode())) {
                    continue;
                }
                // 对齐老 AdjustPoolByRule.checkSecurityOutPoolRelation（关系 12 / 调出限制池）
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        securityPoolAdjustMapper.querySecurityCurrentPoolIdList(company.getSecurityCode()),
                        outRestrictPoolIds)) {
                    warnDetail(detail, "主体[" + company.getSecurityCode() + "]当前在调出限制池中，跳过");
                    continue;
                }
                String reason = buildAdjustReason(company.getOuterRating());
                fillAutoOutLog(company, pool, poolId, batchNo, submitTime, reason);
                // 写自动出池日志
                securityPoolAdjustMapper.addAdjustLog(company);
                // 软删除池状态
                int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(company.getSecurityCode(), poolId);
                if (deleted != 1) {
                    warnDetail(detail, "主体[" + company.getSecurityCode() + "]软删池状态失败（可能并发已出池），跳过");
                    continue;
                }
                poolCount++;
                total++;
                // 主体出池成功后，顺带调出同池旗下债券
                total += outSamePoolBonds(company.getSecurityCode(), pool, poolId, batchNo, submitTime,
                        outRestrictPoolIds, detail, reason);
            }
            infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 出池 "
                    + poolCount + " 个主体（含同池债计入合计）");
        }
        infoDetail(detail, "批次号 " + batchNo + "，合计出池 " + total + " 条（含主体及同池旗下债）");
        return total;
    }

    /**
     * 将命中主体旗下已在同一目标池的债券一并调出。
     */
    private int outSamePoolBonds(String companyCode, InvestmentPoolBo pool, Long poolId,
                                 String batchNo, Date submitTime, List<Long> outRestrictPoolIds,
                                 TaskDetailLog detail, String companyReason) {
        List<IpAdjustLogBo> bonds = autoAdjustMapper.queryCompanyBondInSamePoolForAutoOut(companyCode, poolId);
        if (bonds == null || bonds.isEmpty()) {
            return 0;
        }
        int bondCount = 0;
        String bondReason = companyReason + "（同池旗下债）";
        for (IpAdjustLogBo bond : bonds) {
            if (bond == null || !StringUtils.hasText(bond.getSecurityCode())) {
                continue;
            }
            if (AutoAdjustRestrictHelper.isInAnyPool(
                    securityPoolAdjustMapper.querySecurityCurrentPoolIdList(bond.getSecurityCode()),
                    outRestrictPoolIds)) {
                warnDetail(detail, "债券[" + bond.getSecurityCode() + "]当前在调出限制池中，跳过");
                continue;
            }
            fillAutoOutLog(bond, pool, poolId, batchNo, submitTime, bondReason);
            securityPoolAdjustMapper.addAdjustLog(bond);
            int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(bond.getSecurityCode(), poolId);
            if (deleted != 1) {
                warnDetail(detail, "债券[" + bond.getSecurityCode() + "]软删池状态失败（可能并发已出池），跳过");
                continue;
            }
            bondCount++;
        }
        if (bondCount > 0) {
            infoDetail(detail, "主体[" + companyCode + "]同池旗下债调出 " + bondCount + " 条");
        }
        return bondCount;
    }

    /**
     * 调整原因带上主体当前有效外评。
     */
    static String buildAdjustReason(String outerRating) {
        if (!StringUtils.hasText(outerRating)) {
            return REASON;
        }
        return REASON + "（当前外评：" + outerRating.trim() + "）";
    }

    /**
     * 回填自动出池日志公共字段。
     */
    private void fillAutoOutLog(IpAdjustLogBo log, InvestmentPoolBo pool, Long poolId,
                                String batchNo, Date submitTime, String reason) {
        log.setAdjustType("自动调整");
        log.setAdjustMode(AdjustMode.OUT.getCode());
        log.setTargetPoolId(poolId);
        log.setTargetPoolName(pool.getPoolName());
        log.setPoolType(pool.getPoolType());
        log.setAuditStatus(AuditStatus.APPROVED.getCode());
        log.setAdjusterId(AUTO_ADJUSTER_ID);
        log.setAdjusterName(AUTO_ADJUSTER_NAME);
        log.setAdjustReason(reason);
        log.setAdjustAdvice(reason);
        log.setAdjustBatchNo(batchNo);
        log.setSubmitTime(submitTime);
    }

    /**
     * 从库表读取本任务扩展参数原文
     */
    private String resolveParamJson() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf != null && StringUtils.hasText(conf.getParamJson())) {
            return conf.getParamJson().trim();
        }
        return null;
    }

    /**
     * 解析禁投拦截池：显式 limitPoolIds 优先；未写则默认全部 forbidden 池。
     */
    List<Long> resolveLimitPoolIds(String paramJson, Map<Long, InvestmentPoolBo> poolMap) {
        if (hasLimitPoolIdsField(paramJson)) {
            return parseOptionalIdArray(paramJson, "limitPoolIds");
        }
        List<Long> defaults = new ArrayList<>();
        if (poolMap == null) {
            return defaults;
        }
        for (InvestmentPoolBo pool : poolMap.values()) {
            if (pool != null && pool.getId() != null
                    && PoolType.FORBIDDEN.getCode().equals(pool.getPoolType())) {
                defaults.add(pool.getId());
            }
        }
        return defaults;
    }

    /**
     * 判断 JSON 是否显式带了 limitPoolIds 字段（含空数组）。
     */
    boolean hasLimitPoolIdsField(String raw) {
        if (!StringUtils.hasText(raw) || !raw.trim().startsWith("{")) {
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw.trim());
            return root != null && root.has("limitPoolIds");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 JSON 中的数字 ID 数组；缺字段或空数组返回空列表。
     */
    List<Long> parseOptionalIdArray(String raw, String field) {
        List<Long> result = new ArrayList<>();
        if (!StringUtils.hasText(raw) || !raw.trim().startsWith("{")) {
            return result;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw.trim());
            JsonNode arr = root.get(field);
            if (arr == null || !arr.isArray()) {
                return result;
            }
            for (JsonNode item : arr) {
                if (item == null || item.isNull() || !item.isNumber()) {
                    throw new BizException(field + " 元素须为数字，非法值: " + item);
                }
                result.add(item.asLong());
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 {"poolIds":[15,16]}（包内可测）
     */
    List<Long> parsePoolIds(String raw, String taskName) {
        List<Long> ids = AutoAdjustPoolScopeHelper.parseOptionalPoolIds(raw);
        if (ids.isEmpty()) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[15]}");
        }
        return ids;
    }

    /** 单测入口 */
    List<Long> parsePoolIds(String raw) {
        return parsePoolIds(raw, TASK_CODE);
    }

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

    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line("INFO", line);
        }
    }

    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line("WARN", line);
        }
    }
}
