package com.znty.rrs.entity.securitypooladjusthistory;

import lombok.Data;

/**
 * 证券池调整历史 Excel 导出行。
 */
@Data
public class SecurityPoolAdjustHistoryExportDto {
    /** 调整人 */
    private String adjusterName;
    /** 提交日期 */
    private String submitTime;
    /** 证券简称 */
    private String securityShortName;
    /** 证券代码 */
    private String securityCode;
    /** 证券类型 */
    private String securityTypeName;
    /** 发行主体名称 */
    private String issuer;
    /** 调整类型 */
    private String adjustType;
    /** 调整方向 */
    private String adjustMode;
    /** 调整原因 */
    private String adjustReason;
    /** 投资池名称 */
    private String targetPoolPath;
    /** 审核状态 */
    private String auditStatusLabel;
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
