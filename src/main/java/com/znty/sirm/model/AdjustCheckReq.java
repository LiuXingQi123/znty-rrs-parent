package com.znty.sirm.model;

import lombok.Data;

import java.util.List;

/**
 * 调库校验请求
 */
@Data
public class AdjustCheckReq {

    /** 证券 ID */
    private Long securityId;

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 证券类型 */
    private String securityType;

    /** 待校验的调库项列表 */
    private List<CheckItem> items;

    /**
     * 单个调库项
     */
    @Data
    public static class CheckItem {

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 目标投资池名称（前端原始名，备用） */
        private String targetPoolName;

        /** 投资池类型 */
        private String poolType;

        /** 调整方向：调入 / 调出 */
        private String adjustMode;
    }
}
