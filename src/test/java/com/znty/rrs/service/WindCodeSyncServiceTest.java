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
    /** 查询窗口采用上海日期，主档缺失和重复分别计数并输出特别提示。 */
    @Test
    public void shouldSummarizeAllOutcomesAndUseShanghaiDates() {
        WindCodeSyncMapper mapper = mock(WindCodeSyncMapper.class);
        TempSecurityCodeService transactions = mock(TempSecurityCodeService.class);
        // 创建任务及三个独立证券事件。
        WindCodeSyncService service = task(mapper, transactions);
        // 构造成功事件。
        WindCodeChangeDto updated = change("1", "ID1", "A", "B");
        // 构造主档缺失事件。
        WindCodeChangeDto missing = change("2", "ID2", "C", "D");
        // 构造重复事件。
        WindCodeChangeDto repeated = change("3", "ID3", "E", "F");
        when(mapper.queryRecentWindCodeChangeList(anyString(), anyString())).thenReturn(Arrays.asList(updated, missing, repeated));
        when(transactions.editWindCodeChangeByJob(updated)).thenReturn(WindCodeChangeResult.UPDATED);
        when(transactions.editWindCodeChangeByJob(missing)).thenReturn(WindCodeChangeResult.MISSING_MASTER);
        when(transactions.editWindCodeChangeByJob(repeated)).thenReturn(WindCodeChangeResult.ALREADY_COMPLETED);
        ScheduledTaskResult result = service.execute();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        verify(mapper).queryRecentWindCodeChangeList(today.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE),
                today.format(DateTimeFormatter.BASIC_ISO_DATE));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getDetailLog()).contains("扫描条件：", "特别提示：按 SEC_ID 未找到证券主档", "SEC_ID=ID2", "重复事件跳过");
        assertThat(result.getMessage()).contains("成功 1 条", "主档缺失跳过 1 条", "重复跳过 1 条");
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

    /** 一条证券失败不阻断另一条证券，同时使任务汇总为失败。 */
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
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("成功 1 条", "失败 1 条");
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
