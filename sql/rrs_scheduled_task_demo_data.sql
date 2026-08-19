-- ============================================================
-- znty-rrs 定时任务配置 - 演示数据
-- MySQL version: 8.0.33
-- 说明：预置已知任务配置；task_code 须与 RrsScheduledTask 实现类一致
-- 执行顺序只约束 cron（见 requirements/29-scheduled-task.md 第 4.1 / 4.2 节），列表 id 不要求与执行顺序一致；
-- 新增任务不得只改代码，须按依赖调整 cron 并更新需求文档。
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
-- cron 执行顺序：先清到期 → 先改主体（先出后入）→ 再同步旗下债（先入后出）
-- 02 到期债股出 → 03 CRMW到期出 → 04 外评主体出 → 05 外评主体入 → 06 同池债入 → 07 Job债入 → 08 Job债出
(1, 'security_expired_auto_out', '到期证券自动出池',
 '扫描所列池中已生效的债、股：到期日早于昨天（T-2）则自动调出；调出限制池阻断；仅软删成功才计数；不含主体/基金/CRMW（CRMW 走独立任务）',
 '0 0 2 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(2, 'crmw_expired_auto_out', 'CRMW到期自动出池',
 '扫描 CRMW 池已生效组合，凭证到期日早于昨天（T-2）则自动调出；调出限制池阻断；仅软删成功才计数',
 '0 0 3 * * ?', 0, '{"poolIds":[18]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(3, 'company_outer_rating_not_aa_minus_auto_out', '外评非AA-及以下主体自动出池',
 '有效外评不在上述名单且已在目标池 → 出主体并顺带出同池债；已在 limitPoolIds（默认禁投池）则不出；调出限制池阻断',
 '0 0 4 * * ?', 0, '{"poolIds":[16],"limitPoolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(4, 'company_outer_rating_aa_minus_auto_in', '外评AA-及以下主体自动入池',
 'Wind 有效外评（近12个月取档最高）落在 AA-/A/BBB…（不含 AA/AA+/AAA）且未在目标池 → 自动入主体；调入限制池阻断',
 '0 0 5 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(5, 'company_same_pool_bond_auto_in', '主体下债券自动入库',
 '主体已在 poolIds 同一池 → 旗下债未到期（含当天）且未在本池则自动入；尊重 market_codes；不排除临时代码/ABS；调入限制池阻断。跨池请用「在池主体旗下债券自动入池」',
 '0 0 6 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(6, 'company_inpool_bond_auto_in', '在池主体旗下债券自动入池',
 '主体已在池 → 旗下债未到期、未在目标池则自动入；排除临时代码已更新、ABS、CRMW；不看限制池。同池 poolIds，跨池 mappings',
 '0 0 7 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(7, 'company_not_in_pool_bond_auto_out', '主体不在池债券自动出池',
 '债已在债券池、主体不在对应主体池则出债；排除 ABS/CRMW（ABS 走禁投独立链路）；不看限制池。默认关闭。同池 poolIds，跨池 mappings',
 '0 0 8 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(8, 'bond_grade_inconformity_alert', '不符合主体债入库规则提醒',
 '扫描已在信用债 1～5 级且按当前特殊债规则不再允许待在该档的债券（不含境外债），写入待办供人工下调或出库；摘要区分本轮命中/本轮失效/仍待处理；不自动改池。对齐老系统 InconformityMaingrade2Job',
 '0 0 9 * * ?', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW());
