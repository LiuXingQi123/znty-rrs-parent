-- ============================================================
-- znty-sirm 债券信息表 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，创建数据库和全部业务表结构
-- ============================================================

USE
znty_sirm;
SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sirm_bondinfo`;

-- ----------------------------------------------------------------------------
-- 1. 债券信息表
-- ----------------------------------------------------------------------------
CREATE TABLE `sirm_bondinfo`
(
    `id`                       BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    -- 基础及标识信息
    `s_info_code`              varchar(100)   DEFAULT NULL COMMENT '关联代码代码',
    `b_info_fullname`          varchar(300)   DEFAULT NULL COMMENT '债券全称',
    `b_issue_announcement`     varchar(10)    DEFAULT NULL COMMENT '公告日期',
    `s_info_name`              varchar(100)   DEFAULT NULL COMMENT '债券简称',
    `s_windcode_sh`            varchar(100)   DEFAULT NULL COMMENT '沪市债券代码',
    `s_windcode_sz`            varchar(100)   DEFAULT NULL COMMENT '深市债券代码',
    `s_windcode_nib`           varchar(100)   DEFAULT NULL COMMENT '银行间市场代码',
    `s_windcode_nbc`           varchar(100)   DEFAULT NULL COMMENT '其他',
    `d_bond_type`              int            DEFAULT NULL COMMENT '债券类型',
    `b_info_term_year_`        decimal(20, 4) DEFAULT NULL COMMENT '债券期限(年)',
    `b_info_term_day_`         decimal(20, 4) DEFAULT NULL COMMENT '债券期限(天)',
    `b_info_form`              varchar(20)    DEFAULT NULL COMMENT '债券形式',
    `s_info_sectypename`       varchar(300)   DEFAULT NULL COMMENT '品种类别',
    `s_info_comptype`          varchar(100)   DEFAULT NULL COMMENT '发行人类型',
    `b_info_issuer`            varchar(100)   DEFAULT NULL COMMENT '发行人',
    `b_info_issuercode`        varchar(20)    DEFAULT NULL COMMENT '发行人代码',

    -- 发行与基本条款信息
    `crncy_code`               varchar(10)    DEFAULT NULL COMMENT '币种',
    `b_issue_amountplan`       decimal(10, 0) DEFAULT NULL COMMENT '发行规模(亿元)',
    `b_info_listdate`          varchar(10)    DEFAULT NULL COMMENT '上市日期',
    `b_issue_firstissue`       varchar(10)    DEFAULT NULL COMMENT '发行日期',
    `b_info_maturitydate`      varchar(10)    DEFAULT NULL COMMENT '到期日期',
    `b_info_carrydate`         varchar(10)    DEFAULT NULL COMMENT '起息日期',
    `b_info_enddate`           varchar(10)    DEFAULT NULL COMMENT '止息日期',
    `b_info_interesttype`      varchar(20)    DEFAULT NULL COMMENT '利率类型',
    `b_info_interestfrequency` varchar(100)   DEFAULT NULL COMMENT '付息方式',
    `b_info_interestyearcount` int            DEFAULT NULL COMMENT '每年付息次数',
    `b_info_couponrate`        varchar(100)   DEFAULT NULL COMMENT '票面利率(%)',
    `b_info_spread`            varchar(100)   DEFAULT NULL COMMENT '基本利差(%)',
    `b_info_redemptionflag`    varchar(1)     DEFAULT NULL COMMENT '是否可赎回',
    `b_info_redemptiondate`    varchar(10)    DEFAULT NULL COMMENT '赎回日期',
    `b_info_redemptionprice`   decimal(10, 4) DEFAULT NULL COMMENT '赎回价格',
    `b_info_repurchaseflag`    varchar(1)     DEFAULT NULL COMMENT '可回售性',
    `b_info_repurchasedate`    varchar(10)    DEFAULT NULL COMMENT '回售日期',
    `b_info_repurchaseprice`   decimal(10, 4) DEFAULT NULL COMMENT '回售价格',
    `b_info_paymentdate`       varchar(10)    DEFAULT NULL COMMENT '兑付起始日',
    `b_info_delistdate`        varchar(10)    DEFAULT NULL COMMENT '摘牌日',

    -- 赎回、分类及特殊条款
    `b_info_redemptioncontent` longtext COMMENT '短期融资券,如果F4_1090等于DQ时-1,否则为0 / 利率说明',
    `b_info_issuetype`         longtext COMMENT '赎回条款',
    `b_info_callbkorputbkdate` varchar(100)   DEFAULT NULL COMMENT '发行方式',
    `b_info_content`           varchar(10)    DEFAULT NULL COMMENT '行权日',
    `createtime`               longtext COMMENT '赎回条款',
    `ts`                       timestamp NULL DEFAULT NULL COMMENT '创建时间',
    `is_corporate_bond`        timestamp NULL DEFAULT NULL COMMENT '更新时间',
    `is_payadvanced`           int            DEFAULT NULL COMMENT '是否公司债',
    `is_callable`              varchar(1)     DEFAULT NULL COMMENT '是否可提前兑付',
    `is_chooseright`           varchar(1)     DEFAULT NULL COMMENT '是否可赎回',
    `is_incbonds`              varchar(1)     DEFAULT NULL COMMENT '是否有选择权',
    `s_info_industryname`      varchar(1)     DEFAULT NULL COMMENT '是否增发债',
    `s_info_industryname2`     varchar(100)   DEFAULT NULL COMMENT '一级板块',
    `s_info_innerclass`        varchar(100)   DEFAULT NULL COMMENT '二级板块',
    `is_cj`                    varchar(100)   DEFAULT NULL COMMENT '内部分类',
    `is_yx`                    varchar(1)     DEFAULT NULL COMMENT '是否次级',
    `is_dy`                    varchar(1)     DEFAULT NULL COMMENT '是否永续',
    `is_deferred`              varchar(1)     DEFAULT NULL COMMENT '是否递延', -- 对应图1最后一行半遮挡的'是否递延'

    -- ==========================================
-- 1. 基础、承销与控制类字段
-- ==========================================
    `b_info_term_str`          varchar(100)   DEFAULT NULL COMMENT '债券期限(文本形式，如“4年11月1天”)',
    `b_info_lead_underwriter`  varchar(300)   DEFAULT NULL COMMENT '主承销商',
    `b_info_cur_interestrate`  decimal(10, 4) DEFAULT NULL COMMENT '当期利率(%)',
    `b_info_pledge_ratio`      decimal(10, 4) DEFAULT NULL COMMENT '质押比率(%)',
    `b_info_guarantee_status`  varchar(500)   DEFAULT NULL COMMENT '担保情况',

-- ==========================================
-- 2. 行权相关期限字段
-- ==========================================
    `b_info_rem_exe_term`      varchar(100)   DEFAULT NULL COMMENT '行权剩余期限',
    `b_info_put_exe_term`      varchar(100)   DEFAULT NULL COMMENT '回售行权期限',
    `b_info_call_rem_term`     varchar(100)   DEFAULT NULL COMMENT '赎回行权剩余期限',
    `b_info_opt_rem_term`      varchar(100)   DEFAULT NULL COMMENT '含权债剩余期限',

-- ==========================================
-- 3. 信用评级与内部评价字段
-- ==========================================
    `credit_rating_agency`     varchar(100)   DEFAULT NULL COMMENT '评级机构',
    `b_credit_rating`          varchar(50)    DEFAULT NULL COMMENT '债券评级',
    `issuer_credit_rating`     varchar(50)    DEFAULT NULL COMMENT '主体评级',
    `rating_outlook`           varchar(50)    DEFAULT NULL COMMENT '展望评级',
    `inner_issuer_rating`      varchar(50)    DEFAULT NULL COMMENT '主体内评分档',
    `inner_guarantor_rating`   varchar(50)    DEFAULT NULL COMMENT '担保人主体内评分',

-- ==========================================
-- 4. 页面下方大文本框字段
-- ==========================================
    `b_fund_usage`             longtext       DEFAULT NULL COMMENT '募集资金用途',
    `b_prompt_reason`          longtext       DEFAULT NULL COMMENT '提示原因',
    `b_analysis`               longtext       DEFAULT NULL COMMENT '债券分析'

    -- 主键设置（可根据业务实际需求调整，这里默认以自增id或核心代码作为参考）
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '债券信息表';