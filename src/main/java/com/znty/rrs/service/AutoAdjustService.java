package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
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
 * 到期证券自动出池任务实现
 * <p>
 * 按 param_json.poolIds 指定投资池，将池内已生效、债/股大类且到期日早于昨天（T-2，对齐老系统
 * AdjustRuleByExpired，ptype=4000/2000）的证券自动调出；CRMW 走独立任务。
 * 若证券当前在目标池的调出限制池（out_restrict）中则跳过。
 * 仅软删在池状态成功才写调出日志并计数。adjust_type=自动调整，audit_status=20，不走审批。
 * 任务名称/说明/cron/启停/扩展参数由库表 sys_scheduled_task 维护（task_code 见 {@link #TASK_CODE}）。
 * </p>
 */
@Slf4j
@Service
public class AutoAdjustService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "security_expired_auto_out";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 自动调出原因（写入调库日志） */
    private static final String REASON_EXPIRED_OUT = "证券到期自动调出";
    /** 批次号规则后缀 */
    private static final String BATCH_SUFFIX = "3001";
    /** 解析 param_json 用 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 本任务扩展参数说明（配置页按行拆成列表展示）
     */
    private static final String PARAM_HELP =
            "参数格式：须填写 JSON 对象，例如 <code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：扫描 15（债券禁止库）内已生效的债券、股票，到期后从 15（债券禁止库）自动调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多池）：<code>{\"poolIds\":[15,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：分别扫描 15（债券禁止库）、16（观察池）内的证券，到期后从各自所在池自动调出\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（到期出池扫描池）：必填；任务只扫描这些池内已生效的债券、股票，到期后从各自所在池出库，可填写多个数字 ID\n"
                    + "处理规则：扫描目标池内已生效的债券、股票；到期日早于昨天（T-2）时自动调出\n"
                    + "排除范围：主体、基金和 CRMW 不处理；CRMW 请使用“CRMW到期自动出池”任务\n"
                    + "限制规则：证券已在目标池配置的调出限制池时，跳过该条记录\n"
                    + "执行方式：直接生效，不走审批；仅软删除成功才写日志并计入影响条数\n"
                    + "参数缺失或格式错误时，本轮任务失败";

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
     * 兼容旧调用入口，内部转调 {@link #execute()}
     */
    public void executeAutoAdjust() {
        execute();
    }

    /**
     * 执行到期证券自动出池：按扩展参数 poolIds 扫描各池到期证券（T-2），
     * 跳过调出限制池阻断项，软删成功后再写调出日志
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
            // 执行到期证券自动出池（扩展参数非法时抛 BizException → 记失败）
            int total = doAutoOutExpired(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共自动出池 " + total + " 条到期证券";
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
     * 到期证券自动出池核心逻辑：从 param_json 解析 poolIds，扫描到期证券并调出
     */
    private int doAutoOutExpired(String taskName, TaskDetailLog detail) {
        // 从扩展参数解析待扫描池 ID（非法则抛业务异常）
        List<Long> poolIds = resolvePoolIds(taskName, detail);
        infoDetail(detail, "扫描池列表 poolIds=" + poolIds);
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> poolList = investmentPoolMapper.queryPoolList();
        if (poolList != null) {
            for (InvestmentPoolBo pool : poolList) {
                poolMap.put(pool.getId(), pool);
            }
        }
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
            List<Long> outRestrictPoolIds = AutoAdjustRestrictHelper.resolveRelationPoolIds(
                    poolId, RelationType.OUT_RESTRICT.getCode(), allRelations);
            List<IpAdjustLogBo> expiredList = autoAdjustMapper.queryPoolSecurityByExpired(poolId);
            if (expiredList == null || expiredList.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无到期证券");
                continue;
            }
            int poolCount = 0;
            for (IpAdjustLogBo sec : expiredList) {
                if (sec == null || !StringUtils.hasText(sec.getSecurityCode())) {
                    continue;
                }
                // 对齐老 AdjustPoolByRule.checkSecurityOutPoolRelation（关系 12 / 调出限制池）
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        securityPoolAdjustMapper.querySecurityCurrentPoolIdList(sec.getSecurityCode()),
                        outRestrictPoolIds)) {
                    warnDetail(detail, "证券[" + sec.getSecurityCode() + "]当前在调出限制池中，跳过");
                    continue;
                }
                // 先软删，未删到则视为已不在池，不写日志、不计数
                int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(sec.getSecurityCode(), poolId);
                if (deleted == 0) {
                    warnDetail(detail, "证券[" + sec.getSecurityCode() + "]软删池状态失败（可能并发已出池），跳过");
                    continue;
                }
                sec.setAdjustType("自动调整");
                sec.setAdjustMode(AdjustMode.OUT.getCode());
                sec.setTargetPoolId(poolId);
                sec.setTargetPoolName(pool.getPoolName());
                sec.setPoolType(pool.getPoolType());
                sec.setAuditStatus(AuditStatus.APPROVED.getCode());
                sec.setAdjusterId(AUTO_ADJUSTER_ID);
                sec.setAdjusterName(AUTO_ADJUSTER_NAME);
                sec.setAdjustReason(REASON_EXPIRED_OUT);
                sec.setAdjustBatchNo(batchNo);
                sec.setSubmitTime(submitTime);
                // 软删成功后再写自动调出日志
                securityPoolAdjustMapper.addAdjustLog(sec);
                poolCount++;
                total++;
            }
            infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 调出 "
                    + poolCount + " 条到期证券");
        }
        infoDetail(detail, "本轮共调出 " + total + " 条到期证券，批次号 " + batchNo);
        return total;
    }

    /**
     * 从库表 param_json 解析 poolIds（仅合法 JSON；缺失或非法抛业务异常）
     */
    private List<Long> resolvePoolIds(String taskName, TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf == null || !StringUtils.hasText(conf.getParamJson())) {
            throw new BizException("扩展参数未配置，须为 JSON，例如 {\"poolIds\":[15]}");
        }
        infoDetail(detail, "扩展参数 param_json=" + conf.getParamJson().trim());
        // 解析 JSON 扩展参数
        return parsePoolIds(conf.getParamJson().trim(), taskName);
    }

    /**
     * 解析扩展参数 JSON 为池 ID 列表，格式：{"poolIds":[10,15]}
     * <p>非法 JSON、缺 poolIds、无有效 ID 均抛 {@link BizException}（由 execute 记失败）。
     */
    List<Long> parsePoolIds(String raw, String taskName) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException("扩展参数未配置，须为 JSON，例如 {\"poolIds\":[15]}");
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            throw new BizException("扩展参数仅支持 JSON 对象，示例 {\"poolIds\":[15]}，当前: " + text);
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            log.warn("{}：JSON 扩展参数解析失败: {}，原因: {}", taskName, text, e.getMessage());
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage()
                    + "；请使用标准 JSON，数字勿加单引号，示例 {\"poolIds\":[15]}");
        }
        JsonNode poolIds = root.get("poolIds");
        if (poolIds == null || !poolIds.isArray() || poolIds.size() == 0) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[15]}");
        }
        List<Long> result = new ArrayList<>();
        for (JsonNode item : poolIds) {
            Long id = readPoolId(item);
            if (id == null) {
                throw new BizException("poolIds 元素须为数字，非法值: " + item
                        + "；正确示例 {\"poolIds\":[15]}（勿写 '15' 单引号）");
            }
            result.add(id);
        }
        return result;
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
     * 单测入口：解析 poolIds
     */
    List<Long> parsePoolIds(String raw) {
        return parsePoolIds(raw, TASK_CODE);
    }

    /**
     * 读取 poolIds 数组中的单个 ID（仅数字节点）
     */
    private Long readPoolId(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isNumber()) {
            return item.asLong();
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
}
