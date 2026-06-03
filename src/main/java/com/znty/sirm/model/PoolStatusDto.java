package com.znty.sirm.model;

import lombok.Data;

/**
 * 投资池当前状态返回对象
 */
@Data
public class PoolStatusDto {
    /** 投资池名称 */
    private String poolName;
    /** 池类型 */
    private String poolType;
    /** 入池日期 */
    private String entryDate;
    /** 主体在该池债券数（仅主体所在池使用） */
    private Integer bondCount;
    /** 状态 */
    private String status;
}
