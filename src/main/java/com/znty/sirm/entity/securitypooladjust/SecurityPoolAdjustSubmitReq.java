package com.znty.sirm.entity.securitypooladjust;


import com.znty.sirm.entity.bo.SecurityInfoBo;
import lombok.Data;

import java.util.List;

/**
 * 证券池调库提交请求
 */
@Data
public class SecurityPoolAdjustSubmitReq {

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 证券类型 */
    private String securityType;

    /** CRMW名称 */
    private String crmwName;

    /** CRMW 证券代码 */
    private String crmwScode;

    /** CRMW 市场代码 */
    private String crmwMktcode;

    /** CRMW 证券类型 */
    private String crmwStype;

    /** 调整类型 */
    private String adjustType;

    /** 调整原因 */
    private String adjustReason;

    /** 调整意见 */
    private String adjustAdvice;

    /** 证券基础信息 */
    private SecurityInfoBo securityInfo;

    /** 调整人 ID */
    private String adjusterId;

    /** 调整人名称 */
    private String adjusterName;

    /** 调库明细列表 */
    private List<AdjustItem> items;

    /**
     * 单条调库明细
     */
    @Data
    public static class AdjustItem {

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 目标投资池名称 */
        private String targetPoolName;

        /** 投资池类型 */
        private String poolType;

        /** 调整模式：调入/调出 */
        private String adjustMode;

        /** 调整项来源：manual=手工 / linkage=联动 / mutex=互斥 */
        private String itemTag;

        /** 调库分组 Key：手工项及其触发的联动/互斥项共用 */
        private String adjustGroupKey;

        /** 本次选择的流程 ID */
        private Long flowId;

        /** 本次选择的流程 Key */
        private String flowKey;

        /** 本次选择的流程类型 */
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
