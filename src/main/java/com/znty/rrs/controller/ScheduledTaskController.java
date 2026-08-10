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
 * 定时任务管理接口。
 *
 * <p>提供任务清单、配置保存、手动执行与执行历史查询，路径前缀 {@code /api/v1/scheduledTask}。
 */
@RestController
@RequestMapping("/api/v1/scheduledTask")
public class ScheduledTaskController {

    /** 定时任务编排服务 */
    @Resource
    private ScheduledTaskService scheduledTaskService;

    /**
     * 查询全部任务（含库表配置与最近执行）。
     *
     * @param req 请求体（可空）
     * @return 任务列表
     */
    @PostMapping("/queryTaskList")
    public ApiResponse<List<ScheduledTaskInfoDto>> queryTaskList(@RequestBody(required = false) ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.queryTaskList());
    }

    /**
     * 按编码查询单个任务。
     *
     * @param req 含 taskCode
     * @return 任务信息
     */
    @PostMapping("/queryTask")
    public ApiResponse<ScheduledTaskInfoDto> queryTask(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.queryTask(req == null ? null : req.getTaskCode()));
    }

    /**
     * 保存任务配置（cron / 启停 / 扩展参数）并即时重挂载。
     *
     * @param req 配置请求
     * @return 更新后的任务信息
     */
    @PostMapping("/editTaskConfig")
    public ApiResponse<ScheduledTaskInfoDto> editTaskConfig(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.editTaskConfig(req));
    }

    /**
     * 手动执行单个任务。
     *
     * @param req 含 taskCode、操作人
     * @return 执行结果
     */
    @PostMapping("/executeTask")
    public ApiResponse<ScheduledTaskResult> executeTask(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.executeTask(req));
    }

    /**
     * 按序手动执行多个任务。
     *
     * @param req 含 taskCodes
     * @return 各任务执行结果
     */
    @PostMapping("/executeTasks")
    public ApiResponse<List<ScheduledTaskResult>> executeTasks(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.executeTasks(req));
    }

    /**
     * 分页查询执行历史。
     *
     * @param req 含 taskCode、分页参数
     * @return 分页历史
     */
    @PostMapping("/queryRunLogPage")
    public ApiResponse<PageResult<ScheduledTaskRunLogDto>> queryRunLogPage(@RequestBody ScheduledTaskReq req) {
        return ApiResponse.success(scheduledTaskService.queryRunLogPage(req));
    }
}
