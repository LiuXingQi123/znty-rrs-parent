-- ============================================================
-- znty-sirm 投资池初始化 - 演示数据脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，插入投资池、角色、人员等测试数据
-- ============================================================
USE `znty_sirm`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 清空所有业务表
-- ----------------------------------------------------------------------------
TRUNCATE TABLE `ip_pool_permission_evt`;
TRUNCATE TABLE `t_sys_user_role_evt`;
TRUNCATE TABLE `t_sys_user_evt`;
TRUNCATE TABLE `t_sys_role_evt`;
TRUNCATE TABLE `ip_pool_auto_rule_evt`;
TRUNCATE TABLE `ip_pool_relation_evt`;
TRUNCATE TABLE `ip_investment_pool_evt`;
TRUNCATE TABLE `ip_pool_permission`;
TRUNCATE TABLE `t_sys_user_role`;
TRUNCATE TABLE `t_sys_user`;
TRUNCATE TABLE `t_sys_role`;
TRUNCATE TABLE `ip_pool_auto_rule`;
TRUNCATE TABLE `ip_pool_relation`;
TRUNCATE TABLE `ip_investment_pool`;

-- ----------------------------------------------------------------------------
-- 1. 初始化固定投资池树
-- ----------------------------------------------------------------------------
INSERT INTO `ip_investment_pool` (`id`, `parent_id`, `pool_code`, `pool_name`, `pool_type`, `pool_level`, `market_codes`, `variety_codes`, `in_report_restriction`, `out_report_restriction`, `outer_sort`, `inner_sort`, `status`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(1, NULL, 'credit_bond_root', '信用债大库', 'credit_bond', 1, JSON_ARRAY(), JSON_ARRAY('bond'), 'internal', 'internal', 1, 1, 'enabled', 0, NOW(), NOW()),
(2, 1, 'credit_bond_level_1', '一级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 1, 1, 'enabled', 0, NOW(), NOW()),
(3, 1, 'credit_bond_level_2', '二级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 1, 2, 'enabled', 0, NOW(), NOW()),
(4, 1, 'credit_bond_level_3', '三级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 1, 3, 'enabled', 0, NOW(), NOW()),
(5, 1, 'credit_bond_level_4', '四级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 1, 4, 'enabled', 0, NOW(), NOW()),
(6, 1, 'credit_bond_level_5', '五级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 1, 5, 'enabled', 0, NOW(), NOW()),
(7, NULL, 'offshore_bond_root', '境外债库', 'offshore_bond', 1, JSON_ARRAY(), JSON_ARRAY('bond'), 'internal', 'any', 2, 1, 'enabled', 0, NOW(), NOW()),
(8, NULL, 'convertible_bond_root', '转债库', 'convertible_bond', 1, JSON_ARRAY(), JSON_ARRAY('bond'), 'any', 'any', 3, 1, 'enabled', 0, NOW(), NOW()),
(9, NULL, 'special_account_root', '专户产品', 'special_account', 1, JSON_ARRAY(), JSON_ARRAY('bond'), 'internal', 'internal', 4, 1, 'enabled', 0, NOW(), NOW()),
(10, 9, 'special_account_level_1', '一级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 4, 1, 'enabled', 0, NOW(), NOW()),
(11, 9, 'special_account_level_2', '二级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 4, 2, 'enabled', 0, NOW(), NOW()),
(12, 9, 'special_account_level_3', '三级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 4, 3, 'enabled', 0, NOW(), NOW()),
(13, 9, 'special_account_level_4', '四级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 4, 4, 'enabled', 0, NOW(), NOW()),
(14, 9, 'special_account_level_5', '五级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 4, 5, 'enabled', 0, NOW(), NOW()),
(15, NULL, 'forbidden_root', '禁投池', 'forbidden', 1, JSON_ARRAY(), JSON_ARRAY('bond'), NULL, NULL, 5, 1, 'enabled', 0, NOW(), NOW());

-- 初始化投资池互斥关系（信用债库 2-6 互斥，专户产品 10-14 互斥）
INSERT INTO `ip_pool_relation` (`pool_id`, `relation_type`, `relation_pool_id`, `relation_pool_name`, `sort_order`, `is_deleted`, `crte_time`, `updt_time`)
SELECT a.id,
       'in_mutex',
       b.id,
       b.pool_name,
       0,
       0,
       NOW(),
       NOW()
FROM ip_investment_pool a
         JOIN ip_investment_pool b ON a.parent_id = b.parent_id AND a.id != b.id
WHERE a.parent_id IN (1, 9)
  AND a.is_deleted = 0
  AND b.is_deleted = 0;

INSERT INTO `ip_pool_relation` (`pool_id`, `relation_type`, `relation_pool_id`, `relation_pool_name`, `sort_order`, `is_deleted`, `crte_time`, `updt_time`)
SELECT a.id,
       'out_mutex',
       b.id,
       b.pool_name,
       0,
       0,
       NOW(),
       NOW()
FROM ip_investment_pool a
         JOIN ip_investment_pool b ON a.parent_id = b.parent_id AND a.id != b.id
WHERE a.parent_id IN (1, 9)
  AND a.is_deleted = 0
  AND b.is_deleted = 0;

INSERT INTO `ip_pool_relation_evt` (`id`, `pool_id`, `relation_type`, `relation_pool_id`, `relation_pool_name`, `sort_order`, `is_deleted`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`)
SELECT id, pool_id, relation_type, relation_pool_id, relation_pool_name, sort_order, is_deleted, crte_time, updt_time, 'system', NOW(), '新增'
FROM ip_pool_relation;

INSERT INTO `ip_investment_pool_evt` (
    `id`, `parent_id`, `pool_code`, `pool_name`, `pool_type`, `pool_level`, `market_codes`, `variety_codes`,
    `hs_pool_name`, `in_flow_id`, `in_flow_key`, `in_flow_name`, `out_flow_id`, `out_flow_key`, `out_flow_name`,
    `simple_in_flow_id`, `simple_in_flow_key`, `simple_in_flow_name`, `simple_out_flow_id`, `simple_out_flow_key`,
    `simple_out_flow_name`, `batch_in_flow_id`, `batch_in_flow_key`, `batch_in_flow_name`, `batch_out_flow_id`,
    `batch_out_flow_key`, `batch_out_flow_name`, `in_report_restriction`, `out_report_restriction`,
    `max_capacity`, `outer_sort`, `inner_sort`, `description`, `status`,
    `is_deleted`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`
)
SELECT `id`, `parent_id`, `pool_code`, `pool_name`, `pool_type`, `pool_level`, `market_codes`, `variety_codes`,
       `hs_pool_name`, `in_flow_id`, `in_flow_key`, `in_flow_name`, `out_flow_id`, `out_flow_key`, `out_flow_name`,
       `simple_in_flow_id`, `simple_in_flow_key`, `simple_in_flow_name`, `simple_out_flow_id`, `simple_out_flow_key`,
       `simple_out_flow_name`, `batch_in_flow_id`, `batch_in_flow_key`, `batch_in_flow_name`, `batch_out_flow_id`,
       `batch_out_flow_key`, `batch_out_flow_name`, `in_report_restriction`, `out_report_restriction`,
       `max_capacity`, `outer_sort`, `inner_sort`, `description`, `status`,
       `is_deleted`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `ip_investment_pool`;

-- ----------------------------------------------------------------------------
-- 2. 初始化角色和人员数据
-- ----------------------------------------------------------------------------
-- 角色树：研究部 > 信用研究组 > 利率研究组
INSERT INTO `t_sys_role` (`id`, `name`, `parent_id`, `sort_order`, `enable`, `crte_time`, `updt_time`) VALUES
(1, '研究部',       NULL, 1, 1, NOW(), NOW()),
(2, '信用研究组',   1,    2, 1, NOW(), NOW()),
(3, '利率研究组',   2,    3, 1, NOW(), NOW()),
(4, '固收部',       NULL, 4, 1, NOW(), NOW()),
(5, '利率组',       4,    5, 1, NOW(), NOW()),
(6, '信用组',       4,    6, 1, NOW(), NOW()),
(7, '权益部',       NULL, 7, 1, NOW(), NOW()),
(8, '行业研究组',   7,    8, 1, NOW(), NOW()),
(9, '量化部',       NULL, 9, 1, NOW(), NOW());

-- 人员
INSERT INTO `t_sys_user` (`id`, `name`, `user_name`, `dr`, `crte_time`, `updt_time`) VALUES
(1,  '研究员1', 'yanjiuyuan1', 0, NOW(), NOW()),
(2,  '叶伟', 'yewei', 0, NOW(), NOW()),
(3,  '研究员3', 'yanjiuyuan3', 0, NOW(), NOW()),
(4,  '研究员4', 'yanjiuyuan4', 0, NOW(), NOW()),
(5,  '研究员5', 'yanjiuyuan5', 0, NOW(), NOW()),
(6,  '固收1', 'gushou1', 0, NOW(), NOW()),
(7,  '固收2', 'gushou2', 0, NOW(), NOW()),
(8,  '固收3', 'gushou3', 0, NOW(), NOW()),
(9,  '固收4', 'gushou4', 0, NOW(), NOW()),
(10,  '权益1', 'quanyi1', 0, NOW(), NOW()),
(11,  '权益2', 'quanyi2', 0, NOW(), NOW()),
(12,  '权益3', 'quanyi3', 0, NOW(), NOW()),
(13,  '量化1', 'lianghua1', 0, NOW(), NOW()),
(14,  '量化2', 'lianghua2', 0, NOW(), NOW()),
(1001,  '管理员', 'admin', 0, NOW(), NOW());

INSERT INTO `t_sys_user_role` (`id`, `user_id`, `role_id`, `dr`, `crte_time`, `updt_time`) VALUES
(1,  1,  1, 0, NOW(), NOW()),  -- 研究员1: 研究部
(2,  1,  2, 0, NOW(), NOW()),  -- 研究员1: 信用研究组
(3,  2,  2, 0, NOW(), NOW()),  -- 研究员2: 信用研究组
(4,  2,  3, 0, NOW(), NOW()),  -- 研究员2: 利率研究组
(5,  3,  1, 0, NOW(), NOW()),  -- 研究员3: 研究部
(6,  3,  3, 0, NOW(), NOW()),  -- 研究员3: 利率研究组
(7,  4,  2, 0, NOW(), NOW()),  -- 研究员4: 信用研究组
(8,  5,  3, 0, NOW(), NOW()),  -- 研究员5: 利率研究组
(9,  6,  4, 0, NOW(), NOW()),  -- 固收1: 固收部
(10, 6,  5, 0, NOW(), NOW()),  -- 固收1: 利率组
(11, 7,  4, 0, NOW(), NOW()),  -- 固收2: 固收部
(12, 7,  6, 0, NOW(), NOW()),  -- 固收2: 信用组
(13, 8,  5, 0, NOW(), NOW()),  -- 固收3: 利率组
(14, 9,  6, 0, NOW(), NOW()),  -- 固收4: 信用组
(15, 10, 7, 0, NOW(), NOW()),  -- 权益1: 权益部
(16, 10, 8, 0, NOW(), NOW()),  -- 权益1: 行业研究组
(17, 11, 7, 0, NOW(), NOW()),  -- 权益2: 权益部
(18, 12, 8, 0, NOW(), NOW()),  -- 权益3: 行业研究组
(19, 13, 9, 0, NOW(), NOW()),  -- 量化1: 量化部
(20, 14, 9, 0, NOW(), NOW());  -- 量化2: 量化部

INSERT INTO `t_sys_role_evt` (`id`, `name`, `parent_id`, `sort_order`, `enable`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`)
SELECT `id`, `name`, `parent_id`, `sort_order`, `enable`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `t_sys_role`;

INSERT INTO `t_sys_user_evt` (`id`, `name`, `user_name`, `dr`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`)
SELECT `id`, `name`, `user_name`, `dr`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `t_sys_user`;

INSERT INTO `t_sys_user_role_evt` (`id`, `user_id`, `role_id`, `dr`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`)
SELECT `id`, `user_id`, `role_id`, `dr`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `t_sys_user_role`;

-- ----------------------------------------------------------------------------
-- 3. 初始化投资池权限配置
-- ----------------------------------------------------------------------------
-- 权限说明：
--   permission_type: viewable=可查看 / adjustable=可调整 / excel_importable=可Excel导入
--   subject_type:    role=角色 / user=人员
-- 叶子节点 pool_id: 2-6（信用债子库） / 7（境外债） / 8（转债） / 10-14（专户产品子库） / 15（禁投池）
-- ----------------------------------------------------------------------------
INSERT INTO `ip_pool_permission` (`pool_id`, `permission_type`, `subject_type`, `subject_id`, `subject_name`, `is_deleted`, `crte_time`, `updt_time`) VALUES
-- 管理员（user_id=1001）：对所有叶子节点拥有 adjustable + excel_importable 权限
(2,  'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(3,  'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(4,  'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(5,  'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(6,  'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(7,  'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(8,  'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(10, 'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(11, 'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(12, 'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(13, 'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(14, 'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(15, 'adjustable',      'user', 1001, '管理员', 0, NOW(), NOW()),
(2,  'excel_importable','user', 1001, '管理员', 0, NOW(), NOW()),
(3,  'excel_importable','user', 1001, '管理员', 0, NOW(), NOW()),
(10, 'excel_importable','user', 1001, '管理员', 0, NOW(), NOW()),
(11, 'excel_importable','user', 1001, '管理员', 0, NOW(), NOW()),
-- 信用研究组（role_id=2）：信用债子库 + 禁投池 可调整
(2,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(3,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(4,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(5,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(6,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(15, 'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
-- 利率研究组（role_id=3）：境外债库可调整
(7,  'adjustable',      'role', 3, '利率研究组', 0, NOW(), NOW()),
-- 固收部（role_id=4）：信用债 + 专户产品 + 境外债 可调整
(2,  'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(3,  'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(7,  'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(10, 'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(11, 'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
-- 权益部（role_id=7）：转债库可调整
(8,  'adjustable',      'role', 7, '权益部', 0, NOW(), NOW()),
-- 量化部（role_id=9）：全部可查看（只读）
(1,  'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(7,  'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(8,  'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(9,  'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(15, 'viewable',        'role', 9, '量化部', 0, NOW(), NOW());

INSERT INTO `ip_pool_permission_evt` (
    `pool_id`, `permission_type`, `subject_type`, `subject_id`, `subject_name`,
    `is_deleted`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`
)
SELECT `pool_id`, `permission_type`, `subject_type`, `subject_id`, `subject_name`,
       `is_deleted`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `ip_pool_permission`;
