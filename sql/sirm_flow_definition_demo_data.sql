-- ============================================================================
-- 工作流平台 — 20 条流程测试数据初始化脚本
-- 目标库：znty_sirm
-- 兼容：  MySQL 8.4
-- 说明：
--   流程类型 & 规则：
--     标准升库/降库（债券/基金）：
--       start → 研究员A发起 → 研究员B复核 → [驳回→研究员A修改→重新提交→研究员B复核]
--              → 研究总监审批 → [不通过→结束] → o32自动审批 → end
--     快速入/出库（债券/基金）：start → 研究员A发起 → end
--     批量入/出库（债券/基金）：start → 研究员A发起 → end
--     白名单入/出库（债券/基金）：start → 研究员A发起 → end
--     特殊策略入/出库（债券/基金）：同标准流程
--
--   角色说明（使用 wf_role_dict 中已有角色）：
--     researcher-a  → 对应 fund-manager（研究员A，发起人）
--     researcher-b  → 对应 risk-officer（研究员B，复核人）
--     invest-director → 投资总监（研究总监）
--     o32-auto        → admin（o32自动审批）
-- ============================================================================

USE znty_sirm;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 清空所有业务表（TRUNCATE：重置自增 ID + 释放索引空间，比 DELETE 快）
-- 按依赖倒序：先叶子后主干，虽有关闭 FK 校验兜底但仍保持良好习惯
-- ============================================================================
TRUNCATE TABLE `wf_edge_cond_rule`;
TRUNCATE TABLE `wf_edge_cond_rule_evt`;
TRUNCATE TABLE `wf_flow_edge`;
TRUNCATE TABLE `wf_flow_edge_evt`;
TRUNCATE TABLE `wf_node_condition_config`;
TRUNCATE TABLE `wf_node_condition_config_evt`;
TRUNCATE TABLE `wf_node_notify_config`;
TRUNCATE TABLE `wf_node_notify_config_evt`;
TRUNCATE TABLE `wf_node_auto_config`;
TRUNCATE TABLE `wf_node_auto_config_evt`;
TRUNCATE TABLE `wf_node_approval_config`;
TRUNCATE TABLE `wf_node_approval_config_evt`;
TRUNCATE TABLE `wf_flow_node`;
TRUNCATE TABLE `wf_flow_node_evt`;
TRUNCATE TABLE `wf_flow_version`;
TRUNCATE TABLE `wf_flow_version_evt`;
TRUNCATE TABLE `wf_flow_definition`;
TRUNCATE TABLE `wf_flow_definition_evt`;
TRUNCATE TABLE `wf_role_dict`;
TRUNCATE TABLE `wf_role_dict_evt`;

-- ============================================================================
-- 注意：本脚本使用 INSERT IGNORE / 不 TRUNCATE 已有数据
-- 若需全量重置，请先手动 TRUNCATE 相关表后再执行
-- 本脚本 flow_definition.id 从 101 开始，避免与演示数据（1-3）冲突
-- ============================================================================

-- ============================================================================
-- 补充角色字典（研究员A / 研究员B，若不存在则插入）
-- ============================================================================
INSERT IGNORE INTO `wf_role_dict` (`id`, `role_code`, `role_name`, `sort_order`, `is_active`, `crte_time`, `updt_time`)
VALUES (7, 'researcher-a', '研究员A（发起人）', 7, 1, NOW(), NOW()),
       (8, 'researcher-b', '研究员B（复核人）', 8, 1, NOW(), NOW()),
       (9, 'research-director', '研究总监', 9, 1, NOW(), NOW()),
       (10, 'o32-system', 'O32系统自动审批', 10, 1, NOW(), NOW());


-- ============================================================================
-- 1. 流程定义主表（20 条）
--    id 101-120
--    bond: category='bond'  fund: category='fund'
-- ============================================================================
INSERT INTO `wf_flow_definition`
(`id`, `name`, `flow_key`, `category`, `description`, `remark`, `status`, `created_by`, `updated_by`, `is_deleted`,
 `crte_time`, `updt_time`)
VALUES
-- 债券标准流程（升/降库）
(101, '债券标准升库流程', 'bond:standard-upgrade', 'bond',
 '债券标准升库审批：研究员A发起→研究员B复核→研究总监审批→O32自动审批', '', 'active', 1001, 1001, 0,
 '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102, '债券标准降库流程', 'bond:standard-downgrade', 'bond',
 '债券标准降库审批：研究员A发起→研究员B复核→研究总监审批→O32自动审批', '', 'active', 1001, 1001, 0,
 '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- 基金标准流程（升/降库）
(103, '基金标准升库流程', 'fund:standard-upgrade', 'fund',
 '基金标准升库审批：研究员A发起→研究员B复核→研究总监审批→O32自动审批', '', 'active', 1001, 1001, 0,
 '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104, '基金标准降库流程', 'fund:standard-downgrade', 'fund',
 '基金标准降库审批：研究员A发起→研究员B复核→研究总监审批→O32自动审批', '', 'active', 1001, 1001, 0,
 '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- 债券快速/白名单/批量/特殊策略 入库
(105, '债券快速入库流程', 'bond:fast-inbound', 'bond', '债券快速入库：研究员A发起后直接入库，无需审批', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(106, '债券白名单入库流程', 'bond:whitelist-inbound', 'bond', '债券白名单入库：研究员A发起后直接入库', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(107, '债券批量入库流程', 'bond:batch-inbound', 'bond', '债券批量入库：研究员A发起后批量直接入库', '', 'active', 1001,
 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108, '债券特殊策略入库流程', 'bond:special-inbound', 'bond', '债券特殊策略入库：同标准升库流程，适用于特殊投资策略标的',
 '', 'active', 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- 基金快速/白名单/批量/特殊策略 入库
(109, '基金快速入库流程', 'fund:fast-inbound', 'fund', '基金快速入库：研究员A发起后直接入库，无需审批', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110, '基金白名单入库流程', 'fund:whitelist-inbound', 'fund', '基金白名单入库：研究员A发起后直接入库', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(111, '基金批量入库流程', 'fund:batch-inbound', 'fund', '基金批量入库：研究员A发起后批量直接入库', '', 'active', 1001,
 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112, '基金特殊策略入库流程', 'fund:special-inbound', 'fund', '基金特殊策略入库：同标准升库流程，适用于特殊投资策略标的',
 '', 'active', 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- 债券快速/白名单/批量/特殊策略 出库
(113, '债券快速出库流程', 'bond:fast-outbound', 'bond', '债券快速出库：研究员A发起后直接出库，无需审批', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114, '债券白名单出库流程', 'bond:whitelist-outbound', 'bond', '债券白名单出库：研究员A发起后直接出库', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(115, '债券批量出库流程', 'bond:batch-outbound', 'bond', '债券批量出库：研究员A发起后批量直接出库', '', 'active', 1001,
 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116, '债券特殊策略出库流程', 'bond:special-outbound', 'bond', '债券特殊策略出库：同标准降库流程，适用于特殊投资策略标的',
 '', 'active', 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- 基金快速/白名单/批量/特殊策略 出库
(117, '基金快速出库流程', 'fund:fast-outbound', 'fund', '基金快速出库：研究员A发起后直接出库，无需审批', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(118, '基金白名单出库流程', 'fund:whitelist-outbound', 'fund', '基金白名单出库：研究员A发起后直接出库', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(119, '基金批量出库流程', 'fund:batch-outbound', 'fund', '基金批量出库：研究员A发起后批量直接出库', '', 'active', 1001,
 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120, '基金特殊策略出库流程', 'fund:special-outbound', 'fund', '基金特殊策略出库：同标准降库流程，适用于特殊投资策略标的',
 '', 'active', 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00');


-- ============================================================================
-- 2. 流程版本（每条流程各 1 个版本 v1）
--    version id: 101-120，与 flow_id 一一对应
-- ============================================================================

-- -------------------------------------------------------
-- 标准流程画布 JSON（公共定义，8节点7连线含驳回回路）
-- 流程：start→研究员A发起(approval)→研究员B复核(approval/condition)
--       →[驳回→研究员A修改(approval)]→研究总监审批(approval)
--       →[不通过→end1] → O32自动审批(auto) → end2
-- 为避免超长，画布快照 JSON 以简化形式内联存储
-- -------------------------------------------------------

-- 版本说明：
--   标准流程（flow 101-104, 108, 112, 116, 120）：7节点画布
--   简单直通流程（flow 105-107, 109-111, 113-115, 117-119）：3节点画布

INSERT INTO `wf_flow_version`
(`id`, `flow_id`, `flow_key`, `ver_num`, `status`, `publish_note`,
 `canvas_nodes`, `canvas_edges`,
 `canvas_pan_x`, `canvas_pan_y`, `canvas_zoom`,
 `published_by`, `published_time`, `created_by`, `crte_time`, `updt_time`)
VALUES

-- ===== 标准升/降库流程 v1（flow 101~104） =====
-- 节点：n1=开始 n2=研究员A发起 n3=研究员B复核 n4=研究员A修改 n5=研究总监审批 n6=O32自动审批 n7=结束
-- 连线：n1→n2 n2→n3(提交) n3→n4(驳回) n4→n3(重新提交) n3→n5(复核通过) n5→n7(不通过) n5→n6(通过) n6→n7

(101, 101, 'bond:standard-upgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e8","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(102, 102, 'bond:standard-downgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e8","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(103, 103, 'fund:standard-upgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e8","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(104, 104, 'fund:standard-downgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e8","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券快速/白名单/批量入库（直通，flow 105-107） =====
-- 节点：n1=开始 n2=研究员A发起 n3=结束

(105, 105, 'bond:fast-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(106, 106, 'bond:whitelist-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(107, 107, 'bond:batch-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"批量入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券特殊策略入库（同标准流程，flow 108） =====
(108, 108, 'bond:special-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e8","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金快速/白名单/批量入库（直通，flow 109-111） =====
(109, 109, 'fund:fast-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(110, 110, 'fund:whitelist-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(111, 111, 'fund:batch-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"批量入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金特殊策略入库（同标准，flow 112） =====
(112, 112, 'fund:special-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e8","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券快速/白名单/批量出库（直通，flow 113-115） =====
(113, 113, 'bond:fast-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(114, 114, 'bond:whitelist-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(115, 115, 'bond:batch-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"批量出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券特殊策略出库（同标准，flow 116） =====
(116, 116, 'bond:special-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e8","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金快速/白名单/批量出库（直通，flow 117-119） =====
(117, 117, 'fund:fast-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(118, 118, 'fund:whitelist-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(119, 119, 'fund:batch-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"批量出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金特殊策略出库（同标准，flow 120） =====
(120, 120, 'fund:special-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND"},{"id":"e8","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00');


-- ============================================================================
-- 3. 流程节点（wf_flow_node）
-- ============================================================================
-- 节点 ID 规则：
--   标准7节点流程：version_id*10 + 1~7
--   简单3节点流程：version_id*10 + 1~3
-- 使用固定的 id 值（10010~12007），避免与已有演示数据冲突
-- ============================================================================

INSERT INTO `wf_flow_node`
(`id`, `version_id`, `flow_id`, `node_id`, `node_type`, `label`, `shape`, `pos_x`, `pos_y`, `sub_label`, `sort_order`,
 `crte_time`, `updt_time`)
VALUES
-- ===== flow 101 (version 101) — 债券标准升库，7节点 =====
(10101, 101, 101, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10102, 101, 101, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10103, 101, 101, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10104, 101, 101, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10105, 101, 101, 'n5', 'approval', '研究总监审批', 'rect', 400, 410, 'research-director', 5, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10106, 101, 101, 'n6', 'auto', 'O32自动审批', 'rect', 400, 540, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10107, 101, 101, 'n7', 'end', '结束', 'circle', 400, 660, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 102 (version 102) — 债券标准降库，7节点 =====
(10201, 102, 102, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10202, 102, 102, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10203, 102, 102, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10204, 102, 102, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10205, 102, 102, 'n5', 'approval', '研究总监审批', 'rect', 400, 410, 'research-director', 5, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10206, 102, 102, 'n6', 'auto', 'O32自动审批', 'rect', 400, 540, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10207, 102, 102, 'n7', 'end', '结束', 'circle', 400, 660, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 103 (version 103) — 基金标准升库，7节点 =====
(10301, 103, 103, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10302, 103, 103, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10303, 103, 103, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10304, 103, 103, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10305, 103, 103, 'n5', 'approval', '研究总监审批', 'rect', 400, 410, 'research-director', 5, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10306, 103, 103, 'n6', 'auto', 'O32自动审批', 'rect', 400, 540, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10307, 103, 103, 'n7', 'end', '结束', 'circle', 400, 660, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 104 (version 104) — 基金标准降库，7节点 =====
(10401, 104, 104, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10402, 104, 104, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10403, 104, 104, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10404, 104, 104, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10405, 104, 104, 'n5', 'approval', '研究总监审批', 'rect', 400, 410, 'research-director', 5, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10406, 104, 104, 'n6', 'auto', 'O32自动审批', 'rect', 400, 540, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10407, 104, 104, 'n7', 'end', '结束', 'circle', 400, 660, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 105 (version 105) — 债券快速入库，3节点 =====
(10501, 105, 105, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10502, 105, 105, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10503, 105, 105, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 106 (version 106) — 债券白名单入库，3节点 =====
(10601, 106, 106, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10602, 106, 106, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10603, 106, 106, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 107 (version 107) — 债券批量入库，3节点 =====
(10701, 107, 107, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10702, 107, 107, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10703, 107, 107, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 108 (version 108) — 债券特殊策略入库，7节点 =====
(10801, 108, 108, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10802, 108, 108, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10803, 108, 108, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10804, 108, 108, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10805, 108, 108, 'n5', 'approval', '研究总监审批', 'rect', 400, 410, 'research-director', 5, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10806, 108, 108, 'n6', 'auto', 'O32自动审批', 'rect', 400, 540, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10807, 108, 108, 'n7', 'end', '结束', 'circle', 400, 660, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 109 (version 109) — 基金快速入库，3节点 =====
(10901, 109, 109, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10902, 109, 109, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10903, 109, 109, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 110 (version 110) — 基金白名单入库，3节点 =====
(11001, 110, 110, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11002, 110, 110, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11003, 110, 110, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 111 (version 111) — 基金批量入库，3节点 =====
(11101, 111, 111, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11102, 111, 111, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11103, 111, 111, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 112 (version 112) — 基金特殊策略入库，7节点 =====
(11201, 112, 112, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11202, 112, 112, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11203, 112, 112, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11204, 112, 112, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11205, 112, 112, 'n5', 'approval', '研究总监审批', 'rect', 400, 410, 'research-director', 5, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11206, 112, 112, 'n6', 'auto', 'O32自动审批', 'rect', 400, 540, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11207, 112, 112, 'n7', 'end', '结束', 'circle', 400, 660, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 113 (version 113) — 债券快速出库，3节点 =====
(11301, 113, 113, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11302, 113, 113, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11303, 113, 113, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 114 (version 114) — 债券白名单出库，3节点 =====
(11401, 114, 114, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11402, 114, 114, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11403, 114, 114, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 115 (version 115) — 债券批量出库，3节点 =====
(11501, 115, 115, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11502, 115, 115, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11503, 115, 115, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 116 (version 116) — 债券特殊策略出库，7节点 =====
(11601, 116, 116, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11602, 116, 116, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11603, 116, 116, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11604, 116, 116, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11605, 116, 116, 'n5', 'approval', '研究总监审批', 'rect', 400, 410, 'research-director', 5, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11606, 116, 116, 'n6', 'auto', 'O32自动审批', 'rect', 400, 540, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11607, 116, 116, 'n7', 'end', '结束', 'circle', 400, 660, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 117 (version 117) — 基金快速出库，3节点 =====
(11701, 117, 117, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11702, 117, 117, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11703, 117, 117, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 118 (version 118) — 基金白名单出库，3节点 =====
(11801, 118, 118, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11802, 118, 118, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11803, 118, 118, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 119 (version 119) — 基金批量出库，3节点 =====
(11901, 119, 119, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11902, 119, 119, 'n2', 'approval', '研究员A发起', 'rect', 400, 160, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11903, 119, 119, 'n3', 'end', '结束', 'circle', 400, 280, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== flow 120 (version 120) — 基金特殊策略出库，7节点 =====
(12001, 120, 120, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12002, 120, 120, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(12003, 120, 120, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(12004, 120, 120, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(12005, 120, 120, 'n5', 'approval', '研究总监审批', 'rect', 400, 410, 'research-director', 5, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(12006, 120, 120, 'n6', 'auto', 'O32自动审批', 'rect', 400, 540, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12007, 120, 120, 'n7', 'end', '结束', 'circle', 400, 660, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00');


-- ============================================================================
-- 4. 审批节点配置（wf_node_approval_config）
--    标准7节点流程：n2(研究员A发起) n3(研究员B复核) n4(研究员A修改) n5(研究总监审批)
--    简单3节点流程：n2(研究员A发起)
-- ============================================================================

INSERT INTO `wf_node_approval_config`
(`id`, `node_id`, `approval_strategy`, `approval_persons`, `approval_remark`, `crte_time`, `updt_time`)
VALUES
-- flow 101 研究员A发起(10102) 研究员B复核(10103) 研究员A修改(10104) 研究总监审批(10105)
(10102, 10102, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券标准升库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10103, 10103, 'preempt', '[
  "researcher-b"
]', '研究员B对债券标准升库进行复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10104, 10104, 'preempt', '[
  "researcher-a"
]', '研究员A根据驳回意见修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10105, 10105, 'preempt', '[
  "research-director"
]', '研究总监审批，不通过则流程直接结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 102
(10202, 10202, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券标准降库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10203, 10203, 'preempt', '[
  "researcher-b"
]', '研究员B对债券标准降库进行复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10204, 10204, 'preempt', '[
  "researcher-a"
]', '研究员A根据驳回意见修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10205, 10205, 'preempt', '[
  "research-director"
]', '研究总监审批，不通过则流程直接结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 103
(10302, 10302, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金标准升库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10303, 10303, 'preempt', '[
  "researcher-b"
]', '研究员B对基金标准升库进行复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10304, 10304, 'preempt', '[
  "researcher-a"
]', '研究员A根据驳回意见修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10305, 10305, 'preempt', '[
  "research-director"
]', '研究总监审批，不通过则流程直接结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 104
(10402, 10402, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金标准降库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10403, 10403, 'preempt', '[
  "researcher-b"
]', '研究员B对基金标准降库进行复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10404, 10404, 'preempt', '[
  "researcher-a"
]', '研究员A根据驳回意见修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10405, 10405, 'preempt', '[
  "research-director"
]', '研究总监审批，不通过则流程直接结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 105 债券快速入库
(10502, 10502, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券快速入库，直接入库无需审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 106 债券白名单入库
(10602, 10602, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券白名单入库，直接入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 107 债券批量入库
(10702, 10702, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券批量入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 108 债券特殊策略入库
(10802, 10802, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券特殊策略入库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10803, 10803, 'preempt', '[
  "researcher-b"
]', '研究员B复核债券特殊策略入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10804, 10804, 'preempt', '[
  "researcher-a"
]', '研究员A修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10805, 10805, 'preempt', '[
  "research-director"
]', '研究总监审批，不通过则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 109 基金快速入库
(10902, 10902, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金快速入库，直接入库无需审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 110 基金白名单入库
(11002, 11002, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金白名单入库，直接入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 111 基金批量入库
(11102, 11102, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金批量入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 112 基金特殊策略入库
(11202, 11202, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金特殊策略入库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11203, 11203, 'preempt', '[
  "researcher-b"
]', '研究员B复核基金特殊策略入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11204, 11204, 'preempt', '[
  "researcher-a"
]', '研究员A修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11205, 11205, 'preempt', '[
  "research-director"
]', '研究总监审批，不通过则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 113 债券快速出库
(11302, 11302, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券快速出库，直接出库无需审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 114 债券白名单出库
(11402, 11402, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券白名单出库，直接出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 115 债券批量出库
(11502, 11502, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券批量出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 116 债券特殊策略出库
(11602, 11602, 'preempt', '[
  "researcher-a"
]', '研究员A发起债券特殊策略出库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11603, 11603, 'preempt', '[
  "researcher-b"
]', '研究员B复核债券特殊策略出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11604, 11604, 'preempt', '[
  "researcher-a"
]', '研究员A修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11605, 11605, 'preempt', '[
  "research-director"
]', '研究总监审批，不通过则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 117 基金快速出库
(11702, 11702, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金快速出库，直接出库无需审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 118 基金白名单出库
(11802, 11802, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金白名单出库，直接出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 119 基金批量出库
(11902, 11902, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金批量出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 120 基金特殊策略出库
(12002, 12002, 'preempt', '[
  "researcher-a"
]', '研究员A发起基金特殊策略出库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12003, 12003, 'preempt', '[
  "researcher-b"
]', '研究员B复核基金特殊策略出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12004, 12004, 'preempt', '[
  "researcher-a"
]', '研究员A修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12005, 12005, 'preempt', '[
  "research-director"
]', '研究总监审批，不通过则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00');


-- ============================================================================
-- 5. 自动执行节点配置（wf_node_auto_config）
--    标准7节点流程：n6(O32自动审批) → task_code=riskCheck（模拟O32系统自动审批动作）
-- ============================================================================

INSERT INTO `wf_node_auto_config`
(`id`, `node_id`, `task_seq`, `task_code`, `auto_remark`, `crte_time`, `updt_time`)
VALUES
-- 标准流程 O32自动审批节点（flow 101-104, 108, 112, 116, 120）
(10106, 10106, 1, 'riskCheck', 'O32系统自动执行风控检查，通过后流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10107, 10106, 2, 'archiveRecord', 'O32系统自动归档入库记录', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10206, 10206, 1, 'riskCheck', 'O32系统自动执行风控检查，通过后流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10207, 10206, 2, 'archiveRecord', 'O32系统自动归档降库记录', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10306, 10306, 1, 'riskCheck', 'O32系统自动执行风控检查，通过后流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10307, 10306, 2, 'archiveRecord', 'O32系统自动归档入库记录', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10406, 10406, 1, 'riskCheck', 'O32系统自动执行风控检查，通过后流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10407, 10406, 2, 'archiveRecord', 'O32系统自动归档降库记录', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10806, 10806, 1, 'riskCheck', 'O32系统自动执行风控检查，通过后流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10807, 10806, 2, 'archiveRecord', 'O32系统自动归档特殊策略入库记录', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11206, 11206, 1, 'riskCheck', 'O32系统自动执行风控检查，通过后流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11207, 11206, 2, 'archiveRecord', 'O32系统自动归档特殊策略入库记录', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11606, 11606, 1, 'riskCheck', 'O32系统自动执行风控检查，通过后流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11607, 11606, 2, 'archiveRecord', 'O32系统自动归档特殊策略出库记录', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12006, 12006, 1, 'riskCheck', 'O32系统自动执行风控检查，通过后流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12007, 12006, 2, 'archiveRecord', 'O32系统自动归档特殊策略出库记录', '2026-05-20 09:00:00', '2026-05-20 09:00:00');


-- ============================================================================
-- 6. 流程连线（wf_flow_edge）
--    标准7节点流程（8条边）：
--      e1: n1→n2  e2: n2→n3(提交)  e3: n3→n4(驳回)  e4: n4→n3(重新提交)
--      e5: n3→n5(复核通过)  e6: n5→n7(不通过)  e7: n5→n6(通过)  e8: n6→n7
--    简单3节点流程（2条边）：
--      e1: n1→n2  e2: n2→n3
-- ============================================================================

INSERT INTO `wf_flow_edge`
(`id`, `version_id`, `flow_id`, `edge_id`, `from_node_id`, `to_node_id`, `label`, `cond_logic`, `remark`, `crte_time`,
 `updt_time`)
VALUES
-- ===== flow 101 (8条边) =====
(10101, 101, 101, 'e1', 10101, 10102, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10102, 101, 101, 'e2', 10102, 10103, '提交', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10103, 101, 101, 'e3', 10103, 10104, '驳回', 'AND', '研究员B复核不通过时驳回给研究员A修改', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10104, 101, 101, 'e4', 10104, 10103, '重新提交', 'AND', '研究员A修改完成后重新提交给研究员B', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10105, 101, 101, 'e5', 10103, 10105, '复核通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10106, 101, 101, 'e6', 10105, 10107, '不通过', 'AND', '研究总监审批不通过，流程直接结束', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10107, 101, 101, 'e7', 10105, 10106, '通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10108, 101, 101, 'e8', 10106, 10107, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 102 (8条边) =====
(10201, 102, 102, 'e1', 10201, 10202, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10202, 102, 102, 'e2', 10202, 10203, '提交', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10203, 102, 102, 'e3', 10203, 10204, '驳回', 'AND', '研究员B复核不通过时驳回给研究员A修改', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10204, 102, 102, 'e4', 10204, 10203, '重新提交', 'AND', '研究员A修改完成后重新提交给研究员B', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10205, 102, 102, 'e5', 10203, 10205, '复核通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10206, 102, 102, 'e6', 10205, 10207, '不通过', 'AND', '研究总监审批不通过，流程直接结束', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10207, 102, 102, 'e7', 10205, 10206, '通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10208, 102, 102, 'e8', 10206, 10207, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 103 (8条边) =====
(10301, 103, 103, 'e1', 10301, 10302, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10302, 103, 103, 'e2', 10302, 10303, '提交', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10303, 103, 103, 'e3', 10303, 10304, '驳回', 'AND', '研究员B复核不通过时驳回给研究员A修改', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10304, 103, 103, 'e4', 10304, 10303, '重新提交', 'AND', '研究员A修改完成后重新提交给研究员B', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10305, 103, 103, 'e5', 10303, 10305, '复核通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10306, 103, 103, 'e6', 10305, 10307, '不通过', 'AND', '研究总监审批不通过，流程直接结束', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10307, 103, 103, 'e7', 10305, 10306, '通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10308, 103, 103, 'e8', 10306, 10307, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 104 (8条边) =====
(10401, 104, 104, 'e1', 10401, 10402, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10402, 104, 104, 'e2', 10402, 10403, '提交', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10403, 104, 104, 'e3', 10403, 10404, '驳回', 'AND', '研究员B复核不通过时驳回给研究员A修改', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10404, 104, 104, 'e4', 10404, 10403, '重新提交', 'AND', '研究员A修改完成后重新提交给研究员B', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10405, 104, 104, 'e5', 10403, 10405, '复核通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10406, 104, 104, 'e6', 10405, 10407, '不通过', 'AND', '研究总监审批不通过，流程直接结束', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10407, 104, 104, 'e7', 10405, 10406, '通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10408, 104, 104, 'e8', 10406, 10407, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 105 债券快速入库（2条边） =====
(10501, 105, 105, 'e1', 10501, 10502, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10502, 105, 105, 'e2', 10502, 10503, '直接入库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 106 债券白名单入库（2条边） =====
(10601, 106, 106, 'e1', 10601, 10602, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10602, 106, 106, 'e2', 10602, 10603, '直接入库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 107 债券批量入库（2条边） =====
(10701, 107, 107, 'e1', 10701, 10702, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10702, 107, 107, 'e2', 10702, 10703, '批量入库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 108 债券特殊策略入库（8条边） =====
(10801, 108, 108, 'e1', 10801, 10802, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10802, 108, 108, 'e2', 10802, 10803, '提交', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10803, 108, 108, 'e3', 10803, 10804, '驳回', 'AND', '研究员B复核不通过时驳回给研究员A修改', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10804, 108, 108, 'e4', 10804, 10803, '重新提交', 'AND', '研究员A修改完成后重新提交给研究员B', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10805, 108, 108, 'e5', 10803, 10805, '复核通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10806, 108, 108, 'e6', 10805, 10807, '不通过', 'AND', '研究总监审批不通过，流程直接结束', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10807, 108, 108, 'e7', 10805, 10806, '通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10808, 108, 108, 'e8', 10806, 10807, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 109 基金快速入库（2条边） =====
(10901, 109, 109, 'e1', 10901, 10902, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10902, 109, 109, 'e2', 10902, 10903, '直接入库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 110 基金白名单入库（2条边） =====
(11001, 110, 110, 'e1', 11001, 11002, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11002, 110, 110, 'e2', 11002, 11003, '直接入库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 111 基金批量入库（2条边） =====
(11101, 111, 111, 'e1', 11101, 11102, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11102, 111, 111, 'e2', 11102, 11103, '批量入库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 112 基金特殊策略入库（8条边） =====
(11201, 112, 112, 'e1', 11201, 11202, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11202, 112, 112, 'e2', 11202, 11203, '提交', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11203, 112, 112, 'e3', 11203, 11204, '驳回', 'AND', '研究员B复核不通过时驳回给研究员A修改', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11204, 112, 112, 'e4', 11204, 11203, '重新提交', 'AND', '研究员A修改完成后重新提交给研究员B', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11205, 112, 112, 'e5', 11203, 11205, '复核通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11206, 112, 112, 'e6', 11205, 11207, '不通过', 'AND', '研究总监审批不通过，流程直接结束', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11207, 112, 112, 'e7', 11205, 11206, '通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11208, 112, 112, 'e8', 11206, 11207, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 113 债券快速出库（2条边） =====
(11301, 113, 113, 'e1', 11301, 11302, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11302, 113, 113, 'e2', 11302, 11303, '直接出库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 114 债券白名单出库（2条边） =====
(11401, 114, 114, 'e1', 11401, 11402, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11402, 114, 114, 'e2', 11402, 11403, '直接出库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 115 债券批量出库（2条边） =====
(11501, 115, 115, 'e1', 11501, 11502, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11502, 115, 115, 'e2', 11502, 11503, '批量出库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 116 债券特殊策略出库（8条边） =====
(11601, 116, 116, 'e1', 11601, 11602, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11602, 116, 116, 'e2', 11602, 11603, '提交', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11603, 116, 116, 'e3', 11603, 11604, '驳回', 'AND', '研究员B复核不通过时驳回给研究员A修改', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11604, 116, 116, 'e4', 11604, 11603, '重新提交', 'AND', '研究员A修改完成后重新提交给研究员B', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11605, 116, 116, 'e5', 11603, 11605, '复核通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11606, 116, 116, 'e6', 11605, 11607, '不通过', 'AND', '研究总监审批不通过，流程直接结束', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(11607, 116, 116, 'e7', 11605, 11606, '通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11608, 116, 116, 'e8', 11606, 11607, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 117 基金快速出库（2条边） =====
(11701, 117, 117, 'e1', 11701, 11702, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11702, 117, 117, 'e2', 11702, 11703, '直接出库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 118 基金白名单出库（2条边） =====
(11801, 118, 118, 'e1', 11801, 11802, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11802, 118, 118, 'e2', 11802, 11803, '直接出库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 119 基金批量出库（2条边） =====
(11901, 119, 119, 'e1', 11901, 11902, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11902, 119, 119, 'e2', 11902, 11903, '批量出库', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- ===== flow 120 基金特殊策略出库（8条边） =====
(12001, 120, 120, 'e1', 12001, 12002, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12002, 120, 120, 'e2', 12002, 12003, '提交', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12003, 120, 120, 'e3', 12003, 12004, '驳回', 'AND', '研究员B复核不通过时驳回给研究员A修改', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(12004, 120, 120, 'e4', 12004, 12003, '重新提交', 'AND', '研究员A修改完成后重新提交给研究员B', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(12005, 120, 120, 'e5', 12003, 12005, '复核通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12006, 120, 120, 'e6', 12005, 12007, '不通过', 'AND', '研究总监审批不通过，流程直接结束', '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(12007, 120, 120, 'e7', 12005, 12006, '通过', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12008, 120, 120, 'e8', 12006, 12007, '', 'AND', '', '2026-05-20 09:00:00', '2026-05-20 09:00:00');


-- ============================================================================
-- 7. 连线条件规则（wf_edge_cond_rule）
--    标准流程的以下连线需要条件规则：
--      e3(驳回)：auditStatus=驳回
--      e5(复核通过)：auditStatus=通过
--      e6(不通过)：auditStatus=不通过
--      e7(通过)：auditStatus=通过
-- ============================================================================

INSERT INTO `wf_edge_cond_rule`
(`id`, `edge_id`, `seq`, `field_code`, `operator`, `field_val`, `crte_time`, `updt_time`)
VALUES
-- flow 101
(10103, 10103, 1, 'auditStatus', 'eq', '驳回', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10105, 10105, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10106, 10106, 1, 'auditStatus', 'eq', '不通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10107, 10107, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 102
(10203, 10203, 1, 'auditStatus', 'eq', '驳回', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10205, 10205, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10206, 10206, 1, 'auditStatus', 'eq', '不通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10207, 10207, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 103
(10303, 10303, 1, 'auditStatus', 'eq', '驳回', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10305, 10305, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10306, 10306, 1, 'auditStatus', 'eq', '不通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10307, 10307, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 104
(10403, 10403, 1, 'auditStatus', 'eq', '驳回', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10405, 10405, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10406, 10406, 1, 'auditStatus', 'eq', '不通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10407, 10407, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 108
(10803, 10803, 1, 'auditStatus', 'eq', '驳回', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10805, 10805, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10806, 10806, 1, 'auditStatus', 'eq', '不通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10807, 10807, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 112
(11203, 11203, 1, 'auditStatus', 'eq', '驳回', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11205, 11205, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11206, 11206, 1, 'auditStatus', 'eq', '不通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11207, 11207, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 116
(11603, 11603, 1, 'auditStatus', 'eq', '驳回', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11605, 11605, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11606, 11606, 1, 'auditStatus', 'eq', '不通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11607, 11607, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 120
(12003, 12003, 1, 'auditStatus', 'eq', '驳回', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12005, 12005, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12006, 12006, 1, 'auditStatus', 'eq', '不通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12007, 12007, 1, 'auditStatus', 'eq', '通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00');


SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================================
-- 数据概览
-- ============================================================================
--
--  流程类型          ID范围   节点数  连线数  条件规则  说明
--  ─────────────────────────────────────────────────────────────────────────
--  债券标准升库      101      7       8       4        标准流程，含驳回回路
--  债券标准降库      102      7       8       4        标准流程，含驳回回路
--  基金标准升库      103      7       8       4        标准流程，含驳回回路
--  基金标准降库      104      7       8       4        标准流程，含驳回回路
--  债券快速入库      105      3       2       0        直通，无审批
--  债券白名单入库    106      3       2       0        直通，无审批
--  债券批量入库      107      3       2       0        直通，无审批
--  债券特殊策略入库  108      7       8       4        同标准流程
--  基金快速入库      109      3       2       0        直通，无审批
--  基金白名单入库    110      3       2       0        直通，无审批
--  基金批量入库      111      3       2       0        直通，无审批
--  基金特殊策略入库  112      7       8       4        同标准流程
--  债券快速出库      113      3       2       0        直通，无审批
--  债券白名单出库    114      3       2       0        直通，无审批
--  债券批量出库      115      3       2       0        直通，无审批
--  债券特殊策略出库  116      7       8       4        同标准流程
--  基金快速出库      117      3       2       0        直通，无审批
--  基金白名单出库    118      3       2       0        直通，无审批
--  基金批量出库      119      3       2       0        直通，无审批
--  基金特殊策略出库  120      7       8       4        同标准流程
--  ─────────────────────────────────────────────────────────────────────────
--  合计 20 条流程 / 20 个版本 / 100 个节点 / 120 条连线 / 32 条条件规则
--
--  标准流程拓扑（升/降库 + 特殊策略共8条流程）：
--    开始 → 研究员A发起 → 研究员B复核 ─[驳回]→ 研究员A修改 ─[重新提交]→ 研究员B复核
--                          └─[复核通过]→ 研究总监审批 ─[不通过]→ 结束
--                                          └─[通过]→ O32自动审批 → 结束
--
--  直通流程拓扑（快速/白名单/批量共12条流程）：
--    开始 → 研究员A发起 → 结束
-- ============================================================================