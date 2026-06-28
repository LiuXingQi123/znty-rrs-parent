package com.znty.sirm.entity.forbiddenpooladjust;

import lombok.Data;

import java.util.List;

/**
 * 禁投池主体调整校验请求对象。
 */
@Data
public class ForbiddenPoolAdjustCheckReq {

    /** 主体代码 */
    private String companyCode;
    /** 主体简称 */
    private String companyShortName;
    /** 主体类型 */
    private String securityType;
    /** 待校验调整项 */
    private List<CheckItem> items;

    /**
     * 单条待校验调整项。
     */
    @Data
    public static class CheckItem {
        /** 目标投资池 ID */
        private Long targetPoolId;
        /** 目标投资池名称 */
        private String targetPoolName;
        /** 投资池类型 */
        private String poolType;
        /** 调整方向 */
        private String adjustMode;
    }
}
