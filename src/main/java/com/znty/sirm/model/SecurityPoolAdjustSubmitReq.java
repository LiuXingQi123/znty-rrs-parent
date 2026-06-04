package com.znty.sirm.model;

import lombok.Data;

import java.util.List;

/**
 * 证券池调库提交请求
 */
@Data
public class SecurityPoolAdjustSubmitReq {

    /** 证券 ID */
    private Long securityId;

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

        /** 调整说明 */
        private String adjustmentNote;

        /** 附件报告（JSON字符串） */
        private String attachmentFiles;

        /** 其他材料（JSON字符串） */
        private String materialFiles;
    }
}
