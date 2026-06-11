package com.znty.sirm.model;

import lombok.Data;

/**
 * 证券池调库审批处理请求，用于提交当前流程步骤的通过或驳回意见。
 */
@Data
public class SecurityPoolAdjustAuditReq {

    /** 调库记录 ID */
    private Long adjustLogId;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 当前处理的流程步骤 ID */
    private Long stepId;

    /** 处理动作：approve=通过 / reject=驳回 */
    private String processAction;

    /** 处理意见 */
    private String processComment;

    /** 当前处理人 ID */
    private String handlerId;

    /** 当前处理人名称 */
    private String handlerName;
}
