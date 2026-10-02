-- ============================================================
-- znty-rrs 基金逐日净值表演示数据
-- MySQL version: 8.0.33
-- 说明：本脚本仅维护 rrs_fund_nav 演示数据
-- ============================================================

USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `rrs_fund_nav`;

INSERT INTO `rrs_fund_nav` (
    `fund_code`
    ,`trade_date`
    ,`announcement_date`
    ,`currency_code`
    ,`unit_nav`
    ,`unit_nav_growth_rate`
    ,`accumulated_nav`
    ,`daily_ten_thousand_income`
    ,`seven_day_annualized_yield`
    ,`source_system`
    ,`memo`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
) VALUES
('FUND001.SH', '2026-09-18', '2026-09-21', 'CNY', 4.200000, 0.250000, 4.834400, NULL, NULL, 'DEMO', '普通净值型基金历史净值演示数据', 0, NOW(), NOW()),
('FUND001.SH', '2026-09-21', '2026-09-22', 'CNY', 4.215600, 0.371429, 4.850000, NULL, NULL, 'DEMO', '普通净值型基金历史净值演示数据', 0, NOW(), NOW()),
('FUND003.OF', '2026-09-18', '2026-09-21', 'CNY', 1.000000, NULL, 1.000000, 0.548900, 1.892000, 'DEMO', '货币型基金万份收益和七日年化演示数据', 0, NOW(), NOW()),
('FUND003.OF', '2026-09-21', '2026-09-22', 'CNY', 1.000000, NULL, 1.000000, 0.532100, 1.854000, 'DEMO', '货币型基金万份收益和七日年化演示数据', 0, NOW(), NOW());
