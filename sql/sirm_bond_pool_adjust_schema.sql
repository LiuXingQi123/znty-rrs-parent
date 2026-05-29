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
    `id`                         BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键 ID',

    -- ==========================================
    -- 基础及标识信息
    -- ==========================================
    `s_info_code`                varchar(100)    DEFAULT NULL COMMENT '关联代码',
    `b_info_fullname`            varchar(300)    DEFAULT NULL COMMENT '债券全称',
    `b_issue_announcement`       varchar(10)     DEFAULT NULL COMMENT '公告日期',
    `s_info_name`                varchar(100)    DEFAULT NULL COMMENT '债券简称',
    `s_windcode_sh`              varchar(100)    DEFAULT NULL COMMENT '沪市债券代码',
    `s_windcode_sz`              varchar(100)    DEFAULT NULL COMMENT '深市债券代码',
    `s_windcode_nib`             varchar(100)    DEFAULT NULL COMMENT '银行间市场代码',
    `s_windcode_nbc`             varchar(100)    DEFAULT NULL COMMENT '其他',
    `d_bond_type`                int             DEFAULT NULL COMMENT '债券类型',
    `b_info_term_year_`          decimal(20, 4)  DEFAULT NULL COMMENT '债券期限(年)',
    `b_info_term_day_`           decimal(20, 4)  DEFAULT NULL COMMENT '债券期限(天)',
    `b_info_form`                varchar(20)     DEFAULT NULL COMMENT '债券形式',
    `s_info_sectypename`         varchar(300)    DEFAULT NULL COMMENT '品种类别',
    `s_info_comptype`            varchar(100)    DEFAULT NULL COMMENT '发行人类型',
    `b_info_issuer`              varchar(100)    DEFAULT NULL COMMENT '发行人',
    `b_info_issuercode`          varchar(20)     DEFAULT NULL COMMENT '发行人代码',

    -- ==========================================
    -- 发行与基本条款信息
    -- ==========================================
    `crncy_code`                 varchar(10)     DEFAULT NULL COMMENT '币种',
    `b_issue_amountplan`         decimal(10, 0)  DEFAULT NULL COMMENT '发行规模(亿元)',
    `b_info_listdate`            varchar(10)     DEFAULT NULL COMMENT '上市日期',
    `b_issue_firstissue`         varchar(10)     DEFAULT NULL COMMENT '发行日期',
    `b_info_maturitydate`        varchar(10)     DEFAULT NULL COMMENT '到期日期',
    `b_info_carrydate`           varchar(10)     DEFAULT NULL COMMENT '起息日期',
    `b_info_enddate`             varchar(10)     DEFAULT NULL COMMENT '止息日期',
    `b_info_interesttype`        varchar(20)     DEFAULT NULL COMMENT '利率类型',
    `b_info_interestfrequency`   varchar(100)    DEFAULT NULL COMMENT '付息方式',
    `b_info_interestyearcount`   int             DEFAULT NULL COMMENT '每年付息次数',
    `b_info_couponrate`          varchar(100)    DEFAULT NULL COMMENT '票面利率(%)',
    `b_info_spread`              varchar(100)    DEFAULT NULL COMMENT '基本利差(%)',

    -- ==========================================
    -- 赎回 / 回售 / 兑付条款
    -- ==========================================
    `is_redemption`              varchar(1)      DEFAULT NULL COMMENT '是否可赎回',
    `b_info_redemptiondate`      varchar(10)     DEFAULT NULL COMMENT '赎回日期',
    `b_info_redemptionprice`     decimal(10, 4)  DEFAULT NULL COMMENT '赎回价格',
    `is_repurchase`              varchar(1)      DEFAULT NULL COMMENT '可回售性',
    `b_info_repurchasedate`      varchar(10)     DEFAULT NULL COMMENT '回售日期',
    `b_info_repurchaseprice`     decimal(10, 4)  DEFAULT NULL COMMENT '回售价格',
    `b_info_paymentdate`         varchar(10)     DEFAULT NULL COMMENT '兑付起始日',
    `b_info_delistdate`          varchar(10)     DEFAULT NULL COMMENT '摘牌日',
    `s_info_securitiestypes_dq`  int             DEFAULT NULL COMMENT '短期融资券,如果F4_1090等于DQ时-1,否则为0',
    `b_info_coupontxt`           longtext        COMMENT '利率说明',
    `b_info_redemptioncontent`   longtext        COMMENT '赎回条款',
    `b_info_issuetype`           varchar(100)    DEFAULT NULL COMMENT '发行方式',
    `b_info_callbkorputbkdate`   varchar(10)     DEFAULT NULL COMMENT '行权日',
    `b_info_content`             longtext        COMMENT '赎回条款',
    `createtime`                 timestamp NULL  DEFAULT NULL COMMENT '创建时间',
    `ts`                         timestamp NULL  DEFAULT NULL COMMENT '更新时间',

    -- ==========================================
    -- 分类与特殊条款标识
    -- ==========================================
    `is_corporate_bond`          int             DEFAULT NULL COMMENT '是否公司债',
    `is_payadvanced`             varchar(1)      DEFAULT NULL COMMENT '是否可提前兑付',
    `is_callable`                varchar(1)      DEFAULT NULL COMMENT '是否可赎回',
    `is_chooseright`             varchar(1)      DEFAULT NULL COMMENT '是否有选择权',
    `is_incbonds`                varchar(1)      DEFAULT NULL COMMENT '是否增发债',
    `s_info_industryname`        varchar(100)    DEFAULT NULL COMMENT '一级板块',
    `s_info_industryname2`       varchar(100)    DEFAULT NULL COMMENT '二级板块',
    `s_info_innerclass`          varchar(100)    DEFAULT NULL COMMENT '内部分类',
    `is_cj`                      varchar(1)      DEFAULT NULL COMMENT '是否次级',
    `is_yx`                      varchar(1)      DEFAULT NULL COMMENT '是否永续',
    `is_dy`                      varchar(1)      DEFAULT NULL COMMENT '是否递延',

    -- ==========================================
    -- 剩余期限相关字段
    -- ==========================================
    `date_redemtion_exists`      varchar(10)     DEFAULT NULL COMMENT '回售剩余期限-最新',
    `date_call_exists`           varchar(10)     DEFAULT NULL COMMENT '赎回剩余期限-最新',
    `date_exists`                varchar(10)     DEFAULT NULL COMMENT '债券期限-最新',
    `date_inright_exists`        varchar(10)     DEFAULT NULL COMMENT '含权债剩余期限-最新',

    -- ==========================================
    -- 评级相关字段
    -- ==========================================
    `rating_bond`                varchar(10)     DEFAULT NULL COMMENT '债券评级',
    `rating_bondissuer`          varchar(10)     DEFAULT NULL COMMENT '主体评级',
    `rating_outlook`             varchar(10)     DEFAULT NULL COMMENT '展望评级',
    `rating_bond_agency`         varchar(400)    DEFAULT NULL COMMENT '债券评级机构',
    `rating_bondissuer_agency`   varchar(400)    DEFAULT NULL COMMENT '主体评级机构',
    `rating_cnbd`                varchar(400)    DEFAULT NULL COMMENT '中债隐含评级',

    -- ==========================================
    -- 含权债相关字段
    -- ==========================================
    `is_inright`                 varchar(1)      DEFAULT NULL COMMENT '是否含权债',
    `date_inright_next`          varchar(10)     DEFAULT NULL COMMENT '含权债下一个行权日',

    -- ==========================================
    -- 发行额、担保与承销相关字段
    -- ==========================================
    `b_issue_amountact`          decimal(30, 10) DEFAULT NULL COMMENT '发行总额-亿',
    `b_agency_grnttype`          varchar(400)    DEFAULT NULL COMMENT '担保方式',
    `guarantor`                  varchar(400)    DEFAULT NULL COMMENT '担保人',
    `guarantor_id`               varchar(40)     DEFAULT NULL COMMENT '担保人ID',
    `b_agency_name`              varchar(400)    DEFAULT NULL COMMENT '主承销商',
    `b_agency_nameid`            varchar(400)    DEFAULT NULL COMMENT '主承销商Id',

    -- ==========================================
    -- 末尾追加字段（质押比率、内评分、文本说明）
    -- ==========================================
    `b_info_pledge_ratio`        decimal(10, 4)  DEFAULT NULL COMMENT '质押比率(%)',
    `inner_issuer_rating`        varchar(50)     DEFAULT NULL COMMENT '主体内评分档',
    `inner_guarantor_rating`     varchar(50)     DEFAULT NULL COMMENT '担保人主体内评分',
    `b_fund_usage`               longtext        COMMENT '募集资金用途',
    `b_prompt_reason`            longtext        COMMENT '提示原因',
    `b_analysis`                 longtext        COMMENT '债券分析',

    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '债券信息表';

-- ============================================================================
-- 2. 债券池调库记录表
-- ============================================================================
DROP TABLE IF EXISTS `ip_adjust_log`;

CREATE TABLE `ip_adjust_log`
(
    `id`               BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `bond_id`          BIGINT       DEFAULT NULL COMMENT '债券 ID，关联 sirm_bondinfo.id',
    `bond_code`        VARCHAR(32)  DEFAULT NULL COMMENT '债券代码',
    `bond_short_name`  VARCHAR(128) DEFAULT NULL COMMENT '债券简称',
    `bond_type`        VARCHAR(32)  DEFAULT NULL COMMENT '债券类型：中期票据/公司债/可交换债/商业银行债/短期融资券/资产支持证券/超短期融资券/其他',
    `adjust_type`      VARCHAR(32)  DEFAULT NULL COMMENT '调整类型：手工调整/联动调整/互斥调整/关联调整/Excel导入/手动批量调整',
    `adjust_mode`      VARCHAR(8)   DEFAULT NULL COMMENT '调整模式：调入/调出',
    `target_pool_id`   BIGINT       DEFAULT NULL COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name` VARCHAR(128) DEFAULT NULL COMMENT '目标投资池名称',
    `pool_type`        VARCHAR(32)  DEFAULT NULL COMMENT '投资池类型：research=研究池 / fund=基金池 / restricted=限制池 / other=其他池 / industry=行业池 / whitelist=白名单 / blacklist=黑名单 / private_placement=私募池',
    `audit_status`     VARCHAR(4)   DEFAULT NULL COMMENT '审核状态：-1=无效调整 / 00=待审核 / 10=审核通过 / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 99=已撤回',
    `adjuster_id`      VARCHAR(32)  DEFAULT NULL COMMENT '调整人 ID',
    `adjuster_name`    VARCHAR(64)  DEFAULT NULL COMMENT '调整人名称',
    `adjust_reason`    VARCHAR(500) DEFAULT NULL COMMENT '调整原因',
    `adjust_advice`    VARCHAR(500) DEFAULT NULL COMMENT '调整意见',
    `attachment_files` TEXT         DEFAULT NULL COMMENT '附件报告文件路径（JSON数组）',
    `material_files`   TEXT         DEFAULT NULL COMMENT '其他材料文件路径（JSON数组）',
    `submit_time`      DATETIME     DEFAULT NULL COMMENT '提交时间',
    `audit_time`       DATETIME     DEFAULT NULL COMMENT '审核时间',
    `entry_time`       DATETIME     DEFAULT NULL COMMENT '入池时间',
    `is_deleted`       TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`        DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`        DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '债券池调库记录表';
