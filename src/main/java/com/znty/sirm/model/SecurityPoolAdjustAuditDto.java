package com.znty.sirm.model;

import lombok.Data;

/**
 * 证券池调库审批处理结果 DTO，返回审批动作后的流程状态。
 */
@Data
public class SecurityPoolAdjustAuditDto {

    /** 调库记录 ID */
    private Long adjustLogId;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 当前处理步骤 ID */
    private Long stepId;

    /** 调库记录审核状态 */
    private String auditStatus;

    /** 是否已结束流程 */
    private Boolean finished;

    /** 是否新增了后续待处理步骤 */
    private Boolean nextStepCreated;

    /** 结果提示 */
    private String message;
}
