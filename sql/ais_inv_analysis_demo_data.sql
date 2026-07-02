-- ============================================================
-- AIS 投资分析库 - 演示数据初始化脚本
-- 前提：需先执行 ais_inv_analysis_schema.sql 完成建表
-- 说明：初始化主体评级、角色、人员及人员角色关联样例数据
-- ============================================================

USE `ais_inv_analysis`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 清空业务表
-- ----------------------------------------------------------------------------
TRUNCATE TABLE `t_sys_user_role_evt`;
TRUNCATE TABLE `t_sys_user_evt`;
TRUNCATE TABLE `t_sys_role_evt`;
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
-- 角色和人员样例
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
(9, '量化部',       NULL, 9, 1, NOW(), NOW()),
(10, '风险管理部',  NULL, 10, 1, NOW(), NOW());

-- 人员
INSERT INTO `t_sys_user` (`id`, `name`, `user_name`, `dr`, `crte_time`, `updt_time`) VALUES
(1,  '研究员1', 'yanjiuyuan1', 0, NOW(), NOW()),
(2,  '研究员2', 'yanjiuyuan2', 0, NOW(), NOW()),
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
(15,  '风控经理', 'fengkongjingli', 0, NOW(), NOW()),
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
(20, 14, 9, 0, NOW(), NOW()),  -- 量化2: 量化部
(21, 15, 10, 0, NOW(), NOW()); -- 风控经理: 风险管理部

SET FOREIGN_KEY_CHECKS = 1;
