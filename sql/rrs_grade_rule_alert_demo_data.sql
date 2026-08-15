-- ============================================================
-- znty-rrs 不符合主体债入库规则提醒 - 演示数据
-- MySQL version: 8.0.33
-- 说明：待办由定时任务扫描生成，演示环境不预置业务行
-- ============================================================

USE `znty_rrs`;
SET NAMES utf8mb4;

DELETE FROM `ip_grade_rule_alert_evt`;
DELETE FROM `ip_grade_rule_alert`;
