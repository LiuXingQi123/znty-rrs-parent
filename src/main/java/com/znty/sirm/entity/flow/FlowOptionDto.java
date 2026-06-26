package com.znty.sirm.entity.flow;

import lombok.Data;

/**
 * 流程下拉选项 DTO，用于投资池配置中选择关联审批流程
 */
@Data
public class FlowOptionDto {

    /** 流程 ID */
    private Long flowId;

    /** 流程唯一标识（flowKey） */
    private String flowKey;

    /** 流程名称 */
    private String flowName;

    /** 流程描述 */
    private String description;
}
