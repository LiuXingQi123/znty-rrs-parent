package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
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
 * 主体下新债自动入池任务。
 *
 * <p>对应老系统 {@code AutoAdjustInNewBondToLimitPoolJob}。
 * 调度启停与 cron 由 {@code sys_scheduled_task} 管理；扩展参数 {@code param_json}
 * 存放池映射（如 {@code 15-15}，对应老配置 AUTOPOOLID_XYJJ）。
 */
@Slf4j
@Service
public class CompanyNewBondAutoInService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "company_new_bond_auto_in";
    /** 任务名称 */
    public static final String TASK_NAME = "主体下新债自动入池";

    private static final String AUTO_ADJUSTER_ID = "0";
    private static final String AUTO_ADJUSTER_NAME = "系统";
    private static final String REASON_COMPANY_NEW_BOND_IN = "主体新发债券自动导入";
    private static final String DEFAULT_POOL_PAIRS = "15-15";
    private static final String DEFAULT_CRON = "0 0 3 * * ?";
    private static final String BATCH_SUFFIX = "3002";

    /** 自动调库查询 Mapper */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 证券池调库落地 Mapper */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 投资池查询 Mapper */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 定时任务配置 Mapper（读取 param_json 池映射） */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getTaskName() {
        return TASK_NAME;
    }

    @Override
    public String getDescription() {
        return "主体已在配置池内时，将其旗下未到期且尚未在目标池的债券自动入池（对应老系统主体新发债券自动入池）";
    }

    @Override
    public String getDefaultCronExpression() {
        return DEFAULT_CRON;
    }

    @Override
    public boolean isDefaultScheduleEnabled() {
        return false;
    }

    @Override
    public String getDefaultParamJson() {
        return DEFAULT_POOL_PAIRS;
    }

    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        log.info("{} 开始", TASK_NAME);
        try {
            int total = doAutoInCompanyNewBond();
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共入池 " + total + " 条";
            log.info("{} 结束，{}", TASK_NAME, message);
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, total, startTime, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", TASK_NAME, e);
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME,
                    "执行异常: " + e.getMessage(), startTime, duration);
        }
    }

    /** 核心业务：按库表扩展参数中的池映射扫描并自动入池。 */
    private int doAutoInCompanyNewBond() {
        String pairs = resolvePoolPairs();
        List<long[]> pairList = parsePoolPairs(pairs);
        if (pairList.isEmpty()) {
            log.info("{}：未配置有效池映射，跳过", TASK_NAME);
            return 0;
        }
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap();
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        int total = 0;
        for (long[] pair : pairList) {
            Long companyPoolId = pair[0];
            Long targetPoolId = pair[1];
            InvestmentPoolBo targetPool = poolMap.get(targetPoolId);
            if (targetPool == null) {
                log.warn("{}：目标池[{}]不存在，跳过映射 {}-{}", TASK_NAME, targetPoolId, companyPoolId, targetPoolId);
                continue;
            }
            List<IpAdjustLogBo> bondList = autoAdjustMapper.queryCompanyNewBondForAutoIn(companyPoolId, targetPoolId);
            if (bondList == null || bondList.isEmpty()) {
                log.info("{}：主体池[{}]→目标池[{}]({}) 无待入池新债",
                        TASK_NAME, companyPoolId, targetPool.getPoolName(), targetPoolId);
                continue;
            }
            int pairCount = 0;
            for (IpAdjustLogBo bond : bondList) {
                bond.setAdjustType("自动调整");
                bond.setAdjustMode(AdjustMode.IN.getCode());
                bond.setTargetPoolId(targetPoolId);
                bond.setTargetPoolName(targetPool.getPoolName());
                bond.setPoolType(targetPool.getPoolType());
                bond.setAuditStatus(AuditStatus.APPROVED.getCode());
                bond.setAdjusterId(AUTO_ADJUSTER_ID);
                bond.setAdjusterName(AUTO_ADJUSTER_NAME);
                bond.setAdjustReason(REASON_COMPANY_NEW_BOND_IN);
                bond.setAdjustAdvice(REASON_COMPANY_NEW_BOND_IN);
                bond.setAdjustBatchNo(batchNo);
                bond.setSubmitTime(submitTime);
                // 写自动调入日志
                securityPoolAdjustMapper.addAdjustLog(bond);
                bond.setAdjustLogId(bond.getId());
                // 写入在池状态
                int inserted = securityPoolAdjustMapper.addPoolStatus(bond);
                if (inserted != 1) {
                    log.warn("{}：债券[{}]写入池状态失败（可能并发已入池），跳过", TASK_NAME, bond.getSecurityCode());
                    continue;
                }
                pairCount++;
                total++;
            }
            log.info("{}：主体池[{}]→目标池[{}]({}) 入池 {} 条",
                    TASK_NAME, companyPoolId, targetPool.getPoolName(), targetPoolId, pairCount);
        }
        log.info("{}：批次号 {}，合计入池 {} 条", TASK_NAME, batchNo, total);
        return total;
    }

    /** 从库表读取扩展参数（池映射），失败则用默认值。 */
    private String resolvePoolPairs() {
        try {
            SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
            if (conf != null && StringUtils.hasText(conf.getParamJson())) {
                return conf.getParamJson().trim();
            }
        } catch (Exception e) {
            log.warn("{}：读取库表扩展参数失败，使用默认 {}", TASK_NAME, DEFAULT_POOL_PAIRS, e);
        }
        return DEFAULT_POOL_PAIRS;
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

    /**
     * 解析主体池-目标池映射。格式：{@code 15-15,16-100}。
     */
    List<long[]> parsePoolPairs(String raw) {
        List<long[]> result = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return result;
        }
        String[] segments = raw.split(",");
        for (String segment : segments) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            String trimmed = segment.trim();
            String[] parts = trimmed.split("-");
            if (parts.length != 2) {
                log.warn("{}：池映射格式非法，跳过 [{}]", TASK_NAME, trimmed);
                continue;
            }
            try {
                long companyPoolId = Long.parseLong(parts[0].trim());
                long targetPoolId = Long.parseLong(parts[1].trim());
                result.add(new long[]{companyPoolId, targetPoolId});
            } catch (NumberFormatException ex) {
                log.warn("{}：池映射 ID 非法，跳过 [{}]", TASK_NAME, trimmed);
            }
        }
        return result;
    }
}
