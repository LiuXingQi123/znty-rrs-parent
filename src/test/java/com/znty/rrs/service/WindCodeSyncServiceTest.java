package com.znty.rrs.service;

import com.znty.rrs.common.enums.WindCodeChangeResult;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Wind 扫描任务的汇总、最新记录冲突及异常隔离测试。 */
public class WindCodeSyncServiceTest {
    /** 日期窗口由 SQL 计算，主档缺失、已经是最新代码和重复分别计数。 */
    @Test
    public void shouldSummarizeAllOutcomesAndUseDatabaseDates() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        // 创建任务及四个独立证券事件。
        WindCodeSyncService service = task(mapper, transactions);
        // 构造成功事件。
        WindCodeChangeDto updated = change("1", "ID1", "A", "B");
        // 构造主档缺失事件。
        WindCodeChangeDto missing = change("2", "ID2", "C", "D");
        // 构造重复事件。
        WindCodeChangeDto repeated = change("3", "ID3", "E", "F");
        // 构造主档已经是最新代码的记录。
        WindCodeChangeDto alreadyCurrent = change("4", "ID4", "G", "H");
        when(mapper.queryRecentWindCodeChangeList(1)).thenReturn(Arrays.asList(updated, missing, repeated, alreadyCurrent));
        when(transactions.editWindCodeChangeByJob(updated)).thenReturn(WindCodeChangeResult.UPDATED);
        when(transactions.editWindCodeChangeByJob(missing)).thenReturn(WindCodeChangeResult.MISSING_MASTER);
        when(transactions.editWindCodeChangeByJob(repeated)).thenReturn(WindCodeChangeResult.ALREADY_COMPLETED);
        when(transactions.editWindCodeChangeByJob(alreadyCurrent)).thenReturn(WindCodeChangeResult.ALREADY_CURRENT);
        ScheduledTaskResult result = service.execute();
        verify(mapper).queryRecentWindCodeChangeList(1);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getDetailLog()).contains("扫描条件：", "扫描天数=1", "DATE_FORMAT(TIMESTAMPADD(DAY, -1, NOW())", "特别提示：按 SEC_ID 未找到证券主档", "SEC_ID=ID2", "重复事件跳过");
        assertThat(result.getMessage()).contains("成功 1 条", "主档缺失跳过 1 条", "重复跳过 1 条");
        assertThat(result.getDetailLog()).contains("已是新码跳过", "SEC_ID=ID4", "旧代码=G", "新代码=H", "变更日期=20261021")
                .doesNotContain("[ERROR]");
        assertThat(result.getMessage()).contains("已是新码跳过 1 条", "失败 0 条");
    }

    /** 最新日期存在不同变更时不能仅凭代码关系猜测先后顺序。 */
    @Test
    public void shouldRejectConflictingChangesAtSameLatestDate() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        // 两条记录的日期相同，无法按 CHANGE_DATE 确定哪条更新。
        WindCodeChangeDto second = change("2", "ID", "B", "C");
        WindCodeChangeDto first = change("1", "ID", "A", "B");
        when(mapper.queryRecentWindCodeChangeList(1)).thenReturn(Arrays.asList(second, first));
        // 任务完成扫描，但这只券因源数据冲突不执行换码。
        ScheduledTaskResult result = task(mapper, transactions).execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("失败 1 条");
        assertThat(result.getDetailLog()).contains("最新日期存在不同变更", "SEC_ID=ID");
        verifyNoInteractions(transactions);
    }

    /** 同一最新变更存在多行时，只执行一次，S_INFO_WINDCODE 不作为证券分组键。 */
    @Test
    public void shouldProcessIdenticalLatestChangesOnce() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        // 两行属于同一 SEC_ID、同一日期，最终新代码相同，历史旧码和对象 ID 可以不同。
        WindCodeChangeDto first = change("1", "ID", "A", "C");
        WindCodeChangeDto duplicate = change("2", "ID", "B", "C");
        first.setWindCode("B");
        duplicate.setWindCode("C");
        when(mapper.queryRecentWindCodeChangeList(1)).thenReturn(Arrays.asList(first, duplicate));
        when(transactions.editWindCodeChangeByJob(first)).thenReturn(WindCodeChangeResult.UPDATED);
        // 同一证券的相同变更只进入一次事务。
        ScheduledTaskResult result = task(mapper, transactions).execute();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("成功 1 条", "失败 0 条");
        verify(transactions).editWindCodeChangeByJob(first);
        verifyNoMoreInteractions(transactions);
    }

    /** 单条失败不阻断其他证券，任务正常完成时返回成功并保留失败明细。 */
    @Test
    public void shouldContinueOtherSecuritiesAfterFailure() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        // 构造错误证券。
        WindCodeChangeDto bad = change("1", "BAD", "A", "B");
        // 构造独立的正常证券。
        WindCodeChangeDto good = change("2", "GOOD", "C", "D");
        when(mapper.queryRecentWindCodeChangeList(1)).thenReturn(Arrays.asList(bad, good));
        when(transactions.editWindCodeChangeByJob(bad)).thenThrow(new IllegalStateException("测试数据错误"));
        when(transactions.editWindCodeChangeByJob(good)).thenReturn(WindCodeChangeResult.UPDATED);
        // 执行任务并验证后续证券仍处理。
        ScheduledTaskResult result = task(mapper, transactions).execute();
        verify(transactions).editWindCodeChangeByJob(good);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("成功 1 条", "失败 1 条");
        assertThat(result.getDetailLog()).contains("换码失败：SEC_ID=BAD", "测试数据错误", "任务结束（成功）");
    }

    /** 所有单条转换失败也表示任务执行完成，不能隐藏真实失败条数。 */
    @Test
    public void shouldCompleteSuccessfullyWhenAllIndividualChangesFail() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        // 构造扫描正常、业务转换失败的事件。
        WindCodeChangeDto bad = change("1", "BAD", "A", "B");
        when(mapper.queryRecentWindCodeChangeList(1)).thenReturn(Arrays.asList(bad));
        when(transactions.editWindCodeChangeByJob(bad)).thenThrow(new IllegalStateException("测试转换失败"));
        // 运行完整任务，区分执行状态与业务成功数。
        ScheduledTaskResult result = task(mapper, transactions).execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isZero();
        assertThat(result.getMessage()).contains("成功 0 条", "失败 1 条");
        assertThat(result.getDetailLog()).contains("测试转换失败", "任务结束（成功）");
    }

    /** 扫描异常导致任务未完成时仍返回任务失败。 */
    @Test
    public void shouldFailTaskWhenScanningCannotComplete() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        when(mapper.queryRecentWindCodeChangeList(1))
                .thenThrow(new IllegalStateException("测试扫描不可用"));
        // 扫描无法完成时不能伪报执行成功。
        ScheduledTaskResult result = task(mapper, transactions).execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAffectedCount()).isZero();
        assertThat(result.getDetailLog()).contains("扫描执行异常：测试扫描不可用", "任务结束（失败）");
    }

    /** 页面修改扫描天数后，下次执行重新读取配置，并把新天数传给 SQL。 */
    @Test
    public void shouldUseSavedScanDaysAndReloadThemForEachRun() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        // 构造任务和页面已保存的配置。
        WindCodeSyncService service = task(mapper, transactions);
        ScheduledTaskMapper configs = mock(ScheduledTaskMapper.class);
        SysScheduledTaskBo config = new SysScheduledTaskBo();
        config.setParamJson("{\"scanDays\":3}");
        when(configs.queryTaskByCode(WindCodeSyncService.TASK_CODE)).thenReturn(config);
        when(mapper.queryRecentWindCodeChangeList(3)).thenReturn(Collections.emptyList());
        when(mapper.queryRecentWindCodeChangeList(7)).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", configs);
        ScheduledTaskResult first = service.execute();
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getDetailLog()).contains("扫描天数=3");
        // 模拟页面修改为 7 天，下一轮不沿用上一次读取的值。
        config.setParamJson("{\"scanDays\":7}");
        ScheduledTaskResult second = service.execute();
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.getDetailLog()).contains("扫描天数=7");
        verify(mapper).queryRecentWindCodeChangeList(3);
        verify(mapper).queryRecentWindCodeChangeList(7);
        verify(configs, times(2)).queryTaskByCode(WindCodeSyncService.TASK_CODE);
        verifyNoInteractions(transactions);
    }

    /** 扩展参数留空或使用空对象时，默认扫描 1 天。 */
    @Test
    public void shouldDefaultToOneDayWhenScanDaysIsOmitted() {
        for (String param : Arrays.asList(null, "", "   ", "{}")) {
            WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
            TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
            // 每种留空方式分别装配任务配置。
            WindCodeSyncService service = task(mapper, transactions);
            ScheduledTaskMapper configs = mock(ScheduledTaskMapper.class);
            SysScheduledTaskBo config = new SysScheduledTaskBo();
            config.setParamJson(param);
            when(configs.queryTaskByCode(WindCodeSyncService.TASK_CODE)).thenReturn(config);
            when(mapper.queryRecentWindCodeChangeList(1)).thenReturn(Collections.emptyList());
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", configs);
            ScheduledTaskResult result = service.execute();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDetailLog()).contains("扫描天数=1");
            verify(mapper).queryRecentWindCodeChangeList(1);
        }
    }

    /** 参数格式、字段名和扫描天数不合法时，任务失败且不查询或修改业务数据。 */
    @Test
    public void shouldRejectInvalidScanDaysBeforeScanning() {
        for (String param : Arrays.asList("[]", "null", "\"abc\"", "{invalid}", "{\"scanDays\":3} {}",
                "{\"scanDay\":3}", "{\"scanDays\":null}", "{\"scanDays\":0}", "{\"scanDays\":-1}",
                "{\"scanDays\":1.5}", "{\"scanDays\":\"3\"}", "{\"scanDays\":true}", "{\"scanDays\":2147483648}")) {
            WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
            TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
            // 参数错误必须在进入查询前被发现。
            WindCodeSyncService service = task(mapper, transactions);
            ScheduledTaskMapper configs = mock(ScheduledTaskMapper.class);
            SysScheduledTaskBo config = new SysScheduledTaskBo();
            config.setParamJson(param);
            when(configs.queryTaskByCode(WindCodeSyncService.TASK_CODE)).thenReturn(config);
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", configs);
            ScheduledTaskResult result = service.execute();
            assertThat(result.isSuccess()).as("扩展参数=%s", param).isFalse();
            assertThat(result.getDetailLog()).contains("扫描执行异常：", "任务结束（失败）");
            verifyNoInteractions(mapper, transactions);
        }
    }

    /** 构造任务，所有业务写入均通过独立事务服务执行。 */
    private WindCodeSyncService task(WindCodeSyncMapper mapper, TempSecurityCodeService transactions) {
        WindCodeSyncService service = new WindCodeSyncService();
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mock(ScheduledTaskMapper.class));
        ReflectionTestUtils.setField(service, "windCodeSyncMapper", mapper);
        ReflectionTestUtils.setField(service, "tempSecurityCodeService", transactions);
        return service;
    }

    /** 构造一个明确的代码变更事件。 */
    private WindCodeChangeDto change(String objectId, String secId, String oldCode, String newCode) {
        WindCodeChangeDto row = new WindCodeChangeDto();
        row.setObjectId(objectId);
        row.setSecId(secId);
        row.setOldWindCode(oldCode);
        row.setNewWindCode(newCode);
        row.setChangeDate("20261021");
        return row;
    }
}
