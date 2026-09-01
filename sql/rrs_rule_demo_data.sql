-- ============================================================
-- znty-rrs 规则管理库 - 演示数据
-- 先 TRUNCATE 清空，再按 checkAdjust 真实调用点灌数
-- 返回约定：通过=Java null；其余中文=失败/警告原文
-- 空证券代码/空调库项先返回前置失败，禁止空跑直接通过
-- ============================================================

CREATE DATABASE IF NOT EXISTS `znty_rrs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

TRUNCATE TABLE `rule_test_run_log`;
TRUNCATE TABLE `rule_test_run`;
TRUNCATE TABLE `rule_test_case_param`;
TRUNCATE TABLE `rule_test_case`;
TRUNCATE TABLE `rule_param_option`;
TRUNCATE TABLE `rule_param`;
TRUNCATE TABLE `rule_definition`;
TRUNCATE TABLE `rule_preset_option_item`;
TRUNCATE TABLE `rule_preset_option_set`;
TRUNCATE TABLE `rule_category`;

INSERT INTO `rule_category` (id, category_code, category_name, sort_no, enabled, crte_time, updt_time) VALUES
(1,'adjust_in','调入通用校验',10,1,NOW(),NOW()),
(2,'adjust_out','调出通用校验',20,1,NOW(),NOW()),
(3,'bond','债券特有校验',30,1,NOW(),NOW()),
(4,'stock','股票特有校验',40,1,NOW(),NOW()),
(5,'fund','基金特有校验',50,1,NOW(),NOW()),
(6,'grade_matrix','信用债分级矩阵',60,1,NOW(),NOW()),
(7,'flow_match','流程匹配',70,1,NOW(),NOW()),
(8,'submit','提交校验',80,1,NOW(),NOW()),
(9,'auto_adjust','自动调库',90,1,NOW(),NOW()),
(10,'other','其他',99,1,NOW(),NOW());

INSERT INTO `rule_preset_option_set` (id, set_name, sort_no, enabled, crte_time, updt_time) VALUES
(1,'是否/布尔',10,1,NOW(),NOW()),
(2,'主体内评分档',20,1,NOW(),NOW()),
(3,'证券类型',30,1,NOW(),NOW()),
(4,'信用债分级库档位',40,1,NOW(),NOW()),
(5,'特殊债类型',50,1,NOW(),NOW()),
(6,'调整方向',60,1,NOW(),NOW()),
(7,'审核状态',70,1,NOW(),NOW()),
(8,'报告限制',80,1,NOW(),NOW()),
(9,'品种大类',90,1,NOW(),NOW()),
(10,'交易场所',100,1,NOW(),NOW());

INSERT INTO `rule_preset_option_item` (set_id, option_value, option_label, sort_no, crte_time, updt_time) VALUES
(1,'是','是',1,NOW(),NOW()),(1,'否','否',2,NOW(),NOW()),
(2,'1','1',1,NOW(),NOW()),(2,'2+','2+',2,NOW(),NOW()),(2,'2','2',3,NOW(),NOW()),(2,'2-','2-',4,NOW(),NOW()),
(2,'3+','3+',5,NOW(),NOW()),(2,'3','3',6,NOW(),NOW()),(2,'3-','3-',7,NOW(),NOW()),(2,'4','4',8,NOW(),NOW()),
(3,'abs','资产支持证券',1,NOW(),NOW()),(3,'convertible_bond','可转债',2,NOW(),NOW()),
(3,'exchangeable_bond','可交换公司债券',3,NOW(),NOW()),(3,'detachable_convertible_bond','可分离转债存债',4,NOW(),NOW()),
(3,'crmw','信用风险缓释凭证',5,NOW(),NOW()),(3,'corporate_bond','企业债',6,NOW(),NOW()),(3,'mtn','中期票据',7,NOW(),NOW()),
(4,'1','一级库',1,NOW(),NOW()),(4,'2','二级库',2,NOW(),NOW()),(4,'3','三级库',3,NOW(),NOW()),
(4,'4','四级库',4,NOW(),NOW()),(4,'5','五级库',5,NOW(),NOW()),
(5,'abs','ABS',1,NOW(),NOW()),(5,'private','私募',2,NOW(),NOW()),(5,'subordinated','次级',3,NOW(),NOW()),
(5,'perpetual','永续',4,NOW(),NOW()),(5,'guaranteed','担保',5,NOW(),NOW()),
(6,'调入','调入',1,NOW(),NOW()),(6,'调出','调出',2,NOW(),NOW()),
(7,'00','流程中',1,NOW(),NOW()),(7,'20','审批通过',2,NOW(),NOW()),(7,'21','审批驳回',3,NOW(),NOW()),
(8,'none','不限制',1,NOW(),NOW()),(8,'any','任意研究报告',2,NOW(),NOW()),(8,'internal','内部研究报告',3,NOW(),NOW()),
(9,'bond','债券',1,NOW(),NOW()),(9,'stock','股票',2,NOW(),NOW()),(9,'fund','基金',3,NOW(),NOW()),(9,'company','主体',4,NOW(),NOW()),
(10,'SSE','上交所',1,NOW(),NOW()),(10,'SZSE','深交所',2,NOW(),NOW()),(10,'CIBM','银行间',3,NOW(),NOW()),(10,'BSE','北交所',4,NOW(),NOW());

INSERT INTO `rule_definition` (id, rule_name, description, category_code, script, status, deleted_flag, crte_time, updt_time) VALUES
(1,'调入-池锁定','[checkCommonIn] inCheckPoolLocked。目标池 lock_flag=1 禁止调入。空证券/空调库项与 checkAdjust 前置一致，不返回通过。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((lockFlag == "是" || lockFlag == "1" || lockFlag == 1)) {
    return "目标投资池已锁定";
}
return "通过";
','active',0,NOW(),NOW()),
(2,'调入-投资品种','[checkCommonIn] inCheckVariety。池 variety_codes JSON 须包含带引号的品种大类；空配置不限制。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (varietyCodes == null || varietyCodes == "" || varietyCodes == "[]") {
    return "通过";
}
if (categoryType == null || categoryType == "") {
    return "证券不在本池投资品种范围内";
}
token = "\\"" + categoryType + "\\"";
if (varietyCodes.indexOf(token) >= 0) {
    return "通过";
}
return "证券不在本池投资品种范围内";
','active',0,NOW(),NOW()),
(3,'调入-投资市场','[checkCommonIn] inCheckMarket。池 market_codes JSON 含带引号的市场编码；空或空数组不限制。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (marketCodes == null || marketCodes == "" || marketCodes == "[]") {
    return "通过";
}
if (securityMarkets == null || securityMarkets == "") {
    return "证券不在本池投资市场范围内";
}
token = "\\"" + securityMarkets + "\\"";
if (marketCodes.indexOf(token) >= 0) {
    return "通过";
}
return "证券不在本池投资市场范围内";
','active',0,NOW(),NOW()),
(4,'调入-进行中流程','[checkCommonIn/checkCommonOut] preCheckPendingProcess。存在 pending 步骤则禁止再次调库。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(hasPendingProcess == "是" || hasPendingProcess == "1" || hasPendingProcess == 1)) {
    return "通过";
}
if (pendingNodeLabel != null && pendingNodeLabel != "") {
    return "证券存在进行中的调库流程（当前节点：" + pendingNodeLabel + "）";
}
return "证券存在进行中的调库流程";
','active',0,NOW(),NOW()),
(5,'调入-已在目标池','[checkCommonIn] inCheckSecurityAlreadyInPool。audit_status=20 的有效在池记录视为已在池。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((alreadyInTargetPool == "是" || alreadyInTargetPool == "1" || alreadyInTargetPool == 1)) {
    return "证券已在目标投资池中";
}
return "通过";
','active',0,NOW(),NOW()),
(6,'调入-持仓上限','[checkCommonIn] inCheckPoolCapacity。maxCapacity 空或 0 不限制。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (maxCapacity == null || maxCapacity == "" || maxCapacity <= 0) {
    return "通过";
}
if (currentCount >= maxCapacity) {
    return "目标投资池已达持仓上限（" + maxCapacity + "）";
}
return "通过";
','active',0,NOW(),NOW()),
(7,'调入-来源池限制','[checkCommonIn] inCheckSourcePool。配置了 source 时，须已在来源池或本批同时调入来源池。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(hasSourceLimit == "是" || hasSourceLimit == "1" || hasSourceLimit == 1)) {
    return "通过";
}
if ((inSourcePool == "是" || inSourcePool == "1" || inSourcePool == 1)) {
    return "通过";
}
return "目标池配置了来源池限制，证券须先在以下池中：" + sourcePoolNames;
','active',0,NOW(),NOW()),
(8,'调入-限制池','[checkCommonIn] inCheckRestrictPool / checkBlockedByPools。文案：证券当前在调入限制池中：','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((inRestrictPool == "是" || inRestrictPool == "1" || inRestrictPool == 1)) {
    return "证券当前在调入限制池中：" + restrictPoolNames;
}
return "通过";
','active',0,NOW(),NOW()),
(9,'调入-互斥冲突','[checkCommonIn] inCheckMutexConflict。本批同时勾选 in_mutex 互斥池则双方失败。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((hasMutexConflict == "是" || hasMutexConflict == "1" || hasMutexConflict == 1)) {
    return "与以下互斥池不可同时调入：" + mutexPoolNames;
}
return "通过";
','active',0,NOW(),NOW()),
(10,'调入-弹性禁投','[checkCommonIn] inCheckElasticPool。in_soft_restrict 仅警告不阻断；返回文案与 Java 相同，不加警告前缀。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((inSoftRestrictPool == "是" || inSoftRestrictPool == "1" || inSoftRestrictPool == 1)) {
    return "证券当前在调入弹性禁投池中：" + softRestrictPoolNames;
}
return "通过";
','active',0,NOW(),NOW()),
(11,'调入-全局禁止池','[checkCommonIn] inCheckForbiddenPool。证券在 pool_type=forbidden 且审批通过的池中，不能调入其他池。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((inForbiddenPool == "是" || inForbiddenPool == "1" || inForbiddenPool == 1)) {
    return "证券当前在禁止池中";
}
return "通过";
','active',0,NOW(),NOW()),
(12,'调入-开放日','[checkCommonIn] inCheckOpenDay。open_day_adjust=1 时当日须落在 ip_pool_open_day 区间。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(openDayAdjust == "是" || openDayAdjust == "1" || openDayAdjust == 1)) {
    return "通过";
}
if ((inOpenDay == "是" || inOpenDay == "1" || inOpenDay == 1)) {
    return "通过";
}
return "当前不在本池开放日内";
','active',0,NOW(),NOW()),
(13,'调入-行业限制','[checkCommonIn] inCheckIndustry。代码已注释不执行；保留 Demo 供后续启用。industry_exponent!=0 或证券行业空则跳过。','adjust_in','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(hasIndustryLimit == "是" || hasIndustryLimit == "1" || hasIndustryLimit == 1)) {
    return "通过";
}
if (industryExponent != null && industryExponent != "" && industryExponent != 0) {
    return "通过";
}
if (securityIndustry == null || securityIndustry == "") {
    return "通过";
}
if (securityIndustry == poolIndustry) {
    return "通过";
}
return "证券行业与目标池行业配置不一致";
','disabled',0,NOW(),NOW()),
(14,'调出-池锁定','[checkCommonOut] outCheckPoolLocked。目标池 lock_flag=1 禁止调出。','adjust_out','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((lockFlag == "是" || lockFlag == "1" || lockFlag == 1)) {
    return "目标投资池已锁定";
}
return "通过";
','active',0,NOW(),NOW()),
(15,'调出-未在目标池','[checkCommonOut] outCheckSecurityNotInPool。不在池中无法调出；空在池集合视为不在池，失败而非通过。','adjust_out','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((alreadyInTargetPool == "是" || alreadyInTargetPool == "1" || alreadyInTargetPool == 1)) {
    return "通过";
}
return "证券当前不在目标投资池中";
','active',0,NOW(),NOW()),
(16,'调出-冻结期','[checkCommonOut] outCheckFrozenPeriod。entry_time + frozen_period_in 天内不可调出；无入池时间则失败。','adjust_out','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (frozenPeriodIn == null || frozenPeriodIn == "" || frozenPeriodIn <= 0) {
    return "通过";
}
if (!(hasEntryTime == "是" || hasEntryTime == "1" || hasEntryTime == 1)) {
    return "证券入池生效时间缺失";
}
if ((stillFrozen == "是" || stillFrozen == "1" || stillFrozen == 1)) {
    return "证券仍在目标投资池冻结期内";
}
return "通过";
','active',0,NOW(),NOW()),
(17,'调出-限制池','[checkCommonOut] outCheckRestrictPool。证券在 out_restrict 池中则禁止调出。','adjust_out','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((inOutRestrictPool == "是" || inOutRestrictPool == "1" || inOutRestrictPool == 1)) {
    return "证券当前在调出限制池中：" + restrictPoolNames;
}
return "通过";
','active',0,NOW(),NOW()),
(18,'调出-互斥池','[checkCommonOut] outCheckMutexPool。证券在 out_mutex 池中则禁止从目标池调出。','adjust_out','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((inOutMutexPool == "是" || inOutMutexPool == "1" || inOutMutexPool == 1)) {
    return "证券当前在调出互斥池中：" + mutexPoolNames;
}
return "通过";
','active',0,NOW(),NOW()),
(19,'调出-互斥冲突','[checkCommonOut] outCheckMutexConflict。本批同时对 in_mutex 互斥池调出则失败。','adjust_out','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((hasMutexConflict == "是" || hasMutexConflict == "1" || hasMutexConflict == 1)) {
    return "与以下互斥池不可同时调出：" + mutexPoolNames;
}
return "通过";
','active',0,NOW(),NOW()),
(20,'调出-弹性禁投','[checkCommonOut] outCheckElasticPool。out_soft_restrict 警告不阻断；文案与 Java 相同。','adjust_out','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((inSoftRestrictPool == "是" || inSoftRestrictPool == "1" || inSoftRestrictPool == 1)) {
    return "证券当前在调出弹性禁投池中：" + softRestrictPoolNames;
}
return "通过";
','active',0,NOW(),NOW()),
(21,'调出-开放日','[checkCommonOut] outCheckOpenDay。与调入同一开放日区间。','adjust_out','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(openDayAdjust == "是" || openDayAdjust == "1" || openDayAdjust == 1)) {
    return "通过";
}
if ((inOpenDay == "是" || inOpenDay == "1" || inOpenDay == 1)) {
    return "通过";
}
return "当前不在本池开放日内";
','active',0,NOW(),NOW()),
(22,'债券到期-调入','[checkBondIn] inCheckBondMaturity。maturity_date 早于今日禁止调入；空到期日不拦截。','bond','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (maturityDate == null || maturityDate == "") {
    return "通过";
}
if (maturityDate < today) {
    return "债券已到期";
}
return "通过";
','active',0,NOW(),NOW()),
(23,'债券到期-调出','[checkBondOut] outCheckBondMaturity。到期日早于今日禁止调出。','bond','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (maturityDate == null || maturityDate == "") {
    return "通过";
}
if (maturityDate < today) {
    return "债券已到期";
}
return "通过";
','active',0,NOW(),NOW()),
(24,'股票退市-调入','[checkStockIn] inCheckStockDelist。delist_date 早于今日禁止调入；空摘牌日不拦截。','stock','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (delistDate == null || delistDate == "") {
    return "通过";
}
if (delistDate < today) {
    return "股票已退市";
}
return "通过";
','active',0,NOW(),NOW()),
(25,'股票评级限制','[checkStockIn] inCheckGradeAstrict。尚未接入 StockResearch/investrank，配置了也跳过。','stock','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
return "通过";
','disabled',0,NOW(),NOW()),
(26,'基金评分限制','[checkFundIn] inCheckFundRate。池配置了表达式且未传 fundRate 则失败，文案对齐 Java。','fund','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (fundRateLimit == null || fundRateLimit == "") {
    return "通过";
}
rate = fundRateLimit.replace(" ", "");
msg = poolName + "的评分，必须在" + rate.replace("#rate", "基金评分");
if (fundRate == null || fundRate == "") {
    return msg;
}
if (minRate != null && minRate != "" && fundRate < minRate) {
    return msg;
}
if (maxRate != null && maxRate != "" && fundRate > maxRate) {
    return msg;
}
return "通过";
','active',0,NOW(),NOW()),
(27,'调入-主体债入库矩阵','[checkBondIn] inCheckMainGradeRule 全文。releaseRules/非分级库跳过；可转债CRMW排除；重点观察名单；内评/期限/允许池；特殊债 describeRange。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((releaseRules == "是" || releaseRules == "1" || releaseRules == 1)) {
    return "通过";
}
if (!(isGradedBondPool == "是" || isGradedBondPool == "1" || isGradedBondPool == 1)) {
    return "通过";
}
if (securityType == "convertible_bond" || securityType == "exchangeable_bond" || securityType == "detachable_convertible_bond" || securityType == "crmw") {
    return "可转债、可交换债、信用风险缓释工具不适用信用债分级库";
}
if ((inRestricted == "是" || inRestricted == "1" || inRestricted == 1) && !(hasStrongGuarantee == "是" || hasStrongGuarantee == "1" || hasStrongGuarantee == 1)) {
    notInGraded = (currentGradedSort == null || currentGradedSort == "" || currentGradedSort == 0);
    if (notInGraded) {
        return "重点观察名单原则上不得新增入库信用债分级库";
    }
    if (currentGradedSort <= 4 && targetPoolLevel != 5) {
        return "重点观察名单已在库债券只能调入五级库或调出";
    }
    if (currentGradedSort == 5 && targetPoolLevel < 5) {
        return "重点观察名单已在五级库，不可上调";
    }
}
gradeCode = innerIssuerRating;
if ((gradeCode == null || gradeCode == "") && (isTempCode == "是" || isTempCode == "1" || isTempCode == 1)) {
    gradeCode = "4";
}
if (gradeCode == null || gradeCode == "") {
    return "未配置主体内评分档";
}
if (matchedBucket == null || matchedBucket == "") {
    return "无法匹配债券期限档";
}
if (!(hasAllowedPools == "是" || hasAllowedPools == "1" || hasAllowedPools == 1)) {
    return "主体债入库矩阵未配置允许池";
}
memo = "";
if ((isPrivate == "是" || isPrivate == "1" || isPrivate == 1)) {
    memo = "private";
}
if ((isSubordinated == "是" || isSubordinated == "1" || isSubordinated == 1)) {
    memo = "subordinated";
}
if ((isPerpetual == "是" || isPerpetual == "1" || isPerpetual == 1)) {
    memo = "perpetual";
}
if ((isGuaranteed == "是" || isGuaranteed == "1" || isGuaranteed == 1)) {
    memo = "guaranteed";
}
mode = "EXACT_MATRIX";
startSort = 0;
exactSort = 0;
if ((isAbs == "是" || isAbs == "1" || isAbs == 1)) {
    if (innerGuarantorRating == "1") {
        mode = "LEVEL_ONE_ONLY";
        startSort = 1;
        exactSort = 1;
    } else {
        mode = "DOWNGRADE_CEILING";
        startSort = matrixBestLevel + 1;
        exactSort = 0;
    }
} else {
    if (memo == "private") {
        if (innerIssuerRating == "1") {
            mode = "LEVEL_ONE_ONLY";
            startSort = 1;
            exactSort = 1;
        } else {
            mode = "DOWNGRADE_CEILING";
            startSort = matrixBestLevel + 1;
            exactSort = 0;
        }
    } else {
        if (memo == "perpetual") {
            if (innerIssuerRating == "1") {
                mode = "DOWNGRADE_EXACT";
                startSort = matrixBestLevel + 1;
                exactSort = 1;
            } else {
                mode = "DOWNGRADE_CEILING";
                startSort = matrixBestLevel + 1;
                exactSort = 0;
            }
        } else {
            if (memo == "subordinated") {
                if (innerIssuerRating == "1") {
                    mode = "LEVEL_ONE_ONLY";
                    startSort = 1;
                    exactSort = 1;
                } else {
                    if (innerIssuerRating == "2+" || innerIssuerRating == "2" || innerIssuerRating == "2-") {
                        mode = "DOWNGRADE_EXACT";
                        startSort = matrixBestLevel + 1;
                        exactSort = 1;
                    } else {
                        mode = "DOWNGRADE_CEILING";
                        startSort = matrixBestLevel + 1;
                        exactSort = 0;
                    }
                }
            } else {
                if (memo == "guaranteed" || (inObserve == "是" || inObserve == "1" || inObserve == 1)) {
                    mode = "BEST_AND_WORSE";
                    startSort = matrixBestLevel;
                    exactSort = 0;
                }
            }
        }
    }
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (mode == "EXACT_MATRIX") {
    if ((targetInAllowedPools == "是" || targetInAllowedPools == "1" || targetInAllowedPools == 1)) {
        return "通过";
    }
    return "目标池「" + poolName + "」不在入库矩阵允许范围内（允许：" + allowedPoolNames + "）";
}
if (exactSort == 1) {
    if (targetPoolLevel == startSort) {
        return "通过";
    }
    return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级）";
}
if (targetPoolLevel >= startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级及更差）";
','active',0,NOW(),NOW()),
(28,'矩阵-可转债CRMW排除','[inCheckMainGradeRule] 可转债/可交换/可分离转债/CRMW 不能进信用债 1～5。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (securityType == "convertible_bond" || securityType == "exchangeable_bond" || securityType == "detachable_convertible_bond" || securityType == "crmw") {
    return "可转债、可交换债、信用风险缓释工具不适用信用债分级库";
}
return "通过";
','active',0,NOW(),NOW()),
(29,'矩阵-未配置内评档','[inCheckMainGradeRule] 正式证券无内评禁止入信用债 1～5；临时代码默认档 4。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if ((isTempCode == "是" || isTempCode == "1" || isTempCode == 1)) {
    return "通过";
}
if (innerIssuerRating == null || innerIssuerRating == "") {
    return "未配置主体内评分档";
}
return "通过";
','active',0,NOW(),NOW()),
(30,'矩阵-期限档匹配','[inCheckMainGradeRule] 普通债 date_exists 天÷365；含权回售用年字段；空则默认最长档，匹配不到才失败。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (matchedBucket == null || matchedBucket == "") {
    return "无法匹配债券期限档";
}
return "通过";
','active',0,NOW(),NOW()),
(31,'矩阵-允许池配置','[inCheckMainGradeRule] 内评档×期限档查不到允许池则失败。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(hasAllowedPools == "是" || hasAllowedPools == "1" || hasAllowedPools == 1)) {
    return "主体债入库矩阵未配置允许池";
}
return "通过";
','active',0,NOW(),NOW()),
(32,'矩阵-ABS校验','[CreditBondSpecialInboundRule] ABS 担保人内评 1 档只能一级库（仅 1 级），否则至少下调一级。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(isAbs == "是" || isAbs == "1" || isAbs == 1)) {
    return "通过";
}
if (innerGuarantorRating == "1") {
    startSort = 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel == startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级）";
}
startSort = matrixBestLevel + 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel >= startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级及更差）";
','active',0,NOW(),NOW()),
(33,'矩阵-私募债校验','[inCheckMainGradeRule] 覆盖后仍是私募：主体内评 1 档只能一级库，否则至少下调一级。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(isPrivate == "是" || isPrivate == "1" || isPrivate == 1)) {
    return "通过";
}
if (innerIssuerRating == "1") {
    startSort = 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel == startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级）";
}
startSort = matrixBestLevel + 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel >= startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级及更差）";
','active',0,NOW(),NOW()),
(34,'矩阵-永续债校验','[inCheckMainGradeRule] 主体内评 1 档下调一级且只留该档；否则至少下调一级。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(isPerpetual == "是" || isPerpetual == "1" || isPerpetual == 1)) {
    return "通过";
}
if (innerIssuerRating == "1") {
    startSort = matrixBestLevel + 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel == startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级）";
}
startSort = matrixBestLevel + 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel >= startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级及更差）";
','active',0,NOW(),NOW()),
(35,'矩阵-次级债校验','[inCheckMainGradeRule] 1 档只能一级库；2+/2/2- 下调一级只留该档；其余至少下调一级。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(isSubordinated == "是" || isSubordinated == "1" || isSubordinated == 1)) {
    return "通过";
}
if (innerIssuerRating == "1") {
    startSort = 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel == startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级）";
}
if (innerIssuerRating == "2+" || innerIssuerRating == "2" || innerIssuerRating == "2-") {
    startSort = matrixBestLevel + 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel == startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级）";
}
startSort = matrixBestLevel + 1;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel >= startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级及更差）";
','active',0,NOW(),NOW()),
(36,'矩阵-担保债观察池','[inCheckMainGradeRule] 担保或观察池：不得高于矩阵最好档，从该档开到五级。永续+担保按担保。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(isGuaranteed == "是" || isGuaranteed == "1" || isGuaranteed == 1) && !(inObserve == "是" || inObserve == "1" || inObserve == 1)) {
    return "通过";
}
startSort = matrixBestLevel;
poolName = targetPoolName;
if (poolName == null || poolName == "") {
    poolName = targetPoolId;
}
if (startSort < 1) {
    startSort = 1;
}
if (startSort > 5) {
    startSort = 5;
}
if (targetPoolLevel >= startSort) {
    return "通过";
}
return "目标池「" + poolName + "」不在特殊债调整后的允许范围内（仅 " + startSort + " 级及更差）";
','active',0,NOW(),NOW()),
(37,'矩阵-重点观察名单','[CreditBondSpecialInboundRule.checkRestricted] 未在库不得新增；已在 1～4 只能五级；强担保豁免。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(inRestricted == "是" || inRestricted == "1" || inRestricted == 1)) {
    return "通过";
}
if ((hasStrongGuarantee == "是" || hasStrongGuarantee == "1" || hasStrongGuarantee == 1)) {
    return "通过";
}
notInGraded = (currentGradedSort == null || currentGradedSort == "" || currentGradedSort == 0);
if (notInGraded) {
    return "重点观察名单原则上不得新增入库信用债分级库";
}
if (currentGradedSort <= 4 && targetPoolLevel != 5) {
    return "重点观察名单已在库债券只能调入五级库或调出";
}
if (currentGradedSort == 5 && targetPoolLevel < 5) {
    return "重点观察名单已在五级库，不可上调";
}
return "通过";
','active',0,NOW(),NOW()),
(38,'矩阵-特殊债类型覆盖','[CreditBondSpecialInboundRule.resolveExclusiveMemo] 私募→次级→永续→担保；ABS 始终优先。','grade_matrix','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
result = "ordinary";
if ((isPrivate == "是" || isPrivate == "1" || isPrivate == 1)) {
    result = "private";
}
if ((isSubordinated == "是" || isSubordinated == "1" || isSubordinated == 1)) {
    result = "subordinated";
}
if ((isPerpetual == "是" || isPerpetual == "1" || isPerpetual == 1)) {
    result = "perpetual";
}
if ((isGuaranteed == "是" || isGuaranteed == "1" || isGuaranteed == 1)) {
    result = "guaranteed";
}
if ((isAbs == "是" || isAbs == "1" || isAbs == 1)) {
    return "abs";
}
return result;
','active',0,NOW(),NOW()),
(39,'流程-白名单命中','[resolveAdjustFlowOptions] isWhitelistFlowMatched。当前 WHITELIST_POOL_IDS 空集，运行时固定不命中。','flow_match','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
unmatch = "";
if (remainDays == null || remainDays == "") {
    unmatch = unmatch + "剩余期限无法解析，date_exists 为空;";
} else {
    if (remainDays < 0) {
        unmatch = unmatch + "剩余期限已小于 0 天;";
    } else {
        if (remainDays > 1095) {
            unmatch = unmatch + "剩余期限超过 3 年;";
        }
    }
}
if ((isPerpetual == "是" || isPerpetual == "1" || isPerpetual == 1)) {
    unmatch = unmatch + "债券为永续债，不符合白名单条件;";
} else {
    if ((isAbs == "是" || isAbs == "1" || isAbs == 1)) {
        unmatch = unmatch + "债券为 ABS 债，不符合白名单条件;";
    } else {
        if ((isPrivate == "是" || isPrivate == "1" || isPrivate == 1)) {
            unmatch = unmatch + "债券为私募债，不符合白名单条件;";
        }
    }
}
if (!(isBond == "是" || isBond == "1" || isBond == 1)) {
    unmatch = unmatch + "债券类型不属于债券类;";
}
if (!(whitelistPoolConfigured == "是" || whitelistPoolConfigured == "1" || whitelistPoolConfigured == 1)) {
    unmatch = unmatch + "白名单池未配置，主体在白名单池条件不成立;";
} else {
    if (!(inWhitelistPool == "是" || inWhitelistPool == "1" || inWhitelistPool == 1)) {
        unmatch = unmatch + "主体不在白名单池;";
    }
}
if ((isGuaranteed == "是" || isGuaranteed == "1" || isGuaranteed == 1)) {
    unmatch = unmatch + "债券为担保债，不符合白名单条件;";
}
if (unmatch == "") {
    return "命中白名单";
}
return "未命中:" + unmatch;
','active',0,NOW(),NOW()),
(40,'流程-简易命中','[resolveAdjustFlowOptions] isSimpleInboundFlowMatched。一至三级库、期限、180天一般入库。','flow_match','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
unmatch = "";
if (targetInnerSort < 1 || targetInnerSort > 3) {
    unmatch = unmatch + "目标池不是信用债大库一、二、三级库;";
}
if (!(remainDaysParseable == "是" || remainDaysParseable == "1" || remainDaysParseable == 1)) {
    unmatch = unmatch + "剩余期限无法解析，date_exists 为空;";
}
if ((remainDaysParseable == "是" || remainDaysParseable == "1" || remainDaysParseable == 1) && !(remainNotExceedIssuerMax == "是" || remainNotExceedIssuerMax == "1" || remainNotExceedIssuerMax == 1) && (hasIssuerMaxRemain == "是" || hasIssuerMaxRemain == "1" || hasIssuerMaxRemain == 1)) {
    unmatch = unmatch + "剩余期限超过同主体在池最大期限;";
}
if (!(hasNonSimpleInbound180 == "是" || hasNonSimpleInbound180 == "1" || hasNonSimpleInbound180 == 1)) {
    unmatch = unmatch + "该主体180天内未以一般流程入过目标池，不满足简易流程前提条件;";
}
if (unmatch == "") {
    return "命中简易流程";
}
return "未命中:" + unmatch;
','active',0,NOW(),NOW()),
(41,'流程-信用债升降级','[resolveCreditBondAdjustFlowType] 已在信用债大库时按 inner_sort 比较：目标更小=upgradeInbound。','flow_match','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (!(alreadyInCreditBond == "是" || alreadyInCreditBond == "1" || alreadyInCreditBond == 1)) {
    return "不适用";
}
if (targetInnerSort < currentInnerSort) {
    return "upgradeInbound";
}
if (targetInnerSort > currentInnerSort) {
    return "downgradeInbound";
}
return "normalInbound";
','active',0,NOW(),NOW()),
(42,'提交-研究报告必填','[submitAdjustLog] checkReportRequired。none/空不限制；any 任意报告；internal 必须内部报告库。','submit','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (targetPoolId == null || targetPoolId == "") {
    return "调库项不能为空";
}
if (securityExists == "否") {
    return "证券不存在";
}
if (poolExists == "否") {
    return "目标投资池不存在";
}
if (reportRestriction == null || reportRestriction == "" || reportRestriction == "none") {
    return "通过";
}
if ((hasRecentInboundReport == "是" || hasRecentInboundReport == "1" || hasRecentInboundReport == 1)) {
    return "通过";
}
if (!(hasAnyReport == "是" || hasAnyReport == "1" || hasAnyReport == 1)) {
    return "目标池[" + poolName + "]要求研究报告，请上传或选择报告";
}
if (reportRestriction == "internal" && !(hasInternalReport == "是" || hasInternalReport == "1" || hasInternalReport == 1)) {
    return "目标池[" + poolName + "]要求内部研究报告，请从内部报告库选择";
}
return "通过";
','active',0,NOW(),NOW()),
(43,'自动-到期出池','[AutoAdjustService] 到期日早于昨天（T-2）自动出池，原因与 Java 一致：证券到期自动调出。','auto_adjust','if (securityCode == null || securityCode == "") {
    return "证券代码不能为空";
}
if (maturityDate == null || maturityDate == "") {
    return "通过";
}
if (maturityDate < yesterday) {
    return "证券到期自动调出";
}
return "通过";
','active',0,NOW(),NOW()),
(44,'自动-主体外评AA-入池','[CompanyOuterRatingAaMinusAutoInService] 外评落在 AA-/A/BBB… 名单且尚未在目标池则自动入池。','auto_adjust','if (issuerCode == null || issuerCode == "") {
    return "主体代码不能为空";
}
if (issuerRating == null || issuerRating == "") {
    return "主体外评不能为空";
}
if ((alreadyInTargetPool == "是" || alreadyInTargetPool == "1" || alreadyInTargetPool == 1)) {
    return "通过";
}
lowList = ",AA-,A+,A,A-,BBB+,BBB,BBB-,BB+,BB,BB-,B+,B,B-,CCC,CC,C,";
if (lowList.indexOf("," + issuerRating + ",") >= 0) {
    return "外评AA-及以下主体自动入池";
}
return "通过";
','active',0,NOW(),NOW());

INSERT INTO `rule_param` (id, rule_id, param_name, param_label, param_type, required, sort_no, crte_time, updt_time) VALUES
(1,1,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(2,1,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(3,1,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(4,1,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(5,1,'lockFlag','目标池是否锁定','select',1,5,NOW(),NOW()),
(6,2,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(7,2,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(8,2,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(9,2,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(10,2,'varietyCodes','池投资品种JSON','string',1,5,NOW(),NOW()),
(11,2,'categoryType','证券品种大类','select',1,6,NOW(),NOW()),
(12,3,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(13,3,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(14,3,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(15,3,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(16,3,'marketCodes','池投资市场JSON','string',1,5,NOW(),NOW()),
(17,3,'securityMarkets','证券所在市场','select',1,6,NOW(),NOW()),
(18,4,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(19,4,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(20,4,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(21,4,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(22,4,'hasPendingProcess','是否有进行中流程','select',1,5,NOW(),NOW()),
(23,4,'pendingNodeLabel','当前节点名称','string',1,6,NOW(),NOW()),
(24,5,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(25,5,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(26,5,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(27,5,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(28,5,'alreadyInTargetPool','是否已在目标池','select',1,5,NOW(),NOW()),
(29,6,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(30,6,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(31,6,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(32,6,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(33,6,'maxCapacity','持仓上限','number',1,5,NOW(),NOW()),
(34,6,'currentCount','当前在池数量','number',1,6,NOW(),NOW()),
(35,7,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(36,7,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(37,7,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(38,7,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(39,7,'hasSourceLimit','是否配置来源池','select',1,5,NOW(),NOW()),
(40,7,'inSourcePool','是否已在/本批调入来源池','select',1,6,NOW(),NOW()),
(41,7,'sourcePoolNames','来源池名称','string',1,7,NOW(),NOW()),
(42,8,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(43,8,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(44,8,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(45,8,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(46,8,'inRestrictPool','是否在调入限制池','select',1,5,NOW(),NOW()),
(47,8,'restrictPoolNames','限制池名称','string',1,6,NOW(),NOW()),
(48,9,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(49,9,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(50,9,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(51,9,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(52,9,'hasMutexConflict','本批是否同时勾选互斥池','select',1,5,NOW(),NOW()),
(53,9,'mutexPoolNames','互斥池名称','string',1,6,NOW(),NOW()),
(54,10,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(55,10,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(56,10,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(57,10,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(58,10,'inSoftRestrictPool','是否在弹性禁投池','select',1,5,NOW(),NOW()),
(59,10,'softRestrictPoolNames','弹性禁投池名称','string',1,6,NOW(),NOW()),
(60,11,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(61,11,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(62,11,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(63,11,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(64,11,'inForbiddenPool','是否在全局禁止池','select',1,5,NOW(),NOW()),
(65,12,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(66,12,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(67,12,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(68,12,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(69,12,'openDayAdjust','是否启用开放日','select',1,5,NOW(),NOW()),
(70,12,'inOpenDay','当日是否在开放区间','select',1,6,NOW(),NOW()),
(71,13,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(72,13,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(73,13,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(74,13,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(75,13,'hasIndustryLimit','是否配置行业限制','select',1,5,NOW(),NOW()),
(76,13,'industryExponent','行业指数模式','number',1,6,NOW(),NOW()),
(77,13,'securityIndustry','证券行业','string',1,7,NOW(),NOW()),
(78,13,'poolIndustry','池行业配置','string',1,8,NOW(),NOW()),
(79,14,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(80,14,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(81,14,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(82,14,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(83,14,'lockFlag','目标池是否锁定','select',1,5,NOW(),NOW()),
(84,15,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(85,15,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(86,15,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(87,15,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(88,15,'alreadyInTargetPool','是否已在目标池','select',1,5,NOW(),NOW()),
(89,16,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(90,16,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(91,16,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(92,16,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(93,16,'frozenPeriodIn','冻结期天数','number',1,5,NOW(),NOW()),
(94,16,'hasEntryTime','是否有入池时间','select',1,6,NOW(),NOW()),
(95,16,'stillFrozen','是否仍在冻结期','select',1,7,NOW(),NOW()),
(96,17,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(97,17,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(98,17,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(99,17,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(100,17,'inOutRestrictPool','是否在调出限制池','select',1,5,NOW(),NOW()),
(101,17,'restrictPoolNames','限制池名称','string',1,6,NOW(),NOW()),
(102,18,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(103,18,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(104,18,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(105,18,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(106,18,'inOutMutexPool','是否在调出互斥池','select',1,5,NOW(),NOW()),
(107,18,'mutexPoolNames','互斥池名称','string',1,6,NOW(),NOW()),
(108,19,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(109,19,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(110,19,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(111,19,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(112,19,'hasMutexConflict','本批是否同时调出互斥池','select',1,5,NOW(),NOW()),
(113,19,'mutexPoolNames','互斥池名称','string',1,6,NOW(),NOW()),
(114,20,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(115,20,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(116,20,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(117,20,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(118,20,'inSoftRestrictPool','是否在弹性禁投池','select',1,5,NOW(),NOW()),
(119,20,'softRestrictPoolNames','弹性禁投池名称','string',1,6,NOW(),NOW()),
(120,21,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(121,21,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(122,21,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(123,21,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(124,21,'openDayAdjust','是否启用开放日','select',1,5,NOW(),NOW()),
(125,21,'inOpenDay','当日是否在开放区间','select',1,6,NOW(),NOW()),
(126,22,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(127,22,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(128,22,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(129,22,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(130,22,'maturityDate','到期日yyyyMMdd','string',1,5,NOW(),NOW()),
(131,22,'today','今日yyyyMMdd','string',1,6,NOW(),NOW()),
(132,23,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(133,23,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(134,23,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(135,23,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(136,23,'maturityDate','到期日yyyyMMdd','string',1,5,NOW(),NOW()),
(137,23,'today','今日yyyyMMdd','string',1,6,NOW(),NOW()),
(138,24,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(139,24,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(140,24,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(141,24,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(142,24,'delistDate','退市日yyyyMMdd','string',1,5,NOW(),NOW()),
(143,24,'today','今日yyyyMMdd','string',1,6,NOW(),NOW()),
(144,25,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(145,25,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(146,25,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(147,25,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(148,25,'gradeAstrict','池评级限制表达式','string',1,5,NOW(),NOW()),
(149,26,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(150,26,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(151,26,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(152,26,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(153,26,'fundRateLimit','评分限制原文','string',1,5,NOW(),NOW()),
(154,26,'poolName','目标池名称','string',1,6,NOW(),NOW()),
(155,26,'fundRate','请求基金评分','number',1,7,NOW(),NOW()),
(156,26,'minRate','下限','number',1,8,NOW(),NOW()),
(157,26,'maxRate','上限','number',1,9,NOW(),NOW()),
(158,27,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(159,27,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(160,27,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(161,27,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(162,27,'releaseRules','是否放开规则','select',1,5,NOW(),NOW()),
(163,27,'isGradedBondPool','是否信用债分级库','select',1,6,NOW(),NOW()),
(164,27,'securityType','证券类型','select',1,7,NOW(),NOW()),
(165,27,'inRestricted','是否在重点观察名单','select',1,8,NOW(),NOW()),
(166,27,'hasStrongGuarantee','是否强担保豁免','select',1,9,NOW(),NOW()),
(167,27,'currentGradedSort','当前分级库档0=未在库','number',1,10,NOW(),NOW()),
(168,27,'targetPoolLevel','目标分级库档','number',1,11,NOW(),NOW()),
(169,27,'targetPoolName','目标池名称','string',1,12,NOW(),NOW()),
(170,27,'isTempCode','是否临时代码','select',1,13,NOW(),NOW()),
(171,27,'innerIssuerRating','发债主体内评','select',1,14,NOW(),NOW()),
(172,27,'innerGuarantorRating','担保人内评','select',1,15,NOW(),NOW()),
(173,27,'matchedBucket','匹配到的期限档','select',1,16,NOW(),NOW()),
(174,27,'hasAllowedPools','矩阵是否配置允许池','select',1,17,NOW(),NOW()),
(175,27,'allowedPoolNames','矩阵允许池名称','string',1,18,NOW(),NOW()),
(176,27,'targetInAllowedPools','目标是否在矩阵允许池','select',1,19,NOW(),NOW()),
(177,27,'isAbs','是否ABS','select',1,20,NOW(),NOW()),
(178,27,'isPrivate','是否私募','select',1,21,NOW(),NOW()),
(179,27,'isSubordinated','是否次级','select',1,22,NOW(),NOW()),
(180,27,'isPerpetual','是否永续','select',1,23,NOW(),NOW()),
(181,27,'isGuaranteed','是否担保债','select',1,24,NOW(),NOW()),
(182,27,'inObserve','是否在观察池','select',1,25,NOW(),NOW()),
(183,27,'matrixBestLevel','矩阵最好档','number',1,26,NOW(),NOW()),
(184,28,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(185,28,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(186,28,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(187,28,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(188,28,'securityType','证券类型','select',1,5,NOW(),NOW()),
(189,29,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(190,29,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(191,29,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(192,29,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(193,29,'isTempCode','是否临时代码','select',1,5,NOW(),NOW()),
(194,29,'innerIssuerRating','发债主体内评','select',1,6,NOW(),NOW()),
(195,30,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(196,30,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(197,30,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(198,30,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(199,30,'matchedBucket','匹配到的期限档','select',1,5,NOW(),NOW()),
(200,31,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(201,31,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(202,31,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(203,31,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(204,31,'hasAllowedPools','矩阵是否配置允许池','select',1,5,NOW(),NOW()),
(205,32,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(206,32,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(207,32,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(208,32,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(209,32,'isAbs','是否ABS','select',1,5,NOW(),NOW()),
(210,32,'innerGuarantorRating','担保人内评','select',1,6,NOW(),NOW()),
(211,32,'matrixBestLevel','矩阵最好档','number',1,7,NOW(),NOW()),
(212,32,'targetPoolLevel','目标分级库档','number',1,8,NOW(),NOW()),
(213,32,'targetPoolName','目标池名称','string',1,9,NOW(),NOW()),
(214,33,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(215,33,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(216,33,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(217,33,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(218,33,'isPrivate','是否私募','select',1,5,NOW(),NOW()),
(219,33,'innerIssuerRating','发债主体内评','select',1,6,NOW(),NOW()),
(220,33,'matrixBestLevel','矩阵最好档','number',1,7,NOW(),NOW()),
(221,33,'targetPoolLevel','目标分级库档','number',1,8,NOW(),NOW()),
(222,33,'targetPoolName','目标池名称','string',1,9,NOW(),NOW()),
(223,34,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(224,34,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(225,34,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(226,34,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(227,34,'isPerpetual','是否永续','select',1,5,NOW(),NOW()),
(228,34,'innerIssuerRating','发债主体内评','select',1,6,NOW(),NOW()),
(229,34,'matrixBestLevel','矩阵最好档','number',1,7,NOW(),NOW()),
(230,34,'targetPoolLevel','目标分级库档','number',1,8,NOW(),NOW()),
(231,34,'targetPoolName','目标池名称','string',1,9,NOW(),NOW()),
(232,35,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(233,35,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(234,35,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(235,35,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(236,35,'isSubordinated','是否次级','select',1,5,NOW(),NOW()),
(237,35,'innerIssuerRating','发债主体内评','select',1,6,NOW(),NOW()),
(238,35,'matrixBestLevel','矩阵最好档','number',1,7,NOW(),NOW()),
(239,35,'targetPoolLevel','目标分级库档','number',1,8,NOW(),NOW()),
(240,35,'targetPoolName','目标池名称','string',1,9,NOW(),NOW()),
(241,36,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(242,36,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(243,36,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(244,36,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(245,36,'isGuaranteed','是否担保债','select',1,5,NOW(),NOW()),
(246,36,'inObserve','是否在观察池','select',1,6,NOW(),NOW()),
(247,36,'matrixBestLevel','矩阵最好档','number',1,7,NOW(),NOW()),
(248,36,'targetPoolLevel','目标分级库档','number',1,8,NOW(),NOW()),
(249,36,'targetPoolName','目标池名称','string',1,9,NOW(),NOW()),
(250,37,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(251,37,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(252,37,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(253,37,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(254,37,'inRestricted','是否在重点观察名单','select',1,5,NOW(),NOW()),
(255,37,'hasStrongGuarantee','是否强担保豁免','select',1,6,NOW(),NOW()),
(256,37,'currentGradedSort','当前分级库档0=未在库','number',1,7,NOW(),NOW()),
(257,37,'targetPoolLevel','目标分级库档','number',1,8,NOW(),NOW()),
(258,38,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(259,38,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(260,38,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(261,38,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(262,38,'isAbs','是否ABS','select',1,5,NOW(),NOW()),
(263,38,'isPrivate','是否私募','select',1,6,NOW(),NOW()),
(264,38,'isSubordinated','是否次级','select',1,7,NOW(),NOW()),
(265,38,'isPerpetual','是否永续','select',1,8,NOW(),NOW()),
(266,38,'isGuaranteed','是否担保','select',1,9,NOW(),NOW()),
(267,39,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(268,39,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(269,39,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(270,39,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(271,39,'remainDays','剩余期限天','number',1,5,NOW(),NOW()),
(272,39,'isPerpetual','是否永续','select',1,6,NOW(),NOW()),
(273,39,'isAbs','是否ABS','select',1,7,NOW(),NOW()),
(274,39,'isPrivate','是否私募','select',1,8,NOW(),NOW()),
(275,39,'isBond','是否债券类','select',1,9,NOW(),NOW()),
(276,39,'whitelistPoolConfigured','白名单池是否已配置','select',1,10,NOW(),NOW()),
(277,39,'inWhitelistPool','主体是否在白名单池','select',1,11,NOW(),NOW()),
(278,39,'isGuaranteed','是否担保债','select',1,12,NOW(),NOW()),
(279,40,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(280,40,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(281,40,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(282,40,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(283,40,'targetInnerSort','目标分级库档','number',1,5,NOW(),NOW()),
(284,40,'remainDaysParseable','剩余期限可解析','select',1,6,NOW(),NOW()),
(285,40,'remainNotExceedIssuerMax','不超过同主体最大期限','select',1,7,NOW(),NOW()),
(286,40,'hasIssuerMaxRemain','目标池是否已有同主体债','select',1,8,NOW(),NOW()),
(287,40,'hasNonSimpleInbound180','180天内非简易入库','select',1,9,NOW(),NOW()),
(288,41,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(289,41,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(290,41,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(291,41,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(292,41,'alreadyInCreditBond','是否已在信用债大库','select',1,5,NOW(),NOW()),
(293,41,'currentInnerSort','当前分级档','number',1,6,NOW(),NOW()),
(294,41,'targetInnerSort','目标分级档','number',1,7,NOW(),NOW()),
(295,42,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(296,42,'targetPoolId','目标投资池ID','string',1,2,NOW(),NOW()),
(297,42,'securityExists','证券是否存在','select',1,3,NOW(),NOW()),
(298,42,'poolExists','目标池是否存在','select',1,4,NOW(),NOW()),
(299,42,'reportRestriction','报告限制','select',1,5,NOW(),NOW()),
(300,42,'poolName','目标池名称','string',1,6,NOW(),NOW()),
(301,42,'hasRecentInboundReport','半年内已有入池报告','select',1,7,NOW(),NOW()),
(302,42,'hasAnyReport','是否已选/上传报告','select',1,8,NOW(),NOW()),
(303,42,'hasInternalReport','是否内部报告库','select',1,9,NOW(),NOW()),
(304,43,'securityCode','证券代码','string',1,1,NOW(),NOW()),
(305,43,'maturityDate','到期日yyyy-MM-dd','string',1,2,NOW(),NOW()),
(306,43,'yesterday','昨天yyyy-MM-dd','string',1,3,NOW(),NOW()),
(307,44,'issuerCode','主体代码','string',1,1,NOW(),NOW()),
(308,44,'issuerRating','主体外评','select',1,2,NOW(),NOW()),
(309,44,'alreadyInTargetPool','是否已在目标池','select',1,3,NOW(),NOW());

INSERT INTO `rule_param_option` (param_id, option_value, option_label, sort_no, crte_time, updt_time) VALUES
(3,'是','是',1,NOW(),NOW()),
(3,'否','否',2,NOW(),NOW()),
(4,'是','是',1,NOW(),NOW()),
(4,'否','否',2,NOW(),NOW()),
(5,'是','是',1,NOW(),NOW()),
(5,'否','否',2,NOW(),NOW()),
(8,'是','是',1,NOW(),NOW()),
(8,'否','否',2,NOW(),NOW()),
(9,'是','是',1,NOW(),NOW()),
(9,'否','否',2,NOW(),NOW()),
(11,'bond','债券',1,NOW(),NOW()),
(11,'stock','股票',2,NOW(),NOW()),
(11,'fund','基金',3,NOW(),NOW()),
(11,'company','主体',4,NOW(),NOW()),
(14,'是','是',1,NOW(),NOW()),
(14,'否','否',2,NOW(),NOW()),
(15,'是','是',1,NOW(),NOW()),
(15,'否','否',2,NOW(),NOW()),
(17,'SSE','上交所',1,NOW(),NOW()),
(17,'SZSE','深交所',2,NOW(),NOW()),
(17,'CIBM','银行间',3,NOW(),NOW()),
(17,'BSE','北交所',4,NOW(),NOW()),
(20,'是','是',1,NOW(),NOW()),
(20,'否','否',2,NOW(),NOW()),
(21,'是','是',1,NOW(),NOW()),
(21,'否','否',2,NOW(),NOW()),
(22,'是','是',1,NOW(),NOW()),
(22,'否','否',2,NOW(),NOW()),
(26,'是','是',1,NOW(),NOW()),
(26,'否','否',2,NOW(),NOW()),
(27,'是','是',1,NOW(),NOW()),
(27,'否','否',2,NOW(),NOW()),
(28,'是','是',1,NOW(),NOW()),
(28,'否','否',2,NOW(),NOW()),
(31,'是','是',1,NOW(),NOW()),
(31,'否','否',2,NOW(),NOW()),
(32,'是','是',1,NOW(),NOW()),
(32,'否','否',2,NOW(),NOW()),
(37,'是','是',1,NOW(),NOW()),
(37,'否','否',2,NOW(),NOW()),
(38,'是','是',1,NOW(),NOW()),
(38,'否','否',2,NOW(),NOW()),
(39,'是','是',1,NOW(),NOW()),
(39,'否','否',2,NOW(),NOW()),
(40,'是','是',1,NOW(),NOW()),
(40,'否','否',2,NOW(),NOW()),
(44,'是','是',1,NOW(),NOW()),
(44,'否','否',2,NOW(),NOW()),
(45,'是','是',1,NOW(),NOW()),
(45,'否','否',2,NOW(),NOW()),
(46,'是','是',1,NOW(),NOW()),
(46,'否','否',2,NOW(),NOW()),
(50,'是','是',1,NOW(),NOW()),
(50,'否','否',2,NOW(),NOW()),
(51,'是','是',1,NOW(),NOW()),
(51,'否','否',2,NOW(),NOW()),
(52,'是','是',1,NOW(),NOW()),
(52,'否','否',2,NOW(),NOW()),
(56,'是','是',1,NOW(),NOW()),
(56,'否','否',2,NOW(),NOW()),
(57,'是','是',1,NOW(),NOW()),
(57,'否','否',2,NOW(),NOW()),
(58,'是','是',1,NOW(),NOW()),
(58,'否','否',2,NOW(),NOW()),
(62,'是','是',1,NOW(),NOW()),
(62,'否','否',2,NOW(),NOW()),
(63,'是','是',1,NOW(),NOW()),
(63,'否','否',2,NOW(),NOW()),
(64,'是','是',1,NOW(),NOW()),
(64,'否','否',2,NOW(),NOW()),
(67,'是','是',1,NOW(),NOW()),
(67,'否','否',2,NOW(),NOW()),
(68,'是','是',1,NOW(),NOW()),
(68,'否','否',2,NOW(),NOW()),
(69,'是','是',1,NOW(),NOW()),
(69,'否','否',2,NOW(),NOW()),
(70,'是','是',1,NOW(),NOW()),
(70,'否','否',2,NOW(),NOW()),
(73,'是','是',1,NOW(),NOW()),
(73,'否','否',2,NOW(),NOW()),
(74,'是','是',1,NOW(),NOW()),
(74,'否','否',2,NOW(),NOW()),
(75,'是','是',1,NOW(),NOW()),
(75,'否','否',2,NOW(),NOW()),
(81,'是','是',1,NOW(),NOW()),
(81,'否','否',2,NOW(),NOW()),
(82,'是','是',1,NOW(),NOW()),
(82,'否','否',2,NOW(),NOW()),
(83,'是','是',1,NOW(),NOW()),
(83,'否','否',2,NOW(),NOW()),
(86,'是','是',1,NOW(),NOW()),
(86,'否','否',2,NOW(),NOW()),
(87,'是','是',1,NOW(),NOW()),
(87,'否','否',2,NOW(),NOW()),
(88,'是','是',1,NOW(),NOW()),
(88,'否','否',2,NOW(),NOW()),
(91,'是','是',1,NOW(),NOW()),
(91,'否','否',2,NOW(),NOW()),
(92,'是','是',1,NOW(),NOW()),
(92,'否','否',2,NOW(),NOW()),
(94,'是','是',1,NOW(),NOW()),
(94,'否','否',2,NOW(),NOW()),
(95,'是','是',1,NOW(),NOW()),
(95,'否','否',2,NOW(),NOW()),
(98,'是','是',1,NOW(),NOW()),
(98,'否','否',2,NOW(),NOW()),
(99,'是','是',1,NOW(),NOW()),
(99,'否','否',2,NOW(),NOW()),
(100,'是','是',1,NOW(),NOW()),
(100,'否','否',2,NOW(),NOW()),
(104,'是','是',1,NOW(),NOW()),
(104,'否','否',2,NOW(),NOW()),
(105,'是','是',1,NOW(),NOW()),
(105,'否','否',2,NOW(),NOW()),
(106,'是','是',1,NOW(),NOW()),
(106,'否','否',2,NOW(),NOW()),
(110,'是','是',1,NOW(),NOW()),
(110,'否','否',2,NOW(),NOW()),
(111,'是','是',1,NOW(),NOW()),
(111,'否','否',2,NOW(),NOW()),
(112,'是','是',1,NOW(),NOW()),
(112,'否','否',2,NOW(),NOW()),
(116,'是','是',1,NOW(),NOW()),
(116,'否','否',2,NOW(),NOW()),
(117,'是','是',1,NOW(),NOW()),
(117,'否','否',2,NOW(),NOW()),
(118,'是','是',1,NOW(),NOW()),
(118,'否','否',2,NOW(),NOW()),
(122,'是','是',1,NOW(),NOW()),
(122,'否','否',2,NOW(),NOW()),
(123,'是','是',1,NOW(),NOW()),
(123,'否','否',2,NOW(),NOW()),
(124,'是','是',1,NOW(),NOW()),
(124,'否','否',2,NOW(),NOW()),
(125,'是','是',1,NOW(),NOW()),
(125,'否','否',2,NOW(),NOW()),
(128,'是','是',1,NOW(),NOW()),
(128,'否','否',2,NOW(),NOW()),
(129,'是','是',1,NOW(),NOW()),
(129,'否','否',2,NOW(),NOW()),
(134,'是','是',1,NOW(),NOW()),
(134,'否','否',2,NOW(),NOW()),
(135,'是','是',1,NOW(),NOW()),
(135,'否','否',2,NOW(),NOW()),
(140,'是','是',1,NOW(),NOW()),
(140,'否','否',2,NOW(),NOW()),
(141,'是','是',1,NOW(),NOW()),
(141,'否','否',2,NOW(),NOW()),
(146,'是','是',1,NOW(),NOW()),
(146,'否','否',2,NOW(),NOW()),
(147,'是','是',1,NOW(),NOW()),
(147,'否','否',2,NOW(),NOW()),
(151,'是','是',1,NOW(),NOW()),
(151,'否','否',2,NOW(),NOW()),
(152,'是','是',1,NOW(),NOW()),
(152,'否','否',2,NOW(),NOW()),
(160,'是','是',1,NOW(),NOW()),
(160,'否','否',2,NOW(),NOW()),
(161,'是','是',1,NOW(),NOW()),
(161,'否','否',2,NOW(),NOW()),
(162,'是','是',1,NOW(),NOW()),
(162,'否','否',2,NOW(),NOW()),
(163,'是','是',1,NOW(),NOW()),
(163,'否','否',2,NOW(),NOW()),
(164,'abs','资产支持证券',1,NOW(),NOW()),
(164,'convertible_bond','可转债',2,NOW(),NOW()),
(164,'exchangeable_bond','可交换公司债券',3,NOW(),NOW()),
(164,'detachable_convertible_bond','可分离转债存债',4,NOW(),NOW()),
(164,'crmw','信用风险缓释凭证',5,NOW(),NOW()),
(164,'corporate_bond','企业债',6,NOW(),NOW()),
(164,'mtn','中期票据',7,NOW(),NOW()),
(165,'是','是',1,NOW(),NOW()),
(165,'否','否',2,NOW(),NOW()),
(166,'是','是',1,NOW(),NOW()),
(166,'否','否',2,NOW(),NOW()),
(170,'是','是',1,NOW(),NOW()),
(170,'否','否',2,NOW(),NOW()),
(171,'1','1',1,NOW(),NOW()),
(171,'2+','2+',2,NOW(),NOW()),
(171,'2','2',3,NOW(),NOW()),
(171,'2-','2-',4,NOW(),NOW()),
(171,'3+','3+',5,NOW(),NOW()),
(171,'3','3',6,NOW(),NOW()),
(171,'3-','3-',7,NOW(),NOW()),
(171,'4','4',8,NOW(),NOW()),
(172,'1','1',1,NOW(),NOW()),
(172,'2+','2+',2,NOW(),NOW()),
(172,'2','2',3,NOW(),NOW()),
(172,'2-','2-',4,NOW(),NOW()),
(172,'3+','3+',5,NOW(),NOW()),
(172,'3','3',6,NOW(),NOW()),
(172,'3-','3-',7,NOW(),NOW()),
(172,'4','4',8,NOW(),NOW()),
(173,'GT_5','期限>5',1,NOW(),NOW()),
(173,'GT_3_LE_5','5>=期限>3',2,NOW(),NOW()),
(173,'GT_1_LE_3','3>=期限>1',3,NOW(),NOW()),
(173,'LE_1','1>=期限',4,NOW(),NOW()),
(174,'是','是',1,NOW(),NOW()),
(174,'否','否',2,NOW(),NOW()),
(176,'是','是',1,NOW(),NOW()),
(176,'否','否',2,NOW(),NOW()),
(177,'是','是',1,NOW(),NOW()),
(177,'否','否',2,NOW(),NOW()),
(178,'是','是',1,NOW(),NOW()),
(178,'否','否',2,NOW(),NOW()),
(179,'是','是',1,NOW(),NOW()),
(179,'否','否',2,NOW(),NOW()),
(180,'是','是',1,NOW(),NOW()),
(180,'否','否',2,NOW(),NOW()),
(181,'是','是',1,NOW(),NOW()),
(181,'否','否',2,NOW(),NOW()),
(182,'是','是',1,NOW(),NOW()),
(182,'否','否',2,NOW(),NOW()),
(186,'是','是',1,NOW(),NOW()),
(186,'否','否',2,NOW(),NOW()),
(187,'是','是',1,NOW(),NOW()),
(187,'否','否',2,NOW(),NOW()),
(188,'abs','资产支持证券',1,NOW(),NOW()),
(188,'convertible_bond','可转债',2,NOW(),NOW()),
(188,'exchangeable_bond','可交换公司债券',3,NOW(),NOW()),
(188,'detachable_convertible_bond','可分离转债存债',4,NOW(),NOW()),
(188,'crmw','信用风险缓释凭证',5,NOW(),NOW()),
(188,'corporate_bond','企业债',6,NOW(),NOW()),
(188,'mtn','中期票据',7,NOW(),NOW()),
(191,'是','是',1,NOW(),NOW()),
(191,'否','否',2,NOW(),NOW()),
(192,'是','是',1,NOW(),NOW()),
(192,'否','否',2,NOW(),NOW()),
(193,'是','是',1,NOW(),NOW()),
(193,'否','否',2,NOW(),NOW()),
(194,'1','1',1,NOW(),NOW()),
(194,'2+','2+',2,NOW(),NOW()),
(194,'2','2',3,NOW(),NOW()),
(194,'2-','2-',4,NOW(),NOW()),
(194,'3+','3+',5,NOW(),NOW()),
(194,'3','3',6,NOW(),NOW()),
(194,'3-','3-',7,NOW(),NOW()),
(194,'4','4',8,NOW(),NOW()),
(197,'是','是',1,NOW(),NOW()),
(197,'否','否',2,NOW(),NOW()),
(198,'是','是',1,NOW(),NOW()),
(198,'否','否',2,NOW(),NOW()),
(199,'GT_5','期限>5',1,NOW(),NOW()),
(199,'GT_3_LE_5','5>=期限>3',2,NOW(),NOW()),
(199,'GT_1_LE_3','3>=期限>1',3,NOW(),NOW()),
(199,'LE_1','1>=期限',4,NOW(),NOW()),
(202,'是','是',1,NOW(),NOW()),
(202,'否','否',2,NOW(),NOW()),
(203,'是','是',1,NOW(),NOW()),
(203,'否','否',2,NOW(),NOW()),
(204,'是','是',1,NOW(),NOW()),
(204,'否','否',2,NOW(),NOW()),
(207,'是','是',1,NOW(),NOW()),
(207,'否','否',2,NOW(),NOW()),
(208,'是','是',1,NOW(),NOW()),
(208,'否','否',2,NOW(),NOW()),
(209,'是','是',1,NOW(),NOW()),
(209,'否','否',2,NOW(),NOW()),
(210,'1','1',1,NOW(),NOW()),
(210,'2+','2+',2,NOW(),NOW()),
(210,'2','2',3,NOW(),NOW()),
(210,'2-','2-',4,NOW(),NOW()),
(210,'3+','3+',5,NOW(),NOW()),
(210,'3','3',6,NOW(),NOW()),
(210,'3-','3-',7,NOW(),NOW()),
(210,'4','4',8,NOW(),NOW()),
(216,'是','是',1,NOW(),NOW()),
(216,'否','否',2,NOW(),NOW()),
(217,'是','是',1,NOW(),NOW()),
(217,'否','否',2,NOW(),NOW()),
(218,'是','是',1,NOW(),NOW()),
(218,'否','否',2,NOW(),NOW()),
(219,'1','1',1,NOW(),NOW()),
(219,'2+','2+',2,NOW(),NOW()),
(219,'2','2',3,NOW(),NOW()),
(219,'2-','2-',4,NOW(),NOW()),
(219,'3+','3+',5,NOW(),NOW()),
(219,'3','3',6,NOW(),NOW()),
(219,'3-','3-',7,NOW(),NOW()),
(219,'4','4',8,NOW(),NOW()),
(225,'是','是',1,NOW(),NOW()),
(225,'否','否',2,NOW(),NOW()),
(226,'是','是',1,NOW(),NOW()),
(226,'否','否',2,NOW(),NOW()),
(227,'是','是',1,NOW(),NOW()),
(227,'否','否',2,NOW(),NOW()),
(228,'1','1',1,NOW(),NOW()),
(228,'2+','2+',2,NOW(),NOW()),
(228,'2','2',3,NOW(),NOW()),
(228,'2-','2-',4,NOW(),NOW()),
(228,'3+','3+',5,NOW(),NOW()),
(228,'3','3',6,NOW(),NOW()),
(228,'3-','3-',7,NOW(),NOW()),
(228,'4','4',8,NOW(),NOW()),
(234,'是','是',1,NOW(),NOW()),
(234,'否','否',2,NOW(),NOW()),
(235,'是','是',1,NOW(),NOW()),
(235,'否','否',2,NOW(),NOW()),
(236,'是','是',1,NOW(),NOW()),
(236,'否','否',2,NOW(),NOW()),
(237,'1','1',1,NOW(),NOW()),
(237,'2+','2+',2,NOW(),NOW()),
(237,'2','2',3,NOW(),NOW()),
(237,'2-','2-',4,NOW(),NOW()),
(237,'3+','3+',5,NOW(),NOW()),
(237,'3','3',6,NOW(),NOW()),
(237,'3-','3-',7,NOW(),NOW()),
(237,'4','4',8,NOW(),NOW()),
(243,'是','是',1,NOW(),NOW()),
(243,'否','否',2,NOW(),NOW()),
(244,'是','是',1,NOW(),NOW()),
(244,'否','否',2,NOW(),NOW()),
(245,'是','是',1,NOW(),NOW()),
(245,'否','否',2,NOW(),NOW()),
(246,'是','是',1,NOW(),NOW()),
(246,'否','否',2,NOW(),NOW()),
(252,'是','是',1,NOW(),NOW()),
(252,'否','否',2,NOW(),NOW()),
(253,'是','是',1,NOW(),NOW()),
(253,'否','否',2,NOW(),NOW()),
(254,'是','是',1,NOW(),NOW()),
(254,'否','否',2,NOW(),NOW()),
(255,'是','是',1,NOW(),NOW()),
(255,'否','否',2,NOW(),NOW()),
(260,'是','是',1,NOW(),NOW()),
(260,'否','否',2,NOW(),NOW()),
(261,'是','是',1,NOW(),NOW()),
(261,'否','否',2,NOW(),NOW()),
(262,'是','是',1,NOW(),NOW()),
(262,'否','否',2,NOW(),NOW()),
(263,'是','是',1,NOW(),NOW()),
(263,'否','否',2,NOW(),NOW()),
(264,'是','是',1,NOW(),NOW()),
(264,'否','否',2,NOW(),NOW()),
(265,'是','是',1,NOW(),NOW()),
(265,'否','否',2,NOW(),NOW()),
(266,'是','是',1,NOW(),NOW()),
(266,'否','否',2,NOW(),NOW()),
(269,'是','是',1,NOW(),NOW()),
(269,'否','否',2,NOW(),NOW()),
(270,'是','是',1,NOW(),NOW()),
(270,'否','否',2,NOW(),NOW()),
(272,'是','是',1,NOW(),NOW()),
(272,'否','否',2,NOW(),NOW()),
(273,'是','是',1,NOW(),NOW()),
(273,'否','否',2,NOW(),NOW()),
(274,'是','是',1,NOW(),NOW()),
(274,'否','否',2,NOW(),NOW()),
(275,'是','是',1,NOW(),NOW()),
(275,'否','否',2,NOW(),NOW()),
(276,'是','是',1,NOW(),NOW()),
(276,'否','否',2,NOW(),NOW()),
(277,'是','是',1,NOW(),NOW()),
(277,'否','否',2,NOW(),NOW()),
(278,'是','是',1,NOW(),NOW()),
(278,'否','否',2,NOW(),NOW()),
(281,'是','是',1,NOW(),NOW()),
(281,'否','否',2,NOW(),NOW()),
(282,'是','是',1,NOW(),NOW()),
(282,'否','否',2,NOW(),NOW()),
(284,'是','是',1,NOW(),NOW()),
(284,'否','否',2,NOW(),NOW()),
(285,'是','是',1,NOW(),NOW()),
(285,'否','否',2,NOW(),NOW()),
(286,'是','是',1,NOW(),NOW()),
(286,'否','否',2,NOW(),NOW()),
(287,'是','是',1,NOW(),NOW()),
(287,'否','否',2,NOW(),NOW()),
(290,'是','是',1,NOW(),NOW()),
(290,'否','否',2,NOW(),NOW()),
(291,'是','是',1,NOW(),NOW()),
(291,'否','否',2,NOW(),NOW()),
(292,'是','是',1,NOW(),NOW()),
(292,'否','否',2,NOW(),NOW()),
(297,'是','是',1,NOW(),NOW()),
(297,'否','否',2,NOW(),NOW()),
(298,'是','是',1,NOW(),NOW()),
(298,'否','否',2,NOW(),NOW()),
(299,'none','不限制',1,NOW(),NOW()),
(299,'any','任意研究报告',2,NOW(),NOW()),
(299,'internal','内部研究报告',3,NOW(),NOW()),
(301,'是','是',1,NOW(),NOW()),
(301,'否','否',2,NOW(),NOW()),
(302,'是','是',1,NOW(),NOW()),
(302,'否','否',2,NOW(),NOW()),
(303,'是','是',1,NOW(),NOW()),
(303,'否','否',2,NOW(),NOW()),
(308,'AAA','AAA',1,NOW(),NOW()),
(308,'AA+','AA+',2,NOW(),NOW()),
(308,'AA','AA',3,NOW(),NOW()),
(308,'AA-','AA-',4,NOW(),NOW()),
(308,'A+','A+',5,NOW(),NOW()),
(308,'A','A',6,NOW(),NOW()),
(308,'A-','A-',7,NOW(),NOW()),
(308,'BBB+','BBB+',8,NOW(),NOW()),
(308,'BBB','BBB',9,NOW(),NOW()),
(308,'BBB-','BBB-',10,NOW(),NOW()),
(308,'BB+','BB+',11,NOW(),NOW()),
(308,'BB','BB',12,NOW(),NOW()),
(308,'B','B',13,NOW(),NOW()),
(308,'CCC','CCC',14,NOW(),NOW()),
(308,'CC','CC',15,NOW(),NOW()),
(308,'C','C',16,NOW(),NOW()),
(309,'是','是',1,NOW(),NOW()),
(309,'否','否',2,NOW(),NOW());

INSERT INTO `rule_test_case` (id, case_name, rule_id, rule_name_snapshot, last_result, last_output, last_run_time, crte_time, updt_time) VALUES
(1,'未填写证券代码',1,'调入-池锁定','fail','证券代码不能为空',NOW(),NOW(),NOW()),
(2,'证券不存在',1,'调入-池锁定','fail','证券不存在',NOW(),NOW(),NOW()),
(3,'目标池不存在',1,'调入-池锁定','fail','目标投资池不存在',NOW(),NOW(),NOW()),
(4,'未锁定可通过',1,'调入-池锁定','pass','通过',NOW(),NOW(),NOW()),
(5,'已锁定禁止调入',1,'调入-池锁定','fail','目标投资池已锁定',NOW(),NOW(),NOW()),
(6,'债券在bond池内',2,'调入-投资品种','pass','通过',NOW(),NOW(),NOW()),
(7,'股票不在bond池',2,'调入-投资品种','fail','证券不在本池投资品种范围内',NOW(),NOW(),NOW()),
(8,'银行间匹配',3,'调入-投资市场','pass','通过',NOW(),NOW(),NOW()),
(9,'北交所不在范围内',3,'调入-投资市场','fail','证券不在本池投资市场范围内',NOW(),NOW(),NOW()),
(10,'无进行中流程',4,'调入-进行中流程','pass','通过',NOW(),NOW(),NOW()),
(11,'审批中禁止再调',4,'调入-进行中流程','fail','证券存在进行中的调库流程（当前节点：信用研究组）',NOW(),NOW(),NOW()),
(12,'不在目标池可调入',5,'调入-已在目标池','pass','通过',NOW(),NOW(),NOW()),
(13,'已在目标池重复调入',5,'调入-已在目标池','fail','证券已在目标投资池中',NOW(),NOW(),NOW()),
(14,'未达上限',6,'调入-持仓上限','pass','通过',NOW(),NOW(),NOW()),
(15,'已达上限',6,'调入-持仓上限','fail','目标投资池已达持仓上限（10）',NOW(),NOW(),NOW()),
(16,'已在来源池',7,'调入-来源池限制','pass','通过',NOW(),NOW(),NOW()),
(17,'未在来源池',7,'调入-来源池限制','fail','目标池配置了来源池限制，证券须先在以下池中：信用债大库/一级库',NOW(),NOW(),NOW()),
(18,'不在限制池',8,'调入-限制池','pass','通过',NOW(),NOW(),NOW()),
(19,'在限制池中',8,'调入-限制池','fail','证券当前在调入限制池中：债券禁止库',NOW(),NOW(),NOW()),
(20,'无互斥冲突',9,'调入-互斥冲突','pass','通过',NOW(),NOW(),NOW()),
(21,'同时勾选互斥池',9,'调入-互斥冲突','fail','与以下互斥池不可同时调入：专户产品/一级库',NOW(),NOW(),NOW()),
(22,'无弹性禁投',10,'调入-弹性禁投','pass','通过',NOW(),NOW(),NOW()),
(23,'弹性禁投警告',10,'调入-弹性禁投','pass','证券当前在调入弹性禁投池中：观察池',NOW(),NOW(),NOW()),
(24,'不在禁止池',11,'调入-全局禁止池','pass','通过',NOW(),NOW(),NOW()),
(25,'在禁止池',11,'调入-全局禁止池','fail','证券当前在禁止池中',NOW(),NOW(),NOW()),
(26,'开放日内',12,'调入-开放日','pass','通过',NOW(),NOW(),NOW()),
(27,'非开放日',12,'调入-开放日','fail','当前不在本池开放日内',NOW(),NOW(),NOW()),
(28,'行业一致',13,'调入-行业限制','pass','通过',NOW(),NOW(),NOW()),
(29,'行业不一致',13,'调入-行业限制','fail','证券行业与目标池行业配置不一致',NOW(),NOW(),NOW()),
(30,'未锁定可调出',14,'调出-池锁定','pass','通过',NOW(),NOW(),NOW()),
(31,'已锁定禁止调出',14,'调出-池锁定','fail','目标投资池已锁定',NOW(),NOW(),NOW()),
(32,'在池可调出',15,'调出-未在目标池','pass','通过',NOW(),NOW(),NOW()),
(33,'不在池无法调出',15,'调出-未在目标池','fail','证券当前不在目标投资池中',NOW(),NOW(),NOW()),
(34,'冻结期已过',16,'调出-冻结期','pass','通过',NOW(),NOW(),NOW()),
(35,'仍在冻结期',16,'调出-冻结期','fail','证券仍在目标投资池冻结期内',NOW(),NOW(),NOW()),
(36,'冻结期无入池时间',16,'调出-冻结期','fail','证券入池生效时间缺失',NOW(),NOW(),NOW()),
(37,'不在调出限制池',17,'调出-限制池','pass','通过',NOW(),NOW(),NOW()),
(38,'在调出限制池',17,'调出-限制池','fail','证券当前在调出限制池中：流通受限库',NOW(),NOW(),NOW()),
(39,'不在调出互斥池',18,'调出-互斥池','pass','通过',NOW(),NOW(),NOW()),
(40,'在调出互斥池',18,'调出-互斥池','fail','证券当前在调出互斥池中：专户产品/一级库',NOW(),NOW(),NOW()),
(41,'无同时调出互斥',19,'调出-互斥冲突','pass','通过',NOW(),NOW(),NOW()),
(42,'无弹性禁投调出',20,'调出-弹性禁投','pass','通过',NOW(),NOW(),NOW()),
(43,'开放日可调出',21,'调出-开放日','pass','通过',NOW(),NOW(),NOW()),
(44,'非开放日禁调出',21,'调出-开放日','fail','当前不在本池开放日内',NOW(),NOW(),NOW()),
(45,'未到期可调入',22,'债券到期-调入','pass','通过',NOW(),NOW(),NOW()),
(46,'已到期禁调入',22,'债券到期-调入','fail','债券已到期',NOW(),NOW(),NOW()),
(47,'未到期可调出',23,'债券到期-调出','pass','通过',NOW(),NOW(),NOW()),
(48,'已到期禁调出',23,'债券到期-调出','fail','债券已到期',NOW(),NOW(),NOW()),
(49,'未退市',24,'股票退市-调入','pass','通过',NOW(),NOW(),NOW()),
(50,'已退市',24,'股票退市-调入','fail','股票已退市',NOW(),NOW(),NOW()),
(51,'空实现跳过',25,'股票评级限制','pass','通过',NOW(),NOW(),NOW()),
(52,'评分落在区间',26,'基金评分限制','pass','通过',NOW(),NOW(),NOW()),
(53,'评分超出区间',26,'基金评分限制','fail','基金库的评分，必须在3<=#rate<=8',NOW(),NOW(),NOW()),
(54,'未传基金评分',26,'基金评分限制','fail','基金库的评分，必须在3<=#rate<=8',NOW(),NOW(),NOW()),
(55,'普通债矩阵允许池通过',27,'调入-主体债入库矩阵','pass','通过',NOW(),NOW(),NOW()),
(56,'放开规则跳过矩阵',27,'调入-主体债入库矩阵','pass','通过',NOW(),NOW(),NOW()),
(57,'可转债不能进分级库',27,'调入-主体债入库矩阵','fail','可转债、可交换债、信用风险缓释工具不适用信用债分级库',NOW(),NOW(),NOW()),
(58,'正式证券无内评',27,'调入-主体债入库矩阵','fail','未配置主体内评分档',NOW(),NOW(),NOW()),
(59,'ABS1档进二级库',27,'调入-主体债入库矩阵','fail','目标池「二级库」不在特殊债调整后的允许范围内（仅 1 级）',NOW(),NOW(),NOW()),
(60,'ABS1档进一级库',27,'调入-主体债入库矩阵','pass','通过',NOW(),NOW(),NOW()),
(61,'中票可进分级库',28,'矩阵-可转债CRMW排除','pass','通过',NOW(),NOW(),NOW()),
(62,'可转债排除',28,'矩阵-可转债CRMW排除','fail','可转债、可交换债、信用风险缓释工具不适用信用债分级库',NOW(),NOW(),NOW()),
(63,'已配置内评',29,'矩阵-未配置内评档','pass','通过',NOW(),NOW(),NOW()),
(64,'正式证券无内评分项',29,'矩阵-未配置内评档','fail','未配置主体内评分档',NOW(),NOW(),NOW()),
(65,'匹配到GT_5',30,'矩阵-期限档匹配','pass','通过',NOW(),NOW(),NOW()),
(66,'已配置允许池',31,'矩阵-允许池配置','pass','通过',NOW(),NOW(),NOW()),
(67,'未配置允许池',31,'矩阵-允许池配置','fail','主体债入库矩阵未配置允许池',NOW(),NOW(),NOW()),
(68,'ABS1档进一级库分项',32,'矩阵-ABS校验','pass','通过',NOW(),NOW(),NOW()),
(69,'ABS1档进二级库分项',32,'矩阵-ABS校验','fail','目标池「二级库」不在特殊债调整后的允许范围内（仅 1 级）',NOW(),NOW(),NOW()),
(70,'私募1档进一级库',33,'矩阵-私募债校验','pass','通过',NOW(),NOW(),NOW()),
(71,'私募非1档禁一级库',33,'矩阵-私募债校验','fail','目标池「一级库」不在特殊债调整后的允许范围内（仅 2 级及更差）',NOW(),NOW(),NOW()),
(72,'永续1档下调只留该档',34,'矩阵-永续债校验','pass','通过',NOW(),NOW(),NOW()),
(73,'永续1档仍进一级库',34,'矩阵-永续债校验','fail','目标池「一级库」不在特殊债调整后的允许范围内（仅 2 级）',NOW(),NOW(),NOW()),
(74,'次级2档下调只留该档',35,'矩阵-次级债校验','pass','通过',NOW(),NOW(),NOW()),
(75,'次级2档进三级库',35,'矩阵-次级债校验','fail','目标池「三级库」不在特殊债调整后的允许范围内（仅 2 级）',NOW(),NOW(),NOW()),
(76,'担保从最好档往下',36,'矩阵-担保债观察池','pass','通过',NOW(),NOW(),NOW()),
(77,'担保高于最好档',36,'矩阵-担保债观察池','fail','目标池「一级库」不在特殊债调整后的允许范围内（仅 2 级及更差）',NOW(),NOW(),NOW()),
(78,'未在库禁止新增',37,'矩阵-重点观察名单','fail','重点观察名单原则上不得新增入库信用债分级库',NOW(),NOW(),NOW()),
(79,'强担保豁免',37,'矩阵-重点观察名单','pass','通过',NOW(),NOW(),NOW()),
(80,'永续加担保覆盖为担保',38,'矩阵-特殊债类型覆盖','pass','guaranteed',NOW(),NOW(),NOW()),
(81,'ABS优先于私募',38,'矩阵-特殊债类型覆盖','pass','abs',NOW(),NOW(),NOW()),
(82,'当前未配置白名单池',39,'流程-白名单命中','pass','未命中:白名单池未配置，主体在白名单池条件不成立;',NOW(),NOW(),NOW()),
(83,'已配置且条件满足命中白名单',39,'流程-白名单命中','pass','命中白名单',NOW(),NOW(),NOW()),
(84,'ABS未命中白名单',39,'流程-白名单命中','pass','未命中:债券为 ABS 债，不符合白名单条件;',NOW(),NOW(),NOW()),
(85,'一级库命中简易',40,'流程-简易命中','pass','命中简易流程',NOW(),NOW(),NOW()),
(86,'五级库不走简易',40,'流程-简易命中','pass','未命中:目标池不是信用债大库一、二、三级库;',NOW(),NOW(),NOW()),
(87,'三级调一级为上调',41,'流程-信用债升降级','pass','upgradeInbound',NOW(),NOW(),NOW()),
(88,'一级调三级为下调',41,'流程-信用债升降级','pass','downgradeInbound',NOW(),NOW(),NOW()),
(89,'不限制跳过',42,'提交-研究报告必填','pass','通过',NOW(),NOW(),NOW()),
(90,'any未上传报告',42,'提交-研究报告必填','fail','目标池[一级库]要求研究报告，请上传或选择报告',NOW(),NOW(),NOW()),
(91,'未填证券代码不出池',43,'自动-到期出池','fail','证券代码不能为空',NOW(),NOW(),NOW()),
(92,'未到期不出池',43,'自动-到期出池','pass','通过',NOW(),NOW(),NOW()),
(93,'已到期应出池',43,'自动-到期出池','fail','证券到期自动调出',NOW(),NOW(),NOW()),
(94,'未填主体代码',44,'自动-主体外评AA-入池','fail','主体代码不能为空',NOW(),NOW(),NOW()),
(95,'AA-应入池',44,'自动-主体外评AA-入池','pass','外评AA-及以下主体自动入池',NOW(),NOW(),NOW()),
(96,'A+应入池',44,'自动-主体外评AA-入池','pass','外评AA-及以下主体自动入池',NOW(),NOW(),NOW()),
(97,'AAA不自动入',44,'自动-主体外评AA-入池','pass','通过',NOW(),NOW(),NOW());

INSERT INTO `rule_test_case_param` (id, case_id, param_name, param_label_snapshot, param_type_snapshot, param_value, crte_time, updt_time) VALUES
(1,1,'securityCode','证券代码','string','',NOW(),NOW()),
(2,1,'targetPoolId','目标投资池ID','string','',NOW(),NOW()),
(3,1,'securityExists','证券是否存在','select','',NOW(),NOW()),
(4,1,'poolExists','目标池是否存在','select','',NOW(),NOW()),
(5,1,'lockFlag','目标池是否锁定','select','',NOW(),NOW()),
(6,2,'securityCode','证券代码','string','NO_SUCH',NOW(),NOW()),
(7,2,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(8,2,'securityExists','证券是否存在','select','否',NOW(),NOW()),
(9,2,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(10,2,'lockFlag','目标池是否锁定','select','否',NOW(),NOW()),
(11,3,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(12,3,'targetPoolId','目标投资池ID','string','99999',NOW(),NOW()),
(13,3,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(14,3,'poolExists','目标池是否存在','select','否',NOW(),NOW()),
(15,3,'lockFlag','目标池是否锁定','select','否',NOW(),NOW()),
(16,4,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(17,4,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(18,4,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(19,4,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(20,4,'lockFlag','目标池是否锁定','select','否',NOW(),NOW()),
(21,5,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(22,5,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(23,5,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(24,5,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(25,5,'lockFlag','目标池是否锁定','select','是',NOW(),NOW()),
(26,6,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(27,6,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(28,6,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(29,6,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(30,6,'varietyCodes','池投资品种JSON','string','["bond"]',NOW(),NOW()),
(31,6,'categoryType','证券品种大类','select','bond',NOW(),NOW()),
(32,7,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(33,7,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(34,7,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(35,7,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(36,7,'varietyCodes','池投资品种JSON','string','["bond"]',NOW(),NOW()),
(37,7,'categoryType','证券品种大类','select','stock',NOW(),NOW()),
(38,8,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(39,8,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(40,8,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(41,8,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(42,8,'marketCodes','池投资市场JSON','string','["CIBM","SSE"]',NOW(),NOW()),
(43,8,'securityMarkets','证券所在市场','select','CIBM',NOW(),NOW()),
(44,9,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(45,9,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(46,9,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(47,9,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(48,9,'marketCodes','池投资市场JSON','string','["CIBM","SSE"]',NOW(),NOW()),
(49,9,'securityMarkets','证券所在市场','select','BSE',NOW(),NOW()),
(50,10,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(51,10,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(52,10,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(53,10,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(54,10,'hasPendingProcess','是否有进行中流程','select','否',NOW(),NOW()),
(55,10,'pendingNodeLabel','当前节点名称','string','',NOW(),NOW()),
(56,11,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(57,11,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(58,11,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(59,11,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(60,11,'hasPendingProcess','是否有进行中流程','select','是',NOW(),NOW()),
(61,11,'pendingNodeLabel','当前节点名称','string','信用研究组',NOW(),NOW()),
(62,12,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(63,12,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(64,12,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(65,12,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(66,12,'alreadyInTargetPool','是否已在目标池','select','否',NOW(),NOW()),
(67,13,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(68,13,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(69,13,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(70,13,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(71,13,'alreadyInTargetPool','是否已在目标池','select','是',NOW(),NOW()),
(72,14,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(73,14,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(74,14,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(75,14,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(76,14,'maxCapacity','持仓上限','number','100',NOW(),NOW()),
(77,14,'currentCount','当前在池数量','number','20',NOW(),NOW()),
(78,15,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(79,15,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(80,15,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(81,15,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(82,15,'maxCapacity','持仓上限','number','10',NOW(),NOW()),
(83,15,'currentCount','当前在池数量','number','10',NOW(),NOW()),
(84,16,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(85,16,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(86,16,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(87,16,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(88,16,'hasSourceLimit','是否配置来源池','select','是',NOW(),NOW()),
(89,16,'inSourcePool','是否已在/本批调入来源池','select','是',NOW(),NOW()),
(90,16,'sourcePoolNames','来源池名称','string','信用债大库/一级库',NOW(),NOW()),
(91,17,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(92,17,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(93,17,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(94,17,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(95,17,'hasSourceLimit','是否配置来源池','select','是',NOW(),NOW()),
(96,17,'inSourcePool','是否已在/本批调入来源池','select','否',NOW(),NOW()),
(97,17,'sourcePoolNames','来源池名称','string','信用债大库/一级库',NOW(),NOW()),
(98,18,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(99,18,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(100,18,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(101,18,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(102,18,'inRestrictPool','是否在调入限制池','select','否',NOW(),NOW()),
(103,18,'restrictPoolNames','限制池名称','string','',NOW(),NOW()),
(104,19,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(105,19,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(106,19,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(107,19,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(108,19,'inRestrictPool','是否在调入限制池','select','是',NOW(),NOW()),
(109,19,'restrictPoolNames','限制池名称','string','债券禁止库',NOW(),NOW()),
(110,20,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(111,20,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(112,20,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(113,20,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(114,20,'hasMutexConflict','本批是否同时勾选互斥池','select','否',NOW(),NOW()),
(115,20,'mutexPoolNames','互斥池名称','string','',NOW(),NOW()),
(116,21,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(117,21,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(118,21,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(119,21,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(120,21,'hasMutexConflict','本批是否同时勾选互斥池','select','是',NOW(),NOW()),
(121,21,'mutexPoolNames','互斥池名称','string','专户产品/一级库',NOW(),NOW()),
(122,22,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(123,22,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(124,22,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(125,22,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(126,22,'inSoftRestrictPool','是否在弹性禁投池','select','否',NOW(),NOW()),
(127,22,'softRestrictPoolNames','弹性禁投池名称','string','',NOW(),NOW()),
(128,23,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(129,23,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(130,23,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(131,23,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(132,23,'inSoftRestrictPool','是否在弹性禁投池','select','是',NOW(),NOW()),
(133,23,'softRestrictPoolNames','弹性禁投池名称','string','观察池',NOW(),NOW()),
(134,24,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(135,24,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(136,24,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(137,24,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(138,24,'inForbiddenPool','是否在全局禁止池','select','否',NOW(),NOW()),
(139,25,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(140,25,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(141,25,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(142,25,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(143,25,'inForbiddenPool','是否在全局禁止池','select','是',NOW(),NOW()),
(144,26,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(145,26,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(146,26,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(147,26,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(148,26,'openDayAdjust','是否启用开放日','select','是',NOW(),NOW()),
(149,26,'inOpenDay','当日是否在开放区间','select','是',NOW(),NOW()),
(150,27,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(151,27,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(152,27,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(153,27,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(154,27,'openDayAdjust','是否启用开放日','select','是',NOW(),NOW()),
(155,27,'inOpenDay','当日是否在开放区间','select','否',NOW(),NOW()),
(156,28,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(157,28,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(158,28,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(159,28,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(160,28,'hasIndustryLimit','是否配置行业限制','select','是',NOW(),NOW()),
(161,28,'industryExponent','行业指数模式','number','0',NOW(),NOW()),
(162,28,'securityIndustry','证券行业','string','城投',NOW(),NOW()),
(163,28,'poolIndustry','池行业配置','string','城投',NOW(),NOW()),
(164,29,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(165,29,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(166,29,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(167,29,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(168,29,'hasIndustryLimit','是否配置行业限制','select','是',NOW(),NOW()),
(169,29,'industryExponent','行业指数模式','number','0',NOW(),NOW()),
(170,29,'securityIndustry','证券行业','string','地产',NOW(),NOW()),
(171,29,'poolIndustry','池行业配置','string','城投',NOW(),NOW()),
(172,30,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(173,30,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(174,30,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(175,30,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(176,30,'lockFlag','目标池是否锁定','select','否',NOW(),NOW()),
(177,31,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(178,31,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(179,31,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(180,31,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(181,31,'lockFlag','目标池是否锁定','select','是',NOW(),NOW()),
(182,32,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(183,32,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(184,32,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(185,32,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(186,32,'alreadyInTargetPool','是否已在目标池','select','是',NOW(),NOW()),
(187,33,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(188,33,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(189,33,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(190,33,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(191,33,'alreadyInTargetPool','是否已在目标池','select','否',NOW(),NOW()),
(192,34,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(193,34,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(194,34,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(195,34,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(196,34,'frozenPeriodIn','冻结期天数','number','30',NOW(),NOW()),
(197,34,'hasEntryTime','是否有入池时间','select','是',NOW(),NOW()),
(198,34,'stillFrozen','是否仍在冻结期','select','否',NOW(),NOW()),
(199,35,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(200,35,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(201,35,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(202,35,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(203,35,'frozenPeriodIn','冻结期天数','number','30',NOW(),NOW()),
(204,35,'hasEntryTime','是否有入池时间','select','是',NOW(),NOW()),
(205,35,'stillFrozen','是否仍在冻结期','select','是',NOW(),NOW()),
(206,36,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(207,36,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(208,36,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(209,36,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(210,36,'frozenPeriodIn','冻结期天数','number','30',NOW(),NOW()),
(211,36,'hasEntryTime','是否有入池时间','select','否',NOW(),NOW()),
(212,36,'stillFrozen','是否仍在冻结期','select','否',NOW(),NOW()),
(213,37,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(214,37,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(215,37,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(216,37,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(217,37,'inOutRestrictPool','是否在调出限制池','select','否',NOW(),NOW()),
(218,37,'restrictPoolNames','限制池名称','string','',NOW(),NOW()),
(219,38,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(220,38,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(221,38,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(222,38,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(223,38,'inOutRestrictPool','是否在调出限制池','select','是',NOW(),NOW()),
(224,38,'restrictPoolNames','限制池名称','string','流通受限库',NOW(),NOW()),
(225,39,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(226,39,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(227,39,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(228,39,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(229,39,'inOutMutexPool','是否在调出互斥池','select','否',NOW(),NOW()),
(230,39,'mutexPoolNames','互斥池名称','string','',NOW(),NOW()),
(231,40,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(232,40,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(233,40,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(234,40,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(235,40,'inOutMutexPool','是否在调出互斥池','select','是',NOW(),NOW()),
(236,40,'mutexPoolNames','互斥池名称','string','专户产品/一级库',NOW(),NOW()),
(237,41,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(238,41,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(239,41,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(240,41,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(241,41,'hasMutexConflict','本批是否同时调出互斥池','select','否',NOW(),NOW()),
(242,41,'mutexPoolNames','互斥池名称','string','',NOW(),NOW()),
(243,42,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(244,42,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(245,42,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(246,42,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(247,42,'inSoftRestrictPool','是否在弹性禁投池','select','否',NOW(),NOW()),
(248,42,'softRestrictPoolNames','弹性禁投池名称','string','',NOW(),NOW()),
(249,43,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(250,43,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(251,43,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(252,43,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(253,43,'openDayAdjust','是否启用开放日','select','是',NOW(),NOW()),
(254,43,'inOpenDay','当日是否在开放区间','select','是',NOW(),NOW()),
(255,44,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(256,44,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(257,44,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(258,44,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(259,44,'openDayAdjust','是否启用开放日','select','是',NOW(),NOW()),
(260,44,'inOpenDay','当日是否在开放区间','select','否',NOW(),NOW()),
(261,45,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(262,45,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(263,45,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(264,45,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(265,45,'maturityDate','到期日yyyyMMdd','string','20281231',NOW(),NOW()),
(266,45,'today','今日yyyyMMdd','string','20260301',NOW(),NOW()),
(267,46,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(268,46,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(269,46,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(270,46,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(271,46,'maturityDate','到期日yyyyMMdd','string','20250101',NOW(),NOW()),
(272,46,'today','今日yyyyMMdd','string','20260301',NOW(),NOW()),
(273,47,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(274,47,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(275,47,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(276,47,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(277,47,'maturityDate','到期日yyyyMMdd','string','20281231',NOW(),NOW()),
(278,47,'today','今日yyyyMMdd','string','20260301',NOW(),NOW()),
(279,48,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(280,48,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(281,48,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(282,48,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(283,48,'maturityDate','到期日yyyyMMdd','string','20250101',NOW(),NOW()),
(284,48,'today','今日yyyyMMdd','string','20260301',NOW(),NOW()),
(285,49,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(286,49,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(287,49,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(288,49,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(289,49,'delistDate','退市日yyyyMMdd','string','',NOW(),NOW()),
(290,49,'today','今日yyyyMMdd','string','20260301',NOW(),NOW()),
(291,50,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(292,50,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(293,50,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(294,50,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(295,50,'delistDate','退市日yyyyMMdd','string','20240101',NOW(),NOW()),
(296,50,'today','今日yyyyMMdd','string','20260301',NOW(),NOW()),
(297,51,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(298,51,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(299,51,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(300,51,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(301,51,'gradeAstrict','池评级限制表达式','string','买入,增持',NOW(),NOW()),
(302,52,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(303,52,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(304,52,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(305,52,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(306,52,'fundRateLimit','评分限制原文','string','3<=#rate<=8',NOW(),NOW()),
(307,52,'poolName','目标池名称','string','基金库',NOW(),NOW()),
(308,52,'fundRate','请求基金评分','number','5',NOW(),NOW()),
(309,52,'minRate','下限','number','3',NOW(),NOW()),
(310,52,'maxRate','上限','number','8',NOW(),NOW()),
(311,53,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(312,53,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(313,53,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(314,53,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(315,53,'fundRateLimit','评分限制原文','string','3<=#rate<=8',NOW(),NOW()),
(316,53,'poolName','目标池名称','string','基金库',NOW(),NOW()),
(317,53,'fundRate','请求基金评分','number','9',NOW(),NOW()),
(318,53,'minRate','下限','number','3',NOW(),NOW()),
(319,53,'maxRate','上限','number','8',NOW(),NOW()),
(320,54,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(321,54,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(322,54,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(323,54,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(324,54,'fundRateLimit','评分限制原文','string','3<=#rate<=8',NOW(),NOW()),
(325,54,'poolName','目标池名称','string','基金库',NOW(),NOW()),
(326,54,'fundRate','请求基金评分','number','',NOW(),NOW()),
(327,54,'minRate','下限','number','3',NOW(),NOW()),
(328,54,'maxRate','上限','number','8',NOW(),NOW()),
(329,55,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(330,55,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(331,55,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(332,55,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(333,55,'releaseRules','是否放开规则','select','否',NOW(),NOW()),
(334,55,'isGradedBondPool','是否信用债分级库','select','是',NOW(),NOW()),
(335,55,'securityType','证券类型','select','mtn',NOW(),NOW()),
(336,55,'inRestricted','是否在重点观察名单','select','否',NOW(),NOW()),
(337,55,'hasStrongGuarantee','是否强担保豁免','select','否',NOW(),NOW()),
(338,55,'currentGradedSort','当前分级库档0=未在库','number','0',NOW(),NOW()),
(339,55,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(340,55,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(341,55,'isTempCode','是否临时代码','select','否',NOW(),NOW()),
(342,55,'innerIssuerRating','发债主体内评','select','2',NOW(),NOW()),
(343,55,'innerGuarantorRating','担保人内评','select','',NOW(),NOW()),
(344,55,'matchedBucket','匹配到的期限档','select','GT_5',NOW(),NOW()),
(345,55,'hasAllowedPools','矩阵是否配置允许池','select','是',NOW(),NOW()),
(346,55,'allowedPoolNames','矩阵允许池名称','string','一级库、二级库',NOW(),NOW()),
(347,55,'targetInAllowedPools','目标是否在矩阵允许池','select','是',NOW(),NOW()),
(348,55,'isAbs','是否ABS','select','否',NOW(),NOW()),
(349,55,'isPrivate','是否私募','select','否',NOW(),NOW()),
(350,55,'isSubordinated','是否次级','select','否',NOW(),NOW()),
(351,55,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(352,55,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(353,55,'inObserve','是否在观察池','select','否',NOW(),NOW()),
(354,55,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(355,56,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(356,56,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(357,56,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(358,56,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(359,56,'releaseRules','是否放开规则','select','是',NOW(),NOW()),
(360,56,'isGradedBondPool','是否信用债分级库','select','是',NOW(),NOW()),
(361,56,'securityType','证券类型','select','mtn',NOW(),NOW()),
(362,56,'inRestricted','是否在重点观察名单','select','否',NOW(),NOW()),
(363,56,'hasStrongGuarantee','是否强担保豁免','select','否',NOW(),NOW()),
(364,56,'currentGradedSort','当前分级库档0=未在库','number','0',NOW(),NOW()),
(365,56,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(366,56,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(367,56,'isTempCode','是否临时代码','select','否',NOW(),NOW()),
(368,56,'innerIssuerRating','发债主体内评','select','2',NOW(),NOW()),
(369,56,'innerGuarantorRating','担保人内评','select','',NOW(),NOW()),
(370,56,'matchedBucket','匹配到的期限档','select','GT_5',NOW(),NOW()),
(371,56,'hasAllowedPools','矩阵是否配置允许池','select','是',NOW(),NOW()),
(372,56,'allowedPoolNames','矩阵允许池名称','string','一级库、二级库',NOW(),NOW()),
(373,56,'targetInAllowedPools','目标是否在矩阵允许池','select','是',NOW(),NOW()),
(374,56,'isAbs','是否ABS','select','否',NOW(),NOW()),
(375,56,'isPrivate','是否私募','select','否',NOW(),NOW()),
(376,56,'isSubordinated','是否次级','select','否',NOW(),NOW()),
(377,56,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(378,56,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(379,56,'inObserve','是否在观察池','select','否',NOW(),NOW()),
(380,56,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(381,57,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(382,57,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(383,57,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(384,57,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(385,57,'releaseRules','是否放开规则','select','否',NOW(),NOW()),
(386,57,'isGradedBondPool','是否信用债分级库','select','是',NOW(),NOW()),
(387,57,'securityType','证券类型','select','convertible_bond',NOW(),NOW()),
(388,57,'inRestricted','是否在重点观察名单','select','否',NOW(),NOW()),
(389,57,'hasStrongGuarantee','是否强担保豁免','select','否',NOW(),NOW()),
(390,57,'currentGradedSort','当前分级库档0=未在库','number','0',NOW(),NOW()),
(391,57,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(392,57,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(393,57,'isTempCode','是否临时代码','select','否',NOW(),NOW()),
(394,57,'innerIssuerRating','发债主体内评','select','2',NOW(),NOW()),
(395,57,'innerGuarantorRating','担保人内评','select','',NOW(),NOW()),
(396,57,'matchedBucket','匹配到的期限档','select','GT_5',NOW(),NOW()),
(397,57,'hasAllowedPools','矩阵是否配置允许池','select','是',NOW(),NOW()),
(398,57,'allowedPoolNames','矩阵允许池名称','string','一级库、二级库',NOW(),NOW()),
(399,57,'targetInAllowedPools','目标是否在矩阵允许池','select','是',NOW(),NOW()),
(400,57,'isAbs','是否ABS','select','否',NOW(),NOW()),
(401,57,'isPrivate','是否私募','select','否',NOW(),NOW()),
(402,57,'isSubordinated','是否次级','select','否',NOW(),NOW()),
(403,57,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(404,57,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(405,57,'inObserve','是否在观察池','select','否',NOW(),NOW()),
(406,57,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(407,58,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(408,58,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(409,58,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(410,58,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(411,58,'releaseRules','是否放开规则','select','否',NOW(),NOW()),
(412,58,'isGradedBondPool','是否信用债分级库','select','是',NOW(),NOW()),
(413,58,'securityType','证券类型','select','mtn',NOW(),NOW()),
(414,58,'inRestricted','是否在重点观察名单','select','否',NOW(),NOW()),
(415,58,'hasStrongGuarantee','是否强担保豁免','select','否',NOW(),NOW()),
(416,58,'currentGradedSort','当前分级库档0=未在库','number','0',NOW(),NOW()),
(417,58,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(418,58,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(419,58,'isTempCode','是否临时代码','select','否',NOW(),NOW()),
(420,58,'innerIssuerRating','发债主体内评','select','',NOW(),NOW()),
(421,58,'innerGuarantorRating','担保人内评','select','',NOW(),NOW()),
(422,58,'matchedBucket','匹配到的期限档','select','GT_5',NOW(),NOW()),
(423,58,'hasAllowedPools','矩阵是否配置允许池','select','是',NOW(),NOW()),
(424,58,'allowedPoolNames','矩阵允许池名称','string','一级库、二级库',NOW(),NOW()),
(425,58,'targetInAllowedPools','目标是否在矩阵允许池','select','是',NOW(),NOW()),
(426,58,'isAbs','是否ABS','select','否',NOW(),NOW()),
(427,58,'isPrivate','是否私募','select','否',NOW(),NOW()),
(428,58,'isSubordinated','是否次级','select','否',NOW(),NOW()),
(429,58,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(430,58,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(431,58,'inObserve','是否在观察池','select','否',NOW(),NOW()),
(432,58,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(433,59,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(434,59,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(435,59,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(436,59,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(437,59,'releaseRules','是否放开规则','select','否',NOW(),NOW()),
(438,59,'isGradedBondPool','是否信用债分级库','select','是',NOW(),NOW()),
(439,59,'securityType','证券类型','select','mtn',NOW(),NOW()),
(440,59,'inRestricted','是否在重点观察名单','select','否',NOW(),NOW()),
(441,59,'hasStrongGuarantee','是否强担保豁免','select','否',NOW(),NOW()),
(442,59,'currentGradedSort','当前分级库档0=未在库','number','0',NOW(),NOW()),
(443,59,'targetPoolLevel','目标分级库档','number','2',NOW(),NOW()),
(444,59,'targetPoolName','目标池名称','string','二级库',NOW(),NOW()),
(445,59,'isTempCode','是否临时代码','select','否',NOW(),NOW()),
(446,59,'innerIssuerRating','发债主体内评','select','2',NOW(),NOW()),
(447,59,'innerGuarantorRating','担保人内评','select','1',NOW(),NOW()),
(448,59,'matchedBucket','匹配到的期限档','select','GT_5',NOW(),NOW()),
(449,59,'hasAllowedPools','矩阵是否配置允许池','select','是',NOW(),NOW()),
(450,59,'allowedPoolNames','矩阵允许池名称','string','一级库、二级库',NOW(),NOW()),
(451,59,'targetInAllowedPools','目标是否在矩阵允许池','select','否',NOW(),NOW()),
(452,59,'isAbs','是否ABS','select','是',NOW(),NOW()),
(453,59,'isPrivate','是否私募','select','否',NOW(),NOW()),
(454,59,'isSubordinated','是否次级','select','否',NOW(),NOW()),
(455,59,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(456,59,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(457,59,'inObserve','是否在观察池','select','否',NOW(),NOW()),
(458,59,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(459,60,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(460,60,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(461,60,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(462,60,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(463,60,'releaseRules','是否放开规则','select','否',NOW(),NOW()),
(464,60,'isGradedBondPool','是否信用债分级库','select','是',NOW(),NOW()),
(465,60,'securityType','证券类型','select','mtn',NOW(),NOW()),
(466,60,'inRestricted','是否在重点观察名单','select','否',NOW(),NOW()),
(467,60,'hasStrongGuarantee','是否强担保豁免','select','否',NOW(),NOW()),
(468,60,'currentGradedSort','当前分级库档0=未在库','number','0',NOW(),NOW()),
(469,60,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(470,60,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(471,60,'isTempCode','是否临时代码','select','否',NOW(),NOW()),
(472,60,'innerIssuerRating','发债主体内评','select','2',NOW(),NOW()),
(473,60,'innerGuarantorRating','担保人内评','select','1',NOW(),NOW()),
(474,60,'matchedBucket','匹配到的期限档','select','GT_5',NOW(),NOW()),
(475,60,'hasAllowedPools','矩阵是否配置允许池','select','是',NOW(),NOW()),
(476,60,'allowedPoolNames','矩阵允许池名称','string','一级库、二级库',NOW(),NOW()),
(477,60,'targetInAllowedPools','目标是否在矩阵允许池','select','是',NOW(),NOW()),
(478,60,'isAbs','是否ABS','select','是',NOW(),NOW()),
(479,60,'isPrivate','是否私募','select','否',NOW(),NOW()),
(480,60,'isSubordinated','是否次级','select','否',NOW(),NOW()),
(481,60,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(482,60,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(483,60,'inObserve','是否在观察池','select','否',NOW(),NOW()),
(484,60,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(485,61,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(486,61,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(487,61,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(488,61,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(489,61,'securityType','证券类型','select','mtn',NOW(),NOW()),
(490,62,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(491,62,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(492,62,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(493,62,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(494,62,'securityType','证券类型','select','convertible_bond',NOW(),NOW()),
(495,63,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(496,63,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(497,63,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(498,63,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(499,63,'isTempCode','是否临时代码','select','否',NOW(),NOW()),
(500,63,'innerIssuerRating','发债主体内评','select','2',NOW(),NOW()),
(501,64,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(502,64,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(503,64,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(504,64,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(505,64,'isTempCode','是否临时代码','select','否',NOW(),NOW()),
(506,64,'innerIssuerRating','发债主体内评','select','',NOW(),NOW()),
(507,65,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(508,65,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(509,65,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(510,65,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(511,65,'matchedBucket','匹配到的期限档','select','GT_5',NOW(),NOW()),
(512,66,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(513,66,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(514,66,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(515,66,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(516,66,'hasAllowedPools','矩阵是否配置允许池','select','是',NOW(),NOW()),
(517,67,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(518,67,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(519,67,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(520,67,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(521,67,'hasAllowedPools','矩阵是否配置允许池','select','否',NOW(),NOW()),
(522,68,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(523,68,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(524,68,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(525,68,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(526,68,'isAbs','是否ABS','select','是',NOW(),NOW()),
(527,68,'innerGuarantorRating','担保人内评','select','1',NOW(),NOW()),
(528,68,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(529,68,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(530,68,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(531,69,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(532,69,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(533,69,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(534,69,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(535,69,'isAbs','是否ABS','select','是',NOW(),NOW()),
(536,69,'innerGuarantorRating','担保人内评','select','1',NOW(),NOW()),
(537,69,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(538,69,'targetPoolLevel','目标分级库档','number','2',NOW(),NOW()),
(539,69,'targetPoolName','目标池名称','string','二级库',NOW(),NOW()),
(540,70,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(541,70,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(542,70,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(543,70,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(544,70,'isPrivate','是否私募','select','是',NOW(),NOW()),
(545,70,'innerIssuerRating','发债主体内评','select','1',NOW(),NOW()),
(546,70,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(547,70,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(548,70,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(549,71,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(550,71,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(551,71,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(552,71,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(553,71,'isPrivate','是否私募','select','是',NOW(),NOW()),
(554,71,'innerIssuerRating','发债主体内评','select','3',NOW(),NOW()),
(555,71,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(556,71,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(557,71,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(558,72,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(559,72,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(560,72,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(561,72,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(562,72,'isPerpetual','是否永续','select','是',NOW(),NOW()),
(563,72,'innerIssuerRating','发债主体内评','select','1',NOW(),NOW()),
(564,72,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(565,72,'targetPoolLevel','目标分级库档','number','2',NOW(),NOW()),
(566,72,'targetPoolName','目标池名称','string','二级库',NOW(),NOW()),
(567,73,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(568,73,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(569,73,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(570,73,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(571,73,'isPerpetual','是否永续','select','是',NOW(),NOW()),
(572,73,'innerIssuerRating','发债主体内评','select','1',NOW(),NOW()),
(573,73,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(574,73,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(575,73,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(576,74,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(577,74,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(578,74,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(579,74,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(580,74,'isSubordinated','是否次级','select','是',NOW(),NOW()),
(581,74,'innerIssuerRating','发债主体内评','select','2',NOW(),NOW()),
(582,74,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(583,74,'targetPoolLevel','目标分级库档','number','2',NOW(),NOW()),
(584,74,'targetPoolName','目标池名称','string','二级库',NOW(),NOW()),
(585,75,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(586,75,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(587,75,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(588,75,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(589,75,'isSubordinated','是否次级','select','是',NOW(),NOW()),
(590,75,'innerIssuerRating','发债主体内评','select','2',NOW(),NOW()),
(591,75,'matrixBestLevel','矩阵最好档','number','1',NOW(),NOW()),
(592,75,'targetPoolLevel','目标分级库档','number','3',NOW(),NOW()),
(593,75,'targetPoolName','目标池名称','string','三级库',NOW(),NOW()),
(594,76,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(595,76,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(596,76,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(597,76,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(598,76,'isGuaranteed','是否担保债','select','是',NOW(),NOW()),
(599,76,'inObserve','是否在观察池','select','否',NOW(),NOW()),
(600,76,'matrixBestLevel','矩阵最好档','number','2',NOW(),NOW()),
(601,76,'targetPoolLevel','目标分级库档','number','3',NOW(),NOW()),
(602,76,'targetPoolName','目标池名称','string','三级库',NOW(),NOW()),
(603,77,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(604,77,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(605,77,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(606,77,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(607,77,'isGuaranteed','是否担保债','select','是',NOW(),NOW()),
(608,77,'inObserve','是否在观察池','select','否',NOW(),NOW()),
(609,77,'matrixBestLevel','矩阵最好档','number','2',NOW(),NOW()),
(610,77,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(611,77,'targetPoolName','目标池名称','string','一级库',NOW(),NOW()),
(612,78,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(613,78,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(614,78,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(615,78,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(616,78,'inRestricted','是否在重点观察名单','select','是',NOW(),NOW()),
(617,78,'hasStrongGuarantee','是否强担保豁免','select','否',NOW(),NOW()),
(618,78,'currentGradedSort','当前分级库档0=未在库','number','0',NOW(),NOW()),
(619,78,'targetPoolLevel','目标分级库档','number','2',NOW(),NOW()),
(620,79,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(621,79,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(622,79,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(623,79,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(624,79,'inRestricted','是否在重点观察名单','select','是',NOW(),NOW()),
(625,79,'hasStrongGuarantee','是否强担保豁免','select','是',NOW(),NOW()),
(626,79,'currentGradedSort','当前分级库档0=未在库','number','0',NOW(),NOW()),
(627,79,'targetPoolLevel','目标分级库档','number','1',NOW(),NOW()),
(628,80,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(629,80,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(630,80,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(631,80,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(632,80,'isAbs','是否ABS','select','否',NOW(),NOW()),
(633,80,'isPrivate','是否私募','select','否',NOW(),NOW()),
(634,80,'isSubordinated','是否次级','select','否',NOW(),NOW()),
(635,80,'isPerpetual','是否永续','select','是',NOW(),NOW()),
(636,80,'isGuaranteed','是否担保','select','是',NOW(),NOW()),
(637,81,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(638,81,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(639,81,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(640,81,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(641,81,'isAbs','是否ABS','select','是',NOW(),NOW()),
(642,81,'isPrivate','是否私募','select','是',NOW(),NOW()),
(643,81,'isSubordinated','是否次级','select','否',NOW(),NOW()),
(644,81,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(645,81,'isGuaranteed','是否担保','select','否',NOW(),NOW()),
(646,82,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(647,82,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(648,82,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(649,82,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(650,82,'remainDays','剩余期限天','number','800',NOW(),NOW()),
(651,82,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(652,82,'isAbs','是否ABS','select','否',NOW(),NOW()),
(653,82,'isPrivate','是否私募','select','否',NOW(),NOW()),
(654,82,'isBond','是否债券类','select','是',NOW(),NOW()),
(655,82,'whitelistPoolConfigured','白名单池是否已配置','select','否',NOW(),NOW()),
(656,82,'inWhitelistPool','主体是否在白名单池','select','是',NOW(),NOW()),
(657,82,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(658,83,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(659,83,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(660,83,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(661,83,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(662,83,'remainDays','剩余期限天','number','800',NOW(),NOW()),
(663,83,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(664,83,'isAbs','是否ABS','select','否',NOW(),NOW()),
(665,83,'isPrivate','是否私募','select','否',NOW(),NOW()),
(666,83,'isBond','是否债券类','select','是',NOW(),NOW()),
(667,83,'whitelistPoolConfigured','白名单池是否已配置','select','是',NOW(),NOW()),
(668,83,'inWhitelistPool','主体是否在白名单池','select','是',NOW(),NOW()),
(669,83,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(670,84,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(671,84,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(672,84,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(673,84,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(674,84,'remainDays','剩余期限天','number','800',NOW(),NOW()),
(675,84,'isPerpetual','是否永续','select','否',NOW(),NOW()),
(676,84,'isAbs','是否ABS','select','是',NOW(),NOW()),
(677,84,'isPrivate','是否私募','select','否',NOW(),NOW()),
(678,84,'isBond','是否债券类','select','是',NOW(),NOW()),
(679,84,'whitelistPoolConfigured','白名单池是否已配置','select','是',NOW(),NOW()),
(680,84,'inWhitelistPool','主体是否在白名单池','select','是',NOW(),NOW()),
(681,84,'isGuaranteed','是否担保债','select','否',NOW(),NOW()),
(682,85,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(683,85,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(684,85,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(685,85,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(686,85,'targetInnerSort','目标分级库档','number','1',NOW(),NOW()),
(687,85,'remainDaysParseable','剩余期限可解析','select','是',NOW(),NOW()),
(688,85,'remainNotExceedIssuerMax','不超过同主体最大期限','select','是',NOW(),NOW()),
(689,85,'hasIssuerMaxRemain','目标池是否已有同主体债','select','是',NOW(),NOW()),
(690,85,'hasNonSimpleInbound180','180天内非简易入库','select','是',NOW(),NOW()),
(691,86,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(692,86,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(693,86,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(694,86,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(695,86,'targetInnerSort','目标分级库档','number','5',NOW(),NOW()),
(696,86,'remainDaysParseable','剩余期限可解析','select','是',NOW(),NOW()),
(697,86,'remainNotExceedIssuerMax','不超过同主体最大期限','select','是',NOW(),NOW()),
(698,86,'hasIssuerMaxRemain','目标池是否已有同主体债','select','是',NOW(),NOW()),
(699,86,'hasNonSimpleInbound180','180天内非简易入库','select','是',NOW(),NOW()),
(700,87,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(701,87,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(702,87,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(703,87,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(704,87,'alreadyInCreditBond','是否已在信用债大库','select','是',NOW(),NOW()),
(705,87,'currentInnerSort','当前分级档','number','3',NOW(),NOW()),
(706,87,'targetInnerSort','目标分级档','number','1',NOW(),NOW()),
(707,88,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(708,88,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(709,88,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(710,88,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(711,88,'alreadyInCreditBond','是否已在信用债大库','select','是',NOW(),NOW()),
(712,88,'currentInnerSort','当前分级档','number','1',NOW(),NOW()),
(713,88,'targetInnerSort','目标分级档','number','3',NOW(),NOW()),
(714,89,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(715,89,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(716,89,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(717,89,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(718,89,'reportRestriction','报告限制','select','none',NOW(),NOW()),
(719,89,'poolName','目标池名称','string','一级库',NOW(),NOW()),
(720,89,'hasRecentInboundReport','半年内已有入池报告','select','否',NOW(),NOW()),
(721,89,'hasAnyReport','是否已选/上传报告','select','否',NOW(),NOW()),
(722,89,'hasInternalReport','是否内部报告库','select','否',NOW(),NOW()),
(723,90,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(724,90,'targetPoolId','目标投资池ID','string','1',NOW(),NOW()),
(725,90,'securityExists','证券是否存在','select','是',NOW(),NOW()),
(726,90,'poolExists','目标池是否存在','select','是',NOW(),NOW()),
(727,90,'reportRestriction','报告限制','select','any',NOW(),NOW()),
(728,90,'poolName','目标池名称','string','一级库',NOW(),NOW()),
(729,90,'hasRecentInboundReport','半年内已有入池报告','select','否',NOW(),NOW()),
(730,90,'hasAnyReport','是否已选/上传报告','select','否',NOW(),NOW()),
(731,90,'hasInternalReport','是否内部报告库','select','否',NOW(),NOW()),
(732,91,'securityCode','证券代码','string','',NOW(),NOW()),
(733,91,'maturityDate','到期日yyyy-MM-dd','string','',NOW(),NOW()),
(734,91,'yesterday','昨天yyyy-MM-dd','string','2026-08-31',NOW(),NOW()),
(735,92,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(736,92,'maturityDate','到期日yyyy-MM-dd','string','2028-12-31',NOW(),NOW()),
(737,92,'yesterday','昨天yyyy-MM-dd','string','2026-08-31',NOW(),NOW()),
(738,93,'securityCode','证券代码','string','138026.SH',NOW(),NOW()),
(739,93,'maturityDate','到期日yyyy-MM-dd','string','2024-01-01',NOW(),NOW()),
(740,93,'yesterday','昨天yyyy-MM-dd','string','2026-08-31',NOW(),NOW()),
(741,94,'issuerCode','主体代码','string','',NOW(),NOW()),
(742,94,'issuerRating','主体外评','select','',NOW(),NOW()),
(743,94,'alreadyInTargetPool','是否已在目标池','select','',NOW(),NOW()),
(744,95,'issuerCode','主体代码','string','C1000001',NOW(),NOW()),
(745,95,'issuerRating','主体外评','select','AA-',NOW(),NOW()),
(746,95,'alreadyInTargetPool','是否已在目标池','select','否',NOW(),NOW()),
(747,96,'issuerCode','主体代码','string','C1000001',NOW(),NOW()),
(748,96,'issuerRating','主体外评','select','A+',NOW(),NOW()),
(749,96,'alreadyInTargetPool','是否已在目标池','select','否',NOW(),NOW()),
(750,97,'issuerCode','主体代码','string','C1000001',NOW(),NOW()),
(751,97,'issuerRating','主体外评','select','AAA',NOW(),NOW()),
(752,97,'alreadyInTargetPool','是否已在目标池','select','否',NOW(),NOW());

