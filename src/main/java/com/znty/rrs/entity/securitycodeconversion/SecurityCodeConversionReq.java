package com.znty.rrs.entity.securitycodeconversion;

import lombok.Data;

/**
 * 证券临时代码转正式代码请求。
 */
@Data
public class SecurityCodeConversionReq {

    /** 待替换的临时证券代码 */
    private String tempSecurityCode;

    /** 正式证券代码 */
    private String securityCode;

    /** 正式证券名称，兼容仅提供单一名称的调用方 */
    private String securityName;

    /** 正式证券全称，为空时使用正式证券名称 */
    private String securityFullName;

    /** 正式证券简称，为空时使用正式证券名称 */
    private String securityShortName;

    /** 正式证券市场 */
    private String securityMarket;

    /** 正式证券类型 */
    private String securityType;

    /** 正式证券沪市代码 */
    private String windCodeSh;

    /** 正式证券深市代码 */
    private String windCodeSz;

    /** 正式证券银行间市场代码 */
    private String windCodeNib;

    /** 正式证券北交所代码 */
    private String windCodeBj;

    /** 正式证券其他市场代码 */
    private String windCodeNbc;

    /** 操作来源：manual=人工 / job=定时任务 / other=其他 */
    private String oprtSource;
}
