-- ============================================================================
-- 工作流平台 — 20 条证券池调库流程测试数据初始化脚本
-- 目标库：znty_rrs
-- 兼容：  MySQL 8.0.28
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

USE znty_rrs;

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
TRUNCATE TABLE `wf_node_approval_handler`;
TRUNCATE TABLE `wf_node_approval_handler_evt`;
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
-- 注意：本脚本为全量重置脚本，会先清空流程模块相关表后重建测试数据
-- 本脚本 flow_definition.id 从 101 开始，便于测试时按固定 ID 定位数据
-- ============================================================================

-- ============================================================================
-- 角色字典测试数据（10 条）
-- ============================================================================
INSERT INTO `wf_role_dict` (`id`, `role_code`, `role_name`, `sort_order`, `is_active`, `crte_time`, `updt_time`)
VALUES (1, 'fund-manager', '基金经理', 1, 1, NOW(), NOW()),
       (2, 'credit-research', '信用研究组', 2, 1, NOW(), NOW()),
       (3, 'risk-officer', '风险管理部', 3, 1, NOW(), NOW()),
       (4, 'fixed-income', '固收部', 4, 1, NOW(), NOW()),
       (5, 'invest-director', '投资总监', 5, 1, NOW(), NOW()),
       (6, 'operations', '运营管理部', 6, 1, NOW(), NOW()),
       (7, 'researcher-a', '研究员A（发起人）', 7, 1, NOW(), NOW()),
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
(105, '债券快速入库流程', 'bond:fast-inbound', 'bond', '债券快速入库：研究员A发起→研究员B复核→O32自动审批后入库', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(106, '债券白名单入库流程', 'bond:whitelist-inbound', 'bond', '债券白名单入库：研究员A发起后直接入库', '', 'active',
 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(107, '债券批量入库流程', 'bond:batch-inbound', 'bond', '债券批量入库：研究员A发起后批量直接入库', '', 'active', 1001,
 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108, '债券特殊策略入库流程', 'bond:special-inbound', 'bond', '债券特殊策略入库：同标准升库流程，适用于特殊投资策略标的',
 '', 'active', 1001, 1001, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- 基金快速/白名单/批量/特殊策略 入库
(109, '基金快速入库流程', 'fund:fast-inbound', 'fund', '基金快速入库：研究员A发起→研究员B复核→O32自动审批后入库', '', 'active',
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
-- 标准流程画布 JSON（公共定义，7节点9连线含驳回回路）
-- 流程：start→研究员A发起(approval)→研究员B复核(approval/condition)
--       →[驳回→研究员A修改(approval)→驳回→结束 / 重新提交→研究员B复核]
--       →研究总监审批(approval) → [不通过→结束] → O32自动审批(auto) → 结束
-- 为避免超长，画布快照 JSON 以简化形式内联存储
-- -------------------------------------------------------

-- 版本说明：
--   标准流程（flow 101-104, 108, 112, 116, 120）：7节点画布
--   快速入库复核流程（flow 105, 109）：6节点画布
--   简单直通流程（flow 106-107, 110-111, 113-115, 117-119）：3节点画布

INSERT INTO `wf_flow_version`
(`id`, `flow_id`, `flow_key`, `ver_num`, `status`, `publish_note`,
 `canvas_nodes`, `canvas_edges`,
 `canvas_pan_x`, `canvas_pan_y`, `canvas_zoom`,
 `published_by`, `published_time`, `created_by`, `crte_time`, `updt_time`)
VALUES

-- ===== 标准升/降库流程 v1（flow 101~104） =====
-- 节点：n1=开始 n2=研究员A发起 n3=研究员B复核 n4=研究员A修改 n5=研究总监审批 n6=O32自动审批 n7=结束
-- 连线：n1→n2 n2→n3(提交) n3→n4(驳回) n4→n3(重新提交) n4→n7(驳回) n3→n5(复核通过) n5→n7(不通过) n5→n6(通过) n6→n7

(101, 101, 'bond:standard-upgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":4,"handlerName":"研究员4"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"all","approvalPersons":[{"handlerType":"user","handlerId":2,"handlerName":"研究员2"},{"handlerType":"user","handlerId":1001,"handlerName":"管理员"}],"approvalRemark":"终审节点，配置的全部处理人均需处理"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(102, 102, 'bond:standard-downgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":4,"handlerName":"固收部"},{"handlerType":"user","handlerId":6,"handlerName":"固收1"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":4,"handlerName":"固收部"},{"handlerType":"user","handlerId":2,"handlerName":"研究员2"}],"approvalRemark":"终审节点，按配置处理人审批"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(103, 103, 'fund:standard-upgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":7,"handlerName":"权益部"},{"handlerType":"user","handlerId":12,"handlerName":"权益3"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"all","approvalPersons":[{"handlerType":"role","handlerId":7,"handlerName":"权益部"},{"handlerType":"user","handlerId":1001,"handlerName":"管理员"}],"approvalRemark":"终审节点，配置的全部处理人均需处理"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(104, 104, 'fund:standard-downgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"量化部"},{"handlerType":"user","handlerId":13,"handlerName":"量化1"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"量化部"},{"handlerType":"user","handlerId":1001,"handlerName":"管理员"}],"approvalRemark":"终审节点，按配置处理人审批"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券快速入库（复核后 O32 自动审批，flow 105） =====
-- 节点：n1=开始 n2=研究员A发起 n3=研究员B复核 n4=研究员A修改 n5=O32自动审批 n6=结束

(105, 105, 'bond:fast-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":2,"handlerName":"研究员2"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"auto","label":"O32自动审批","x":400,"y":410,"shape":"rect"},{"id":"n6","type":"end","label":"结束","x":400,"y":530,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n4","to":"n6","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券白名单/批量入库（直通，flow 106-107） =====
-- 节点：n1=开始 n2=研究员A发起 n3=结束

(106, 106, 'bond:whitelist-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(107, 107, 'bond:batch-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"批量入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券特殊策略入库（同标准流程，flow 108） =====
(108, 108, 'bond:special-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":2,"handlerName":"研究员2"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"all","approvalPersons":[{"handlerType":"user","handlerId":2,"handlerName":"研究员2"},{"handlerType":"role","handlerId":4,"handlerName":"固收部"}],"approvalRemark":"终审节点，配置的全部处理人均需处理"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金快速入库（复核后 O32 自动审批，flow 109） =====
(109, 109, 'fund:fast-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":7,"handlerName":"权益部"},{"handlerType":"user","handlerId":12,"handlerName":"权益3"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"auto","label":"O32自动审批","x":400,"y":410,"shape":"rect"},{"id":"n6","type":"end","label":"结束","x":400,"y":530,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n4","to":"n6","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金白名单/批量入库（直通，flow 110-111） =====
(110, 110, 'fund:whitelist-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(111, 111, 'fund:batch-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"批量入库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金特殊策略入库（同标准，flow 112） =====
(112, 112, 'fund:special-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":7,"handlerName":"权益部"},{"handlerType":"user","handlerId":12,"handlerName":"权益3"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"all","approvalPersons":[{"handlerType":"role","handlerId":7,"handlerName":"权益部"},{"handlerType":"user","handlerId":1001,"handlerName":"管理员"}],"approvalRemark":"终审节点，配置的全部处理人均需处理"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券快速/白名单/批量出库（直通，flow 113-115） =====
(113, 113, 'bond:fast-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(114, 114, 'bond:whitelist-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(115, 115, 'bond:batch-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"批量出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券特殊策略出库（同标准，flow 116） =====
(116, 116, 'bond:special-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":4,"handlerName":"固收部"},{"handlerType":"user","handlerId":6,"handlerName":"固收1"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"user","handlerId":2,"handlerName":"研究员2"},{"handlerType":"role","handlerId":4,"handlerName":"固收部"}],"approvalRemark":"终审节点，按配置处理人审批"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金快速/白名单/批量出库（直通，flow 117-119） =====
(117, 117, 'fund:fast-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(118, 118, 'fund:whitelist-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"直接出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(119, 119, 'fund:batch-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"end","label":"结束","x":400,"y":280,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"批量出库","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金特殊策略出库（同标准，flow 120） =====
(120, 120, 'fund:special-outbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"量化部"},{"handlerType":"user","handlerId":13,"handlerName":"量化1"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"量化部"},{"handlerType":"user","handlerId":1001,"handlerName":"管理员"}],"approvalRemark":"终审节点，按配置处理人审批"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
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

-- ===== flow 105 (version 105) — 债券快速入库，6节点 =====
(10501, 105, 105, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10502, 105, 105, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10503, 105, 105, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10504, 105, 105, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10505, 105, 105, 'n5', 'auto', 'O32自动审批', 'rect', 400, 410, NULL, 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10506, 105, 105, 'n6', 'end', '结束', 'circle', 400, 530, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

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

-- ===== flow 109 (version 109) — 基金快速入库，6节点 =====
(10901, 109, 109, 'n1', 'start', '开始', 'circle', 400, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10902, 109, 109, 'n2', 'approval', '研究员A发起', 'rect', 400, 150, 'researcher-a', 2, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10903, 109, 109, 'n3', 'approval', '研究员B复核', 'rect', 400, 280, 'researcher-b', 3, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10904, 109, 109, 'n4', 'approval', '研究员A修改', 'rect', 650, 280, 'researcher-a', 4, '2026-05-20 09:00:00',
 '2026-05-20 09:00:00'),
(10905, 109, 109, 'n5', 'auto', 'O32自动审批', 'rect', 400, 410, NULL, 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10906, 109, 109, 'n6', 'end', '结束', 'circle', 400, 530, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

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
-- 3.1 流程连线（wf_flow_edge）
--     运行时审批流转读取归一化连线表，需与 canvas_edges 保持一致
-- ============================================================================

-- 标准流程：开始→发起→复核→修改/审批→O32→结束，修改驳回也直接结束
INSERT INTO `wf_flow_edge`
(`id`, `version_id`, `flow_id`, `edge_id`, `from_node_id`, `to_node_id`, `label`, `route_action`, `cond_logic`, `remark`,
 `crte_time`, `updt_time`)
SELECT v.id * 1000 + e.seq
      ,v.id
      ,v.flow_id
      ,e.edge_code
      ,fn.id
      ,tn.id
      ,e.label
      ,e.route_action
      ,'AND'
      ,NULL
      ,'2026-05-20 09:00:00'
      ,'2026-05-20 09:00:00'
FROM `wf_flow_version` v
INNER JOIN (
    SELECT 1 AS seq, 'e1' AS edge_code, 'n1' AS from_code, 'n2' AS to_code, '' AS label, NULL AS route_action
    UNION ALL SELECT 2, 'e2', 'n2', 'n3', '提交', NULL
    UNION ALL SELECT 3, 'e3', 'n3', 'n4', '驳回', 'reject'
    UNION ALL SELECT 4, 'e4', 'n4', 'n3', '重新提交', 'approve'
    UNION ALL SELECT 5, 'e5', 'n3', 'n5', '复核通过', 'approve'
    UNION ALL SELECT 6, 'e6', 'n5', 'n7', '不通过', 'reject'
    UNION ALL SELECT 7, 'e7', 'n5', 'n6', '通过', 'approve'
    UNION ALL SELECT 8, 'e8', 'n4', 'n7', '驳回', 'reject'
    UNION ALL SELECT 9, 'e9', 'n6', 'n7', '', NULL
) e
INNER JOIN `wf_flow_node` fn ON fn.version_id = v.id AND fn.node_id = e.from_code
INNER JOIN `wf_flow_node` tn ON tn.version_id = v.id AND tn.node_id = e.to_code
WHERE v.id IN (101, 102, 103, 104, 108, 112, 116, 120);

-- 快速入库复核流程：开始→发起→复核→O32→结束，复核驳回后修改再提交
INSERT INTO `wf_flow_edge`
(`id`, `version_id`, `flow_id`, `edge_id`, `from_node_id`, `to_node_id`, `label`, `route_action`, `cond_logic`, `remark`,
 `crte_time`, `updt_time`)
SELECT v.id * 1000 + e.seq
      ,v.id
      ,v.flow_id
      ,e.edge_code
      ,fn.id
      ,tn.id
      ,e.label
      ,e.route_action
      ,'AND'
      ,NULL
      ,'2026-05-20 09:00:00'
      ,'2026-05-20 09:00:00'
FROM `wf_flow_version` v
INNER JOIN (
    SELECT 1 AS seq, 'e1' AS edge_code, 'n1' AS from_code, 'n2' AS to_code, '' AS label, NULL AS route_action
    UNION ALL SELECT 2, 'e2', 'n2', 'n3', '提交', NULL
    UNION ALL SELECT 3, 'e3', 'n3', 'n4', '驳回', 'reject'
    UNION ALL SELECT 4, 'e4', 'n4', 'n3', '重新提交', 'approve'
    UNION ALL SELECT 5, 'e5', 'n3', 'n5', '复核通过', 'approve'
    UNION ALL SELECT 6, 'e6', 'n4', 'n6', '驳回', 'reject'
    UNION ALL SELECT 7, 'e7', 'n5', 'n6', '', NULL
) e
INNER JOIN `wf_flow_node` fn ON fn.version_id = v.id AND fn.node_id = e.from_code
INNER JOIN `wf_flow_node` tn ON tn.version_id = v.id AND tn.node_id = e.to_code
WHERE v.id IN (105, 109);

-- 直通流程：开始→发起→结束
INSERT INTO `wf_flow_edge`
(`id`, `version_id`, `flow_id`, `edge_id`, `from_node_id`, `to_node_id`, `label`, `route_action`, `cond_logic`, `remark`,
 `crte_time`, `updt_time`)
SELECT v.id * 1000 + e.seq
      ,v.id
      ,v.flow_id
      ,e.edge_code
      ,fn.id
      ,tn.id
      ,CASE
           WHEN e.seq = 1 THEN ''
           WHEN v.flow_key LIKE '%batch-inbound' THEN '批量入库'
           WHEN v.flow_key LIKE '%batch-outbound' THEN '批量出库'
           WHEN v.flow_key LIKE '%inbound' THEN '直接入库'
           WHEN v.flow_key LIKE '%outbound' THEN '直接出库'
           ELSE '直接处理'
       END
      ,NULL
      ,'AND'
      ,NULL
      ,'2026-05-20 09:00:00'
      ,'2026-05-20 09:00:00'
FROM `wf_flow_version` v
INNER JOIN (
    SELECT 1 AS seq, 'e1' AS edge_code, 'n1' AS from_code, 'n2' AS to_code
    UNION ALL SELECT 2, 'e2', 'n2', 'n3'
) e
INNER JOIN `wf_flow_node` fn ON fn.version_id = v.id AND fn.node_id = e.from_code
INNER JOIN `wf_flow_node` tn ON tn.version_id = v.id AND tn.node_id = e.to_code
WHERE v.id IN (106, 107, 110, 111, 113, 114, 115, 117, 118, 119);

-- ============================================================================
-- 3.2 连线条件规则（wf_edge_cond_rule）
--     标准流程：复核/审批节点按 auditStatus=通过/驳回/不通过 决定流向
-- ============================================================================

INSERT INTO `wf_edge_cond_rule`
(`id`, `edge_id`, `seq`, `field_code`, `operator`, `field_val`, `crte_time`, `updt_time`)
SELECT e.id * 10 + 1
      ,e.id
      ,1
      ,'auditStatus'
      ,'eq'
      ,CASE
           WHEN e.label = '驳回' THEN '驳回'
           WHEN e.label = '不通过' THEN '不通过'
           ELSE '通过'
       END
      ,'2026-05-20 09:00:00'
      ,'2026-05-20 09:00:00'
FROM `wf_flow_edge` e
WHERE e.label IN ('驳回', '复核通过', '不通过', '通过');

-- ============================================================================
-- 4. 审批节点配置（wf_node_approval_config）
--    标准7节点流程：n2(研究员A发起) n3(研究员B复核) n4(研究员A修改) n5(研究总监审批)
--    简单3节点流程：n2(研究员A发起)
-- ============================================================================

INSERT INTO `wf_node_approval_config`
(`id`, `node_id`, `approval_strategy`, `approval_remark`, `crte_time`, `updt_time`)
VALUES
-- flow 101 研究员A发起(10102) 研究员B复核(10103) 研究员A修改(10104) 研究总监审批(10105)
(10102, 10102, 'initiator', '研究员A发起债券标准升库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10103, 10103, 'preempt', '研究员B对债券标准升库进行复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10104, 10104, 'initiator', '研究员A根据驳回意见修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10105, 10105, 'all', '研究总监审批，不通过则流程直接结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 102
(10202, 10202, 'initiator', '研究员A发起债券标准降库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10203, 10203, 'preempt', '研究员B对债券标准降库进行复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10204, 10204, 'initiator', '研究员A根据驳回意见修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10205, 10205, 'preempt', '研究总监审批，不通过则流程直接结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 103
(10302, 10302, 'initiator', '研究员A发起基金标准升库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10303, 10303, 'preempt', '研究员B对基金标准升库进行复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10304, 10304, 'initiator', '研究员A根据驳回意见修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10305, 10305, 'all', '研究总监审批，不通过则流程直接结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 104
(10402, 10402, 'initiator', '研究员A发起基金标准降库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10403, 10403, 'preempt', '研究员B对基金标准降库进行复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10404, 10404, 'initiator', '研究员A根据驳回意见修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10405, 10405, 'preempt', '研究总监审批，不通过则流程直接结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 105 债券快速入库
(10502, 10502, 'initiator', '研究员A发起债券快速入库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10503, 10503, 'preempt', '研究员B复核债券快速入库，通过后进入O32自动审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10504, 10504, 'initiator', '研究员A修改后重新提交债券快速入库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 106 债券白名单入库
(10602, 10602, 'initiator', '研究员A发起债券白名单入库，直接入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 107 债券批量入库
(10702, 10702, 'initiator', '研究员A发起债券批量入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 108 债券特殊策略入库
(10802, 10802, 'initiator', '研究员A发起债券特殊策略入库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10803, 10803, 'preempt', '研究员B复核债券特殊策略入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10804, 10804, 'initiator', '研究员A修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10805, 10805, 'all', '研究总监审批，不通过则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 109 基金快速入库
(10902, 10902, 'initiator', '研究员A发起基金快速入库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10903, 10903, 'preempt', '研究员B复核基金快速入库，通过后进入O32自动审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10904, 10904, 'initiator', '研究员A修改后重新提交基金快速入库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 110 基金白名单入库
(11002, 11002, 'initiator', '研究员A发起基金白名单入库，直接入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 111 基金批量入库
(11102, 11102, 'initiator', '研究员A发起基金批量入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 112 基金特殊策略入库
(11202, 11202, 'initiator', '研究员A发起基金特殊策略入库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11203, 11203, 'preempt', '研究员B复核基金特殊策略入库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11204, 11204, 'initiator', '研究员A修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11205, 11205, 'all', '研究总监审批，不通过则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 113 债券快速出库
(11302, 11302, 'initiator', '研究员A发起债券快速出库，直接出库无需审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 114 债券白名单出库
(11402, 11402, 'initiator', '研究员A发起债券白名单出库，直接出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 115 债券批量出库
(11502, 11502, 'initiator', '研究员A发起债券批量出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 116 债券特殊策略出库
(11602, 11602, 'initiator', '研究员A发起债券特殊策略出库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11603, 11603, 'preempt', '研究员B复核债券特殊策略出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11604, 11604, 'initiator', '研究员A修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11605, 11605, 'preempt', '研究总监审批，不通过则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 117 基金快速出库
(11702, 11702, 'initiator', '研究员A发起基金快速出库，直接出库无需审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 118 基金白名单出库
(11802, 11802, 'initiator', '研究员A发起基金白名单出库，直接出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 119 基金批量出库
(11902, 11902, 'initiator', '研究员A发起基金批量出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
-- flow 120 基金特殊策略出库
(12002, 12002, 'initiator', '研究员A发起基金特殊策略出库申请', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12003, 12003, 'preempt', '研究员B复核基金特殊策略出库', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12004, 12004, 'initiator', '研究员A修改后重新提交', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(12005, 12005, 'preempt', '研究总监审批，不通过则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00');


-- ============================================================================
-- 4.1 审批节点处理人明细（wf_node_approval_handler）
--     关联 t_sys_role / t_sys_user，运行时角色会展开为具体人员
-- ============================================================================

INSERT INTO `wf_node_approval_handler`
(`id`, `approval_config_id`, `handler_type`, `handler_id`, `handler_name`, `sort_order`, `crte_time`, `updt_time`)
VALUES
(101031, 10103, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101032, 10103, 'user', 4, '研究员4', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101051, 10105, 'user', 2, '研究员2', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101052, 10105, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102031, 10203, 'role', 4, '固收部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102032, 10203, 'user', 6, '固收1', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102051, 10205, 'role', 4, '固收部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102052, 10205, 'user', 2, '研究员2', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103031, 10303, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103032, 10303, 'user', 12, '权益3', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103051, 10305, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103052, 10305, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104031, 10403, 'role', 9, '量化部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104032, 10403, 'user', 13, '量化1', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104051, 10405, 'role', 9, '量化部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104052, 10405, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(105031, 10503, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(105032, 10503, 'user', 2, '研究员2', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108031, 10803, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108032, 10803, 'user', 2, '研究员2', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108051, 10805, 'user', 2, '研究员2', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108052, 10805, 'role', 4, '固收部', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109031, 10903, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109032, 10903, 'user', 12, '权益3', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112031, 11203, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112032, 11203, 'user', 12, '权益3', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112051, 11205, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112052, 11205, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116031, 11603, 'role', 4, '固收部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116032, 11603, 'user', 6, '固收1', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116051, 11605, 'user', 2, '研究员2', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116052, 11605, 'role', 4, '固收部', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120031, 12003, 'role', 9, '量化部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120032, 12003, 'user', 13, '量化1', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120051, 12005, 'role', 9, '量化部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120052, 12005, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00');

SET FOREIGN_KEY_CHECKS = 1;

-- 初始化结果校验：仅保留 flow 101-120，共 20 条流程
SELECT SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END) AS active_definition_count
      ,SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) AS deleted_definition_count
      ,COUNT(*) AS total_definition_count
FROM `wf_flow_definition`;

SELECT `status`, COUNT(*) AS status_count
FROM `wf_flow_definition`
GROUP BY `status`
ORDER BY `status`;

SELECT `category`, COUNT(*) AS category_count
FROM `wf_flow_definition`
GROUP BY `category`
ORDER BY `category`;

-- 以下校验正常情况下均返回 0 行
SELECT `flow_key`, COUNT(*) AS duplicate_count
FROM `wf_flow_definition`
GROUP BY `flow_key`
HAVING COUNT(*) > 1;

SELECT d.id AS unexpected_flow_id
FROM `wf_flow_definition` d
WHERE d.id NOT BETWEEN 101 AND 120;

SELECT expected.id AS missing_flow_id
FROM (
    SELECT 101 AS id
    UNION ALL SELECT 102
    UNION ALL SELECT 103
    UNION ALL SELECT 104
    UNION ALL SELECT 105
    UNION ALL SELECT 106
    UNION ALL SELECT 107
    UNION ALL SELECT 108
    UNION ALL SELECT 109
    UNION ALL SELECT 110
    UNION ALL SELECT 111
    UNION ALL SELECT 112
    UNION ALL SELECT 113
    UNION ALL SELECT 114
    UNION ALL SELECT 115
    UNION ALL SELECT 116
    UNION ALL SELECT 117
    UNION ALL SELECT 118
    UNION ALL SELECT 119
    UNION ALL SELECT 120
) expected
LEFT JOIN `wf_flow_definition` d ON d.id = expected.id
WHERE d.id IS NULL;

SELECT d.id AS incomplete_flow_id
FROM `wf_flow_definition` d
WHERE d.id BETWEEN 101 AND 120
  AND (
      NOT EXISTS (SELECT 1 FROM `wf_flow_version` v WHERE v.flow_id = d.id)
      OR NOT EXISTS (SELECT 1 FROM `wf_flow_node` n WHERE n.flow_id = d.id)
      OR NOT EXISTS (SELECT 1 FROM `wf_flow_edge` e WHERE e.flow_id = d.id)
  );

SELECT v.id AS invalid_version_id
      ,SUM(CASE WHEN n.node_type = 'start' THEN 1 ELSE 0 END) AS start_count
      ,SUM(CASE WHEN n.node_type = 'end' THEN 1 ELSE 0 END) AS end_count
      ,SUM(CASE WHEN NOT EXISTS (
          SELECT 1
          FROM `wf_flow_edge` e
          WHERE e.version_id = v.id
            AND (e.from_node_id = n.id OR e.to_node_id = n.id)
      ) THEN 1 ELSE 0 END) AS isolated_node_count
FROM `wf_flow_version` v
LEFT JOIN `wf_flow_node` n ON n.version_id = v.id
GROUP BY v.id
HAVING start_count != 1 OR end_count < 1 OR isolated_node_count > 0;

SELECT e.id AS invalid_edge_id
FROM `wf_flow_edge` e
LEFT JOIN `wf_flow_node` fn ON fn.id = e.from_node_id AND fn.version_id = e.version_id
LEFT JOIN `wf_flow_node` tn ON tn.id = e.to_node_id AND tn.version_id = e.version_id
WHERE fn.id IS NULL OR tn.id IS NULL;

SELECT v.id AS inconsistent_version_id
      ,JSON_LENGTH(v.canvas_nodes) AS snapshot_node_count
      ,COUNT(DISTINCT n.id) AS normalized_node_count
      ,JSON_LENGTH(v.canvas_edges) AS snapshot_edge_count
      ,COUNT(DISTINCT e.id) AS normalized_edge_count
FROM `wf_flow_version` v
LEFT JOIN `wf_flow_node` n ON n.version_id = v.id
LEFT JOIN `wf_flow_edge` e ON e.version_id = v.id
WHERE v.flow_id BETWEEN 101 AND 120
GROUP BY v.id
HAVING snapshot_node_count != normalized_node_count
    OR snapshot_edge_count != normalized_edge_count;

-- 11 张业务表的数据量概览
SELECT 'wf_flow_definition' AS table_name, COUNT(*) AS row_count FROM `wf_flow_definition`
UNION ALL SELECT 'wf_flow_version', COUNT(*) FROM `wf_flow_version`
UNION ALL SELECT 'wf_flow_node', COUNT(*) FROM `wf_flow_node`
UNION ALL SELECT 'wf_node_approval_config', COUNT(*) FROM `wf_node_approval_config`
UNION ALL SELECT 'wf_node_approval_handler', COUNT(*) FROM `wf_node_approval_handler`
UNION ALL SELECT 'wf_node_auto_config', COUNT(*) FROM `wf_node_auto_config`
UNION ALL SELECT 'wf_node_notify_config', COUNT(*) FROM `wf_node_notify_config`
UNION ALL SELECT 'wf_node_condition_config', COUNT(*) FROM `wf_node_condition_config`
UNION ALL SELECT 'wf_flow_edge', COUNT(*) FROM `wf_flow_edge`
UNION ALL SELECT 'wf_edge_cond_rule', COUNT(*) FROM `wf_edge_cond_rule`
UNION ALL SELECT 'wf_role_dict', COUNT(*) FROM `wf_role_dict`;

-- 事件表初始化后必须保持为空
SELECT 'wf_flow_definition_evt' AS table_name, COUNT(*) AS row_count FROM `wf_flow_definition_evt`
UNION ALL SELECT 'wf_flow_version_evt', COUNT(*) FROM `wf_flow_version_evt`
UNION ALL SELECT 'wf_flow_node_evt', COUNT(*) FROM `wf_flow_node_evt`
UNION ALL SELECT 'wf_node_approval_config_evt', COUNT(*) FROM `wf_node_approval_config_evt`
UNION ALL SELECT 'wf_node_approval_handler_evt', COUNT(*) FROM `wf_node_approval_handler_evt`
UNION ALL SELECT 'wf_node_auto_config_evt', COUNT(*) FROM `wf_node_auto_config_evt`
UNION ALL SELECT 'wf_node_notify_config_evt', COUNT(*) FROM `wf_node_notify_config_evt`
UNION ALL SELECT 'wf_node_condition_config_evt', COUNT(*) FROM `wf_node_condition_config_evt`
UNION ALL SELECT 'wf_flow_edge_evt', COUNT(*) FROM `wf_flow_edge_evt`
UNION ALL SELECT 'wf_edge_cond_rule_evt', COUNT(*) FROM `wf_edge_cond_rule_evt`
UNION ALL SELECT 'wf_role_dict_evt', COUNT(*) FROM `wf_role_dict_evt`;
