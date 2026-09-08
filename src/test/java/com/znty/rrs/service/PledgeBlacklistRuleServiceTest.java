package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 黑名单质押库三条件统一判定测试。 */
public class PledgeBlacklistRuleServiceTest {

    /** 自动调库规则查询数据访问组件。 */
    private AutoAdjustMapper autoAdjustMapper;
    /** 外部评级机构配置服务。 */
    private ExternalRatingAgencyService externalRatingAgencyService;
    /** 待测试的黑名单质押库规则服务。 */
    private PledgeBlacklistRuleService service;

    /** 初始化待测试服务及依赖。 */
    @Before
    public void setUp() {
        autoAdjustMapper = mock(AutoAdjustMapper.class);
        externalRatingAgencyService = mock(ExternalRatingAgencyService.class);
        when(externalRatingAgencyService.queryRequiredAgencyCodeList())
                .thenReturn(Collections.singletonList("2"));
        service = new PledgeBlacklistRuleService();
        ReflectionTestUtils.setField(service, "autoAdjustMapper", autoAdjustMapper);
        ReflectionTestUtils.setField(service, "externalRatingAgencyService", externalRatingAgencyService);
    }

    /** 任一条件成立时应判定主体需要进入黑名单质押库。 */
    @Test
    public void evaluate_ShouldMatchWhenAnyConditionIsTrue() {
        when(autoAdjustMapper.queryCompanyInPool("C001", PledgeBlacklistRuleService.FORBIDDEN_POOL_ID))
                .thenReturn(true);

        PledgeBlacklistRuleService.Decision decision = service.evaluate("C001");

        assertThat(decision.shouldBeInBlacklist()).isTrue();
        assertThat(decision.getMatchDescription()).contains("债券禁止库15");
    }

    /** 无外评且两个条件池均未命中时应允许调出。 */
    @Test
    public void validateOut_ShouldAllowWhenNoRatingAndNoConditionPool() {
        when(autoAdjustMapper.queryCompanyHasLowOuterRating(any(String.class), any(List.class))).thenReturn(false);

        String failure = service.validate("C002", AdjustMode.OUT.getCode(),
                Collections.<Long>emptySet(), Collections.<Long>emptySet());

        assertThat(failure).isNull();
    }

    /** 本批调入和调出条件池时应使用预计完成后的状态。 */
    @Test
    public void evaluate_ShouldUseProjectedPoolState() {
        when(autoAdjustMapper.queryCompanyHasLowOuterRating(any(String.class), any(List.class))).thenReturn(false);

        PledgeBlacklistRuleService.Decision inbound = service.evaluate("C003",
                Collections.singleton(PledgeBlacklistRuleService.KEY_WATCH_POOL_ID),
                Collections.<Long>emptySet());
        PledgeBlacklistRuleService.Decision outbound = service.evaluate("C003",
                Collections.<Long>emptySet(),
                Collections.singleton(PledgeBlacklistRuleService.FORBIDDEN_POOL_ID));

        assertThat(inbound.shouldBeInBlacklist()).isTrue();
        assertThat(outbound.shouldBeInBlacklist()).isFalse();
    }

    /** 三个条件全部不成立时应拒绝调入。 */
    @Test
    public void validateIn_ShouldRejectWhenAllConditionsAreFalse() {
        String failure = service.validate("C004", AdjustMode.IN.getCode(),
                Collections.<Long>emptySet(), Collections.<Long>emptySet());

        assertThat(failure).contains("未命中黑名单质押库三个条件");
    }

    /** 未配置有效外部评级机构时应阻断黑名单规则判定。 */
    @Test
    public void evaluate_ShouldRejectWhenNoAgencyConfigured() {
        when(externalRatingAgencyService.queryRequiredAgencyCodeList())
                .thenThrow(new BizException("未配置有效外部评级机构"));

        assertThatThrownBy(() -> service.evaluate("C005"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未配置有效外部评级机构");
    }
}
