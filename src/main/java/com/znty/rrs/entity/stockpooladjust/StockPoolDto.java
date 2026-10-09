package com.znty.rrs.entity.stockpooladjust;

import java.util.List;
import lombok.Data;

/** 股票调库可选投资池返回对象 */
@Data
public class StockPoolDto {
    /** 投资池 ID */
    private Long id;
    /** 父级投资池 ID */
    private Long parentId;
    /** 投资池名称 */
    private String poolName;
    /** 投资池编码 */
    private String poolCode;
    /** 投资池类型 */
    private String poolType;
    /** 投资池层级 */
    private Integer poolLevel;
    /** 最大容量 */
    private Long maxCapacity;
    /** 当前股票数 */
    private Integer currentCount;
    /** 调入互斥池 ID */
    private List<Long> inMutexPoolIds;
    /** 调出互斥池 ID */
    private List<Long> outMutexPoolIds;
}
