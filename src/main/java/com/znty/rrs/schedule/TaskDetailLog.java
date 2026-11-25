package com.znty.rrs.schedule;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 收集并汇总单次定时任务的过程日志
 *
 * 任务在 {@link RrsScheduledTask#execute()} 执行期间逐行记录关键过程，追加时自动加上 {@code yyyy-MM-dd HH:mm:ss} 格式的当前时间
 * 任务将汇总后的文本放入 {@link ScheduledTaskResult}，供执行历史详情展示
 */
public class TaskDetailLog {

    /** 过程日志时间格式 */
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 保存本次任务已追加的多行日志 */
    private final StringBuilder buffer = new StringBuilder();

    /** 单次任务执行期间复用的时间格式化对象 */
    private final SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_PATTERN);

    /**
     * 追加带当前时间的一条过程日志
     *
     * 执行顺序：
     * 1. 内容为 null 或空字符串时结束处理
     * 2. 已有日志时先追加换行符
     * 3. 按「时间 内容」的格式追加文本
     *
     * @param line 要追加的日志内容
     */
    public void line(String line) {
        // 1. 跳过空日志
        if (line == null || line.isEmpty()) {
            return;
        }
        // 2. 已有内容时用换行分隔两条日志
        if (buffer.length() > 0) {
            buffer.append('\n');
        }
        // 3. 按「时间 内容」的格式追加
        buffer.append(timeFormat.format(new Date()));
        buffer.append(' ');
        buffer.append(line);
    }

    /**
     * 追加带当前时间和级别的一条过程日志
     *
     * 执行顺序：
     * 1. 内容为 null 或空字符串时结束处理
     * 2. 级别为 null 或空字符串时按普通日志追加
     * 3. 有级别时按「时间 [级别] 内容」的格式追加
     *
     * @param level 日志级别，例如 INFO 或 WARN
     * @param line 要追加的日志内容
     */
    public void line(String level, String line) {
        // 1. 跳过空日志
        if (line == null || line.isEmpty()) {
            return;
        }
        // 2. 未提供级别时按普通日志追加
        if (level == null || level.isEmpty()) {
            line(line);
            return;
        }
        // 3. 加上级别后复用普通日志的写入格式
        line("[" + level + "] " + line);
    }

    /**
     * 判断本次任务的过程日志是否为空
     *
     * 执行顺序：
     * 1. 检查已累计的日志文本长度是否为 0
     *
     * @return true=没有过程日志，false=已有过程日志
     */
    public boolean isEmpty() {
        return buffer.length() == 0;
    }

    /**
     * 汇总本次任务的过程日志
     *
     * 执行顺序：
     * 1. 没有日志时返回 null，否则把已累计的内容转为字符串
     *
     * @return 完整的过程日志文本，没有日志时返回 null
     */
    public String build() {
        return buffer.length() == 0 ? null : buffer.toString();
    }
}
