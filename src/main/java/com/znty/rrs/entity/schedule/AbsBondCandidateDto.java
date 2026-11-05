package com.znty.rrs.entity.schedule;

import lombok.Data;

/**
 * ABS 债券主档候选，供「主体下ABS债券自动入库」一次装载并在内存中对账。
 *
 * <p>只承载对账所需的只读字段，与 {@link ScheduledAdjustCandidateDto} 区分：
 * 后者继承调库日志对象，字段会被落库使用；本对象仅用于计算差集。
 */
@Data
public class AbsBondCandidateDto {

    /** 证券代码（Wind 代码） */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 证券类型编码 */
    private String securityType;

    /** ABS 标志：1=是 */
    private Integer absFlag;

    /** 主档发行主体代码；已确认身份时必有值 */
    private String issuerCode;

    /** 主档发行主体名称 */
    private String issuerName;

    /** 到期日，格式 yyyyMMdd */
    private String maturityDate;

    /** 主档市场编码，用于池 market_codes 准入判断 */
    private String market;
}
