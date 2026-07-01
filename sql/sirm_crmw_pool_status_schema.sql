-- ============================================================
-- znty-sirm CRMW投资池当前状态表 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，创建CRMW投资池当前状态表
-- ============================================================

USE znty_sirm;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ip_pool_status_crmw`;

-- ----------------------------------------------------------------------------
-- 创建CRMW投资池当前状态表
-- ----------------------------------------------------------------------------
CREATE TABLE `ip_pool_status_crmw`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `security_code`       VARCHAR(32)  DEFAULT NULL COMMENT '证券代码',
    `security_short_name` VARCHAR(128) DEFAULT NULL COMMENT '证券简称',
    `security_type`       VARCHAR(32)  DEFAULT NULL COMMENT '证券类型编码，关联 dict_security_type.security_type',
    `crmw_name`           VARCHAR(128) DEFAULT NULL COMMENT 'CRMW名称',
    `crmw_scode`          VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券代码',
    `crmw_mktcode`        VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 市场代码',
    `crmw_stype`          VARCHAR(32)  DEFAULT NULL COMMENT 'CRMW 证券类型',
    `adjust_type`         VARCHAR(32)  DEFAULT NULL COMMENT '调整类型：手工调整/联动调整/互斥调整/关联调整/自动调整/Excel导入/手动批量调整',
    `adjust_mode`         VARCHAR(8)   DEFAULT NULL COMMENT '调整模式：调入/调出',
    `adjust_batch_no`     VARCHAR(64)  DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用',
    `adjust_log_id`       BIGINT       DEFAULT NULL COMMENT '来源调库日志 ID',
    `target_pool_id`      BIGINT       DEFAULT NULL COMMENT '目标投资池 ID，关联 ip_investment_pool.id',
    `target_pool_name`    VARCHAR(128) DEFAULT NULL COMMENT '目标投资池名称',
    `pool_type`           VARCHAR(32)  DEFAULT NULL COMMENT '投资池类型：与 ip_investment_pool.pool_type 一致（credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品 / crmw=CRMW库 / forbidden=禁投池 / observe=观察池 等）',
    `flow_id`             BIGINT       DEFAULT NULL COMMENT '流程定义 ID 快照',
    `flow_key`            VARCHAR(128) DEFAULT NULL COMMENT '流程 Key 快照',
    `flow_type`           VARCHAR(32)  DEFAULT NULL COMMENT '流程类型快照',
    `audit_status`        VARCHAR(4)   DEFAULT NULL COMMENT '审核状态：-1=无效调整 / 00=已提交待审核 / 10=审核通过待审批 / 11=驳回待修改 / 20=审批通过 / 21=审批驳回 / 32=O32自动审批 / 99=发起人已撤回',
    `adjuster_id`         VARCHAR(32)  DEFAULT NULL COMMENT '调整人 ID',
    `adjuster_name`       VARCHAR(64)  DEFAULT NULL COMMENT '调整人名称',
    `adjust_reason`       VARCHAR(500) DEFAULT NULL COMMENT '调整原因',
    `adjust_advice`       VARCHAR(500) DEFAULT NULL COMMENT '调整意见',
    `submit_time`         DATETIME     DEFAULT NULL COMMENT '提交时间',
    `audit_time`          DATETIME     DEFAULT NULL COMMENT '审核时间',
    `entry_time`          DATETIME     DEFAULT NULL COMMENT '入池时间',
    `is_deleted`          TINYINT(1)   DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`           DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`           DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'CRMW投资池当前状态表';
