package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.StockSecurityBatchAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 存量证券批量调整服务测试
 */
public class StockSecurityBatchAdjustServiceTest {

    /** 未选来源池时应拒绝 */
    @Test(expected = BizException.class)
    public void validateSecurityPageReqShouldRequireSourcePool() {
        StockSecurityBatchAdjustMapper mapper = mock(StockSecurityBatchAdjustMapper.class);
        StockSecurityBatchAdjustService service = new StockSecurityBatchAdjustService();
        ReflectionTestUtils.setField(service, "stockSecurityBatchAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(33L)).thenReturn(1);

        StockSecurityBatchAdjustReq req = new StockSecurityBatchAdjustReq();
        req.setPoolId(33L);
        req.setDirection("in");
        req.setSourcePoolIds(Collections.<Long>emptyList());

        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
    }

    /** 来源池不在白名单时应拒绝 */
    @Test(expected = BizException.class)
    public void validateSecurityPageReqShouldRejectUnknownSourcePool() {
        StockSecurityBatchAdjustMapper mapper = mock(StockSecurityBatchAdjustMapper.class);
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        StockSecurityBatchAdjustService service = new StockSecurityBatchAdjustService();
        ReflectionTestUtils.setField(service, "stockSecurityBatchAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        when(mapper.queryEnabledLeafPoolCount(33L)).thenReturn(1);
        stubSourcePools(poolMapper);

        StockSecurityBatchAdjustReq req = new StockSecurityBatchAdjustReq();
        req.setPoolId(33L);
        req.setDirection("in");
        req.setSourcePoolIds(Collections.singletonList(99999L));

        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
    }

    /** 拆分来源池：CRMW 与普通池分别写入 */
    @Test
    public void prepareSourcePoolIdsShouldSplitCrmwAndNormal() {
        InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        StockSecurityBatchAdjustService service = new StockSecurityBatchAdjustService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        stubSourcePools(poolMapper);

        StockSecurityBatchAdjustReq req = new StockSecurityBatchAdjustReq();
        req.setSourcePoolIds(Arrays.asList(18L, 2L, 29L));

        ReflectionTestUtils.invokeMethod(service, "prepareSourcePoolIds", req);

        assertThat(req.getCrmwSourcePoolIds()).containsExactly(18L);
        assertThat(req.getNormalSourcePoolIds()).containsExactly(2L, 29L);
    }

    private void stubSourcePools(InvestmentPoolMapper poolMapper) {
        when(poolMapper.queryPoolByCode(anyString())).thenAnswer(invocation -> {
            String code = (String) invocation.getArguments()[0];
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setPoolCode(code);
            pool.setIsDeleted(0);
            if ("crmw_root".equals(code)) {
                pool.setId(18L);
                pool.setPoolName("CRMW库");
            } else if ("credit_bond_level_1".equals(code)) {
                pool.setId(2L);
                pool.setPoolName("一级库");
            } else if ("credit_bond_level_2".equals(code)) {
                pool.setId(3L);
                pool.setPoolName("二级库");
            } else if ("credit_bond_level_3".equals(code)) {
                pool.setId(4L);
                pool.setPoolName("三级库");
            } else if ("convertible_bond_core".equals(code)) {
                pool.setId(29L);
                pool.setPoolName("核心库");
            } else if ("convertible_bond_focus".equals(code)) {
                pool.setId(30L);
                pool.setPoolName("重点库");
            } else {
                return null;
            }
            return pool;
        });
    }
}
