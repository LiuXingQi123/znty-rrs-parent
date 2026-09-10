package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 定时任务调库日志文案辅助逻辑。 */
final class ScheduledAdjustLogHelper {

    /** 禁止实例化定时调库日志辅助类。 */
    private ScheduledAdjustLogHelper() {
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
