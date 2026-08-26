package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 信用债 1～5 级准入：类型覆盖、特殊债/担保债/观察池/矩阵点名池。
 */
public class CreditBondSpecialInboundRuleTest {

    @Test
    public void shouldIdentifyBondTypes() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssueType("私募");
        sec.setYxFlag(1);
        sec.setCjFlag(1);
        sec.setAbsFlag(1);
        sec.setGuarantFlag(1);
        sec.setInrightFlag(1);
        assertThat(CreditBondSpecialInboundRule.isPrivateBond(sec)).isTrue();
        assertThat(CreditBondSpecialInboundRule.isPerpetual(sec)).isTrue();
        assertThat(CreditBondSpecialInboundRule.isSubordinated(sec)).isTrue();
        assertThat(CreditBondSpecialInboundRule.isAbs(sec)).isTrue();
        assertThat(CreditBondSpecialInboundRule.isGuaranteed(sec)).isTrue();
        assertThat(CreditBondSpecialInboundRule.isInright(sec)).isTrue();

        SecurityInfoBo byType = new SecurityInfoBo();
        byType.setSecurityType("abs");
        assertThat(CreditBondSpecialInboundRule.isAbs(byType)).isTrue();
        byType.setSecurityType("abs_all");
        assertThat(CreditBondSpecialInboundRule.isAbs(byType)).isFalse();
    }

    @Test
    public void exclusiveMemoOverwritesPrivateThenSubordinatedThenPerpetualThenGuarantee() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssueType("私募");
        assertThat(CreditBondSpecialInboundRule.resolveExclusiveMemo(sec))
                .isEqualTo(CreditBondSpecialInboundRule.MEMO_PRIVATE);
        sec.setCjFlag(1);
        assertThat(CreditBondSpecialInboundRule.resolveExclusiveMemo(sec))
                .isEqualTo(CreditBondSpecialInboundRule.MEMO_SUBORDINATED);
        sec.setYxFlag(1);
        assertThat(CreditBondSpecialInboundRule.resolveExclusiveMemo(sec))
                .isEqualTo(CreditBondSpecialInboundRule.MEMO_PERPETUAL);
        sec.setGuarantFlag(1);
        assertThat(CreditBondSpecialInboundRule.resolveExclusiveMemo(sec))
                .isEqualTo(CreditBondSpecialInboundRule.MEMO_GUARANTEED);
    }

    @Test
    public void privateAndAbsIssuerGradeOneAllowsOnlyLevelOne() {
        SecurityInfoBo priv = new SecurityInfoBo();
        priv.setIssueType("私募债");
        priv.setInnerIssuerRating("1");
        CreditBondSpecialInboundRule.GradedInboundMode privMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(priv, false);
        assertThat(privMode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.LEVEL_ONE_ONLY);
        assertThat(privMode.usesSort()).isTrue();
        assertThat(privMode.resolveStartSort(1)).isEqualTo(1);
        assertThat(privMode.allows(1, 1)).isTrue();
        assertThat(privMode.allows(5, 1)).isFalse();

        SecurityInfoBo abs = new SecurityInfoBo();
        abs.setAbsFlag(1);
        abs.setInnerIssuerRating("1");
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(abs, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_CEILING);
        abs.setInnerGuarantorRating("1");
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(abs, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.LEVEL_ONE_ONLY);
    }

    @Test
    public void perpetualIssuerGradeOneDowngradesExactlyOneLevel() {
        SecurityInfoBo perpetual = new SecurityInfoBo();
        perpetual.setYxFlag(1);
        perpetual.setInnerIssuerRating("1");
        CreditBondSpecialInboundRule.GradedInboundMode mode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(perpetual, false);
        assertThat(mode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_EXACT);
        Integer start = mode.resolveStartSort(1);
        assertThat(start).isEqualTo(2);
        assertThat(mode.allows(2, start)).isTrue();
        assertThat(mode.allows(1, start)).isFalse();
        assertThat(mode.allows(3, start)).isFalse();
        assertThat(mode.describeRange(start)).isEqualTo("仅 2 级");
    }

    @Test
    public void subordinatedSplitsThreeWaysByIssuerGrade() {
        SecurityInfoBo sub = new SecurityInfoBo();
        sub.setCjFlag(1);
        sub.setInnerIssuerRating("1");
        CreditBondSpecialInboundRule.GradedInboundMode oneMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(sub, false);
        assertThat(oneMode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.LEVEL_ONE_ONLY);
        Integer oneStart = oneMode.resolveStartSort(1);
        assertThat(oneStart).isEqualTo(1);
        assertThat(oneMode.allows(1, oneStart)).isTrue();
        assertThat(oneMode.allows(2, oneStart)).isFalse();
        assertThat(oneMode.describeRange(oneStart)).isEqualTo("仅 1 级");

        sub.setInnerIssuerRating("2+");
        CreditBondSpecialInboundRule.GradedInboundMode twoMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(sub, false);
        assertThat(twoMode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_EXACT);
        assertThat(twoMode.resolveStartSort(2)).isEqualTo(3);

        sub.setInnerIssuerRating("2");
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(sub, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_EXACT);

        sub.setInnerIssuerRating("2-");
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(sub, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_EXACT);

        sub.setInnerIssuerRating("3");
        CreditBondSpecialInboundRule.GradedInboundMode restMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(sub, false);
        assertThat(restMode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_CEILING);
        assertThat(restMode.resolveStartSort(3)).isEqualTo(4);
    }

    @Test
    public void specialNotIssuerGradeOneDowngradesAtLeastOneLevel() {
        SecurityInfoBo priv = new SecurityInfoBo();
        priv.setIssueType("私募债");
        priv.setInnerIssuerRating("2");
        CreditBondSpecialInboundRule.GradedInboundMode mode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(priv, false);
        assertThat(mode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_CEILING);
        assertThat(mode.usesSort()).isTrue();
        assertThat(mode.resolveStartSort(1)).isEqualTo(2);
        assertThat(mode.resolveStartSort(2)).isEqualTo(3);
        assertThat(mode.allows(2, 2)).isTrue();
        assertThat(mode.allows(5, 2)).isTrue();
        assertThat(mode.allows(1, 2)).isFalse();

        SecurityInfoBo perpetual = new SecurityInfoBo();
        perpetual.setYxFlag(1);
        perpetual.setInnerIssuerRating("2");
        CreditBondSpecialInboundRule.GradedInboundMode perpetualMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(perpetual, false);
        assertThat(perpetualMode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_CEILING);
        assertThat(perpetualMode.resolveStartSort(2)).isEqualTo(3);
    }

    @Test
    public void absOneGradeUsesGuarantorRatingNotIssuer() {
        SecurityInfoBo abs = new SecurityInfoBo();
        abs.setAbsFlag(1);
        abs.setGuarantFlag(1);
        abs.setInnerIssuerRating("2");
        abs.setInnerGuarantorRating("1");
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(abs, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.LEVEL_ONE_ONLY);

        abs.setInnerIssuerRating("1");
        abs.setInnerGuarantorRating("3");
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(abs, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_CEILING);

        abs.setInnerGuarantorRating(null);
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(abs, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_CEILING);
    }

    @Test
    public void guaranteeAndObserveUseBestAndWorseWithoutExtraDowngrade() {
        SecurityInfoBo guaranteed = new SecurityInfoBo();
        guaranteed.setGuarantFlag(1);
        CreditBondSpecialInboundRule.GradedInboundMode guaranteedMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(guaranteed, false);
        assertThat(guaranteedMode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.BEST_AND_WORSE);
        assertThat(guaranteedMode.usesSort()).isTrue();
        assertThat(guaranteedMode.resolveStartSort(2)).isEqualTo(2);

        SecurityInfoBo normal = new SecurityInfoBo();
        CreditBondSpecialInboundRule.GradedInboundMode observeMode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(normal, true);
        assertThat(observeMode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.BEST_AND_WORSE);
        assertThat(observeMode.resolveStartSort(1)).isEqualTo(1);

        CreditBondSpecialInboundRule.GradedInboundMode ordinary =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(new SecurityInfoBo(), false);
        assertThat(ordinary.usesSort()).isFalse();
        assertThat(ordinary.resolveStartSort(1)).isNull();
    }

    @Test
    public void guaranteeOverridesPerpetualInBranchOrder() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssueType("私募");
        sec.setCjFlag(1);
        sec.setYxFlag(1);
        sec.setGuarantFlag(1);
        sec.setInnerIssuerRating("1");
        assertThat(CreditBondSpecialInboundRule.isSpecialBranch(sec)).isFalse();
        CreditBondSpecialInboundRule.GradedInboundMode mode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(sec, false);
        assertThat(mode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.BEST_AND_WORSE);
        assertThat(mode.resolveStartSort(1)).isEqualTo(1);
        assertThat(mode.resolveStartSort(2)).isEqualTo(2);
    }

    @Test
    public void absStaysSpecialEvenWithGuarantee() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setAbsFlag(1);
        sec.setYxFlag(1);
        sec.setGuarantFlag(1);
        sec.setInnerIssuerRating("2");
        sec.setInnerGuarantorRating("1");
        assertThat(CreditBondSpecialInboundRule.isSpecialBranch(sec)).isTrue();
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(sec, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.LEVEL_ONE_ONLY);
        sec.setInnerGuarantorRating("2");
        assertThat(CreditBondSpecialInboundRule.resolveGradedInboundMode(sec, false))
                .isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_CEILING);
    }

    @Test
    public void observeDoesNotOverrideSpecialBranch() {
        SecurityInfoBo perpetual = new SecurityInfoBo();
        perpetual.setYxFlag(1);
        perpetual.setInnerIssuerRating("2");
        CreditBondSpecialInboundRule.GradedInboundMode mode =
                CreditBondSpecialInboundRule.resolveGradedInboundMode(perpetual, true);
        assertThat(mode).isEqualTo(CreditBondSpecialInboundRule.GradedInboundMode.DOWNGRADE_CEILING);
        assertThat(mode.resolveStartSort(2)).isEqualTo(3);
    }

    @Test
    public void convertibleExchangeableCrmwExcludedFromGradedPool() {
        SecurityInfoBo conv = new SecurityInfoBo();
        conv.setSecurityType("convertible_bond");
        assertThat(CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(conv)).isTrue();
        SecurityInfoBo exch = new SecurityInfoBo();
        exch.setSecurityType("exchangeable_bond");
        assertThat(CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(exch)).isTrue();
        SecurityInfoBo crmw = new SecurityInfoBo();
        crmw.setSecurityType("crmw");
        assertThat(CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(crmw)).isTrue();
        SecurityInfoBo corp = new SecurityInfoBo();
        corp.setSecurityType("corporate_bond");
        assertThat(CreditBondSpecialInboundRule.isExcludedFromCreditBondGradedPool(corp)).isFalse();
    }

    @Test
    public void restrictedBlocksNewInboundUnlessStrongGuarantee() {
        SecurityInfoBo sec = new SecurityInfoBo();
        assertThat(CreditBondSpecialInboundRule.checkRestricted(sec, true, null, 1))
                .contains("不得新增入库");
        sec.setGuarantFlag(1);
        sec.setInnerGuarantorRating("1");
        assertThat(CreditBondSpecialInboundRule.checkRestricted(sec, true, null, 1)).isNull();
    }

    @Test
    public void restrictedAlreadyInPoolCanOnlyGoToLevelFive() {
        SecurityInfoBo sec = new SecurityInfoBo();
        assertThat(CreditBondSpecialInboundRule.checkRestricted(sec, true, 2, 3))
                .contains("只能调入五级库");
        assertThat(CreditBondSpecialInboundRule.checkRestricted(sec, true, 2, 5)).isNull();
        assertThat(CreditBondSpecialInboundRule.checkRestricted(sec, true, 5, 3))
                .contains("不可上调");
        assertThat(CreditBondSpecialInboundRule.checkRestricted(sec, true, 5, 5)).isNull();
    }

    @Test
    public void gradedLevelPoolExcludesRoot() {
        InvestmentPoolBo root = new InvestmentPoolBo();
        root.setPoolType("credit_bond");
        root.setPoolLevel(1);
        root.setInnerSort(1);
        assertThat(CreditBondSpecialInboundRule.isGradedLevelPool(root)).isFalse();

        InvestmentPoolBo level2 = new InvestmentPoolBo();
        level2.setPoolType("credit_bond");
        level2.setPoolLevel(2);
        level2.setInnerSort(2);
        assertThat(CreditBondSpecialInboundRule.isGradedLevelPool(level2)).isTrue();

        InvestmentPoolBo offshore = new InvestmentPoolBo();
        offshore.setPoolType("offshore_bond");
        offshore.setPoolLevel(2);
        offshore.setInnerSort(2);
        assertThat(CreditBondSpecialInboundRule.isGradedLevelPool(offshore)).isFalse();
        assertThat(CreditBondSpecialInboundRule.isGradedBondPoolType("offshore_bond")).isFalse();
    }
}
