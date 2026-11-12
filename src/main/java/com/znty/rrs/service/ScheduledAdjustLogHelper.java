package com.znty.rrs.service;

import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 定时任务调库日志文案辅助逻辑。 */
final class ScheduledAdjustLogHelper {

    /** 当前调度线程执行的任务名称，供共享业务方法记录调库来源 */
    private static final ThreadLocal<String> TASK_NAME = new ThreadLocal<>();

    /** 禁止实例化定时调库日志辅助类。 */
    private ScheduledAdjustLogHelper() {
    }

    /** 执行任务并在退出时恢复当前线程原有的任务名称 */
    static <T> T runWithTaskName(String taskName, Supplier<T> task) {
        String previousName = TASK_NAME.get();
        TASK_NAME.set(taskName);
        try {
            return task.get();
        } finally {
            if (previousName == null) {
                TASK_NAME.remove();
            } else {
                TASK_NAME.set(previousName);
            }
        }
    }

    /** 使用定时任务管理页的名称，直接执行时使用业务默认名称 */
    static String taskLabel(String defaultName) {
        String name = TASK_NAME.get();
        return "【任务名称：" + (name == null ? defaultName : name.trim()) + "】";
    }

    /** 共享业务在定时任务执行期间为原因加上任务名称前缀 */
    static String labelCurrentTask(String reason) {
        return TASK_NAME.get() == null ? reason : taskLabel(null) + reason;
    }

    /** 在基础原因后以中文括号追加非空判断信息。 */
    static String appendDetails(String reason, String... details) {
        String baseReason = trimToNull(reason);
        List<String> validDetails = new ArrayList<>();
        if (details != null) {
            for (String detail : details) {
                if (StringUtils.hasText(detail)) {
                    validDetails.add(detail.trim());
                }
            }
        }
        if (validDetails.isEmpty()) {
            return baseReason;
        }
        String detailText = "（" + String.join("；", validDetails) + "）";
        return baseReason == null ? detailText : baseReason + detailText;
    }

    /** 生成发行主体判断信息，名称与代码同时存在时均展示。 */
    static String issuerDetail(ScheduledAdjustCandidateDto item) {
        if (item == null) {
            return null;
        }
        String issuerName = trimToNull(item.getIssuerName());
        String issuerCode = trimToNull(item.getIssuerCode());
        if (issuerName != null && issuerCode != null) {
            return "发行主体：" + issuerName + "/" + issuerCode;
        }
        String issuer = issuerName != null ? issuerName : issuerCode;
        return issuer == null ? null : "发行主体：" + issuer;
    }

    /** 记录 ABS 自动联动入池时尚未确认主体的待确认状态。 */
    static String passiveAbsIdentityDetail(ScheduledAdjustCandidateDto item) {
        if (item == null || (!("abs".equals(item.getSecurityType())
                || Integer.valueOf(1).equals(item.getAbsFlag())))
                || StringUtils.hasText(item.getIssuerCode())) {
            return null;
        }
        return "ABS主体身份：尚未确认";
    }

    /** 记录自动联动的触发主体代码。 */
    static String triggerCompanyDetail(ScheduledAdjustCandidateDto item) {
        if (item == null || !StringUtils.hasText(item.getTriggerCompanyCode())) {
            return null;
        }
        return "联动触发主体：" + item.getTriggerCompanyCode().trim();
    }

    /**
     * 生成主体联动旗下 ABS 的原因片段。
     *
     * <p>仅在联动对象为 ABS 且主体身份尚未确认时生效，此时需同时记录触发主体，
     * 供研究员回溯该笔联动入池的依据；主体已确认时返回 {@code null}，由
     * {@link #appendDetails(String, String...)} 自动跳过。
     *
     * @param security           被联动的证券主数据
     * @param triggerCompanyCode 触发本次联动的主体代码
     * @return 可直接追加的原因片段，无需追加时返回 {@code null}
     */
    static String absCompanySyncDetail(SecurityInfoBo security, String triggerCompanyCode) {
        if (!CreditBondSpecialInboundRule.isAbs(security)
                || StringUtils.hasText(security.getIssuerCode())) {
            return null;
        }
        String triggerCode = trimToNull(triggerCompanyCode);
        return triggerCode == null ? "ABS主体身份：尚未确认" : "ABS主体身份：尚未确认；联动触发主体：" + triggerCode;
    }

    /** 生成池判断信息，优先展示池名称，无名称时回退到池 ID。 */
    static String poolDetail(String label, InvestmentPoolBo pool, Long poolId) {
        String poolName = pool == null ? null : trimToNull(pool.getPoolName());
        if (poolName != null) {
            return label + "：" + poolName;
        }
        return poolId == null ? null : label + "：" + poolId;
    }

    /** 将主数据 yyyyMMdd 日期转换为界面易读格式。 */
    static String dateDetail(String label, String date) {
        String value = trimToNull(date);
        if (value == null) {
            return null;
        }
        if (value.matches("\\d{8}")) {
            value = value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8);
        }
        return label + "：" + value;
    }

    /** 去除字符串首尾空白，并将空字符串转换为 {@code null}。 */
    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
