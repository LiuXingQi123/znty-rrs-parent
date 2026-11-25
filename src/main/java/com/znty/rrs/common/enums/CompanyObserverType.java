package com.znty.rrs.common.enums;

/**
 * AIS 主体观察者类型
 *
 * 使用 AIS 的原始编码区分空、观察和重点观察
 */
public enum CompanyObserverType {

    /** 空 */
    NONE("1"),

    /** 观察 */
    OBSERVE("2"),

    /** 重点观察 */
    KEY_OBSERVE("3");

    /** AIS 主体观察者类型编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的AIS 主体观察者类型编码
     */
    CompanyObserverType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的AIS 主体观察者类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
