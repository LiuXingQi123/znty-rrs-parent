-- ============================================================================
-- 工作流平台 — 流程定义模块 · 演示数据初始化脚本
-- 目标库：  znty_sirm
-- 兼容：    MySQL 8.4
-- 说明：    基于 flow_definition_schema.sql 的表结构生成
--           包含 3 个流程定义，覆盖不同分类、状态、复杂度
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
-- 1. 流程定义
-- ============================================================================

INSERT INTO `wf_flow_definition` (`id`, `name`, `flow_key`, `category`, `description`, `remark`, `status`, `created_by`, `updated_by`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(1, '信用债入库审批流程', 'bond:credit-inbound',  'credit', '信用债入库交易需经投资经理初审；大额交易（≥5000万）加签风控复核', '合规要求：大额阈值 5000 万，风控不可跳过', 'active',   1001, 1001, 0, '2026-04-15 09:30:00', '2026-05-18 14:20:00'),
(2, '利率债出库流程',     'bond:rate-outbound',   'bond',   '利率债出库标准流程，交易员发起后自动更新持仓并通知相关方',   '',                                       'active',   1001, 1002, 0, '2026-05-01 10:00:00', '2026-05-12 11:00:00'),
(3, '基金申购流程',       'fund:purchase',        'fund',   '基金产品申购审批流程（设计中）',                             '待确认：是否需要投资总监审批节点',         'draft',    1002, 1002, 0, '2026-05-10 16:00:00', '2026-05-19 08:30:00');


-- ============================================================================
-- 2. 流程版本
--    流程 1 有两个版本（v1 初始版本 + v2 增加大额判断分支）
--    流程 2、3 各只有 v1
-- ============================================================================

INSERT INTO `wf_flow_version` (`id`, `flow_id`, `flow_key`, `ver_num`, `status`, `publish_note`, `canvas_nodes`, `canvas_edges`, `canvas_pan_x`, `canvas_pan_y`, `canvas_zoom`, `published_by`, `published_time`, `created_by`, `crte_time`, `updt_time`) VALUES

-- 流程 1 v1（初始版本，简单三级）
(1, 1, 'bond:credit-inbound', 1, 'active', '初始版本：仅含投资经理审批节点',
    '[{"id":"n101","type":"start","label":"开始","x":400,"y":50,"shape":"circle","sub":null},{"id":"n102","type":"approval","label":"投资经理审批","x":400,"y":180,"shape":"rect","sub":null,"approvalStrategy":"preempt","approvalPersons":["fund-manager"],"approvalRemark":"投资经理初审信用债入库申请"},{"id":"n103","type":"end","label":"结束","x":400,"y":310,"shape":"circle","sub":null}]',
    '[{"id":"e101","from":"n101","to":"n102","label":"","remark":"","condRules":[],"condLogic":"AND"},{"id":"e102","from":"n102","to":"n103","label":"通过","remark":"","condRules":[],"condLogic":"AND"}]',
    0, 0, 1, 1001, '2026-04-15 10:00:00', 1001, '2026-04-15 09:30:00', '2026-04-15 10:00:00'),

-- 流程 1 v2（当前版本，增加大额判断 + 风控审批分支）
(2, 1, 'bond:credit-inbound', 2, 'active', 'v2：增加大额判断分支，≥5000 万自动加签风控审批',
    '[{"id":"n201","type":"start","label":"开始","x":400,"y":40,"shape":"circle","sub":null},{"id":"n202","type":"approval","label":"投资经理审批","x":400,"y":150,"shape":"rect","sub":null,"approvalStrategy":"preempt","approvalPersons":["fund-manager"],"approvalRemark":"投资经理初审，检查标的基本面与合规性"},{"id":"n203","type":"condition","label":"大额判断","x":400,"y":270,"shape":"diamond","sub":null,"conditionRemark":"申请金额 ≥ 5000 万走大额分支"},{"id":"n204","type":"approval","label":"风控审批","x":200,"y":400,"shape":"rect","sub":null,"approvalStrategy":"all","approvalPersons":["risk-officer","compliance"],"approvalRemark":"大额交易须风控+合规双人审批"},{"id":"n205","type":"auto","label":"清算归档","x":400,"y":530,"shape":"rect","sub":null,"autoTasks":[{"id":1,"task":"syncSettlement"},{"id":2,"task":"archiveRecord"}],"autoRemark":"自动同步清算数据并归档，不论大额/常规均执行"},{"id":"n206","type":"notify","label":"结果通知","x":400,"y":650,"shape":"rect","sub":null,"notifyTarget":"initiator","notifyChannels":["system","email"],"notifyPersons":[],"notifyTpl":"您好，信用债入库申请【#{procName}】已#{result}，请登录系统查看详情。","notifyRemark":"仅通知发起人"},{"id":"n207","type":"end","label":"结束","x":400,"y":770,"shape":"circle","sub":null}]',
    '[{"id":"e201","from":"n201","to":"n202","label":"","remark":"","condRules":[],"condLogic":"AND"},{"id":"e202","from":"n202","to":"n203","label":"审批通过","remark":"","condRules":[],"condLogic":"AND"},{"id":"e203","from":"n203","to":"n204","label":"是-大额","remark":"≥5000 万走风控分支","condRules":[{"field":"isLargeAmount","op":"eq","val":"是"}],"condLogic":"AND"},{"id":"e204","from":"n203","to":"n205","label":"否-常规","remark":"< 5000 万直接进入清算","condRules":[{"field":"isLargeAmount","op":"eq","val":"否"}],"condLogic":"AND"},{"id":"e205","from":"n204","to":"n205","label":"审批通过","remark":"风控审批通过后进入清算归档","condRules":[],"condLogic":"AND"},{"id":"e206","from":"n205","to":"n206","label":"","remark":"","condRules":[],"condLogic":"AND"},{"id":"e207","from":"n206","to":"n207","label":"","remark":"","condRules":[],"condLogic":"AND"}]',
    -120, 30, 0.85, 1001, '2026-05-18 14:20:00', 1001, '2026-05-18 10:00:00', '2026-05-18 14:20:00'),

-- 流程 2 v1（利率债出库）
(3, 2, 'bond:rate-outbound', 1, 'active', '初始发布',
    '[{"id":"n301","type":"start","label":"开始","x":400,"y":40,"shape":"circle","sub":null},{"id":"n302","type":"approval","label":"交易员审批","x":400,"y":160,"shape":"rect","sub":null,"approvalStrategy":"all","approvalPersons":["trader","fund-manager"],"approvalRemark":"交易员与投资经理双人确认出库操作"},{"id":"n303","type":"auto","label":"更新持仓","x":400,"y":290,"shape":"rect","sub":null,"autoTasks":[{"id":1,"task":"updatePosition"},{"id":2,"task":"sendNotify"}],"autoRemark":"自动更新持仓状态并发送系统通知"},{"id":"n304","type":"notify","label":"出库通知","x":400,"y":420,"shape":"rect","sub":null,"notifyTarget":"person","notifyChannels":["system","wecom"],"notifyPersons":["trader","risk-officer"],"notifyTpl":"利率债【#{bondName}】已出库，金额 #{amount} 万元，请知悉。","notifyRemark":"企业微信 + 系统消息双通道通知"},{"id":"n305","type":"end","label":"结束","x":400,"y":550,"shape":"circle","sub":null}]',
    '[{"id":"e301","from":"n301","to":"n302","label":"","remark":"","condRules":[],"condLogic":"AND"},{"id":"e302","from":"n302","to":"n303","label":"通过","remark":"","condRules":[],"condLogic":"AND"},{"id":"e303","from":"n303","to":"n304","label":"","remark":"","condRules":[],"condLogic":"AND"},{"id":"e304","from":"n304","to":"n305","label":"","remark":"","condRules":[],"condLogic":"AND"}]',
    0, 0, 1, 1002, '2026-05-12 11:00:00', 1002, '2026-05-01 10:00:00', '2026-05-12 11:00:00'),

-- 流程 3 v1（基金申购，草稿）
(4, 3, 'fund:purchase', 1, 'draft', NULL,
    '[{"id":"n401","type":"start","label":"开始","x":400,"y":50,"shape":"circle","sub":null},{"id":"n402","type":"approval","label":"投资经理审批","x":400,"y":180,"shape":"rect","sub":null,"approvalStrategy":"preempt","approvalPersons":["fund-manager"],"approvalRemark":"基金申购由投资经理审批"},{"id":"n403","type":"end","label":"结束","x":400,"y":320,"shape":"circle","sub":null}]',
    '[{"id":"e401","from":"n401","to":"n402","label":"","remark":"","condRules":[],"condLogic":"AND"},{"id":"e402","from":"n402","to":"n403","label":"通过","remark":"","condRules":[],"condLogic":"AND"}]',
    0, 0, 1, NULL, NULL, 1002, '2026-05-10 16:00:00', '2026-05-19 08:30:00');


-- ============================================================================
-- 3. 流程节点
-- ============================================================================

-- 流程 1 v1 的节点（version_id = 1）
INSERT INTO `wf_flow_node` (`id`, `version_id`, `flow_id`, `node_id`, `node_type`, `label`, `shape`, `pos_x`, `pos_y`, `sub_label`, `sort_order`, `crte_time`, `updt_time`) VALUES
(1,  1, 1, 'n101', 'start',    '开始',          'circle',  400, 50,  NULL, 1, '2026-04-15 09:30:00', '2026-04-15 09:30:00'),
(2,  1, 1, 'n102', 'approval', '投资经理审批',  'rect',    400, 180, NULL, 2, '2026-04-15 09:30:00', '2026-04-15 09:30:00'),
(3,  1, 1, 'n103', 'end',      '结束',          'circle',  400, 310, NULL, 3, '2026-04-15 09:30:00', '2026-04-15 09:30:00'),

-- 流程 1 v2 的节点（version_id = 2）
(4,  2, 1, 'n201', 'start',     '开始',          'circle',  400, 40,  NULL, 1, '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(5,  2, 1, 'n202', 'approval',  '投资经理审批',  'rect',    400, 150, NULL, 2, '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(6,  2, 1, 'n203', 'condition', '大额判断',      'diamond', 400, 270, NULL, 3, '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(7,  2, 1, 'n204', 'approval',  '风控审批',      'rect',    200, 400, NULL, 4, '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(8,  2, 1, 'n205', 'auto',      '清算归档',      'rect',    400, 530, NULL, 5, '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(9,  2, 1, 'n206', 'notify',    '结果通知',      'rect',    400, 650, NULL, 6, '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(10, 2, 1, 'n207', 'end',       '结束',          'circle',  400, 770, NULL, 7, '2026-05-18 10:00:00', '2026-05-18 10:00:00'),

-- 流程 2 v1 的节点（version_id = 3）
(11, 3, 2, 'n301', 'start',     '开始',          'circle', 400, 40,  NULL, 1, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
(12, 3, 2, 'n302', 'approval',  '交易员审批',    'rect',   400, 160, NULL, 2, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
(13, 3, 2, 'n303', 'auto',      '更新持仓',      'rect',   400, 290, NULL, 3, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
(14, 3, 2, 'n304', 'notify',    '出库通知',      'rect',   400, 420, NULL, 4, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
(15, 3, 2, 'n305', 'end',       '结束',          'circle', 400, 550, NULL, 5, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),

-- 流程 3 v1 的节点（version_id = 4）
(16, 4, 3, 'n401', 'start',     '开始',          'circle', 400, 50,  NULL, 1, '2026-05-10 16:00:00', '2026-05-19 08:30:00'),
(17, 4, 3, 'n402', 'approval',  '投资经理审批',  'rect',   400, 180, NULL, 2, '2026-05-10 16:00:00', '2026-05-19 08:30:00'),
(18, 4, 3, 'n403', 'end',       '结束',          'circle', 400, 320, NULL, 3, '2026-05-10 16:00:00', '2026-05-19 08:30:00');


-- ============================================================================
-- 4. 审批节点配置
-- ============================================================================

INSERT INTO `wf_node_approval_config` (`id`, `node_id`, `approval_strategy`, `approval_persons`, `approval_remark`, `crte_time`, `updt_time`) VALUES
-- 流程 1 v1：投资经理审批（node_id = 2）
(1, 2,  'preempt', '["fund-manager"]',                         '投资经理初审信用债入库申请',             '2026-04-15 09:30:00', '2026-04-15 09:30:00'),
-- 流程 1 v2：投资经理审批（node_id = 5）
(2, 5,  'preempt', '["fund-manager"]',                         '投资经理初审，检查标的基本面与合规性',   '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
-- 流程 1 v2：风控审批（node_id = 7）
(3, 7,  'all',     '["risk-officer","compliance"]',             '大额交易须风控+合规双人审批',             '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
-- 流程 2 v1：交易员审批（node_id = 12）
(4, 12, 'all',     '["trader","fund-manager"]',                 '交易员与投资经理双人确认出库操作',       '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
-- 流程 3 v1：投资经理审批（node_id = 17）
(5, 17, 'preempt', '["fund-manager"]',                         '基金申购由投资经理审批',                 '2026-05-10 16:00:00', '2026-05-19 08:30:00');


-- ============================================================================
-- 5. 自动执行节点配置
--    注：auto_remark 为节点级属性，同节点各行冗余存储（已知设计问题）
-- ============================================================================

INSERT INTO `wf_node_auto_config` (`id`, `node_id`, `task_seq`, `task_code`, `auto_remark`, `crte_time`, `updt_time`) VALUES
-- 流程 1 v2：清算归档（node_id = 8），两个任务
(1, 8, 1, 'syncSettlement', '自动同步清算数据并归档，不论大额/常规均执行', '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(2, 8, 2, 'archiveRecord',  '自动同步清算数据并归档，不论大额/常规均执行', '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
-- 流程 2 v1：更新持仓（node_id = 13），两个任务
(3, 13, 1, 'updatePosition', '自动更新持仓状态并发送系统通知',           '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
(4, 13, 2, 'sendNotify',     '自动更新持仓状态并发送系统通知',           '2026-05-01 10:00:00', '2026-05-01 10:00:00');


-- ============================================================================
-- 6. 消息通知节点配置
-- ============================================================================

INSERT INTO `wf_node_notify_config` (`id`, `node_id`, `notify_channels`, `notify_target`, `notify_persons`, `notify_tpl`, `notify_remark`, `crte_time`, `updt_time`) VALUES
-- 流程 1 v2：结果通知（node_id = 9）
(1, 9,  '["system","email"]', 'initiator', NULL,                         '您好，信用债入库申请【#{procName}】已#{result}，请登录系统查看详情。',     '仅通知发起人',                     '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
-- 流程 2 v1：出库通知（node_id = 14）
(2, 14, '["system","wecom"]', 'person',    '["trader","risk-officer"]',  '利率债【#{bondName}】已出库，金额 #{amount} 万元，请知悉。',               '企业微信 + 系统消息双通道通知',     '2026-05-01 10:00:00', '2026-05-01 10:00:00');


-- ============================================================================
-- 7. 条件节点配置
-- ============================================================================

INSERT INTO `wf_node_condition_config` (`id`, `node_id`, `condition_remark`, `crte_time`, `updt_time`) VALUES
-- 流程 1 v2：大额判断（node_id = 6）
(1, 6, '申请金额 ≥ 5000 万走大额分支', '2026-05-18 10:00:00', '2026-05-18 10:00:00');


-- ============================================================================
-- 8. 流程连线
--    from_node_id / to_node_id 为 wf_flow_node.id（BIGINT 代理键）
--    edge_id 为前端业务标识（仅画布还原用）
-- ============================================================================

INSERT INTO `wf_flow_edge` (`id`, `version_id`, `flow_id`, `edge_id`, `from_node_id`, `to_node_id`, `label`, `cond_logic`, `remark`, `crte_time`, `updt_time`) VALUES
-- 流程 1 v1（version_id = 1）
(1,  1, 1, 'e101', 1,  2,  '',       'AND', '',                         '2026-04-15 09:30:00', '2026-04-15 09:30:00'),
(2,  1, 1, 'e102', 2,  3,  '通过',   'AND', '',                         '2026-04-15 09:30:00', '2026-04-15 09:30:00'),

-- 流程 1 v2（version_id = 2）
(3,  2, 1, 'e201', 4,  5,  '',           'AND', '',                                         '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(4,  2, 1, 'e202', 5,  6,  '审批通过',   'AND', '',                                         '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(5,  2, 1, 'e203', 6,  7,  '是-大额',    'AND', '≥5000 万走风控分支',                       '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(6,  2, 1, 'e204', 6,  8,  '否-常规',    'AND', '< 5000 万直接进入清算',                     '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(7,  2, 1, 'e205', 7,  8,  '审批通过',   'AND', '风控审批通过后进入清算归档',               '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(8,  2, 1, 'e206', 8,  9,  '',           'AND', '',                                         '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
(9,  2, 1, 'e207', 9,  10, '',           'AND', '',                                         '2026-05-18 10:00:00', '2026-05-18 10:00:00'),

-- 流程 2 v1（version_id = 3）
(10, 3, 2, 'e301', 11, 12, '',           'AND', '',                                         '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
(11, 3, 2, 'e302', 12, 13, '通过',       'AND', '',                                         '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
(12, 3, 2, 'e303', 13, 14, '',           'AND', '',                                         '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
(13, 3, 2, 'e304', 14, 15, '',           'AND', '',                                         '2026-05-01 10:00:00', '2026-05-01 10:00:00'),

-- 流程 3 v1（version_id = 4）
(14, 4, 3, 'e401', 16, 17, '',           'AND', '',                                         '2026-05-10 16:00:00', '2026-05-19 08:30:00'),
(15, 4, 3, 'e402', 17, 18, '通过',       'AND', '',                                         '2026-05-10 16:00:00', '2026-05-19 08:30:00');


-- ============================================================================
-- 9. 连线条件规则
--    仅流程 1 v2 的大额判断分支有条件（edge_id = 5, 6）
--    edge_id 此处为 wf_flow_edge.id（BIGINT 代理键）
-- ============================================================================

INSERT INTO `wf_edge_cond_rule` (`id`, `edge_id`, `seq`, `field_code`, `operator`, `field_val`, `crte_time`, `updt_time`) VALUES
-- e203: 大额分支 — isLargeAmount = 是
(1, 5, 1, 'isLargeAmount', 'eq', '是', '2026-05-18 10:00:00', '2026-05-18 10:00:00'),
-- e204: 常规分支 — isLargeAmount = 否
(2, 6, 1, 'isLargeAmount', 'eq', '否', '2026-05-18 10:00:00', '2026-05-18 10:00:00');


-- ============================================================================
-- 10. 角色字典（补充，主 schema 中已有 INSERT，此处用 IGNORE 防重复）
-- ============================================================================

INSERT IGNORE INTO `wf_role_dict` (`id`, `role_code`, `role_name`, `sort_order`, `is_active`, `crte_time`, `updt_time`) VALUES
(1, 'fund-manager',    '投资经理',   1, 1, NOW(), NOW()),
(2, 'trader',          '交易员',     2, 1, NOW(), NOW()),
(3, 'risk-officer',    '风控专员',   3, 1, NOW(), NOW()),
(4, 'compliance',      '合规专员',   4, 1, NOW(), NOW()),
(5, 'invest-director', '投资总监',   5, 1, NOW(), NOW()),
(6, 'admin',           '系统管理员', 6, 1, NOW(), NOW());


SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================================
-- 数据概览
-- ============================================================================
--
--  流程 1「信用债入库审批」  credit  active   v1→v2    8 节点  7 条边  2 条件规则
--    start → 投资经理审批 → 大额判断 ──[是]→ 风控审批 ─→ 清算归档 → 结果通知 → end
--                                    ──[否]───────────→ 清算归档 → 结果通知 → end
--
--  流程 2「利率债出库」      bond    active   v1       5 节点  4 条边  0 条件规则
--    start → 交易员审批 → 更新持仓 → 出库通知 → end
--
--  流程 3「基金申购」        fund    draft    v1       3 节点  2 条边  0 条件规则
--    start → 投资经理审批 → end  （设计中，尚未发布）
--
--  共 18 个节点 / 15 条边 / 5 个审批配置 / 4 个自动任务配置
--     2 个通知配置 / 1 个条件节点配置 / 2 条条件规则 / 6 个角色
-- ============================================================================
