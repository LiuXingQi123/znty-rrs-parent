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

    /**
     * 仅包含指定根池编码及其子孙（如 credit_bond_root、bond_product_root）。
     * 有值时从这些根节点起建树，不再从全部 parent_id IS NULL 的根展开。
     */
    private List<String> includeRootPoolCodes;

    /** 当前用户 ID */
    private String currentUserId;

    /** 权限类型，不传时不按权限过滤 */
    private String permissionType;
}
