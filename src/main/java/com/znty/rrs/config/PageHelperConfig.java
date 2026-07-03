package com.znty.rrs.config;

import com.github.pagehelper.PageHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * PageHelper 分页插件配置，向 Spring 容器注册并初始化分页插件参数。
 */
@Configuration
public class PageHelperConfig {

    /** 创建并配置 PageHelper Bean，设置分页行为参数。 */
    @Bean
    public PageHelper pageHelper() {
        PageHelper pageHelper = new PageHelper();
        Properties p = new Properties();
        p.setProperty("offsetAsPageNum", "true");       // 将 RowBounds 的 offset 参数当作页码使用
        p.setProperty("rowBoundsWithCount", "true");    // 使用 RowBounds 分页时同时执行 count 查询
        p.setProperty("reasonable", "true");            // 页码合理化：超出范围时自动修正到首页或末页
        pageHelper.setProperties(p);
        return pageHelper;
    }
}
