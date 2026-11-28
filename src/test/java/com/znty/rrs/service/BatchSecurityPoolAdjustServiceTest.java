package com.znty.rrs.service;

import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.rrs.mapper.BatchSecurityCheckMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.SecurityMarketMapper;
import com.znty.rrs.entity.common.SecurityMarketMappingDto;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.Row;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchPoolTypeCountDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityInboundAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolDto;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证券池批量调整服务测试
 */
public class BatchSecurityPoolAdjustServiceTest {

    /** 批量校验和提交都应透传 ABS 普通权益人与自选权益人编码。 */
    @Test
    public void batchRequestShouldPassThroughRightsHolderCodes() {
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());
        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setDirection("in");
        req.setPoolId(11L);
        req.setPoolName("一级库");
        req.setPoolType("credit_bond");

        BatchSecurityInboundAdjustReq.SecurityItem security = new BatchSecurityInboundAdjustReq.SecurityItem();
        security.setSecurityCode("ABS001");
        security.setSecurityShortName("测试ABS");
        security.setSecurityType("abs");
        security.setRightsHolderCode("RIGHTS001");
        security.setSelfSelectedRightsHolderCode("SELF001");
        AdjustCheckReq checkReq = ReflectionTestUtils.invokeMethod(
                service, "buildSingleCheckReq", req, security);
        assertThat(checkReq.getRightsHolderCode()).isEqualTo("RIGHTS001");
        assertThat(checkReq.getSelfSelectedRightsHolderCode()).isEqualTo("SELF001");

        BatchSecurityInboundAdjustReq.AdjustItem item = new BatchSecurityInboundAdjustReq.AdjustItem();
        item.setSecurityCode("ABS001");
        item.setSecurityShortName("测试ABS");
        item.setSecurityType("abs");
        item.setRightsHolderCode("RIGHTS001");
        item.setSelfSelectedRightsHolderCode("SELF001");
        item.setTargetPoolId(11L);
        item.setAdjustMode("调入");
        SecurityPoolAdjustSubmitReq submitReq = ReflectionTestUtils.invokeMethod(
                service, "buildSingleSubmitReq", req, Collections.singletonList(item));
        assertThat(submitReq.getRightsHolderCode()).isEqualTo("RIGHTS001");
        assertThat(submitReq.getSelfSelectedRightsHolderCode()).isEqualTo("SELF001");
    }

    /** 验证分页查询按类型分项回填现有数量，无数据池合计为 0 */
    @Test
    public void fillPoolCurrentCountShouldQueryCurrentPagePoolIds() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);

        BatchSecurityPoolDto firstPool = new BatchSecurityPoolDto();
        firstPool.setId(11L);
        BatchSecurityPoolDto secondPool = new BatchSecurityPoolDto();
        secondPool.setId(12L);
        List<BatchSecurityPoolDto> poolList = Arrays.asList(firstPool, secondPool);

        BatchPoolTypeCountDto companyRow = new BatchPoolTypeCountDto();
        companyRow.setPoolId(11L);
        companyRow.setTypeCode("company");
        companyRow.setCount(1);
        BatchPoolTypeCountDto bondRow = new BatchPoolTypeCountDto();
        bondRow.setPoolId(11L);
        bondRow.setTypeCode("bond");
        bondRow.setCount(3);
        when(mapper.queryPoolCurrentCountByTypeList(anyList()))
                .thenReturn(Arrays.asList(bondRow, companyRow));

        ReflectionTestUtils.invokeMethod(service, "fillPoolCurrentCount", poolList);

        assertThat(poolList).extracting(BatchSecurityPoolDto::getCurrentCount)
                .containsExactly(4, 0);
        assertThat(firstPool.getCountByType()).extracting(BatchPoolTypeCountDto::getTypeCode)
                .containsExactly("company", "bond");
        assertThat(firstPool.getCountByType()).extracting(BatchPoolTypeCountDto::getCount)
                .containsExactly(1, 3);
        assertThat(secondPool.getCountByType()).isEmpty();
        verify(mapper).queryPoolCurrentCountByTypeList(Arrays.asList(11L, 12L));
    }

    /** 验证未知市场编码交由查询条件处理 */
    @Test
    public void validateSecurityPageReqShouldAllowUnknownMarketCode() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        when(mapper.queryEnabledLeafPoolCount(11L)).thenReturn(1);

        BatchSecurityPoolAdjustReq req = new BatchSecurityPoolAdjustReq();
        req.setPoolId(11L);
        req.setDirection("in");
        req.setMarketCodes(Collections.singletonList("OTHER"));

        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);

        verify(mapper).queryEnabledLeafPoolCount(11L);
    }

    /** 验证未知调整方向仍应阻止查询范围扩大 */
    @Test(expected = BizException.class)
    public void validateSecurityPageReqShouldRejectUnknownDirection() {
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());

        BatchSecurityPoolAdjustReq req = new BatchSecurityPoolAdjustReq();
        req.setPoolId(11L);
        req.setDirection("unknown");

        ReflectionTestUtils.invokeMethod(service, "validateSecurityPageReq", req);
    }

    /** 验证批量提交转单条提交时保留证券类型 */
    @Test
    public void buildSingleSubmitReqShouldKeepSecurityType() {
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());

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
        item.setMaterialExemptionType("productSource");
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
        assertThat(submitReq.getItems().get(0).getMaterialExemptionType()).isEqualTo("productSource");
        assertThat(submitReq.getItems().get(0).getCreditReportFileIndexes())
                .containsExactly(0);
        assertThat(submitReq.getItems().get(0).getMaterialFileIndexes())
                .containsExactly(1);
        assertThat(submitReq.getItems().get(0).getCreditReportSourceAttachmentIds())
                .containsExactly(7L, 8L);
        assertThat(submitReq.getItems().get(0).getMaterialSourceAttachmentIds())
                .containsExactly(9L);
    }

    /** 验证批量提交多只证券时复用同一个批次号上下文（委托单券服务）。 */
    @Test
    public void addAdjustLogShouldReuseBatchNoContextForMultipleSecurities() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        SecurityPoolAdjustService securityPoolAdjustService = AbsIdentityTestSupport.withIdentity(new SecurityPoolAdjustService());
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        ReflectionTestUtils.setField(securityPoolAdjustService, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "flowMapper", mock(FlowMapper.class));
        ReflectionTestUtils.setField(securityPoolAdjustService, "sysAttachmentService", attachmentService);
        BatchSecurityCheckMapper submitMapper = mock(BatchSecurityCheckMapper.class);
        ReflectionTestUtils.setField(securityPoolAdjustService, "batchSecurityCheckMapper", submitMapper);
        wireSubmitRatings(securityPoolAdjustService, submitMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "reportService", mock(ReportService.class));
        ReflectionTestUtils.setField(service, "securityPoolAdjustService", securityPoolAdjustService);

        when(mapper.queryEnabledLeafPoolCount(11L)).thenReturn(1);
        SysAttachmentService.SubmissionFiles submissionFiles = mock(SysAttachmentService.SubmissionFiles.class);
        when(attachmentService.createSubmissionFiles(
                anyList(), eq("1")))
                .thenReturn(submissionFiles);
        when(adjustMapper.querySecurityBoByCode(anyString()))
                .thenReturn(new SecurityInfoBo());
        when(investmentPoolMapper.queryPoolList()).thenReturn(new ArrayList<>());
        when(investmentPoolMapper.queryPoolById(11L)).thenReturn(buildPool(11L, null, "测试池", "credit_bond"));
        when(adjustMapper.querySecurityCurrentPoolIdList(anyString()))
                .thenReturn(Collections.<Long>emptyList());
        when(adjustMapper.queryAllPoolRelationList()).thenReturn(Collections.emptyList());
        when(adjustMapper.queryPendingManualTargetPoolIdList(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(adjustMapper.queryRecentManualAdjustLogList(anyString(), anyString(), any(Integer.class)))
                .thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(System.nanoTime());
            return 1;
        }).when(adjustMapper).addAdjustLog(any(IpAdjustLogBo.class));
        when(adjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);

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
        verify(adjustMapper, times(2)).addAdjustLog(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(IpAdjustLogBo::getAdjustBatchNo)
                .doesNotHaveDuplicates();
        assertThat(captor.getAllValues().get(0).getAdjustBatchNo()).matches("BOND\\d{17}1001");
        assertThat(captor.getAllValues().get(1).getAdjustBatchNo()).matches("BOND\\d{17}1002");
    }

    /** 验证普通债直通调入按批取数，保留容量和在途检查并跳过快照写入 */
    @Test
    public void directSubmitShouldBatchQueriesAndKeepCapacityAndPendingChecks() {
        SecurityPoolAdjustService submit = AbsIdentityTestSupport.withIdentity(new SecurityPoolAdjustService());
        SecurityPoolAdjustMapper adjust = mock(SecurityPoolAdjustMapper.class);
        BatchSecurityCheckMapper batch = mock(BatchSecurityCheckMapper.class);
        InvestmentPoolMapper pools = mock(InvestmentPoolMapper.class);
        SysAttachmentService attachments = mock(SysAttachmentService.class);
        ReportService reports = mock(ReportService.class);
        ReflectionTestUtils.setField(submit, "securityPoolAdjustMapper", adjust);
        ReflectionTestUtils.setField(submit, "batchSecurityCheckMapper", batch);
        wireSubmitRatings(submit, batch);
        ReflectionTestUtils.setField(submit, "investmentPoolMapper", pools);
        ReflectionTestUtils.setField(submit, "sysAttachmentService", attachments);
        ReflectionTestUtils.setField(submit, "reportService", reports);

        InvestmentPoolBo target = buildPool(11L, null, "测试池", "observe");
        target.setStatus("enabled");
        target.setIsDeleted(0);
        target.setMaxCapacity(999L);
        target.setVarietyCodes("[\"bond\"]");
        Row category = new Row();
        category.setSecurityType("MTN");
        category.setCategoryType("bond");
        when(batch.queryCategories()).thenReturn(Collections.singletonList(category));
        when(pools.queryPoolList()).thenReturn(Collections.singletonList(target));
        List<SecurityInfoBo> masters = new ArrayList<>();
        List<SecurityPoolAdjustSubmitReq> requests = new ArrayList<>();
        List<IpAdjustLogBo> preview = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String code = "B" + i + ".IB";
            SecurityInfoBo master = new SecurityInfoBo();
            master.setWindCode(code);
            master.setSecurityType("mtn");
            masters.add(master);

            SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
            item.setSecurityCode(code);
            item.setSecurityShortName("测试债");
            item.setSecurityType("mtn");
            item.setTargetPoolId(11L);
            item.setAdjustMode("调入");
            item.setItemTag("manual");
            item.setAdjustGroupKey(code + "_manual_11");
            SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
            req.setSecurityCode(code);
            req.setAdjusterId("1");
            req.setAdjustType("手动批量调整");
            req.setItems(Collections.singletonList(item));
            requests.add(req);

            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setSecurityCode(code);
            log.setTargetPoolId(11L);
            log.setAdjustMode("调入");
            preview.add(log);
        }
        when(batch.querySecurities(anyList())).thenReturn(masters.subList(0, 500), masters.subList(500, 1000));
        SysAttachmentService.SubmissionFiles files = mock(SysAttachmentService.SubmissionFiles.class);
        assertThatThrownBy(() -> submit.submitAdjustLogBatch(requests, files, preview))
                .hasMessageContaining("容量已满");
        verify(adjust, never()).addAdjustLog(any());

        target.setMaxCapacity(1000L);
        Row pending = new Row();
        pending.setSecurityCode("B0.IB");
        pending.setPoolId(11L);
        when(batch.querySecurities(anyList())).thenReturn(masters.subList(0, 500), masters.subList(500, 1000));
        when(batch.queryPendingPools(anyList())).thenReturn(Collections.singletonList(pending));
        assertThatThrownBy(() -> submit.submitAdjustLogBatch(requests, files, preview))
                .hasMessageContaining("存在进行中的调库流程");
        verify(adjust, never()).addAdjustLog(any());

        clearInvocations(batch, adjust, pools);
        when(batch.querySecurities(anyList())).thenReturn(masters.subList(0, 500), masters.subList(500, 1000));
        when(batch.queryPendingPools(anyList())).thenReturn(Collections.emptyList());
        doAnswer(call -> {
            IpAdjustLogBo log = call.getArgument(0);
            log.setId((long) log.getSecurityCode().hashCode());
            return 1;
        }).when(adjust).addAdjustLog(any());
        when(adjust.addPoolStatus(any())).thenReturn(1);

        assertThat(submit.submitAdjustLogBatch(requests, files, preview)).hasSize(1000);
        verify(pools, never()).queryExistingPoolIdList(anyList());
        verify(batch).queryPoolCounts(Collections.singletonList(11L));
        verify(batch, times(2)).querySecurities(anyList());
        verify(batch, times(2)).queryActiveStates(anyList());
        verify(batch, times(2)).queryPendingPools(anyList());
        verify(adjust, never()).queryRecentManualAdjustLogList(anyString(), eq("1"), eq(30));
        verify(batch, times(2)).queryRatingCompanies(anyList());
        verify(adjust, never()).queryCategoryTypeBySecurityType(anyString());
        verify(adjust, never()).querySecurityBoByCode(anyString());
        verify(adjust, never()).querySecurityCurrentPoolIdList(anyString());
        verify(adjust, never()).queryPoolCurrentCount(anyLong());
        verify(adjust, times(1000)).addPoolStatus(any());
        verify(adjust, never()).addAdjustSecuritySnapshot(any());
        verify(reports, never()).addInternalReportsOnFinish(anyList());
    }

    /** 验证担保债批量调入及互斥调出分别保存本次选择的担保人和内评 */
    @Test
    public void addAdjustLogShouldSaveSelectedGuarantorForInboundAndMutexOutbound() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        SysAttachmentService attachmentService = mock(SysAttachmentService.class);
        SecurityPoolAdjustService securityPoolAdjustService = AbsIdentityTestSupport.withIdentity(new SecurityPoolAdjustService());
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        ReflectionTestUtils.setField(securityPoolAdjustService, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "flowMapper", mock(FlowMapper.class));
        ReflectionTestUtils.setField(securityPoolAdjustService, "sysAttachmentService", attachmentService);
        BatchSecurityCheckMapper submitMapper = mock(BatchSecurityCheckMapper.class);
        ReflectionTestUtils.setField(securityPoolAdjustService, "batchSecurityCheckMapper", submitMapper);
        wireSubmitRatings(securityPoolAdjustService, submitMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "reportService", mock(ReportService.class));
        ReflectionTestUtils.setField(service, "securityPoolAdjustService", securityPoolAdjustService);

        when(mapper.queryEnabledLeafPoolCount(3L)).thenReturn(1);
        SysAttachmentService.SubmissionFiles submissionFiles = mock(SysAttachmentService.SubmissionFiles.class);
        when(attachmentService.createSubmissionFiles(
                anyList(), eq("1")))
                .thenReturn(submissionFiles);
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode("106006789");
        security.setGuarantFlag(1);
        security.setGuarantorId("C10010,C10008");
        security.setInnerGuarantorRating("1,1");
        when(adjustMapper.querySecurityBoByCode("106006789")).thenReturn(security);
        RelatedRatingCompanyDto guarantor = new RelatedRatingCompanyDto();
        guarantor.setSecurityCode("106006789");
        guarantor.setCompanyCode("C10008");
        guarantor.setCompanyName("某资产管理股份有限公司");
        guarantor.setInnerRating("2+");
        when(submitMapper.queryRatingCompanies(anyList())).thenReturn(Collections.singletonList(guarantor));
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(
                buildPool(1L, null, "信用债大库", "credit_bond"),
                buildPool(2L, 1L, "一级库", "credit_bond"),
                buildPool(3L, 1L, "二级库", "credit_bond")));
        when(investmentPoolMapper.queryPoolById(3L)).thenReturn(buildPool(3L, 1L, "二级库", "credit_bond"));
        when(adjustMapper.querySecurityCurrentPoolIdList("106006789"))
                .thenReturn(Collections.singletonList(2L));
        when(adjustMapper.queryAllPoolRelationList()).thenReturn(Collections.emptyList());
        when(adjustMapper.queryPendingManualTargetPoolIdList(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(adjustMapper.queryRecentManualAdjustLogList(anyString(), anyString(), any(Integer.class)))
                .thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            IpAdjustLogBo bo = (IpAdjustLogBo) invocation.getArguments()[0];
            bo.setId(System.nanoTime());
            return 1;
        }).when(adjustMapper).addAdjustLog(any(IpAdjustLogBo.class));
        when(adjustMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenReturn(1);
        when(adjustMapper.deletePoolStatusSoft("106006789", 2L)).thenReturn(1);

        String groupKey = "106006789_manual_3_调入";
        BatchSecurityInboundAdjustReq req = new BatchSecurityInboundAdjustReq();
        req.setCurrentUserId("1");
        req.setDirection("in");
        req.setAdjusterId("1");
        req.setAdjusterName("管理员");
        req.setPoolId(3L);
        BatchSecurityInboundAdjustReq.AdjustItem inbound =
                buildBatchSubmitItem("106006789", 3L, "二级库", "调入", "manual", groupKey);
        inbound.setGuarantorCode("C10008");
        req.setItems(Arrays.asList(
                inbound,
                buildBatchSubmitItem("106006789", 2L, "一级库", "调出", "mutex", groupKey)));

        service.addAdjustLog(req, Collections.<MultipartFile>emptyList());

        ArgumentCaptor<IpAdjustLogBo> captor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(adjustMapper, times(2)).addAdjustLog(captor.capture());
        assertThat(captor.getAllValues()).extracting(IpAdjustLogBo::getTargetPoolId)
                .containsExactly(3L, 2L);
        assertThat(captor.getAllValues()).extracting(IpAdjustLogBo::getAdjustMode)
                .containsExactly("调入", "调出");
        assertThat(captor.getAllValues()).extracting(IpAdjustLogBo::getAdjustType)
                .containsExactly("手动批量调整", "互斥调整");
        assertThat(captor.getAllValues()).extracting(IpAdjustLogBo::getAdjustBatchNo)
                .containsOnly(captor.getAllValues().get(0).getAdjustBatchNo());

        ArgumentCaptor<AdjustSecuritySnapshotBo> snapshots = ArgumentCaptor.forClass(AdjustSecuritySnapshotBo.class);
        verify(adjustMapper, times(2)).addAdjustSecuritySnapshot(snapshots.capture());
        assertThat(snapshots.getAllValues()).extracting(AdjustSecuritySnapshotBo::getAdjustLogId)
                .containsExactlyInAnyOrder(captor.getAllValues().get(0).getId(), captor.getAllValues().get(1).getId());
        assertThat(snapshots.getAllValues()).extracting(AdjustSecuritySnapshotBo::getWindCode).containsOnly("106006789");
        assertThat(snapshots.getAllValues()).extracting(AdjustSecuritySnapshotBo::getGuarantorId).containsOnly("C10008");
        assertThat(snapshots.getAllValues()).extracting(AdjustSecuritySnapshotBo::getGuarantor)
                .containsOnly("某资产管理股份有限公司");
        assertThat(snapshots.getAllValues()).extracting(AdjustSecuritySnapshotBo::getInnerGuarantorRating)
                .containsOnly("2+");
    }

    /** 验证批量调入时保留同证券当前所在互斥池的自动调出项。 */
    @Test
    public void checkAdjustShouldKeepMutexOutItemWhenInboundSecurityAlreadyInMutexPool() {
        BatchSecurityPoolAdjustMapper mapper = mock(BatchSecurityPoolAdjustMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        InvestmentPoolMapper investmentPoolMapper = mock(InvestmentPoolMapper.class);
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());
        // 批量取数由独立服务负责，共用真实单券判定
        SecurityPoolAdjustService securityPoolAdjustService = AbsIdentityTestSupport.withIdentity(new SecurityPoolAdjustService());
        ReflectionTestUtils.setField(securityPoolAdjustService, "securityMarketService", mock(SecurityMarketService.class));
        ReflectionTestUtils.setField(service, "batchSecurityPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(securityPoolAdjustService, "flowMapper", mock(FlowMapper.class));
        ReflectionTestUtils.setField(securityPoolAdjustService, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
        BatchSecurityCheckService batchChecks = new BatchSecurityCheckService();
        ReflectionTestUtils.setField(batchChecks, "securityPoolCheckRules",
                ReflectionTestUtils.invokeMethod(securityPoolAdjustService, "checkRules"));
        ReflectionTestUtils.setField(batchChecks, "investmentPoolMapper", investmentPoolMapper);
        ReflectionTestUtils.setField(service, "batchSecurityCheckService", batchChecks);

        when(mapper.queryEnabledLeafPoolCount(3L)).thenReturn(1);
        SecurityInfoBo securityInfo = new SecurityInfoBo();
        securityInfo.setWindCode("106006789");
        securityInfo.setShortName("测试债");
        securityInfo.setSecurityType("mtn");
        BatchSecurityCheckMapper batchData = mock(BatchSecurityCheckMapper.class);
        SecurityMarketMapper marketMapper = mock(SecurityMarketMapper.class);
        ReflectionTestUtils.setField(batchChecks, "batchSecurityCheckMapper", batchData);
        ReflectionTestUtils.setField(batchChecks, "securityMarketMapper", marketMapper);
        ReflectionTestUtils.setField(batchChecks, "creditBondGradeRuleMapper", mock(CreditBondGradeRuleMapper.class));
        ReflectionTestUtils.setField(batchChecks, "externalRatingAgencyService", mock(ExternalRatingAgencyService.class));
        when(batchData.querySecurities(Collections.singletonList("106006789"))).thenReturn(Collections.singletonList(securityInfo));
        Row state = new Row();
        state.setSecurityCode("106006789");
        state.setPoolId(2L);
        when(batchData.queryActiveStates(Collections.singletonList("106006789"))).thenReturn(Collections.singletonList(state));
        Row category = new Row();
        category.setSecurityType("mtn");
        category.setCategoryType("bond");
        when(batchData.queryCategories()).thenReturn(Collections.singletonList(category));
        SecurityMarketMappingDto market = new SecurityMarketMappingDto();
        market.setWindCode("106006789");
        market.setRawMarketCode("NIB");
        when(marketMapper.queryMarketMappingList(Collections.singletonList("106006789")))
                .thenReturn(Collections.singletonList(market));
        when(investmentPoolMapper.queryPoolList()).thenReturn(Arrays.asList(
                buildPool(1L, null, "信用债大库", "credit_bond"),
                buildPool(2L, 1L, "一级库", "credit_bond"),
                buildPool(3L, 1L, "二级库", "credit_bond")));
        when(investmentPoolMapper.queryPoolById(3L)).thenReturn(buildPool(3L, 1L, "二级库", "credit_bond"));
        when(adjustMapper.querySecurityCurrentPoolIdList("106006789"))
                .thenReturn(Collections.singletonList(2L));
        when(batchData.queryPoolRelations())
                .thenReturn(Collections.singletonList(buildRelation(3L, "in_mutex", 2L, "一级库")));
        when(adjustMapper.queryPoolCurrentCount(anyLong())).thenReturn(0);
        when(adjustMapper.querySecurityHasPendingProcess("106006789")).thenReturn(false);
        when(adjustMapper.querySecurityPendingProcessNodeLabel("106006789")).thenReturn(null);
        when(adjustMapper.queryPendingManualTargetPoolIdList("106006789", null))
                .thenReturn(Collections.emptyList());
        when(adjustMapper.querySecurityInObservePool("106006789")).thenReturn(false);
        when(adjustMapper.queryIssuerInObservePool("106006789")).thenReturn(false);
        when(adjustMapper.querySecurityInForbiddenPool("106006789")).thenReturn(false);
        when(adjustMapper.queryCategoryTypeBySecurityType(any(String.class))).thenReturn("bond");

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


    /** 验证批量校验结果透传单笔流程候选，不注入 batchIn/batchOut。 */
    @Test
    public void buildBatchCheckResultShouldPassThroughSingleFlowOptions() {
        BatchSecurityPoolAdjustService service = AbsIdentityTestSupport.withIdentity(new BatchSecurityPoolAdjustService());
        BatchSecurityInboundAdjustReq.SecurityItem security = new BatchSecurityInboundAdjustReq.SecurityItem();
        security.setSecurityCode("S001");
        security.setSecurityShortName("测试债");
        security.setSecurityType("company_bond");

        AdjustCheckDto.FlowOption simple = new AdjustCheckDto.FlowOption();
        simple.setFlowType("simpleInbound");
        simple.setFlowName("简易流程");
        simple.setFlowId(11L);
        simple.setFlowKey("bond:simple");
        simple.setMaterialExemptionType("creditBondSimple");
        simple.setRecommended(true);
        simple.setMatched(true);
        simple.setSelectable(true);

        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode("S001");
        item.setSecurityShortName("测试债");
        item.setSecurityType("company_bond");
        item.setTargetPoolId(11L);
        item.setPoolName("测试池");
        item.setPoolType("credit_bond");
        item.setAdjustMode("调入");
        item.setItemTag("manual");
        item.setAdjustGroupKey("11_in");
        item.setCanAdjust(true);
        item.setFlowOptions(new ArrayList<>(Collections.singletonList(simple)));
        item.setMaterialExemptionType("creditBondSimple");

        BatchSecurityInboundAdjustDto.CheckResultItem result =
                ReflectionTestUtils.invokeMethod(service, "buildBatchCheckResult", security, item);

        assertThat(result).isNotNull();
        assertThat(result.getFlowOptions()).hasSize(1);
        assertThat(result.getFlowOptions().get(0).getFlowType()).isEqualTo("simpleInbound");
        assertThat(result.getFlowOptions().get(0).getFlowId()).isEqualTo(11L);
        assertThat(result.getFlowOptions().get(0).getMaterialExemptionType()).isEqualTo("creditBondSimple");
        assertThat(result.getMaterialExemptionType()).isEqualTo("creditBondSimple");
        assertThat(result.getFlowOptions().get(0).isRecommended()).isTrue();
        assertThat(result.getAdjustGroupKey()).isEqualTo("S001_11_in");
    }

    /** 正式提交使用实际候选加载器，Mapper 使用各用例的模拟数据 */
    private void wireSubmitRatings(SecurityPoolAdjustService submit, BatchSecurityCheckMapper mapper) {
        BatchSecurityCheckService check = new BatchSecurityCheckService();
        ReflectionTestUtils.setField(check, "batchSecurityCheckMapper", mapper);
        ReflectionTestUtils.setField(submit, "batchSecurityCheckService", check);
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
