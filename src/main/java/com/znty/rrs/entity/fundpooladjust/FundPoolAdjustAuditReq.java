package com.znty.rrs.entity.fundpooladjust;

import lombok.Data;

/** 基金池调库审核请求。 */
@Data
public class FundPoolAdjustAuditReq {
    /** 调库记录 ID。 */
    private Long adjustLogId;
    /** 调库批次号。 */
    private String adjustBatchNo;
    /** 流程步骤 ID。 */
    private Long stepId;
    /** 处理动作：approve / reject。 */
    private String processAction;
    /** 处理意见。 */
    private String processComment;
    /** 调整原因，仅驳回待修改重新提交时使用；null 表示不修改 */
    private String adjustReason;

    /** 调整意见，仅驳回待修改重新提交时使用；空字符串可清空 */
    private String adjustAdvice;

    /** 当前处理人 ID。 */
    private String handlerId;
    /** 当前处理人名称。 */
    private String handlerName;
}
