-- ============================================================
-- znty-rrs 我的证券池 - 建表脚本
-- MySQL version: 8.0.33
-- 说明：用户可将关注的证券（证券/股票/基金/公司等）添加到个人收藏池
-- ============================================================

USE
znty_rrs;
SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `my_security_pool`;

-- ----------------------------------------------------------------------------
-- 我的证券池表
-- ----------------------------------------------------------------------------
CREATE TABLE `my_security_pool`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `security_code` VARCHAR(32)  DEFAULT NULL COMMENT '证券代码',
    `security_type` VARCHAR(32)  DEFAULT NULL COMMENT '证券类型：股票/证券/基金/公司...',
    `market`        VARCHAR(32)  DEFAULT NULL COMMENT '证券市场：上交所/深交所/银行间/北交所...',
    `user_id`       VARCHAR(32)  DEFAULT NULL COMMENT '用户 ID',
    `status`        VARCHAR(4)   DEFAULT 'use' COMMENT '状态：use=使用 / del=删除',
    `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '我的证券池';
