package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.SecurityInfoBo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 信用债剩余期限：读取 {@code rrs_securityinfo.date_exists}（天）并换算为年。
 *
 * <p>剩余期限由外部数据预计算写入 {@code date_exists}，本工具不做起止日推算。
 * 矩阵期限档 {@code credit_bond_term_bucket} 按「年」配置，匹配时用天数 / 365 换算。
 */
public final class CreditBondRemainTermUtil {

    /** 年换算基准天数（与简易/白名单流程剩余天数展示口径一致） */
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    private CreditBondRemainTermUtil() {
    }

    /**
     * 取证券剩余期限年数，供 {@code matchTermBucket} 使用。
     *
     * @param sec 证券主数据（读 {@code dateExists} 天数）
     * @return 剩余期限年数；sec 或 dateExists 为空时返回 null（跳过矩阵期限档）
     */
    public static BigDecimal resolveRemainTermYears(SecurityInfoBo sec) {
        if (sec == null) {
            return null;
        }
        return daysToYears(sec.getDateExists());
    }

    /**
     * 剩余期限天数 → 年（保留 6 位小数，便于落入 term_bucket 区间边界）。
     *
     * @param remainDays 剩余期限天数（可为 0；负值按 0 处理）
     * @return 年数；入参 null 时返回 null
     */
    public static BigDecimal daysToYears(Integer remainDays) {
        if (remainDays == null) {
            return null;
        }
        int days = remainDays < 0 ? 0 : remainDays;
        return BigDecimal.valueOf(days).divide(DAYS_PER_YEAR, 6, RoundingMode.HALF_UP);
    }
}
