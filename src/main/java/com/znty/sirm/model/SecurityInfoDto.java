package com.znty.sirm.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 证券信息列表返回对象（证券查询列表用）
 */
@Data
public class SecurityInfoDto {
    /** 主键 ID */
    private Long id;
    /** 证券代码 */
    private String securityCode;
    /** 证券简称 */
    private String securityShortName;
    /** 发行人 */
    private String issuer;
    /** 证券全称 */
    private String fullName;
    /** 发行总额(亿元) */
    private BigDecimal issueAmount;
    /** 起息日期 */
    private String carryDate;
    /** 到期日 */
    private String maturityDate;
    /** 证券评级 */
    private String securityRating;
    /** 主体评级 */
    private String issuerRating;
    /** 证券类型 */
    private String securityType;
    /** 当期利率(%) */
    private BigDecimal currentRate;
    /** 证券期限 */
    private String termStr;
}
