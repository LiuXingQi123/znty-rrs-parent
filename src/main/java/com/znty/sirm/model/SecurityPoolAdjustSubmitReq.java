package com.znty.sirm.model;

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

        /** 本次选择的流程 ID（当前仅透传，不落表） */
        private Long flowId;

        /** 本次选择的流程 Key（当前仅透传，不落表） */
        private String flowKey;

        /** 本次选择的流程类型（当前仅透传，不落表） */
        private String flowType;

        /** 调整说明 */
        private String adjustmentNote;

        /** 附件报告（JSON字符串） */
        private String attachmentFiles;

        /** 其他材料（JSON字符串） */
        private String materialFiles;
    }
}
