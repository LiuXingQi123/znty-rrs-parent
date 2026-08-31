package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.hspoolexport.HsPoolManualExportReq;
import com.znty.rrs.exception.BizException;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 恒生格式手动导出服务单元测试。 */
public class HsPoolManualExportServiceTest {

    /** 验证增量模式按页面指定池范围和时间窗口执行。 */
    @Test
    public void incrementExportShouldUseSpecifiedPoolsAndTimeWindow() throws Exception {
        HsPoolFullExcelExportService fullService = mock(HsPoolFullExcelExportService.class);
        HsPoolIncrementExcelExportService incrementService = mock(HsPoolIncrementExcelExportService.class);
        HsPoolManualExportService service = createService(fullService, incrementService);
        HsPoolManualExportReq req = request("increment", "2026-08-01 09:00:00", "2026-08-02 18:30:00");
        req.setPoolIds(Arrays.asList(15L, 16L));
        Date startTime = parse("2026-08-01 09:00:00");
        Date endTime = parse("2026-08-02 18:30:00");
        CommonFileDto expected = new CommonFileDto();
        expected.setFileName("hs_pool_increment_20260802_183000.xlsx");
        when(incrementService.exportManual(req.getPoolIds(), startTime, endTime)).thenReturn(expected);

        CommonFileDto result = service.exportHsPoolExcel(req);

        assertThat(result).isSameAs(expected);
        verify(incrementService).exportManual(req.getPoolIds(), startTime, endTime);
        verify(fullService, never()).exportManual(req.getPoolIds(), startTime, endTime);
    }

    /** 验证全量模式调用全量实现，未选择投资池时传递空池范围。 */
    @Test
    public void fullExportShouldDelegateToFullService() throws Exception {
        HsPoolFullExcelExportService fullService = mock(HsPoolFullExcelExportService.class);
        HsPoolIncrementExcelExportService incrementService = mock(HsPoolIncrementExcelExportService.class);
        HsPoolManualExportService service = createService(fullService, incrementService);
        HsPoolManualExportReq req = request("full", "2026-08-01 09:00:00", "2026-08-02 18:30:00");
        Date startTime = parse("2026-08-01 09:00:00");
        Date endTime = parse("2026-08-02 18:30:00");
        CommonFileDto expected = new CommonFileDto();
        when(fullService.exportManual(null, startTime, endTime)).thenReturn(expected);

        CommonFileDto result = service.exportHsPoolExcel(req);

        assertThat(result).isSameAs(expected);
        verify(fullService).exportManual(null, startTime, endTime);
        verify(incrementService, never()).exportManual(null, startTime, endTime);
    }

    /** 验证缺少必填项、时间格式错误和反向时间窗口均被拒绝。 */
    @Test
    public void invalidRequestShouldBeRejected() {
        HsPoolManualExportService service = createService(
                mock(HsPoolFullExcelExportService.class), mock(HsPoolIncrementExcelExportService.class));

        assertThatThrownBy(() -> service.exportHsPoolExcel(request("full", null, null)))
                .isInstanceOf(BizException.class).hasMessage("开始时间不能为空");
        assertThatThrownBy(() -> service.exportHsPoolExcel(request(null, "2026-08-01 09:00:00", null)))
                .isInstanceOf(BizException.class).hasMessage("导出模式必须选择增量或全量");
        assertThatThrownBy(() -> service.exportHsPoolExcel(request("full", "2026/08/01", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("开始时间格式错误");
        assertThatThrownBy(() -> service.exportHsPoolExcel(
                request("increment", "2026-08-02 09:00:00", "2026-08-01 09:00:00")))
                .isInstanceOf(BizException.class).hasMessage("结束时间不能早于开始时间");
    }

    /** 创建并注入被测服务。 */
    private HsPoolManualExportService createService(HsPoolFullExcelExportService fullService,
                                                     HsPoolIncrementExcelExportService incrementService) {
        HsPoolManualExportService service = new HsPoolManualExportService();
        ReflectionTestUtils.setField(service, "fullExportService", fullService);
        ReflectionTestUtils.setField(service, "incrementExportService", incrementService);
        return service;
    }

    /** 创建页面请求。 */
    private HsPoolManualExportReq request(String mode, String startTime, String endTime) {
        HsPoolManualExportReq req = new HsPoolManualExportReq();
        req.setExportMode(mode);
        req.setStartTime(startTime);
        req.setEndTime(endTime);
        return req;
    }

    /** 解析测试日期时间。 */
    private Date parse(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
    }
}
