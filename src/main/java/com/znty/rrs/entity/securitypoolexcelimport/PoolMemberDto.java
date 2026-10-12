package com.znty.rrs.entity.securitypoolexcelimport;

import lombok.Data;

/**
 * 目标池当前有效在池成员（清空目标池用）
 */
@Data
public class PoolMemberDto {

    /** 证券/主体代码 */
    private String securityCode;
    /** 简称 */
    private String securityShortName;
    /** 证券类型 */
    private String securityType;
}
