package com.znty.rrs.common.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RatingComparator 评级下调判定工具测试。
 */
public class RatingComparatorTest {

    @Test
    public void 长期评级下调应返回true() {
        // AA(index 3) 相对 AAA(index 1) 下调
        assertThat(RatingComparator.isDowngraded("AA", "AAA", "下调")).isTrue();
    }

    @Test
    public void 长期评级上调应返回false() {
        // AAA(index 1) 相对 AA(index 3) 上调
        assertThat(RatingComparator.isDowngraded("AAA", "AA", "上调")).isFalse();
    }

    @Test
    public void 长期评级维持应返回false() {
        assertThat(RatingComparator.isDowngraded("AAA", "AAA", "维持")).isFalse();
    }

    @Test
    public void AAA降为AAplus应判定下调() {
        // AAA(index 1) -> AA+(index 2) 下调
        assertThat(RatingComparator.isDowngraded("AA+", "AAA", null)).isTrue();
    }

    @Test
    public void AAminus相对AA为下调() {
        // AA-(index 4) 相对 AA(index 3) 下调
        assertThat(RatingComparator.isDowngraded("AA-", "AA", null)).isTrue();
    }

    @Test
    public void 短期评级下调应返回true() {
        // A-2(index 1) 相对 A-1(index 0) 下调
        assertThat(RatingComparator.isDowngraded("A-2", "A-1", null)).isTrue();
    }

    @Test
    public void 短期评级维持应返回false() {
        assertThat(RatingComparator.isDowngraded("A-1", "A-1", null)).isFalse();
    }

    @Test
    public void 前次评级为空视为首次评级返回false() {
        assertThat(RatingComparator.isDowngraded("AA", null, null)).isFalse();
        assertThat(RatingComparator.isDowngraded("AA", "", null)).isFalse();
        assertThat(RatingComparator.isDowngraded("AA", "  ", null)).isFalse();
    }

    @Test
    public void 当前评级为空返回false() {
        assertThat(RatingComparator.isDowngraded(null, "AAA", null)).isFalse();
        assertThat(RatingComparator.isDowngraded("", "AAA", null)).isFalse();
    }

    @Test
    public void 跨尺度无法比较且回退文本含下调应返回true() {
        // AAA(长期) vs A-1(短期) 跨尺度，回退到评级变动文本
        assertThat(RatingComparator.isDowngraded("AAA", "A-1", "主体评级下调")).isTrue();
    }

    @Test
    public void 跨尺度无法比较且回退文本为维持应返回false() {
        assertThat(RatingComparator.isDowngraded("AAA", "A-1", "维持")).isFalse();
    }

    @Test
    public void 未知评级且回退文本含下调应返回true() {
        // 评级不在序号表，回退到评级变动文本
        assertThat(RatingComparator.isDowngraded("XX", "YY", "评级下调")).isTrue();
    }

    @Test
    public void 未知评级且无回退文本应failOpen返回false() {
        assertThat(RatingComparator.isDowngraded("XX", "YY", null)).isFalse();
        assertThat(RatingComparator.isDowngraded("XX", "YY", "")).isFalse();
    }

    @Test
    public void 评级前后空白应被trim() {
        // 带空格的评级应 trim 后比较
        assertThat(RatingComparator.isDowngraded(" AA ", " AAA ", null)).isTrue();
    }

    @Test
    public void 评级大小写不敏感() {
        assertThat(RatingComparator.isDowngraded("aa", "aaa", null)).isTrue();
    }
}
