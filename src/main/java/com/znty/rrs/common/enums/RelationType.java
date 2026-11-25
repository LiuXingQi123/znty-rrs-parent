package com.znty.rrs.common.enums;

/**
 * 池关系类型
 *
 * 对应 {@code ip_pool_relation.relation_type}，区分来源、限制、联动、互斥及弹性禁投关系
 */
public enum RelationType {

    /** 来源池 */
    SOURCE("source"),

    /** 调入限制池 */
    IN_RESTRICT("in_restrict"),

    /** 调出限制池 */
    OUT_RESTRICT("out_restrict"),

    /** 调入联动池 */
    IN_LINKED("in_linked"),

    /** 调出联动池 */
    OUT_LINKED("out_linked"),

    /** 调入互斥池 */
    IN_MUTEX("in_mutex"),

    /** 调出互斥池 */
    OUT_MUTEX("out_mutex"),

    /** 调入弹性禁投池 */
    IN_SOFT_RESTRICT("in_soft_restrict"),

    /** 调出弹性禁投池 */
    OUT_SOFT_RESTRICT("out_soft_restrict");

    /** 池关系类型编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的池关系类型编码
     */
    RelationType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的池关系类型编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
