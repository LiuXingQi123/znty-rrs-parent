package com.znty.rrs.common.enums;

/**
 * 定时任务执行状态（对应 sys_scheduled_task.last_run_status / sys_scheduled_task_run_log.run_status）。
 */
public enum ScheduleRunStatus {
    /** 成功 */
    SUCCESS("success"),
    /** 失败 */
    FAIL("fail");

    /** 枚举 code 值 */
    private final String code;

    ScheduleRunStatus(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
