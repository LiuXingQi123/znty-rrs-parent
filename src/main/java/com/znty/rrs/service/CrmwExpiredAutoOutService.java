package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.common.enums.SecurityStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRMW到期自动出池
 *
 * 对应老 IP_RULE AdjustRuleCrmwDueOutPool，使用 CRMW 专用池状态表执行到期出池
 *
 * 执行顺序：
 * 1. 合并扩展参数和关系配置中的 CRMW 组合池
 * 2. 按凭证状态或摘牌日扫描到期的生效组合，同时标记是否命中调出限制池
 * 3. 跳过缺少凭证代码、命中调出限制或已提前出池的组合
 * 4. 软删除池状态成功后写入自动调库日志，批次按凭证和标的证券组合生成
 * 5. 汇总各目标池实际出池的组合数量
 */
@Slf4j
@Service
public class CrmwExpiredAutoOutService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "crmw_expired_auto_out";

    private static final String AUTO_ADJUSTER_ID = "0";
    private static final String AUTO_ADJUSTER_NAME = "系统";
    private static final String DEFAULT_TASK_NAME = "CRMW到期自动出池";
    private static final String PARAM_HELP =
            "参数格式：JSON 对象，例如 <code>{\"poolIds\":[18],\"maturityOffsetDays\":1}</code>；也可不填 poolIds，仅扫描投资池关系配置中绑定了本任务的池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[18]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：扫描 18（CRMW库）内已生效的 CRMW 组合，凭证状态为 D 或摘牌日早于截止日时进入候选\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（CRMW 出池扫描池）：可选；与投资池「关系配置 → 自动调出规则」中绑定本任务的池取并集后扫描\n"
                    + "maturityOffsetDays（到期日期偏移天数）：可选，省略默认 1；须为 int 范围内的整数，允许 0 和负数\n"
                    + "天数含义：按数据库当前日期减去该天数比较凭证摘牌日（不含截止当天）。1=早于昨天，0=早于今天，3=早于三天前，-1=早于明天\n"
                    + "扫描范围：扩展参数 poolIds 与投资池关系配置绑定本任务的池取并集；并集为空时本轮失败\n"
                    + "处理规则：扫描目标池内已生效的 CRMW 组合；按 maturityOffsetDays 计算截止日期，默认 1 沿用早于昨天（T-2）的口径\n"
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

    /** 返回CRMW到期自动出池任务编码 */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** 返回CRMW到期自动出池任务参数说明 */
    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 执行CRMW到期自动出池
     *
     * 执行顺序：
     * 1. 读取任务展示名称并记录任务开始
     * 2. 合并扫描池，逐池查询到期 CRMW 组合
     * 3. 对命中调出限制池或已提前出池的记录记录原因并跳过
     * 4. 对实际出池的 CRMW 写入调库日志并汇总结果
     * 5. 返回任务成功或任务级失败结果
     *
     * @return 本轮任务执行结果
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        String taskName = resolveTaskName();
        infoDetail(detail, taskName + " 开始");
        try {
            AutoOutSummary summary = doAutoOut(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = summary.buildMessage();
            infoDetail(detail, taskName + " 结束，" + message);
            return ScheduledTaskResult.success(TASK_CODE, taskName, message, summary.total, startTime, duration,
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
     * 按扫描池处理到期 CRMW 组合
     *
     * 执行顺序：
     * 1. 解析扩展参数和关系配置得到扫描池
     * 2. 读取投资池信息，准备本轮批次和提交时间
     * 3. 读取池关系配置
     * 4. 逐池按凭证状态或摘牌日查询组合，并按标的、凭证和凭证类型标记调出限制
     * 5. 跳过缺少凭证编码或命中限制的组合
     * 6. 软删可调出的组合，实际删到才写出池日志
     * 7. 汇总各池实际出池数量
     *
     * @param taskName 任务展示名称
     * @param detail 过程日志
     * @return 本轮 CRMW 到期出池汇总
     */
    private AutoOutSummary doAutoOut(String taskName, TaskDetailLog detail) {
        // 1. 读取本轮扩展参数，解析日期偏移并合并待扫描池
        String paramJson = resolveParamJson(detail);
        int maturityOffsetDays = AutoAdjustPoolScopeHelper.parseMaturityOffsetDays(paramJson);
        List<Long> poolIds = poolScopeHelper.resolveUnionPoolIds(
                paramJson, TASK_CODE, RuleType.AUTO_OUT.getCode(), detail);
        String maturityRule = "凭证摘牌日早于当前日期减去 " + maturityOffsetDays + " 天";
        infoDetail(detail, "扫描池列表 poolIds=" + poolIds);
        infoDetail(detail, "扫描条件：ip_pool_status_crmw.is_deleted=0、audit_status=20、target_pool_id IN "
                + poolIds + "、pool_type=crmw、凭证 security_type=crmw，凭证状态为 D 或" + maturityRule
                + "（数据库日期，maturityOffsetDays=" + maturityOffsetDays + "）");
        // 2. 读取投资池信息和自动调库批次时间
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        ScheduledAdjustBatchNoContext batchNoContext = new ScheduledAdjustBatchNoContext();
        Date submitTime = batchNoContext.getSubmitTime();
        // 3. 读取全量池关系，供调出限制池校验
        List<PoolRelationBo> allRelations = crmwPoolAdjustMapper.queryAllPoolRelationList();
        AutoOutSummary summary = new AutoOutSummary();
        // 4. 逐池扫描到期组合，同时标记调出限制命中结果
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                warnDetail(detail, "池[" + poolId + "]不存在，跳过");
                continue;
            }
            // 取本目标池的调出限制配置，供候选查询按 CRMW 组合匹配
            List<Long> outRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.OUT_RESTRICT.getCode(), allRelations);
            List<ScheduledAdjustCandidateDto> expiredList = autoAdjustMapper.queryCrmwPoolByExpired(
                    poolId, maturityOffsetDays, outRestrictPoolIds);
            if (expiredList == null || expiredList.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无到期 CRMW");
                continue;
            }
            int poolCount = 0;
            // 5. 跳过缺少凭证编码或命中调出限制的组合
            for (ScheduledAdjustCandidateDto item : expiredList) {
                if (item == null || !StringUtils.hasText(item.getCrmwScode())) {
                    continue;
                }
                // 使用候选查询返回的调出限制结果
                if (item.isOutRestricted()) {
                    warnDetail(detail, "CRMW[" + item.getCrmwScode() + "]当前在调出限制池中，跳过");
                    continue;
                }
                // 6. 软删仍在目标池的组合，实际删到才写出池日志
                int deleted = crmwPoolAdjustMapper.deletePoolStatusSoft(
                        item.getSecurityCode(), item.getCrmwScode(), item.getCrmwStype(), poolId);
                if (deleted == 0) {
                    warnDetail(detail, "CRMW[" + item.getCrmwScode() + "/" + item.getSecurityCode()
                            + "]软删失败（可能并发已出池），跳过");
                    continue;
                }
                // 记录实际命中的凭证到期依据
                item.setAdjustType("自动调整");
                item.setAdjustMode(AdjustMode.OUT.getCode());
                item.setTargetPoolId(poolId);
                item.setTargetPoolName(pool.getPoolName());
                item.setPoolType(pool.getPoolType());
                item.setAuditStatus(AuditStatus.APPROVED.getCode());
                item.setAdjusterId(AUTO_ADJUSTER_ID);
                item.setAdjusterName(AUTO_ADJUSTER_NAME);
                String reason = ScheduledAdjustLogHelper.appendDetails(ScheduledAdjustLogHelper.taskLabel(taskName),
                        ScheduledAdjustLogHelper.dateDetail("凭证摘牌日", item.getDelistDate()),
                        "出池口径：" + (SecurityStatus.DELISTED.getCode().equals(item.getSecurityStatus())
                                ? "凭证状态为 D（退市）" : maturityRule));
                item.setAdjustReason(reason);
                // 按 CRMW 凭证和标的证券组合生成自动调库批次号
                String batchNo = batchNoContext.resolveCrmwBatchNo(
                        item.getCrmwScode(), item.getSecurityCode(), poolId, AdjustMode.OUT.getCode());
                item.setAdjustBatchNo(batchNo);
                item.setSubmitTime(submitTime);
                crmwPoolAdjustMapper.addAdjustLog(item);
                poolCount++;
            }
            // 7. 汇总当前目标池实际出池数量
            summary.addPool(poolId, pool.getPoolName(), poolCount);
            infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 调出 " + poolCount + " 条");
        }
        infoDetail(detail, summary.buildMessage());
        return summary;
    }

    /** 本轮 CRMW 到期出池汇总 */
    private static final class AutoOutSummary {

        /** 本轮实际出池总数 */
        private int total;
        /** 按目标池保存实际出池数量 */
        private final Map<Long, PoolSummary> poolSummaries = new LinkedHashMap<>();

        /** 累加目标池出池数量 */
        private void addPool(Long poolId, String poolName, int count) {
            poolSummaries.put(poolId, new PoolSummary(poolId, poolName, count));
            total += count;
        }

        /** 构建最近执行结果文案 */
        private String buildMessage() {
            StringBuilder message = new StringBuilder("本轮共自动出池 ")
                    .append(total).append(" 条到期 CRMW 组合");
            if (!poolSummaries.isEmpty()) {
                message.append("；目标池明细：");
                int index = 0;
                for (PoolSummary pool : poolSummaries.values()) {
                    if (index++ > 0) {
                        message.append("；");
                    }
                    message.append(pool.poolName).append("(").append(pool.poolId).append(")：")
                            .append(pool.count).append(" 条 CRMW 组合");
                }
            }
            return message.toString();
        }
    }

    /** 单个目标池出池统计 */
    private static final class PoolSummary {

        /** 目标池 ID */
        private final Long poolId;
        /** 目标池名称 */
        private final String poolName;
        /** 实际出池数量 */
        private final int count;

        /** 创建目标池统计 */
        private PoolSummary(Long poolId, String poolName, int count) {
            this.poolId = poolId;
            this.poolName = poolName;
            this.count = count;
        }
    }

    /**
     * 读取本轮任务使用的扩展参数
     *
     * 执行顺序：
     * 1. 从任务配置读取 param_json 并记录过程日志
     * 2. 返回参数原文，供日期偏移和扫描池解析共用
     *
     * @param detail 过程日志
     * @return 扩展参数原文，未配置时为 null
     */
    private String resolveParamJson(TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        String paramJson = (conf != null && StringUtils.hasText(conf.getParamJson()))
                ? conf.getParamJson().trim() : null;
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        return paramJson;
    }

    /**
     * 解析扩展参数中的扫描池
     *
     * 非法 JSON 或空池列表直接返回业务错误
     *
     * @param raw 扩展参数原文
     * @param taskName 任务展示名称
     * @return 扫描池 ID 列表
     */
    List<Long> parsePoolIds(String raw, String taskName) {
        List<Long> ids = AutoAdjustPoolScopeHelper.parseOptionalPoolIds(raw);
        if (ids.isEmpty()) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[18]}");
        }
        return ids;
    }

    /** 单测入口：解析 poolIds */
    List<Long> parsePoolIds(String raw) {
        return parsePoolIds(raw, TASK_CODE);
    }

    /** 加载全部投资池，供目标池存在性和池名称校验使用 */
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

    /** 从库表读取任务展示名称，缺失时使用默认任务名称 */
    private String resolveTaskName() {
        try {
            SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
            if (conf != null && StringUtils.hasText(conf.getTaskName())) {
                return conf.getTaskName();
            }
        } catch (Exception e) {
            log.debug("读取任务名称失败: {}", TASK_CODE);
        }
        return DEFAULT_TASK_NAME;
    }

    /** 写 INFO 级过程日志并同步任务明细 */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line("INFO", line);
        }
    }

    /** 写 WARN 级过程日志并同步任务明细 */
    private void warnDetail(TaskDetailLog detail, String line) {
        log.warn(line);
        if (detail != null) {
            detail.line("WARN", line);
        }
    }
}
