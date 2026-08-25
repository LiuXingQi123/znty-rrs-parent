package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 信用债剩余期限：读取 {@code rrs_securityinfo.date_exists}（天，DECIMAL）并换算为年。
 *
 * <p>剩余期限由外部数据预计算写入 {@code date_exists}，本工具不做起止日推算。
 * 矩阵期限档 {@code credit_bond_term_bucket} 按「年」配置，匹配时用天数 / 365 换算。
 */
public final class CreditBondRemainTermUtil {

    /** 年换算基准天数（与简易/白名单流程剩余天数展示口径一致） */
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    /** 工具类禁止实例化 */
    private CreditBondRemainTermUtil() {
    }

    /**
     * 取证券剩余期限年数，供 {@code matchTermBucket} 使用。
     *
     * <p>普通债读 {@code dateExists}。含权债：回售按行权期限
     * （{@code dateInrightExists} / {@code dateRepurchaseExists}），赎回按到期剩余期限
     * （{@code dateExists}）；两者都有时取更短。</p>
     *
     * @param sec 证券主数据
     * @return 剩余期限年数；无法解析时返回 null（由 {@link #matchTermBucket} 按最长档兜底）
     */
    public static BigDecimal resolveRemainTermYears(SecurityInfoBo sec) {
        // 先按含权口径取天数，再换算为年供期限档匹配
        return daysToYears(resolveRemainTermDays(sec));
    }

    /**
     * 取矩阵用剩余期限天数。含权口径见 {@link #resolveRemainTermYears}。
     *
     * @param sec 证券主数据
     * @return 天数；无法解析时返回 null
     */
    public static BigDecimal resolveRemainTermDays(SecurityInfoBo sec) {
        if (sec == null) {
            return null;
        }
        if (CreditBondSpecialInboundRule.isInright(sec)) {
            // 回售行权期限：含权剩余天数优先，否则回购剩余天数
            BigDecimal putDays = firstPositiveOrRaw(sec.getDateInrightExists(), sec.getDateRepurchaseExists());
            BigDecimal maturityDays = sec.getDateExists();
            if (putDays != null && maturityDays != null) {
                return putDays.min(maturityDays);
            }
            if (putDays != null) {
                return putDays;
            }
            return maturityDays;
        }
        return sec.getDateExists();
    }

    /**
     * 取第一个非空天数。
     *
     * @param first  优先值
     * @param second 回退值
     * @return 第一个非空天数
     */
    private static BigDecimal firstPositiveOrRaw(BigDecimal first, BigDecimal second) {
        if (first != null) {
            return first;
        }
        return second;
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
