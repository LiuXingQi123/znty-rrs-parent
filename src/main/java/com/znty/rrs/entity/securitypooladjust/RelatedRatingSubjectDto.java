package com.znty.rrs.entity.securitypooladjust;

import lombok.Data;

import java.util.Date;

/**
 * 证券池调库相关评级主体 DTO。
 */
@Data
public class RelatedRatingSubjectDto {

    /** 所属证券 Wind 代码 */
    private String securityCode;

    /** 主体 Wind 代码 */
    private String companyCode;

    /** 主体名称 */
    private String companyName;

    /** 关系类型编码 */
    private Long relationTypeCode;

    /** 最新主体内评分档 */
    private String innerRating;

    /** 内评更新时间 */
    private Date ratingTime;
}
