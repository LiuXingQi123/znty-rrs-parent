-- ============================================================
-- znty-rrs 定时任务配置 - 演示数据
-- MySQL version: 8.0.33
-- 说明：预置已知任务配置；应用启动时也会按代码实现自动补全缺失任务
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
(1, 'auto_out_expired', '到期出池',
 '按扩展参数 poolIds 扫描指定投资池，将池内已到期证券自动调出',
 '0 0 2 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(2, 'company_new_bond_auto_in', '主体下新债自动入池',
 '扫描指定池内已在池主体，将其旗下未到期且尚未写入目标池的债券自动入池（主场景：债券禁止库）',
 '0 0 3 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW());
