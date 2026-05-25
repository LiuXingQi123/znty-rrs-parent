-- 1. 环境初始化
CREATE DATABASE IF NOT EXISTS `znty_sirm` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `znty_sirm`;
SET NAMES utf8mb4;

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
    `pool_type`             VARCHAR(64)  DEFAULT NULL            COMMENT '投资池类型：credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品',
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
    `pool_type`             VARCHAR(64)  DEFAULT NULL            COMMENT '投资池类型：credit_bond=信用债 / offshore_bond=境外债 / convertible_bond=转债 / special_account=专户产品',
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

-- ----------------------------------------------------------------------------
-- 5. 初始化固定投资池树
-- ----------------------------------------------------------------------------
INSERT INTO `ip_investment_pool` (`id`, `parent_id`, `pool_code`, `pool_name`, `pool_type`, `pool_level`, `market_codes`, `variety_codes`, `outer_sort`, `inner_sort`, `status`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(1, NULL, 'credit_bond_root', '信用债大库', 'credit_bond', 1, JSON_ARRAY(), JSON_ARRAY('bond'), 1, 1, 'enabled', 0, NOW(), NOW()),
(2, 1, 'credit_bond_level_1', '一级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 1, 1, 'enabled', 0, NOW(), NOW()),
(3, 1, 'credit_bond_level_2', '二级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 1, 2, 'enabled', 0, NOW(), NOW()),
(4, 1, 'credit_bond_level_3', '三级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 1, 3, 'enabled', 0, NOW(), NOW()),
(5, 1, 'credit_bond_level_4', '四级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 1, 4, 'enabled', 0, NOW(), NOW()),
(6, 1, 'credit_bond_level_5', '五级库', 'credit_bond', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 1, 5, 'enabled', 0, NOW(), NOW()),
(7, NULL, 'offshore_bond_root', '境外债库', 'offshore_bond', 1, JSON_ARRAY(), JSON_ARRAY('bond'), 2, 1, 'enabled', 0, NOW(), NOW()),
(8, NULL, 'convertible_bond_root', '转债库', 'convertible_bond', 1, JSON_ARRAY(), JSON_ARRAY('bond'), 3, 1, 'enabled', 0, NOW(), NOW()),
(9, NULL, 'special_account_root', '专户产品', 'special_account', 1, JSON_ARRAY(), JSON_ARRAY('bond'), 4, 1, 'enabled', 0, NOW(), NOW()),
(10, 9, 'special_account_level_1', '一级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 4, 1, 'enabled', 0, NOW(), NOW()),
(11, 9, 'special_account_level_2', '二级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 4, 2, 'enabled', 0, NOW(), NOW()),
(12, 9, 'special_account_level_3', '三级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 4, 3, 'enabled', 0, NOW(), NOW()),
(13, 9, 'special_account_level_4', '四级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 4, 4, 'enabled', 0, NOW(), NOW()),
(14, 9, 'special_account_level_5', '五级库', 'special_account', 2, JSON_ARRAY(), JSON_ARRAY('bond'), 4, 5, 'enabled', 0, NOW(), NOW());

INSERT INTO `ip_investment_pool_evt` (
    `id`, `parent_id`, `pool_code`, `pool_name`, `pool_type`, `pool_level`, `market_codes`, `variety_codes`,
    `hs_pool_name`, `in_flow_id`, `in_flow_key`, `in_flow_name`, `out_flow_id`, `out_flow_key`, `out_flow_name`,
    `simple_in_flow_id`, `simple_in_flow_key`, `simple_in_flow_name`, `simple_out_flow_id`, `simple_out_flow_key`,
    `simple_out_flow_name`, `batch_in_flow_id`, `batch_in_flow_key`, `batch_in_flow_name`, `batch_out_flow_id`,
    `batch_out_flow_key`, `batch_out_flow_name`, `max_capacity`, `outer_sort`, `inner_sort`, `description`, `status`,
    `is_deleted`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`
)
SELECT `id`, `parent_id`, `pool_code`, `pool_name`, `pool_type`, `pool_level`, `market_codes`, `variety_codes`,
       `hs_pool_name`, `in_flow_id`, `in_flow_key`, `in_flow_name`, `out_flow_id`, `out_flow_key`, `out_flow_name`,
       `simple_in_flow_id`, `simple_in_flow_key`, `simple_in_flow_name`, `simple_out_flow_id`, `simple_out_flow_key`,
       `simple_out_flow_name`, `batch_in_flow_id`, `batch_in_flow_key`, `batch_in_flow_name`, `batch_out_flow_id`,
       `batch_out_flow_key`, `batch_out_flow_name`, `max_capacity`, `outer_sort`, `inner_sort`, `description`, `status`,
       `is_deleted`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `ip_investment_pool`;

-- ----------------------------------------------------------------------------
-- 6. 初始化角色和人员数据
-- ----------------------------------------------------------------------------
-- 角色树：研究部 > 信用研究组 > 利率研究组
INSERT INTO `ip_role` (`id`, `role_name`, `parent_id`, `sort_order`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(1, '研究部',       NULL, 1, 0, NOW(), NOW()),
(2, '信用研究组',   1,    2, 0, NOW(), NOW()),
(3, '利率研究组',   2,    3, 0, NOW(), NOW()),
(4, '固收部',       NULL, 4, 0, NOW(), NOW()),
(5, '利率组',       4,    5, 0, NOW(), NOW()),
(6, '信用组',       4,    6, 0, NOW(), NOW()),
(7, '权益部',       NULL, 7, 0, NOW(), NOW()),
(8, '行业研究组',   7,    8, 0, NOW(), NOW()),
(9, '量化部',       NULL, 9, 0, NOW(), NOW());

-- 人员
INSERT INTO `ip_user` (`id`, `user_name`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(1,  '研究员1', 0, NOW(), NOW()),
(2,  '研究员2', 0, NOW(), NOW()),
(3,  '研究员3', 0, NOW(), NOW()),
(4,  '研究员4', 0, NOW(), NOW()),
(5,  '研究员5', 0, NOW(), NOW()),
(6,  '固收1',   0, NOW(), NOW()),
(7,  '固收2',   0, NOW(), NOW()),
(8,  '固收3',   0, NOW(), NOW()),
(9,  '固收4',   0, NOW(), NOW()),
(10, '权益1',   0, NOW(), NOW()),
(11, '权益2',   0, NOW(), NOW()),
(12, '权益3',   0, NOW(), NOW()),
(13, '量化1',   0, NOW(), NOW()),
(14, '量化2',   0, NOW(), NOW());

-- 人员角色关联（支持一人多角色）
INSERT INTO `ip_user_role` (`id`, `user_id`, `role_id`, `is_deleted`, `crte_time`, `updt_time`) VALUES
(1,  1,  1, 0, NOW(), NOW()),  -- 研究员1: 研究部
(2,  1,  2, 0, NOW(), NOW()),  -- 研究员1: 信用研究组
(3,  2,  2, 0, NOW(), NOW()),  -- 研究员2: 信用研究组
(4,  2,  3, 0, NOW(), NOW()),  -- 研究员2: 利率研究组
(5,  3,  1, 0, NOW(), NOW()),  -- 研究员3: 研究部
(6,  3,  3, 0, NOW(), NOW()),  -- 研究员3: 利率研究组
(7,  4,  2, 0, NOW(), NOW()),  -- 研究员4: 信用研究组
(8,  5,  3, 0, NOW(), NOW()),  -- 研究员5: 利率研究组
(9,  6,  4, 0, NOW(), NOW()),  -- 固收1: 固收部
(10, 6,  5, 0, NOW(), NOW()),  -- 固收1: 利率组
(11, 7,  4, 0, NOW(), NOW()),  -- 固收2: 固收部
(12, 7,  6, 0, NOW(), NOW()),  -- 固收2: 信用组
(13, 8,  5, 0, NOW(), NOW()),  -- 固收3: 利率组
(14, 9,  6, 0, NOW(), NOW()),  -- 固收4: 信用组
(15, 10, 7, 0, NOW(), NOW()),  -- 权益1: 权益部
(16, 10, 8, 0, NOW(), NOW()),  -- 权益1: 行业研究组
(17, 11, 7, 0, NOW(), NOW()),  -- 权益2: 权益部
(18, 12, 8, 0, NOW(), NOW()),  -- 权益3: 行业研究组
(19, 13, 9, 0, NOW(), NOW()),  -- 量化1: 量化部
(20, 14, 9, 0, NOW(), NOW());  -- 量化2: 量化部

INSERT INTO `ip_role_evt` (`id`, `role_name`, `parent_id`, `sort_order`, `is_deleted`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`)
SELECT `id`, `role_name`, `parent_id`, `sort_order`, `is_deleted`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `ip_role`;

INSERT INTO `ip_user_evt` (`id`, `user_name`, `is_deleted`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`)
SELECT `id`, `user_name`, `is_deleted`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `ip_user`;

INSERT INTO `ip_user_role_evt` (`id`, `user_id`, `role_id`, `is_deleted`, `crte_time`, `updt_time`, `opter_id`, `opt_time`, `oprt_type`)
SELECT `id`, `user_id`, `role_id`, `is_deleted`, `crte_time`, `updt_time`, 'system', NOW(), '新增'
FROM `ip_user_role`;
