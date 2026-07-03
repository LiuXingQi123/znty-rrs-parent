package com.znty.rrs.entity.crmwpooladjust;

import lombok.Data;

/**
 * 投资池当前状态返回对象
 */
@Data
public class PoolStatusDto {
    /** 投资池 ID */
    private Long targetPoolId;
    /** 投资池名称 */
    private String poolName;
    /** 池类型 */
    private String poolType;
    /** 调库批次号 */
    private String adjustBatchNo;
    /** 来源调库日志 ID */
    private Long adjustLogId;
    /** 入池日期 */
    private String entryDate;
    /** 主体在该池证券数（仅主体所在池使用） */
    private Integer bondCount;
}
