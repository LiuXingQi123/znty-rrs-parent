-- ============================================================
-- znty-rrs 字典/配置表 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，创建证券类型等字典配置表结构
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `dict_security_type`;

-- ----------------------------------------------------------------------------
-- 证券类型字典表
-- ----------------------------------------------------------------------------
CREATE TABLE `dict_security_type` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `security_type`      VARCHAR(64) DEFAULT NULL            COMMENT '证券类型编码，如：company_bond、mtn、ncd、a_share、etf_fund',
    `security_type_name` VARCHAR(64) DEFAULT NULL            COMMENT '证券类型名称，如：公司债、中期票据、同业存单、A股、ETF基金',
    `category_type`      VARCHAR(32) DEFAULT NULL            COMMENT '大类类型编码：bond=债券 / stock=股票 / fund=基金 / company=公司主体',
    `category_type_name` VARCHAR(32) DEFAULT NULL            COMMENT '大类类型名称，如：债券、股票、基金、公司主体',
    `sort_order`         INT         DEFAULT NULL            COMMENT '排序序号',
    `is_deleted`         TINYINT(1)  DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`          DATETIME    DEFAULT NULL            COMMENT '创建时间',
    `updt_time`          DATETIME    DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '证券类型字典表';
