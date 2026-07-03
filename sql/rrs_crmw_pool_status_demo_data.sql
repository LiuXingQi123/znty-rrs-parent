-- ============================================================
-- znty-sirm CRMW投资池当前状态表 - 演示数据脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，插入CRMW投资池当前状态测试数据
-- ============================================================

USE znty_rrs;
SET NAMES utf8mb4;

TRUNCATE TABLE `ip_pool_status_crmw`;

INSERT INTO `ip_pool_status_crmw` (
    `security_code`, `security_short_name`, `security_type`,
    `crmw_name`, `crmw_scode`, `crmw_mktcode`, `crmw_stype`,
    `adjust_type`, `adjust_mode`, `adjust_log_id`, `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`,
    `flow_id`, `flow_key`, `flow_type`,
    `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `adjust_advice`,
    `submit_time`, `audit_time`, `entry_time`,
    `is_deleted`, `crte_time`, `updt_time`
) VALUES
('101901234', '24某交投MTN001', 'mtn',
 '24某交投CRMW001', 'CRMW24001.IB', 'CIBM', 'crmw',
 '手工调整', '调入', 35, 'CRMW202606131000001001', 19, 'CRMW核心库', 'crmw',
 NULL, NULL, NULL,
 '20', '1001', '管理员', '交投主体资质稳定，挂钩债券符合CRMW库准入标准', '审批通过，已调入CRMW库',
 '2026-06-13 10:00:00', '2026-06-13 15:00:00', '2026-06-14 09:00:00',
 0, NOW(), NOW()),
('102002345', '23某城投债', 'company_bond',
 '23某城投CRMW001', 'CRMW23001.IB', 'CIBM', 'crmw',
 '手工调整', '调入', 36, 'CRMW202606141030001001', 20, 'CRMW关注库', 'crmw',
 NULL, NULL, NULL,
 '20', '2', '研究员2', '城投债增信凭证发行要素完整，调入CRMW库跟踪', '审批通过，已调入CRMW库',
 '2026-06-14 10:30:00', '2026-06-14 16:30:00', '2026-06-15 09:00:00',
 0, NOW(), NOW()),
('106006789', '22某电力MTN001', 'mtn',
 '22某电力CRMW001', 'CRMW22001.IB', 'CIBM', 'crmw',
 '手工调整', '调入', 40, 'CRMW202606181430001001', 19, 'CRMW核心库', 'crmw',
 NULL, NULL, NULL,
 '20', '9', '固收4', '电力央企经营稳定，CRMW风险缓释效果明确', '审批通过，已调入CRMW库',
 '2026-06-18 14:30:00', '2026-06-18 17:30:00', '2026-06-19 09:00:00',
 0, NOW(), NOW());
