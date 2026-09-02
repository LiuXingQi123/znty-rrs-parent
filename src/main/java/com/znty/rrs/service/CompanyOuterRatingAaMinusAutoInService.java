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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外评 AA- 及以下主体自动入池任务
 * <p>
 * 对应老系统 {@code AdjustRuleInAA}（配置名：自动入外部评级 AA- 及以下的主体）。
 * 扫描 Wind 主体有效外评（近 12 个月取档位最高，更早取日期最新，再取更近一条）
 * 落在 AA-/A/BBB… 列表内、且尚未在目标池的主体，自动调入
 * param_json.poolIds 指定池；adjust_type=自动调整，不走审批。
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
     * 扩展参数说明
     */
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[15]}</code>；也可不填 poolIds，仅扫描投资池关系配置中绑定了本任务的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：外评为 AA-及以下、尚未在 15（债券禁止库）的主体，自动调入 15（债券禁止库）\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多池）：<code>{\"poolIds\":[15,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：外评为 AA-及以下的主体，分别补充调入 15（债券禁止库）、16（观察池）中尚未在池的目标池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体入池目标池）：可选；与投资池「关系配置 → 自动调入规则」中绑定本任务的池取并集后扫描\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：有效外评落在 AA-、A、BBB、BB、B、CCC/CC/C 等名单内，且尚未在目标池的主体自动入池\n"
                    + "评级口径：近 12 个月取评级档位最高，更早评级取日期最新，再选取日期更近的有效外评\n"
                    + "限制规则：主体已在目标池配置的调入限制池时，跳过该条记录\n"
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
            int total = doAutoIn(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共自动入池 " + total + " 个主体";
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
     * 按 poolIds 将外评 AA- 及以下且未在池的主体自动入池
     */
    private int doAutoIn(String taskName, TaskDetailLog detail) {
        List<Long> poolIds = resolvePoolIds(taskName, detail);
        infoDetail(detail, "目标池列表 poolIds=" + poolIds);
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
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
            List<Long> inRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.IN_RESTRICT.getCode(), allRelations);
            List<IpAdjustLogBo> companies = autoAdjustMapper.queryCompanyByLowOuterRatingNotInPool(poolId);
            if (companies == null || companies.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无待入池低外评主体");
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
                company.setAdjustReason(REASON);
                company.setAdjustAdvice(REASON);
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
     * 解析扫描池：扩展参数 poolIds 与关系配置绑定本任务的池取并集
     */
    private List<Long> resolvePoolIds(String taskName, TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        String paramJson = (conf != null && StringUtils.hasText(conf.getParamJson()))
                ? conf.getParamJson().trim() : null;
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        return poolScopeHelper.resolveUnionPoolIds(paramJson, TASK_CODE, RuleType.AUTO_IN.getCode(), detail);
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
