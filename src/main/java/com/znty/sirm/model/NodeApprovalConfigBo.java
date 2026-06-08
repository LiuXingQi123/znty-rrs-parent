package com.znty.sirm.model;

import lombok.Data;

import java.util.Date;

/**
 * 审批节点配置表实体，存储流程中审批节点的策略和备注信息
 */
@Data
public class NodeApprovalConfigBo {
    /** 主键 */
    private Long id;
    /** 关联的流程节点 ID */
    private Long nodeId;
    /** 审批策略（如：任意一人通过/全部通过） */
    private String approvalStrategy;
    /** 审批节点备注说明 */
    private String approvalRemark;
    /** 创建时间 */
    private Date crteTime;
    /** 更新时间 */
    private Date updtTime;
}
