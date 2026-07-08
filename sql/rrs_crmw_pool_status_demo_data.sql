-- ============================================================
-- CRMW 池状态演示数据
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
-- CRMW1 + 升库债1 在 CRMW核心库
('101901234.IB', '24交投MTN001', 'mtn', '某CRMW凭证A', 'CRMW001.IB', 'CIBM', 'crmw', '手工调整', '调入', 'CRMW20260301001', 14, 19, 'CRMW核心库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW调入核心库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
-- CRMW5 + 主体5 在 CRMW核心库（CRMW5调入触发凭证已在池校验）
('C10005', '地产公司', 'company', '某CRMW凭证E', 'CRMW005.IB', 'CIBM', 'crmw', '手工调整', '调入', 'CRMW20260302001', 15, 19, 'CRMW核心库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW已在核心库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW());
