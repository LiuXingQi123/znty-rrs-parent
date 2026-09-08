package com.znty.rrs.entity.batchcrmwpooladjust;

import lombok.Data;

import java.util.List;

/**
 * CRMW 池批量调库校验 / 提交请求对象
 */
@Data
public class BatchCrmwAdjustReq {

    /** 当前用户 ID，1 视为管理员 */
    private String currentUserId;

    /** 调整人 ID */
    private String adjusterId;

    /** 调整人名称 */
    private String adjusterName;

    /** 目标投资池 ID */
    private Long poolId;

    /** 调整方向：in=调入 / out=调出 */
    private String direction;

    /** 目标投资池名称 */
    private String poolName;

    /** 投资池类型 */
    private String poolType;

    /** 调整原因 */
    private String adjustReason;

    /** 调整建议 */
    private String adjustAdvice;

    /** 是否放开主体债入库矩阵规则：yes=放开 / no=不放开 */
    private String releaseRules;

    /** 整批信评报告附件在 multipart 文件数组中的下标 */
    private List<Integer> creditReportFileIndexes;

    /** 整批其他材料附件在 multipart 文件数组中的下标 */
    private List<Integer> materialFileIndexes;

    /** 已选组合列表，用于下一步校验 */
    private List<SecurityItem> securities;

    /** 已确认的调库明细列表，用于提交 */
    private List<AdjustItem> items;

    /**
     * 批量选择的凭证+标的组合
     */
    @Data
    public static class SecurityItem {

        /** 标的证券代码 */
        private String securityCode;

        /** 标的证券简称 */
        private String securityShortName;

        /** 标的证券类型 */
        private String securityType;

        /** CRMW 凭证名称 */
        private String crmwName;

        /** CRMW 凭证代码 */
        private String crmwScode;

        /** CRMW 证券类型 */
        private String crmwStype;

        /** 前端选中的担保人代码 */
        private String guarantorCode;
    }

    /**
     * 批量提交的单条调库明细
     */
    @Data
    public static class AdjustItem {

        /** 标的证券代码 */
        private String securityCode;

        /** 标的证券简称 */
        private String securityShortName;

        /** 标的证券类型 */
        private String securityType;

        /** 本次校验时选择的担保人 Wind 主体代码 */
        private String guarantorCode;

        /** CRMW 凭证名称 */
        private String crmwName;

        /** CRMW 凭证代码 */
        private String crmwScode;

        /** CRMW 证券类型 */
        private String crmwStype;

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 目标投资池名称 */
        private String targetPoolName;

        /** 投资池类型 */
        private String poolType;

        /** 调整方向：调入 / 调出 */
        private String adjustMode;

        /** 调整项来源：manual=手工 / linkage=联动 / mutex=互斥 / related=多市场关联码 */
        private String itemTag;

        /** 调库分组 Key */
        private String adjustGroupKey;

        /** 触发扩批的主组合键（crmwScode|securityCode） */
        private String sourceSecurityCode;

        /** 审批流程 ID */
        private Long flowId;

        /** 审批流程 Key */
        private String flowKey;

        /** 审批流程类型 */
        private String flowType;

        /** 调整说明 */
        private String adjustmentNote;

        /** 信评报告附件在 multipart 文件数组中的下标 */
        private List<Integer> creditReportFileIndexes;

        /** 其他材料附件在 multipart 文件数组中的下标 */
        private List<Integer> materialFileIndexes;

        /** 需要复制为信评报告附件的报告库附件 ID */
        private List<Long> creditReportSourceAttachmentIds;

        /** 需要复制为其他材料附件的报告库附件 ID */
        private List<Long> materialSourceAttachmentIds;
    }
}
