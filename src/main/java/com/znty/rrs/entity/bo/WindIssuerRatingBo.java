package com.znty.rrs.entity.bo;

import lombok.Data;

/**
 * Wind 债券发行人最新评级业务对象，对应 ais_inv_ods.wind_cbondissuerrating 最新一条记录。
 * 调库简易流程第⑤条件用其判断主体/担保人评级是否下调。
 */
@Data
public class WindIssuerRatingBo {

    /** 主体信用评级（当前，b_info_creditrating） */
    private String creditRating;

    /** 前次主体信用评级（b_info_precreditrating） */
    private String preCreditRating;

    /** 信用评级变动文本（b_creditratingchange，如 维持/上调/下调） */
    private String creditRatingChange;
}
