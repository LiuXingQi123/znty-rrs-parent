package com.znty.rrs.schedule;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 定时任务执行过程日志缓冲
 * <p>
 * 业务实现在 execute 中边写 SLF4J 边 append 到本对象，最终写入
 * {@link ScheduledTaskResult#getDetailLog()}，落库后在执行历史页展示。
 * 每行自动带时间前缀 {@code yyyy-MM-dd HH:mm:ss}。
 * </p>
 */
public class TaskDetailLog {

    /** 过程日志时间格式 */
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 多行内容 */
    private final StringBuilder buffer = new StringBuilder();
    /** 本缓冲实例内复用（单次任务执行串行使用） */
    private final SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_PATTERN);

    /**
     * 追加一行过程日志（自动加时间戳与换行）
     *
     * @param line 单行内容，null/空串忽略
     */
    public void line(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (buffer.length() > 0) {
            buffer.append('\n');
        }
        // 行首时间
        buffer.append(timeFormat.format(new Date()));
        buffer.append(' ');
        buffer.append(line);
    }

    /**
     * 追加带级别前缀的一行（如 INFO / WARN），格式：时间 [级别] 内容
     *
     * @param level 级别文案
     * @param line  内容
     */
    public void line(String level, String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (level == null || level.isEmpty()) {
            // 无级别时按普通行写入
            line(line);
            return;
        }
        line("[" + level + "] " + line);
    }

    /**
     * 是否已有内容
     */
    public boolean isEmpty() {
        return buffer.length() == 0;
    }

    /**
     * 输出完整过程日志文本，无内容返回 null
     */
    public String build() {
        return buffer.length() == 0 ? null : buffer.toString();
    }
}
