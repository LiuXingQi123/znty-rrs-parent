-- ============================================================
-- znty-rrs 信用债期限-主体内评分档-投资池关系 - 演示数据
-- MySQL version: 8.0.33
-- 说明：初始化信用债大库评级准入矩阵配置
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `credit_bond_pool_grade_rule`;
TRUNCATE TABLE `credit_bond_inner_rating_grade`;
TRUNCATE TABLE `credit_bond_term_bucket`;

-- ----------------------------------------------------------------------------
-- 1. 期限分组
-- ----------------------------------------------------------------------------
INSERT INTO `credit_bond_term_bucket` (
    `id`
    ,`bucket_code`
    ,`bucket_name`
    ,`min_term_year`
    ,`min_inclusive`
    ,`max_term_year`
    ,`max_inclusive`
    ,`expression_text`
    ,`sort_no`
    ,`enabled`
    ,`crte_time`
    ,`updt_time`
) VALUES
(1, 'GT_5',      '期限>5',       5.0000, 0, NULL,   NULL, 'remain_term_year > 5',                              1, 1, NOW(), NOW()),
(2, 'GT_3_LE_5', '5≥期限>3',    3.0000, 0, 5.0000, 1,    'remain_term_year > 3 AND remain_term_year ≤ 5', 2, 1, NOW(), NOW()),
(3, 'GT_1_LE_3', '3≥期限>1',    1.0000, 0, 3.0000, 1,    'remain_term_year > 1 AND remain_term_year ≤ 3', 3, 1, NOW(), NOW()),
(4, 'LE_1',      '1≥期限',      NULL,   NULL, 1.0000, 1, 'remain_term_year ≤ 1',                             4, 1, NOW(), NOW());

-- ----------------------------------------------------------------------------
-- 2. 主体内评分档
-- ----------------------------------------------------------------------------
INSERT INTO `credit_bond_inner_rating_grade` (
    `id`
    ,`grade_code`
    ,`grade_name`
    ,`sort_no`
    ,`enabled`
    ,`crte_time`
    ,`updt_time`
) VALUES
(1, '1',  '1',  1, 1, NOW(), NOW()),
(2, '2+', '2+', 2, 1, NOW(), NOW()),
(3, '2',  '2',  3, 1, NOW(), NOW()),
(4, '2-', '2-', 4, 1, NOW(), NOW()),
(5, '3+', '3+', 5, 1, NOW(), NOW()),
(6, '3',  '3',  6, 1, NOW(), NOW()),
(7, '3-', '3-', 7, 1, NOW(), NOW()),
(8, '4',  '4',  8, 1, NOW(), NOW());

-- ----------------------------------------------------------------------------
-- 3. 期限分组 + 主体内评分档 + 信用债库关系
--    pool_id：2=一级库 / 3=二级库 / 4=三级库 / 5=四级库 / 6=五级库
-- ----------------------------------------------------------------------------
INSERT INTO `credit_bond_pool_grade_rule` (
    `id`
    ,`term_bucket_id`
    ,`inner_rating_grade_id`
    ,`pool_id`
    ,`pool_code_snapshot`
    ,`pool_name_snapshot`
    ,`enabled`
    ,`sort_no`
    ,`crte_time`
    ,`updt_time`
) VALUES
(1,  1, 1, 2, 'credit_bond_level_1', '一级库', 1, 1, NOW(), NOW()),
(2,  2, 1, 2, 'credit_bond_level_1', '一级库', 1, 1, NOW(), NOW()),
(3,  3, 1, 2, 'credit_bond_level_1', '一级库', 1, 1, NOW(), NOW()),
(4,  4, 1, 2, 'credit_bond_level_1', '一级库', 1, 1, NOW(), NOW()),

(5,  1, 2, 3, 'credit_bond_level_2', '二级库', 1, 1, NOW(), NOW()),
(6,  2, 2, 2, 'credit_bond_level_1', '一级库', 1, 1, NOW(), NOW()),
(7,  3, 2, 2, 'credit_bond_level_1', '一级库', 1, 1, NOW(), NOW()),
(8,  4, 2, 2, 'credit_bond_level_1', '一级库', 1, 1, NOW(), NOW()),

(9,  1, 3, 3, 'credit_bond_level_2', '二级库', 1, 1, NOW(), NOW()),
(10, 2, 3, 3, 'credit_bond_level_2', '二级库', 1, 1, NOW(), NOW()),
(11, 3, 3, 3, 'credit_bond_level_2', '二级库', 1, 1, NOW(), NOW()),
(12, 4, 3, 3, 'credit_bond_level_2', '二级库', 1, 1, NOW(), NOW()),

(13, 1, 4, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(14, 1, 4, 5, 'credit_bond_level_4', '四级库', 1, 2, NOW(), NOW()),
(15, 1, 4, 4, 'credit_bond_level_3', '三级库', 1, 3, NOW(), NOW()),
(16, 1, 4, 3, 'credit_bond_level_2', '二级库', 1, 4, NOW(), NOW()),
(17, 2, 4, 4, 'credit_bond_level_3', '三级库', 1, 1, NOW(), NOW()),
(18, 3, 4, 3, 'credit_bond_level_2', '二级库', 1, 1, NOW(), NOW()),
(19, 4, 4, 3, 'credit_bond_level_2', '二级库', 1, 1, NOW(), NOW()),

(20, 1, 5, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(21, 2, 5, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(22, 2, 5, 5, 'credit_bond_level_4', '四级库', 1, 2, NOW(), NOW()),
(23, 2, 5, 4, 'credit_bond_level_3', '三级库', 1, 3, NOW(), NOW()),
(24, 3, 5, 4, 'credit_bond_level_3', '三级库', 1, 1, NOW(), NOW()),
(25, 4, 5, 4, 'credit_bond_level_3', '三级库', 1, 1, NOW(), NOW()),

(26, 1, 6, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(27, 2, 6, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(28, 3, 6, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(29, 4, 6, 5, 'credit_bond_level_4', '四级库', 1, 1, NOW(), NOW()),
(30, 4, 6, 6, 'credit_bond_level_5', '五级库', 1, 2, NOW(), NOW()),

(31, 1, 7, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(32, 2, 7, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(33, 3, 7, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(34, 4, 7, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),

(35, 1, 8, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(36, 2, 8, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(37, 3, 8, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW()),
(38, 4, 8, 6, 'credit_bond_level_5', '五级库', 1, 1, NOW(), NOW());
