package com.znty.rrs.common.enums;

/** 入库材料豁免依据 */
public enum MaterialExemptionType {
    /** 信用债简易入库 */
    CREDIT_BOND_SIMPLE("creditBondSimple"),
    /** 产品库当前来源资格 */
    PRODUCT_SOURCE("productSource");

    /** 依据编码 */
    private final String code;

    MaterialExemptionType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
