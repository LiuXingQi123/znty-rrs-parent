package com.znty.rrs.entity.common;

import lombok.Data;

import java.util.List;

/**
 * 公共查询请求对象
 */
@Data
public class CommonReq {
    /** 包含的投资池类型列表 */
    private List<String> includePoolTypes;

    /** 排除的投资池类型列表 */
    private List<String> excludePoolTypes;

    /** 当前用户 ID */
    private String currentUserId;

    /** 权限类型，不传时不按权限过滤 */
    private String permissionType;
}
