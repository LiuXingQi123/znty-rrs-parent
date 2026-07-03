package com.znty.rrs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智慧风控平台 — 统一后端服务启动入口。
 */
@SpringBootApplication
@MapperScan("com.znty.rrs.**.mapper")
public class ZntyRrsApplication {

    /** 应用程序入口，启动 Spring Boot 容器并扫描所有 Mapper 接口。 */
    public static void main(String[] args) {
        SpringApplication.run(ZntyRrsApplication.class, args);
    }
}
