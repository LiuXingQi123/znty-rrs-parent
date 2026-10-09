-- ============================================================
-- znty-rrs 股票基础信息、研究评级历史、多人分管建表脚本
-- MySQL version: 8.0.33
-- 独立股票脚本，不纳入 INIT_SCHEMA / INIT_DEMO / RESET_ALL。
-- ============================================================

-- 1. 环境初始化
CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `znty_rrs`;
SET NAMES utf8mb4;

-- 2. 删除旧表（若存在）
-- 仅维护只读股票数据，评级本身保留历史；不建立主档冗余评级或完整信息快照。
DROP TABLE IF EXISTS `rrs_stock_manager`;
DROP TABLE IF EXISTS `rrs_stock_rating`;
DROP TABLE IF EXISTS `rrs_stockinfo`;

-- ============================================================================
-- 3.1 股票基础信息表
-- ============================================================================
CREATE TABLE `rrs_stockinfo`
(
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT      COMMENT '主键 ID',
    `stock_code`              VARCHAR(32)     DEFAULT NULL                 COMMENT '股票代码，包含市场后缀',
    `stock_name`              VARCHAR(300)    DEFAULT NULL                 COMMENT '股票名称',
    `stock_short_name`        VARCHAR(100)    DEFAULT NULL                 COMMENT '股票简称',
    `industry_code`           VARCHAR(100)    DEFAULT NULL                 COMMENT '所属行业编码',
    `industry_name`           VARCHAR(100)    DEFAULT NULL                 COMMENT '所属行业名称',
    `security_type`           VARCHAR(64)     DEFAULT NULL                 COMMENT '证券类型：a_share=A股 / hk_share=港股，关联 dict_security_type.security_type',
    `security_status`         VARCHAR(1)      DEFAULT NULL                 COMMENT '证券状态：L=上市 / D=退市 / N=未上市',
    `market_code`             VARCHAR(32)     DEFAULT NULL                 COMMENT '市场：SSE=沪市 / SZSE=深市 / HKEX=港股',
    `currency_code`           VARCHAR(10)     DEFAULT NULL                 COMMENT '报价币种：CNY=人民币 / HKD=港币',
    `list_date`               DATE            DEFAULT NULL                 COMMENT '上市日期',
    `delist_date`             DATE            DEFAULT NULL                 COMMENT '退市日期',
    `previous_close_price`    DECIMAL(20, 6)  DEFAULT NULL                 COMMENT '昨收，单位为报价币种',
    `high_price`              DECIMAL(20, 6)  DEFAULT NULL                 COMMENT '最高价，单位为报价币种',
    `low_price`               DECIMAL(20, 6)  DEFAULT NULL                 COMMENT '最低价，单位为报价币种',
    `average_price`           DECIMAL(20, 6)  DEFAULT NULL                 COMMENT '平均价，单位为报价币种',
    `turnover_rate`           DECIMAL(10, 6)  DEFAULT NULL                 COMMENT '换手率，单位为百分比，1.25 表示 1.25%',
    `trade_volume`            DECIMAL(20, 4)  DEFAULT NULL                 COMMENT '交易总量，单位为手，直接保存来源手数，港股不固定按100股换算',
    `trade_market_value`      DECIMAL(20, 4)  DEFAULT NULL                 COMMENT '当日成交金额，单位为万报价币种',
    `total_shares`            DECIMAL(20, 4)  DEFAULT NULL                 COMMENT '总股本，单位为百万股',
    `total_market_value`      DECIMAL(20, 4)  DEFAULT NULL                 COMMENT '总市值，单位为百万报价币种',
    `circulating_a_shares`    DECIMAL(20, 4)  DEFAULT NULL                 COMMENT '流通A股，单位为百万股，港股可为空',
    `a_share_market_value`    DECIMAL(20, 4)  DEFAULT NULL                 COMMENT 'A股市值，单位为百万报价币种，港股可为空',
    `data_date`               DATE            DEFAULT NULL                 COMMENT '行情数据日期',
    `source_system`           VARCHAR(32)     DEFAULT NULL                 COMMENT '数据来源',
    `memo`                    VARCHAR(512)    DEFAULT NULL                 COMMENT '备注',
    `is_deleted`              TINYINT(1)      DEFAULT NULL                 COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`               DATETIME        DEFAULT NULL                 COMMENT '创建时间',
    `updt_time`               DATETIME        DEFAULT NULL                 COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '股票基础信息表';

-- ============================================================================
-- 3.2 股票研究评级历史表
-- ============================================================================
CREATE TABLE `rrs_stock_rating`
(
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT      COMMENT '主键 ID',
    `stock_code`              VARCHAR(32)     DEFAULT NULL                 COMMENT '股票代码，关联 rrs_stockinfo.stock_code',
    `rating_code`             VARCHAR(32)     DEFAULT NULL                 COMMENT '研究评级：buy=买入 / overweight=增持 / neutral=中性 / underweight=减持 / sell=卖出',
    `rating_date`             DATETIME        DEFAULT NULL                 COMMENT '评级生效时间，同一时间按 id 降序确定先后',
    `researcher_id`           VARCHAR(32)     DEFAULT NULL                 COMMENT '评级研究员 ID',
    `remark`                  VARCHAR(1000)   DEFAULT NULL                 COMMENT '评级说明',
    `is_deleted`              TINYINT(1)      DEFAULT NULL                 COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`               DATETIME        DEFAULT NULL                 COMMENT '创建时间',
    `updt_time`               DATETIME        DEFAULT NULL                 COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '股票研究评级历史表';

-- ============================================================================
-- 3.3 股票多人分管关联表
-- ============================================================================
CREATE TABLE `rrs_stock_manager`
(
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT      COMMENT '主键 ID',
    `stock_code`              VARCHAR(32)     DEFAULT NULL                 COMMENT '股票代码，关联 rrs_stockinfo.stock_code',
    `user_id`                 VARCHAR(32)     DEFAULT NULL                 COMMENT '分管人员 ID，同一股票可有多名有效分管人员',
    `is_deleted`              TINYINT(1)      DEFAULT NULL                 COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`               DATETIME        DEFAULT NULL                 COMMENT '创建时间',
    `updt_time`               DATETIME        DEFAULT NULL                 COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '股票多人分管关联表';
