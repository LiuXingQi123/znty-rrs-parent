package com.znty.rrs.entity.fundpooladjusthistory;

import lombok.Data;

/** 基金池调整历史 Excel 导出行。 */
@Data
public class FundPoolAdjustHistoryExportDto {
    /** 调整人。 */
    private String adjusterName;
    /** 提交时间。 */
    private String submitTime;
    /** 基金名称。 */
    private String fundName;
    /** 基金代码。 */
    private String fundCode;
    /** 基金类型。 */
    private String securityTypeName;
    /** 调整类型。 */
    private String adjustType;
    /** 调整方向。 */
    private String adjustMode;
    /** 投资池。 */
    private String targetPoolPath;
    /** 审核状态。 */
    private String auditStatusLabel;
}
