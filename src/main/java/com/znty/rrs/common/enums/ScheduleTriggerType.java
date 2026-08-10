package com.znty.rrs.common.enums;

/**
 * 定时任务触发方式（对应 sys_scheduled_task.last_trigger_type / sys_scheduled_task_run_log.trigger_type）。
 */
public enum ScheduleTriggerType {
    /** 手动触发 */
    MANUAL("manual"),
    /** 定时 cron 触发 */
    CRON("cron");

    /** 枚举 code 值 */
    private final String code;

    ScheduleTriggerType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
