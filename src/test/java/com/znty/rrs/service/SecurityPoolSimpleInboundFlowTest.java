package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 证券池简易调入流程的业务门槛与特殊债边界测试。 */
public class SecurityPoolSimpleInboundFlowTest {

    /** 180天内非简易信用债入库和报告库有效信评必须同时具备，且不再依赖剩余天数。 */
    @Test
    public void standardBondShouldPassWithUniqueLevelAndBothRecentPrerequisites() {
        // 构建只允许二级库的标准券
        Fixture fixture = createFixture("2+", "2年", 2);
        fixture.security.setDateExists(null);

        assertThat(fixture.matches(2)).isTrue();
        assertThat(fixture.unmatchReasons).isEmpty();
        verify(fixture.mapper).queryIssuerHasNonSimpleCreditBondInboundWithinDays("SIMPLE001.IB", 180);
        verify(fixture.mapper).queryIssuerHasRecentCreditReportWithinDays("SIMPLE001.IB", 180);
    }

    /** 只有报告不能替代同主体最近180天的非简易信用债入库记录。 */
    @Test
    public void shouldRejectWhenRecentNonSimpleInboundIsMissing() {
        // 构建有报告但没有非简易入库历史的标准券
        Fixture fixture = createFixture("2+", "2年", 2);
        when(fixture.mapper.queryIssuerHasNonSimpleCreditBondInboundWithinDays("SIMPLE001.IB", 180))
                .thenReturn(false);

        assertThat(fixture.matches(2)).isFalse();
        assertThat(fixture.unmatchReasons).isNotEmpty();
    }

    /** 报告库中没有同发行人的有效信评报告时不能走简易流程。 */
    @Test
    public void shouldRejectWhenRecentCreditReportIsMissing() {
        // 构建只有非简易入库历史的标准券
        Fixture fixture = createFixture("2+", "2年", 2);
        when(fixture.mapper.queryIssuerHasRecentCreditReportWithinDays("SIMPLE001.IB", 180)).thenReturn(false);

        assertThat(fixture.matches(2)).isFalse();
        assertThat(fixture.unmatchReasons).isNotEmpty();
    }

    /** 唯一性须比较完整一至五级集合，不能先去掉四级、五级再判断。 */
    @Test
    public void shouldRejectWhenMatrixAllowsLevelTwoAndLevelFour() {
        // 构建跨越简易范围的两个准入等级
        Fixture fixture = createFixture("2+", "2年", 2, 4);

        assertThat(fixture.matches(2)).isFalse();
    }

    /** 不同池 ID 属于同一等级时，准入等级仍然唯一。 */
    @Test
    public void shouldCountDistinctLevelsInsteadOfPoolIds() {
        // 构建同属二级的两个池
        Fixture fixture = createFixture("2+", "2年", 2);
        InvestmentPoolBo anotherLevelTwo = new InvestmentPoolBo();
        anotherLevelTwo.setId(20L);
        anotherLevelTwo.setParentId(1L);
        anotherLevelTwo.setPoolType("credit_bond");
        anotherLevelTwo.setPoolLevel(2);
        anotherLevelTwo.setInnerSort(2);
        anotherLevelTwo.setStatus("enabled");
        fixture.shared.getPoolMap().put(20L, anotherLevelTwo);
        when(fixture.gradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(anyString(), anyString()))
                .thenReturn(Arrays.asList(3L, 20L));

        assertThat(fixture.matches(2)).isTrue();
    }

    /** 唯一准入四级或五级不满足简易范围。 */
    @Test
    public void shouldRejectSoleLevelOutsideOneToThree() {
        // 分别检查唯一四级和唯一五级
        Fixture levelFour = createFixture("2+", "2年", 4);
        // 构建唯一五级案例
        Fixture levelFive = createFixture("2+", "2年", 5);

        assertThat(levelFour.matches(4)).isFalse();
        assertThat(levelFive.matches(5)).isFalse();
    }

    /** 目标池必须就是按规则唯一允许的等级。 */
    @Test
    public void shouldRejectTargetDifferentFromSoleAllowedLevel() {
        // 二级唯一允许，一级不能套用其简易资格
        Fixture fixture = createFixture("2+", "2年", 2);

        assertThat(fixture.matches(1)).isFalse();
    }

    /** 唯一允许等级相同也不能替代标准矩阵对目标池本身的准入要求。 */
    @Test
    public void shouldRejectTargetSharingLevelWithDifferentAllowedPool() {
        // 矩阵只允许同属二级的另一个池，本次目标池不在矩阵中
        Fixture fixture = createFixture("2+", "2年", 2);
        // 将唯一允许池切换为另一个二级池
        allowAlternativeLevelTwoOnly(fixture);

        assertThat(fixture.matches(2)).isFalse();
    }

    /** 根池伪造同级序号也不能套用分级叶子库的简易资格。 */
    @Test
    public void shouldRejectRootTargetEvenIfItsSortMatchesSoleAllowedLevel() {
        // 有效二级叶子提供唯一等级，本次目标被设置为根节点
        Fixture fixture = createFixture("2+", "2年", 2);
        // 保留真正允许的二级叶子
        allowAlternativeLevelTwoOnly(fixture);
        fixture.shared.getPoolMap().get(3L).setPoolLevel(1);

        assertThat(fixture.matches(2)).isFalse();
    }

    /** 标准主体内评三档恰好一年仍可走简易流程。 */
    @Test
    public void standardGradeThreeShouldAllowExactlyOneYear() {
        // 构建期限边界上的标准三档券
        Fixture fixture = createFixture("3", "1年", 3);

        assertThat(fixture.matches(3)).isTrue();
    }

    /** 标准主体内评三档超过一年不得走简易流程。 */
    @Test
    public void standardGradeThreeShouldRejectOneYearAndOneDay() {
        // 多一天即超过一年上限
        Fixture fixture = createFixture("3", "1年1天", 3);

        assertThat(fixture.matches(3)).isFalse();
    }

    /** 标准主体内评三档无法解析期限时不能满足一年上限。 */
    @Test
    public void standardGradeThreeShouldRejectUnknownTerm() {
        // 矩阵按最长档仍有允许池，但简易期限条件不能放行
        Fixture fixture = createFixture("3", "未知", 3);

        assertThat(fixture.matches(3)).isFalse();
    }

    /** 一年上限仅针对标准主体内评三档，不扩大到其他评级。 */
    @Test
    public void standardOtherGradeShouldHaveNoExtraOneYearLimit() {
        // 三加档不属于新增的一年限制
        Fixture fixture = createFixture("3+", "6年", 3);

        assertThat(fixture.matches(3)).isTrue();
    }

    /** 主体一档永续债只允许从标准最好等级降一级后的唯一等级。 */
    @Test
    public void perpetualGradeOneShouldUseStandardBaseBeforeDowngrade() {
        // 标准二级降到三级，最终唯一三级可走简易流程
        Fixture fixture = createFixture("1", "2年", 2);
        fixture.security.setYxFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(3)).isTrue();
    }

    /** 永续债不能直接选择尚未降级的标准基准池。 */
    @Test
    public void perpetualGradeOneShouldRejectUndowngradedTarget() {
        // 标准二级不是降级后的三级
        Fixture fixture = createFixture("1", "2年", 2);
        fixture.security.setYxFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(2)).isFalse();
    }

    /** 非一档永续债允许降一级及更差，包含多个等级时不得走简易流程。 */
    @Test
    public void perpetualOtherGradeShouldRejectMultipleAllowedLevels() {
        // 标准一级按至少降一级展开二至五级
        Fixture fixture = createFixture("2+", "2年", 1);
        fixture.security.setYxFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(2)).isFalse();
    }

    /** ABS 覆盖分类仍不能绕过原始永续标志要求的降级约束。 */
    @Test
    public void absClassificationShouldNotSkipRawPerpetualDowngradeRequirement() {
        // ABS 一档只准一级，但原始永续要求标准一级降到二级
        Fixture fixture = createFixture("1", "2年", 1);
        fixture.security.setAbsFlag(1);
        fixture.security.setInnerGuarantorRating("1");
        fixture.security.setYxFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(1)).isFalse();
    }

    /** 主体一档次级债仅一级可走简易流程，不能把标准基准直接加一。 */
    @Test
    public void subordinatedGradeOneShouldAllowOnlyLevelOne() {
        // 标准矩阵二级，经次级一档规则限制为一级
        Fixture fixture = createFixture("1", "2年", 2);
        fixture.security.setCjFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(1)).isTrue();
    }

    /** 次级二加、二档都按标准基准降一级，不受二减档五年附加限制。 */
    @Test
    public void subordinatedGradeTwoAndTwoPlusShouldAllowExactDowngrade() {
        // 分别验证二加、二档的降级规则
        for (String grade : Arrays.asList("2+", "2")) {
            // 标准二级降到三级，期限六年不触发二减限制
            Fixture fixture = createFixture(grade, "6年", 2);
            fixture.security.setCjFlag(1);
            fixture.security.setStdCreditFlag(0);

            assertThat(fixture.matches(3)).as("主体内评 %s", grade).isTrue();
        }
    }

    /** 次级二减档恰好五年且标准二级降到三级时允许简易流程。 */
    @Test
    public void subordinatedGradeTwoMinusShouldAllowExactlyFiveYears() {
        // 构建五年边界案例
        Fixture fixture = createFixture("2-", "5年", 2);
        fixture.security.setCjFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(3)).isTrue();
    }

    /** 次级二减档超过五年不得走简易流程，即使最终唯一三级。 */
    @Test
    public void subordinatedGradeTwoMinusShouldRejectFiveYearsAndOneDay() {
        // 多一天即超过次级二减上限
        Fixture fixture = createFixture("2-", "5年1天", 2);
        fixture.security.setCjFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(3)).isFalse();
    }

    /** 次级二减档期限未知不能通过五年上限。 */
    @Test
    public void subordinatedGradeTwoMinusShouldRejectUnknownTerm() {
        // 矩阵最长档准入不等于满足次级简易期限上限
        Fixture fixture = createFixture("2-", "未知", 2);
        fixture.security.setCjFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(3)).isFalse();
    }

    /** 次级其他评级允许降级及更差，不能只取当前目标判断唯一。 */
    @Test
    public void subordinatedOtherGradeShouldRejectMultipleAllowedLevels() {
        // 标准一级按至少降一级展开二至五级
        Fixture fixture = createFixture("3", "2年", 1);
        fixture.security.setCjFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(2)).isFalse();
    }

    /** ABS 的最终单级限制不能覆盖原始次级标志的降级约束。 */
    @Test
    public void absClassificationShouldNotSkipRawSubordinatedDowngradeRequirement() {
        // ABS 内评一档仅允许一级，但主体二档次级要求标准一级降至二级
        Fixture fixture = createFixture("2", "2年", 1);
        fixture.security.setAbsFlag(1);
        fixture.security.setInnerGuarantorRating("1");
        fixture.security.setCjFlag(1);
        fixture.security.setStdCreditFlag(0);

        assertThat(fixture.matches(1)).isFalse();
    }

    /** 标准分类只认汇总标识，不再次判断条款标识。 */
    @Test
    public void standardClassificationShouldUseSummaryFlagRegardlessOfClauseFlag() {
        // 条款字段空值由标准信用债汇总标识承接
        Fixture fixture = createFixture("2+", "2年", 2);
        fixture.security.setStdClauseFlag(null);

        assertThat(fixture.isStandard()).isTrue();
        fixture.security.setStdClauseFlag(0);
        assertThat(fixture.isStandard()).isTrue();
    }

    /** 标准汇总标识为零或为空时不推断为标准券。 */
    @Test
    public void standardClassificationShouldRequireSummaryFlagOne() {
        // 缺少汇总标识时不能推断标准
        Fixture missing = createFixture("2+", "2年", 2);
        missing.security.setStdCreditFlag(null);
        // 汇总标识为零保持非标准
        Fixture nonstandard = createFixture("2+", "2年", 2);
        nonstandard.security.setStdCreditFlag(0);
        // 非枚举标准值同样不能推断为标准
        Fixture invalid = createFixture("2+", "2年", 2);
        invalid.security.setStdCreditFlag(2);
        assertThat(missing.isStandard()).isFalse();
        assertThat(nonstandard.isStandard()).isFalse();
        assertThat(invalid.isStandard()).isFalse();
    }

    /** 担保债标准标识为一时仍按标准券增加相应期限限制。 */
    @Test
    public void standardClassificationShouldKeepGuaranteedBondWhenSummaryFlagIsOne() {
        // 担保属性由上游汇总标识处理，不在本入口重复排除
        Fixture fixture = createFixture("3", "2年", 3);
        fixture.security.setGuarantFlag(1);

        assertThat(fixture.isStandard()).isTrue();
        List<String> failures = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(fixture.service, "checkSimpleInboundSpecialConditions",
                fixture.shared, 3, 3, failures);
        assertThat(failures).isNotEmpty();
    }

    /** 观察状态只影响当前准入集合，不改变已有标准汇总标识。 */
    @Test
    public void standardClassificationShouldKeepSummaryFlagUnderWatchStatuses() {
        // 同时存在观察状态时仍由标准汇总标识判断标准分类
        Fixture fixture = createFixture("2+", "2年", 2);
        fixture.shared.setSecurityInObservePool(true);
        fixture.shared.setIssuerInObservePool(true);
        fixture.shared.setSecurityInRestrictedPool(true);
        fixture.shared.setIssuerInRestrictedPool(true);

        assertThat(fixture.isStandard()).isTrue();
    }

    /** 非简易入库历史和报告失效时，提交准备阶段不重新判断简易资格。 */
    @Test
    public void submitPreparationShouldNotRecheckRecentInboundHistoryOrReport() {
        // 模拟下一步校验后，非简易入库历史和报告条件均已不满足
        Fixture fixture = createFixture("2+", "2年", 2);
        when(fixture.mapper.queryIssuerHasNonSimpleCreditBondInboundWithinDays("SIMPLE001.IB", 180))
                .thenReturn(false);
        when(fixture.mapper.queryIssuerHasRecentCreditReportWithinDays("SIMPLE001.IB", 180)).thenReturn(false);
        // 执行实际提交准备入口，确认不因简易历史条件失效而拒绝
        assertThat(fixture.prepareSubmit()).isNotNull();

        verify(fixture.mapper, never()).queryIssuerHasNonSimpleCreditBondInboundWithinDays("SIMPLE001.IB", 180);
        verify(fixture.mapper, never()).queryIssuerHasRecentCreditReportWithinDays("SIMPLE001.IB", 180);
    }

    /** 准入层级变为多个时，提交准备阶段不重新计算简易唯一性。 */
    @Test
    public void submitPreparationShouldNotRecheckChangedAdmissionLevels() {
        // 模拟下一步校验后，矩阵变为二级和四级两个允许层级
        Fixture fixture = createFixture("2+", "2年", 2, 4);
        // 执行实际提交准备入口，确认不会重查简易准入矩阵
        assertThat(fixture.prepareSubmit()).isNotNull();

        verify(fixture.gradeRuleMapper, never()).queryAllowedPoolIdsByGradeAndBucket(anyString(), anyString());
    }

    /** 将矩阵唯一允许池切换成同属二级的另一个ID，以核验目标本身的准入。 */
    private void allowAlternativeLevelTwoOnly(Fixture fixture) {
        InvestmentPoolBo allowedPool = new InvestmentPoolBo();
        allowedPool.setId(20L);
        allowedPool.setParentId(1L);
        allowedPool.setPoolType("credit_bond");
        allowedPool.setPoolLevel(2);
        allowedPool.setInnerSort(2);
        allowedPool.setStatus("enabled");
        fixture.shared.getPoolMap().put(20L, allowedPool);
        when(fixture.gradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(anyString(), anyString()))
                .thenReturn(Collections.singletonList(20L));
    }

    /** 构建覆盖一至五级池及四个期限档的独立测试上下文。 */
    private Fixture createFixture(String issuerGrade, String termText, int... matrixLevels) {
        Fixture fixture = new Fixture();
        fixture.service = new SecurityPoolAdjustService();
        fixture.mapper = mock(SecurityPoolAdjustMapper.class);
        fixture.gradeRuleMapper = mock(CreditBondGradeRuleMapper.class);
        ReflectionTestUtils.setField(fixture.service, "securityPoolAdjustMapper", fixture.mapper);
        ReflectionTestUtils.setField(fixture.service, "creditBondGradeRuleMapper", fixture.gradeRuleMapper);
        fixture.security = new SecurityInfoBo();
        fixture.security.setWindCode("SIMPLE001.IB");
        fixture.security.setSecurityType("bond");
        fixture.security.setIssueType("公募");
        fixture.security.setInnerIssuerRating(issuerGrade);
        fixture.security.setDateExistsStr(termText);
        fixture.security.setStdCreditFlag(1);
        fixture.security.setStdClauseFlag(1);
        fixture.shared = new AdjustSharedData();
        fixture.shared.setSecurityInfo(fixture.security);
        fixture.shared.setCurrentPoolIds(Collections.emptySet());
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        for (int level = 1; level <= 5; level++) {
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setId((long) level + 1);
            pool.setParentId(1L);
            pool.setPoolType("credit_bond");
            pool.setPoolLevel(2);
            pool.setInnerSort(level);
            pool.setStatus("enabled");
            poolMap.put(pool.getId(), pool);
        }
        fixture.shared.setPoolMap(poolMap);
        List<Long> matrixIds = new ArrayList<>();
        for (int level : matrixLevels) {
            matrixIds.add((long) level + 1);
        }
        when(fixture.gradeRuleMapper.queryAllowedPoolIdsByGradeAndBucket(anyString(), anyString()))
                .thenReturn(matrixIds);
        // 按现有四档构建期限规则，边界全部使用年口径
        when(fixture.gradeRuleMapper.queryEnabledTermBucketList()).thenReturn(Arrays.asList(
                createTermBucket("LE_1", "0", true, "1", true),
                createTermBucket("GT_1_LE_3", "1", false, "3", true),
                createTermBucket("GT_3_LE_5", "3", false, "5", true),
                createTermBucket("GT_5", "5", false, null, false)));
        List<CreditBondInnerRatingGradeBo> grades = new ArrayList<>();
        List<String> gradeCodes = Arrays.asList("1", "2+", "2", "2-", "3+", "3", "3-", "4");
        for (int index = 0; index < gradeCodes.size(); index++) {
            CreditBondInnerRatingGradeBo grade = new CreditBondInnerRatingGradeBo();
            grade.setGradeCode(gradeCodes.get(index));
            grade.setSortNo(index + 1);
            grades.add(grade);
        }
        when(fixture.gradeRuleMapper.queryEnabledRatingGradeList()).thenReturn(grades);
        when(fixture.mapper.queryIssuerHasNonSimpleCreditBondInboundWithinDays("SIMPLE001.IB", 180))
                .thenReturn(true);
        when(fixture.mapper.queryIssuerHasRecentCreditReportWithinDays("SIMPLE001.IB", 180)).thenReturn(true);
        return fixture;
    }

    /** 构建指定区间的年期限档。 */
    private CreditBondTermBucketBo createTermBucket(String code, String min, boolean minInclusive,
                                                   String max, boolean maxInclusive) {
        CreditBondTermBucketBo bucket = new CreditBondTermBucketBo();
        bucket.setBucketCode(code);
        bucket.setMinTermYear(min == null ? null : new BigDecimal(min));
        bucket.setMinInclusive(minInclusive ? 1 : 0);
        bucket.setMaxTermYear(max == null ? null : new BigDecimal(max));
        bucket.setMaxInclusive(maxInclusive ? 1 : 0);
        return bucket;
    }

    /** 每个案例隔离证券、矩阵与匹配原因，便于覆盖多个规则分支。 */
    private static class Fixture {

        /** 本次测试的证券池调整服务 */
        private SecurityPoolAdjustService service;
        /** 180天历史及报告查询组件 */
        private SecurityPoolAdjustMapper mapper;
        /** 标准评级矩阵查询组件 */
        private CreditBondGradeRuleMapper gradeRuleMapper;
        /** 当前证券主档 */
        private SecurityInfoBo security;
        /** 当前证券校验共享数据 */
        private AdjustSharedData shared;
        /** 本次简易流程命中原因 */
        private final List<String> matchReasons = new ArrayList<>();
        /** 本次简易流程未命中原因 */
        private final List<String> unmatchReasons = new ArrayList<>();

        /** 按指定目标层级调用简易流程实际判断入口。 */
        private boolean matches(int targetLevel) {
            AdjustCheckReq req = new AdjustCheckReq();
            req.setSecurityCode(security.getWindCode());
            Boolean result = ReflectionTestUtils.invokeMethod(service, "isSimpleInboundFlowMatched",
                    req, shared, shared.getPoolMap().get((long) targetLevel + 1), matchReasons, unmatchReasons);
            return Boolean.TRUE.equals(result);
        }

        /** 调用标准分类入口，验证上游汇总标识的权威性。 */
        private boolean isStandard() {
            Boolean result = ReflectionTestUtils.invokeMethod(service, "isStandardCreditBond", security);
            return Boolean.TRUE.equals(result);
        }

        /** 调用实际提交准备入口，保留其他提交检查，不创建调库记录。 */
        private Object prepareSubmit() {
            InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
            when(poolMapper.queryPoolList()).thenReturn(new ArrayList<>(shared.getPoolMap().values()));
            when(mapper.querySecurityBoByCode(security.getWindCode())).thenReturn(security);
            when(mapper.querySecurityCurrentPoolIdList(security.getWindCode()))
                    .thenReturn(new ArrayList<>(shared.getCurrentPoolIds()));

            SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
            req.setSecurityCode(security.getWindCode());
            SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
            item.setItemTag("manual");
            item.setAdjustMode("调入");
            item.setTargetPoolId(3L);
            item.setFlowType("simpleInbound");
            req.setItems(Collections.singletonList(item));
            return ReflectionTestUtils.invokeMethod(service, "prepareSubmitSharedData",
                    req, new SecurityPoolAdjustService.BatchNoContext());
        }
    }
}
