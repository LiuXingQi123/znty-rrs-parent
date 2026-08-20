-- ============================================================
-- znty-rrs 临时代码管理表 - 演示数据脚本
-- MySQL version: 8.0.33
-- 说明：temporary / updated / cancelled 各 1 条；主体与正式代码对齐证券主数据
--   TMP001.IB 仍为临时代码（rrs_securityinfo 占位 security_source=temporary, status=L）
--   TMP002.IB 已更新为正式证券 ABN002.IB（25国贸ABN）
--   TMP003.IB 已取消发行（rrs_securityinfo 占位 security_source=temporary, status=D）
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `rrs_temp_security_code`;
TRUNCATE TABLE `rrs_temp_security_code_update_log`;

INSERT INTO `rrs_temp_security_code` (
    `id`
    ,`temp_security_name`
    ,`temp_security_code`
    ,`temp_security_market`
    ,`temp_security_type`
    ,`temp_mitigation_code`
    ,`temp_company_code`
    ,`temp_company_name_snapshot`
    ,`temp_issue_date`
    ,`temp_maturity_date`
    ,`security_name`
    ,`security_code`
    ,`security_market`
    ,`security_type`
    ,`update_time`
    ,`status`
    ,`is_deleted`
    ,`oprt_source`
    ,`memo`
    ,`crte_time`
    ,`updt_time`
) VALUES
(1, '某交投临时中票', 'TMP001.IB', 'CIBM', 'mtn', NULL, 'C10001', '某交投集团', '2026-07-01', '2031-07-01', NULL, NULL, NULL, NULL, NULL, 'temporary', 0, 'manual', NULL, NOW(), NOW()),
(2, '某国贸临时ABN', 'TMP002.IB', 'CIBM', 'abn', NULL, 'C10009', '某国贸公司', '2024-03-15', '2029-03-18', '25国贸ABN01', 'ABN002.IB', 'CIBM', 'abn', '2026-06-26 10:30:00', 'updated', 0, 'manual', NULL, NOW(), NOW()),
(3, '某能源临时金融债', 'TMP003.IB', 'CIBM', 'financial_bond', NULL, 'C10003', '某能源集团', '2026-08-01', '2029-08-01', NULL, NULL, NULL, NULL, '2026-06-26 11:10:00', 'cancelled', 0, 'manual', NULL, NOW(), NOW());

INSERT INTO `rrs_temp_security_code_update_log` (
    `temp_security_name`
    ,`temp_security_code`
    ,`temp_security_market`
    ,`temp_security_type`
    ,`security_name`
    ,`security_code`
    ,`security_market`
    ,`security_type`
    ,`replace_table_name`
    ,`replace_record_id`
    ,`replace_status`
    ,`replace_time`
    ,`crte_time`
) VALUES
('某国贸临时ABN', 'TMP002.IB', 'CIBM', 'abn', '25国贸ABN01', 'ABN002.IB', 'CIBM', 'abn', 'rrs_securityinfo', NULL, 'success', '2026-06-26 10:30:00', NOW());
