package com.znty.sirm.config;

import com.ql.util.express.ExpressRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * 应用全局配置，包含 QLExpress 规则引擎 Bean 注册和开发期 CORS 跨域配置。
 */
@Configuration
public class AppConfig extends WebMvcConfigurerAdapter {

    /** 注册 QLExpress 脚本执行器单例。 */
    @Bean
    public ExpressRunner expressRunner() {
        return new ExpressRunner();
    }

    /** 配置 API 跨域访问规则。 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
