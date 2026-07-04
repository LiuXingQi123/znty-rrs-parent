-- ============================================================
-- znty-rrs 临时代码管理表 - 建表脚本
-- MySQL version: 8.0.28
-- 说明：维护临时证券代码、正式证券代码更新及取消发行状态
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `rrs_temp_security_code`;

CREATE TABLE `rrs_temp_security_code`
(
    `id`                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `temp_security_name`         VARCHAR(200) DEFAULT NULL            COMMENT '临时证券名称',
    `temp_security_code`         VARCHAR(100) DEFAULT NULL            COMMENT '临时证券代码',
    `temp_security_market`       VARCHAR(32)  DEFAULT NULL            COMMENT '临时证券市场：SSE=上海证券交易所 / SZSE=深圳证券交易所 / CIBM=银行间市场 / OTC=场外市场 / UNKNOWN=未知市场 / JWCW=JWCW市场',
    `temp_security_type`         VARCHAR(64)  DEFAULT NULL            COMMENT '临时证券类型编码，关联 dict_security_type.security_type',
    `temp_mitigation_code`       VARCHAR(100) DEFAULT NULL            COMMENT '临时缓释凭证代码',
    `temp_company_id`            BIGINT       DEFAULT NULL            COMMENT '临时关联主体 ID，关联 t_inv_company.id',
    `temp_company_name_snapshot` VARCHAR(200) DEFAULT NULL            COMMENT '临时关联主体名称快照',
    `temp_issue_date`            DATE         DEFAULT NULL            COMMENT '临时发行日期',
    `temp_maturity_date`         DATE         DEFAULT NULL            COMMENT '临时到期日期',
    `security_name`              VARCHAR(200) DEFAULT NULL            COMMENT '正式证券名称',
    `security_code`              VARCHAR(100) DEFAULT NULL            COMMENT '正式证券代码',
    `security_market`            VARCHAR(32)  DEFAULT NULL            COMMENT '正式证券市场：SSE=上海证券交易所 / SZSE=深圳证券交易所 / CIBM=银行间市场 / OTC=场外市场 / UNKNOWN=未知市场 / JWCW=JWCW市场',
    `security_type`              VARCHAR(64)  DEFAULT NULL            COMMENT '正式证券类型编码，关联 dict_security_type.security_type',
    `update_time`                DATETIME     DEFAULT NULL            COMMENT '业务更新时间，更新为正式证券或取消发行时写入',
    `status`                     VARCHAR(32)  DEFAULT NULL            COMMENT '状态：temporary=临时 / updated=已更新 / cancelled=已取消 / deleted=已删除',
    `operation_type`             VARCHAR(32)  DEFAULT NULL            COMMENT '最近操作：add=新增 / update=更新 / cancel_issue=取消发行 / delete=删除',
    `is_deleted`                 TINYINT(1)   DEFAULT NULL            COMMENT '是否删除：0=否 / 1=是',
    `crte_time`                  DATETIME     DEFAULT NULL            COMMENT '创建时间',
    `updt_time`                  DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_rrs_temp_security_code_temp_code` (`temp_security_code`),
    KEY `idx_rrs_temp_security_code_security_code` (`security_code`),
    KEY `idx_rrs_temp_security_code_company` (`temp_company_id`),
    KEY `idx_rrs_temp_security_code_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='临时代码表';
