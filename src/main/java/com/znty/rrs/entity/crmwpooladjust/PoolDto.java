package com.znty.rrs.entity.crmwpooladjust;

import lombok.Data;
import java.util.List;

/**
 * 投资池返回对象（调库页面可调入/可调出投资池列表）
 */
@Data
public class PoolDto {
    /** 投资池 ID */
    private Long id;
    /** 父级 ID */
    private Long parentId;
    /** 投资池名称 */
    private String poolName;
    /** 投资池编码 */
    private String poolCode;
    /** 投资池类型 */
    private String poolType;
    /** 投资池层级 */
    private Integer poolLevel;
    /** 上限数量 */
    private Long maxCapacity;
    /** 现有数量（占位，当前无 bond-pool 映射表） */
    private Integer currentCount;
    /** 调入互斥池 ID 列表 */
    private List<Long> inMutexPoolIds;
    /** 调出互斥池 ID 列表 */
    private List<Long> outMutexPoolIds;
}
