-- ============================================================
-- znty-rrs 基金临时代码表建表脚本
-- MySQL version: 8.0.33
-- 说明：维护临时基金代码与正式基金代码的映射及业务状态
-- 按先删后建顺序重建三张表，执行会清除原表数据
-- 删除时由业务操作同时设置 status=deleted 与 is_deleted=1
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `znty_rrs`;
SET NAMES utf8mb4;

-- 删除旧表：先删除登记审计及替换明细，再删除登记主表。
DROP TABLE IF EXISTS `rrs_temp_fund_code_evt`;
DROP TABLE IF EXISTS `rrs_temp_fund_code_update_log`;
DROP TABLE IF EXISTS `rrs_temp_fund_code`;

-- 创建登记主表。
CREATE TABLE IF NOT EXISTS `rrs_temp_fund_code` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `temp_fund_code`       VARCHAR(100) DEFAULT NULL COMMENT '临时基金代码',
    `temp_fund_short_name` VARCHAR(100) DEFAULT NULL COMMENT '临时基金简称',
    `temp_security_type`   VARCHAR(64)  DEFAULT NULL COMMENT '临时基金产品类型，关联 dict_security_type.security_type（category_type=fund）',
    `temp_market_code`     VARCHAR(32)  DEFAULT NULL COMMENT '临时基金交易市场编码',
    `fund_code`            VARCHAR(100) DEFAULT NULL COMMENT '正式基金代码，关联 rrs_fundinfo.fund_code',
    `fund_name`            VARCHAR(300) DEFAULT NULL COMMENT '正式基金全称快照',
    `fund_short_name`      VARCHAR(100) DEFAULT NULL COMMENT '正式基金简称快照',
    `market_code`          VARCHAR(32)  DEFAULT NULL COMMENT '正式基金交易市场编码快照',
    `security_type`        VARCHAR(64)  DEFAULT NULL COMMENT '正式基金产品类型快照，关联 dict_security_type.security_type（category_type=fund）',
    `update_time`          DATETIME     DEFAULT NULL COMMENT '最近状态变更时间（转正、取消或删除；新增时为空）',
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

-- 保存登记记录的完整变更快照，删除为业务软删除。
CREATE TABLE IF NOT EXISTS `rrs_temp_fund_code_evt` (
    `evt_id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`                   BIGINT       DEFAULT NULL COMMENT '主键 ID',
    `temp_fund_code`       VARCHAR(100) DEFAULT NULL COMMENT '临时基金代码',
    `temp_fund_short_name` VARCHAR(100) DEFAULT NULL COMMENT '临时基金简称',
    `temp_security_type`   VARCHAR(64)  DEFAULT NULL COMMENT '临时基金产品类型，关联 dict_security_type.security_type（category_type=fund）',
    `temp_market_code`     VARCHAR(32)  DEFAULT NULL COMMENT '临时基金交易市场编码',
    `fund_code`            VARCHAR(100) DEFAULT NULL COMMENT '正式基金代码，关联 rrs_fundinfo.fund_code',
    `fund_name`            VARCHAR(300) DEFAULT NULL COMMENT '正式基金全称快照',
    `fund_short_name`      VARCHAR(100) DEFAULT NULL COMMENT '正式基金简称快照',
    `market_code`          VARCHAR(32)  DEFAULT NULL COMMENT '正式基金交易市场编码快照',
    `security_type`        VARCHAR(64)  DEFAULT NULL COMMENT '正式基金产品类型快照，关联 dict_security_type.security_type（category_type=fund）',
    `update_time`          DATETIME     DEFAULT NULL COMMENT '最近状态变更时间（转正、取消或删除；新增时为空）',
    `status`               VARCHAR(32)  DEFAULT NULL COMMENT '状态：temporary=临时 / updated=已更新 / cancelled=已取消 / deleted=已删除',
    `is_deleted`           TINYINT(1)   DEFAULT NULL COMMENT '是否删除：0=否 / 1=是',
    `oprt_source`          VARCHAR(32)  DEFAULT NULL COMMENT '操作来源：manual=人工 / job=定时任务 / other=其他',
    `memo`                 VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `crte_time`            DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`            DATETIME     DEFAULT NULL COMMENT '更新时间',
    `opter_id`             VARCHAR(20)  DEFAULT NULL COMMENT '经办人 ID',
    `opt_time`             DATETIME     DEFAULT NULL COMMENT '经办时间',
    `oprt_type`            VARCHAR(20)  DEFAULT NULL COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '基金临时代码表（操作审计）';

-- 按实际被替换的基金业务记录记录成功结果，异常随业务事务整体回滚。
CREATE TABLE IF NOT EXISTS `rrs_temp_fund_code_update_log` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `temp_code_id`         BIGINT       DEFAULT NULL COMMENT '临时基金登记记录 ID',
    `temp_fund_code`       VARCHAR(100) DEFAULT NULL COMMENT '原临时基金代码',
    `temp_fund_short_name` VARCHAR(100) DEFAULT NULL COMMENT '原临时基金简称',
    `temp_market_code`     VARCHAR(32)  DEFAULT NULL COMMENT '原临时基金交易市场编码',
    `temp_security_type`   VARCHAR(64)  DEFAULT NULL COMMENT '原临时基金产品类型',
    `fund_code`            VARCHAR(100) DEFAULT NULL COMMENT '正式基金代码',
    `fund_name`            VARCHAR(300) DEFAULT NULL COMMENT '正式基金全称',
    `fund_short_name`      VARCHAR(100) DEFAULT NULL COMMENT '正式基金简称',
    `market_code`          VARCHAR(32)  DEFAULT NULL COMMENT '正式基金交易市场编码',
    `security_type`        VARCHAR(64)  DEFAULT NULL COMMENT '正式基金产品类型',
    `replace_table_name`   VARCHAR(100) DEFAULT NULL COMMENT '被替换的基金业务表名',
    `replace_record_id`    BIGINT       DEFAULT NULL COMMENT '被替换的基金业务记录 ID',
    `replace_status`       VARCHAR(32)  DEFAULT NULL COMMENT '替换状态：success=成功',
    `replace_time`         DATETIME     DEFAULT NULL COMMENT '替换时间',
    `operator_id`          VARCHAR(20)  DEFAULT NULL COMMENT '操作人 ID',
    `crte_time`            DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`            DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '临时基金代码替换日志';
