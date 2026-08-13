-- ============================================================
-- znty-rrs 定时任务配置 - 建表脚本
-- MySQL version: 8.0.33
-- 说明：任务配置持久化（启停/cron/扩展参数）+ 执行历史，支撑可视化管理
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `sys_scheduled_task_run_log`;
DROP TABLE IF EXISTS `sys_scheduled_task_evt`;
DROP TABLE IF EXISTS `sys_scheduled_task`;

-- 定时任务配置主表（每个 task_code 一条；只认库表，启动时不再按代码种子写库）
CREATE TABLE `sys_scheduled_task`
(
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `task_code`            VARCHAR(64)  DEFAULT NULL            COMMENT '任务编码（稳定标识，对应代码实现 getTaskCode）',
    `task_name`            VARCHAR(128) DEFAULT NULL            COMMENT '任务名称（页面展示，启动同步时可用代码默认名覆盖）',
    `description`          VARCHAR(500) DEFAULT NULL            COMMENT '任务说明',
    `cron_expression`      VARCHAR(64)  DEFAULT NULL            COMMENT 'cron 表达式（6 位 Spring 风格：秒 分 时 日 月 周）',
    `schedule_enabled`     TINYINT(1)   DEFAULT NULL            COMMENT '是否启用定时调度：0=关闭 / 1=启用；关闭后仍可手动执行',
    `param_json`           VARCHAR(1000) DEFAULT NULL           COMMENT '任务扩展参数（通用文本，推荐 JSON，格式由各任务实现自行解析；无则空）',
    `last_run_status`      VARCHAR(32)  DEFAULT NULL            COMMENT '最近执行状态：success=成功 / fail=失败',
    `last_run_message`     VARCHAR(1000) DEFAULT NULL           COMMENT '最近执行结果说明',
    `last_run_time`        DATETIME     DEFAULT NULL            COMMENT '最近执行开始时间',
    `last_affected_count`  INT          DEFAULT NULL            COMMENT '最近执行影响条数',
    `last_duration_ms`     BIGINT       DEFAULT NULL            COMMENT '最近执行耗时毫秒',
    `last_trigger_type`    VARCHAR(32)  DEFAULT NULL            COMMENT '最近触发方式：manual=手动 / cron=定时',
    `is_deleted`           TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`            DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`            DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务配置';

-- 定时任务配置操作审计
CREATE TABLE `sys_scheduled_task_evt`
(
    `evt_id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`                   BIGINT       DEFAULT NULL            COMMENT '主表主键 ID',
    `task_code`            VARCHAR(64)  DEFAULT NULL            COMMENT '任务编码（稳定标识，对应代码实现 getTaskCode）',
    `task_name`            VARCHAR(128) DEFAULT NULL            COMMENT '任务名称（页面展示，启动同步时可用代码默认名覆盖）',
    `description`          VARCHAR(500) DEFAULT NULL            COMMENT '任务说明',
    `cron_expression`      VARCHAR(64)  DEFAULT NULL            COMMENT 'cron 表达式（6 位 Spring 风格：秒 分 时 日 月 周）',
    `schedule_enabled`     TINYINT(1)   DEFAULT NULL            COMMENT '是否启用定时调度：0=关闭 / 1=启用；关闭后仍可手动执行',
    `param_json`           VARCHAR(1000) DEFAULT NULL           COMMENT '任务扩展参数（通用文本，推荐 JSON，格式由各任务实现自行解析；无则空）',
    `last_run_status`      VARCHAR(32)  DEFAULT NULL            COMMENT '最近执行状态：success=成功 / fail=失败',
    `last_run_message`     VARCHAR(1000) DEFAULT NULL           COMMENT '最近执行结果说明',
    `last_run_time`        DATETIME     DEFAULT NULL            COMMENT '最近执行开始时间',
    `last_affected_count`  INT          DEFAULT NULL            COMMENT '最近执行影响条数',
    `last_duration_ms`     BIGINT       DEFAULT NULL            COMMENT '最近执行耗时毫秒',
    `last_trigger_type`    VARCHAR(32)  DEFAULT NULL            COMMENT '最近触发方式：manual=手动 / cron=定时',
    `is_deleted`           TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`            DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`            DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`             VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`             DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`            VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务配置（操作审计）';

-- 定时任务执行历史（页面可视化）
CREATE TABLE `sys_scheduled_task_run_log`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `task_code`       VARCHAR(64)  DEFAULT NULL            COMMENT '任务编码',
    `task_name`       VARCHAR(128) DEFAULT NULL            COMMENT '任务名称快照',
    `trigger_type`    VARCHAR(32)  DEFAULT NULL            COMMENT '触发方式：manual=手动 / cron=定时',
    `run_status`      VARCHAR(32)  DEFAULT NULL            COMMENT '执行状态：success=成功 / fail=失败',
    `message`         VARCHAR(1000) DEFAULT NULL           COMMENT '执行结果说明',
    `detail_log`      TEXT         DEFAULT NULL            COMMENT '执行过程日志（多行文本，供历史页查看）',
    `affected_count`  INT          DEFAULT NULL            COMMENT '影响条数',
    `duration_ms`     BIGINT       DEFAULT NULL            COMMENT '耗时毫秒',
    `start_time`      DATETIME     DEFAULT NULL            COMMENT '开始时间',
    `end_time`        DATETIME     DEFAULT NULL            COMMENT '结束时间',
    `operator_id`     VARCHAR(32)  DEFAULT NULL            COMMENT '操作人 ID（定时触发为 0）',
    `operator_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '操作人名称（定时触发为 系统）',
    `is_deleted`      TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`       DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`       DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务执行历史';

-- 若库表已存在仅缺 detail_log，可手工执行：
-- ALTER TABLE `sys_scheduled_task_run_log` ADD COLUMN `detail_log` TEXT DEFAULT NULL COMMENT '执行过程日志（多行文本，供历史页查看）' AFTER `message`;
