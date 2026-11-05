package com.znty.rrs.entity.schedule;

import lombok.Data;

/**
 * ABS 四类关联主体关系行，供「主体下ABS债券自动入库」一次性装载。
 *
 * <p>关系码取担保人、差额支付承诺人、债务主体、原始权益人四类，
 * 与 {@code AbsIdentityMapper} 中查询口径保持一致。
 */
@Data
public class AbsCompanyRelationDto {

    /** ABS 证券代码 */
    private String absCode;

    /** 关联主体代码 */
    private String companyCode;
}
