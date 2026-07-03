package com.znty.rrs.entity.creditbondgraderule;

import java.util.List;
import lombok.Data;

/**
 * 主体内评分档规则请求对象，携带矩阵保存数据和经办人信息
 */
@Data
public class CreditBondGradeRuleReq {

    /** 矩阵关系列表 */
    private List<RuleItem> rules;

    /** 经办人 ID */
    private String operatorId;

    /**
     * 期限分组、主体内评分档、投资池关系请求项
     */
    @Data
    public static class RuleItem {

        /** 期限分组 ID */
        private Long termBucketId;

        /** 主体内评分档 ID */
        private Long innerRatingGradeId;

        /** 投资池 ID */
        private Long poolId;
    }
}
