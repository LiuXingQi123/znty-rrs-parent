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

    /** 投资品种编码，节点 variety_codes 包含其中任一项即保留，并带上祖先节点 */
    private List<String> includeVarietyCodes;

    /** 投资池编码，仅保留这些池；若命中子节点则同时保留其祖先 */
    private List<String> includePoolCodes;

    /** 当前用户 ID */
    private String currentUserId;

    /** 权限类型，不传时不按权限过滤 */
    private String permissionType;
}
