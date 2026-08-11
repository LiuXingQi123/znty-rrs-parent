package com.znty.rrs.common.enums;

/**
 * 审计事件操作类型（对应各 _evt 表.oprt_type）
 * <p>
 * 统一存英文：INSERT=新增 / UPDATE=修改 / DELETE=删除。中文展示由前端或字典映射，不入库。
 * </p>
 */
public enum EventType {
    /** 新增 */
    INSERT("INSERT"),
    /** 修改 */
    UPDATE("UPDATE"),
    /** 删除 */
    DELETE("DELETE");

    /** 枚举 code 值 */
    private final String code;

    EventType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return this.code;
    }
}
