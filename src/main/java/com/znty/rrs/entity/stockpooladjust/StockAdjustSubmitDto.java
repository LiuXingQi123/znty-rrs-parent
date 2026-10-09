package com.znty.rrs.entity.stockpooladjust;

import java.util.List;
import lombok.Data;

/** 股票调库提交结果 */
@Data
public class StockAdjustSubmitDto {
    /** 新增股票调库记录 ID */
    private List<Long> adjustLogIds;
    /** 本次生成的调库批次号 */
    private List<String> adjustBatchNos;
}
