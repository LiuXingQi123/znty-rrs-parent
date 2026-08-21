package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 特殊债分级入库规则：私募/永续/次级/ABS 下调、担保孰高、观察封顶、重点观察禁新增。
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
    }

    @Test
    public void perpetualAlwaysDowngradeAtLeastOneLevel() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setYxFlag(1);
        // 1 档也下调一级：矩阵最好档 1 → 2～5；最好档 2 → 3～5
        assertThat(CreditBondSpecialInboundRule.needsCeilingModel(sec, "1")).isTrue();
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("1", 1, sec)).isEqualTo(2);
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("3", 2, sec)).isEqualTo(3);
    }

    @Test
    public void privateAndAbsGradeOneStayAtMatrixWithoutDowngrade() {
        SecurityInfoBo priv = new SecurityInfoBo();
        priv.setIssueType("私募债");
        assertThat(CreditBondSpecialInboundRule.needsCeilingModel(priv, "1")).isFalse();
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("1", 1, priv)).isEqualTo(1);
        assertThat(CreditBondSpecialInboundRule.needsCeilingModel(priv, "2")).isTrue();
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("2", 1, priv)).isEqualTo(2);
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("2", 2, priv)).isEqualTo(3);

        SecurityInfoBo abs = new SecurityInfoBo();
        abs.setAbsFlag(1);
        assertThat(CreditBondSpecialInboundRule.needsCeilingModel(abs, "1")).isFalse();
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("1", 1, abs)).isEqualTo(1);
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("3", 2, abs)).isEqualTo(3);
    }

    @Test
    public void observeAndGuaranteeDoNotUseDowngradeCeiling() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setGuarantFlag(1);
        assertThat(CreditBondSpecialInboundRule.needsCeilingModel(sec, "2")).isFalse();
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("2", 2, sec)).isEqualTo(2);
        assertThat(CreditBondSpecialInboundRule.needsCeilingModel(new SecurityInfoBo(), "1")).isFalse();
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("1", 1, new SecurityInfoBo())).isEqualTo(1);
    }

    @Test
    public void overlapTakesStrictestCeiling() {
        SecurityInfoBo sec = new SecurityInfoBo();
        sec.setIssueType("私募");
        sec.setYxFlag(1);
        // 私募 1 档可进一级，永续仍降一级
        assertThat(CreditBondSpecialInboundRule.needsCeilingModel(sec, "1")).isTrue();
        assertThat(CreditBondSpecialInboundRule.resolveCeilingSort("1", 1, sec)).isEqualTo(2);
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
