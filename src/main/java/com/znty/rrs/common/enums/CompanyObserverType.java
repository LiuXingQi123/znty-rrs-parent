package com.znty.rrs.common.enums;

/** AIS 主体观察者类型。 */
public enum CompanyObserverType {
    /** 空 */
    NONE("1"),
    /** 观察 */
    OBSERVE("2"),
    /** 重点观察 */
    KEY_OBSERVE("3");

    /** 观察者类型编码 */
    private final String code;

    CompanyObserverType(String code) {
        this.code = code;
    }

    /** 获取观察者类型编码。 */
    public String getCode() {
        return code;
    }
}
