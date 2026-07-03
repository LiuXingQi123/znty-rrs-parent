package com.znty.rrs.entity.common;

import lombok.Data;

/**
 * 角色返回对象（投资池权限选择用）
 */
@Data
public class RoleDto {

    /** 主键 ID */
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 父级角色 ID */
    private Long parentId;

    /** 排序序号 */
    private Integer sortOrder;
}
