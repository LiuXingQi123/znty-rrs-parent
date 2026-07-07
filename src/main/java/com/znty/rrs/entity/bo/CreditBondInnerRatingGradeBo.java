package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 信用债主体内评分档业务对象，对应 credit_bond_inner_rating_grade 表
 */
@Data
public class CreditBondInnerRatingGradeBo {

    /** 主键 ID */
    private Long id;

    /** 主体内评分档编码 */
    private String gradeCode;

    /** 主体内评分档名称 */
    private String gradeName;

    /** 排序序号 */
    private Integer sortNo;

    /** 是否启用 */
    private Integer enabled;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
