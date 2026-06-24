package com.znty.sirm.service;

import com.znty.sirm.mapper.ReportMapper;
import com.znty.sirm.model.ReportReq;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

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
}
