package com.znty.sirm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智慧风控平台 — 统一后端服务启动入口。
 */
@SpringBootApplication
@MapperScan("com.znty.sirm.**.mapper")
public class ZntySirmApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZntySirmApplication.class, args);
    }
}
