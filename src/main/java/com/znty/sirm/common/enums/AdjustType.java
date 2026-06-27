package com.znty.sirm.common.enums;

/** 调整类型（对应 ip_adjust_log.adjust_type / ip_pool_status.adjust_type） */
public enum AdjustType {
    /** 手工调整 */
    MANUAL("手工调整"),
    /** 联动调整 */
    LINKAGE("联动调整"),
    /** 互斥调整 */
    MUTEX("互斥调整"),
    /** 关联调整 */
    RELATED("关联调整"),
    /** Excel 导入 */
    EXCEL_IMPORT("Excel导入"),
    /** 手动批量调整 */
    MANUAL_BATCH("手动批量调整");

    /** 枚举 code 值 */
    private final String code;

    AdjustType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
