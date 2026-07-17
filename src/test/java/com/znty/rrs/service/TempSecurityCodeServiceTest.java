package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        assertThat(captor.getValue().getSecurityName()).isEqualTo("某基建集团临时中票");
        assertThat(captor.getValue().getSecurityMarket()).isEqualTo(MarketCode.CIBM.getCode());
        assertThat(captor.getValue().getSecuritySource()).isEqualTo("temporary");
    }

    /** 验证仅在途日志时只做字段替换，不写出入库业务单。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldOnlyReplacePendingLogs() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        SecurityPoolAdjustMapper adjustMapper = mock(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService service = buildService(mapper, adjustMapper);
        TempSecurityCodeReq req = buildUpdateReq();
        TempSecurityCodeBo oldBo = buildOldBo();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.queryPendingAdjustLogSecurityReferenceIdList(any(TempSecurityCodeBo.class)))
                .thenReturn(Collections.singletonList(10L));
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

        service.editTempSecurityCodeToUpdated(req);

        verify(mapper).editAdjustLogSecurityReference(any(TempSecurityCodeBo.class),
                eq(Collections.singletonList(10L)));
        verify(mapper).addTempSecurityCodeUpdateLog(any(TempSecurityCodeBo.class));
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

        service.editTempSecurityCodeToUpdated(req);

        // 出库 + 入库 两条调库日志
        verify(adjustMapper, times(2)).addAdjustLog(any(IpAdjustLogBo.class));
        verify(mapper).deletePoolStatusSoftById(eq(88L), any(Date.class));
        verify(adjustMapper).addPoolStatus(any(IpAdjustLogBo.class));
        ArgumentCaptor<IpAdjustLogBo> logCaptor = ArgumentCaptor.forClass(IpAdjustLogBo.class);
        verify(adjustMapper, times(2)).addAdjustLog(logCaptor.capture());
        assertThat(logCaptor.getAllValues().get(0).getAdjustReason()).isEqualTo("债券临时代码调出");
        assertThat(logCaptor.getAllValues().get(0).getAdjustMode()).isEqualTo("调出");
        assertThat(logCaptor.getAllValues().get(1).getSecurityCode()).isEqualTo("110001.IB");
        assertThat(logCaptor.getAllValues().get(1).getAdjustMode()).isEqualTo("调入");
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
        when(mapper.queryActivePoolStatusCount("110001.IB", "mtn", 100L)).thenReturn(1);

        service.editTempSecurityCodeToUpdated(req);

        // 仅出库一条
        verify(adjustMapper, times(1)).addAdjustLog(any(IpAdjustLogBo.class));
        verify(mapper).deletePoolStatusSoftById(eq(88L), any(Date.class));
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
        assertThat(captor.getValue().getCancelDate()).isNotBlank();
    }

    /** 构建测试服务。 */
    private TempSecurityCodeService buildService(TempSecurityCodeMapper mapper,
                                                 SecurityPoolAdjustMapper adjustMapper) {
        TempSecurityCodeService service = new TempSecurityCodeService();
        ReflectionTestUtils.setField(service, "tempSecurityCodeMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        return service;
    }

    /** 构建新增请求。 */
    private TempSecurityCodeReq buildAddReq() throws Exception {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setTempSecurityName("某基建集团临时中票");
        req.setTempSecurityCode("TMP001");
        req.setTempSecurityMarket(MarketCode.CIBM.getCode());
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
        bo.setTempSecurityMarket(MarketCode.CIBM.getCode());
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
        option.setSecurityMarket(MarketCode.CIBM.getCode());
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
