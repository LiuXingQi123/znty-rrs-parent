package com.znty.rrs.common.enums;

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
    /** Excel 清空 */
    EXCEL_CLEAR("Excel清空"),
    /** 手动批量调整 */
    MANUAL_BATCH("手动批量调整"),
    /** 自动调整 */
    AUTO("自动调整"),
    /** 临时代码调出 */
    TEMP_CODE_OUT("临时代码调出"),
    /** Wind 代码变更调出 */
    WIND_CODE_CHANGE_OUT("Wind代码变更调出");

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
