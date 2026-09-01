-- ============================================================
-- znty-rrs 定时任务配置 - 演示数据
-- MySQL version: 8.0.33
-- 说明：预置已知任务配置；task_code 须与 RrsScheduledTask 实现类一致
-- Demo 行与 id 按业务确认顺序排列；全部任务默认关闭调度；
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
-- 23:00 到期债股出 → 23:05 CRMW到期出 → 23:10 外评主体出 → 23:15 外评主体入 → 23:20 同池债入
(1, 'security_expired_auto_out', '到期证券自动出池',
 '1. 每天 23:00 执行，默认关闭调度。\n2. 扫描 poolIds 指定目标池；默认 [15]，即 15（债券禁止库）。\n3. 已生效债券、股票的到期日早于昨天（T-2）时，自动调出。\n4. 主体、基金、CRMW 不处理；CRMW 请使用独立到期出池任务。\n5. 证券命中目标池的调出限制池时，跳过该条记录。\n6. 仅软删除成功才写日志并计入影响条数。',
 '0 0 23 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(2, 'crmw_expired_auto_out', 'CRMW到期自动出池',
 '1. 每天 23:05 执行，默认关闭调度。\n2. 扫描 poolIds 指定 CRMW 目标池；默认 [18]，即 18（CRMW库）。\n3. 已生效 CRMW 组合的凭证到期日早于昨天（T-2）时，自动调出。\n4. 凭证命中目标池的调出限制池时，跳过该条记录。\n5. 仅软删除成功才写日志并计入影响条数。',
 '0 5 23 * * ?', 0, '{"poolIds":[18]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(3, 'company_outer_rating_not_aa_minus_auto_out', '外评非AA-及以下主体自动出池',
 '1. 每天 23:10 执行，默认关闭调度。\n2. 扫描 poolIds 指定目标池中已在池的主体；默认 [16]，即 16（观察池）。\n3. 主体有效外评为 AA、AA+、AAA 等非 AA-及以下名单时，自动调出主体。\n4. 主体成功出池后，顺带调出该主体在同一目标池内的旗下债券。\n5. 主体已在 limitPoolIds 拦截池时不出；默认 [15]，即 15（债券禁止库）。\n6. 主体或旗下债命中目标池的调出限制池时，跳过该条记录。',
 '0 10 23 * * ?', 0, '{"poolIds":[16],"limitPoolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(4, 'company_outer_rating_aa_minus_auto_in', '外评AA-及以下主体自动入池',
 '1. 每天 23:15 执行，默认关闭调度。\n2. 扫描 poolIds 指定目标池；默认 [15]，即 15（债券禁止库）。\n3. 有效外评属于 AA-及以下名单、且尚未在目标池的主体自动入池。\n4. 有效外评按近 12 个月取评级最高、更早评级取日期最新的规则确定。\n5. 主体命中目标池的调入限制池时，跳过该条记录。\n6. 直接生效，不走审批。',
 '0 15 23 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(5, 'company_same_pool_bond_auto_in', '主体下债券自动入库',
 '1. 每天 23:20 执行，默认关闭调度。\n2. 扫描 poolIds 指定同一目标池；默认 [15]，即 15（债券禁止库）。\n3. 主体已在该池时，旗下未到期（含当天）且未在同一池的债券自动入池。\n4. 按目标池 market_codes 校验债券市场；未配置时不限制。\n5. 债券命中目标池的调入限制池时，跳过该条记录。\n6. 不排除已更新临时代码和 ABS；跨池场景请使用“在池主体旗下债券自动入池”任务。',
 '0 20 23 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(6, 'company_inpool_bond_auto_in', '在池主体旗下债券自动入池',
 '1. 每 10 分钟执行一次，默认关闭调度。\n2. 同池使用 poolIds；默认 [15]，即 15（债券禁止库）。\n3. 跨池使用 mappings，分别配置主体所在池与债券目标池。\n4. 主体已在主体池时，旗下未到期且未在债券目标池的债券自动入池。\n5. 排除已更新临时代码、ABS、CRMW；不检查限制池和 market_codes。',
 '0 */10 * * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(7, 'company_not_in_pool_bond_auto_out', '主体不在池债券自动出池(默认关闭)',
 '1. 每天 00:00 执行，默认关闭调度，建议按需手动执行。\n2. 同池使用 poolIds；默认 [15]，即 15（债券禁止库）。\n3. 跨池使用 mappings，分别配置债券当前所在池与主体应在池。\n4. 债券已在债券池、发行主体不在对应主体池时，自动调出债券。\n5. 排除 ABS、CRMW；不检查调入或调出限制池。',
 '0 0 0 * * ?', 0, '{"poolIds":[15]}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(8, 'bond_grade_inconformity_alert', '不符合主体债入库规则提醒',
 '1. 每天 01:00 执行，默认关闭调度，无需扩展参数。\n2. 扫描已在信用债 1～5 级且不符合当前主体债入库规则的债券，不含境外债。\n3. 写入待办供人工下调或出库，不自动修改池状态。\n4. 执行摘要区分本轮命中、本轮失效、仍待处理。\n5. 对齐老系统 InconformityMaingrade2Job。',
 '0 0 1 * * ?', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(9, 'hs_pool_full_excel_export', '恒生池全量数据导出（不含已到期）',
 '1. 每天 01:10 执行，默认关闭调度。\n2. poolIds 可限制导出叶子池，未填写时导出全部叶子池。\n3. 导出当前已生效的非主体证券和 CRMW；普通证券排除已到期数据，CRMW 不校验到期日。\n4. 恒生池名称为空时使用投资池完整名称，竖线可拆分多个 Sheet，同名 Sheet 合并并记录警告。\n5. 固定导出证券名称、证券代码、操作类型、市场名称、备注；全量操作类型和备注为空。\n6. 市场输出中文名称并按多个市场代码字段拆行，该口径后续再确认。\n7. outputDir 下按 yyyyMMdd 建日期目录并生成 bak 备份；FTP 暂未接入。',
 '0 10 1 * * ?', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(10, 'hs_pool_full_including_expired_excel_export', '恒生池全量数据导出（含已到期）',
 '1. 每天 01:20 执行，默认关闭调度。\n2. poolIds 可限制导出叶子池，未填写时导出全部叶子池。\n3. 导出当前已生效的非主体证券和 CRMW；普通证券包含已到期数据，CRMW 不校验到期日。\n4. 恒生池名称为空时使用投资池完整名称，竖线可拆分多个 Sheet，同名 Sheet 合并并记录警告。\n5. 固定导出证券名称、证券代码、操作类型、市场名称、备注；全量操作类型和备注为空。\n6. 市场输出中文名称并按多个市场代码字段拆行，该口径后续再确认。\n7. outputDir 下按 yyyyMMdd 建日期目录并生成 bak 备份；FTP 暂未接入。',
 '0 20 1 * * ?', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
(11, 'hs_pool_increment_excel_export', '恒生池增量数据导出',
 '1. 每 6 分钟执行一次，默认关闭调度；交易日列表入口已预留，当前列表为空，因此交易日和非交易日均正常执行。\n2. 首次以 initialStartTime 为下界，后续以上次成功执行开始时间为下界，本次开始时间为上界。\n3. 老系统通过 exportflag 控制增量；新系统使用任务成功时间水位线，不更新业务调库日志。\n4. 导出窗口内审批通过的调入和调出事件，不要求证券当前仍在池。\n5. 调出记录操作类型写删除，包含普通非主体证券和 CRMW。\n6. 市场输出中文名称并按多个市场代码字段拆行，该口径后续再确认。\n7. poolIds 和 exportEmptyPool 与全量任务保持一致。\n8. outputDir 下按 yyyyMMdd 建日期目录并生成 bak 备份；FTP 暂未接入。',
 '0 */6 * * * ?', 0, '{"initialStartTime":"2026-01-01 00:00:00"}', NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW()),
-- 无池状态依赖：默认每 10 分钟；空壳待接入公司逻辑
(12, 'wind_code_sync', 'Wind代码变更同步',
 '1. 每 10 分钟执行一次，默认关闭调度，无需扩展参数。\n2. 当前为空壳任务，仅用于验证页面立即执行和定时调度挂载。\n3. 后续接入后扫描 Wind 代码变更，将临时代码同步为正式代码。\n4. 不读写池状态，与自动调库任务无编排依赖，可并行执行。',
 '0 */10 * * * ?', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 0, NOW(), NOW());
