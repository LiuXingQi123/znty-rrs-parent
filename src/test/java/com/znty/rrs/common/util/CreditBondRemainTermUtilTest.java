package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.junit.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * date_exists（天）→ 年 换算。
 */
public class CreditBondRemainTermUtilTest {

    @Test
    public void shouldReturnNullWhenDateExistsMissing() {
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(null)).isNull();
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(new SecurityInfoBo())).isNull();
        assertThat(CreditBondRemainTermUtil.daysToYears(null)).isNull();
    }

    @Test
    public void shouldConvertDaysToYearsBy365() {
        // 365 天 = 1 年
        assertThat(CreditBondRemainTermUtil.daysToYears(365))
                .isEqualByComparingTo("1");
        // 1095 天 = 3 年
        assertThat(CreditBondRemainTermUtil.daysToYears(1095))
                .isEqualByComparingTo("3");
        // 1826 天 > 5 年（5*365=1825）
        assertThat(CreditBondRemainTermUtil.daysToYears(1826))
                .isGreaterThan(new BigDecimal("5"));
    }

    @Test
    public void shouldTreatNegativeDaysAsZero() {
        assertThat(CreditBondRemainTermUtil.daysToYears(-10))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void shouldReadDateExistsFromSecurity() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setDateExists(730);
        assertThat(CreditBondRemainTermUtil.resolveRemainTermYears(sec))
                .isEqualByComparingTo("2");
    }
}
