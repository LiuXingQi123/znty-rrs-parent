-- ============================================================
-- znty-rrs 定时任务配置 - 演示数据
-- MySQL version: 8.0.33
-- 说明：预置已知任务配置；task_code 须与 RrsScheduledTask 实现类一致
-- ============================================================

USE `znty_rrs`;
SET NAMES utf8mb4;

DELETE FROM `sys_scheduled_task_run_log`;
DELETE FROM `sys_scheduled_task_evt`;
DELETE FROM `sys_scheduled_task`;

INSERT INTO `sys_scheduled_task` (
    `id`, `task_code`, `task_name`, `description`, `cron_expression`, `schedule_enabled`,
    `param_json`, `last_run_status`, `last_run_message`, `last_run_time`,
    `last_affected_count`, `last_duration_ms`, `last_trigger_type`,
    `is_deleted`, `crte_time`, `updt_time`
) VALUES
(1, 'security_expired_auto_out', '到期证券自动出池',
 '按 param_json.poolIds 扫描指定投资池：池内已生效（audit_status=20）且主数据到期日早于当天的证券自动调出；不走审批，adjust_type=自动调整',
 '0 0 2 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(2, 'company_inpool_bond_auto_in', '在池主体旗下债券自动入池',
 '扫描主体所在池内已在池主体，将其旗下 bond 大类、未到期、尚未在目标池的债券自动入池（排除临时代码已更新正式代码）；主场景债券禁止库；支持 poolIds 同池或 mappings 跨池',
 '0 0 3 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(3, 'company_outer_rating_aa_minus_auto_in', '外评AA-及以下主体自动入池',
 '对应老系统 AdjustRuleInAA：扫描 Wind 主体最新外评落在 AA-/A/BBB… 列表内（不含 AA/AA+/AAA）的主体，对 param_json.poolIds 指定池自动调入（security_type=company，未在池才入）；不走审批',
 '0 0 4 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW());
