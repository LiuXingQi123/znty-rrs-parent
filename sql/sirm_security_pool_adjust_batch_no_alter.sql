-- 为证券池调库记录和流程步骤补充同批调库标识。
-- 已存在字段时请勿重复执行。

ALTER TABLE `ip_adjust_log`
    ADD COLUMN `adjust_batch_no` VARCHAR(64) DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用'
    AFTER `adjust_mode`;

ALTER TABLE `ip_adjust_step`
    ADD COLUMN `adjust_batch_no` VARCHAR(64) DEFAULT NULL COMMENT '调库批次号，同一组调库记录共用'
    AFTER `adjust_log_id`;
