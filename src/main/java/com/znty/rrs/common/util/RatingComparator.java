package com.znty.rrs.common.util;

import java.util.Arrays;
import java.util.List;

/**
 * 外部主体评级比较工具：按评级序号判断当前评级是否相对前次下调。
 * 评级序号对齐前端 DICT_RATING_LONG / DICT_RATING_SHORT（index 越小评级越高）。
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

    private RatingComparator() {
    }

    /**
     * 判断当前评级是否相对前次评级下调。
     * <p>优先按同尺度评级序号比较（当前序号 &gt; 前次序号即下调，序号大者评级低）；
     * 若评级不在序号表或跨长期/短期尺度无法比较，回退到评级变动文本（含"下调"判下调）；
     * 仍无法判定则 fail open 返回 false（不阻断简易流程）。</p>
     *
     * @param current    当前评级（b_info_creditrating）
     * @param previous   前次评级（b_info_precreditrating）；空视为首次评级，返回 false
     * @param changeText 评级变动文本（b_creditratingchange，如 维持/上调/下调）；可为空
     * @return true=下调
     */
    public static boolean isDowngraded(String current, String previous, String changeText) {
        // 当前或前次评级缺失（含首次评级），无法判定为下调
        if (isBlank(current) || isBlank(previous)) {
            return false;
        }
        String cur = current.trim();
        String pre = previous.trim();
        // 同属长期评级尺度，序号大者评级低
        Integer curLong = indexIn(LONG_TERM, cur);
        Integer preLong = indexIn(LONG_TERM, pre);
        if (curLong != null && preLong != null) {
            return curLong > preLong;
        }
        // 同属短期评级尺度
        Integer curShort = indexIn(SHORT_TERM, cur);
        Integer preShort = indexIn(SHORT_TERM, pre);
        if (curShort != null && preShort != null) {
            return curShort > preShort;
        }
        // 评级不在序号表或跨尺度，回退到评级变动文本
        if (changeText != null) {
            String change = changeText.trim();
            if (change.contains("下调")) {
                return true;
            }
            if (change.contains("上调") || change.contains("维持")) {
                return false;
            }
        }
        // 无法判定，fail open
        return false;
    }

    private static Integer indexIn(List<String> list, String rating) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(rating)) {
                return i;
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
