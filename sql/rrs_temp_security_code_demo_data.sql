-- ============================================================
-- znty-rrs 临时代码管理表 - 演示数据脚本
-- MySQL version: 8.0.28
-- 说明：初始化临时代码管理演示数据
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
    ,`temp_company_id`
    ,`temp_company_name_snapshot`
    ,`temp_issue_date`
    ,`temp_maturity_date`
    ,`security_name`
    ,`security_code`
    ,`security_market`
    ,`security_type`
    ,`update_time`
    ,`status`
    ,`operation_type`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
) VALUES
(1, '某基建集团临时中票', 'TMP20260626001', 'CIBM', 'mtn', 'CRMVTMP001', 1, '某基础设施建设投资集团有限公司', '2026-07-01', '2031-07-01', NULL, NULL, NULL, NULL, NULL, 'temporary', 'add', 0, NOW(), NOW()),
(2, '某国贸临时企业债', 'TMP20260626002', 'SSE', 'corporate_bond', NULL, 2, '某国际贸易有限公司', '2026-07-15', '2033-07-15', '26某国贸债01', '126000001.SH', 'SSE', 'corporate_bond', '2026-06-26 10:30:00', 'updated', 'update', 0, NOW(), NOW()),
(3, '某能源临时金融债', 'TMP20260626003', 'CIBM', 'financial_bond', NULL, 3, '某能源集团股份有限公司', '2026-08-01', '2029-08-01', NULL, NULL, NULL, NULL, '2026-06-26 11:10:00', 'cancelled', 'cancel_issue', 0, NOW(), NOW());
