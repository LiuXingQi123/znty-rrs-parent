-- ============================================================
-- znty-rrs 信用债期限-主体内评分档-投资池关系 - 建表脚本
-- MySQL version: 8.0.33
-- 说明：创建信用债大库评级准入矩阵配置表
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `credit_bond_pool_grade_rule`;
DROP TABLE IF EXISTS `credit_bond_inner_rating_grade`;
DROP TABLE IF EXISTS `credit_bond_term_bucket`;

-- ----------------------------------------------------------------------------
-- 2. 创建主表
-- ----------------------------------------------------------------------------
CREATE TABLE `credit_bond_term_bucket` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `bucket_code`      VARCHAR(32)   DEFAULT NULL            COMMENT '期限分组编码：GT_5=期限大于5年 / GT_3_LE_5=期限大于3年且小于等于5年 / GT_1_LE_3=期限大于1年且小于等于3年 / LE_1=期限小于等于1年',
    `bucket_name`      VARCHAR(64)   DEFAULT NULL            COMMENT '期限分组名称',
    `min_term_year`    DECIMAL(20,4) DEFAULT NULL            COMMENT '期限下限年数，空表示无下限',
    `min_inclusive`    TINYINT(1)    DEFAULT NULL            COMMENT '是否包含期限下限：1=包含 / 0=不包含',
    `max_term_year`    DECIMAL(20,4) DEFAULT NULL            COMMENT '期限上限年数，空表示无上限',
    `max_inclusive`    TINYINT(1)    DEFAULT NULL            COMMENT '是否包含期限上限：1=包含 / 0=不包含',
    `expression_text`  VARCHAR(128)  DEFAULT NULL            COMMENT '期限分组表达式，用于页面展示',
    `sort_no`          INT           DEFAULT NULL            COMMENT '排序序号',
    `enabled`          TINYINT(1)    DEFAULT NULL            COMMENT '是否启用：1=启用 / 0=停用',
    `crte_time`        DATETIME      DEFAULT NULL            COMMENT '创建时间',
    `updt_time`        DATETIME      DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_credit_bond_term_bucket_code` (`bucket_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '信用债期限分组配置表';

CREATE TABLE `credit_bond_inner_rating_grade` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `grade_code`  VARCHAR(32) DEFAULT NULL            COMMENT '主体内评分档编码，如：1、2+、2、2-、3+、3、3-、4',
    `grade_name`  VARCHAR(64) DEFAULT NULL            COMMENT '主体内评分档名称',
    `sort_no`     INT         DEFAULT NULL            COMMENT '排序序号',
    `enabled`     TINYINT(1)  DEFAULT NULL            COMMENT '是否启用：1=启用 / 0=停用',
    `crte_time`   DATETIME    DEFAULT NULL            COMMENT '创建时间',
    `updt_time`   DATETIME    DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_credit_bond_inner_rating_grade_code` (`grade_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '信用债主体内评分档字典表';

CREATE TABLE `credit_bond_pool_grade_rule` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `term_bucket_id`        BIGINT       DEFAULT NULL            COMMENT '期限分组 ID，关联 credit_bond_term_bucket.id',
    `inner_rating_grade_id` BIGINT       DEFAULT NULL            COMMENT '主体内评分档 ID，关联 credit_bond_inner_rating_grade.id',
    `pool_id`               BIGINT       DEFAULT NULL            COMMENT '投资池 ID，关联 ip_investment_pool.id，仅允许信用债大库一级库至五级库',
    `pool_code_snapshot`    VARCHAR(64)  DEFAULT NULL            COMMENT '投资池编码快照',
    `pool_name_snapshot`    VARCHAR(128) DEFAULT NULL            COMMENT '投资池名称快照',
    `enabled`               TINYINT(1)   DEFAULT NULL            COMMENT '是否启用：1=启用 / 0=停用',
    `sort_no`               INT          DEFAULT NULL            COMMENT '排序序号',
    `crte_time`             DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`             DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_credit_bond_pool_grade_rule` (`term_bucket_id`, `inner_rating_grade_id`, `pool_id`),
    KEY `idx_credit_bond_pool_grade_rule_pool_id` (`pool_id`),
    KEY `idx_credit_bond_pool_grade_rule_term_grade` (`term_bucket_id`, `inner_rating_grade_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '信用债期限主体内评分档投资池关系表';

SET FOREIGN_KEY_CHECKS = 1;
