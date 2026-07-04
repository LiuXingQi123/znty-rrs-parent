-- ============================================================
-- AIS 投资分析库 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：创建主体评级、角色、用户及用户角色关联表
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ais_inv_analysis` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ais_inv_analysis`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_inv_grade_result`;
DROP TABLE IF EXISTS `t_inv_company`;
DROP TABLE IF EXISTS `t_sys_user_role`;
DROP TABLE IF EXISTS `t_sys_user`;
DROP TABLE IF EXISTS `t_sys_role`;

-- ----------------------------------------------------------------------------
-- 1. 主体基础信息表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_inv_company`
(
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `business_scope` VARCHAR(2000) DEFAULT NULL COMMENT '经营范围',
    `city_id`        VARCHAR(100)  DEFAULT NULL COMMENT '城市 ID',
    `code`           VARCHAR(100)  DEFAULT NULL COMMENT '主体编码',
    `country_id`     VARCHAR(100)  DEFAULT NULL COMMENT '国家 ID',
    `county_id`      VARCHAR(100)  DEFAULT NULL COMMENT '区县 ID',
    `description`    VARCHAR(2000) DEFAULT NULL COMMENT '主体描述',
    `full_name`      VARCHAR(100)  DEFAULT NULL COMMENT '主体全称',
    `industry_id`    VARCHAR(100)  DEFAULT NULL COMMENT '行业 ID',
    `legaler`        VARCHAR(200)  DEFAULT NULL COMMENT '法定代表人',
    `province_id`    VARCHAR(100)  DEFAULT NULL COMMENT '省份 ID',
    `reg_address`    VARCHAR(100)  DEFAULT NULL COMMENT '注册地址',
    `reg_capital`    VARCHAR(100)  DEFAULT NULL COMMENT '注册资本',
    `short_name`     VARCHAR(100)  DEFAULT NULL COMMENT '主体简称',
    `stock_sty`      VARCHAR(100)  DEFAULT NULL COMMENT '股票类型',
    `village_id`     VARCHAR(100)  DEFAULT NULL COMMENT '街道或乡镇 ID',
    `web_site`       VARCHAR(100)  DEFAULT NULL COMMENT '官方网站',
    `es_type`        VARCHAR(100)  DEFAULT NULL COMMENT 'ES 类型',
    `es_index`       VARCHAR(100)  DEFAULT NULL COMMENT 'ES 指标',
    `es_score`       VARCHAR(100)  DEFAULT NULL COMMENT 'ES 分数',
    `wind_code`      VARCHAR(100)  DEFAULT NULL COMMENT 'Wind 主体代码',
    `industry`       VARCHAR(100)  DEFAULT NULL COMMENT '所属行业',
    `ts`             TIMESTAMP     DEFAULT NULL COMMENT '时间戳',
    `fundDate`       VARCHAR(100)  DEFAULT NULL COMMENT '成立日期',
    `templateId`     VARCHAR(10)   DEFAULT NULL COMMENT '模板 ID',
    `researcher`     VARCHAR(100)  DEFAULT NULL COMMENT '研究员',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主体基础信息表';

-- ----------------------------------------------------------------------------
-- 2. 主体评级结果表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_inv_grade_result`
(
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `company_id`     BIGINT      DEFAULT NULL COMMENT '主体 ID，关联 t_inv_company.id',
    `area_id`        BIGINT      DEFAULT NULL COMMENT '区域 ID',
    `template_id`    BIGINT      DEFAULT NULL COMMENT '评级模板 ID',
    `temp_id`        BIGINT      DEFAULT NULL COMMENT '临时模板 ID',
    `time`           VARCHAR(50) DEFAULT NULL COMMENT '评级时间',
    `cal_score`      FLOAT       DEFAULT NULL COMMENT '计算分数',
    `total_score`    FLOAT       DEFAULT NULL COMMENT '总分',
    `other_score`    TEXT        COMMENT '其他评分明细',
    `mapping_step`   VARCHAR(50) DEFAULT NULL COMMENT '映射步骤',
    `steps`          INT         DEFAULT NULL COMMENT '步骤数',
    `final_step`     VARCHAR(50) DEFAULT NULL COMMENT '最终步骤',
    `deal_user_id`   BIGINT      DEFAULT NULL COMMENT '处理人 ID',
    `ts`             TIMESTAMP   DEFAULT NULL COMMENT '时间戳',
    `adjust_note`    LONGTEXT    COMMENT '调整说明',
    `template_type`  BIGINT      DEFAULT NULL COMMENT '模板类型',
    `weighted_score` FLOAT       DEFAULT NULL COMMENT '加权分数',
    `observe_type`   VARCHAR(50) DEFAULT NULL COMMENT '观察类型',
    PRIMARY KEY (`id`),
    KEY `idx_t_inv_grade_result_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主体评级结果表';

-- ----------------------------------------------------------------------------
-- 3. 角色/部门表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_sys_role` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `name`             VARCHAR(50)  DEFAULT NULL            COMMENT '角色名称',
    `enable`           INT          DEFAULT NULL            COMMENT '是否启用：1=启用 / 0=禁用',
    `ts`               TIMESTAMP    DEFAULT NULL            COMMENT '时间戳',
    `code`             VARCHAR(50)  DEFAULT NULL            COMMENT '角色编码',
    `memo`             VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    `parent_id`        BIGINT       DEFAULT NULL            COMMENT '父级角色 ID',
    `inherit_role_ids` VARCHAR(100) DEFAULT NULL            COMMENT '继承角色 ID 集合，逗号分割',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色/部门表';

-- ----------------------------------------------------------------------------
-- 4. 用户表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id`       BIGINT       DEFAULT NULL            COMMENT '用户 ID',
    `name`          VARCHAR(50)  DEFAULT NULL            COMMENT '用户姓名',
    `user_name`     VARCHAR(50)  DEFAULT NULL            COMMENT '登录用户名/拼音',
    `user_eng_name` VARCHAR(100) DEFAULT NULL            COMMENT '用户英文名',
    `ts`            TIMESTAMP    DEFAULT NULL            COMMENT '时间戳',
    `dr`            SMALLINT     DEFAULT NULL            COMMENT '删除标志：0=正常 / 1=已删除',
    `pwd`           VARCHAR(32)  DEFAULT NULL            COMMENT '密码',
    `oapwd`         VARCHAR(32)  DEFAULT NULL            COMMENT 'OA 密码',
    `ORGID`         VARCHAR(20)  DEFAULT NULL            COMMENT '机构 ID',
    `TEL`           VARCHAR(20)  DEFAULT NULL            COMMENT '电话',
    `MOBILE`        VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    `EMAIL`         VARCHAR(50)  DEFAULT NULL            COMMENT '邮箱',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户表';

-- ----------------------------------------------------------------------------
-- 5. 用户角色关联表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_sys_user_role` (
    `id`      BIGINT    NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `role_id` BIGINT    DEFAULT NULL            COMMENT '角色 ID，关联 t_sys_role.id',
    `user_id` BIGINT    DEFAULT NULL            COMMENT '用户 ID，关联 t_sys_user.id',
    `ts`      TIMESTAMP DEFAULT NULL            COMMENT '时间戳',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户角色关联表';
