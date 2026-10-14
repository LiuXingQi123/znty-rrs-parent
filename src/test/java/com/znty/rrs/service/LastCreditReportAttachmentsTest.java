package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustReq;
import com.znty.rrs.entity.report.ReportDto;
import com.znty.rrs.entity.report.ReportReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import com.znty.rrs.mapper.ForbiddenAbsPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 最近报告自动回填必须带回整份报告的附件。 */
public class LastCreditReportAttachmentsTest {
    /** 证券池自动回填不再把定位用的一份 PDF 当成整份报告。 */
    @Test
    public void securityLastReportShouldReturnBothFiles() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        when(mapper.queryLastInboundLogIdWithCreditReportBySecurity("BOND")).thenReturn(88L);
        // 构造明确的日志及报告库附件定位信息。
        SysAttachmentBo file = reportFile();
        when(mapper.queryFirstCreditReportAttachment(88L)).thenReturn(file);
        when(mapper.queryReportLibraryAttachmentByFileName("one.pdf", "rrs_report_in", "report_in")).thenReturn(file);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        // 复用报告详情的完整附件返回值。
        ReflectionTestUtils.setField(service, "reportService", reportService());
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setSecurityCode("BOND");
        assertThat(service.queryLastCreditReport(req).getAttachments()).extracting(SysAttachmentDto::getId).containsExactly(1L, 2L);
    }

    /** 禁投 ABS 自动回填与证券池保持一致。 */
    @Test
    public void absLastReportShouldReturnBothFiles() {
        ForbiddenAbsPoolAdjustMapper mapper = mock(ForbiddenAbsPoolAdjustMapper.class);
        when(mapper.queryLastInboundLogIdWithCreditReportBySecurity("ABS")).thenReturn(88L);
        // 构造明确的日志及报告库附件定位信息。
        SysAttachmentBo file = reportFile();
        when(mapper.queryFirstCreditReportAttachment(88L)).thenReturn(file);
        when(mapper.queryReportLibraryAttachmentByFileName("one.pdf", "rrs_report_in", "report_in")).thenReturn(file);
        ForbiddenAbsPoolAdjustService service = new ForbiddenAbsPoolAdjustService();
        ReflectionTestUtils.setField(service, "forbiddenAbsPoolAdjustMapper", mapper);
        // 复用报告详情的完整附件返回值。
        ReflectionTestUtils.setField(service, "reportService", reportService());
        ForbiddenAbsPoolAdjustReq req = new ForbiddenAbsPoolAdjustReq();
        req.setSecurityCode("ABS");
        assertThat(service.queryLastCreditReport(req).getAttachments()).extracting(SysAttachmentDto::getId).containsExactly(1L, 2L);
    }

    /** 构造报告定位用的附件，不代表报告只有这一份文件。 */
    private SysAttachmentBo reportFile() {
        SysAttachmentBo file = new SysAttachmentBo();
        file.setId(1L);
        file.setMainId(20L);
        file.setFileName("one.pdf");
        file.setAttachmentCategory("credit_report_in");
        return file;
    }

    /** 构造包含两份 PDF 的完整报告。 */
    private ReportService reportService() {
        ReportService service = mock(ReportService.class);
        ReportDto report = new ReportDto();
        report.setId(20L);
        report.setReportTitle("两份附件的报告");
        SysAttachmentDto first = new SysAttachmentDto();
        first.setId(1L);
        SysAttachmentDto second = new SysAttachmentDto();
        second.setId(2L);
        report.setAttachments(Arrays.asList(first, second));
        when(service.queryReportDetail(any(ReportReq.class))).thenReturn(report);
        return service;
    }
}
