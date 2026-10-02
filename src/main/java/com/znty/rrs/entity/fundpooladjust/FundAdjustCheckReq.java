package com.znty.rrs.entity.fundpooladjust;

import java.util.List;
import lombok.Data;

/** 基金调库校验请求 */
@Data
public class FundAdjustCheckReq {
    /** 基金代码 */
    private String fundCode;
    /** 待校验调库项 */
    private List<CheckItem> items;

    /** 单个调库项 */
    @Data
    public static class CheckItem {
        /** 目标池 ID */
        private Long targetPoolId;
        /** 目标池名称 */
        private String targetPoolName;
        /** 投资池类型 */
        private String poolType;
        /** 调整方向：in=调入 / out=调出 */
        private String adjustMode;
    }
}
