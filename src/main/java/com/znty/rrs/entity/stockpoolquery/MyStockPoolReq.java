package com.znty.rrs.entity.stockpoolquery;

import lombok.Data;

/** 股票自选操作请求，品种及市场由基础数据核实。 */
@Data
public class MyStockPoolReq {
    /** 股票代码。 */
    private String stockCode;
    /** 当前用户 ID。 */
    private String currentUserId;
}
