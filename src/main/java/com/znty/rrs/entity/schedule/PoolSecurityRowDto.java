package com.znty.rrs.entity.schedule;

import lombok.Data;

/**
 * 池生效状态聚合行，按池装载主体代码或 ABS 代码，供对账任务在内存中做差集。
 */
@Data
public class PoolSecurityRowDto {

    /** 目标池 ID */
    private Long poolId;

    /** 已生效的证券或主体代码 */
    private String securityCode;
}
