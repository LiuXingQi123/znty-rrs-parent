package com.znty.rrs.entity.mymatters;

import lombok.Data;

/** 当前用户可显示的已接入业务入口 */
@Data
public class BusinessDomainDto {
    /** 业务编码 */
    private String businessDomain;
}
