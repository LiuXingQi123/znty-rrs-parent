package com.znty.rrs.entity.securitypooladjust;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 证券池调库相关评级主体 DTO。
 */
@Data
public class RelatedRatingCompanyDto {

    /** 沪市证券代码 */
    private String windCodeSh;
    /** 深市证券代码 */
    private String windCodeSz;
    /** 银行间证券代码 */
    private String windCodeNib;
    /** 北交所证券代码 */
    private String windCodeBj;
    /** 其他市场证券代码 */
    private String windCodeNbc;
    /** 证券来源：official=正式 / temporary=临时 / temp_converted=已转正 */
    private String securitySource;
    /** 证券身份：正式ISIN / TEMP:临时代码 */
    private String isinCode;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date ratingTime;
}
