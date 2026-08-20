package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * date_exists（天，DECIMAL）→ 年 换算。
 */
public class CreditBondRemainTermUtilTest {

    @Test
    public void shouldReturnNullWhenDateExistsMissing() {
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(null)).isNull();
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(new SecurityInfoBo())).isNull();
        assertThat(CreditBondRemainTermUtil.daysToYears((BigDecimal) null)).isNull();
    }

    @Test
    public void shouldConvertDaysToYearsBy365() {
        // 365 天 = 1 年
        assertThat(CreditBondRemainTermUtil.daysToYears(new BigDecimal("365")))
                .isEqualByComparingTo("1");
        // 1095 天 = 3 年
        assertThat(CreditBondRemainTermUtil.daysToYears(new BigDecimal("1095")))
                .isEqualByComparingTo("3");
        // 1826 天 > 5 年（5*365=1825）
        assertThat(CreditBondRemainTermUtil.daysToYears(new BigDecimal("1826")))
                .isGreaterThan(new BigDecimal("5"));
        // 支持小数天
        assertThat(CreditBondRemainTermUtil.daysToYears(new BigDecimal("182.5")))
                .isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    public void shouldTreatNegativeDaysAsZero() {
        assertThat(CreditBondRemainTermUtil.daysToYears(new BigDecimal("-10")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void shouldReadDateExistsFromSecurity() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setDateExists(new BigDecimal("730"));
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(sec))
                .isEqualByComparingTo("2");
    }

    @Test
    public void inrightShouldUsePutDaysWhenShorterThanMaturity() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setInrightFlag(1);
        sec.setDateExists(new BigDecimal("1825"));
        sec.setDateInrightExists(new BigDecimal("365"));
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(sec))
                .isEqualByComparingTo("1");
    }

    @Test
    public void inrightCallOnlyShouldUseMaturityDays() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setInrightFlag(1);
        sec.setDateExists(new BigDecimal("730"));
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(sec))
                .isEqualByComparingTo("2");
    }

    @Test
    public void missingTermShouldDefaultToLongestBucket() {
        CreditBondTermBucketBo le1 = new CreditBondTermBucketBo();
        le1.setBucketCode("LE_1");
        le1.setMaxTermYear(new BigDecimal("1"));
        le1.setMaxInclusive(1);
        CreditBondTermBucketBo gt5 = new CreditBondTermBucketBo();
        gt5.setBucketCode("GT_5");
        gt5.setMinTermYear(new BigDecimal("5"));
        gt5.setMinInclusive(0);
        assertThat(CreditBondRemainTermUtil.matchTermBucket(null, Arrays.asList(le1, gt5)))
                .isEqualTo("GT_5");
        assertThat(CreditBondRemainTermUtil.matchTermBucket(null, Collections.<CreditBondTermBucketBo>emptyList()))
                .isNull();
    }

    @Test
    public void knownYearsShouldMatchBucketNotDefaultLongest() {
        CreditBondTermBucketBo le1 = new CreditBondTermBucketBo();
        le1.setBucketCode("LE_1");
        le1.setMaxTermYear(new BigDecimal("1"));
        le1.setMaxInclusive(1);
        CreditBondTermBucketBo gt5 = new CreditBondTermBucketBo();
        gt5.setBucketCode("GT_5");
        gt5.setMinTermYear(new BigDecimal("5"));
        gt5.setMinInclusive(0);
        assertThat(CreditBondRemainTermUtil.matchTermBucket(new BigDecimal("0.5"), Arrays.asList(le1, gt5)))
                .isEqualTo("LE_1");
    }

    @Test
    public void unmatchedKnownYearsShouldNotDefaultToLongest() {
        CreditBondTermBucketBo le1 = new CreditBondTermBucketBo();
        le1.setBucketCode("LE_1");
        le1.setMaxTermYear(new BigDecimal("1"));
        le1.setMaxInclusive(1);
        CreditBondTermBucketBo gt5 = new CreditBondTermBucketBo();
        gt5.setBucketCode("GT_5");
        gt5.setMinTermYear(new BigDecimal("5"));
        gt5.setMinInclusive(0);
        // 已算出年数但两档之间有缺口时，不能当成「期限为空」去兜底最长档
        assertThat(CreditBondRemainTermUtil.matchTermBucket(new BigDecimal("2"), Arrays.asList(le1, gt5)))
                .isNull();
    }
}
