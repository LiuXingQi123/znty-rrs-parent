package com.znty.rrs.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 生成规则提醒的依据摘要
 *
 * 已有人工处理结论且摘要一致时，调用方沿用该结论并跳过本次提醒
 * 身份、池状态、档位或不符合原因变化导致摘要更新时，仍存在的问题重新提醒
 */
public final class AlertContextKey {

    /** 工具类禁止实例化 */
    private AlertContextKey() {
    }

    /**
     * 根据传入的依据生成固定长度的摘要
     *
     * 执行顺序：
     * 1. 各段内容按传入顺序用竖线连接，值为 null 的段按 null 字样拼接
     * 2. 按 UTF-8 计算 SHA-256 摘要，再转成小写十六进制文本
     *
     * @param parts 组成依据的各段内容
     * @return 64 个字符的小写十六进制摘要
     * @throws IllegalStateException 运行环境缺少 SHA-256
     */
    public static String of(String... parts) {
        // 1. 按调用方传入的顺序拼接摘要依据
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                value.append('|');
            }
            value.append(parts[i]);
        }
        try {
            // 2. 计算摘要并转成固定长度的十六进制文本
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
