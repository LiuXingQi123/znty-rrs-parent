-- ============================================================
-- AIS 投资分析库 - 演示数据初始化脚本
-- MySQL version: 8.0.33
-- 前提：需先执行 ais_inv_analysis_schema.sql 完成建表
-- 说明：10 个主体 C10001～C10010
--       t_inv_company / t_inv_grade_result / v_inv_grade_result 一一对应，
--       保证调库相关主体、自选权益人下拉每个主体都有内评分可展示
--       t_sys_resource / t_sys_permission 资源、权限及其数据目前仅用于迁移，不接入本项目菜单或权限逻辑
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ais_inv_analysis` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ais_inv_analysis`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 清空业务表
-- ----------------------------------------------------------------------------
-- 清空主体评级结果表
TRUNCATE TABLE `t_inv_grade_result`;
-- 清空担保人内评结果表
TRUNCATE TABLE `v_inv_grade_result`;
-- 清空用户角色关联表
TRUNCATE TABLE `t_sys_user_role`;
-- 清空主体基础信息表
TRUNCATE TABLE `t_inv_company`;
-- 清空用户表
TRUNCATE TABLE `t_sys_user`;
-- 清空角色/部门表
TRUNCATE TABLE `t_sys_role`;
-- 清空权限表
TRUNCATE TABLE `t_sys_permission`;
-- 清空资源表
TRUNCATE TABLE `t_sys_resource`;

-- ----------------------------------------------------------------------------
-- 主体基础信息样例（10 条）
-- ----------------------------------------------------------------------------
INSERT INTO `t_inv_company` (
    `id`, `business_scope`, `city_id`, `code`, `country_id`, `county_id`, `description`,
    `full_name`, `industry_id`, `legaler`, `province_id`, `reg_address`, `reg_capital`,
    `short_names`, `stock_sty`, `village_id`, `web_site`, `es_type`, `es_index`, `es_score`,
    `wind_code`, `industry`, `ts`, `fundDate`, `templateId`, `researcher`
) VALUES
(1, '交通基础设施投资、建设与运营管理。', '320100', 'C10001', 'CN', '320102', '省属交通投资平台，资产规模较大，融资渠道稳定。', '某省交通投资集团有限公司', '4800', '张明', '320000', '某省南京市玄武区中山路 100 号', '5000000万元', '某交投集团', '未上市', '320102001', 'https://www.demo-traffic.example', 'state_owned', 'AAA', '88.50', 'C10001', '交通运输', '2026-06-01 09:00:00', '2001-05-18', 'T1', '研究员1'),
(2, '城市基础设施建设、土地整理、保障房建设及配套服务。', '330100', 'C10002', 'CN', '330106', '区域城投主体，承担区域基础设施建设职能。', '某城市投资开发建设有限公司', '4700', '李华', '330000', '某省杭州市西湖区文三路 88 号', '1200000万元', '某城投公司', '未上市', '330106002', 'https://www.demo-city.example', 'city_investment', 'AA+', '82.10', 'C10002', '建筑与工程', '2026-06-02 09:00:00', '2008-09-26', 'T1', '研究员2'),
(3, '煤炭开采、能源投资与运营。', '140100', 'C10003', 'CN', '140105', '省属能源主体，煤电一体化运营。', '某能源集团有限公司', '2100', '赵刚', '140000', '某省太原市迎泽区解放路 12 号', '3000000万元', '某能源集团', '未上市', '140105001', 'https://www.demo-energy.example', 'state_owned', 'AAA', '86.20', 'C10003', '能源', '2026-06-03 09:00:00', '1999-03-08', 'T1', '研究员1'),
(4, '交通基建投资、建设与运营。', '420100', 'C10004', 'CN', '420102', '区域基建平台，项目储备充足。', '某基础设施建设有限公司', '4700', '孙伟', '420000', '某省武汉市江汉区建设大道 58 号', '1800000万元', '某基建公司', '未上市', '420102001', 'https://www.demo-infra.example', 'state_owned', 'AA', '79.40', 'C10004', '建筑与工程', '2026-06-04 09:00:00', '2005-07-16', 'T1', '研究员2'),
(5, '房地产开发经营、物业管理、商业运营。', '440300', 'C10005', 'CN', '440305', '民营地产主体，需关注销售回款和短期债务压力。', '某地产集团股份有限公司', '7000', '王强', '440000', '广东省深圳市南山区科技园 66 号', '800000万元', '某地产公司', 'A股', '440305003', 'https://www.demo-realestate.example', 'private_enterprise', 'AA+', '63.80', 'C10005', '房地产', '2026-06-05 09:00:00', '1998-11-12', 'T2', '研究员3'),
(6, '电力生产、电网投资与综合能源服务。', '320100', 'C10006', 'CN', '320104', '电力央企子公司，现金流稳定。', '某电力股份有限公司', '2700', '周敏', '320000', '某省南京市鼓楼区中山北路 200 号', '2500000万元', '某电力公司', 'A股', '320104001', 'https://www.demo-power.example', 'state_owned', 'AAA', '90.10', 'C10006', '公用事业', '2026-06-06 09:00:00', '1997-01-20', 'T1', '研究员4'),
(7, '软件开发、信息技术服务。', '110100', 'C10007', 'CN', '110108', '民营科技主体，评级偏低，关注再融资能力。', '某科技股份有限公司', '4500', '吴磊', '110000', '北京市海淀区中关村大街 1 号', '200000万元', '某科技公司', 'A股', '110108001', 'https://www.demo-tech.example', 'private_enterprise', 'A', '58.70', 'C10007', '信息技术', '2026-06-07 09:00:00', '2012-04-09', 'T2', '研究员5'),
(8, '不良资产经营、资产管理与投资。', '310100', 'C10008', 'CN', '310115', '金融资产管理公司，资本实力较强。', '某资产管理股份有限公司', '4000', '郑浩', '310000', '上海市浦东新区世纪大道 100 号', '10000000万元', '某资产管理公司', '未上市', '310115001', 'https://www.demo-amc.example', 'amc', 'AAA', '91.20', 'C10008', '金融', '2026-06-08 09:00:00', '1999-10-18', 'T1', '研究员4'),
(9, '进出口贸易、供应链管理。', '310100', 'C10009', 'CN', '310101', '地方国有贸易主体，业务覆盖大宗商品。', '某国际贸易集团有限公司', '3500', '冯丽', '310000', '上海市黄浦区中山东一路 8 号', '1500000万元', '某国贸公司', '未上市', '310101001', 'https://www.demo-trade.example', 'state_owned', 'AA+', '80.60', 'C10009', '贸易', '2026-06-09 09:00:00', '2003-06-22', 'T1', '研究员2'),
(10, '综合金融服务、同业投融资。', '110100', 'C10010', 'CN', '110102', '金融机构，资本充足、评级稳定。', '某金融控股有限公司', '4000', '陈凯', '110000', '北京市西城区金融大街 15 号', '8000000万元', '某金融公司', '未上市', '110102001', 'https://www.demo-finance.example', 'financial_institution', 'AAA', '92.00', 'C10010', '金融', '2026-06-10 09:00:00', '1995-12-01', 'T1', '研究员1');

-- ----------------------------------------------------------------------------
-- 主体评级结果样例（与主体 1:1）
-- ----------------------------------------------------------------------------
INSERT INTO `t_inv_grade_result` (
    `id`, `company_id`, `area_id`, `template_id`, `temp_id`, `time`, `cal_score`, `total_score`,
    `other_score`, `mapping_step`, `steps`, `final_step`, `deal_user_id`, `ts`, `adjust_note`,
    `template_type`, `weighted_score`, `observe_type`
) VALUES
(1, 1, 320000, 101, 1001, '2026-06-01 10:00:00', 88.50, 90.00, '{"external_rating":"AAA","finance_score":87.3,"industry_score":86.5}', '初评映射', 3, '一级复核', 2, '2026-06-01 10:05:00', '省属平台资质较强，维持高等级观察。', 1, 89.10, 'normal'),
(2, 2, 330000, 101, 1001, '2026-06-02 10:00:00', 82.10, 84.00, '{"external_rating":"AA+","finance_score":80.9,"industry_score":80.1}', '初评映射', 3, '一级复核', 2, '2026-06-02 10:05:00', '区域财政实力较好，关注有息债务增长。', 1, 82.80, 'watch'),
(3, 3, 140000, 101, 1001, '2026-06-03 10:00:00', 86.20, 88.00, '{"external_rating":"AAA","finance_score":85.0,"industry_score":84.2}', '初评映射', 3, '一级复核', 2, '2026-06-03 10:05:00', '煤电一体化经营稳定。', 1, 86.80, 'normal'),
(4, 4, 420000, 101, 1001, '2026-06-04 10:00:00', 79.40, 81.00, '{"external_rating":"AA","finance_score":78.2,"industry_score":77.4}', '初评映射', 3, '一级复核', 2, '2026-06-04 10:05:00', '在建项目较多，关注资本开支。', 1, 80.10, 'watch'),
(5, 5, 440000, 101, 1001, '2026-06-05 10:00:00', 63.80, 66.00, '{"external_rating":"AA+","finance_score":62.6,"industry_score":61.8}', '初评映射', 3, '一级复核', 2, '2026-06-05 10:05:00', '地产销售回款承压，纳入重点观察。', 1, 64.20, 'focus'),
(6, 6, 320000, 101, 1001, '2026-06-06 10:00:00', 90.10, 91.00, '{"external_rating":"AAA","finance_score":88.9,"industry_score":88.1}', '初评映射', 3, '一级复核', 2, '2026-06-06 10:05:00', '电力央企子公司，现金流稳定。', 1, 90.40, 'normal'),
(7, 7, 110000, 101, 1001, '2026-06-07 10:00:00', 58.70, 61.00, '{"external_rating":"A","finance_score":57.5,"industry_score":56.7}', '初评映射', 3, '一级复核', 2, '2026-06-07 10:05:00', '民营科技主体，再融资能力偏弱。', 1, 59.50, 'focus'),
(8, 8, 310000, 101, 1001, '2026-06-08 10:00:00', 91.20, 92.00, '{"external_rating":"AAA","finance_score":90.0,"industry_score":89.2}', '初评映射', 3, '一级复核', 2, '2026-06-08 10:05:00', '金融资产管理公司资本实力较强。', 1, 91.50, 'normal'),
(9, 9, 310000, 101, 1001, '2026-06-09 10:00:00', 80.60, 82.00, '{"external_rating":"AA+","finance_score":79.4,"industry_score":78.6}', '初评映射', 3, '一级复核', 2, '2026-06-09 10:05:00', '贸易业务受大宗商品价格波动影响。', 1, 81.10, 'watch'),
(10, 10, 110000, 101, 1001, '2026-06-10 10:00:00', 92.00, 93.00, '{"external_rating":"AAA","finance_score":90.8,"industry_score":90.0}', '初评映射', 3, '一级复核', 2, '2026-06-10 10:05:00', '金融机构资本充足、评级稳定。', 1, 92.30, 'normal');

-- ----------------------------------------------------------------------------
-- 主体内评结果样例（按 t_inv_company.wind_code 关联；档位对齐证券主数据/主体外评口径）
-- 调库相关主体、担保人/权益人候选均要求存在最新内评，故 Demo 为每个主体补齐评分
-- ----------------------------------------------------------------------------
INSERT INTO `v_inv_grade_result` (
    `windcode`, `windname`, `total_score`, `ts`
) VALUES
('C10001', '某交投集团', '1', '2026-06-01 10:05:00'),
('C10002', '某城投公司', '2+', '2026-06-02 10:05:00'),
('C10003', '某能源集团', '1', '2026-06-03 10:05:00'),
('C10004', '某基建公司', '2', '2026-06-04 10:05:00'),
('C10005', '某地产公司', '2', '2026-06-05 10:05:00'),
('C10006', '某电力公司', '1', '2026-06-06 10:05:00'),
('C10007', '某科技公司', '3-', '2026-06-07 10:05:00'),
('C10008', '某资产管理公司', '1', '2026-06-08 10:05:00'),
('C10009', '某国贸公司', '2+', '2026-06-09 10:05:00'),
('C10010', '某金融公司', '1', '2026-06-10 10:05:00');

-- ----------------------------------------------------------------------------
-- 角色和用户样例
-- 角色树：研究部 > 信用研究组 > 利率研究组
-- ----------------------------------------------------------------------------
INSERT INTO `t_sys_role` (
    `id`, `name`, `enable`, `ts`, `code`, `memo`, `parent_id`, `inherit_role_ids`
) VALUES
(1, '研究部', 1, NOW(), 'ROLE_RESEARCH', '研究部门', NULL, NULL),
(2, '信用研究组', 1, NOW(), 'ROLE_CREDIT_RESEARCH','信用研究小组', 1, NULL),
(3, '利率研究组', 1, NOW(), 'ROLE_RATE_RESEARCH', '利率研究小组', 2, NULL),
(4, '固收部', 1, NOW(), 'ROLE_FIXED_INCOME', '固定收益部门', NULL, NULL),
(5, '利率组', 1, NOW(), 'ROLE_RATE', '利率小组', 4, NULL),
(6, '信用组', 1, NOW(), 'ROLE_CREDIT', '信用小组', 4, NULL),
(7, '权益部', 1, NOW(), 'ROLE_EQUITY', '权益部门', NULL, NULL),
(8, '行业研究组', 1, NOW(), 'ROLE_INDUSTRY', '行业研究小组', 7, NULL),
(9, '量化部', 1, NOW(), 'ROLE_QUANT', '量化部门', NULL, NULL),
(10, '风险管理部', 1, NOW(), 'ROLE_RISK', '风险管理部门', NULL, NULL),
(11, '研究总监', 1, NOW(), 'ROLE_RESEARCH_DIRECTOR', '研究总监', 1, NULL),
(12, 'O32系统自动审批', 1, NOW(), 'ROLE_O32_SYSTEM', 'O32系统自动审批', NULL, NULL),
(13, '投资决策委员会', 1, NOW(), 'ROLE_INVEST_COMMITTEE', '投资决策委员会', NULL, NULL),
(14, '基金经理', 1, NOW(), 'ROLE_FUND_MANAGER', '基金经理', NULL, NULL),
(15, '系统管理员', 1, NOW(), 'ROLE_SYSTEM_ADMIN', '系统管理，不参与业务审批', NULL, NULL);

-- ----------------------------------------------------------------------------
-- 用户样例（19 条）
-- 管理员仅属于系统管理员；研究总监 1～3 同时承担 O32 临时代码人工审批。
-- 信用研究、研究总监、O32、投委会、基金经理角色均至少关联 3 名有效人员。
-- 用户
-- ----------------------------------------------------------------------------
INSERT INTO `t_sys_user` (
    `id`, `user_id`, `name`, `user_name`, `user_eng_name`, `ts`, `dr`, `pwd`, `oapwd`, `ORGID`,
    `TEL`, `MOBILE`, `EMAIL`
) VALUES
(1, 1, '管理员', 'admin', 'admin', NOW(), 0, '123456', NULL, 'ORG_ADMIN', NULL, NULL, 'admin@example.com'),
(2, 2, '研究员1', 'yanjiuyuan1', 'researcher1', NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan1@example.com'),
(3, 3, '研究员2', 'yanjiuyuan2', 'researcher2', NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan2@example.com'),
(4, 4, '研究员3', 'yanjiuyuan3', 'researcher3', NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan3@example.com'),
(5, 5, '研究员4', 'yanjiuyuan4', 'researcher4', NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan4@example.com'),
(6, 6, '研究员5', 'yanjiuyuan5', 'researcher5', NOW(), 0, '123456', NULL, 'ORG_RESEARCH', NULL, NULL, 'yanjiuyuan5@example.com'),
(7, 7, '固收1', 'gushou1', 'fixed_income1', NOW(), 0, '123456', NULL, 'ORG_FIXED', NULL, NULL, 'gushou1@example.com'),
(8, 8, '固收2', 'gushou2', 'fixed_income2', NOW(), 0, '123456', NULL, 'ORG_FIXED', NULL, NULL, 'gushou2@example.com'),
(9, 9, '固收3', 'gushou3', 'fixed_income3', NOW(), 0, '123456', NULL, 'ORG_FIXED', NULL, NULL, 'gushou3@example.com'),
(10, 10, '固收4', 'gushou4', 'fixed_income4', NOW(), 0, '123456', NULL, 'ORG_FIXED', NULL, NULL, 'gushou4@example.com'),
(11, 11, '权益1', 'quanyi1', 'equity1', NOW(), 0, '123456', NULL, 'ORG_EQUITY', NULL, NULL, 'quanyi1@example.com'),
(12, 12, '权益2', 'quanyi2', 'equity2', NOW(), 0, '123456', NULL, 'ORG_EQUITY', NULL, NULL, 'quanyi2@example.com'),
(13, 13, '权益3', 'quanyi3', 'equity3', NOW(), 0, '123456', NULL, 'ORG_EQUITY', NULL, NULL, 'quanyi3@example.com'),
(14, 14, '量化1', 'lianghua1', 'quant1', NOW(), 0, '123456', NULL, 'ORG_QUANT', NULL, NULL, 'lianghua1@example.com'),
(15, 15, '量化2', 'lianghua2', 'quant2', NOW(), 0, '123456', NULL, 'ORG_QUANT', NULL, NULL, 'lianghua2@example.com'),
(16, 16, '风控经理', 'fengkongjingli', 'risk_manager', NOW(), 0, '123456', NULL, 'ORG_RISK', NULL, NULL, 'fengkongjingli@example.com'),
(17, 17, '研究总监1', 'yanjiuzongjian1', 'research_director1', NOW(), 0, NULL, NULL, 'ORG_RESEARCH', NULL, NULL, 'research_director1@example.com'),
(18, 18, '研究总监2', 'yanjiuzongjian2', 'research_director2', NOW(), 0, NULL, NULL, 'ORG_RESEARCH', NULL, NULL, 'research_director2@example.com'),
(19, 19, '研究总监3', 'yanjiuzongjian3', 'research_director3', NOW(), 0, NULL, NULL, 'ORG_RESEARCH', NULL, NULL, 'research_director3@example.com');

-- ----------------------------------------------------------------------------
-- 用户角色关联样例（34 条）
-- ----------------------------------------------------------------------------
INSERT INTO `t_sys_user_role` (
    `id`, `role_id`, `user_id`, `ts`
) VALUES
(1, 1, 2, NOW()),
(2, 2, 2, NOW()),
(3, 2, 3, NOW()),
(4, 3, 3, NOW()),
(5, 1, 4, NOW()),
(6, 3, 4, NOW()),
(7, 2, 5, NOW()),
(8, 3, 6, NOW()),
(9, 4, 7, NOW()),
(10, 5, 7, NOW()),
(11, 4, 8, NOW()),
(12, 6, 8, NOW()),
(13, 5, 9, NOW()),
(14, 6, 10, NOW()),
(15, 7, 11, NOW()),
(16, 8, 11, NOW()),
(17, 7, 12, NOW()),
(18, 8, 13, NOW()),
(19, 9, 14, NOW()),
(20, 9, 15, NOW()),
(21, 10, 16, NOW()),
(22, 11, 17, NOW()),
(23, 12, 17, NOW()),
(24, 13, 7, NOW()),
(25, 14, 7, NOW()),
(26, 15, 1, NOW()),
(27, 11, 18, NOW()),
(28, 11, 19, NOW()),
(29, 12, 18, NOW()),
(30, 12, 19, NOW()),
(31, 13, 11, NOW()),
(32, 13, 16, NOW()),
(33, 14, 8, NOW()),
(34, 14, 9, NOW());

-- ----------------------------------------------------------------------------
-- 菜单与页面资源迁移样例（55 条，目前仅用于迁移）
-- 外部父节点由目标系统维护；按截图的子节点前缀，48 为 00002，534 为 0000700004
-- 五个原有二级功能菜单挂在 48 下，编码为 0000200010～0000200014；基金、股票顺延
-- 我的事宜直接挂在 534 下，截图 ID 20005 改为 10037，编码保留 000070000400002
-- 保留截图已有 ID、名称、图标及父子关系；10026 和 RRS配置子编码 00001 空位不补用
-- type：1=功能菜单 / 2=页面；enable 全部为 1；hidden：1=隐藏 / 0=显示
-- URL 统一使用 /fd/analysis/rrs/pages/ + 菜单或页面路由名称，不带 .html 后缀
-- 详情审核分组及详情、审核、报告详情页隐藏，其他页面显示；新增资源从 10038 继续分配
-- 不包含工作台首页、仅重定向到我的事宜的 grade_rule_alert，以及 docs 文档/脚本工具页
-- ----------------------------------------------------------------------------
INSERT INTO `t_sys_resource` (
    `id`, `name`, `url`, `tree_code`, `enable`, `type`, `parent_id`, `ico`, `hidden`
) VALUES
(10001, '债券研究', '/fd/analysis/rrs/pages/bond_research', '0000200010', 1, '1', 48, 'el-icon-document', 0),
(10002, '债券禁投主体维护', '/fd/analysis/rrs/pages/forbidden_entity', '0000200011', 1, '1', 48, 'el-icon-warning', 0),
(10003, 'CRMW', '/fd/analysis/rrs/pages/crmw', '0000200012', 1, '1', 48, 'el-icon-office-building', 0),
(10004, '详情审核', '/fd/analysis/rrs/pages/detail_approve', '0000200013', 1, '1', 48, 'el-icon-check', 1),
(10005, 'RRS配置', '/fd/analysis/rrs/pages/rrs_config', '0000200014', 1, '1', 48, 'el-icon-setting', 0),
(10006, '证券池调整', '/fd/analysis/rrs/pages/security_pool_adjust', '000020001000001', 1, '2', 10001, 'el-icon-edit', 0),
(10007, '证券池批量调整', '/fd/analysis/rrs/pages/batch_security_pool_adjust', '000020001000002', 1, '2', 10001, 'el-icon-document-copy', 0),
(10008, '证券池查询', '/fd/analysis/rrs/pages/security_pool_query', '000020001000003', 1, '2', 10001, 'el-icon-search', 0),
(10009, '证券池调整历史', '/fd/analysis/rrs/pages/security_pool_adjust_history', '000020001000004', 1, '2', 10001, 'el-icon-time', 0),
(10010, '临时代码管理', '/fd/analysis/rrs/pages/temp_security_code', '000020001000005', 1, '2', 10001, 'el-icon-key', 0),
(10011, '主体内评分档规则', '/fd/analysis/rrs/pages/credit_bond_grade_rule', '000020001000006', 1, '2', 10001, 'el-icon-s-check', 0),
(10012, '禁投池调整', '/fd/analysis/rrs/pages/forbidden_pool_adjust', '000020001100001', 1, '2', 10002, 'el-icon-edit', 0),
(10013, '主体池查询', '/fd/analysis/rrs/pages/company_pool_query', '000020001100002', 1, '2', 10002, 'el-icon-search', 0),
(10014, '主体池调整历史查询', '/fd/analysis/rrs/pages/company_pool_adjust_history', '000020001100003', 1, '2', 10002, 'el-icon-time', 0),
(10015, '禁投池查询', '/fd/analysis/rrs/pages/forbidden_pool_query', '000020001100004', 1, '2', 10002, 'el-icon-search', 0),
(10016, '禁投池历史查询', '/fd/analysis/rrs/pages/forbidden_pool_history', '000020001100005', 1, '2', 10002, 'el-icon-time', 0),
(10017, 'CRMW池调整', '/fd/analysis/rrs/pages/crmw_pool_adjust', '000020001200001', 1, '2', 10003, 'el-icon-edit', 0),
(10018, 'CRMW池查询', '/fd/analysis/rrs/pages/crmw_pool_query', '000020001200002', 1, '2', 10003, 'el-icon-search', 0),
(10019, 'CRMW池调整历史查询', '/fd/analysis/rrs/pages/crmw_pool_adjust_history', '000020001200003', 1, '2', 10003, 'el-icon-time', 0),
(10020, '证券池调库详情', '/fd/analysis/rrs/pages/security_pool_adjust_detail', '000020001300001', 1, '2', 10004, 'el-icon-document', 1),
(10021, '证券池调库审核', '/fd/analysis/rrs/pages/security_pool_adjust_approve', '000020001300002', 1, '2', 10004, 'el-icon-check', 1),
(10022, '禁投池调整详情', '/fd/analysis/rrs/pages/forbidden_pool_adjust_detail', '000020001300003', 1, '2', 10004, 'el-icon-document', 1),
(10023, '禁投池调整审核', '/fd/analysis/rrs/pages/forbidden_pool_adjust_approve', '000020001300004', 1, '2', 10004, 'el-icon-check', 1),
(10024, 'CRMW池调库详情', '/fd/analysis/rrs/pages/crmw_pool_adjust_detail', '000020001300005', 1, '2', 10004, 'el-icon-document', 1),
(10025, 'CRMW池调库审核', '/fd/analysis/rrs/pages/crmw_pool_adjust_approve', '000020001300006', 1, '2', 10004, 'el-icon-check', 1),
(10027, '流程定义 - 工作流平台', '/fd/analysis/rrs/pages/flow_definition', '000020001400002', 1, '2', 10005, 'el-icon-s-grid', 0),
(10028, '投资池维护', '/fd/analysis/rrs/pages/investment_pool', '000020001400003', 1, '2', 10005, 'el-icon-folder-opened', 0),
(10029, '规则管理中心', '/fd/analysis/rrs/pages/rule_manager', '000020001400004', 1, '2', 10005, 'el-icon-cpu', 0),
(10030, '存量证券批量调整', '/fd/analysis/rrs/pages/stock_security_batch_adjust', '000020001000007', 1, '2', 10001, 'el-icon-document-copy', 0),
(10031, '证券池Excel导入', '/fd/analysis/rrs/pages/security_pool_excel_import', '000020001000008', 1, '2', 10001, 'el-icon-upload2', 0),
(10032, '投资池开放日维护', '/fd/analysis/rrs/pages/pool_open_day', '000020001400005', 1, '2', 10005, 'el-icon-calendar', 0),
(10033, '定时任务管理', '/fd/analysis/rrs/pages/scheduled_task', '000020001400006', 1, '2', 10005, 'el-icon-timer', 0),
(10034, '恒生格式手动导出', '/fd/analysis/rrs/pages/hs_pool_manual_export', '000020001000009', 1, '2', 10001, 'el-icon-download', 0),
(10035, 'CRMW池批量调整', '/fd/analysis/rrs/pages/batch_crmw_pool_adjust', '000020001200004', 1, '2', 10003, 'el-icon-document-copy', 0),
(10036, 'CRMW池批量导入', '/fd/analysis/rrs/pages/crmw_pool_excel_import', '000020001200005', 1, '2', 10003, 'el-icon-upload2', 0),
(10037, '我的事宜', '/fd/analysis/rrs/pages/my_matters', '000070000400002', 1, '2', 534, 'el-icon-user', 0),
(10038, '报告详情', '/fd/analysis/rrs/pages/report_detail', '000020001300007', 1, '2', 10004, 'el-icon-document', 1),
(10039, '基金研究', '/fd/analysis/rrs/pages/fund_research', '0000200015', 1, '1', 48, 'el-icon-pie-chart', 0),
(10040, '股票研究', '/fd/analysis/rrs/pages/stock_research', '0000200016', 1, '1', 48, 'el-icon-data-line', 0),
(10041, '基金池调整', '/fd/analysis/rrs/pages/fund_pool_adjust', '000020001500001', 1, '2', 10039, 'el-icon-sort', 0),
(10042, '基金池批量调整', '/fd/analysis/rrs/pages/batch_fund_pool_adjust', '000020001500002', 1, '2', 10039, 'el-icon-s-grid', 0),
(10043, '基金池Excel导入', '/fd/analysis/rrs/pages/fund_pool_excel_import', '000020001500003', 1, '2', 10039, 'el-icon-upload2', 0),
(10044, '基金池查询', '/fd/analysis/rrs/pages/fund_pool_query', '000020001500004', 1, '2', 10039, 'el-icon-search', 0),
(10045, '基金净值导出', '/fd/analysis/rrs/pages/fund_nav_export', '000020001500005', 1, '2', 10039, 'el-icon-download', 0),
(10046, '基金池调整历史', '/fd/analysis/rrs/pages/fund_pool_adjust_history', '000020001500006', 1, '2', 10039, 'el-icon-time', 0),
(10047, '基金临时代码管理', '/fd/analysis/rrs/pages/temp_fund_code', '000020001500007', 1, '2', 10039, 'el-icon-tickets', 0),
(10048, '股票池调整', '/fd/analysis/rrs/pages/stock_pool_adjust', '000020001600001', 1, '2', 10040, 'el-icon-sort', 0),
(10049, '股票池批量调整', '/fd/analysis/rrs/pages/batch_stock_pool_adjust', '000020001600002', 1, '2', 10040, 'el-icon-s-grid', 0),
(10050, '股票池Excel导入', '/fd/analysis/rrs/pages/stock_pool_excel_import', '000020001600003', 1, '2', 10040, 'el-icon-upload2', 0),
(10051, '股票池查询', '/fd/analysis/rrs/pages/stock_pool_query', '000020001600004', 1, '2', 10040, 'el-icon-search', 0),
(10052, '股票池调整历史查询', '/fd/analysis/rrs/pages/stock_pool_adjust_history', '000020001600005', 1, '2', 10040, 'el-icon-time', 0),
(10053, '基金池调库详情', '/fd/analysis/rrs/pages/fund_pool_adjust_detail', '000020001300008', 1, '2', 10004, 'el-icon-document', 1),
(10054, '基金池调库审核', '/fd/analysis/rrs/pages/fund_pool_adjust_approve', '000020001300009', 1, '2', 10004, 'el-icon-check', 1),
(10055, '股票池调库详情', '/fd/analysis/rrs/pages/stock_pool_adjust_detail', '000020001300010', 1, '2', 10004, 'el-icon-document', 1),
(10056, '股票池调库审核', '/fd/analysis/rrs/pages/stock_pool_adjust_approve', '000020001300011', 1, '2', 10004, 'el-icon-check', 1);

-- ----------------------------------------------------------------------------
-- 资源查询权限迁移样例（48 条，目前仅用于迁移）
-- 仅 type=2 的页面资源生成查询权限（包含隐藏页面），type=1 的功能菜单不生成权限
-- 权限 ID 从 10001 连续递增至 10048，resource_id 保持对应资源主键
-- resource_id 对应资源主键；tree_code 为对应资源的 tree_code + 00001
-- code 为 rrs_ + 资源名称的中文拼音小写首字母 + _query；英文保留原字母并转小写
-- 名称中的空格、连字符不进入 code；调整取 tz，调库取 dk；name 严格为资源名称 + 查询
-- ts 使用执行脚本时的当前时间；沿用外部系统字段，不接入本项目权限逻辑
-- ----------------------------------------------------------------------------
INSERT INTO `t_sys_permission` (
    `id`, `resource_id`, `tree_code`, `code`, `name`, `ts`
) VALUES
(10001, 10006, '00002000100000100001', 'rrs_zqctz_query', '证券池调整查询', NOW()),
(10002, 10007, '00002000100000200001', 'rrs_zqcpltz_query', '证券池批量调整查询', NOW()),
(10003, 10008, '00002000100000300001', 'rrs_zqccx_query', '证券池查询查询', NOW()),
(10004, 10009, '00002000100000400001', 'rrs_zqctzls_query', '证券池调整历史查询', NOW()),
(10005, 10010, '00002000100000500001', 'rrs_lsdmgl_query', '临时代码管理查询', NOW()),
(10006, 10011, '00002000100000600001', 'rrs_ztnpfdgz_query', '主体内评分档规则查询', NOW()),
(10007, 10012, '00002000110000100001', 'rrs_jtctz_query', '禁投池调整查询', NOW()),
(10008, 10013, '00002000110000200001', 'rrs_ztccx_query', '主体池查询查询', NOW()),
(10009, 10014, '00002000110000300001', 'rrs_ztctzlscx_query', '主体池调整历史查询查询', NOW()),
(10010, 10015, '00002000110000400001', 'rrs_jtccx_query', '禁投池查询查询', NOW()),
(10011, 10016, '00002000110000500001', 'rrs_jtclscx_query', '禁投池历史查询查询', NOW()),
(10012, 10017, '00002000120000100001', 'rrs_crmwctz_query', 'CRMW池调整查询', NOW()),
(10013, 10018, '00002000120000200001', 'rrs_crmwccx_query', 'CRMW池查询查询', NOW()),
(10014, 10019, '00002000120000300001', 'rrs_crmwctzlscx_query', 'CRMW池调整历史查询查询', NOW()),
(10015, 10020, '00002000130000100001', 'rrs_zqcdkxq_query', '证券池调库详情查询', NOW()),
(10016, 10021, '00002000130000200001', 'rrs_zqcdksh_query', '证券池调库审核查询', NOW()),
(10017, 10022, '00002000130000300001', 'rrs_jtctzxq_query', '禁投池调整详情查询', NOW()),
(10018, 10023, '00002000130000400001', 'rrs_jtctzsh_query', '禁投池调整审核查询', NOW()),
(10019, 10024, '00002000130000500001', 'rrs_crmwcdkxq_query', 'CRMW池调库详情查询', NOW()),
(10020, 10025, '00002000130000600001', 'rrs_crmwcdksh_query', 'CRMW池调库审核查询', NOW()),
(10021, 10027, '00002000140000200001', 'rrs_lcdygzlpt_query', '流程定义 - 工作流平台查询', NOW()),
(10022, 10028, '00002000140000300001', 'rrs_tzcwh_query', '投资池维护查询', NOW()),
(10023, 10029, '00002000140000400001', 'rrs_gzglzx_query', '规则管理中心查询', NOW()),
(10024, 10030, '00002000100000700001', 'rrs_clzqpltz_query', '存量证券批量调整查询', NOW()),
(10025, 10031, '00002000100000800001', 'rrs_zqcexceldr_query', '证券池Excel导入查询', NOW()),
(10026, 10032, '00002000140000500001', 'rrs_tzckfrwh_query', '投资池开放日维护查询', NOW()),
(10027, 10033, '00002000140000600001', 'rrs_dsrwgl_query', '定时任务管理查询', NOW()),
(10028, 10034, '00002000100000900001', 'rrs_hsgssddc_query', '恒生格式手动导出查询', NOW()),
(10029, 10035, '00002000120000400001', 'rrs_crmwcpltz_query', 'CRMW池批量调整查询', NOW()),
(10030, 10036, '00002000120000500001', 'rrs_crmwcpldr_query', 'CRMW池批量导入查询', NOW()),
(10031, 10037, '00007000040000200001', 'rrs_wdsy_query', '我的事宜查询', NOW()),
(10032, 10038, '00002000130000700001', 'rrs_bgxq_query', '报告详情查询', NOW()),
(10033, 10041, '00002000150000100001', 'rrs_jjctz_query', '基金池调整查询', NOW()),
(10034, 10042, '00002000150000200001', 'rrs_jjcpltz_query', '基金池批量调整查询', NOW()),
(10035, 10043, '00002000150000300001', 'rrs_jjcexceldr_query', '基金池Excel导入查询', NOW()),
(10036, 10044, '00002000150000400001', 'rrs_jjccx_query', '基金池查询查询', NOW()),
(10037, 10045, '00002000150000500001', 'rrs_jjjzdc_query', '基金净值导出查询', NOW()),
(10038, 10046, '00002000150000600001', 'rrs_jjctzls_query', '基金池调整历史查询', NOW()),
(10039, 10047, '00002000150000700001', 'rrs_jjlsdmgl_query', '基金临时代码管理查询', NOW()),
(10040, 10048, '00002000160000100001', 'rrs_gpctz_query', '股票池调整查询', NOW()),
(10041, 10049, '00002000160000200001', 'rrs_gpcpltz_query', '股票池批量调整查询', NOW()),
(10042, 10050, '00002000160000300001', 'rrs_gpcexceldr_query', '股票池Excel导入查询', NOW()),
(10043, 10051, '00002000160000400001', 'rrs_gpccx_query', '股票池查询查询', NOW()),
(10044, 10052, '00002000160000500001', 'rrs_gpctzlscx_query', '股票池调整历史查询查询', NOW()),
(10045, 10053, '00002000130000800001', 'rrs_jjcdkxq_query', '基金池调库详情查询', NOW()),
(10046, 10054, '00002000130000900001', 'rrs_jjcdksh_query', '基金池调库审核查询', NOW()),
(10047, 10055, '00002000130001000001', 'rrs_gpcdkxq_query', '股票池调库详情查询', NOW()),
(10048, 10056, '00002000130001100001', 'rrs_gpcdksh_query', '股票池调库审核查询', NOW());
