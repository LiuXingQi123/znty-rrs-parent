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

    /** 验证转正式证券时替换核心调库引用并写入日志。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldReplaceCoreReferences() throws Exception {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService service = buildService(mapper);
        TempSecurityCodeReq req = buildUpdateReq();
        TempSecurityCodeBo oldBo = buildOldBo();
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(oldBo);
        when(mapper.queryFormalSecurityByCode("110001.IB")).thenReturn(buildFormalSecurityOption());
        when(mapper.queryAdjustLogSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.singletonList(10L));
        when(mapper.queryPoolStatusSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryAdjustLogCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.singletonList(20L));
        when(mapper.queryCrmwPoolStatusSecurityReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());
        when(mapper.queryCrmwPoolStatusCrmwReferenceIdList(any(TempSecurityCodeBo.class))).thenReturn(Collections.<Long>emptyList());

        service.editTempSecurityCodeToUpdated(req);

        // 正式证券已存在于主数据，转正不再新增/改写正式主数据
        verify(mapper, never()).addSecurityInfo(any(TempSecurityCodeBo.class));
        verify(mapper, never()).editSecurityInfo(any(TempSecurityCodeBo.class));
        verify(mapper).editAdjustLogSecurityReference(any(TempSecurityCodeBo.class), org.mockito.Matchers.eq(Collections.singletonList(10L)));
        verify(mapper).editPoolStatusCrmwReference(any(TempSecurityCodeBo.class), org.mockito.Matchers.eq(Collections.singletonList(20L)));
        verify(mapper, times(2)).addTempSecurityCodeUpdateLog(any(TempSecurityCodeBo.class));
        verify(mapper).editTempSecurityInfoToDisabled(any(TempSecurityCodeBo.class));
        ArgumentCaptor<TempSecurityCodeBo> updateCaptor = ArgumentCaptor.forClass(TempSecurityCodeBo.class);
        verify(mapper).editTempSecurityCodeToUpdated(updateCaptor.capture());
        // 临时字段保持库内原值，正式字段取自主数据
        assertThat(updateCaptor.getValue().getTempSecurityCode()).isEqualTo("TMP001");
        assertThat(updateCaptor.getValue().getSecurityCode()).isEqualTo("110001.IB");
        assertThat(updateCaptor.getValue().getSecurityName()).isEqualTo("26某基建MTN001");
        assertThat(updateCaptor.getValue().getSecurityMarket()).isEqualTo(MarketCode.CIBM.getCode());
    }

    /** 验证正式证券不存在时转正失败。 */
    @Test
    public void editTempSecurityCodeToUpdatedShouldRejectMissingFormalSecurity() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService service = buildService(mapper);
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

    /** 解析日期。 */
    private Date parseDate(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd").parse(value);
    }
}
