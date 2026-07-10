-- ============================================================
-- AIS 投资 ODS 库 - Wind 债券发行人评级表建表脚本
-- MySQL version: 8.0.33
-- 说明：表结构与公司原有 wind_cbondissuerrating 表保持一致
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ais_inv_ods` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `ais_inv_ods`;
SET NAMES utf8mb4;

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
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Wind 中国债券发行主体信用评级表';
