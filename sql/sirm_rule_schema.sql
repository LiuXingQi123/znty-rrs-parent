-- ============================================================
-- znty-sirm 规则管理库 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，创建数据库和全部业务表结构
-- ============================================================

CREATE DATABASE IF NOT EXISTS znty_sirm
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE znty_sirm;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `rule_test_run_log`;
DROP TABLE IF EXISTS `rule_test_run`;
DROP TABLE IF EXISTS `rule_test_case_param`;
DROP TABLE IF EXISTS `rule_test_case`;
DROP TABLE IF EXISTS `rule_param_option`;
DROP TABLE IF EXISTS `rule_param`;
DROP TABLE IF EXISTS `rule_definition`;
DROP TABLE IF EXISTS `rule_preset_option_item`;
DROP TABLE IF EXISTS `rule_preset_option_set`;
DROP TABLE IF EXISTS `rule_category`;

-- ----------------------------------------------------------------------------
-- 1. 规则分类表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_category` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `category_code` VARCHAR(32)  DEFAULT NULL             COMMENT '分类编码，如 risk、marketing、pricing',
  `category_name` VARCHAR(50)  DEFAULT NULL             COMMENT '分类名称，如风控规则、营销规则、定价规则',
  `sort_no`       INT          DEFAULT NULL             COMMENT '排序号',
  `enabled`       TINYINT      DEFAULT NULL             COMMENT '是否启用：1=启用 / 0=停用',
  `crte_time`     DATETIME     DEFAULT NULL             COMMENT '创建时间',
  `updt_time`     DATETIME     DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '规则分类表';

-- ----------------------------------------------------------------------------
-- 2. 规则定义表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_definition` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `rule_name`     VARCHAR(50)  DEFAULT NULL             COMMENT '规则名称',
  `description`   VARCHAR(500) DEFAULT NULL             COMMENT '规则描述',
  `category_code` VARCHAR(32)  DEFAULT NULL             COMMENT '规则分类编码',
  `script`        MEDIUMTEXT   DEFAULT NULL             COMMENT 'QLExpress 规则脚本',
  `status`        VARCHAR(20)  DEFAULT NULL             COMMENT '规则状态：active=启用 / disabled=禁用',
  `deleted_flag`  TINYINT      DEFAULT NULL             COMMENT '删除标识：0=未删除 / 1=已删除',
  `crte_time`     DATETIME     DEFAULT NULL             COMMENT '创建时间',
  `updt_time`     DATETIME     DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '规则定义表';

-- ----------------------------------------------------------------------------
-- 3. 规则参数表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_param` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `rule_id`     BIGINT       DEFAULT NULL             COMMENT '规则 ID，对应 rule_definition.id',
  `param_name`  VARCHAR(64)  DEFAULT NULL             COMMENT '参数字段名，即脚本变量名，如 orderAmount',
  `param_label` VARCHAR(100) DEFAULT NULL             COMMENT '参数显示名称，如订单金额',
  `param_type`  VARCHAR(32)  DEFAULT NULL             COMMENT '参数类型：string=字符串 / number=数值 / select=单选 / multiselect=多选',
  `required`    TINYINT      DEFAULT NULL             COMMENT '是否必填：1=必填 / 0=非必填',
  `sort_no`     INT          DEFAULT NULL             COMMENT '排序号',
  `crte_time`   DATETIME     DEFAULT NULL             COMMENT '创建时间',
  `updt_time`   DATETIME     DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '规则参数表';

-- ----------------------------------------------------------------------------
-- 4. 规则参数选项表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_param_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `param_id`     BIGINT       DEFAULT NULL             COMMENT '规则参数 ID，对应 rule_param.id',
  `option_value` VARCHAR(100) DEFAULT NULL             COMMENT '选项值',
  `option_label` VARCHAR(100) DEFAULT NULL             COMMENT '选项显示名称',
  `sort_no`      INT          DEFAULT NULL             COMMENT '排序号',
  `crte_time`    DATETIME     DEFAULT NULL             COMMENT '创建时间',
  `updt_time`    DATETIME     DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '规则参数选项表';

-- ----------------------------------------------------------------------------
-- 5. 参数预设选项集表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_preset_option_set` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `set_name`  VARCHAR(50) DEFAULT NULL             COMMENT '预设选项集名称，如主体评级、债券类型',
  `sort_no`   INT         DEFAULT NULL             COMMENT '排序号',
  `enabled`   TINYINT     DEFAULT NULL             COMMENT '是否启用：1=启用 / 0=停用',
  `crte_time` DATETIME    DEFAULT NULL             COMMENT '创建时间',
  `updt_time` DATETIME    DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '参数预设选项集表';

-- ----------------------------------------------------------------------------
-- 6. 参数预设选项明细表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_preset_option_item` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `set_id`       BIGINT       DEFAULT NULL             COMMENT '预设选项集 ID，对应 rule_preset_option_set.id',
  `option_value` VARCHAR(100) DEFAULT NULL             COMMENT '选项值',
  `option_label` VARCHAR(100) DEFAULT NULL             COMMENT '选项显示名称',
  `sort_no`      INT          DEFAULT NULL             COMMENT '排序号',
  `crte_time`    DATETIME     DEFAULT NULL             COMMENT '创建时间',
  `updt_time`    DATETIME     DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '参数预设选项明细表';

-- ----------------------------------------------------------------------------
-- 7. 规则测试用例表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_test_case` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `case_name`          VARCHAR(50)   DEFAULT NULL             COMMENT '测试用例名称',
  `rule_id`            BIGINT        DEFAULT NULL             COMMENT '关联规则 ID，对应 rule_definition.id',
  `rule_name_snapshot` VARCHAR(50)   DEFAULT NULL             COMMENT '规则名称快照',
  `last_result`        VARCHAR(20)   DEFAULT NULL             COMMENT '最近执行结果：pending=待执行 / running=执行中 / pass=通过 / fail=失败',
  `last_output`        VARCHAR(1000) DEFAULT NULL             COMMENT '最近执行输出',
  `last_run_time`      DATETIME      DEFAULT NULL             COMMENT '最近执行时间',
  `crte_time`          DATETIME      DEFAULT NULL             COMMENT '创建时间',
  `updt_time`          DATETIME      DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '规则测试用例表';

-- ----------------------------------------------------------------------------
-- 8. 测试用例参数值表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_test_case_param` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `case_id`             BIGINT       DEFAULT NULL             COMMENT '测试用例 ID，对应 rule_test_case.id',
  `param_name`          VARCHAR(64)  DEFAULT NULL             COMMENT '参数字段名',
  `param_label_snapshot` VARCHAR(100) DEFAULT NULL            COMMENT '参数显示名称快照',
  `param_type_snapshot` VARCHAR(32)  DEFAULT NULL             COMMENT '参数类型快照',
  `param_value`         TEXT         DEFAULT NULL             COMMENT '参数值，多选可使用逗号分隔或 JSON 字符串',
  `crte_time`           DATETIME     DEFAULT NULL             COMMENT '创建时间',
  `updt_time`           DATETIME     DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '测试用例参数值表';

-- ----------------------------------------------------------------------------
-- 9. 测试执行记录表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_test_run` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `case_id`       BIGINT        DEFAULT NULL             COMMENT '测试用例 ID，对应 rule_test_case.id',
  `rule_id`       BIGINT        DEFAULT NULL             COMMENT '规则 ID，对应 rule_definition.id',
  `run_status`    VARCHAR(20)   DEFAULT NULL             COMMENT '执行状态：running=执行中 / pass=通过 / fail=失败',
  `output`        VARCHAR(1000) DEFAULT NULL             COMMENT '执行输出',
  `error_message` VARCHAR(1000) DEFAULT NULL             COMMENT '错误信息',
  `start_time`    DATETIME      DEFAULT NULL             COMMENT '开始时间',
  `finish_time`   DATETIME      DEFAULT NULL             COMMENT '结束时间',
  `crte_time`     DATETIME      DEFAULT NULL             COMMENT '创建时间',
  `updt_time`     DATETIME      DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '测试执行记录表';

-- ----------------------------------------------------------------------------
-- 10. 测试执行日志表
-- ----------------------------------------------------------------------------
CREATE TABLE `rule_test_run_log` (
  `id`        BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
  `run_id`    BIGINT        DEFAULT NULL             COMMENT '测试执行记录 ID，对应 rule_test_run.id',
  `log_time`  DATETIME      DEFAULT NULL             COMMENT '日志时间',
  `log_type`  VARCHAR(20)   DEFAULT NULL             COMMENT '日志类型：info=信息 / success=成功 / error=错误',
  `message`   VARCHAR(1000) DEFAULT NULL             COMMENT '日志内容',
  `crte_time` DATETIME      DEFAULT NULL             COMMENT '创建时间',
  `updt_time` DATETIME      DEFAULT NULL             COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '测试执行日志表';
