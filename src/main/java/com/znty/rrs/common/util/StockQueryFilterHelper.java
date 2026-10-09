package com.znty.rrs.common.util;

import com.znty.rrs.exception.BizException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** 股票查询的用户及自然日筛选校验。 */
public final class StockQueryFilterHelper {
    /** 工具类禁止实例化。 */
    private StockQueryFilterHelper() { }

    /** 校验当前用户，避免自选或分管查询缺少用户范围。 */
    public static String requireUserId(String userId) {
        if (userId == null || !userId.matches("[1-9][0-9]*")) {
            throw new BizException("当前用户 ID 无效");
        }
        try {
            Long.valueOf(userId);
            return userId;
        } catch (NumberFormatException e) {
            throw new BizException("当前用户 ID 无效");
        }
    }

    /** 校验日期范围并计算包含截止自然日的排他边界。 */
    public static LocalDateTime endExclusive(String start, String end) {
        // 严格解析两个日期，拒绝无效或颠倒的范围。
        LocalDate startDate = parseDate(start);
        LocalDate endDate = parseDate(end);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BizException("开始日期不能晚于结束日期");
        }
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    /** 严格解析非空自然日参数。 */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) { return null; }
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new DateTimeParseException("日期格式无效", value, 0);
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BizException("日期必须为有效的 yyyy-MM-dd 自然日");
        }
    }

    /** 导出评级中文文案；未知编码明确报错。 */
    public static String ratingLabel(String code) {
        if (code == null || code.isEmpty()) { return ""; }
        switch (code) {
            case "buy": return "买入";
            case "overweight": return "增持";
            case "neutral": return "中性";
            case "underweight": return "减持";
            case "sell": return "卖出";
            default: throw new BizException("股票评级编码无效：" + code);
        }
    }
}
