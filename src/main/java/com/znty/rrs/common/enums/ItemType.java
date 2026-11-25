package com.znty.rrs.common.enums;

/**
 * 调库项标签
 *
 * 通过业务字段 {@code item_tag} 区分手工、联动、互斥、多市场关联及清空目标池产生的调整项
 */
public enum ItemType {

    /** 手工项 */
    MANUAL("manual"),

    /** 联动项 */
    LINKAGE("linkage"),

    /** 互斥项 */
    MUTEX("mutex"),

    /** 多市场关联码项 */
    RELATED("related"),

    /** Excel 导入「首先清空目标池」产生的出库项 */
    CLEAR("clear");

    /** 调库项标签编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的调库项标签编码
     */
    ItemType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的调库项标签编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
