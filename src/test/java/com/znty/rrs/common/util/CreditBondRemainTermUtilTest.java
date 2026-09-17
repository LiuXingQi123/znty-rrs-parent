package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证券期限解析：date_exists_str 提供年、月、日，含权/回购剩余期限字段本身是年。
 */
public class CreditBondRemainTermUtilTest {

    @Test
    public void shouldReturnNullWhenDateExistsStrMissing() {
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(null)).isNull();
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(new SecurityInfoBo())).isNull();
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears(null)).isNull();
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("")).isNull();
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("未知")).isNull();
    }

    @Test
    public void shouldParseSupportedDateExistsStrFormats() {
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("3年6天"))
                .isGreaterThan(new BigDecimal("3"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("3年6个月3天"))
                .isGreaterThan(new BigDecimal("3.5"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("3年6月3日"))
                .isEqualByComparingTo(CreditBondRemainTermUtil.parseRemainTermYears("3年6个月3天"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("3年6月3天"))
                .isEqualByComparingTo(CreditBondRemainTermUtil.parseRemainTermYears("3年6个月3天"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("3年6个月3日"))
                .isEqualByComparingTo(CreditBondRemainTermUtil.parseRemainTermYears("3年6个月3天"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("6个月3天"))
                .isGreaterThan(new BigDecimal("0.5"))
                .isLessThan(new BigDecimal("1"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("6月3日"))
                .isEqualByComparingTo(CreditBondRemainTermUtil.parseRemainTermYears("6个月3天"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("6天"))
                .isGreaterThan(BigDecimal.ZERO)
                .isLessThan(new BigDecimal("1"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears("6日"))
                .isEqualByComparingTo(CreditBondRemainTermUtil.parseRemainTermYears("6天"));
        assertThat(CreditBondRemainTermUtil.parseRemainTermYears(" 3年 6个月 3天 "))
                .isEqualByComparingTo(CreditBondRemainTermUtil.parseRemainTermYears("3年6个月3天"));
    }

    @Test
    public void shouldReadDateExistsStrInsteadOfDateExistsFromSecurity() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setDateExists(new BigDecimal("9999"));
        sec.setDateExistsStr("2年");
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(sec))
                .isEqualByComparingTo("2");
    }

    @Test
    public void inrightShouldUsePutYearsWhenShorterThanMaturity() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setInrightFlag(1);
        // date_exists_str 为 5 年；含权剩余期限 1 年（已是年）
        sec.setDateExistsStr("5年");
        sec.setDateInrightExists(new BigDecimal("1"));
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(sec))
                .isEqualByComparingTo("1");
    }

    @Test
    public void inrightShouldNotTreatInrightYearsAsDays() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setInrightFlag(1);
        sec.setDateExistsStr("5年");
        sec.setDateInrightExists(new BigDecimal("2"));
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(sec))
                .isEqualByComparingTo("2");
    }

    @Test
    public void inrightShouldFallbackToRepurchaseYears() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setInrightFlag(1);
        sec.setDateExistsStr("5年");
        sec.setDateRepurchaseExists(new BigDecimal("1.5"));
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(sec))
                .isEqualByComparingTo("1.5");
    }

    @Test
    public void inrightCallOnlyShouldUseDateExistsStr() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setInrightFlag(1);
        sec.setDateExists(new BigDecimal("9999"));
        sec.setDateExistsStr("2年");
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
