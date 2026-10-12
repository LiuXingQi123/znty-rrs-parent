package com.znty.rrs.service;

import com.znty.rrs.mapper.AutoAdjustMapper;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 自动调库单测辅助：给任务实现注入扫描池并集解析器。
 */
final class AutoAdjustTestSupport {

    private AutoAdjustTestSupport() {
    }

    /**
     * 构造并注入 {@link AutoAdjustPoolScopeHelper}。
     */
    static AutoAdjustPoolScopeHelper bindPoolScope(Object service, AutoAdjustMapper mapper) {
        AutoAdjustPoolScopeHelper helper = new AutoAdjustPoolScopeHelper();
        ReflectionTestUtils.setField(helper, "autoAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "poolScopeHelper", helper);
        return helper;
    }
}
