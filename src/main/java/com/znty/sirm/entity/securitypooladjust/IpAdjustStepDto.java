package com.znty.sirm.entity.securitypooladjust;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 证券池调库-流程步骤记录 DTO，返回给前端展示审批流每一步的处理状态
 */
@Data
public class IpAdjustStepDto {

    /** 主键 ID */
    private Long id;

    /** 关联调库记录 ID */
    private Long adjustLogId;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 关联流程节点 ID */
    private Long flowNodeId;

    /** 节点业务标识 */
    private String nodeCode;

    /** 节点显示名称 */
    private String nodeLabel;

    /** 节点类型 */
    private String nodeType;

    /** 审批策略 */
    private String approvalStrategy;

    /** 排序序号 */
    private Integer sortOrder;

    /** 步骤处理状态 */
    private String stepStatus;

    /** 处理人 ID */
    private String handlerId;

    /** 处理人名称 */
    private String handlerName;

    /** 处理动作 */
    private String processAction;

    /** 处理意见 */
    private String processComment;

    /** 步骤激活时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date processTime;
}
