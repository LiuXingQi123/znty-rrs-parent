package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 到期出池自动调库任务。
 *
 * <p>参考老项目 AdjustRuleByExpired。调度启停与 cron 由 {@code sys_scheduled_task} 持久化，
 * 经 {@link ScheduledTaskService} 动态挂载；本类只实现业务。
 */
@Slf4j
@Service
public class AutoAdjustService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "auto_out_expired";
    /** 任务名称 */
    public static final String TASK_NAME = "到期出池";

    private static final String AUTO_ADJUSTER_ID = "0";
    private static final String AUTO_ADJUSTER_NAME = "系统";
    private static final String REASON_EXPIRED_OUT = "证券到期自动调出";
    private static final String BATCH_SUFFIX = "3001";
    private static final String DEFAULT_CRON = "0 0 2 * * ?";

    /** 自动调库查询 Mapper */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 证券池调库落地 Mapper */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 投资池查询 Mapper */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

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
        return "扫描配置了 auto_out 规则的启用池，将池内已到期证券自动调出";
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
        return null;
    }

    /**
     * 兼容旧调用入口。
     */
    public void executeAutoAdjust() {
        execute();
    }

    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        log.info("{} 开始", TASK_NAME);
        try {
            int total = doAutoOutExpired();
            long duration = System.currentTimeMillis() - begin;
            String message = "本轮共调出 " + total + " 条到期证券";
            log.info("{} 结束，{}", TASK_NAME, message);
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, total, startTime, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - begin;
            log.error("{} 异常", TASK_NAME, e);
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME,
                    "执行异常: " + e.getMessage(), startTime, duration);
        }
    }

    /** 到期出池核心逻辑。 */
    private int doAutoOutExpired() {
        List<Long> poolIds = autoAdjustMapper.queryAutoOutPoolIds();
        if (poolIds == null || poolIds.isEmpty()) {
            log.info("到期出池：无配置 auto_out 规则的池，跳过");
            return 0;
        }
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> poolList = investmentPoolMapper.queryPoolList();
        if (poolList != null) {
            for (InvestmentPoolBo pool : poolList) {
                poolMap.put(pool.getId(), pool);
            }
        }
        Date submitTime = new Date();
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime) + BATCH_SUFFIX;
        int total = 0;
        for (Long poolId : poolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                continue;
            }
            List<IpAdjustLogBo> expiredList = autoAdjustMapper.queryPoolSecurityByExpired(poolId);
            if (expiredList == null || expiredList.isEmpty()) {
                continue;
            }
            for (IpAdjustLogBo sec : expiredList) {
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
                // 写自动调出日志
                securityPoolAdjustMapper.addAdjustLog(sec);
                // 软删除池状态
                securityPoolAdjustMapper.deletePoolStatusSoft(sec.getSecurityCode(), poolId);
                total++;
            }
            log.info("到期出池：池[{}]({}) 调出 {} 条到期证券", pool.getPoolName(), poolId, expiredList.size());
        }
        log.info("到期出池：本轮共调出 {} 条到期证券，批次号 {}", total, batchNo);
        return total;
    }
}
