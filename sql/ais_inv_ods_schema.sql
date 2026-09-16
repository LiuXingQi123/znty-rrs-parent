-- ============================================================
-- AIS 投资 ODS 库 - Wind 债券发行人、评级与公司财务指标表建表脚本
-- MySQL version: 8.0.33
-- 说明：表结构与公司原有 wind_cbondissuer / wind_cbondissuerrating 表保持一致
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ais_inv_ods` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ais_inv_ods`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `wind_companyfinancial`;
DROP TABLE IF EXISTS `wind_cbondissuerrating`;
DROP TABLE IF EXISTS `wind_cbondissuer`;

-- ----------------------------------------------------------------------------
-- 创建 Wind 债券发行人主体表
-- 粒度说明：本表为「债券 + 主体」维度（s_info_windcode + s_info_compcode），
-- 同一 s_info_compcode 可对应多只债券多行，业务按主体查询时必须对 s_info_compcode 去重。
-- ----------------------------------------------------------------------------
CREATE TABLE `wind_cbondissuer` (
    `object_id`                    VARCHAR(100)  NOT NULL     COMMENT '对象 ID',
    `s_info_windcode`              VARCHAR(40)   DEFAULT NULL COMMENT 'Wind 证券代码（与主体组合构成本表业务粒度）',
    `s_info_compname`              VARCHAR(100)  DEFAULT NULL COMMENT '发行主体名称',
    `s_info_compcode`              VARCHAR(10)   DEFAULT NULL COMMENT '发行主体代码（同一主体可对应多行债券）',
    `used`                         INT           DEFAULT NULL COMMENT '使用标识：1=有效 / 0=无效',
    `s_info_compind_code1`         VARCHAR(50)   DEFAULT NULL COMMENT '一级行业代码',
    `s_info_compind_name1`         VARCHAR(100)  DEFAULT NULL COMMENT '一级行业名称',
    `s_info_compind_code2`         VARCHAR(50)   DEFAULT NULL COMMENT '二级行业代码',
    `s_info_compind_name2`         VARCHAR(100)  DEFAULT NULL COMMENT '二级行业名称',
    `s_info_compind_code3`         VARCHAR(50)   DEFAULT NULL COMMENT '三级行业代码',
    `s_info_compind_name3`         VARCHAR(100)  DEFAULT NULL COMMENT '三级行业名称',
    `s_info_compind_code4`         VARCHAR(50)   DEFAULT NULL COMMENT '四级行业代码',
    `s_info_compind_name4`         VARCHAR(100)  DEFAULT NULL COMMENT '四级行业名称',
    `s_info_compregaddress`        VARCHAR(200)  DEFAULT NULL COMMENT '发行主体注册地址',
    `s_info_comptype`              VARCHAR(40)   DEFAULT NULL COMMENT '发行主体类型',
    `s_info_listcompornot`         INT           DEFAULT NULL COMMENT '是否上市：1=是 / 0=否',
    `s_info_effective_dt`          VARCHAR(8)    DEFAULT NULL COMMENT '生效日期，yyyyMMdd',
    `s_info_invalid_dt`            VARCHAR(8)    DEFAULT NULL COMMENT '失效日期，yyyyMMdd',
    `b_agency_guarantornature`     VARCHAR(40)   DEFAULT NULL COMMENT '担保主体性质',
    `is_fin_inst`                  INT           DEFAULT NULL COMMENT '是否金融机构：1=是 / 0=否',
    `s_info_typecode`              BIGINT        DEFAULT NULL COMMENT '发行主体类型编码',
    `opdate`                       DATE          DEFAULT NULL COMMENT '操作日期',
    `opmode`                       VARCHAR(1)    DEFAULT NULL COMMENT '操作模式：0=新增 / 1=修改 / 2=删除',
    PRIMARY KEY (`object_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Wind 中国债券发行主体信息表（债券+主体粒度，同主体可多行；外部 ODS 表一般不自建二级索引）';

-- ----------------------------------------------------------------------------
-- 创建 Wind 债券发行人评级表
-- ----------------------------------------------------------------------------
CREATE TABLE `wind_cbondissuerrating` (
    `object_id`                    VARCHAR(100)  NOT NULL     COMMENT '对象 ID',
    `s_info_compname`              VARCHAR(100)  DEFAULT NULL COMMENT '公司名称',
    `ann_dt`                       VARCHAR(8)    DEFAULT NULL COMMENT '公告日期，yyyyMMdd',
    `b_rate_style`                 VARCHAR(100)  DEFAULT NULL COMMENT '评级类型',
    `b_info_creditrating`          VARCHAR(40)   DEFAULT NULL COMMENT '主体信用评级',
    `b_rate_ratingoutlook`         DOUBLE        DEFAULT NULL COMMENT '评级展望',
    `b_info_creditratingagency`    VARCHAR(10)   DEFAULT NULL COMMENT '评级机构；有效外评机构由 znty_rrs.dict_external_rating_agency 配置',
    `s_info_compcode`              VARCHAR(10)   DEFAULT NULL COMMENT '公司代码',
    `b_info_creditratingexplain`   VARCHAR(1000) DEFAULT NULL COMMENT '评级说明',
    `b_info_precreditrating`       VARCHAR(40)   DEFAULT NULL COMMENT '前次主体信用评级',
    `b_creditratingchange`         VARCHAR(10)   DEFAULT NULL COMMENT '信用评级变动',
    `b_info_issuerratetype`        DOUBLE        DEFAULT NULL COMMENT '发行人评级类型',
    `ann_dt2`                      VARCHAR(8)    DEFAULT NULL COMMENT '评级日期，yyyyMMdd',
    `opdate`                       DATE          DEFAULT NULL COMMENT '操作日期',
    `opmode`                       VARCHAR(1)    DEFAULT NULL COMMENT '操作模式',
    PRIMARY KEY (`object_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Wind 中国债券发行主体信用评级表';

-- ----------------------------------------------------------------------------
-- 创建 Wind 公司财务指标表
-- 粒度说明：本表以公司编码与报告日期为联合主键；COMPANYCODE 对齐发行主体代码
--           （与 wind_cbondissuer.s_info_compcode / Demo 主体 C10001～C10010 一致）；
--           报告日期格式为 yyyyMMdd，末四位仅可为 0331（一季报）/ 0630（半年报）/
--           0930（三季报）/ 1231（年报）。绝对金额字段单位均为亿元。
-- ----------------------------------------------------------------------------
CREATE TABLE `wind_companyfinancial` (
    `COMPANYCODE`             VARCHAR(40) NOT NULL     COMMENT '公司编码（对齐主体代码 s_info_compcode）',
    `REPORTDATE`              BIGINT      NOT NULL     COMMENT '报告日期，yyyyMMdd，末四位：0331=一季报 / 0630=半年报 / 0930=三季报 / 1231=年报',
    `CUR_RATIO`               FLOAT       DEFAULT NULL COMMENT '流动比率',
    `QUICK_RATIO`             FLOAT       DEFAULT NULL COMMENT '速动比率',
    `AR_TURNOVER`             FLOAT       DEFAULT NULL COMMENT '应收账款周转率',
    `AR_TURN_DAYS`            FLOAT       DEFAULT NULL COMMENT '应收账款周转天数',
    `CASH_TO_SHORT_DEBT`      BIGINT      DEFAULT NULL COMMENT '现金与短期债务比率',
    `EBIT_INT_COV`            BIGINT      DEFAULT NULL COMMENT 'EBIT 利息保障倍数',
    `LT_DEBT_WK_CAP`          FLOAT       DEFAULT NULL COMMENT '长期债务与营运资本比率',
    `EM_INVRS`                BIGINT      DEFAULT NULL COMMENT '权益乘数',
    `INVST_CAP_RATIO`         FLOAT       DEFAULT NULL COMMENT '投资资本比率',
    `EPS_YOY_ADJ`             BIGINT      DEFAULT NULL COMMENT '每股收益增长率调整',
    `DEBT_EQY_RATIO`          BIGINT      DEFAULT NULL COMMENT '负债权益比',
    `NON_CUR_AS_EQY`          BIGINT      DEFAULT NULL COMMENT '非流动资产对股东权益比',
    `CHG_RATIO`               FLOAT       DEFAULT NULL COMMENT '产权比率',
    `TANGIBLE_LIAB_COV`       BIGINT      DEFAULT NULL COMMENT '有形资产负债债比率',
    `FIX_AS_PCT`              BIGINT      DEFAULT NULL COMMENT '固定资产占比',
    `INV_TURNOVER`            FLOAT       DEFAULT NULL COMMENT '存货周转率',
    `FIX_AS_TURN`             FLOAT       DEFAULT NULL COMMENT '固定资产周转率',
    `AT_TURNOVER`             FLOAT       DEFAULT NULL COMMENT '总资产周转率',
    `INV_TURN_DAYS`           FLOAT       DEFAULT NULL COMMENT '存货周转天数',
    `AT_TURN_DAYS`            BIGINT      DEFAULT NULL COMMENT '总资产周转天数',
    `CUR_AS_TURN`             FLOAT       DEFAULT NULL COMMENT '流动资产周转率',
    `CUR_AS_TURN_DAYS`        BIGINT      DEFAULT NULL COMMENT '流动资产周转天数',
    `ROA`                     FLOAT       DEFAULT NULL COMMENT '总资产收益率 ROA',
    `PROF_OP_COST_RATIO`      BIGINT      DEFAULT NULL COMMENT '利润总额占营业成本比率',
    `OP_PROFIT_REV_PCT`       FLOAT       DEFAULT NULL COMMENT '营业利润率占营收比',
    `COST_REV_RATIO`          BIGINT      DEFAULT NULL COMMENT '成本收入比',
    `NET_MARGIN`              FLOAT       DEFAULT NULL COMMENT '净利率',
    `ROE`                     FLOAT       DEFAULT NULL COMMENT '净资产收益率 ROE',
    `ROA2`                    FLOAT       DEFAULT NULL COMMENT '总资产收益率二次',
    `GPM`                     FLOAT       DEFAULT NULL COMMENT '毛利率',
    `THREE_EXP_REV_PCT`       BIGINT      DEFAULT NULL COMMENT '三费占营收比',
    `OTHER_REV_PCT`           BIGINT      DEFAULT NULL COMMENT '其他业务收入占比',
    `GPM_PROF_COV`            BIGINT      DEFAULT NULL COMMENT '毛利利润总额覆盖率',
    `EPS_DILUTED_V2`          FLOAT       DEFAULT NULL COMMENT '稀释每股收益 v2',
    `YOY_TR`                  FLOAT       DEFAULT NULL COMMENT '营业收入同比增长率',
    `OCF_REV_RATIO`           FLOAT       DEFAULT NULL COMMENT '经营现金流/营业收入',
    `OCF_AT_RATIO`            BIGINT      DEFAULT NULL COMMENT '总资产经营现金流比',
    `OCF_DEBT_RATIO`          FLOAT       DEFAULT NULL COMMENT '经营现金流/总债务',
    `OCF_SHORT_DEBT_RATIO`    BIGINT      DEFAULT NULL COMMENT '经营现金流短期债务比',
    `REV_SHR_EQY_RATIO`       BIGINT      DEFAULT NULL COMMENT '营业收入对股东权益比',
    `EPS_DILUTED`             FLOAT       DEFAULT NULL COMMENT '稀释每股收益',
    `BPS`                     FLOAT       DEFAULT NULL COMMENT '每股净资产',
    `OCFPS`                   FLOAT       DEFAULT NULL COMMENT '每股经营现金流',
    `DEBT_ASSETS_RATIO`       FLOAT       DEFAULT NULL COMMENT '资产负债率',
    `OP_PROFIT`               FLOAT       DEFAULT NULL COMMENT '营业利润（亿元）',
    `TOT_ASSETS`              FLOAT       DEFAULT NULL COMMENT '总资产（亿元）',
    `EPS_DILUTED_V3`          FLOAT       DEFAULT NULL COMMENT '稀释每股收益 v3',
    `ROE_DEDUCTED`            FLOAT       DEFAULT NULL COMMENT '扣非 ROE',
    `SURPLUS_CAP_PS`          FLOAT       DEFAULT NULL COMMENT '每股资本公积',
    `DEDUCTED_PROFIT`         FLOAT       DEFAULT NULL COMMENT '扣非净利润（亿元）',
    `UNDIST_PS`               FLOAT       DEFAULT NULL COMMENT '每股未分配利润',
    `INT_DEBT`                FLOAT       DEFAULT NULL COMMENT '有息债务（亿元）',
    `EBITDA`                  FLOAT       DEFAULT NULL COMMENT 'EBITDA（亿元）',
    `EBITDA_TO_DEBT`          FLOAT       DEFAULT NULL COMMENT '总债务/EBITDA',
    `NET_CASH_OPER`           FLOAT       DEFAULT NULL COMMENT '经营性净现金流（亿元）',
    `NET_CASH_INV`            FLOAT       DEFAULT NULL COMMENT '投资性净现金流（亿元）',
    `NET_PROFIT`              FLOAT       DEFAULT NULL COMMENT '净利润（亿元）',
    `TOT_REV`                 FLOAT       DEFAULT NULL COMMENT '营业收入（亿元）',
    `ts`                      TIMESTAMP   NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '时间戳',
    `GRP`                     FLOAT       DEFAULT NULL COMMENT '地区生产总值（亿元）',
    `GENERAL_BUDGET_REV`      FLOAT       DEFAULT NULL COMMENT '一般预算收入（亿元）',
    `GENERAL_BUDGET_EXP`      FLOAT       DEFAULT NULL COMMENT '一般预算支出（亿元）',
    `SHAREHOLDER_EQUITY`      FLOAT       DEFAULT NULL COMMENT '所有者权益（亿元）',
    `PROVINCE`                VARCHAR(50) DEFAULT NULL COMMENT '省份',
    `CITY`                    VARCHAR(50) DEFAULT NULL COMMENT '城市',
    PRIMARY KEY (`COMPANYCODE`, `REPORTDATE`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Wind 公司财务指标表';
