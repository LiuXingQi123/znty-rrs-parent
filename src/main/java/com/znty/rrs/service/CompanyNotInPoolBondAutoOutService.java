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
 * 主体不在指定池时，将其旗下已在债券池的债自动调出。
 * <p>
 * 对应老 {@code AutoAdjustInLimitPoolToNewBondJob}（独立 Job，不走 AdjustPoolByRule，
 * 因此不看调入/调出限制池）。老配置 AUTOPOOLID_BPMP 为「债券池-主体池」。
 * 老 Job 只排 CRMW；新系统同时排除 ABS，避免绕过禁投 ABS 独立链路。
 * </p>
 */
@Slf4j
@Service
public class CompanyNotInPoolBondAutoOutService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "company_not_in_pool_bond_auto_out";

    private static final String AUTO_ADJUSTER_ID = "0";
    private static final String AUTO_ADJUSTER_NAME = "系统";
    private static final String REASON = "债券主体不在池债券出池";
    private static final String BATCH_SUFFIX = "3008";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PARAM_HELP =
            "须填写 JSON 对象；poolIds 与 mappings 可组合，至少解析出一组映射\n"
                    + "同池示例 <code>{\"poolIds\":[15]}</code>（债券池与主体池相同）\n"
                    + "跨池示例 <code>{\"mappings\":[{\"bondPoolId\":15,\"companyPoolId\":15}]}</code>\n"
                    + "作用：债已在债券池、发行主体不在对应主体池 → 将该债从债券池自动调出\n"
                    + "排除：ABS / CRMW（ABS 走禁投独立链路，不是老 Job 原样）\n"
                    + "不看调出限制池；仅软删成功才写日志并计数\n"
                    + "未配置或格式错误则本轮失败";

    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
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
            String message = "本轮共自动出池 " + total + " 条债券";
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
        String paramJson = resolveParamJson();
        infoDetail(detail, "扩展参数 param_json=" + (paramJson == null ? "" : paramJson));
        List<long[]> pairList = parseParamMappings(paramJson, taskName);
        infoDetail(detail, "有效映射组数 " + pairList.size());
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        infoDetail(detail, "本轮批次号 " + batchNo);
        int total = 0;
        for (long[] pair : pairList) {
            Long bondPoolId = pair[0];
            Long companyPoolId = pair[1];
            InvestmentPoolBo bondPool = poolMap.get(bondPoolId);
            if (bondPool == null) {
                warnDetail(detail, "债券池[" + bondPoolId + "]不存在，跳过映射 "
                        + bondPoolId + "←主体池" + companyPoolId);
                continue;
            }
            List<IpAdjustLogBo> bonds = autoAdjustMapper.queryBondInPoolWhenCompanyNotIn(
                    bondPoolId, companyPoolId);
            if (bonds == null || bonds.isEmpty()) {
                infoDetail(detail, "债券池[" + bondPool.getPoolName() + "](" + bondPoolId
                        + ") 无「主体不在池" + companyPoolId + "」待出债");
                continue;
            }
            int pairCount = 0;
            for (IpAdjustLogBo bond : bonds) {
                if (bond == null || !StringUtils.hasText(bond.getSecurityCode())) {
                    continue;
                }
                int deleted = securityPoolAdjustMapper.deletePoolStatusSoft(bond.getSecurityCode(), bondPoolId);
                if (deleted == 0) {
                    warnDetail(detail, "债券[" + bond.getSecurityCode() + "]软删失败（可能并发已出池），跳过");
                    continue;
                }
                bond.setAdjustType("自动调整");
                bond.setAdjustMode(AdjustMode.OUT.getCode());
                bond.setTargetPoolId(bondPoolId);
                bond.setTargetPoolName(bondPool.getPoolName());
                bond.setPoolType(bondPool.getPoolType());
                bond.setAuditStatus(AuditStatus.APPROVED.getCode());
                bond.setAdjusterId(AUTO_ADJUSTER_ID);
                bond.setAdjusterName(AUTO_ADJUSTER_NAME);
                bond.setAdjustReason(REASON);
                bond.setAdjustAdvice(REASON);
                bond.setAdjustBatchNo(batchNo);
                bond.setSubmitTime(submitTime);
                securityPoolAdjustMapper.addAdjustLog(bond);
                pairCount++;
                total++;
            }
            infoDetail(detail, "债券池[" + bondPool.getPoolName() + "](" + bondPoolId
                    + ")←主体池[" + companyPoolId + "] 出池 " + pairCount + " 条");
        }
        infoDetail(detail, "批次号 " + batchNo + "，合计出池 " + total + " 条");
        return total;
    }

    private String resolveParamJson() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf != null && StringUtils.hasText(conf.getParamJson())) {
            return conf.getParamJson().trim();
        }
        return null;
    }

    /**
     * 解析为 [bondPoolId, companyPoolId]；poolIds 表示两池相同。
     */
    List<long[]> parseParamMappings(String raw, String taskName) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException("扩展参数未配置，须为 JSON，例如 {\"poolIds\":[15]}");
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            throw new BizException("扩展参数须为 JSON 对象，示例 {\"poolIds\":[15]}，当前: " + text);
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage());
        }
        List<long[]> result = new ArrayList<>();
        JsonNode poolIdsNode = root.get("poolIds");
        if (poolIdsNode != null && !poolIdsNode.isNull()) {
            if (!poolIdsNode.isArray() || poolIdsNode.size() == 0) {
                throw new BizException("poolIds 须为非空数字数组，示例 {\"poolIds\":[15]}");
            }
            for (JsonNode item : poolIdsNode) {
                if (item == null || item.isNull() || !item.isNumber()) {
                    throw new BizException("poolIds 元素须为数字，非法值: " + item);
                }
                long id = item.asLong();
                result.add(new long[]{id, id});
            }
        }
        JsonNode mappings = root.get("mappings");
        if (mappings != null && mappings.isArray()) {
            for (JsonNode item : mappings) {
                if (item == null || item.isNull()) {
                    continue;
                }
                JsonNode bondNode = item.get("bondPoolId");
                JsonNode companyNode = item.get("companyPoolId");
                if (bondNode == null || !bondNode.isNumber() || companyNode == null || !companyNode.isNumber()) {
                    throw new BizException("mappings 项须含 bondPoolId、companyPoolId 数字，非法项: " + item);
                }
                result.add(new long[]{bondNode.asLong(), companyNode.asLong()});
            }
        }
        if (result.isEmpty()) {
            throw new BizException("扩展参数未解析到有效映射，示例 {\"poolIds\":[15]} 或 "
                    + "{\"mappings\":[{\"bondPoolId\":15,\"companyPoolId\":15}]}");
        }
        return result;
    }

    List<long[]> parseParamMappings(String raw) {
        return parseParamMappings(raw, TASK_CODE);
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
