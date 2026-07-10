package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.WindIssuerRatingBo;
import com.znty.rrs.mapper.WindRatingMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RatingDowngradeChecker 评级下调判定组件测试。
 */
public class RatingDowngradeCheckerTest {

    private WindRatingMapper windRatingMapper;
    private RatingDowngradeChecker checker;

    @Before
    public void setUp() {
        windRatingMapper = mock(WindRatingMapper.class);
        checker = new RatingDowngradeChecker();
        // 注入 mock 的 WindRatingMapper
        ReflectionTestUtils.setField(checker, "windRatingMapper", windRatingMapper);
    }

    // ===== 主体评级下调 =====

    @Test
    public void 主体评级下调应返回true() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssuerCode("C10001");
        // 按发行人代码查 wind，返回下调记录
        WindIssuerRatingBo rating = new WindIssuerRatingBo();
        rating.setCreditRating("AA");
        rating.setPreCreditRating("AAA");
        rating.setCreditRatingChange("下调");
        when(windRatingMapper.queryLatestRating("C10001")).thenReturn(rating);

        assertThat(checker.isIssuerDowngraded(sec)).isTrue();
    }

    @Test
    public void 主体评级未下调应返回false() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssuerCode("C10001");
        WindIssuerRatingBo rating = new WindIssuerRatingBo();
        rating.setCreditRating("AAA");
        rating.setPreCreditRating("AAA");
        rating.setCreditRatingChange("维持");
        when(windRatingMapper.queryLatestRating("C10001")).thenReturn(rating);

        assertThat(checker.isIssuerDowngraded(sec)).isFalse();
    }

    @Test
    public void 主体无wind记录应返回false() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssuerCode("C10001");
        when(windRatingMapper.queryLatestRating("C10001")).thenReturn(null);

        assertThat(checker.isIssuerDowngraded(sec)).isFalse();
    }

    @Test
    public void 主体查询抛跨库异常应failOpen返回false() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssuerCode("C10001");
        // 模拟 H2 测试库无 wind 表场景
        when(windRatingMapper.queryLatestRating("C10001"))
                .thenThrow(new BadSqlGrammarException("query", "SELECT ...", new SQLException("table not found")));

        assertThat(checker.isIssuerDowngraded(sec)).isFalse();
    }

    @Test
    public void 主体证券为空返回false() {
        assertThat(checker.isIssuerDowngraded(null)).isFalse();
    }

    @Test
    public void 主体发行人代码为空返回false且不查库() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssuerCode("");
        assertThat(checker.isIssuerDowngraded(sec)).isFalse();
        verify(windRatingMapper, never()).queryLatestRating(anyString());
    }

    // ===== 展望评级下调 =====

    @Test
    public void 展望为负面应返回true() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setRatingOutlook("负面");
        assertThat(checker.isOutlookNegative(sec)).isTrue();
    }

    @Test
    public void 展望为稳定应返回false() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setRatingOutlook("稳定");
        assertThat(checker.isOutlookNegative(sec)).isFalse();
    }

    @Test
    public void 展望为正面应返回false() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setRatingOutlook("正面");
        assertThat(checker.isOutlookNegative(sec)).isFalse();
    }

    @Test
    public void 展望为空应返回false() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setRatingOutlook(null);
        assertThat(checker.isOutlookNegative(sec)).isFalse();
        sec.setRatingOutlook("");
        assertThat(checker.isOutlookNegative(sec)).isFalse();
    }

    @Test
    public void 展望证券为空返回false() {
        assertThat(checker.isOutlookNegative(null)).isFalse();
    }

    // ===== 担保人评级下调 =====

    @Test
    public void 担保人代码为空返回false且不查库() {
        assertThat(checker.isGuarantorDowngraded(null)).isFalse();
        assertThat(checker.isGuarantorDowngraded("")).isFalse();
        assertThat(checker.isGuarantorDowngraded("  ")).isFalse();
        verify(windRatingMapper, never()).queryLatestRating(anyString());
    }

    @Test
    public void 担保人评级下调应返回true() {
        WindIssuerRatingBo rating = new WindIssuerRatingBo();
        rating.setCreditRating("A");
        rating.setPreCreditRating("AA");
        rating.setCreditRatingChange("下调");
        when(windRatingMapper.queryLatestRating("C90005")).thenReturn(rating);

        assertThat(checker.isGuarantorDowngraded("C90005")).isTrue();
    }

    @Test
    public void 担保人无wind记录应返回false() {
        when(windRatingMapper.queryLatestRating("C90005")).thenReturn(null);
        assertThat(checker.isGuarantorDowngraded("C90005")).isFalse();
    }
}
