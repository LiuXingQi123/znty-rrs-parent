package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 自动调库批次号上下文单元测试。 */
public class ScheduledAdjustBatchNoContextTest {

    /** 同一实际调整对象、目标池和方向应复用批次号。 */
    @Test
    public void resolveBatchNoShouldReuseSameBusinessGroup() {
        ScheduledAdjustBatchNoContext context = new ScheduledAdjustBatchNoContext();

        String first = context.resolveBondBatchNo("B001", 15L, AdjustMode.IN.getCode());
        String second = context.resolveBondBatchNo("B001", 15L, AdjustMode.IN.getCode());

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("BOND\\d{17}1001");
    }

    /** 不同债券、目标池或方向应分别生成批次号。 */
    @Test
    public void resolveBatchNoShouldSeparateDifferentBusinessGroups() {
        ScheduledAdjustBatchNoContext context = new ScheduledAdjustBatchNoContext();

        String firstBond = context.resolveBondBatchNo("B001", 15L, AdjustMode.IN.getCode());
        String secondBond = context.resolveBondBatchNo("B002", 15L, AdjustMode.IN.getCode());
        String anotherPool = context.resolveBondBatchNo("B001", 16L, AdjustMode.IN.getCode());
        String outbound = context.resolveBondBatchNo("B001", 15L, AdjustMode.OUT.getCode());

        assertThat(firstBond).endsWith("1001");
        assertThat(secondBond).endsWith("1002");
        assertThat(anotherPool).endsWith("1003");
        assertThat(outbound).endsWith("2001");
    }

    /** 主体和 CRMW 应使用各自业务前缀。 */
    @Test
    public void resolveBatchNoShouldUseBusinessPrefix() {
        ScheduledAdjustBatchNoContext context = new ScheduledAdjustBatchNoContext();

        String company = context.resolveCompanyBatchNo("C001", 17L, AdjustMode.IN.getCode());
        String crmw = context.resolveCrmwBatchNo("CRMW001", "B001", 18L, AdjustMode.OUT.getCode());

        assertThat(company).matches("COMP\\d{17}1001");
        assertThat(crmw).matches("CRMW\\d{17}2001");
    }

    /** 同一凭证绑定不同标的证券时应生成不同批次号。 */
    @Test
    public void resolveCrmwBatchNoShouldSeparateUnderlyingSecurity() {
        ScheduledAdjustBatchNoContext context = new ScheduledAdjustBatchNoContext();

        String first = context.resolveCrmwBatchNo("CRMW001", "B001", 18L, AdjustMode.OUT.getCode());
        String second = context.resolveCrmwBatchNo("CRMW001", "B002", 18L, AdjustMode.OUT.getCode());

        assertThat(first).endsWith("2001");
        assertThat(second).endsWith("2002");
    }
}
