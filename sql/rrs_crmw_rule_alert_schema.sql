-- ============================================================
-- CRMW在库规则提醒，按第十八条检查创设方或保护方风险
-- MySQL version: 8.0.33
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
-- 删除CRMW规则提醒（操作审计）
DROP TABLE IF EXISTS `ip_crmw_rule_alert_evt`;
-- 删除CRMW规则提醒
DROP TABLE IF EXISTS `ip_crmw_rule_alert`;

-- ----------------------------------------------------------------------------
-- 1. CRMW规则提醒
-- ----------------------------------------------------------------------------
CREATE TABLE `ip_crmw_rule_alert` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `crmw_scode` VARCHAR(64) DEFAULT NULL COMMENT 'CRMW凭证代码',
    `crmw_name` VARCHAR(128) DEFAULT NULL COMMENT 'CRMW凭证名称',
    `security_code` VARCHAR(64) DEFAULT NULL COMMENT '标的证券代码',
    `security_short_name` VARCHAR(128) DEFAULT NULL COMMENT '标的证券简称',
    `issuer_code` VARCHAR(64) DEFAULT NULL COMMENT '创设方或保护方代码，取凭证主档发行主体',
    `issuer_name` VARCHAR(256) DEFAULT NULL COMMENT '创设方或保护方名称',
    `inner_issuer_rating` VARCHAR(16) DEFAULT NULL COMMENT '创设方或保护方最新主体内评',
    `current_pool_id` BIGINT DEFAULT NULL COMMENT '当前所在投资池 ID',
    `current_pool_name` VARCHAR(128) DEFAULT NULL COMMENT '当前所在投资池名称',
    `adjust_log_id` BIGINT DEFAULT NULL COMMENT '当前入库来源调库日志 ID',
    `fail_reason` VARCHAR(500) DEFAULT NULL COMMENT '命中的规则原因',
    `context_key` VARCHAR(64) DEFAULT NULL COMMENT '组合、入库来源、主体、内评及原因摘要',
    `alert_status` VARCHAR(8) DEFAULT NULL COMMENT '提醒状态：00=待处理 / 20=已处理 / 99=已失效',
    `last_scan_time` DATETIME DEFAULT NULL COMMENT '最近一次扫描命中时间',
    `deal_user_id` VARCHAR(32) DEFAULT NULL COMMENT '处理人 ID',
    `deal_user_name` VARCHAR(64) DEFAULT NULL COMMENT '处理人名称',
    `deal_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `is_deleted` TINYINT(1) DEFAULT NULL COMMENT '逻辑删除：0=正常 / 1=已删除',
    `crte_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `updt_time` DATETIME DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRMW规则提醒';

-- ----------------------------------------------------------------------------
-- 2. CRMW规则提醒（操作审计）
-- ----------------------------------------------------------------------------
CREATE TABLE `ip_crmw_rule_alert_evt` (
    `evt_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id` BIGINT DEFAULT NULL COMMENT '主键 ID',
    `crmw_scode` VARCHAR(64) DEFAULT NULL COMMENT 'CRMW凭证代码',
    `crmw_name` VARCHAR(128) DEFAULT NULL COMMENT 'CRMW凭证名称',
    `security_code` VARCHAR(64) DEFAULT NULL COMMENT '标的证券代码',
    `security_short_name` VARCHAR(128) DEFAULT NULL COMMENT '标的证券简称',
    `issuer_code` VARCHAR(64) DEFAULT NULL COMMENT '创设方或保护方代码，取凭证主档发行主体',
    `issuer_name` VARCHAR(256) DEFAULT NULL COMMENT '创设方或保护方名称',
    `inner_issuer_rating` VARCHAR(16) DEFAULT NULL COMMENT '创设方或保护方最新主体内评',
    `current_pool_id` BIGINT DEFAULT NULL COMMENT '当前所在投资池 ID',
    `current_pool_name` VARCHAR(128) DEFAULT NULL COMMENT '当前所在投资池名称',
    `adjust_log_id` BIGINT DEFAULT NULL COMMENT '当前入库来源调库日志 ID',
    `fail_reason` VARCHAR(500) DEFAULT NULL COMMENT '命中的规则原因',
    `context_key` VARCHAR(64) DEFAULT NULL COMMENT '组合、入库来源、主体、内评及原因摘要',
    `alert_status` VARCHAR(8) DEFAULT NULL COMMENT '提醒状态：00=待处理 / 20=已处理 / 99=已失效',
    `last_scan_time` DATETIME DEFAULT NULL COMMENT '最近一次扫描命中时间',
    `deal_user_id` VARCHAR(32) DEFAULT NULL COMMENT '处理人 ID',
    `deal_user_name` VARCHAR(64) DEFAULT NULL COMMENT '处理人名称',
    `deal_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `is_deleted` TINYINT(1) DEFAULT NULL COMMENT '逻辑删除：0=正常 / 1=已删除',
    `crte_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `updt_time` DATETIME DEFAULT NULL COMMENT '修改时间',
    `opter_id` VARCHAR(20) DEFAULT NULL COMMENT '经办人 ID',
    `opt_time` DATETIME DEFAULT NULL COMMENT '经办时间',
    `oprt_type` VARCHAR(20) DEFAULT NULL COMMENT '操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRMW规则提醒（操作审计）';
