-- ============================================================
-- AIS 投资分析库 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：创建主体评级、角色、人员及人员角色关联表
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ais_inv_analysis` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `ais_inv_analysis`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_sys_user_role_evt`;
DROP TABLE IF EXISTS `t_sys_user_evt`;
DROP TABLE IF EXISTS `t_sys_role_evt`;
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
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `name`       VARCHAR(64)  DEFAULT NULL            COMMENT '角色名称',
    `parent_id`  BIGINT       DEFAULT NULL            COMMENT '父级角色 ID',
    `sort_order` INT          DEFAULT NULL            COMMENT '排序序号',
    `enable`     TINYINT(1)   DEFAULT NULL            COMMENT '是否启用：1=启用 / 0=禁用',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '角色/部门表';

-- ----------------------------------------------------------------------------
-- 4. 人员表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_sys_user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `name`       VARCHAR(64)  DEFAULT NULL            COMMENT '人员姓名',
    `user_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '登录用户名/拼音',
    `dr`         TINYINT(1)   DEFAULT NULL            COMMENT '删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '人员表';

-- ----------------------------------------------------------------------------
-- 5. 人员角色关联表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_sys_user_role` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id`    BIGINT       DEFAULT NULL            COMMENT '人员 ID，关联 t_sys_user.id',
    `role_id`    BIGINT       DEFAULT NULL            COMMENT '角色 ID，关联 t_sys_role.id',
    `dr`         TINYINT(1)   DEFAULT NULL            COMMENT '删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '人员角色关联表';

-- ----------------------------------------------------------------------------
-- 6. 角色/部门事件表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_sys_role_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `name`       VARCHAR(64)  DEFAULT NULL            COMMENT '角色名称',
    `parent_id`  BIGINT       DEFAULT NULL            COMMENT '父级角色 ID',
    `sort_order` INT          DEFAULT NULL            COMMENT '排序序号',
    `enable`     TINYINT(1)   DEFAULT NULL            COMMENT '是否启用：1=启用 / 0=禁用',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`   VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文：新增 / 修改 / 删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '角色/部门表（操作审计）';

-- ----------------------------------------------------------------------------
-- 7. 人员事件表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_sys_user_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `name`       VARCHAR(64)  DEFAULT NULL            COMMENT '人员姓名',
    `user_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '登录用户名/拼音',
    `dr`         TINYINT(1)   DEFAULT NULL            COMMENT '删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`   VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文：新增 / 修改 / 删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '人员表（操作审计）';

-- ----------------------------------------------------------------------------
-- 8. 人员角色关联事件表
-- ----------------------------------------------------------------------------
CREATE TABLE `t_sys_user_role_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `user_id`    BIGINT       DEFAULT NULL            COMMENT '人员 ID，关联 t_sys_user.id',
    `role_id`    BIGINT       DEFAULT NULL            COMMENT '角色 ID，关联 t_sys_role.id',
    `dr`         TINYINT(1)   DEFAULT NULL            COMMENT '删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`   VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文：新增 / 修改 / 删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '人员角色关联表（操作审计）';

SET FOREIGN_KEY_CHECKS = 1;
