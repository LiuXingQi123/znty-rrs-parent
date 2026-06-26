package com.znty.sirm.entity.batchsecuritypooladjust;

import lombok.Data;

/**
 * 证券池批量调整投资池列表返回对象
 */
@Data
public class BatchSecurityPoolDto {

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

    /** 上限数量 */
    private Long maxCapacity;

    /** 当前有效证券数量 */
    private Integer currentCount;
}
