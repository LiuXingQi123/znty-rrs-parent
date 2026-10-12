package com.znty.rrs.entity.common;

import lombok.Data;

/** Wind 与临时代码登记的原始市场映射。 */
@Data
public class SecurityMarketMappingDto {
    /** 当前 Wind 代码。 */
    private String windCode;
    /** 外部市场编码或临时登记市场。 */
    private String rawMarketCode;
    /** 证券来源。 */
    private String securitySource;
}
