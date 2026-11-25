package com.znty.rrs.common.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 产品库入库使用的池编码
 *
 * 产品库快速入库和存量批量调整使用同一份来源池名单
 */
public final class ProductInboundPoolCodes {

    /** 存量债券产品库根节点编码，用于识别产品库子池 */
    public static final String ROOT = "bond_product_root";

    /** CRMW 来源池编码，用于识别需要核对在池状态的 CRMW 库 */
    public static final String CRMW = "crmw_root";

    /**
     * 产品库快速入库允许使用的来源池编码名单
     *
     * 按存量批量入口的顺序列出 CRMW 库、信用债一至三级库、转债核心库和转债重点库
     */
    public static final List<String> SOURCES = Collections.unmodifiableList(Arrays.asList(
            CRMW, CreditBondPoolCodes.LEVEL_1, CreditBondPoolCodes.LEVEL_2,
            CreditBondPoolCodes.LEVEL_3, "convertible_bond_core", "convertible_bond_focus"));

    /** 禁止实例化常量类 */
    private ProductInboundPoolCodes() {
    }
}
