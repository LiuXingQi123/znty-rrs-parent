-- ============================================================
-- znty-rrs 基金临时代码表演示数据脚本
-- MySQL version: 8.0.33
-- 说明：演示初始化会清空临时代码登记、登记审计和替换日志
-- 不生成演示登记；基金占位主档由基金主档脚本及新增接口维护
-- ============================================================

USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `rrs_temp_fund_code_evt`;
TRUNCATE TABLE `rrs_temp_fund_code_update_log`;
TRUNCATE TABLE `rrs_temp_fund_code`;
