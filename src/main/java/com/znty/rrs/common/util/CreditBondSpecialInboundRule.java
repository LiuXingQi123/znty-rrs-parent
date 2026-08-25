package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;

/**
 * 信用债大库 1～5 级准入规则。
 *
 * <p>一只券可能同时是私募、次级、永续、担保。先压成<strong>一个</strong>类型标签，后写覆盖先写：
 * 私募 → 次级 → 永续 → 担保。例如永续+担保最终按担保处理。
 * ABS（{@code absFlag=1} 或证券类型 abs/abn）不看标签，始终走特殊债。</p>
 *
 * <p>特殊债口径（私募/永续/次级的「1 档」只看<strong>发债主体内评</strong> {@code innerIssuerRating}；
 * ABS 的「1 档」只看<strong>页面所选担保人内评</strong> {@code innerGuarantorRating}，无担保人则不是 1 档）：
 * <ul>
 *   <li>只能调入一级库：仅一级。</li>
 *   <li>下调一级：矩阵最好档再降一档，且只留那一档（最好已是五级则仍是五级）。</li>
 *   <li>至少下调一级：从「矩阵最好档再降一档」起一直开到五级（最好 1 则 2～5）。</li>
 * </ul>
 * ABS：担保人内评 1 档只能调入一级库，无担保人或非 1 档至少下调一级。
 * 私募：发债主体内评 1 档只能调入一级库，其余至少下调一级。
 * 永续：发债主体内评 1 档下调一级（矩阵最好档再降一档且只留那一档），其余至少下调一级。
 * 次级：发债主体内评 1 档只能调入一级库；2+/2/2- 下调一级（只留那一档）；其余至少下调一级。</p>
 *
 * <p>再按下面顺序选一种准入方式（先命中先走）：
 * <ol>
 *   <li>ABS，或标签仍是私募/次级/永续：按上面口径。</li>
 *   <li>标签是担保，或券/主体在观察池：入库不得高于矩阵最好档，从该档开到五级。担保查矩阵前先把主体内评和担保人内评取更好的一档（只影响查哪一格）。</li>
 *   <li>其余普通债：只能进矩阵单元格里的那些池，不开放更差档。</li>
 * </ol>
 * 含权债不改档位，只改期限（见 {@code CreditBondRemainTermUtil}）。
 * 可转债 / 可交换债 / 可分离转债 / CRMW 不能进信用债 1～5。
 * 重点观察名单不走上面的类型分支，单独 {@link #checkRestricted}：未在分级库不得新增；已在 1～4 级只能去五级或出库；
 * 有担保且担保人内评 1 档可豁免；CRMW 事先已排除分级库。
 * 境外债不走本规则。</p>
 */
public final class CreditBondSpecialInboundRule {

    /** 分级库最差档（五级库） */
    public static final int MAX_LEVEL = 5;

    /** 分级库最好档（一级库） */
    public static final int MIN_LEVEL = 1;

    /** 私募（覆盖顺序第 1 档） */
    public static final String MEMO_PRIVATE = "private";
    /** 次级（覆盖私募） */
    public static final String MEMO_SUBORDINATED = "subordinated";
    /** 永续（覆盖次级 / 私募） */
    public static final String MEMO_PERPETUAL = "perpetual";
    /** 担保（覆盖永续 / 次级 / 私募） */
    public static final String MEMO_GUARANTEED = "guaranteed";

    /**
     * 信用债 1～5 级准入方式。档位从哪一级起、是只留那一级还是开到五级，都由枚举自己判断。
     */
    public enum GradedInboundMode {
        /** 只能调入一级库：仅一级 */
        LEVEL_ONE_ONLY(true),
        /** 至少下调一级：矩阵最好档再降一档后，从该档开到五级 */
        DOWNGRADE_CEILING(false),
        /** 下调一级：矩阵最好档再降一档，且只留那一档 */
        DOWNGRADE_EXACT(true),
        /** 担保债或已在观察池：不得高于矩阵最好档，从该档开到五级 */
        BEST_AND_WORSE(false),
        /** 普通债：矩阵写了哪几个池就只能进那几个，不自动带上更差档 */
        EXACT_MATRIX(false);

        /** true=只留起始档那一级；false=起始档及更差 */
        private final boolean exactSort;

        GradedInboundMode(boolean exactSort) {
            this.exactSort = exactSort;
        }

        /**
         * 是否按档位判断叶子（不是只认矩阵点名的池 ID）。
         *
         * @return true=按 inner_sort 判断
         */
        public boolean usesSort() {
            return this != EXACT_MATRIX;
        }

        /**
         * 算出起始档。矩阵未命中或普通债返回 null。
         *
         * @param bestAllowedSort 矩阵允许池里最好的 inner_sort
         * @return 起始档 1～5；不适用为 null
         */
        public Integer resolveStartSort(Integer bestAllowedSort) {
            if (bestAllowedSort == null || this == EXACT_MATRIX) {
                return null;
            }
            if (this == LEVEL_ONE_ONLY) {
                return MIN_LEVEL;
            }
            if (this == DOWNGRADE_CEILING || this == DOWNGRADE_EXACT) {
                return clampLevel(bestAllowedSort + 1);
            }
            // 担保债 / 观察池：矩阵最好档本身（不得高于该档，开到五级）
            return clampLevel(bestAllowedSort);
        }

        /**
         * 这一级能不能按档位规则调入。
         *
         * @param poolSort 目标池 inner_sort
         * @param startSort 起始档
         * @return true=允许调入
         */
        public boolean allows(Integer poolSort, Integer startSort) {
            if (poolSort == null || startSort == null || !usesSort()) {
                return false;
            }
            int pool = poolSort;
            int start = startSort;
            if (exactSort) {
                return pool == start;
            }
            return pool >= start;
        }

        /**
         * 档位规则失败时的允许范围说明。
         *
         * @param startSort 起始档
         * @return 如「仅 1 级」或「仅 2 级及更差」
         */
        public String describeRange(Integer startSort) {
            if (exactSort) {
                return "仅 " + startSort + " 级";
            }
            return "仅 " + startSort + " 级及更差";
        }
    }

    /** 工具类禁止实例化 */
    private CreditBondSpecialInboundRule() {
    }

    /**
     * 是否私募债：发行方式或内部分类含「私募」。
     *
     * @param sec 证券主数据
     * @return true=私募债
     */
    public static boolean isPrivateBond(SecurityInfoBo sec) {
        if (sec == null) {
            return false;
        }
        // 发行方式、内部分类任一含「私募」即视为私募债
        return containsPrivate(sec.getIssueType()) || containsPrivate(sec.getInnerClass());
    }

    /**
     * 是否永续债。
     *
     * @param sec 证券主数据
     * @return true=永续债
     */
    public static boolean isPerpetual(SecurityInfoBo sec) {
        return sec != null && sec.getYxFlag() != null && sec.getYxFlag() == 1;
    }

    /**
     * 是否次级债。
     *
     * @param sec 证券主数据
     * @return true=次级债
     */
    public static boolean isSubordinated(SecurityInfoBo sec) {
        return sec != null && sec.getCjFlag() != null && sec.getCjFlag() == 1;
    }

    /**
     * 是否资产支持证券 / ABS（含 ABN）。
     *
     * @param sec 证券主数据
     * @return true=ABS
     */
    public static boolean isAbs(SecurityInfoBo sec) {
        if (sec == null) {
            return false;
        }
        if (sec.getAbsFlag() != null && sec.getAbsFlag() == 1) {
            return true;
        }
        String type = sec.getSecurityType();
        return "abs".equals(type) || "abn".equals(type);
    }

    /**
     * 可转债 / 可交换债 / 可分离转债 / CRMW：信用债 1～5 不适用。
     *
     * @param sec 证券主数据
     * @return true=选池去掉分级库，校验目标为分级库时失败
     */
    public static boolean isExcludedFromCreditBondGradedPool(SecurityInfoBo sec) {
        if (sec == null) {
            return false;
        }
        String type = sec.getSecurityType();
        return "convertible_bond".equals(type)
                || "exchangeable_bond".equals(type)
                || "detachable_convertible_bond".equals(type)
                || "crmw".equals(type);
    }

    /**
     * 是否担保债。
     *
     * @param sec 证券主数据
     * @return true=担保债
     */
    public static boolean isGuaranteed(SecurityInfoBo sec) {
        return sec != null && sec.getGuarantFlag() != null && sec.getGuarantFlag() == 1;
    }

    /**
     * 是否含权债。
     *
     * @param sec 证券主数据
     * @return true=含权债
     */
    public static boolean isInright(SecurityInfoBo sec) {
        return sec != null && sec.getInrightFlag() != null && sec.getInrightFlag() == 1;
    }

    /**
     * 强担保豁免：有担保且担保人内评档为 1。
     *
     * @param sec 证券主数据
     * @return true=强担保，重点观察名单可豁免
     */
    public static boolean isStrongGuarantee(SecurityInfoBo sec) {
        return isGuaranteed(sec) && isGradeOne(sec.getInnerGuarantorRating());
    }

    /**
     * 把并存的债券类型压成一个标签。后写覆盖先写：私募 → 次级 → 永续 → 担保。
     *
     * @param sec 证券主数据
     * @return private / subordinated / perpetual / guaranteed；皆无则 null
     */
    public static String resolveExclusiveMemo(SecurityInfoBo sec) {
        if (sec == null) {
            return null;
        }
        String memo = null;
        if (isPrivateBond(sec)) {
            memo = MEMO_PRIVATE;
        }
        if (isSubordinated(sec)) {
            memo = MEMO_SUBORDINATED;
        }
        if (isPerpetual(sec)) {
            memo = MEMO_PERPETUAL;
        }
        // 有担保则标签改成担保；ABS 仍靠 isAbs 单独判定，不受这里影响
        if (isGuaranteed(sec)) {
            memo = MEMO_GUARANTEED;
        }
        return memo;
    }

    /**
     * 是否按特殊债处理：ABS，或覆盖后的标签仍是私募 / 次级 / 永续。
     * 担保会覆盖永续等标签，因此永续+担保这里为 false（改走担保）。
     *
     * @param sec 证券主数据
     * @return true=特殊债
     */
    public static boolean isSpecialBranch(SecurityInfoBo sec) {
        if (isAbs(sec)) {
            return true;
        }
        String memo = resolveExclusiveMemo(sec);
        return MEMO_PRIVATE.equals(memo) || MEMO_SUBORDINATED.equals(memo) || MEMO_PERPETUAL.equals(memo);
    }

    /**
     * 解析准入方式。顺序：特殊债（ABS 或私募/次级/永续）→ 担保债或已在观察池 → 普通债只认矩阵点名的池。
     * 私募/永续/次级「1 档 / 2+/2/2-」只看发债主体内评；ABS「1 档」只看担保人内评（无担保人则不是 1 档）。
     * 重点观察名单不在这里，见 {@link #checkRestricted}。
     *
     * @param sec       证券主数据
     * @param inObserve 证券或主体在观察池
     * @return 准入方式
     */
    public static GradedInboundMode resolveGradedInboundMode(SecurityInfoBo sec, boolean inObserve) {
        boolean issuerOne = isIssuerGradeOne(sec);
        if (isAbs(sec)) {
            // ABS：1 档看页面所选担保人内评；无担保人或非 1 档走至少下调一级
            return isGuarantorGradeOne(sec) ? GradedInboundMode.LEVEL_ONE_ONLY : GradedInboundMode.DOWNGRADE_CEILING;
        }
        String memo = resolveExclusiveMemo(sec);
        if (MEMO_PRIVATE.equals(memo)) {
            return issuerOne ? GradedInboundMode.LEVEL_ONE_ONLY : GradedInboundMode.DOWNGRADE_CEILING;
        }
        if (MEMO_PERPETUAL.equals(memo)) {
            // 发债主体内评 1 档：按矩阵最好档下调一级，只留那一档
            return issuerOne ? GradedInboundMode.DOWNGRADE_EXACT : GradedInboundMode.DOWNGRADE_CEILING;
        }
        if (MEMO_SUBORDINATED.equals(memo)) {
            if (issuerOne) {
                return GradedInboundMode.LEVEL_ONE_ONLY;
            }
            if (isIssuerGradeTwoBand(sec)) {
                return GradedInboundMode.DOWNGRADE_EXACT;
            }
            return GradedInboundMode.DOWNGRADE_CEILING;
        }
        if (MEMO_GUARANTEED.equals(memo) || inObserve) {
            return GradedInboundMode.BEST_AND_WORSE;
        }
        return GradedInboundMode.EXACT_MATRIX;
    }

    /**
     * 已在重点观察名单时的入库校验（仅目标为信用债 1～5 级时拦截）。
     * 未在分级库：不得新增；已在 1～4 级：只能去五级或出库；已在五级：不可上调。
     * 强担保（有担保且担保人内评 1 档）返回 null，由调用方继续走其它规则。
     *
     * @param sec               证券
     * @param inRestricted      证券或主体在重点观察名单
     * @param currentGradedSort 当前已在分级库的最好档；未在分级库为 null
     * @param targetSort        目标分级库档；非分级库目标为 null（本方法不拦）
     * @return 失败文案；通过为 null
     */
    public static String checkRestricted(SecurityInfoBo sec, boolean inRestricted,
                                         Integer currentGradedSort, Integer targetSort) {
        if (!inRestricted || targetSort == null) {
            return null;
        }
        if (isStrongGuarantee(sec)) {
            return null;
        }
        if (currentGradedSort == null) {
            return "重点观察名单原则上不得新增入库信用债分级库";
        }
        if (currentGradedSort <= 4 && targetSort != MAX_LEVEL) {
            return "重点观察名单已在库债券只能调入五级库或调出";
        }
        if (currentGradedSort == MAX_LEVEL && targetSort < MAX_LEVEL) {
            return "重点观察名单已在五级库，不可上调";
        }
        return null;
    }

    /**
     * 是否信用债大库树（含根节点）。境外债不纳入主体评分档套件。
     *
     * @param poolType 投资池类型
     * @return true=信用债
     */
    public static boolean isGradedBondPoolType(String poolType) {
        return "credit_bond".equals(poolType);
    }

    /**
     * 是否信用债分级库叶子（一～五级）。根节点 pool_level=1 排除。
     *
     * @param pool 投资池
     * @return true=信用债一～五级叶子
     */
    public static boolean isGradedLevelPool(InvestmentPoolBo pool) {
        if (pool == null || !isGradedBondPoolType(pool.getPoolType())) {
            return false;
        }
        Integer sort = pool.getInnerSort();
        if (sort == null || sort < MIN_LEVEL || sort > MAX_LEVEL) {
            return false;
        }
        return pool.getPoolLevel() == null || pool.getPoolLevel() == 2;
    }

    /**
     * 将档位限制在 1～5。
     *
     * @param sort 原始档位
     * @return 限制后的档位
     */
    public static int clampLevel(int sort) {
        if (sort < MIN_LEVEL) {
            return MIN_LEVEL;
        }
        if (sort > MAX_LEVEL) {
            return MAX_LEVEL;
        }
        return sort;
    }

    /**
     * 内评档编码是否为 1 档。
     *
     * @param gradeCode 内评档编码
     * @return true=1 档
     */
    private static boolean isGradeOne(String gradeCode) {
        return gradeCode != null && "1".equals(gradeCode.trim());
    }

    /**
     * 发债主体内评是否为 1 档。只看 {@code innerIssuerRating}。
     *
     * @param sec 证券主数据
     * @return true=发债主体内评 1 档
     */
    private static boolean isIssuerGradeOne(SecurityInfoBo sec) {
        return sec != null && isGradeOne(sec.getInnerIssuerRating());
    }

    /**
     * 页面所选担保人内评是否为 1 档。只看 {@code innerGuarantorRating}；无担保人返回 false。
     *
     * @param sec 证券主数据
     * @return true=担保人内评 1 档
     */
    private static boolean isGuarantorGradeOne(SecurityInfoBo sec) {
        return sec != null && isGradeOne(sec.getInnerGuarantorRating());
    }

    /**
     * 发债主体内评是否为 2+/2/2-。只看 {@code innerIssuerRating}。
     *
     * @param sec 证券主数据
     * @return true=2+/2/2-
     */
    private static boolean isIssuerGradeTwoBand(SecurityInfoBo sec) {
        if (sec == null || sec.getInnerIssuerRating() == null) {
            return false;
        }
        String code = sec.getInnerIssuerRating().trim();
        return "2+".equals(code) || "2".equals(code) || "2-".equals(code);
    }

    /**
     * 文案是否包含「私募」。
     *
     * @param text 发行方式或内部分类
     * @return true=含私募
     */
    private static boolean containsPrivate(String text) {
        return text != null && text.contains("私募");
    }
}
