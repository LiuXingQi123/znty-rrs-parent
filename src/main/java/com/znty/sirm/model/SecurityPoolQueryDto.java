package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 证券池查询返回对象
 */
@Data
public class SecurityPoolQueryDto {

    /** 主键 ID */
    private Long id;
    /** 证券名称 */
    private String securityShortName;
    /** 证券代码 */
    private String securityCode;
    /** 调整人 */
    private String adjusterName;
    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;
    /** 投资池名称 */
    private String targetPoolName;
    /** 证券类型编码 */
    private String securityType;

    /** 证券类型名称（关联 dict_security_type 表） */
    private String securityTypeName;
    /** 我的证券池记录ID（null=未收藏，非null=已收藏，用于前端爱心图标状态） */
    private Long mySecurityPoolId;
    /** 票面年利率 */
    private String couponRate;
    /** 发行主体名称 */
    private String issuer;
    /** 证券全称 */
    private String fullName;
    /** 发行日期 */
    private String issueDate;
    /** 起息日 */
    private String carryDate;
    /** 到期日 */
    private String maturityDate;
    /** 退市日期 */
    private String delistDate;
    /** 行权日期（回售） */
    private String repurchaseDate;
}
