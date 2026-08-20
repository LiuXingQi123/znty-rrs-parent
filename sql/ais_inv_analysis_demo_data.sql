-- ============================================================
-- AIS 投资分析库 - 演示数据初始化脚本
-- MySQL version: 8.0.33
-- 前提：需先执行 ais_inv_analysis_schema.sql 完成建表
-- 说明：10 个主体 C10001～C10010，与 rrs_securityinfo.issuer_code / wind_cbondissuer.s_info_compcode 对齐
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ais_inv_analysis` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ais_inv_analysis`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 清空业务表
-- ----------------------------------------------------------------------------
TRUNCATE TABLE `t_inv_grade_result`;
TRUNCATE TABLE `t_sys_user_role`;
TRUNCATE TABLE `t_inv_company`;
TRUNCATE TABLE `t_sys_user`;
TRUNCATE TABLE `t_sys_role`;

-- ----------------------------------------------------------------------------
-- 主体基础信息样例（10 条）
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
 '320000', '某省南京市玄武区中山路 100 号', '5000000万元', '某交投集团', '未上市', '320102001',
 'https://www.demo-traffic.example', 'state_owned', 'AAA', '88.50', 'C10001.WI', '交通运输',
 '2026-06-01 09:00:00', '2001-05-18', 'T1', '研究员1'),
(2, '城市基础设施建设、土地整理、保障房建设及配套服务。', '330100', 'C10002', 'CN', '330106',
 '区域城投主体，承担区域基础设施建设职能。', '某城市投资开发建设有限公司', '4700', '李华',
 '330000', '某省杭州市西湖区文三路 88 号', '1200000万元', '某城投公司', '未上市', '330106002',
 'https://www.demo-city.example', 'city_investment', 'AA+', '82.10', 'C10002.WI', '建筑与工程',
 '2026-06-02 09:00:00', '2008-09-26', 'T1', '研究员2'),
(3, '煤炭开采、能源投资与运营。', '140100', 'C10003', 'CN', '140105',
 '省属能源主体，煤电一体化运营。', '某能源集团有限公司', '2100', '赵刚',
 '140000', '某省太原市迎泽区解放路 12 号', '3000000万元', '某能源集团', '未上市', '140105001',
 'https://www.demo-energy.example', 'state_owned', 'AAA', '86.20', 'C10003.WI', '能源',
 '2026-06-03 09:00:00', '1999-03-08', 'T1', '研究员1'),
(4, '交通基建投资、建设与运营。', '420100', 'C10004', 'CN', '420102',
 '区域基建平台，项目储备充足。', '某基础设施建设有限公司', '4700', '孙伟',
 '420000', '某省武汉市江汉区建设大道 58 号', '1800000万元', '某基建公司', '未上市', '420102001',
 'https://www.demo-infra.example', 'state_owned', 'AA', '79.40', 'C10004.WI', '建筑与工程',
 '2026-06-04 09:00:00', '2005-07-16', 'T1', '研究员2'),
(5, '房地产开发经营、物业管理、商业运营。', '440300', 'C10005', 'CN', '440305',
 '民营地产主体，需关注销售回款和短期债务压力。', '某地产集团股份有限公司', '7000', '王强',
 '440000', '广东省深圳市南山区科技园 66 号', '800000万元', '某地产公司', 'A股', '440305003',
 'https://www.demo-realestate.example', 'private_enterprise', 'AA+', '63.80', 'C10005.WI', '房地产',
 '2026-06-05 09:00:00', '1998-11-12', 'T2', '研究员3'),
(6, '电力生产、电网投资与综合能源服务。', '320100', 'C10006', 'CN', '320104',
 '电力央企子公司，现金流稳定。', '某电力股份有限公司', '2700', '周敏',
 '320000', '某省南京市鼓楼区中山北路 200 号', '2500000万元', '某电力公司', 'A股', '320104001',
 'https://www.demo-power.example', 'state_owned', 'AAA', '90.10', 'C10006.WI', '公用事业',
 '2026-06-06 09:00:00', '1997-01-20', 'T1', '研究员4'),
(7, '软件开发、信息技术服务。', '110100', 'C10007', 'CN', '110108',
 '民营科技主体，评级偏低，关注再融资能力。', '某科技股份有限公司', '4500', '吴磊',
 '110000', '北京市海淀区中关村大街 1 号', '200000万元', '某科技公司', 'A股', '110108001',
 'https://www.demo-tech.example', 'private_enterprise', 'A', '58.70', 'C10007.WI', '信息技术',
 '2026-06-07 09:00:00', '2012-04-09', 'T2', '研究员5'),
(8, '不良资产经营、资产管理与投资。', '310100', 'C10008', 'CN', '310115',
 '金融资产管理公司，资本实力较强。', '某资产管理股份有限公司', '4000', '郑浩',
 '310000', '上海市浦东新区世纪大道 100 号', '10000000万元', '某资产管理公司', '未上市', '310115001',
 'https://www.demo-amc.example', 'amc', 'AAA', '91.20', 'C10008.WI', '金融',
 '2026-06-08 09:00:00', '1999-10-18', 'T1', '研究员4'),
(9, '进出口贸易、供应链管理。', '310100', 'C10009', 'CN', '310101',
 '地方国有贸易主体，业务覆盖大宗商品。', '某国际贸易集团有限公司', '3500', '冯丽',
 '310000', '上海市黄浦区中山东一路 8 号', '1500000万元', '某国贸公司', '未上市', '310101001',
 'https://www.demo-trade.example', 'state_owned', 'AA+', '80.60', 'C10009.WI', '贸易',
 '2026-06-09 09:00:00', '2003-06-22', 'T1', '研究员2'),
(10, '综合金融服务、同业投融资。', '110100', 'C10010', 'CN', '110102',
 '金融机构，资本充足、评级稳定。', '某金融控股有限公司', '4000', '陈凯',
 '110000', '北京市西城区金融大街 15 号', '8000000万元', '某金融公司', '未上市', '110102001',
 'https://www.demo-finance.example', 'financial_institution', 'AAA', '92.00', 'C10010.WI', '金融',
 '2026-06-10 09:00:00', '1995-12-01', 'T1', '研究员1');

-- ----------------------------------------------------------------------------
-- 主体评级结果样例（与主体 1:1）
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
 '{"external_rating":"AAA","finance_score":87.3,"industry_score":86.5}', '初评映射', 3, '一级复核',
 2, '2026-06-01 10:05:00', '省属平台资质较强，维持高等级观察。', 1, 89.10, 'normal'),
(2, 2, 330000, 101, 1001, '2026-06-02 10:00:00', 82.10, 84.00,
 '{"external_rating":"AA+","finance_score":80.9,"industry_score":80.1}', '初评映射', 3, '一级复核',
 2, '2026-06-02 10:05:00', '区域财政实力较好，关注有息债务增长。', 1, 82.80, 'watch'),
(3, 3, 140000, 101, 1001, '2026-06-03 10:00:00', 86.20, 88.00,
 '{"external_rating":"AAA","finance_score":85.0,"industry_score":84.2}', '初评映射', 3, '一级复核',
 2, '2026-06-03 10:05:00', '煤电一体化经营稳定。', 1, 86.80, 'normal'),
(4, 4, 420000, 101, 1001, '2026-06-04 10:00:00', 79.40, 81.00,
 '{"external_rating":"AA","finance_score":78.2,"industry_score":77.4}', '初评映射', 3, '一级复核',
 2, '2026-06-04 10:05:00', '在建项目较多，关注资本开支。', 1, 80.10, 'watch'),
(5, 5, 440000, 101, 1001, '2026-06-05 10:00:00', 63.80, 66.00,
 '{"external_rating":"AA+","finance_score":62.6,"industry_score":61.8}', '初评映射', 3, '一级复核',
 2, '2026-06-05 10:05:00', '地产销售回款承压，纳入重点观察。', 1, 64.20, 'focus'),
(6, 6, 320000, 101, 1001, '2026-06-06 10:00:00', 90.10, 91.00,
 '{"external_rating":"AAA","finance_score":88.9,"industry_score":88.1}', '初评映射', 3, '一级复核',
 2, '2026-06-06 10:05:00', '电力央企子公司，现金流稳定。', 1, 90.40, 'normal'),
(7, 7, 110000, 101, 1001, '2026-06-07 10:00:00', 58.70, 61.00,
 '{"external_rating":"A","finance_score":57.5,"industry_score":56.7}', '初评映射', 3, '一级复核',
 2, '2026-06-07 10:05:00', '民营科技主体，再融资能力偏弱。', 1, 59.50, 'focus'),
(8, 8, 310000, 101, 1001, '2026-06-08 10:00:00', 91.20, 92.00,
 '{"external_rating":"AAA","finance_score":90.0,"industry_score":89.2}', '初评映射', 3, '一级复核',
 2, '2026-06-08 10:05:00', '金融资产管理公司资本实力较强。', 1, 91.50, 'normal'),
(9, 9, 310000, 101, 1001, '2026-06-09 10:00:00', 80.60, 82.00,
 '{"external_rating":"AA+","finance_score":79.4,"industry_score":78.6}', '初评映射', 3, '一级复核',
 2, '2026-06-09 10:05:00', '贸易业务受大宗商品价格波动影响。', 1, 81.10, 'watch'),
(10, 10, 110000, 101, 1001, '2026-06-10 10:00:00', 92.00, 93.00,
 '{"external_rating":"AAA","finance_score":90.8,"industry_score":90.0}', '初评映射', 3, '一级复核',
 2, '2026-06-10 10:05:00', '金融机构资本充足、评级稳定。', 1, 92.30, 'normal');

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
(1,    1,    '管理员',   'admin',            'admin',                NOW(), 0, '123456', NULL, 'ORG_ADMIN',    NULL, NULL, 'admin@example.com'),
(2,    2,    '研究员1', 'yanjiuyuan1',      'researcher1',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan1@example.com'),
(3,    3,    '研究员2', 'yanjiuyuan2',      'researcher2',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan2@example.com'),
(4,    4,    '研究员3', 'yanjiuyuan3',      'researcher3',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan3@example.com'),
(5,    5,    '研究员4', 'yanjiuyuan4',      'researcher4',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan4@example.com'),
(6,    6,    '研究员5', 'yanjiuyuan5',      'researcher5',          NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan5@example.com'),
(7,    7,    '固收1',   'gushou1',          'fixed_income1',        NOW(), 0, '123456', NULL, 'ORG_FIXED',    NULL, NULL, 'gushou1@example.com'),
(8,    8,    '固收2',   'gushou2',          'fixed_income2',        NOW(), 0, '123456', NULL, 'ORG_FIXED',    NULL, NULL, 'gushou2@example.com'),
(9,    9,    '固收3',   'gushou3',          'fixed_income3',        NOW(), 0, '123456', NULL, 'ORG_FIXED',    NULL, NULL, 'gushou3@example.com'),
(10,   10,   '固收4',   'gushou4',          'fixed_income4',        NOW(), 0, '123456', NULL, 'ORG_FIXED',    NULL, NULL, 'gushou4@example.com'),
(11,   11,   '权益1',   'quanyi1',          'equity1',              NOW(), 0, '123456', NULL, 'ORG_EQUITY',   NULL, NULL, 'quanyi1@example.com'),
(12,   12,   '权益2',   'quanyi2',          'equity2',              NOW(), 0, '123456', NULL, 'ORG_EQUITY',   NULL, NULL, 'quanyi2@example.com'),
(13,   13,   '权益3',   'quanyi3',          'equity3',              NOW(), 0, '123456', NULL, 'ORG_EQUITY',   NULL, NULL, 'quanyi3@example.com'),
(14,   14,   '量化1',   'lianghua1',        'quant1',               NOW(), 0, '123456', NULL, 'ORG_QUANT',    NULL, NULL, 'lianghua1@example.com'),
(15,   15,   '量化2',   'lianghua2',        'quant2',               NOW(), 0, '123456', NULL, 'ORG_QUANT',    NULL, NULL, 'lianghua2@example.com'),
(16,   16,   '风控经理', 'fengkongjingli',   'risk_manager',         NOW(), 0, '123456', NULL, 'ORG_RISK',     NULL, NULL, 'fengkongjingli@example.com');

INSERT INTO `t_sys_user_role` (
    `id`
    ,`role_id`
    ,`user_id`
    ,`ts`
) VALUES
(1,  1,  2,  NOW()),
(2,  2,  2,  NOW()),
(3,  2,  3,  NOW()),
(4,  3,  3,  NOW()),
(5,  1,  4,  NOW()),
(6,  3,  4,  NOW()),
(7,  2,  5,  NOW()),
(8,  3,  6,  NOW()),
(9,  4,  7,  NOW()),
(10, 5,  7,  NOW()),
(11, 4,  8,  NOW()),
(12, 6,  8,  NOW()),
(13, 5,  9,  NOW()),
(14, 6,  10, NOW()),
(15, 7,  11, NOW()),
(16, 8,  11, NOW()),
(17, 7,  12, NOW()),
(18, 8,  13, NOW()),
(19, 9,  14, NOW()),
(20, 9,  15, NOW()),
(21, 10, 16, NOW());
