-- ============================================================
-- znty-rrs 基金调库运行表演示数据
-- MySQL version: 8.0.33
-- 说明：覆盖待审批、审批通过、驳回待修改、审批驳回、撤回和已通过调出
-- ============================================================

USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `ip_adjust_step_fund`;
TRUNCATE TABLE `ip_pool_status_fund`;
TRUNCATE TABLE `ip_adjust_log_fund`;

INSERT INTO `ip_adjust_log_fund` (
    `id`
    ,`fund_code`
    ,`fund_name`
    ,`fund_short_name`
    ,`security_type`
    ,`fund_score`
    ,`fund_investment_type`
    ,`need_risk_leader_approval`
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
) VALUES
(910001, 'FUND001.SH', '沪深300交易型开放式指数基金', '沪深300ETF', 'etf_fund', 8.2500, 'stock', 1, '手工调整', '调入', 'FUND-20260922-001', 145, '母公司基础基金池', 'fund', 121, 'fund:normal-inbound', 'normalInbound', '00', '2', '研究员1', '纳入基础基金池观察与管理', NULL, NOW(), NULL, NULL, 0, NOW(), NOW()),
(910002, 'FUND002.SZ', '成长主题上市开放式基金', '成长LOF', 'lof_fund', 8.8000, 'equity_hybrid', 0, '手工调整', '调入', 'FUND-20260922-002', 146, '母公司核心基金池', 'fund', 121, 'fund:normal-inbound', 'normalInbound', '20', '2', '研究员1', '基金长期表现稳定，申请调入核心基金池', '审批通过', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0, NOW(), NOW()),
(910003, 'FUND003.OF', '稳健收益开放式证券投资基金', '稳健收益', 'open_fund', 7.5000, 'money_market', NULL, '手工调整', '调入', 'FUND-20260922-003', 149, '兴易多策略1号基础基金库', 'fund', 121, 'fund:normal-inbound', 'normalInbound', '11', '5', '研究员4', '申请纳入产品基础基金库', '材料不完整，请补充后重新提交', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 0, NOW(), NOW()),
(910004, 'FUND004.HK', '香港市场交易型开放式基金', '香港ETF', 'qdii_fund', 6.9000, 'other', 1, '手工调整', '调入', 'FUND-20260922-004', 151, '沪深300ETF发起式联接基础基金库', 'fund', 121, 'fund:normal-inbound', 'normalInbound', '21', '5', '研究员4', '申请纳入产品基础基金库', '不符合当前产品配置要求，审批驳回', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, 0, NOW(), NOW()),
(910005, 'FUND001.SH', '沪深300交易型开放式指数基金', '沪深300ETF', 'etf_fund', 8.2500, 'stock', NULL, '手工调整', '调入', 'FUND-20260922-005', 153, '中证500ETF发起式联接基础基金库', 'fund', 121, 'fund:normal-inbound', 'normalInbound', '99', '2', '研究员1', '申请纳入产品基础基金库', '发起人主动撤回', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 0, NOW(), NOW()),
(910006, 'FUND002.SZ', '成长主题上市开放式基金', '成长LOF', 'lof_fund', 8.8000, 'equity_hybrid', 0, '手工调整', '调出', 'FUND-20260922-006', 145, '母公司基础基金池', 'fund', 122, 'fund:normal-outbound', 'normalOutbound', '20', '2', '研究员1', '投资策略调整，申请调出基础基金池', '审批通过并完成调出', DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NULL, 0, NOW(), NOW());

INSERT INTO `ip_pool_status_fund` (
    `id`
    ,`fund_code`
    ,`fund_name`
    ,`fund_short_name`
    ,`security_type`
    ,`fund_score`
    ,`fund_investment_type`
    ,`need_risk_leader_approval`
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
) VALUES
(911001, 'FUND002.SZ', '成长主题上市开放式基金', '成长LOF', 'lof_fund', 8.8000, 'equity_hybrid', 0, '手工调整', '调入', 'FUND-20260922-002', 910002, 146, '母公司核心基金池', 'fund', 121, 'fund:normal-inbound', 'normalInbound', '20', '2', '研究员1', '基金长期表现稳定，申请调入核心基金池', '审批通过', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0, NOW(), NOW());

INSERT INTO `ip_adjust_step_fund` (
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
) VALUES
(912001, 910001, 'FUND-20260922-001', 12102, 'n102', '研究员A发起', 'approval', 'initiator', 1, 'submit', '2', '研究员1', 'submit', '提交基金调库申请', NOW(), NOW(), NOW(), NOW()),
(912002, 910001, 'FUND-20260922-001', 12103, 'n103', '研究员B复核', 'approval', 'preempt', 2, 'pending', '5', '研究员4', NULL, NULL, NOW(), NULL, NOW(), NOW()),
(912003, 910002, 'FUND-20260922-002', 12102, 'n102', '研究员A发起', 'approval', 'initiator', 1, 'submit', '2', '研究员1', 'submit', '提交基金调库申请', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW()),
(912004, 910002, 'FUND-20260922-002', 12103, 'n103', '研究员B复核', 'approval', 'preempt', 2, 'approve', '5', '研究员4', 'approve', '复核通过', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
(912005, 910002, 'FUND-20260922-002', 12105, 'n105', '研究总监审批', 'approval', 'preempt', 3, 'approve', '1', '管理员', 'approve', '审批通过', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
(912006, 910003, 'FUND-20260922-003', 12102, 'n102', '研究员A发起', 'approval', 'initiator', 1, 'submit', '5', '研究员4', 'submit', '提交基金调库申请', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
(912007, 910003, 'FUND-20260922-003', 12103, 'n103', '研究员B复核', 'approval', 'preempt', 2, 'reject', '1', '管理员', 'reject', '材料不完整，请补充', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
(912008, 910003, 'FUND-20260922-003', 12104, 'n104', '研究员A修改', 'approval', 'initiator', 3, 'pending', '5', '研究员4', NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NOW(), NOW()),
(912009, 910004, 'FUND-20260922-004', 12102, 'n102', '研究员A发起', 'approval', 'initiator', 1, 'submit', '5', '研究员4', 'submit', '提交基金调库申请', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW()),
(912010, 910004, 'FUND-20260922-004', 12105, 'n105', '研究总监审批', 'approval', 'preempt', 2, 'reject', '1', '管理员', 'reject', '不符合当前产品配置要求', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
(912011, 910005, 'FUND-20260922-005', 12102, 'n102', '研究员A发起', 'approval', 'initiator', 1, 'submit', '2', '研究员1', 'submit', '提交基金调库申请', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
(912012, 910005, 'FUND-20260922-005', 12103, 'n103', '研究员B复核', 'approval', 'preempt', 2, 'canceled', '5', '研究员4', 'skipped', '发起人撤回，待办取消', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
(912013, 910006, 'FUND-20260922-006', 12202, 'n102', '研究员A发起', 'approval', 'initiator', 1, 'submit', '2', '研究员1', 'submit', '提交基金调出申请', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW()),
(912014, 910006, 'FUND-20260922-006', 12203, 'n103', '研究员B复核', 'approval', 'preempt', 2, 'approve', '5', '研究员4', 'approve', '复核通过', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
(912015, 910006, 'FUND-20260922-006', 12205, 'n105', '研究总监审批', 'approval', 'preempt', 3, 'approve', '1', '管理员', 'approve', '审批通过并完成调出', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW(), NOW());
