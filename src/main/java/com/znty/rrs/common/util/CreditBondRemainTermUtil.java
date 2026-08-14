package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.SecurityInfoBo;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
     * @return 剩余期限年数；无法解析时返回 null（跳过矩阵期限档）
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
}
