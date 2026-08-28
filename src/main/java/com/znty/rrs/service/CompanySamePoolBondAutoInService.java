package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
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
 * 主体下债券自动入库任务（同池）
 * <p>
 * 对应老系统 IP_RULE type=0「主体下债券自动入库」：主体已在目标池 → 旗下 bond 大类、
 * 未到期（含到期当天）、尚未在<strong>同一池</strong>的债券自动入池；尊重池 market_codes（空不限制）；
 * 不排除临时代码已更新记录。与 {@link CompanyNewBondAutoInService}
 * （对应 AutoAdjustInNewBondToLimitPoolJob，可跨池、排除临时代码）区分。
 * </p>
 */
@Slf4j
@Service
public class CompanySamePoolBondAutoInService implements RrsScheduledTask {

    /** 任务编码（与库表 task_code 绑定） */
    public static final String TASK_CODE = "company_same_pool_bond_auto_in";

    /** 系统调库操作人 ID */
    private static final String AUTO_ADJUSTER_ID = "0";
    /** 系统调库操作人名称 */
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 自动入池原因 */
    private static final String REASON = "主体下债券自动入库";
    /** 批次号后缀 */
    private static final String BATCH_SUFFIX = "3006";
    /** JSON 解析 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 扩展参数说明
     */
    private static final String PARAM_HELP =
            "参数格式：须填写 JSON 对象，例如 <code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（单池）：<code>{\"poolIds\":[15]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：主体已在 15（债券禁止库）时，旗下符合条件且尚未在 15（债券禁止库）的债券自动调入该池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "数组写法（多池）：<code>{\"poolIds\":[15,16]}</code>\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "配置含义：分别扫描 15（债券禁止库）、16（观察池）内的主体，将其旗下符合条件的债券补充调入主体所在的同一池\n"
                    + PARAM_HELP_TOOLTIP_PREFIX + "poolIds（主体所在池 + 债券入池目标池）：必填；主体在这些池内时，旗下符合条件的债券自动入同一个池，可填写多个数字 ID\n"
                    + "处理规则：主体已在目标池时，将其旗下未到期（含到期当天）且未在同一池的债券自动入池\n"
                    + "市场规则：目标池 market_codes 为空或 [] 时不限制；有配置时债券须命中允许市场\n"
                    + "限制规则：债券已在目标池配置的调入限制池时，跳过该条记录\n"
                    + "范围说明：不排除已更新临时代码和 ABS；跨池场景请使用“在池主体旗下债券自动入池”任务\n"
                    + "参数缺失或格式错误时，本轮任务失败";

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
            int total = doAutoIn(taskName, detail);
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共自动入池 " + total + " 条债券";
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
     * 按 poolIds 将同池在池主体旗下未在池债券自动入池
     */
    private int doAutoIn(String taskName, TaskDetailLog detail) {
        List<Long> poolIds = resolvePoolIds(taskName, detail);
        infoDetail(detail, "目标池列表 poolIds=" + poolIds + "（主体与债同一池）");
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
            // 查询同池待入库债券（含市场过滤）
            List<IpAdjustLogBo> bondList = autoAdjustMapper.queryCompanyBondSamePoolForAutoIn(poolId);
            if (bondList == null || bondList.isEmpty()) {
                infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 无待入库同池债券");
                continue;
            }
            int poolCount = 0;
            for (IpAdjustLogBo bond : bondList) {
                if (bond == null || !StringUtils.hasText(bond.getSecurityCode())) {
                    continue;
                }
                // 对齐老 AdjustPoolByRule.checkSecurityInPoolRelation（关系 11 / 调入限制池）
                if (AutoAdjustRestrictHelper.isInAnyPool(
                        securityPoolAdjustMapper.querySecurityCurrentPoolIdList(bond.getSecurityCode()),
                        inRestrictPoolIds)) {
                    warnDetail(detail, "债券[" + bond.getSecurityCode() + "]当前在调入限制池中，跳过");
                    continue;
                }
                bond.setAdjustType("自动调整");
                bond.setAdjustMode(AdjustMode.IN.getCode());
                bond.setTargetPoolId(poolId);
                bond.setTargetPoolName(pool.getPoolName());
                bond.setPoolType(pool.getPoolType());
                bond.setAuditStatus(AuditStatus.APPROVED.getCode());
                bond.setAdjusterId(AUTO_ADJUSTER_ID);
                bond.setAdjusterName(AUTO_ADJUSTER_NAME);
                bond.setAdjustReason(REASON);
                bond.setAdjustAdvice(REASON);
                bond.setAdjustBatchNo(batchNo);
                bond.setSubmitTime(submitTime);
                // 写自动入池日志
                securityPoolAdjustMapper.addAdjustLog(bond);
                bond.setAdjustLogId(bond.getId());
                // 写入在池状态
                int inserted = securityPoolAdjustMapper.addPoolStatus(bond);
                if (inserted != 1) {
                    warnDetail(detail, "债券[" + bond.getSecurityCode() + "]写入池状态失败（可能并发已入池），跳过");
                    continue;
                }
                poolCount++;
                total++;
            }
            infoDetail(detail, "池[" + pool.getPoolName() + "](" + poolId + ") 入池 " + poolCount + " 条");
        }
        infoDetail(detail, "批次号 " + batchNo + "，合计入池 " + total + " 条债券");
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
