package com.znty.sirm.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 债券信息列表返回对象（债券查询列表用）
 */
@Data
public class BondInfoDto {
    /** 主键 ID */
    private Long id;
    /** 债券代码 */
    private String bondCode;
    /** 债券简称 */
    private String bondShortName;
    /** 发行人 */
    private String issuer;
    /** 债券全称 */
    private String fullName;
    /** 发行总额(亿元) */
    private BigDecimal issueAmount;
    /** 起息日期 */
    private String carryDate;
    /** 到期日 */
    private String maturityDate;
    /** 债券评级 */
    private String bondRating;
    /** 主体评级 */
    private String issuerRating;
    /** 债券类型 */
    private String bondType;
    /** 当期利率(%) */
    private BigDecimal currentRate;
    /** 债券期限 */
    private String termStr;
}
