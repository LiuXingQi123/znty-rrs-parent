-- ============================================================
-- 调库日志 / 池状态 / 流程步骤 演示数据（不含外部导入表，见 rrs_external_import_demo_data.sql）
-- 覆盖：债券(可调+触发) / 股票 / 基金 / 主体 / CRMW
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
-- 升库债1 在三级库（可升入一级/二级库）
('101901234.IB', '24交投MTN001', 'mtn', '手工调整', '调入', 'BOND20260301001', 1, 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '正常调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
-- 升库债2 在四级库（可升入二级/三级库）
('102002345.IB', '23城投MTN', 'mtn', '手工调整', '调入', 'BOND20260302001', 2, 5, '四级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '正常调入四级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
-- 降库债1 在一级库（可降入二级/三级库）
('103003456.SH', '24能E1', 'company_bond', '手工调整', '调入', 'BOND20260303001', 3, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '正常调入一级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
-- 降库债2 在二级库（可降入三级/四级库）
('104004567.IB', '23基建债', 'company_bond', '手工调整', '调入', 'BOND20260304001', 4, 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '正常调入二级库', '2026-03-04 09:00:00', '2026-03-04 14:00:00', '2026-03-04 14:00:00', 0, NOW(), NOW()),
-- 普通债1 在一级库
('106006789.IB', '22电力MTN', 'mtn', '手工调整', '调入', 'BOND20260305001', 5, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '正常调入一级库', '2026-03-05 09:00:00', '2026-03-05 14:00:00', '2026-03-05 14:00:00', 0, NOW(), NOW()),
-- 到期债 在一级库（调出触发到期校验）
('105005678.IB', '22地产CP', 'cp', '手工调整', '调入', 'BOND20260306001', 6, 2, '一级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '到期债在一级库', '2026-03-06 09:00:00', '2026-03-06 14:00:00', '2026-03-06 14:00:00', 0, NOW(), NOW()),
-- 冻结期内债 在二级库（entry_time=NOW，调出触发冻结期 frozen_period_in=365）
('108008901.IB', '23某资A', 'abs', '手工调整', '调入', 'BOND20260701001', 7, 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '冻结期内债在二级库', '2026-07-01 09:00:00', '2026-07-01 14:00:00', NOW(), 0, NOW(), NOW()),
-- 担保债 在二级库
('112008888.IB', '24担保债', 'company_bond', '手工调整', '调入', 'BOND20260307001', 8, 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '担保债在二级库', '2026-03-07 09:00:00', '2026-03-07 14:00:00', '2026-03-07 14:00:00', 0, NOW(), NOW()),
-- 普通债2 在境外债库
('109009012.IB', '25国贸SCP', 'scp', '手工调整', '调入', 'BOND20260308001', 9, 7, '境外债库', 'offshore_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '在境外债库', '2026-03-08 09:00:00', '2026-03-08 14:00:00', '2026-03-08 14:00:00', 0, NOW(), NOW()),
-- 基金1 在基金池
('159001.SH', '测试基金A', 'etf_fund', '手工调整', '调入', 'BOND20260309001', 10, 22, '基金池', 'fund', 109, 'fund:fast-inbound', 'normalInbound', '20', '1', '管理员', '基金在基金池', '2026-03-09 09:00:00', '2026-03-09 09:00:00', '2026-03-09 09:00:00', 0, NOW(), NOW()),
-- 主体1 在一级库
('C10001', '交投集团', 'company', '手工调整', '调入', 'BOND20260310001', 11, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '主体在一级库', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
-- 主体2 在二级库
('C10002', '城投公司', 'company', '手工调整', '调入', 'BOND20260311001', 12, 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '主体在二级库', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
-- 主体5 在禁投池（触发禁投池校验）
('C10005', '地产公司', 'company', '手工调整', '调入', 'BOND20260312001', 13, 15, '禁投池', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体在禁投池', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW());

INSERT INTO `ip_adjust_log` (
    `id`, `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`,
    `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`,
    `flow_id`, `flow_key`, `flow_type`,
    `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`,
    `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
-- === 债券-已通过(20) ===
(1, '101901234.IB', '24交投MTN001', 'mtn', '手工调整', '调入', 'BOND20260301001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '升库债1调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(2, '102002345.IB', '23城投MTN', 'mtn', '手工调整', '调入', 'BOND20260302001', 5, '四级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '升库债2调入四级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(3, '103003456.SH', '24能E1', 'company_bond', '手工调整', '调入', 'BOND20260303001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '降库债1调入一级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
(4, '104004567.IB', '23基建债', 'company_bond', '手工调整', '调入', 'BOND20260304001', 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '降库债2调入二级库', '2026-03-04 09:00:00', '2026-03-04 14:00:00', '2026-03-04 14:00:00', 0, NOW(), NOW()),
(5, '106006789.IB', '22电力MTN', 'mtn', '手工调整', '调入', 'BOND20260305001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '普通债1调入一级库', '2026-03-05 09:00:00', '2026-03-05 14:00:00', '2026-03-05 14:00:00', 0, NOW(), NOW()),
(6, '105005678.IB', '22地产CP', 'cp', '手工调整', '调入', 'BOND20260306001', 2, '一级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '到期债调入一级库', '2026-03-06 09:00:00', '2026-03-06 14:00:00', '2026-03-06 14:00:00', 0, NOW(), NOW()),
(7, '108008901.IB', '23某资A', 'abs', '手工调整', '调入', 'BOND20260701001', 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '冻结期内债调入二级库', '2026-07-01 09:00:00', '2026-07-01 14:00:00', NOW(), 0, NOW(), NOW()),
(8, '112008888.IB', '24担保债', 'company_bond', '手工调整', '调入', 'BOND20260307001', 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '担保债调入二级库', '2026-03-07 09:00:00', '2026-03-07 14:00:00', '2026-03-07 14:00:00', 0, NOW(), NOW()),
(9, '109009012.IB', '25国贸SCP', 'scp', '手工调整', '调入', 'BOND20260308001', 7, '境外债库', 'offshore_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '普通债2调入境外债库', '2026-03-08 09:00:00', '2026-03-08 14:00:00', '2026-03-08 14:00:00', 0, NOW(), NOW()),
-- === 债券-待审批(00) ===
(10, '110010123.IB', '23金融债', 'company_bond', '手工调整', '调入', 'BOND20260710001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '00', '1', '管理员', '错行业债调入一级库待审批', '2026-07-10 09:00:00', NULL, NULL, 0, NOW(), NOW()),
-- === 债券-驳回待修改(11) ===
(11, '107007890.IB', '24科技K', 'company_bond', '手工调整', '调入', 'BOND20260711001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '11', '1', '管理员', '低评级债调入一级库被驳回', '2026-07-11 09:00:00', NULL, NULL, 0, NOW(), NOW()),
-- === 债券-审批驳回(21) ===
(12, '107007890.IB', '24科技K', 'company_bond', '手工调整', '调入', 'BOND20260712001', 6, '五级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '21', '1', '管理员', '低评级债调入五级库被驳回', '2026-07-12 09:00:00', '2026-07-12 14:00:00', NULL, 0, NOW(), NOW()),
-- === 债券-已撤回(99) ===
(13, '101901234.IB', '24交投MTN001', 'mtn', '手工调整', '调入', 'BOND20260713001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '99', '1', '管理员', '发起人撤回', '2026-07-13 09:00:00', NULL, NULL, 0, NOW(), NOW()),
-- === 基金-已通过(20) ===
(14, '159001.SH', '测试基金A', 'etf_fund', '手工调整', '调入', 'BOND20260309001', 22, '基金池', 'fund', 109, 'fund:fast-inbound', 'normalInbound', '20', '1', '管理员', '基金调入基金池', '2026-03-09 09:00:00', '2026-03-09 09:00:00', '2026-03-09 09:00:00', 0, NOW(), NOW()),
-- === 主体-已通过(20) ===
(15, 'C10001', '交投集团', 'company', '手工调整', '调入', 'BOND20260310001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '主体调入一级库', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(16, 'C10005', '地产公司', 'company', '手工调整', '调入', 'BOND20260312001', 15, '禁投池', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入禁投池', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
-- === 主体-待审批(00) ===
(17, 'C10003', '能源集团', 'company', '手工调整', '调入', 'BOND20260714001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '00', '1', '管理员', '主体调入一级库待审批', '2026-07-14 09:00:00', NULL, NULL, 0, NOW(), NOW()),
-- === CRMW-已通过(20) ===
(18, '101901234.IB', '24交投MTN001', 'mtn', '手工调整', '调入', 'CRMW20260301001', 19, 'CRMW核心库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW调入核心库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(19, 'C10005', '地产公司', 'company', '手工调整', '调入', 'CRMW20260302001', 19, 'CRMW核心库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW5调入核心库（已在池）', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
-- === CRMW-待审批(00) ===
(20, '102002345.IB', '23城投MTN', 'mtn', '手工调整', '调入', 'CRMW20260715001', 20, 'CRMW关注库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '00', '1', '管理员', 'CRMW调入关注库待审批', '2026-07-15 09:00:00', NULL, NULL, 0, NOW(), NOW());

INSERT INTO `ip_adjust_step` (
    `adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`,
    `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`,
    `start_time`, `process_time`, `crte_time`, `updt_time`
) VALUES
-- 债券：log 10 待审批
(10, 'BOND20260710001', 1012, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-10 09:00:00', '2026-07-10 09:00:00', NOW(), NOW()),
(10, 'BOND20260710001', 1013, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-10 09:00:00', NULL, NOW(), NOW()),
-- 主体：log 17 待审批
(17, 'BOND20260714001', 1012, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-14 09:00:00', '2026-07-14 09:00:00', NOW(), NOW()),
(17, 'BOND20260714001', 1013, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-14 09:00:00', NULL, NOW(), NOW()),
-- CRMW：log 20 待审批
(20, 'CRMW20260715001', 1052, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-15 09:00:00', '2026-07-15 09:00:00', NOW(), NOW()),
(20, 'CRMW20260715001', 1053, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-15 09:00:00', NULL, NOW(), NOW()),
-- 禁投：log 16 已通过（主体入禁投池，无审批流程直通）
(16, 'BOND20260312001', NULL, NULL, '直通落地', 'auto', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-12 09:00:00', '2026-03-12 14:00:00', NOW(), NOW()),
-- 基金：log 14 已通过（直通流程）
(14, 'BOND20260309001', 1092, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-09 09:00:00', '2026-03-09 09:00:00', NOW(), NOW()),
-- 股票：log 11 驳回待修改
(11, 'BOND20260711001', 1012, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-11 09:00:00', '2026-07-11 09:00:00', NOW(), NOW()),
(11, 'BOND20260711001', 1013, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'reject', '3', '研究员2', 'reject', '2026-07-11 09:00:00', '2026-07-11 14:00:00', NOW(), NOW()),
(11, 'BOND20260711001', 1014, 'n4', '研究员A修改', 'approval', 'initiator', 4, 'pending', '1', '管理员', NULL, '2026-07-11 14:00:00', NULL, NOW(), NOW());

