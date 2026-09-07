package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.CategoryType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckContext;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 普通债、ABS 与主体入口统一接入黑名单质押库判定的测试。 */
public class PledgeBlacklistEntryPointTest {

    /** 普通债入口应按发行主体当前状态校验两个调整方向。 */
    @Test
    public void securityAdjust_ShouldValidateBothDirectionsByIssuer() {
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        verifyBothDirections(service, CategoryType.BOND.getCode(), false);
    }

    /** ABS 入口应按发行主体当前状态校验两个调整方向。 */
    @Test
    public void absAdjust_ShouldValidateBothDirectionsByIssuer() {
        ForbiddenAbsPoolAdjustService service = new ForbiddenAbsPoolAdjustService();
        verifyBothDirections(service, CategoryType.BOND.getCode(), false);
    }

    /** 主体入口应按本批预计状态校验两个调整方向。 */
    @Test
    public void companyAdjust_ShouldValidateBothDirectionsByCompanyCode() {
        ForbiddenPoolAdjustService service = new ForbiddenPoolAdjustService();
        verifyBothDirections(service, CategoryType.COMPANY.getCode(), true);
    }

    /**
     * 验证指定入口的调入、调出方向均调用统一三条件服务。
     *
     * @param service      调库服务
     * @param categoryType 调整对象大类
     * @param company      是否为主体级入口
     */
    @SuppressWarnings("unchecked")
    private void verifyBothDirections(Object service, String categoryType, boolean company) {
        PledgeBlacklistRuleService ruleService = mock(PledgeBlacklistRuleService.class);
        Set<Long> requestInPoolIds = Collections.singleton(PledgeBlacklistRuleService.FORBIDDEN_POOL_ID);
        Set<Long> requestOutPoolIds = Collections.singleton(PledgeBlacklistRuleService.KEY_WATCH_POOL_ID);
        Set<Long> expectedInPoolIds = company ? requestInPoolIds : Collections.<Long>emptySet();
        Set<Long> expectedOutPoolIds = company ? requestOutPoolIds : Collections.<Long>emptySet();
        when(ruleService.validate(eq("C001"), eq(AdjustMode.IN.getCode()),
                eq(expectedInPoolIds), eq(expectedOutPoolIds)))
                .thenReturn("调入条件不满足");
        when(ruleService.validate(eq("C001"), eq(AdjustMode.OUT.getCode()),
                eq(expectedInPoolIds), eq(expectedOutPoolIds)))
                .thenReturn("调出条件不满足");
        ReflectionTestUtils.setField(service, "pledgeBlacklistRuleService", ruleService);

        AdjustCheckContext context = new AdjustCheckContext();
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(PledgeBlacklistRuleService.BLACKLIST_POOL_ID);
        context.setTargetPool(pool);
        context.setCategoryType(categoryType);
        context.setRequestInPoolIds(requestInPoolIds);
        context.setRequestOutPoolIds(requestOutPoolIds);
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode(company ? "C001" : "B001");
        security.setIssuerCode("C001");
        context.setSecurityInfo(security);

        List<String> inboundFailures = new ArrayList<>();
        // 执行调入方向的统一条件校验
        ReflectionTestUtils.invokeMethod(service, "addPledgeBlacklistFailure",
                inboundFailures, context, AdjustMode.IN.getCode());
        List<String> outboundFailures = new ArrayList<>();
        // 执行调出方向的统一条件校验
        ReflectionTestUtils.invokeMethod(service, "addPledgeBlacklistFailure",
                outboundFailures, context, AdjustMode.OUT.getCode());

        assertThat(inboundFailures).containsExactly("调入条件不满足");
        assertThat(outboundFailures).containsExactly("调出条件不满足");
    }
}
