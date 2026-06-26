package com.znty.sirm.entity.common;

import lombok.Data;

/**
 * 人员返回对象（投资池权限选择用）
 */
@Data
public class UserDto {

    /** 主键 ID */
    private Long id;

    /** 人员姓名 */
    private String userName;

    /** 角色名称拼接（逗号分隔） */
    private String roleName;
}
