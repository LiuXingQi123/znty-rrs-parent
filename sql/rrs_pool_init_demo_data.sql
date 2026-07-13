-- ============================================================
-- znty-rrs 投资池初始化 - 演示数据脚本
-- MySQL version: 8.0.28
-- 说明：首次部署执行，插入投资池相关测试数据
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 清空所有业务表
-- ----------------------------------------------------------------------------
TRUNCATE TABLE `ip_pool_permission_evt`;
TRUNCATE TABLE `ip_pool_auto_rule_evt`;
TRUNCATE TABLE `ip_pool_relation_evt`;
TRUNCATE TABLE `ip_investment_pool_evt`;
TRUNCATE TABLE `ip_pool_permission`;
TRUNCATE TABLE `ip_pool_auto_rule`;
TRUNCATE TABLE `ip_pool_relation`;
TRUNCATE TABLE `ip_investment_pool`;

-- 投资池（22 池，含校验配置字段）
INSERT INTO `ip_investment_pool` (
    `id`, `parent_id`, `pool_code`, `pool_name`, `pool_type`, `pool_level`,
    `market_codes`, `variety_codes`, `description`, `hs_pool_name`,
    `in_flow_id`, `in_flow_key`, `in_flow_name`,
    `out_flow_id`, `out_flow_key`, `out_flow_name`,
    `simple_in_flow_id`, `simple_in_flow_key`, `simple_in_flow_name`,
    `batch_in_flow_id`, `batch_in_flow_key`, `batch_in_flow_name`,
    `in_report_restriction`, `out_report_restriction`,
    `max_capacity`, `lock_flag`, `frozen_period_in`, `grade_astrict`,
    `industry_code`, `industry_exponent`, `fund_rate_limit`, `open_day_adjust`,
    `outer_sort`, `inner_sort`, `status`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
(1, NULL, 'credit_bond_root', '信用债大库', 'credit_bond', 1, '["SSE","SZSE","CIBM"]', '["bond"]', '信用债大库根池', '信用债大库', 101, 'bond:standard-upgrade', '债券标准升库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'internal', 'internal', 100, 0, 0, NULL, NULL, 0, NULL, 0, 1, 1, 'enabled', 0, NOW(), NOW()),
(2, 1, 'credit_bond_level_1', '一级库', 'credit_bond', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '信用债一级库', '一级库', 101, 'bond:standard-upgrade', '债券标准升库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 50, 0, 90, NULL, NULL, 0, NULL, 0, 1, 1, 'enabled', 0, NOW(), NOW()),
(3, 1, 'credit_bond_level_2', '二级库', 'credit_bond', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '信用债二级库', '二级库', 102, 'bond:standard-downgrade', '债券标准降库流程', 114, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 50, 0, 365, NULL, NULL, 0, NULL, 0, 1, 2, 'enabled', 0, NOW(), NOW()),
(4, 1, 'credit_bond_level_3', '三级库', 'credit_bond', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '信用债三级库', '三级库', 101, 'bond:standard-upgrade', '债券标准升库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 1, 0, 90, NULL, NULL, 0, NULL, 0, 1, 3, 'enabled', 0, NOW(), NOW()),
(5, 1, 'credit_bond_level_4', '四级库', 'credit_bond', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '信用债四级库', '四级库', 102, 'bond:standard-downgrade', '债券标准降库流程', 114, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 50, 1, 90, NULL, NULL, 0, NULL, 0, 1, 4, 'enabled', 0, NOW(), NOW()),
(6, 1, 'credit_bond_level_5', '五级库', 'credit_bond', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '信用债五级库', '五级库', 102, 'bond:standard-downgrade', '债券标准降库流程', 114, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 100, 0, 90, NULL, '制造业', 0, NULL, 0, 1, 5, 'enabled', 0, NOW(), NOW()),
(7, NULL, 'offshore_bond_root', '境外债库', 'offshore_bond', 1, '["CIBM"]', '["bond"]', '境外债库', '境外债库', 105, 'bond:fast-inbound', '债券快速入库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'any', 'any', 50, 0, 90, NULL, NULL, 0, NULL, 0, 2, 1, 'enabled', 0, NOW(), NOW()),
(8, NULL, 'convertible_bond_root', '转债库', 'convertible_bond', 1, '["SSE","SZSE"]', '["bond"]', '转债库', '转债库', 108, 'bond:special-inbound', '债券特殊策略入库流程', 116, 'bond:special-outbound', '债券特殊策略出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'any', 'any', 50, 0, 0, NULL, NULL, 0, NULL, 1, 3, 1, 'enabled', 0, NOW(), NOW()),
(9, NULL, 'special_account_root', '专户产品', 'special_account', 1, '["SSE","SZSE","CIBM"]', '["bond"]', '专户产品根池', '专户产品', 101, 'bond:standard-upgrade', '债券标准升库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'internal', 'internal', 100, 0, 0, NULL, NULL, 0, NULL, 0, 4, 1, 'enabled', 0, NOW(), NOW()),
(10, 9, 'special_account_level_1', '一级库', 'special_account', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '专户一级库', '专户一级库', 101, 'bond:standard-upgrade', '债券标准升库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 50, 0, 90, NULL, NULL, 0, NULL, 0, 4, 1, 'enabled', 0, NOW(), NOW()),
(11, 9, 'special_account_level_2', '二级库', 'special_account', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '专户二级库', '专户二级库', 102, 'bond:standard-downgrade', '债券标准降库流程', 114, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 50, 0, 90, NULL, NULL, 0, NULL, 0, 4, 2, 'enabled', 0, NOW(), NOW()),
(12, 9, 'special_account_level_3', '三级库', 'special_account', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '专户三级库', '专户三级库', 101, 'bond:standard-upgrade', '债券标准升库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 50, 0, 90, NULL, NULL, 0, NULL, 0, 4, 3, 'enabled', 0, NOW(), NOW()),
(13, 9, 'special_account_level_4', '四级库', 'special_account', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '专户四级库', '专户四级库', 102, 'bond:standard-downgrade', '债券标准降库流程', 114, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 50, 0, 90, NULL, NULL, 0, NULL, 0, 4, 4, 'enabled', 0, NOW(), NOW()),
(14, 9, 'special_account_level_5', '五级库', 'special_account', 2, '["SSE","SZSE","CIBM"]', '["bond"]', '专户五级库', '专户五级库', 102, 'bond:standard-downgrade', '债券标准降库流程', 114, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'none', 'none', 100, 0, 90, NULL, NULL, 0, NULL, 0, 4, 5, 'enabled', 0, NOW(), NOW()),
(15, NULL, 'forbidden_root', '禁投池', 'forbidden', 1, '["SSE","SZSE","CIBM"]', '["bond"]', '禁投池', '禁投池', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'none', 'none', 100, 0, 0, NULL, NULL, 0, NULL, 0, 5, 1, 'enabled', 0, NOW(), NOW()),
(16, NULL, 'observe_root', '观察池', 'observe', 1, '["SSE","SZSE","CIBM"]', '["bond"]', '观察池', '观察池', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'none', 'none', 100, 0, 0, NULL, NULL, 0, NULL, 0, 6, 1, 'enabled', 0, NOW(), NOW()),
(17, NULL, 'pledge_blacklist_root', '黑名单质押库', 'blacklist', 1, '["SSE","SZSE","CIBM"]', '["bond"]', '黑名单质押库', '黑名单质押库', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'none', 'none', 100, 0, 0, NULL, NULL, 0, NULL, 0, 7, 1, 'enabled', 0, NOW(), NOW()),
(18, NULL, 'crmw_root', 'CRMW库', 'crmw', 1, '["SSE","SZSE","CIBM"]', '["bond"]', 'CRMW库根池', 'CRMW库', 105, 'bond:fast-inbound', '债券快速入库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'internal', 'internal', 50, 0, 0, NULL, NULL, 0, NULL, 0, 8, 1, 'enabled', 0, NOW(), NOW()),
(19, 18, 'crmw_core_pool', 'CRMW核心库', 'crmw', 2, '["SSE","SZSE","CIBM"]', '["bond"]', 'CRMW核心库', 'CRMW核心库', 105, 'bond:fast-inbound', '债券快速入库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'internal', 'internal', 50, 0, 90, NULL, NULL, 0, NULL, 0, 8, 1, 'enabled', 0, NOW(), NOW()),
(20, 18, 'crmw_watch_pool', 'CRMW关注库', 'crmw', 2, '["SSE","SZSE","CIBM"]', '["bond"]', 'CRMW关注库', 'CRMW关注库', 105, 'bond:fast-inbound', '债券快速入库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'internal', 'internal', 50, 0, 90, NULL, NULL, 0, NULL, 0, 8, 2, 'enabled', 0, NOW(), NOW()),
(21, 18, 'crmw_exit_watch_pool', 'CRMW退出观察库', 'crmw', 2, '["SSE","SZSE","CIBM"]', '["bond"]', 'CRMW退出观察库', 'CRMW退出观察库', 105, 'bond:fast-inbound', '债券快速入库流程', 113, 'bond:fast-outbound', '债券快速出库流程', 105, 'bond:fast-inbound', '债券快速入库流程', 107, 'bond:batch-inbound', '债券批量入库流程', 'internal', 'internal', 50, 0, 90, NULL, NULL, 0, NULL, 0, 8, 3, 'enabled', 0, NOW(), NOW()),
(22, NULL, 'fund_pool_root', '基金池', 'fund', 1, '["SSE","SZSE"]', '["fund"]', '基金池（基金评分校验）', '基金池', 109, 'fund:fast-inbound', '基金快速入库流程', 117, 'fund:fast-outbound', '基金快速出库流程', 109, 'fund:fast-inbound', '基金快速入库流程', 111, 'fund:batch-inbound', '基金批量入库流程', 'none', 'none', 50, 0, 0, NULL, NULL, 0, '3<=#rate<=8', 0, 9, 1, 'enabled', 0, NOW(), NOW());

-- 池关系（全部 9 种 relation_type）
INSERT INTO `ip_pool_relation` (`pool_id`, `relation_type`, `relation_pool_id`, `relation_pool_name`, `sort_order`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(3, 'source', 2, '一级库', 1, 0, NOW(), NOW()),
(20, 'source', 19, 'CRMW核心库', 1, 0, NOW(), NOW()),
(21, 'source', 20, 'CRMW关注库', 1, 0, NOW(), NOW()),
(4, 'in_restrict', 15, '禁投池', 1, 0, NOW(), NOW()),
(4, 'out_restrict', 15, '禁投池', 1, 0, NOW(), NOW()),
(7, 'in_linked', 2, '一级库', 1, 0, NOW(), NOW()),
(7, 'out_linked', 2, '一级库', 1, 0, NOW(), NOW()),
(2, 'in_mutex', 3, '二级库', 1, 0, NOW(), NOW()), (3, 'in_mutex', 2, '一级库', 1, 0, NOW(), NOW()),
(2, 'in_mutex', 4, '三级库', 2, 0, NOW(), NOW()), (4, 'in_mutex', 2, '一级库', 2, 0, NOW(), NOW()),
(3, 'in_mutex', 4, '三级库', 3, 0, NOW(), NOW()), (4, 'in_mutex', 3, '二级库', 3, 0, NOW(), NOW()),
(2, 'in_mutex', 5, '四级库', 4, 0, NOW(), NOW()), (5, 'in_mutex', 2, '一级库', 4, 0, NOW(), NOW()),
(2, 'in_mutex', 6, '五级库', 5, 0, NOW(), NOW()), (6, 'in_mutex', 2, '一级库', 5, 0, NOW(), NOW()),
(3, 'in_mutex', 5, '四级库', 6, 0, NOW(), NOW()), (5, 'in_mutex', 3, '二级库', 6, 0, NOW(), NOW()),
(3, 'in_mutex', 6, '五级库', 7, 0, NOW(), NOW()), (6, 'in_mutex', 3, '二级库', 7, 0, NOW(), NOW()),
(4, 'in_mutex', 5, '四级库', 8, 0, NOW(), NOW()), (5, 'in_mutex', 4, '三级库', 8, 0, NOW(), NOW()),
(4, 'in_mutex', 6, '五级库', 9, 0, NOW(), NOW()), (6, 'in_mutex', 4, '三级库', 9, 0, NOW(), NOW()),
(5, 'in_mutex', 6, '五级库', 10, 0, NOW(), NOW()), (6, 'in_mutex', 5, '四级库', 10, 0, NOW(), NOW()),
(3, 'out_mutex', 6, '五级库', 1, 0, NOW(), NOW()),
(8, 'in_soft_restrict', 15, '禁投池', 1, 0, NOW(), NOW()),
(8, 'out_soft_restrict', 15, '禁投池', 1, 0, NOW(), NOW()),
(20, 'in_soft_restrict', 16, '观察池', 1, 0, NOW(), NOW()),
(21, 'out_soft_restrict', 16, '观察池', 1, 0, NOW(), NOW());

-- 自动调库规则
INSERT INTO `ip_pool_auto_rule` (`id`, `pool_id`, `rule_type`, `rule_id`, `rule_desc`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(1, 2, 'auto_out', 1, '到期证券自动调出（试点）', 0, NOW(), NOW()),
(2, 3, 'auto_out', 1, '到期证券自动调出', 0, NOW(), NOW()),
(3, 15, 'auto_in', 5, '风险预警规则触发列入禁投池', 0, NOW(), NOW()),
(4, 15, 'auto_out', 5, '禁投池自动调出', 0, NOW(), NOW());

INSERT INTO `ip_pool_permission` (`pool_id`, `permission_type`, `handler_type`, `handler_id`, `handler_name`, `is_deleted`, `crte_time`, `updt_time`) VALUES
-- 管理员（user_id=1）：对所有叶子节点拥有 adjustable + excel_importable 权限
(2,  'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(3,  'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(4,  'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(5,  'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(6,  'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(7,  'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(8,  'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(10, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(11, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(12, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(13, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(14, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(15, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(16, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(17, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(18, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(19, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(20, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(21, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW()),
(2,  'excel_importable','user', 1, '管理员', 0, NOW(), NOW()),
(3,  'excel_importable','user', 1, '管理员', 0, NOW(), NOW()),
(10, 'excel_importable','user', 1, '管理员', 0, NOW(), NOW()),
(11, 'excel_importable','user', 1, '管理员', 0, NOW(), NOW()),
-- 信用研究组（role_id=2）：信用债子库 + 禁投池 可调整
(2,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(3,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(4,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(5,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(6,  'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
(15, 'adjustable',      'role', 2, '信用研究组', 0, NOW(), NOW()),
-- 利率研究组（role_id=3）：境外债库可调整
(7,  'adjustable',      'role', 3, '利率研究组', 0, NOW(), NOW()),
-- 固收部（role_id=4）：信用债 + 专户产品 + 境外债 + CRMW库 可调整
(2,  'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(3,  'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(7,  'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(10, 'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(11, 'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(18, 'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(19, 'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(20, 'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
(21, 'adjustable',      'role', 4, '固收部', 0, NOW(), NOW()),
-- 权益部（role_id=7）：转债库可调整
(8,  'adjustable',      'role', 7, '权益部', 0, NOW(), NOW()),
-- 量化部（role_id=9）：全部可查看（只读）
(1,  'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(7,  'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(8,  'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(9,  'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(15, 'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(16, 'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
(17, 'viewable',        'role', 9, '量化部', 0, NOW(), NOW()),
-- 风险管理部：禁投池可调整，全部一级目录可查看
(15, 'adjustable',      'role', 10, '风险管理部', 0, NOW(), NOW()),
(16, 'adjustable',      'role', 10, '风险管理部', 0, NOW(), NOW()),
(17, 'adjustable',      'role', 10, '风险管理部', 0, NOW(), NOW()),
(1,  'viewable',        'role', 10, '风险管理部', 0, NOW(), NOW()),
(7,  'viewable',        'role', 10, '风险管理部', 0, NOW(), NOW()),
(8,  'viewable',        'role', 10, '风险管理部', 0, NOW(), NOW()),
(9,  'viewable',        'role', 10, '风险管理部', 0, NOW(), NOW()),
(22, 'adjustable',      'user', 1, '管理员', 0, NOW(), NOW());

-- 开放日配置
TRUNCATE TABLE `ip_pool_open_day`;
INSERT INTO `ip_pool_open_day` (`id`, `pool_id`, `begin_date`, `end_date`, `description`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(1, 8, '2026-01-01', '2026-06-30', '已过期开放区间（测试不在开放日校验）', 0, NOW(), NOW());
