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

    /**
     * 扩展参数原文（库表 param_json）
     * <p>
     * 通用字段：各任务自行约定格式，推荐 JSON；无参数任务可为空。
     * 具体填写方式见 {@link #paramHelp}（由已注册实现提供）。
     * </p>
     */
    private String paramJson;

    /**
     * 扩展参数填写说明（由已注册的 RrsScheduledTask#getParamHelp 提供；未注册实现时为空）
     */
    private String paramHelp;

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
