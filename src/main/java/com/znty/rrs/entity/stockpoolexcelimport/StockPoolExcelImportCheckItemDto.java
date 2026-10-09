package com.znty.rrs.entity.stockpoolexcelimport;

import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckDto;
import java.util.List;
import lombok.Data;

/** 股票 Excel 调库校验项，保留来源行和一般流程候选。 */
@Data
public class StockPoolExcelImportCheckItemDto {
    /** 股票代码 */
    private String stockCode;
    /** 股票简称 */
    private String stockShortName;
    /** 股票产品类型 */
    private String securityType;
    /** 目标池 ID */
    private Long targetPoolId;
    /** 投资池全路径 */
    private String poolName;
    /** 投资池类型 */
    private String poolType;
    /** 调整方向：in / out */
    private String adjustDirection;
    /** 来源：manual / linkage / mutex / clear */
    private String itemTag;
    /** 来源分组键 */
    private String adjustGroupKey;
    /** 来源导入明细 ID，清空项为空 */
    private Long sourceItemId;
    /** 来源 Excel 物理行号，清空项为空 */
    private Integer rowNo;
    /** 是否可调整 */
    private boolean canAdjust;
    /** 阻断原因 */
    private List<String> failReasons;
    /** 非阻断提示 */
    private List<String> warnings;
    /** 一般流程候选 */
    private List<StockAdjustCheckDto.FlowOption> flowOptions;
    /** 选中一般流程 ID */
    private Long selectedFlowId;
    /** 选中一般流程 Key */
    private String selectedFlowKey;
    /** 选中一般流程类型 */
    private String selectedFlowType;
    /** 调整说明 */
    private String adjustmentNote;
    /** 调整类型 */
    private String adjustType;
}
