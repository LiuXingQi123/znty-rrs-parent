package com.znty.rrs.config;

import com.ql.util.express.ExpressRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 应用全局配置
 *
 * 注册规则服务使用的 QLExpress 执行器，并配置浏览器跨域访问 API 的规则
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    /**
     * 注册供规则服务共用的 QLExpress 脚本执行器
     *
     * @return 由 Spring 管理的脚本执行器单例
     */
    @Bean
    public ExpressRunner expressRunner() {
        return new ExpressRunner();
    }

    /**
     * 配置浏览器跨域访问 API 的规则
     *
     * 执行顺序：
     * 1. 为 API 路径注册规则，使用来源模式匹配任意来源
     * 2. 接受 GET、POST 和 OPTIONS 方法及任意请求头，允许携带凭证，预检结果可缓存 3600 秒
     *
     * @param registry Spring MVC 提供的跨域规则注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 允许携带凭证时，通过来源模式匹配任意来源
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
