package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 证券池调库请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityPoolAdjustReq extends PageRequest {
    /** 证券代码（模糊搜索） */
    private String securityCode;
    /** 证券简称（模糊搜索） */
    private String securityShortName;
    /** 发行人（模糊搜索） */
    private String issuer;
    /** 证券 ID（查看详情用） */
    private Long securityId;
    /** 调库方向：in=可调入库 / out=可调出库 */
    private String adjustDirection;
}
