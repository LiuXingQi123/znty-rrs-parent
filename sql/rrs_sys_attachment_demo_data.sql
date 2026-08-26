-- ============================================================
-- znty-rrs 系统附件关联表 - 演示数据脚本
-- MySQL version: 8.0.33
-- 说明：报告与附件的证券/主体代码对齐 rrs_securityinfo、t_inv_company；
--       ip_adjust_log 附件 main_id 对齐调库日志 1/2/3/7
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `rrs_report_out`;
TRUNCATE TABLE `rrs_report_in`;
TRUNCATE TABLE `sys_attachment`;

INSERT INTO `rrs_report_in` (
    `author_name`
    ,`report_title`
    ,`report_type`
    ,`security_code`
    ,`company_code`
    ,`security_type`
    ,`data_source`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
) VALUES
('张明远', '交投中票入库分析报告', 'bond_in_report', 'MTN001.IB', 'C10001', 'mtn', 'uploaded', 0, '2026-05-08 09:00:00', '2026-05-08 09:00:00'),
('李思涵', '交投信用债入库报告', 'bond_in_report', 'XYB001.IB', 'C10001', 'credit_bond', 'uploaded', 0, '2026-05-12 14:20:00', '2026-05-12 14:20:00'),
('王建强', '某资ABS二级库跟踪报告', 'bond_in_report', '108008901.IB', 'C10008', 'abs', 'uploaded', 0, '2026-05-15 11:15:00', '2026-05-15 11:15:00'),
('赵晓峰', '能源公司债入库报告', 'bond_in_report', 'GSB001.SH', 'C10003', 'company_bond', 'uploaded', 0, '2026-05-22 15:10:00', '2026-05-22 15:10:00'),
('陈雨欣', '地产短融风险跟踪报告', 'bond_out_report', 'CP001.IB', 'C10005', 'cp', 'migrated', 0, '2026-05-25 13:35:00', '2026-05-25 13:35:00'),
('刘志伟', '科技可转债跟踪报告', 'bond_in_report', 'CB001.SH', 'C10007', 'convertible_bond', 'uploaded', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
('周明华', '交投集团主体信用风险专项报告', 'other_report', 'C10001', 'C10001', 'company', 'migrated', 0, '2026-05-29 13:30:00', '2026-05-29 13:30:00'),
('测试用户', '已删除内部报告测试数据', 'other_report', 'MTN001.IB', 'C10001', 'mtn', 'uploaded', 1, '2026-06-15 09:00:00', '2026-06-15 09:00:00');

INSERT INTO `rrs_report_out` (
    `author_name`
    ,`source_org_name`
    ,`report_title`
    ,`report_type`
    ,`security_code`
    ,`company_code`
    ,`security_type`
    ,`data_source`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
) VALUES
('中金证券', '中金证券', '交投中票外部跟踪报告', 'bond_in_report', 'MTN001.IB', 'C10001', 'mtn', 'migrated', 0, '2026-05-28 10:05:00', '2026-05-28 10:05:00'),
('中信证券', '中信证券', '交投信用债外部入库报告', 'bond_in_report', 'XYB001.IB', 'C10001', 'credit_bond', 'migrated', 0, '2026-06-01 09:25:00', '2026-06-01 09:25:00'),
('华泰证券', '华泰证券', '交通基础设施主体研究报告', 'other_report', 'C10001', 'C10001', 'company', 'uploaded', 0, '2026-06-03 14:00:00', '2026-06-03 14:00:00'),
('联合资信', '联合资信', '某资ABS信用跟踪报告', 'bond_in_report', '108008901.IB', 'C10008', 'abs', 'uploaded', 0, '2026-06-05 16:30:00', '2026-06-05 16:30:00'),
('标准普尔', '标准普尔', '地产公司主体风险评估报告', 'other_report', 'C10005', 'C10005', 'company', 'migrated', 0, '2026-06-08 11:20:00', '2026-06-08 11:20:00'),
('招商证券', '招商证券', '科技可转债外部研究报告', 'bond_in_report', 'CB001.SH', 'C10007', 'convertible_bond', 'uploaded', 0, '2026-06-10 15:45:00', '2026-06-10 15:45:00'),
('申万宏源', '申万宏源', '国贸资产证券化配置报告', 'bond_in_report', 'ABN002.IB', 'C10009', 'abs_all', 'migrated', 0, '2026-06-12 10:50:00', '2026-06-12 10:50:00'),
('测试机构', '测试机构', '已删除外部报告测试数据', 'other_report', 'MTN001.IB', 'C10001', 'mtn', 'uploaded', 1, '2026-06-15 09:00:00', '2026-06-15 09:00:00');

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
('ip_adjust_log', 1, 'credit_report_hand', 'pdf', 'report_mtn001.pdf',
 'credit_report_20260301090000_1.pdf', 1536000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260301/credit_report_20260301090000_1.pdf',
 '1', 0, NOW(), NOW()),
('ip_adjust_log', 1, 'material_hand', 'pdf', 'material_mtn001.pdf',
 'material_20260301090000_1.pdf', 512000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260301/material_20260301090000_1.pdf',
 '1', 0, NOW(), NOW()),
('ip_adjust_log', 2, 'credit_report_hand', 'pdf', 'report_xyb001.pdf',
 'credit_report_20260302100000_2.pdf', 2048000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260302/credit_report_20260302100000_2.pdf',
 '1', 0, NOW(), NOW()),
('ip_adjust_log', 2, 'material_hand', 'pdf', 'material_xyb001.pdf',
 'material_20260302100000_2.pdf', 768000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260302/material_20260302100000_2.pdf',
 '1', 0, NOW(), NOW()),
('ip_adjust_log', 3, 'credit_report_hand', 'pdf', 'report_abs001.pdf',
 'credit_report_20260303160000_3.pdf', 1024000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260303/credit_report_20260303160000_3.pdf',
 '1', 0, NOW(), NOW()),
('ip_adjust_log', 3, 'material_hand', 'pdf', 'material_abs001.pdf',
 'material_20260303160000_3.pdf', 614400, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260303/material_20260303160000_3.pdf',
 '1', 0, NOW(), NOW()),
('ip_adjust_log', 7, 'credit_report_hand', 'pdf', 'report_crmw001.pdf',
 'credit_report_20260301093000_7.pdf', 1843200, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260301/credit_report_20260301093000_7.pdf',
 '1', 0, NOW(), NOW()),
('ip_adjust_log', 7, 'material_hand', 'pdf', 'material_crmw001.pdf',
 'material_20260301093000_7.pdf', 409600, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260301/material_20260301093000_7.pdf',
 '1', 0, NOW(), NOW());

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
('rrs_report_in', 1, 'report_in', 'pdf', '交投中票入库分析报告.pdf',
 'report_file_in_20260508090000_1.pdf', 1536000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260508/report_file_in_20260508090000_1.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_in', 2, 'report_in', 'pdf', '交投信用债入库报告.pdf',
 'report_file_in_20260512142000_2.pdf', 2048000, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260512/report_file_in_20260512142000_2.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_in', 3, 'report_in', 'pdf', '某资ABS二级库跟踪报告.pdf',
 'report_file_in_20260515111500_3.pdf', 1843200, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260515/report_file_in_20260515111500_3.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_in', 4, 'report_in', 'pdf', '能源公司债入库报告.pdf',
 'report_file_in_20260522151000_4.pdf', 1433600, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260522/report_file_in_20260522151000_4.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_in', 5, 'report_in', 'pdf', '地产短融风险跟踪报告.pdf',
 'report_file_in_20260525133500_5.pdf', 1320960, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260525/report_file_in_20260525133500_5.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_in', 6, 'report_in', 'pdf', '科技可转债跟踪报告.pdf',
 'report_file_in_20260527100000_6.pdf', 1740800, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260527/report_file_in_20260527100000_6.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_in', 7, 'report_in', 'pdf', '交投集团主体信用风险专项报告.pdf',
 'report_file_in_20260529133000_7.pdf', 1126400, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260529/report_file_in_20260529133000_7.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_out', 1, 'report_out', 'pdf', '交投中票外部跟踪报告.pdf',
 'report_file_out_20260528100500_1.pdf', 1638400, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260528/report_file_out_20260528100500_1.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_out', 2, 'report_out', 'pdf', '交投信用债外部入库报告.pdf',
 'report_file_out_20260601092500_2.pdf', 1945600, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260601/report_file_out_20260601092500_2.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_out', 3, 'report_out', 'pdf', '交通基础设施主体研究报告.pdf',
 'report_file_out_20260603140000_3.pdf', 1228800, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260603/report_file_out_20260603140000_3.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_out', 4, 'report_out', 'pdf', '某资ABS信用跟踪报告.pdf',
 'report_file_out_20260605163000_4.pdf', 2150400, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260605/report_file_out_20260605163000_4.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_out', 5, 'report_out', 'pdf', '地产公司主体风险评估报告.pdf',
 'report_file_out_20260608112000_5.pdf', 1843200, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260608/report_file_out_20260608112000_5.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_out', 6, 'report_out', 'pdf', '科技可转债外部研究报告.pdf',
 'report_file_out_20260610154500_6.pdf', 1761280, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260610/report_file_out_20260610154500_6.pdf',
 '1', 0, NOW(), NOW()),
('rrs_report_out', 7, 'report_out', 'pdf', '国贸ABN配置报告.pdf',
 'report_file_out_20260612105000_7.pdf', 1515520, 'application/pdf',
 '/api/v1/attachments/downloadAttachment', '20260612/report_file_out_20260612105000_7.pdf',
 '1', 0, NOW(), NOW());
