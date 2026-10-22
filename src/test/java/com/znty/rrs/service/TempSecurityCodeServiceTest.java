package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempOprtSource;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

/** 临时代码管理服务测试。 */
public class TempSecurityCodeServiceTest {

    /** 验证新增临时代码时同步写入证券主数据。 */
    @Test
    public void addTempSecurityCodeShouldSyncSecurityInfo() throws Exception {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeReq req = buildAddReq();
        TempSecurityCodeDto.CompanyOption company = new TempSecurityCodeDto.CompanyOption();
        company.setCompanyCode("C10001");
        company.setFullName("某交投集团");
        when(mapper.queryCompanyByCode("C10001")).thenReturn(company);
        when(mapper.querySecurityTypeCount("mtn")).thenReturn(1);
        when(mapper.queryTempSecurityCodeCount("TMP001", null)).thenReturn(0);
        when(mapper.querySecurityInfoCount("TMP001")).thenReturn(0);

        service.addTempSecurityCode(req);

        ArgumentCaptor<TempSecurityCodeBo> captor = ArgumentCaptor.forClass(TempSecurityCodeBo.class);
        verify(mapper).addSecurityInfo(captor.capture());
        assertThat(captor.getValue().getSecurityCode()).isEqualTo("TMP001");
        assertThat(captor.getValue().getSecId()).isEqualTo("TEMP_TMP001");
        ArgumentCaptor<TempSecurityCodeBo> tempCaptor = ArgumentCaptor.forClass(TempSecurityCodeBo.class);
        verify(mapper).addTempSecurityCode(tempCaptor.capture());
        assertThat(tempCaptor.getValue().getSecId()).isEqualTo(captor.getValue().getSecId());
        assertThat(captor.getValue().getSecurityName()).isEqualTo("某基建集团临时中票");
        assertThat(captor.getValue().getSecurityMarket()).isEqualTo(MarketCode.NIB.getCode());
        assertThat(captor.getValue().getTempCompanyCode()).isEqualTo("C10001");
        assertThat(captor.getValue().getSecuritySource()).isEqualTo("temporary");
    }

    /** 验证 CRMW 仅有流程中申请时换码并补快照，不立即出入池。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldOnlyReplacePendingLogs() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeReq req = buildUpdateReq();
        TempSecurityCodeBo oldBo = buildOldBo();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        IpAdjustLogBo pending = new IpAdjustLogBo();
        pending.setId(10L);
        pending.setSecurityCode("TMP001");
        pending.setSecurityType("mtn");
        pending.setTargetPoolId(200L);
        pending.setPoolType("crmw");
        pending.setCrmwScode("K.IB");
        pending.setCrmwStype("crmw");
        pending.setAuditStatus("00");
        pending.setAdjustMode("调入");
        pending.setAdjustBatchNo("ORIGINAL");
        when(mapper.queryCrmwPendingLogList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.singletonList(pending));
        when(mapper.queryPendingAdjustLogCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActivePoolStatusList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<IpPoolStatusBo>emptyList());
        when(mapper.queryPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActiveCrmwPoolStatusList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<IpPoolStatusBo>emptyList());
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.editTempSecurityCodeToUpdated(any(TempSecurityCodeBo.class))).thenReturn(1);

        service.editTempSecurityCodeToUpdated(req);

        verify(mapper).editConversionPendingLog(any(IpAdjustLogBo.class), eq("TMP001"));
        verify(mapper, times(2)).addTempSecurityCodeUpdateLog(any(TempSecurityCodeBo.class));
        // 在途场景不写业务出入库
        verify(adjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
        verify(adjustMapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
        verify(mapper, never()).deletePoolStatusSoftById(anyLong(), any(Date.class));
        verify(mapper).editTempSecurityInfoToDisabled(any(TempSecurityCodeBo.class));
        verify(mapper).editTempSecurityCodeToUpdated(any(TempSecurityCodeBo.class));
    }

    /** 验证已在池时生成临时出库 + 正式入库业务日志。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldOutAndInWhenInPool() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeReq req = buildUpdateReq();
        TempSecurityCodeBo oldBo = buildOldBo();
        IpPoolStatusBo poolStatus = buildPoolStatus();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.queryPendingAdjustLogSecurityReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPendingAdjustLogCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActivePoolStatusList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.singletonList(poolStatus));
        when(mapper.queryPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActiveCrmwPoolStatusList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<IpPoolStatusBo>emptyList());
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActivePoolStatusCount("110001.IB", "mtn", 100L)).thenReturn(0);
        when(mapper.editTempSecurityCodeToUpdated(any(TempSecurityCodeBo.class))).thenReturn(1);

        service.editTempSecurityCodeToUpdated(req);

        // 出库 + 入库 两条调库日志
        verify(adjustMapper, times(2)).addAdjustLog(any(IpAdjustLogBo.class));
        verify(mapper).deleteOrdinaryPoolStatusForConversion(eq(88L), any(Date.class));
        verify(adjustMapper).addPoolStatus(any(IpAdjustLogBo.class));
        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(adjustMapper, times(2)).addAdjustLog(logCaptor.capture());
        assertThat(logCaptor.getAllValues().get(0).getAdjustReason())
                .isEqualTo("债券临时代码调出（临时代码：TMP001；正式代码：110001.IB）");
        assertThat(logCaptor.getAllValues().get(0).getAdjustAdvice())
                .isEqualTo(logCaptor.getAllValues().get(0).getAdjustReason());
        assertThat(logCaptor.getAllValues().get(0).getAdjustMode()).isEqualTo("调出");
        assertThat(logCaptor.getAllValues().get(1).getSecurityCode()).isEqualTo("110001.IB");
        assertThat(logCaptor.getAllValues().get(1).getAdjustMode()).isEqualTo("调入");
        assertThat(logCaptor.getAllValues().get(1).getAdjustReason())
                .isEqualTo("研究建议（代码替换：TMP001→110001.IB；本条正式代码：110001.IB）");
        assertThat(logCaptor.getAllValues().get(1).getAdjustAdvice())
                .isEqualTo(logCaptor.getAllValues().get(1).getAdjustReason());
    }

    /** 验证正式码已在池时只出不入。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldOnlyOutWhenFormalAlreadyInPool() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeReq req = buildUpdateReq();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(buildOldBo());
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.queryPendingAdjustLogSecurityReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPendingAdjustLogCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActivePoolStatusList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.singletonList(buildPoolStatus()));
        when(mapper.queryPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActiveCrmwPoolStatusList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<IpPoolStatusBo>emptyList());
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        // 正式已在池
        IpPoolStatusBo formalStatus = buildPoolStatus();
        formalStatus.setSecurityCode("110001.IB");
        when(mapper.queryFormalGroupPoolStatusList(any())).thenReturn(Collections.singletonList(formalStatus));
        when(mapper.editTempSecurityCodeToUpdated(any(TempSecurityCodeBo.class))).thenReturn(1);

        service.editTempSecurityCodeToUpdated(req);

        // 仅出库一条
        verify(adjustMapper, times(1)).addAdjustLog(any(IpAdjustLogBo.class));
        verify(mapper).deleteOrdinaryPoolStatusForConversion(eq(88L), any(Date.class));
        verify(adjustMapper, never()).addPoolStatus(any(IpAdjustLogBo.class));
    }

    /** 验证正式证券不存在时转正失败。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldRejectMissingFormalSecurity() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setId(1L);
        req.setSecurityCode("110001.IB");
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(buildOldBo());
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(null);

        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("正式证券不存在或不可用");
    }

    /** 验证定时任务入口记录 job 来源，且自动调库日志不写调整建议。 */
    @Test
    public void editTempSecurityCodeToUpdatedByJobShouldUseMappedFormalCode() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeBo oldBo = buildOldBo();
        oldBo.setSecurityCode("110001.IB");
        IpPoolStatusBo crmwPoolStatus = buildPoolStatus();
        crmwPoolStatus.setId(89L);
        crmwPoolStatus.setPoolType("crmw");
        crmwPoolStatus.setTargetPoolId(200L);
        crmwPoolStatus.setCrmwScode("K.IB");
        crmwPoolStatus.setCrmwStype("crmw");
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.queryPendingAdjustLogSecurityReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPendingAdjustLogCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActivePoolStatusList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.singletonList(buildPoolStatus()));
        when(mapper.queryPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActiveCrmwPoolStatusList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.singletonList(crmwPoolStatus));
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.<Long>emptyList());
        when(mapper.queryActivePoolStatusCount("110001.IB", "mtn", 100L)).thenReturn(0);
        when(mapper.queryActiveCrmwPoolStatusCount("110001.IB", "mtn", 100L)).thenReturn(0);
        when(mapper.editTempSecurityCodeToUpdated(any(TempSecurityCodeBo.class))).thenReturn(1);

        service.editTempSecurityCodeToUpdatedByJob(1L);

        ArgumentCaptor<TempSecurityCodeBo> captor = ArgumentCaptor.forClass(TempSecurityCodeBo.class);
        verify(mapper).editTempSecurityCodeToUpdated(captor.capture());
        assertThat(captor.getValue().getSecurityCode()).isEqualTo("110001.IB");
        assertThat(captor.getValue().getOprtSource()).isEqualTo(TempOprtSource.JOB.getCode());
        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(adjustMapper, times(4)).addAdjustLog(logCaptor.capture());
        assertThat(logCaptor.getAllValues().get(0).getAdjustReason())
                .isEqualTo("债券临时代码调出（临时代码：TMP001；正式代码：110001.IB）");
        assertThat(logCaptor.getAllValues().get(1).getAdjustReason())
                .isEqualTo("研究建议（代码替换：TMP001→110001.IB；本条正式代码：110001.IB）");
        for (IpAdjustLogBo log : logCaptor.getAllValues()) {
            assertThat(log.getAdjustAdvice()).isNull();
            assertThat(log.getAdjusterId()).isEqualTo("0");
            assertThat(log.getAdjusterName()).isEqualTo("系统");
        }
        ArgumentCaptor<IpAdjustLogBo> statusCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(adjustMapper).addPoolStatus(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getAdjustAdvice()).isNull();
        CrmwPoolAdjustMapper crmwMapper = (CrmwPoolAdjustMapper) ReflectionTestUtils.getField(
                ReflectionTestUtils.getField(service, "businessConverter"), "crmwPoolAdjustMapper");
        ArgumentCaptor<IpAdjustLogBo> crmwStatusCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(crmwMapper).addPoolStatus(crmwStatusCaptor.capture());
        assertThat(crmwStatusCaptor.getValue().getAdjustAdvice()).isNull();
        verify(mapper).editTempSecurityInfoToDisabled(any(TempSecurityCodeBo.class));
    }

    /** 验证主表未更新成功时不继续改调库/池状态。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldRejectWhenMainRowNotUpdated() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeReq req = buildUpdateReq();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(buildOldBo());
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.editTempSecurityCodeToUpdated(any(TempSecurityCodeBo.class))).thenReturn(0);

        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已不是临时状态");
        verify(mapper, never()).queryPendingAdjustLogSecurityReferenceIdList(any(TempSecurityCodeBo.class));
        verify(mapper, never()).editTempSecurityInfoToDisabled(any(TempSecurityCodeBo.class));
        verify(adjustMapper, never()).addAdjustLog(any(IpAdjustLogBo.class));
    }

    /** 无业务引用时映射日志插入零行，不能继续报告转正成功。 */
    @Test
    public void conversionShouldRejectWhenSummaryAuditNotInserted() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(buildOldBo());
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.editTempSecurityCodeToUpdated(any())).thenReturn(1);
        when(mapper.addTempSecurityCodeUpdateLog(any())).thenReturn(0);

        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(buildUpdateReq()))
                .isInstanceOf(BizException.class).hasMessageContaining("映射日志写入失败", "登记ID=1");
        verify(mapper, never()).editTempSecurityInfoToDisabled(any());
    }

    /** CRMW 凭证引用逐条留痕，使用实际表名标记和被替换记录 ID。 */
    @Test
    public void credentialReferencesShouldRecordEveryChangedRow() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(buildOldBo());
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.editTempSecurityCodeToUpdated(any())).thenReturn(1);
        when(mapper.queryPendingAdjustLogCrmwReferenceIdList(any())).thenReturn(Arrays.asList(10L, 11L));
        when(mapper.queryPoolStatusCrmwReferenceIdList(any())).thenReturn(Collections.singletonList(88L));
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any())).thenReturn(Collections.singletonList(89L));
        List<String> auditRows = new ArrayList<>();
        doAnswer(call -> {
            TempSecurityCodeBo audit = call.getArgument(0);
            auditRows.add(audit.getReplaceTableName() + ":" + audit.getReplaceRecordId());
            assertThat(audit.getTempSecurityCode()).isEqualTo("TMP001");
            assertThat(audit.getSecurityCode()).isEqualTo("110001.IB");
            assertThat(audit.getReplaceStatus()).isEqualTo("success");
            return 1;
        }).when(mapper).addTempSecurityCodeUpdateLog(any());

        service.editTempSecurityCodeToUpdated(buildUpdateReq());

        assertThat(auditRows).containsExactlyInAnyOrder("ip_adjust_log.crmw:10", "ip_adjust_log.crmw:11",
                "ip_pool_status.crmw:88", "ip_pool_status_crmw.crmw:89", "rrs_temp_security_code:1");
        verify(mapper).editAdjustLogCrmwReference(any(), eq(Arrays.asList(10L, 11L)));
        verify(mapper).editPoolStatusCrmwReference(any(), eq(Collections.singletonList(88L)));
        verify(mapper).editCrmwPoolStatusCrmwReference(any(), eq(Collections.singletonList(89L)));
    }

    /** CRMW 凭证明细插入零行时应中断转换，不能只留下成功映射。 */
    @Test
    public void credentialConversionShouldRejectWhenDetailAuditNotInserted() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(buildOldBo());
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.editTempSecurityCodeToUpdated(any())).thenReturn(1);
        when(mapper.queryPoolStatusCrmwReferenceIdList(any())).thenReturn(Collections.singletonList(88L));
        when(mapper.addTempSecurityCodeUpdateLog(any())).thenReturn(0);

        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(buildUpdateReq()))
                .isInstanceOf(BizException.class).hasMessageContaining("明细日志写入失败", "ip_pool_status.crmw", "88");
        verify(mapper, never()).editTempSecurityInfoToDisabled(any());
    }

    /** 验证已被核心调库业务引用的临时代码不允许删除。 */
    @Test
    public void deleteTempSecurityCodeShouldRejectWhenReferenced() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeBo oldBo = buildOldBo();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        when(mapper.queryCoreReferenceCount(oldBo)).thenReturn(1);
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setId(1L);

        assertThatThrownBy(() -> service.deleteTempSecurityCode(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("该临时代码已被调库业务使用，无法删除");
    }

    /** 验证取消发行时同步更新临时证券主数据。 */
    @Test
    public void editTempSecurityCodeToCancelledShouldUpdateSecurityInfo() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeBo oldBo = buildOldBo();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setId(1L);

        service.editTempSecurityCodeToCancelled(req);

        ArgumentCaptor<TempSecurityCodeBo> captor = ArgumentCaptor.forClass(TempSecurityCodeBo.class);
        verify(mapper).editSecurityInfoToCancelled(captor.capture());
        assertThat(captor.getValue().getTempSecurityCode()).isEqualTo("TMP001");
        assertThat(captor.getValue().getCancelDate()).matches("\\d{8}");
    }

    /** 构建测试服务。 */
    private TempSecurityCodeService buildService(TempSecurityCodeMapper mapper,
                                                 SecurityPoolAdjustMapper adjustMapper) {
        TempSecurityCodeService service = new TempSecurityCodeService();
        SecurityMarketService markets = mock(SecurityMarketService.class);
        when(markets.queryMarketCode(anyString())).thenReturn("NIB");
        ReflectionTestUtils.setField(service, "securityMarketService", markets);
        ReflectionTestUtils.setField(service, "tempSecurityCodeMapper", mapper);
        when(mapper.addTempSecurityCodeUpdateLog(any(TempSecurityCodeBo.class))).thenReturn(1);
        when(mapper.queryOrdinaryConversionPoolIdList(any())).thenReturn(new ArrayList<>(Collections.singletonList(100L)));
        when(mapper.deleteOrdinaryPoolStatusForConversion(anyLong(), any(Date.class))).thenReturn(1);
        when(mapper.deleteCrmwPoolStatusSoftById(anyLong(), any(Date.class))).thenReturn(1);
        when(mapper.editConversionPendingLog(any(), anyString())).thenReturn(1);
        when(adjustMapper.addPoolStatus(any())).thenReturn(1);
        when(adjustMapper.addAdjustSecuritySnapshot(any())).thenReturn(1);
        doAnswer(call -> {
            IpAdjustLogBo log = call.getArgument(0);
            log.setId(1000L);
            return 1;
        }).when(adjustMapper).addAdjustLog(any());
        SecurityInfoBo member = new SecurityInfoBo();
        member.setWindCode("110001.IB");
        member.setShortName("26某基建MTN001");
        member.setSecurityType("mtn");
        member.setSecurityStatus("L");
        member.setSecuritySource("official");
        when(adjustMapper.querySecurityBoByCode("110001.IB")).thenReturn(member);
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(100L);
        pool.setStatus("enabled");
        InvestmentPoolBo crmwPool = new InvestmentPoolBo();
        crmwPool.setId(200L);
        crmwPool.setPoolType("crmw");
        when(mapper.queryConversionPoolList(any())).thenReturn(Arrays.asList(pool, crmwPool));
        TempSecurityCodeBusinessConverter converter = new TempSecurityCodeBusinessConverter();
        ReflectionTestUtils.setField(converter, "tempSecurityCodeMapper", mapper);
        ReflectionTestUtils.setField(converter, "securityPoolAdjustMapper", adjustMapper);
        CrmwPoolAdjustMapper crmwMapper = mock(CrmwPoolAdjustMapper.class);
        when(crmwMapper.addPoolStatus(any())).thenReturn(1);
        when(crmwMapper.addAdjustSecuritySnapshotCrmw(any())).thenReturn(1);
        ReflectionTestUtils.setField(converter, "crmwPoolAdjustMapper", crmwMapper);
        ReflectionTestUtils.setField(converter, "securityMarketService", markets);
        ReflectionTestUtils.setField(service, "businessConverter", converter);
        return service;
    }

    /** 构建新增请求。 */
    private TempSecurityCodeReq buildAddReq() throws Exception {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setTempSecurityName("某基建集团临时中票");
        req.setTempSecurityCode("TMP001");
        req.setTempSecurityMarket(MarketCode.NIB.getCode());
        req.setTempSecurityType("mtn");
        req.setTempCompanyCode("C10001");
        req.setTempIssueDate(parseDate("2026-07-01"));
        req.setTempMaturityDate(parseDate("2031-07-01"));
        return req;
    }

    /** 构建更新请求（仅需 id + 正式证券代码）。 */
    private TempSecurityCodeReq buildUpdateReq() {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setId(1L);
        req.setSecurityCode("110001.IB");
        return req;
    }

    /** 构建原始临时代码记录。 */
    private TempSecurityCodeBo buildOldBo() {
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(1L);
        bo.setTempSecurityName("某基建集团临时中票");
        bo.setTempSecurityCode("TMP001");
        bo.setTempSecurityMarket(MarketCode.NIB.getCode());
        bo.setTempSecurityType("mtn");
        bo.setTempCompanyCode("C10001");
        bo.setTempCompanyNameSnapshot("某基础设施建设投资集团有限公司");
        bo.setStatus(TempStatus.TEMPORARY.getCode());
        return bo;
    }

    /** 构建正式证券主数据选项。 */
    private TempSecurityCodeDto.FormalSecurityOption buildFormalSecurityOption() {
        TempSecurityCodeDto.FormalSecurityOption option = new TempSecurityCodeDto.FormalSecurityOption();
        option.setSecurityCode("110001.IB");
        option.setSecurityName("26某基建MTN001");
        option.setSecurityMarket(MarketCode.NIB.getCode());
        option.setSecurityType("mtn");
        option.setSecurityTypeName("中期票据");
        return option;
    }

    /** 构建在池状态。 */
    private IpPoolStatusBo buildPoolStatus() {
        IpPoolStatusBo status = new IpPoolStatusBo();
        status.setId(88L);
        status.setSecurityCode("TMP001");
        status.setSecurityShortName("某基建集团临时中票");
        status.setSecurityType("mtn");
        status.setTargetPoolId(100L);
        status.setTargetPoolName("信用债一级库");
        status.setPoolType("credit_bond");
        status.setAdjustType("手工调整");
        status.setAdjustMode("调入");
        status.setAuditStatus("20");
        status.setAdjusterId("u1");
        status.setAdjusterName("张三");
        status.setAdjustReason("研究建议");
        return status;
    }

    /** 解析日期。 */
    private Date parseDate(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd").parse(value);
    }
}
