package com.znty.rrs.service;

import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.StockSecurityBatchAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 存量证券批量调整服务测试
 */
public class StockSecurityBatchAdjustServiceTest {

    /** 调入未选来源池时应拒绝 */
    @Test(expected = BizException.class)
    public void validateSecurityPageReqShouldRequireSourcePoolForInbound() {
        StockSecurityBatchAdjustMapper mapper = mock(StockSecurityBatchAdjustMapper.class);
        StockSecurityBatchAdjustService service = new StockSecurityBatchAdjustService();
        ReflectionTestUtils.setField(service, "stockSecurityBatchAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(33L)).thenReturn(1);

        StockSecurityBatchAdjustReq req = new StockSecurityBatchAdjustReq();
        req.setPoolId(33L);
        req.setDirection("in");
        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
    }

    /** 调出不校验来源池参数 */
    @Test
    public void validateSecurityPageReqShouldIgnoreSourcePoolForOutbound() {
        StockSecurityBatchAdjustMapper mapper = mock(StockSecurityBatchAdjustMapper.class);
        StockSecurityBatchAdjustService service = new StockSecurityBatchAdjustService();
        ReflectionTestUtils.setField(service, "stockSecurityBatchAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(33L)).thenReturn(1);

        StockSecurityBatchAdjustReq req = new StockSecurityBatchAdjustReq();
        req.setPoolId(33L);
        req.setDirection("out");
        req.setSourcePoolIds(Collections.singletonList(99999L));

        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
    }

}
