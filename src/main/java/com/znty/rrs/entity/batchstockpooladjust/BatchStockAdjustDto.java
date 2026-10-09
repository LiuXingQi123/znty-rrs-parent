package com.znty.rrs.entity.batchstockpooladjust;

import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckDto;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 股票池批量校验及提交结果 */
@Data
public class BatchStockAdjustDto {
    /** 展开后的完整调库校验明细 */
    private List<StockAdjustCheckDto.CheckResultItem> items = new ArrayList<>();
    /** 本次校验或提交的股票数量 */
    private Integer stockCount;
    /** 本次写入的调库日志数量 */
    private Integer submitCount;
    /** 本次写入的调库日志 ID */
    private List<Long> logIds = new ArrayList<>();
    /** 各股票独立的调库批次号 */
    private List<String> adjustBatchNos = new ArrayList<>();
}
