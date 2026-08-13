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
 '按 param_json.poolIds 扫描指定投资池：池内已生效（audit_status=20）且主数据到期日早于昨天（T-2）的证券自动调出；当前在调出限制池则跳过；仅软删成功才写日志；不走审批，adjust_type=自动调整',
 '0 0 2 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(2, 'company_inpool_bond_auto_in', '在池主体旗下债券自动入池',
 '扫描主体所在池内已在池主体，将其旗下 bond 大类、未到期、尚未在目标池的债券自动入池（排除临时代码已更新正式代码；排除 ABS 与 CRMW）；主场景债券禁止库；支持 poolIds 同池或 mappings 跨池',
 '0 0 3 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(3, 'company_outer_rating_aa_minus_auto_in', '外评AA-及以下主体自动入池',
 '对应老系统 AdjustRuleInAA：扫描 Wind 主体最新外评落在 AA-/A/BBB… 列表内（不含 AA/AA+/AAA）的主体，对 param_json.poolIds 指定池自动调入（security_type=company，未在池才入）；不走审批',
 '0 0 4 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(4, 'company_outer_rating_not_aa_minus_auto_out', '外评非AA-及以下主体自动出池',
 '对应老系统 AdjustRuleOutAA：有效外评不在 AA-/A/BBB… 则出池；主体已在 limitPoolIds（默认 forbidden 池）则不出；主体出成功后再出同池旗下债。示例挂观察池并拦截禁止库：{"poolIds":[16],"limitPoolIds":[15]}',
 '0 0 5 * * ?', 0, '{"poolIds":[16],"limitPoolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(5, 'company_same_pool_bond_auto_in', '主体下债券自动入库',
 '对应老系统 IP_RULE type=0「主体下债券自动入库」：主体已在 poolIds 池内 → 旗下 bond 大类未到期且尚未在同一池的债券自动入池（主体与债必须同池）；尊重池 market_codes；不排除临时代码已更新；调入限制池（in_restrict）阻断；不走审批。跨池请用 company_inpool_bond_auto_in',
 '0 0 6 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(6, 'crmw_expired_auto_out', 'CRMW到期自动出池',
 '对应老 IP_RULE AdjustRuleCrmwDueOutPool：扫描 CRMW 池中已生效组合，凭证到期日早于昨天（T-2）则自动调出 ip_pool_status_crmw；调出限制池阻断；仅软删成功才写日志',
 '0 0 7 * * ?', 0, '{"poolIds":[18]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(7, 'company_not_in_pool_bond_auto_out', '主体不在池债券自动出池',
 '对应老 AutoAdjustInLimitPoolToNewBondJob（默认不启用）：债已在债券池、发行主体不在主体池 → 将该债从债券池调出；排除 ABS/CRMW；独立 Job 口径不看限制池。同池 {"poolIds":[15]}，跨池 mappings bondPoolId/companyPoolId',
 '0 0 8 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW());
