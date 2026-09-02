package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
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
 * CRMW 凭证到期自动出池。
 * <p>
 * 对应老 IP_RULE {@code AdjustRuleCrmwDueOutPool}：走 AdjustPoolByRule 出池，
 * 到期宽限 T-2，并拦截调出限制池（关系 12）。落 {@code ip_pool_status_crmw}。
 * </p>
 */
@Slf4j
@Service
public class CrmwExpiredAutoOutService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "crmw_expired_auto_out";

    private static final String AUTO_ADJUSTER_ID = "0";
    private static final String AUTO_ADJUSTER_NAME = "系统";
    private static final String REASON = "CRMW到期自动调出";
    private static final String BATCH_SUFFIX = "3007";
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[18]}</code>；也可不填 poolIds，仅扫描投资池关系配置中绑定了本任务的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[18]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：扫描 18（CRMW库）内已生效的 CRMW 组合，到期后从 18（CRMW库）自动调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（CRMW 出池扫描池）：可选；与投资池「关系配置 → 自动调出规则」中绑定本任务的池取并集后扫描\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：扫描目标池内已生效的 CRMW 组合；凭证到期日早于昨天（T-2）时自动调出\n"
                    + "限制规则：当前已在目标池配置的调出限制池时，跳过该条记录\n"
                    + "执行方式：直接生效，不走审批；仅软删除成功才写日志并计入影响条数\n"
                    + "参数格式错误时，本轮任务失败";

    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    @Resource
    private CrmwPoolAdjustMapper crmwPoolAdjustMapper;
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
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
            String message = "本轮共自动出池 " + total + " 条到期 CRMW 组合";
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

    private int doAutoOut(String taskName, TaskDetailLog detail) {
        List<Long> poolIds = resolvePoolIds(taskName, detail);
        infoDetail(detail, "扫描池列表 poolIds=" + poolIds);
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        infoDetail(detail, "本轮批次号 " + batchNo);
        List<PoolRelationBo> allRelations = crmwPoolAdjustMapper.queryAllPoolRelationList();
        int total = 0;
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                warnDetail(detail, "池[" + poolId + "]不存在，跳过");
                continue;
            }
            List<Long> outRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.OUT_RESTRICT.getCode(), allRelations);
            List<IpAdjustLogBo> expiredList = autoAdjustMapper.queryCrmwPoolByExpired(poolId);
            if (expiredList == null || expiredList.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无到期 CRMW");
                continue;
            }
            int poolCount = 0;
            for (IpAdjustLogBo item : expiredList) {
                if (item == null || !StringUtils.hasText(item.getCrmwScode())) {
                    continue;
                }
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        crmwPoolAdjustMapper.querySecurityCurrentPoolIdList(
                                item.getSecurityCode(), item.getCrmwScode(), item.getCrmwStype()),
                        outRestrictPoolIds)) {
                    warnDetail(detail, "CRMW[" + item.getCrmwScode() + "]当前在调出限制池中，跳过");
                    continue;
                }
                int deleted = crmwPoolAdjustMapper.deletePoolStatusSoft(
                        item.getSecurityCode(), item.getCrmwScode(), item.getCrmwStype(), poolId);
                if (deleted == 0) {
                    warnDetail(detail, "CRMW[" + item.getCrmwScode() + "/" + item.getSecurityCode()
                            + "]软删失败（可能并发已出池），跳过");
                    continue;
                }
                item.setAdjustType("自动调整");
                item.setAdjustMode(AdjustMode.OUT.getCode());
                item.setTargetPoolId(poolId);
                item.setTargetPoolName(pool.getPoolName());
                item.setPoolType(pool.getPoolType());
                item.setAuditStatus(AuditStatus.APPROVED.getCode());
                item.setAdjusterId(AUTO_ADJUSTER_ID);
                item.setAdjusterName(AUTO_ADJUSTER_NAME);
                item.setAdjustReason(REASON);
                item.setAdjustAdvice(REASON);
                item.setAdjustBatchNo(batchNo);
                item.setSubmitTime(submitTime);
                crmwPoolAdjustMapper.addAdjustLog(item);
                poolCount++;
                total++;
            }
            infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 调出 " + poolCount + " 条");
        }
        infoDetail(detail, "本轮共调出 " + total + " 条，批次号 " + batchNo);
        return total;
    }

    private List<Long> resolvePoolIds(String taskName, TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        String paramJson = (conf != null && StringUtils.hasText(conf.getParamJson()))
                ? conf.getParamJson().trim() : null;
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        return poolScopeHelper.resolveUnionPoolIds(paramJson, TASK_CODE, RuleType.AUTO_OUT.getCode(), detail);
    }

    List<Long> parsePoolIds(String raw, String taskName) {
        List<Long> ids = AutoAdjustPoolScopeHelper.parseOptionalPoolIds(raw);
        if (ids.isEmpty()) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[18]}");
        }
        return ids;
    }

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
