package com.znty.sirm.service;

import com.znty.sirm.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustReq;
import com.znty.sirm.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.sirm.entity.batchsecuritypooladjust.BatchSecurityPoolDto;
import com.znty.sirm.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.sirm.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

    /** 验证批量提交转单条提交时保留证券类型 */
    @Test
    public void buildSingleSubmitReqShouldKeepSecurityType() {
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();

        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setAdjustReason("原因");
        req.setAdjustAdvice("建议");
        req.setAdjusterId("1001");
        req.setAdjusterName("管理员");

        BatchSecurityInboundAdjustReq.AdjustItem item = new BatchSecurityInboundAdjustReq.AdjustItem();
        item.setSecurityCode("102002345");
        item.setSecurityShortName("23某城投债");
        item.setSecurityType("company_bond");
        item.setTargetPoolId(11L);
        item.setAdjustMode("调入");
        item.setFlowId(1L);
        item.setCreditReportFileIndexes(Collections.singletonList(0));
        item.setMaterialFileIndexes(Collections.singletonList(1));
        item.setCreditReportSourceAttachmentIds(Arrays.asList(7L, 8L));
        item.setMaterialSourceAttachmentIds(Collections.singletonList(9L));

        SecurityPoolAdjustSubmitReq submitReq = ReflectionTestUtils.invokeMethod(
                service,
                "buildSingleSubmitReq",
                req,
                Collections.singletonList(item));

        assertThat(submitReq.getSecurityType()).isEqualTo("company_bond");
        assertThat(submitReq.getItems().get(0).getCreditReportFileIndexes())
                .containsExactly(0);
        assertThat(submitReq.getItems().get(0).getMaterialFileIndexes())
                .containsExactly(1);
        assertThat(submitReq.getItems().get(0).getCreditReportSourceAttachmentIds())
                .containsExactly(7L, 8L);
        assertThat(submitReq.getItems().get(0).getMaterialSourceAttachmentIds())
                .containsExactly(9L);
    }

    /** 验证批量提交多只证券时复用同一个批次号上下文。 */
    @Test
    public void addAdjustLogShouldReuseBatchNoContextForMultipleSecurities() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService adjustService = mock(SecurityPoolAdjustService.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustService", adjustService);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);

        when(mapper.queryEnabledLeafPoolCount(11L)).thenReturn(1);
        SysAttachmentService.SubmissionFiles submissionFiles = mock(SysAttachmentService.SubmissionFiles.class);
        when(attachmentService.createSubmissionFiles(
                org.mockito.Matchers.anyListOf(MultipartFile.class), org.mockito.Matchers.eq("1001")))
                .thenReturn(submissionFiles);
        SecurityPoolAdjustService.BatchNoContext batchNoContext = new SecurityPoolAdjustService.BatchNoContext();
        when(adjustService.createBatchNoContext()).thenReturn(batchNoContext);
        AdjustSubmitDto submitDto = new AdjustSubmitDto();
        submitDto.setSubmitCount(1);
        when(adjustService.addAdjustLog(any(SecurityPoolAdjustSubmitReq.class), same(submissionFiles),
                same(batchNoContext))).thenReturn(submitDto);

        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setCurrentUserId("1001");
        req.setDirection("in");
        req.setAdjusterId("1001");
        req.setAdjusterName("管理员");
        req.setPoolId(11L);
        req.setItems(Arrays.asList(
                buildBatchSubmitItem("102002345"),
                buildBatchSubmitItem("102002346")));

        service.addAdjustLog(req, Collections.<MultipartFile>emptyList());

        verify(adjustService).createBatchNoContext();
        verify(adjustService, times(2)).addAdjustLog(any(SecurityPoolAdjustSubmitReq.class),
                same(submissionFiles), same(batchNoContext));
    }

    /** 构建批量提交明细。 */
    private BatchSecurityInboundAdjustReq.AdjustItem buildBatchSubmitItem(String securityCode) {
        BatchSecurityInboundAdjustReq.AdjustItem item = new BatchSecurityInboundAdjustReq.AdjustItem();
        item.setSecurityCode(securityCode);
        item.setSecurityShortName("测试证券");
        item.setSecurityType("company_bond");
        item.setTargetPoolId(11L);
        item.setAdjustMode("调入");
        item.setFlowId(1L);
        item.setAdjustGroupKey(securityCode + "_manual_11_调入");
        return item;
    }
}
