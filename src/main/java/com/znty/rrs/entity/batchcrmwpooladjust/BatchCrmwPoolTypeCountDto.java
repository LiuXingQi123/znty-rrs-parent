package com.znty.rrs.entity.batchcrmwpooladjust;

import lombok.Data;

/**
 * CRMW 池批量调整在池数量分项
 */
@Data
public class BatchCrmwPoolTypeCountDto {

    /** 投资池 ID */
    private Long poolId;

    /** 类型编码，CRMW 池固定 crmw */
    private String typeCode;

    /** 在池组合数量 */
    private Integer count;
}
