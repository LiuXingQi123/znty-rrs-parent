package com.znty.sirm.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 信用债期限主体内评分档投资池关系业务对象，对应 credit_bond_pool_grade_rule 表
 */
@Data
public class CreditBondPoolGradeRuleBo {

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

    /** 是否启用 */
    private Integer enabled;

    /** 排序序号 */
    private Integer sortNo;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
