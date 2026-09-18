package com.znty.rrs.entity.forbiddenpoolhistory;

import lombok.Data;

/**
 * 禁投池历史 Excel 导出行。
 */
@Data
public class ForbiddenPoolHistoryExportDto {
    /** 调整人 */
    private String adjusterName;
    /** 提交时间 */
    private String submitTime;
    /** 证券名称 */
    private String securityShortName;
    /** 证券代码 */
    private String securityCode;
    /** 发行主体 */
    private String issuer;
    /** 调整类型 */
    private String adjustType;
    /** 调整方向 */
    private String adjustMode;
    /** 投资池名称 */
    private String targetPoolName;
    /** 审核状态 */
    private String auditStatusLabel;
}
