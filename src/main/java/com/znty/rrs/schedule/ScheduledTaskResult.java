package com.znty.rrs.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 定时任务单次执行结果（成功/失败、说明、过程日志、影响条数、起止时间与耗时）
 */
@Data
public class ScheduledTaskResult {

    /** 任务编码 */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 是否成功 */
    private boolean success;

    /** 结果说明（摘要，列表展示） */
    private String message;

    /**
     * 执行过程日志（多行文本，写入历史 detail_log，供页面查看）
     */
    private String detailLog;

    /** 影响条数（入池/出池等业务计数，无则 0） */
    private int affectedCount;

    /** 耗时毫秒 */
    private long durationMs;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    /**
     * 构造成功执行结果（含影响条数、开始时间、耗时与过程日志）
     */
    public static ScheduledTaskResult success(String taskCode, String taskName, String message,
                                             int affectedCount, Date startTime, long durationMs,
                                             String detailLog) {
        ScheduledTaskResult result = new ScheduledTaskResult();
        result.setTaskCode(taskCode);
        result.setTaskName(taskName);
        result.setSuccess(true);
        result.setMessage(message);
        result.setDetailLog(detailLog);
        result.setAffectedCount(affectedCount);
        result.setStartTime(startTime);
        result.setDurationMs(durationMs);
        result.setEndTime(new Date(startTime.getTime() + durationMs));
        return result;
    }

    /**
     * 构造成功执行结果（无过程日志）
     */
    public static ScheduledTaskResult success(String taskCode, String taskName, String message,
                                             int affectedCount, Date startTime, long durationMs) {
        return success(taskCode, taskName, message, affectedCount, startTime, durationMs, null);
    }

    /**
     * 构造失败执行结果（含过程日志）
     */
    public static ScheduledTaskResult failure(String taskCode, String taskName, String message,
                                             Date startTime, long durationMs, String detailLog) {
        ScheduledTaskResult result = new ScheduledTaskResult();
        result.setTaskCode(taskCode);
        result.setTaskName(taskName);
        result.setSuccess(false);
        result.setMessage(message);
        result.setDetailLog(detailLog);
        result.setAffectedCount(0);
        result.setStartTime(startTime);
        result.setDurationMs(durationMs);
        result.setEndTime(new Date(startTime.getTime() + durationMs));
        return result;
    }

    /**
     * 构造失败执行结果（无过程日志）
     */
    public static ScheduledTaskResult failure(String taskCode, String taskName, String message,
                                             Date startTime, long durationMs) {
        return failure(taskCode, taskName, message, startTime, durationMs, null);
    }
}
