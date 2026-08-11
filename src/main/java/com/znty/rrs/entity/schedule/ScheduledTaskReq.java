package com.znty.rrs.entity.schedule;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 定时任务查询/触发/配置请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduledTaskReq extends PageRequest {

    /** 单个任务编码 */
    private String taskCode;

    /** 多个任务编码（批量执行） */
    private List<String> taskCodes;

    /** 任务名称（保存配置，可编辑） */
    private String taskName;

    /** 任务说明（保存配置，可编辑） */
    private String description;

    /** cron 表达式（保存配置） */
    private String cronExpression;

    /** 是否启用定时调度（保存配置） */
    private Boolean scheduleEnabled;

    /** 扩展参数（保存配置；通用文本，推荐 JSON，格式由各任务约定） */
    private String paramJson;

    /** 操作人 ID（写审计/执行日志） */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;
}
