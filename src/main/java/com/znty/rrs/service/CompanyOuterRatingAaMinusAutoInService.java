package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外评 AA- 及以下主体自动入池任务
 * <p>
 * 对应老系统 {@code AdjustRuleInAA}。现按质押券黑名单管理办法：
 * 未在目标池且满足（一）禁止库 /（二）近一年孰低 AA-及以下 /（三）重点观察名单 之一即入池。
 * Demo 目标池为黑名单质押库 17。adjust_type=自动调整，不走审批。
 * </p>
 */
@Slf4j
@Service
public class CompanyOuterRatingAaMinusAutoInService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "company_outer_rating_aa_minus_auto_in";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 自动入池原因 */
    private static final String REASON = "外评AA-及以下主体自动入池";
    /** 批次号后缀 */
    private static final String BATCH_SUFFIX = "3004";

    /**
     * 本任务扩展参数说明（配置页按行拆成列表展示）
     */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[17]}</code>；也可不填 poolIds，仅扫描投资池关系配置中绑定了本任务的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[17]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：符合质押券黑名单（一）（二）（三）之一、尚未在 17（黑名单质押库）的主体，自动调入 17（黑名单质押库）\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多池）：<code>{\"poolIds\":[17,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：符合（一）（二）（三）之一的主体，分别补充调入 17（黑名单质押库）、16（观察池）中尚未在池的目标池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体入池目标池）：可选；与投资池「关系配置 → 自动调入规则」中绑定本任务的池取并集后扫描\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：满足下列任一且尚未在目标池则自动入池：（一）当前在公司信用债禁止库 15；（二）近一年认可外评孰低为 AA-及以下；（三）当前在重点观察名单 23\n"
                    + "评级口径：近一年（日历年）内 10 家认可机构多评级取孰低，一年以前忽略；仅认机构 2/3/4/5/6/7/13/14/19/20；近一年无认可外评不因（二）入库\n"
                    + "限制规则：主体已在目标池配置的调入限制池时，跳过该条记录\n"
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
     * 执行质押券黑名单自动入池：扫描满足（一）（二）（三）之一且未在目标池的主体
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
            // 执行自动入池（扩展参数非法时抛 BizException → 记失败）
            int total = doAutoIn(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共自动入池 " + total + " 个主体";
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
     * 按目标池将满足质押券黑名单（一）（二）（三）之一且未在池的主体自动入池
     *
     * @param taskName 任务展示名称
     * @param detail   过程日志
     * @return 本轮入池主体数
     */
    private int doAutoIn(String taskName, TaskDetailLog detail) {
        // 从扩展参数与关系配置解析入池目标池
        List<Long> poolIds = resolvePoolIds(taskName, detail);
        infoDetail(detail, "目标池列表 poolIds=" + poolIds);
        // 构建池 ID → 池对象映射
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        infoDetail(detail, "本轮批次号 " + batchNo);
        // 一次加载全量池关系，供调入限制池（in_restrict）拦截
        List<PoolRelationBo> allRelations = securityPoolAdjustMapper.queryAllPoolRelationList();
        int total = 0;
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                warnDetail(detail, "池[" + poolId + "]不存在，跳过");
                continue;
            }
            // 解析目标池的调入限制池
            List<Long> inRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.IN_RESTRICT.getCode(), allRelations);
            // 分别查询（一）（二）（三）后在内存合并去重
            List<IpAdjustLogBo> companies = queryInboundCandidates(poolId);
            if (companies == null || companies.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无待入池黑名单主体");
                continue;
            }
            int poolCount = 0;
            for (IpAdjustLogBo company : companies) {
                if (company == null || !StringUtils.hasText(company.getSecurityCode())) {
                    continue;
                }
                // 对齐老 AdjustPoolByRule.checkSecurityInPoolRelation（关系 11 / 调入限制池）
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        securityPoolAdjustMapper.querySecurityCurrentPoolIdList(company.getSecurityCode()),
                        inRestrictPoolIds)) {
                    warnDetail(detail, "主体[" + company.getSecurityCode() + "]当前在调入限制池中，跳过");
                    continue;
                }
                company.setSecurityType("company");
                company.setAdjustType("自动调整");
                company.setAdjustMode(AdjustMode.IN.getCode());
                company.setTargetPoolId(poolId);
                company.setTargetPoolName(pool.getPoolName());
                company.setPoolType(pool.getPoolType());
                company.setAuditStatus(AuditStatus.APPROVED.getCode());
                company.setAdjusterId(AUTO_ADJUSTER_ID);
                company.setAdjusterName(AUTO_ADJUSTER_NAME);
                // 按命中的（一）（二）（三）拼接调整原因
                String reason = buildAdjustReason(company);
                company.setAdjustReason(reason);
                company.setAdjustAdvice(reason);
                company.setAdjustBatchNo(batchNo);
                company.setSubmitTime(submitTime);
                // 写自动入池日志
                securityPoolAdjustMapper.addAdjustLog(company);
                company.setAdjustLogId(company.getId());
                // 写入在池状态
                int inserted = securityPoolAdjustMapper.addPoolStatus(company);
                if (inserted != 1) {
                    warnDetail(detail, "主体[" + company.getSecurityCode() + "]写入池状态失败（可能并发已入池），跳过");
                    continue;
                }
                poolCount++;
                total++;
            }
            infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 入池 " + poolCount + " 个主体");
        }
        infoDetail(detail, "批次号 " + batchNo + "，合计入池 " + total + " 个主体");
        return total;
    }

    /**
     * 分别查询条款（一）（二）（三），按主体代码合并并回填命中标记。
     *
     * @param targetPoolId 入池目标池
     * @return 去重后的待入池主体
     */
    private List<IpAdjustLogBo> queryInboundCandidates(Long targetPoolId) {
        Map<String, IpAdjustLogBo> merged = new LinkedHashMap<>();
        // 条款（一）：当前在禁止库且尚未在目标池
        mergeInboundHits(merged, autoAdjustMapper.queryCompanyInPoolNotInTarget(
                AutoAdjustRestrictHelper.COMPANY_FORBIDDEN_POOL_ID, targetPoolId), true, false, false);
        // 条款（三）：当前在重点观察且尚未在目标池
        mergeInboundHits(merged, autoAdjustMapper.queryCompanyInPoolNotInTarget(
                AutoAdjustRestrictHelper.KEY_WATCH_POOL_ID, targetPoolId), false, false, true);
        // 条款（二）：近一年孰低 AA-及以下且尚未在目标池
        mergeInboundHits(merged, autoAdjustMapper.queryCompanyByLowOuterRatingNotInPool(targetPoolId),
                false, true, false);
        return new ArrayList<>(merged.values());
    }

    /**
     * 将一批候选并入结果，并按本批命中的条款打标记。
     *
     * @param merged     按主体代码去重的结果
     * @param rows       本条款查询结果
     * @param forbidden  是否条款（一）
     * @param lowRating  是否条款（二）
     * @param restricted 是否条款（三）
     */
    private void mergeInboundHits(Map<String, IpAdjustLogBo> merged, List<IpAdjustLogBo> rows,
                                  boolean forbidden, boolean lowRating, boolean restricted) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (IpAdjustLogBo row : rows) {
            if (row == null || !StringUtils.hasText(row.getSecurityCode())) {
                continue;
            }
            IpAdjustLogBo exist = merged.get(row.getSecurityCode());
            if (exist == null) {
                exist = row;
                exist.setSecurityType("company");
                exist.setInForbiddenPool(0);
                exist.setInLowOuterRating(0);
                exist.setInRestrictedPool(0);
                merged.put(row.getSecurityCode(), exist);
            } else if (!StringUtils.hasText(exist.getOuterRating())
                    && StringUtils.hasText(row.getOuterRating())) {
                exist.setOuterRating(row.getOuterRating());
            }
            if (forbidden) {
                exist.setInForbiddenPool(1);
            }
            if (lowRating) {
                exist.setInLowOuterRating(1);
            }
            if (restricted) {
                exist.setInRestrictedPool(1);
            }
        }
    }

    /**
     * 调整原因按命中的（一）（二）（三）拼接。
     *
     * @param company 入池候选（含命中标记与孰低外评）
     * @return 调整原因
     */
    static String buildAdjustReason(IpAdjustLogBo company) {
        if (company == null) {
            return REASON;
        }
        List<String> parts = new ArrayList<>();
        // 条款（一）：当前在公司信用债禁止库
        if (isFlagHit(company.getInForbiddenPool())) {
            parts.add("公司信用债禁止库内主体");
        }
        // 条款（二）：近一年认可外评孰低为 AA-及以下
        if (isLowOuterRatingHit(company)) {
            if (StringUtils.hasText(company.getOuterRating())) {
                parts.add("近一年孰低外评：" + company.getOuterRating().trim());
            } else {
                parts.add("近一年孰低外评为AA-及以下");
            }
        }
        // 条款（三）：当前在重点观察名单
        if (isFlagHit(company.getInRestrictedPool())) {
            parts.add("重点观察名单内主体");
        }
        if (parts.isEmpty()) {
            return REASON;
        }
        StringBuilder builder = new StringBuilder(REASON);
        builder.append('（');
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append('；');
            }
            builder.append(parts.get(i));
        }
        builder.append('）');
        return builder.toString();
    }

    /**
     * 判断查询回填的命中标记是否为 1。
     *
     * @param flag 命中标记
     * @return true=命中
     */
    private static boolean isFlagHit(Integer flag) {
        return flag != null && flag.intValue() == 1;
    }

    /**
     * 判断是否命中条款（二）；查询未带回标记时，仅有外评文本则按（二）展示。
     *
     * @param company 入池候选
     * @return true=命中近一年孰低 AA-及以下
     */
    private static boolean isLowOuterRatingHit(IpAdjustLogBo company) {
        // 优先使用 SQL 回填的（二）命中标记
        if (isFlagHit(company.getInLowOuterRating())) {
            return true;
        }
        return company.getInLowOuterRating() == null
                && StringUtils.hasText(company.getOuterRating())
                && !isFlagHit(company.getInForbiddenPool())
                && !isFlagHit(company.getInRestrictedPool());
    }

    /**
     * 解析扫描池：扩展参数 poolIds 与关系配置绑定本任务的池取并集
     *
     * @param taskName 任务展示名称
     * @param detail   过程日志
     * @return 入池目标池 ID
     */
    private List<Long> resolvePoolIds(String taskName, TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        String paramJson = (conf != null && StringUtils.hasText(conf.getParamJson()))
                ? conf.getParamJson().trim() : null;
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        // 参数池与关系配置绑定池取并集
        return poolScopeHelper.resolveUnionPoolIds(paramJson, TASK_CODE, RuleType.AUTO_IN.getCode(), detail);
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
