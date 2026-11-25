package com.znty.rrs.common.util;

import com.znty.rrs.exception.BizException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 股票查询筛选与评级导出工具
 *
 * 统一校验自选和分管查询的用户范围、自然日区间，并提供导出评级文案
 */
public final class StockQueryFilterHelper {

    /** 工具类禁止实例化 */
    private StockQueryFilterHelper() { }

    /**
     * 校验自选和分管查询所需的当前用户 ID
     *
     * 执行顺序：
     * 1. 检查 ID 是否只含数字且首位大于 0
     * 2. 确认该值在 Long 的范围内，校验通过后原样返回
     *
     * @param userId 当前用户 ID
     * @return 校验通过的用户 ID
     * @throws BizException 用户 ID 缺失、格式无效或超出 Long 范围
     */
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

    /**
     * 校验自然日范围并返回查询的结束边界
     *
     * 执行顺序：
     * 1. 分别解析开始和结束日期，检查开始日期是否晚于结束日期
     * 2. 将结束日期的次日零点作为排他边界，使查询包含结束日期当天
     *
     * @param start 开始日期文本，格式为 yyyy-MM-dd
     * @param end 结束日期文本，格式为 yyyy-MM-dd
     * @return 结束日期的次日零点，未传结束日期时返回 null
     * @throws BizException 日期无效或开始日期晚于结束日期
     */
    public static LocalDateTime endExclusive(String start, String end) {
        // 1. 解析开始自然日，用于检查日期先后顺序
        LocalDate startDate = parseDate(start);
        // 解析结束自然日，用于检查日期范围和计算结束边界
        LocalDate endDate = parseDate(end);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BizException("开始日期不能晚于结束日期");
        }
        // 2. 用次日零点作为结束边界，保留截止当天
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    /**
     * 校验日期文本格式并解析自然日
     *
     * @param value 日期参数，非空时须为 yyyy-MM-dd 格式
     * @return 解析出的日期，参数为 null 或空字符串时返回 null
     * @throws BizException 文本格式无效或日期不存在
     */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new DateTimeParseException("日期格式无效", value, 0);
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BizException("日期必须为有效的 yyyy-MM-dd 自然日");
        }
    }

    /**
     * 将股票评级编码转为导出使用的中文名称
     *
     * @param code 股票评级编码
     * @return 对应的中文名称，编码为 null 或空字符串时返回空字符串
     * @throws BizException 评级编码未知
     */
    public static String ratingLabel(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        switch (code) {
            case "buy":
                return "买入";
            case "overweight":
                return "增持";
            case "neutral":
                return "中性";
            case "underweight":
                return "减持";
            case "sell":
                return "卖出";
            default:
                throw new BizException("股票评级编码无效：" + code);
        }
    }
}
