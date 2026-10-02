-- ============================================================
-- znty-rrs 基金逐日净值表建表脚本
-- MySQL version: 8.0.33
-- 说明：
--   1. 本脚本仅维护基金逐日净值表
--   2. 每只基金按交易日期保存一条权威来源记录
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `znty_rrs`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `rrs_fund_nav`;

CREATE TABLE `rrs_fund_nav`
(
    `id`                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `fund_code`                  VARCHAR(100) DEFAULT NULL COMMENT '基金代码，与 rrs_fundinfo.fund_code 逻辑关联',
    `trade_date`                 DATE         DEFAULT NULL COMMENT '交易日期',
    `announcement_date`          DATE         DEFAULT NULL COMMENT '公告日期',
    `currency_code`              VARCHAR(10)  DEFAULT NULL COMMENT '币种编码，如：CNY / HKD / USD',
    `unit_nav`                   DECIMAL(20, 6) DEFAULT NULL COMMENT '单位净值，币种见 currency_code，按基金份额计',
    `unit_nav_growth_rate`        DECIMAL(10, 6) DEFAULT NULL COMMENT '单位净值增长率(%)，按百分数保存，如：1.25 表示 1.25%',
    `accumulated_nav`            DECIMAL(20, 6) DEFAULT NULL COMMENT '累计净值，币种见 currency_code',
    `daily_ten_thousand_income`  DECIMAL(20, 6) DEFAULT NULL COMMENT '每万份基金份额的日收益金额，币种见 currency_code',
    `seven_day_annualized_yield` DECIMAL(10, 6) DEFAULT NULL COMMENT '7日年化收益率(%)，按百分数保存，如：1.25 表示 1.25%',
    `source_system`              VARCHAR(32)  DEFAULT NULL COMMENT '数据来源系统',
    `memo`                       VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`                 TINYINT(1)   DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`                  DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`                  DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '基金逐日净值表';
