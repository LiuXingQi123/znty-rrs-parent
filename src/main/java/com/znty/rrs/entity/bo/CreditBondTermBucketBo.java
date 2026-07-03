package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 信用债期限分组业务对象，对应 credit_bond_term_bucket 表
 */
@Data
public class CreditBondTermBucketBo {

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

    /** 是否启用 */
    private Integer enabled;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
