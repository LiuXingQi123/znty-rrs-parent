package com.znty.rrs.common.enums;

/**
 * 审计事件操作类型
 *
 * 各 {@code _evt} 表的 {@code oprt_type} 统一保存英文编码，分别表示新增、修改和删除
 * 中文文案由前端或字典映射，数据库只保存编码
 */
public enum EventType {

    /** 新增 */
    INSERT("INSERT"),

    /** 修改 */
    UPDATE("UPDATE"),

    /** 删除 */
    DELETE("DELETE");

    /** 审计事件操作类型编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的审计事件操作类型编码
     */
    EventType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的审计事件操作类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return this.code;
    }
}
