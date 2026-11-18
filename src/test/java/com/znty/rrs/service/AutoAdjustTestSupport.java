package com.znty.rrs.service;

import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.ExcelPoolExclusionMapper;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

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

    static ExcelPoolExclusionMapper bindExcelExclusion(Object service) {
        ExcelPoolExclusionMapper mapper = mock(ExcelPoolExclusionMapper.class);
        bindExcelExclusion(service, mapper);
        return mapper;
    }

    static ExcelPoolExclusionService bindExcelExclusion(Object service, ExcelPoolExclusionMapper mapper) {
        ExcelPoolExclusionService exclusions = new ExcelPoolExclusionService();
        ReflectionTestUtils.setField(exclusions, "excelPoolExclusionMapper", mapper);
        ReflectionTestUtils.setField(service, "excelPoolExclusionService", exclusions);
        return exclusions;
    }
}
