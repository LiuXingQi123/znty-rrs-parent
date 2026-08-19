package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;

/**
 * 信用债大库分级库特殊债入库规则（对齐老 polidEnum：仅信用债 1～5）。
 *
 * <p>标准矩阵给出最好档后，私募 / 永续 / 次级 / ABS 再下调；担保与观察名单按最好档封顶；
 * 重点观察禁止新增进入信用债 1～5 级（强担保豁免）。重叠时取最严天花板。
 * 境外债分级库不走本套规则（老系统目标池不在 polidEnum 时整段跳过 MainGrade）。</p>
 */
public final class CreditBondSpecialInboundRule {

    /** 分级库最差档（五级库） */
    public static final int MAX_LEVEL = 5;

    /** 分级库最好档（一级库） */
    public static final int MIN_LEVEL = 1;

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
     * 是否资产支持证券 / ABS。
     *
     * @param sec 证券主数据
     * @return true=ABS
     */
    public static boolean isAbs(SecurityInfoBo sec) {
        return sec != null && sec.getAbsFlag() != null && sec.getAbsFlag() == 1;
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
     * 主体内评是否为 1 档。
     *
     * @param gradeCode 内评档编码
     * @return true=1 档
     */
    public static boolean isGradeOne(String gradeCode) {
        return gradeCode != null && "1".equals(gradeCode.trim());
    }

    /**
     * 强担保豁免：有担保且担保人内评档为 1。
     *
     * @param sec 证券主数据
     * @return true=强担保，重点观察可豁免
     */
    public static boolean isStrongGuarantee(SecurityInfoBo sec) {
        return isGuaranteed(sec) && isGradeOne(sec.getInnerGuarantorRating());
    }

    /**
     * 是否走「天花板」模型（目标 inner_sort ≥ 最好档），而不是矩阵精确 poolId。
     *
     * <p>观察名单、担保债、私募 / 永续 / 次级 / ABS 均按档位封顶。
     *
     * @param sec       证券主数据
     * @param inObserve 证券或主体是否在观察名单
     * @return true=按天花板校验
     */
    public static boolean needsCeilingModel(SecurityInfoBo sec, boolean inObserve) {
        return inObserve || isGuaranteed(sec) || isPrivateBond(sec)
                || isPerpetual(sec) || isSubordinated(sec) || isAbs(sec);
    }

    /**
     * 在标准最好档上叠加特殊债下调，重叠取最严（sort 更大）。
     *
     * @param gradeCode       用于查矩阵的内评档（担保已取孰高）
     * @param bestAllowedSort 标准矩阵允许池的最小 inner_sort
     * @param sec             证券
     * @param inObserve       证券或主体在观察名单
     * @return 1～5 的天花板
     */
    public static int resolveCeilingSort(String gradeCode, int bestAllowedSort, SecurityInfoBo sec,
                                         boolean inObserve) {
        // 先把标准最好档限制在 1～5
        int ceiling = clampLevel(bestAllowedSort);
        if (isAbs(sec) && !isGradeOne(gradeCode)) {
            ceiling = Math.max(ceiling, bestAllowedSort + 1);
        }
        if (isPrivateBond(sec) && !isGradeOne(gradeCode)) {
            ceiling = Math.max(ceiling, bestAllowedSort + 1);
        }
        // 永续：1 档也下调一级
        if (isPerpetual(sec)) {
            ceiling = Math.max(ceiling, bestAllowedSort + 1);
        }
        if (isSubordinated(sec) && !isGradeOne(gradeCode)) {
            ceiling = Math.max(ceiling, bestAllowedSort + 1);
        }
        // 担保债、观察名单：不额外 +1，封顶即为标准最好档
        if (inObserve || isGuaranteed(sec)) {
            ceiling = Math.max(ceiling, bestAllowedSort);
        }
        // 下调后仍不超过五级库
        return clampLevel(ceiling);
    }

    /**
     * 重点观察入库校验。强担保返回 null（由调用方继续走其它规则）。
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
     * 文案是否包含「私募」。
     *
     * @param text 发行方式或内部分类
     * @return true=含私募
     */
    private static boolean containsPrivate(String text) {
        return text != null && text.contains("私募");
    }
}
