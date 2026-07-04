-- ============================================================
-- AIS 投资分析库 - 演示数据初始化脚本
-- 前提：需先执行 ais_inv_analysis_schema.sql 完成建表
-- 说明：初始化主体评级、角色、用户及用户角色关联样例数据
-- ============================================================

USE `ais_inv_analysis`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 清空业务表
-- ----------------------------------------------------------------------------
TRUNCATE TABLE `t_inv_grade_result`;
TRUNCATE TABLE `t_sys_user_role`;
TRUNCATE TABLE `t_inv_company`;
TRUNCATE TABLE `t_sys_user`;
TRUNCATE TABLE `t_sys_role`;

-- ----------------------------------------------------------------------------
-- 主体基础信息样例
-- ----------------------------------------------------------------------------
INSERT INTO `t_inv_company` (
    `id`
    ,`business_scope`
    ,`city_id`
    ,`code`
    ,`country_id`
    ,`county_id`
    ,`description`
    ,`full_name`
    ,`industry_id`
    ,`legaler`
    ,`province_id`
    ,`reg_address`
    ,`reg_capital`
    ,`short_name`
    ,`stock_sty`
    ,`village_id`
    ,`web_site`
    ,`es_type`
    ,`es_index`
    ,`es_score`
    ,`wind_code`
    ,`industry`
    ,`ts`
    ,`fundDate`
    ,`templateId`
    ,`researcher`
) VALUES
(1, '交通基础设施投资、建设与运营管理。', '320100', 'C10001', 'CN', '320102',
 '省属交通投资平台，资产规模较大，融资渠道稳定。', '某省交通投资集团有限公司', '4800', '张明',
 '320000', '某省南京市玄武区中山路 100 号', '5000000万元', '某省交投', '未上市', '320102001',
 'https://www.demo-traffic.example', 'state_owned', 'AA+', '88.50', 'C10001.WI', '交通运输',
 '2026-06-01 09:00:00', '2001-05-18', 'T1', '研究员1'),
(2, '城市基础设施建设、土地整理、保障房建设及配套服务。', '330100', 'C10002', 'CN', '330106',
 '区域城投主体，承担区域基础设施建设职能。', '某城市投资开发建设有限公司', '4700', '李华',
 '330000', '某省杭州市西湖区文三路 88 号', '1200000万元', '某城投', '未上市', '330106002',
 'https://www.demo-city.example', 'city_investment', 'AA', '82.10', 'C10002.WI', '建筑与工程',
 '2026-06-02 10:30:00', '2008-09-26', 'T1', '研究员2'),
(3, '房地产开发经营、物业管理、商业运营。', '440300', 'C10003', 'CN', '440305',
 '民营地产主体，需关注销售回款和短期债务压力。', '某地产集团股份有限公司', '7000', '王强',
 '440000', '广东省深圳市南山区科技园 66 号', '800000万元', '某地产', 'A股', '440305003',
 'https://www.demo-realestate.example', 'private_enterprise', 'BBB+', '63.80', '000001.SZ', '房地产',
 '2026-06-03 14:20:00', '1998-11-12', 'T2', '研究员3');

-- ----------------------------------------------------------------------------
-- 主体评级结果样例
-- ----------------------------------------------------------------------------
INSERT INTO `t_inv_grade_result` (
    `id`
    ,`company_id`
    ,`area_id`
    ,`template_id`
    ,`temp_id`
    ,`time`
    ,`cal_score`
    ,`total_score`
    ,`other_score`
    ,`mapping_step`
    ,`steps`
    ,`final_step`
    ,`deal_user_id`
    ,`ts`
    ,`adjust_note`
    ,`template_type`
    ,`weighted_score`
    ,`observe_type`
) VALUES
(1, 1, 320000, 101, 1001, '2026-06-01 10:00:00', 88.50, 90.00,
 '{"external_rating":"AAA","finance_score":89.2,"industry_score":86.5}', '初评映射', 3, '一级复核',
 1001, '2026-06-01 10:05:00', '省属平台资质较强，维持高等级观察。', 1, 89.10, 'normal'),
(2, 2, 330000, 101, 1001, '2026-06-02 11:00:00', 82.10, 84.00,
 '{"external_rating":"AA+","finance_score":80.6,"industry_score":83.1}', '区域映射', 4, '二级复核',
 1002, '2026-06-02 11:15:00', '区域财政实力较好，关注有息债务增长。', 1, 82.80, 'watch'),
(3, 3, 440000, 102, 2001, '2026-06-03 15:00:00', 63.80, 66.00,
 '{"external_rating":"AA","finance_score":61.4,"industry_score":58.2}', '压力映射', 5, '风险复核',
 1003, '2026-06-03 15:30:00', '地产销售回款承压，纳入重点观察。', 2, 64.20, 'focus');

-- ----------------------------------------------------------------------------
-- 角色和用户样例
-- ----------------------------------------------------------------------------
-- 角色树：研究部 > 信用研究组 > 利率研究组
INSERT INTO `t_sys_role` (
    `id`
    ,`name`
    ,`enable`
    ,`ts`
    ,`code`
    ,`memo`
    ,`parent_id`
    ,`inherit_role_ids`
) VALUES
(1, '研究部',      1, NOW(), 'ROLE_RESEARCH',       '研究部门',       NULL, NULL),
(2, '信用研究组',  1, NOW(), 'ROLE_CREDIT_RESEARCH','信用研究小组',   1,    NULL),
(3, '利率研究组',  1, NOW(), 'ROLE_RATE_RESEARCH',  '利率研究小组',   2,    NULL),
(4, '固收部',      1, NOW(), 'ROLE_FIXED_INCOME',   '固定收益部门',   NULL, NULL),
(5, '利率组',      1, NOW(), 'ROLE_RATE',           '利率小组',       4,    NULL),
(6, '信用组',      1, NOW(), 'ROLE_CREDIT',         '信用小组',       4,    NULL),
(7, '权益部',      1, NOW(), 'ROLE_EQUITY',         '权益部门',       NULL, NULL),
(8, '行业研究组',  1, NOW(), 'ROLE_INDUSTRY',       '行业研究小组',   7,    NULL),
(9, '量化部',      1, NOW(), 'ROLE_QUANT',          '量化部门',       NULL, NULL),
(10, '风险管理部', 1, NOW(), 'ROLE_RISK',           '风险管理部门',   NULL, NULL);

-- 用户
INSERT INTO `t_sys_user` (
    `id`
    ,`user_id`
    ,`name`
    ,`user_name`
    ,`user_eng_name`
    ,`ts`
    ,`dr`
    ,`pwd`
    ,`oapwd`
    ,`ORGID`
    ,`TEL`
    ,`MOBILE`
    ,`EMAIL`
) VALUES
(1,    1,    '研究员1', 'yanjiuyuan1',      'researcher1',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan1@example.com'),
(2,    2,    '研究员2', 'yanjiuyuan2',      'researcher2',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan2@example.com'),
(3,    3,    '研究员3', 'yanjiuyuan3',      'researcher3',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan3@example.com'),
(4,    4,    '研究员4', 'yanjiuyuan4',      'researcher4',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan4@example.com'),
(5,    5,    '研究员5', 'yanjiuyuan5',      'researcher5',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan5@example.com'),
(6,    6,    '固收1',   'gushou1',          'fixed_income1',        NOW(), 0, '123456', NULL, 'ORG_FIXED',    NULL, NULL, 'gushou1@example.com'),
(7,    7,    '固收2',   'gushou2',          'fixed_income2',        NOW(), 0, '123456', NULL, 'ORG_FIXED',    NULL, NULL, 'gushou2@example.com'),
(8,    8,    '固收3',   'gushou3',          'fixed_income3',        NOW(), 0, '123456', NULL, 'ORG_FIXED',    NULL, NULL, 'gushou3@example.com'),
(9,    9,    '固收4',   'gushou4',          'fixed_income4',        NOW(), 0, '123456', NULL, 'ORG_FIXED',    NULL, NULL, 'gushou4@example.com'),
(10,   10,   '权益1',   'quanyi1',          'equity1',              NOW(), 0, '123456', NULL, 'ORG_EQUITY',   NULL, NULL, 'quanyi1@example.com'),
(11,   11,   '权益2',   'quanyi2',          'equity2',              NOW(), 0, '123456', NULL, 'ORG_EQUITY',   NULL, NULL, 'quanyi2@example.com'),
(12,   12,   '权益3',   'quanyi3',          'equity3',              NOW(), 0, '123456', NULL, 'ORG_EQUITY',   NULL, NULL, 'quanyi3@example.com'),
(13,   13,   '量化1',   'lianghua1',        'quant1',               NOW(), 0, '123456', NULL, 'ORG_QUANT',    NULL, NULL, 'lianghua1@example.com'),
(14,   14,   '量化2',   'lianghua2',        'quant2',               NOW(), 0, '123456', NULL, 'ORG_QUANT',    NULL, NULL, 'lianghua2@example.com'),
(15,   15,   '风控经理', 'fengkongjingli',   'risk_manager',         NOW(), 0, '123456', NULL, 'ORG_RISK',     NULL, NULL, 'fengkongjingli@example.com'),
(1001, 1001, '管理员',   'admin',            'admin',                NOW(), 0, '123456', NULL, 'ORG_ADMIN',    NULL, NULL, 'admin@example.com');

INSERT INTO `t_sys_user_role` (
    `id`
    ,`role_id`
    ,`user_id`
    ,`ts`
) VALUES
(1,  1,  1,  NOW()),  -- 研究员1: 研究部
(2,  2,  1,  NOW()),  -- 研究员1: 信用研究组
(3,  2,  2,  NOW()),  -- 研究员2: 信用研究组
(4,  3,  2,  NOW()),  -- 研究员2: 利率研究组
(5,  1,  3,  NOW()),  -- 研究员3: 研究部
(6,  3,  3,  NOW()),  -- 研究员3: 利率研究组
(7,  2,  4,  NOW()),  -- 研究员4: 信用研究组
(8,  3,  5,  NOW()),  -- 研究员5: 利率研究组
(9,  4,  6,  NOW()),  -- 固收1: 固收部
(10, 5,  6,  NOW()),  -- 固收1: 利率组
(11, 4,  7,  NOW()),  -- 固收2: 固收部
(12, 6,  7,  NOW()),  -- 固收2: 信用组
(13, 5,  8,  NOW()),  -- 固收3: 利率组
(14, 6,  9,  NOW()),  -- 固收4: 信用组
(15, 7,  10, NOW()),  -- 权益1: 权益部
(16, 8,  10, NOW()),  -- 权益1: 行业研究组
(17, 7,  11, NOW()),  -- 权益2: 权益部
(18, 8,  12, NOW()),  -- 权益3: 行业研究组
(19, 9,  13, NOW()),  -- 量化1: 量化部
(20, 9,  14, NOW()),  -- 量化2: 量化部
(21, 10, 15, NOW()); -- 风控经理: 风险管理部

SET FOREIGN_KEY_CHECKS = 1;
