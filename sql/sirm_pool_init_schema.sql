-- ============================================================
-- znty-sirm 投资池初始化 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，创建数据库和全部投资池业务表结构
-- ============================================================

-- 1. 环境初始化
CREATE DATABASE IF NOT EXISTS `znty_sirm` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `znty_sirm`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 2. 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ip_pool_permission_evt`;
DROP TABLE IF EXISTS `ip_user_role_evt`;
DROP TABLE IF EXISTS `ip_user_evt`;
DROP TABLE IF EXISTS `ip_role_evt`;
DROP TABLE IF EXISTS `ip_pool_auto_rule_evt`;
DROP TABLE IF EXISTS `ip_pool_relation_evt`;
DROP TABLE IF EXISTS `ip_investment_pool_evt`;
DROP TABLE IF EXISTS `ip_pool_permission`;
DROP TABLE IF EXISTS `ip_user_role`;
DROP TABLE IF EXISTS `ip_user`;
DROP TABLE IF EXISTS `ip_role`;
DROP TABLE IF EXISTS `ip_pool_auto_rule`;
DROP TABLE IF EXISTS `ip_pool_relation`;
DROP TABLE IF EXISTS `ip_investment_pool`;

-- ----------------------------------------------------------------------------
-- 3. 创建主表
-- ----------------------------------------------------------------------------
CREATE TABLE `ip_investment_pool` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `parent_id`             BIGINT       DEFAULT NULL            COMMENT '父级投资池 ID，顶级投资池为空',
    `pool_code`             VARCHAR(64)  DEFAULT NULL            COMMENT '投资池编码，用于系统内部识别，如：credit_bond_level_1',
    `pool_name`             VARCHAR(128) DEFAULT NULL            COMMENT '投资池名称，如：信用债大库、一级库',
    `pool_type`             VARCHAR(64)  DEFAULT NULL            COMMENT '投资池类型：research=研究池 / fund=基金池 / restricted=限制池 / other=其他池 / industry=行业池 / whitelist=白名单 / blacklist=黑名单 / private_placement=私募池 / credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品',
    `pool_level`            INT          DEFAULT NULL            COMMENT '投资池层级：1=一级目录 / 2=子级池',
    `market_codes`          JSON         DEFAULT NULL            COMMENT '投资市场编码 JSON，如：["SSE","SZSE","CIBM"]',
    `variety_codes`         JSON         DEFAULT NULL            COMMENT '投资品种编码 JSON，如：["bond"]',
    `hs_pool_name`          VARCHAR(128) DEFAULT NULL            COMMENT '恒生池名称',
    `in_flow_id`            BIGINT       DEFAULT NULL            COMMENT '标准调入审批流程 ID，空表示不需要审批',
    `in_flow_key`           VARCHAR(128) DEFAULT NULL            COMMENT '标准调入审批流程 Key 快照',
    `in_flow_name`          VARCHAR(128) DEFAULT NULL            COMMENT '标准调入审批流程名称快照',
    `out_flow_id`           BIGINT       DEFAULT NULL            COMMENT '标准调出审批流程 ID，空表示不需要审批',
    `out_flow_key`          VARCHAR(128) DEFAULT NULL            COMMENT '标准调出审批流程 Key 快照',
    `out_flow_name`         VARCHAR(128) DEFAULT NULL            COMMENT '标准调出审批流程名称快照',
    `simple_in_flow_id`     BIGINT       DEFAULT NULL            COMMENT '简易调入审批流程 ID，空表示不需要审批',
    `simple_in_flow_key`    VARCHAR(128) DEFAULT NULL            COMMENT '简易调入审批流程 Key 快照',
    `simple_in_flow_name`   VARCHAR(128) DEFAULT NULL            COMMENT '简易调入审批流程名称快照',
    `simple_out_flow_id`    BIGINT       DEFAULT NULL            COMMENT '简易调出审批流程 ID，空表示不需要审批',
    `simple_out_flow_key`   VARCHAR(128) DEFAULT NULL            COMMENT '简易调出审批流程 Key 快照',
    `simple_out_flow_name`  VARCHAR(128) DEFAULT NULL            COMMENT '简易调出审批流程名称快照',
    `batch_in_flow_id`      BIGINT       DEFAULT NULL            COMMENT '批量调入审批流程 ID，空表示不需要审批',
    `batch_in_flow_key`     VARCHAR(128) DEFAULT NULL            COMMENT '批量调入审批流程 Key 快照',
    `batch_in_flow_name`    VARCHAR(128) DEFAULT NULL            COMMENT '批量调入审批流程名称快照',
    `batch_out_flow_id`     BIGINT       DEFAULT NULL            COMMENT '批量调出审批流程 ID，空表示不需要审批',
    `batch_out_flow_key`    VARCHAR(128) DEFAULT NULL            COMMENT '批量调出审批流程 Key 快照',
    `batch_out_flow_name`   VARCHAR(128) DEFAULT NULL            COMMENT '批量调出审批流程名称快照',
    `in_report_restriction` VARCHAR(32)  DEFAULT NULL            COMMENT '调入研报限制：none=不限制研究报告 / any=任意一篇研究报告 / internal=必须是内部研究报告',
    `out_report_restriction` VARCHAR(32)  DEFAULT NULL            COMMENT '调出研报限制：none=不限制研究报告 / any=任意一篇研究报告 / internal=必须是内部研究报告',
    `max_capacity`          BIGINT       DEFAULT NULL            COMMENT '最大上限数量，用于控制投资池最多容纳标的数量',
    `outer_sort`            INT          DEFAULT NULL            COMMENT '投资池外部排序，用于顶级投资池展示顺序',
    `inner_sort`            INT          DEFAULT NULL            COMMENT '投资池内部排序，用于子级池展示顺序',
    `description`           VARCHAR(512) DEFAULT NULL            COMMENT '投资池描述',
    `status`                VARCHAR(16)  DEFAULT NULL            COMMENT '状态：enabled=启用 / disabled=停用',
    `is_deleted`            TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`             DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`             DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '投资池主表';

CREATE TABLE `ip_pool_relation` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `pool_id`            BIGINT       DEFAULT NULL            COMMENT '投资池 ID，关联 ip_investment_pool.id',
    `relation_type`      VARCHAR(32)  DEFAULT NULL            COMMENT '关系类型：source=来源池 / in_restrict=调入限制池 / out_restrict=调出限制池 / in_linked=调入联动池 / out_linked=调出联动池 / in_mutex=调入互斥池 / out_mutex=调出互斥池 / in_soft_restrict=调入弹性禁投池 / out_soft_restrict=调出弹性禁投池',
    `relation_pool_id`   BIGINT       DEFAULT NULL            COMMENT '关联投资池 ID，关联 ip_investment_pool.id',
    `relation_pool_name` VARCHAR(128) DEFAULT NULL            COMMENT '关联投资池名称快照',
    `sort_order`         INT          DEFAULT NULL            COMMENT '排序序号',
    `remark`             VARCHAR(512) DEFAULT NULL            COMMENT '备注',
    `is_deleted`         TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`          DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`          DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '投资池关系配置表';

CREATE TABLE `ip_pool_auto_rule` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `pool_id`    BIGINT       DEFAULT NULL            COMMENT '投资池 ID，关联 ip_investment_pool.id',
    `rule_type`  VARCHAR(16)  DEFAULT NULL            COMMENT '规则类型：auto_in=自动调入 / auto_out=自动调出',
    `rule_id`    BIGINT       DEFAULT NULL            COMMENT '关联规则 ID（规则管理中心）',
    `rule_desc`  VARCHAR(512) DEFAULT NULL            COMMENT '规则描述文本，一期仅保存备注不执行',
    `is_deleted` TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '投资池自动调入调出规则备注表';

CREATE TABLE `ip_role` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `role_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '角色名称',
    `parent_id`  BIGINT       DEFAULT NULL            COMMENT '父级角色 ID',
    `sort_order` INT          DEFAULT NULL            COMMENT '排序序号',
    `is_deleted` TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '角色/部门表';

CREATE TABLE `ip_user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '人员姓名',
    `is_deleted` TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '人员表';

CREATE TABLE `ip_user_role` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id`    BIGINT       DEFAULT NULL            COMMENT '人员 ID，关联 ip_user.id',
    `role_id`    BIGINT       DEFAULT NULL            COMMENT '角色 ID，关联 ip_role.id',
    `is_deleted` TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '人员角色关联表';

CREATE TABLE `ip_pool_permission` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `pool_id`         BIGINT       DEFAULT NULL            COMMENT '投资池 ID，关联 ip_investment_pool.id',
    `permission_type` VARCHAR(32)  DEFAULT NULL            COMMENT '权限类型：viewable=可查看 / adjustable=可调整 / excel_importable=可Excel导入',
    `subject_type`    VARCHAR(16)  DEFAULT NULL            COMMENT '主体类型：role=角色 / user=人员',
    `subject_id`      BIGINT       DEFAULT NULL            COMMENT '主体 ID（角色 ID 或人员 ID）',
    `subject_name`    VARCHAR(128) DEFAULT NULL            COMMENT '主体名称快照',
    `is_deleted`      TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`       DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`       DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '投资池权限配置表';

-- ----------------------------------------------------------------------------
-- 4. 创建事件表
-- ----------------------------------------------------------------------------
CREATE TABLE `ip_investment_pool_evt` (
    `evt_id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`                    BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `parent_id`             BIGINT       DEFAULT NULL            COMMENT '父级投资池 ID，顶级投资池为空',
    `pool_code`             VARCHAR(64)  DEFAULT NULL            COMMENT '投资池编码，用于系统内部识别，如：credit_bond_level_1',
    `pool_name`             VARCHAR(128) DEFAULT NULL            COMMENT '投资池名称，如：信用债大库、一级库',
    `pool_type`             VARCHAR(64)  DEFAULT NULL            COMMENT '投资池类型：research=研究池 / fund=基金池 / restricted=限制池 / other=其他池 / industry=行业池 / whitelist=白名单 / blacklist=黑名单 / private_placement=私募池 / credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品',
    `pool_level`            INT          DEFAULT NULL            COMMENT '投资池层级：1=一级目录 / 2=子级池',
    `market_codes`          JSON         DEFAULT NULL            COMMENT '投资市场编码 JSON，如：["SSE","SZSE","CIBM"]',
    `variety_codes`         JSON         DEFAULT NULL            COMMENT '投资品种编码 JSON，如：["bond"]',
    `hs_pool_name`          VARCHAR(128) DEFAULT NULL            COMMENT '恒生池名称',
    `in_flow_id`            BIGINT       DEFAULT NULL            COMMENT '标准调入审批流程 ID，空表示不需要审批',
    `in_flow_key`           VARCHAR(128) DEFAULT NULL            COMMENT '标准调入审批流程 Key 快照',
    `in_flow_name`          VARCHAR(128) DEFAULT NULL            COMMENT '标准调入审批流程名称快照',
    `out_flow_id`           BIGINT       DEFAULT NULL            COMMENT '标准调出审批流程 ID，空表示不需要审批',
    `out_flow_key`          VARCHAR(128) DEFAULT NULL            COMMENT '标准调出审批流程 Key 快照',
    `out_flow_name`         VARCHAR(128) DEFAULT NULL            COMMENT '标准调出审批流程名称快照',
    `simple_in_flow_id`     BIGINT       DEFAULT NULL            COMMENT '简易调入审批流程 ID，空表示不需要审批',
    `simple_in_flow_key`    VARCHAR(128) DEFAULT NULL            COMMENT '简易调入审批流程 Key 快照',
    `simple_in_flow_name`   VARCHAR(128) DEFAULT NULL            COMMENT '简易调入审批流程名称快照',
    `simple_out_flow_id`    BIGINT       DEFAULT NULL            COMMENT '简易调出审批流程 ID，空表示不需要审批',
    `simple_out_flow_key`   VARCHAR(128) DEFAULT NULL            COMMENT '简易调出审批流程 Key 快照',
    `simple_out_flow_name`  VARCHAR(128) DEFAULT NULL            COMMENT '简易调出审批流程名称快照',
    `batch_in_flow_id`      BIGINT       DEFAULT NULL            COMMENT '批量调入审批流程 ID，空表示不需要审批',
    `batch_in_flow_key`     VARCHAR(128) DEFAULT NULL            COMMENT '批量调入审批流程 Key 快照',
    `batch_in_flow_name`    VARCHAR(128) DEFAULT NULL            COMMENT '批量调入审批流程名称快照',
    `batch_out_flow_id`     BIGINT       DEFAULT NULL            COMMENT '批量调出审批流程 ID，空表示不需要审批',
    `batch_out_flow_key`    VARCHAR(128) DEFAULT NULL            COMMENT '批量调出审批流程 Key 快照',
    `batch_out_flow_name`   VARCHAR(128) DEFAULT NULL            COMMENT '批量调出审批流程名称快照',
    `in_report_restriction` VARCHAR(32)  DEFAULT NULL            COMMENT '调入研报限制：none=不限制研究报告 / any=任意一篇研究报告 / internal=必须是内部研究报告',
    `out_report_restriction` VARCHAR(32)  DEFAULT NULL            COMMENT '调出研报限制：none=不限制研究报告 / any=任意一篇研究报告 / internal=必须是内部研究报告',
    `max_capacity`          BIGINT       DEFAULT NULL            COMMENT '最大上限数量，用于控制投资池最多容纳标的数量',
    `outer_sort`            INT          DEFAULT NULL            COMMENT '投资池外部排序，用于顶级投资池展示顺序',
    `inner_sort`            INT          DEFAULT NULL            COMMENT '投资池内部排序，用于子级池展示顺序',
    `description`           VARCHAR(512) DEFAULT NULL            COMMENT '投资池描述',
    `status`                VARCHAR(16)  DEFAULT NULL            COMMENT '状态：enabled=启用 / disabled=停用',
    `is_deleted`            TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`             DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`             DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`              VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`              DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`             VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '投资池主表（操作审计）';

CREATE TABLE `ip_pool_relation_evt` (
    `evt_id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`                 BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `pool_id`            BIGINT       DEFAULT NULL            COMMENT '投资池 ID，关联 ip_investment_pool.id',
    `relation_type`      VARCHAR(32)  DEFAULT NULL            COMMENT '关系类型：source=来源池 / in_restrict=调入限制池 / out_restrict=调出限制池 / in_linked=调入联动池 / out_linked=调出联动池 / in_mutex=调入互斥池 / out_mutex=调出互斥池 / in_soft_restrict=调入弹性禁投池 / out_soft_restrict=调出弹性禁投池',
    `relation_pool_id`   BIGINT       DEFAULT NULL            COMMENT '关联投资池 ID，关联 ip_investment_pool.id',
    `relation_pool_name` VARCHAR(128) DEFAULT NULL            COMMENT '关联投资池名称快照',
    `sort_order`         INT          DEFAULT NULL            COMMENT '排序序号',
    `remark`             VARCHAR(512) DEFAULT NULL            COMMENT '备注',
    `is_deleted`         TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`          DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`          DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`           VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`           DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`          VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '投资池关系配置表（操作审计）';

CREATE TABLE `ip_pool_auto_rule_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `pool_id`    BIGINT       DEFAULT NULL            COMMENT '投资池 ID，关联 ip_investment_pool.id',
    `rule_type`  VARCHAR(16)  DEFAULT NULL            COMMENT '规则类型：auto_in=自动调入 / auto_out=自动调出',
    `rule_id`    BIGINT       DEFAULT NULL            COMMENT '关联规则 ID（规则管理中心）',
    `rule_desc`  VARCHAR(512) DEFAULT NULL            COMMENT '规则描述文本，一期仅保存备注不执行',
    `is_deleted` TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`   VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '投资池自动调入调出规则备注表（操作审计）';

CREATE TABLE `ip_role_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `role_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '角色名称',
    `parent_id`  BIGINT       DEFAULT NULL            COMMENT '父级角色 ID',
    `sort_order` INT          DEFAULT NULL            COMMENT '排序序号',
    `is_deleted` TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`   VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '角色/部门表（操作审计）';

CREATE TABLE `ip_user_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `user_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '人员姓名',
    `is_deleted` TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`   VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '人员表（操作审计）';

CREATE TABLE `ip_user_role_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `user_id`    BIGINT       DEFAULT NULL            COMMENT '人员 ID，关联 ip_user.id',
    `role_id`    BIGINT       DEFAULT NULL            COMMENT '角色 ID，关联 ip_role.id',
    `is_deleted` TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`   VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '人员角色关联表（操作审计）';

CREATE TABLE `ip_pool_permission_evt` (
    `evt_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件主键 ID',
    `id`              BIGINT       DEFAULT NULL            COMMENT '主键 ID',
    `pool_id`         BIGINT       DEFAULT NULL            COMMENT '投资池 ID，关联 ip_investment_pool.id',
    `permission_type` VARCHAR(32)  DEFAULT NULL            COMMENT '权限类型：viewable=可查看 / adjustable=可调整 / excel_importable=可Excel导入',
    `subject_type`    VARCHAR(16)  DEFAULT NULL            COMMENT '主体类型：role=角色 / user=人员',
    `subject_id`      BIGINT       DEFAULT NULL            COMMENT '主体 ID（角色 ID 或人员 ID）',
    `subject_name`    VARCHAR(128) DEFAULT NULL            COMMENT '主体名称快照',
    `is_deleted`      TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`       DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`       DATETIME     DEFAULT NULL            COMMENT '修改时间',
    `opter_id`        VARCHAR(20)  DEFAULT NULL            COMMENT '经办人 ID',
    `opt_time`        DATETIME     DEFAULT NULL            COMMENT '经办时间',
    `oprt_type`       VARCHAR(20)  DEFAULT NULL            COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '投资池权限配置表（操作审计）';
