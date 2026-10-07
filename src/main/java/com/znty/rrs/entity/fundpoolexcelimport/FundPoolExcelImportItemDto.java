package com.znty.rrs.entity.fundpoolexcelimport;

import lombok.Data;

/** 基金 Excel 原始明细及行级状态。 */
@Data
public class FundPoolExcelImportItemDto {
    /** 导入明细 ID */
    private Long id;
    /** Excel 物理行号 */
    private Integer rowNo;
    /** 基金代码 */
    private String fundCode;
    /** 基金名称，以主档为准 */
    private String fundName;
    /** 父池名称 */
    private String parentPoolName;
    /** 子池名称 */
    private String childPoolName;
    /** 基金评分原始文本 */
    private String fundScoreRaw;
    /** 基金投资类型原始文本 */
    private String fundInvestmentTypeRaw;
    /** 风管领导审批原始文本 */
    private String needRiskLeaderApprovalRaw;
    /** 解析目标池 ID */
    private Long targetPoolId;
    /** 池类型 */
    private String poolType;
    /** 校验结果：0 / 1 / 2 */
    private String chkRslt;
    /** 校验说明 */
    private String chkDscr;
    /** 保存结果 */
    private String saveRslt;
    /** 保存说明 */
    private String saveDscr;
}
