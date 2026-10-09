-- ============================================================
-- znty-rrs 股票调库日志、审批步骤和当前池状态建表脚本
-- MySQL version: 8.0.33
-- 独立股票脚本，不纳入 INIT_SCHEMA / INIT_DEMO / RESET_ALL。
-- ============================================================

-- 1. 环境初始化
CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `znty_rrs`;
SET NAMES utf8mb4;

-- 2. 删除旧表（若存在）
-- 日志冻结名称、行业、评级；基础行情按主档实时读取，不创建完整信息快照。
-- 重建日志前仅移除股票日志的附件关联，保留报告库与其他资产附件。
DELETE FROM `sys_attachment` WHERE `table_name` = 'ip_adjust_log_stock';

DROP TABLE IF EXISTS `ip_adjust_step_stock`;
DROP TABLE IF EXISTS `ip_pool_status_stock`;
DROP TABLE IF EXISTS `ip_adjust_log_stock`;

-- ============================================================================
-- 3.1 股票调库记录表
-- ============================================================================
CREATE TABLE `ip_adjust_log_stock`
(
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT      COMMENT '主键 ID',
    `stock_code`              VARCHAR(32)     DEFAULT NULL                 COMMENT '股票代码，关联 rrs_stockinfo.stock_code',
    `stock_name`              VARCHAR(300)    DEFAULT NULL                 COMMENT '股票名称',
    `stock_short_name`        VARCHAR(100)    DEFAULT NULL                 COMMENT '股票简称',
    `security_type`           VARCHAR(64)     DEFAULT NULL                 COMMENT '证券类型：a_share=A股 / hk_share=港股，关联 dict_security_type.security_type',
    `industry_code`           VARCHAR(100)    DEFAULT NULL                 COMMENT '行业编码快照',
    `industry_name`           VARCHAR(100)    DEFAULT NULL                 COMMENT '行业名称快照',
    `latest_rating`           VARCHAR(32)     DEFAULT NULL                 COMMENT '最新研究评级快照：buy=买入 / overweight=增持 / neutral=中性 / underweight=减持 / sell=卖出',
    `previous_rating`         VARCHAR(32)     DEFAULT NULL                 COMMENT '上次研究评级快照：buy=买入 / overweight=增持 / neutral=中性 / underweight=减持 / sell=卖出，取最近第二次有效评级',
    `adjust_type`             VARCHAR(32)     DEFAULT NULL                 COMMENT '调整类型：手工调整=人工选择 / 联动调整=关联池联动 / 互斥调整=互斥池调整 / 关联调整=关联池调整 / 自动调整=系统触发 / Excel导入=文件导入 / 手动批量调整=人工批量发起',
    `adjust_mode`             VARCHAR(8)      DEFAULT NULL                 COMMENT '调整模式：调入=进入目标池 / 调出=退出目标池',
    `adjust_batch_no`         VARCHAR(64)     DEFAULT NULL                 COMMENT '股票调库批次号，使用 STOCK 前缀，同一组调库记录共用',
    `target_pool_id`          BIGINT          DEFAULT NULL                 COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name`        VARCHAR(128)    DEFAULT NULL                 COMMENT '目标投资池名称',
    `pool_type`               VARCHAR(64)     DEFAULT NULL                 COMMENT '投资池类型快照：stock=股票 / stock_product=股票产品',
    `flow_id`                 BIGINT          DEFAULT NULL                 COMMENT '流程定义 ID 快照',
    `flow_key`                VARCHAR(128)    DEFAULT NULL                 COMMENT '流程 Key 快照',
    `flow_type`               VARCHAR(32)     DEFAULT NULL                 COMMENT '流程类型快照：normalInbound=一般调入 / normalOutbound=一般调出 / fastInbound=快速调入 / fastOutbound=快速调出',
    `audit_status`            VARCHAR(4)      DEFAULT NULL                 COMMENT '审核状态：-1=无效调整 / 00=流程中（待审批/审批中） / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 32=O32自动审批（预留） / 99=发起人已撤回',
    `adjuster_id`             VARCHAR(32)     DEFAULT NULL                 COMMENT '调整人 ID',
    `adjuster_name`           VARCHAR(64)     DEFAULT NULL                 COMMENT '调整人名称',
    `adjust_reason`           VARCHAR(1000)   DEFAULT NULL                 COMMENT '调整原因',
    `adjust_advice`           VARCHAR(1000)   DEFAULT NULL                 COMMENT '调整意见',
    `submit_time`             DATETIME        DEFAULT NULL                 COMMENT '提交时间',
    `audit_time`              DATETIME        DEFAULT NULL                 COMMENT '审核时间',
    `entry_time`              DATETIME        DEFAULT NULL                 COMMENT '入池时间',
    `is_deleted`              TINYINT(1)      DEFAULT NULL                 COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`               DATETIME        DEFAULT NULL                 COMMENT '创建时间',
    `updt_time`               DATETIME        DEFAULT NULL                 COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '股票调库记录表';

-- ============================================================================
-- 3.2 股票当前池状态表
-- ============================================================================
CREATE TABLE `ip_pool_status_stock`
(
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT      COMMENT '主键 ID',
    `stock_code`              VARCHAR(32)     DEFAULT NULL                 COMMENT '股票代码，关联 rrs_stockinfo.stock_code',
    `stock_name`              VARCHAR(300)    DEFAULT NULL                 COMMENT '股票名称',
    `stock_short_name`        VARCHAR(100)    DEFAULT NULL                 COMMENT '股票简称',
    `security_type`           VARCHAR(64)     DEFAULT NULL                 COMMENT '证券类型：a_share=A股 / hk_share=港股，关联 dict_security_type.security_type',
    `industry_code`           VARCHAR(100)    DEFAULT NULL                 COMMENT '行业编码快照',
    `industry_name`           VARCHAR(100)    DEFAULT NULL                 COMMENT '行业名称快照',
    `latest_rating`           VARCHAR(32)     DEFAULT NULL                 COMMENT '最新研究评级快照：buy=买入 / overweight=增持 / neutral=中性 / underweight=减持 / sell=卖出',
    `previous_rating`         VARCHAR(32)     DEFAULT NULL                 COMMENT '上次研究评级快照：buy=买入 / overweight=增持 / neutral=中性 / underweight=减持 / sell=卖出，取最近第二次有效评级',
    `adjust_type`             VARCHAR(32)     DEFAULT NULL                 COMMENT '调整类型：手工调整=人工选择 / 联动调整=关联池联动 / 互斥调整=互斥池调整 / 关联调整=关联池调整 / 自动调整=系统触发 / Excel导入=文件导入 / 手动批量调整=人工批量发起',
    `adjust_mode`             VARCHAR(8)      DEFAULT NULL                 COMMENT '调整模式：调入=进入目标池 / 调出=退出目标池',
    `adjust_batch_no`         VARCHAR(64)     DEFAULT NULL                 COMMENT '股票调库批次号，使用 STOCK 前缀，同一组调库记录共用',
    `adjust_log_id`           BIGINT          DEFAULT NULL                 COMMENT '来源股票调库日志 ID，逻辑关联 ip_adjust_log_stock.id',
    `target_pool_id`          BIGINT          DEFAULT NULL                 COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name`        VARCHAR(128)    DEFAULT NULL                 COMMENT '目标投资池名称',
    `pool_type`               VARCHAR(64)     DEFAULT NULL                 COMMENT '投资池类型快照：stock=股票 / stock_product=股票产品',
    `flow_id`                 BIGINT          DEFAULT NULL                 COMMENT '流程定义 ID 快照',
    `flow_key`                VARCHAR(128)    DEFAULT NULL                 COMMENT '流程 Key 快照',
    `flow_type`               VARCHAR(32)     DEFAULT NULL                 COMMENT '流程类型快照：normalInbound=一般调入 / normalOutbound=一般调出 / fastInbound=快速调入 / fastOutbound=快速调出',
    `audit_status`            VARCHAR(4)      DEFAULT NULL                 COMMENT '审核状态：20=审批通过',
    `adjuster_id`             VARCHAR(32)     DEFAULT NULL                 COMMENT '调整人 ID',
    `adjuster_name`           VARCHAR(64)     DEFAULT NULL                 COMMENT '调整人名称',
    `adjust_reason`           VARCHAR(1000)   DEFAULT NULL                 COMMENT '调整原因',
    `adjust_advice`           VARCHAR(1000)   DEFAULT NULL                 COMMENT '调整意见',
    `submit_time`             DATETIME        DEFAULT NULL                 COMMENT '提交时间',
    `audit_time`              DATETIME        DEFAULT NULL                 COMMENT '审核时间',
    `entry_time`              DATETIME        DEFAULT NULL                 COMMENT '入池时间',
    `is_deleted`              TINYINT(1)      DEFAULT NULL                 COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`               DATETIME        DEFAULT NULL                 COMMENT '创建时间',
    `updt_time`               DATETIME        DEFAULT NULL                 COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '股票当前池状态表';

-- ============================================================================
-- 3.3 股票调库审批步骤记录表
-- ============================================================================
CREATE TABLE `ip_adjust_step_stock`
(
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT      COMMENT '主键 ID',
    `adjust_log_id`           BIGINT          DEFAULT NULL                 COMMENT '关联股票调库记录 ID，逻辑关联 ip_adjust_log_stock.id',
    `adjust_batch_no`         VARCHAR(64)     DEFAULT NULL                 COMMENT '股票调库批次号，使用 STOCK 前缀，同一组调库记录共用',
    `flow_node_id`            BIGINT          DEFAULT NULL                 COMMENT '关联流程节点 ID',
    `node_code`               VARCHAR(32)     DEFAULT NULL                 COMMENT '节点业务标识',
    `node_label`              VARCHAR(128)    DEFAULT NULL                 COMMENT '节点显示名称',
    `node_type`               VARCHAR(32)     DEFAULT NULL                 COMMENT '节点类型：start=开始 / approval=审批 / auto=自动处理 / end=结束 / notify=通知 / condition=条件',
    `approval_strategy`       VARCHAR(16)     DEFAULT NULL                 COMMENT '审批策略：preempt=抢占审批 / all=会签 / initiator=发起人 / o32=O32自动审批 / auto=系统自动审批',
    `sort_order`              INT             DEFAULT NULL                 COMMENT '排序序号',
    `step_status`             VARCHAR(16)     DEFAULT NULL                 COMMENT '步骤处理状态：pending=待处理 / approve=通过 / reject=驳回 / submit=提交 / auto_process=自动处理 / canceled=已撤回',
    `handler_id`              VARCHAR(32)     DEFAULT NULL                 COMMENT '处理人 ID',
    `handler_name`            VARCHAR(64)     DEFAULT NULL                 COMMENT '处理人名称',
    `process_action`          VARCHAR(16)     DEFAULT NULL                 COMMENT '处理动作：submit=提交 / resubmit=重新提交 / approve=通过 / reject=驳回 / auto_process=自动处理 / skipped=被跳过',
    `process_comment`         VARCHAR(1000)   DEFAULT NULL                 COMMENT '处理意见',
    `start_time`              DATETIME        DEFAULT NULL                 COMMENT '步骤激活时间',
    `process_time`            DATETIME        DEFAULT NULL                 COMMENT '处理时间',
    `crte_time`               DATETIME        DEFAULT NULL                 COMMENT '创建时间',
    `updt_time`               DATETIME        DEFAULT NULL                 COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '股票调库审批步骤记录表';
