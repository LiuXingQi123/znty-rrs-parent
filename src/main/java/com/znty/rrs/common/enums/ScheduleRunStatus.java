package com.znty.rrs.common.enums;

/**
 * 定时任务执行状态
 *
 * 对应任务配置的 {@code sys_scheduled_task.last_run_status} 和执行历史的 {@code sys_scheduled_task_run_log.run_status}
 */
public enum ScheduleRunStatus {

    /** 成功 */
    SUCCESS("success"),

    /** 失败 */
    FAIL("fail");

    /** 定时任务执行状态编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的定时任务执行状态编码
     */
    ScheduleRunStatus(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的定时任务执行状态编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
