package com.znty.rrs.entity.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 定时任务管理页列表/详情 DTO（代码注册信息 + 库表配置 + 最近执行）。
 *
 * <p>状态类字段仅返回 code，中文由前端字典映射。
 */
@Data
public class ScheduledTaskInfoDto {

    /** 库表主键 */
    private Long id;

    /** 任务编码 */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 任务说明 */
    private String description;

    /** cron 表达式（库表配置，可页面修改） */
    private String cronExpression;

    /** 是否启用定时调度 */
    private boolean scheduleEnabled;

    /** 扩展参数（按任务自定义，如主体下新债的池映射 15-15） */
    private String paramJson;

    /** 代码是否已注册实现（false 表示库中有配置但代码未部署） */
    private boolean codeRegistered;

    /** 当前是否已挂载动态调度 */
    private boolean currentlyScheduled;

    /** 最近执行状态 code（ScheduleRunStatus） */
    private String lastRunStatus;

    /** 最近执行结果说明 */
    private String lastRunMessage;

    /** 最近执行开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastRunTime;

    /** 最近执行影响条数 */
    private Integer lastAffectedCount;

    /** 最近执行耗时毫秒 */
    private Long lastDurationMs;

    /** 最近触发方式 code（ScheduleTriggerType） */
    private String lastTriggerType;
}
