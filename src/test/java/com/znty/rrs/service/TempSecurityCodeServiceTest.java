package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempOprtSource;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.entity.securitycodeconversion.SecurityCodeConversionReq;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 临时代码管理服务测试。 */
public class TempSecurityCodeServiceTest {

    /** 临时主档使用前缀身份，市场码保留原始录入值。 */
    @Test
    public void addShouldSeparateTemporaryIdentityFromMarketCode() throws Exception {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService service = buildService(mapper, mock(SecurityPoolAdjustMapper.class));
        when(mapper.querySecurityTypeCount("mtn")).thenReturn(1);
        TempSecurityCodeDto.CompanyOption company = new TempSecurityCodeDto.CompanyOption();
        company.setFullName("某集团");
        when(mapper.queryCompanyByCode("C10001")).thenReturn(company);
        service.addTempSecurityCode(buildAddReq());
        ArgumentCaptor<TempSecurityCodeBo> captor = ArgumentCaptor.forClass(TempSecurityCodeBo.class);
        verify(mapper).addSecurityInfo(captor.capture());
        assertThat(captor.getValue().getIsinCode()).isEqualTo("TEMP:TMP001");
        assertThat(captor.getValue().getSecurityCode()).isEqualTo("TMP001");
        assertThat(captor.getValue().getSecurityMarket()).isEqualTo("CIBM");
    }

    /** 转正登记状态和运行态引用均在原事务内完成。 */
    @Test
    public void conversionShouldDelegateWithOfficialIsin() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService service = buildService(mapper, mock(SecurityPoolAdjustMapper.class));
        SecurityCodeConversionService conversion = mock(SecurityCodeConversionService.class);
        ReflectionTestUtils.setField(service, "securityCodeConversionService", conversion);
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(buildOldBo());
        when(mapper.queryFormalSecurityByCode("CN0000000001")).thenReturn(buildFormalSecurityOption());
        when(mapper.editTempSecurityCodeToUpdated(any(TempSecurityCodeBo.class))).thenReturn(1);
        service.editTempSecurityCodeToUpdated(buildUpdateReq());
        ArgumentCaptor<SecurityCodeConversionReq> captor =
                ArgumentCaptor.forClass(SecurityCodeConversionReq.class);
        verify(conversion).replaceTempSecurityCodeReferences(captor.capture());
        assertThat(captor.getValue().getIsinCode()).isEqualTo("CN0000000001");
        assertThat(captor.getValue().getTempSecurityCode()).isEqualTo("TMP001");
    }

    /** 旧市场码请求不能静默回退为新身份。 */
    @Test
    public void conversionShouldRejectLegacyRequest() {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setId(1L); req.setSecurityCode("110001.IB");
        assertThatThrownBy(() -> buildService(mock(TempSecurityCodeMapper.class), mock(SecurityPoolAdjustMapper.class))
                .editTempSecurityCodeToUpdated(req)).isInstanceOf(BizException.class).hasMessageContaining("ISIN");
    }

    /** 保护前缀后的最大长度及CRMW银行间限制。 */
    @Test
    public void addShouldValidateLengthAndCrmwMarket() throws Exception {
        TempSecurityCodeService service = buildService(mock(TempSecurityCodeMapper.class), mock(SecurityPoolAdjustMapper.class));
        TempSecurityCodeReq longReq = buildAddReq();
        longReq.setTempSecurityCode(String.join("", Collections.nCopies(96, "A")));
        assertThatThrownBy(() -> service.addTempSecurityCode(longReq)).isInstanceOf(BizException.class).hasMessageContaining("95");
        TempSecurityCodeReq crmw = buildAddReq(); crmw.setTempSecurityType("crmw"); crmw.setTempSecurityMarket("SSE");
        assertThatThrownBy(() -> service.addTempSecurityCode(crmw)).isInstanceOf(BizException.class).hasMessageContaining("银行间");
    }

    /** 取消发行以临时身份定位主档。 */
    @Test
    public void cancellationShouldKeepPrefixedIdentity() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService service = buildService(mapper, mock(SecurityPoolAdjustMapper.class));
        when(mapper.queryTempSecurityCodeById(1L)).thenReturn(buildOldBo());
        TempSecurityCodeReq req = new TempSecurityCodeReq(); req.setId(1L);
        service.editTempSecurityCodeToCancelled(req);
        ArgumentCaptor<TempSecurityCodeBo> captor = ArgumentCaptor.forClass(TempSecurityCodeBo.class);
        verify(mapper).editSecurityInfoToCancelled(captor.capture());
        assertThat(captor.getValue().getTempIsinCode()).isEqualTo("TEMP:TMP001");
    }

    /** 构建测试服务。 */
    private TempSecurityCodeService buildService(TempSecurityCodeMapper mapper,
                                                 SecurityPoolAdjustMapper adjustMapper) {
        TempSecurityCodeService service = new TempSecurityCodeService();
        ReflectionTestUtils.setField(service, "tempSecurityCodeMapper", mapper);
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", adjustMapper);
        ReflectionTestUtils.setField(service, "securityCodeConversionService", mock(SecurityCodeConversionService.class));
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
        req.setIsinCode("CN0000000001");
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
        option.setIsinCode("CN0000000001");
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
