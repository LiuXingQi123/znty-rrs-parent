package com.znty.rrs.entity.common;

import lombok.Data;

import java.util.Date;

/**
 * 担保人内评结果
 */
@Data
public class GuarantorGradeDto {

    /** 主体最新认可外评 */
    private String ratingBondissuer;

    /** 上述外评对应机构代码 */
    private String ratingBondissuerAgency;

    /** 所属证券 Wind 代码 */
    private String securityCode;

    /** Wind 主体代码 */
    private String windcode;

    /** Wind 主体名称 */
    private String windname;

    /** Wind 担保人类型编码 */
    private Long guarantorTypeCode;

    /** 主体内评分 */
    private String totalScore;

    /** 更新时间 */
    private Date ts;
}
