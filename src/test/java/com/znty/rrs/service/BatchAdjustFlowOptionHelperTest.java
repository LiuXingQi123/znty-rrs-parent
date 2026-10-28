package com.znty.rrs.service;

import com.znty.rrs.common.util.BatchAdjustFlowOptionHelper;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 批量流程追加规则测试 */
public class BatchAdjustFlowOptionHelperTest {

    @Test
    public void shouldPreserveFastChoiceAndExemptionWhenBatchUsesSameDefinition() {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setBatchInFlowId(10L);
        pool.setBatchInFlowKey("bond:direct");
        pool.setBatchInFlowName("债券批量入库流程");
        AdjustCheckDto.FlowOption fast = new AdjustCheckDto.FlowOption();
        fast.setFlowId(10L);
        fast.setFlowKey("bond:direct");
        fast.setFlowType("simpleInbound");
        fast.setRecommended(true);
        fast.setSelectable(true);
        fast.setMaterialExemptionType("productSource");
        AdjustCheckDto.FlowOption normal = new AdjustCheckDto.FlowOption();
        normal.setFlowType("normalInbound");
        normal.setSelectable(true);

        List<AdjustCheckDto.FlowOption> result = BatchAdjustFlowOptionHelper.appendConfiguredBatchFlow(
                Arrays.asList(fast, normal), pool, "调入");

        assertEquals(3, result.size());
        assertEquals("batchInbound", result.get(0).getFlowType());
        assertEquals("债券批量入库流程", result.get(0).getFlowName());
        assertTrue(result.get(0).isRecommended());
        assertNull(result.get(0).getMaterialExemptionType());
        assertEquals("simpleInbound", result.get(1).getFlowType());
        assertEquals("productSource", result.get(1).getMaterialExemptionType());
        assertFalse(result.get(1).isRecommended());
        assertFalse(result.get(2).isRecommended());
        assertTrue(fast.isRecommended());
        assertNotSame(fast, result.get(1));
        assertEquals(3, BatchAdjustFlowOptionHelper.appendConfiguredBatchFlow(result, pool, "调入").size());
    }

    @Test
    public void shouldAppendOutboundUsingConfiguredKeyWithoutId() {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setBatchOutFlowKey("bond:batch-outbound");
        pool.setBatchOutFlowName("债券批量出库流程");
        pool.setBatchInFlowId(20L);
        List<AdjustCheckDto.FlowOption> result = BatchAdjustFlowOptionHelper.appendConfiguredBatchFlow(
                Collections.emptyList(), pool, "调出");
        assertEquals(1, result.size());
        assertEquals("batchOutbound", result.get(0).getFlowType());
        assertEquals("bond:batch-outbound", result.get(0).getFlowKey());
        assertNull(result.get(0).getFlowId());
        assertTrue(result.get(0).isSelectable());
    }

    @Test
    public void shouldKeepSingleRecommendationWhenBatchNotConfigured() {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setBatchInFlowName("仅名称不代表已配置流程");
        pool.setBatchInFlowKey(" ");
        AdjustCheckDto.FlowOption normal = new AdjustCheckDto.FlowOption();
        normal.setFlowType("normalInbound");
        normal.setRecommended(true);
        List<AdjustCheckDto.FlowOption> result = BatchAdjustFlowOptionHelper.appendConfiguredBatchFlow(
                Collections.singletonList(normal), pool, "调入");
        assertEquals(1, result.size());
        assertEquals("normalInbound", result.get(0).getFlowType());
        assertTrue(result.get(0).isRecommended());
    }
}
