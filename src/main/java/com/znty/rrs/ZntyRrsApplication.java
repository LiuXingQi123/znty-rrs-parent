package com.znty.rrs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智慧风控平台后端启动入口
 *
 * 通过 {@link MapperScan} 扫描项目各 mapper 包中的 MyBatis 接口
 * 通过 {@link EnableScheduling} 启用 Spring 定时任务支持
 */
@SpringBootApplication
@MapperScan("com.znty.rrs.**.mapper")
@EnableScheduling
public class ZntyRrsApplication {

    /**
     * 使用当前应用配置启动 Spring Boot 容器
     *
     * @param args 传给 Spring Boot 的命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ZntyRrsApplication.class, args);
    }
}
