-- ============================================================
-- znty-sirm 字典/配置表 - 演示数据脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，插入证券类型等字典配置基础数据
-- ============================================================

USE `znty_sirm`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 清空字典表
-- ----------------------------------------------------------------------------
TRUNCATE TABLE `dict_security_type`;

-- ----------------------------------------------------------------------------
-- 证券类型字典数据
-- ----------------------------------------------------------------------------
INSERT INTO `dict_security_type` (`id`, `security_type`, `security_type_name`, `category_type`, `category_type_name`, `sort_order`, `is_deleted`, `crte_time`, `updt_time`) VALUES
-- 债券类
(1,  'treasury_bond',      '国债',         'bond',    '债券', 11, 0, NOW(), NOW()),
(2,  'local_gov_bond',     '地方政府债',   'bond',    '债券', 12, 0, NOW(), NOW()),
(3,  'financial_bond',     '金融债',       'bond',    '债券', 13, 0, NOW(), NOW()),
(4,  'enterprise_bond',    '企业债',       'bond',    '债券', 14, 0, NOW(), NOW()),
(5,  'company_bond',       '公司债',       'bond',    '债券', 15, 0, NOW(), NOW()),
(6,  'mtn',               '中期票据',      'bond',    '债券', 16, 0, NOW(), NOW()),
(7,  'cp',                '短期融资券',    'bond',    '债券', 17, 0, NOW(), NOW()),
(8,  'ncd',               '同业存单',      'bond',    '债券', 18, 0, NOW(), NOW()),
(9,  'convertible_bond',   '可转债',       'bond',    '债券', 19, 0, NOW(), NOW()),
(10, 'exchangeable_bond',  '可交换债',     'bond',    '债券', 20, 0, NOW(), NOW()),
(11, 'subordinated_bond',  '次级债',       'bond',    '债券', 21, 0, NOW(), NOW()),
(12, 'abs',               '资产支持证券',  'bond',    '债券', 22, 0, NOW(), NOW()),
(22, 'scp',               '超短期融资券',  'bond',    '债券', 23, 0, NOW(), NOW()),
(23, 'bank_bond',         '商业银行债',    'bond',    '债券', 24, 0, NOW(), NOW()),
-- 股票类
(13, 'a_share',           'A股',           'stock',   '股票', 31, 0, NOW(), NOW()),
(14, 'b_share',           'B股',           'stock',   '股票', 32, 0, NOW(), NOW()),
(15, 's_share',           'S股',           'stock',   '股票', 33, 0, NOW(), NOW()),
(16, 'h_share',           '港股',          'stock',   '股票', 34, 0, NOW(), NOW()),
-- 基金类
(17, 'etf_fund',          'ETF基金',       'fund',    '基金', 41, 0, NOW(), NOW()),
(18, 'lof_fund',          'LOF基金',       'fund',    '基金', 42, 0, NOW(), NOW()),
(19, 'closed_fund',       '封闭式基金',    'fund',    '基金', 43, 0, NOW(), NOW()),
-- 公司主体类
(20, 'company',           '公司主体',      'company', '公司主体', 51, 0, NOW(), NOW());
