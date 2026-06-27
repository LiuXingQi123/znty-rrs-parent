package com.znty.sirm.common.enums;

/** 调库项标签（item_tag 业务字段） */
public enum ItemType {
    /** 手工项 */
    MANUAL("manual"),
    /** 联动项 */
    LINKAGE("linkage"),
    /** 互斥项 */
    MUTEX("mutex");

    /** 枚举 code 值 */
    private final String code;

    ItemType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
