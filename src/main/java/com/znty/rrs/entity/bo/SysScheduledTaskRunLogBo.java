package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 定时任务执行历史实体，对应 sys_scheduled_task_run_log。
 */
@Data
public class SysScheduledTaskRunLogBo {

    /** 主键 ID */
    private Long id;

    /** 任务编码 */
    private String taskCode;

    /** 任务名称快照 */
    private String taskName;

    /** 触发方式：manual / cron */
    private String triggerType;

    /** 执行状态：success / fail */
    private String runStatus;

    /** 执行结果说明 */
    private String message;

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

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
