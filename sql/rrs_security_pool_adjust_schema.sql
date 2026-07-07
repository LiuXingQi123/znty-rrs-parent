-- ============================================================
-- znty-rrs 证券信息表 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，创建数据库和全部业务表结构
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `rrs_securityinfo`;

-- ----------------------------------------------------------------------------
-- 1. 证券信息表
-- ----------------------------------------------------------------------------
CREATE TABLE `rrs_securityinfo`
(
    `id`                         BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键 ID',

    -- ==========================================
    -- 基础及标识信息
    -- ==========================================
    `wind_code`                  varchar(100)    DEFAULT NULL COMMENT '关联代码',
    `full_name`                  varchar(300)    DEFAULT NULL COMMENT '证券全称',
    `issue_announcement`         varchar(10)     DEFAULT NULL COMMENT '公告日期',
    `short_name`                 varchar(100)    DEFAULT NULL COMMENT '证券简称',
    `wind_code_sh`              varchar(100)    DEFAULT NULL COMMENT '沪市证券代码',
    `wind_code_sz`              varchar(100)    DEFAULT NULL COMMENT '深市证券代码',
    `wind_code_nib`             varchar(100)    DEFAULT NULL COMMENT '银行间市场代码',
    `wind_code_bj`              varchar(100)    DEFAULT NULL COMMENT '北交所代码',
    `wind_code_nbc`             varchar(100)    DEFAULT NULL COMMENT '其他',
    `security_type`              varchar(32)     DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `term_year`                  decimal(20, 4)  DEFAULT NULL COMMENT '证券期限(年)',
    `term_day`                   decimal(20, 4)  DEFAULT NULL COMMENT '证券期限(天)',
    `maturityembedded_desc`      varchar(255)    DEFAULT NULL COMMENT '含权期限说明',
    `form_desc`                  varchar(20)     DEFAULT NULL COMMENT '证券形式',
    `sec_typename`               varchar(300)    DEFAULT NULL COMMENT '品种类别',
    `comp_type`                  varchar(100)    DEFAULT NULL COMMENT '发行人类型',
    `issuer`                     varchar(100)    DEFAULT NULL COMMENT '发行人',
    `issuer_code`                varchar(20)     DEFAULT NULL COMMENT '发行人代码',

    -- ==========================================
    -- 发行与基本条款信息
    -- ==========================================
    `crncy_code`                 varchar(10)     DEFAULT NULL COMMENT '币种',
    `issue_amountplan`           decimal(10, 0)  DEFAULT NULL COMMENT '发行规模(亿元)',
    `list_date`                  varchar(10)     DEFAULT NULL COMMENT '上市日期',
    `firstissue_date`            varchar(10)     DEFAULT NULL COMMENT '发行日期',
    `maturity_date`              varchar(10)     DEFAULT NULL COMMENT '到期日期',
    `carry_date`                 varchar(10)     DEFAULT NULL COMMENT '起息日期',
    `end_date`                   varchar(10)     DEFAULT NULL COMMENT '止息日期',
    `interest_type`              varchar(20)     DEFAULT NULL COMMENT '利率类型',
    `interest_frequency`         varchar(100)    DEFAULT NULL COMMENT '付息方式',
    `interest_yearcount`         int             DEFAULT NULL COMMENT '每年付息次数',
    `coupon_rate`                varchar(100)    DEFAULT NULL COMMENT '票面利率(%)',
    `spread`                     varchar(100)    DEFAULT NULL COMMENT '基本利差(%)',

    -- ==========================================
    -- 赎回 / 回售 / 兑付条款
    -- ==========================================
    `redemption_flag`            int             DEFAULT NULL COMMENT '是否可赎回',
    `redemption_date`            varchar(10)     DEFAULT NULL COMMENT '赎回日期',
    `redemption_price`           decimal(10, 4)  DEFAULT NULL COMMENT '赎回价格',
    `date_call_exists`           varchar(10)     DEFAULT NULL COMMENT '赎回剩余期限-最新',
    `callbkorputbk_date`         varchar(10)     DEFAULT NULL COMMENT '赎回行权日',
    `redemption_content`         longtext        COMMENT '赎回条款',
    `repurchase_flag`            int             DEFAULT NULL COMMENT '可回售性',
    `date_redemtion_exists`      varchar(10)     DEFAULT NULL COMMENT '回售剩余期限-最新',
    `repurchase_date`            varchar(10)     DEFAULT NULL COMMENT '回售日期',
    `repurchase_price`           decimal(10, 4)  DEFAULT NULL COMMENT '回售价格',
    `repurchase_content`         longtext        COMMENT '回售条款',
    `payment_date`               varchar(10)     DEFAULT NULL COMMENT '兑付起始日',
    `delist_date`                varchar(10)     DEFAULT NULL COMMENT '摘牌日',
    `securities_types_dq`        int             DEFAULT NULL COMMENT '短期融资券,如果F4_1090等于DQ时-1,否则为0',
    `coupon_desc`                longtext        COMMENT '利率说明',
    `issue_type`                 varchar(100)    DEFAULT NULL COMMENT '发行方式',
    `corporate_bond_flag`        int             DEFAULT NULL COMMENT '是否公司债',
    `payadvanced_flag`           int             DEFAULT NULL COMMENT '是否可提前兑付',
    `chooseright_flag`           int             DEFAULT NULL COMMENT '是否有选择权',
    `incbonds_flag`              int             DEFAULT NULL COMMENT '是否增发债',
    `industry_name`              varchar(100)    DEFAULT NULL COMMENT '一级板块',
    `industry_name2`             varchar(100)    DEFAULT NULL COMMENT '二级板块',

    -- ==========================================
    -- 分类与期限
    -- ==========================================
    `inner_class`                varchar(100)    DEFAULT NULL COMMENT '内部分类',
    `cj_flag`                    int             DEFAULT NULL COMMENT '是否次级',
    `yx_flag`                    int             DEFAULT NULL COMMENT '是否永续',
    `dy_flag`                    int             DEFAULT NULL COMMENT '是否递延',
    `date_exists`                varchar(10)     DEFAULT NULL COMMENT '证券期限-最新',

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
    `inright_flag`               int             DEFAULT NULL COMMENT '是否含权债',
    `date_inright_exists`        varchar(10)     DEFAULT NULL COMMENT '含权债剩余期限-最新',
    `date_inright_next`          varchar(10)     DEFAULT NULL COMMENT '含权债下一个行权日',

    -- ==========================================
    -- 发行额、担保与承销相关字段
    -- ==========================================
    `issue_amountact`            decimal(30, 10) DEFAULT NULL COMMENT '发行总额-亿',
    `agency_grnttype`            varchar(400)    DEFAULT NULL COMMENT '担保方式',
    `guarantor`                  varchar(400)    DEFAULT NULL COMMENT '担保人',
    `guarantor_id`               varchar(1000)   DEFAULT NULL COMMENT '担保人ID',
    `agency_name`                varchar(1000)   DEFAULT NULL COMMENT '主承销商',
    `agency_nameid`              varchar(1000)   DEFAULT NULL COMMENT '主承销商Id',
    `create_time`                timestamp NULL  DEFAULT NULL COMMENT '创建时间',
    `ts`                         timestamp NULL  DEFAULT NULL COMMENT '更新时间',
    `security_status`            varchar(1)      DEFAULT NULL COMMENT '证券状态：L=上市中 / N=待上市 / D=退市',
    `date_next`                  varchar(10)     DEFAULT NULL COMMENT '下一个行权日（回售/赎回）',
    `fund_use`                   longtext        COMMENT '资金募集用途',
    `par`                        decimal(20, 4)  DEFAULT NULL COMMENT '面值',

    -- ==========================================
    -- 扩展字段
    -- ==========================================
    `info_pledge_ratio`          decimal(10, 4)  DEFAULT NULL COMMENT '质押比率(%)',
    `inner_issuer_rating`        varchar(50)     DEFAULT NULL COMMENT '主体内评分档',
    `inner_guarantor_rating`     varchar(50)     DEFAULT NULL COMMENT '担保人主体内评分',
    `prompt_reason`              longtext        COMMENT '提示原因',
    `analysis`                   longtext        COMMENT '证券分析',
    `date_repurchase_exists`     varchar(10)     DEFAULT NULL COMMENT '回购剩余期限-最新',
    `guarant_flag`               int             DEFAULT NULL COMMENT '是否担保',
    `guarant_type`               varchar(100)    DEFAULT NULL COMMENT '担保类型',
    `abs_flag`                   int             DEFAULT NULL COMMENT '是否 ABS',

    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '证券信息表';

-- ============================================================================
-- 2. 证券池调库记录表
-- ============================================================================
DROP TABLE IF EXISTS `ip_adjust_log`;

CREATE TABLE `ip_adjust_log`
(
    `id`               BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `security_code`        VARCHAR(32)  DEFAULT NULL COMMENT '证券代码',
    `security_short_name`  VARCHAR(128) DEFAULT NULL COMMENT '证券简称',
    `security_type`        VARCHAR(32)  DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `crmw_name`            VARCHAR(128) DEFAULT NULL COMMENT 'CRMW名称',
    `crmw_scode`           VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券代码',
    `crmw_mktcode`         VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 市场代码',
    `crmw_stype`           VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券类型',
    `adjust_type`      VARCHAR(32)  DEFAULT NULL COMMENT '调整类型：手工调整/联动调整/互斥调整/关联调整/自动调整/Excel导入/手动批量调整',
    `adjust_mode`      VARCHAR(8)   DEFAULT NULL COMMENT '调整模式：调入/调出',
    `adjust_batch_no`  VARCHAR(64)  DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用',
    `target_pool_id`   BIGINT       DEFAULT NULL COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name` VARCHAR(128) DEFAULT NULL COMMENT '目标投资池名称',
    `pool_type`        VARCHAR(32)  DEFAULT NULL COMMENT '投资池类型：与 ip_investment_pool.pool_type 一致（credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品 / crmw=CRMW库 / forbidden=禁投池 / observe=观察池 等）',
    `flow_id`          BIGINT       DEFAULT NULL COMMENT '流程定义 ID 快照',
    `flow_key`         VARCHAR(128) DEFAULT NULL COMMENT '流程 Key 快照',
    `flow_type`        VARCHAR(32)  DEFAULT NULL COMMENT '流程类型快照',
    `audit_status`     VARCHAR(4)   DEFAULT NULL COMMENT '审核状态：-1=无效调整 / 00=流程中（待审批/审批中） / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 32=O32自动审批 / 99=发起人已撤回',
    `adjuster_id`      VARCHAR(32)  DEFAULT NULL COMMENT '调整人 ID',
    `adjuster_name`    VARCHAR(64)  DEFAULT NULL COMMENT '调整人名称',
    `adjust_reason`    VARCHAR(500) DEFAULT NULL COMMENT '调整原因',
    `adjust_advice`    VARCHAR(500) DEFAULT NULL COMMENT '调整意见',
    `submit_time`      DATETIME     DEFAULT NULL COMMENT '提交时间',
    `audit_time`       DATETIME     DEFAULT NULL COMMENT '审核时间',
    `entry_time`       DATETIME     DEFAULT NULL COMMENT '入池时间',
    `is_deleted`       TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`        DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`        DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '证券池调库记录表';

-- ============================================================================
-- 3. 投资池当前状态表
-- ============================================================================
DROP TABLE IF EXISTS `ip_pool_status`;

CREATE TABLE `ip_pool_status`
(
    `id`               BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `security_code`        VARCHAR(32)  DEFAULT NULL COMMENT '证券代码',
    `security_short_name`  VARCHAR(128) DEFAULT NULL COMMENT '证券简称',
    `security_type`        VARCHAR(32)  DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `crmw_name`            VARCHAR(128) DEFAULT NULL COMMENT 'CRMW名称',
    `crmw_scode`           VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券代码',
    `crmw_mktcode`         VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 市场代码',
    `crmw_stype`           VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券类型',
    `adjust_type`      VARCHAR(32)  DEFAULT NULL COMMENT '调整类型：手工调整/联动调整/互斥调整/关联调整/自动调整/Excel导入/手动批量调整',
    `adjust_mode`      VARCHAR(8)   DEFAULT NULL COMMENT '调整模式：调入/调出',
    `adjust_batch_no`  VARCHAR(64)  DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用',
    `adjust_log_id`    BIGINT       DEFAULT NULL COMMENT '来源调库日志 ID',
    `target_pool_id`   BIGINT       DEFAULT NULL COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name` VARCHAR(128) DEFAULT NULL COMMENT '目标投资池名称',
    `pool_type`        VARCHAR(32)  DEFAULT NULL COMMENT '投资池类型：与 ip_investment_pool.pool_type 一致（credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品 / crmw=CRMW库 / forbidden=禁投池 / observe=观察池 等）',
    `flow_id`          BIGINT       DEFAULT NULL COMMENT '流程定义 ID 快照',
    `flow_key`         VARCHAR(128) DEFAULT NULL COMMENT '流程 Key 快照',
    `flow_type`        VARCHAR(32)  DEFAULT NULL COMMENT '流程类型快照',
    `audit_status`     VARCHAR(4)   DEFAULT NULL COMMENT '审核状态：-1=无效调整 / 00=流程中（待审批/审批中） / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 32=O32自动审批 / 99=发起人已撤回',
    `adjuster_id`      VARCHAR(32)  DEFAULT NULL COMMENT '调整人 ID',
    `adjuster_name`    VARCHAR(64)  DEFAULT NULL COMMENT '调整人名称',
    `adjust_reason`    VARCHAR(500) DEFAULT NULL COMMENT '调整原因',
    `adjust_advice`    VARCHAR(500) DEFAULT NULL COMMENT '调整意见',
    `submit_time`      DATETIME     DEFAULT NULL COMMENT '提交时间',
    `audit_time`       DATETIME     DEFAULT NULL COMMENT '审核时间',
    `entry_time`       DATETIME     DEFAULT NULL COMMENT '入池时间',
    `is_deleted`       TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`        DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`        DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '投资池当前状态表';

-- ============================================================================
-- 4. 证券池调库-流程步骤记录表
-- ============================================================================
DROP TABLE IF EXISTS `ip_adjust_step`;

CREATE TABLE `ip_adjust_step`
(
    `id`                BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `adjust_log_id`     BIGINT       DEFAULT NULL COMMENT '关联调库记录 ID',
    `adjust_batch_no`   VARCHAR(64)  DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用',
    `flow_node_id`      BIGINT       DEFAULT NULL COMMENT '关联流程节点 ID',
    `node_code`         VARCHAR(32)  DEFAULT NULL COMMENT '节点业务标识',
    `node_label`        VARCHAR(128) DEFAULT NULL COMMENT '节点显示名称',
    `node_type`         VARCHAR(32)  DEFAULT NULL COMMENT '节点类型：start/approval/auto/end/notify/condition',
    `approval_strategy` VARCHAR(16)  DEFAULT NULL COMMENT '审批策略：preempt=抢占审批 / all=会签 / initiator=发起人',
    `sort_order`        INT          DEFAULT NULL COMMENT '排序序号',
    `step_status`       VARCHAR(16)  DEFAULT NULL COMMENT '步骤处理状态：pending=待处理 / approve=通过 / reject=驳回 / submit=提交 / auto_process=自动处理 / canceled=已撤回',
    `handler_id`        VARCHAR(32)  DEFAULT NULL COMMENT '处理人 ID',
    `handler_name`      VARCHAR(64)  DEFAULT NULL COMMENT '处理人名称',
    `process_action`    VARCHAR(16)  DEFAULT NULL COMMENT '处理动作：submit=提交 / approve=通过 / reject=驳回 / auto_process=自动处理 / skipped=被跳过',
    `process_comment`   VARCHAR(500) DEFAULT NULL COMMENT '处理意见',
    `start_time`        DATETIME     DEFAULT NULL COMMENT '步骤激活时间',
    `process_time`      DATETIME     DEFAULT NULL COMMENT '处理时间',
    `crte_time`         DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`         DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '证券池调库-流程步骤记录表';
