package com.znty.rrs.common.enums;

/**
 * 定时任务触发方式
 *
 * 对应任务配置的 {@code sys_scheduled_task.last_trigger_type} 和执行历史的 {@code sys_scheduled_task_run_log.trigger_type}
 */
public enum ScheduleTriggerType {

    /** 手动触发 */
    MANUAL("manual"),

    /** 定时 cron 触发 */
    CRON("cron");

    /** 定时任务触发方式编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的定时任务触发方式编码
     */
    ScheduleTriggerType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的定时任务触发方式编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
