-- ============================================================
-- znty-rrs 股票调库日志、审批步骤、当前池状态与自选股演示数据
-- MySQL version: 8.0.33
-- 独立股票脚本，不纳入 INIT_SCHEMA / INIT_DEMO / RESET_ALL。
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

-- 前置：公共池/流程/AIS用户已初始化，股票基础 Demo 已执行。
-- 一般115/116、快速117/118；只审批通过的调入记录保留当前池状态。
-- 仅移除股票日志附件关联，禁止清空公共报告库及其他资产自选记录。
DELETE FROM `sys_attachment` WHERE `table_name` = 'ip_adjust_log_stock';
TRUNCATE TABLE `ip_adjust_step_stock`;
TRUNCATE TABLE `ip_pool_status_stock`;
TRUNCATE TABLE `ip_adjust_log_stock`;

INSERT INTO `ip_adjust_log_stock`
(
    `id`
    ,`stock_code`
    ,`stock_name`
    ,`stock_short_name`
    ,`security_type`
    ,`industry_code`
    ,`industry_name`
    ,`latest_rating`
    ,`previous_rating`
    ,`adjust_type`
    ,`adjust_mode`
    ,`adjust_batch_no`
    ,`target_pool_id`
    ,`target_pool_name`
    ,`pool_type`
    ,`flow_id`
    ,`flow_key`
    ,`flow_type`
    ,`audit_status`
    ,`adjuster_id`
    ,`adjuster_name`
    ,`adjust_reason`
    ,`adjust_advice`
    ,`submit_time`
    ,`audit_time`
    ,`entry_time`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
)
VALUES
(1, 'STOCK001.SH', '晨曦林业股份有限公司', '晨曦林业', 'a_share', 'A02', '林业', 'buy', 'overweight', '手工调整', '调入', 'STOCK_DEMO_001', 106, '基础库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00', 0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(2, 'STOCK002.SZ', '海蓝渔业股份有限公司', '海蓝渔业', 'a_share', 'A04', '渔业', 'overweight', 'buy', '手工调整', '调入', 'STOCK_DEMO_002', 106, '基础库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-01 11:00:00', '2026-10-01 11:00:00', '2026-10-01 11:00:00', 0, '2026-10-01 11:00:00', '2026-10-01 11:00:00'),
(3, 'STOCK003.SH', '远景科技股份有限公司', '远景科技', 'a_share', 'I65', '软件和信息技术服务业', 'neutral', 'overweight', '手工调整', '调入', 'STOCK_DEMO_003', 107, '重点库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 12:00:00', '2026-10-01 12:00:00', '2026-10-01 12:00:00', 0, '2026-10-01 12:00:00', '2026-10-01 12:00:00'),
(4, 'STOCK004.SZ', '华信制造股份有限公司', '华信制造', 'a_share', 'C35', '专用设备制造业', 'underweight', 'neutral', '手工调整', '调入', 'STOCK_DEMO_004', 109, '核心库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-01 13:00:00', '2026-10-01 13:00:00', '2026-10-01 13:00:00', 0, '2026-10-01 13:00:00', '2026-10-01 13:00:00'),
(5, 'STOCK005.HK', '港湾医药控股有限公司', '港湾医药', 'hk_share', 'C27', '医药制造业', 'buy', 'neutral', '手工调整', '调入', 'STOCK_DEMO_005', 141, '港股基础库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 14:00:00', '2026-10-01 14:00:00', '2026-10-01 14:00:00', 0, '2026-10-01 14:00:00', '2026-10-01 14:00:00'),
(6, 'STOCK006.HK', '星河科技控股有限公司', '星河科技', 'hk_share', 'I65', '软件和信息技术服务业', 'overweight', 'buy', '手工调整', '调入', 'STOCK_DEMO_006', 120, '兴业科技创新产品库H股', 'stock_product', 115, 'stock:normal-inbound', 'normalInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-01 15:00:00', '2026-10-01 15:00:00', '2026-10-01 15:00:00', 0, '2026-10-01 15:00:00', '2026-10-01 15:00:00'),
(7, 'STOCK007.SH', '青山食品股份有限公司', '青山食品', 'a_share', 'C14', '食品制造业', 'sell', 'underweight', '手工调整', '调入', 'STOCK_DEMO_007', 108, '限制类股票库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 16:00:00', '2026-10-01 16:00:00', '2026-10-01 16:00:00', 0, '2026-10-01 16:00:00', '2026-10-01 16:00:00'),
(8, 'STOCK008.SZ', '丰禾农业股份有限公司', '丰禾农业', 'a_share', 'A01', '农业', 'neutral', NULL, '手工调整', '调入', 'STOCK_DEMO_008', 106, '基础库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-01 09:00:00', '2026-10-01 09:00:00', '2026-10-01 09:00:00', 0, '2026-10-01 09:00:00', '2026-10-01 09:00:00'),
(9, 'STOCK009.HK', '海岳工业控股有限公司', '海岳工业', 'hk_share', 'C35', '专用设备制造业', 'neutral', 'buy', '手工调整', '调入', 'STOCK_DEMO_009', 121, '兴业先进制造产品库H股', 'stock_product', 115, 'stock:normal-inbound', 'normalInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00', 0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(10, 'STOCK010.SH', '安宁能源股份有限公司', '安宁能源', 'a_share', 'D44', '电力、热力生产和供应业', 'buy', 'buy', '手工调整', '调入', 'STOCK_DEMO_010', 109, '核心库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-02 11:00:00', '2026-10-02 11:00:00', '2026-10-02 11:00:00', 0, '2026-10-02 11:00:00', '2026-10-02 11:00:00'),
(11, 'STOCK001.SH', '晨曦林业股份有限公司', '晨曦林业', 'a_share', 'A02', '林业', 'buy', 'overweight', '手工调整', '调入', 'STOCK_DEMO_011', 107, '重点库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-02 12:00:00', '2026-10-02 12:00:00', '2026-10-02 12:00:00', 0, '2026-10-02 12:00:00', '2026-10-02 12:00:00'),
(12, 'STOCK003.SH', '远景科技股份有限公司', '远景科技', 'a_share', 'I65', '软件和信息技术服务业', 'neutral', 'overweight', '手工调整', '调入', 'STOCK_DEMO_012', 106, '基础库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-02 13:00:00', '2026-10-02 13:00:00', '2026-10-02 13:00:00', 0, '2026-10-02 13:00:00', '2026-10-02 13:00:00'),
(13, 'STOCK013.SZ', '零成交工业股份有限公司', '零成交工业', 'a_share', 'C35', '专用设备制造业', 'neutral', NULL, '手工调整', '调入', 'STOCK_DEMO_013', 106, '基础库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-02 14:00:00', '2026-10-02 14:00:00', '2026-10-02 14:00:00', 0, '2026-10-02 14:00:00', '2026-10-02 14:00:00'),
(14, 'STOCK014.SH', '缺行情林业股份有限公司', '缺行情林业', 'a_share', 'A02', '林业', NULL, NULL, '手工调整', '调入', 'STOCK_DEMO_014', 107, '重点库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-02 15:00:00', '2026-10-02 15:00:00', '2026-10-02 15:00:00', 0, '2026-10-02 15:00:00', '2026-10-02 15:00:00'),
(15, 'STOCK003.SH', '远景科技股份有限公司', '远景科技', 'a_share', 'I65', '软件和信息技术服务业', 'neutral', 'overweight', '手工调整', '调出', 'STOCK_DEMO_015', 106, '基础库', 'stock', 118, 'stock:fast-outbound', 'fastOutbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-02 16:00:00', '2026-10-02 16:00:00', NULL, 0, '2026-10-02 16:00:00', '2026-10-02 16:00:00'),
(16, 'STOCK001.SH', '晨曦林业股份有限公司', '晨曦林业', 'a_share', 'A02', '林业', 'buy', 'overweight', '手工调整', '调入', 'STOCK_DEMO_016', 109, '核心库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '00', '2', '研究员1', '独立股票调库演示：00', '演示研究意见', '2026-10-02 09:00:00', NULL, NULL, 0, '2026-10-02 09:00:00', '2026-10-02 09:00:00'),
(17, 'STOCK002.SZ', '海蓝渔业股份有限公司', '海蓝渔业', 'a_share', 'A04', '渔业', 'overweight', 'buy', '手工调整', '调入', 'STOCK_DEMO_017', 109, '核心库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '11', '5', '研究员4', '独立股票调库演示：11', '演示研究意见', '2026-10-02 10:00:00', NULL, NULL, 0, '2026-10-02 10:00:00', '2026-10-02 10:00:00'),
(18, 'STOCK004.SZ', '华信制造股份有限公司', '华信制造', 'a_share', 'C35', '专用设备制造业', 'underweight', 'neutral', '手工调整', '调入', 'STOCK_DEMO_018', 107, '重点库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '21', '5', '研究员4', '独立股票调库演示：21', '演示研究意见', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NULL, 0, '2026-10-02 11:00:00', '2026-10-02 11:00:00'),
(19, 'STOCK005.HK', '港湾医药控股有限公司', '港湾医药', 'hk_share', 'C27', '医药制造业', 'buy', 'neutral', '手工调整', '调入', 'STOCK_DEMO_019', 142, '港股核心库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '99', '2', '研究员1', '独立股票调库演示：99', '演示研究意见', '2026-10-02 12:00:00', '2026-10-02 12:00:00', NULL, 0, '2026-10-02 12:00:00', '2026-10-02 12:00:00'),
(20, 'STOCK006.HK', '星河科技控股有限公司', '星河科技', 'hk_share', 'I65', '软件和信息技术服务业', 'overweight', 'buy', '手工调整', '调入', 'STOCK_DEMO_020', 143, '港股重点库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '-1', '5', '研究员4', '独立股票调库演示：-1', '演示研究意见', '2026-10-03 13:00:00', NULL, NULL, 0, '2026-10-03 13:00:00', '2026-10-03 13:00:00'),
(21, 'STOCK007.SH', '青山食品股份有限公司', '青山食品', 'a_share', 'C14', '食品制造业', 'sell', 'underweight', '手工调整', '调出', 'STOCK_DEMO_021', 108, '限制类股票库', 'stock', 116, 'stock:normal-outbound', 'normalOutbound', '00', '2', '研究员1', '独立股票调库演示：00', '演示研究意见', '2026-10-03 14:00:00', NULL, NULL, 0, '2026-10-03 14:00:00', '2026-10-03 14:00:00'),
(22, 'STOCK010.SH', '安宁能源股份有限公司', '安宁能源', 'a_share', 'D44', '电力、热力生产和供应业', 'buy', 'buy', '手工调整', '调出', 'STOCK_DEMO_022', 109, '核心库', 'stock', 116, 'stock:normal-outbound', 'normalOutbound', '21', '5', '研究员4', '独立股票调库演示：21', '演示研究意见', '2026-10-03 15:00:00', '2026-10-03 15:00:00', NULL, 0, '2026-10-03 15:00:00', '2026-10-03 15:00:00'),
(23, 'STOCK009.HK', '海岳工业控股有限公司', '海岳工业', 'hk_share', 'C35', '专用设备制造业', 'neutral', 'buy', '手工调整', '调出', 'STOCK_DEMO_023', 121, '兴业先进制造产品库H股', 'stock_product', 116, 'stock:normal-outbound', 'normalOutbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-03 16:00:00', '2026-10-03 16:00:00', NULL, 0, '2026-10-03 16:00:00', '2026-10-03 16:00:00');

INSERT INTO `ip_adjust_step_stock`
(
    `id`
    ,`adjust_log_id`
    ,`adjust_batch_no`
    ,`flow_node_id`
    ,`node_code`
    ,`node_label`
    ,`node_type`
    ,`approval_strategy`
    ,`sort_order`
    ,`step_status`
    ,`handler_id`
    ,`handler_name`
    ,`process_action`
    ,`process_comment`
    ,`start_time`
    ,`process_time`
    ,`crte_time`
    ,`updt_time`
)
VALUES
(1, 1, 'STOCK_DEMO_001', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(2, 1, 'STOCK_DEMO_001', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(3, 1, 'STOCK_DEMO_001', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(4, 1, 'STOCK_DEMO_001', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(5, 1, 'STOCK_DEMO_001', 11506, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(6, 1, 'STOCK_DEMO_001', 11507, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(7, 2, 'STOCK_DEMO_002', 11701, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 11:00:00', '2026-10-01 11:00:00', NOW(), NOW()),
(8, 2, 'STOCK_DEMO_002', 11702, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-01 11:00:00', '2026-10-01 11:00:00', NOW(), NOW()),
(9, 2, 'STOCK_DEMO_002', 11703, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 11:00:00', '2026-10-01 11:00:00', NOW(), NOW()),
(10, 3, 'STOCK_DEMO_003', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 12:00:00', '2026-10-01 12:00:00', NOW(), NOW()),
(11, 3, 'STOCK_DEMO_003', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-01 12:00:00', '2026-10-01 12:00:00', NOW(), NOW()),
(12, 3, 'STOCK_DEMO_003', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-01 12:00:00', '2026-10-01 12:00:00', NOW(), NOW()),
(13, 3, 'STOCK_DEMO_003', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-01 12:00:00', '2026-10-01 12:00:00', NOW(), NOW()),
(14, 3, 'STOCK_DEMO_003', 11506, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-01 12:00:00', '2026-10-01 12:00:00', NOW(), NOW()),
(15, 3, 'STOCK_DEMO_003', 11507, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 12:00:00', '2026-10-01 12:00:00', NOW(), NOW()),
(16, 4, 'STOCK_DEMO_004', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 13:00:00', '2026-10-01 13:00:00', NOW(), NOW()),
(17, 4, 'STOCK_DEMO_004', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-01 13:00:00', '2026-10-01 13:00:00', NOW(), NOW()),
(18, 4, 'STOCK_DEMO_004', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-01 13:00:00', '2026-10-01 13:00:00', NOW(), NOW()),
(19, 4, 'STOCK_DEMO_004', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-01 13:00:00', '2026-10-01 13:00:00', NOW(), NOW()),
(20, 4, 'STOCK_DEMO_004', 11506, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-01 13:00:00', '2026-10-01 13:00:00', NOW(), NOW()),
(21, 4, 'STOCK_DEMO_004', 11507, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 13:00:00', '2026-10-01 13:00:00', NOW(), NOW()),
(22, 5, 'STOCK_DEMO_005', 11701, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 14:00:00', '2026-10-01 14:00:00', NOW(), NOW()),
(23, 5, 'STOCK_DEMO_005', 11702, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-01 14:00:00', '2026-10-01 14:00:00', NOW(), NOW()),
(24, 5, 'STOCK_DEMO_005', 11703, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 14:00:00', '2026-10-01 14:00:00', NOW(), NOW()),
(25, 6, 'STOCK_DEMO_006', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 15:00:00', '2026-10-01 15:00:00', NOW(), NOW()),
(26, 6, 'STOCK_DEMO_006', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-01 15:00:00', '2026-10-01 15:00:00', NOW(), NOW()),
(27, 6, 'STOCK_DEMO_006', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-01 15:00:00', '2026-10-01 15:00:00', NOW(), NOW()),
(28, 6, 'STOCK_DEMO_006', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-01 15:00:00', '2026-10-01 15:00:00', NOW(), NOW()),
(29, 6, 'STOCK_DEMO_006', 11506, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-01 15:00:00', '2026-10-01 15:00:00', NOW(), NOW()),
(30, 6, 'STOCK_DEMO_006', 11507, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 15:00:00', '2026-10-01 15:00:00', NOW(), NOW()),
(31, 7, 'STOCK_DEMO_007', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 16:00:00', '2026-10-01 16:00:00', NOW(), NOW()),
(32, 7, 'STOCK_DEMO_007', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-01 16:00:00', '2026-10-01 16:00:00', NOW(), NOW()),
(33, 7, 'STOCK_DEMO_007', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-01 16:00:00', '2026-10-01 16:00:00', NOW(), NOW()),
(34, 7, 'STOCK_DEMO_007', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-01 16:00:00', '2026-10-01 16:00:00', NOW(), NOW()),
(35, 7, 'STOCK_DEMO_007', 11506, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-01 16:00:00', '2026-10-01 16:00:00', NOW(), NOW()),
(36, 7, 'STOCK_DEMO_007', 11507, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 16:00:00', '2026-10-01 16:00:00', NOW(), NOW()),
(37, 8, 'STOCK_DEMO_008', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 09:00:00', '2026-10-01 09:00:00', NOW(), NOW()),
(38, 8, 'STOCK_DEMO_008', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-01 09:00:00', '2026-10-01 09:00:00', NOW(), NOW()),
(39, 8, 'STOCK_DEMO_008', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-01 09:00:00', '2026-10-01 09:00:00', NOW(), NOW()),
(40, 8, 'STOCK_DEMO_008', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-01 09:00:00', '2026-10-01 09:00:00', NOW(), NOW()),
(41, 8, 'STOCK_DEMO_008', 11506, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-01 09:00:00', '2026-10-01 09:00:00', NOW(), NOW()),
(42, 8, 'STOCK_DEMO_008', 11507, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 09:00:00', '2026-10-01 09:00:00', NOW(), NOW()),
(43, 9, 'STOCK_DEMO_009', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(44, 9, 'STOCK_DEMO_009', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(45, 9, 'STOCK_DEMO_009', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(46, 9, 'STOCK_DEMO_009', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(47, 9, 'STOCK_DEMO_009', 11506, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(48, 9, 'STOCK_DEMO_009', 11507, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-01 10:00:00', '2026-10-01 10:00:00', NOW(), NOW()),
(49, 10, 'STOCK_DEMO_010', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(50, 10, 'STOCK_DEMO_010', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(51, 10, 'STOCK_DEMO_010', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(52, 10, 'STOCK_DEMO_010', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(53, 10, 'STOCK_DEMO_010', 11506, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(54, 10, 'STOCK_DEMO_010', 11507, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(55, 11, 'STOCK_DEMO_011', 11701, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 12:00:00', '2026-10-02 12:00:00', NOW(), NOW()),
(56, 11, 'STOCK_DEMO_011', 11702, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-02 12:00:00', '2026-10-02 12:00:00', NOW(), NOW()),
(57, 11, 'STOCK_DEMO_011', 11703, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-02 12:00:00', '2026-10-02 12:00:00', NOW(), NOW()),
(58, 12, 'STOCK_DEMO_012', 11701, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 13:00:00', '2026-10-02 13:00:00', NOW(), NOW()),
(59, 12, 'STOCK_DEMO_012', 11702, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-02 13:00:00', '2026-10-02 13:00:00', NOW(), NOW()),
(60, 12, 'STOCK_DEMO_012', 11703, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-02 13:00:00', '2026-10-02 13:00:00', NOW(), NOW()),
(61, 13, 'STOCK_DEMO_013', 11701, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 14:00:00', '2026-10-02 14:00:00', NOW(), NOW()),
(62, 13, 'STOCK_DEMO_013', 11702, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-02 14:00:00', '2026-10-02 14:00:00', NOW(), NOW()),
(63, 13, 'STOCK_DEMO_013', 11703, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-02 14:00:00', '2026-10-02 14:00:00', NOW(), NOW()),
(64, 14, 'STOCK_DEMO_014', 11701, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 15:00:00', '2026-10-02 15:00:00', NOW(), NOW()),
(65, 14, 'STOCK_DEMO_014', 11702, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-02 15:00:00', '2026-10-02 15:00:00', NOW(), NOW()),
(66, 14, 'STOCK_DEMO_014', 11703, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-02 15:00:00', '2026-10-02 15:00:00', NOW(), NOW()),
(67, 15, 'STOCK_DEMO_015', 11801, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 16:00:00', '2026-10-02 16:00:00', NOW(), NOW()),
(68, 15, 'STOCK_DEMO_015', 11802, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-02 16:00:00', '2026-10-02 16:00:00', NOW(), NOW()),
(69, 15, 'STOCK_DEMO_015', 11803, 'n103', '结束', 'end', NULL, 3, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-02 16:00:00', '2026-10-02 16:00:00', NOW(), NOW()),
(70, 16, 'STOCK_DEMO_016', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 09:00:00', '2026-10-02 09:00:00', NOW(), NOW()),
(71, 16, 'STOCK_DEMO_016', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-02 09:00:00', '2026-10-02 09:00:00', NOW(), NOW()),
(72, 16, 'STOCK_DEMO_016', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, NULL, '2026-10-02 09:00:00', NULL, NOW(), NOW()),
(73, 17, 'STOCK_DEMO_017', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 10:00:00', '2026-10-02 10:00:00', NOW(), NOW()),
(74, 17, 'STOCK_DEMO_017', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-02 10:00:00', '2026-10-02 10:00:00', NOW(), NOW()),
(75, 17, 'STOCK_DEMO_017', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'reject', '3', '研究员2', 'reject', 'Demo：研究员B复核', '2026-10-02 10:00:00', '2026-10-02 10:00:00', NOW(), NOW()),
(76, 17, 'STOCK_DEMO_017', 11504, 'n104', '研究员A修改', 'approval', 'initiator', 4, 'pending', '5', '研究员4', NULL, NULL, '2026-10-02 10:00:00', NULL, NOW(), NOW()),
(77, 18, 'STOCK_DEMO_018', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(78, 18, 'STOCK_DEMO_018', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(79, 18, 'STOCK_DEMO_018', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(80, 18, 'STOCK_DEMO_018', 11505, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'reject', '4', '研究员3', 'reject', 'Demo：研究总监审批', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(81, 18, 'STOCK_DEMO_018', 11507, 'n107', '结束', 'end', NULL, 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-02 11:00:00', '2026-10-02 11:00:00', NOW(), NOW()),
(82, 19, 'STOCK_DEMO_019', 11501, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-02 12:00:00', '2026-10-02 12:00:00', NOW(), NOW()),
(83, 19, 'STOCK_DEMO_019', 11502, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-02 12:00:00', '2026-10-02 12:00:00', NOW(), NOW()),
(84, 19, 'STOCK_DEMO_019', 11503, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'canceled', '3', '研究员2', 'skipped', 'Demo：研究员B复核', '2026-10-02 12:00:00', '2026-10-02 12:00:00', NOW(), NOW()),
(85, 21, 'STOCK_DEMO_021', 11601, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-03 14:00:00', '2026-10-03 14:00:00', NOW(), NOW()),
(86, 21, 'STOCK_DEMO_021', 11602, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-03 14:00:00', '2026-10-03 14:00:00', NOW(), NOW()),
(87, 21, 'STOCK_DEMO_021', 11603, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'pending', '3', '研究员2', NULL, NULL, '2026-10-03 14:00:00', NULL, NOW(), NOW()),
(88, 22, 'STOCK_DEMO_022', 11601, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-03 15:00:00', '2026-10-03 15:00:00', NOW(), NOW()),
(89, 22, 'STOCK_DEMO_022', 11602, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '5', '研究员4', 'submit', 'Demo：研究员A发起', '2026-10-03 15:00:00', '2026-10-03 15:00:00', NOW(), NOW()),
(90, 22, 'STOCK_DEMO_022', 11603, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-03 15:00:00', '2026-10-03 15:00:00', NOW(), NOW()),
(91, 22, 'STOCK_DEMO_022', 11605, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'reject', '4', '研究员3', 'reject', 'Demo：研究总监审批', '2026-10-03 15:00:00', '2026-10-03 15:00:00', NOW(), NOW()),
(92, 22, 'STOCK_DEMO_022', 11607, 'n107', '结束', 'end', NULL, 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-03 15:00:00', '2026-10-03 15:00:00', NOW(), NOW()),
(93, 23, 'STOCK_DEMO_023', 11601, 'n101', '开始', 'start', NULL, 1, 'auto_process', NULL, NULL, 'auto_process', 'Demo：开始', '2026-10-03 16:00:00', '2026-10-03 16:00:00', NOW(), NOW()),
(94, 23, 'STOCK_DEMO_023', 11602, 'n102', '研究员A发起', 'approval', 'initiator', 2, 'submit', '2', '研究员1', 'submit', 'Demo：研究员A发起', '2026-10-03 16:00:00', '2026-10-03 16:00:00', NOW(), NOW()),
(95, 23, 'STOCK_DEMO_023', 11603, 'n103', '研究员B复核', 'approval', 'preempt', 3, 'approve', '3', '研究员2', 'approve', 'Demo：研究员B复核', '2026-10-03 16:00:00', '2026-10-03 16:00:00', NOW(), NOW()),
(96, 23, 'STOCK_DEMO_023', 11605, 'n105', '研究总监审批', 'approval', 'preempt', 4, 'approve', '4', '研究员3', 'approve', 'Demo：研究总监审批', '2026-10-03 16:00:00', '2026-10-03 16:00:00', NOW(), NOW()),
(97, 23, 'STOCK_DEMO_023', 11606, 'n106', 'O32自动审批', 'approval', 'o32', 5, 'auto_process', NULL, NULL, 'auto_process', 'Demo：O32自动审批', '2026-10-03 16:00:00', '2026-10-03 16:00:00', NOW(), NOW()),
(98, 23, 'STOCK_DEMO_023', 11607, 'n107', '结束', 'end', NULL, 6, 'auto_process', NULL, NULL, 'auto_process', 'Demo：结束', '2026-10-03 16:00:00', '2026-10-03 16:00:00', NOW(), NOW());

INSERT INTO `ip_pool_status_stock`
(
    `id`
    ,`stock_code`
    ,`stock_name`
    ,`stock_short_name`
    ,`security_type`
    ,`industry_code`
    ,`industry_name`
    ,`latest_rating`
    ,`previous_rating`
    ,`adjust_type`
    ,`adjust_mode`
    ,`adjust_batch_no`
    ,`adjust_log_id`
    ,`target_pool_id`
    ,`target_pool_name`
    ,`pool_type`
    ,`flow_id`
    ,`flow_key`
    ,`flow_type`
    ,`audit_status`
    ,`adjuster_id`
    ,`adjuster_name`
    ,`adjust_reason`
    ,`adjust_advice`
    ,`submit_time`
    ,`audit_time`
    ,`entry_time`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
)
VALUES
(1, 'STOCK001.SH', '晨曦林业股份有限公司', '晨曦林业', 'a_share', 'A02', '林业', 'buy', 'overweight', '手工调整', '调入', 'STOCK_DEMO_001', 1, 106, '基础库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00', 0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(2, 'STOCK002.SZ', '海蓝渔业股份有限公司', '海蓝渔业', 'a_share', 'A04', '渔业', 'overweight', 'buy', '手工调整', '调入', 'STOCK_DEMO_002', 2, 106, '基础库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-01 11:00:00', '2026-10-01 11:00:00', '2026-10-01 11:00:00', 0, '2026-10-01 11:00:00', '2026-10-01 11:00:00'),
(3, 'STOCK003.SH', '远景科技股份有限公司', '远景科技', 'a_share', 'I65', '软件和信息技术服务业', 'neutral', 'overweight', '手工调整', '调入', 'STOCK_DEMO_003', 3, 107, '重点库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 12:00:00', '2026-10-01 12:00:00', '2026-10-01 12:00:00', 0, '2026-10-01 12:00:00', '2026-10-01 12:00:00'),
(4, 'STOCK004.SZ', '华信制造股份有限公司', '华信制造', 'a_share', 'C35', '专用设备制造业', 'underweight', 'neutral', '手工调整', '调入', 'STOCK_DEMO_004', 4, 109, '核心库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-01 13:00:00', '2026-10-01 13:00:00', '2026-10-01 13:00:00', 0, '2026-10-01 13:00:00', '2026-10-01 13:00:00'),
(5, 'STOCK005.HK', '港湾医药控股有限公司', '港湾医药', 'hk_share', 'C27', '医药制造业', 'buy', 'neutral', '手工调整', '调入', 'STOCK_DEMO_005', 5, 141, '港股基础库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 14:00:00', '2026-10-01 14:00:00', '2026-10-01 14:00:00', 0, '2026-10-01 14:00:00', '2026-10-01 14:00:00'),
(6, 'STOCK006.HK', '星河科技控股有限公司', '星河科技', 'hk_share', 'I65', '软件和信息技术服务业', 'overweight', 'buy', '手工调整', '调入', 'STOCK_DEMO_006', 6, 120, '兴业科技创新产品库H股', 'stock_product', 115, 'stock:normal-inbound', 'normalInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-01 15:00:00', '2026-10-01 15:00:00', '2026-10-01 15:00:00', 0, '2026-10-01 15:00:00', '2026-10-01 15:00:00'),
(7, 'STOCK007.SH', '青山食品股份有限公司', '青山食品', 'a_share', 'C14', '食品制造业', 'sell', 'underweight', '手工调整', '调入', 'STOCK_DEMO_007', 7, 108, '限制类股票库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-01 16:00:00', '2026-10-01 16:00:00', '2026-10-01 16:00:00', 0, '2026-10-01 16:00:00', '2026-10-01 16:00:00'),
(8, 'STOCK008.SZ', '丰禾农业股份有限公司', '丰禾农业', 'a_share', 'A01', '农业', 'neutral', NULL, '手工调整', '调入', 'STOCK_DEMO_008', 8, 106, '基础库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-01 09:00:00', '2026-10-01 09:00:00', '2026-10-01 09:00:00', 0, '2026-10-01 09:00:00', '2026-10-01 09:00:00'),
(9, 'STOCK010.SH', '安宁能源股份有限公司', '安宁能源', 'a_share', 'D44', '电力、热力生产和供应业', 'buy', 'buy', '手工调整', '调入', 'STOCK_DEMO_010', 10, 109, '核心库', 'stock', 115, 'stock:normal-inbound', 'normalInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-02 11:00:00', '2026-10-02 11:00:00', '2026-10-02 11:00:00', 0, '2026-10-02 11:00:00', '2026-10-02 11:00:00'),
(10, 'STOCK001.SH', '晨曦林业股份有限公司', '晨曦林业', 'a_share', 'A02', '林业', 'buy', 'overweight', '手工调整', '调入', 'STOCK_DEMO_011', 11, 107, '重点库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-02 12:00:00', '2026-10-02 12:00:00', '2026-10-02 12:00:00', 0, '2026-10-02 12:00:00', '2026-10-02 12:00:00'),
(11, 'STOCK013.SZ', '零成交工业股份有限公司', '零成交工业', 'a_share', 'C35', '专用设备制造业', 'neutral', NULL, '手工调整', '调入', 'STOCK_DEMO_013', 13, 106, '基础库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '2', '研究员1', '独立股票调库演示：20', '演示研究意见', '2026-10-02 14:00:00', '2026-10-02 14:00:00', '2026-10-02 14:00:00', 0, '2026-10-02 14:00:00', '2026-10-02 14:00:00'),
(12, 'STOCK014.SH', '缺行情林业股份有限公司', '缺行情林业', 'a_share', 'A02', '林业', NULL, NULL, '手工调整', '调入', 'STOCK_DEMO_014', 14, 107, '重点库', 'stock', 117, 'stock:fast-inbound', 'fastInbound', '20', '5', '研究员4', '独立股票调库演示：20', '演示研究意见', '2026-10-02 15:00:00', '2026-10-02 15:00:00', '2026-10-02 15:00:00', 0, '2026-10-02 15:00:00', '2026-10-02 15:00:00');

-- 自选仅幂等追加当前股票，不清空或覆盖现有证券/基金收藏。
INSERT INTO `my_security_pool`
(`security_code`, `security_type`, `market`, `user_id`, `status`, `remark`, `create_time`, `update_time`)
SELECT 'STOCK001.SH', 'a_share', 'SSE', '2', 'use', '股票自选演示', NOW(), NOW()
WHERE NOT EXISTS
(
    SELECT 1 FROM `my_security_pool`
    WHERE `security_code` = 'STOCK001.SH' AND `user_id` = '2' AND `status` = 'use'
);

-- 自选仅幂等追加当前股票，不清空或覆盖现有证券/基金收藏。
INSERT INTO `my_security_pool`
(`security_code`, `security_type`, `market`, `user_id`, `status`, `remark`, `create_time`, `update_time`)
SELECT 'STOCK003.SH', 'a_share', 'SSE', '2', 'use', '股票自选演示', NOW(), NOW()
WHERE NOT EXISTS
(
    SELECT 1 FROM `my_security_pool`
    WHERE `security_code` = 'STOCK003.SH' AND `user_id` = '2' AND `status` = 'use'
);

-- 自选仅幂等追加当前股票，不清空或覆盖现有证券/基金收藏。
INSERT INTO `my_security_pool`
(`security_code`, `security_type`, `market`, `user_id`, `status`, `remark`, `create_time`, `update_time`)
SELECT 'STOCK005.HK', 'hk_share', 'HKEX', '2', 'use', '股票自选演示', NOW(), NOW()
WHERE NOT EXISTS
(
    SELECT 1 FROM `my_security_pool`
    WHERE `security_code` = 'STOCK005.HK' AND `user_id` = '2' AND `status` = 'use'
);

-- 自选仅幂等追加当前股票，不清空或覆盖现有证券/基金收藏。
INSERT INTO `my_security_pool`
(`security_code`, `security_type`, `market`, `user_id`, `status`, `remark`, `create_time`, `update_time`)
SELECT 'STOCK002.SZ', 'a_share', 'SZSE', '5', 'use', '股票自选演示', NOW(), NOW()
WHERE NOT EXISTS
(
    SELECT 1 FROM `my_security_pool`
    WHERE `security_code` = 'STOCK002.SZ' AND `user_id` = '5' AND `status` = 'use'
);

-- 自选仅幂等追加当前股票，不清空或覆盖现有证券/基金收藏。
INSERT INTO `my_security_pool`
(`security_code`, `security_type`, `market`, `user_id`, `status`, `remark`, `create_time`, `update_time`)
SELECT 'STOCK006.HK', 'hk_share', 'HKEX', '5', 'use', '股票自选演示', NOW(), NOW()
WHERE NOT EXISTS
(
    SELECT 1 FROM `my_security_pool`
    WHERE `security_code` = 'STOCK006.HK' AND `user_id` = '5' AND `status` = 'use'
);
