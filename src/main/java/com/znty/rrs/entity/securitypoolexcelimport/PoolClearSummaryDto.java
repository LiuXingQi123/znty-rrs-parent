package com.znty.rrs.entity.securitypoolexcelimport;

import lombok.Data;

/** 清空目标池的在池成员统计 */
@Data
public class PoolClearSummaryDto {

    /** 叶子池 ID */
    private Long id;
    /** 叶子池名称 */
    private String poolName;
    /** 在池主体数量 */
    private Long companyCount;
    /** 在池债券数量 */
    private Long bondCount;
}
