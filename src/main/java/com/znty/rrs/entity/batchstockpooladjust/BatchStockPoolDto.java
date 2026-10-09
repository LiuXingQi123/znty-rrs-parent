package com.znty.rrs.entity.batchstockpooladjust;

import lombok.Data;

/** 股票池批量调整目标池列表 */
@Data
public class BatchStockPoolDto {
    /** 投资池 ID */
    private Long id;
    /** 投资池名称 */
    private String poolName;
    /** 投资池全路径名称 */
    private String poolFullName;
    /** 投资池类型 */
    private String poolType;
    /** 投资市场编码 JSON */
    private String marketCodes;
    /** 投资品种编码 JSON */
    private String varietyCodes;
    /** 投资池描述 */
    private String description;
    /** 投资池容量上限 */
    private Long maxCapacity;
    /** 当前有效在池股票数量 */
    private Integer currentCount;
}
