-- ============================================================
-- znty-rrs 基金调库运行表建表脚本
-- MySQL version: 8.0.33
-- 说明：
--   1. 本脚本仅维护基金调库日志、审批步骤和当前池状态
--   2. 基金调库复用通用投资池与流程定义，不复用债券调库运行表
--   3. 基金调库不创建基金基础信息快照表
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `znty_rrs`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `ip_adjust_step_fund`;
DROP TABLE IF EXISTS `ip_pool_status_fund`;
DROP TABLE IF EXISTS `ip_adjust_log_fund`;

-- ============================================================================
-- 1. 基金调库记录表
-- ============================================================================
CREATE TABLE `ip_adjust_log_fund`
(
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `fund_code`           VARCHAR(100)  DEFAULT NULL COMMENT '基金代码，关联 rrs_fundinfo.fund_code',
    `fund_name`           VARCHAR(300)  DEFAULT NULL COMMENT '基金全称',
    `fund_short_name`     VARCHAR(100)  DEFAULT NULL COMMENT '基金简称',
    `security_type`       VARCHAR(64)   DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `fund_score`          DECIMAL(10, 4) DEFAULT NULL COMMENT '基金评分',
    `fund_investment_type` VARCHAR(32)  DEFAULT NULL COMMENT '基金投资类型：stock=股票型 / equity_hybrid=偏股混合型 / money_market=货币型 / bond_hybrid=偏债混合型 / other=其余类型',
    `need_risk_leader_approval` TINYINT(1) DEFAULT NULL COMMENT '是否需要风管领导审批：0=否 / 1=是',
    `adjust_type`         VARCHAR(32)   DEFAULT NULL COMMENT '调整类型常见取值：手工调整=人工选择 / 联动调整=关联池联动 / 互斥调整=互斥池调整 / 关联调整=关联池调整 / 自动调整=系统触发 / Excel导入=文件导入 / 手动批量调整=人工批量发起',
    `adjust_mode`         VARCHAR(8)    DEFAULT NULL COMMENT '调整模式：调入=进入目标池 / 调出=退出目标池',
    `adjust_batch_no`     VARCHAR(64)   DEFAULT NULL COMMENT '基金调库批次号，使用 FUND 前缀，同一组调库记录共用',
    `target_pool_id`      BIGINT        DEFAULT NULL COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name`    VARCHAR(128)  DEFAULT NULL COMMENT '目标投资池名称',
    `pool_type`           VARCHAR(64)   DEFAULT NULL COMMENT '投资池类型快照（业务域可扩展）：credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / bond_product=债券产品 / special_account=专户 / stock=股票 / stock_product=股票产品 / fund=基金 / forbidden=禁止库 / observe=观察池 / blacklist=黑名单 / restricted=限制名单 / whitelist=白名单 / crmw=CRMW库 / other=其他',
    `flow_id`             BIGINT        DEFAULT NULL COMMENT '流程定义 ID 快照',
    `flow_key`            VARCHAR(128)  DEFAULT NULL COMMENT '流程 Key 快照',
    `flow_type`           VARCHAR(32)   DEFAULT NULL COMMENT '流程类型快照：normalInbound=一般调入 / normalOutbound=一般调出',
    `audit_status`        VARCHAR(4)    DEFAULT NULL COMMENT '审核状态：-1=无效调整 / 00=流程中（待审批/审批中） / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 32=O32自动审批（预留） / 99=发起人已撤回',
    `adjuster_id`         VARCHAR(32)   DEFAULT NULL COMMENT '调整人 ID',
    `adjuster_name`       VARCHAR(64)   DEFAULT NULL COMMENT '调整人名称',
    `adjust_reason`       VARCHAR(1000) DEFAULT NULL COMMENT '调整原因',
    `adjust_advice`       VARCHAR(1000) DEFAULT NULL COMMENT '调整意见',
    `submit_time`         DATETIME      DEFAULT NULL COMMENT '提交时间',
    `audit_time`          DATETIME      DEFAULT NULL COMMENT '审核时间',
    `entry_time`          DATETIME      DEFAULT NULL COMMENT '入池时间',
    `is_deleted`          TINYINT(1)    DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`           DATETIME      DEFAULT NULL COMMENT '创建时间',
    `updt_time`           DATETIME      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_fund_adjust_log_batch` (`adjust_batch_no`),
    KEY `idx_fund_adjust_log_code_status` (`fund_code`, `audit_status`, `is_deleted`),
    KEY `idx_fund_adjust_log_pool_status` (`target_pool_id`, `audit_status`, `is_deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '基金调库记录表';

-- ============================================================================
-- 2. 基金当前池状态表
-- ============================================================================
CREATE TABLE `ip_pool_status_fund`
(
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `fund_code`           VARCHAR(100)  DEFAULT NULL COMMENT '基金代码，关联 rrs_fundinfo.fund_code',
    `fund_name`           VARCHAR(300)  DEFAULT NULL COMMENT '基金全称',
    `fund_short_name`     VARCHAR(100)  DEFAULT NULL COMMENT '基金简称',
    `security_type`       VARCHAR(64)   DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `fund_score`          DECIMAL(10, 4) DEFAULT NULL COMMENT '基金评分',
    `fund_investment_type` VARCHAR(32)  DEFAULT NULL COMMENT '基金投资类型：stock=股票型 / equity_hybrid=偏股混合型 / money_market=货币型 / bond_hybrid=偏债混合型 / other=其余类型',
    `need_risk_leader_approval` TINYINT(1) DEFAULT NULL COMMENT '是否需要风管领导审批：0=否 / 1=是',
    `adjust_type`         VARCHAR(32)   DEFAULT NULL COMMENT '调整类型常见取值：手工调整=人工选择 / 联动调整=关联池联动 / 互斥调整=互斥池调整 / 关联调整=关联池调整 / 自动调整=系统触发 / Excel导入=文件导入 / 手动批量调整=人工批量发起',
    `adjust_mode`         VARCHAR(8)    DEFAULT NULL COMMENT '调整模式：调入=进入目标池 / 调出=退出目标池',
    `adjust_batch_no`     VARCHAR(64)   DEFAULT NULL COMMENT '基金调库批次号，使用 FUND 前缀，同一组调库记录共用',
    `adjust_log_id`       BIGINT        DEFAULT NULL COMMENT '来源基金调库日志 ID，逻辑关联 ip_adjust_log_fund.id',
    `target_pool_id`      BIGINT        DEFAULT NULL COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name`    VARCHAR(128)  DEFAULT NULL COMMENT '目标投资池名称',
    `pool_type`           VARCHAR(64)   DEFAULT NULL COMMENT '投资池类型快照（业务域可扩展）：credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / bond_product=债券产品 / special_account=专户 / stock=股票 / stock_product=股票产品 / fund=基金 / forbidden=禁止库 / observe=观察池 / blacklist=黑名单 / restricted=限制名单 / whitelist=白名单 / crmw=CRMW库 / other=其他',
    `flow_id`             BIGINT        DEFAULT NULL COMMENT '流程定义 ID 快照',
    `flow_key`            VARCHAR(128)  DEFAULT NULL COMMENT '流程 Key 快照',
    `flow_type`           VARCHAR(32)   DEFAULT NULL COMMENT '流程类型快照：normalInbound=一般调入 / normalOutbound=一般调出',
    `audit_status`        VARCHAR(4)    DEFAULT NULL COMMENT '审核状态：20=审批通过',
    `adjuster_id`         VARCHAR(32)   DEFAULT NULL COMMENT '调整人 ID',
    `adjuster_name`       VARCHAR(64)   DEFAULT NULL COMMENT '调整人名称',
    `adjust_reason`       VARCHAR(1000) DEFAULT NULL COMMENT '调整原因',
    `adjust_advice`       VARCHAR(1000) DEFAULT NULL COMMENT '调整意见',
    `submit_time`         DATETIME      DEFAULT NULL COMMENT '提交时间',
    `audit_time`          DATETIME      DEFAULT NULL COMMENT '审核时间',
    `entry_time`          DATETIME      DEFAULT NULL COMMENT '入池时间',
    `is_deleted`          TINYINT(1)    DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`           DATETIME      DEFAULT NULL COMMENT '创建时间',
    `updt_time`           DATETIME      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_fund_pool_status_code_pool` (`fund_code`, `target_pool_id`, `is_deleted`),
    KEY `idx_fund_pool_status_pool_status` (`target_pool_id`, `audit_status`, `is_deleted`),
    KEY `idx_fund_pool_status_log` (`adjust_log_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '基金当前池状态表';

-- ============================================================================
-- 3. 基金调库审批步骤记录表
-- ============================================================================
CREATE TABLE `ip_adjust_step_fund`
(
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `adjust_log_id`     BIGINT        DEFAULT NULL COMMENT '关联基金调库记录 ID，逻辑关联 ip_adjust_log_fund.id',
    `adjust_batch_no`   VARCHAR(64)   DEFAULT NULL COMMENT '基金调库批次号，使用 FUND 前缀，同一组调库记录共用',
    `flow_node_id`      BIGINT        DEFAULT NULL COMMENT '关联流程节点 ID',
    `node_code`         VARCHAR(32)   DEFAULT NULL COMMENT '节点业务标识',
    `node_label`        VARCHAR(128)  DEFAULT NULL COMMENT '节点显示名称',
    `node_type`         VARCHAR(32)   DEFAULT NULL COMMENT '节点类型：start=开始 / approval=审批 / auto=自动处理 / end=结束 / notify=通知 / condition=条件',
    `approval_strategy` VARCHAR(16)   DEFAULT NULL COMMENT '审批策略：preempt=抢占审批 / all=会签 / initiator=发起人 / o32=O32自动审批 / auto=系统自动审批',
    `sort_order`        INT           DEFAULT NULL COMMENT '排序序号',
    `step_status`       VARCHAR(16)   DEFAULT NULL COMMENT '步骤处理状态：pending=待处理 / approve=通过 / reject=驳回 / submit=提交 / auto_process=自动处理 / canceled=已撤回',
    `handler_id`        VARCHAR(32)   DEFAULT NULL COMMENT '处理人 ID',
    `handler_name`      VARCHAR(64)   DEFAULT NULL COMMENT '处理人名称',
    `process_action`    VARCHAR(16)   DEFAULT NULL COMMENT '处理动作：submit=提交 / resubmit=重新提交 / approve=通过 / reject=驳回 / auto_process=自动处理 / skipped=被跳过',
    `process_comment`   VARCHAR(1000) DEFAULT NULL COMMENT '处理意见',
    `start_time`        DATETIME      DEFAULT NULL COMMENT '步骤激活时间',
    `process_time`      DATETIME      DEFAULT NULL COMMENT '处理时间',
    `crte_time`         DATETIME      DEFAULT NULL COMMENT '创建时间',
    `updt_time`         DATETIME      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_fund_adjust_step_log_status` (`adjust_log_id`, `step_status`),
    KEY `idx_fund_adjust_step_batch_status` (`adjust_batch_no`, `step_status`),
    KEY `idx_fund_adjust_step_handler_status` (`handler_id`, `step_status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '基金调库审批步骤记录表';
