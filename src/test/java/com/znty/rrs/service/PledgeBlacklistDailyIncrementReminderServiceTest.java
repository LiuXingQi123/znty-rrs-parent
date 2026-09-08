package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.BondReminderDto;
import com.znty.rrs.mapper.BondReminderMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.util.Collections;
import java.util.Date;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 质押黑名单库每日增量提醒测试。 */
public class PledgeBlacklistDailyIncrementReminderServiceTest {

    /** 验证按上次成功水位扫描，并明确记录通知未接入。 */
    @Test
    public void executeShouldScanIncrementAndKeepDeliveryPending() {
        BondReminderMapper reminderMapper = mock(BondReminderMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        PledgeBlacklistDailyIncrementReminderService service =
                new PledgeBlacklistDailyIncrementReminderService();
        ReflectionTestUtils.setField(service, "bondReminderMapper", reminderMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "reminderDeliveryService", new ScheduledReminderDeliveryService());
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setParamJson("{\"poolIds\":[17]}");
        Date previous = new Date(System.currentTimeMillis() - 60000L);
        BondReminderDto row = new BondReminderDto();
        row.setSecurityCode("SEC001");
        row.setSecurityShortName("测试债");
        when(taskMapper.queryTaskByCode(PledgeBlacklistDailyIncrementReminderService.TASK_CODE))
                .thenReturn(conf);
        when(taskMapper.queryLastSuccessStartTime(PledgeBlacklistDailyIncrementReminderService.TASK_CODE))
                .thenReturn(previous);
        when(reminderMapper.queryPledgeBlacklistIncrementList(
                eq(Collections.singletonList(17L)), eq(previous), any(Date.class)))
                .thenReturn(Collections.singletonList(row));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("通知通道未接入");
        assertThat(result.getDetailLog()).contains("任务开始：【质押黑名单库每日增量提醒】")
                .contains("扫描条件：目标池=[17]")
                .contains("扫描完成：增量提醒候选 1 条")
                .contains("消息投递状态=待接入")
                .contains("任务结束（成功）：候选 1 条，通知状态=待接入");
        verify(reminderMapper).queryPledgeBlacklistIncrementList(
                eq(Collections.singletonList(17L)), eq(previous), any(Date.class));
    }
}
