package com.znty.rrs.service;

import com.znty.rrs.common.util.AlertContextKey;
import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.schedule.CrmwRuleAlertScanDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.CrmwRuleAlertTaskMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRMW规则提醒定时任务，只负责扫描风险、维护待办及返回任务结果
 *
 * 业务示例：
 * 1. 标的债券：A 公司发行的债券，是接受信用保护的对象
 * 2. CRMW 凭证：由 B 机构创设／提供信用保护，用于保护上述标的债券
 * 3. 创设方／保护方内评：指 B 机构最新的主体内评，不是 A 公司的主体内评，也不是凭证自身的证券评级
 *
 * 本任务取 CRMW 凭证主档的发行主体代码定位 B 机构，并检查 B 机构的内评和观察名单状态
 */
@Slf4j
@Service
public class CrmwRuleAlertTaskService implements RrsScheduledTask {

    /** 定时任务编码 */
    public static final String TASK_CODE = "crmw_rule_alert";
    /** 定时任务名称 */
    public static final String TASK_NAME = "CRMW规则提醒";
    /** 任务扫描及待办生成数据访问 */
    @Resource
    private CrmwRuleAlertTaskMapper crmwRuleAlertTaskMapper;
    /** 复用主体内评档位顺序，不使用信用债准入矩阵 */
    @Resource
    private CreditBondGradeRuleMapper creditBondGradeRuleMapper;

    /**
     * 返回调度编码
     *
     * 执行顺序：
     * 1. 返回与任务配置一致的编码
     *
     * @return 任务编码
     */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /**
     * 说明扫描范围和人工处理方式
     *
     * 执行顺序：
     * 1. 返回无参数任务的业务口径
     *
     * @return 参数说明
     */
    @Override
    public String getParamHelp() {
        return "参数说明：无需扩展参数，请将 param_json 留空\n"
                + "扫描条件：ip_pool_status_crmw 中未删除、审批通过且投资池有效的 CRMW 组合\n"
                + "主体来源：凭证主档发行主体代表创设方或保护方，不使用标的债发行主体\n"
                + "评级来源：按主体代码查询 v_inv_grade_result 中 ts 最新内评，按启用档位顺序判断\n"
                + "提醒条件：内评低于3档，或主体已在观察池、重点观察名单；3档本身不触发评级提醒\n"
                + "处理方式：每天按当前状态检查，包含存量风险；只提醒，不自动出库或进入禁止库\n"
                + "沿用结论：同组合、同池、同入库日志、同主体、同内评及同原因已处理时不再提醒\n"
                + "数据错误：凭证、主体、最新内评或有效档位缺失时本轮失败并回滚，不使用旧评分兜底";
    }

    /**
     * 执行CRMW提醒任务，统一记录计时、日志和任务结果
     *
     * 执行顺序：
     * 1. 记录任务开始时间和开始日志
     * 2. 调用扫描方法生成、刷新及失效风险提醒
     * 3. 记录任务结束日志并返回成功结果
     * 4. 业务或系统异常时标记整轮事务回滚，保留过程日志并返回失败结果
     *
     * @return 本轮任务执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskResult execute() {
        // 1. 记录任务开始时间和过程日志
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        log.info("任务开始：{}", TASK_NAME);
        detail.line("任务开始：" + TASK_NAME);
        try {
            // 2. 执行CRMW在库风险扫描及待办维护
            RuleAlertSummary summary = doScanCrmwRuleAlerts(detail);
            // 3. 记录结束日志，影响条数保持为仍待处理数量
            long duration = System.currentTimeMillis() - begin;
            log.info("任务结束：{}，{}，耗时 {} 毫秒", TASK_NAME, summary.message, duration);
            detail.line("任务结束：" + summary.message + "；耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, summary.message, summary.openCount,
                    startTime, duration, detail.build());
        } catch (BizException e) {
            // 4. 业务失败时撤销本轮提醒变更并保留明确原因
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            log.warn("{}未完成：{}", TASK_NAME, e.getMessage());
            detail.line("WARN", "任务未完成（整轮回滚）：" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, e.getMessage(),
                    startTime, duration, detail.build());
        } catch (Exception e) {
            // 4. 系统异常同样撤销本轮提醒变更，并记录异常上下文
            markTransactionRollbackOnly();
            long duration = System.currentTimeMillis() - begin;
            log.error("{}异常", TASK_NAME, e);
            detail.line("ERROR", "任务未完成（整轮回滚）：系统异常，" + e.getMessage());
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, "执行异常: " + e.getMessage(),
                    startTime, duration, detail.build());
        }
    }

    /**
     * 扫描CRMW创设方风险并维护待办，不提供页面查询或人工处理
     *
     * 每天检查当前状态，覆盖已有低评级记录；只读取池状态，不执行自动调库
     *
     * 执行顺序：
     * 1. 读取启用内评档位并定位3档分界，再扫描有效CRMW在池组合
     * 2. 按本轮凭证及创设方去重，批量读取主档、最新内评、名单及最近提醒
     * 3. 逐组合复核风险，在内存中匹配依据并新增或刷新待办
     * 4. 失效本轮不再命中的待办，保存变更审计并汇总数量
     *
     * @param detail 本轮任务过程日志
     * @return 提醒数量与结果摘要
     */
    private RuleAlertSummary doScanCrmwRuleAlerts(TaskDetailLog detail) {
        detail.line("扫描条件：ip_pool_status_crmw.is_deleted=0、audit_status=20；投资池有效且pool_type=crmw");
        detail.line("评级及名单：按凭证主档发行主体查询最新内评，以及生效的主体观察池和重点观察名单");
        // 1. 按配置中的档位顺序确定3档边界，避免把档位当数字比较
        Map<String, Integer> ratingSorts = new HashMap<>();
        for (CreditBondInnerRatingGradeBo grade : creditBondGradeRuleMapper.queryEnabledRatingGradeList()) {
            ratingSorts.put(grade.getGradeCode(), grade.getSortNo());
        }
        Integer thresholdSort = ratingSorts.get("3");
        if (thresholdSort == null) {
            throw new BizException("CRMW规则提醒缺少启用的主体内评3档顺序配置");
        }
        List<CrmwRuleAlertScanDto> rows = crmwRuleAlertTaskMapper.queryCrmwInPoolList();
        // 2. 以本轮在池凭证为范围去重，主档、评级和名单不再跟随组合逐条查询
        Set<String> crmwCodeSet = new LinkedHashSet<>();
        for (CrmwRuleAlertScanDto row : rows) {
            crmwCodeSet.add(row.getCrmwScode());
        }
        List<String> crmwCodes = new ArrayList<>(crmwCodeSet);
        Map<String, CrmwRuleAlertScanDto> securities = new HashMap<>();
        Map<String, CrmwRuleAlertScanDto> issuerRatings = new HashMap<>();
        Map<String, CrmwRuleAlertScanDto> issuerWatches = new HashMap<>();
        Map<String, IpCrmwRuleAlertBo> latestAlerts = new HashMap<>();
        if (!crmwCodes.isEmpty()) {
            Set<String> issuerCodeSet = new LinkedHashSet<>();
            for (CrmwRuleAlertScanDto security : crmwRuleAlertTaskMapper.queryCrmwSecurityList(crmwCodes)) {
                securities.put(security.getCrmwScode(), security);
                if (StringUtils.hasText(security.getIssuerCode())) {
                    issuerCodeSet.add(security.getIssuerCode());
                }
            }
            List<String> issuerCodes = new ArrayList<>(issuerCodeSet);
            if (!issuerCodes.isEmpty()) {
                for (CrmwRuleAlertScanDto rating : crmwRuleAlertTaskMapper.queryLatestIssuerRatingList(issuerCodes)) {
                    issuerRatings.putIfAbsent(rating.getIssuerCode(), rating);
                }
                for (CrmwRuleAlertScanDto watch : crmwRuleAlertTaskMapper.queryIssuerWatchList(issuerCodes)) {
                    issuerWatches.put(watch.getIssuerCode(), watch);
                }
            }
            for (IpCrmwRuleAlertBo alert : crmwRuleAlertTaskMapper.queryLatestAlertList(crmwCodes)) {
                latestAlerts.put(alert.getCrmwScode() + "|" + alert.getSecurityCode() + "|" + alert.getCurrentPoolId(), alert);
            }
        }
        Date scanTime = new Date();
        int hits = 0;
        int skipped = 0;
        detail.line("扫描组合 " + rows.size() + " 条");
        for (CrmwRuleAlertScanDto row : rows) {
            // 3. 在内存中补齐本组合的创设方风险信息，不借用标的债主体或旧评分
            CrmwRuleAlertScanDto security = securities.get(row.getCrmwScode());
            row.setCrmwSecurityType(security == null ? null : security.getCrmwSecurityType());
            row.setIssuerCode(security == null ? null : security.getIssuerCode());
            row.setIssuerName(security == null ? null : security.getIssuerName());
            CrmwRuleAlertScanDto rating = issuerRatings.get(row.getIssuerCode());
            row.setInnerIssuerRating(rating == null ? null : rating.getInnerIssuerRating());
            CrmwRuleAlertScanDto watch = issuerWatches.get(row.getIssuerCode());
            row.setInObservePool(watch != null && watch.isInObservePool());
            row.setInRestrictedPool(watch != null && watch.isInRestrictedPool());
            String context = "CRMW[" + row.getCrmwScode() + "]标的[" + row.getSecurityCode()
                    + "]当前池[" + row.getCurrentPoolId() + "]";
            if (!"crmw".equals(row.getCrmwSecurityType()) || !StringUtils.hasText(row.getIssuerCode())
                    || row.getIssuerCode().contains(",")) {
                throw new BizException(context + "凭证主档或唯一创设方/保护方代码缺失或不正确");
            }
            Integer ratingSort = ratingSorts.get(row.getInnerIssuerRating());
            if (ratingSort == null) {
                throw new BizException(context + "主体[" + row.getIssuerCode()
                        + "]最新内评为空或未配置有效档位：" + row.getInnerIssuerRating());
            }
            List<String> reasons = new ArrayList<>();
            if (ratingSort > thresholdSort) {
                reasons.add("创设方/保护方主体内评低于3档，由研究员决定是否出库");
            }
            if (row.isInObservePool()) {
                reasons.add("创设方/保护方已在观察名单，由研究员决定相关组合出库或主体纳入禁止库");
            }
            if (row.isInRestrictedPool()) {
                reasons.add("创设方/保护方已在重点观察名单，由研究员决定相关组合出库或主体纳入禁止库");
            }
            if (reasons.isEmpty()) {
                continue;
            }
            row.setFailReason(String.join("；", reasons));
            row.setContextKey(AlertContextKey.of(row.getCrmwScode(), row.getSecurityCode(),
                    String.valueOf(row.getCurrentPoolId()), String.valueOf(row.getAdjustLogId()),
                    row.getIssuerCode(), row.getInnerIssuerRating(), row.getFailReason()));
            row.setLastScanTime(scanTime);
            row.setUpdtTime(scanTime);
            // 按同一组合和池匹配已批量读取的最近提醒，依据变化时重新提醒
            String combinationKey = row.getCrmwScode() + "|" + row.getSecurityCode() + "|" + row.getCurrentPoolId();
            IpCrmwRuleAlertBo latest = latestAlerts.get(combinationKey);
            if (latest != null && "20".equals(latest.getAlertStatus())
                    && row.getContextKey().equals(latest.getContextKey())) {
                skipped++;
                continue;
            }
            row.setAlertStatus("00");
            if (latest != null && "00".equals(latest.getAlertStatus())) {
                row.setId(latest.getId());
                crmwRuleAlertTaskMapper.editAlertScan(row);
                crmwRuleAlertTaskMapper.addAlertEvent(row.getId(), "0", "UPDATE");
            } else {
                row.setIsDeleted(0);
                row.setCrteTime(scanTime);
                crmwRuleAlertTaskMapper.addAlert(row);
                crmwRuleAlertTaskMapper.addAlertEvent(row.getId(), "0", "INSERT");
            }
            latestAlerts.put(combinationKey, row);
            hits++;
        }
        // 4. 只失效旧待处理提醒，已处理记录保留人工结论
        String scanTimeText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(scanTime);
        int invalid = 0;
        for (IpCrmwRuleAlertBo stale : crmwRuleAlertTaskMapper.queryStaleOpenAlertList(scanTimeText)) {
            stale.setUpdtTime(scanTime);
            invalid += crmwRuleAlertTaskMapper.editAlertInvalid(stale);
            crmwRuleAlertTaskMapper.addAlertEvent(stale.getId(), "0", "UPDATE");
        }
        int open = crmwRuleAlertTaskMapper.queryOpenAlertCount();
        String message = "扫描完成：本轮命中 " + hits + " 条，沿用已处理 " + skipped
                + " 条，本轮失效 " + invalid + " 条，仍待处理 " + open + " 条";
        RuleAlertSummary summary = new RuleAlertSummary();
        summary.message = message;
        summary.openCount = open;
        return summary;
    }

    /**
     * 任务捕获异常并返回失败结果时，撤销本轮已写入的提醒和审计
     *
     * 执行顺序：
     * 1. 将当前事务标记为回滚，避免失败结果导致部分变更被提交
     * 2. 直接调用且没有事务的单元测试只记录调试日志
     */
    private void markTransactionRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            log.debug("当前无可回滚事务: {}", TASK_CODE);
        }
    }

    /** 本轮CRMW扫描任务汇总 */
    private static final class RuleAlertSummary {
        /** 本轮扫描结果摘要 */
        private String message;
        /** 执行结束后仍待处理的提醒数量 */
        private int openCount;
    }
}
