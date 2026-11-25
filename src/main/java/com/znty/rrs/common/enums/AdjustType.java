package com.znty.rrs.common.enums;

/**
 * 调整类型
 *
 * 调库日志和池状态使用本编码记录调整来源或触发方式，对应 {@code ip_adjust_log.adjust_type} 和 {@code ip_pool_status.adjust_type}
 */
public enum AdjustType {

    /**
     * 手工调整
     */
    MANUAL("手工调整"),

    /**
     * 联动调整
     */
    LINKAGE("联动调整"),

    /**
     * 互斥调整
     */
    MUTEX("互斥调整"),

    /**
     * 关联调整
     */
    RELATED("关联调整"),

    /**
     * Excel 导入
     */
    EXCEL_IMPORT("Excel导入"),

    /**
     * Excel 清空
     */
    EXCEL_CLEAR("Excel清空"),

    /**
     * 清空目标池时生成的出库调整
     */
    CLEAR_POOL("清空目标池"),

    /**
     * 手动批量调整
     */
    MANUAL_BATCH("手动批量调整"),

    /**
     * 自动调整
     */
    AUTO("自动调整"),

    /**
     * 临时代码调出
     */
    TEMP_CODE_OUT("临时代码调出"),

    /**
     * Wind 代码变更调出
     */
    WIND_CODE_CHANGE_OUT("Wind代码变更调出");

    /**
     * 调整类型编码
     */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的调整类型编码
     */
    AdjustType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的调整类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
