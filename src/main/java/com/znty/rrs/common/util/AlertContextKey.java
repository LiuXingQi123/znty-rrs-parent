package com.znty.rrs.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 提醒依据摘要：把一条待办的适用范围压成固定长度标识
 *
 * <p>人工处理结论按摘要沿用：摘要不变说明问题依据没变，同一问题不再重复提醒；
 * 摘要变化说明身份、池状态、档位或不符合原因已经变了，需要重新提醒
 */
public final class AlertContextKey {

    /** 工具类不允许实例化 */
    private AlertContextKey() {
    }

    /**
     * 计算依据摘要
     *
     * 执行顺序：
     * 1. 各段内容按传入顺序用竖线连接，空值保留为 null 字样
     * 2. 对连接结果取 SHA-256 并转成 64 位十六进制
     *
     * @param parts 组成依据的各段内容
     * @return 十六进制摘要
     */
    public static String of(String... parts) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                value.append('|');
            }
            value.append(parts[i]);
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境缺少 SHA-256", ex);
        }
    }
}
