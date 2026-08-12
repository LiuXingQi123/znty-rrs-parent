package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
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
 * 扫描 Wind 主体最新外评<strong>不在</strong> AA-/A/BBB… 列表内（如 AA/AA+/AAA）、
 * 且当前已在目标池的主体，自动调出 param_json.poolIds 指定池；
 * adjust_type=自动调整，不走审批。仅处理 security_type=company，不同步旗下债。
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
            "须填写 JSON 对象\n"
                    + "示例 <code>{\"poolIds\":[15]}</code> 或 <code>{\"poolIds\":[15,16]}</code>\n"
                    + "作用：扫描 Wind 主体最新外评<strong>不在</strong> AA-/A/BBB… 列表内（如 AA/AA+/AAA）的主体，"
                    + "对 poolIds 中每个目标池：主体已生效在池则自动调出（security_type=company）\n"
                    + "与入池任务 company_outer_rating_aa_minus_auto_in 评级列表互为补集\n"
                    + "poolIds 必填，至少一个数字 ID（对应老系统池上勾选该自动调出规则）\n"
                    + "未配置或格式错误则本轮失败";

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
            String message = "本轮共自动出池 " + total + " 个主体";
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
     * 按 poolIds 将外评非 AA- 及以下且已在池的主体自动出池
     */
    private int doAutoOut(String taskName, TaskDetailLog detail) {
        List<Long> poolIds = resolvePoolIds(taskName, detail);
        infoDetail(detail, "目标池列表 poolIds=" + poolIds);
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        infoDetail(detail, "本轮批次号 " + batchNo);
        int total = 0;
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                warnDetail(detail, "池[" + poolId + "]不存在，跳过");
                continue;
            }
            // 查询在池且外评已高于 AA- 及以下列表的主体
            List<IpAdjustLogBo> companies = autoAdjustMapper.queryCompanyByNotLowOuterRatingInPool(poolId);
            if (companies == null || companies.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无待出池高外评主体");
                continue;
            }
            int poolCount = 0;
            for (IpAdjustLogBo company : companies) {
                if (company == null || !StringUtils.hasText(company.getSecurityCode())) {
                    continue;
                }
                company.setSecurityType("company");
                company.setAdjustType("自动调整");
                company.setAdjustMode(AdjustMode.OUT.getCode());
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
                // 写自动出池日志
                securityPoolAdjustMapper.addAdjustLog(company);
                // 软删除在池状态
                int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(company.getSecurityCode(), poolId);
                if (deleted != 1) {
                    warnDetail(detail, "主体[" + company.getSecurityCode() + "]软删池状态失败（可能并发已出池），跳过计数");
                    continue;
                }
                poolCount++;
                total++;
            }
            infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 出池 " + poolCount + " 个主体");
        }
        infoDetail(detail, "批次号 " + batchNo + "，合计出池 " + total + " 个主体");
        return total;
    }

    /**
     * 从库表 param_json 解析 poolIds
     */
    private List<Long> resolvePoolIds(String taskName, TaskDetailLog detail) {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf == null || !StringUtils.hasText(conf.getParamJson())) {
            throw new BizException("扩展参数未配置，须为 JSON，例如 {\"poolIds\":[15]}");
        }
        infoDetail(detail, "扩展参数 param_json=" + conf.getParamJson().trim());
        return parsePoolIds(conf.getParamJson().trim(), taskName);
    }

    /**
     * 解析 {"poolIds":[15,16]}（包内可测）
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
                    + "；请使用标准 JSON，示例 {\"poolIds\":[15]}");
        }
        JsonNode poolIds = root.get("poolIds");
        if (poolIds == null || !poolIds.isArray() || poolIds.size() == 0) {
            throw new BizException("扩展参数须包含非空 poolIds 数组，示例 {\"poolIds\":[15]}");
        }
        List<Long> result = new ArrayList<>();
        for (JsonNode item : poolIds) {
            if (item == null || item.isNull() || !item.isNumber()) {
                throw new BizException("poolIds 元素须为数字，非法值: " + item
                        + "；正确示例 {\"poolIds\":[15]}");
            }
            result.add(item.asLong());
        }
        return result;
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
