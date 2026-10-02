-- ============================================================
-- znty-rrs 基金基础信息表演示数据
-- MySQL version: 8.0.33
-- 说明：本脚本仅维护 rrs_fundinfo 演示数据
-- ============================================================

USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `rrs_fundinfo`;

INSERT INTO `rrs_fundinfo` (
    `fund_code`
    ,`fund_name`
    ,`fund_short_name`
    ,`security_type`
    ,`security_status`
    ,`market_code`
    ,`currency_code`
    ,`establishment_date`
    ,`list_date`
    ,`delist_date`
    ,`fund_company_code`
    ,`fund_company_name`
    ,`is_h`
    ,`is_hksc`
    ,`latest_nav`
    ,`fund_manager_names`
    ,`fund_administrator`
    ,`fund_custodian`
    ,`accumulated_nav`
    ,`investment_style`
    ,`trade_price`
    ,`issue_scale`
    ,`issue_term`
    ,`daily_ten_thousand_income`
    ,`seven_day_annualized_yield`
    ,`latest_scale`
    ,`previous_close_price`
    ,`premium_discount_rate`
    ,`data_date`
    ,`source_system`
    ,`memo`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
) VALUES
('FUND001.SH', '沪深300交易型开放式指数基金', '沪深300ETF', 'etf_fund', 'L', 'SSE', 'CNY', '2012-05-04', '2012-05-28', NULL, 'FC001', '兴盛基金管理有限公司', 0, 0, 4.215600, '张明,李华', '兴盛基金管理有限公司', '华东商业银行股份有限公司', 4.850000, '指数型', 4.230000, 100.0000, '不定期', NULL, NULL, 328.4500, 4.220000, 0.341585, '2026-09-21', 'DEMO', '上海证券交易所场内基金数据', 0, NOW(), NOW()),
('FUND002.SZ', '成长主题上市开放式基金', '成长LOF', 'lof_fund', 'L', 'SZSE', 'CNY', '2015-03-10', '2015-04-01', NULL, 'FC002', '恒泰资产管理有限公司', 0, 0, 1.563200, '陈静', '恒泰资产管理有限公司', '华南股份制银行股份有限公司', 2.103400, '成长型', 1.550000, 30.0000, '不定期', NULL, NULL, 45.3300, 1.545000, -0.844421, '2026-09-21', 'DEMO', '深圳证券交易所场内基金数据', 0, NOW(), NOW()),
('FUND003.OF', '稳健收益开放式证券投资基金', '稳健收益', 'open_fund', 'L', 'OTC', 'CNY', '2018-06-15', NULL, NULL, 'FC003', '华盈公募基金管理有限公司', 0, 0, 1.000000, '王强,赵敏', '华盈公募基金管理有限公司', '中州国有商业银行股份有限公司', 1.234500, '货币型', NULL, 50.0000, '不定期', 0.532100, 1.854000, 126.7800, NULL, NULL, '2026-09-21', 'DEMO', '场外开放式基金数据', 0, NOW(), NOW()),
('FUND004.HK', '香港市场交易型开放式基金', '香港ETF', 'qdii_fund', 'L', 'HKEX', 'HKD', '2020-08-20', '2020-09-01', NULL, 'FC004', '远洋国际基金管理有限公司', 1, 1, 12.345000, '周宇,林峰', '远洋国际基金管理有限公司', '港城托管银行有限公司', 15.678000, '指数型', 12.420000, 25.0000, '不定期', NULL, NULL, 86.3200, 12.380000, 0.607534, '2026-09-21', 'DEMO', '香港交易所及港股通标的数据', 0, NOW(), NOW()),
('FUND005.SH', '已终止封闭式证券投资基金', '封闭基金', 'closed_fund', 'D', 'SSE', 'CNY', '2008-01-10', '2008-02-01', '2024-12-31', 'FC005', '安和证券投资基金管理有限公司', 0, 0, 1.225000, '刘洋', '安和证券投资基金管理有限公司', '安信证券托管有限公司', 3.650000, '平衡型', 1.220000, 20.0000, '15年', NULL, NULL, 0.0000, 1.218000, -0.408163, '2024-12-31', 'DEMO', '已终止基金数据', 0, NOW(), NOW());
