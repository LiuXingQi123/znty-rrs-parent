-- ============================================================
-- znty-rrs 脚本工具执行审计 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：
--   1. 记录 ScriptTool 写操作（初始化/清空/重置/模块重置/场景生成）的操作人、结果与摘要。
--   2. 仅审计表，无 Demo 灌数；已注册 ScriptTool schema 清单与可清空组。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `sys_script_tool_run_log`;

CREATE TABLE `sys_script_tool_run_log`
(
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `action_type`        VARCHAR(32)   DEFAULT NULL            COMMENT '动作类型：task=初始化任务 / clear=清空选中表 / reset=重置选中表 / module=模块重置 / scene=场景生成',
    `action_code`        VARCHAR(64)   DEFAULT NULL            COMMENT '动作编码：taskCode / moduleCode / sceneCode / CLEAR_SELECTED_TABLES 等',
    `action_name`        VARCHAR(128)  DEFAULT NULL            COMMENT '动作名称（展示用）',
    `run_status`         VARCHAR(16)   DEFAULT NULL            COMMENT '执行状态：success=成功 / failed=失败',
    `error_message`      VARCHAR(1000) DEFAULT NULL           COMMENT '失败原因摘要',
    `executed_summary`   VARCHAR(2000) DEFAULT NULL           COMMENT '已执行项摘要（脚本名/表名，过长截断）',
    `executed_count`     INT           DEFAULT NULL            COMMENT '已执行项数量',
    `cost_millis`        BIGINT        DEFAULT NULL            COMMENT '耗时毫秒',
    `start_time`         DATETIME      DEFAULT NULL            COMMENT '开始时间',
    `end_time`           DATETIME      DEFAULT NULL            COMMENT '结束时间',
    `operator_id`        VARCHAR(32)   DEFAULT NULL            COMMENT '操作人 ID',
    `operator_name`      VARCHAR(64)   DEFAULT NULL            COMMENT '操作人名称',
    `is_deleted`         TINYINT(1)    DEFAULT NULL            COMMENT '逻辑删除：0=正常 / 1=已删除',
    `crte_time`          DATETIME      DEFAULT NULL            COMMENT '创建时间',
    `updt_time`          DATETIME      DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '脚本工具写操作执行审计';
