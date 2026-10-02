-- ============================================================
-- znty-rrs 基金基础信息表建表脚本
-- MySQL version: 8.0.33
-- 说明：
--   1. 本脚本仅维护基金基础信息表
--   2. 基金数据与债券证券主数据分开维护
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `znty_rrs`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `rrs_fundinfo`;

CREATE TABLE `rrs_fundinfo`
(
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `fund_code`               VARCHAR(100) DEFAULT NULL COMMENT '基金代码，场内基金建议保存带市场后缀的完整代码',
    `fund_name`               VARCHAR(300) DEFAULT NULL COMMENT '基金全称',
    `fund_short_name`         VARCHAR(100) DEFAULT NULL COMMENT '基金简称',
    `security_type`           VARCHAR(64)  DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `security_status`         VARCHAR(1)   DEFAULT NULL COMMENT '基金状态：L=存续或上市中 / N=待成立或待上市 / D=终止或退市 / U=未知',
    `market_code`             VARCHAR(32)  DEFAULT NULL COMMENT '交易市场编码，如：SSE=上海证券交易所 / SZSE=深圳证券交易所 / OTC=场外市场 / HKEX=香港交易所',
    `currency_code`           VARCHAR(10)  DEFAULT NULL COMMENT '币种编码，如：CNY / HKD / USD',
    `establishment_date`      DATE         DEFAULT NULL COMMENT '基金成立日期',
    `list_date`               DATE         DEFAULT NULL COMMENT '上市日期，场外基金可为空',
    `delist_date`             DATE         DEFAULT NULL COMMENT '退市或终止日期',
    `fund_company_code`       VARCHAR(100) DEFAULT NULL COMMENT '基金管理公司代码',
    `fund_company_name`       VARCHAR(300) DEFAULT NULL COMMENT '基金管理公司名称',
    `is_h`                    TINYINT(1)   DEFAULT NULL COMMENT '是否为港股标的：0=否 / 1=是',
    `is_hksc`                 TINYINT(1)   DEFAULT NULL COMMENT '是否属于港股通标的：0=否 / 1=是',
    `latest_nav`              DECIMAL(20, 6) DEFAULT NULL COMMENT '最新单位净值，币种见 currency_code',
    `fund_manager_names`      VARCHAR(1000) DEFAULT NULL COMMENT '基金经理姓名，多个使用英文逗号分隔',
    `fund_administrator`      VARCHAR(300) DEFAULT NULL COMMENT '基金管理人',
    `fund_custodian`          VARCHAR(300) DEFAULT NULL COMMENT '基金托管人',
    `accumulated_nav`         DECIMAL(20, 6) DEFAULT NULL COMMENT '累计净值，币种见 currency_code',
    `investment_style`        VARCHAR(100) DEFAULT NULL COMMENT '投资风格，如：成长型 / 价值型 / 平衡型',
    `trade_price`             DECIMAL(20, 6) DEFAULT NULL COMMENT '最新交易价格，币种见 currency_code',
    `issue_scale`             DECIMAL(20, 4) DEFAULT NULL COMMENT '发行规模(亿元)，币种见 currency_code',
    `issue_term`              VARCHAR(100) DEFAULT NULL COMMENT '发行期限，保存来源展示值，如：不定期 / 3年',
    `daily_ten_thousand_income` DECIMAL(20, 6) DEFAULT NULL COMMENT '日万份收益',
    `seven_day_annualized_yield` DECIMAL(10, 6) DEFAULT NULL COMMENT '7日年化收益率(%)，按百分数保存，如：1.25 表示 1.25%',
    `latest_scale`            DECIMAL(20, 4) DEFAULT NULL COMMENT '最新规模(亿元)，币种见 currency_code',
    `previous_close_price`    DECIMAL(20, 6) DEFAULT NULL COMMENT '昨收盘价，币种见 currency_code',
    `premium_discount_rate`   DECIMAL(10, 6) DEFAULT NULL COMMENT '折溢价率(%)，正数为溢价 / 负数为折价',
    `data_date`               DATE         DEFAULT NULL COMMENT '最新净值、行情和规模对应的业务日期',
    `source_system`           VARCHAR(32)  DEFAULT NULL COMMENT '数据来源系统',
    `memo`                    VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`              TINYINT(1)   DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`               DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`               DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '基金基础信息表';
