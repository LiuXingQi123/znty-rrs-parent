package com.znty.sirm.service;

import com.znty.sirm.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.sirm.model.BatchSecurityPoolAdjustReq;
import com.znty.sirm.model.BatchSecurityPoolDto;
import com.znty.sirm.exception.BizException;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证券池批量调整服务测试
 */
public class BatchSecurityPoolAdjustServiceTest {

    /** 验证分页查询只统计当前页投资池数量并回填零值 */
    @Test
    public void fillPoolCurrentCountShouldQueryCurrentPagePoolIds() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);

        BatchSecurityPoolDto firstPool = new BatchSecurityPoolDto();
        firstPool.setId(11L);
        BatchSecurityPoolDto secondPool = new BatchSecurityPoolDto();
        secondPool.setId(12L);
        List<BatchSecurityPoolDto> poolList = Arrays.asList(firstPool, secondPool);

        BatchSecurityPoolDto firstPoolCount = new BatchSecurityPoolDto();
        firstPoolCount.setId(11L);
        firstPoolCount.setCurrentCount(3);
        when(mapper.queryPoolCurrentCountList(org.mockito.Matchers.anyListOf(Long.class)))
                .thenReturn(Collections.singletonList(firstPoolCount));

        ReflectionTestUtils.invokeMethod(service, "fillPoolCurrentCount", poolList);

        assertThat(poolList).extracting(BatchSecurityPoolDto::getCurrentCount)
                .containsExactly(3, 0);
        verify(mapper).queryPoolCurrentCountList(Arrays.asList(11L, 12L));
    }

    /** 验证未知市场编码交由查询条件处理 */
    @Test
    public void validateSecurityPageReqShouldAllowUnknownMarketCode() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(11L)).thenReturn(1);

        BatchSecurityPoolAdjustReq req = new BatchSecurityPoolAdjustReq();
        req.setPoolId(11L);
        req.setDirection("in");
        req.setMarketCodes(Collections.singletonList("UNKNOWN"));

        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);

        verify(mapper).queryEnabledLeafPoolCount(11L);
    }

    /** 验证未知调整方向仍应阻止查询范围扩大 */
    @Test(expected = BizException.class)
    public void validateSecurityPageReqShouldRejectUnknownDirection() {
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();

        BatchSecurityPoolAdjustReq req = new BatchSecurityPoolAdjustReq();
        req.setPoolId(11L);
        req.setDirection("unknown");

        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
    }
}
