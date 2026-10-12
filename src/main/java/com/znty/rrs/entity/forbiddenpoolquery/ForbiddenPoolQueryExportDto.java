package com.znty.rrs.entity.forbiddenpoolquery;

import lombok.Data;

/**
 * 禁投池查询 Excel 导出行。
 */
@Data
public class ForbiddenPoolQueryExportDto {
    /** 证券简称 */
    private String securityShortName;
    /** 证券身份：正式ISIN / TEMP:临时代码 */
    private String isinCode;
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
    /** 证券代码 */
    private String securityCode;
    /** 发行主体 */
    private String issuer;
    /** 调整人 */
    private String adjusterName;
    /** 证券类型 */
    private String securityTypeName;
    /** 投资池名称 */
    private String targetPoolName;
    /** 入池时间 */
    private String entryTime;
    /** 证券状态 */
    private String securityStatusLabel;
    /** 退市日期 */
    private String delistDate;
    /** 行权日期（回售） */
    private String repurchaseDate;
}
