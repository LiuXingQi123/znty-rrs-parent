package com.znty.rrs.service;

import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 校验规则管理 Demo 脚本与 checkAdjust 口径一致，且可被 QLExpress 执行。
 */
public class RuleDemoScriptTest {

    /** 与 Demo 调库规则相同的前置校验。 */
    private static final String ENTRY =
            "if (securityCode == null || securityCode == \"\") {\n"
                    + "    return \"证券代码不能为空\";\n"
                    + "}\n"
                    + "if (targetPoolId == null || targetPoolId == \"\") {\n"
                    + "    return \"调库项不能为空\";\n"
                    + "}\n"
                    + "if (securityExists == \"否\") {\n"
                    + "    return \"证券不存在\";\n"
                    + "}\n"
                    + "if (poolExists == \"否\") {\n"
                    + "    return \"目标投资池不存在\";\n"
                    + "}\n";

    /**
     * 执行 QLExpress 脚本。
     *
     * @param script 脚本
     * @param kv     上下文键值对
     * @return 执行结果
     */
    private Object run(String script, Object... kv) throws Exception {
        DefaultContext<String, Object> ctx = new DefaultContext<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            ctx.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return new ExpressRunner().execute(script, ctx, null, true, false);
    }

    /** 空参数与 checkAdjust 前置一致，不能返回通过 */
    @Test
    public void emptyParamsFailLikeCheckAdjust() throws Exception {
        String script = ENTRY
                + "if ((lockFlag == \"是\" || lockFlag == \"1\" || lockFlag == 1)) {\n"
                + "    return \"目标投资池已锁定\";\n"
                + "}\n"
                + "return \"通过\";\n";
        assertEquals("证券代码不能为空", run(script, "securityCode", "", "targetPoolId", "",
                "securityExists", "", "poolExists", "", "lockFlag", ""));
        assertEquals("调库项不能为空", run(script, "securityCode", "138026.SH", "targetPoolId", "",
                "securityExists", "是", "poolExists", "是", "lockFlag", "否"));
        assertEquals("证券不存在", run(script, "securityCode", "NO_SUCH", "targetPoolId", "1",
                "securityExists", "否", "poolExists", "是", "lockFlag", "否"));
        assertEquals("目标投资池不存在", run(script, "securityCode", "138026.SH", "targetPoolId", "9",
                "securityExists", "是", "poolExists", "否", "lockFlag", "否"));
    }

    /** 调入-池锁定：与 inCheckPoolLocked 失败文案一致，lock_flag=1 与「是」均可 */
    @Test
    public void inCheckPoolLockedMessages() throws Exception {
        String script = ENTRY
                + "if ((lockFlag == \"是\" || lockFlag == \"1\" || lockFlag == 1)) {\n"
                + "    return \"目标投资池已锁定\";\n"
                + "}\n"
                + "return \"通过\";\n";
        assertEquals("通过", run(script, "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是", "lockFlag", "否"));
        assertEquals("目标投资池已锁定", run(script, "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是", "lockFlag", "是"));
        assertEquals("目标投资池已锁定", run(script, "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是", "lockFlag", "1"));
        assertEquals("目标投资池已锁定", run(script, "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是", "lockFlag", 1L));
    }

    /** 投资品种：按 JSON 带引号 token 匹配，对齐 inCheckVariety */
    @Test
    public void inCheckVarietyQuotedJsonToken() throws Exception {
        String script = ENTRY
                + "if (varietyCodes == null || varietyCodes == \"\" || varietyCodes == \"[]\") {\n"
                + "    return \"通过\";\n"
                + "}\n"
                + "if (categoryType == null || categoryType == \"\") {\n"
                + "    return \"证券不在本池投资品种范围内\";\n"
                + "}\n"
                + "token = \"\\\"\" + categoryType + \"\\\"\";\n"
                + "if (varietyCodes.indexOf(token) >= 0) {\n"
                + "    return \"通过\";\n"
                + "}\n"
                + "return \"证券不在本池投资品种范围内\";\n";
        assertEquals("通过", run(script, "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是",
                "varietyCodes", "[\"bond\"]", "categoryType", "bond"));
        assertEquals("证券不在本池投资品种范围内", run(script, "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是",
                "varietyCodes", "[\"bond\"]", "categoryType", "stock"));
    }

    /** ABS：担保人内评 1 档只能调入一级库，文案对齐 describeRange */
    @Test
    public void absGradeOneOnlyAllowsLevelOne() throws Exception {
        String script = ENTRY
                + "if (!(isAbs == \"是\" || isAbs == \"1\" || isAbs == 1)) {\n"
                + "    return \"通过\";\n"
                + "}\n"
                + "if (innerGuarantorRating == \"1\") {\n"
                + "    startSort = 1;\n"
                + "    poolName = targetPoolName;\n"
                + "    if (poolName == null || poolName == \"\") {\n"
                + "        poolName = targetPoolId;\n"
                + "    }\n"
                + "    if (startSort < 1) {\n"
                + "        startSort = 1;\n"
                + "    }\n"
                + "    if (startSort > 5) {\n"
                + "        startSort = 5;\n"
                + "    }\n"
                + "    if (targetPoolLevel == startSort) {\n"
                + "        return \"通过\";\n"
                + "    }\n"
                + "    return \"目标池「\" + poolName + \"」不在特殊债调整后的允许范围内（仅 \" + startSort + \" 级）\";\n"
                + "}\n"
                + "startSort = matrixBestLevel + 1;\n"
                + "poolName = targetPoolName;\n"
                + "if (poolName == null || poolName == \"\") {\n"
                + "    poolName = targetPoolId;\n"
                + "}\n"
                + "if (startSort < 1) {\n"
                + "    startSort = 1;\n"
                + "}\n"
                + "if (startSort > 5) {\n"
                + "    startSort = 5;\n"
                + "}\n"
                + "if (targetPoolLevel >= startSort) {\n"
                + "    return \"通过\";\n"
                + "}\n"
                + "return \"目标池「\" + poolName + \"」不在特殊债调整后的允许范围内（仅 \" + startSort + \" 级及更差）\";\n";
        assertEquals("通过", run(script,
                "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是",
                "isAbs", "是", "innerGuarantorRating", "1",
                "matrixBestLevel", 1L, "targetPoolLevel", 1L, "targetPoolName", "一级库"));
        assertEquals("目标池「二级库」不在特殊债调整后的允许范围内（仅 1 级）",
                run(script,
                        "securityCode", "138026.SH", "targetPoolId", "2",
                        "securityExists", "是", "poolExists", "是",
                        "isAbs", "是", "innerGuarantorRating", "1",
                        "matrixBestLevel", 1L, "targetPoolLevel", 2L, "targetPoolName", "二级库"));
    }

    /** 当前白名单池未配置时固定不命中，对齐 isWhitelistFlowMatched */
    @Test
    public void whitelistUnconfiguredNeverMatches() throws Exception {
        String script = ENTRY
                + "unmatch = \"\";\n"
                + "if (remainDays == null || remainDays == \"\") {\n"
                + "    unmatch = unmatch + \"剩余期限无法解析，date_exists 为空;\";\n"
                + "} else {\n"
                + "    if (remainDays < 0) {\n"
                + "        unmatch = unmatch + \"剩余期限已小于 0 天;\";\n"
                + "    } else {\n"
                + "        if (remainDays > 1095) {\n"
                + "            unmatch = unmatch + \"剩余期限超过 3 年;\";\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "if ((isAbs == \"是\" || isAbs == \"1\" || isAbs == 1)) {\n"
                + "    unmatch = unmatch + \"债券为 ABS 债，不符合白名单条件;\";\n"
                + "}\n"
                + "if (!(isBond == \"是\" || isBond == \"1\" || isBond == 1)) {\n"
                + "    unmatch = unmatch + \"债券类型不属于债券类;\";\n"
                + "}\n"
                + "if (!(whitelistPoolConfigured == \"是\" || whitelistPoolConfigured == \"1\" || whitelistPoolConfigured == 1)) {\n"
                + "    unmatch = unmatch + \"白名单池未配置，主体在白名单池条件不成立;\";\n"
                + "}\n"
                + "if ((isGuaranteed == \"是\" || isGuaranteed == \"1\" || isGuaranteed == 1)) {\n"
                + "    unmatch = unmatch + \"债券为担保债，不符合白名单条件;\";\n"
                + "}\n"
                + "if (unmatch == \"\") {\n"
                + "    return \"命中白名单\";\n"
                + "}\n"
                + "return \"未命中:\" + unmatch;\n";
        assertEquals("未命中:白名单池未配置，主体在白名单池条件不成立;", run(script,
                "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是",
                "remainDays", 800L, "isAbs", "否", "isBond", "是",
                "whitelistPoolConfigured", "否", "isGuaranteed", "否"));
        assertEquals("未命中:债券为 ABS 债，不符合白名单条件;", run(script,
                "securityCode", "138026.SH", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是",
                "remainDays", 800L, "isAbs", "是", "isBond", "是",
                "whitelistPoolConfigured", "是", "isGuaranteed", "否"));
    }

    /** 自动入池：AA-及以下才入，AAA 不入；空主体代码失败 */
    @Test
    public void autoInAaMinusAndBelow() throws Exception {
        String script = "if (issuerCode == null || issuerCode == \"\") {\n"
                + "    return \"主体代码不能为空\";\n"
                + "}\n"
                + "if (issuerRating == null || issuerRating == \"\") {\n"
                + "    return \"主体外评不能为空\";\n"
                + "}\n"
                + "if ((alreadyInTargetPool == \"是\" || alreadyInTargetPool == \"1\" || alreadyInTargetPool == 1)) {\n"
                + "    return \"通过\";\n"
                + "}\n"
                + "lowList = \",AA-,A+,A,A-,BBB+,BBB,BBB-,BB+,BB,BB-,B+,B,B-,CCC,CC,C,\";\n"
                + "if (lowList.indexOf(\",\" + issuerRating + \",\") >= 0) {\n"
                + "    return \"外评AA-及以下主体自动入池\";\n"
                + "}\n"
                + "return \"通过\";\n";
        assertEquals("主体代码不能为空", run(script, "issuerCode", "", "issuerRating", "", "alreadyInTargetPool", ""));
        assertEquals("外评AA-及以下主体自动入池", run(script, "issuerCode", "C1", "issuerRating", "AA-", "alreadyInTargetPool", "否"));
        assertEquals("外评AA-及以下主体自动入池", run(script, "issuerCode", "C1", "issuerRating", "A+", "alreadyInTargetPool", "否"));
        assertEquals("通过", run(script, "issuerCode", "C1", "issuerRating", "AAA", "alreadyInTargetPool", "否"));
    }

    /** 基金评分未传时失败，文案对齐 inCheckFundRate */
    @Test
    public void fundRateMissingFails() throws Exception {
        String script = ENTRY
                + "if (fundRateLimit == null || fundRateLimit == \"\") {\n"
                + "    return \"通过\";\n"
                + "}\n"
                + "rate = fundRateLimit.replace(\" \", \"\");\n"
                + "msg = poolName + \"的评分，必须在\" + rate.replace(\"#rate\", \"基金评分\");\n"
                + "if (fundRate == null || fundRate == \"\") {\n"
                + "    return msg;\n"
                + "}\n"
                + "if (minRate != null && minRate != \"\" && fundRate < minRate) {\n"
                + "    return msg;\n"
                + "}\n"
                + "if (maxRate != null && maxRate != \"\" && fundRate > maxRate) {\n"
                + "    return msg;\n"
                + "}\n"
                + "return \"通过\";\n";
        assertEquals("基金库的评分，必须在3<=基金评分<=8", run(script,
                "securityCode", "F1", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是",
                "fundRateLimit", "3<=#rate<=8", "poolName", "基金库",
                "fundRate", "", "minRate", 3L, "maxRate", 8L));
        assertEquals("通过", run(script,
                "securityCode", "F1", "targetPoolId", "1",
                "securityExists", "是", "poolExists", "是",
                "fundRateLimit", "3<=#rate<=8", "poolName", "基金库",
                "fundRate", 5L, "minRate", 3L, "maxRate", 8L));
    }
}
