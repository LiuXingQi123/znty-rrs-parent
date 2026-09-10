package com.znty.rrs.service;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 定时任务调库日志文案辅助逻辑单元测试。 */
public class ScheduledAdjustLogHelperTest {

    /** 验证日志详情拼接会忽略空白项。 */
    @Test
    public void appendDetailsShouldIgnoreBlankDetails() {
        assertThat(ScheduledAdjustLogHelper.appendDetails("自动调整", null, " ", "判断：命中"))
                .isEqualTo("自动调整（判断：命中）");
        assertThat(ScheduledAdjustLogHelper.appendDetails("自动调整", null, " "))
                .isEqualTo("自动调整");
    }

    /** 验证空基础原因不会被渲染成字符串 null。 */
    @Test
    public void appendDetailsShouldNotRenderNullReason() {
        assertThat(ScheduledAdjustLogHelper.appendDetails(null, "判断：命中"))
                .isEqualTo("（判断：命中）");
        assertThat(ScheduledAdjustLogHelper.appendDetails(" ", null))
                .isNull();
    }

    /** 验证 Wind 日期会转换为标准日期展示格式。 */
    @Test
    public void dateDetailShouldFormatWindDate() {
        assertThat(ScheduledAdjustLogHelper.dateDetail("到期日", "20260901"))
                .isEqualTo("到期日：2026-09-01");
        assertThat(ScheduledAdjustLogHelper.dateDetail("到期日", "2026/09/01"))
                .isEqualTo("到期日：2026/09/01");
    }
}
