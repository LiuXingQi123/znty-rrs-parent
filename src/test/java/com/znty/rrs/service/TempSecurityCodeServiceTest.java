package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.exception.BizException;
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
        TempSecurityCodeService service = buildService(mapper);
        TempSecurityCodeReq req = buildAddReq();
        TempSecurityCodeDto.CompanyOption company = new TempSecurityCodeDto.CompanyOption();
        company.setCompanyId(1L);
        company.setFullName("某基础设施建设投资集团有限公司");
        when(mapper.queryCompanyById(1L)).thenReturn(company);
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

    /** 验证转正式证券时替换核心调库引用并写入日志。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldReplaceCoreReferences() throws Exception {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService service = buildService(mapper);
        TempSecurityCodeReq req = buildUpdateReq();
        TempSecurityCodeBo oldBo = buildOldBo();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        when(mapper.querySecurityTypeCount("mtn")).thenReturn(1);
        when(mapper.queryTempSecurityCodeCount("TMP001", 1L)).thenReturn(0);
        when(mapper.querySecurityInfoCount("110001.IB")).thenReturn(0);
        when(mapper.queryAdjustLogSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.singletonList(10L));
        when(mapper.queryPoolStatusSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryAdjustLogCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.singletonList(20L));
        when(mapper.queryCrmwPoolStatusSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());

        service.editTempSecurityCodeToUpdated(req);

        ArgumentCaptor<TempSecurityCodeBo> securityInfoCaptor = ArgumentCaptor.forClass(TempSecurityCodeBo.class);
        verify(mapper).addSecurityInfo(securityInfoCaptor.capture());
        assertThat(securityInfoCaptor.getValue().getSecuritySource()).isEqualTo("temp_converted");
        verify(mapper).editAdjustLogSecurityReference(any(TempSecurityCodeBo.class), org.mockito.Matchers.eq(Collections.singletonList(10L)));
        verify(mapper).editPoolStatusCrmwReference(any(TempSecurityCodeBo.class), org.mockito.Matchers.eq(Collections.singletonList(20L)));
        verify(mapper, times(2)).addTempSecurityCodeUpdateLog(any(TempSecurityCodeBo.class));
        verify(mapper).editTempSecurityInfoToDisabled(any(TempSecurityCodeBo.class));
        verify(mapper).editTempSecurityCodeToUpdated(any(TempSecurityCodeBo.class));
    }

    /** 验证转正式证券已存在时不新增正式证券，避免覆盖来源。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldKeepExistingSecuritySource() throws Exception {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService service = buildService(mapper);
        TempSecurityCodeReq req = buildUpdateReq();
        TempSecurityCodeBo oldBo = buildOldBo();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        when(mapper.querySecurityTypeCount("mtn")).thenReturn(1);
        when(mapper.queryTempSecurityCodeCount("TMP001", 1L)).thenReturn(0);
        when(mapper.querySecurityInfoCount("110001.IB")).thenReturn(1);
        when(mapper.queryAdjustLogSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPoolStatusSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryAdjustLogCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryCrmwPoolStatusSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());

        service.editTempSecurityCodeToUpdated(req);

        verify(mapper).editSecurityInfo(any(TempSecurityCodeBo.class));
        verify(mapper, never()).addSecurityInfo(any(TempSecurityCodeBo.class));
        verify(mapper).editTempSecurityInfoToDisabled(any(TempSecurityCodeBo.class));
    }

    /** 验证已被核心调库业务引用的临时代码不允许删除。 */
    @Test
    public void deleteTempSecurityCodeShouldRejectWhenReferenced() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService service = buildService(mapper);
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
        TempSecurityCodeService service = buildService(mapper);
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
    private TempSecurityCodeService buildService(TempSecurityCodeMapper mapper) {
        TempSecurityCodeService service = new TempSecurityCodeService();
        ReflectionTestUtils.setField(service, "tempSecurityCodeMapper", mapper);
        return service;
    }

    /** 构建新增请求。 */
    private TempSecurityCodeReq buildAddReq() throws Exception {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setTempSecurityName("某基建集团临时中票");
        req.setTempSecurityCode("TMP001");
        req.setTempSecurityMarket(MarketCode.CIBM.getCode());
        req.setTempSecurityType("mtn");
        req.setTempCompanyId(1L);
        req.setTempIssueDate(parseDate("2026-07-01"));
        req.setTempMaturityDate(parseDate("2031-07-01"));
        return req;
    }

    /** 构建更新请求。 */
    private TempSecurityCodeReq buildUpdateReq() throws Exception {
        TempSecurityCodeReq req = buildAddReq();
        req.setId(1L);
        req.setSecurityName("26某基建MTN001");
        req.setSecurityCode("110001.IB");
        req.setSecurityMarket(MarketCode.CIBM.getCode());
        req.setSecurityType("mtn");
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
        bo.setTempCompanyId(1L);
        bo.setTempCompanyNameSnapshot("某基础设施建设投资集团有限公司");
        bo.setStatus(TempStatus.TEMPORARY.getCode());
        return bo;
    }

    /** 解析日期。 */
    private Date parseDate(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd").parse(value);
    }
}
