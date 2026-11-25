package com.znty.rrs.common.enums;

/**
 * 入库材料豁免依据
 *
 * 区分信用债简易入库条件和产品库当前来源资格
 */
public enum MaterialExemptionType {

    /** 信用债简易入库 */
    CREDIT_BOND_SIMPLE("creditBondSimple"),

    /** 产品库当前来源资格 */
    PRODUCT_SOURCE("productSource");

    /** 入库材料豁免依据编码 */
    private final String code;

    /**
     * 记录当前枚举项的编码
     *
     * @param code 当前枚举项使用的入库材料豁免依据编码
     */
    MaterialExemptionType(String code) {
        this.code = code;
    }

    /**
     * 获取当前枚举项对应的入库材料豁免依据编码
     *
     * @return 枚举项定义的固定编码
     */
    public String getCode() {
        return code;
    }
}
