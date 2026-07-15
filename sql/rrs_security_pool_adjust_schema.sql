-- ============================================================
-- znty-rrs 证券池调库相关表 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：调库日志 / 池状态 / 流程步骤（不含外部导入表，见 rrs_external_import_schema.sql）
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ============================================================================
-- 1. 证券池调库记录表
-- ============================================================================
DROP TABLE IF EXISTS `ip_adjust_log`;

CREATE TABLE `ip_adjust_log`
(
    `id`               BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `security_code`        VARCHAR(32)  DEFAULT NULL COMMENT '证券代码',
    `security_short_name`  VARCHAR(128) DEFAULT NULL COMMENT '证券简称',
    `security_type`        VARCHAR(32)  DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `crmw_name`            VARCHAR(128) DEFAULT NULL COMMENT 'CRMW名称',
    `crmw_scode`           VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券代码',
    `crmw_mktcode`         VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 市场代码',
    `crmw_stype`           VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券类型',
    `adjust_type`      VARCHAR(32)  DEFAULT NULL COMMENT '调整类型：手工调整/联动调整/互斥调整/关联调整/自动调整/Excel导入/手动批量调整',
    `adjust_mode`      VARCHAR(8)   DEFAULT NULL COMMENT '调整模式：调入/调出',
    `adjust_batch_no`  VARCHAR(64)  DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用',
    `target_pool_id`   BIGINT       DEFAULT NULL COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name` VARCHAR(128) DEFAULT NULL COMMENT '目标投资池名称',
    `pool_type`        VARCHAR(32)  DEFAULT NULL COMMENT '投资池类型：与 ip_investment_pool.pool_type 一致（credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品 / crmw=CRMW库 / forbidden=禁投池 / observe=观察池 等）',
    `flow_id`          BIGINT       DEFAULT NULL COMMENT '流程定义 ID 快照',
    `flow_key`         VARCHAR(128) DEFAULT NULL COMMENT '流程 Key 快照',
    `flow_type`        VARCHAR(32)  DEFAULT NULL COMMENT '流程类型快照',
    `audit_status`     VARCHAR(4)   DEFAULT NULL COMMENT '审核状态：-1=无效调整 / 00=流程中（待审批/审批中） / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 32=O32自动审批 / 99=发起人已撤回',
    `adjuster_id`      VARCHAR(32)  DEFAULT NULL COMMENT '调整人 ID',
    `adjuster_name`    VARCHAR(64)  DEFAULT NULL COMMENT '调整人名称',
    `adjust_reason`    VARCHAR(500) DEFAULT NULL COMMENT '调整原因',
    `adjust_advice`    VARCHAR(500) DEFAULT NULL COMMENT '调整意见',
    `submit_time`      DATETIME     DEFAULT NULL COMMENT '提交时间',
    `audit_time`       DATETIME     DEFAULT NULL COMMENT '审核时间',
    `entry_time`       DATETIME     DEFAULT NULL COMMENT '入池时间',
    `is_deleted`       TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`        DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`        DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '证券池调库记录表';

-- ============================================================================
-- 2. 投资池当前状态表
-- ============================================================================
DROP TABLE IF EXISTS `ip_pool_status`;

CREATE TABLE `ip_pool_status`
(
    `id`               BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `security_code`        VARCHAR(32)  DEFAULT NULL COMMENT '证券代码',
    `security_short_name`  VARCHAR(128) DEFAULT NULL COMMENT '证券简称',
    `security_type`        VARCHAR(32)  DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `crmw_name`            VARCHAR(128) DEFAULT NULL COMMENT 'CRMW名称',
    `crmw_scode`           VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券代码',
    `crmw_mktcode`         VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 市场代码',
    `crmw_stype`           VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券类型',
    `adjust_type`      VARCHAR(32)  DEFAULT NULL COMMENT '调整类型：手工调整/联动调整/互斥调整/关联调整/自动调整/Excel导入/手动批量调整',
    `adjust_mode`      VARCHAR(8)   DEFAULT NULL COMMENT '调整模式：调入/调出',
    `adjust_batch_no`  VARCHAR(64)  DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用',
    `adjust_log_id`    BIGINT       DEFAULT NULL COMMENT '来源调库日志 ID',
    `target_pool_id`   BIGINT       DEFAULT NULL COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name` VARCHAR(128) DEFAULT NULL COMMENT '目标投资池名称',
    `pool_type`        VARCHAR(32)  DEFAULT NULL COMMENT '投资池类型：与 ip_investment_pool.pool_type 一致（credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品 / crmw=CRMW库 / forbidden=禁投池 / observe=观察池 等）',
    `flow_id`          BIGINT       DEFAULT NULL COMMENT '流程定义 ID 快照',
    `flow_key`         VARCHAR(128) DEFAULT NULL COMMENT '流程 Key 快照',
    `flow_type`        VARCHAR(32)  DEFAULT NULL COMMENT '流程类型快照',
    `audit_status`     VARCHAR(4)   DEFAULT NULL COMMENT '审核状态：-1=无效调整 / 00=流程中（待审批/审批中） / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 32=O32自动审批 / 99=发起人已撤回',
    `adjuster_id`      VARCHAR(32)  DEFAULT NULL COMMENT '调整人 ID',
    `adjuster_name`    VARCHAR(64)  DEFAULT NULL COMMENT '调整人名称',
    `adjust_reason`    VARCHAR(500) DEFAULT NULL COMMENT '调整原因',
    `adjust_advice`    VARCHAR(500) DEFAULT NULL COMMENT '调整意见',
    `submit_time`      DATETIME     DEFAULT NULL COMMENT '提交时间',
    `audit_time`       DATETIME     DEFAULT NULL COMMENT '审核时间',
    `entry_time`       DATETIME     DEFAULT NULL COMMENT '入池时间',
    `is_deleted`       TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`        DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`        DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '投资池当前状态表';

-- ============================================================================
-- 3. 证券池调库-流程步骤记录表
-- ============================================================================
DROP TABLE IF EXISTS `ip_adjust_step`;

CREATE TABLE `ip_adjust_step`
(
    `id`                BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `adjust_log_id`     BIGINT       DEFAULT NULL COMMENT '关联调库记录 ID',
    `adjust_batch_no`   VARCHAR(64)  DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用',
    `flow_node_id`      BIGINT       DEFAULT NULL COMMENT '关联流程节点 ID',
    `node_code`         VARCHAR(32)  DEFAULT NULL COMMENT '节点业务标识',
    `node_label`        VARCHAR(128) DEFAULT NULL COMMENT '节点显示名称',
    `node_type`         VARCHAR(32)  DEFAULT NULL COMMENT '节点类型：start/approval/auto/end/notify/condition',
    `approval_strategy` VARCHAR(16)  DEFAULT NULL COMMENT '审批策略：preempt=抢占审批 / all=会签 / initiator=发起人',
    `sort_order`        INT          DEFAULT NULL COMMENT '排序序号',
    `step_status`       VARCHAR(16)  DEFAULT NULL COMMENT '步骤处理状态：pending=待处理 / approve=通过 / reject=驳回 / submit=提交 / auto_process=自动处理 / canceled=已撤回',
    `handler_id`        VARCHAR(32)  DEFAULT NULL COMMENT '处理人 ID',
    `handler_name`      VARCHAR(64)  DEFAULT NULL COMMENT '处理人名称',
    `process_action`    VARCHAR(16)  DEFAULT NULL COMMENT '处理动作：submit=提交 / approve=通过 / reject=驳回 / auto_process=自动处理 / skipped=被跳过',
    `process_comment`   VARCHAR(500) DEFAULT NULL COMMENT '处理意见',
    `start_time`        DATETIME     DEFAULT NULL COMMENT '步骤激活时间',
    `process_time`      DATETIME     DEFAULT NULL COMMENT '处理时间',
    `crte_time`         DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`         DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '证券池调库-流程步骤记录表';
