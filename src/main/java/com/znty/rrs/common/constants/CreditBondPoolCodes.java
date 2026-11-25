package com.znty.rrs.common.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 信用债大库下级池编码
 *
 * 选池时仅信用债大库下的一至五级库允许自动回填最近信评报告，与老系统 {@code COMMON.polidEnum} 的名单一致
 * 使用各环境一致的池编码匹配，避免池 ID 不同影响判断
 */
public final class CreditBondPoolCodes {

    /** 禁止实例化常量类 */
    private CreditBondPoolCodes() {
    }

    /** 信用债一级库编码 */
    public static final String LEVEL_1 = "credit_bond_level_1";

    /** 信用债二级库编码 */
    public static final String LEVEL_2 = "credit_bond_level_2";

    /** 信用债三级库编码 */
    public static final String LEVEL_3 = "credit_bond_level_3";

    /** 信用债四级库编码 */
    public static final String LEVEL_4 = "credit_bond_level_4";

    /** 信用债五级库编码 */
    public static final String LEVEL_5 = "credit_bond_level_5";

    /** 允许自动回填最近信评报告的池编码集合，包含信用债一至五级库 */
    public static final Set<String> LAST_CREDIT_REPORT_AUTOFILL_POOL_CODES =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                    LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5
            )));

    /**
     * 按池编码判断选池时能否自动回填最近信评报告
     *
     * @param poolCode 投资池编码
     * @return true=池编码在回填名单中，false=池编码为空或不在名单中
     */
    public static boolean isLastCreditReportAutofillPool(String poolCode) {
        return poolCode != null && LAST_CREDIT_REPORT_AUTOFILL_POOL_CODES.contains(poolCode);
    }
}
