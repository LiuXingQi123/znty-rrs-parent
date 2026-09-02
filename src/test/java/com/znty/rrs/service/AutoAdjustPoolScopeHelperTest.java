package com.znty.rrs.service;

import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.schedule.TaskDetailLog;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自动调库扫描池并集解析单元测试。
 */
public class AutoAdjustPoolScopeHelperTest {

    @Test
    public void parseOptionalPoolIdsShouldAllowEmpty() {
        assertThat(AutoAdjustPoolScopeHelper.parseOptionalPoolIds(null)).isEmpty();
        assertThat(AutoAdjustPoolScopeHelper.parseOptionalPoolIds("{}")).isEmpty();
        assertThat(AutoAdjustPoolScopeHelper.parseOptionalPoolIds("{\"poolIds\":[]}")).isEmpty();
        assertThat(AutoAdjustPoolScopeHelper.parseOptionalPoolIds("{\"poolIds\":[15,16]}"))
                .containsExactly(15L, 16L);
        assertThatThrownBy(() -> AutoAdjustPoolScopeHelper.parseOptionalPoolIds("15-15"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AutoAdjustPoolScopeHelper.parseOptionalPoolIds("{\"poolIds\":'123'}"))
                .isInstanceOf(BizException.class);
    }

    @Test
    public void resolveUnionPoolIdsShouldMergeParamAndConfig() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        AutoAdjustPoolScopeHelper helper = new AutoAdjustPoolScopeHelper();
        ReflectionTestUtils.setField(helper, "autoAdjustMapper", mapper);
        when(mapper.queryBoundPoolIds("security_expired_auto_out", RuleType.AUTO_OUT.getCode()))
                .thenReturn(Arrays.asList(10L, 16L));

        List<Long> union = helper.resolveUnionPoolIds("{\"poolIds\":[10,15]}",
                "security_expired_auto_out", RuleType.AUTO_OUT.getCode(), null);

        assertThat(union).containsExactly(10L, 15L, 16L);
    }

    @Test
    public void resolveUnionPoolIdsShouldUseConfigWhenParamEmpty() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        AutoAdjustPoolScopeHelper helper = new AutoAdjustPoolScopeHelper();
        ReflectionTestUtils.setField(helper, "autoAdjustMapper", mapper);
        when(mapper.queryBoundPoolIds("security_expired_auto_out", RuleType.AUTO_OUT.getCode()))
                .thenReturn(Collections.singletonList(16L));

        List<Long> union = helper.resolveUnionPoolIds(null,
                "security_expired_auto_out", RuleType.AUTO_OUT.getCode(), null);

        assertThat(union).containsExactly(16L);
    }

    @Test
    public void resolveUnionPoolIdsShouldFailWhenBothEmpty() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        AutoAdjustPoolScopeHelper helper = new AutoAdjustPoolScopeHelper();
        ReflectionTestUtils.setField(helper, "autoAdjustMapper", mapper);

        assertThatThrownBy(() -> helper.resolveUnionPoolIds("{}",
                "security_expired_auto_out", RuleType.AUTO_OUT.getCode(), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未配置扫描池");
    }

    @Test
    public void resolveUnionPoolIdsShouldFailOnInvalidJsonEvenIfConfigExists() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        AutoAdjustPoolScopeHelper helper = new AutoAdjustPoolScopeHelper();
        ReflectionTestUtils.setField(helper, "autoAdjustMapper", mapper);
        when(mapper.queryBoundPoolIds("security_expired_auto_out", RuleType.AUTO_OUT.getCode()))
                .thenReturn(Collections.singletonList(16L));

        assertThatThrownBy(() -> helper.resolveUnionPoolIds("{\"poolIds\":'123'}",
                "security_expired_auto_out", RuleType.AUTO_OUT.getCode(), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    public void unionSamePoolMappingsShouldAppendBoundPools() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        AutoAdjustPoolScopeHelper helper = new AutoAdjustPoolScopeHelper();
        ReflectionTestUtils.setField(helper, "autoAdjustMapper", mapper);
        when(mapper.queryBoundPoolIds("company_inpool_bond_auto_in", RuleType.AUTO_IN.getCode()))
                .thenReturn(Arrays.asList(15L, 16L));

        List<long[]> param = Collections.singletonList(new long[]{15L, 100L});
        List<long[]> merged = helper.unionSamePoolMappings(param,
                "company_inpool_bond_auto_in", RuleType.AUTO_IN.getCode(), null);

        assertThat(merged).hasSize(3);
        assertThat(merged.get(0)).containsExactly(15L, 100L);
        assertThat(merged.get(1)).containsExactly(15L, 15L);
        assertThat(merged.get(2)).containsExactly(16L, 16L);
    }

    @Test
    public void resolveUnionPoolIdsShouldLogAllBoundPoolsIncludingEmptyScanPools() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        AutoAdjustPoolScopeHelper helper = new AutoAdjustPoolScopeHelper();
        ReflectionTestUtils.setField(helper, "autoAdjustMapper", mapper);
        ReflectionTestUtils.setField(helper, "investmentPoolMapper", poolMapper);
        when(mapper.queryBoundPoolIds("security_expired_auto_out", RuleType.AUTO_OUT.getCode()))
                .thenReturn(Arrays.asList(3L, 15L));
        InvestmentPoolBo pool3 = new InvestmentPoolBo();
        pool3.setId(3L);
        pool3.setPoolName("信用债一级库");
        InvestmentPoolBo pool15 = new InvestmentPoolBo();
        pool15.setId(15L);
        pool15.setPoolName("债券禁止库");
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool3, pool15));

        TaskDetailLog detail = new TaskDetailLog();
        helper.resolveUnionPoolIds("{\"poolIds\":[15]}",
                "security_expired_auto_out", RuleType.AUTO_OUT.getCode(), detail);

        String log = detail.build();
        assertThat(log).contains("关系配置 poolIds=[3(信用债一级库), 15(债券禁止库)]");
        assertThat(log).contains("扫描池并集 poolIds=[15, 3]");
    }

    @Test
    public void resolveUnionPoolIdsShouldLogEmptyRelationConfig() {
        AutoAdjustMapper mapper = mock(AutoAdjustMapper.class);
        AutoAdjustPoolScopeHelper helper = new AutoAdjustPoolScopeHelper();
        ReflectionTestUtils.setField(helper, "autoAdjustMapper", mapper);
        when(mapper.queryBoundPoolIds("security_expired_auto_out", RuleType.AUTO_OUT.getCode()))
                .thenReturn(Collections.<Long>emptyList());

        TaskDetailLog detail = new TaskDetailLog();
        helper.resolveUnionPoolIds("{\"poolIds\":[15]}",
                "security_expired_auto_out", RuleType.AUTO_OUT.getCode(), detail);

        assertThat(detail.build()).contains("关系配置 poolIds=[]");
    }
}
