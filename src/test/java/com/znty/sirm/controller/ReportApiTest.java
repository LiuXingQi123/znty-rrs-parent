package com.znty.sirm.controller;

import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.ReportDto;
import com.znty.sirm.model.ReportReq;
import com.znty.sirm.model.SysAttachmentDto;
import com.znty.sirm.service.ReportService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 报告库查询接口测试
 */
public class ReportApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端 */
    private MockMvc mockMvc;

    /** 报告库查询服务 */
    private ReportService reportService;

    /** 初始化测试环境 */
    @Before
    public void setUp() {
        reportService = mock(ReportService.class);
        when(reportService.queryInReportPage(any(ReportReq.class)))
                .thenReturn(new PageResult<ReportDto>(Collections.<ReportDto>emptyList(), 0, 1, 20));
        when(reportService.queryOutReportPage(any(ReportReq.class)))
                .thenReturn(new PageResult<ReportDto>(Collections.<ReportDto>emptyList(), 0, 1, 20));
        ReportController controller = new ReportController();
        ReflectionTestUtils.setField(controller, "reportService", reportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证内部报告分页查询接口可用 */
    @Test
    public void shouldSupportInternalReportQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/reports/queryInReportPage", "{}");
    }

    /** 验证外部报告分页查询接口可用 */
    @Test
    public void shouldSupportExternalReportQuery() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/reports/queryOutReportPage", "{}");
    }

    /** 验证筛选条件可以传递到服务层 */
    @Test
    public void shouldPassReportFiltersToService() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/reports/queryInReportPage",
                "{\"reportTitle\":\"入库\",\"securityCode\":\"1020\",\"reportType\":\"bond_in_report\",\"crteTimeStart\":\"2026-05-01\",\"crteTimeEnd\":\"2026-06-01\"}");

        ArgumentCaptor<ReportReq> captor = ArgumentCaptor.forClass(ReportReq.class);
        verify(reportService).queryInReportPage(captor.capture());
        ReportReq req = captor.getValue();
        assertThat(req.getReportTitle()).isEqualTo("入库");
        assertThat(req.getSecurityCode()).isEqualTo("1020");
        assertThat(req.getReportType()).isEqualTo("bond_in_report");
        assertThat(req.getCrteTimeStart()).isEqualTo("2026-05-01");
        assertThat(req.getCrteTimeEnd()).isEqualTo("2026-06-01");
    }

    /** 验证外部报告筛选条件可以传递到服务层 */
    @Test
    public void shouldPassOutReportFiltersToService() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/reports/queryOutReportPage",
                "{\"reportTitle\":\"出库\",\"securityCode\":\"601318\",\"reportType\":\"stock_out_report\",\"crteTimeStart\":\"2026-06-01\",\"crteTimeEnd\":\"2026-06-30\"}");

        ArgumentCaptor<ReportReq> captor = ArgumentCaptor.forClass(ReportReq.class);
        verify(reportService).queryOutReportPage(captor.capture());
        ReportReq req = captor.getValue();
        assertThat(req.getReportTitle()).isEqualTo("出库");
        assertThat(req.getSecurityCode()).isEqualTo("601318");
        assertThat(req.getReportType()).isEqualTo("stock_out_report");
        assertThat(req.getCrteTimeStart()).isEqualTo("2026-06-01");
        assertThat(req.getCrteTimeEnd()).isEqualTo("2026-06-30");
    }

    /** 验证报告分页接口返回附件列表 */
    @Test
    public void shouldReturnReportAttachments() throws Exception {
        ReportDto report = new ReportDto();
        report.setId(1L);
        SysAttachmentDto attachment = new SysAttachmentDto();
        attachment.setId(10L);
        attachment.setMainId(1L);
        attachment.setOriginalFileName("报告附件.pdf");
        report.setAttachments(Arrays.asList(attachment));
        when(reportService.queryInReportPage(any(ReportReq.class)))
                .thenReturn(new PageResult<ReportDto>(Arrays.asList(report), 1, 1, 20));

        postJson(mockMvc, "/api/v1/reports/queryInReportPage", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].attachments[0].mainId").value(1))
                .andExpect(jsonPath("$.data.records[0].attachments[0].originalFileName").value("报告附件.pdf"));
    }
}
