package com.znty.rrs.entity.fundpooladjust;

import lombok.Data;

/** 基金池调库审核处理结果。 */
@Data
public class FundPoolAdjustAuditDto {
    /** 调库记录 ID。 */
    private Long adjustLogId;
    /** 调库批次号。 */
    private String adjustBatchNo;
    /** 流程步骤 ID。 */
    private Long stepId;
    /** 当前审核状态。 */
    private String auditStatus;
    /** 流程是否结束。 */
    private Boolean finished;
    /** 是否生成后续待处理步骤。 */
    private Boolean nextStepCreated;
    /** 处理结果说明。 */
    private String message;
}
