package com.znty.sirm.entity.securitypoolquery;

import lombok.Data;

/**
 * 我的证券池操作请求对象，用于添加或移除用户自选证券池中的证券
 */
@Data
public class MySecurityPoolReq {
    /** 证券代码 */
    private String securityCode;
    /** 证券类型 */
    private String securityType;
    /** 证券市场 */
    private String market;
    /** 用户 ID */
    private String userId;
}
