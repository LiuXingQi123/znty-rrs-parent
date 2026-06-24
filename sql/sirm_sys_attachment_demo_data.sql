-- ============================================================
-- znty-sirm 系统附件关联表 - 演示数据脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，插入系统附件关联表测试数据
-- ============================================================

USE znty_sirm;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 清空系统附件关联表
-- ----------------------------------------------------------------------------
TRUNCATE TABLE `sys_attachment`;

-- ----------------------------------------------------------------------------
-- 系统附件关联表插入测试数据
-- ----------------------------------------------------------------------------
INSERT INTO `sys_attachment` (
    `table_name`
    ,`main_id`
    ,`attachment_category`
    ,`file_type`
    ,`original_file_name`
    ,`new_file_name`
    ,`file_size`
    ,`content_type`
    ,`full_url`
    ,`file_name`
    ,`uploader_id`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
) VALUES
('ip_adjust_log', 1, 'credit_report', 'pdf', 'report_20260508_001.pdf',
 'credit_report_20260508090000_1.pdf', 1536000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260508/credit_report_20260508090000_1.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 1, 'material', 'pdf', 'material_20260508_001.pdf',
 'material_20260508090000_1.pdf', 512000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260508/material_20260508090000_1.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 2, 'credit_report', 'pdf', 'report_20260510_002.pdf',
 'credit_report_20260510100000_2.pdf', 2048000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260510/credit_report_20260510100000_2.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 2, 'material', 'pdf', 'material_20260510_002.pdf',
 'material_20260510100000_2.pdf', 768000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260510/material_20260510100000_2.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 5, 'credit_report', 'xlsx', 'batch_import_20260518.xlsx',
 'credit_report_20260518160000_5.xlsx', 102400,
 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
 '/api/v1/attachments/downloadAttachment', '20260518/credit_report_20260518160000_5.xlsx',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 5, 'material', 'pdf', 'approval_20260518_signed.pdf',
 'material_20260518160000_5.pdf', 614400, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260518/material_20260518160000_5.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 8, 'credit_report', 'pdf', 'report_20260528_008.pdf',
 'credit_report_20260528093000_8.pdf', 1843200, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260528/credit_report_20260528093000_8.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 8, 'material', 'pdf', 'material_20260528_008.pdf',
 'material_20260528093000_8.pdf', 409600, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260528/material_20260528093000_8.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 18, 'credit_report', 'pdf', 'report_batch_20260605_001.pdf',
 'credit_report_20260605103000_18.pdf', 2252800, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260605/credit_report_20260605103000_18.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 18, 'material', 'pdf', 'material_batch_20260605_001.pdf',
 'material_20260605103000_18.pdf', 716800, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260605/material_20260605103000_18.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 21, 'credit_report', 'pdf', 'report_o32_20260608_001.pdf',
 'credit_report_20260608100000_21.pdf', 1945600, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260608/credit_report_20260608100000_21.pdf',
 '1001', 0, NOW(), NOW()),
('ip_adjust_log', 21, 'material', 'json', 'o32_result_20260608_001.json',
 'material_20260608100000_21.json', 20480, 'application/json',
 '/api/v1/attachments/downloadAttachment', '20260608/material_20260608100000_21.json',
 '1001', 0, NOW(), NOW());

