package com.znty.rrs.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

/**
 * 在数据源自动配置前解码 Base64 格式的数据库密码。
 */
public class Base64DataSourcePasswordEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 数据源密码配置键 */
    private static final String DATASOURCE_PASSWORD_KEY = "spring.datasource.password";

    /** 解码后密码属性源名称 */
    private static final String DATASOURCE_PASSWORD_PROPERTY_SOURCE = "base64DataSourcePassword";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String encodedPassword = environment.getProperty(DATASOURCE_PASSWORD_KEY);
        if (encodedPassword == null) {
            return;
        }

        try {
            String decodedPassword = new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
            environment.getPropertySources().addFirst(new MapPropertySource(
                    DATASOURCE_PASSWORD_PROPERTY_SOURCE,
                    Collections.<String, Object>singletonMap(DATASOURCE_PASSWORD_KEY, decodedPassword)));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("数据库密码 Base64 解码失败", e);
        }
    }

    @Override
    public int getOrder() {
        return ConfigFileApplicationListener.DEFAULT_ORDER + 1;
    }
}
