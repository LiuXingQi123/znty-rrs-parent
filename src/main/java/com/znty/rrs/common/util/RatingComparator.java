package com.znty.rrs.common.util;

import java.util.Arrays;
import java.util.List;

/**
 * 外部主体评级比较工具
 *
 * 评级顺序与前端 {@code DICT_RATING_LONG} 和 {@code DICT_RATING_SHORT} 一致，序号越小评级越高
 */
public final class RatingComparator {

    /** 长期评级序号表（index 越小评级越高）：AAA+ > AAA > AA+ > ... > D */
    private static final List<String> LONG_TERM = Arrays.asList(
            "AAA+", "AAA", "AA+", "AA", "AA-",
            "A+", "A", "A-",
            "BBB+", "BBB", "BBB-",
            "BB+", "BB", "BB-",
            "B", "CCC", "CC", "C", "D");

    /** 短期评级序号表（CP/SCP 专用，index 越小评级越高）：A-1 > A-2 > A-3 > B > C > D */
    private static final List<String> SHORT_TERM = Arrays.asList(
            "A-1", "A-2", "A-3", "B", "C", "D");

    /** 工具类禁止实例化 */
    private RatingComparator() {
    }

    /**
     * 判断当前评级是否较前次下调
     *
     * 当前评级序号大于前次序号时认定为下调，依据不足时按未下调处理，简易流程继续检查其他条件
     *
     * 执行顺序：
     * 1. 当前或前次评级为空白时返回 false，首次评级也按此处理
     * 2. 优先比较两次评级在长期评级表中的序号
     * 3. 长期评级表无法同时匹配时，再比较短期评级序号
     * 4. 评级未知或跨长期和短期序列时，通过变动文案是否包含「下调」判断
     *
     * @param current 当前评级，对应 b_info_creditrating
     * @param previous 前次评级，对应 b_info_precreditrating
     * @param changeText 可为空，对应 b_creditratingchange，例如「维持」「上调」「下调」
     * @return true=可判定为下调，false=未下调或缺少依据
     */
    public static boolean isDowngraded(String current, String previous, String changeText) {
        // 1. 检查两期评级是否齐全，缺少任一评级时无法判定为下调
        if (isBlank(current) || isBlank(previous)) {
            return false;
        }
        String cur = current.trim();
        String pre = previous.trim();
        // 2. 先查长期评级序号，序号越大评级越低
        Integer curLong = indexIn(LONG_TERM, cur);
        // 取得前次长期评级序号，用于比较
        Integer preLong = indexIn(LONG_TERM, pre);
        if (curLong != null && preLong != null) {
            return curLong > preLong;
        }
        // 3. 长期评级无法比较时，检查当前和前次短期评级序号
        Integer curShort = indexIn(SHORT_TERM, cur);
        // 取得前次短期评级序号，用于比较
        Integer preShort = indexIn(SHORT_TERM, pre);
        if (curShort != null && preShort != null) {
            return curShort > preShort;
        }
        // 4. 评级序号无法比较时，使用变动文案
        if (changeText != null) {
            String change = changeText.trim();
            if (change.contains("下调")) {
                return true;
            }
            if (change.contains("上调") || change.contains("维持")) {
                return false;
            }
        }
        return false;
    }

    /**
     * 在评级表内查找指定评级的序号
     *
     * @param list 按评级从高到低排列的列表
     * @param rating 待查找的评级，匹配时忽略大小写
     * @return 从 0 开始的序号，找不到时返回 null
     */
    private static Integer indexIn(List<String> list, String rating) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(rating)) {
                return i;
            }
        }
        return null;
    }

    /**
     * 检查评级文本是否为空白
     *
     * @param s 评级文本
     * @return true=文本为 null 或去掉首尾空白后为空，false=文本有内容
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
