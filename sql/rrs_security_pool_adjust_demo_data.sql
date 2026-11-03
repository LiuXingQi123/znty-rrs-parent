-- ============================================================
-- 调库日志 / 池状态 / 流程步骤 演示数据（不含外部导入表，见 rrs_external_import_demo_data.sql）
-- MySQL version: 8.0.33
-- 说明：
--   同券三个市场代码要么全部在同一池，要么全部不在池；在池状态均对应已通过日志
--   C10005 在禁止库15及黑名单质押库17，旗下4只债券同步在池；主体批次统一 COMP 前缀
--   GSB001.SH 一级库申请、C10003 禁止库申请待审批，不预写池状态
--   关联项与主项共用批次和流程步骤；已完成主项补齐从开始到结束的步骤
--   代码均对齐 rrs_securityinfo / t_inv_company；evt 表不灌演示数据
--   已在库 ABS 与主档、申请快照使用同一生效身份；其他 ABS 初始身份留空
--   ABS 首次成功入库后永久固定主体，全部出库也不解除；从未成功的驳回或撤回申请不绑定
--   快照 guarantor / guarantor_id / inner_guarantor_rating 保存本次实际选定的单一权益人或担保人，不能照搬主档候选列表
--   明确选择：108008901.IB=C10008；DBB001.IB/DBB003.IB=C10010；DBB002.IB=C10008
--   观察池主体同步旗下已确定主体的证券；产品库1预置标的和 CRMW 凭证，供跨子库组合
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 清空业务表
-- ----------------------------------------------------------------------------
-- 清空证券调库申请快照
TRUNCATE TABLE `ip_adjust_security_snapshot`;
-- 清空 CRMW 调库申请快照
TRUNCATE TABLE `ip_adjust_security_snapshot_crmw`;
-- 清空证券池调库记录表
TRUNCATE TABLE `ip_adjust_log`;
-- 清空投资池当前状态表
TRUNCATE TABLE `ip_pool_status`;
-- 清空证券池调库-流程步骤记录表
TRUNCATE TABLE `ip_adjust_step`;

-- ----------------------------------------------------------------------------
-- 投资池当前状态样例（40 条）
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
('C10001', '某省交通投资集团有限公司', 'company', '手工调整', '调入', 'COMP20260310001', 4, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体调入观察池', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('C10002', '某城市投资开发建设有限公司', 'company', '手工调整', '调入', 'COMP20260311001', 5, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体调入观察池', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('C10005', '某地产集团股份有限公司', 'company', '手工调整', '调入', 'COMP20260312001', 6, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体调入债券禁止库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('198101.SH', '24交投信用', 'credit_bond', '关联调整', '调入', 'BOND20260302001', 10, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '同券跨市场代码同步调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
('128101.SZ', '24交投信用', 'credit_bond', '关联调整', '调入', 'BOND20260302001', 11, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '同券跨市场代码同步调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
('C10005', '某地产集团股份有限公司', 'company', '联动调整', '调入', 'COMP20260312001', 12, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体在债券禁止库，联动调入黑名单质押库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CRMW005.IB', 'CRMW-E', 'crmw', '自动调整', '调入', 'COMP20260312001', 13, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('PPB001.IB', '24地产私募', 'company_bond', '自动调整', '调入', 'COMP20260312001', 14, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('DBB002.IB', '24地产担保', 'company_bond', '自动调整', '调入', 'COMP20260312001', 15, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CP001.IB', '24地产CP', 'cp', '自动调整', '调入', 'COMP20260312001', 16, 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CRMW005.IB', 'CRMW-E', 'crmw', '自动调整', '调入', 'COMP20260312001', 17, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('PPB001.IB', '24地产私募', 'company_bond', '自动调整', '调入', 'COMP20260312001', 18, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('DBB002.IB', '24地产担保', 'company_bond', '自动调整', '调入', 'COMP20260312001', 19, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CP001.IB', '24地产CP', 'cp', '自动调整', '调入', 'COMP20260312001', 20, 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
('CRMW001.IB', 'CRMW-A', 'crmw', '自动调整', '调入', 'COMP20260310001', 21, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('ABN003.IB', '25交投ABS全', 'abs_all', '自动调整', '调入', 'COMP20260310001', 22, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('YXB001.IB', '24交投永续', 'mtn', '自动调整', '调入', 'COMP20260310001', 23, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('HQB002.SH', '24交投含权', 'company_bond', '自动调整', '调入', 'COMP20260310001', 24, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('XYB001.IB', '24交投信用', 'credit_bond', '自动调整', '调入', 'COMP20260310001', 25, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('TB001.IB', '24国债01', 'treasury_bond', '自动调整', '调入', 'COMP20260310001', 26, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('MTN001.IB', '24交投MTN', 'mtn', '自动调整', '调入', 'COMP20260310001', 27, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('GOVT001.IB', '24交投Govt', 'govt_bond_intl', '自动调整', '调入', 'COMP20260310001', 28, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('198101.SH', '24交投信用', 'credit_bond', '自动调整', '调入', 'COMP20260310001', 29, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('128101.SZ', '24交投信用', 'credit_bond', '自动调整', '调入', 'COMP20260310001', 30, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('TMP001.IB', '交投临时中票', 'mtn', '自动调整', '调入', 'COMP20260310001', 31, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('WCTMP001.IB', '换码临时中票', 'mtn', '自动调整', '调入', 'COMP20260310001', 32, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
('CRMW002.IB', 'CRMW-B', 'crmw', '自动调整', '调入', 'COMP20260311001', 33, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('PPB003.SH', '25城投私募', 'company_bond', '自动调整', '调入', 'COMP20260311001', 34, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('DBB003.IB', '25城投担保', 'company_bond', '自动调整', '调入', 'COMP20260311001', 35, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('XYB002.IB', '24城投信用', 'credit_bond', '自动调整', '调入', 'COMP20260311001', 36, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('PPN001.IB', '24城投PPN', 'ppn', '自动调整', '调入', 'COMP20260311001', 37, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('198102.SH', '24城投信用', 'credit_bond', '自动调整', '调入', 'COMP20260311001', 38, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('128102.SZ', '24城投信用', 'credit_bond', '自动调整', '调入', 'COMP20260311001', 39, 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
('MTN001.IB', '24交投MTN', 'mtn', '手工调整', '调入', 'BOND20260320001', 40, 163, '产品库1标的证券', 'bond_product', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '债券产品库1来源样本，供 CRMW 跨子库组合调入', '2026-03-20 09:00:00', '2026-03-20 14:00:00', '2026-03-20 14:00:00', 0, NOW(), NOW()),
('DBB001.IB', '24基建担保', 'company_bond', '手工调整', '调入', 'BOND20260320002', 41, 163, '产品库1标的证券', 'bond_product', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '债券产品库1来源样本，供 CRMW 跨子库组合调入', '2026-03-20 09:00:00', '2026-03-20 14:00:00', '2026-03-20 14:00:00', 0, NOW(), NOW()),
('CRMW001.IB', 'CRMW-A', 'crmw', 'Excel导入', '调入', 'BOND20260320003', 42, 164, '产品库1凭证', 'bond_product', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '债券产品库1来源样本，供 CRMW 跨子库组合调入', '2026-03-20 09:00:00', '2026-03-20 14:00:00', '2026-03-20 14:00:00', 0, NOW(), NOW()),
('CRMW004.IB', 'CRMW-D', 'crmw', 'Excel导入', '调入', 'BOND20260320004', 43, 164, '产品库1凭证', 'bond_product', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '债券产品库1来源样本，供 CRMW 跨子库组合调入', '2026-03-20 09:00:00', '2026-03-20 14:00:00', '2026-03-20 14:00:00', 0, NOW(), NOW());

-- ----------------------------------------------------------------------------
-- 证券池调库记录样例（43 条）
-- ----------------------------------------------------------------------------
INSERT INTO `ip_adjust_log` (
    `id`, `security_code`, `security_short_name`, `security_type`, `crmw_name`, `crmw_scode`,
    `crmw_mktcode`, `crmw_stype`, `adjust_type`, `adjust_mode`, `adjust_batch_no`,
    `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`,
    `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `submit_time`, `audit_time`,
    `entry_time`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
(1, 'MTN001.IB', '24交投MTN', 'mtn', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260301001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投中票调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(2, 'XYB001.IB', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260302001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '交投信用债调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(3, '108008901.IB', '24资ABS01', 'abs', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260303001', 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'ABS债调入二级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
(4, 'C10001', '某省交通投资集团有限公司', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体调入观察池', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(5, 'C10002', '某城市投资开发建设有限公司', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体调入观察池', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(6, 'C10005', '某地产集团股份有限公司', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体调入债券禁止库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(7, 'MTN001.IB', '24交投MTN', 'mtn', '某CRMW凭证A', 'CRMW001.IB', 'NIB', 'crmw', '手工调整', '调入', 'CRMW20260301001', 18, 'CRMW库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW调入CRMW库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(8, 'GSB001.SH', '24能E1', 'company_bond', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260710001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '00', '1', '管理员', '能源公司债调入一级库待审批', '2026-07-10 09:00:00', NULL, NULL, 0, NOW(), NOW()),
(9, 'C10003', '某能源集团有限公司', 'company', NULL, NULL, NULL, NULL, '手工调整', '调入', 'COMP20260714001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '00', '1', '管理员', '主体调入债券禁止库待审批', '2026-07-14 09:00:00', NULL, NULL, 0, NOW(), NOW()),
(10, '198101.SH', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '关联调整', '调入', 'BOND20260302001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '同券跨市场代码同步调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(11, '128101.SZ', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '关联调整', '调入', 'BOND20260302001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '同券跨市场代码同步调入一级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(12, 'C10005', '某地产集团股份有限公司', 'company', NULL, NULL, NULL, NULL, '联动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体在债券禁止库，联动调入黑名单质押库', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(13, 'CRMW005.IB', 'CRMW-E', 'crmw', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(14, 'PPB001.IB', '24地产私募', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(15, 'DBB002.IB', '24地产担保', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(16, 'CP001.IB', '24地产CP', 'cp', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 15, '债券禁止库', 'forbidden', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(17, 'CRMW005.IB', 'CRMW-E', 'crmw', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(18, 'PPB001.IB', '24地产私募', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(19, 'DBB002.IB', '24地产担保', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(20, 'CP001.IB', '24地产CP', 'cp', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260312001', 17, '黑名单质押库', 'blacklist', 113, 'company:forbidden-inbound', 'normalInbound', '20', '1', '管理员', '主体入池后同步旗下债券', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
(21, 'CRMW001.IB', 'CRMW-A', 'crmw', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(22, 'ABN003.IB', '25交投ABS全', 'abs_all', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(23, 'YXB001.IB', '24交投永续', 'mtn', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(24, 'HQB002.SH', '24交投含权', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(25, 'XYB001.IB', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(26, 'TB001.IB', '24国债01', 'treasury_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(27, 'MTN001.IB', '24交投MTN', 'mtn', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(28, 'GOVT001.IB', '24交投Govt', 'govt_bond_intl', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(29, '198101.SH', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(30, '128101.SZ', '24交投信用', 'credit_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(31, 'TMP001.IB', '交投临时中票', 'mtn', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(32, 'WCTMP001.IB', '换码临时中票', 'mtn', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260310001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(33, 'CRMW002.IB', 'CRMW-B', 'crmw', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(34, 'PPB003.SH', '25城投私募', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(35, 'DBB003.IB', '25城投担保', 'company_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(36, 'XYB002.IB', '24城投信用', 'credit_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(37, 'PPN001.IB', '24城投PPN', 'ppn', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(38, '198102.SH', '24城投信用', 'credit_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(39, '128102.SZ', '24城投信用', 'credit_bond', NULL, NULL, NULL, NULL, '自动调整', '调入', 'COMP20260311001', 16, '观察池', 'observe', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', '主体进入观察池，同批同步旗下已确定主体的未到期证券', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
(40, 'MTN001.IB', '24交投MTN', 'mtn', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260320001', 163, '产品库1标的证券', 'bond_product', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '债券产品库1来源样本，供 CRMW 跨子库组合调入', '2026-03-20 09:00:00', '2026-03-20 14:00:00', '2026-03-20 14:00:00', 0, NOW(), NOW()),
(41, 'DBB001.IB', '24基建担保', 'company_bond', NULL, NULL, NULL, NULL, '手工调整', '调入', 'BOND20260320002', 163, '产品库1标的证券', 'bond_product', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '债券产品库1来源样本，供 CRMW 跨子库组合调入', '2026-03-20 09:00:00', '2026-03-20 14:00:00', '2026-03-20 14:00:00', 0, NOW(), NOW()),
(42, 'CRMW001.IB', 'CRMW-A', 'crmw', NULL, NULL, NULL, NULL, 'Excel导入', '调入', 'BOND20260320003', 164, '产品库1凭证', 'bond_product', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '债券产品库1来源样本，供 CRMW 跨子库组合调入', '2026-03-20 09:00:00', '2026-03-20 14:00:00', '2026-03-20 14:00:00', 0, NOW(), NOW()),
(43, 'CRMW004.IB', 'CRMW-D', 'crmw', NULL, NULL, NULL, NULL, 'Excel导入', '调入', 'BOND20260320004', 164, '产品库1凭证', 'bond_product', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '债券产品库1来源样本，供 CRMW 跨子库组合调入', '2026-03-20 09:00:00', '2026-03-20 14:00:00', '2026-03-20 14:00:00', 0, NOW(), NOW());

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
(9, 'COMP20260714001', 11303, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-14 09:00:00', NULL, NOW(), NOW()),
(40, 'BOND20260320001', 10501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(40, 'BOND20260320001', 10502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(40, 'BOND20260320001', 10503, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(41, 'BOND20260320002', 10501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(41, 'BOND20260320002', 10502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(41, 'BOND20260320002', 10503, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(42, 'BOND20260320003', 10501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(42, 'BOND20260320003', 10502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(42, 'BOND20260320003', 10503, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(43, 'BOND20260320004', 10501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(43, 'BOND20260320004', 10502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW()),
(43, 'BOND20260320004', 10503, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', '2026-03-20 09:00:00', '2026-03-20 14:00:00', NOW(), NOW());


-- ----------------------------------------------------------------------------
-- 证券申请快照（37 条，逐笔关联申请）
-- ----------------------------------------------------------------------------
INSERT INTO `ip_adjust_security_snapshot` (
    `adjust_log_id`, `submitter_id`, `submit_time`, `wind_code`, `full_name`, `short_name`,
    `issuer`, `issuer_code`, `issue_amountplan`, `coupon_rate`, `date_inright_exists`,
    `carry_date`, `maturity_date`, `info_pledge_ratio`, `rating_bond_agency`, `rating_bond`,
    `rating_bondissuer`, `rating_outlook`, `guarantor`, `guarantor_id`, `agency_name`,
    `inner_issuer_rating`, `security_type`, `sec_typename`, `date_call_exists`,
    `inner_guarantor_rating`, `date_exists`, `date_exists_str`, `fund_use`, `prompt_reason`,
    `analysis`, `term_year`, `term_day`, `maturityembedded_desc`, `comp_type`, `issue_amountact`,
    `date_repurchase_exists`, `abs_flag`, `guarant_flag`, `guarant_type`,
    `rating_bondissuer_agency`, `agency_nameid`, `bond_biz_type`, `abs_originator_name`,
    `abs_share_ratio`, `expected_maturity_date`, `legal_maturity_date`, `abs_report_org`,
    `abs_custodian`, `company_selector`, `extra_ind_1`, `extra_ind_2`, `extra_ind_3`,
    `is_deleted`, `crte_time`, `updt_time`
) VALUES
(1, '1', '2026-03-01 09:00:00', 'MTN001.IB', '24交投MTN001', '24交投MTN', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'mtn', '中期票据', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-01 09:00:00', '2026-03-01 09:00:00'),
(2, '1', '2026-03-02 09:00:00', 'XYB001.IB', '24交投信用债01', '24交投信用', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6天', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-02 09:00:00', '2026-03-02 09:00:00'),
(3, '1', '2026-03-03 09:00:00', '108008901.IB', '24某资ABS01', '24资ABS01', '某资产管理股份有限公司', 'C10008', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', '某资产管理股份有限公司', 'C10008', NULL, '1', 'abs', '资产支持证券', NULL, '1', 365.0000, '1年', NULL, NULL, NULL, 1, 365.0000, NULL, '资产管理公司', NULL, NULL, 1, 0, NULL, '2', NULL, NULL, '某资产管理股份有限公司', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-03 09:00:00', '2026-03-03 09:00:00'),
(8, '1', '2026-07-10 09:00:00', 'GSB001.SH', '24能源公司债01', '24能E1', '某能源集团有限公司', 'C10003', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'company_bond', '公司债', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-07-10 09:00:00', '2026-07-10 09:00:00'),
(10, '1', '2026-03-02 09:00:00', '198101.SH', '24交投信用债01', '24交投信用', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6天', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-02 09:00:00', '2026-03-02 09:00:00'),
(11, '1', '2026-03-02 09:00:00', '128101.SZ', '24交投信用债01', '24交投信用', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6天', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-02 09:00:00', '2026-03-02 09:00:00'),
(13, '1', '2026-03-12 09:00:00', 'CRMW005.IB', '某CRMW凭证E', 'CRMW-E', '某地产集团股份有限公司', 'C10005', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '负面', NULL, NULL, NULL, '2', 'crmw', '信用风险缓释凭证', NULL, NULL, 730.0000, '2年', NULL, NULL, NULL, 2, 730.0000, NULL, '民营企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-12 09:00:00', '2026-03-12 09:00:00'),
(14, '1', '2026-03-12 09:00:00', 'PPB001.IB', '24地产私募债01', '24地产私募', '某地产集团股份有限公司', 'C10005', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '负面', NULL, NULL, NULL, '2', 'company_bond', '公司债', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '民营企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-12 09:00:00', '2026-03-12 09:00:00'),
(15, '1', '2026-03-12 09:00:00', 'DBB002.IB', '24地产担保债01', '24地产担保', '某地产集团股份有限公司', 'C10005', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '负面', '某资产管理股份有限公司', 'C10008', NULL, '2', 'company_bond', '公司债', NULL, '1', 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '民营企业', NULL, NULL, 0, 1, '连带责任保证', '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-12 09:00:00', '2026-03-12 09:00:00'),
(16, '1', '2026-03-12 09:00:00', 'CP001.IB', '24地产CP001', '24地产CP', '某地产集团股份有限公司', 'C10005', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '负面', NULL, NULL, NULL, '2', 'cp', '短期融资券', NULL, NULL, 270.0000, '6个月3天', NULL, NULL, NULL, 0.75, 270.0000, NULL, '民营企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-12 09:00:00', '2026-03-12 09:00:00'),
(17, '1', '2026-03-12 09:00:00', 'CRMW005.IB', '某CRMW凭证E', 'CRMW-E', '某地产集团股份有限公司', 'C10005', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '负面', NULL, NULL, NULL, '2', 'crmw', '信用风险缓释凭证', NULL, NULL, 730.0000, '2年', NULL, NULL, NULL, 2, 730.0000, NULL, '民营企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-12 09:00:00', '2026-03-12 09:00:00'),
(18, '1', '2026-03-12 09:00:00', 'PPB001.IB', '24地产私募债01', '24地产私募', '某地产集团股份有限公司', 'C10005', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '负面', NULL, NULL, NULL, '2', 'company_bond', '公司债', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '民营企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-12 09:00:00', '2026-03-12 09:00:00'),
(19, '1', '2026-03-12 09:00:00', 'DBB002.IB', '24地产担保债01', '24地产担保', '某地产集团股份有限公司', 'C10005', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '负面', '某资产管理股份有限公司', 'C10008', NULL, '2', 'company_bond', '公司债', NULL, '1', 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '民营企业', NULL, NULL, 0, 1, '连带责任保证', '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-12 09:00:00', '2026-03-12 09:00:00'),
(20, '1', '2026-03-12 09:00:00', 'CP001.IB', '24地产CP001', '24地产CP', '某地产集团股份有限公司', 'C10005', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '负面', NULL, NULL, NULL, '2', 'cp', '短期融资券', NULL, NULL, 270.0000, '6个月3天', NULL, NULL, NULL, 0.75, 270.0000, NULL, '民营企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-12 09:00:00', '2026-03-12 09:00:00'),
(21, '1', '2026-03-10 09:00:00', 'CRMW001.IB', '某CRMW凭证A', 'CRMW-A', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'crmw', '信用风险缓释凭证', NULL, NULL, 730.0000, '2年', NULL, NULL, NULL, 2, 730.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(22, '1', '2026-03-10 09:00:00', 'ABN003.IB', '25交投资产证券化01', '25交投ABS全', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'abs_all', '资产证券化', NULL, NULL, 365.0000, '1年', NULL, NULL, NULL, 1, 365.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(23, '1', '2026-03-10 09:00:00', 'YXB001.IB', '24交投永续MTN', '24交投永续', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'mtn', '中期票据', NULL, NULL, 1825.0000, '5年', NULL, NULL, NULL, 5, 1825.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(24, '1', '2026-03-10 09:00:00', 'HQB002.SH', '24交投含权债01', '24交投含权', '某省交通投资集团有限公司', 'C10001', 10, '3.5', 2.0000, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'company_bond', '公司债', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(25, '1', '2026-03-10 09:00:00', 'XYB001.IB', '24交投信用债01', '24交投信用', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6天', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(26, '1', '2026-03-10 09:00:00', 'TB001.IB', '24国债01', '24国债01', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'treasury_bond', '国债', NULL, NULL, 3650.0000, '10年', NULL, NULL, NULL, 10, 3650.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(27, '1', '2026-03-10 09:00:00', 'MTN001.IB', '24交投MTN001', '24交投MTN', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'mtn', '中期票据', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(28, '1', '2026-03-10 09:00:00', 'GOVT001.IB', '24交投Govt债01', '24交投Govt', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'govt_bond_intl', 'Govt债券', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(29, '1', '2026-03-10 09:00:00', '198101.SH', '24交投信用债01', '24交投信用', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6天', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(30, '1', '2026-03-10 09:00:00', '128101.SZ', '24交投信用债01', '24交投信用', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6天', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(31, '1', '2026-03-10 09:00:00', 'TMP001.IB', '某交投临时中票', '交投临时中票', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, NULL, '20310701', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'mtn', '中期票据', NULL, NULL, NULL, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(32, '1', '2026-03-10 09:00:00', 'WCTMP001.IB', 'Wind临时换码中票', '换码临时中票', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, NULL, '20310701', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'mtn', '中期票据', NULL, NULL, NULL, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-10 09:00:00', '2026-03-10 09:00:00'),
(33, '1', '2026-03-11 09:00:00', 'CRMW002.IB', '某CRMW凭证B', 'CRMW-B', '某城市投资开发建设有限公司', 'C10002', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '稳定', NULL, NULL, NULL, '2+', 'crmw', '信用风险缓释凭证', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '城投公司', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-11 09:00:00', '2026-03-11 09:00:00'),
(34, '1', '2026-03-11 09:00:00', 'PPB003.SH', '25城投私募债01', '25城投私募', '某城市投资开发建设有限公司', 'C10002', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '稳定', NULL, NULL, NULL, '2+', 'company_bond', '公司债', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '城投公司', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-11 09:00:00', '2026-03-11 09:00:00'),
(35, '1', '2026-03-11 09:00:00', 'DBB003.IB', '25城投担保债01', '25城投担保', '某城市投资开发建设有限公司', 'C10002', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '稳定', '某金融控股有限公司', 'C10010', NULL, '2+', 'company_bond', '公司债', NULL, '1', 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '城投公司', NULL, NULL, 0, 1, '连带责任保证', '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-11 09:00:00', '2026-03-11 09:00:00'),
(36, '1', '2026-03-11 09:00:00', 'XYB002.IB', '24城投信用债01', '24城投信用', '某城市投资开发建设有限公司', 'C10002', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '稳定', NULL, NULL, NULL, '2+', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6个月3天', NULL, NULL, NULL, 3, 1095.0000, NULL, '城投公司', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-11 09:00:00', '2026-03-11 09:00:00'),
(37, '1', '2026-03-11 09:00:00', 'PPN001.IB', '24城投定向工具01', '24城投PPN', '某城市投资开发建设有限公司', 'C10002', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '稳定', NULL, NULL, NULL, '2+', 'ppn', '定向工具', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '城投公司', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-11 09:00:00', '2026-03-11 09:00:00'),
(38, '1', '2026-03-11 09:00:00', '198102.SH', '24城投信用债01', '24城投信用', '某城市投资开发建设有限公司', 'C10002', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '稳定', NULL, NULL, NULL, '2+', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6个月3天', NULL, NULL, NULL, 3, 1095.0000, NULL, '城投公司', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-11 09:00:00', '2026-03-11 09:00:00'),
(39, '1', '2026-03-11 09:00:00', '128102.SZ', '24城投信用债01', '24城投信用', '某城市投资开发建设有限公司', 'C10002', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA+', 'AA+', '稳定', NULL, NULL, NULL, '2+', 'credit_bond', '信用债', NULL, NULL, 1095.0000, '3年6个月3天', NULL, NULL, NULL, 3, 1095.0000, NULL, '城投公司', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-11 09:00:00', '2026-03-11 09:00:00'),
(40, '1', '2026-03-20 09:00:00', 'MTN001.IB', '24交投MTN001', '24交投MTN', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'mtn', '中期票据', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-20 09:00:00', '2026-03-20 09:00:00'),
(41, '1', '2026-03-20 09:00:00', 'DBB001.IB', '24基建担保债01', '24基建担保', '某基础设施建设有限公司', 'C10004', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA', 'AA', '稳定', '某金融控股有限公司', 'C10010', NULL, '2', 'company_bond', '公司债', NULL, '1', 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 1, '连带责任保证', '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-20 09:00:00', '2026-03-20 09:00:00'),
(42, '1', '2026-03-20 09:00:00', 'CRMW001.IB', '某CRMW凭证A', 'CRMW-A', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'crmw', '信用风险缓释凭证', NULL, NULL, 730.0000, '2年', NULL, NULL, NULL, 2, 730.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-20 09:00:00', '2026-03-20 09:00:00'),
(43, '1', '2026-03-20 09:00:00', 'CRMW004.IB', '某CRMW凭证D', 'CRMW-D', '某基础设施建设有限公司', 'C10004', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AA', 'AA', '稳定', NULL, NULL, NULL, '2', 'crmw', '信用风险缓释凭证', NULL, NULL, 548.0000, '1年6个月', NULL, NULL, NULL, 1.5, 548.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-20 09:00:00', '2026-03-20 09:00:00');


-- ----------------------------------------------------------------------------
-- CRMW 申请快照（1 条，逐笔关联申请）
-- ----------------------------------------------------------------------------
INSERT INTO `ip_adjust_security_snapshot_crmw` (
    `adjust_log_id`, `submitter_id`, `submit_time`, `crmw_name`, `crmw_scode`, `crmw_mktcode`,
    `crmw_stype`, `wind_code`, `full_name`, `short_name`, `issuer`, `issuer_code`,
    `issue_amountplan`, `coupon_rate`, `date_inright_exists`, `carry_date`, `maturity_date`,
    `info_pledge_ratio`, `rating_bond_agency`, `rating_bond`, `rating_bondissuer`,
    `rating_outlook`, `guarantor`, `guarantor_id`, `agency_name`, `inner_issuer_rating`,
    `security_type`, `sec_typename`, `date_call_exists`, `inner_guarantor_rating`, `date_exists`,
    `date_exists_str`, `fund_use`, `prompt_reason`, `analysis`, `term_year`, `term_day`,
    `maturityembedded_desc`, `comp_type`, `issue_amountact`, `date_repurchase_exists`, `abs_flag`,
    `guarant_flag`, `guarant_type`, `rating_bondissuer_agency`, `agency_nameid`, `bond_biz_type`,
    `abs_originator_name`, `abs_share_ratio`, `expected_maturity_date`, `legal_maturity_date`,
    `abs_report_org`, `abs_custodian`, `company_selector`, `extra_ind_1`, `extra_ind_2`,
    `extra_ind_3`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
(7, '1', '2026-03-01 09:00:00', '某CRMW凭证A', 'CRMW001.IB', 'NIB', 'crmw', 'MTN001.IB', '24交投MTN001', '24交投MTN', '某省交通投资集团有限公司', 'C10001', 10, '3.5', NULL, '20240318', '20290318', 0, '中诚信', 'AAA', 'AAA', '稳定', NULL, NULL, NULL, '1', 'mtn', '中期票据', NULL, NULL, 1095.0000, '3年', NULL, NULL, NULL, 3, 1095.0000, NULL, '国有企业', NULL, NULL, 0, 0, NULL, '中诚信', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '2026-03-01 09:00:00', '2026-03-01 09:00:00');
