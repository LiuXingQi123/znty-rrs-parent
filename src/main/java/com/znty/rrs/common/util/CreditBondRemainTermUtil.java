package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 信用债剩余期限与期限档匹配工具
 *
 * 普通债和赎回期限取证券期限文本 {@code date_exists_str}，含权债还会比较以年为单位的回售期限
 * 主档的总天数字段 {@code date_exists} 保留在数据和快照中，年数计算以期限文本和回售年数为准
 */
public final class CreditBondRemainTermUtil {

    /** 每年月份数 */
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

    /** 每年天数，专用于折算证券期限文本中的天数部分 */
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    /** 月数和天数折算成年数时保留的小数位数 */
    private static final int TERM_SCALE = 10;

    /** 证券期限的年、月、日文本格式，至少含一项由解析方法校验 */
    private static final Pattern TERM_PATTERN = Pattern.compile(
            "^(?:(\\d+(?:\\.\\d+)?)年)?(?:(\\d+(?:\\.\\d+)?)个?月)?(?:(\\d+(?:\\.\\d+)?)[天日])?$");

    /** 工具类禁止实例化 */
    private CreditBondRemainTermUtil() {
    }

    /**
     * 计算证券剩余期限年数，供 {@link #matchTermBucket} 匹配期限档
     *
     * 执行顺序：
     * 1. 证券主数据为空时返回 null
     * 2. 含权债优先取 {@code dateInrightExists}，缺失时取 {@code dateRepurchaseExists}，两项均为年数
     * 3. 含权债取回售和赎回中较短的有效期限，仅一项有效时使用该项，普通债直接解析证券期限文本
     *
     * @param sec 证券主数据
     * @return 剩余期限年数，无法取得期限时返回 null，由期限档匹配方法选最长档
     */
    public static BigDecimal resolveRemainTermYears(SecurityInfoBo sec) {
        if (sec == null) {
            return null;
        }
        if (CreditBondSpecialInboundRule.isInright(sec)) {
            // 回售期限已是年数，行权期限缺失时取回购期限
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
     * 将证券期限文本解析为年数
     *
     * 支持「3年6个月3天」「3年6月3日」「6个月3天」「6月3日」「6天」「6日」等格式
     *
     * 执行顺序：
     * 1. 去掉空白，输入为空或格式不符时返回 null
     * 2. 分别读取年、月和天，缺失部分按 0 处理
     * 3. 保留原有年数，将文本中的月数除以 12、天数除以 365，再把三段相加
     *
     * @param termText 证券期限文本
     * @return 折算后的年数，文本为空或格式无效时返回 null
     */
    public static BigDecimal parseRemainTermYears(String termText) {
        // 1. 去掉空白并校验年、月、天的格式
        if (termText == null || termText.trim().isEmpty()) {
            return null;
        }
        String normalized = termText.replaceAll("\\s+", "");
        Matcher matcher = TERM_PATTERN.matcher(normalized);
        if (!matcher.matches()
                || (matcher.group(1) == null && matcher.group(2) == null && matcher.group(3) == null)) {
            return null;
        }
        // 2. 读取年数，缺失时按 0 处理
        BigDecimal years = parseNumber(matcher.group(1));
        // 读取月数，供后续折算成年
        BigDecimal months = parseNumber(matcher.group(2));
        // 读取天数，供后续折算成年
        BigDecimal days = parseNumber(matcher.group(3));
        // 3. 将三部分统一为年数后相加
        return years
                .add(months.divide(MONTHS_PER_YEAR, TERM_SCALE, RoundingMode.HALF_UP))
                .add(days.divide(DAYS_PER_YEAR, TERM_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 按优先顺序取第一个非空期限，负值按 0 年处理
     *
     * @param first 优先使用的期限年数
     * @param second 优先值缺失时使用的期限年数
     * @return 选中的非负年数，两项都缺失时返回 null
     */
    private static BigDecimal firstYears(BigDecimal first, BigDecimal second) {
        if (first != null) {
            // 使用优先值，并将已过期的期限按 0 年处理
            return clampNonNegative(first);
        }
        if (second != null) {
            // 优先值缺失时使用备选值，并将负值按 0 年处理
            return clampNonNegative(second);
        }
        return null;
    }

    /**
     * 将负的期限年数归零
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
     * 将期限的一段数字文本转为数值
     *
     * @param value 已匹配到的数字文本，未匹配时为 null
     * @return 对应数值，输入为 null 时返回 0
     */
    private static BigDecimal parseNumber(String value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }

    /**
     * 按剩余期限年数匹配期限档
     *
     * 执行顺序：
     * 1. 没有可检查的期限档列表时返回 null
     * 2. 无法取得期限年数时选最长的有效档，标准配置下为大于 5 年的 {@code GT_5} 档
     * 3. 有期限年数时，按传入顺序匹配第一个有编码且符合区间的档
     *
     * @param remainTermYears 剩余期限年数，null 表示无法解析
     * @param buckets 启用的期限档
     * @return 匹配的期限档编码，没有可用档或未匹配时返回 null
     */
    public static String matchTermBucket(BigDecimal remainTermYears, List<CreditBondTermBucketBo> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return null;
        }
        if (remainTermYears == null) {
            // 期限缺失时用最长档继续查矩阵，标准配置对应大于 5 年
            return pickLongestTermBucket(buckets);
        }
        for (CreditBondTermBucketBo bucket : buckets) {
            if (bucket == null || bucket.getBucketCode() == null || bucket.getBucketCode().isEmpty()) {
                continue;
            }
            // 按当前期限档的上下限和边界配置检查是否匹配
            if (inTermRange(remainTermYears, bucket)) {
                return bucket.getBucketCode();
            }
        }
        return null;
    }

    /**
     * 按期限档的上下限及边界包含标志检查年数
     *
     * @param years 待匹配的期限年数
     * @param bucket 期限档配置，缺失的上限或下限视为无对应边界
     * @return true=年数满足上下限及边界设置，false=年数超出配置区间
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
     * 选择用于期限缺失时回退的最长期限档
     *
     * 执行顺序：
     * 1. 跳过无编码的档，优先选择没有上限的档
     * 2. 有无上限的情况相同时选下限较高的档，缺少下限时按 0 比较
     *
     * @param buckets 可选的期限档
     * @return 选中的期限档编码，没有有效档时返回 null
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
