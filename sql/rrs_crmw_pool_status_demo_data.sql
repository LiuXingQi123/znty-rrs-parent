-- ============================================================
-- CRMW 池状态演示数据
-- MySQL version: 8.0.33
-- 说明：CRMW 在池 1 条；凭证 032400101.IB + 标的 102480041.IB（同属 C10001）
--       adjust_log_id=7 对齐 rrs_security_pool_adjust_demo_data.sql
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `ip_pool_status_crmw`;

INSERT INTO `ip_pool_status_crmw` (`isin_code`, `security_code`, `security_short_name`, `security_type`, `crmw_name`, `crmw_isin_code`, `crmw_scode`, `crmw_mktcode`, `crmw_stype`, `adjust_type`, `adjust_mode`, `adjust_batch_no`, `adjust_log_id`, `target_pool_id`, `target_pool_name`, `pool_type`, `flow_id`, `flow_key`, `flow_type`, `audit_status`, `adjuster_id`, `adjuster_name`, `adjust_reason`, `submit_time`, `audit_time`, `entry_time`, `is_deleted`, `crte_time`, `updt_time`) VALUES
('CNDEMO000384', NULL, '24交投MTN', 'mtn', '某CRMW凭证A', 'CNDEMO000061', '032400101.IB', 'CIBM', 'crmw', '手工调整', '调入', 'CRMW20260601001', 7, 18, 'CRMW库', 'crmw', 112, 'bond:no-approval', 'normalInbound', '20', '1', '管理员', 'CRMW调入CRMW库', '2026-06-01 09:00:00', '2026-06-01 14:00:00', '2026-06-01 14:00:00', 0, NOW(), NOW());


-- 按提交时主档生成 Demo 信息快照，验证详情读取及证券市场代码留痕。
TRUNCATE TABLE `ip_adjust_security_snapshot_crmw`;
INSERT INTO `ip_adjust_security_snapshot_crmw` (
    `adjust_log_id`
    ,`submitter_id`
    ,`submit_time`
    ,`crmw_name`
    ,`crmw_isin_code`
    ,`crmw_scode`
    ,`crmw_mktcode`
    ,`crmw_stype`
    ,`isin_code`
    ,`full_name`
    ,`short_name`
    ,`issuer`
    ,`issuer_code`
    ,`wind_code_nib`
    ,`wind_code_sh`
    ,`wind_code_sz`
    ,`wind_code_bj`
    ,`issue_amountplan`
    ,`coupon_rate`
    ,`date_inright_exists`
    ,`carry_date`
    ,`maturity_date`
    ,`info_pledge_ratio`
    ,`rating_bond_agency`
    ,`rating_bond`
    ,`rating_bondissuer`
    ,`rating_outlook`
    ,`guarantor`
    ,`guarantor_id`
    ,`inner_issuer_rating`
    ,`security_type`
    ,`sec_typename`
    ,`date_call_exists`
    ,`inner_guarantor_rating`
    ,`date_exists`
    ,`date_exists_str`
    ,`wind_code_nbc`
    ,`term_year`
    ,`term_day`
    ,`comp_type`
    ,`date_repurchase_exists`
    ,`abs_flag`
    ,`guarant_flag`
    ,`guarant_type`
    ,`rating_bondissuer_agency`
    ,`is_deleted`
    ,`crte_time`
    ,`updt_time`
)
SELECT
    l.id
    ,l.adjuster_id
    ,l.submit_time
    ,l.crmw_name
    ,l.crmw_isin_code
    ,l.crmw_scode
    ,l.crmw_mktcode
    ,l.crmw_stype
    ,s.isin_code
    ,s.full_name
    ,s.short_name
    ,s.issuer
    ,s.issuer_code
    ,s.wind_code_nib
    ,s.wind_code_sh
    ,s.wind_code_sz
    ,s.wind_code_bj
    ,s.issue_amountplan
    ,s.coupon_rate
    ,s.date_inright_exists
    ,s.carry_date
    ,s.maturity_date
    ,s.info_pledge_ratio
    ,s.rating_bond_agency
    ,s.rating_bond
    ,s.rating_bondissuer
    ,s.rating_outlook
    ,s.guarantor
    ,s.guarantor_id
    ,s.inner_issuer_rating
    ,s.security_type
    ,s.sec_typename
    ,s.date_call_exists
    ,s.inner_guarantor_rating
    ,s.date_exists
    ,s.date_exists_str
    ,s.wind_code_nbc
    ,s.term_year
    ,s.term_day
    ,s.comp_type
    ,s.date_repurchase_exists
    ,s.abs_flag
    ,s.guarant_flag
    ,s.guarant_type
    ,s.rating_bondissuer_agency
    ,0
    ,l.submit_time
    ,l.submit_time
FROM `ip_adjust_log` l
INNER JOIN `rrs_securityinfo` s ON s.isin_code = l.isin_code
WHERE l.pool_type = 'crmw';
