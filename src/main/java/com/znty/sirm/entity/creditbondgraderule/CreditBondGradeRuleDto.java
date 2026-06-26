package com.znty.sirm.entity.creditbondgraderule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 主体内评分档规则 DTO，返回期限、评分档、投资池选项和矩阵关系
 */
@Data
public class CreditBondGradeRuleDto {

    /** 期限分组列表 */
    private List<TermBucketItem> termBuckets = new ArrayList<>();

    /** 主体内评分档列表 */
    private List<InnerRatingGradeItem> ratingGrades = new ArrayList<>();

    /** 信用债大库一至五级库选项 */
    private List<PoolOptionItem> poolOptions = new ArrayList<>();

    /** 矩阵关系列表 */
    private List<RuleItem> rules = new ArrayList<>();

    /**
     * 期限分组选项
     */
    @Data
    public static class TermBucketItem {

        /** 主键 ID */
        private Long id;

        /** 期限分组编码 */
        private String bucketCode;

        /** 期限分组名称 */
        private String bucketName;

        /** 期限下限年数 */
        private BigDecimal minTermYear;

        /** 是否包含期限下限 */
        private Integer minInclusive;

        /** 期限上限年数 */
        private BigDecimal maxTermYear;

        /** 是否包含期限上限 */
        private Integer maxInclusive;

        /** 期限分组表达式 */
        private String expressionText;

        /** 排序序号 */
        private Integer sortNo;
    }

    /**
     * 主体内评分档选项
     */
    @Data
    public static class InnerRatingGradeItem {

        /** 主键 ID */
        private Long id;

        /** 主体内评分档编码 */
        private String gradeCode;

        /** 主体内评分档名称 */
        private String gradeName;

        /** 排序序号 */
        private Integer sortNo;
    }

    /**
     * 投资池选项
     */
    @Data
    public static class PoolOptionItem {

        /** 投资池 ID */
        private Long poolId;

        /** 投资池编码 */
        private String poolCode;

        /** 投资池名称 */
        private String poolName;

        /** 投资池完整名称 */
        private String poolFullName;

        /** 内部排序 */
        private Integer innerSort;
    }

    /**
     * 矩阵关系项
     */
    @Data
    public static class RuleItem {

        /** 主键 ID */
        private Long id;

        /** 期限分组 ID */
        private Long termBucketId;

        /** 主体内评分档 ID */
        private Long innerRatingGradeId;

        /** 投资池 ID */
        private Long poolId;

        /** 投资池编码快照 */
        private String poolCodeSnapshot;

        /** 投资池名称快照 */
        private String poolNameSnapshot;

        /** 排序序号 */
        private Integer sortNo;
    }
}
