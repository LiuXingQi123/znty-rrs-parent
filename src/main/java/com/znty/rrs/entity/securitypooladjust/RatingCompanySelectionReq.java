package com.znty.rrs.entity.securitypooladjust;

import lombok.Data;

/** 页面已查询并选中的评级主体，后续请求直接传递，不重复查询候选 */
@Data
public class RatingCompanySelectionReq {

    /** 本次最终主体代码，自选优先 */
    private String companyCode;

    /** 本次最终主体名称 */
    private String companyName;

    /** 本次最终主体内评 */
    private String innerRating;

    /** 本次最终主体外评 */
    private String ratingBondissuer;

    /** 外评对应机构 */
    private String ratingBondissuerAgency;

    /** 普通权益人名称，申请快照留痕使用 */
    private String rightsHolderName;

    /** 自选权益人名称，申请快照留痕使用 */
    private String selfSelectedRightsHolderName;
}
