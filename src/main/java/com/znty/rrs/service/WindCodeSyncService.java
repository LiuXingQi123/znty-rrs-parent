package com.znty.rrs.service;

import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Wind 代码变更同步定时任务（空壳）。
 * <p>
 * 作用：将临时代码同步为正式代码。入口不是「临时代码管理」页的人工更新，
 * 但业务逻辑一致，复用同一套更新方法；部分临时代码可能未经过临时代码管理页面，
 * 而是由 Wind 侧变更后直接在库中把临时代码更新为正式代码，由本任务扫描并落业务数据。
 * 对应公司侧 {@code WindCodeSyncService}：后续将逻辑按本项目写法迁入 {@link #execute()}。
 * 任务编码 {@code wind_code_sync}；Demo 默认每 10 分钟调度、默认关闭；与自动调库无池依赖。
 * </p>
 */
@Slf4j
@Service
public class WindCodeSyncService implements RrsScheduledTask {

    /** 任务编码（与 sys_scheduled_task.task_code 一致） */
    public static final String TASK_CODE = "wind_code_sync";

    /** 任务名称 */
    public static final String TASK_NAME = "Wind代码变更同步";

    private static final String PARAM_HELP =
            "参数说明：本任务暂无扩展参数，请将 param_json 留空\n"
                    + "执行频率：默认每 10 分钟执行一次\n"
                    + "当前状态：任务为空壳，仅用于验证页面立即执行和定时调度挂载\n"
                    + "后续用途：接入后扫描 Wind 代码变更，将临时代码同步为正式代码\n"
                    + "依赖关系：不读写池状态，可与自动调库任务并行执行";

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /**
     * 空壳执行：仅写过程日志并返回成功，便于页面「立即执行」与调度挂载验证。
     * 后续在此复制/改写公司 WindCodeSyncService 逻辑。
     */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        detail.line(TASK_NAME + " 开始（空壳，待接入公司逻辑）");
        // TODO 后续将公司 WindCodeSyncService 业务逻辑按本项目写法迁入此处
        detail.line(TASK_NAME + " 结束：暂无业务处理");
        long duration = System.currentTimeMillis() - begin;
        String message = "空壳执行成功，待接入 Wind 代码变更同步逻辑";
        log.info("{} {}", TASK_NAME, message);
        return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, 0, startTime, duration, detail.build());
    }
}
