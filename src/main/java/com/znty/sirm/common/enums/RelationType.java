package com.znty.sirm.common.enums;

/** 池关系类型（对应 ip_pool_relation.relation_type） */
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

    /** 枚举 code 值 */
    private final String code;

    RelationType(String code) {
        this.code = code;
    }

    /** 获取 code 值 */
    public String getCode() {
        return code;
    }
}
