package com.znty.rrs.entity.stockpoolexcelimport;

import lombok.Data;

/** 股票 Excel 原始明细及行级状态。 */
@Data
public class StockPoolExcelImportItemDto {
    /** 导入明细 ID */
    private Long id;
    /** Excel 物理行号 */
    private Integer rowNo;
    /** 股票代码 */
    private String stockCode;
    /** 股票名称，以主档为准 */
    private String stockName;
    /** 父池名称 */
    private String parentPoolName;
    /** 子池名称 */
    private String childPoolName;
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
