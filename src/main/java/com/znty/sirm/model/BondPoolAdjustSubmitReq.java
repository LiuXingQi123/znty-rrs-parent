package com.znty.sirm.model;

import lombok.Data;

import java.util.List;

/**
 * 债券池调库提交请求
 */
@Data
public class BondPoolAdjustSubmitReq {

    /** 债券 ID */
    private Long bondId;

    /** 债券代码 */
    private String bondCode;

    /** 债券简称 */
    private String bondShortName;

    /** 债券类型 */
    private String bondType;

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
