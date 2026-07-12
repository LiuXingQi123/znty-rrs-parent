package com.znty.rrs.service;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调库批次号上下文测试。
 */
public class AdjustBatchNoContextTest {

    /** 验证四类调库上下文均使用毫秒级时间片。 */
    @Test
    public void batchNoContextsShouldUseMillisecondTimeText() throws Exception {
        // 校验证券单笔调库批次时间片
        assertBatchTimeText("com.znty.rrs.service.SecurityPoolAdjustService$BatchNoContext");
        // 校验证券批量调库批次时间片
        assertBatchTimeText("com.znty.rrs.service.BatchSecurityPoolAdjustService$BatchNoContext");
        // 校验主体调库批次时间片
        assertBatchTimeText("com.znty.rrs.service.ForbiddenPoolAdjustService$BatchNoContext");
        // 校验 CRMW 调库批次时间片
        assertBatchTimeText("com.znty.rrs.service.CrmwPoolAdjustService$BatchNoContext");
    }

    /** 验证指定批次号上下文的时间片格式。 */
    private void assertBatchTimeText(String className) throws Exception {
        Class<?> contextClass = Class.forName(className);
        Constructor<?> constructor = contextClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object context = constructor.newInstance();
        Field batchTimeTextField = contextClass.getDeclaredField("batchTimeText");
        batchTimeTextField.setAccessible(true);
        assertThat((String) batchTimeTextField.get(context)).matches("\\d{17}");
    }
}
