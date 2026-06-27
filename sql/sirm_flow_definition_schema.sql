-- ============================================================================
-- 工作流平台 — 流程定义模块数据库设计
-- 数据库版本：MySQL 8.4
-- 说　　明：根据前端 flow-definition-vue2.html 反向设计
-- 规　　范：
--   1. 主键 id 非空，其余字段全部允许为 NULL
--   2. 时间字段统一命名 crte_time（创建时间）/ updt_time（修改时间）
--   3. 仅保留 PRIMARY KEY，不设其他约束（含外键）
--   4. 每张业务表附带同结构事件表（_evt），用于操作审计
-- ============================================================================

USE znty_sirm;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `wf_edge_cond_rule_evt`;
DROP TABLE IF EXISTS `wf_edge_cond_rule`;
DROP TABLE IF EXISTS `wf_flow_edge_evt`;
DROP TABLE IF EXISTS `wf_flow_edge`;
DROP TABLE IF EXISTS `wf_node_condition_config_evt`;
DROP TABLE IF EXISTS `wf_node_condition_config`;
DROP TABLE IF EXISTS `wf_node_notify_config_evt`;
DROP TABLE IF EXISTS `wf_node_notify_config`;
DROP TABLE IF EXISTS `wf_node_auto_config_evt`;
DROP TABLE IF EXISTS `wf_node_auto_config`;
DROP TABLE IF EXISTS `wf_node_approval_handler_evt`;
DROP TABLE IF EXISTS `wf_node_approval_handler`;
DROP TABLE IF EXISTS `wf_node_approval_config_evt`;
DROP TABLE IF EXISTS `wf_node_approval_config`;
DROP TABLE IF EXISTS `wf_flow_node_evt`;
DROP TABLE IF EXISTS `wf_flow_node`;
DROP TABLE IF EXISTS `wf_flow_version_evt`;
DROP TABLE IF EXISTS `wf_flow_version`;
DROP TABLE IF EXISTS `wf_flow_definition_evt`;
DROP TABLE IF EXISTS `wf_flow_definition`;
DROP TABLE IF EXISTS `wf_role_dict_evt`;
DROP TABLE IF EXISTS `wf_role_dict`;


-- ============================================================================
-- 1. 流程定义主表
--    对应前端：tableData 列表、createForm 新建弹窗、editInfoForm 编辑弹窗
--    版本号不在本表冗余，由 wf_flow_version 聚合查询（SELECT MAX(ver_num)）
-- ============================================================================
CREATE TABLE `wf_flow_definition` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `name`        VARCHAR(128) DEFAULT NULL             COMMENT '流程名称，如：信用债入库审批',
    `flow_key`    VARCHAR(128) DEFAULT NULL             COMMENT '流程唯一标识 Key，代码调用时使用，如：bond:credit-inbound',
    `category`    VARCHAR(32)  DEFAULT NULL             COMMENT '业务分类：bond=债券 / fund=基金 / stock=股票 / company=主体 / other=其他',
    `description` VARCHAR(512) DEFAULT NULL             COMMENT '流程描述，简述业务场景及用途',
    `remark`      VARCHAR(512) DEFAULT NULL             COMMENT '内部备注，不对发起人展示',
    `status`      VARCHAR(16)  DEFAULT NULL             COMMENT '流程状态：draft=草稿 / active=已发布 / disabled=已停用',
    `created_by`  BIGINT       DEFAULT NULL             COMMENT '创建人用户 ID',
    `updated_by`  BIGINT       DEFAULT NULL             COMMENT '最后更新人用户 ID',
    `is_deleted`  TINYINT(1)   DEFAULT NULL             COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`   DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`   DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '流程定义主表';

CREATE TABLE `wf_flow_definition_evt` (
    `evt_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`          BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `name`        VARCHAR(128) DEFAULT NULL             COMMENT '流程名称，如：信用债入库审批',
    `flow_key`    VARCHAR(128) DEFAULT NULL             COMMENT '流程唯一标识 Key，代码调用时使用，如：bond:credit-inbound',
    `category`    VARCHAR(32)  DEFAULT NULL             COMMENT '业务分类：bond=债券 / fund=基金 / stock=股票 / company=主体 / other=其他',
    `description` VARCHAR(512) DEFAULT NULL             COMMENT '流程描述，简述业务场景及用途',
    `remark`      VARCHAR(512) DEFAULT NULL             COMMENT '内部备注，不对发起人展示',
    `status`      VARCHAR(16)  DEFAULT NULL             COMMENT '流程状态：draft=草稿 / active=已发布 / disabled=已停用',
    `created_by`  BIGINT       DEFAULT NULL             COMMENT '创建人用户 ID',
    `updated_by`  BIGINT       DEFAULT NULL             COMMENT '最后更新人用户 ID',
    `is_deleted`  TINYINT(1)   DEFAULT NULL             COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`   DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`   DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`    VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`    DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`   VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '流程定义事件表（操作审计）';


-- ============================================================================
-- 2. 流程版本表
--    对应前端：historyRows 历史弹窗、publishNote 发布备注、版本快照
--    每次发布产生一条新版本记录，节点/连线 JSON 随版本归档
-- ============================================================================
CREATE TABLE `wf_flow_version` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `flow_id`        BIGINT       DEFAULT NULL             COMMENT '关联流程定义 ID（wf_flow_definition.id）',
    `flow_key`       VARCHAR(128) DEFAULT NULL             COMMENT '冗余流程 Key（写入时快照，不随定义表联动），方便按 Key 查历史',
    `ver_num`        INT          DEFAULT NULL             COMMENT '版本号（整数，展示时拼 v 前缀）',
    `status`         VARCHAR(16)  DEFAULT NULL             COMMENT '版本状态：draft=草稿 / active=已发布 / disabled=已停用',
    `publish_note`   VARCHAR(512) DEFAULT NULL             COMMENT '发布备注，描述本次改动内容',
    `canvas_nodes`   LONGTEXT     DEFAULT NULL             COMMENT '画布节点数据 JSON 快照',
    `canvas_edges`   LONGTEXT     DEFAULT NULL             COMMENT '画布连线数据 JSON 快照',
    `canvas_pan_x`   DOUBLE       DEFAULT NULL             COMMENT '画布水平平移偏移量（px）',
    `canvas_pan_y`   DOUBLE       DEFAULT NULL             COMMENT '画布垂直平移偏移量（px）',
    `canvas_zoom`    DOUBLE       DEFAULT NULL             COMMENT '画布缩放比例（0.25 ~ 2.5）',
    `published_by`   BIGINT       DEFAULT NULL             COMMENT '发布操作人用户 ID',
    `published_time` DATETIME     DEFAULT NULL             COMMENT '发布时间',
    `created_by`     BIGINT       DEFAULT NULL             COMMENT '创建人用户 ID',
    `crte_time`      DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`      DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '流程版本表，每次发布生成一条记录，含画布快照';

CREATE TABLE `wf_flow_version_evt` (
    `evt_id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`             BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `flow_id`        BIGINT       DEFAULT NULL             COMMENT '关联流程定义 ID（wf_flow_definition.id）',
    `flow_key`       VARCHAR(128) DEFAULT NULL             COMMENT '冗余流程 Key（写入时快照，不随定义表联动）',
    `ver_num`        INT          DEFAULT NULL             COMMENT '版本号（整数，展示时拼 v 前缀）',
    `status`         VARCHAR(16)  DEFAULT NULL             COMMENT '版本状态：draft=草稿 / active=已发布 / disabled=已停用',
    `publish_note`   VARCHAR(512) DEFAULT NULL             COMMENT '发布备注，描述本次改动内容',
    `canvas_nodes`   LONGTEXT     DEFAULT NULL             COMMENT '画布节点数据 JSON 快照',
    `canvas_edges`   LONGTEXT     DEFAULT NULL             COMMENT '画布连线数据 JSON 快照',
    `canvas_pan_x`   DOUBLE       DEFAULT NULL             COMMENT '画布水平平移偏移量（px）',
    `canvas_pan_y`   DOUBLE       DEFAULT NULL             COMMENT '画布垂直平移偏移量（px）',
    `canvas_zoom`    DOUBLE       DEFAULT NULL             COMMENT '画布缩放比例（0.25 ~ 2.5）',
    `published_by`   BIGINT       DEFAULT NULL             COMMENT '发布操作人用户 ID',
    `published_time` DATETIME     DEFAULT NULL             COMMENT '发布时间',
    `created_by`     BIGINT       DEFAULT NULL             COMMENT '创建人用户 ID',
    `crte_time`      DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`      DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`       VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`       DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`      VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '流程版本事件表（操作审计）';


-- ============================================================================
-- 3. 流程节点表
--    对应前端：nodes 数组
--    节点类型：start=开始 / end=结束 / approval=审批 / condition=条件
--              auto=自动执行 / notify=消息通知
-- ============================================================================
CREATE TABLE `wf_flow_node` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `version_id` BIGINT       DEFAULT NULL             COMMENT '关联流程版本 ID（wf_flow_version.id）',
    `flow_id`    BIGINT       DEFAULT NULL             COMMENT '关联流程定义 ID（冗余，方便按流程直接查询节点）',
    `node_id`    VARCHAR(32)  DEFAULT NULL             COMMENT '节点业务标识，画布内唯一，如 n101（前端生成）',
    `node_type`  VARCHAR(32)  DEFAULT NULL             COMMENT '节点类型：start / end / approval / condition / auto / notify',
    `label`      VARCHAR(128) DEFAULT NULL             COMMENT '节点显示名称，如：HR 审批',
    `shape`      VARCHAR(16)  DEFAULT NULL             COMMENT '画布形状：rect=矩形 / circle=圆形 / diamond=菱形',
    `pos_x`      DOUBLE       DEFAULT NULL             COMMENT '节点在画布上的 X 坐标（px）',
    `pos_y`      DOUBLE       DEFAULT NULL             COMMENT '节点在画布上的 Y 坐标（px）',
    `sub_label`  VARCHAR(128) DEFAULT NULL             COMMENT '节点副标题/角色标识，如：HR_DEPT、发起人上级',
    `sort_order` INT          DEFAULT NULL             COMMENT '节点排序序号',
    `crte_time`  DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '流程节点表，对应画布中每个节点（通用字段）';

CREATE TABLE `wf_flow_node_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `version_id` BIGINT       DEFAULT NULL             COMMENT '关联流程版本 ID（wf_flow_version.id）',
    `flow_id`    BIGINT       DEFAULT NULL             COMMENT '关联流程定义 ID（冗余，方便按流程直接查询节点）',
    `node_id`    VARCHAR(32)  DEFAULT NULL             COMMENT '节点业务标识，画布内唯一，如 n101（前端生成）',
    `node_type`  VARCHAR(32)  DEFAULT NULL             COMMENT '节点类型：start / end / approval / condition / auto / notify',
    `label`      VARCHAR(128) DEFAULT NULL             COMMENT '节点显示名称，如：HR 审批',
    `shape`      VARCHAR(16)  DEFAULT NULL             COMMENT '画布形状：rect=矩形 / circle=圆形 / diamond=菱形',
    `pos_x`      DOUBLE       DEFAULT NULL             COMMENT '节点在画布上的 X 坐标（px）',
    `pos_y`      DOUBLE       DEFAULT NULL             COMMENT '节点在画布上的 Y 坐标（px）',
    `sub_label`  VARCHAR(128) DEFAULT NULL             COMMENT '节点副标题/角色标识，如：HR_DEPT、发起人上级',
    `sort_order` INT          DEFAULT NULL             COMMENT '节点排序序号',
    `crte_time`  DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`   VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '流程节点事件表（操作审计）';


-- ============================================================================
-- 4. 审批节点配置表（approval 专属）
--    对应前端：selNode.approvalStrategy / approvalRemark
-- ============================================================================
CREATE TABLE `wf_node_approval_config` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `node_id`           BIGINT       DEFAULT NULL             COMMENT '关联节点 ID（wf_flow_node.id）',
    `approval_strategy` VARCHAR(32)  DEFAULT NULL             COMMENT '处理策略：preempt=抢占审批（任一人） / all=全部处理 / initiator=流程发起人',
    `approval_remark`   VARCHAR(512) DEFAULT NULL             COMMENT '审批节点备注说明',
    `crte_time`         DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`         DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '审批节点专属配置表（approval 类型）';

CREATE TABLE `wf_node_approval_config_evt` (
    `evt_id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`                BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `node_id`           BIGINT       DEFAULT NULL             COMMENT '关联节点 ID（wf_flow_node.id）',
    `approval_strategy` VARCHAR(32)  DEFAULT NULL             COMMENT '处理策略：preempt=抢占审批（任一人） / all=全部处理 / initiator=流程发起人',
    `approval_remark`   VARCHAR(512) DEFAULT NULL             COMMENT '审批节点备注说明',
    `crte_time`         DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`         DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`          VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`          DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`         VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '审批节点配置事件表（操作审计）';

CREATE TABLE `wf_node_approval_handler` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `approval_config_id` BIGINT       DEFAULT NULL             COMMENT '关联审批节点配置 ID（wf_node_approval_config.id）',
    `subject_type`       VARCHAR(16)  DEFAULT NULL             COMMENT '处理人主体类型：role=角色 / user=人员',
    `subject_id`         BIGINT       DEFAULT NULL             COMMENT '处理人主体 ID（角色 ID 或人员 ID）',
    `subject_name`       VARCHAR(128) DEFAULT NULL             COMMENT '处理人主体名称快照',
    `sort_order`         INT          DEFAULT NULL             COMMENT '排序号',
    `crte_time`          DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`          DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_approval_handler_config` (`approval_config_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '审批节点处理人明细表，支持角色和人员混选';

CREATE TABLE `wf_node_approval_handler_evt` (
    `evt_id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`                 BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `approval_config_id` BIGINT       DEFAULT NULL             COMMENT '关联审批节点配置 ID',
    `subject_type`       VARCHAR(16)  DEFAULT NULL             COMMENT '处理人主体类型：role=角色 / user=人员',
    `subject_id`         BIGINT       DEFAULT NULL             COMMENT '处理人主体 ID',
    `subject_name`       VARCHAR(128) DEFAULT NULL             COMMENT '处理人主体名称快照',
    `sort_order`         INT          DEFAULT NULL             COMMENT '排序号',
    `crte_time`          DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`          DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`           VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`           DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`          VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '审批节点处理人明细事件表（操作审计）';


-- ============================================================================
-- 5. 自动执行节点配置表（auto 专属）
--    对应前端：selNode.autoTasks（数组）、autoRemark
--    一个节点可配置多个自动任务，按 task_seq 顺序执行
--    【已知问题】auto_remark 是节点级属性，但存储在每个任务行上，存在冗余
--              修改备注需要更新该节点下所有行
-- ============================================================================
CREATE TABLE `wf_node_auto_config` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `node_id`     BIGINT       DEFAULT NULL             COMMENT '关联节点 ID（wf_flow_node.id）',
    `task_seq`    INT          DEFAULT NULL             COMMENT '任务执行顺序（从 1 开始）',
    `task_code`   VARCHAR(64)  DEFAULT NULL             COMMENT '任务标识：createAccount=创建账号 / updatePosition=更新持仓 / syncSettlement=同步清算 / riskCheck=风控检查 / sendNotify=发送通知 / archiveRecord=归档记录',
    `auto_remark` VARCHAR(512) DEFAULT NULL             COMMENT '节点备注（节点级属性，同一节点下各行冗余存储）',
    `crte_time`   DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`   DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '自动执行节点任务配置表（auto 类型，一节点多任务）';

CREATE TABLE `wf_node_auto_config_evt` (
    `evt_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`          BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `node_id`     BIGINT       DEFAULT NULL             COMMENT '关联节点 ID（wf_flow_node.id）',
    `task_seq`    INT          DEFAULT NULL             COMMENT '任务执行顺序（从 1 开始）',
    `task_code`   VARCHAR(64)  DEFAULT NULL             COMMENT '任务标识',
    `auto_remark` VARCHAR(512) DEFAULT NULL             COMMENT '节点备注（节点级属性，同一节点下各行冗余存储）',
    `crte_time`   DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`   DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`    VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`    DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`   VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '自动执行节点配置事件表（操作审计）';


-- ============================================================================
-- 6. 消息通知节点配置表（notify 专属）
--    对应前端：selNode.notifyChannels / notifyTarget / notifyPersons
--             notifyTpl / notifyRemark
-- ============================================================================
CREATE TABLE `wf_node_notify_config` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `node_id`         BIGINT        DEFAULT NULL             COMMENT '关联节点 ID（wf_flow_node.id）',
    `notify_channels` JSON          DEFAULT NULL             COMMENT '通知渠道列表，如：["system","email"]，可用 JSON_CONTAINS 查询',
    `notify_target`   VARCHAR(32)   DEFAULT NULL             COMMENT '通知对象：initiator=流程发起人 / person=指定人员',
    `notify_persons`  JSON          DEFAULT NULL             COMMENT '指定通知人员角色列表，notify_target=person 时有效，可用 JSON_CONTAINS 查询',
    `notify_tpl`      VARCHAR(1024) DEFAULT NULL             COMMENT '消息模板内容，支持变量占位符，如：您的申请【#{procName}】已完成',
    `notify_remark`   VARCHAR(512)  DEFAULT NULL             COMMENT '通知节点备注说明',
    `crte_time`       DATETIME      DEFAULT NULL             COMMENT '创建时间',
    `updt_time`       DATETIME      DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '消息通知节点专属配置表（notify 类型）';

CREATE TABLE `wf_node_notify_config_evt` (
    `evt_id`          BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`              BIGINT        DEFAULT NULL             COMMENT '主键 ID',
    `node_id`         BIGINT        DEFAULT NULL             COMMENT '关联节点 ID（wf_flow_node.id）',
    `notify_channels` JSON          DEFAULT NULL             COMMENT '通知渠道列表',
    `notify_target`   VARCHAR(32)   DEFAULT NULL             COMMENT '通知对象：initiator=流程发起人 / person=指定人员',
    `notify_persons`  JSON          DEFAULT NULL             COMMENT '指定通知人员角色列表',
    `notify_tpl`      VARCHAR(1024) DEFAULT NULL             COMMENT '消息模板内容，支持变量占位符',
    `notify_remark`   VARCHAR(512)  DEFAULT NULL             COMMENT '通知节点备注说明',
    `crte_time`       DATETIME      DEFAULT NULL             COMMENT '创建时间',
    `updt_time`       DATETIME      DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`        VARCHAR(20)   DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`        DATETIME      DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`       VARCHAR(20)   DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '消息通知节点配置事件表（操作审计）';


-- ============================================================================
-- 7. 条件节点配置表（condition 专属）
--    对应前端：selNode.conditionRemark
--    注：分支条件逻辑在连线表（wf_flow_edge + wf_edge_cond_rule）中维护
--    【已知问题】本表仅含一个备注字段 + 时间戳，性价比低
--              可考虑将 remark 上收至 wf_flow_node 加通用列
-- ============================================================================
CREATE TABLE `wf_node_condition_config` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `node_id`          BIGINT       DEFAULT NULL             COMMENT '关联节点 ID（wf_flow_node.id）',
    `condition_remark` VARCHAR(512) DEFAULT NULL             COMMENT '条件节点备注说明（具体分支逻辑在出线上配置）',
    `crte_time`        DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`        DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '条件节点专属配置表（condition 类型）';

CREATE TABLE `wf_node_condition_config_evt` (
    `evt_id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`               BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `node_id`          BIGINT       DEFAULT NULL             COMMENT '关联节点 ID（wf_flow_node.id）',
    `condition_remark` VARCHAR(512) DEFAULT NULL             COMMENT '条件节点备注说明',
    `crte_time`        DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`        DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`         VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`         DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`        VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '条件节点配置事件表（操作审计）';


-- ============================================================================
-- 8. 流程连线表
--    对应前端：edges 数组，含连线的起止节点、标签、条件规则、备注
--    from_node_id / to_node_id 关联 wf_flow_node.id（BIGINT 代理键）
--    edge_id 保留前端业务标识（如 e102），仅用于画布还原
-- ============================================================================
CREATE TABLE `wf_flow_edge` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `version_id`   BIGINT       DEFAULT NULL             COMMENT '关联流程版本 ID（wf_flow_version.id）',
    `flow_id`      BIGINT       DEFAULT NULL             COMMENT '关联流程定义 ID（冗余，方便按流程查询）',
    `edge_id`      VARCHAR(32)  DEFAULT NULL             COMMENT '连线业务标识，画布内唯一，如 e102（前端生成，仅用于画布还原）',
    `from_node_id` BIGINT       DEFAULT NULL             COMMENT '起始节点数据库 ID（wf_flow_node.id），用于 SQL 关联查询',
    `to_node_id`   BIGINT       DEFAULT NULL             COMMENT '目标节点数据库 ID（wf_flow_node.id），用于 SQL 关联查询',
    `label`        VARCHAR(128) DEFAULT NULL             COMMENT '连线显示标签，如：通过 / 驳回，条件配置后自动同步',
    `route_action` VARCHAR(16)  DEFAULT NULL             COMMENT '流转动作：approve=通过 / reject=驳回 / auto=自动 / submit=提交',
    `cond_logic`   VARCHAR(8)   DEFAULT NULL             COMMENT '多条件间逻辑关系：AND=且 / OR=或',
    `remark`       VARCHAR(512) DEFAULT NULL             COMMENT '连线备注说明',
    `crte_time`    DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`    DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '流程连线表，记录节点间的有向边';

CREATE TABLE `wf_flow_edge_evt` (
    `evt_id`       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`           BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `version_id`   BIGINT       DEFAULT NULL             COMMENT '关联流程版本 ID（wf_flow_version.id）',
    `flow_id`      BIGINT       DEFAULT NULL             COMMENT '关联流程定义 ID（冗余，方便按流程查询）',
    `edge_id`      VARCHAR(32)  DEFAULT NULL             COMMENT '连线业务标识，画布内唯一，如 e102（前端生成，仅用于画布还原）',
    `from_node_id` BIGINT       DEFAULT NULL             COMMENT '起始节点数据库 ID（wf_flow_node.id）',
    `to_node_id`   BIGINT       DEFAULT NULL             COMMENT '目标节点数据库 ID（wf_flow_node.id）',
    `label`        VARCHAR(128) DEFAULT NULL             COMMENT '连线显示标签，如：通过 / 驳回',
    `route_action` VARCHAR(16)  DEFAULT NULL             COMMENT '流转动作：approve=通过 / reject=驳回 / auto=自动 / submit=提交',
    `cond_logic`   VARCHAR(8)   DEFAULT NULL             COMMENT '多条件间逻辑关系：AND=且 / OR=或',
    `remark`       VARCHAR(512) DEFAULT NULL             COMMENT '连线备注说明',
    `crte_time`    DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`    DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`     VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`     DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`    VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '流程连线事件表（操作审计）';


-- ============================================================================
-- 9. 连线条件规则表
--    对应前端：condRules 数组（每条规则含 field / op / val）
--    条件字段分组：
--      [审批结果] auditStatus=审核状态 / auditComment=审核意见
--      [业务标志] isDebtSimple=债大库简易流程 / isWhitelist=白名单流程
--                 isSimple=简易流程 / isRestricted=禁止库标的 / isLargeAmount=大额交易
--      [流程变量] applyAmount=申请金额 / creditRating=标的评级 / investType=投资类型
-- ============================================================================
CREATE TABLE `wf_edge_cond_rule` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `edge_id`    BIGINT       DEFAULT NULL             COMMENT '关联连线 ID（wf_flow_edge.id）',
    `seq`        INT          DEFAULT NULL             COMMENT '条件规则序号（用于排序，与 cond_logic 配合）',
    `field_code` VARCHAR(64)  DEFAULT NULL             COMMENT '判断字段标识',
    `operator`   VARCHAR(8)   DEFAULT NULL             COMMENT '比较运算符：eq / neq / gt / lt / gte / lte',
    `field_val`  VARCHAR(256) DEFAULT NULL             COMMENT '判断值，如：通过 / 驳回 / 是 / 否 / 1000000 / AAA 等',
    `crte_time`  DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '连线条件规则表，一条连线可配置多条规则，规则间逻辑以 wf_flow_edge.cond_logic 连接';

CREATE TABLE `wf_edge_cond_rule_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `edge_id`    BIGINT       DEFAULT NULL             COMMENT '关联连线 ID（wf_flow_edge.id）',
    `seq`        INT          DEFAULT NULL             COMMENT '条件规则序号',
    `field_code` VARCHAR(64)  DEFAULT NULL             COMMENT '判断字段标识',
    `operator`   VARCHAR(8)   DEFAULT NULL             COMMENT '比较运算符：eq / neq / gt / lt / gte / lte',
    `field_val`  VARCHAR(256) DEFAULT NULL             COMMENT '判断值',
    `crte_time`  DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`   VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '连线条件规则事件表（操作审计）';


-- ============================================================================
-- 10. 业务角色字典表
--     对应前端：审批处理人 / 通知对象 下拉选项
--     角色编码：fund-manager / trader / risk-officer / compliance
--               invest-director / admin
-- ============================================================================
CREATE TABLE `wf_role_dict` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `role_code`  VARCHAR(64)  DEFAULT NULL             COMMENT '角色编码，如：fund-manager',
    `role_name`  VARCHAR(64)  DEFAULT NULL             COMMENT '角色中文名，如：投资经理',
    `sort_order` INT          DEFAULT NULL             COMMENT '排序序号',
    `is_active`  TINYINT(1)   DEFAULT NULL             COMMENT '是否启用：1=启用 / 0=停用',
    `crte_time`  DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '业务角色字典表，维护审批人/通知人的角色选项';

CREATE TABLE `wf_role_dict_evt` (
    `evt_id`     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`         BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `role_code`  VARCHAR(64)  DEFAULT NULL             COMMENT '角色编码，如：fund-manager',
    `role_name`  VARCHAR(64)  DEFAULT NULL             COMMENT '角色中文名，如：投资经理',
    `sort_order` INT          DEFAULT NULL             COMMENT '排序序号',
    `is_active`  TINYINT(1)   DEFAULT NULL             COMMENT '是否启用：1=启用 / 0=停用',
    `crte_time`  DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`  DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`   VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`   DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`  VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '业务角色字典事件表（操作审计）';


-- ============================================================================
-- 初始化：角色字典种子数据
-- ============================================================================
INSERT INTO `wf_role_dict` (`role_code`, `role_name`, `sort_order`, `is_active`, `crte_time`, `updt_time`) VALUES
    ('fund-manager',    '投资经理',   1, 1, NOW(), NOW()),
    ('trader',          '交易员',     2, 1, NOW(), NOW()),
    ('risk-officer',    '风控专员',   3, 1, NOW(), NOW()),
    ('compliance',      '合规专员',   4, 1, NOW(), NOW()),
    ('invest-director', '投资总监',   5, 1, NOW(), NOW()),
    ('admin',           '系统管理员', 6, 1, NOW(), NOW());


SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================================
-- 表清单汇总（共 22 张表：11 业务表 + 11 事件表）
-- ============================================================================
--
--  业务表                           事件表
--  ───────────────────────────────  ─────────────────────────────────
--  wf_flow_definition               wf_flow_definition_evt
--  wf_flow_version                  wf_flow_version_evt
--  wf_flow_node                     wf_flow_node_evt
--  wf_node_approval_config          wf_node_approval_config_evt
--  wf_node_approval_handler         wf_node_approval_handler_evt
--  wf_node_auto_config              wf_node_auto_config_evt
--  wf_node_notify_config            wf_node_notify_config_evt
--  wf_node_condition_config         wf_node_condition_config_evt
--  wf_flow_edge                     wf_flow_edge_evt
--  wf_edge_cond_rule                wf_edge_cond_rule_evt
--  wf_role_dict                     wf_role_dict_evt
--
--
-- ============================================================================
-- ER 关系概要（逻辑关联，无外键约束）
-- ============================================================================
--
--  wf_flow_definition (1)
--    └── wf_flow_version (N)               flow_id → wf_flow_definition.id
--          ├── wf_flow_node (N)            version_id → wf_flow_version.id
--          │     ├── wf_node_approval_config  (1:1)  node_id → wf_flow_node.id
--          │     │     └── wf_node_approval_handler (N) approval_config_id → wf_node_approval_config.id
--          │     ├── wf_node_auto_config      (1:N)  node_id → wf_flow_node.id
--          │     ├── wf_node_notify_config    (1:1)  node_id → wf_flow_node.id
--          │     └── wf_node_condition_config (1:1)  node_id → wf_flow_node.id
--          └── wf_flow_edge (N)            version_id → wf_flow_version.id
--                │  from_node_id → wf_flow_node.id（BIGINT 代理键，支持 SQL 图遍历）
--                │  to_node_id   → wf_flow_node.id（BIGINT 代理键，支持 SQL 图遍历）
--                │  edge_id 字段保留前端画布标识（如 e102），仅用于画布还原
--                └── wf_edge_cond_rule (N)  edge_id → wf_flow_edge.id
--
--  wf_role_dict  独立字典表
--
--
-- ============================================================================
-- 变更记录
-- ============================================================================
--
-- v2 (2026-05-19)
--   1. wf_flow_definition 删除 current_ver 字段
--      版本号改由 SELECT MAX(ver_num) FROM wf_flow_version WHERE flow_id=? 查询
--   2. wf_flow_edge.from_node_id / to_node_id：VARCHAR(32) → BIGINT
--      改为关联 wf_flow_node.id（代理键），解决与配置表引用方式不一致的问题
--      edge_id（VARCHAR）保留，仅用于画布还原
--   3. wf_node_notify_config.notify_channels / notify_persons：VARCHAR → JSON
--      利用 MySQL 8.4 原生 JSON 类型，支持 JSON_CONTAINS 精确查询和自动校验
--   4. 同步修改所有受影响的事件表（_evt）
--   5. 审批节点处理人由 wf_node_approval_handler 明细表维护，支持角色和人员混选
-- ============================================================================
