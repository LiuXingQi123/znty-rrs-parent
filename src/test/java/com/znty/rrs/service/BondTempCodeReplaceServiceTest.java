package com.znty.rrs.service;

import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.util.Arrays;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 债券临时代码替换任务测试。 */
public class BondTempCodeReplaceServiceTest {

    /** 验证任务逐条调用 job 转正入口并汇总成功数。 */
    @Test
    public void executeShouldConvertAllReadyMappings() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService tempService = mock(TempSecurityCodeService.class);
        BondTempCodeReplaceService service = buildService(mapper, tempService);
        TempSecurityCodeBo first = buildRow(1L, "TMP001", "110001.IB");
        TempSecurityCodeBo second = buildRow(2L, "TMP002", "110002.IB");
        when(mapper.queryJobReadyTempSecurityCodeList()).thenReturn(Arrays.asList(first, second));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(result.getDetailLog()).contains("任务开始：【债券临时代码替换】")
                .contains("扫描条件：rrs_temp_security_code.is_deleted=0、status=temporary、"
                        + "security_code IS NOT NULL 且 TRIM 后非空")
                .contains("扫描完成：待替换候选 2 条")
                .contains("记录ID=1，临时代码=TMP001，正式代码=110001.IB")
                .contains("任务结束（成功）：候选 2 条，成功 2 条，失败 0 条");
        verify(tempService).editTempSecurityCodeToUpdatedByJob(1L);
        verify(tempService).editTempSecurityCodeToUpdatedByJob(2L);
    }

    /** 验证失败项保留给后续重试，并把本轮结果标记为失败。 */
    @Test
    public void executeShouldReportFailedMapping() {
        TempSecurityCodeMapper mapper = mock(TempSecurityCodeMapper.class);
        TempSecurityCodeService tempService = mock(TempSecurityCodeService.class);
        BondTempCodeReplaceService service = buildService(mapper, tempService);
        TempSecurityCodeBo row = buildRow(1L, "TMP001", "110001.IB");
        when(mapper.queryJobReadyTempSecurityCodeList()).thenReturn(Arrays.asList(row));
        doThrow(new IllegalStateException("正式证券不存在"))
                .when(tempService).editTempSecurityCodeToUpdatedByJob(1L);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("失败 1 条");
        assertThat(result.getDetailLog()).contains("替换失败：记录ID=1")
                .contains("任务结束（失败）：候选 1 条，成功 0 条，失败 1 条");
    }

    /** 构建测试服务。 */
    private BondTempCodeReplaceService buildService(TempSecurityCodeMapper mapper,
                                                     TempSecurityCodeService tempService) {
        BondTempCodeReplaceService service = new BondTempCodeReplaceService();
        ReflectionTestUtils.setField(service, "tempSecurityCodeMapper", mapper);
        ReflectionTestUtils.setField(service, "tempSecurityCodeService", tempService);
        return service;
    }

    /** 构建待替换记录。 */
    private TempSecurityCodeBo buildRow(Long id, String tempCode, String formalCode) {
        TempSecurityCodeBo row = new TempSecurityCodeBo();
        row.setId(id);
        row.setTempSecurityCode(tempCode);
        row.setSecurityCode(formalCode);
        return row;
    }
}
