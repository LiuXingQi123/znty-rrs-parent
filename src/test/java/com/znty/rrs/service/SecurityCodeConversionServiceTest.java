package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempOprtSource;
import com.znty.rrs.entity.bo.SecurityCodeConversionBo;
import com.znty.rrs.entity.securitycodeconversion.SecurityCodeConversionReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityCodeConversionMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 证券代码转换服务测试。 */
public class SecurityCodeConversionServiceTest {

    /** 验证外部临时代码无需登记记录也能生成主档映射摘要。 */
    @Test
    public void replaceReferencesShouldSupportExternalTempCodeWithoutRegistration() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        // 准备无业务引用场景
        stubEmptyRuntimeReferences(mapper);
        stubSecurityMaster(mapper);

        service.replaceTempSecurityCodeReferences(buildReq());

        ArgumentCaptor<SecurityCodeConversionBo> captor = ArgumentCaptor.forClass(
                SecurityCodeConversionBo.class);
        verify(mapper).addSecurityCodeConversionLog(captor.capture());
        assertThat(captor.getValue().getTempSecurityCode()).isEqualTo("TMP-EXT-001");
        assertThat(captor.getValue().getSecurityCode()).isEqualTo("102600001.IB");
        assertThat(captor.getValue().getFullName()).isEqualTo("某集团2026年度第一期中期票据");
        assertThat(captor.getValue().getShortName()).isEqualTo("26某集团MTN001");
        assertThat(captor.getValue().getWindCodeNib()).isEqualTo("102600001.IB");
        assertThat(captor.getValue().getReplaceTableName()).isEqualTo("rrs_securityinfo");
        assertThat(captor.getValue().getReplaceRecordId()).isNull();
        // 独立流程不负责修改证券主档
        verify(mapper, never()).addAdjustLog(any(SecurityCodeConversionBo.class));
        verify(mapper, never()).addPoolStatus(any(SecurityCodeConversionBo.class));
    }

    /** 验证在途日志与关联快照一起迁移。 */
    @Test
    public void replaceReferencesShouldUpdatePendingLogAndSnapshots() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        // 准备在途证券引用及两类关联快照
        stubEmptyRuntimeReferences(mapper);
        stubSecurityMaster(mapper);
        when(mapper.queryPendingAdjustLogSecurityReferenceIdList(
                any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.singletonList(11L));
        when(mapper.editAdjustLogSecurityReference(any(SecurityCodeConversionBo.class),
                eq(Collections.singletonList(11L)))).thenReturn(1);
        when(mapper.queryPendingSecuritySnapshotReferenceIdList(
                any(SecurityCodeConversionBo.class), eq(Collections.singletonList(11L))))
                .thenReturn(Collections.singletonList(21L));
        when(mapper.editPendingSecuritySnapshotReference(any(SecurityCodeConversionBo.class),
                eq(Collections.singletonList(21L)))).thenReturn(1);
        when(mapper.queryPendingCrmwSecuritySnapshotReferenceIdList(
                any(SecurityCodeConversionBo.class), eq(Collections.singletonList(11L))))
                .thenReturn(Collections.singletonList(31L));
        when(mapper.editPendingCrmwSecuritySnapshotReference(
                any(SecurityCodeConversionBo.class),
                eq(Collections.singletonList(31L)))).thenReturn(1);

        service.replaceTempSecurityCodeReferences(buildReq());

        verify(mapper).editAdjustLogSecurityReference(any(SecurityCodeConversionBo.class),
                eq(Collections.singletonList(11L)));
        verify(mapper).editPendingSecuritySnapshotReference(any(SecurityCodeConversionBo.class),
                eq(Collections.singletonList(21L)));
        verify(mapper).editPendingCrmwSecuritySnapshotReference(
                any(SecurityCodeConversionBo.class), eq(Collections.singletonList(31L)));
    }

    /** 验证普通池临时代码执行出池并以正式代码重新入池。 */
    @Test
    public void replaceReferencesShouldConvertActivePoolStatus() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        // 准备一条当前有效普通池状态
        stubEmptyRuntimeReferences(mapper);
        stubSecurityMaster(mapper);
        when(mapper.queryActivePoolStatusList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.singletonList(buildPoolStatus()));
        when(mapper.deletePoolStatusSoftById(eq(81L), any(Date.class))).thenReturn(1);
        when(mapper.queryActivePoolStatusCount("102600001.IB", 100L)).thenReturn(0);
        AtomicLong logId = new AtomicLong(1000L);
        when(mapper.addAdjustLog(any(SecurityCodeConversionBo.class))).thenAnswer(invocation -> {
            SecurityCodeConversionBo log = invocation.getArgument(0);
            log.setId(logId.getAndIncrement());
            return 1;
        });
        when(mapper.addSecuritySnapshotFromSource(any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(1);
        when(mapper.addPoolStatus(any(SecurityCodeConversionBo.class))).thenAnswer(invocation -> {
            SecurityCodeConversionBo status = invocation.getArgument(0);
            status.setId(2000L);
            return 1;
        });

        service.replaceTempSecurityCodeReferences(buildReq());

        verify(mapper, times(2)).addAdjustLog(any(SecurityCodeConversionBo.class));
        verify(mapper).deletePoolStatusSoftById(eq(81L), any(Date.class));
        verify(mapper).queryActivePoolStatusCount("102600001.IB", 100L);
        verify(mapper).addPoolStatus(any(SecurityCodeConversionBo.class));
        verify(mapper, times(2)).addSecuritySnapshotFromSource(
                any(), any(), any(), anyBoolean(), any(), any());

        ArgumentCaptor<SecurityCodeConversionBo> logCaptor = ArgumentCaptor.forClass(
                SecurityCodeConversionBo.class);
        verify(mapper, times(2)).addAdjustLog(logCaptor.capture());
        SecurityCodeConversionBo outLog = logCaptor.getAllValues().get(0);
        SecurityCodeConversionBo inLog = logCaptor.getAllValues().get(1);
        assertThat(outLog.getAuditTime()).isNotNull();
        assertThat(outLog.getEntryTime()).isNull();
        assertThat(inLog.getAuditTime()).isNotNull();
        assertThat(inLog.getEntryTime()).isNotNull();
        assertThat(outLog.getAdjustBatchNo()).startsWith("BOND");
        assertThat(inLog.getAdjustBatchNo()).isEqualTo(outLog.getAdjustBatchNo());
    }

    /** 验证 CRMW 池按正式标的、凭证组合和目标池判重。 */
    @Test
    public void replaceReferencesShouldUseFullCrmwCombinationForDuplicateCheck() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        SecurityCodeConversionBo poolStatus = buildPoolStatus();
        poolStatus.setId(82L);
        poolStatus.setPoolType("crmw");
        poolStatus.setCrmwScode("TMP-EXT-001");
        poolStatus.setCrmwStype("mtn");
        // 准备一条当前有效 CRMW 池状态
        stubEmptyRuntimeReferences(mapper);
        stubSecurityMaster(mapper);
        when(mapper.queryActiveCrmwPoolStatusList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.singletonList(poolStatus));
        when(mapper.deleteCrmwPoolStatusSoftById(eq(82L), any(Date.class))).thenReturn(1);
        when(mapper.queryActiveCrmwPoolStatusCount(
                "102600001.IB", "102600001.IB", "mtn", 100L)).thenReturn(1);
        when(mapper.addAdjustLog(any(SecurityCodeConversionBo.class))).thenAnswer(invocation -> {
            SecurityCodeConversionBo log = invocation.getArgument(0);
            log.setId(1000L);
            return 1;
        });
        when(mapper.addCrmwSecuritySnapshotFromSource(
                any(), any(), any(), anyBoolean(), any(), any())).thenReturn(1);

        service.replaceTempSecurityCodeReferences(buildReq());

        verify(mapper).queryActiveCrmwPoolStatusCount(
                "102600001.IB", "102600001.IB", "mtn", 100L);
        // 正式组合已存在时只执行临时代码出池
        verify(mapper, times(1)).addAdjustLog(any(SecurityCodeConversionBo.class));
        verify(mapper, never()).addCrmwPoolStatus(any(SecurityCodeConversionBo.class));
    }

    /** 验证临时证券主数据已不存在时拒绝静默转换。 */
    @Test
    public void replaceReferencesShouldRejectMissingTempSecurityInfo() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        when(mapper.querySecurityInfoListForUpdate(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.replaceTempSecurityCodeReferences(buildReq()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("临时证券主数据不存在");

        verify(mapper, never()).addSecurityCodeConversionLog(
                any(SecurityCodeConversionBo.class));
    }

    /** 验证正式证券主数据已存在时拒绝执行覆盖式转换。 */
    @Test
    public void replaceReferencesShouldRejectExistingFormalSecurityInfo() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        SecurityCodeConversionBo formalSecurity = new SecurityCodeConversionBo();
        formalSecurity.setWindCode("102600001.IB");
        when(mapper.querySecurityInfoListForUpdate(any())).thenReturn(
                Arrays.asList(buildTempSecurityInfo(), formalSecurity));

        assertThatThrownBy(() -> service.replaceTempSecurityCodeReferences(buildReq()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("正式证券主数据已存在");

        verify(mapper, never()).addSecurityCodeConversionLog(
                any(SecurityCodeConversionBo.class));
    }

    /** 验证原临时代码管理流程仍持有有效登记时拒绝重复转换。 */
    @Test
    public void replaceReferencesShouldRejectActiveTempRegistration() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        stubSecurityMaster(mapper);
        when(mapper.querySecurityCategoryType("mtn")).thenReturn("bond");
        when(mapper.queryActiveTempSecurityRegistrationIdListForUpdate("TMP-EXT-001"))
                .thenReturn(Collections.singletonList(9L));

        assertThatThrownBy(() -> service.replaceTempSecurityCodeReferences(buildReq()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("原临时代码管理流程");

        verify(mapper, never()).addSecurityCodeConversionLog(
                any(SecurityCodeConversionBo.class));
    }

    /** 验证包含临时代码的未提交导入批次整批作废并逐条留痕。 */
    @Test
    public void replaceReferencesShouldInvalidateAffectedImportDrafts() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        stubEmptyRuntimeReferences(mapper);
        stubSecurityMaster(mapper);
        when(mapper.queryAffectedImportBatchIdListForUpdate(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.singletonList(41L));
        when(mapper.queryAffectedImportDetailIdListForUpdate(Collections.singletonList(41L)))
                .thenReturn(Arrays.asList(51L, 52L));
        when(mapper.deleteAffectedImportDetailListSoft(
                eq(Collections.singletonList(41L)), any(Date.class))).thenReturn(2);
        when(mapper.deleteAffectedImportBatchListSoft(
                eq(Collections.singletonList(41L)), any(Date.class))).thenReturn(1);

        service.replaceTempSecurityCodeReferences(buildReq());

        verify(mapper).deleteAffectedImportDetailListSoft(
                eq(Collections.singletonList(41L)), any(Date.class));
        verify(mapper).deleteAffectedImportBatchListSoft(
                eq(Collections.singletonList(41L)), any(Date.class));
        verify(mapper, times(4)).addSecurityCodeConversionLog(
                any(SecurityCodeConversionBo.class));
    }

    /** 验证原快照缺失时从主档补建，并将附件关联复制到正式调入日志。 */
    @Test
    public void replaceReferencesShouldFallbackSnapshotAndCopyAttachments() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        stubEmptyRuntimeReferences(mapper);
        stubSecurityMaster(mapper);
        when(mapper.queryActivePoolStatusList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.singletonList(buildPoolStatus()));
        when(mapper.deletePoolStatusSoftById(eq(81L), any(Date.class))).thenReturn(1);
        when(mapper.queryActivePoolStatusCount("102600001.IB", 100L)).thenReturn(0);
        AtomicLong logId = new AtomicLong(3000L);
        when(mapper.addAdjustLog(any(SecurityCodeConversionBo.class))).thenAnswer(invocation -> {
            SecurityCodeConversionBo log = invocation.getArgument(0);
            log.setId(logId.getAndIncrement());
            return 1;
        });
        when(mapper.addSecuritySnapshotFromSource(any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(0);
        when(mapper.addSecuritySnapshotFromMaster(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(1);
        when(mapper.queryActiveAttachmentIdListForUpdate(701L))
                .thenReturn(Arrays.asList(61L, 62L));
        when(mapper.addFormalInAttachmentList(eq(701L), eq(3001L), any(Date.class)))
                .thenReturn(2);
        when(mapper.addPoolStatus(any(SecurityCodeConversionBo.class))).thenAnswer(invocation -> {
            SecurityCodeConversionBo status = invocation.getArgument(0);
            status.setId(4000L);
            return 1;
        });

        service.replaceTempSecurityCodeReferences(buildReq());

        verify(mapper, times(2)).addSecuritySnapshotFromMaster(
                any(), any(), anyBoolean(), any(), any());
        verify(mapper).addFormalInAttachmentList(eq(701L), eq(3001L), any(Date.class));
    }

    /** 验证转换后会形成重复在途业务对象时在任何写入前失败。 */
    @Test
    public void replaceReferencesShouldRejectPendingAdjustConflict() {
        SecurityCodeConversionMapper mapper = mock(SecurityCodeConversionMapper.class);
        SecurityCodeConversionService service = buildService(mapper);
        stubEmptyRuntimeReferences(mapper);
        stubSecurityMaster(mapper);
        when(mapper.queryPendingAdjustLogConflictCount(any(SecurityCodeConversionBo.class)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.replaceTempSecurityCodeReferences(buildReq()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("在途调库记录");

        verify(mapper, never()).addSecurityCodeConversionLog(
                any(SecurityCodeConversionBo.class));
    }

    /** 验证转换方法强制加入调用方事务。 */
    @Test
    public void replaceReferencesShouldRequireExistingTransaction() throws Exception {
        Method method = SecurityCodeConversionService.class.getMethod(
                "replaceTempSecurityCodeReferences", SecurityCodeConversionReq.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    /** 构建待测服务。 */
    private SecurityCodeConversionService buildService(SecurityCodeConversionMapper mapper) {
        SecurityCodeConversionService service = new SecurityCodeConversionService();
        ReflectionTestUtils.setField(service, "securityCodeConversionMapper", mapper);
        return service;
    }

    /** 为与当前场景无关的运行态引用提供空结果。 */
    private void stubEmptyRuntimeReferences(SecurityCodeConversionMapper mapper) {
        when(mapper.querySecurityCategoryType("mtn")).thenReturn("bond");
        when(mapper.queryActiveTempSecurityRegistrationIdListForUpdate("TMP-EXT-001"))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPendingAdjustLogConflictCount(any(SecurityCodeConversionBo.class)))
                .thenReturn(0);
        when(mapper.queryAffectedImportBatchIdListForUpdate(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPendingAdjustLogSecurityReferenceIdList(
                any(SecurityCodeConversionBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPendingAdjustLogCrmwReferenceIdList(
                any(SecurityCodeConversionBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActivePoolStatusList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.<SecurityCodeConversionBo>emptyList());
        when(mapper.queryPoolStatusCrmwReferenceIdList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActiveCrmwPoolStatusList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.<SecurityCodeConversionBo>emptyList());
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActiveMySecurityPoolReferenceIdList(
                any(SecurityCodeConversionBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActiveInReportReferenceIdList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActiveOutReportReferenceIdList(any(SecurityCodeConversionBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryOpenGradeRuleAlertReferenceIdList(
                any(SecurityCodeConversionBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryRuntimeReferenceCount(any(SecurityCodeConversionBo.class))).thenReturn(0);
        when(mapper.addSecurityCodeConversionLog(any(SecurityCodeConversionBo.class))).thenReturn(1);
    }

    /** 准备仅存在临时码、正式码尚未写入的证券主数据。 */
    private void stubSecurityMaster(SecurityCodeConversionMapper mapper) {
        when(mapper.querySecurityInfoListForUpdate(any()))
                .thenReturn(Collections.singletonList(buildTempSecurityInfo()));
    }

    /** 构建外部导入的临时证券主数据。 */
    private SecurityCodeConversionBo buildTempSecurityInfo() {
        SecurityCodeConversionBo bo = new SecurityCodeConversionBo();
        bo.setWindCode("TMP-EXT-001");
        bo.setFullName("某集团临时中票");
        bo.setShortName("临时中票");
        bo.setWindCodeNib("TMP-EXT-001");
        bo.setSecurityType("mtn");
        return bo;
    }

    /** 构建当前有效池状态。 */
    private SecurityCodeConversionBo buildPoolStatus() {
        SecurityCodeConversionBo bo = new SecurityCodeConversionBo();
        bo.setId(81L);
        bo.setSecurityCode("TMP-EXT-001");
        bo.setSecurityShortName("临时中票");
        bo.setSecurityType("mtn");
        bo.setAdjustType("手工调整");
        bo.setAdjustMode("调入");
        bo.setTargetPoolId(100L);
        bo.setTargetPoolName("信用债一级库");
        bo.setPoolType("credit_bond");
        bo.setAuditStatus("20");
        bo.setAdjusterId("u1");
        bo.setAdjusterName("张三");
        bo.setAdjustReason("研究建议");
        bo.setAdjustLogId(701L);
        return bo;
    }

    /** 构建证券代码转换请求。 */
    private SecurityCodeConversionReq buildReq() {
        SecurityCodeConversionReq req = new SecurityCodeConversionReq();
        req.setTempSecurityCode("TMP-EXT-001");
        req.setSecurityCode("102600001.IB");
        req.setSecurityName("26某集团MTN001");
        req.setSecurityFullName("某集团2026年度第一期中期票据");
        req.setSecurityShortName("26某集团MTN001");
        req.setSecurityMarket(MarketCode.CIBM.getCode());
        req.setSecurityType("mtn");
        req.setOprtSource(TempOprtSource.OTHER.getCode());
        return req;
    }
}
