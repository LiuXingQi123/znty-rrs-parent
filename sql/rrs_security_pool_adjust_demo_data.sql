-- ============================================================
-- 调库日志 / 池状态 / 流程步骤 演示数据（不含外部导入表，见 rrs_external_import_demo_data.sql）
-- MySQL version: 8.0.33
-- 说明：
--   证券在池 3 条：MTN001.IB / XYB001.IB / 108008901.IB
--   主体在池 3 条：C10001 / C10002 / C10005
--   对应调库日志均为 audit_status=20；另附 2 条待审批（不落池）：
--     GSB001.SH → 一级库（证券池审核）；C10003 → 观察池（禁投主体审核，勿走证券池）
--   代码均对齐 rrs_securityinfo / t_inv_company；evt 表不灌演示数据
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `ip_adjust_log`;
TRUNCATE TABLE `ip_pool_status`;
TRUNCATE TABLE `ip_adjust_step`;

INSERT INTO `ip_pool_status` (
    `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`,
    `adjust_batch_no`, `adjust_log_id`, `target_pool_id`, `target_pool_name`, `pool_type`,
    `flow_id`, `flow_key`, `flow_type`,
    `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`,
    `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
-- 证券在池 3
('MTN001.IB', '24交投MTN', 'mtn', '手工调整', '调入', 'BOND20260301001', 1, 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投中票调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
('XYB001.IB', '24交投信用', 'credit_bond', '手工调整', '调入', 'BOND20260302001', 2, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投信用债调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
('108008901.IB', '24资ABS01', 'abs', '手工调整', '调入', 'BOND20260303001', 3, 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'ABS债调入二级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
-- 主体在池 3
('C10001', '某交投集团', 'company', '手工调整', '调入', 'BOND20260310001', 4, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '主体调入一级库', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('C10002', '某城投公司', 'company', '手工调整', '调入', 'BOND20260311001', 5, 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '主体调入二级库', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('C10005', '某地产公司', 'company', '手工调整', '调入', 'BOND20260312001', 6, 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW());

INSERT INTO `ip_adjust_log` (
    `id`, `security_code`, `security_short_name`, `security_type`,
    `crmw_name`, `crmw_scode`, `crmw_mktcode`, `crmw_stype`,
    `adjust_type`, `adjust_mode`,
    `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`,
    `flow_id`, `flow_key`, `flow_type`,
    `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`,
    `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
-- 证券在池对应已通过
(1, 'MTN001.IB', '24交投MTN', 'mtn', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260301001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投中票调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(2, 'XYB001.IB', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260302001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投信用债调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(3, '108008901.IB', '24资ABS01', 'abs', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260303001', 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'ABS债调入二级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
-- 主体在池对应已通过
(4, 'C10001', '某交投集团', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260310001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '主体调入一级库', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(5, 'C10002', '某城投公司', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260311001', 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '主体调入二级库', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(6, 'C10005', '某地产公司', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260312001', 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
-- CRMW 在池对应已通过（落 ip_pool_status_crmw，不落 ip_pool_status）
(7, 'MTN001.IB', '24交投MTN', 'mtn', '某CRMW凭证A', 'CRMW001.IB', 'CIBM', 'crmw', '手工调整', '调入', 'CRMW20260301001', 18, 'CRMW库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW调入CRMW库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
-- 待审批（不落池）
(8, 'GSB001.SH', '24能E1', 'company_bond', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260710001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '00', '1', '管理员', '能源公司债调入一级库待审批', '2026-07-10 09:00:00', NULL, NULL, 0, NOW(), NOW()),
(9, 'C10003', '某能源集团', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260714001', 16, '观察池', 'observe', 113, 'company:forbidden-inbound', 'normalInbound', '00', '1', '管理员', '主体调入观察池待审批', '2026-07-14 09:00:00', NULL, NULL, 0, NOW(), NOW());

INSERT INTO `ip_adjust_step` (
    `adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`,
    `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`,
    `start_time`, `process_time`, `crte_time`, `updt_time`
) VALUES
-- 债券待审批 log 8
(8, 'BOND20260710001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-10 09:00:00', '2026-07-10 09:00:00', NOW(), NOW()),
(8, 'BOND20260710001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-10 09:00:00', NULL, NOW(), NOW()),
-- 主体待审批 log 9（观察池 / flow 113，我的事宜 businessScene=forbiddenCompanyAdjust）
(9, 'BOND20260714001', 11302, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-14 09:00:00', '2026-07-14 09:00:00', NOW(), NOW()),
(9, 'BOND20260714001', 11303, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-14 09:00:00', NULL, NOW(), NOW()),
-- 债券禁止库 log 6 直通
(6, 'BOND20260312001', NULL, NULL, '直通落地', 'auto', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-12 09:00:00', '2026-03-12 14:00:00', NOW(), NOW());
