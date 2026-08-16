-- ============================================================
-- znty-rrs 调库信息快照表 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：
--   1. 调库提交时落库的信息快照；语义上可对照老系统 FINANCIALINDICATORS 的「提交快照」职责。
--   2. 本脚本含两张表，与调库三链路中「证券池 / CRMW 池」对应（禁投池主体侧一期不建）：
--        ip_adjust_security_snapshot       证券池调库证券信息快照
--        ip_adjust_security_snapshot_crmw  CRMW 池调库证券信息快照
--      命名对齐 ip_pool_status / ip_pool_status_crmw。
--   3. 快照不是 rrs_securityinfo 全量拷贝；与 rrs 语义对应的字段列名/类型/注释与那边一致。
--   4. 仅存在于快照侧的扩展/预留字段可使用本表自有命名。
--   5. CRMW 表在证券快照字段基础上，增加与 ip_adjust_log / ip_pool_status_crmw
--      一致的凭证四元组：crmw_name / crmw_scode / crmw_mktcode / crmw_stype
--      （标的证券字段仍用 rrs 同名列，凭证侧用 crmw_*，与运行态表一致）。
--   6. 一期不建 _evt；多期财报另表。
--   7. 字段顺序：快照关联键 →（CRMW 表：凭证四元组）→ 调库详情已用字段
--      → 预留字段 → is_deleted / crte_time / updt_time 固定置尾。
--   8. 已注册 ScriptTool：INIT_SCHEMA / RESET_ALL / 结构差异 / 表清空 / CLEAR_ADJUST_FLOW。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ============================================================================
-- 1. 调库证券信息快照表（证券池调库）
-- ============================================================================
DROP TABLE IF EXISTS `ip_adjust_security_snapshot`;

CREATE TABLE `ip_adjust_security_snapshot`
(
    -- ========== 快照关联（本表专有） ==========
    `id`                         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `adjust_log_id`              BIGINT         DEFAULT NULL COMMENT '关联调库日志 ID，逻辑关联 ip_adjust_log.id',
    `submitter_id`               VARCHAR(32)    DEFAULT NULL COMMENT '填写人 ID',
    `submit_time`                DATETIME       DEFAULT NULL COMMENT '本条快照提交时间',

    -- ========== 调库详情页已用字段（与 rrs_securityinfo 对应的用同名同类型） ==========
    `wind_code`                  VARCHAR(100)   DEFAULT NULL COMMENT '关联代码',
    `full_name`                  VARCHAR(300)   DEFAULT NULL COMMENT '证券全称',
    `short_name`                 VARCHAR(100)   DEFAULT NULL COMMENT '证券简称',
    `issuer`                     VARCHAR(100)   DEFAULT NULL COMMENT '发行人',
    `issuer_code`                VARCHAR(20)    DEFAULT NULL COMMENT '发行人代码',
    `wind_code_nib`              VARCHAR(100)   DEFAULT NULL COMMENT '银行间市场代码',
    `wind_code_sh`               VARCHAR(100)   DEFAULT NULL COMMENT '沪市证券代码',
    `wind_code_sz`               VARCHAR(100)   DEFAULT NULL COMMENT '深市证券代码',
    `wind_code_bj`               VARCHAR(100)   DEFAULT NULL COMMENT '北交所代码',
    `issue_amountplan`           DECIMAL(10, 0) DEFAULT NULL COMMENT '发行规模(亿元)',
    `coupon_rate`                VARCHAR(100)   DEFAULT NULL COMMENT '票面利率(%)',
    `date_inright_exists`        DECIMAL(10, 4) DEFAULT NULL COMMENT '含权债剩余期限-最新',
    `carry_date`                 VARCHAR(10)    DEFAULT NULL COMMENT '起息日期',
    `maturity_date`              VARCHAR(10)    DEFAULT NULL COMMENT '到期日期',
    `info_pledge_ratio`          DECIMAL(10, 4) DEFAULT NULL COMMENT '质押比率(%)',
    `rating_bond_agency`         VARCHAR(400)   DEFAULT NULL COMMENT '债券评级机构',
    `rating_bond`                VARCHAR(10)    DEFAULT NULL COMMENT '债券评级',
    `rating_bondissuer`          VARCHAR(10)    DEFAULT NULL COMMENT '主体评级',
    `rating_outlook`             VARCHAR(10)    DEFAULT NULL COMMENT '展望评级',
    `guarantor`                  VARCHAR(400)   DEFAULT NULL COMMENT '担保人',
    `guarantor_id`               VARCHAR(1000)  DEFAULT NULL COMMENT '担保人ID',
    `agency_name`                VARCHAR(1000)  DEFAULT NULL COMMENT '主承销商',
    `inner_issuer_rating`        VARCHAR(50)    DEFAULT NULL COMMENT '主体内评分档',
    `security_type`              VARCHAR(32)    DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `sec_typename`               VARCHAR(300)   DEFAULT NULL COMMENT '品种类别',
    `date_call_exists`           DECIMAL(10, 4) DEFAULT NULL COMMENT '赎回剩余期限-最新',
    `inner_guarantor_rating`     VARCHAR(50)    DEFAULT NULL COMMENT '担保人主体内评分',
    `date_exists`                DECIMAL(10, 4) DEFAULT NULL COMMENT '剩余期限-最新（天）',
    `fund_use`                   LONGTEXT       DEFAULT NULL COMMENT '资金募集用途',
    `prompt_reason`              LONGTEXT       DEFAULT NULL COMMENT '提示原因',
    `analysis`                   LONGTEXT       DEFAULT NULL COMMENT '证券分析',

    -- ========== 预留：与 rrs_securityinfo 对应的仍用同名同类型 ==========
    `wind_code_nbc`              VARCHAR(100)   DEFAULT NULL COMMENT '其他',
    `term_year`                  DECIMAL(10, 4) DEFAULT NULL COMMENT '证券期限(年)',
    `term_day`                   DECIMAL(10, 4) DEFAULT NULL COMMENT '证券期限(天)',
    `maturityembedded_desc`      VARCHAR(255)   DEFAULT NULL COMMENT '含权期限说明',
    `comp_type`                  VARCHAR(100)   DEFAULT NULL COMMENT '发行人类型',
    `issue_amountact`            DECIMAL(30, 10) DEFAULT NULL COMMENT '发行总额-亿',
    `date_repurchase_exists`     DECIMAL(10, 4) DEFAULT NULL COMMENT '回购剩余期限-最新',
    `abs_flag`                   INT            DEFAULT NULL COMMENT '是否 ABS',
    `guarant_flag`               INT            DEFAULT NULL COMMENT '是否担保',
    `guarant_type`               VARCHAR(100)   DEFAULT NULL COMMENT '担保类型',
    `rating_bondissuer_agency`   VARCHAR(400)   DEFAULT NULL COMMENT '主体评级机构',
    `agency_nameid`              VARCHAR(1000)  DEFAULT NULL COMMENT '主承销商Id',

    -- ========== 预留：本表自有扩展（无 rrs_securityinfo 对应列） ==========
    `bond_biz_type`              INT            DEFAULT NULL COMMENT '债项业务细类：1=产业类等 / 2=城投类（老 bondType 预留）',
    `abs_originator_name`        VARCHAR(256)   DEFAULT NULL COMMENT 'ABS 相关主体/权益人名称（预留）',
    `abs_share_ratio`            DECIMAL(20, 8) DEFAULT NULL COMMENT 'ABS 分层比例（预留）',
    `expected_maturity_date`     VARCHAR(10)    DEFAULT NULL COMMENT '预期到期日（预留）',
    `legal_maturity_date`        VARCHAR(10)    DEFAULT NULL COMMENT '法定到期日（预留）',
    `abs_report_org`             VARCHAR(128)   DEFAULT NULL COMMENT '中介/报告机构（预留）',
    `abs_custodian`              VARCHAR(128)   DEFAULT NULL COMMENT '托管机构（预留）',
    `company_selector`           VARCHAR(256)   DEFAULT NULL COMMENT '主体选择器展示值（预留）',
    `extra_ind_1`                VARCHAR(500)   DEFAULT NULL COMMENT '扩展指标1（对照老 fi5，预留）',
    `extra_ind_2`                VARCHAR(500)   DEFAULT NULL COMMENT '扩展指标2（对照老 fi6，预留）',
    `extra_ind_3`                VARCHAR(500)   DEFAULT NULL COMMENT '扩展指标3（对照老 fi7，预留）',

    -- ========== 公共尾字段（固定置尾） ==========
    `is_deleted`                 TINYINT(1)     DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`                  DATETIME       DEFAULT NULL COMMENT '创建时间',
    `updt_time`                  DATETIME       DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '调库证券信息快照表';

-- ============================================================================
-- 2. CRMW 调库证券信息快照表（CRMW 池调库，与表 1 同构 + 凭证四元组）
-- ============================================================================
DROP TABLE IF EXISTS `ip_adjust_security_snapshot_crmw`;

CREATE TABLE `ip_adjust_security_snapshot_crmw`
(
    -- ========== 快照关联（本表专有） ==========
    `id`                         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `adjust_log_id`              BIGINT         DEFAULT NULL COMMENT '关联调库日志 ID，逻辑关联 ip_adjust_log.id',
    `submitter_id`               VARCHAR(32)    DEFAULT NULL COMMENT '填写人 ID',
    `submit_time`                DATETIME       DEFAULT NULL COMMENT '本条快照提交时间',

    -- ========== CRMW 凭证（与 ip_adjust_log / ip_pool_status_crmw 一致） ==========
    `crmw_name`                  VARCHAR(128)   DEFAULT NULL COMMENT 'CRMW名称',
    `crmw_scode`                 VARCHAR(32)    DEFAULT NULL COMMENT 'CRMW 证券代码',
    `crmw_mktcode`               VARCHAR(32)    DEFAULT NULL COMMENT 'CRMW 市场代码',
    `crmw_stype`                 VARCHAR(32)    DEFAULT NULL COMMENT 'CRMW 证券类型',

    -- ========== 标的证券：调库详情页已用字段（与 rrs_securityinfo 对应的用同名同类型） ==========
    `wind_code`                  VARCHAR(100)   DEFAULT NULL COMMENT '关联代码',
    `full_name`                  VARCHAR(300)   DEFAULT NULL COMMENT '证券全称',
    `short_name`                 VARCHAR(100)   DEFAULT NULL COMMENT '证券简称',
    `issuer`                     VARCHAR(100)   DEFAULT NULL COMMENT '发行人',
    `issuer_code`                VARCHAR(20)    DEFAULT NULL COMMENT '发行人代码',
    `wind_code_nib`              VARCHAR(100)   DEFAULT NULL COMMENT '银行间市场代码',
    `wind_code_sh`               VARCHAR(100)   DEFAULT NULL COMMENT '沪市证券代码',
    `wind_code_sz`               VARCHAR(100)   DEFAULT NULL COMMENT '深市证券代码',
    `wind_code_bj`               VARCHAR(100)   DEFAULT NULL COMMENT '北交所代码',
    `issue_amountplan`           DECIMAL(10, 0) DEFAULT NULL COMMENT '发行规模(亿元)',
    `coupon_rate`                VARCHAR(100)   DEFAULT NULL COMMENT '票面利率(%)',
    `date_inright_exists`        DECIMAL(10, 4) DEFAULT NULL COMMENT '含权债剩余期限-最新',
    `carry_date`                 VARCHAR(10)    DEFAULT NULL COMMENT '起息日期',
    `maturity_date`              VARCHAR(10)    DEFAULT NULL COMMENT '到期日期',
    `info_pledge_ratio`          DECIMAL(10, 4) DEFAULT NULL COMMENT '质押比率(%)',
    `rating_bond_agency`         VARCHAR(400)   DEFAULT NULL COMMENT '债券评级机构',
    `rating_bond`                VARCHAR(10)    DEFAULT NULL COMMENT '债券评级',
    `rating_bondissuer`          VARCHAR(10)    DEFAULT NULL COMMENT '主体评级',
    `rating_outlook`             VARCHAR(10)    DEFAULT NULL COMMENT '展望评级',
    `guarantor`                  VARCHAR(400)   DEFAULT NULL COMMENT '担保人',
    `guarantor_id`               VARCHAR(1000)  DEFAULT NULL COMMENT '担保人ID',
    `agency_name`                VARCHAR(1000)  DEFAULT NULL COMMENT '主承销商',
    `inner_issuer_rating`        VARCHAR(50)    DEFAULT NULL COMMENT '主体内评分档',
    `security_type`              VARCHAR(32)    DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `sec_typename`               VARCHAR(300)   DEFAULT NULL COMMENT '品种类别',
    `date_call_exists`           DECIMAL(10, 4) DEFAULT NULL COMMENT '赎回剩余期限-最新',
    `inner_guarantor_rating`     VARCHAR(50)    DEFAULT NULL COMMENT '担保人主体内评分',
    `date_exists`                DECIMAL(10, 4) DEFAULT NULL COMMENT '剩余期限-最新（天）',
    `fund_use`                   LONGTEXT       DEFAULT NULL COMMENT '资金募集用途',
    `prompt_reason`              LONGTEXT       DEFAULT NULL COMMENT '提示原因',
    `analysis`                   LONGTEXT       DEFAULT NULL COMMENT '证券分析',

    -- ========== 预留：与 rrs_securityinfo 对应的仍用同名同类型 ==========
    `wind_code_nbc`              VARCHAR(100)   DEFAULT NULL COMMENT '其他',
    `term_year`                  DECIMAL(10, 4) DEFAULT NULL COMMENT '证券期限(年)',
    `term_day`                   DECIMAL(10, 4) DEFAULT NULL COMMENT '证券期限(天)',
    `maturityembedded_desc`      VARCHAR(255)   DEFAULT NULL COMMENT '含权期限说明',
    `comp_type`                  VARCHAR(100)   DEFAULT NULL COMMENT '发行人类型',
    `issue_amountact`            DECIMAL(30, 10) DEFAULT NULL COMMENT '发行总额-亿',
    `date_repurchase_exists`     DECIMAL(10, 4) DEFAULT NULL COMMENT '回购剩余期限-最新',
    `abs_flag`                   INT            DEFAULT NULL COMMENT '是否 ABS',
    `guarant_flag`               INT            DEFAULT NULL COMMENT '是否担保',
    `guarant_type`               VARCHAR(100)   DEFAULT NULL COMMENT '担保类型',
    `rating_bondissuer_agency`   VARCHAR(400)   DEFAULT NULL COMMENT '主体评级机构',
    `agency_nameid`              VARCHAR(1000)  DEFAULT NULL COMMENT '主承销商Id',

    -- ========== 预留：本表自有扩展（无 rrs_securityinfo 对应列） ==========
    `bond_biz_type`              INT            DEFAULT NULL COMMENT '债项业务细类：1=产业类等 / 2=城投类（老 bondType 预留）',
    `abs_originator_name`        VARCHAR(256)   DEFAULT NULL COMMENT 'ABS 相关主体/权益人名称（预留）',
    `abs_share_ratio`            DECIMAL(20, 8) DEFAULT NULL COMMENT 'ABS 分层比例（预留）',
    `expected_maturity_date`     VARCHAR(10)    DEFAULT NULL COMMENT '预期到期日（预留）',
    `legal_maturity_date`        VARCHAR(10)    DEFAULT NULL COMMENT '法定到期日（预留）',
    `abs_report_org`             VARCHAR(128)   DEFAULT NULL COMMENT '中介/报告机构（预留）',
    `abs_custodian`              VARCHAR(128)   DEFAULT NULL COMMENT '托管机构（预留）',
    `company_selector`           VARCHAR(256)   DEFAULT NULL COMMENT '主体选择器展示值（预留）',
    `extra_ind_1`                VARCHAR(500)   DEFAULT NULL COMMENT '扩展指标1（对照老 fi5，预留）',
    `extra_ind_2`                VARCHAR(500)   DEFAULT NULL COMMENT '扩展指标2（对照老 fi6，预留）',
    `extra_ind_3`                VARCHAR(500)   DEFAULT NULL COMMENT '扩展指标3（对照老 fi7，预留）',

    -- ========== 公共尾字段（固定置尾） ==========
    `is_deleted`                 TINYINT(1)     DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`                  DATETIME       DEFAULT NULL COMMENT '创建时间',
    `updt_time`                  DATETIME       DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'CRMW调库证券信息快照表';
