package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 信用债期限解析与期限档匹配工具。
 *
 * <p>普通债和赎回口径从 {@code date_exists_str} 解析年、月、日，不使用
 * {@code date_exists} 天数换算。含权债回售使用已是年的行权期限
 * （{@code dateInrightExists} / {@code dateRepurchaseExists}），
 * 回售与赎回期限都有时取更短。</p>
 */
public final class CreditBondRemainTermUtil {

    /** 每年月份数 */
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");
    /** 每年天数，仅用于 date_exists_str 中不足一年的“天”部分 */
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    /** 期限计算保留位数 */
    private static final int TERM_SCALE = 10;
    /** 证券期限格式：年、月（“月”或“个月”）、日（“天”或“日”）均为可选，但至少应包含一项 */
    private static final Pattern TERM_PATTERN = Pattern.compile(
            "^(?:(\\d+(?:\\.\\d+)?)年)?(?:(\\d+(?:\\.\\d+)?)个?月)?(?:(\\d+(?:\\.\\d+)?)[天日])?$");

    /** 工具类禁止实例化 */
    private CreditBondRemainTermUtil() {
    }

    /**
     * 取证券剩余期限年数，供 {@code matchTermBucket} 使用。
     *
     * <p>普通债：解析 {@code dateExistsStr} 中的年、月、天。
     * 含权债：回售按 {@code dateInrightExists}/{@code dateRepurchaseExists}（年）；
     * 赎回按 {@code dateExistsStr}；两者都有取更短。</p>
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
            // 赎回按 date_exists_str 解析到期剩余期限
            BigDecimal callYears = parseRemainTermYears(sec.getDateExistsStr());
            if (putYears != null && callYears != null) {
                return putYears.min(callYears);
            }
            if (putYears != null) {
                return putYears;
            }
            return callYears;
        }
        return parseRemainTermYears(sec.getDateExistsStr());
    }

    /**
     * 将证券期限文本解析为年数。
     *
     * <p>支持“3年6个月3天”“3年6月3日”“6个月3天”“6月3日”“6天”“6日”等格式。
     * 年部分直接保留，月部分按 12 个月折算，天部分只折算文本中的不足一年部分，
     * 不再使用 {@code date_exists} 总天数除以 365。</p>
     *
     * @param termText 证券期限文本
     * @return 期限年数；为空或格式不正确时返回 null
     */
    public static BigDecimal parseRemainTermYears(String termText) {
        if (termText == null || termText.trim().isEmpty()) {
            return null;
        }
        String normalized = termText.replaceAll("\\s+", "");
        Matcher matcher = TERM_PATTERN.matcher(normalized);
        if (!matcher.matches()
                || (matcher.group(1) == null && matcher.group(2) == null && matcher.group(3) == null)) {
            return null;
        }
        BigDecimal years = parseNumber(matcher.group(1));
        BigDecimal months = parseNumber(matcher.group(2));
        BigDecimal days = parseNumber(matcher.group(3));
        return years
                .add(months.divide(MONTHS_PER_YEAR, TERM_SCALE, RoundingMode.HALF_UP))
                .add(days.divide(DAYS_PER_YEAR, TERM_SCALE, RoundingMode.HALF_UP));
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

    /** 将可空数字文本转换为 BigDecimal。 */
    private static BigDecimal parseNumber(String value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
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
