package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.schedule.ScheduledTaskInfoDto;
import com.znty.rrs.entity.schedule.ScheduledTaskReq;
import com.znty.rrs.entity.schedule.ScheduledTaskRunLogDto;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.service.ScheduledTaskService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 定时任务管理控制器
 * <p>
 * 提供任务配置的查询、新增、修改、删除，以及手动触发执行与执行历史分页查询。
 * 路径前缀 {@code /api/v1/scheduledTask}。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/scheduledTask")
public class ScheduledTaskController {

    /** 定时任务服务 */
    @Resource
    private ScheduledTaskService scheduledTaskService;

    /**
     * 查询全部有效定时任务配置列表，含最近执行摘要与是否已注册业务实现/是否已挂载调度
     */
    @PostMapping("/queryTaskList")
    public ApiResponse<List<ScheduledTaskInfoDto>> queryTaskList(@RequestBody(required = false) ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.queryTaskList());
    }

    /**
     * 按任务编码查询单条定时任务配置详情
     */
    @PostMapping("/queryTask")
    public ApiResponse<ScheduledTaskInfoDto> queryTask(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.queryTask(req == null ? null : req.getTaskCode()));
    }

    /**
     * 新增定时任务配置（编码、名称、说明、cron、启停、扩展参数），有业务实现时同步挂载调度
     */
    @PostMapping("/addTask")
    public ApiResponse<ScheduledTaskInfoDto> addTask(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.addTask(req));
    }

    /**
     * 修改定时任务配置（名称、说明、cron、启停、扩展参数），保存后按最新配置重挂或取消调度
     */
    @PostMapping("/editTask")
    public ApiResponse<ScheduledTaskInfoDto> editTask(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.editTask(req));
    }

    /**
     * 逻辑删除定时任务配置，并取消已挂载的调度
     */
    @PostMapping("/deleteTask")
    public ApiResponse<ScheduledTaskInfoDto> deleteTask(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.deleteTask(req));
    }

    /**
     * 手动触发执行指定定时任务，写入最近执行摘要与执行历史
     */
    @PostMapping("/executeTask")
    public ApiResponse<ScheduledTaskResult> executeTask(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.executeTask(req));
    }

    /**
     * 分页查询定时任务执行历史，可按任务编码筛选
     */
    @PostMapping("/queryRunLogPage")
    public ApiResponse<PageResult<ScheduledTaskRunLogDto>> queryRunLogPage(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.queryRunLogPage(req));
    }
}
