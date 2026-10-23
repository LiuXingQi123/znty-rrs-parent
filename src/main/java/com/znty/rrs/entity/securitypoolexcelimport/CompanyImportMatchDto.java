package com.znty.rrs.entity.securitypoolexcelimport;

import lombok.Data;

/** 主体批量匹配结果，保留原查询值，匹配比较交由数据库完成。 */
@Data
public class CompanyImportMatchDto {
    /** 原查询值 */
    private String lookupValue;
    /** 主体代码 */
    private String companyCode;
    /** 主体全称 */
    private String companyFullName;
    /** 主体简称 */
    private String companyShortName;
}
