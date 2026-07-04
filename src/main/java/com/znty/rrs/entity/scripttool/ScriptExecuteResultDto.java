package com.znty.rrs.entity.scripttool;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 脚本任务执行结果。
 */
@Data
public class ScriptExecuteResultDto {

    /** 脚本任务编码 */
    private String taskCode;

    /** 脚本任务名称 */
    private String taskName;

    /** 执行状态：success=成功 / failed=失败 */
    private String status;

    /** 操作人 ID */
    private String currentUserId;

    /** 操作人名称 */
    private String currentUserName;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /** 执行耗时，单位毫秒 */
    private Long costMillis;

    /** 已执行脚本或清空表清单 */
    private List<String> executedItems;

    /** 错误信息 */
    private String errorMessage;
}
