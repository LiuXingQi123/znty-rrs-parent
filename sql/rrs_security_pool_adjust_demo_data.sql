-- ============================================================
-- 证券主数据 / 调库日志 / 池状态 / 流程步骤 演示数据
-- 覆盖：债券(可调+触发) / 股票 / 基金 / 主体 / CRMW
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `rrs_securityinfo`;
TRUNCATE TABLE `ip_adjust_log`;
TRUNCATE TABLE `ip_pool_status`;
TRUNCATE TABLE `ip_adjust_step`;

INSERT INTO `rrs_securityinfo` (
    `wind_code`, `full_name`, `short_name`, `wind_code_sh`, `wind_code_sz`, `wind_code_nib`,
    `security_type`, `term_year`, `term_day`, `maturity_date`, `delist_date`, `security_status`,
    `industry_name`, `industry_name2`,
    `rating_bond`, `rating_bondissuer`, `rating_outlook`, `inner_issuer_rating`, `inner_guarantor_rating`,
    `guarant_flag`, `guarantor`, `guarant_type`, `issuer`, `issuer_code`,
    `list_date`, `firstissue_date`, `carry_date`, `end_date`,
    `coupon_rate`, `spread`, `interest_type`, `interest_frequency`, `interest_yearcount`,
    `crncy_code`, `issue_amountplan`, `par`, `info_pledge_ratio`,
    `form_desc`, `sec_typename`, `comp_type`, `issue_type`,
    `redemption_flag`, `redemption_date`, `callbkorputbk_date`, `date_next`,
    `corporate_bond_flag`, `payadvanced_flag`, `chooseright_flag`, `incbonds_flag`,
    `inner_class`, `cj_flag`, `yx_flag`, `dy_flag`, `abs_flag`,
    `rating_bond_agency`, `rating_bondissuer_agency`,
    `security_source`, `create_time`, `ts`
) VALUES
-- ===== 债券-可调整 =====
('101901234.IB', '24某交投MTN001', '24交投MTN001', NULL, NULL, '101901234.IB', 'mtn', 3, 1095, '2029-03-18', '2029-03-18', 'L', '制造业', '交通运输', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, '某交投集团', 'C10001', '2024-03-18', '2024-03-15', '2024-03-18', '2029-03-18', '3.5', '1.2', '固定利率', '年付', 1, 'CNY', 10, 100, 0, '固息', '中期票据', '国有企业', '公开招标', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW()),
('102002345.IB', '23某城投MTN001', '23城投MTN', NULL, NULL, '102002345.IB', 'mtn', 5, 1825, '2029-08-15', '2029-08-15', 'L', '制造业', '城市基建', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, '某城投公司', 'C10002', '2023-08-15', '2023-08-12', '2023-08-15', '2029-08-15', '4.2', '1.8', '固定利率', '年付', 1, 'CNY', 15, 100, 0, '固息', '中期票据', '城投公司', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW()),
('103003456.SH', '24某能E1', '24能E1', '103003456.SH', NULL, NULL, 'company_bond', 3, 1095, '2029-01-10', '2029-01-10', 'L', '建筑', '能源开采', 'AA+', 'AA+', '稳定', '2+', NULL, 0, NULL, NULL, '某能源集团', 'C10003', '2024-01-10', '2024-01-08', '2024-01-10', '2029-01-10', '1.8', '0.5', '固定利率', '年付', 1, 'CNY', 8, 100, 0, '固息', '公司债', '国有企业', '公开招标', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW()),
('104004567.IB', '23某基建债01', '23基建债', NULL, NULL, '104004567.IB', 'company_bond', 5, 1825, '2030-11-24', '2030-11-24', 'L', '基建', '交通基建', 'AA', 'AA', '稳定', '2', NULL, 0, NULL, NULL, '某基建公司', 'C10004', '2023-11-24', '2023-11-22', '2023-11-24', '2030-11-24', '4.5', '2.1', '固定利率', '年付', 1, 'CNY', 20, 100, 0, '固息', '公司债', '国有企业', '簿记建档', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW()),
('106006789.IB', '22某电力MTN001', '22电力MTN', NULL, NULL, '106006789.IB', 'mtn', 7, 2555, '2030-09-16', '2030-09-16', 'L', '制造业', '电力设备', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, '某电力公司', 'C10006', '2022-09-16', '2022-09-14', '2022-09-16', '2030-09-16', '3.2', '1.0', '固定利率', '年付', 1, 'CNY', 20, 100, 0, '固息', '中期票据', '国有企业', '公开招标', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW()),
('109009012.IB', '25某国贸SCP001', '25国贸SCP', NULL, NULL, '109009012.IB', 'scp', 2, 730, '2028-08-28', '2028-08-28', 'L', '贸易', '国际贸易', 'AA+', 'AA+', '稳定', '2', NULL, 0, NULL, NULL, '某国贸公司', 'C10009', '2025-02-28', '2025-02-26', '2025-02-28', '2028-08-28', '2.8', '0.3', '固定利率', '到期一次还本', 0, 'CNY', 10, 100, 0, '固息', '超短期融资券', '国有企业', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW()),
-- ===== 债券-触发校验 =====
('105005678.IB', '22某地产CP001', '22地产CP', NULL, NULL, '105005678.IB', 'cp', 0.75, 270, '2025-06-15', '2025-06-15', 'L', '住宅开发', '房地产开发', 'A-1', 'AA+', '负面', '2', NULL, 0, NULL, NULL, '某地产公司', 'C10005', '2022-06-15', '2022-06-13', '2022-06-15', '2025-06-15', '5.5', '3.2', '固定利率', '到期一次还本', 0, 'CNY', 5, 100, 0, '固息', '短期融资券', '民营企业', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW()),
('107007890.IB', '24某科技K1', '24科技K', NULL, NULL, '107007890.IB', 'company_bond', 3, 1095, '2028-04-08', '2028-04-08', 'L', '软件服务', '信息技术', 'A', 'A', '负面', '3-', NULL, 0, NULL, NULL, '某科技公司', 'C10007', '2024-04-08', '2024-04-05', '2024-04-08', '2028-04-08', '6.8', '4.5', '固定利率', '年付', 1, 'CNY', 5, 100, 0, '固息', '公司债', '民营企业', '簿记建档', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW()),
('110010123.IB', '23某金融债001', '23金融债', NULL, NULL, '110010123.IB', 'company_bond', 7, 2555, '2032-06-19', '2032-06-19', 'L', '金融', '其他金融', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, '某金融公司', 'C10010', '2023-06-19', '2023-06-16', '2023-06-19', '2032-06-19', '3.8', '1.5', '固定利率', '年付', 1, 'CNY', 30, 100, 0, '固息', '公司债', '国有企业', '公开招标', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW()),
('112008888.IB', '24某担保债001', '24担保债', NULL, NULL, '112008888.IB', 'company_bond', 3, 1095, '2029-03-20', '2029-03-20', 'L', '建筑', '城市建设', 'AA', 'AA-', '稳定', '3', '2', 1, '某担保集团', '连带责任保证', '某被担保公司', 'C90005', '2024-03-20', '2024-03-18', '2024-03-20', '2029-03-20', '5.0', '2.8', '固定利率', '年付', 1, 'CNY', 8, 100, 0, '固息', '公司债', '城投公司', '簿记建档', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW()),
('108008901.IB', '23某资A1', '23某资A', NULL, NULL, '108008901.IB', 'abs', 1, 365, '2027-12-06', '2027-12-06', 'L', 'AMC', '资产管理', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, '某资产管理公司', 'C10008', '2023-12-06', '2023-12-04', '2023-12-06', '2027-12-06', '3.0', '1.0', '固定利率', '年付', 1, 'CNY', 30, 100, 0, '固息', '资产支持证券', '资产管理公司', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'ABS', 0, 0, 0, 1, '中诚信', '中诚信', 'official', NOW(), NOW()),
-- ===== 股票-可调整 =====
('600002.SH', '某正常股份', '正常股A', '600002.SH', NULL, NULL, 'a_share', NULL, NULL, NULL, '2028-12-31', 'L', '制造业', '制造业', NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, '某正常公司A', 'C90002', '2022-12-31', '2022-12-28', '2022-12-31', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, 'A股', '股份有限公司', 'IPO', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW()),
('600003.SH', '某科技股份', '科技股B', '600003.SH', NULL, NULL, 'a_share', NULL, NULL, NULL, '2029-06-30', 'L', '软件服务', '信息技术', NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, '某科技公司B', 'C90006', '2023-06-30', '2023-06-28', '2023-06-30', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, 'A股', '股份有限公司', 'IPO', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW()),
-- ===== 股票-触发校验 =====
('600001.SH', '某退市股份', '退市股', '600001.SH', NULL, NULL, 'a_share', NULL, NULL, NULL, '2025-06-15', 'D', '制造业', '制造业', NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, '某退市公司', 'C90001', '2020-06-15', '2020-06-12', '2020-06-15', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, 'A股', '股份有限公司', 'IPO', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW()),
-- ===== 基金-可调整 =====
('159001.SH', '某ETF基金', '测试基金A', '159001.SH', NULL, NULL, 'etf_fund', NULL, NULL, NULL, NULL, 'L', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, '某基金公司', 'C90003', '2023-01-01', '2022-12-28', '2023-01-01', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, 'ETF基金', '基金公司', '公开发售', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW()),
('159002.SH', '某开放基金', '开放基金B', '159002.SH', NULL, NULL, 'open_fund', NULL, NULL, NULL, NULL, 'L', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, '某基金公司B', 'C90007', '2023-03-01', '2023-02-26', '2023-03-01', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, '开放式基金', '基金公司', '公开发售', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW()),
-- ===== 主体-可调整 =====
('C10001', '某交投集团', '交投集团', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '交通运输', '高速公路', NULL, 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, 'C10001', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '中诚信', 'official', NOW(), NOW()),
('C10002', '某城投公司', '城投公司', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '建筑', '城市基建', NULL, 'AA+', '稳定', '2+', NULL, 0, NULL, NULL, NULL, 'C10002', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '联合资信', 'official', NOW(), NOW()),
('C10003', '某能源集团', '能源集团', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '煤炭', '能源开采', NULL, 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, 'C10003', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '中诚信', 'official', NOW(), NOW()),
('C10004', '某电力公司', '电力公司', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '制造业', '电力设备', NULL, 'AA', '稳定', '2', NULL, 0, NULL, NULL, NULL, 'C10004', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '联合资信', 'official', NOW(), NOW()),
-- ===== 主体-触发校验 =====
('C10005', '某地产公司', '地产公司', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '住宅开发', '房地产开发', NULL, 'AA+', '负面', '3', NULL, 0, NULL, NULL, NULL, 'C10005', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '中诚信', 'official', NOW(), NOW()),
-- ===== CRMW-可调整 =====
('CRMW001.IB', '某CRMW凭证A', 'CRMW-A', NULL, NULL, 'CRMW001.IB', 'crmw', 2, 730, '2028-06-15', '2028-06-15', 'L', NULL, NULL, 'AAA', 'AAA', '稳定', NULL, NULL, 0, NULL, NULL, '某交投集团', 'C10001', '2026-01-15', '2026-01-12', '2026-01-15', '2028-06-15', NULL, NULL, NULL, NULL, NULL, 'CNY', 5, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW()),
('CRMW002.IB', '某CRMW凭证B', 'CRMW-B', NULL, NULL, 'CRMW002.IB', 'crmw', 3, 1095, '2029-06-15', '2029-06-15', 'L', NULL, NULL, 'AA+', 'AA+', '稳定', NULL, NULL, 0, NULL, NULL, '某城投公司', 'C10002', '2026-02-15', '2026-02-12', '2026-02-15', '2029-06-15', NULL, NULL, NULL, NULL, NULL, 'CNY', 8, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW()),
('CRMW003.IB', '某CRMW凭证C', 'CRMW-C', NULL, NULL, 'CRMW003.IB', 'crmw', 1, 365, '2027-06-15', '2027-06-15', 'L', NULL, NULL, 'AA', 'AA', '稳定', NULL, NULL, 0, NULL, NULL, '某能源集团', 'C10003', '2026-03-15', '2026-03-12', '2026-03-15', '2027-06-15', NULL, NULL, NULL, NULL, NULL, 'CNY', 3, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW()),
('CRMW004.IB', '某CRMW凭证D', 'CRMW-D', NULL, NULL, 'CRMW004.IB', 'crmw', 1.5, 548, '2027-12-15', '2027-12-15', 'L', NULL, NULL, 'AA+', 'AA+', '稳定', NULL, NULL, 0, NULL, NULL, '某电力公司', 'C10004', '2026-04-15', '2026-04-12', '2026-04-15', '2027-12-15', NULL, NULL, NULL, NULL, NULL, 'CNY', 5, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW()),
-- ===== CRMW-触发校验 =====
('CRMW005.IB', '某CRMW凭证E', 'CRMW-E', NULL, NULL, 'CRMW005.IB', 'crmw', 2, 730, '2028-06-15', '2028-06-15', 'L', NULL, NULL, 'AA', 'AA', '稳定', NULL, NULL, 0, NULL, NULL, '某地产公司', 'C10005', '2026-05-15', '2026-05-12', '2026-05-15', '2028-06-15', NULL, NULL, NULL, NULL, NULL, 'CNY', 5, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW());

-- 补充担保人ID（多担保人逗号分隔，与 guarantor 名称按位置配对）：112008888.IB 为多担保人担保债
-- 配套 wind_cbondissuerrating：发行人 C90005 主体评级下调(AA->AA-)；担保人 C90011 未下调 / C90012 下调(AA+->AA)
UPDATE `rrs_securityinfo`
SET `guarantor` = '某担保集团,某增信担保', `guarantor_id` = 'C90011,C90012'
WHERE `wind_code` = '112008888.IB';

INSERT INTO `ip_pool_status` (
    `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`,
    `adjust_batch_no`, `adjust_log_id`, `target_pool_id`, `target_pool_name`, `pool_type`,
    `flow_id`, `flow_key`, `flow_type`,
    `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`,
    `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
-- 升库债1 在三级库（可升入一级/二级库）
('101901234.IB', '24交投MTN001', 'mtn', '手工调整', '调入', 'BOND20260301001', 1, 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '正常调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
-- 升库债2 在四级库（可升入二级/三级库）
('102002345.IB', '23城投MTN', 'mtn', '手工调整', '调入', 'BOND20260302001', 2, 5, '四级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '正常调入四级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
-- 降库债1 在一级库（可降入二级/三级库）
('103003456.SH', '24能E1', 'company_bond', '手工调整', '调入', 'BOND20260303001', 3, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '正常调入一级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
-- 降库债2 在二级库（可降入三级/四级库）
('104004567.IB', '23基建债', 'company_bond', '手工调整', '调入', 'BOND20260304001', 4, 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '正常调入二级库', '2026-03-04 09:00:00', '2026-03-04 14:00:00', '2026-03-04 14:00:00', 0, NOW(), NOW()),
-- 普通债1 在一级库
('106006789.IB', '22电力MTN', 'mtn', '手工调整', '调入', 'BOND20260305001', 5, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '正常调入一级库', '2026-03-05 09:00:00', '2026-03-05 14:00:00', '2026-03-05 14:00:00', 0, NOW(), NOW()),
-- 到期债 在一级库（调出触发到期校验）
('105005678.IB', '22地产CP', 'cp', '手工调整', '调入', 'BOND20260306001', 6, 2, '一级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '到期债在一级库', '2026-03-06 09:00:00', '2026-03-06 14:00:00', '2026-03-06 14:00:00', 0, NOW(), NOW()),
-- 冻结期内债 在二级库（entry_time=NOW，调出触发冻结期 frozen_period_in=365）
('108008901.IB', '23某资A', 'abs', '手工调整', '调入', 'BOND20260701001', 7, 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '冻结期内债在二级库', '2026-07-01 09:00:00', '2026-07-01 14:00:00', NOW(), 0, NOW(), NOW()),
-- 担保债 在二级库
('112008888.IB', '24担保债', 'company_bond', '手工调整', '调入', 'BOND20260307001', 8, 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '担保债在二级库', '2026-03-07 09:00:00', '2026-03-07 14:00:00', '2026-03-07 14:00:00', 0, NOW(), NOW()),
-- 普通债2 在境外债库
('109009012.IB', '25国贸SCP', 'scp', '手工调整', '调入', 'BOND20260308001', 9, 7, '境外债库', 'offshore_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '在境外债库', '2026-03-08 09:00:00', '2026-03-08 14:00:00', '2026-03-08 14:00:00', 0, NOW(), NOW()),
-- 基金1 在基金池
('159001.SH', '测试基金A', 'etf_fund', '手工调整', '调入', 'BOND20260309001', 10, 22, '基金池', 'fund', 109, 'fund:fast-inbound', 'normalInbound', '20', '1', '管理员', '基金在基金池', '2026-03-09 09:00:00', '2026-03-09 09:00:00', '2026-03-09 09:00:00', 0, NOW(), NOW()),
-- 主体1 在一级库
('C10001', '交投集团', 'company', '手工调整', '调入', 'BOND20260310001', 11, 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '主体在一级库', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
-- 主体2 在二级库
('C10002', '城投公司', 'company', '手工调整', '调入', 'BOND20260311001', 12, 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '主体在二级库', '2026-03-11 09:00:00', '2026-03-11 14:00:00', '2026-03-11 14:00:00', 0, NOW(), NOW()),
-- 主体5 在禁投池（触发禁投池校验）
('C10005', '地产公司', 'company', '手工调整', '调入', 'BOND20260312001', 13, 15, '禁投池', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体在禁投池', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW());

INSERT INTO `ip_adjust_log` (
    `id`, `security_code`, `security_short_name`, `security_type`, `adjust_type`, `adjust_mode`,
    `adjust_batch_no`, `target_pool_id`, `target_pool_name`, `pool_type`,
    `flow_id`, `flow_key`, `flow_type`,
    `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`,
    `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`
) VALUES
-- === 债券-已通过(20) ===
(1, '101901234.IB', '24交投MTN001', 'mtn', '手工调整', '调入', 'BOND20260301001', 4, '三级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '升库债1调入三级库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(2, '102002345.IB', '23城投MTN', 'mtn', '手工调整', '调入', 'BOND20260302001', 5, '四级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '升库债2调入四级库', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
(3, '103003456.SH', '24能E1', 'company_bond', '手工调整', '调入', 'BOND20260303001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '降库债1调入一级库', '2026-03-03 09:00:00', '2026-03-03 14:00:00', '2026-03-03 14:00:00', 0, NOW(), NOW()),
(4, '104004567.IB', '23基建债', 'company_bond', '手工调整', '调入', 'BOND20260304001', 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '降库债2调入二级库', '2026-03-04 09:00:00', '2026-03-04 14:00:00', '2026-03-04 14:00:00', 0, NOW(), NOW()),
(5, '106006789.IB', '22电力MTN', 'mtn', '手工调整', '调入', 'BOND20260305001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '普通债1调入一级库', '2026-03-05 09:00:00', '2026-03-05 14:00:00', '2026-03-05 14:00:00', 0, NOW(), NOW()),
(6, '105005678.IB', '22地产CP', 'cp', '手工调整', '调入', 'BOND20260306001', 2, '一级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '到期债调入一级库', '2026-03-06 09:00:00', '2026-03-06 14:00:00', '2026-03-06 14:00:00', 0, NOW(), NOW()),
(7, '108008901.IB', '23某资A', 'abs', '手工调整', '调入', 'BOND20260701001', 3, '二级库', 'credit_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '冻结期内债调入二级库', '2026-07-01 09:00:00', '2026-07-01 14:00:00', NOW(), 0, NOW(), NOW()),
(8, '112008888.IB', '24担保债', 'company_bond', '手工调整', '调入', 'BOND20260307001', 3, '二级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '20', '1', '管理员', '担保债调入二级库', '2026-03-07 09:00:00', '2026-03-07 14:00:00', '2026-03-07 14:00:00', 0, NOW(), NOW()),
(9, '109009012.IB', '25国贸SCP', 'scp', '手工调整', '调入', 'BOND20260308001', 7, '境外债库', 'offshore_bond', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', '普通债2调入境外债库', '2026-03-08 09:00:00', '2026-03-08 14:00:00', '2026-03-08 14:00:00', 0, NOW(), NOW()),
-- === 债券-待审批(00) ===
(10, '110010123.IB', '23金融债', 'company_bond', '手工调整', '调入', 'BOND20260710001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '00', '1', '管理员', '错行业债调入一级库待审批', '2026-07-10 09:00:00', NULL, NULL, 0, NOW(), NOW()),
-- === 债券-驳回待修改(11) ===
(11, '107007890.IB', '24科技K', 'company_bond', '手工调整', '调入', 'BOND20260711001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '11', '1', '管理员', '低评级债调入一级库被驳回', '2026-07-11 09:00:00', NULL, NULL, 0, NOW(), NOW()),
-- === 债券-审批驳回(21) ===
(12, '107007890.IB', '24科技K', 'company_bond', '手工调整', '调入', 'BOND20260712001', 6, '五级库', 'credit_bond', 102, 'bond:standard-downgrade', 'downgradeInbound', '21', '1', '管理员', '低评级债调入五级库被驳回', '2026-07-12 09:00:00', '2026-07-12 14:00:00', NULL, 0, NOW(), NOW()),
-- === 债券-已撤回(99) ===
(13, '101901234.IB', '24交投MTN001', 'mtn', '手工调整', '调入', 'BOND20260713001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '99', '1', '管理员', '发起人撤回', '2026-07-13 09:00:00', NULL, NULL, 0, NOW(), NOW()),
-- === 基金-已通过(20) ===
(14, '159001.SH', '测试基金A', 'etf_fund', '手工调整', '调入', 'BOND20260309001', 22, '基金池', 'fund', 109, 'fund:fast-inbound', 'normalInbound', '20', '1', '管理员', '基金调入基金池', '2026-03-09 09:00:00', '2026-03-09 09:00:00', '2026-03-09 09:00:00', 0, NOW(), NOW()),
-- === 主体-已通过(20) ===
(15, 'C10001', '交投集团', 'company', '手工调整', '调入', 'BOND20260310001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '20', '1', '管理员', '主体调入一级库', '2026-03-10 09:00:00', '2026-03-10 14:00:00', '2026-03-10 14:00:00', 0, NOW(), NOW()),
(16, 'C10005', '地产公司', 'company', '手工调整', '调入', 'BOND20260312001', 15, '禁投池', 'forbidden', NULL, NULL, NULL, '20', '1', '管理员', '主体调入禁投池', '2026-03-12 09:00:00', '2026-03-12 14:00:00', '2026-03-12 14:00:00', 0, NOW(), NOW()),
-- === 主体-待审批(00) ===
(17, 'C10003', '能源集团', 'company', '手工调整', '调入', 'BOND20260714001', 2, '一级库', 'credit_bond', 101, 'bond:standard-upgrade', 'upgradeInbound', '00', '1', '管理员', '主体调入一级库待审批', '2026-07-14 09:00:00', NULL, NULL, 0, NOW(), NOW()),
-- === CRMW-已通过(20) ===
(18, '101901234.IB', '24交投MTN001', 'mtn', '手工调整', '调入', 'CRMW20260301001', 19, 'CRMW核心库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW调入核心库', '2026-03-01 09:00:00', '2026-03-01 14:00:00', '2026-03-01 14:00:00', 0, NOW(), NOW()),
(19, 'C10005', '地产公司', 'company', '手工调整', '调入', 'CRMW20260302001', 19, 'CRMW核心库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '20', '1', '管理员', 'CRMW5调入核心库（已在池）', '2026-03-02 09:00:00', '2026-03-02 14:00:00', '2026-03-02 14:00:00', 0, NOW(), NOW()),
-- === CRMW-待审批(00) ===
(20, '102002345.IB', '23城投MTN', 'mtn', '手工调整', '调入', 'CRMW20260715001', 20, 'CRMW关注库', 'crmw', 105, 'bond:fast-inbound', 'normalInbound', '00', '1', '管理员', 'CRMW调入关注库待审批', '2026-07-15 09:00:00', NULL, NULL, 0, NOW(), NOW());

INSERT INTO `ip_adjust_step` (
    `adjust_log_id`, `adjust_batch_no`, `flow_node_id`, `node_code`, `node_label`, `node_type`,
    `approval_strategy`, `sort_order`, `step_status`, `handler_id`, `handler_name`, `process_action`,
    `start_time`, `process_time`, `crte_time`, `updt_time`
) VALUES
-- 债券：log 10 待审批
(10, 'BOND20260710001', 1012, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-10 09:00:00', '2026-07-10 09:00:00', NOW(), NOW()),
(10, 'BOND20260710001', 1013, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-10 09:00:00', NULL, NOW(), NOW()),
-- 主体：log 17 待审批
(17, 'BOND20260714001', 1012, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-14 09:00:00', '2026-07-14 09:00:00', NOW(), NOW()),
(17, 'BOND20260714001', 1013, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-14 09:00:00', NULL, NOW(), NOW()),
-- CRMW：log 20 待审批
(20, 'CRMW20260715001', 1052, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-15 09:00:00', '2026-07-15 09:00:00', NOW(), NOW()),
(20, 'CRMW20260715001', 1053, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, '2026-07-15 09:00:00', NULL, NOW(), NOW()),
-- 禁投：log 16 已通过（主体入禁投池，无审批流程直通）
(16, 'BOND20260312001', NULL, NULL, '直通落地', 'auto', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', '2026-03-12 09:00:00', '2026-03-12 14:00:00', NOW(), NOW()),
-- 基金：log 14 已通过（直通流程）
(14, 'BOND20260309001', 1092, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-03-09 09:00:00', '2026-03-09 09:00:00', NOW(), NOW()),
-- 股票：log 11 驳回待修改
(11, 'BOND20260711001', 1012, 'n2', '研究员A发起', 'approval', 'initiator', 2, 'submit', '1', '管理员', 'submit', '2026-07-11 09:00:00', '2026-07-11 09:00:00', NOW(), NOW()),
(11, 'BOND20260711001', 1013, 'n3', '研究员B复核', 'approval', 'preempt', 3, 'reject', '3', '研究员2', 'reject', '2026-07-11 09:00:00', '2026-07-11 14:00:00', NOW(), NOW()),
(11, 'BOND20260711001', 1014, 'n4', '研究员A修改', 'approval', 'initiator', 4, 'pending', '1', '管理员', NULL, '2026-07-11 14:00:00', NULL, NOW(), NOW());

