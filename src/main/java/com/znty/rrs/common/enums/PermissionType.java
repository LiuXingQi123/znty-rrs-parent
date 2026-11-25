package com.znty.rrs.common.enums;

/**
 * 投资池权限类型
 *
 * 对应 {@code ip_pool_permission.permission_type}，区分查看、调整和 Excel 导入权限
 */
public enum PermissionType {

    /**
     * 可查看
     */
    VIEWABLE("viewable"),

    /**
     * 可调整
     */
    ADJUSTABLE("adjustable"),

    /**
     * 可 Excel 导入
     */
    EXCEL_IMPORTABLE("excel_importable");

    /**
     * 投资池权限类型编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的投资池权限类型编码
     */
    PermissionType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的投资池权限类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
