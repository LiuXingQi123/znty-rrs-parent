package com.znty.rrs.entity.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 定时任务执行历史 DTO。
 */
@Data
public class ScheduledTaskRunLogDto {

    /** 主键 */
    private Long id;

    /** 任务编码 */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 触发方式 code（ScheduleTriggerType） */
    private String triggerType;

    /** 执行状态 code（ScheduleRunStatus） */
    private String runStatus;

    /** 结果说明 */
    private String message;

    /** 执行过程日志（多行文本，历史页可查看） */
    private String detailLog;

    /** 影响条数 */
    private Integer affectedCount;

    /** 耗时毫秒 */
    private Long durationMs;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    /** 操作人 ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;
}
