package com.znty.rrs.entity.securitypoolquery;

import lombok.Data;

/**
 * 证券池查询 Excel 导出行。
 */
@Data
public class SecurityPoolQueryExportDto {
    /** 证券简称 */
    private String securityShortName;
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
    /** 证券代码 */
    private String securityCode;
    /** 调整人 */
    private String adjusterName;
    /** 入池时间 */
    private String entryTime;
    /** 投资池名称 */
    private String targetPoolName;
    /** 证券类型 */
    private String securityTypeName;
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
    /** 证券期限（与页面 dateExistsStr 一致） */
    private String dateExistsStr;
    /** 证券状态 */
    private String securityStatusLabel;
    /** 退市日期 */
    private String delistDate;
    /** 行权日期（回售） */
    private String repurchaseDate;
    /** 是否 ABS */
    private String absLabel;
    /** 是否担保 */
    private String guarantLabel;
    /** 是否永续 */
    private String yxLabel;
    /** 是否次级 */
    private String cjLabel;
    /** 是否私募 */
    private String privateLabel;
    /** 是否含权 */
    private String inrightLabel;
}
