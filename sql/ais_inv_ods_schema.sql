-- ============================================================
-- AIS 投资 ODS 库 - Wind 债券发行人主体与评级表建表脚本
-- MySQL version: 8.0.33
-- 说明：表结构与公司原有 wind_cbondissuer / wind_cbondissuerrating 表保持一致
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ais_inv_ods` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ais_inv_ods`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 创建 Wind 债券发行人主体表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wind_cbondissuer` (
    `object_id`                    VARCHAR(100)  NOT NULL     COMMENT '对象 ID',
    `s_info_windcode`              VARCHAR(40)   DEFAULT NULL COMMENT 'Wind 证券代码',
    `s_info_compname`              VARCHAR(100)  DEFAULT NULL COMMENT '发行主体名称',
    `s_info_compcode`              VARCHAR(10)   DEFAULT NULL COMMENT '发行主体代码',
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
  COMMENT = 'Wind 中国债券发行主体信息表';

-- ----------------------------------------------------------------------------
-- 创建 Wind 债券发行人评级表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wind_cbondissuerrating` (
    `object_id`                    VARCHAR(100)  NOT NULL     COMMENT '对象 ID',
    `s_info_compname`              VARCHAR(100)  DEFAULT NULL COMMENT '公司名称',
    `ann_dt`                       VARCHAR(8)    DEFAULT NULL COMMENT '公告日期，yyyyMMdd',
    `b_rate_style`                 VARCHAR(100)  DEFAULT NULL COMMENT '评级类型',
    `b_info_creditrating`          VARCHAR(40)   DEFAULT NULL COMMENT '主体信用评级',
    `b_rate_ratingoutlook`         DOUBLE        DEFAULT NULL COMMENT '评级展望',
    `b_info_creditratingagency`    VARCHAR(10)   DEFAULT NULL COMMENT '评级机构',
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
