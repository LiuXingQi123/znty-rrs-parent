package com.znty.rrs.entity.common;

import lombok.Data;

/** 当前证券或同券关联证券的市场代码。 */
@Data
public class SecurityMarketDto {
    /** Wind 证券代码。 */
    private String windCode;
    /** 系统市场编码。 */
    private String marketCode;
}
