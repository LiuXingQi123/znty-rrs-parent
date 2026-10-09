package com.znty.rrs.entity.stockpooladjust;

import java.util.List;
import lombok.Data;

/** 股票池调库审核请求。 */
@Data
public class StockPoolAdjustAuditReq {
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
    /** 驳回修改阶段逐条调整记录的附件变更 */
    private List<AttachmentChange> attachmentChanges;

    /** 同批次单条调整记录附件变更 */
    @Data
    public static class AttachmentChange {
        /** 调整记录 ID */
        private Long adjustLogId;
        /** 待删除附件 ID */
        private List<Long> deleteAttachmentIds;
        /** 本次上传报告索引 */
        private List<Integer> reportFileIndexes;
        /** 本次上传材料索引 */
        private List<Integer> materialFileIndexes;
        /** 来源报告附件 ID */
        private List<Long> reportSourceAttachmentIds;
        /** 来源材料附件 ID */
        private List<Long> materialSourceAttachmentIds;
    }
}
