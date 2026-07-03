package com.znty.rrs.entity.flow;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 流程画布连线 DTO，表示流程设计器中节点间有向连线的配置，包含条件规则信息
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanvasEdgeDto {
    /** 连线唯一标识 */
    private String id;
    /** 起始节点 ID */
    private String from;
    /** 目标节点 ID */
    private String to;
    /** 连线标签 */
    private String label;
    /** 流转动作：approve=通过 / reject=驳回 / auto=自动 / submit=提交 */
    private String routeAction;
    /** 连线备注 */
    private String remark;
    /** 条件逻辑：and/or */
    private String condLogic;
    /** 条件规则列表 */
    private List<CondRuleItemDto> condRules;
}
