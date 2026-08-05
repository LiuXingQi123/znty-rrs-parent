package com.znty.rrs.entity.stocksecuritybatchadjust;

import lombok.Data;

import java.util.List;

/**
 * 存量证券批量调整投资池列表返回对象
 */
@Data
public class StockSecurityBatchPoolDto {

    /** 投资池 ID */
    private Long id;

    /** 投资池名称 */
    private String poolName;

    /** 投资池全路径名称 */
    private String poolFullName;

    /** 投资池类型 */
    private String poolType;

    /**
     * 投资市场编码 JSON：
     * SSE=上海证券交易所 / SZSE=深圳证券交易所 / CIBM=银行间市场 / BSE=北京证券交易所 /
     * COMPANY=主体 / OTC=场外市场 / QDII=其他QDII市场 / JWCW=JWCW市场 / UNKNOWN=未知 / OTHER=其他
     */
    private String marketCodes;

    /** 投资品种编码 JSON */
    private String varietyCodes;

    /** 投资池描述 */
    private String description;

    /** 上限数量 */
    private Long maxCapacity;

    /**
     * 当前有效在池总数（各类型合计，含主体 / CRMW 等）。
     */
    private Integer currentCount;

    /**
     * 按类型分项的在池数量（仅 count 大于 0 的项）。
     * typeCode：company / crmw / bond / stock / fund 等，中文由前端展示。
     */
    private List<StockPoolTypeCountDto> countByType;

    /** 批量调入流程 ID */
    private Long batchInFlowId;

    /** 批量调入流程 Key */
    private String batchInFlowKey;

    /** 批量调入流程名称 */
    private String batchInFlowName;

    /** 批量调出流程 ID */
    private Long batchOutFlowId;

    /** 批量调出流程 Key */
    private String batchOutFlowKey;

    /** 批量调出流程名称 */
    private String batchOutFlowName;
}
