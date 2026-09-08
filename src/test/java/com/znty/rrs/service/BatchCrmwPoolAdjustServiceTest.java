package com.znty.rrs.service;

import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwAdjustDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwAdjustReq;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwCandidateDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolAdjustReq;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolTypeCountDto;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchCrmwPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CRMW 池批量调整服务测试
 */
public class BatchCrmwPoolAdjustServiceTest {

    /** 验证分页查询回填 CRMW 在池数量 */
    @Test
    public void fillPoolCurrentCountShouldQueryCurrentPagePoolIds() {
        BatchCrmwPoolAdjustMapper mapper = mock(BatchCrmwPoolAdjustMapper.class);
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchCrmwPoolAdjustMapper", mapper);

        BatchCrmwPoolDto firstPool = new BatchCrmwPoolDto();
        firstPool.setId(18L);
        BatchCrmwPoolDto secondPool = new BatchCrmwPoolDto();
        secondPool.setId(19L);
        List<BatchCrmwPoolDto> poolList = Arrays.asList(firstPool, secondPool);

        BatchCrmwPoolTypeCountDto crmwRow = new BatchCrmwPoolTypeCountDto();
        crmwRow.setPoolId(18L);
        crmwRow.setTypeCode("crmw");
        crmwRow.setCount(2);
        when(mapper.queryPoolCurrentCountByTypeList(anyListOf(Long.class)))
                .thenReturn(Collections.singletonList(crmwRow));

        ReflectionTestUtils.invokeMethod(service, "fillPoolCurrentCount", poolList);

        assertThat(poolList).extracting(BatchCrmwPoolDto::getCurrentCount)
                .containsExactly(2, 0);
        assertThat(firstPool.getCountByType()).extracting(BatchCrmwPoolTypeCountDto::getTypeCode)
                .containsExactly("crmw");
        verify(mapper).queryPoolCurrentCountByTypeList(Arrays.asList(18L, 19L));
    }

    /** 验证非 CRMW 叶子池不能查候选 */
    @Test(expected = BizException.class)
    public void validateCandidatePageReqShouldRejectNonCrmwPool() {
        BatchCrmwPoolAdjustMapper mapper = mock(BatchCrmwPoolAdjustMapper.class);
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchCrmwPoolAdjustMapper", mapper);
        when(mapper.queryEnabledLeafCrmwPoolCount(11L)).thenReturn(0);

        BatchCrmwPoolAdjustReq req = new BatchCrmwPoolAdjustReq();
        req.setPoolId(11L);
        ReflectionTestUtils.invokeMethod(service, "validateCandidatePageReq", req);
    }

    /** 验证校验委托单笔 CRMW 服务并带回凭证字段 */
    @Test
    public void checkAdjustShouldDelegateToCrmwPoolAdjustService() {
        BatchCrmwPoolAdjustMapper mapper = mock(BatchCrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService crmwPoolAdjustService = mock(CrmwPoolAdjustService.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchCrmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustService", crmwPoolAdjustService);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        when(mapper.queryEnabledLeafCrmwPoolCount(18L)).thenReturn(1);

        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setTargetPoolId(18L);
        item.setPoolName("CRMW库");
        item.setPoolType("crmw");
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setAdjustGroupKey("18_in");
        item.setCanAdjust(true);
        item.setFailReasons(Collections.emptyList());
        AdjustCheckDto checkDto = new AdjustCheckDto();
        checkDto.setItems(Collections.singletonList(item));
        when(crmwPoolAdjustService.checkCrmwAdjust(any(AdjustCheckReq.class))).thenReturn(checkDto);

        BatchCrmwAdjustReq req = new BatchCrmwAdjustReq();
        req.setCurrentUserId("1");
        req.setDirection("in");
        req.setPoolId(18L);
        req.setPoolName("CRMW库");
        req.setPoolType("crmw");
        BatchCrmwAdjustReq.SecurityItem security = new BatchCrmwAdjustReq.SecurityItem();
        security.setSecurityCode("MTN001.IB");
        security.setSecurityShortName("24交投MTN");
        security.setSecurityType("mtn");
        security.setCrmwScode("CRMW001.IB");
        security.setCrmwName("某CRMW凭证A");
        req.setSecurities(Collections.singletonList(security));

        BatchCrmwAdjustDto dto = service.checkAdjust(req);

        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getCrmwScode()).isEqualTo("CRMW001.IB");
        assertThat(dto.getItems().get(0).getSecurityCode()).isEqualTo("MTN001.IB");
        assertThat(dto.getItems().get(0).getFailReasons()).isEmpty();
        ArgumentCaptor<AdjustCheckReq> captor = ArgumentCaptor.forClass(AdjustCheckReq.class);
        verify(crmwPoolAdjustService).checkCrmwAdjust(captor.capture());
        assertThat(captor.getValue().getCrmwScode()).isEqualTo("CRMW001.IB");
        assertThat(captor.getValue().getCrmwName()).isEqualTo("某CRMW凭证A");
        assertThat(captor.getValue().getItems().get(0).getPoolType()).isEqualTo("crmw");
    }

    /** 验证提交转单笔时保留凭证与标的 */
    @Test
    public void buildSingleSubmitReqShouldKeepCrmwAndSecurity() {
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();

        BatchCrmwAdjustReq req = new BatchCrmwAdjustReq();
        req.setAdjustReason("原因");
        req.setAdjustAdvice("建议");
        req.setAdjusterId("1");
        req.setAdjusterName("管理员");

        BatchCrmwAdjustReq.AdjustItem item = new BatchCrmwAdjustReq.AdjustItem();
        item.setSecurityCode("MTN001.IB");
        item.setSecurityShortName("24交投MTN");
        item.setSecurityType("mtn");
        item.setCrmwScode("CRMW001.IB");
        item.setCrmwName("某CRMW凭证A");
        item.setTargetPoolId(18L);
        item.setAdjustMode("调入");
        item.setFlowId(1L);
        item.setCreditReportFileIndexes(Collections.singletonList(0));
        item.setMaterialFileIndexes(Collections.singletonList(1));

        CrmwPoolAdjustSubmitReq submitReq = ReflectionTestUtils.invokeMethod(
                service,
                "buildSingleSubmitReq",
                req,
                Collections.singletonList(item));

        assertThat(submitReq.getSecurityCode()).isEqualTo("MTN001.IB");
        assertThat(submitReq.getCrmwScode()).isEqualTo("CRMW001.IB");
        assertThat(submitReq.getCrmwStype()).isEqualTo("crmw");
        assertThat(submitReq.getAdjustType()).isEqualTo("手动批量调整");
        assertThat(submitReq.getItems().get(0).getPoolType()).isEqualTo("crmw");
        assertThat(submitReq.getItems().get(0).getCreditReportFileIndexes()).containsExactly(0);
    }

    /** 验证防重复键包含凭证代码 */
    @Test
    public void duplicateSubmitShouldCompareCrmwCombination() {
        BatchCrmwPoolAdjustMapper mapper = mock(BatchCrmwPoolAdjustMapper.class);
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchCrmwPoolAdjustMapper", mapper);

        IpAdjustLogBo recent = new IpAdjustLogBo();
        recent.setSecurityCode("MTN001.IB");
        recent.setCrmwScode("CRMW001.IB");
        recent.setTargetPoolId(18L);
        recent.setAdjustMode("调入");
        recent.setFlowId(1L);
        recent.setFlowKey("bond:no-approval");
        recent.setAdjustReason("原因");
        recent.setAdjustAdvice("建议");
        when(mapper.queryRecentBatchManualAdjustLogList("1", 30))
                .thenReturn(Collections.singletonList(recent));

        BatchCrmwAdjustReq req = new BatchCrmwAdjustReq();
        req.setAdjusterId("1");
        req.setAdjustReason("原因");
        req.setAdjustAdvice("建议");
        BatchCrmwAdjustReq.AdjustItem item = new BatchCrmwAdjustReq.AdjustItem();
        item.setSecurityCode("MTN001.IB");
        item.setCrmwScode("CRMW001.IB");
        item.setTargetPoolId(18L);
        item.setAdjustMode("调入");
        item.setFlowId(1L);
        item.setFlowKey("bond:no-approval");
        item.setItemTag("manual");
        req.setItems(Collections.singletonList(item));

        try {
            ReflectionTestUtils.invokeMethod(service, "checkRecentBatchDuplicateSubmit", req);
            throw new AssertionError("应拒绝重复提交");
        } catch (BizException e) {
            assertThat(e.getMessage()).contains("请勿重复操作");
        }
    }

    /** 验证批量校验结果透传单笔流程候选，不注入 batchIn/batchOut。 */
    @Test
    public void buildBatchCheckResultShouldPassThroughSingleFlowOptions() {
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        BatchCrmwAdjustReq.SecurityItem security = new BatchCrmwAdjustReq.SecurityItem();
        security.setSecurityCode("MTN001.IB");
        security.setSecurityShortName("24交投MTN");
        security.setSecurityType("mtn");
        security.setCrmwScode("CRMW001.IB");
        security.setCrmwName("某CRMW凭证A");

        AdjustCheckDto.FlowOption simple = new AdjustCheckDto.FlowOption();
        simple.setFlowType("simpleInbound");
        simple.setFlowName("简易流程");
        simple.setFlowId(11L);
        simple.setFlowKey("bond:simple");
        simple.setRecommended(true);
        simple.setMatched(true);
        simple.setSelectable(true);

        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode("MTN001.IB");
        item.setSecurityShortName("24交投MTN");
        item.setSecurityType("mtn");
        item.setCrmwScode("CRMW001.IB");
        item.setCrmwName("某CRMW凭证A");
        item.setCrmwStype("crmw");
        item.setSourceSecurityCode("CRMW001.IB|MTN001.IB");
        item.setTargetPoolId(18L);
        item.setPoolName("CRMW库");
        item.setPoolType("crmw");
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setAdjustGroupKey("18_in");
        item.setCanAdjust(true);
        item.setWarnings(Collections.singletonList("命中弹性限制池"));
        item.setFlowOptions(new ArrayList<>(Collections.singletonList(simple)));

        BatchCrmwAdjustDto.CheckResultItem result =
                ReflectionTestUtils.invokeMethod(service, "buildBatchCheckResult", security, item);

        assertThat(result).isNotNull();
        assertThat(result.getFlowOptions()).hasSize(1);
        assertThat(result.getFlowOptions().get(0).getFlowType()).isEqualTo("simpleInbound");
        assertThat(result.getFlowOptions().get(0).getFlowId()).isEqualTo(11L);
        assertThat(result.getFlowOptions().get(0).isRecommended()).isTrue();
        assertThat(result.getWarnings()).containsExactly("命中弹性限制池");
        assertThat(result.getAdjustGroupKey()).isEqualTo("CRMW001.IB|MTN001.IB_18_in");
    }

    /** 验证批量调入时保留单笔返回的互斥调出项。 */
    @Test
    public void checkAdjustShouldKeepMutexOutItemFromSingleCheck() {
        BatchCrmwPoolAdjustMapper mapper = mock(BatchCrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService crmwPoolAdjustService = mock(CrmwPoolAdjustService.class);
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchCrmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustService", crmwPoolAdjustService);
        when(mapper.queryEnabledLeafCrmwPoolCount(18L)).thenReturn(1);

        AdjustCheckDto.CheckResultItem manual = new AdjustCheckDto.CheckResultItem();
        manual.setSecurityCode("MTN001.IB");
        manual.setCrmwScode("CRMW001.IB");
        manual.setSourceSecurityCode("CRMW001.IB|MTN001.IB");
        manual.setTargetPoolId(18L);
        manual.setPoolName("CRMW库");
        manual.setPoolType("crmw");
        manual.setAdjustMode("调入");
        manual.setItemTag("manual");
        manual.setAdjustGroupKey("18_in");
        manual.setCanAdjust(true);

        AdjustCheckDto.CheckResultItem mutex = new AdjustCheckDto.CheckResultItem();
        mutex.setSecurityCode("MTN001.IB");
        mutex.setCrmwScode("CRMW001.IB");
        mutex.setSourceSecurityCode("CRMW001.IB|MTN001.IB");
        mutex.setTargetPoolId(19L);
        mutex.setPoolName("CRMW库/观察库");
        mutex.setPoolType("crmw");
        mutex.setAdjustMode("调出");
        mutex.setItemTag("mutex");
        mutex.setAdjustGroupKey("18_in");
        mutex.setCanAdjust(true);

        AdjustCheckDto checkDto = new AdjustCheckDto();
        checkDto.setItems(Arrays.asList(manual, mutex));
        when(crmwPoolAdjustService.checkCrmwAdjust(any(AdjustCheckReq.class))).thenReturn(checkDto);

        BatchCrmwAdjustReq req = new BatchCrmwAdjustReq();
        req.setCurrentUserId("1");
        req.setDirection("in");
        req.setPoolId(18L);
        req.setPoolName("CRMW库");
        req.setPoolType("crmw");
        BatchCrmwAdjustReq.SecurityItem security = new BatchCrmwAdjustReq.SecurityItem();
        security.setSecurityCode("MTN001.IB");
        security.setCrmwScode("CRMW001.IB");
        req.setSecurities(Collections.singletonList(security));

        BatchCrmwAdjustDto dto = service.checkAdjust(req);

        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems()).extracting(BatchCrmwAdjustDto.CheckResultItem::getItemTag)
                .containsExactly("manual", "mutex");
        assertThat(dto.getItems().get(1).getTargetPoolId()).isEqualTo(19L);
        assertThat(dto.getItems().get(1).getAdjustMode()).isEqualTo("调出");
        assertThat(dto.getItems().get(1).getCrmwScode()).isEqualTo("CRMW001.IB");
    }

    /** 验证组合集合不同时不按重复批量申请拦截。 */
    @Test
    public void checkRecentBatchDuplicateShouldAllowDifferentCombinationSet() {
        BatchCrmwPoolAdjustMapper mapper = mock(BatchCrmwPoolAdjustMapper.class);
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchCrmwPoolAdjustMapper", mapper);
        IpAdjustLogBo history = new IpAdjustLogBo();
        history.setSecurityCode("MTN001.IB");
        history.setCrmwScode("CRMW001.IB");
        history.setTargetPoolId(18L);
        history.setAdjustMode("调入");
        history.setFlowId(1L);
        when(mapper.queryRecentBatchManualAdjustLogList("1", 30))
                .thenReturn(Collections.singletonList(history));

        BatchCrmwAdjustReq req = new BatchCrmwAdjustReq();
        req.setAdjusterId("1");
        req.setItems(Arrays.asList(
                buildBatchSubmitItem("MTN001.IB", "CRMW001.IB"),
                buildBatchSubmitItem("MTN002.IB", "CRMW001.IB")));

        ReflectionTestUtils.invokeMethod(service, "checkRecentBatchDuplicateSubmit", req);

        verify(mapper).queryRecentBatchManualAdjustLogList("1", 30);
    }

    /** 验证批量组合顺序不同但内容相同时仍阻止重复提交。 */
    @Test
    public void checkRecentBatchDuplicateShouldRejectSameItemsInDifferentOrder() {
        BatchCrmwPoolAdjustMapper mapper = mock(BatchCrmwPoolAdjustMapper.class);
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchCrmwPoolAdjustMapper", mapper);
        IpAdjustLogBo first = buildBatchHistoryLog("MTN001.IB", "CRMW001.IB");
        IpAdjustLogBo second = buildBatchHistoryLog("MTN002.IB", "CRMW001.IB");
        when(mapper.queryRecentBatchManualAdjustLogList("1", 30)).thenReturn(Arrays.asList(first, second));

        BatchCrmwAdjustReq req = new BatchCrmwAdjustReq();
        req.setAdjusterId("1");
        req.setItems(Arrays.asList(
                buildBatchSubmitItem("MTN002.IB", "CRMW001.IB"),
                buildBatchSubmitItem("MTN001.IB", "CRMW001.IB")));

        try {
            ReflectionTestUtils.invokeMethod(service, "checkRecentBatchDuplicateSubmit", req);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BizException.class);
            assertThat(e.getMessage()).contains("请勿重复操作");
            return;
        }
        throw new AssertionError("内容相同且顺序不同时应阻止重复提交");
    }

    /** 构建批量提交明细。 */
    private BatchCrmwAdjustReq.AdjustItem buildBatchSubmitItem(String securityCode, String crmwScode) {
        BatchCrmwAdjustReq.AdjustItem item = new BatchCrmwAdjustReq.AdjustItem();
        item.setSecurityCode(securityCode);
        item.setCrmwScode(crmwScode);
        item.setTargetPoolId(18L);
        item.setAdjustMode("调入");
        item.setFlowId(1L);
        item.setItemTag("manual");
        return item;
    }

    /** 构建批量防重复历史日志。 */
    private IpAdjustLogBo buildBatchHistoryLog(String securityCode, String crmwScode) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setSecurityCode(securityCode);
        log.setCrmwScode(crmwScode);
        log.setTargetPoolId(18L);
        log.setAdjustMode("调入");
        log.setFlowId(1L);
        return log;
    }

    /** 验证填充市场编码 */
    @Test
    public void fillCandidateFieldsShouldSplitMarketText() {
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        BatchCrmwCandidateDto dto = new BatchCrmwCandidateDto();
        dto.setMarketCodeText("SSE,CIBM");
        ReflectionTestUtils.invokeMethod(service, "fillCandidateFields", Collections.singletonList(dto));
        assertThat(dto.getMarketCodes()).containsExactly("SSE", "CIBM");
        assertThat(dto.getCrmwStype()).isEqualTo("crmw");
    }
}
