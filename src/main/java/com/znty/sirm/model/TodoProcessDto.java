package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 待办处理列表返回对象。
 */
@Data
public class TodoProcessDto {

    /** 待办步骤 ID，作为列表主键 */
    private Long id;

    /** 调库记录 ID */
    private Long adjustLogId;

    /** 审批步骤 ID */
    private Long stepId;

    /** 流程名称 */
    private String flowName;

    /** 步骤名称 */
    private String stepName;

    /** 流程描述 */
    private String processDescription;

    /** 发起人 */
    private String initiatorName;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
}
