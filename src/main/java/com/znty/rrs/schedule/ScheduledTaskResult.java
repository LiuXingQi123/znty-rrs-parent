package com.znty.rrs.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 记录定时任务单次执行的状态、说明、影响条数和时间
 *
 * 结果说明供执行历史列表展示，由 {@link TaskDetailLog} 汇总的过程日志供历史详情查看
 */
@Data
public class ScheduledTaskResult {

    /** 任务编码 */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 本次执行是否成功 */
    private boolean success;

    /** 执行历史列表展示的结果说明 */
    private String message;

    /** 写入执行历史 detail_log 的多行过程日志，无日志时为 null */
    private String detailLog;

    /** 本次执行影响的业务记录条数，例如入池或出池条数，失败时为 0 */
    private int affectedCount;

    /** 本次执行耗时，单位为毫秒 */
    private long durationMs;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    /**
     * 创建可附带过程日志的成功结果
     *
     * 执行顺序：
     * 1. 标记执行成功，保存任务信息、结果说明、影响条数和过程日志
     * 2. 保存开始时间和耗时，并据此算出结束时间
     *
     * @param taskCode 任务编码
     * @param taskName 任务名称
     * @param message 本次执行的结果说明
     * @param affectedCount 本次执行影响的业务记录条数
     * @param startTime 本次执行的开始时间
     * @param durationMs 本次执行耗时，单位为毫秒
     * @param detailLog 多行过程日志，无日志时传 null
     * @return 本次执行的成功结果
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
        // 用开始时间和耗时算出结束时间
        result.setStartTime(startTime);
        result.setDurationMs(durationMs);
        result.setEndTime(new Date(startTime.getTime() + durationMs));
        return result;
    }

    /**
     * 创建无过程日志的成功结果
     *
     * 执行顺序：
     * 1. 将过程日志置为 null，沿用成功结果的构造步骤
     *
     * @param taskCode 任务编码
     * @param taskName 任务名称
     * @param message 本次执行的结果说明
     * @param affectedCount 本次执行影响的业务记录条数
     * @param startTime 本次执行的开始时间
     * @param durationMs 本次执行耗时，单位为毫秒
     * @return 过程日志为 null 的成功结果
     */
    public static ScheduledTaskResult success(String taskCode, String taskName, String message,
                                             int affectedCount, Date startTime, long durationMs) {
        return success(taskCode, taskName, message, affectedCount, startTime, durationMs, null);
    }

    /**
     * 创建可附带过程日志的失败结果
     *
     * 执行顺序：
     * 1. 标记执行失败，保存任务信息、失败说明和过程日志，并将影响条数记为 0
     * 2. 保存开始时间和耗时，并据此算出结束时间
     *
     * @param taskCode 任务编码
     * @param taskName 任务名称
     * @param message 本次执行的失败说明
     * @param startTime 本次执行的开始时间
     * @param durationMs 本次执行耗时，单位为毫秒
     * @param detailLog 多行过程日志，无日志时传 null
     * @return 影响条数为 0 的失败结果
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
        // 用开始时间和耗时算出结束时间
        result.setStartTime(startTime);
        result.setDurationMs(durationMs);
        result.setEndTime(new Date(startTime.getTime() + durationMs));
        return result;
    }

    /**
     * 创建无过程日志的失败结果
     *
     * 执行顺序：
     * 1. 将过程日志置为 null，沿用失败结果的构造步骤
     *
     * @param taskCode 任务编码
     * @param taskName 任务名称
     * @param message 本次执行的失败说明
     * @param startTime 本次执行的开始时间
     * @param durationMs 本次执行耗时，单位为毫秒
     * @return 过程日志为 null 的失败结果
     */
    public static ScheduledTaskResult failure(String taskCode, String taskName, String message,
                                             Date startTime, long durationMs) {
        return failure(taskCode, taskName, message, startTime, durationMs, null);
    }
}
