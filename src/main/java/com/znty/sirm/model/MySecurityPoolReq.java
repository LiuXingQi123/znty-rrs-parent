package com.znty.sirm.model;

import lombok.Data;

/**
 * 我的证券池请求对象（添加/移除）
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
