package com.znty.rrs.entity.securitypoolquery;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 证券池查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityPoolQueryReq extends PageRequest {
    /** 证券池树选中节点ID列表 */
    private List<Long> poolIds;
    /** 证券代码（模糊搜索） */
    private String securityCode;
    /** 证券简称（模糊搜索） */
    private String securityShortName;
    /** 证券类型（精确匹配） */
    private String securityType;
    /** 证券状态：active=存续 / matured=到期 */
    private String securityStatus;
    /** 入池时间起 */
    private String entryTimeStart;
    /** 入池时间止 */
    private String entryTimeEnd;
    /** 调整人（模糊搜索） */
    private String adjusterName;
    /** 发行主体名称（模糊搜索） */
    private String issuer;
    /** 我的证券 */
    private Boolean mySecurities;
    /** 当前用户ID（我的证券勾选时使用） */
    private String currentUserId;
}
