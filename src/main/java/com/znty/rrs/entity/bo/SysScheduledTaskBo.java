package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 定时任务配置实体，对应 sys_scheduled_task。
 */
@Data
public class SysScheduledTaskBo {

    /** 主键 ID */
    private Long id;

    /** 任务编码 */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 任务说明 */
    private String description;

    /** cron 表达式 */
    private String cronExpression;

    /** 是否启用定时调度：0=关闭 / 1=启用 */
    private Integer scheduleEnabled;

    /** 任务扩展参数（按任务自定义） */
    private String paramJson;

    /** 最近执行状态：success / fail */
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

    /** 最近触发方式：manual / cron */
    private String lastTriggerType;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
