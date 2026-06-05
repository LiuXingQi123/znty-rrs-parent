package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 审批节点配置表实体，存储流程中审批节点的策略和审批人员信息
 */
@Data
public class NodeApprovalConfigBo {
    /** 主键 */
    private Long id;
    /** 关联的流程节点 ID */
    private Long nodeId;
    /** 审批策略（如：任意一人通过/全部通过） */
    private String approvalStrategy;
    /** 审批人列表（JSON 字符串，存储角色或人员 ID） */
    private String approvalPersons;
    /** 审批节点备注说明 */
    private String approvalRemark;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
