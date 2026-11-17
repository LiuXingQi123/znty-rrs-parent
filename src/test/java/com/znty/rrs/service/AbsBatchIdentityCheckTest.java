package com.znty.rrs.service;

import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustSubmitReq;
import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.rrs.mapper.StockSecurityBatchAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/** 两个批量入口的同批 ABS 不同主体应返回逐项不可调整，不中断整批校验 */
public class AbsBatchIdentityCheckTest {

    @Test
    public void ordinaryBatchShouldBlockBothConflictingSelectionsAndKeepOtherSecurity() {
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(101L)).thenReturn(1);
        // 批量编排应透传校验服务的主体冲突原因
        prepareIdentity(service);
        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setCurrentUserId("1");
        req.setPoolId(101L);
        req.setDirection("in");
        BatchSecurityInboundAdjustReq.SecurityItem first = new BatchSecurityInboundAdjustReq.SecurityItem();
        first.setSecurityCode("ABS1");
        first.setRightsHolderCode("A");
        BatchSecurityInboundAdjustReq.SecurityItem second = new BatchSecurityInboundAdjustReq.SecurityItem();
        second.setSecurityCode("ABS1");
        second.setSelfSelectedRightsHolderCode("B");
        BatchSecurityInboundAdjustReq.SecurityItem other = new BatchSecurityInboundAdjustReq.SecurityItem();
        other.setSecurityCode("OTHER");
        req.setSecurities(Arrays.asList(first, second, other));

        BatchSecurityInboundAdjustDto result = service.checkAdjust(req);

        assertThat(result.getItems()).hasSize(3);
        assertThat(result.getItems().subList(0, 2)).allSatisfy(item -> {
            assertThat(item.isCanAdjust()).isFalse();
            assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("同批申请中选择了不同主体"));
        });
        assertThat(result.getItems().get(2).isCanAdjust()).isTrue();
    }

    @Test
    public void stockBatchShouldBlockBothConflictingSelectionsAndKeepOtherSecurity() {
        StockSecurityBatchAdjustService service = new StockSecurityBatchAdjustService();
        StockSecurityBatchAdjustMapper mapper = mock(StockSecurityBatchAdjustMapper.class);
        ReflectionTestUtils.setField(service, "stockSecurityBatchAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(101L)).thenReturn(1);
        // 存量入口应整批传入并保留逐项失败原因
        prepareIdentity(service);
        StockSecurityBatchAdjustSubmitReq req = new StockSecurityBatchAdjustSubmitReq();
        req.setCurrentUserId("1");
        req.setPoolId(101L);
        req.setDirection("out");
        StockSecurityBatchAdjustSubmitReq.SecurityItem first = new StockSecurityBatchAdjustSubmitReq.SecurityItem();
        first.setSecurityCode("ABS1");
        first.setRightsHolderCode("A");
        StockSecurityBatchAdjustSubmitReq.SecurityItem second = new StockSecurityBatchAdjustSubmitReq.SecurityItem();
        second.setSecurityCode("ABS1");
        second.setRightsHolderCode("B");
        StockSecurityBatchAdjustSubmitReq.SecurityItem other = new StockSecurityBatchAdjustSubmitReq.SecurityItem();
        other.setSecurityCode("OTHER");
        req.setSecurities(Arrays.asList(first, second, other));

        StockSecurityBatchAdjustDto result = service.checkAdjust(req);

        BatchSecurityCheckService batch = (BatchSecurityCheckService)
                ReflectionTestUtils.getField(service, "batchSecurityCheckService");
        verify(batch).checkSecurityBatch(anyList(), eq(false));

        assertThat(result.getItems()).hasSize(3);
        assertThat(result.getItems().subList(0, 2)).allSatisfy(item -> {
            assertThat(item.isCanAdjust()).isFalse();
            assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("同批申请中选择了不同主体"));
        });
        assertThat(result.getItems().get(2).isCanAdjust()).isTrue();
    }

    /** 为两个批量入口设置共用校验依赖，每项创建独立测试结果 */
    private void prepareIdentity(Object target) {
        AbsIdentityService identity = new AbsIdentityService();
        BatchSecurityCheckService batch = mock(BatchSecurityCheckService.class);
        when(batch.checkSecurityBatch(anyList(), eq(false))).thenAnswer(invocation -> {
            List<AdjustCheckReq> requests = invocation.getArgument(0);
            return requests.stream().map(req -> {
                AdjustCheckDto result = allowed(req);
                if ("ABS1".equals(req.getSecurityCode())) {
                    identity.applyCheckFailure(result.getItems().get(0),
                            "同一 ABS[ABS1]在同批申请中选择了不同主体，请统一后重新校验");
                }
                return result;
            }).collect(Collectors.toList());
        });
        ReflectionTestUtils.setField(target, "batchSecurityCheckService", batch);
    }

    /** 创建独立结果，避免一条身份失败污染其他请求 */
    private AdjustCheckDto allowed(AdjustCheckReq req) {
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode(req.getSecurityCode());
        item.setCanAdjust(true);
        AdjustCheckDto result = new AdjustCheckDto();
        result.setItems(Collections.singletonList(item));
        return result;
    }
}
