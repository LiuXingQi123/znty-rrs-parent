package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 审批节点配置
 */
@Data
public class NodeApprovalConfigBo {
    /** 主键 */
    private Long id;
    /** 节点 ID */
    private Long nodeId;
    /** 审批策略 */
    private String approvalStrategy;
    /** 审批人 JSON */
    private String approvalPersons;
    /** 审批备注 */
    private String approvalRemark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;
}
