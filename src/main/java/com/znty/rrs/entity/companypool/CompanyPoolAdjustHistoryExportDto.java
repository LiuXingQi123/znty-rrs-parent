package com.znty.rrs.entity.companypool;

import lombok.Data;

/**
 * 主体池调整历史 Excel 导出行。
 */
@Data
public class CompanyPoolAdjustHistoryExportDto {
    /** 调整人 */
    private String adjusterName;
    /** 提交日期 */
    private String submitTime;
    /** 主体名称 */
    private String companyName;
    /** 主体代码 */
    private String companyCode;
    /** 调整类型 */
    private String adjustType;
    /** 调整方向 */
    private String adjustMode;
    /** 投资池名称 */
    private String targetPoolName;
    /** 审核状态 */
    private String auditStatusLabel;
}