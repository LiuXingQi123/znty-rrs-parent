package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RuleType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 外评非 AA- 及以下主体自动出池任务
 * <p>
 * 对应老系统 {@code AdjustRuleOutAA}。现按质押券黑名单管理办法：
 * 已在目标池且（一）（二）（三）均不满足才出池。近一年无认可外评不出。
 * Demo 目标池为黑名单质押库 17，limitPoolIds 为空数组。adjust_type=自动调整，不走审批。
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
     * 本任务扩展参数说明（配置页按行拆成列表展示）
     */
    private static final String PARAM_HELP =
            "参数格式：须填写 JSON 对象，例如 <code>{\"poolIds\":[17],\"limitPoolIds\":[]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（目标池 + 空拦截）：<code>{\"poolIds\":[17],\"limitPoolIds\":[]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：已在 17（黑名单质押库），且不在禁止库 15、不在重点观察 23、近一年孰低外评也不属于 AA-及以下的主体，自动调出 17\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（仅目标池）：<code>{\"poolIds\":[17]}</code>；省略 limitPoolIds 与填写 <code>[]</code> 相同，不再默认拦禁投池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：与 Demo 相同，从 17（黑名单质押库）按（一）（二）（三）均不满足的规则出库\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（目标池 + 额外拦截）：<code>{\"poolIds\":[17],\"limitPoolIds\":[16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：在 17 且三条都不满足的主体出库；同时在 16（观察池）的主体额外排除\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体出池目标池）：可选；与投资池「关系配置 → 自动调出规则」中绑定本任务的池取并集后扫描\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "limitPoolIds（禁止出池拦截池）：可选；Demo 为 <code>[]</code>。主体当前已在这些池中的任一池时，不从扫描目标池自动出库\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "limitPoolIds 省略或 <code>[]</code>：不追加额外拦截（条款（一）（三）已在扫描中排除 15/23）\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：已在目标池，且不在 15、不在 23、近一年认可外评存在且孰低不属于 AA-及以下时，自动调出主体\n"
                    + "评级口径：近一年（日历年）内 10 家认可机构多评级取孰低；仅认机构 2/3/4/5/6/7/13/14/19/20；无认可外评的主体不处理（不出）\n"
                    + "联动处理：主体成功出池后，继续调出该主体在同一目标池内的旗下债券\n"
                    + "限制规则：主体或旗下债已在目标池配置的调出限制池时，跳过该条记录\n"
                    + "执行方式：直接生效，不走审批；参数格式错误时，本轮任务失败";

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

    /**
     * 返回与库表绑定的任务编码
     */
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
     * 执行质押券黑名单自动出池：已在目标池且（一）（二）（三）均不满足的主体出库
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
            // 执行自动出池（扩展参数非法时抛 BizException → 记失败）
            int total = doAutoOut(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共自动出池 " + total + " 条（含主体及同池旗下债）";
            // 记录结束（控制台 + 过程日志）
            infoDetail(detail, taskName + " 结束，" + message);
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, total, startTime, duration,
                    detail.build());
        } catch (BizException e) {
            long duration = System.currentTimeMillis() - begin;
            // 记录业务失败
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
     * 按目标池将（一）（二）（三）均不满足且已在池的主体自动出池，并顺带出同池旗下债
     *
     * @param taskName 任务展示名称
     * @param detail   过程日志
     * @return 本轮出池条数（含同池旗下债）
     */
    private int doAutoOut(String taskName, TaskDetailLog detail) {
        // 读取本任务扩展参数
        String paramJson = resolveParamJson();
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        // 参数池与关系配置绑定池取并集
        List<Long> poolIds = poolScopeHelper.resolveUnionPoolIds(
                paramJson, TASK_CODE, RuleType.AUTO_OUT.getCode(), detail);
        infoDetail(detail, "目标池列表 poolIds=" + poolIds);
        // 构建池 ID → 池对象映射
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        // 解析额外拦截池（省略或 [] 都不拦截）
        List<Long> limitPoolIds = resolveLimitPoolIds(paramJson);
        infoDetail(detail, "额外拦截池 limitPoolIds=" + limitPoolIds);
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        infoDetail(detail, "本轮批次号 " + batchNo);
        // 一次加载全量池关系，供调出限制池（out_restrict）拦截
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        int total = 0;
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                warnDetail(detail, "池[" + poolId + "]不存在，跳过");
                continue;
            }
            // 解析目标池的调出限制池
            List<Long> outRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.OUT_RESTRICT.getCode(), allRelations);
            // 条款（二）反面：已在目标池且近一年孰低不属于 AA-及以下
            List<IpAdjustLogBo> companies = autoAdjustMapper.queryCompanyByNotLowOuterRatingInPool(
                    poolId, limitPoolIds);
            // 仍命中（一）或（三）的主体不出
            companies = excludeCompaniesStillMatchingClauseOneOrThree(companies);
            if (companies == null || companies.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无待出池主体");
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
                // 调整原因带近一年孰低外评
                String reason = buildAdjustReason(company.getOuterRating());
                // 回填自动出池日志公共字段
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
     * 排除当前仍在禁止库或重点观察名单的主体（条款（一）（三）仍成立则不出）。
     *
     * @param companies 条款（二）反面的出池候选
     * @return 三条均不满足的主体
     */
    private List<IpAdjustLogBo> excludeCompaniesStillMatchingClauseOneOrThree(List<IpAdjustLogBo> companies) {
        if (companies == null || companies.isEmpty()) {
            return companies;
        }
        // 查询仍在禁止库、重点观察的主体代码
        Set<String> stayCodes = new HashSet<>();
        addCompanyCodes(stayCodes, autoAdjustMapper.queryCompanyCodeListInPool(
                AutoAdjustRestrictHelper.COMPANY_FORBIDDEN_POOL_ID));
        addCompanyCodes(stayCodes, autoAdjustMapper.queryCompanyCodeListInPool(
                AutoAdjustRestrictHelper.KEY_WATCH_POOL_ID));
        if (stayCodes.isEmpty()) {
            return companies;
        }
        List<IpAdjustLogBo> result = new ArrayList<>();
        for (IpAdjustLogBo company : companies) {
            if (company == null || !StringUtils.hasText(company.getSecurityCode())
                    || stayCodes.contains(company.getSecurityCode())) {
                continue;
            }
            result.add(company);
        }
        return result;
    }

    /**
     * 将在池主体代码并入排除集合。
     *
     * @param stayCodes 排除集合
     * @param codes     在池主体代码
     */
    private void addCompanyCodes(Set<String> stayCodes, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        for (String code : codes) {
            if (StringUtils.hasText(code)) {
                stayCodes.add(code);
            }
        }
    }

    /**
     * 将命中主体旗下已在同一目标池的债券一并调出。
     *
     * @param companyCode        主体代码
     * @param pool               目标池
     * @param poolId             目标池 ID
     * @param batchNo            批次号
     * @param submitTime         提交时间
     * @param outRestrictPoolIds 调出限制池
     * @param detail             过程日志
     * @param companyReason      主体出池原因
     * @return 调出债券条数
     */
    private int outSamePoolBonds(String companyCode, InvestmentPoolBo pool, Long poolId,
                                 String batchNo, Date submitTime, List<Long> outRestrictPoolIds,
                                 TaskDetailLog detail, String companyReason) {
        // 查询该主体旗下已在同一目标池的债券
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
            // 对齐老 AdjustPoolByRule.checkSecurityOutPoolRelation（关系 12 / 调出限制池）
            if (AutoAdjustRestrictHelper.isInAnyPool(
                    securityPoolAdjustMapper.querySecurityCurrentPoolIdList(bond.getSecurityCode()),
                    outRestrictPoolIds)) {
                warnDetail(detail, "债券[" + bond.getSecurityCode() + "]当前在调出限制池中，跳过");
                continue;
            }
            // 回填自动出池日志公共字段
            fillAutoOutLog(bond, pool, poolId, batchNo, submitTime, bondReason);
            // 写自动出池日志
            securityPoolAdjustMapper.addAdjustLog(bond);
            // 软删除池状态
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
     * 调整原因带上近一年孰低外评。
     *
     * @param outerRating 近一年孰低外评
     * @return 调整原因
     */
    static String buildAdjustReason(String outerRating) {
        if (!StringUtils.hasText(outerRating)) {
            return REASON;
        }
        return REASON + "（近一年孰低外评：" + outerRating.trim() + "）";
    }

    /**
     * 回填自动出池日志公共字段。
     *
     * @param log        调库日志
     * @param pool       目标池
     * @param poolId     目标池 ID
     * @param batchNo    批次号
     * @param submitTime 提交时间
     * @param reason     调整原因
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
     *
     * @return param_json；未配置时返回 null
     */
    private String resolveParamJson() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf != null && StringUtils.hasText(conf.getParamJson())) {
            return conf.getParamJson().trim();
        }
        return null;
    }

    /**
     * 解析额外拦截池：省略或 [] 都不拦截。
     *
     * @param paramJson 扩展参数原文
     * @return 拦截池 ID；空表示不追加拦截
     */
    List<Long> resolveLimitPoolIds(String paramJson) {
        // 从 JSON 读取 limitPoolIds，缺字段视为空
        return parseOptionalIdArray(paramJson, "limitPoolIds");
    }

    /**
     * 解析 JSON 中的数字 ID 数组；缺字段或空数组返回空列表。
     *
     * @param raw   扩展参数原文
     * @param field 数组字段名
     * @return ID 列表
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
     * 解析扩展参数 JSON 为池 ID 列表，格式：{"poolIds":[17]}
     *
     * @param raw      扩展参数原文
     * @param taskName 任务展示名称
     * @return 池 ID 列表
     */
    List<Long> parsePoolIds(String raw, String taskName) {
        // 解析 JSON 中的 poolIds 数组
        List<Long> ids = AutoAdjustPoolScopeHelper.parseOptionalPoolIds(raw);
        if (ids.isEmpty()) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[17]}");
        }
        return ids;
    }

    /**
     * 单测入口：解析 poolIds
     *
     * @param raw 扩展参数原文
     * @return 池 ID 列表
     */
    List<Long> parsePoolIds(String raw) {
        return parsePoolIds(raw, TASK_CODE);
    }

    /**
     * 构建池 ID 到投资池对象的映射
     *
     * @return 池映射
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
     * 从库表读取任务展示名称，缺失时回退为任务编码
     *
     * @return 任务名称
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
     * 写 INFO 级过程日志并同步控制台
     *
     * @param detail 过程日志
     * @param line   日志内容
     */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line("INFO", line);
        }
    }

    /**
     * 写 WARN 级过程日志并同步控制台
     *
     * @param detail 过程日志
     * @param line   日志内容
     */
    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line("WARN", line);
        }
    }
}
