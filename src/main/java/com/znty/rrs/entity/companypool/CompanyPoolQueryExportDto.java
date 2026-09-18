package com.znty.rrs.entity.companypool;

import lombok.Data;

/**
 * 主体池查询 Excel 导出行。
 */
@Data
public class CompanyPoolQueryExportDto {
    /** 主体名称 */
    private String securityShortName;
    /** 主体代码 */
    private String securityCode;
    /** 调整人 */
    private String adjusterName;
    /** 入池时间 */
    private String entryTime;
    /** 投资池名称 */
    private String targetPoolName;
}
