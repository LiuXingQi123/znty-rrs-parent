package com.znty.rrs.common.enums;

/** 临时代码操作类型（对应 rrs_temp_security_code.operation_type） */
public enum TempOperationType {
    /** 新增 */
    ADD("add"),
    /** 更新 */
    UPDATE("update"),
    /** 取消发行 */
    CANCEL_ISSUE("cancel_issue"),
    /** 删除 */
    DELETE("delete");

    /** 枚举 code 值 */
    private final String code;

    TempOperationType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
