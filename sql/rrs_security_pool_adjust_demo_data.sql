-- ============================================================
-- 调库日志 / 池状态 / 流程步骤 演示数据（不含外部导入表，见 rrs_external_import_demo_data.sql）
-- MySQL version: 8.0.33
-- 说明：
--   证券在池 3 条：102480041.IB / 102480001.IB / 108008901.IB
--   主体在池 3 条：C10001 / C10002 / C10005；C10005 同批联动旗下 4 只未到期债券
--   对应调库日志均为 audit_status=20；另附待审批、驳回修改、终态驳回及撤回样本（均不落池）：
--     185901.SH → 一级库（证券池审核）；C10003 → 债券禁止库（禁投主体审核，勿走证券池）
--   代码均对齐 rrs_securityinfo / t_inv_company；evt 表不灌演示数据
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `ip_adjust_log`;
TRUNCATE TABLE `ip_pool_status`;
TRUNCATE TABLE `ip_adjust_step`;

INSERT INTO `ip_pool_status` (`isin_code`, `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `adjust_log_id`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`) VALUES
('CNDEMO000384', NULL, '24交投MTN', 'mtn', '手工调整', '调入', 'BOND20260601001', 1, 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投中票调入三级库', '2026-06-01 09:00:00', '2026-06-01 14:00:00', '2026-06-01 14:00:00', 0, NOW(), NOW()),
('CNDEMO000293', NULL, '24交投信用', 'credit_bond', '手工调整', '调入', 'BOND20260602001', 2, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投信用债调入一级库', '2026-06-02 09:00:00', '2026-06-02 14:00:00', '2026-06-02 14:00:00', 0, NOW(), NOW()),
('CNDEMO000012', NULL, '24资ABS01', 'abs', '手工调整', '调入', 'BOND20260603001', 3, 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'ABS债调入二级库', '2026-06-03 09:00:00', '2026-06-03 14:00:00', '2026-06-03 14:00:00', 0, NOW(), NOW()),
(NULL, 'C10001', '某交投集团', 'company', '手工调整', '调入', 'COMP20260610001', 4, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '主体调入一级库', '2026-06-10 09:00:00', '2026-06-10 14:00:00', '2026-06-10 14:00:00', 0, NOW(), NOW()),
(NULL, 'C10002', '某城投公司', 'company', '手工调整', '调入', 'COMP20260611001', 5, 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '主体调入二级库', '2026-06-11 09:00:00', '2026-06-11 14:00:00', '2026-06-11 14:00:00', 0, NOW(), NOW()),
(NULL, 'C10005', '某地产公司', 'company', '手工调整', '调入', 'COMP20260612001', 6, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体调入债券禁止库', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
('CNDEMO000103', NULL, 'CRMW-E', 'crmw', '自动调整', '调入', 'COMP20260612001', 13, 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库，联动旗下未到期债券', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
('CNDEMO000145', NULL, '24地产私募', 'company_bond', '自动调整', '调入', 'COMP20260612001', 14, 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库，联动旗下未到期债券', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
('CNDEMO000244', NULL, '24地产担保', 'company_bond', '自动调整', '调入', 'COMP20260612001', 15, 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库，联动旗下未到期债券', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
('CNDEMO000434', NULL, '24地产CP', 'cp', '自动调整', '调入', 'COMP20260612001', 16, 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库，联动旗下未到期债券', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
(NULL, 'C10005', '某地产公司', 'company', '联动调整', '调入', 'COMP20260612001', 17, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体进入债券禁止库，命中黑名单质押库条件，联动调入17', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
('CNDEMO000103', NULL, 'CRMW-E', 'crmw', '自动调整', '调入', 'COMP20260612001', 18, 17, '黑名单质押库', 'blacklist', NULL, NULL, NULL, '20', '1', '管理员', '主体进入黑名单质押库，旗下未到期债券自动同步', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
('CNDEMO000145', NULL, '24地产私募', 'company_bond', '自动调整', '调入', 'COMP20260612001', 19, 17, '黑名单质押库', 'blacklist', NULL, NULL, NULL, '20', '1', '管理员', '主体进入黑名单质押库，旗下未到期债券自动同步', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
('CNDEMO000244', NULL, '24地产担保', 'company_bond', '自动调整', '调入', 'COMP20260612001', 20, 17, '黑名单质押库', 'blacklist', NULL, NULL, NULL, '20', '1', '管理员', '主体进入黑名单质押库，旗下未到期债券自动同步', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW()),
('CNDEMO000434', NULL, '24地产CP', 'cp', '自动调整', '调入', 'COMP20260612001', 21, 17, '黑名单质押库', 'blacklist', NULL, NULL, NULL, '20', '1', '管理员', '主体进入黑名单质押库，旗下未到期债券自动同步', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW());

INSERT INTO `ip_adjust_log` (`id`, `isin_code`, `security_code`, `security_short_name`, `security_type`, `crmw_name`, `crmw_isin_code`, `crmw_scode`, `crmw_mktcode`, `crmw_stype`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`, `wind_code_sh`, `wind_code_sz`, `wind_code_nib`, `wind_code_bj`, `wind_code_nbc`) VALUES
(1, 'CNDEMO000384', NULL, '24交投MTN', 'mtn', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260601001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投中票调入三级库', '2026-06-01 09:00:00', '2026-06-01 14:00:00', '2026-06-01 14:00:00', 0, NOW(), NOW(), NULL, NULL, '102480041.IB', NULL, NULL),
(2, 'CNDEMO000293', NULL, '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260602001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投信用债调入一级库', '2026-06-02 09:00:00', '2026-06-02 14:00:00', '2026-06-02 14:00:00', 0, NOW(), NOW(), '184901.SH', '149901.SZ', '102480001.IB', NULL, NULL),
(3, 'CNDEMO000012', NULL, '24资ABS01', 'abs', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260603001', 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'ABS债调入二级库', '2026-06-03 09:00:00', '2026-06-03 14:00:00', '2026-06-03 14:00:00', 0, NOW(), NOW(), NULL, NULL, '108008901.IB', NULL, NULL),
(4, NULL, 'C10001', '某交投集团', 'company', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260610001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '主体调入一级库', '2026-06-10 09:00:00', '2026-06-10 14:00:00', '2026-06-10 14:00:00', 0, NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
(5, NULL, 'C10002', '某城投公司', 'company', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260611001', 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '主体调入二级库', '2026-06-11 09:00:00', '2026-06-11 14:00:00', '2026-06-11 14:00:00', 0, NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
(6, NULL, 'C10005', '某地产公司', 'company', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260612001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体调入债券禁止库', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
(7, 'CNDEMO000384', NULL, '24交投MTN', 'mtn', '某CRMW凭证A', 'CNDEMO000061', '032400101.IB', 'CIBM', 'crmw', '手工调整', '调入', 'CRMW20260601001', 18, 'CRMW库', 'crmw', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', 'CRMW调入CRMW库', '2026-06-01 09:00:00', '2026-06-01 14:00:00', '2026-06-01 14:00:00', 0, NOW(), NOW(), NULL, NULL, '102480041.IB', NULL, NULL),
(8, 'CNDEMO000376', NULL, '24能E1', 'company_bond', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260710001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '00', '1', '管理员', '能源公司债调入一级库待审批', '2026-07-10 09:00:00', NULL, NULL, 0, NOW(), NOW(), '185901.SH', NULL, NULL, NULL, NULL),
(9, NULL, 'C10003', '某能源集团', 'company', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260714001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '00', '1', '管理员', '主体调入债券禁止库待审批', '2026-07-14 09:00:00', NULL, NULL, 0, NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
(10, 'CNDEMO000301', NULL, '24城投信用', 'credit_bond', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260715001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '11', '2', '研究员1', '补充研究材料后重新提交', '2026-07-15 09:00:00', '2026-07-15 14:00:00', NULL, 0, NOW(), NOW(), NULL, NULL, '102480002.IB', NULL, NULL),
(11, 'CNDEMO000392', NULL, '24城投PPN', 'ppn', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260716001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '21', '2', '研究员1', '审批不同意，未落池', '2026-07-16 09:00:00', '2026-07-16 14:00:00', NULL, 0, NOW(), NOW(), NULL, NULL, '032480011.IB', NULL, NULL),
(12, 'CNDEMO000160', NULL, '25城投私募', 'company_bond', NULL, NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260717001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '99', '2', '研究员1', '发起人撤回，未落池', '2026-07-17 09:00:00', '2026-07-17 14:00:00', NULL, 0, NOW(), NOW(), '196901.SH', NULL, NULL, NULL, NULL),
(13, 'CNDEMO000103', NULL, 'CRMW-E', 'crmw', NULL, NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260612001', 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库，联动旗下未到期债券', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, '032400105.IB', NULL, NULL),
(14, 'CNDEMO000145', NULL, '24地产私募', 'company_bond', NULL, NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260612001', 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库，联动旗下未到期债券', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, '032480001.IB', NULL, NULL),
(15, 'CNDEMO000244', NULL, '24地产担保', 'company_bond', NULL, NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260612001', 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库，联动旗下未到期债券', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, '102480022.IB', NULL, NULL),
(16, 'CNDEMO000434', NULL, '24地产CP', 'cp', NULL, NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260612001', 15, '债券禁止库', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入债券禁止库，联动旗下未到期债券', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, '042480001.IB', NULL, NULL),
(17, NULL, 'C10005', '某地产公司', 'company', NULL, NULL, NULL, NULL, NULL, '联动调整', '调入', 'COMP20260612001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体进入债券禁止库，命中黑名单质押库条件，联动调入17', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
(18, 'CNDEMO000103', NULL, 'CRMW-E', 'crmw', NULL, NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260612001', 17, '黑名单质押库', 'blacklist', NULL, NULL, NULL, '20', '1', '管理员', '主体进入黑名单质押库，旗下未到期债券自动同步', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, '032400105.IB', NULL, NULL),
(19, 'CNDEMO000145', NULL, '24地产私募', 'company_bond', NULL, NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260612001', 17, '黑名单质押库', 'blacklist', NULL, NULL, NULL, '20', '1', '管理员', '主体进入黑名单质押库，旗下未到期债券自动同步', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, '032480001.IB', NULL, NULL),
(20, 'CNDEMO000244', NULL, '24地产担保', 'company_bond', NULL, NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260612001', 17, '黑名单质押库', 'blacklist', NULL, NULL, NULL, '20', '1', '管理员', '主体进入黑名单质押库，旗下未到期债券自动同步', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, '102480022.IB', NULL, NULL),
(21, 'CNDEMO000434', NULL, '24地产CP', 'cp', NULL, NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260612001', 17, '黑名单质押库', 'blacklist', NULL, NULL, NULL, '20', '1', '管理员', '主体进入黑名单质押库，旗下未到期债券自动同步', '2026-06-12 09:00:00', '2026-06-12 14:00:00', '2026-06-12 14:00:00', 0, NOW(), NOW(), NULL, NULL, '042480001.IB', NULL, NULL);

INSERT INTO `ip_adjust_step` (
    `adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`,
    `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`,
    `start_time`, `process_time`, `crte_time`, `updt_time`
) VALUES
(1, 'BOND20260601001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-06-01 09:00:00', '2026-06-01 09:00:00', NOW(), NOW()),
(1, 'BOND20260601001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-06-01 09:00:00', '2026-06-01 09:00:00', NOW(), NOW()),
(1, 'BOND20260601001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-06-01 14:00:00', '2026-06-01 14:00:00', NOW(), NOW()),
(1, 'BOND20260601001', 10105, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', '2026-06-01 14:00:00', '2026-06-01 14:00:00', NOW(), NOW()),
(1, 'BOND20260601001', 10106, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', '2026-06-01 14:00:00', '2026-06-01 14:00:00', NOW(), NOW()),
(1, 'BOND20260601001', 10107, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', '2026-06-01 14:00:00', '2026-06-01 14:00:00', NOW(), NOW()),
(2, 'BOND20260602001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-06-02 09:00:00', '2026-06-02 09:00:00', NOW(), NOW()),
(2, 'BOND20260602001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-06-02 09:00:00', '2026-06-02 09:00:00', NOW(), NOW()),
(2, 'BOND20260602001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-06-02 14:00:00', '2026-06-02 14:00:00', NOW(), NOW()),
(2, 'BOND20260602001', 10105, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', '2026-06-02 14:00:00', '2026-06-02 14:00:00', NOW(), NOW()),
(2, 'BOND20260602001', 10106, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', '2026-06-02 14:00:00', '2026-06-02 14:00:00', NOW(), NOW()),
(2, 'BOND20260602001', 10107, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', '2026-06-02 14:00:00', '2026-06-02 14:00:00', NOW(), NOW()),
(3, 'BOND20260603001', 10501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-06-03 09:00:00', '2026-06-03 09:00:00', NOW(), NOW()),
(3, 'BOND20260603001', 10502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-06-03 09:00:00', '2026-06-03 09:00:00', NOW(), NOW()),
(3, 'BOND20260603001', 10503, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-06-03 14:00:00', '2026-06-03 14:00:00', NOW(), NOW()),
(4, 'COMP20260610001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-06-10 09:00:00', '2026-06-10 09:00:00', NOW(), NOW()),
(4, 'COMP20260610001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-06-10 09:00:00', '2026-06-10 09:00:00', NOW(), NOW()),
(4, 'COMP20260610001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-06-10 14:00:00', '2026-06-10 14:00:00', NOW(), NOW()),
(4, 'COMP20260610001', 10105, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', '2026-06-10 14:00:00', '2026-06-10 14:00:00', NOW(), NOW()),
(4, 'COMP20260610001', 10106, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', '2026-06-10 14:00:00', '2026-06-10 14:00:00', NOW(), NOW()),
(4, 'COMP20260610001', 10107, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', '2026-06-10 14:00:00', '2026-06-10 14:00:00', NOW(), NOW()),
(5, 'COMP20260611001', 10201, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-06-11 09:00:00', '2026-06-11 09:00:00', NOW(), NOW()),
(5, 'COMP20260611001', 10202, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-06-11 09:00:00', '2026-06-11 09:00:00', NOW(), NOW()),
(5, 'COMP20260611001', 10203, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-06-11 14:00:00', '2026-06-11 14:00:00', NOW(), NOW()),
(5, 'COMP20260611001', 10205, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', '2026-06-11 14:00:00', '2026-06-11 14:00:00', NOW(), NOW()),
(5, 'COMP20260611001', 10206, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', '2026-06-11 14:00:00', '2026-06-11 14:00:00', NOW(), NOW()),
(5, 'COMP20260611001', 10207, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', '2026-06-11 14:00:00', '2026-06-11 14:00:00', NOW(), NOW()),
(6, 'COMP20260612001', 11301, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-06-12 09:00:00', '2026-06-12 09:00:00', NOW(), NOW()),
(6, 'COMP20260612001', 11302, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-06-12 09:00:00', '2026-06-12 09:00:00', NOW(), NOW()),
(6, 'COMP20260612001', 11303, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-06-12 14:00:00', '2026-06-12 14:00:00', NOW(), NOW()),
(6, 'COMP20260612001', 11305, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', '2026-06-12 14:00:00', '2026-06-12 14:00:00', NOW(), NOW()),
(6, 'COMP20260612001', 11306, 'n106', '投资决策委员会审批', 'approval', 'auto', 5, 'auto_process', NULL, NULL, 'auto_process', '2026-06-12 14:00:00', '2026-06-12 14:00:00', NOW(), NOW()),
(6, 'COMP20260612001', 11307, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', '2026-06-12 14:00:00', '2026-06-12 14:00:00', NOW(), NOW()),
(7, 'CRMW20260601001', 11201, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-06-01 09:00:00', '2026-06-01 09:00:00', NOW(), NOW()),
(7, 'CRMW20260601001', 11202, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-06-01 09:00:00', '2026-06-01 09:00:00', NOW(), NOW()),
(7, 'CRMW20260601001', 11203, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-06-01 14:00:00', '2026-06-01 14:00:00', NOW(), NOW()),
(8, 'BOND20260710001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-07-10 09:00:00', '2026-07-10 09:00:00', NOW(), NOW()),
(8, 'BOND20260710001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-10 09:00:00', '2026-07-10 09:00:00', NOW(), NOW()),
(8, 'BOND20260710001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-10 09:00:00', NULL, NOW(), NOW()),
(9, 'COMP20260714001', 11301, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-07-14 09:00:00', '2026-07-14 09:00:00', NOW(), NOW()),
(9, 'COMP20260714001', 11302, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-14 09:00:00', '2026-07-14 09:00:00', NOW(), NOW()),
(9, 'COMP20260714001', 11303, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-14 09:00:00', NULL, NOW(), NOW()),
(10, 'BOND20260715001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-07-15 09:00:00', '2026-07-15 09:00:00', NOW(), NOW()),
(10, 'BOND20260715001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', '2026-07-15 09:00:00', '2026-07-15 09:00:00', NOW(), NOW()),
(10, 'BOND20260715001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'reject', '3', '研究员2', 'reject', '2026-07-15 14:00:00', '2026-07-15 14:00:00', NOW(), NOW()),
(10, 'BOND20260715001', 10104, 'n104', '研究员A修改', 'approval', 'initiator', 4, 'pending', '2', '研究员1', NULL, '2026-07-15 14:00:00', NULL, NOW(), NOW()),
(11, 'BOND20260716001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-07-16 09:00:00', '2026-07-16 09:00:00', NOW(), NOW()),
(11, 'BOND20260716001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', '2026-07-16 09:00:00', '2026-07-16 09:00:00', NOW(), NOW()),
(11, 'BOND20260716001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', '2026-07-16 14:00:00', '2026-07-16 14:00:00', NOW(), NOW()),
(11, 'BOND20260716001', 10105, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'reject', '4', '研究员3', 'reject', '2026-07-16 14:00:00', '2026-07-16 14:00:00', NOW(), NOW()),
(11, 'BOND20260716001', 10107, 'n107', '结束', 'end', NULL, 5, 'auto_process', NULL, NULL, 'auto_process', '2026-07-16 14:00:00', '2026-07-16 14:00:00', NOW(), NOW()),
(12, 'BOND20260717001', 10101, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-07-17 09:00:00', '2026-07-17 09:00:00', NOW(), NOW()),
(12, 'BOND20260717001', 10102, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', '2026-07-17 09:00:00', '2026-07-17 09:00:00', NOW(), NOW()),
(12, 'BOND20260717001', 10103, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'canceled', '3', '研究员2', 'skipped', '2026-07-17 14:00:00', '2026-07-17 14:00:00', NOW(), NOW());


-- 按提交时主档生成 Demo 信息快照，验证详情读取及证券市场代码留痕。
TRUNCATE TABLE `ip_adjust_security_snapshot`;
INSERT INTO `ip_adjust_security_snapshot` (
    `adjust_log_id`
    ,`submitter_id`
    ,`submit_time`
    ,`isin_code`
    ,`full_name`
    ,`short_name`
    ,`issuer`
    ,`issuer_code`
    ,`wind_code_nib`
    ,`wind_code_sh`
    ,`wind_code_sz`
    ,`wind_code_bj`
    ,`issue_amountplan`
    ,`coupon_rate`
    ,`date_inright_exists`
    ,`carry_date`
    ,`maturity_date`
    ,`info_pledge_ratio`
    ,`rating_bond_agency`
    ,`rating_bond`
    ,`rating_bondissuer`
    ,`rating_outlook`
    ,`guarantor`
    ,`guarantor_id`
    ,`inner_issuer_rating`
    ,`security_type`
    ,`sec_typename`
    ,`date_call_exists`
    ,`inner_guarantor_rating`
    ,`date_exists`
    ,`date_exists_str`
    ,`wind_code_nbc`
    ,`term_year`
    ,`term_day`
    ,`comp_type`
    ,`date_repurchase_exists`
    ,`abs_flag`
    ,`guarant_flag`
    ,`guarant_type`
    ,`rating_bondissuer_agency`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
)
SELECT
    l.id
    ,l.adjuster_id
    ,l.submit_time
    ,s.isin_code
    ,s.full_name
    ,s.short_name
    ,s.issuer
    ,s.issuer_code
    ,s.wind_code_nib
    ,s.wind_code_sh
    ,s.wind_code_sz
    ,s.wind_code_bj
    ,s.issue_amountplan
    ,s.coupon_rate
    ,s.date_inright_exists
    ,s.carry_date
    ,s.maturity_date
    ,s.info_pledge_ratio
    ,s.rating_bond_agency
    ,s.rating_bond
    ,s.rating_bondissuer
    ,s.rating_outlook
    ,s.guarantor
    ,s.guarantor_id
    ,s.inner_issuer_rating
    ,s.security_type
    ,s.sec_typename
    ,s.date_call_exists
    ,s.inner_guarantor_rating
    ,s.date_exists
    ,s.date_exists_str
    ,s.wind_code_nbc
    ,s.term_year
    ,s.term_day
    ,s.comp_type
    ,s.date_repurchase_exists
    ,s.abs_flag
    ,s.guarant_flag
    ,s.guarant_type
    ,s.rating_bondissuer_agency
    ,0
    ,l.submit_time
    ,l.submit_time
FROM `ip_adjust_log` l
INNER JOIN `rrs_securityinfo` s ON s.isin_code = l.isin_code
WHERE l.security_type <> 'company' AND l.pool_type <> 'crmw' AND l.adjust_type = '手工调整';
