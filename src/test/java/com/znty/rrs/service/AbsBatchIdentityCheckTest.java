package com.znty.rrs.service;

import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustReq;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustSubmitReq;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.rrs.mapper.StockSecurityBatchAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 两个批量入口的同批 ABS 不同主体应返回逐项不可调整，不中断整批校验 */
public class AbsBatchIdentityCheckTest {

    @Test
    public void ordinaryBatchShouldBlockBothConflictingSelectionsAndKeepOtherSecurity() {
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(101L)).thenReturn(1);
        // 使用真实主体冲突组件及正常单券校验响应，验证批量编排保留失败结果
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
        // 使用相同真实主体组件验证存量入口，不给普通证券追加身份限制
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

        assertThat(result.getItems()).hasSize(3);
        assertThat(result.getItems().subList(0, 2)).allSatisfy(item -> {
            assertThat(item.isCanAdjust()).isFalse();
            assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("同批申请中选择了不同主体"));
        });
        assertThat(result.getItems().get(2).isCanAdjust()).isTrue();
    }

    /** 为批量编排设置真实身份组件，单券响应逐次创建，避免结果对象互相污染 */
    private void prepareIdentity(Object target) {
        AbsIdentityService identity = new AbsIdentityService();
        AbsIdentityMapper mapper = mock(AbsIdentityMapper.class);
        SecurityInfoBo abs = new SecurityInfoBo();
        abs.setWindCode("ABS1");
        abs.setSecurityType("abs");
        when(mapper.queryAbsSecurityList(anyList())).thenReturn(Collections.singletonList(abs));
        ReflectionTestUtils.setField(identity, "absIdentityMapper", mapper);
        ReflectionTestUtils.setField(target, "absIdentityService", identity);
        SecurityPoolAdjustService single = mock(SecurityPoolAdjustService.class);
        when(single.checkBatchAdjust(any())).thenAnswer(invocation -> {
            AdjustCheckReq req = invocation.getArgument(0);
            AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
            item.setSecurityCode(req.getSecurityCode());
            item.setCanAdjust(true);
            AdjustCheckDto result = new AdjustCheckDto();
            result.setItems(Collections.singletonList(item));
            return result;
        });
        ReflectionTestUtils.setField(target, "securityPoolAdjustService", single);
    }
}
