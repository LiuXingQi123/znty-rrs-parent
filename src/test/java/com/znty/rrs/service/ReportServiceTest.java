package com.znty.rrs.service;

import com.znty.rrs.mapper.ReportMapper;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.report.ReportDto;
import com.znty.rrs.entity.report.ReportReq;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import com.znty.rrs.exception.BizException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报告库查询服务测试
 */
public class ReportServiceTest {

    /** 详情通过明确来源与 ID 取数，返回同报告的全部附件。 */
    @Test
    public void queryDetailShouldReturnAllAttachmentsAndKeepSourcesSeparate() {
        ReportMapper mapper = mock(ReportMapper.class);
        ReportService service = new ReportService();
        ReflectionTestUtils.setField(service, "reportMapper", mapper);
        for (String source : Arrays.asList("internal", "external")) {
            ReportReq req = new ReportReq();
            req.setReportId(20L);
            req.setSource(source);
            ReportDto report = new ReportDto();
            report.setId(20L);
            SysAttachmentDto first = new SysAttachmentDto();
            first.setId(1L);
            first.setMainId(20L);
            SysAttachmentDto second = new SysAttachmentDto();
            second.setId(2L);
            second.setMainId(20L);
            when(mapper.queryReportDetail(req)).thenReturn(report);
            if ("internal".equals(source)) {
                when(mapper.queryInReportAttachmentList(Collections.singletonList(20L))).thenReturn(Arrays.asList(first, second));
            } else {
                when(mapper.queryOutReportAttachmentList(Collections.singletonList(20L))).thenReturn(Arrays.asList(first, second));
            }
            assertThat(service.queryReportDetail(req).getAttachments()).extracting(SysAttachmentDto::getId).containsExactly(1L, 2L);
        }
    }

    /** 非法来源、缺失主键与不存在的报告必须明确失败。 */
    @Test
    public void queryDetailShouldRejectInvalidIdentityOrMissingReport() {
        ReportService service = new ReportService();
        ReflectionTestUtils.setField(service, "reportMapper", mock(ReportMapper.class));
        ReportReq req = new ReportReq();
        assertThatThrownBy(() -> service.queryReportDetail(req)).isInstanceOf(BizException.class);
        req.setReportId(20L);
        req.setSource("unexpected");
        assertThatThrownBy(() -> service.queryReportDetail(req)).hasMessageContaining("internal/external");
        req.setSource("internal");
        assertThatThrownBy(() -> service.queryReportDetail(req)).hasMessageContaining("不存在或已删除").hasMessageContaining("20");
    }

    /** 验证内部报告分页查询会向数据访问层传递筛选条件 */
    @Test
    public void queryInReportPageShouldPassFiltersToMapper() {
        ReportMapper mapper = mock(ReportMapper.class);
        ReportService service = new ReportService();
        ReflectionTestUtils.setField(service, "reportMapper", mapper);
        ReportReq req = new ReportReq();
        req.setReportTitle("入库");
        req.setSecurityCode("1020");
        req.setReportType("bond_in_report");
        req.setCrteTimeStart("2026-06-01");
        req.setCrteTimeEnd("2026-06-30");
        when(mapper.queryInReportPage(req)).thenReturn(Collections.emptyList());

        service.queryInReportPage(req);

        ArgumentCaptor<ReportReq> captor = ArgumentCaptor.forClass(ReportReq.class);
        verify(mapper).queryInReportPage(captor.capture());
        assertThat(captor.getValue().getReportTitle()).isEqualTo("入库");
        assertThat(captor.getValue().getSecurityCode()).isEqualTo("1020");
        assertThat(captor.getValue().getReportType()).isEqualTo("bond_in_report");
        assertThat(captor.getValue().getCrteTimeStart()).isEqualTo("2026-06-01");
        assertThat(captor.getValue().getCrteTimeEnd()).isEqualTo("2026-06-30");
    }

    /** 验证外部报告分页查询会向数据访问层传递筛选条件 */
    @Test
    public void queryOutReportPageShouldPassFiltersToMapper() {
        ReportMapper mapper = mock(ReportMapper.class);
        ReportService service = new ReportService();
        ReflectionTestUtils.setField(service, "reportMapper", mapper);
        ReportReq req = new ReportReq();
        req.setReportTitle("出库");
        req.setSecurityCode("601318");
        req.setReportType("stock_out_report");
        req.setCrteTimeStart("2026-06-01");
        req.setCrteTimeEnd("2026-06-30");
        when(mapper.queryOutReportPage(req)).thenReturn(Collections.emptyList());

        service.queryOutReportPage(req);

        ArgumentCaptor<ReportReq> captor = ArgumentCaptor.forClass(ReportReq.class);
        verify(mapper).queryOutReportPage(captor.capture());
        assertThat(captor.getValue().getReportTitle()).isEqualTo("出库");
        assertThat(captor.getValue().getSecurityCode()).isEqualTo("601318");
        assertThat(captor.getValue().getReportType()).isEqualTo("stock_out_report");
        assertThat(captor.getValue().getCrteTimeStart()).isEqualTo("2026-06-01");
        assertThat(captor.getValue().getCrteTimeEnd()).isEqualTo("2026-06-30");
    }

    /** 验证内部报告分页查询会回填报告附件 */
    @Test
    public void queryInReportPageShouldFillAttachments() {
        ReportMapper mapper = mock(ReportMapper.class);
        ReportService service = new ReportService();
        ReflectionTestUtils.setField(service, "reportMapper", mapper);
        ReportReq req = new ReportReq();
        ReportDto report = new ReportDto();
        report.setId(10L);
        SysAttachmentDto attachment = new SysAttachmentDto();
        attachment.setId(100L);
        attachment.setMainId(10L);
        attachment.setOriginalFileName("内部报告附件.pdf");
        when(mapper.queryInReportPage(req)).thenReturn(Arrays.asList(report));
        when(mapper.queryInReportAttachmentList(Arrays.asList(10L))).thenReturn(Arrays.asList(attachment));

        PageResult<ReportDto> result = service.queryInReportPage(req);

        verify(mapper).queryInReportAttachmentList(Arrays.asList(10L));
        List<SysAttachmentDto> attachments = result.getRecords().get(0).getAttachments();
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getOriginalFileName()).isEqualTo("内部报告附件.pdf");
    }

    /** 验证外部报告分页查询会回填报告附件 */
    @Test
    public void queryOutReportPageShouldFillAttachments() {
        ReportMapper mapper = mock(ReportMapper.class);
        ReportService service = new ReportService();
        ReflectionTestUtils.setField(service, "reportMapper", mapper);
        ReportReq req = new ReportReq();
        ReportDto report = new ReportDto();
        report.setId(20L);
        SysAttachmentDto attachment = new SysAttachmentDto();
        attachment.setId(200L);
        attachment.setMainId(20L);
        attachment.setOriginalFileName("外部报告附件.pdf");
        when(mapper.queryOutReportPage(req)).thenReturn(Arrays.asList(report));
        when(mapper.queryOutReportAttachmentList(Arrays.asList(20L))).thenReturn(Arrays.asList(attachment));

        PageResult<ReportDto> result = service.queryOutReportPage(req);

        verify(mapper).queryOutReportAttachmentList(Arrays.asList(20L));
        List<SysAttachmentDto> attachments = result.getRecords().get(0).getAttachments();
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getOriginalFileName()).isEqualTo("外部报告附件.pdf");
    }
}
