package com.znty.rrs.entity.batchstockpooladjust;

import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustSubmitReq;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 股票池批量校验及提交请求 */
@Data
public class BatchStockAdjustReq {
    /** 当前用户 ID */
    private String currentUserId;
    /** 手工调整目标投资池 ID */
    private Long poolId;
    /** 批量方向：in / out */
    private String direction;
    /** 待校验的股票 */
    private List<StockItem> stocks;
    /** 整批调整原因 */
    private String adjustReason;
    /** 整批调整建议 */
    private String adjustAdvice;
    /** 调整人 ID */
    private String adjusterId;
    /** 调整人名称 */
    private String adjusterName;
    /** 已校验的完整股票调库明细 */
    private List<AdjustItem> items;

    /** 单个待校验股票 */
    @Data
    public static class StockItem {
        /** 股票代码 */
        private String stockCode;
    }

    /** 复用股票单笔明细字段并标记所属股票 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AdjustItem extends StockPoolAdjustSubmitReq.AdjustItem {
        /** 股票代码 */
        private String stockCode;
    }
}
