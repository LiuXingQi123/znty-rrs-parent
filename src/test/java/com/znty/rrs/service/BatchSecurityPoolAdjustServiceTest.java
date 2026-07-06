package com.znty.rrs.service;

import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import org.mockito.ArgumentCaptor;
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

    /** 验证批量提交转单条提交时保留证券类型 */
    @Test
    public void buildSingleSubmitReqShouldKeepSecurityType() {
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();

        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setAdjustReason("原因");
        req.setAdjustAdvice("建议");
        req.setAdjusterId("1");
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
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        FlowMapper flowMapper = mock(FlowMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);

        when(mapper.queryEnabledLeafPoolCount(11L)).thenReturn(1);
        SysAttachmentService.SubmissionFiles submissionFiles = mock(SysAttachmentService.SubmissionFiles.class);
        when(attachmentService.createSubmissionFiles(
                org.mockito.Matchers.anyListOf(MultipartFile.class), org.mockito.Matchers.eq("1")))
                .thenReturn(submissionFiles);
        when(adjustMapper.querySecurityBoByCode(org.mockito.Matchers.anyString()))
                .thenReturn(new SecurityInfoBo());
        when(investmentPoolMapper.queryPoolList()).thenReturn(new ArrayList<>());
        when(adjustMapper.querySecurityCurrentPoolIdList(org.mockito.Matchers.anyString()))
                .thenReturn(Collections.<Long>emptyList());
        when(adjustMapper.queryAllPoolRelationList()).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(System.nanoTime());
            return 1;
        }).when(adjustMapper).addAdjustLog(any(IpAdjustLogBo.class));

        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setCurrentUserId("1");
        req.setDirection("in");
        req.setAdjusterId("1");
        req.setAdjusterName("管理员");
        req.setPoolId(11L);
        req.setItems(Arrays.asList(
                buildBatchSubmitItem("102002345"),
                buildBatchSubmitItem("102002346")));

        service.addAdjustLog(req, Collections.<MultipartFile>emptyList());

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(adjustMapper, org.mockito.Mockito.times(2)).addAdjustLog(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(IpAdjustLogBo::getAdjustBatchNo)
                .doesNotHaveDuplicates();
    }

    /** 验证批量提交同一证券的手工调入和互斥调出时写入两条调库记录。 */
    @Test
    public void addAdjustLogShouldInsertManualInboundAndMutexOutboundForSameSecurity() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        FlowMapper flowMapper = mock(FlowMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);

        when(mapper.queryEnabledLeafPoolCount(3L)).thenReturn(1);
        SysAttachmentService.SubmissionFiles submissionFiles = mock(SysAttachmentService.SubmissionFiles.class);
        when(attachmentService.createSubmissionFiles(
                org.mockito.Matchers.anyListOf(MultipartFile.class), org.mockito.Matchers.eq("1")))
                .thenReturn(submissionFiles);
        when(adjustMapper.querySecurityBoByCode("106006789")).thenReturn(new SecurityInfoBo());
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(
                buildPool(1L, null, "信用债大库", "credit_bond"),
                buildPool(2L, 1L, "一级库", "credit_bond"),
                buildPool(3L, 1L, "二级库", "credit_bond")));
        when(adjustMapper.querySecurityCurrentPoolIdList("106006789"))
                .thenReturn(Collections.singletonList(2L));
        when(adjustMapper.queryAllPoolRelationList()).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(System.nanoTime());
            return 1;
        }).when(adjustMapper).addAdjustLog(any(IpAdjustLogBo.class));

        String groupKey = "106006789_manual_3_调入";
        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setCurrentUserId("1");
        req.setDirection("in");
        req.setAdjusterId("1");
        req.setAdjusterName("管理员");
        req.setPoolId(3L);
        req.setItems(Arrays.asList(
                buildBatchSubmitItem("106006789", 3L, "二级库", "调入", "manual", groupKey),
                buildBatchSubmitItem("106006789", 2L, "一级库", "调出", "mutex", groupKey)));

        service.addAdjustLog(req, Collections.<MultipartFile>emptyList());

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(adjustMapper, org.mockito.Mockito.times(2)).addAdjustLog(captor.capture());
        assertThat(captor.getAllValues()).extracting(IpAdjustLogBo::getTargetPoolId)
                .containsExactly(3L, 2L);
        assertThat(captor.getAllValues()).extracting(IpAdjustLogBo::getAdjustMode)
                .containsExactly("调入", "调出");
        assertThat(captor.getAllValues()).extracting(IpAdjustLogBo::getAdjustType)
                .containsExactly("手工调整", "互斥调整");
        assertThat(captor.getAllValues()).extracting(IpAdjustLogBo::getAdjustBatchNo)
                .containsOnly(captor.getAllValues().get(0).getAdjustBatchNo());
    }

    /** 验证批量调入时保留同证券当前所在互斥池的自动调出项。 */
    @Test
    public void checkAdjustShouldKeepMutexOutItemWhenInboundSecurityAlreadyInMutexPool() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        BatchSecurityPoolAdjustService service = new BatchSecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);

        when(mapper.queryEnabledLeafPoolCount(3L)).thenReturn(1);
        when(adjustMapper.querySecurityBoByCode("106006789")).thenReturn(new SecurityInfoBo());
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(
                buildPool(1L, null, "信用债大库", "credit_bond"),
                buildPool(2L, 1L, "一级库", "credit_bond"),
                buildPool(3L, 1L, "二级库", "credit_bond")));
        when(adjustMapper.querySecurityCurrentPoolIdList("106006789"))
                .thenReturn(Collections.singletonList(2L));
        when(adjustMapper.queryAllPoolRelationList())
                .thenReturn(Collections.singletonList(buildRelation(3L, "in_mutex", 2L, "一级库")));
        when(adjustMapper.queryPoolCurrentCount(org.mockito.Matchers.anyLong())).thenReturn(0);

        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setCurrentUserId("1");
        req.setDirection("in");
        req.setPoolId(3L);
        req.setPoolName("二级库");
        req.setPoolType("credit_bond");
        req.setSecurities(Collections.singletonList(buildSecurityItem("106006789")));

        BatchSecurityInboundAdjustDto dto = service.checkAdjust(req);

        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems()).extracting(BatchSecurityInboundAdjustDto.CheckResultItem::getItemTag)
                .containsExactly("manual", "mutex");
        assertThat(dto.getItems().get(1).getSecurityCode()).isEqualTo("106006789");
        assertThat(dto.getItems().get(1).getTargetPoolId()).isEqualTo(2L);
        assertThat(dto.getItems().get(1).getPoolName()).isEqualTo("信用债大库/一级库");
        assertThat(dto.getItems().get(1).getAdjustMode()).isEqualTo("调出");
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

    /** 构建指定属性的批量提交明细。 */
    private BatchSecurityInboundAdjustReq.AdjustItem buildBatchSubmitItem(
            String securityCode, Long targetPoolId, String targetPoolName,
            String adjustMode, String itemTag, String adjustGroupKey) {
        BatchSecurityInboundAdjustReq.AdjustItem item = new BatchSecurityInboundAdjustReq.AdjustItem();
        item.setSecurityCode(securityCode);
        item.setSecurityShortName("22某电力MTN001");
        item.setSecurityType("mtn");
        item.setTargetPoolId(targetPoolId);
        item.setTargetPoolName(targetPoolName);
        item.setPoolType("credit_bond");
        item.setAdjustMode(adjustMode);
        item.setItemTag(itemTag);
        item.setAdjustGroupKey(adjustGroupKey);
        item.setFlowId(1L);
        return item;
    }

    /** 构建批量校验证券。 */
    private BatchSecurityInboundAdjustReq.SecurityItem buildSecurityItem(String securityCode) {
        BatchSecurityInboundAdjustReq.SecurityItem item = new BatchSecurityInboundAdjustReq.SecurityItem();
        item.setSecurityCode(securityCode);
        item.setSecurityShortName("22某电力MTN001");
        item.setSecurityType("mtn");
        return item;
    }

    /** 构建投资池。 */
    private InvestmentPoolBo buildPool(Long id, Long parentId, String poolName, String poolType) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setParentId(parentId);
        pool.setPoolName(poolName);
        pool.setPoolType(poolType);
        return pool;
    }

    /** 构建投资池关系。 */
    private PoolRelationBo buildRelation(Long poolId, String relationType, Long relationPoolId, String relationPoolName) {
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(poolId);
        relation.setRelationType(relationType);
        relation.setRelationPoolId(relationPoolId);
        relation.setRelationPoolName(relationPoolName);
        return relation;
    }
}
