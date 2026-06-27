package com.znty.sirm.common.enums;

/** 投资池权限类型（对应 ip_pool_permission.permission_type） */
public enum PermissionType {
    /** 可查看 */
    VIEWABLE("viewable"),
    /** 可调整 */
    ADJUSTABLE("adjustable"),
    /** 可 Excel 导入 */
    EXCEL_IMPORTABLE("excel_importable");

    /** 枚举 code 值 */
    private final String code;

    PermissionType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
