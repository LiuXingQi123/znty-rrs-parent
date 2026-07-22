-- ============================================================
-- znty-rrs 外部导入表 - 演示数据脚本
-- 说明：
--   1. 存放外部导入类表演示数据（非本系统业务运行态数据）
--   2. 当前包含：rrs_securityinfo；后续同类表在本文件追加
--   3. 与 rrs_security_pool_adjust_demo_data.sql 解耦，独立维护
--   4. 不随「初始化 Demo 数据 / 重建完整演示环境」主库批量任务执行，需单独触发
--   5. rrs_securityinfo 覆盖：债券(可调+触发) / 股票 / 基金 / 主体 / CRMW
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `rrs_securityinfo`;

INSERT INTO `rrs_securityinfo` (
    `wind_code`, `full_name`, `short_name`, `wind_code_sh`, `wind_code_sz`, `wind_code_nib`,
    `security_type`, `term_year`, `term_day`, `maturity_date`, `delist_date`, `security_status`,
    `industry_name`, `industry_name2`,
    `rating_bond`, `rating_bondissuer`, `rating_outlook`, `inner_issuer_rating`, `inner_guarantor_rating`,
    `guarant_flag`, `guarantor`, `guarantor_id`, `guarant_type`, `issuer`, `issuer_code`,
    `list_date`, `firstissue_date`, `carry_date`, `end_date`,
    `coupon_rate`, `spread`, `interest_type`, `interest_frequency`, `interest_yearcount`,
    `crncy_code`, `issue_amountplan`, `par`, `info_pledge_ratio`,
    `form_desc`, `sec_typename`, `comp_type`, `issue_type`,
    `redemption_flag`, `redemption_date`, `callbkorputbk_date`, `date_next`,
    `corporate_bond_flag`, `payadvanced_flag`, `chooseright_flag`, `incbonds_flag`,
    `inner_class`, `cj_flag`, `yx_flag`, `dy_flag`, `abs_flag`,
    `rating_bond_agency`, `rating_bondissuer_agency`,
    `security_source`, `create_time`, `ts`,
    `sec_id_sh`, `sec_id_sz`, `sec_id_nib`, `sec_id_nbc`, `sec_id_bj`
) VALUES
-- ===== 债券-可调整 =====
('101901234.IB', '24某交投MTN001', '24交投MTN001', NULL, NULL, '101901234.IB', 'mtn', 3, 1095, '20290318', '20290318', 'L', '制造业', '交通运输', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, '某交投集团', 'C10001', '20240318', '20240315', '20240318', '20290318', '3.5', '1.2', '固定利率', '年付', 1, 'CNY', 10, 100, 0, '固息', '中期票据', '国有企业', '公开招标', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('102002345.IB', '23某城投MTN001', '23城投MTN', NULL, NULL, '102002345.IB', 'mtn', 5, 1825, '20290815', '20290815', 'L', '制造业', '城市基建', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, '某城投公司', 'C10002', '20230815', '20230812', '20230815', '20290815', '4.2', '1.8', '固定利率', '年付', 1, 'CNY', 15, 100, 0, '固息', '中期票据', '城投公司', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('103003456.SH', '24某能E1', '24能E1', '103003456.SH', NULL, NULL, 'company_bond', 3, 1095, '20290110', '20290110', 'L', '建筑', '能源开采', 'AA+', 'AA+', '稳定', '2+', NULL, 0, NULL, NULL, NULL, '某能源集团', 'C10003', '20240110', '20240108', '20240110', '20290110', '1.8', '0.5', '固定利率', '年付', 1, 'CNY', 8, 100, 0, '固息', '公司债', '国有企业', '公开招标', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('104004567.IB', '23某基建债01', '23基建债', NULL, NULL, '104004567.IB', 'company_bond', 5, 1825, '20301124', '20301124', 'L', '基建', '交通基建', 'AA', 'AA', '稳定', '2', NULL, 0, NULL, NULL, NULL, '某基建公司', 'C10004', '20231124', '20231122', '20231124', '20301124', '4.5', '2.1', '固定利率', '年付', 1, 'CNY', 20, 100, 0, '固息', '公司债', '国有企业', '簿记建档', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('106006789.IB', '22某电力MTN001', '22电力MTN', NULL, NULL, '106006789.IB', 'mtn', 7, 2555, '20300916', '20300916', 'L', '制造业', '电力设备', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, '某电力公司', 'C10006', '20220916', '20220914', '20220916', '20300916', '3.2', '1.0', '固定利率', '年付', 1, 'CNY', 20, 100, 0, '固息', '中期票据', '国有企业', '公开招标', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('109009012.IB', '25某国贸SCP001', '25国贸SCP', NULL, NULL, '109009012.IB', 'scp', 2, 730, '20280828', '20280828', 'L', '贸易', '国际贸易', 'AA+', 'AA+', '稳定', '2', NULL, 0, NULL, NULL, NULL, '某国贸公司', 'C10009', '20250228', '20250226', '20250228', '20280828', '2.8', '0.3', '固定利率', '到期一次还本', 0, 'CNY', 10, 100, 0, '固息', '超短期融资券', '国有企业', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
-- ===== 债券-触发校验 =====
('105005678.IB', '22某地产CP001', '22地产CP', NULL, NULL, '105005678.IB', 'cp', 0.75, 270, '20250615', '20250615', 'L', '住宅开发', '房地产开发', 'A-1', 'AA+', '负面', '2', NULL, 0, NULL, NULL, NULL, '某地产公司', 'C10005', '20220615', '20220613', '20220615', '20250615', '5.5', '3.2', '固定利率', '到期一次还本', 0, 'CNY', 5, 100, 0, '固息', '短期融资券', '民营企业', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('107007890.IB', '24某科技K1', '24科技K', NULL, NULL, '107007890.IB', 'company_bond', 3, 1095, '20280408', '20280408', 'L', '软件服务', '信息技术', 'A', 'A', '负面', '3-', NULL, 0, NULL, NULL, NULL, '某科技公司', 'C10007', '20240408', '20240405', '20240408', '20280408', '6.8', '4.5', '固定利率', '年付', 1, 'CNY', 5, 100, 0, '固息', '公司债', '民营企业', '簿记建档', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('110010123.IB', '23某金融债001', '23金融债', NULL, NULL, '110010123.IB', 'company_bond', 7, 2555, '20320619', '20320619', 'L', '金融', '其他金融', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, '某金融公司', 'C10010', '20230619', '20230616', '20230619', '20320619', '3.8', '1.5', '固定利率', '年付', 1, 'CNY', 30, 100, 0, '固息', '公司债', '国有企业', '公开招标', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('112008888.IB', '24某担保债001', '24担保债', NULL, NULL, '112008888.IB', 'company_bond', 3, 1095, '20290320', '20290320', 'L', '建筑', '城市建设', 'AA', 'AA-', '稳定', '3', '2', 1, '某担保集团,某增信担保', 'C90011,C90012', '连带责任保证', '某被担保公司', 'C90005', '20240320', '20240318', '20240320', '20290320', '5.0', '2.8', '固定利率', '年付', 1, 'CNY', 8, 100, 0, '固息', '公司债', '城投公司', '簿记建档', 0, NULL, NULL, NULL, 1, 0, 0, 0, '信用债', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('108008901.IB', '23某资A1', '23某资A', NULL, NULL, '108008901.IB', 'abs', 1, 365, '20271206', '20271206', 'L', 'AMC', '资产管理', 'AAA', 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, '某资产管理公司', 'C10008', '20231206', '20231204', '20231206', '20271206', '3.0', '1.0', '固定利率', '年付', 1, 'CNY', 30, 100, 0, '固息', '资产支持证券', '资产管理公司', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'ABS', 0, 0, 0, 1, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
-- ===== 股票-可调整 =====
('600002.SH', '某正常股份', '正常股A', '600002.SH', NULL, NULL, 'a_share', NULL, NULL, NULL, '20281231', 'L', '制造业', '制造业', NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, '某正常公司A', 'C90002', '20221231', '20221228', '20221231', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, 'A股', '股份有限公司', 'IPO', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('600003.SH', '某科技股份', '科技股B', '600003.SH', NULL, NULL, 'a_share', NULL, NULL, NULL, '20290630', 'L', '软件服务', '信息技术', NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, '某科技公司B', 'C90006', '20230630', '20230628', '20230630', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, 'A股', '股份有限公司', 'IPO', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
-- ===== 股票-触发校验 =====
('600001.SH', '某退市股份', '退市股', '600001.SH', NULL, NULL, 'a_share', NULL, NULL, NULL, '20250615', 'D', '制造业', '制造业', NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, '某退市公司', 'C90001', '20200615', '20200612', '20200615', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, 'A股', '股份有限公司', 'IPO', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
-- ===== 基金-可调整 =====
('159001.SH', '某ETF基金', '测试基金A', '159001.SH', NULL, NULL, 'etf_fund', NULL, NULL, NULL, NULL, 'L', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, '某基金公司', 'C90003', '20230101', '20221228', '20230101', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, 'ETF基金', '基金公司', '公开发售', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('159002.SH', '某开放基金', '开放基金B', '159002.SH', NULL, NULL, 'open_fund', NULL, NULL, NULL, NULL, 'L', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, '某基金公司B', 'C90007', '20230301', '20230226', '20230301', NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, 1, 0, NULL, '开放式基金', '基金公司', '公开发售', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
-- ===== 主体-可调整 =====
('C10001', '某交投集团', '交投集团', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '交通运输', '高速公路', NULL, 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, NULL, 'C10001', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('C10002', '某城投公司', '城投公司', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '建筑', '城市基建', NULL, 'AA+', '稳定', '2+', NULL, 0, NULL, NULL, NULL, NULL, 'C10002', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('C10003', '某能源集团', '能源集团', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '煤炭', '能源开采', NULL, 'AAA', '稳定', '1', NULL, 0, NULL, NULL, NULL, NULL, 'C10003', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('C10004', '某电力公司', '电力公司', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '制造业', '电力设备', NULL, 'AA', '稳定', '2', NULL, 0, NULL, NULL, NULL, NULL, 'C10004', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
-- ===== 主体-触发校验 =====
('C10005', '某地产公司', '地产公司', NULL, NULL, NULL, 'company', NULL, NULL, NULL, NULL, 'L', '住宅开发', '房地产开发', NULL, 'AA+', '负面', '3', NULL, 0, NULL, NULL, NULL, NULL, 'C10005', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
-- ===== CRMW-可调整 =====
('CRMW001.IB', '某CRMW凭证A', 'CRMW-A', NULL, NULL, 'CRMW001.IB', 'crmw', 2, 730, '20280615', '20280615', 'L', NULL, NULL, 'AAA', 'AAA', '稳定', NULL, NULL, 0, NULL, NULL, NULL, '某交投集团', 'C10001', '20260115', '20260112', '20260115', '20280615', NULL, NULL, NULL, NULL, NULL, 'CNY', 5, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('CRMW002.IB', '某CRMW凭证B', 'CRMW-B', NULL, NULL, 'CRMW002.IB', 'crmw', 3, 1095, '20290615', '20290615', 'L', NULL, NULL, 'AA+', 'AA+', '稳定', NULL, NULL, 0, NULL, NULL, NULL, '某城投公司', 'C10002', '20260215', '20260212', '20260215', '20290615', NULL, NULL, NULL, NULL, NULL, 'CNY', 8, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('CRMW003.IB', '某CRMW凭证C', 'CRMW-C', NULL, NULL, 'CRMW003.IB', 'crmw', 1, 365, '20270615', '20270615', 'L', NULL, NULL, 'AA', 'AA', '稳定', NULL, NULL, 0, NULL, NULL, NULL, '某能源集团', 'C10003', '20260315', '20260312', '20260315', '20270615', NULL, NULL, NULL, NULL, NULL, 'CNY', 3, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
('CRMW004.IB', '某CRMW凭证D', 'CRMW-D', NULL, NULL, 'CRMW004.IB', 'crmw', 1.5, 548, '20271215', '20271215', 'L', NULL, NULL, 'AA+', 'AA+', '稳定', NULL, NULL, 0, NULL, NULL, NULL, '某电力公司', 'C10004', '20260415', '20260412', '20260415', '20271215', NULL, NULL, NULL, NULL, NULL, 'CNY', 5, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '联合资信', '联合资信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL),
-- ===== CRMW-触发校验 =====
('CRMW005.IB', '某CRMW凭证E', 'CRMW-E', NULL, NULL, 'CRMW005.IB', 'crmw', 2, 730, '20280615', '20280615', 'L', NULL, NULL, 'AA', 'AA', '稳定', NULL, NULL, 0, NULL, NULL, NULL, '某地产公司', 'C10005', '20260515', '20260512', '20260515', '20280615', NULL, NULL, NULL, NULL, NULL, 'CNY', 5, 100, 0, NULL, 'CRMW', '金融机构', '簿记建档', 0, NULL, NULL, NULL, 0, 0, 0, 0, 'CRMW', 0, 0, 0, 0, '中诚信', '中诚信', 'official', NOW(), NOW(), NULL, NULL, NULL, NULL, NULL);

-- 剩余期限（天）：演示数据用 term_day 近似填入 date_exists（正式环境由外部源预计算写入）
UPDATE `rrs_securityinfo`
SET `date_exists` = `term_day`
WHERE `term_day` IS NOT NULL;

