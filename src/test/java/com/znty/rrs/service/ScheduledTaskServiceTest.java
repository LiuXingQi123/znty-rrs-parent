package com.znty.rrs.service;

import com.znty.rrs.common.PageResult;
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
 * 定时任务服务单元测试。
 */
public class ScheduledTaskServiceTest {

    @Test
    public void queryTaskListShouldReturnDbRows() {
        RrsScheduledTask task = mockTask("a");
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        SysScheduledTaskBo bo = new SysScheduledTaskBo();
        bo.setTaskCode("a");
        bo.setTaskName("任务A");
        bo.setCronExpression("0 0 2 * * ?");
        bo.setScheduleEnabled(1);
        when(mapper.queryTaskList()).thenReturn(Collections.singletonList(bo));
        when(scheduler.isScheduled("a")).thenReturn(true);

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        List<ScheduledTaskInfoDto> list = service.queryTaskList();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).isCodeRegistered()).isTrue();
    }

    @Test
    public void queryTaskPageShouldReturnPagedRows() {
        RrsScheduledTask task = mockTask("a");
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        SysScheduledTaskBo bo = new SysScheduledTaskBo();
        bo.setTaskCode("a");
        bo.setTaskName("任务A");
        bo.setCronExpression("0 0 2 * * ?");
        bo.setScheduleEnabled(1);
        when(mapper.queryTaskPage(any(ScheduledTaskReq.class))).thenReturn(Collections.singletonList(bo));
        when(scheduler.isScheduled("a")).thenReturn(true);

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setKeyword("任务");
        req.setScheduleEnabled(Boolean.TRUE);
        req.setPageIndex(1);
        req.setPageSize(10);
        PageResult<ScheduledTaskInfoDto> page = service.queryTaskPage(req);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).isCodeRegistered()).isTrue();
        assertThat(page.getPageIndex()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(10);
        verify(mapper).queryTaskPage(any(ScheduledTaskReq.class));
    }

    @Test
    public void queryTaskPageShouldAcceptNullReq() {
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        when(mapper.queryTaskPage(any(ScheduledTaskReq.class))).thenReturn(Collections.<SysScheduledTaskBo>emptyList());

        ScheduledTaskService service = new ScheduledTaskService(Collections.<RrsScheduledTask>emptyList());
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        PageResult<ScheduledTaskInfoDto> page = service.queryTaskPage(null);
        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getPageIndex()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
    }

    @Test
    public void addTaskShouldPersist() {
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        when(scheduler.isValidCron(anyString())).thenReturn(true);
        SysScheduledTaskBo saved = new SysScheduledTaskBo();
        saved.setTaskCode("new_task");
        saved.setTaskName("新任务");
        saved.setCronExpression("0 0 1 * * ?");
        saved.setScheduleEnabled(0);
        when(mapper.queryTaskByCode("new_task")).thenReturn(null).thenReturn(saved);
        when(mapper.addTask(any(SysScheduledTaskBo.class))).thenReturn(1);

        ScheduledTaskService service = new ScheduledTaskService(Collections.<RrsScheduledTask>emptyList());
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("new_task");
        req.setTaskName("新任务");
        req.setCronExpression("0 0 1 * * ?");
        req.setOperatorId("1");
        service.addTask(req);

        verify(mapper).addTask(any(SysScheduledTaskBo.class));
        // 无实现不挂载调度
        verify(scheduler, never()).schedule(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    public void addTaskWithImplShouldScheduleWhenEnabled() {
        RrsScheduledTask task = mockTask("job_a");
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        when(scheduler.isValidCron(anyString())).thenReturn(true);
        SysScheduledTaskBo saved = new SysScheduledTaskBo();
        saved.setTaskCode("job_a");
        saved.setTaskName("任务");
        saved.setCronExpression("0 0 2 * * ?");
        saved.setScheduleEnabled(1);
        when(mapper.queryTaskByCode("job_a")).thenReturn(null).thenReturn(saved);
        when(mapper.addTask(any(SysScheduledTaskBo.class))).thenReturn(1);

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("job_a");
        req.setTaskName("任务");
        req.setCronExpression("0 0 2 * * ?");
        req.setScheduleEnabled(true);
        service.addTask(req);

        verify(scheduler).schedule(eq("job_a"), eq("0 0 2 * * ?"), any(Runnable.class));
    }

    @Test
    public void deleteTaskShouldSoftDelete() {
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        SysScheduledTaskBo existing = new SysScheduledTaskBo();
        existing.setTaskCode("a");
        existing.setTaskName("A");
        when(mapper.queryTaskByCode("a")).thenReturn(existing);

        ScheduledTaskService service = new ScheduledTaskService(Collections.<RrsScheduledTask>emptyList());
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("a");
        req.setOperatorId("1");
        service.deleteTask(req);

        verify(scheduler).cancel("a");
        verify(mapper).deleteTaskSoft("a");
    }

    @Test
    public void editTaskShouldUpdate() {
        RrsScheduledTask task = mockTask("a");
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        when(scheduler.isValidCron(anyString())).thenReturn(true);
        SysScheduledTaskBo existing = new SysScheduledTaskBo();
        existing.setTaskCode("a");
        existing.setTaskName("旧名");
        existing.setCronExpression("0 0 2 * * ?");
        when(mapper.queryTaskByCode("a")).thenReturn(existing);
        when(mapper.editTask(any(SysScheduledTaskBo.class))).thenReturn(1);

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("a");
        req.setTaskName("新名");
        req.setCronExpression("0 0 3 * * ?");
        req.setScheduleEnabled(true);
        service.editTask(req);

        verify(mapper).editTask(any(SysScheduledTaskBo.class));
        verify(scheduler).schedule(eq("a"), eq("0 0 3 * * ?"), any(Runnable.class));
    }

    @Test
    public void executeTaskShouldRequireImpl() {
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskCode("a");
        when(mapper.queryTaskByCode("a")).thenReturn(conf);

        ScheduledTaskService service = new ScheduledTaskService(Collections.<RrsScheduledTask>emptyList());
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("a");
        assertThatThrownBy(() -> service.executeTask(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("业务实现");
    }

    @Test
    public void executeTaskShouldRunImpl() {
        RrsScheduledTask task = mockTask("a");
        when(task.execute()).thenReturn(ScheduledTaskResult.success("a", "A", "ok", 1, new Date(), 1L));
        ScheduledTaskMapper mapper = mock(ScheduledTaskMapper.class);
        DynamicTaskScheduler scheduler = mock(DynamicTaskScheduler.class);
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setTaskCode("a");
        conf.setTaskName("A");
        when(mapper.queryTaskByCode("a")).thenReturn(conf);

        ScheduledTaskService service = new ScheduledTaskService(Collections.singletonList(task));
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dynamicTaskScheduler", scheduler);

        ScheduledTaskReq req = new ScheduledTaskReq();
        req.setTaskCode("a");
        ScheduledTaskResult result = service.executeTask(req);
        assertThat(result.isSuccess()).isTrue();
        verify(task, times(1)).execute();
        verify(mapper).addRunLog(any());
    }

    private RrsScheduledTask mockTask(String code) {
        RrsScheduledTask task = mock(RrsScheduledTask.class);
        when(task.getTaskCode()).thenReturn(code);
        return task;
    }
}
