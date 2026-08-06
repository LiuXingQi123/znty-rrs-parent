-- ============================================================
-- znty-rrs 通用导入临时表 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：导入批次主表 + 导入临时明细（FLD001~FLD030 通用槽位）
--       参考 IMP_TMPPROS_C / IMP_TMP_C，对齐本项目字段规范
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_imp_tmp`;
DROP TABLE IF EXISTS `sys_imp_tmp_batch`;

-- ----------------------------------------------------------------------------
-- 导入数据批次（临时主表）
-- ----------------------------------------------------------------------------
CREATE TABLE `sys_imp_tmp_batch`
(
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `imp_id`        VARCHAR(32)   DEFAULT NULL COMMENT '导入批次号',
    `biz_type`      VARCHAR(32)   DEFAULT NULL COMMENT '批次类型：security_pool_excel=证券池Excel导入',
    `template_code` VARCHAR(64)   DEFAULT NULL COMMENT '模板编码',
    `file_name`     VARCHAR(256)  DEFAULT NULL COMMENT '原始文件名',
    `file_size`     BIGINT        DEFAULT NULL COMMENT '文件字节数',
    `file_path`     VARCHAR(512)  DEFAULT NULL COMMENT '文件存储路径',
    `target_id`     BIGINT        DEFAULT NULL COMMENT '目标对象 ID（本业务=目标池 id）',
    `target_name`   VARCHAR(128)  DEFAULT NULL COMMENT '目标对象名称快照',
    `target_type`   VARCHAR(32)   DEFAULT NULL COMMENT '目标对象类型快照（pool_type）',
    `biz_mode`      VARCHAR(16)   DEFAULT NULL COMMENT '业务模式：in=调入 / out=调出',
    `option_json`   VARCHAR(2000) DEFAULT NULL COMMENT '选项 JSON：clearTarget/allowLinkMutex 等',
    `reason`        VARCHAR(1000) DEFAULT NULL COMMENT '调整原因',
    `total_count`   INT           DEFAULT NULL COMMENT '明细总行数',
    `pass_count`    INT           DEFAULT NULL COMMENT '校验通过数',
    `fail_count`    INT           DEFAULT NULL COMMENT '校验失败数',
    `chk_rslt`      VARCHAR(1)    DEFAULT NULL COMMENT '批次校验结果：0=未校验 / 1=全部通过 / 2=存在失败',
    `chk_dscr`      VARCHAR(500)  DEFAULT NULL COMMENT '批次校验说明',
    `save_rslt`     VARCHAR(1)    DEFAULT NULL COMMENT '保存结果：0=未保存 / 1=成功 / 2=失败 / 3=已取消',
    `save_dscr`     VARCHAR(500)  DEFAULT NULL COMMENT '保存说明',
    `imp_time`      DATETIME      DEFAULT NULL COMMENT '导入时间',
    `opter_id`      VARCHAR(32)   DEFAULT NULL COMMENT '经办人 ID',
    `opter_name`    VARCHAR(64)   DEFAULT NULL COMMENT '经办人名称',
    `result_json`   MEDIUMTEXT    DEFAULT NULL COMMENT '校验/提交结果扩展 JSON（含 checkItems，体积可能较大）',
    `is_deleted`    TINYINT(1)    DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`     DATETIME      DEFAULT NULL COMMENT '创建时间',
    `updt_time`     DATETIME      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '导入数据批次（临时主表）';

-- ----------------------------------------------------------------------------
-- 导入临时明细表（通用字段槽 FLD001~FLD030）
-- ----------------------------------------------------------------------------
CREATE TABLE `sys_imp_tmp`
(
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `imp_detl_id` VARCHAR(32)  DEFAULT NULL COMMENT '导入明细号',
    `imp_id`      VARCHAR(32)  DEFAULT NULL COMMENT '导入批次号',
    `row_no`      INT          DEFAULT NULL COMMENT 'Excel 行号',
    `chk_rslt`    VARCHAR(1)   DEFAULT NULL COMMENT '校验结果：0=待校验 / 1=通过 / 2=失败',
    `chk_dscr`    VARCHAR(500) DEFAULT NULL COMMENT '校验说明',
    `save_rslt`   VARCHAR(1)   DEFAULT NULL COMMENT '保存结果：0=未保存 / 1=成功 / 2=失败 / 3=跳过',
    `save_dscr`   VARCHAR(500) DEFAULT NULL COMMENT '保存说明',
    `imp_time`    DATETIME     DEFAULT NULL COMMENT '导入时间',
    `opter_id`    VARCHAR(32)  DEFAULT NULL COMMENT '经办人 ID',
    `ref_id`      BIGINT       DEFAULT NULL COMMENT '提交后业务单 ID（本业务=ip_adjust_log.id）',
    `fld001`      VARCHAR(200) DEFAULT NULL COMMENT '字段001（业务主键槽，本业务=证券代码）',
    `fld002`      VARCHAR(200) DEFAULT NULL COMMENT '字段002',
    `fld003`      VARCHAR(200) DEFAULT NULL COMMENT '字段003',
    `fld004`      VARCHAR(200) DEFAULT NULL COMMENT '字段004',
    `fld005`      VARCHAR(200) DEFAULT NULL COMMENT '字段005',
    `fld006`      VARCHAR(200) DEFAULT NULL COMMENT '字段006',
    `fld007`      VARCHAR(200) DEFAULT NULL COMMENT '字段007',
    `fld008`      VARCHAR(200) DEFAULT NULL COMMENT '字段008',
    `fld009`      VARCHAR(200) DEFAULT NULL COMMENT '字段009',
    `fld010`      VARCHAR(200) DEFAULT NULL COMMENT '字段010',
    `fld011`      TEXT         DEFAULT NULL COMMENT '字段011',
    `fld012`      TEXT         DEFAULT NULL COMMENT '字段012',
    `fld013`      TEXT         DEFAULT NULL COMMENT '字段013',
    `fld014`      TEXT         DEFAULT NULL COMMENT '字段014',
    `fld015`      TEXT         DEFAULT NULL COMMENT '字段015',
    `fld016`      TEXT         DEFAULT NULL COMMENT '字段016',
    `fld017`      TEXT         DEFAULT NULL COMMENT '字段017',
    `fld018`      TEXT         DEFAULT NULL COMMENT '字段018',
    `fld019`      TEXT         DEFAULT NULL COMMENT '字段019',
    `fld020`      TEXT         DEFAULT NULL COMMENT '字段020',
    `fld021`      TEXT         DEFAULT NULL COMMENT '字段021',
    `fld022`      TEXT         DEFAULT NULL COMMENT '字段022',
    `fld023`      TEXT         DEFAULT NULL COMMENT '字段023',
    `fld024`      TEXT         DEFAULT NULL COMMENT '字段024',
    `fld025`      TEXT         DEFAULT NULL COMMENT '字段025',
    `fld026`      TEXT         DEFAULT NULL COMMENT '字段026',
    `fld027`      TEXT         DEFAULT NULL COMMENT '字段027',
    `fld028`      TEXT         DEFAULT NULL COMMENT '字段028',
    `fld029`      TEXT         DEFAULT NULL COMMENT '字段029',
    `fld030`      TEXT         DEFAULT NULL COMMENT '字段030',
    `is_deleted`  TINYINT(1)   DEFAULT NULL COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updt_time`   DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '导入临时明细表';
