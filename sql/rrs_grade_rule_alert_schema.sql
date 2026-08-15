-- ============================================================
-- znty-rrs 不符合主体债入库规则提醒 - 建表脚本
-- MySQL version: 8.0.33
-- 说明：定时扫描已在信用债/境外债分级库、但按当前特殊债规则已不允许待在该档的债券，生成待办供人工处理
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `ip_grade_rule_alert_evt`;
DROP TABLE IF EXISTS `ip_grade_rule_alert`;

CREATE TABLE `ip_grade_rule_alert` (
    `id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `security_code`        VARCHAR(64)   DEFAULT NULL            COMMENT '证券代码',
    `security_short_name`  VARCHAR(128)  DEFAULT NULL            COMMENT '证券简称',
    `issuer_code`          VARCHAR(64)   DEFAULT NULL            COMMENT '发行主体代码',
    `issuer_name`          VARCHAR(256)  DEFAULT NULL            COMMENT '发行主体名称',
    `current_pool_id`      BIGINT        DEFAULT NULL            COMMENT '当前所在分级库 ID',
    `current_pool_name`    VARCHAR(128)  DEFAULT NULL            COMMENT '当前所在分级库名称',
    `current_sort`         INT           DEFAULT NULL            COMMENT '当前分级库档位 inner_sort，1=一级最好 / 5=五级最差',
    `fail_reason`          VARCHAR(500)  DEFAULT NULL            COMMENT '不符合原因（与 checkAdjust 文案一致）',
    `special_type_desc`    VARCHAR(256)  DEFAULT NULL            COMMENT '命中的特殊类型说明，如私募/永续/观察名单',
    `alert_status`         VARCHAR(8)    DEFAULT NULL            COMMENT '待办状态：00=待处理 / 20=已处理 / 99=已失效（本轮扫描已符合）',
    `last_scan_time`       DATETIME      DEFAULT NULL            COMMENT '最近一次扫描命中时间',
    `deal_user_id`         VARCHAR(32)   DEFAULT NULL            COMMENT '处理人 ID',
    `deal_user_name`       VARCHAR(64)   DEFAULT NULL            COMMENT '处理人名称',
    `deal_time`            DATETIME      DEFAULT NULL            COMMENT '处理时间',
    `is_deleted`           TINYINT(1)    DEFAULT NULL            COMMENT '逻辑删除：0=正常 / 1=已删除',
    `crte_time`            DATETIME      DEFAULT NULL            COMMENT '创建时间',
    `updt_time`            DATETIME      DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '不符合主体债入库规则提醒待办';

CREATE TABLE `ip_grade_rule_alert_evt` (
    `evt_id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`                   BIGINT        DEFAULT NULL            COMMENT '主键 ID',
    `security_code`        VARCHAR(64)   DEFAULT NULL            COMMENT '证券代码',
    `security_short_name`  VARCHAR(128)  DEFAULT NULL            COMMENT '证券简称',
    `issuer_code`          VARCHAR(64)   DEFAULT NULL            COMMENT '发行主体代码',
    `issuer_name`          VARCHAR(256)  DEFAULT NULL            COMMENT '发行主体名称',
    `current_pool_id`      BIGINT        DEFAULT NULL            COMMENT '当前所在分级库 ID',
    `current_pool_name`    VARCHAR(128)  DEFAULT NULL            COMMENT '当前所在分级库名称',
    `current_sort`         INT           DEFAULT NULL            COMMENT '当前分级库档位 inner_sort，1=一级最好 / 5=五级最差',
    `fail_reason`          VARCHAR(500)  DEFAULT NULL            COMMENT '不符合原因（与 checkAdjust 文案一致）',
    `special_type_desc`    VARCHAR(256)  DEFAULT NULL            COMMENT '命中的特殊类型说明，如私募/永续/观察名单',
    `alert_status`         VARCHAR(8)    DEFAULT NULL            COMMENT '待办状态：00=待处理 / 20=已处理 / 99=已失效（本轮扫描已符合）',
    `last_scan_time`       DATETIME      DEFAULT NULL            COMMENT '最近一次扫描命中时间',
    `deal_user_id`         VARCHAR(32)   DEFAULT NULL            COMMENT '处理人 ID',
    `deal_user_name`       VARCHAR(64)   DEFAULT NULL            COMMENT '处理人名称',
    `deal_time`            DATETIME      DEFAULT NULL            COMMENT '处理时间',
    `is_deleted`           TINYINT(1)    DEFAULT NULL            COMMENT '逻辑删除：0=正常 / 1=已删除',
    `crte_time`            DATETIME      DEFAULT NULL            COMMENT '创建时间',
    `updt_time`            DATETIME      DEFAULT NULL            COMMENT '修改时间',
    `opter_id`             VARCHAR(20)   DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`             DATETIME      DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`            VARCHAR(20)   DEFAULT NULL            COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '不符合主体债入库规则提醒待办（操作审计）';
