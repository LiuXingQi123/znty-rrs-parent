-- ============================================================
-- CRMW 池状态演示数据
-- MySQL version: 8.0.33
-- 说明：CRMW 在池 1 条；凭证 CRMW001.IB + 标的 MTN001.IB（同属 C10001）
--       adjust_log_id=7 对齐 rrs_security_pool_adjust_demo_data.sql
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `ip_pool_status_crmw`;

INSERT INTO `ip_pool_status_crmw` (
    `security_code`, `security_short_name`, `security_type`,
    `crmw_name`, `crmw_scode`, `crmw_mktcode`, `crmw_stype`,
    `adjust_type`, `adjust_mode`, `adjust_batch_no`, `adjust_log_id`,
    `target_pool_id`, `target_pool_name`, `pool_type`,
    `flow_id`, `flow_key`, `flow_type`,
    `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`,
    `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
('MTN001.IB', '24交投MTN', 'mtn', '某CRMW凭证A', 'CRMW001.IB', 'CIBM', 'crmw', '手工调整', '调入', 'CRMW20260301001', 7, 18, 'CRMW库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW调入CRMW库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW());
