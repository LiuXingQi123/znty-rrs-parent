-- ============================================================================
-- 工作流平台 — 32 条正常流程 + 1 条逻辑删除流程测试数据初始化脚本
-- 目标库：znty_sirm
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
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":2,"subjectName":"信用研究组"},{"subjectType":"user","subjectId":4,"subjectName":"研究员4"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"all","approvalPersons":[{"subjectType":"user","subjectId":2,"subjectName":"叶伟"},{"subjectType":"user","subjectId":1001,"subjectName":"管理员"}],"approvalRemark":"终审节点，配置的全部处理人均需处理"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(102, 102, 'bond:standard-downgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":4,"subjectName":"固收部"},{"subjectType":"user","subjectId":6,"subjectName":"固收1"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":4,"subjectName":"固收部"},{"subjectType":"user","subjectId":2,"subjectName":"叶伟"}],"approvalRemark":"终审节点，按配置处理人审批"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(103, 103, 'fund:standard-upgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":7,"subjectName":"权益部"},{"subjectType":"user","subjectId":12,"subjectName":"权益3"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"all","approvalPersons":[{"subjectType":"role","subjectId":7,"subjectName":"权益部"},{"subjectType":"user","subjectId":1001,"subjectName":"管理员"}],"approvalRemark":"终审节点，配置的全部处理人均需处理"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

(104, 104, 'fund:standard-downgrade', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":9,"subjectName":"量化部"},{"subjectType":"user","subjectId":13,"subjectName":"量化1"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":9,"subjectName":"量化部"},{"subjectType":"user","subjectId":1001,"subjectName":"管理员"}],"approvalRemark":"终审节点，按配置处理人审批"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 债券快速入库（复核后 O32 自动审批，flow 105） =====
-- 节点：n1=开始 n2=研究员A发起 n3=研究员B复核 n4=研究员A修改 n5=O32自动审批 n6=结束

(105, 105, 'bond:fast-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":2,"subjectName":"信用研究组"},{"subjectType":"user","subjectId":2,"subjectName":"叶伟"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"auto","label":"O32自动审批","x":400,"y":410,"shape":"rect"},{"id":"n6","type":"end","label":"结束","x":400,"y":530,"shape":"circle"}]',
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
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":2,"subjectName":"信用研究组"},{"subjectType":"user","subjectId":2,"subjectName":"叶伟"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"all","approvalPersons":[{"subjectType":"user","subjectId":2,"subjectName":"叶伟"},{"subjectType":"role","subjectId":4,"subjectName":"固收部"}],"approvalRemark":"终审节点，配置的全部处理人均需处理"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
 '[{"id":"e1","from":"n1","to":"n2","label":"","condRules":[],"condLogic":"AND"},{"id":"e2","from":"n2","to":"n3","label":"提交","condRules":[],"condLogic":"AND"},{"id":"e3","from":"n3","to":"n4","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n4","to":"n3","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"approve"},{"id":"e5","from":"n3","to":"n5","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n5","to":"n7","label":"不通过","condRules":[{"field":"auditStatus","op":"eq","val":"不通过"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n5","to":"n6","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n4","to":"n7","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n6","to":"n7","label":"","condRules":[],"condLogic":"AND"}]',
 0, 0, 1, 1001, '2026-05-20 09:00:00', 1001, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),

-- ===== 基金快速入库（复核后 O32 自动审批，flow 109） =====
(109, 109, 'fund:fast-inbound', 1, 'active', '初始版本',
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":7,"subjectName":"权益部"},{"subjectType":"user","subjectId":12,"subjectName":"权益3"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"auto","label":"O32自动审批","x":400,"y":410,"shape":"rect"},{"id":"n6","type":"end","label":"结束","x":400,"y":530,"shape":"circle"}]',
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
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":7,"subjectName":"权益部"},{"subjectType":"user","subjectId":12,"subjectName":"权益3"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"all","approvalPersons":[{"subjectType":"role","subjectId":7,"subjectName":"权益部"},{"subjectType":"user","subjectId":1001,"subjectName":"管理员"}],"approvalRemark":"终审节点，配置的全部处理人均需处理"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
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
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":4,"subjectName":"固收部"},{"subjectType":"user","subjectId":6,"subjectName":"固收1"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"user","subjectId":2,"subjectName":"叶伟"},{"subjectType":"role","subjectId":4,"subjectName":"固收部"}],"approvalRemark":"终审节点，按配置处理人审批"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
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
 '[{"id":"n1","type":"start","label":"开始","x":400,"y":40,"shape":"circle"},{"id":"n2","type":"approval","label":"研究员A发起","x":400,"y":150,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n3","type":"approval","label":"研究员B复核","x":400,"y":280,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":9,"subjectName":"量化部"},{"subjectType":"user","subjectId":13,"subjectName":"量化1"}],"approvalRemark":"复核节点，支持角色和人员混选，任一处理人通过即可"},{"id":"n4","type":"approval","label":"研究员A修改","x":650,"y":280,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交"},{"id":"n5","type":"approval","label":"研究总监审批","x":400,"y":410,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"subjectType":"role","subjectId":9,"subjectName":"量化部"},{"subjectType":"user","subjectId":1001,"subjectName":"管理员"}],"approvalRemark":"终审节点，按配置处理人审批"},{"id":"n6","type":"auto","label":"O32自动审批","x":400,"y":540,"shape":"rect"},{"id":"n7","type":"end","label":"结束","x":400,"y":660,"shape":"circle"}]',
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
(`id`, `approval_config_id`, `subject_type`, `subject_id`, `subject_name`, `sort_order`, `crte_time`, `updt_time`)
VALUES
(101031, 10103, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101032, 10103, 'user', 4, '研究员4', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101051, 10105, 'user', 2, '叶伟', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101052, 10105, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102031, 10203, 'role', 4, '固收部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102032, 10203, 'user', 6, '固收1', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102051, 10205, 'role', 4, '固收部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102052, 10205, 'user', 2, '叶伟', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103031, 10303, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103032, 10303, 'user', 12, '权益3', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103051, 10305, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103052, 10305, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104031, 10403, 'role', 9, '量化部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104032, 10403, 'user', 13, '量化1', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104051, 10405, 'role', 9, '量化部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104052, 10405, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(105031, 10503, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(105032, 10503, 'user', 2, '叶伟', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108031, 10803, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108032, 10803, 'user', 2, '叶伟', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108051, 10805, 'user', 2, '叶伟', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108052, 10805, 'role', 4, '固收部', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109031, 10903, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109032, 10903, 'user', 12, '权益3', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112031, 11203, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112032, 11203, 'user', 12, '权益3', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112051, 11205, 'role', 7, '权益部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112052, 11205, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116031, 11603, 'role', 4, '固收部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116032, 11603, 'user', 6, '固收1', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116051, 11605, 'user', 2, '叶伟', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(116052, 11605, 'role', 4, '固收部', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120031, 12003, 'role', 9, '量化部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120032, 12003, 'user', 13, '量化1', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120051, 12005, 'role', 9, '量化部', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(120052, 12005, 'user', 1001, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00');

-- ============================================================================
-- 5. 扩展测试场景（flow 121-135）
--    补齐 stock / company 分类、draft / disabled / deleted 状态、多版本历史及全部节点类型
-- ============================================================================

INSERT INTO `wf_flow_definition`
(`id`, `name`, `flow_key`, `category`, `description`, `remark`, `status`, `created_by`, `updated_by`, `is_deleted`,
 `crte_time`, `updt_time`)
VALUES
(121, '债券准入审批流程', 'bond:admission-review', 'bond',
 '债券首次准入时按评级分支审批，通过后执行风控检查并归档。', '覆盖评级条件、审批和多自动任务。', 'active',
 1001, 1001, 0, '2026-05-21 09:10:00', '2026-05-21 10:30:00'),
(122, '债券评级下调处置流程', 'bond:rating-downgrade', 'bond',
 '债券评级下调后由研究组复核，并向指定人员发送处置通知。', '覆盖驳回路径与指定人员通知。', 'active',
 1001, 1001, 0, '2026-05-22 09:20:00', '2026-05-22 11:00:00'),
(123, '债券临时额度申请流程', 'bond:temporary-limit', 'bond',
 '债券临时额度申请草稿，待补充大额条件和审批人后发布。', '从未发布的草稿流程，允许修改流程 Key。', 'draft',
 1001, 1001, 0, '2026-06-01 14:00:00', '2026-06-01 16:20:00'),
(124, '基金大额申购审批流程', 'fund:large-subscription', 'fund',
 '基金大额申购按申请金额分流，大额场景需全部审批人处理。', '覆盖金额条件、全部处理和多个自动任务。', 'active',
 1001, 1001, 0, '2026-05-24 09:00:00', '2026-05-24 15:40:00'),
(125, '受限债券解禁审批流程', 'bond:restriction-release', 'bond',
 '受限债券解除交易限制前执行审批和持仓同步。', '流程已停用，用于验证停用状态和历史展示。', 'disabled',
 1001, 1001, 0, '2026-04-18 10:00:00', '2026-05-25 17:30:00'),
(126, '另类投资备案流程', 'other:alternative-filing', 'other',
 '另类投资项目完成备案审批后通知流程发起人。', '覆盖 other 分类和发起人通知。', 'active',
 1001, 1001, 0, '2026-05-26 09:30:00', '2026-05-26 12:00:00'),
(127, '跨部门联合评审流程', 'other:joint-department-review', 'other',
 '投资、风控和运营部门联合评审，全部处理人完成后通知发起人。', '覆盖角色人员混选和全部处理策略。', 'active',
 1001, 1001, 0, '2026-05-27 09:15:00', '2026-05-27 14:20:00'),
(128, '债券紧急风险处置流程', 'bond:emergency-risk-disposal', 'bond',
 '债券突发风险事件由任一风控人员抢占审批，随后执行风控检查和持仓更新。', '覆盖抢占审批和多自动任务。', 'active',
 1001, 1001, 0, '2026-05-28 08:30:00', '2026-05-28 09:45:00'),
(129, '基金净值异常复核流程', 'fund:nav-anomaly-review', 'fund',
 '基金净值异常复核流程，已发布版本基础上正在调整异常分级规则。', '存在 active v1 和 draft v2。', 'draft',
 1001, 1001, 0, '2026-05-10 10:00:00', '2026-06-02 11:30:00'),
(130, '发行人观察名单审批流程', 'bond:issuer-watchlist', 'bond',
 '发行人满足评级或受限标志任一条件时进入观察名单审批。', '覆盖多条件 OR 分支。', 'active',
 1001, 1001, 0, '2026-05-29 09:40:00', '2026-05-29 16:10:00'),
(131, '业务数据订正流程', 'other:data-correction', 'other',
 '业务数据订正经运营复核后执行同步与归档。', '保留历史 v1，当前发布版本为 v2。', 'active',
 1001, 1001, 0, '2026-04-01 09:00:00', '2026-06-03 10:20:00'),
(132, '市场事件通知流程', 'other:market-event-notify', 'other',
 '市场重大事件发生后通过系统、邮件和短信通知指定人员。', '覆盖多渠道和多人通知。', 'active',
 1001, 1001, 0, '2026-05-30 07:50:00', '2026-05-30 08:10:00'),
(133, '已删除账户开立审批流程', 'other:deleted-account-opening', 'other',
 '历史账户开立审批流程，已被逻辑删除但保留完整版本和画布数据。', '验证列表排除、详情不存在和删除数据留痕。', 'active',
 1001, 1001, 1, '2026-03-10 09:00:00', '2026-05-31 18:00:00'),
(134, '股票入库审批流程', 'stock:inbound-review', 'stock',
 '股票首次入库需研究组发起、投资总监审批，通过后自动同步持仓。', '覆盖股票分类和标准审批。', 'active',
 1001, 1001, 0, '2026-06-10 09:00:00', '2026-06-10 10:00:00'),
(135, '发行人主体准入审批流程', 'company:issuer-admission', 'company',
 '对发行人主体进行准入评估，审核通过后关联主体纳入白名单管理。', '覆盖主体分类和多人审批。', 'active',
 1001, 1001, 0, '2026-06-12 09:00:00', '2026-06-12 10:30:00');

-- 版本 ID 规则：flow_id * 10 + ver_num
INSERT INTO `wf_flow_version`
(`id`, `flow_id`, `flow_key`, `ver_num`, `status`, `publish_note`,
 `canvas_nodes`, `canvas_edges`, `canvas_pan_x`, `canvas_pan_y`, `canvas_zoom`,
 `published_by`, `published_time`, `created_by`, `crte_time`, `updt_time`)
VALUES
(1211, 121, 'bond:admission-review', 1, 'active', '债券准入审批初始版本',
 NULL, NULL, 20, 10, 1.00, 1001, '2026-05-21 10:30:00', 1001, '2026-05-21 09:10:00', '2026-05-21 10:30:00'),
(1221, 122, 'bond:rating-downgrade', 1, 'active', '评级下调处置初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-05-22 11:00:00', 1001, '2026-05-22 09:20:00', '2026-05-22 11:00:00'),
(1231, 123, 'bond:temporary-limit', 1, 'draft', NULL,
 NULL, NULL, 80, 30, 0.90, NULL, NULL, 1001, '2026-06-01 14:00:00', '2026-06-01 16:20:00'),
(1241, 124, 'fund:large-subscription', 1, 'active', '大额申购分级审批上线',
 NULL, NULL, -20, 0, 1.10, 1001, '2026-05-24 15:40:00', 1001, '2026-05-24 09:00:00', '2026-05-24 15:40:00'),
(1251, 125, 'bond:restriction-release', 1, 'disabled', '受限债券解禁审批初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-04-18 14:00:00', 1001, '2026-04-18 10:00:00', '2026-05-25 17:30:00'),
(1261, 126, 'other:alternative-filing', 1, 'active', '另类投资备案初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-05-26 12:00:00', 1001, '2026-05-26 09:30:00', '2026-05-26 12:00:00'),
(1271, 127, 'other:joint-department-review', 1, 'active', '跨部门联合评审初始版本',
 NULL, NULL, 30, 0, 0.95, 1001, '2026-05-27 14:20:00', 1001, '2026-05-27 09:15:00', '2026-05-27 14:20:00'),
(1281, 128, 'bond:emergency-risk-disposal', 1, 'active', '紧急风险处置初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-05-28 09:45:00', 1001, '2026-05-28 08:30:00', '2026-05-28 09:45:00'),
(1291, 129, 'fund:nav-anomaly-review', 1, 'active', '净值异常人工复核初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-05-10 15:00:00', 1001, '2026-05-10 10:00:00', '2026-05-10 15:00:00'),
(1292, 129, 'fund:nav-anomaly-review', 2, 'draft', NULL,
 NULL, NULL, 60, 20, 0.90, NULL, NULL, 1001, '2026-06-02 09:00:00', '2026-06-02 11:30:00'),
(1301, 130, 'bond:issuer-watchlist', 1, 'active', '发行人观察名单初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-05-29 16:10:00', 1001, '2026-05-29 09:40:00', '2026-05-29 16:10:00'),
(1311, 131, 'other:data-correction', 1, 'disabled', '业务数据订正基础版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-04-01 14:00:00', 1001, '2026-04-01 09:00:00', '2026-05-15 09:00:00'),
(1312, 131, 'other:data-correction', 2, 'active', '增加归档任务和完成通知',
 NULL, NULL, 10, 0, 1.00, 1001, '2026-06-03 10:20:00', 1001, '2026-06-03 09:00:00', '2026-06-03 10:20:00'),
(1321, 132, 'other:market-event-notify', 1, 'active', '市场事件多渠道通知初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-05-30 08:10:00', 1001, '2026-05-30 07:50:00', '2026-05-30 08:10:00'),
(1331, 133, 'other:deleted-account-opening', 1, 'active', '账户开立审批历史版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-03-10 15:00:00', 1001, '2026-03-10 09:00:00', '2026-05-31 18:00:00'),
(1341, 134, 'stock:inbound-review', 1, 'active', '股票入库审批初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-06-10 10:00:00', 1001, '2026-06-10 09:00:00', '2026-06-10 10:00:00'),
(1351, 135, 'company:issuer-admission', 1, 'active', '发行人主体准入初始版本',
 NULL, NULL, 0, 0, 1.00, 1001, '2026-06-12 10:30:00', 1001, '2026-06-12 09:00:00', '2026-06-12 10:30:00');

-- 节点 ID 规则：version_id * 10 + 节点序号
INSERT INTO `wf_flow_node`
(`id`, `version_id`, `flow_id`, `node_id`, `node_type`, `label`, `shape`, `pos_x`, `pos_y`, `sub_label`, `sort_order`,
 `crte_time`, `updt_time`)
VALUES
(12111,1211,121,'n1','start','开始','circle',120,240,NULL,1,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12112,1211,121,'n2','condition','评级判断','diamond',300,240,NULL,2,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12113,1211,121,'n3','approval','信用研究复核','rect',500,160,'credit-research',3,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12114,1211,121,'n4','auto','准入后处理','rect',700,240,NULL,4,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12115,1211,121,'n5','end','结束','circle',900,240,NULL,5,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12211,1221,122,'n1','start','开始','circle',120,240,NULL,1,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12212,1221,122,'n2','approval','评级下调复核','rect',320,240,'credit-research',2,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12213,1221,122,'n3','condition','复核结果','diamond',520,240,NULL,3,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12214,1221,122,'n4','notify','处置通知','rect',720,160,NULL,4,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12215,1221,122,'n5','end','结束','circle',920,240,NULL,5,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12311,1231,123,'n1','start','开始','circle',120,240,NULL,1,'2026-06-01 14:00:00','2026-06-01 16:20:00'),
(12312,1231,123,'n2','approval','额度审批','rect',340,240,'risk-officer',2,'2026-06-01 14:00:00','2026-06-01 16:20:00'),
(12313,1231,123,'n3','condition','额度判断','diamond',560,240,NULL,3,'2026-06-01 14:00:00','2026-06-01 16:20:00'),
(12314,1231,123,'n4','end','结束','circle',780,240,NULL,4,'2026-06-01 14:00:00','2026-06-01 16:20:00'),
(12411,1241,124,'n1','start','开始','circle',100,240,NULL,1,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12412,1241,124,'n2','condition','申购金额判断','diamond',280,240,NULL,2,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12413,1241,124,'n3','approval','大额联合审批','rect',480,160,'invest-director',3,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12414,1241,124,'n4','approval','普通申购复核','rect',480,320,'fund-manager',4,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12415,1241,124,'n5','auto','申购后处理','rect',700,240,NULL,5,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12416,1241,124,'n6','end','结束','circle',900,240,NULL,6,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12511,1251,125,'n1','start','开始','circle',120,240,NULL,1,'2026-04-18 10:00:00','2026-05-25 17:30:00'),
(12512,1251,125,'n2','approval','解禁审批','rect',360,240,'risk-officer',2,'2026-04-18 10:00:00','2026-05-25 17:30:00'),
(12513,1251,125,'n3','auto','同步交易限制','rect',600,240,NULL,3,'2026-04-18 10:00:00','2026-05-25 17:30:00'),
(12514,1251,125,'n4','end','结束','circle',840,240,NULL,4,'2026-04-18 10:00:00','2026-05-25 17:30:00'),
(12611,1261,126,'n1','start','开始','circle',120,240,NULL,1,'2026-05-26 09:30:00','2026-05-26 12:00:00'),
(12612,1261,126,'n2','approval','备案审批','rect',360,240,'invest-director',2,'2026-05-26 09:30:00','2026-05-26 12:00:00'),
(12613,1261,126,'n3','notify','备案结果通知','rect',600,240,NULL,3,'2026-05-26 09:30:00','2026-05-26 12:00:00'),
(12614,1261,126,'n4','end','结束','circle',840,240,NULL,4,'2026-05-26 09:30:00','2026-05-26 12:00:00'),
(12711,1271,127,'n1','start','开始','circle',100,240,NULL,1,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12712,1271,127,'n2','approval','投资与风控评审','rect',300,240,'joint-review',2,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12713,1271,127,'n3','approval','运营复核','rect',520,240,'operations',3,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12714,1271,127,'n4','notify','评审结果通知','rect',740,240,NULL,4,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12715,1271,127,'n5','end','结束','circle',940,240,NULL,5,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12811,1281,128,'n1','start','开始','circle',120,240,NULL,1,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12812,1281,128,'n2','approval','紧急处置审批','rect',340,240,'risk-officer',2,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12813,1281,128,'n3','auto','执行风险处置','rect',560,240,NULL,3,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12814,1281,128,'n4','notify','处置完成通知','rect',780,240,NULL,4,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12815,1281,128,'n5','end','结束','circle',980,240,NULL,5,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12911,1291,129,'n1','start','开始','circle',120,240,NULL,1,'2026-05-10 10:00:00','2026-05-10 15:00:00'),
(12912,1291,129,'n2','approval','净值异常复核','rect',380,240,'fund-manager',2,'2026-05-10 10:00:00','2026-05-10 15:00:00'),
(12913,1291,129,'n3','notify','复核结果通知','rect',640,240,NULL,3,'2026-05-10 10:00:00','2026-05-10 15:00:00'),
(12914,1291,129,'n4','end','结束','circle',900,240,NULL,4,'2026-05-10 10:00:00','2026-05-10 15:00:00'),
(12921,1292,129,'n1','start','开始','circle',100,240,NULL,1,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(12922,1292,129,'n2','condition','异常幅度判断','diamond',300,240,NULL,2,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(12923,1292,129,'n3','approval','重大异常复核','rect',520,160,'invest-director',3,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(12924,1292,129,'n4','notify','复核结果通知','rect',740,240,NULL,4,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(12925,1292,129,'n5','end','结束','circle',940,240,NULL,5,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(13011,1301,130,'n1','start','开始','circle',120,240,NULL,1,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13012,1301,130,'n2','condition','观察名单条件','diamond',340,240,NULL,2,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13013,1301,130,'n3','approval','观察名单审批','rect',560,160,'credit-research',3,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13014,1301,130,'n4','notify','名单变更通知','rect',780,240,NULL,4,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13015,1301,130,'n5','end','结束','circle',980,240,NULL,5,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13111,1311,131,'n1','start','开始','circle',120,240,NULL,1,'2026-04-01 09:00:00','2026-05-15 09:00:00'),
(13112,1311,131,'n2','approval','运营复核','rect',380,240,'operations',2,'2026-04-01 09:00:00','2026-05-15 09:00:00'),
(13113,1311,131,'n3','auto','同步订正数据','rect',640,240,NULL,3,'2026-04-01 09:00:00','2026-05-15 09:00:00'),
(13114,1311,131,'n4','end','结束','circle',900,240,NULL,4,'2026-04-01 09:00:00','2026-05-15 09:00:00'),
(13121,1312,131,'n1','start','开始','circle',100,240,NULL,1,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13122,1312,131,'n2','approval','运营复核','rect',300,240,'operations',2,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13123,1312,131,'n3','auto','同步并归档','rect',520,240,NULL,3,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13124,1312,131,'n4','notify','订正完成通知','rect',740,240,NULL,4,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13125,1312,131,'n5','end','结束','circle',940,240,NULL,5,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13211,1321,132,'n1','start','开始','circle',160,240,NULL,1,'2026-05-30 07:50:00','2026-05-30 08:10:00'),
(13212,1321,132,'n2','notify','市场事件通知','rect',460,240,NULL,2,'2026-05-30 07:50:00','2026-05-30 08:10:00'),
(13213,1321,132,'n3','end','结束','circle',760,240,NULL,3,'2026-05-30 07:50:00','2026-05-30 08:10:00'),
(13311,1331,133,'n1','start','开始','circle',80,240,NULL,1,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13312,1331,133,'n2','condition','账户类型判断','diamond',260,240,NULL,2,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13313,1331,133,'n3','approval','账户开立审批','rect',460,160,'operations',3,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13314,1331,133,'n4','auto','创建账户并通知','rect',660,240,NULL,4,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13315,1331,133,'n5','notify','账户结果通知','rect',860,240,NULL,5,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13316,1331,133,'n6','end','结束','circle',1040,240,NULL,6,'2026-03-10 09:00:00','2026-05-31 18:00:00');

-- 连线 ID 规则：version_id * 10 + 连线序号
INSERT INTO `wf_flow_edge`
(`id`, `version_id`, `flow_id`, `edge_id`, `from_node_id`, `to_node_id`, `label`, `route_action`, `cond_logic`,
 `remark`, `crte_time`, `updt_time`)
VALUES
(12111,1211,121,'e1',12111,12112,'提交','submit','AND',NULL,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12112,1211,121,'e2',12112,12113,'评级需复核','approve','AND',NULL,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12113,1211,121,'e3',12112,12114,'评级直通','auto','AND',NULL,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12114,1211,121,'e4',12113,12114,'通过','approve','AND',NULL,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12115,1211,121,'e5',12114,12115,'完成','auto','AND',NULL,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(12211,1221,122,'e1',12211,12212,'提交','submit','AND',NULL,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12212,1221,122,'e2',12212,12213,'复核完成','approve','AND',NULL,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12213,1221,122,'e3',12213,12214,'需要处置','approve','AND',NULL,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12214,1221,122,'e4',12213,12215,'驳回结束','reject','AND',NULL,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12215,1221,122,'e5',12214,12215,'通知完成','auto','AND',NULL,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12311,1231,123,'e1',12311,12312,'提交','submit','AND',NULL,'2026-06-01 14:00:00','2026-06-01 16:20:00'),
(12312,1231,123,'e2',12312,12313,'审批通过','approve','AND',NULL,'2026-06-01 14:00:00','2026-06-01 16:20:00'),
(12313,1231,123,'e3',12313,12314,'额度校验完成','approve','AND',NULL,'2026-06-01 14:00:00','2026-06-01 16:20:00'),
(12411,1241,124,'e1',12411,12412,'提交','submit','AND',NULL,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12412,1241,124,'e2',12412,12413,'大额申购','approve','AND',NULL,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12413,1241,124,'e3',12412,12414,'普通申购','approve','AND',NULL,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12414,1241,124,'e4',12413,12415,'全部通过','approve','AND',NULL,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12415,1241,124,'e5',12414,12415,'复核通过','approve','AND',NULL,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12416,1241,124,'e6',12415,12416,'处理完成','auto','AND',NULL,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(12511,1251,125,'e1',12511,12512,'提交','submit','AND',NULL,'2026-04-18 10:00:00','2026-05-25 17:30:00'),
(12512,1251,125,'e2',12512,12513,'审批通过','approve','AND',NULL,'2026-04-18 10:00:00','2026-05-25 17:30:00'),
(12513,1251,125,'e3',12513,12514,'同步完成','auto','AND',NULL,'2026-04-18 10:00:00','2026-05-25 17:30:00'),
(12611,1261,126,'e1',12611,12612,'提交','submit','AND',NULL,'2026-05-26 09:30:00','2026-05-26 12:00:00'),
(12612,1261,126,'e2',12612,12613,'审批通过','approve','AND',NULL,'2026-05-26 09:30:00','2026-05-26 12:00:00'),
(12613,1261,126,'e3',12613,12614,'通知完成','auto','AND',NULL,'2026-05-26 09:30:00','2026-05-26 12:00:00'),
(12711,1271,127,'e1',12711,12712,'提交','submit','AND',NULL,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12712,1271,127,'e2',12712,12713,'联合评审通过','approve','AND',NULL,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12713,1271,127,'e3',12713,12714,'运营复核通过','approve','AND',NULL,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12714,1271,127,'e4',12714,12715,'通知完成','auto','AND',NULL,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12811,1281,128,'e1',12811,12812,'紧急提交','submit','AND',NULL,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12812,1281,128,'e2',12812,12813,'审批通过','approve','AND',NULL,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12813,1281,128,'e3',12813,12814,'处置完成','auto','AND',NULL,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12814,1281,128,'e4',12814,12815,'通知完成','auto','AND',NULL,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12911,1291,129,'e1',12911,12912,'提交','submit','AND',NULL,'2026-05-10 10:00:00','2026-05-10 15:00:00'),
(12912,1291,129,'e2',12912,12913,'复核完成','approve','AND',NULL,'2026-05-10 10:00:00','2026-05-10 15:00:00'),
(12913,1291,129,'e3',12913,12914,'通知完成','auto','AND',NULL,'2026-05-10 10:00:00','2026-05-10 15:00:00'),
(12921,1292,129,'e1',12921,12922,'提交','submit','AND',NULL,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(12922,1292,129,'e2',12922,12923,'重大异常','approve','AND',NULL,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(12923,1292,129,'e3',12922,12924,'一般异常','approve','AND',NULL,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(12924,1292,129,'e4',12923,12924,'复核完成','approve','AND',NULL,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(12925,1292,129,'e5',12924,12925,'通知完成','auto','AND',NULL,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(13011,1301,130,'e1',13011,13012,'提交','submit','AND',NULL,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13012,1301,130,'e2',13012,13013,'进入观察名单','approve','OR',NULL,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13013,1301,130,'e3',13012,13015,'无需观察','approve','AND',NULL,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13014,1301,130,'e4',13013,13014,'审批通过','approve','AND',NULL,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13015,1301,130,'e5',13014,13015,'通知完成','auto','AND',NULL,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13111,1311,131,'e1',13111,13112,'提交','submit','AND',NULL,'2026-04-01 09:00:00','2026-05-15 09:00:00'),
(13112,1311,131,'e2',13112,13113,'复核通过','approve','AND',NULL,'2026-04-01 09:00:00','2026-05-15 09:00:00'),
(13113,1311,131,'e3',13113,13114,'同步完成','auto','AND',NULL,'2026-04-01 09:00:00','2026-05-15 09:00:00'),
(13121,1312,131,'e1',13121,13122,'提交','submit','AND',NULL,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13122,1312,131,'e2',13122,13123,'复核通过','approve','AND',NULL,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13123,1312,131,'e3',13123,13124,'同步归档完成','auto','AND',NULL,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13124,1312,131,'e4',13124,13125,'通知完成','auto','AND',NULL,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13211,1321,132,'e1',13211,13212,'触发通知','auto','AND',NULL,'2026-05-30 07:50:00','2026-05-30 08:10:00'),
(13212,1321,132,'e2',13212,13213,'通知完成','auto','AND',NULL,'2026-05-30 07:50:00','2026-05-30 08:10:00'),
(13311,1331,133,'e1',13311,13312,'提交','submit','AND',NULL,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13312,1331,133,'e2',13312,13313,'专用账户','approve','AND',NULL,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13313,1331,133,'e3',13312,13314,'普通账户','auto','AND',NULL,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13314,1331,133,'e4',13313,13314,'审批通过','approve','AND',NULL,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13315,1331,133,'e5',13314,13315,'账户创建完成','auto','AND',NULL,'2026-03-10 09:00:00','2026-05-31 18:00:00'),
(13316,1331,133,'e6',13315,13316,'通知完成','auto','AND',NULL,'2026-03-10 09:00:00','2026-05-31 18:00:00');

-- 条件规则
INSERT INTO `wf_edge_cond_rule`
(`id`, `edge_id`, `seq`, `field_code`, `operator`, `field_val`, `crte_time`, `updt_time`)
VALUES
(121121,12112,1,'creditRating','neq','AAA','2026-05-21 09:10:00','2026-05-21 10:30:00'),
(121131,12113,1,'creditRating','eq','AAA','2026-05-21 09:10:00','2026-05-21 10:30:00'),
(122131,12213,1,'auditStatus','eq','通过','2026-05-22 09:20:00','2026-05-22 11:00:00'),
(122141,12214,1,'auditStatus','eq','驳回','2026-05-22 09:20:00','2026-05-22 11:00:00'),
(123131,12313,1,'applyAmount','lte','50000000','2026-06-01 14:00:00','2026-06-01 16:20:00'),
(124121,12412,1,'applyAmount','gte','10000000','2026-05-24 09:00:00','2026-05-24 15:40:00'),
(124131,12413,1,'applyAmount','lt','10000000','2026-05-24 09:00:00','2026-05-24 15:40:00'),
(129221,12922,1,'applyAmount','gte','5000000','2026-06-02 09:00:00','2026-06-02 11:30:00'),
(129231,12923,1,'applyAmount','lt','5000000','2026-06-02 09:00:00','2026-06-02 11:30:00'),
(130121,13012,1,'creditRating','lte','AA','2026-05-29 09:40:00','2026-05-29 16:10:00'),
(130122,13012,2,'isRestricted','eq','是','2026-05-29 09:40:00','2026-05-29 16:10:00'),
(130131,13013,1,'creditRating','gt','AA','2026-05-29 09:40:00','2026-05-29 16:10:00'),
(130132,13013,2,'isRestricted','eq','否','2026-05-29 09:40:00','2026-05-29 16:10:00'),
(133121,13312,1,'investType','eq','专用账户','2026-03-10 09:00:00','2026-05-31 18:00:00'),
(133131,13313,1,'investType','neq','专用账户','2026-03-10 09:00:00','2026-05-31 18:00:00');

-- 条件节点配置
INSERT INTO `wf_node_condition_config`
(`id`, `node_id`, `condition_remark`, `crte_time`, `updt_time`)
SELECT n.id
      ,n.id
      ,CASE n.id
           WHEN 12112 THEN 'AAA 评级直通，其他评级进入信用研究复核'
           WHEN 12213 THEN '根据复核结果决定通知处置或驳回结束'
           WHEN 12313 THEN '草稿阶段暂按五千万元以内额度配置'
           WHEN 12412 THEN '一千万元及以上进入大额联合审批'
           WHEN 12922 THEN '五百万元及以上净值影响进入重大异常复核'
           WHEN 13012 THEN '评级不高于 AA 或已受限时进入观察名单审批'
           WHEN 13312 THEN '专用账户进入运营审批，普通账户直接创建'
       END
      ,n.crte_time
      ,n.updt_time
FROM `wf_flow_node` n
WHERE n.id IN (12112,12213,12313,12412,12922,13012,13312);

-- 审批节点配置
INSERT INTO `wf_node_approval_config`
(`id`, `node_id`, `approval_strategy`, `approval_remark`, `crte_time`, `updt_time`)
SELECT n.id
      ,n.id
      ,CASE
           WHEN n.id IN (12413,12712) THEN 'all'
           WHEN n.id IN (12812) THEN 'preempt'
           ELSE 'preempt'
       END
      ,CASE n.id
           WHEN 12113 THEN '由信用研究组任一人员完成准入复核'
           WHEN 12212 THEN '评级下调后由信用研究组抢占复核'
           WHEN 12312 THEN '临时额度由风控人员审批'
           WHEN 12413 THEN '基金经理和投资总监全部处理后方可通过'
           WHEN 12414 THEN '普通申购由基金经理任一人员复核'
           WHEN 12512 THEN '受限债券解禁由风控人员审批'
           WHEN 12612 THEN '另类投资项目由投资总监备案审批'
           WHEN 12712 THEN '投资和风控部门全部处理'
           WHEN 12713 THEN '运营人员复核材料完整性'
           WHEN 12812 THEN '紧急事件由任一风控人员抢占处理'
           WHEN 12912 THEN '基金净值异常由基金经理复核'
           WHEN 12923 THEN '重大净值异常由投资总监复核'
           WHEN 13013 THEN '发行人观察名单由信用研究组审批'
           WHEN 13112 THEN '历史版本的运营复核节点'
           WHEN 13122 THEN '当前版本的运营复核节点'
           WHEN 13313 THEN '已删除流程的账户开立审批节点'
       END
      ,n.crte_time
      ,n.updt_time
FROM `wf_flow_node` n
WHERE n.id IN (12113,12212,12312,12413,12414,12512,12612,12712,12713,12812,12912,12923,13013,13112,13122,13313);

-- 审批处理人：引用现有角色和用户
INSERT INTO `wf_node_approval_handler`
(`id`, `approval_config_id`, `subject_type`, `subject_id`, `subject_name`, `sort_order`, `crte_time`, `updt_time`)
VALUES
(121131,12113,'role',2,'信用研究组',1,'2026-05-21 09:10:00','2026-05-21 10:30:00'),
(122121,12212,'role',2,'信用研究组',1,'2026-05-22 09:20:00','2026-05-22 11:00:00'),
(123121,12312,'role',3,'风险管理部',1,'2026-06-01 14:00:00','2026-06-01 16:20:00'),
(124131,12413,'role',1,'基金经理',1,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(124132,12413,'user',2,'叶伟',2,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(124141,12414,'role',1,'基金经理',1,'2026-05-24 09:00:00','2026-05-24 15:40:00'),
(125121,12512,'role',3,'风险管理部',1,'2026-04-18 10:00:00','2026-05-25 17:30:00'),
(126121,12612,'user',2,'叶伟',1,'2026-05-26 09:30:00','2026-05-26 12:00:00'),
(127121,12712,'role',4,'固收部',1,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(127122,12712,'role',3,'风险管理部',2,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(127131,12713,'user',1001,'管理员',1,'2026-05-27 09:15:00','2026-05-27 14:20:00'),
(128121,12812,'role',3,'风险管理部',1,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(128122,12812,'user',2,'叶伟',2,'2026-05-28 08:30:00','2026-05-28 09:45:00'),
(129121,12912,'role',1,'基金经理',1,'2026-05-10 10:00:00','2026-05-10 15:00:00'),
(129231,12923,'user',2,'叶伟',1,'2026-06-02 09:00:00','2026-06-02 11:30:00'),
(130131,13013,'role',2,'信用研究组',1,'2026-05-29 09:40:00','2026-05-29 16:10:00'),
(131121,13112,'user',1001,'管理员',1,'2026-04-01 09:00:00','2026-05-15 09:00:00'),
(131221,13122,'user',1001,'管理员',1,'2026-06-03 09:00:00','2026-06-03 10:20:00'),
(133131,13313,'role',6,'运营管理部',1,'2026-03-10 09:00:00','2026-05-31 18:00:00');

-- 自动节点任务配置
INSERT INTO `wf_node_auto_config`
(`id`, `node_id`, `task_seq`, `task_code`, `auto_remark`, `crte_time`, `updt_time`)
VALUES
(121141,12114,1,'riskCheck','准入通过后执行风控检查并归档。','2026-05-21 09:10:00','2026-05-21 10:30:00'),
(121142,12114,2,'archiveRecord','准入通过后执行风控检查并归档。','2026-05-21 09:10:00','2026-05-21 10:30:00'),
(124151,12415,1,'updatePosition','申购审批通过后更新持仓并同步清算。','2026-05-24 09:00:00','2026-05-24 15:40:00'),
(124152,12415,2,'syncSettlement','申购审批通过后更新持仓并同步清算。','2026-05-24 09:00:00','2026-05-24 15:40:00'),
(125131,12513,1,'updatePosition','解除限制后同步持仓状态。','2026-04-18 10:00:00','2026-05-25 17:30:00'),
(128131,12813,1,'riskCheck','紧急处置先执行风控检查，再更新持仓。','2026-05-28 08:30:00','2026-05-28 09:45:00'),
(128132,12813,2,'updatePosition','紧急处置先执行风控检查，再更新持仓。','2026-05-28 08:30:00','2026-05-28 09:45:00'),
(131131,13113,1,'syncSettlement','历史版本仅同步订正后的业务数据。','2026-04-01 09:00:00','2026-05-15 09:00:00'),
(131231,13123,1,'syncSettlement','当前版本同步数据后归档订正记录。','2026-06-03 09:00:00','2026-06-03 10:20:00'),
(131232,13123,2,'archiveRecord','当前版本同步数据后归档订正记录。','2026-06-03 09:00:00','2026-06-03 10:20:00'),
(133141,13314,1,'createAccount','创建投资账户后发送内部通知。','2026-03-10 09:00:00','2026-05-31 18:00:00'),
(133142,13314,2,'sendNotify','创建投资账户后发送内部通知。','2026-03-10 09:00:00','2026-05-31 18:00:00');

-- 通知节点配置
INSERT INTO `wf_node_notify_config`
(`id`, `node_id`, `notify_channels`, `notify_target`, `notify_persons`, `notify_tpl`, `notify_remark`, `crte_time`,
 `updt_time`)
VALUES
(12214,12214,JSON_ARRAY('system','email'),'person',JSON_ARRAY('risk-officer','invest-director'),
 '债券【#{procName}】评级下调处置已进入执行阶段。','通知风控人员和投资总监。','2026-05-22 09:20:00','2026-05-22 11:00:00'),
(12613,12613,JSON_ARRAY('system'),'initiator',JSON_ARRAY(),
 '另类投资备案【#{procName}】已审批完成。','通知流程发起人备案结果。','2026-05-26 09:30:00','2026-05-26 12:00:00'),
(12714,12714,JSON_ARRAY('system','email'),'initiator',JSON_ARRAY(),
 '跨部门联合评审【#{procName}】已完成。','通知发起人联合评审结果。','2026-05-27 09:15:00','2026-05-27 14:20:00'),
(12814,12814,JSON_ARRAY('system','email'),'person',JSON_ARRAY('risk-officer','fund-manager'),
 '紧急风险处置【#{procName}】已执行完成。','通知风控人员和基金经理。','2026-05-28 08:30:00','2026-05-28 09:45:00'),
(12913,12913,JSON_ARRAY('system'),'initiator',JSON_ARRAY(),
 '基金净值异常复核【#{procName}】已完成。','v1 通知发起人。','2026-05-10 10:00:00','2026-05-10 15:00:00'),
(12924,12924,JSON_ARRAY('system','email'),'initiator',JSON_ARRAY(),
 '基金净值异常分级复核【#{procName}】已完成。','v2 草稿增加邮件渠道。','2026-06-02 09:00:00','2026-06-02 11:30:00'),
(13014,13014,JSON_ARRAY('system','email'),'person',JSON_ARRAY('credit-research','risk-officer'),
 '发行人【#{procName}】已纳入观察名单。','通知信用研究和风控人员。','2026-05-29 09:40:00','2026-05-29 16:10:00'),
(13124,13124,JSON_ARRAY('system'),'initiator',JSON_ARRAY(),
 '业务数据订正【#{procName}】已同步并归档。','v2 新增完成通知。','2026-06-03 09:00:00','2026-06-03 10:20:00'),
(13212,13212,JSON_ARRAY('system','email','sms'),'person',JSON_ARRAY('fund-manager','risk-officer','invest-director'),
 '市场重大事件【#{procName}】请及时关注并评估影响。','多渠道通知基金经理、风控和投资总监。','2026-05-30 07:50:00','2026-05-30 08:10:00'),
(13315,13315,JSON_ARRAY('system','email'),'initiator',JSON_ARRAY(),
 '账户开立流程【#{procName}】已处理完成。','已删除流程保留的通知配置。','2026-03-10 09:00:00','2026-05-31 18:00:00');

-- 根据归一化表生成版本画布 JSON，确保设计器快照与关系表一致
UPDATE `wf_flow_version` v
SET v.canvas_nodes = (
        SELECT JSON_ARRAYAGG(
                   JSON_OBJECT(
                       'id', n.node_id
                       ,'type', n.node_type
                       ,'label', n.label
                       ,'x', n.pos_x
                       ,'y', n.pos_y
                       ,'shape', n.shape
                       ,'sub', n.sub_label
                       ,'approvalStrategy', ac.approval_strategy
                       ,'approvalPersons', COALESCE((
                           SELECT JSON_ARRAYAGG(JSON_OBJECT(
                               'subjectType', ah.subject_type
                               ,'subjectId', ah.subject_id
                               ,'subjectName', ah.subject_name
                           ))
                           FROM `wf_node_approval_handler` ah
                           WHERE ah.approval_config_id = ac.id
                       ), JSON_ARRAY())
                       ,'approvalRemark', ac.approval_remark
                       ,'autoTasks', COALESCE((
                           SELECT JSON_ARRAYAGG(JSON_OBJECT('task', au.task_code))
                           FROM `wf_node_auto_config` au
                           WHERE au.node_id = n.id
                       ), JSON_ARRAY())
                       ,'autoRemark', (
                           SELECT MAX(au.auto_remark)
                           FROM `wf_node_auto_config` au
                           WHERE au.node_id = n.id
                       )
                       ,'notifyChannels', nc.notify_channels
                       ,'notifyTarget', nc.notify_target
                       ,'notifyPersons', nc.notify_persons
                       ,'notifyTpl', nc.notify_tpl
                       ,'notifyRemark', nc.notify_remark
                       ,'conditionRemark', cc.condition_remark
                   )
               )
        FROM `wf_flow_node` n
        LEFT JOIN `wf_node_approval_config` ac ON ac.node_id = n.id
        LEFT JOIN `wf_node_notify_config` nc ON nc.node_id = n.id
        LEFT JOIN `wf_node_condition_config` cc ON cc.node_id = n.id
        WHERE n.version_id = v.id
    )
   ,v.canvas_edges = (
        SELECT JSON_ARRAYAGG(
                   JSON_OBJECT(
                       'id', e.edge_id
                       ,'from', fn.node_id
                       ,'to', tn.node_id
                       ,'label', e.label
                       ,'routeAction', e.route_action
                       ,'condLogic', e.cond_logic
                       ,'remark', e.remark
                       ,'condRules', COALESCE((
                           SELECT JSON_ARRAYAGG(JSON_OBJECT(
                               'field', r.field_code
                               ,'op', r.operator
                               ,'val', r.field_val
                           ))
                           FROM `wf_edge_cond_rule` r
                           WHERE r.edge_id = e.id
                       ), JSON_ARRAY())
                   )
               )
        FROM `wf_flow_edge` e
        INNER JOIN `wf_flow_node` fn ON fn.id = e.from_node_id
        INNER JOIN `wf_flow_node` tn ON tn.id = e.to_node_id
        WHERE e.version_id = v.id
    )
WHERE v.flow_id BETWEEN 121 AND 133;


-- ============================================================================
-- 6. 事件表保持为空
--    事件数据由后续新增、编辑、发布、停用和删除等真实业务操作产生
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 1;

-- 初始化结果校验：正常流程 32 条，逻辑删除流程 1 条
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
WHERE v.flow_id BETWEEN 121 AND 133
GROUP BY v.id
HAVING snapshot_node_count != normalized_node_count
    OR snapshot_edge_count != normalized_edge_count;

SELECT `flow_id`, `ver_num`, `status`, `publish_note`, `published_time`
FROM `wf_flow_version`
WHERE `flow_id` IN (129, 131)
ORDER BY `flow_id`, `ver_num`;

-- 13 条扩展流程必须都有版本、节点和连线
SELECT d.id AS incomplete_flow_id
FROM `wf_flow_definition` d
WHERE d.id BETWEEN 121 AND 133
  AND (
      NOT EXISTS (SELECT 1 FROM `wf_flow_version` v WHERE v.flow_id = d.id)
      OR NOT EXISTS (SELECT 1 FROM `wf_flow_node` n WHERE n.flow_id = d.id)
      OR NOT EXISTS (SELECT 1 FROM `wf_flow_edge` e WHERE e.flow_id = d.id)
  );

-- 六种自动任务均应被覆盖
SELECT expected.task_code AS missing_task_code
FROM (
    SELECT 'createAccount' AS task_code
    UNION ALL SELECT 'updatePosition'
    UNION ALL SELECT 'syncSettlement'
    UNION ALL SELECT 'riskCheck'
    UNION ALL SELECT 'sendNotify'
    UNION ALL SELECT 'archiveRecord'
) expected
LEFT JOIN `wf_node_auto_config` actual ON actual.task_code = expected.task_code
WHERE actual.id IS NULL;

-- 六种条件比较运算符均应被覆盖
SELECT expected.operator AS missing_operator
FROM (
    SELECT 'eq' AS operator
    UNION ALL SELECT 'neq'
    UNION ALL SELECT 'gt'
    UNION ALL SELECT 'lt'
    UNION ALL SELECT 'gte'
    UNION ALL SELECT 'lte'
) expected
LEFT JOIN `wf_edge_cond_rule` actual ON actual.operator = expected.operator
WHERE actual.id IS NULL;

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
