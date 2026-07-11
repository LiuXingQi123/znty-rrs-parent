package com.znty.rrs.entity.securitypooladjust;

import lombok.Data;

import java.util.List;

/**
 * 证券调库校验请求对象，携带证券基本信息和待校验的目标池调整项列表
 */
@Data
public class AdjustCheckReq {

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 证券类型 */
    private String securityType;

    /** 基金评分（基金证券调入校验用，前端传入；空则跳过基金评分校验） */
    private String fundRate;

    /** 前端选中的担保人代码（多担保人时下拉二选一；用于简易流程第⑤条件担保人评级下调判断，空表示无担保人/未选中） */
    private String guarantorCode;

    /** 是否放开主体债入库矩阵规则 */
    private boolean releaseRules;

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
