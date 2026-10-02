-- ============================================================
-- znty-rrs 基金临时代码表建表脚本
-- MySQL version: 8.0.33
-- 说明：维护临时基金代码与正式基金代码的映射及业务状态
-- 删除时由业务操作同时设置 status=deleted 与 is_deleted=1
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `znty_rrs`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `rrs_temp_fund_code`;

CREATE TABLE `rrs_temp_fund_code` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `temp_fund_code`       VARCHAR(100) DEFAULT NULL COMMENT '临时基金代码',
    `temp_fund_short_name` VARCHAR(100) DEFAULT NULL COMMENT '临时基金简称',
    `temp_security_type`   VARCHAR(64)  DEFAULT NULL COMMENT '临时基金产品类型，关联 dict_security_type.security_type',
    `temp_market_code`     VARCHAR(32)  DEFAULT NULL COMMENT '临时基金交易市场编码',
    `fund_code`            VARCHAR(100) DEFAULT NULL COMMENT '正式基金代码，关联 rrs_fundinfo.fund_code',
    `status`               VARCHAR(32)  DEFAULT NULL COMMENT '状态：temporary=临时 / updated=已更新 / cancelled=已取消 / deleted=已删除',
    `is_deleted`           TINYINT(1)   DEFAULT NULL COMMENT '是否删除：0=否 / 1=是',
    `oprt_source`          VARCHAR(32)  DEFAULT NULL COMMENT '操作来源：manual=人工 / job=定时任务 / other=其他',
    `memo`                 VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `crte_time`            DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`            DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '基金临时代码表';
