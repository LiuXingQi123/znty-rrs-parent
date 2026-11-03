package com.znty.rrs.entity.graderulealert;

import lombok.Data;

/** ABS 规则提醒中的主体候选。 */
@Data
public class AbsAlertCandidateDto {

    /** 主体编码 */
    private String companyCode;

    /** 主体全称 */
    private String companyName;

    /** 主体内评分 */
    private String innerIssuerRating;

    /** 主体外评 */
    private String ratingBondissuer;

    /** 主体外评机构 */
    private String ratingBondissuerAgency;

    /** 关系类型编码 */
    private Long relationTypeCode;
}
