package com.znty.sirm.model;

import lombok.Data;

import java.util.List;

/**
 * 调库校验结果
 */
@Data
public class AdjustCheckDto {

    /** 各目标池校验结果列表（顺序与请求 items 一致） */
    private List<CheckResultItem> items;

    /**
     * 单个目标池校验结果
     */
    @Data
    public static class CheckResultItem {

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 投资池路径名称（格式：父级名称/子级名称，如"专户产品/二级库"） */
        private String poolName;

        /** 投资池类型 */
        private String poolType;

        /** 调整方向：调入 / 调出 */
        private String adjustMode;

        /** 调整项来源：manual=用户主动选择, linkage=联动调整, mutex=互斥调整 */
        private String itemTag;

        /** 是否可调整 */
        private boolean canAdjust;

        /** 不可调整原因列表（可调整时为空列表） */
        private List<String> failReasons;
    }
}
