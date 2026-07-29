-- =============================================================================
-- 工作流平台 — 流程定义演示数据（按业务口径重排）
-- 目标库：znty_rrs
-- 兼容：  MySQL 8.0.28
-- 说明：
--   共 15 条：债券 13 + 主体(禁止库) 2
--   标准+O32 / 直通 / 禁止库(投决会=auto自动审批) / 特殊策略(三节点审批+auto)
--   处理策略：preempt / initiator / o32 / auto（自动审批，系统自动通过）
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

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
       (10, 'o32-system', 'O32系统自动审批', 10, 1, NOW(), NOW()),
       (11, 'invest-committee', '投资决策委员会', 11, 1, NOW(), NOW());

INSERT INTO `wf_flow_definition`
(`id`, `name`, `flow_key`, `category`, `description`, `remark`, `status`, `created_by`, `updated_by`, `is_deleted`, `crte_time`, `updt_time`)
VALUES
(101, '债券标准升库流程', 'bond:standard-upgrade', 'bond', '债券标准升库审批：研究员A发起→研究员B复核→研究总监审批→O32自动审批', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102, '债券标准降库流程', 'bond:standard-downgrade', 'bond', '债券标准降库审批：研究员A发起→研究员B复核→研究总监审批→O32自动审批', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103, '债券一般入库流程', 'bond:normal-inbound', 'bond', '债券一般入库审批：研究员A发起→研究员B复核→研究总监审批→O32自动审批', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104, '债券一般出库流程', 'bond:normal-outbound', 'bond', '债券一般出库审批：研究员A发起→研究员B复核→研究总监审批→O32自动审批', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(105, '债券快速入库流程', 'bond:fast-inbound', 'bond', '债券快速入库：研究员A发起后直接入库', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(106, '债券快速出库流程', 'bond:fast-outbound', 'bond', '债券快速出库：研究员A发起后直接出库', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(107, '债券批量入库流程', 'bond:batch-inbound', 'bond', '债券批量入库：研究员A发起后批量直接入库', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108, '债券批量出库流程', 'bond:batch-outbound', 'bond', '债券批量出库：研究员A发起后批量直接出库', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109, '债券特殊策略入库流程', 'bond:special-inbound', 'bond', '债券特殊策略入库：研究员A发起→基金经理/投资经理意向(自动审批)→信用研究建议(自动审批)→投决会(自动审批)', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110, '债券特殊策略出库流程', 'bond:special-outbound', 'bond', '债券特殊策略出库：研究员A发起→基金经理/投资经理意向(自动审批)→信用研究建议(自动审批)→投决会(自动审批)', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(111, '债券白名单入库流程', 'bond:whitelist-inbound', 'bond', '债券白名单入库：研究员A发起后直接入库', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112, '无需审核审批流程', 'bond:no-approval', 'bond', '无需审核审批：研究员A发起后直接完成', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113, '禁止库入库流程', 'company:forbidden-inbound', 'company', '禁止库入库：研究员A发起→研究员B复核→研究总监审批→投资决策委员会自动审批', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114, '禁止库出库流程', 'company:forbidden-outbound', 'company', '禁止库出库：研究员A发起→研究员B复核→研究总监审批→投资决策委员会自动审批', '', 'active', 1, 1, 0, '2026-05-20 09:00:00', '2026-05-20 09:00:00');

INSERT INTO `wf_flow_version`
(`id`, `flow_id`, `flow_key`, `ver_num`, `status`, `publish_note`, `canvas_nodes`, `canvas_edges`,
 `canvas_pan_x`, `canvas_pan_y`, `canvas_zoom`, `published_by`, `published_time`, `created_by`, `crte_time`, `updt_time`)
VALUES
(101, 101, 'bond:standard-upgrade', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":28,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":130,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"approval","label":"研究员B复核","x":316,"y":260,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":5,"handlerName":"研究员4"}],"approvalRemark":"复核节点，抢占审批，任一处理人通过即可"},{"id":"n104","type":"approval","label":"研究员A修改","x":576,"y":260,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交，也可终止流程"},{"id":"n105","type":"approval","label":"研究总监审批","x":316,"y":390,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"研究总监"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"研究总监抢占审批，驳回则结束"},{"id":"n106","type":"approval","label":"O32自动审批","x":316,"y":520,"shape":"rect","approvalStrategy":"o32","approvalPersons":[{"handlerType":"role","handlerId":10,"handlerName":"O32系统自动审批"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"O32系统自动审批，非临时代码系统代审，临时代码转人工"},{"id":"n107","type":"end","label":"结束","x":620,"y":530,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"提交","condRules":[],"condLogic":"AND","routeAction":"submit"},{"id":"e3","from":"n103","to":"n104","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n104","to":"n103","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"resubmit"},{"id":"e5","from":"n103","to":"n105","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n105","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n105","to":"n106","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n104","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n106","to":"n107","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102, 102, 'bond:standard-downgrade', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":28,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":130,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"approval","label":"研究员B复核","x":316,"y":260,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":5,"handlerName":"研究员4"}],"approvalRemark":"复核节点，抢占审批，任一处理人通过即可"},{"id":"n104","type":"approval","label":"研究员A修改","x":576,"y":260,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交，也可终止流程"},{"id":"n105","type":"approval","label":"研究总监审批","x":316,"y":390,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"研究总监"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"研究总监抢占审批，驳回则结束"},{"id":"n106","type":"approval","label":"O32自动审批","x":316,"y":520,"shape":"rect","approvalStrategy":"o32","approvalPersons":[{"handlerType":"role","handlerId":10,"handlerName":"O32系统自动审批"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"O32系统自动审批，非临时代码系统代审，临时代码转人工"},{"id":"n107","type":"end","label":"结束","x":620,"y":530,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"提交","condRules":[],"condLogic":"AND","routeAction":"submit"},{"id":"e3","from":"n103","to":"n104","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n104","to":"n103","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"resubmit"},{"id":"e5","from":"n103","to":"n105","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n105","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n105","to":"n106","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n104","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n106","to":"n107","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103, 103, 'bond:normal-inbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":28,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":130,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"approval","label":"研究员B复核","x":316,"y":260,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":5,"handlerName":"研究员4"}],"approvalRemark":"复核节点，抢占审批，任一处理人通过即可"},{"id":"n104","type":"approval","label":"研究员A修改","x":576,"y":260,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交，也可终止流程"},{"id":"n105","type":"approval","label":"研究总监审批","x":316,"y":390,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"研究总监"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"研究总监抢占审批，驳回则结束"},{"id":"n106","type":"approval","label":"O32自动审批","x":316,"y":520,"shape":"rect","approvalStrategy":"o32","approvalPersons":[{"handlerType":"role","handlerId":10,"handlerName":"O32系统自动审批"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"O32系统自动审批，非临时代码系统代审，临时代码转人工"},{"id":"n107","type":"end","label":"结束","x":620,"y":530,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"提交","condRules":[],"condLogic":"AND","routeAction":"submit"},{"id":"e3","from":"n103","to":"n104","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n104","to":"n103","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"resubmit"},{"id":"e5","from":"n103","to":"n105","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n105","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n105","to":"n106","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n104","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n106","to":"n107","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104, 104, 'bond:normal-outbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":28,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":130,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"approval","label":"研究员B复核","x":316,"y":260,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":5,"handlerName":"研究员4"}],"approvalRemark":"复核节点，抢占审批，任一处理人通过即可"},{"id":"n104","type":"approval","label":"研究员A修改","x":576,"y":260,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交，也可终止流程"},{"id":"n105","type":"approval","label":"研究总监审批","x":316,"y":390,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"研究总监"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"研究总监抢占审批，驳回则结束"},{"id":"n106","type":"approval","label":"O32自动审批","x":316,"y":520,"shape":"rect","approvalStrategy":"o32","approvalPersons":[{"handlerType":"role","handlerId":10,"handlerName":"O32系统自动审批"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"O32系统自动审批，非临时代码系统代审，临时代码转人工"},{"id":"n107","type":"end","label":"结束","x":620,"y":530,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"提交","condRules":[],"condLogic":"AND","routeAction":"submit"},{"id":"e3","from":"n103","to":"n104","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n104","to":"n103","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"resubmit"},{"id":"e5","from":"n103","to":"n105","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n105","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n105","to":"n106","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n104","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e9","from":"n106","to":"n107","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(105, 105, 'bond:fast-inbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":40,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"end","label":"结束","x":360,"y":320,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"直接入库","condRules":[],"condLogic":"AND","routeAction":"submit"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(106, 106, 'bond:fast-outbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":40,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"end","label":"结束","x":360,"y":320,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"直接出库","condRules":[],"condLogic":"AND","routeAction":"submit"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(107, 107, 'bond:batch-inbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":40,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"end","label":"结束","x":360,"y":320,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"批量入库","condRules":[],"condLogic":"AND","routeAction":"submit"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108, 108, 'bond:batch-outbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":40,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"end","label":"结束","x":360,"y":320,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"批量出库","condRules":[],"condLogic":"AND","routeAction":"submit"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109, 109, 'bond:special-inbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":28,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":130,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"approval","label":"基金经理/投资经理提出投资意向","x":316,"y":260,"shape":"rect","sub":"fund-manager","approvalStrategy":"auto","approvalPersons":[{"handlerType":"role","handlerId":1,"handlerName":"基金经理"}],"approvalRemark":"自动审批：基金经理/投资经理提出投资意向，系统自动通过"},{"id":"n104","type":"approval","label":"信用研究建议","x":316,"y":390,"shape":"rect","sub":"credit-research","approvalStrategy":"auto","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"}],"approvalRemark":"自动审批：信用研究建议，系统自动通过"},{"id":"n105","type":"approval","label":"投资决策委员会审批","x":316,"y":520,"shape":"rect","sub":"invest-committee","approvalStrategy":"auto","approvalPersons":[{"handlerType":"role","handlerId":11,"handlerName":"投资决策委员会"}],"approvalRemark":"自动审批：投资决策委员会审批，系统自动通过"},{"id":"n106","type":"end","label":"结束","x":360,"y":670,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"提交","condRules":[],"condLogic":"AND","routeAction":"submit"},{"id":"e3","from":"n103","to":"n104","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e4","from":"n104","to":"n105","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e5","from":"n105","to":"n106","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110, 110, 'bond:special-outbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":28,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":130,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"approval","label":"基金经理/投资经理提出投资意向","x":316,"y":260,"shape":"rect","sub":"fund-manager","approvalStrategy":"auto","approvalPersons":[{"handlerType":"role","handlerId":1,"handlerName":"基金经理"}],"approvalRemark":"自动审批：基金经理/投资经理提出投资意向，系统自动通过"},{"id":"n104","type":"approval","label":"信用研究建议","x":316,"y":390,"shape":"rect","sub":"credit-research","approvalStrategy":"auto","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"}],"approvalRemark":"自动审批：信用研究建议，系统自动通过"},{"id":"n105","type":"approval","label":"投资决策委员会审批","x":316,"y":520,"shape":"rect","sub":"invest-committee","approvalStrategy":"auto","approvalPersons":[{"handlerType":"role","handlerId":11,"handlerName":"投资决策委员会"}],"approvalRemark":"自动审批：投资决策委员会审批，系统自动通过"},{"id":"n106","type":"end","label":"结束","x":360,"y":670,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"提交","condRules":[],"condLogic":"AND","routeAction":"submit"},{"id":"e3","from":"n103","to":"n104","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e4","from":"n104","to":"n105","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e5","from":"n105","to":"n106","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(111, 111, 'bond:whitelist-inbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":40,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"end","label":"结束","x":360,"y":320,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"直接入库","condRules":[],"condLogic":"AND","routeAction":"submit"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112, 112, 'bond:no-approval', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":40,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":160,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"end","label":"结束","x":360,"y":320,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"直接完成","condRules":[],"condLogic":"AND","routeAction":"submit"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113, 113, 'company:forbidden-inbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":28,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":130,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"approval","label":"研究员B复核","x":316,"y":260,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":5,"handlerName":"研究员4"}],"approvalRemark":"复核节点，抢占审批"},{"id":"n104","type":"approval","label":"研究员A修改","x":576,"y":260,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交，也可终止流程"},{"id":"n105","type":"approval","label":"研究总监审批","x":316,"y":390,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"研究总监"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"研究总监抢占审批，驳回则结束"},{"id":"n106","type":"approval","label":"投资决策委员会审批","x":316,"y":520,"shape":"rect","sub":"invest-committee","approvalStrategy":"auto","approvalPersons":[{"handlerType":"role","handlerId":11,"handlerName":"投资决策委员会"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"自动审批：投资决策委员会，系统自动通过"},{"id":"n107","type":"end","label":"结束","x":620,"y":530,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"提交","condRules":[],"condLogic":"AND","routeAction":"submit"},{"id":"e3","from":"n103","to":"n104","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n104","to":"n103","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"resubmit"},{"id":"e5","from":"n103","to":"n105","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n105","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n105","to":"n106","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n106","to":"n107","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e9","from":"n104","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114, 114, 'company:forbidden-outbound', 1, 'active', '初始版本', '[{"id":"n101","type":"start","label":"开始","x":360,"y":28,"shape":"circle"},{"id":"n102","type":"approval","label":"研究员A发起","x":316,"y":130,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"由流程发起人提交，系统自动完成该节点"},{"id":"n103","type":"approval","label":"研究员B复核","x":316,"y":260,"shape":"rect","sub":"researcher-b","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":2,"handlerName":"信用研究组"},{"handlerType":"user","handlerId":5,"handlerName":"研究员4"}],"approvalRemark":"复核节点，抢占审批"},{"id":"n104","type":"approval","label":"研究员A修改","x":576,"y":260,"shape":"rect","sub":"researcher-a","approvalStrategy":"initiator","approvalPersons":[],"approvalRemark":"驳回后由流程发起人修改并重新提交，也可终止流程"},{"id":"n105","type":"approval","label":"研究总监审批","x":316,"y":390,"shape":"rect","sub":"research-director","approvalStrategy":"preempt","approvalPersons":[{"handlerType":"role","handlerId":9,"handlerName":"研究总监"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"研究总监抢占审批，驳回则结束"},{"id":"n106","type":"approval","label":"投资决策委员会审批","x":316,"y":520,"shape":"rect","sub":"invest-committee","approvalStrategy":"auto","approvalPersons":[{"handlerType":"role","handlerId":11,"handlerName":"投资决策委员会"},{"handlerType":"user","handlerId":1,"handlerName":"管理员"}],"approvalRemark":"自动审批：投资决策委员会，系统自动通过"},{"id":"n107","type":"end","label":"结束","x":620,"y":530,"shape":"circle"}]', '[{"id":"e1","from":"n101","to":"n102","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e2","from":"n102","to":"n103","label":"提交","condRules":[],"condLogic":"AND","routeAction":"submit"},{"id":"e3","from":"n103","to":"n104","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e4","from":"n104","to":"n103","label":"重新提交","condRules":[],"condLogic":"AND","routeAction":"resubmit"},{"id":"e5","from":"n103","to":"n105","label":"复核通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e6","from":"n105","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"},{"id":"e7","from":"n105","to":"n106","label":"通过","condRules":[{"field":"auditStatus","op":"eq","val":"通过"}],"condLogic":"AND","routeAction":"approve"},{"id":"e8","from":"n106","to":"n107","label":"","condRules":[],"condLogic":"AND","routeAction":"auto"},{"id":"e9","from":"n104","to":"n107","label":"驳回","condRules":[{"field":"auditStatus","op":"eq","val":"驳回"}],"condLogic":"AND","routeAction":"reject"}]', 0, 0, 1, 1, '2026-05-20 09:00:00', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00');

INSERT INTO `wf_flow_node`
(`id`, `version_id`, `flow_id`, `node_id`, `node_type`, `label`, `shape`, `pos_x`, `pos_y`, `sub_label`, `sort_order`, `crte_time`, `updt_time`)
VALUES
(10101, 101, 101, 'n101', 'start', '开始', 'circle', 360, 28, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10102, 101, 101, 'n102', 'approval', '研究员A发起', 'rect', 316, 130, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10103, 101, 101, 'n103', 'approval', '研究员B复核', 'rect', 316, 260, 'researcher-b', 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10104, 101, 101, 'n104', 'approval', '研究员A修改', 'rect', 576, 260, 'researcher-a', 4, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10105, 101, 101, 'n105', 'approval', '研究总监审批', 'rect', 316, 390, 'research-director', 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10106, 101, 101, 'n106', 'approval', 'O32自动审批', 'rect', 316, 520, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10107, 101, 101, 'n107', 'end', '结束', 'circle', 620, 530, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10201, 102, 102, 'n101', 'start', '开始', 'circle', 360, 28, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10202, 102, 102, 'n102', 'approval', '研究员A发起', 'rect', 316, 130, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10203, 102, 102, 'n103', 'approval', '研究员B复核', 'rect', 316, 260, 'researcher-b', 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10204, 102, 102, 'n104', 'approval', '研究员A修改', 'rect', 576, 260, 'researcher-a', 4, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10205, 102, 102, 'n105', 'approval', '研究总监审批', 'rect', 316, 390, 'research-director', 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10206, 102, 102, 'n106', 'approval', 'O32自动审批', 'rect', 316, 520, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10207, 102, 102, 'n107', 'end', '结束', 'circle', 620, 530, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10301, 103, 103, 'n101', 'start', '开始', 'circle', 360, 28, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10302, 103, 103, 'n102', 'approval', '研究员A发起', 'rect', 316, 130, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10303, 103, 103, 'n103', 'approval', '研究员B复核', 'rect', 316, 260, 'researcher-b', 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10304, 103, 103, 'n104', 'approval', '研究员A修改', 'rect', 576, 260, 'researcher-a', 4, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10305, 103, 103, 'n105', 'approval', '研究总监审批', 'rect', 316, 390, 'research-director', 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10306, 103, 103, 'n106', 'approval', 'O32自动审批', 'rect', 316, 520, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10307, 103, 103, 'n107', 'end', '结束', 'circle', 620, 530, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10401, 104, 104, 'n101', 'start', '开始', 'circle', 360, 28, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10402, 104, 104, 'n102', 'approval', '研究员A发起', 'rect', 316, 130, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10403, 104, 104, 'n103', 'approval', '研究员B复核', 'rect', 316, 260, 'researcher-b', 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10404, 104, 104, 'n104', 'approval', '研究员A修改', 'rect', 576, 260, 'researcher-a', 4, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10405, 104, 104, 'n105', 'approval', '研究总监审批', 'rect', 316, 390, 'research-director', 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10406, 104, 104, 'n106', 'approval', 'O32自动审批', 'rect', 316, 520, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10407, 104, 104, 'n107', 'end', '结束', 'circle', 620, 530, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10501, 105, 105, 'n101', 'start', '开始', 'circle', 360, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10502, 105, 105, 'n102', 'approval', '研究员A发起', 'rect', 316, 160, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10503, 105, 105, 'n103', 'end', '结束', 'circle', 360, 320, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10601, 106, 106, 'n101', 'start', '开始', 'circle', 360, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10602, 106, 106, 'n102', 'approval', '研究员A发起', 'rect', 316, 160, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10603, 106, 106, 'n103', 'end', '结束', 'circle', 360, 320, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10701, 107, 107, 'n101', 'start', '开始', 'circle', 360, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10702, 107, 107, 'n102', 'approval', '研究员A发起', 'rect', 316, 160, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10703, 107, 107, 'n103', 'end', '结束', 'circle', 360, 320, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10801, 108, 108, 'n101', 'start', '开始', 'circle', 360, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10802, 108, 108, 'n102', 'approval', '研究员A发起', 'rect', 316, 160, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10803, 108, 108, 'n103', 'end', '结束', 'circle', 360, 320, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10901, 109, 109, 'n101', 'start', '开始', 'circle', 360, 28, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10902, 109, 109, 'n102', 'approval', '研究员A发起', 'rect', 316, 130, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10903, 109, 109, 'n103', 'approval', '基金经理/投资经理提出投资意向', 'rect', 316, 260, 'fund-manager', 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10904, 109, 109, 'n104', 'approval', '信用研究建议', 'rect', 316, 390, 'credit-research', 4, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10905, 109, 109, 'n105', 'approval', '投资决策委员会审批', 'rect', 316, 520, 'invest-committee', 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10906, 109, 109, 'n106', 'end', '结束', 'circle', 360, 670, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11001, 110, 110, 'n101', 'start', '开始', 'circle', 360, 28, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11002, 110, 110, 'n102', 'approval', '研究员A发起', 'rect', 316, 130, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11003, 110, 110, 'n103', 'approval', '基金经理/投资经理提出投资意向', 'rect', 316, 260, 'fund-manager', 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11004, 110, 110, 'n104', 'approval', '信用研究建议', 'rect', 316, 390, 'credit-research', 4, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11005, 110, 110, 'n105', 'approval', '投资决策委员会审批', 'rect', 316, 520, 'invest-committee', 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11006, 110, 110, 'n106', 'end', '结束', 'circle', 360, 670, NULL, 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11101, 111, 111, 'n101', 'start', '开始', 'circle', 360, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11102, 111, 111, 'n102', 'approval', '研究员A发起', 'rect', 316, 160, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11103, 111, 111, 'n103', 'end', '结束', 'circle', 360, 320, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11201, 112, 112, 'n101', 'start', '开始', 'circle', 360, 40, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11202, 112, 112, 'n102', 'approval', '研究员A发起', 'rect', 316, 160, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11203, 112, 112, 'n103', 'end', '结束', 'circle', 360, 320, NULL, 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11301, 113, 113, 'n101', 'start', '开始', 'circle', 360, 28, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11302, 113, 113, 'n102', 'approval', '研究员A发起', 'rect', 316, 130, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11303, 113, 113, 'n103', 'approval', '研究员B复核', 'rect', 316, 260, 'researcher-b', 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11304, 113, 113, 'n104', 'approval', '研究员A修改', 'rect', 576, 260, 'researcher-a', 4, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11305, 113, 113, 'n105', 'approval', '研究总监审批', 'rect', 316, 390, 'research-director', 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11306, 113, 113, 'n106', 'approval', '投资决策委员会审批', 'rect', 316, 520, 'invest-committee', 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11307, 113, 113, 'n107', 'end', '结束', 'circle', 620, 530, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11401, 114, 114, 'n101', 'start', '开始', 'circle', 360, 28, NULL, 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11402, 114, 114, 'n102', 'approval', '研究员A发起', 'rect', 316, 130, 'researcher-a', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11403, 114, 114, 'n103', 'approval', '研究员B复核', 'rect', 316, 260, 'researcher-b', 3, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11404, 114, 114, 'n104', 'approval', '研究员A修改', 'rect', 576, 260, 'researcher-a', 4, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11405, 114, 114, 'n105', 'approval', '研究总监审批', 'rect', 316, 390, 'research-director', 5, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11406, 114, 114, 'n106', 'approval', '投资决策委员会审批', 'rect', 316, 520, 'invest-committee', 6, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11407, 114, 114, 'n107', 'end', '结束', 'circle', 620, 530, NULL, 7, '2026-05-20 09:00:00', '2026-05-20 09:00:00');

INSERT INTO `wf_flow_edge`
(`id`, `version_id`, `flow_id`, `edge_id`, `from_node_id`, `to_node_id`, `label`, `route_action`, `cond_logic`, `remark`, `crte_time`, `updt_time`)
VALUES
(101001, 101, 101, 'e1', 10101, 10102, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101002, 101, 101, 'e2', 10102, 10103, '提交', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101003, 101, 101, 'e3', 10103, 10104, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101004, 101, 101, 'e4', 10104, 10103, '重新提交', 'resubmit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101005, 101, 101, 'e5', 10103, 10105, '复核通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101006, 101, 101, 'e6', 10105, 10107, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101007, 101, 101, 'e7', 10105, 10106, '通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101008, 101, 101, 'e8', 10104, 10107, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101009, 101, 101, 'e9', 10106, 10107, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102001, 102, 102, 'e1', 10201, 10202, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102002, 102, 102, 'e2', 10202, 10203, '提交', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102003, 102, 102, 'e3', 10203, 10204, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102004, 102, 102, 'e4', 10204, 10203, '重新提交', 'resubmit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102005, 102, 102, 'e5', 10203, 10205, '复核通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102006, 102, 102, 'e6', 10205, 10207, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102007, 102, 102, 'e7', 10205, 10206, '通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102008, 102, 102, 'e8', 10204, 10207, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102009, 102, 102, 'e9', 10206, 10207, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103001, 103, 103, 'e1', 10301, 10302, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103002, 103, 103, 'e2', 10302, 10303, '提交', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103003, 103, 103, 'e3', 10303, 10304, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103004, 103, 103, 'e4', 10304, 10303, '重新提交', 'resubmit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103005, 103, 103, 'e5', 10303, 10305, '复核通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103006, 103, 103, 'e6', 10305, 10307, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103007, 103, 103, 'e7', 10305, 10306, '通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103008, 103, 103, 'e8', 10304, 10307, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103009, 103, 103, 'e9', 10306, 10307, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104001, 104, 104, 'e1', 10401, 10402, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104002, 104, 104, 'e2', 10402, 10403, '提交', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104003, 104, 104, 'e3', 10403, 10404, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104004, 104, 104, 'e4', 10404, 10403, '重新提交', 'resubmit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104005, 104, 104, 'e5', 10403, 10405, '复核通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104006, 104, 104, 'e6', 10405, 10407, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104007, 104, 104, 'e7', 10405, 10406, '通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104008, 104, 104, 'e8', 10404, 10407, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104009, 104, 104, 'e9', 10406, 10407, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(105001, 105, 105, 'e1', 10501, 10502, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(105002, 105, 105, 'e2', 10502, 10503, '直接入库', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(106001, 106, 106, 'e1', 10601, 10602, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(106002, 106, 106, 'e2', 10602, 10603, '直接出库', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(107001, 107, 107, 'e1', 10701, 10702, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(107002, 107, 107, 'e2', 10702, 10703, '批量入库', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108001, 108, 108, 'e1', 10801, 10802, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(108002, 108, 108, 'e2', 10802, 10803, '批量出库', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109001, 109, 109, 'e1', 10901, 10902, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109002, 109, 109, 'e2', 10902, 10903, '提交', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109003, 109, 109, 'e3', 10903, 10904, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109004, 109, 109, 'e4', 10904, 10905, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109005, 109, 109, 'e5', 10905, 10906, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110001, 110, 110, 'e1', 11001, 11002, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110002, 110, 110, 'e2', 11002, 11003, '提交', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110003, 110, 110, 'e3', 11003, 11004, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110004, 110, 110, 'e4', 11004, 11005, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110005, 110, 110, 'e5', 11005, 11006, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(111001, 111, 111, 'e1', 11101, 11102, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(111002, 111, 111, 'e2', 11102, 11103, '直接入库', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112001, 112, 112, 'e1', 11201, 11202, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(112002, 112, 112, 'e2', 11202, 11203, '直接完成', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113001, 113, 113, 'e1', 11301, 11302, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113002, 113, 113, 'e2', 11302, 11303, '提交', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113003, 113, 113, 'e3', 11303, 11304, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113004, 113, 113, 'e4', 11304, 11303, '重新提交', 'resubmit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113005, 113, 113, 'e5', 11303, 11305, '复核通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113006, 113, 113, 'e6', 11305, 11307, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113007, 113, 113, 'e7', 11305, 11306, '通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113008, 113, 113, 'e8', 11306, 11307, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113009, 113, 113, 'e9', 11304, 11307, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114001, 114, 114, 'e1', 11401, 11402, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114002, 114, 114, 'e2', 11402, 11403, '提交', 'submit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114003, 114, 114, 'e3', 11403, 11404, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114004, 114, 114, 'e4', 11404, 11403, '重新提交', 'resubmit', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114005, 114, 114, 'e5', 11403, 11405, '复核通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114006, 114, 114, 'e6', 11405, 11407, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114007, 114, 114, 'e7', 11405, 11406, '通过', 'approve', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114008, 114, 114, 'e8', 11406, 11407, '', 'auto', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114009, 114, 114, 'e9', 11404, 11407, '驳回', 'reject', 'AND', NULL, '2026-05-20 09:00:00', '2026-05-20 09:00:00');

INSERT INTO `wf_edge_cond_rule` (`id`, `edge_id`, `seq`, `field_code`, `operator`, `field_val`, `crte_time`, `updt_time`)
SELECT e.id * 10 + 1, e.id, 1, 'auditStatus', 'eq', CASE WHEN e.label = '驳回' THEN '驳回' WHEN e.label = '不通过' THEN '不通过' ELSE '通过' END,
'2026-05-20 09:00:00', '2026-05-20 09:00:00' FROM `wf_flow_edge` e WHERE e.label IN ('驳回', '复核通过', '不通过', '通过');

INSERT INTO `wf_node_approval_config` (`id`, `node_id`, `approval_strategy`, `approval_remark`, `crte_time`, `updt_time`)
VALUES
(10102, 10102, 'initiator', '研究员A发起债券标准升库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10103, 10103, 'preempt', '研究员B复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10104, 10104, 'initiator', '研究员A根据驳回意见修改后重新提交或终止', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10105, 10105, 'preempt', '研究总监审批，驳回则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10106, 10106, 'o32', 'O32系统自动审批，临时代码转人工', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10202, 10202, 'initiator', '研究员A发起债券标准降库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10203, 10203, 'preempt', '研究员B复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10204, 10204, 'initiator', '研究员A根据驳回意见修改后重新提交或终止', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10205, 10205, 'preempt', '研究总监审批，驳回则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10206, 10206, 'o32', 'O32系统自动审批，临时代码转人工', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10302, 10302, 'initiator', '研究员A发起债券一般入库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10303, 10303, 'preempt', '研究员B复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10304, 10304, 'initiator', '研究员A根据驳回意见修改后重新提交或终止', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10305, 10305, 'preempt', '研究总监审批，驳回则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10306, 10306, 'o32', 'O32系统自动审批，临时代码转人工', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10402, 10402, 'initiator', '研究员A发起债券一般出库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10403, 10403, 'preempt', '研究员B复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10404, 10404, 'initiator', '研究员A根据驳回意见修改后重新提交或终止', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10405, 10405, 'preempt', '研究总监审批，驳回则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10406, 10406, 'o32', 'O32系统自动审批，临时代码转人工', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10502, 10502, 'initiator', '研究员A发起债券快速入库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10602, 10602, 'initiator', '研究员A发起债券快速出库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10702, 10702, 'initiator', '研究员A发起债券批量入库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10802, 10802, 'initiator', '研究员A发起债券批量出库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10902, 10902, 'initiator', '研究员A发起债券特殊策略入库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10903, 10903, 'auto', '自动审批：基金经理/投资经理提出投资意向', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10904, 10904, 'auto', '自动审批：信用研究建议', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(10905, 10905, 'auto', '自动审批：投资决策委员会审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11002, 11002, 'initiator', '研究员A发起债券特殊策略出库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11003, 11003, 'auto', '自动审批：基金经理/投资经理提出投资意向', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11004, 11004, 'auto', '自动审批：信用研究建议', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11005, 11005, 'auto', '自动审批：投资决策委员会审批', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11102, 11102, 'initiator', '研究员A发起债券白名单入库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11202, 11202, 'initiator', '研究员A发起无需审核审批流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11302, 11302, 'initiator', '研究员A发起禁止库入库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11303, 11303, 'preempt', '研究员B复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11304, 11304, 'initiator', '研究员A根据驳回意见修改后重新提交或终止', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11305, 11305, 'preempt', '研究总监审批，驳回则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11306, 11306, 'auto', '自动审批：投资决策委员会审批，系统自动通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11402, 11402, 'initiator', '研究员A发起禁止库出库流程', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11403, 11403, 'preempt', '研究员B复核，不通过则驳回研究员A修改', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11404, 11404, 'initiator', '研究员A根据驳回意见修改后重新提交或终止', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11405, 11405, 'preempt', '研究总监审批，驳回则流程结束', '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(11406, 11406, 'auto', '自动审批：投资决策委员会审批，系统自动通过', '2026-05-20 09:00:00', '2026-05-20 09:00:00');

INSERT INTO `wf_node_approval_handler` (`id`, `approval_config_id`, `handler_type`, `handler_id`, `handler_name`, `sort_order`, `crte_time`, `updt_time`)
VALUES
(101031, 10103, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101032, 10103, 'user', 5, '研究员4', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101051, 10105, 'role', 9, '研究总监', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101052, 10105, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101061, 10106, 'role', 10, 'O32系统自动审批', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(101062, 10106, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102031, 10203, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102032, 10203, 'user', 5, '研究员4', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102051, 10205, 'role', 9, '研究总监', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102052, 10205, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102061, 10206, 'role', 10, 'O32系统自动审批', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(102062, 10206, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103031, 10303, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103032, 10303, 'user', 5, '研究员4', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103051, 10305, 'role', 9, '研究总监', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103052, 10305, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103061, 10306, 'role', 10, 'O32系统自动审批', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(103062, 10306, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104031, 10403, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104032, 10403, 'user', 5, '研究员4', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104051, 10405, 'role', 9, '研究总监', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104052, 10405, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104061, 10406, 'role', 10, 'O32系统自动审批', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(104062, 10406, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109031, 10903, 'role', 1, '基金经理', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109041, 10904, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(109051, 10905, 'role', 11, '投资决策委员会', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110031, 11003, 'role', 1, '基金经理', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110041, 11004, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(110051, 11005, 'role', 11, '投资决策委员会', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113031, 11303, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113032, 11303, 'user', 5, '研究员4', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113051, 11305, 'role', 9, '研究总监', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113052, 11305, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113061, 11306, 'role', 11, '投资决策委员会', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(113062, 11306, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114031, 11403, 'role', 2, '信用研究组', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114032, 11403, 'user', 5, '研究员4', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114051, 11405, 'role', 9, '研究总监', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114052, 11405, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114061, 11406, 'role', 11, '投资决策委员会', 1, '2026-05-20 09:00:00', '2026-05-20 09:00:00'),
(114062, 11406, 'user', 1, '管理员', 2, '2026-05-20 09:00:00', '2026-05-20 09:00:00');

-- 校验
SELECT COUNT(*) AS total_definition_count FROM `wf_flow_definition`;
SELECT `category`, COUNT(*) AS category_count FROM `wf_flow_definition` GROUP BY `category`;
SELECT `approval_strategy`, COUNT(*) AS cnt FROM `wf_node_approval_config` GROUP BY `approval_strategy`;
