package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 主体旗下债券联动范围策略测试。 */
public class CompanyBondSyncPolicyTest {

    /** 验证当前范围不排除 ABS、CRMW 或其他 bond 子类型。 */
    @Test
    public void currentTypeScopeShouldIncludeAllBondTypes() {
        CompanyBondTypeScopeBo scope = CompanyBondSyncPolicy.currentTypeScope();

        assertThat(scope.isExcludeAbs()).isFalse();
        assertThat(scope.getExcludedSecurityTypes()).isEmpty();
    }

    /** 验证后续可通过统一对象表达 ABS 与其他证券类型排除。 */
    @Test
    public void excludingShouldKeepExplicitFutureFilters() {
        CompanyBondTypeScopeBo scope = CompanyBondTypeScopeBo.excluding(
                true, Arrays.asList("crmw", "convertible_bond"));

        assertThat(scope.isExcludeAbs()).isTrue();
        assertThat(scope.getExcludedSecurityTypes()).containsExactly("crmw", "convertible_bond");
    }

    /**
     * 验证三条任务专用范围统一排除 ABS，且都不排除 CRMW
     *
     * <p>三条任务的旗下债口径必须一致，否则同一条主体联动链路会出现 ABS 从某个入口漏进来
     */
    @Test
    public void taskSpecificScopesShouldExcludeAbsOnly() {
        for (CompanyBondTypeScopeBo scope : Arrays.asList(
                CompanyBondSyncPolicy.samePoolBondScope(),
                CompanyBondSyncPolicy.newBondInboundScope(),
                CompanyBondSyncPolicy.outerRatingInboundScope())) {
            assertThat(scope.isExcludeAbs()).isTrue();
            assertThat(scope.getExcludedSecurityTypes()).isEmpty();
        }
    }

    /** 验证 CRMW 专用组合池与普通风险池边界。 */
    @Test
    public void isCrmwCombinationPoolShouldUsePoolType() {
        InvestmentPoolBo crmwPool = new InvestmentPoolBo();
        crmwPool.setPoolType("crmw");
        InvestmentPoolBo forbiddenPool = new InvestmentPoolBo();
        forbiddenPool.setPoolType("forbidden");

        assertThat(CompanyBondSyncPolicy.isCrmwCombinationPool(crmwPool)).isTrue();
        assertThat(CompanyBondSyncPolicy.isCrmwCombinationPool(forbiddenPool)).isFalse();
        assertThat(CompanyBondSyncPolicy.isCrmwCombinationPool(null)).isFalse();
    }
}
