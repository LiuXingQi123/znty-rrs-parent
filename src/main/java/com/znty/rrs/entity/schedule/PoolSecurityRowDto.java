package com.znty.rrs.entity.schedule;

import lombok.Data;

/**
 * 定时任务批量读取的生效池状态，包含池 ID 和证券或主体代码
 */
@Data
public class PoolSecurityRowDto {

    /** 目标池 ID */
    private Long poolId;

    /** 已生效的证券或主体代码 */
    private String securityCode;

    /** 在池记录 ID */
    private Long poolStatusId;
}
