package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动调库定时任务服务。
 *
 * <p>参考老项目 AdjustPoolByRule 意图（定时扫描规则触发自动调库，adjustType=自动调整，直接落地不走审批），
 * 按新项目架构用 Spring @Scheduled + 直接 mapper 落地实现，不照搬老项目 Quartz+IAdjustRule 反射结构。
 * 通过配置 rrs.auto-adjust.enabled=true 启用，默认关闭（避免开发环境意外触发）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rrs.auto-adjust.enabled", havingValue = "true")
public class AutoAdjustService {

    /** 自动调库操作人（系统） */
    private static final String AUTO_ADJUSTER_ID = "0";
    private static final String AUTO_ADJUSTER_NAME = "系统";
    /** 自动调出规则：到期出池 */
    private static final String REASON_EXPIRED_OUT = "证券到期自动调出";

    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /**
     * 自动调库定时入口（默认每天凌晨 2 点，cron 可配）。
     *
     * <p>依次执行各自动调库规则。当前实现到期出池，后续可按同模式扩展退市/禁投池/行业等规则。
     */
    @Scheduled(cron = "${rrs.auto-adjust.cron:0 0 2 * * ?}")
    public void executeAutoAdjust() {
        log.info("自动调库定时任务开始");
        try {
            autoOutExpired();
        } catch (Exception e) {
            log.error("自动调库定时任务异常", e);
        }
        log.info("自动调库定时任务结束");
    }

    /**
     * 规则：到期出池。
     *
     * <p>扫描配置了 auto_out 规则的启用池，将池内已到期（maturity_date 早于今日）的证券自动调出，
     * 写 ip_adjust_log（adjustType=自动调整，audit_status=20 直接生效）+ 软删除 ip_pool_status。
     * 对应老项目 AdjustRuleByExpired。
     */
    private void autoOutExpired() {
        List<Long> poolIds = autoAdjustMapper.queryAutoOutPoolIds();
        if (poolIds == null || poolIds.isEmpty()) {
            log.info("到期出池：无配置 auto_out 规则的池，跳过");
            return;
        }
        // 全量池 Map，取池名/类型
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        for (InvestmentPoolBo pool : investmentPoolMapper.queryPoolList()) {
            poolMap.put(pool.getId(), pool);
        }
        // 本轮统一批次号
        String batchNo = "AUTO" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
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
                // 构建自动调出日志（直接生效，不走审批）
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
                securityPoolAdjustMapper.addAdjustLog(sec);
                // 软删除池状态
                securityPoolAdjustMapper.deletePoolStatusSoft(sec.getSecurityCode(), poolId);
                total++;
            }
            log.info("到期出池：池[{}]({}) 调出 {} 条到期证券", pool.getPoolName(), poolId, expiredList.size());
        }
        log.info("到期出池：本轮共调出 {} 条到期证券，批次号 {}", total, batchNo);
    }
}
