package com.znty.rrs.service;

import com.znty.rrs.mapper.ReportMapper;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.report.ReportDto;
import com.znty.rrs.entity.report.ReportReq;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报告库查询服务测试
 */
public class ReportServiceTest {

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
