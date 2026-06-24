-- ============================================================
-- znty-sirm 系统附件关联表 - 建库建表脚本
-- MySQL version: 8.0.33
-- 说明：首次部署执行，创建系统附件关联表结构
-- ============================================================

USE znty_sirm;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_attachment`;

-- ----------------------------------------------------------------------------
-- 创建系统附件关联表
-- ----------------------------------------------------------------------------
CREATE TABLE `sys_attachment`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `table_name`          VARCHAR(64)  DEFAULT NULL            COMMENT '关联表名称',
    `main_id`             BIGINT       DEFAULT NULL            COMMENT '关联主键 ID',
    `attachment_category` VARCHAR(32)  DEFAULT NULL            COMMENT '业务附件分类：credit_report=信评报告 / material=其他材料',
    `file_type`           VARCHAR(16)  DEFAULT NULL            COMMENT '附件类型：xls=xls文件 / xlsx=xlsx文件 / pdf=pdf文件 / doc=doc文件 / docx=docx文件 / json=json文件',
    `original_file_name`  VARCHAR(255) DEFAULT NULL            COMMENT '用户上传的原始文件名称',
    `new_file_name`       VARCHAR(255) DEFAULT NULL            COMMENT '新附件名称',
    `file_size`           BIGINT       DEFAULT NULL            COMMENT '文件大小，单位字节',
    `content_type`        VARCHAR(128) DEFAULT NULL            COMMENT '文件 MIME 类型',
    `full_url`            VARCHAR(512) DEFAULT NULL            COMMENT '附件下载访问地址',
    `file_name`           VARCHAR(512) DEFAULT NULL            COMMENT '服务器保存的相对文件路径',
    `uploader_id`         VARCHAR(32)  DEFAULT NULL            COMMENT '上传人 ID',
    `is_deleted`          TINYINT(1)   DEFAULT NULL            COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`           DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`           DATETIME     DEFAULT NULL            COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统附件关联表';
