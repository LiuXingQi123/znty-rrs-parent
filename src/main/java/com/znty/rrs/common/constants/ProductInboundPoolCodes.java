package com.znty.rrs.common.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 产品库快速入库与存量批量入口共用的池编码 */
public final class ProductInboundPoolCodes {

    /** 存量债券产品库根节点 */
    public static final String ROOT = "bond_product_root";

    /** 产品库快速入库允许的当前来源池，顺序与存量入口一致 */
    public static final List<String> SOURCES = Collections.unmodifiableList(Arrays.asList(
            "crmw_root", CreditBondPoolCodes.LEVEL_1, CreditBondPoolCodes.LEVEL_2,
            CreditBondPoolCodes.LEVEL_3, "convertible_bond_core", "convertible_bond_focus"));

    private ProductInboundPoolCodes() {
    }
}
