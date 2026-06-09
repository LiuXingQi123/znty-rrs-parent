package com.znty.sirm.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证券信息列表返回对象（证券查询列表用）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityInfoDto extends SecurityInfoBo {

    /** 证券类型名称 */
    private String securityTypeName;
}
