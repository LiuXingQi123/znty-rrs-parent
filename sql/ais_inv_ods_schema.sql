-- ============================================================
-- AIS 投资 ODS 库 - Wind 公司简介、债券发行人、评级、公司财务指标、跨市场证券代码与代码变更表建表脚本
-- MySQL version: 8.0.33
-- 说明：表结构与公司原有 wind_cbondissuer / wind_cbondissuerrating 表保持一致
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ais_inv_ods` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ais_inv_ods`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
-- 删除Wind 公司简介表（全量主体）
DROP TABLE IF EXISTS `wind_compintroduction`;
-- 删除Wind 中国债券 Wind 代码变更表
DROP TABLE IF EXISTS `wind_cbondchangewindcode`;
-- 删除Wind 公司财务指标表
DROP TABLE IF EXISTS `wind_companyfinancial`;
-- 删除Wind 跨市场证券代码关联表
DROP TABLE IF EXISTS `wind_windcustomcode`;
-- 删除Wind 中国债券发行主体信用评级表
DROP TABLE IF EXISTS `wind_cbondissuerrating`;
-- 删除Wind 中国债券发行主体信息表（债券+主体粒度，同主体可多行；外部 ODS 表一般不自建二级索引）
DROP TABLE IF EXISTS `wind_cbondissuer`;

-- ----------------------------------------------------------------------------
-- 1. Wind 中国债券发行主体信息表（债券+主体粒度，同主体可多行；外部 ODS 表一般不自建二级索引）
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
-- 2. Wind 中国债券发行主体信用评级表
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
-- 3. Wind 跨市场证券代码关联表
-- 创建 Wind 跨市场证券代码关联表
-- 外部源结构按截图保留 28 列的名称、类型与顺序，不追加业务主键或时间字段。
-- S_INFO_WINDCODE 对齐 znty_rrs.rrs_securityinfo.wind_code；相同非空 ISIN 表示同券。
-- ----------------------------------------------------------------------------
CREATE TABLE `wind_windcustomcode` (
    `OBJECT_ID`                  VARCHAR(100) NOT NULL     COMMENT '对象 ID',
    `S_INFO_WINDCODE`            VARCHAR(40)  DEFAULT NULL COMMENT 'Wind 证券代码，对齐 rrs_securityinfo.wind_code',
    `S_INFO_ASHARECODE`          VARCHAR(10)  DEFAULT NULL COMMENT 'Wind 内部证券代码',
    `S_INFO_COMPCODE`            VARCHAR(10)  DEFAULT NULL COMMENT '发行主体代码',
    `S_INFO_SECURITIESTYPES`     VARCHAR(10)  DEFAULT NULL COMMENT '证券类型，截图样本：Q=企债 / K=可转换债券',
    `S_INFO_SECTYPENAME`         VARCHAR(100) DEFAULT NULL COMMENT '证券类型名称',
    `S_INFO_COUNTRYNAME`         VARCHAR(100) DEFAULT NULL COMMENT '国家名称',
    `S_INFO_COUNTRYCODE`         VARCHAR(10)  DEFAULT NULL COMMENT '国家代码，如 CN',
    `S_INFO_EXCHMARKETNAME`      VARCHAR(40)  DEFAULT NULL COMMENT '交易市场名称',
    `S_INFO_EXCHMARKET`          VARCHAR(10)  DEFAULT NULL COMMENT '交易市场代码，Demo：IB=银行间 / SSE=上海 / SZSE=深圳',
    `CRNCY_NAME`                VARCHAR(40)  DEFAULT NULL COMMENT '币种名称',
    `CRNCY_CODE`                VARCHAR(10)  DEFAULT NULL COMMENT '币种代码，如 CNY',
    `S_INFO_ISINCODE`           VARCHAR(40)  DEFAULT NULL COMMENT '国际证券识别码，相同非空值表示同一只券，NULL 不作为关联依据',
    `S_INFO_CODE`               VARCHAR(40)  DEFAULT NULL COMMENT '市场证券代码，不含 Wind 市场后缀',
    `S_INFO_NAME`               VARCHAR(200) DEFAULT NULL COMMENT '证券名称',
    `EXCHMARKET`                VARCHAR(10)  DEFAULT NULL COMMENT '交易市场代码',
    `SECURITY_STATUS`           BIGINT       DEFAULT NULL COMMENT 'Wind 证券状态原始编码',
    `S_INFO_ORG_CODE`           VARCHAR(20)  DEFAULT NULL COMMENT '机构代码',
    `S_INFO_TYPECODE`           BIGINT       DEFAULT NULL COMMENT '证券类型编码',
    `S_INFO_MIN_PRICE_CHG_UNIT` DOUBLE       DEFAULT NULL COMMENT '最小价格变动单位',
    `S_INFO_LOT_SIZE`           FLOAT        DEFAULT NULL COMMENT '最小交易单位',
    `S_INFO_ENAME`              VARCHAR(200) DEFAULT NULL COMMENT '证券英文名称',
    `TRADING_HOURS_CODE`        VARCHAR(10)  DEFAULT NULL COMMENT '交易时间代码',
    `S_INFO_SECTYPEBCODE`       BIGINT       DEFAULT NULL COMMENT '证券大类编码',
    `S_INFO_SECTYPESCODE`       BIGINT       DEFAULT NULL COMMENT '证券小类编码',
    `S_INFO_PINYIN`             VARCHAR(40)  DEFAULT NULL COMMENT '证券名称拼音缩写',
    `OPDATE`                    DATE         DEFAULT NULL COMMENT '操作日期',
    `OPMODE`                    VARCHAR(1)   DEFAULT NULL COMMENT '操作模式：0=新增 / 1=修改 / 2=删除',
    PRIMARY KEY (`OBJECT_ID`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Wind 跨市场证券代码关联表';

-- ----------------------------------------------------------------------------
-- 4. Wind 公司财务指标表
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
    `EBIT_INT_COV`            BIGINT      DEFAULT NULL COMMENT '已获利息倍数(EBIT/利息费用)',
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
    `EBITDA_TO_DEBT`          FLOAT       DEFAULT NULL COMMENT '息税折旧摊销前利润/负债合计',
    `DEBT_TO_EBITDA`          FLOAT       DEFAULT NULL COMMENT '总债务/EBITDA',
    `EXP_TO_EBITDA`           FLOAT       DEFAULT NULL COMMENT 'EBIT利息保障倍数',
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

-- ----------------------------------------------------------------------------
-- 5. Wind 中国债券 Wind 代码变更表
-- 按截图保留字段名称、顺序、类型及非空约束，不额外增加字段或主键。
-- CHANGE_DATE 按用户补充使用 8 位字符串日期 yyyyMMdd。
-- ----------------------------------------------------------------------------
CREATE TABLE `wind_cbondchangewindcode` (
    `OBJECT_ID`          VARCHAR(100) NOT NULL     COMMENT '对象 ID',
    `S_INFO_WINDCODE`    VARCHAR(40)  DEFAULT NULL COMMENT 'Wind 证券代码',
    `S_INFO_OLDWINDCODE` VARCHAR(40)  DEFAULT NULL COMMENT '变更前 Wind 证券代码',
    `S_INFO_NEWWINDCODE` VARCHAR(40)  DEFAULT NULL COMMENT '变更后 Wind 证券代码',
    `CHANGE_DATE`        VARCHAR(8)   DEFAULT NULL COMMENT '变更日期，yyyyMMdd（8 位字符串）',
    `SEC_ID`             VARCHAR(10)  DEFAULT NULL COMMENT '证券 ID',
    `OPDATE`             VARCHAR(20)  DEFAULT NULL COMMENT '操作日期',
    `OPMODE`             VARCHAR(1)   DEFAULT NULL COMMENT '操作模式'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_general_ci
  COMMENT = 'Wind 中国债券 Wind 代码变更表';

-- ----------------------------------------------------------------------------
-- 6. Wind 公司简介表（全量主体）
-- 严格保留截图中的 26 列名称、顺序、类型及字符集，不追加字段、主键、索引或事件表。
-- COMP_ID 为主体编码；REGISTERNUMBER 为统一社会信用代码，允许为空。
-- 本表覆盖全部主体，不以是否发债或在池为条件；主体 Excel 匹配及调库身份校验使用本表。
-- ----------------------------------------------------------------------------
CREATE TABLE `wind_compintroduction` (
    `COMP_ID`               VARCHAR(40)     DEFAULT NULL COMMENT '主体编码',
    `COMP_NAME`             VARCHAR(200)    DEFAULT NULL COMMENT '主体全称',
    `COMP_SNAME`            VARCHAR(100)    DEFAULT NULL COMMENT '主体简称',
    `COMP_NAME_ENG`         VARCHAR(100)    DEFAULT NULL COMMENT '主体英文全称',
    `COMP_SNAMEENG`         VARCHAR(100)    DEFAULT NULL COMMENT '主体英文简称',
    `PROVINCE`              VARCHAR(30)     DEFAULT NULL COMMENT '省份',
    `CITY`                  VARCHAR(50)     DEFAULT NULL COMMENT '城市',
    `ADDRESS`               VARCHAR(500)    DEFAULT NULL COMMENT '注册地址',
    `OFFICE`                VARCHAR(400)    DEFAULT NULL COMMENT '办公地址',
    `REGISTERNUMBER`        VARCHAR(30)     DEFAULT NULL COMMENT '统一社会信用代码，允许为空',
    `REGCAPITAL`            FLOAT           DEFAULT NULL COMMENT '注册资本，保留外部源数值口径',
    `CURRENCYCODE`          VARCHAR(10)     DEFAULT NULL COMMENT '注册资本币种代码',
    `FOUNDDATE`             VARCHAR(8)      DEFAULT NULL COMMENT '成立日期，yyyyMMdd',
    `ENDDATE`               VARCHAR(8)      DEFAULT NULL COMMENT '终止日期，yyyyMMdd',
    `COMP_TYPE`             VARCHAR(100)    DEFAULT NULL COMMENT '主体类型，保留外部源原值',
    `COMP_PROPERTY`         VARCHAR(100)    DEFAULT NULL COMMENT '企业性质，保留外部源原值',
    `COUNTRY`               VARCHAR(20)     DEFAULT NULL COMMENT '国家',
    `COMPANY_TYPE`          VARCHAR(10)     DEFAULT NULL COMMENT '公司类型，保留外部源原值',
    `S_INFO_TOTALEMPLOYEES` BIGINT UNSIGNED DEFAULT NULL COMMENT '员工总数',
    `S_INFO_ORG_CODE`       VARCHAR(30)     DEFAULT NULL COMMENT '组织机构代码',
    `IS_LISTED`             VARCHAR(1)      DEFAULT NULL COMMENT '是否上市，保留外部源原值',
    `S_INFO_COMPTYPE`       VARCHAR(20)     DEFAULT NULL COMMENT '公司分类，保留外部源原值',
    `IS_REGISTERED`         VARCHAR(1)      DEFAULT NULL COMMENT '是否注册，保留外部源原值',
    `DISTRICT`              VARCHAR(100)    DEFAULT NULL COMMENT '所在区县',
    `OPDATE`                DATE            DEFAULT NULL COMMENT '操作日期',
    `OPMODE`                VARCHAR(1)      DEFAULT NULL COMMENT '操作模式，保留外部源原值'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_general_ci
  COMMENT = 'Wind 公司简介表（全量主体）';
