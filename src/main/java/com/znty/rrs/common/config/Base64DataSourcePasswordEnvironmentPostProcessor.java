package com.znty.rrs.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

/**
 * 数据源密码 Base64 解码处理器
 *
 * 由 Spring Boot 在启动时调用，配置加载完成后将解码结果放入环境，供数据源自动配置读取
 */
public class Base64DataSourcePasswordEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 数据源自动配置读取的密码配置键 */
    private static final String DATASOURCE_PASSWORD_KEY = "spring.datasource.password";

    /** 保存解码结果的属性源名称 */
    private static final String DATASOURCE_PASSWORD_PROPERTY_SOURCE = "base64DataSourcePassword";

    /**
     * 在数据源自动配置读取密码前，用解码结果覆盖原配置
     *
     * 执行顺序：
     * 1. 读取数据源密码配置，未配置时直接结束
     * 2. 对配置值执行 Base64 解码并按 UTF-8 转为文本，空字符串仍为空字符串
     * 3. 把解码结果放入优先级最高的属性源，让后续配置读取解码后的值
     * 4. 配置值无法解码时抛出异常，结束应用启动
     *
     * @param environment 已加载配置的 Spring 环境
     * @param application 当前启动的 Spring 应用
     * @throws IllegalStateException 数据源密码配置无法按 Base64 解码
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 1. 读取密码配置，未配置时直接结束
        String encodedPassword = environment.getProperty(DATASOURCE_PASSWORD_KEY);
        if (encodedPassword == null) {
            return;
        }

        try {
            // 2. 按 Base64 和 UTF-8 还原配置文本
            String decodedPassword = new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
            // 3. 让数据源优先读取解码后的值
            environment.getPropertySources().addFirst(new MapPropertySource(
                    DATASOURCE_PASSWORD_PROPERTY_SOURCE,
                    Collections.<String, Object>singletonMap(DATASOURCE_PASSWORD_KEY, decodedPassword)));
        } catch (IllegalArgumentException e) {
            // 4. 解码失败时停止启动，避免数据源使用无效配置
            throw new IllegalStateException("数据库密码 Base64 解码失败", e);
        }
    }

    /**
     * 指定解码处理器的启动顺序
     *
     * @return 比 {@link ConfigDataEnvironmentPostProcessor} 的顺序值大 1，确保先加载配置再解码
     */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
