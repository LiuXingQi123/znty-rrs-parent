package com.znty.sirm.model;

import lombok.Data;

/**
 * 流程下拉选项
 */
@Data
public class FlowOptionDto {

    /** 流程 ID */
    private Long flowId;

    /** 流程 Key */
    private String flowKey;

    /** 流程名称 */
    private String flowName;
}
