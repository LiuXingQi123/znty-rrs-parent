package com.znty.rrs.entity.securitypooladjust;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 自选权益人分页结果 DTO。
 */
@Data
public class SelfSelectedRightsHolderDto {

    /** 主体编码 */
    private String companyCode;

    /** 主体名称 */
    private String companyName;

    /** 最新主体内评分档 */
    private String innerRating;

    /** 内评更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date ratingTime;
}
