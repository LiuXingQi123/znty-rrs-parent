package com.znty.rrs.service;

import com.znty.rrs.common.enums.WindCodeChangeResult;
import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Wind 扫描任务的汇总、变更链及异常隔离测试。 */
public class WindCodeSyncServiceTest {
    /** 查询窗口采用上海日期，主档缺失、旧码缺失和重复分别计数并输出特别提示。 */
    @Test
    public void shouldSummarizeAllOutcomesAndUseShanghaiDates() {
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
        // 构造有 SEC_ID 主档但旧代码不存在的事件。
        WindCodeChangeDto missingOld = change("4", "ID4", "G", "H");
        when(mapper.queryRecentWindCodeChangeList(anyString(), anyString())).thenReturn(Arrays.asList(updated, missing, repeated, missingOld));
        when(transactions.editWindCodeChangeByJob(updated)).thenReturn(WindCodeChangeResult.UPDATED);
        when(transactions.editWindCodeChangeByJob(missing)).thenReturn(WindCodeChangeResult.MISSING_MASTER);
        when(transactions.editWindCodeChangeByJob(repeated)).thenReturn(WindCodeChangeResult.ALREADY_COMPLETED);
        when(transactions.editWindCodeChangeByJob(missingOld)).thenReturn(WindCodeChangeResult.MISSING_OLD_CODE);
        ScheduledTaskResult result = service.execute();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        verify(mapper).queryRecentWindCodeChangeList(today.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE),
                today.format(DateTimeFormatter.BASIC_ISO_DATE));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getDetailLog()).contains("扫描条件：", "特别提示：按 SEC_ID 未找到证券主档", "SEC_ID=ID2", "重复事件跳过");
        assertThat(result.getMessage()).contains("成功 1 条", "主档缺失跳过 1 条", "重复跳过 1 条");
        assertThat(result.getDetailLog()).contains("特别提示：旧代码不在 rrs_securityinfo 中", "SEC_ID=ID4", "旧代码=G", "新代码=H", "变更日期=20261021")
                .doesNotContain("[ERROR]");
        assertThat(result.getMessage()).contains("旧码缺失跳过 1 条", "失败 0 条");
    }

    /** 同日乱序的连续变更按 A→B→C 执行。 */
    @Test
    public void shouldOrderChangesByTheirCodeChain() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        // 后一条故意先从源查询返回。
        WindCodeChangeDto second = change("2", "ID", "B", "C");
        // 前一条必须先执行。
        WindCodeChangeDto first = change("1", "ID", "A", "B");
        when(mapper.queryRecentWindCodeChangeList(anyString(), anyString())).thenReturn(Arrays.asList(second, first));
        when(transactions.editWindCodeChangeByJob(first)).thenReturn(WindCodeChangeResult.UPDATED);
        when(transactions.editWindCodeChangeByJob(second)).thenReturn(WindCodeChangeResult.UPDATED);
        // 构造任务并运行实际排序逻辑。
        assertThat(task(mapper, transactions).execute().isSuccess()).isTrue();
        InOrder order = inOrder(transactions);
        order.verify(transactions).editWindCodeChangeByJob(first);
        order.verify(transactions).editWindCodeChangeByJob(second);
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
        when(mapper.queryRecentWindCodeChangeList(anyString(), anyString())).thenReturn(Arrays.asList(bad, good));
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
        when(mapper.queryRecentWindCodeChangeList(anyString(), anyString())).thenReturn(Arrays.asList(bad));
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
        when(mapper.queryRecentWindCodeChangeList(anyString(), anyString()))
                .thenThrow(new IllegalStateException("测试扫描不可用"));
        // 扫描无法完成时不能伪报执行成功。
        ScheduledTaskResult result = task(mapper, transactions).execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAffectedCount()).isZero();
        assertThat(result.getDetailLog()).contains("扫描执行异常：测试扫描不可用", "任务结束（失败）");
    }

    /** 构造任务，所有业务写入均通过独立事务服务执行。 */
    private WindCodeSyncService task(WindCodeSyncMapper mapper, TempSecurityCodeService transactions) {
        WindCodeSyncService service = new WindCodeSyncService();
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
