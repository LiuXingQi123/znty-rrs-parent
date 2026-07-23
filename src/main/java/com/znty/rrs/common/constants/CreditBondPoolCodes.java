package com.znty.rrs.common.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 信用债大库下级池编码常量。
 *
 * <p>对齐老系统枚举 {@code COMMON.polidEnum} 白名单：仅这些池允许「勾选/选池阶段自动回填最近信评报告」。
 * 使用 pool_code 写死，避免跨环境 pool id 不一致；不含信用债大库根节点。
 */
public final class CreditBondPoolCodes {

    private CreditBondPoolCodes() {
    }

    /** 信用债一级库 */
    public static final String LEVEL_1 = "credit_bond_level_1";
    /** 信用债二级库 */
    public static final String LEVEL_2 = "credit_bond_level_2";
    /** 信用债三级库 */
    public static final String LEVEL_3 = "credit_bond_level_3";
    /** 信用债四级库 */
    public static final String LEVEL_4 = "credit_bond_level_4";
    /** 信用债五级库 */
    public static final String LEVEL_5 = "credit_bond_level_5";

    /**
     * 允许自动回填最近信评报告的池编码集合（信用债大库下 1～5 级）
     */
    public static final Set<String> LAST_CREDIT_REPORT_AUTOFILL_POOL_CODES =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5
            )));

    /**
     * 是否允许在该池自动回填最近信评报告（对齐老 polidEnum 门禁）
     *
     * @param poolCode 投资池编码
     * @return true=允许回填
     */
    public static boolean isLastCreditReportAutofillPool(String poolCode) {
        return poolCode != null && LAST_CREDIT_REPORT_AUTOFILL_POOL_CODES.contains(poolCode);
    }
}
