-- ============================================================
-- CRMW在库规则提醒，按第十八条检查创设方或保护方风险
-- MySQL version: 8.0.33
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 清空业务表
-- ----------------------------------------------------------------------------
-- 清空CRMW规则提醒操作审计
TRUNCATE TABLE `ip_crmw_rule_alert_evt`;
-- 清空CRMW规则提醒
TRUNCATE TABLE `ip_crmw_rule_alert`;

-- 提醒由定时任务扫描实际在池组合生成，不预置与池状态无关的虚构待办
