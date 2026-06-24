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
TRUNCATE TABLE `sirm_report_out`;
TRUNCATE TABLE `sirm_report_in`;
TRUNCATE TABLE `sys_attachment`;

-- ----------------------------------------------------------------------------
-- 内部报告库插入测试数据
-- ----------------------------------------------------------------------------
INSERT INTO `sirm_report_in` (
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
('张明远', '万科企业股票入库分析报告', 'stock_in_report', '000002.SZ', 'SUBJ000002', 'stock', 'migrated', 0, '2026-05-08 09:00:00', '2026-05-08 09:00:00'),
('李思涵', '中国石油债券入库报告', 'bond_in_report', '102000002.IB', 'SUBJ102000002', 'bond', 'uploaded', 0, '2026-05-12 14:20:00', '2026-05-12 14:20:00'),
('王建强', '深圳地铁集团债券出库报告', 'bond_out_report', '102100003.IB', 'SUBJ102100003', 'bond', 'uploaded', 0, '2026-05-15 11:15:00', '2026-05-15 11:15:00'),
('赵晓峰', '稳健收益基金入库报告', 'fund_in_report', 'FUND000002', 'SUBJ_FUND_002', 'fund', 'uploaded', 0, '2026-05-22 15:10:00', '2026-05-22 15:10:00'),
('陈雨欣', '地产主题基金出库报告', 'fund_out_report', 'FUND000003', 'SUBJ_FUND_003', 'fund', 'migrated', 0, '2026-05-25 13:35:00', '2026-05-25 13:35:00'),
('刘志伟', '平安集团股票出库复核报告', 'stock_out_report', '601318.SH', 'SUBJ601318', 'stock', 'uploaded', 0, '2026-05-27 10:00:00', '2026-05-27 10:00:00'),
('周明华', '主体信用风险专项报告', 'other_report', 'COMPANY.RISK.001', 'SUBJ_RISK_001', 'company', 'migrated', 0, '2026-05-29 13:30:00', '2026-05-29 13:30:00'),
('测试用户', '已删除内部报告测试数据', 'other_report', 'DELETE.IN', 'SUBJ_DELETE_IN', 'other', 'uploaded', 1, '2026-06-15 09:00:00', '2026-06-15 09:00:00');

-- ----------------------------------------------------------------------------
-- 外部报告库插入测试数据
-- ----------------------------------------------------------------------------
INSERT INTO `sirm_report_out` (
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
('中金证券', '中金证券', '万科企业股票入库外部研究报告', 'stock_in_report', '000002.SZ', 'SUBJ000002', 'stock', 'migrated', 0, '2026-05-28 10:05:00', '2026-05-28 10:05:00'),
('中信证券', '中信证券', '中国石油债券跟踪入库报告', 'bond_in_report', '102000002.IB', 'SUBJ102000002', 'bond', 'migrated', 0, '2026-06-01 09:25:00', '2026-06-01 09:25:00'),
('华泰证券', '华泰证券', '交通运输基础设施其他报告', 'other_report', 'OTHER.TRANSPORT.001', 'INDU_TRANSPORT', 'other', 'uploaded', 0, '2026-06-03 14:00:00', '2026-06-03 14:00:00'),
('联合资信', '联合资信', '三一重工债券出库信用报告', 'bond_out_report', '102100010.IB', 'SUBJ102100010', 'bond', 'uploaded', 0, '2026-06-05 16:30:00', '2026-06-05 16:30:00'),
('标准普尔', '标准普尔', '华夏幸福主体风险评估报告', 'other_report', '101900004.IB', 'SUBJ101900004', 'company', 'migrated', 0, '2026-06-08 11:20:00', '2026-06-08 11:20:00'),
('招商证券', '招商证券', '比亚迪股票出库产业链研究报告', 'stock_out_report', '102100006.IB', 'SUBJ102100006', 'stock', 'uploaded', 0, '2026-06-10 15:45:00', '2026-06-10 15:45:00'),
('申万宏源', '申万宏源', '全球能源基金入库配置报告', 'fund_in_report', 'FUND000008', 'SUBJ_FUND_008', 'fund', 'migrated', 0, '2026-06-12 10:50:00', '2026-06-12 10:50:00'),
('测试机构', '测试机构', '已删除外部报告测试数据', 'other_report', 'DELETE.OUT', 'SUBJ_DELETE_OUT', 'other', 'uploaded', 1, '2026-06-15 09:00:00', '2026-06-15 09:00:00');

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

