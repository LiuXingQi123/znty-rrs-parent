package com.znty.rrs.common.enums;

/** 审计事件操作类型（对应各 _evt 表.oprt_type，中英文两套并存） */
public enum EventType {
    /** 新增（英文，流程模块审计） */
    INSERT("INSERT"),
    /** 修改（英文，流程模块审计） */
    UPDATE("UPDATE"),
    /** 删除（英文，流程模块审计） */
    DELETE("DELETE"),
    /** 新增（中文，投资池审计） */
    ADD_CN("新增"),
    /** 修改（中文，投资池审计） */
    EDIT_CN("修改"),
    /** 删除（中文，投资池审计） */
    DELETE_CN("删除"),
    /** 审核（中文，投资池审计） */
    AUDIT_CN("审核");

    /** 枚举 code 值 */
    private final String code;

    EventType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
