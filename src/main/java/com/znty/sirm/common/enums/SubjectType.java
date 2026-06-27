package com.znty.sirm.common.enums;

/** 权限主体类型（对应 ip_pool_permission.subject_type） */
public enum SubjectType {
    /** 角色 */
    ROLE("role"),
    /** 人员 */
    USER("user");

    /** 枚举 code 值 */
    private final String code;

    SubjectType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
