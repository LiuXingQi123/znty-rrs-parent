package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;

/**
 * 信用债分级库准入规则
 *
 * {@link #resolveExclusiveMemo} 将重叠的特征归成一个标签，优先级从高到低为担保、永续、次级、私募
 * 例如同时具备永续和担保特征的债券按担保分支处理
 * ABS 单独走特殊债分支，调库时其评级主体为选定的权益人，自选权益人优先
 *
 * {@link #resolveGradedInboundMode} 决定按矩阵点名池还是按档位准入，{@link #checkRestricted} 单独校验重点观察名单
 * 含权债的行权期限由 {@link CreditBondRemainTermUtil} 计算，对准入方式沿用相应的债券类型规则
 */
public final class CreditBondSpecialInboundRule {

    /** 分级库最差档（五级库） */
    public static final int MAX_LEVEL = 5;

    /** 分级库最好档（一级库） */
    public static final int MIN_LEVEL = 1;

    /** 私募标签，优先级最低 */
    public static final String MEMO_PRIVATE = "private";

    /** 次级（覆盖私募） */
    public static final String MEMO_SUBORDINATED = "subordinated";

    /** 永续（覆盖次级 / 私募） */
    public static final String MEMO_PERPETUAL = "perpetual";

    /** 担保（覆盖永续 / 次级 / 私募） */
    public static final String MEMO_GUARANTEED = "guaranteed";

    /**
     * 信用债分级库准入方式
     *
     * 按档位准入时，决定起始档以及是否开放更差的档位
     */
    public enum GradedInboundMode {
        /** 只允许调入一级库 */
        LEVEL_ONE_ONLY(true),

        /** 矩阵最好档再降一档后，从该档开放到五级，例如最好档为一级时开放二至五级 */
        DOWNGRADE_CEILING(false),

        /** 矩阵最好档再降一档，只允许该档 */
        DOWNGRADE_EXACT(true),

        /** 担保债或已在观察池时，从矩阵最好档开放到五级 */
        BEST_AND_WORSE(false),

        /** 普通债只允许进入矩阵指定的池 */
        EXACT_MATRIX(false);

        /** 按档位判断时，true 只允许起始档，false 允许起始档及更差的档位 */
        private final boolean exactSort;

        /**
         * 记录按档位准入时是否只允许起始档
         *
         * @param exactSort 是否只允许起始档
         */
        GradedInboundMode(boolean exactSort) {
            this.exactSort = exactSort;
        }

        /**
         * 判断准入是否使用目标池档位
         *
         * @return true=按目标池档位判断，false=按矩阵指定的池判断
         */
        public boolean usesSort() {
            return this != EXACT_MATRIX;
        }

        /**
         * 根据准入方式和矩阵最好档计算起始档
         *
         * 执行顺序：
         * 1. 没有矩阵最好档或按矩阵指定池准入时返回 null
         * 2. 只允许一级库时返回一级
         * 3. 需要降档时从矩阵最好档再降一级，否则沿用矩阵最好档，结果限制在一至五级
         *
         * @param bestAllowedSort 矩阵允许调入的最好档位
         * @return 可调入的起始档位，缺少矩阵结果或按矩阵指定池准入时返回 null
         */
        public Integer resolveStartSort(Integer bestAllowedSort) {
            if (bestAllowedSort == null || this == EXACT_MATRIX) {
                return null;
            }
            if (this == LEVEL_ONE_ONLY) {
                return MIN_LEVEL;
            }
            if (this == DOWNGRADE_CEILING || this == DOWNGRADE_EXACT) {
                // 矩阵最好档已是五级时，降档结果仍为五级
                return clampLevel(bestAllowedSort + 1);
            }
            // 担保债 / 观察池：矩阵最好档本身（不得高于该档，开到五级）
            return clampLevel(bestAllowedSort);
        }

        /**
         * 检查目标档位是否符合当前准入方式
         *
         * 执行顺序：
         * 1. 目标档或起始档缺失，或该方式按矩阵指定池判断时返回 false
         * 2. 只允许起始档时比较是否相等，否则比较目标档是否等于起始档或更差
         *
         * @param poolSort 目标池档位，对应 inner_sort
         * @param startSort 起始档
         * @return true=档位满足准入方式，false=档位缺失、不适用档位判断或目标档不符
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
         * 生成档位校验失败时展示的允许范围
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
     * 按发行方式和内部分类识别私募债
     *
     * @param sec 证券主数据
     * @return true=任一字段含「私募」，false=证券缺失或两个字段均不含「私募」
     */
    public static boolean isPrivateBond(SecurityInfoBo sec) {
        if (sec == null) {
            return false;
        }
        // 发行方式、内部分类任一含「私募」即视为私募债
        return containsPrivate(sec.getIssueType()) || containsPrivate(sec.getInnerClass());
    }

    /**
     * 按主数据的永续标记识别永续债
     *
     * @param sec 证券主数据
     * @return true=永续标记为 1，false=证券或标记缺失，或标记不为 1
     */
    public static boolean isPerpetual(SecurityInfoBo sec) {
        return sec != null && sec.getYxFlag() != null && sec.getYxFlag() == 1;
    }

    /**
     * 按主数据的次级标记识别次级债
     *
     * @param sec 证券主数据
     * @return true=次级标记为 1，false=证券或标记缺失，或标记不为 1
     */
    public static boolean isSubordinated(SecurityInfoBo sec) {
        return sec != null && sec.getCjFlag() != null && sec.getCjFlag() == 1;
    }

    /**
     * 按主数据的标记或证券类型识别 ABS
     *
     * 类型为 {@code abs_all} 时，只有独立的 ABS 标记为 1 才会命中
     *
     * @param sec 证券主数据
     * @return true=ABS 标记为 1 或类型为 {@code abs}，false=证券缺失或两项均不满足
     */
    public static boolean isAbs(SecurityInfoBo sec) {
        if (sec == null) {
            return false;
        }
        if (sec.getAbsFlag() != null && sec.getAbsFlag() == 1) {
            return true;
        }
        return "abs".equals(sec.getSecurityType());
    }

    /**
     * 判断证券类型是否应从信用债分级库准入中排除
     *
     * 命中排除条件时，选池阶段去掉分级库，校验阶段阻止调入分级库
     *
     * @param sec 证券主数据
     * @return true=可转债、可交换债、可分离转债或 CRMW，false=证券缺失或属于其他类型
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
     * 按主数据的担保标记识别担保债
     *
     * @param sec 证券主数据
     * @return true=担保标记为 1，false=证券或标记缺失，或标记不为 1
     */
    public static boolean isGuaranteed(SecurityInfoBo sec) {
        return sec != null && sec.getGuarantFlag() != null && sec.getGuarantFlag() == 1;
    }

    /**
     * 按主数据的含权标记识别含权债
     *
     * @param sec 证券主数据
     * @return true=含权标记为 1，false=证券或标记缺失，或标记不为 1
     */
    public static boolean isInright(SecurityInfoBo sec) {
        return sec != null && sec.getInrightFlag() != null && sec.getInrightFlag() == 1;
    }

    /**
     * 判断债券是否满足强担保豁免条件
     *
     * ABS 的评级主体是已固定的权益人，强担保豁免只适用于担保人内评为 1 档的非 ABS 担保债
     *
     * @param sec 证券主数据
     * @param strongGuaranteeEnabled 是否启用强担保豁免
     * @return true=开关开启且满足豁免条件，false=开关关闭或不满足条件
     */
    public static boolean isStrongGuarantee(SecurityInfoBo sec, boolean strongGuaranteeEnabled) {
        // 非 ABS 担保债使用担保人内评判断是否满足 1 档豁免条件
        return strongGuaranteeEnabled && !isAbs(sec) && isGuaranteed(sec)
                && isGradeOne(sec.getInnerGuarantorRating());
    }

    /**
     * 将可能并存的债券特征归成一个准入标签
     *
     * 依次检查私募、次级、永续、担保，后命中的特征覆盖前一个标签
     *
     * @param sec 证券主数据
     * @return 命中优先级最高的标签，证券缺失或无对应特征时返回 null
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
     * 判断是否使用特殊债分支
     *
     * ABS 可直接命中，其余证券使用覆盖后的标签判断
     *
     * @param sec 证券主数据
     * @return true=ABS 或最终标签为私募、次级、永续之一，false=证券缺失或应采用其他分支
     */
    public static boolean isSpecialBranch(SecurityInfoBo sec) {
        if (isAbs(sec)) {
            return true;
        }
        String memo = resolveExclusiveMemo(sec);
        return MEMO_PRIVATE.equals(memo) || MEMO_SUBORDINATED.equals(memo) || MEMO_PERPETUAL.equals(memo);
    }

    /**
     * 按债券类型和观察池状态选出准入方式
     *
     * 特殊债读取 {@code innerIssuerRating}，其中 ABS 使用调用方写入发行人字段的权益人内评
     * 非 ABS 担保债由调用方取发行人与担保人的较优内评来选矩阵单元格，特殊债的降档仍按发行人内评判断
     * 重点观察名单由 {@link #checkRestricted} 单独校验
     *
     * 执行顺序：
     * 1. ABS 使用权益人内评，1 档只允许一级，内评缺失或属于其他档时至少下调一级
     * 2. 计算最终标签，私募内评 1 档只允许一级，其余至少下调一级
     * 3. 永续内评 1 档下调一级且只允许该档，其余至少下调一级
     * 4. 次级内评 1 档只允许一级，2+、2、2- 下调一级且只允许该档，其余至少下调一级
     * 5. 担保或已在观察池时，从矩阵最好档开放到五级，其余只接受矩阵指定的池
     *
     * @param sec 证券主数据
     * @param inObserve 证券或主体是否在观察池
     * @return 准入方式
     */
    public static GradedInboundMode resolveGradedInboundMode(SecurityInfoBo sec, boolean inObserve) {
        // 1. 读取发行人内评，ABS 使用该字段保存的权益人内评
        boolean issuerOne = isIssuerGradeOne(sec);
        if (isAbs(sec)) {
            return issuerOne ? GradedInboundMode.LEVEL_ONE_ONLY : GradedInboundMode.DOWNGRADE_CEILING;
        }
        // 2. 先确定标签，再处理私募债
        String memo = resolveExclusiveMemo(sec);
        if (MEMO_PRIVATE.equals(memo)) {
            return issuerOne ? GradedInboundMode.LEVEL_ONE_ONLY : GradedInboundMode.DOWNGRADE_CEILING;
        }
        // 3. 永续债内评 1 档时只允许降一档后的库
        if (MEMO_PERPETUAL.equals(memo)) {
            return issuerOne ? GradedInboundMode.DOWNGRADE_EXACT : GradedInboundMode.DOWNGRADE_CEILING;
        }
        // 4. 次级债按发行人内评分别处理 1 档、2 档区间和其他档
        if (MEMO_SUBORDINATED.equals(memo)) {
            if (issuerOne) {
                return GradedInboundMode.LEVEL_ONE_ONLY;
            }
            // 检查 2+、2、2- 三档是否适用只降一级
            if (isIssuerGradeTwoBand(sec)) {
                return GradedInboundMode.DOWNGRADE_EXACT;
            }
            return GradedInboundMode.DOWNGRADE_CEILING;
        }
        // 5. 担保或观察池按档位放行，其余债券只按矩阵指定池放行
        if (MEMO_GUARANTEED.equals(memo) || inObserve) {
            return GradedInboundMode.BEST_AND_WORSE;
        }
        return GradedInboundMode.EXACT_MATRIX;
    }

    /**
     * 校验重点观察名单证券能否调入信用债分级库
     *
     * 已在一至四级的债券仍可按出库规则办理出库，本方法校验调入的目标档位
     *
     * 执行顺序：
     * 1. 未在重点观察名单或目标无分级档位时，跳过本项校验
     * 2. 满足强担保豁免时通过本项校验，由调用方继续检查其他规则
     * 3. 尚未在分级库时禁止新增，已在一至四级时只允许调入五级，已在五级时禁止上调
     *
     * @param sec 证券主数据
     * @param inRestricted 证券或主体是否在重点观察名单
     * @param currentGradedSort 当前所在分级库的最好档，未在分级库时为 null
     * @param targetSort 目标分级库档位，非分级库目标为 null
     * @param strongGuaranteeEnabled 是否启用强担保豁免
     * @return 限制原因，跳过或通过本项校验时返回 null
     */
    public static String checkRestricted(SecurityInfoBo sec, boolean inRestricted,
                                         Integer currentGradedSort, Integer targetSort,
                                         boolean strongGuaranteeEnabled) {
        if (!inRestricted || targetSort == null) {
            return null;
        }
        if (isStrongGuarantee(sec, strongGuaranteeEnabled)) {
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
     * 判断池类型是否属于信用债大库，包含根节点
     *
     * 信用债大库使用这套主体评分分级矩阵，境外债池采用独立的池类型
     *
     * @param poolType 投资池类型
     * @return true=池类型为 {@code credit_bond}，false=类型缺失或属于其他池
     */
    public static boolean isGradedBondPoolType(String poolType) {
        return "credit_bond".equals(poolType);
    }

    /**
     * 判断池是否为信用债大库下的一至五级分级库
     *
     * 池类型须为信用债，档位须在一至五级，树层级接受二级或未填写
     *
     * @param pool 投资池
     * @return true=池满足分级库条件，false=池缺失或任一条件不符
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
     * 将档位限制在信用债分级库的一至五级范围
     *
     * @param sort 原始档位
     * @return 小于 1 时返回 1，大于 5 时返回 5，范围内原样返回
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
     * 按内评编码判断是否为 1 档
     *
     * @param gradeCode 内评档编码
     * @return true=去掉首尾空白后编码为 1，false=编码为空或属于其他档
     */
    private static boolean isGradeOne(String gradeCode) {
        return gradeCode != null && "1".equals(gradeCode.trim());
    }

    /**
     * 检查发行人内评字段 {@code innerIssuerRating} 是否为 1 档
     *
     * @param sec 证券主数据
     * @return true=发行人内评为 1 档，false=证券或内评缺失，或内评属于其他档
     */
    private static boolean isIssuerGradeOne(SecurityInfoBo sec) {
        // 用统一的 1 档编码判断发行人内评
        return sec != null && isGradeOne(sec.getInnerIssuerRating());
    }

    /**
     * 检查发行人内评字段 {@code innerIssuerRating} 是否在 2 档区间
     *
     * @param sec 证券主数据
     * @return true=发行人内评为 2+、2 或 2-，false=证券或内评缺失，或内评属于其他档
     */
    private static boolean isIssuerGradeTwoBand(SecurityInfoBo sec) {
        if (sec == null || sec.getInnerIssuerRating() == null) {
            return false;
        }
        String code = sec.getInnerIssuerRating().trim();
        return "2+".equals(code) || "2".equals(code) || "2-".equals(code);
    }

    /**
     * 检查证券分类文本是否包含「私募」
     *
     * @param text 发行方式或内部分类
     * @return true=文本含「私募」，false=文本为空或不含「私募」
     */
    private static boolean containsPrivate(String text) {
        return text != null && text.contains("私募");
    }
}
