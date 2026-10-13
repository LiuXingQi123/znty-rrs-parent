-- ============================================================
-- 调库日志 / 池状态 / 流程步骤 演示数据（不含外部导入表，见 rrs_external_import_demo_data.sql）
-- MySQL version: 8.0.33
-- 说明：
--   同券三个市场代码要么全部在同一池，要么全部不在池；在池状态均对应已通过日志
--   C10005 在禁止库15及黑名单质押库17，旗下4只债券同步在池；主体批次统一 COMP 前缀
--   GSB001.SH 一级库申请、C10003 禁止库申请待审批，不预写池状态
--   关联项与主项共用批次和流程步骤；已完成主项补齐从开始到结束的步骤
--   代码均对齐 rrs_securityinfo / t_inv_company；evt 表不灌演示数据
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 清空业务表
-- ----------------------------------------------------------------------------
-- 清空证券池调库记录表
TRUNCATE TABLE `ip_adjust_log`;
-- 清空投资池当前状态表
TRUNCATE TABLE `ip_pool_status`;
-- 清空证券池调库-流程步骤记录表
TRUNCATE TABLE `ip_adjust_step`;

-- ----------------------------------------------------------------------------
-- 投资池当前状态样例（17 条）
-- ----------------------------------------------------------------------------
INSERT INTO `ip_pool_status` (
    `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`,
    `adjust_batch_no`, `adjust_log_id`, `target_pool_id`, `target_pool_name`, `pool_type`,
    `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`,
    `adjust_reason`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`,
    `updt_time`
) VALUES
('MTN001.IB', '24交投MTN', 'mtn', '手工调整', '调入', 'BOND20260301001', 1, 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投中票调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
('XYB001.IB', '24交投信用', 'credit_bond', '手工调整', '调入', 'BOND20260302001', 2, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投信用债调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
('108008901.IB', '24资ABS01', 'abs', '手工调整', '调入', 'BOND20260303001', 3, 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'ABS债调入二级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
('C10001', '某交投集团', 'company', '手工调整', '调入', 'COMP20260310001', 4, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体调入观察池', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('C10002', '某城投公司', 'company', '手工调整', '调入', 'COMP20260311001', 5, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体调入观察池', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('C10005', '某地产公司', 'company', '手工调整', '调入', 'COMP20260312001', 6, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体调入债券禁止库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('198101.SH', '24交投信用', 'credit_bond', '关联调整', '调入', 'BOND20260302001', 10, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '同券跨市场代码同步调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
('128101.SZ', '24交投信用', 'credit_bond', '关联调整', '调入', 'BOND20260302001', 11, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '同券跨市场代码同步调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
('C10005', '某地产公司', 'company', '联动调整', '调入', 'COMP20260312001', 12, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体在债券禁止库，联动调入黑名单质押库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CRMW005.IB', 'CRMW-E', 'crmw', '自动调整', '调入', 'COMP20260312001', 13, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('PPB001.IB', '24地产私募', 'company_bond', '自动调整', '调入', 'COMP20260312001', 14, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('DBB002.IB', '24地产担保', 'company_bond', '自动调整', '调入', 'COMP20260312001', 15, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CP001.IB', '24地产CP', 'cp', '自动调整', '调入', 'COMP20260312001', 16, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CRMW005.IB', 'CRMW-E', 'crmw', '自动调整', '调入', 'COMP20260312001', 17, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('PPB001.IB', '24地产私募', 'company_bond', '自动调整', '调入', 'COMP20260312001', 18, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('DBB002.IB', '24地产担保', 'company_bond', '自动调整', '调入', 'COMP20260312001', 19, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CP001.IB', '24地产CP', 'cp', '自动调整', '调入', 'COMP20260312001', 20, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW());

-- ----------------------------------------------------------------------------
-- 证券池调库记录样例（20 条）
-- ----------------------------------------------------------------------------
INSERT INTO `ip_adjust_log` (
    `id`, `security_code`, `security_short_name`, `security_type`, `crmw_name`, `crmw_scode`,
    `crmw_mktcode`, `crmw_stype`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `target_pool_id`,
    `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`,
    `adjuster_id`, `adjuster_name`, `adjust_reason`, `submit_time`, `audit_time`, `entry_time`,
    `is_deleted`, `crte_time`, `updt_time`
) VALUES
(1, 'MTN001.IB', '24交投MTN', 'mtn', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260301001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投中票调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(2, 'XYB001.IB', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260302001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投信用债调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(3, '108008901.IB', '24资ABS01', 'abs', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260303001', 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'ABS债调入二级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
(4, 'C10001', '某交投集团', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体调入观察池', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(5, 'C10002', '某城投公司', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体调入观察池', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(6, 'C10005', '某地产公司', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体调入债券禁止库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(7, 'MTN001.IB', '24交投MTN', 'mtn', '某CRMW凭证A', 'CRMW001.IB', 'CIBM', 'crmw', '手工调整', '调入', 'CRMW20260301001', 18, 'CRMW库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW调入CRMW库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(8, 'GSB001.SH', '24能E1', 'company_bond', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260710001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '00', '1', '管理员', '能源公司债调入一级库待审批', '2026-07-10 09:00:00', NULL, NULL, 0, NOW(), NOW()),
(9, 'C10003', '某能源集团', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260714001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '00', '1', '管理员', '主体调入债券禁止库待审批', '2026-07-14 09:00:00', NULL, NULL, 0, NOW(), NOW()),
(10, '198101.SH', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '关联调整', '调入', 'BOND20260302001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '同券跨市场代码同步调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(11, '128101.SZ', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '关联调整', '调入', 'BOND20260302001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '同券跨市场代码同步调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(12, 'C10005', '某地产公司', 'company', NULL, NULL, NULL, NULL, '联动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体在债券禁止库，联动调入黑名单质押库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(13, 'CRMW005.IB', 'CRMW-E', 'crmw', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(14, 'PPB001.IB', '24地产私募', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(15, 'DBB002.IB', '24地产担保', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(16, 'CP001.IB', '24地产CP', 'cp', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(17, 'CRMW005.IB', 'CRMW-E', 'crmw', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(18, 'PPB001.IB', '24地产私募', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(19, 'DBB002.IB', '24地产担保', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(20, 'CP001.IB', '24地产CP', 'cp', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW());

-- ----------------------------------------------------------------------------
-- 证券池调库-流程步骤记录样例（36 条）
-- ----------------------------------------------------------------------------
INSERT INTO `ip_adjust_step` (
    `adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`,
    `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`,
    `process_action`, `start_time`, `process_time`, `crte_time`, `updt_time`
) VALUES
(1, 'BOND20260301001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-01 09:00:00', '2026-03-01 09:00:00', NOW(), NOW()),
(1, 'BOND20260301001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-01 09:00:00', '2026-03-01 09:00:00', NOW(), NOW()),
(1, 'BOND20260301001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-03-01 09:00:00', '2026-03-01 14:00:00', NOW(), NOW()),
(1, 'BOND20260301001', 10105, 'n105', '研究总监审批', 'approval', 'preempt', 5, 'approve', '17', '研究总监1', 'approve', '2026-03-01 09:00:00', '2026-03-01 14:00:00', NOW(), NOW()),
(1, 'BOND20260301001', 10106, 'n106', 'O32自动审批', 'approval', 'o32', 6, 'auto_process', NULL, NULL, 'auto_process', '2026-03-01 09:00:00', '2026-03-01 14:00:00', NOW(), NOW()),
(1, 'BOND20260301001', 10107, 'n107', '结束', 'end', NULL, 7, 'auto_process', NULL, NULL, 'auto_process', '2026-03-01 09:00:00', '2026-03-01 14:00:00', NOW(), NOW()),
(2, 'BOND20260302001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-02 09:00:00', '2026-03-02 09:00:00', NOW(), NOW()),
(2, 'BOND20260302001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-02 09:00:00', '2026-03-02 09:00:00', NOW(), NOW()),
(2, 'BOND20260302001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-03-02 09:00:00', '2026-03-02 14:00:00', NOW(), NOW()),
(2, 'BOND20260302001', 10105, 'n105', '研究总监审批', 'approval', 'preempt', 5, 'approve', '17', '研究总监1', 'approve', '2026-03-02 09:00:00', '2026-03-02 14:00:00', NOW(), NOW()),
(2, 'BOND20260302001', 10106, 'n106', 'O32自动审批', 'approval', 'o32', 6, 'auto_process', NULL, NULL, 'auto_process', '2026-03-02 09:00:00', '2026-03-02 14:00:00', NOW(), NOW()),
(2, 'BOND20260302001', 10107, 'n107', '结束', 'end', NULL, 7, 'auto_process', NULL, NULL, 'auto_process', '2026-03-02 09:00:00', '2026-03-02 14:00:00', NOW(), NOW()),
(3, 'BOND20260303001', 10501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-03 09:00:00', '2026-03-03 09:00:00', NOW(), NOW()),
(3, 'BOND20260303001', 10502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-03 09:00:00', '2026-03-03 09:00:00', NOW(), NOW()),
(3, 'BOND20260303001', 10503, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-03-03 09:00:00', '2026-03-03 14:00:00', NOW(), NOW()),
(4, 'COMP20260310001', 11201, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-10 09:00:00', '2026-03-10 09:00:00', NOW(), NOW()),
(4, 'COMP20260310001', 11202, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-10 09:00:00', '2026-03-10 09:00:00', NOW(), NOW()),
(4, 'COMP20260310001', 11203, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-03-10 09:00:00', '2026-03-10 14:00:00', NOW(), NOW()),
(5, 'COMP20260311001', 11201, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-11 09:00:00', '2026-03-11 09:00:00', NOW(), NOW()),
(5, 'COMP20260311001', 11202, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-11 09:00:00', '2026-03-11 09:00:00', NOW(), NOW()),
(5, 'COMP20260311001', 11203, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-03-11 09:00:00', '2026-03-11 14:00:00', NOW(), NOW()),
(6, 'COMP20260312001', 11301, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-12 09:00:00', '2026-03-12 09:00:00', NOW(), NOW()),
(6, 'COMP20260312001', 11302, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-12 09:00:00', '2026-03-12 09:00:00', NOW(), NOW()),
(6, 'COMP20260312001', 11303, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-03-12 09:00:00', '2026-03-12 14:00:00', NOW(), NOW()),
(6, 'COMP20260312001', 11305, 'n105', '研究总监审批', 'approval', 'preempt', 5, 'approve', '17', '研究总监1', 'approve', '2026-03-12 09:00:00', '2026-03-12 14:00:00', NOW(), NOW()),
(6, 'COMP20260312001', 11306, 'n106', '投资决策委员会审批', 'approval', 'auto', 6, 'auto_process', NULL, NULL, 'auto_process', '2026-03-12 09:00:00', '2026-03-12 14:00:00', NOW(), NOW()),
(6, 'COMP20260312001', 11307, 'n107', '结束', 'end', NULL, 7, 'auto_process', NULL, NULL, 'auto_process', '2026-03-12 09:00:00', '2026-03-12 14:00:00', NOW(), NOW()),
(7, 'CRMW20260301001', 10501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-01 09:00:00', '2026-03-01 09:00:00', NOW(), NOW()),
(7, 'CRMW20260301001', 10502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-01 09:00:00', '2026-03-01 09:00:00', NOW(), NOW()),
(7, 'CRMW20260301001', 10503, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-03-01 09:00:00', '2026-03-01 14:00:00', NOW(), NOW()),
(8, 'BOND20260710001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-07-10 09:00:00', '2026-07-10 09:00:00', NOW(), NOW()),
(8, 'BOND20260710001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-10 09:00:00', '2026-07-10 09:00:00', NOW(), NOW()),
(8, 'BOND20260710001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-10 09:00:00', NULL, NOW(), NOW()),
(9, 'COMP20260714001', 11301, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-07-14 09:00:00', '2026-07-14 09:00:00', NOW(), NOW()),
(9, 'COMP20260714001', 11302, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-14 09:00:00', '2026-07-14 09:00:00', NOW(), NOW()),
(9, 'COMP20260714001', 11303, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-14 09:00:00', NULL, NOW(), NOW());
