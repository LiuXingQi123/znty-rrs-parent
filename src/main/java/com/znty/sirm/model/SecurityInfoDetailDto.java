package com.znty.sirm.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证券详情返回对象（调库页面顶部信息展示）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityInfoDetailDto extends SecurityInfoBo {

    /** 证券类型名称 */
    private String securityTypeName;
}
