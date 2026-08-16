package com.znty.rrs.entity.scripttool;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 脚本工具写操作审计记录。
 */
@Data
public class ScriptToolRunLogDto {

    /** 主键 ID */
    private Long id;

    /** 动作类型：task / clear / reset / module / scene */
    private String actionType;

    /** 动作编码 */
    private String actionCode;

    /** 动作名称 */
    private String actionName;

    /** 执行状态：success / failed */
    private String runStatus;

    /** 失败原因摘要 */
    private String errorMessage;

    /** 已执行项摘要 */
    private String executedSummary;

    /** 已执行项数量 */
    private Integer executedCount;

    /** 耗时毫秒 */
    private Long costMillis;

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

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
}
