package com.znty.rrs.entity.flow;


import com.znty.rrs.entity.bo.EdgeCondRuleBo;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 流程运行时快照，聚合流程定义、版本、节点、连线和审批配置。
 */
@Data
@AllArgsConstructor
public class FlowSnapshot {

    /** 流程定义 */
    private final FlowDefinitionBo definition;

    /** 当前启用流程版本 */
    private final FlowVersionBo activeVersion;

    /** 节点索引：节点 ID -> 节点 */
    private final Map<Long, FlowNodeBo> nodeMap;

    /** 流程连线列表 */
    private final List<FlowEdgeBo> edges;

    /** 审批配置索引：节点 ID -> 审批配置 */
    private final Map<Long, NodeApprovalConfigBo> approvalConfigMap;

    /** 审批处理人索引：审批配置 ID -> 处理人列表 */
    private final Map<Long, List<NodeApprovalHandlerBo>> approvalHandlerMap;

    /** 连线条件索引：连线 ID -> 条件列表 */
    private final Map<Long, List<EdgeCondRuleBo>> condRuleMap;
}
