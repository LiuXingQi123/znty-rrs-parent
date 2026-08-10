package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.ScheduledTaskInfoDto;
import com.znty.rrs.entity.schedule.ScheduledTaskReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.DynamicTaskScheduler;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务编排服务单元测试。
 */
public class ScheduledTaskServiceTest {

    @Test
    public void queryTaskListShouldMergeCodeAndDb() {
        RrsScheduledTask task = mockTask("a", "任务A");
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        SysScheduledTaskBo bo = new SysScheduledTaskBo();
        bo.setTaskCode("a");
        bo.setCronExpression("0 0 2 * * ?");
        bo.setScheduleEnabled(1);
        when(mapper.queryTaskList()).thenReturn(Collections.singletonList(bo));
        when(scheduler.isScheduled("a")).thenReturn(true);

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        List<ScheduledTaskInfoDto> list = service.queryTaskList();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).isScheduleEnabled()).isTrue();
        assertThat(list.get(0).isCurrentlyScheduled()).isTrue();
        assertThat(list.get(0).isCodeRegistered()).isTrue();
    }

    @Test
    public void editTaskConfigShouldValidateCronAndReschedule() {
        RrsScheduledTask task = mockTask("a", "任务A");
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        when(scheduler.isValidCron(anyString())).thenReturn(true);
        SysScheduledTaskBo existing = new SysScheduledTaskBo();
        existing.setTaskCode("a");
        existing.setCronExpression("0 0 2 * * ?");
        existing.setScheduleEnabled(0);
        when(mapper.queryTaskByCode("a")).thenReturn(existing);
        when(mapper.editTaskConfig(any(SysScheduledTaskBo.class))).thenReturn(1);

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("a");
        req.setTaskName("任务A改名");
        req.setDescription("说明可编辑");
        req.setCronExpression("0 0 3 * * ?");
        req.setScheduleEnabled(true);
        req.setOperatorId("1");

        service.editTaskConfig(req);

        verify(mapper).editTaskConfig(any(SysScheduledTaskBo.class));
        verify(mapper).addTaskEvent(any(SysScheduledTaskBo.class), eq("1"), eq("修改"));
        verify(scheduler).schedule(eq("a"), eq("0 0 3 * * ?"), any(Runnable.class));
    }

    @Test
    public void editTaskConfigShouldRejectInvalidCron() {
        RrsScheduledTask task = mockTask("a", "任务A");
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        when(scheduler.isValidCron(anyString())).thenReturn(false);
        when(mapper.queryTaskByCode("a")).thenReturn(new SysScheduledTaskBo());

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("a");
        req.setCronExpression("bad");
        assertThatThrownBy(() -> service.editTaskConfig(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("cron");
        verify(scheduler, never()).schedule(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    public void executeTaskShouldPersistRunLog() {
        RrsScheduledTask task = mockTask("a", "任务A");
        when(task.execute()).thenReturn(ScheduledTaskResult.success("a", "任务A", "ok", 2, new Date(), 5L));
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("a");
        req.setOperatorId("u1");
        req.setOperatorName("张三");
        ScheduledTaskResult result = service.executeTask(req);
        assertThat(result.getAffectedCount()).isEqualTo(2);
        verify(mapper).editTaskLastRun(any(SysScheduledTaskBo.class));
        verify(mapper).addRunLog(any());
        verify(task, times(1)).execute();
    }

    @Test
    public void constructorShouldRejectDuplicateCodes() {
        RrsScheduledTask task1 = mockTask("same", "A");
        RrsScheduledTask task2 = mockTask("same", "B");
        assertThatThrownBy(() -> new ScheduledTaskService(Arrays.asList(task1, task2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private RrsScheduledTask mockTask(String code, String name) {
        RrsScheduledTask task = mock(RrsScheduledTask.class);
        when(task.getTaskCode()).thenReturn(code);
        when(task.getTaskName()).thenReturn(name);
        when(task.getDescription()).thenReturn(name + "说明");
        when(task.getDefaultCronExpression()).thenReturn("0 0 2 * * ?");
        when(task.isDefaultScheduleEnabled()).thenReturn(false);
        when(task.getDefaultParamJson()).thenReturn(null);
        return task;
    }
}
