package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 信用债剩余期限，换算为年供期限档匹配。
 *
 * <p>单位：{@code date_exists} 是天；含权债剩余期限、赎回行权剩余期限、回购剩余期限是年。
 * 普通债把 {@code date_exists} ÷365。含权债回售用已是年的行权期限
 * （{@code dateInrightExists} / {@code dateRepurchaseExists}），
 * 赎回把 {@code date_exists} ÷365，两者都有取更短。不做起止日推算。</p>
 */
public final class CreditBondRemainTermUtil {

    /** 年换算基准天数（仅用于 date_exists） */
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    /** 工具类禁止实例化 */
    private CreditBondRemainTermUtil() {
    }

    /**
     * 取证券剩余期限年数，供 {@code matchTermBucket} 使用。
     *
     * <p>普通债：{@code dateExists}（天）÷365。
     * 含权债：回售按 {@code dateInrightExists}/{@code dateRepurchaseExists}（年）；
     * 赎回按 {@code dateExists}（天÷365）；两者都有取更短。</p>
     *
     * @param sec 证券主数据
     * @return 剩余期限年数；无法解析时返回 null（由 {@link #matchTermBucket} 按最长档兜底）
     */
    public static BigDecimal resolveRemainTermYears(SecurityInfoBo sec) {
        if (sec == null) {
            return null;
        }
        if (CreditBondSpecialInboundRule.isInright(sec)) {
            // 回售行权期限已经是年，不再 ÷365
            BigDecimal putYears = firstYears(sec.getDateInrightExists(), sec.getDateRepurchaseExists());
            // 赎回按到期剩余期限：date_exists 是天
            BigDecimal callYears = daysToYears(sec.getDateExists());
            if (putYears != null && callYears != null) {
                return putYears.min(callYears);
            }
            if (putYears != null) {
                return putYears;
            }
            return callYears;
        }
        return daysToYears(sec.getDateExists());
    }

    /**
     * 取到期剩余期限天数（仅 {@code date_exists}，单位天）。
     *
     * @param sec 证券主数据
     * @return 天数；无法解析时返回 null
     */
    public static BigDecimal resolveRemainTermDays(SecurityInfoBo sec) {
        if (sec == null) {
            return null;
        }
        return sec.getDateExists();
    }

    /**
     * 取第一个非空期限（年），负值按 0。
     *
     * @param first  优先值（年）
     * @param second 回退值（年）
     * @return 年数
     */
    private static BigDecimal firstYears(BigDecimal first, BigDecimal second) {
        if (first != null) {
            return clampNonNegative(first);
        }
        if (second != null) {
            return clampNonNegative(second);
        }
        return null;
    }

    /**
     * 年数为负时按 0。
     *
     * @param years 年数
     * @return 非负年数
     */
    private static BigDecimal clampNonNegative(BigDecimal years) {
        if (years.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return years;
    }

    /**
     * 剩余期限天数 → 年（保留 6 位小数，便于落入 term_bucket 区间边界）。
     *
     * @param remainDays 剩余期限天数（可为 0；负值按 0 处理）
     * @return 年数；入参 null 时返回 null
     */
    public static BigDecimal daysToYears(BigDecimal remainDays) {
        if (remainDays == null) {
            return null;
        }
        BigDecimal days = remainDays.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remainDays;
        return days.divide(DAYS_PER_YEAR, 6, RoundingMode.HALF_UP);
    }

    /**
     * 兼容 Integer 入参（测试或过渡调用）。
     *
     * @param remainDays 剩余期限天数
     * @return 年数；入参 null 时返回 null
     */
    public static BigDecimal daysToYears(Integer remainDays) {
        if (remainDays == null) {
            return null;
        }
        // 转成 BigDecimal 后走统一换算
        return daysToYears(BigDecimal.valueOf(remainDays.longValue()));
    }

    /**
     * 按剩余期限（年）匹配期限档。年数为 null 时默认最长档（无上限或下限最高，对应期限>5）。
     *
     * @param remainTermYears 剩余期限年数，null 表示无法解析
     * @param buckets         启用的期限档
     * @return 期限档编码；无可用档或年数落不进任何档时返回 null
     */
    public static String matchTermBucket(BigDecimal remainTermYears, List<CreditBondTermBucketBo> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return null;
        }
        if (remainTermYears == null) {
            // 期限为空时按期限>5 的最长档继续匹配，不跳过矩阵
            return pickLongestTermBucket(buckets);
        }
        for (CreditBondTermBucketBo bucket : buckets) {
            if (bucket == null || bucket.getBucketCode() == null || bucket.getBucketCode().isEmpty()) {
                continue;
            }
            if (inTermRange(remainTermYears, bucket)) {
                return bucket.getBucketCode();
            }
        }
        return null;
    }

    /**
     * 是否落入期限档区间（含 inclusive 标志）。
     */
    private static boolean inTermRange(BigDecimal years, CreditBondTermBucketBo bucket) {
        boolean minOk = bucket.getMinTermYear() == null
                || (bucket.getMinInclusive() != null && bucket.getMinInclusive() == 1
                    ? years.compareTo(bucket.getMinTermYear()) >= 0
                    : years.compareTo(bucket.getMinTermYear()) > 0);
        boolean maxOk = bucket.getMaxTermYear() == null
                || (bucket.getMaxInclusive() != null && bucket.getMaxInclusive() == 1
                    ? years.compareTo(bucket.getMaxTermYear()) <= 0
                    : years.compareTo(bucket.getMaxTermYear()) < 0);
        return minOk && maxOk;
    }

    /**
     * 取最长期限档：优先无上限（max 为空），同为无上限时取下限更高者。
     */
    private static String pickLongestTermBucket(List<CreditBondTermBucketBo> buckets) {
        CreditBondTermBucketBo best = null;
        for (CreditBondTermBucketBo bucket : buckets) {
            if (bucket == null || bucket.getBucketCode() == null || bucket.getBucketCode().isEmpty()) {
                continue;
            }
            if (best == null) {
                best = bucket;
                continue;
            }
            boolean bucketOpen = bucket.getMaxTermYear() == null;
            boolean bestOpen = best.getMaxTermYear() == null;
            if (bucketOpen && !bestOpen) {
                best = bucket;
                continue;
            }
            if (bucketOpen == bestOpen) {
                BigDecimal bucketMin = bucket.getMinTermYear() == null ? BigDecimal.ZERO : bucket.getMinTermYear();
                BigDecimal bestMin = best.getMinTermYear() == null ? BigDecimal.ZERO : best.getMinTermYear();
                if (bucketMin.compareTo(bestMin) > 0) {
                    best = bucket;
                }
            }
        }
        return best == null ? null : best.getBucketCode();
    }
}
