-- ============================================================
-- znty-sirm 规则管理库 - 演示数据初始化脚本
-- 前提：需先执行 sirm_rule_schema.sql 完成建表
-- 说明：
--   1. 所有脚本兼容 QLExpress 语法（无 StringBuilder/boolean/int/float 声明）
--   2. 参数变量与脚本中使用到的变量一一对应
--   3. 覆盖 string / number / select / multiselect 四种参数类型
--   4. 测试用例覆盖 pass / fail / pending 三种状态
--   5. 10张表全覆盖：6分类 + 12预设集 + 10规则 + 40参数 + 110选项 + 18用例 + 62用例参数 + 16执行记录 + 16日志
-- ============================================================
USE znty_sirm;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 清空所有业务表（TRUNCATE：重置自增 ID + 释放索引空间，比 DELETE 快）
-- ============================================================================
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

-- ============================================================================
-- 规则分类（6 类）
-- ============================================================================
INSERT INTO `rule_category` (id, category_code, category_name, sort_no, enabled, crte_time, updt_time) VALUES
(1,'risk',     '风控规则', 10, 1, NOW(), NOW()),
(2,'credit',   '信用评估', 20, 1, NOW(), NOW()),
(3,'pricing',  '定价规则', 30, 1, NOW(), NOW()),
(4,'admission','准入规则', 40, 1, NOW(), NOW()),
(5,'warning',  '预警规则', 50, 1, NOW(), NOW()),
(6,'other',    '其他',     99, 1, NOW(), NOW());

-- ============================================================================
-- 预设选项集（12 套）— 提供常用参数选项模板
-- ============================================================================
INSERT INTO `rule_preset_option_set` (id, set_name, sort_no, enabled, crte_time, updt_time) VALUES
(1, '主体评级',       10,  1, NOW(), NOW()),
(2, '主体内评',       20,  1, NOW(), NOW()),
(3, '外评机构',       30,  1, NOW(), NOW()),
(4, '债券期限',       40,  1, NOW(), NOW()),
(5, '证券类型',       50,  1, NOW(), NOW()),
(6, '行业分类',       60,  1, NOW(), NOW()),
(7, '是否/布尔',      70,  1, NOW(), NOW()),
(8, '风险等级',       80,  1, NOW(), NOW()),
(9, '审批状态',       90,  1, NOW(), NOW()),
(10,'交易场所',       100, 1, NOW(), NOW()),
(11,'主体类型',       110, 1, NOW(), NOW()),
(12,'ABS基础资产类型',120, 1, NOW(), NOW());

INSERT INTO `rule_preset_option_item` (set_id, option_value, option_label, sort_no) VALUES
-- 主体评级（set_id=1）
(1,'AAA+','AAA+',1),(1,'AAA','AAA',2),(1,'AA+','AA+',3),(1,'AA','AA',4),(1,'AA-','AA-',5),
(1,'A+','A+',6),(1,'A','A',7),(1,'A-','A-',8),(1,'BBB+','BBB+',9),(1,'BBB','BBB',10),
(1,'BBB-','BBB-',11),(1,'BB+','BB+',12),(1,'BB','BB',13),(1,'BB-','BB-',14),(1,'B','B',15),
(1,'CCC','CCC',16),(1,'CC','CC',17),(1,'C','C',18),(1,'D','D',19),
-- 主体内评（set_id=2）
(2,'1级','1级',1),(2,'2级','2级',2),(2,'3级','3级',3),(2,'4级','4级',4),(2,'5级','5级',5),
(2,'6级','6级',6),(2,'7级','7级',7),(2,'8级','8级',8),(2,'9级','9级',9),(2,'10级','10级',10),
-- 外评机构（set_id=3）
(3,'中诚信国际','中诚信国际',1),(3,'联合资信','联合资信',2),(3,'大公国际','大公国际',3),
(3,'东方金诚','东方金诚',4),(3,'新世纪评级','新世纪评级',5),
(3,'标准普尔','标准普尔',6),(3,'穆迪','穆迪',7),(3,'惠誉','惠誉',8),
-- 债券期限（set_id=4）
(4,'1年以内','1年以内',1),(4,'1-3年','1-3年',2),(4,'3-5年','3-5年',3),
(4,'5-7年','5-7年',4),(4,'7-10年','7-10年',5),(4,'10年以上','10年以上',6),
-- 证券类型（set_id=5）
(5,'国债','国债',1),(5,'地方政府债','地方政府债',2),(5,'金融债','金融债',3),(5,'企业债','企业债',4),
(5,'公司债','公司债',5),(5,'ABS','ABS',6),(5,'ABN','ABN',7),(5,'可转债','可转债',8),
(5,'短融','短融',9),(5,'超短融','超短融',10),(5,'中票','中票',11),(5,'PPN','PPN',12),
-- 行业分类（set_id=6）
(6,'金融','金融',1),(6,'地产','地产',2),(6,'城投','城投',3),(6,'制造业','制造业',4),
(6,'能源','能源',5),(6,'交通运输','交通运输',6),(6,'信息技术','信息技术',7),
(6,'消费','消费',8),(6,'医疗','医疗',9),(6,'公用事业','公用事业',10),
-- 是否/布尔（set_id=7）
(7,'是','是',1),(7,'否','否',2),
-- 风险等级（set_id=8）
(8,'低风险','低风险',1),(8,'中低风险','中低风险',2),(8,'中风险','中风险',3),
(8,'中高风险','中高风险',4),(8,'高风险','高风险',5),
-- 审批状态（set_id=9）
(9,'待审批','待审批',1),(9,'审批中','审批中',2),(9,'已通过','已通过',3),
(9,'已拒绝','已拒绝',4),(9,'已撤回','已撤回',5),
-- 交易场所（set_id=10）
(10,'银行间','银行间市场',1),(10,'上交所','上交所',2),(10,'深交所','深交所',3),
(10,'北交所','北交所',4),(10,'OTC','场外柜台',5),
-- 主体类型（set_id=11）
(11,'央企','央企',1),(11,'地方国企','地方国企',2),(11,'城投平台','城投平台',3),
(11,'民营企业','民营企业',4),(11,'股份制银行','股份制银行',5),(11,'城商行','城商行',6),
(11,'外资企业','外资企业',7),
-- ABS基础资产类型（set_id=12）
(12,'消费金融','消费金融',1),(12,'供应链应收款','供应链应收款',2),
(12,'住房抵押贷款','住房抵押贷款',3),(12,'小微贷款','小微贷款',4),
(12,'票据','票据',5),(12,'租赁资产','租赁资产',6),(12,'地方政府债权','地方政府债权',7);

-- ============================================================================
-- 10条规则（全部与证券投资风控业务相关，QLExpress 兼容格式）
-- ============================================================================
INSERT INTO `rule_definition` (id, rule_name, description, category_code, script, status, deleted_flag, crte_time, updt_time) VALUES

-- 规则1：信用债分级准入规则
(1,'信用债分级准入规则','根据主体类型和主体评级判断可进入的信用债库级别','admission',
'poolLevel = "不符合准入";
if (companyType == "城投平台") {
    if (issuerRating == "AAA+" || issuerRating == "AAA") {
        poolLevel = "一级库";
    } else if (issuerRating == "AA+") {
        poolLevel = "二级库";
    } else if (issuerRating == "AA") {
        poolLevel = "三级库";
    } else if (issuerRating == "AA-") {
        poolLevel = "四级库";
    } else {
        poolLevel = "不符合准入";
    }
} else if (companyType == "央企" || companyType == "地方国企") {
    if (issuerRating == "AAA+" || issuerRating == "AAA") {
        poolLevel = "一级库";
    } else if (issuerRating == "AA+") {
        poolLevel = "二级库";
    } else if (issuerRating == "AA") {
        poolLevel = "三级库";
    } else {
        poolLevel = "不符合准入";
    }
} else if (companyType == "民营企业") {
    if (securityRating == "AAA" && issuerRating == "AAA") {
        poolLevel = "二级库";
    } else if (securityRating == "AA+" && issuerRating == "AA+") {
        poolLevel = "三级库";
    } else {
        poolLevel = "不符合准入";
    }
}
return poolLevel;','active',0,'2025-04-01 09:00:00','2025-04-01 09:00:00'),

-- 规则2：债券利率定价规则
(2,'债券利率定价规则','基准利率+期限利差+评级调整+风险溢价，输出债券定价利率(%)','pricing',
'spreadBp = 0;
if (bondTerm == "1年以内") {
    spreadBp = 20;
} else if (bondTerm == "1-3年") {
    spreadBp = 50;
} else if (bondTerm == "3-5年") {
    spreadBp = 80;
} else if (bondTerm == "5-7年") {
    spreadBp = 110;
} else {
    spreadBp = 150;
}
if (issuerRating == "AAA+" || issuerRating == "AAA") {
    spreadBp = spreadBp - 10;
} else if (issuerRating == "AA+") {
    spreadBp = spreadBp + 20;
} else if (issuerRating == "AA") {
    spreadBp = spreadBp + 50;
} else if (issuerRating == "AA-" || issuerRating == "A+") {
    spreadBp = spreadBp + 100;
} else {
    spreadBp = spreadBp + 200;
}
finalRate = baseRate + spreadBp * 0.01 + riskPremium;
return finalRate;','active',0,'2025-04-10 09:00:00','2025-04-10 09:00:00'),

-- 规则3：证券信用风险评分
(3,'证券信用风险评分','综合评级、负债率、利息保障倍数、流动比率，输出信用风险分（越高风险越大）','risk',
'riskScore = 0;
if (issuerRating == "AAA+" || issuerRating == "AAA") {
    riskScore = riskScore + 5;
} else if (issuerRating == "AA+") {
    riskScore = riskScore + 15;
} else if (issuerRating == "AA") {
    riskScore = riskScore + 25;
} else if (issuerRating == "AA-" || issuerRating == "A+") {
    riskScore = riskScore + 40;
} else {
    riskScore = riskScore + 60;
}
if (debtRatio > 0.80) {
    riskScore = riskScore + 30;
} else if (debtRatio > 0.65) {
    riskScore = riskScore + 15;
}
if (interestCoverageRatio < 1.5) {
    riskScore = riskScore + 25;
} else if (interestCoverageRatio < 2.5) {
    riskScore = riskScore + 10;
}
if (liquidityRatio < 1.0) {
    riskScore = riskScore + 20;
}
return riskScore;','active',0,'2025-03-05 14:30:00','2025-03-05 14:30:00'),

-- 规则4：证券投资准入规则
(4,'证券投资准入规则','多维度准入校验：评级/类型/行业/规模，支持全参数类型','admission',
'approved = 1;
reason = "";
if (issuerRating == "D" || issuerRating == "C" || issuerRating == "CC" || issuerRating == "CCC") {
    approved = 0;
    reason = reason + "主体评级过低(" + issuerRating + ");";
}
if (securityType == "ABS" || securityType == "ABN") {
    reason = reason + "需审查底层资产;";
}
if (industry == "地产") {
    if (issuerRating == "A" || issuerRating == "A-" || issuerRating == "BBB+") {
        reason = reason + "地产需增信措施;";
    }
}
if (minIssueAmount < 5) {
    approved = 0;
    reason = reason + "规模不足5亿;";
}
if (approved == 0) {
    return "否决:" + reason;
}
return "通过:" + reason;','active',0,'2025-04-15 11:00:00','2025-04-20 16:30:00'),

-- 规则5：评级下调禁投预警
(5,'评级下调禁投预警','连续评级下调次数+波动率综合判断，输出预警级别','warning',
'warningScore = 0;
if (consecutiveDowngrades >= 2) {
    warningScore = warningScore + 50;
} else if (consecutiveDowngrades == 1) {
    warningScore = warningScore + 20;
}
if (currentRating == "AA-" || currentRating == "A+" || currentRating == "A") {
    warningScore = warningScore + 30;
} else if (currentRating == "BBB+" || currentRating == "BBB" || currentRating == "BBB-") {
    warningScore = warningScore + 50;
}
if (monthlyVolatility > 5.0) {
    warningScore = warningScore + 20;
}
if (warningScore >= 50) {
    return "禁投预警-" + warningScore;
}
if (warningScore >= 30) {
    return "重点监控-" + warningScore;
}
return "正常-" + warningScore;','active',0,'2025-05-01 08:00:00','2025-05-01 08:00:00'),

-- 规则6：城投债区域评估规则
(6,'城投债区域评估规则','综合债务率、评级、存量规模评估城投债入库风险','credit',
'regionScore = 0;
if (issuerRating == "AAA+" || issuerRating == "AAA") {
    regionScore = regionScore + 10;
} else if (issuerRating == "AA+") {
    regionScore = regionScore + 20;
} else if (issuerRating == "AA") {
    regionScore = regionScore + 35;
} else {
    regionScore = regionScore + 60;
}
if (debtToRevenue > 3.0) {
    regionScore = regionScore + 40;
} else if (debtToRevenue > 2.0) {
    regionScore = regionScore + 20;
} else if (debtToRevenue > 1.5) {
    regionScore = regionScore + 10;
}
if (debtScale > 500) {
    regionScore = regionScore + 15;
} else if (debtScale > 200) {
    regionScore = regionScore + 5;
}
if (regionScore >= 70) {
    return "高风险-禁止准入";
}
if (regionScore >= 40) {
    return "中高风险-谨慎准入";
}
return "可以准入-" + regionScore;','active',0,'2025-05-10 09:00:00','2025-05-10 09:00:00'),

-- 规则7：短融超短融准入规则
(7,'短融超短融准入规则','校验主体类型、评级及注册额度使用情况','admission',
'approved = 1;
reason = "";
if (issuerType == "民营企业") {
    if (issuerRating == "AA-" || issuerRating == "A+" || issuerRating == "A" || issuerRating == "A-") {
        approved = 0;
        reason = reason + "民企评级不满足短融准入(需AA及以上);";
    }
}
if (existingBalance >= registeredQuota) {
    approved = 0;
    reason = reason + "余额已达注册额度上限;";
}
if (existingBalance >= registeredQuota * 0.8) {
    reason = reason + "注意:余额超注册额度80%;";
}
if (approved == 0) {
    return "否决:" + reason;
}
return "通过:" + reason;','active',0,'2025-05-05 10:00:00','2025-05-05 10:00:00'),

-- 规则8：ABS底层资产评估规则
(8,'ABS底层资产评估规则','综合逾期率、集中度、资产规模评估ABS底层资产质量','credit',
'totalScore = 0;
if (overdueRate > 5.0) {
    totalScore = totalScore + 50;
} else if (overdueRate > 3.0) {
    totalScore = totalScore + 30;
} else if (overdueRate > 1.5) {
    totalScore = totalScore + 15;
}
if (top3Concentration > 30.0) {
    totalScore = totalScore + 30;
} else if (top3Concentration > 20.0) {
    totalScore = totalScore + 15;
}
if (assetType == "消费金融" || assetType == "小微贷款") {
    totalScore = totalScore + 10;
}
if (originalBalance < 5.0) {
    totalScore = totalScore + 20;
}
if (totalScore >= 60) {
    return "高风险-" + totalScore;
}
if (totalScore >= 30) {
    return "中风险-" + totalScore;
}
return "低风险-" + totalScore;','disabled',0,'2025-05-08 14:00:00','2025-05-08 14:00:00'),

-- 规则9：民企债风险预警规则
(9,'民企债风险预警规则','综合评级、负债率、利息保障倍数、增信情况输出风险标签','warning',
'warningFlag = "正常";
issues = "";
if (issuerRating == "AA-" || issuerRating == "A+" || issuerRating == "A" || issuerRating == "A-") {
    issues = issues + "评级偏低;";
}
if (assetLiabilityRatio > 0.70) {
    issues = issues + "资产负债率过高(" + assetLiabilityRatio + ");";
}
if (interestCoverageRatio < 2.0) {
    issues = issues + "利息保障倍数不足;";
}
if (hasPledge == "否" && assetLiabilityRatio > 0.65) {
    issues = issues + "无增信且负债率偏高;";
}
if (assetLiabilityRatio > 0.70 && interestCoverageRatio < 1.5) {
    warningFlag = "禁止准入";
} else if (issues != "") {
    warningFlag = "重点关注";
}
return warningFlag + ":" + issues;','active',0,'2025-04-25 15:00:00','2025-04-25 15:00:00'),

-- 规则10：大额债券交易审批规则
(10,'大额债券交易审批规则','根据交易金额、目标池类型、紧急标志判断所需审批层级','risk',
'approvalLevel = "部门负责人审批";
notes = "";
if (poolType == "禁投池") {
    return "不允许交易-证券在禁投池";
}
if (tradeAmount > 5000) {
    approvalLevel = "投资决策委员会审批";
    notes = notes + "单笔超5亿需决委会;";
} else if (tradeAmount > 1000) {
    approvalLevel = "分管领导审批";
    notes = notes + "单笔超1亿需分管领导;";
}
if (urgentFlag == "是" && tradeAmount > 1000) {
    notes = notes + "紧急交易需补充说明;";
}
return approvalLevel + ":" + notes;','active',0,'2025-05-12 09:00:00','2025-05-12 09:00:00');

-- ============================================================================
-- 规则参数（40个）
-- ============================================================================
INSERT INTO `rule_param` (id, rule_id, param_name, param_label, param_type, required, sort_no, crte_time, updt_time) VALUES
-- 规则1：信用债分级准入
(1, 1, 'issuerRating',  '发行人主体评级', 'select', 1, 1, NOW(), NOW()),
(2, 1, 'securityRating','证券评级',       'select', 1, 2, NOW(), NOW()),
(3, 1, 'companyType',   '主体类型',       'select', 1, 3, NOW(), NOW()),
-- 规则2：利率定价
(4, 2, 'baseRate',      '基准利率(%)',    'number', 1, 1, NOW(), NOW()),
(5, 2, 'bondTerm',      '债券期限',       'select', 1, 2, NOW(), NOW()),
(6, 2, 'issuerRating',  '发行人评级',     'select', 1, 3, NOW(), NOW()),
(7, 2, 'riskPremium',   '风险溢价(%)',    'number', 1, 4, NOW(), NOW()),
-- 规则3：信用风险评分
(8,  3, 'issuerRating',          '发行人评级',     'select', 1, 1, NOW(), NOW()),
(9,  3, 'debtRatio',             '资产负债率',     'number', 1, 2, NOW(), NOW()),
(10, 3, 'interestCoverageRatio', '利息保障倍数',   'number', 1, 3, NOW(), NOW()),
(11, 3, 'liquidityRatio',        '流动比率',       'number', 1, 4, NOW(), NOW()),
-- 规则4：投资准入
(12, 4, 'issuerRating',    '发行人评级',       'string',      1, 1, NOW(), NOW()),
(13, 4, 'securityType',    '证券类型',         'select',      1, 2, NOW(), NOW()),
(14, 4, 'industry',        '所属行业',         'select',      1, 3, NOW(), NOW()),
(15, 4, 'allowedExchanges','允许交易场所',     'multiselect', 1, 4, NOW(), NOW()),
(16, 4, 'minIssueAmount',  '最低发行规模(亿)', 'number',      1, 5, NOW(), NOW()),
-- 规则5：评级下调预警
(17, 5, 'currentRating',          '当前评级',         'select', 1, 1, NOW(), NOW()),
(18, 5, 'previousRating',         '前期评级',         'select', 1, 2, NOW(), NOW()),
(19, 5, 'consecutiveDowngrades',  '连续下调次数',     'number', 1, 3, NOW(), NOW()),
(20, 5, 'monthlyVolatility',      '月收益率波动率(%)', 'number', 1, 4, NOW(), NOW()),
-- 规则6：城投债区域评估
(21, 6, 'region',        '所属区域',             'string', 1, 1, NOW(), NOW()),
(22, 6, 'debtToRevenue', '债务/财政收入(倍)',     'number', 1, 2, NOW(), NOW()),
(23, 6, 'issuerRating',  '发行人评级',           'select', 1, 3, NOW(), NOW()),
(24, 6, 'debtScale',     '存量债务规模(亿)',      'number', 1, 4, NOW(), NOW()),
-- 规则7：短融超短融准入
(25, 7, 'issuerType',       '主体类型',         'select', 1, 1, NOW(), NOW()),
(26, 7, 'issuerRating',     '发行人评级',       'select', 1, 2, NOW(), NOW()),
(27, 7, 'registeredQuota',  '注册额度(亿)',     'number', 1, 3, NOW(), NOW()),
(28, 7, 'existingBalance',  '存量余额(亿)',     'number', 1, 4, NOW(), NOW()),
-- 规则8：ABS底层资产评估
(29, 8, 'assetType',         '基础资产类型',   'select', 1, 1, NOW(), NOW()),
(30, 8, 'overdueRate',       '逾期率(%)',       'number', 1, 2, NOW(), NOW()),
(31, 8, 'top3Concentration', 'Top3集中度(%)',   'number', 1, 3, NOW(), NOW()),
(32, 8, 'originalBalance',   '入池资产规模(亿)','number', 1, 4, NOW(), NOW()),
-- 规则9：民企债风险预警
(33, 9, 'issuerRating',          '发行人评级',   'select', 1, 1, NOW(), NOW()),
(34, 9, 'assetLiabilityRatio',   '资产负债率',   'number', 1, 2, NOW(), NOW()),
(35, 9, 'interestCoverageRatio', '利息保障倍数', 'number', 1, 3, NOW(), NOW()),
(36, 9, 'hasPledge',             '有无增信措施', 'select', 1, 4, NOW(), NOW()),
-- 规则10：大额交易审批
(37, 10, 'tradeAmount', '交易金额(百万)',   'number', 1, 1, NOW(), NOW()),
(38, 10, 'poolType',    '目标池类型',       'select', 1, 2, NOW(), NOW()),
(39, 10, 'counterparty','交易对手方',       'string', 1, 3, NOW(), NOW()),
(40, 10, 'urgentFlag',  '是否紧急',         'select', 1, 4, NOW(), NOW());

-- ============================================================================
-- 参数选项（110个）
-- ============================================================================
INSERT INTO `rule_param_option` (id, param_id, option_value, option_label, sort_no, crte_time, updt_time) VALUES
-- 规则1 param1 issuerRating (ids 1-7)
(1, 1,'AAA+','AAA+',1,NOW(),NOW()),(2, 1,'AAA','AAA',2,NOW(),NOW()),(3, 1,'AA+','AA+',3,NOW(),NOW()),
(4, 1,'AA','AA',4,NOW(),NOW()),(5, 1,'AA-','AA-',5,NOW(),NOW()),(6, 1,'A+','A+',6,NOW(),NOW()),
(7, 1,'A','A',7,NOW(),NOW()),
-- 规则1 param2 securityRating (ids 8-14)
(8, 2,'AAA+','AAA+',1,NOW(),NOW()),(9, 2,'AAA','AAA',2,NOW(),NOW()),(10, 2,'AA+','AA+',3,NOW(),NOW()),
(11, 2,'AA','AA',4,NOW(),NOW()),(12, 2,'AA-','AA-',5,NOW(),NOW()),(13, 2,'A+','A+',6,NOW(),NOW()),
(14, 2,'A','A',7,NOW(),NOW()),
-- 规则1 param3 companyType (ids 15-20)
(15, 3,'央企','央企',1,NOW(),NOW()),(16, 3,'地方国企','地方国企',2,NOW(),NOW()),
(17, 3,'城投平台','城投平台',3,NOW(),NOW()),(18, 3,'民营企业','民营企业',4,NOW(),NOW()),
(19, 3,'股份制银行','股份制银行',5,NOW(),NOW()),(20, 3,'城商行','城商行',6,NOW(),NOW()),
-- 规则2 param5 bondTerm (ids 21-26)
(21, 5,'1年以内','1年以内',1,NOW(),NOW()),(22, 5,'1-3年','1-3年',2,NOW(),NOW()),
(23, 5,'3-5年','3-5年',3,NOW(),NOW()),(24, 5,'5-7年','5-7年',4,NOW(),NOW()),
(25, 5,'7-10年','7-10年',5,NOW(),NOW()),(26, 5,'10年以上','10年以上',6,NOW(),NOW()),
-- 规则2 param6 issuerRating (ids 27-32)
(27, 6,'AAA+','AAA+',1,NOW(),NOW()),(28, 6,'AAA','AAA',2,NOW(),NOW()),(29, 6,'AA+','AA+',3,NOW(),NOW()),
(30, 6,'AA','AA',4,NOW(),NOW()),(31, 6,'AA-','AA-',5,NOW(),NOW()),(32, 6,'A+','A+',6,NOW(),NOW()),
-- 规则3 param8 issuerRating (ids 33-38)
(33, 8,'AAA+','AAA+',1,NOW(),NOW()),(34, 8,'AAA','AAA',2,NOW(),NOW()),(35, 8,'AA+','AA+',3,NOW(),NOW()),
(36, 8,'AA','AA',4,NOW(),NOW()),(37, 8,'AA-','AA-',5,NOW(),NOW()),(38, 8,'A+','A+',6,NOW(),NOW()),
-- 规则4 param13 securityType (ids 39-46)
(39,13,'金融债','金融债',1,NOW(),NOW()),(40,13,'企业债','企业债',2,NOW(),NOW()),
(41,13,'公司债','公司债',3,NOW(),NOW()),(42,13,'ABS','ABS',4,NOW(),NOW()),
(43,13,'ABN','ABN',5,NOW(),NOW()),(44,13,'中票','中票',6,NOW(),NOW()),
(45,13,'短融','短融',7,NOW(),NOW()),(46,13,'超短融','超短融',8,NOW(),NOW()),
-- 规则4 param14 industry (ids 47-53)
(47,14,'金融','金融',1,NOW(),NOW()),(48,14,'地产','地产',2,NOW(),NOW()),
(49,14,'城投','城投',3,NOW(),NOW()),(50,14,'制造业','制造业',4,NOW(),NOW()),
(51,14,'能源','能源',5,NOW(),NOW()),(52,14,'交通运输','交通运输',6,NOW(),NOW()),
(53,14,'信息技术','信息技术',7,NOW(),NOW()),
-- 规则4 param15 allowedExchanges (ids 54-58)
(54,15,'银行间','银行间市场',1,NOW(),NOW()),(55,15,'上交所','上交所',2,NOW(),NOW()),
(56,15,'深交所','深交所',3,NOW(),NOW()),(57,15,'北交所','北交所',4,NOW(),NOW()),
(58,15,'OTC','场外柜台',5,NOW(),NOW()),
-- 规则5 param17 currentRating (ids 59-65)
(59,17,'AAA','AAA',1,NOW(),NOW()),(60,17,'AA+','AA+',2,NOW(),NOW()),(61,17,'AA','AA',3,NOW(),NOW()),
(62,17,'AA-','AA-',4,NOW(),NOW()),(63,17,'A+','A+',5,NOW(),NOW()),
(64,17,'BBB+','BBB+',6,NOW(),NOW()),(65,17,'BBB','BBB',7,NOW(),NOW()),
-- 规则5 param18 previousRating (ids 66-72)
(66,18,'AAA+','AAA+',1,NOW(),NOW()),(67,18,'AAA','AAA',2,NOW(),NOW()),(68,18,'AA+','AA+',3,NOW(),NOW()),
(69,18,'AA','AA',4,NOW(),NOW()),(70,18,'AA-','AA-',5,NOW(),NOW()),
(71,18,'A+','A+',6,NOW(),NOW()),(72,18,'A','A',7,NOW(),NOW()),
-- 规则6 param23 issuerRating (ids 73-78)
(73,23,'AAA+','AAA+',1,NOW(),NOW()),(74,23,'AAA','AAA',2,NOW(),NOW()),(75,23,'AA+','AA+',3,NOW(),NOW()),
(76,23,'AA','AA',4,NOW(),NOW()),(77,23,'AA-','AA-',5,NOW(),NOW()),(78,23,'A+','A+',6,NOW(),NOW()),
-- 规则7 param25 issuerType (ids 79-83)
(79,25,'央企','央企',1,NOW(),NOW()),(80,25,'地方国企','地方国企',2,NOW(),NOW()),
(81,25,'城投平台','城投平台',3,NOW(),NOW()),(82,25,'民营企业','民营企业',4,NOW(),NOW()),
(83,25,'股份制银行','股份制银行',5,NOW(),NOW()),
-- 规则7 param26 issuerRating (ids 84-89)
(84,26,'AAA+','AAA+',1,NOW(),NOW()),(85,26,'AAA','AAA',2,NOW(),NOW()),(86,26,'AA+','AA+',3,NOW(),NOW()),
(87,26,'AA','AA',4,NOW(),NOW()),(88,26,'AA-','AA-',5,NOW(),NOW()),(89,26,'A+','A+',6,NOW(),NOW()),
-- 规则8 param29 assetType (ids 90-95)
(90,29,'消费金融','消费金融',1,NOW(),NOW()),(91,29,'供应链应收款','供应链应收款',2,NOW(),NOW()),
(92,29,'住房抵押贷款','住房抵押贷款',3,NOW(),NOW()),(93,29,'小微贷款','小微贷款',4,NOW(),NOW()),
(94,29,'票据','票据',5,NOW(),NOW()),(95,29,'租赁资产','租赁资产',6,NOW(),NOW()),
-- 规则9 param33 issuerRating (ids 96-101)
(96, 33,'AAA','AAA',1,NOW(),NOW()),(97, 33,'AA+','AA+',2,NOW(),NOW()),(98, 33,'AA','AA',3,NOW(),NOW()),
(99, 33,'AA-','AA-',4,NOW(),NOW()),(100,33,'A+','A+',5,NOW(),NOW()),(101,33,'A','A',6,NOW(),NOW()),
-- 规则9 param36 hasPledge (ids 102-103)
(102,36,'是','是',1,NOW(),NOW()),(103,36,'否','否',2,NOW(),NOW()),
-- 规则10 param38 poolType (ids 104-108)
(104,38,'信用债大库','信用债大库',1,NOW(),NOW()),(105,38,'专户产品','专户产品',2,NOW(),NOW()),
(106,38,'境外债库','境外债库',3,NOW(),NOW()),(107,38,'转债库','转债库',4,NOW(),NOW()),
(108,38,'禁投池','禁投池',5,NOW(),NOW()),
-- 规则10 param40 urgentFlag (ids 109-110)
(109,40,'是','是',1,NOW(),NOW()),(110,40,'否','否',2,NOW(),NOW());

-- ============================================================================
-- 测试用例（18个）
-- ============================================================================
INSERT INTO `rule_test_case` (id, case_name, rule_id, rule_name_snapshot, last_result, last_output, last_run_time, crte_time, updt_time) VALUES
(1, '央企AAA→一级库',               1, '信用债分级准入规则', 'pass', '一级库',                        '2025-04-01 09:30:00', NOW(), NOW()),
(2, '城投平台AA→三级库',             1, '信用债分级准入规则', 'pass', '三级库',                        '2025-04-01 09:35:00', NOW(), NOW()),
(3, 'AAA一年内债低溢价定价',         2, '债券利率定价规则',   'pass', '2.6',                          '2025-04-10 09:30:00', NOW(), NOW()),
(4, 'AA三至五年债中等定价',          2, '债券利率定价规则',   'pass', '4.3',                          '2025-04-10 09:35:00', NOW(), NOW()),
(5, 'AAA低负债低风险评分',           3, '证券信用风险评分',   'pass', '5',                            '2025-03-05 14:40:00', NOW(), NOW()),
(6, 'AA高负债高风险评分',            3, '证券信用风险评分',   'pass', '70',                           '2025-03-05 14:45:00', NOW(), NOW()),
(7, 'AAA金融债准入通过',             4, '证券投资准入规则',   'pass', '通过:',                        '2025-04-20 10:00:00', NOW(), NOW()),
(8, 'D级地产小规模否决',             4, '证券投资准入规则',   'fail', '否决:主体评级过低(D);规模不足5亿;', '2025-04-20 10:05:00', NOW(), NOW()),
(9, '连续两次下调禁投预警',          5, '评级下调禁投预警',   'pass', '禁投预警-80',                   '2025-05-01 09:00:00', NOW(), NOW()),
(10,'单次小幅下调正常',              5, '评级下调禁投预警',   'pass', '正常-20',                       '2025-05-01 09:05:00', NOW(), NOW()),
(11,'高债务城投中高风险',            6, '城投债区域评估规则', 'pass', '中高风险-谨慎准入',             '2025-05-10 10:00:00', NOW(), NOW()),
(12,'央企短融额度充足通过',          7, '短融超短融准入规则', 'pass', '通过:',                        '2025-05-05 11:00:00', NOW(), NOW()),
(13,'民企低评级短融否决',            7, '短融超短融准入规则', 'pass', '否决:民企评级不满足短融准入(需AA及以上);', '2025-05-05 11:05:00', NOW(), NOW()),
(14,'高逾期消费金融ABS高风险',       8, 'ABS底层资产评估规则',NULL,   NULL,                            NULL,                  NOW(), NOW()),
(15,'民企高负债无增信禁止准入',      9, '民企债风险预警规则', 'pass', '禁止准入:评级偏低;资产负债率过高(0.78);利息保障倍数不足;无增信且负债率偏高;', '2025-04-25 15:30:00', NOW(), NOW()),
(16,'民企无增信负债偏高重点关注',    9, '民企债风险预警规则', 'pass', '重点关注:无增信且负债率偏高;',  '2025-04-25 15:35:00', NOW(), NOW()),
(17,'中等金额债券分管领导审批',      10,'大额债券交易审批规则','pass','分管领导审批:单笔超1亿需分管领导;','2025-05-12 10:00:00', NOW(), NOW()),
(18,'禁投池证券不允许交易',          10,'大额债券交易审批规则',NULL,  NULL,                            NULL,                  NOW(), NOW());

-- ============================================================================
-- 测试用例参数（62个）
-- ============================================================================
INSERT INTO `rule_test_case_param` (id, case_id, param_name, param_label_snapshot, param_type_snapshot, param_value, crte_time, updt_time) VALUES
-- 用例1：央企AAA
(1, 1,'issuerRating', '发行人主体评级','select','AAA',NOW(),NOW()),
(2, 1,'securityRating','证券评级',     'select','AAA',NOW(),NOW()),
(3, 1,'companyType',  '主体类型',      'select','央企',NOW(),NOW()),
-- 用例2：城投平台AA
(4, 2,'issuerRating', '发行人主体评级','select','AA',NOW(),NOW()),
(5, 2,'securityRating','证券评级',     'select','AA',NOW(),NOW()),
(6, 2,'companyType',  '主体类型',      'select','城投平台',NOW(),NOW()),
-- 用例3：AAA一年内低溢价
(7, 3,'baseRate',    '基准利率(%)',   'number','2.5',NOW(),NOW()),
(8, 3,'bondTerm',    '债券期限',      'select','1年以内',NOW(),NOW()),
(9, 3,'issuerRating','发行人评级',    'select','AAA',NOW(),NOW()),
(10,3,'riskPremium', '风险溢价(%)',   'number','0',NOW(),NOW()),
-- 用例4：AA三至五年
(11,4,'baseRate',    '基准利率(%)',   'number','2.5',NOW(),NOW()),
(12,4,'bondTerm',    '债券期限',      'select','3-5年',NOW(),NOW()),
(13,4,'issuerRating','发行人评级',    'select','AA',NOW(),NOW()),
(14,4,'riskPremium', '风险溢价(%)',   'number','0.5',NOW(),NOW()),
-- 用例5：AAA低风险评分
(15,5,'issuerRating',         '发行人评级',  'select','AAA',NOW(),NOW()),
(16,5,'debtRatio',            '资产负债率',  'number','0.45',NOW(),NOW()),
(17,5,'interestCoverageRatio','利息保障倍数','number','5.0',NOW(),NOW()),
(18,5,'liquidityRatio',       '流动比率',    'number','1.5',NOW(),NOW()),
-- 用例6：AA高负债
(19,6,'issuerRating',         '发行人评级',  'select','AA',NOW(),NOW()),
(20,6,'debtRatio',            '资产负债率',  'number','0.75',NOW(),NOW()),
(21,6,'interestCoverageRatio','利息保障倍数','number','1.8',NOW(),NOW()),
(22,6,'liquidityRatio',       '流动比率',    'number','0.8',NOW(),NOW()),
-- 用例7：AAA金融债通过
(23,7,'issuerRating',   '发行人评级',   'string',     'AAA',NOW(),NOW()),
(24,7,'securityType',   '证券类型',     'select',     '金融债',NOW(),NOW()),
(25,7,'industry',       '所属行业',     'select',     '金融',NOW(),NOW()),
(26,7,'allowedExchanges','允许交易场所','multiselect', '银行间,上交所',NOW(),NOW()),
(27,7,'minIssueAmount', '最低发行规模(亿)','number',  '100',NOW(),NOW()),
-- 用例8：D级地产否决
(28,8,'issuerRating',   '发行人评级',   'string',     'D',NOW(),NOW()),
(29,8,'securityType',   '证券类型',     'select',     '企业债',NOW(),NOW()),
(30,8,'industry',       '所属行业',     'select',     '地产',NOW(),NOW()),
(31,8,'allowedExchanges','允许交易场所','multiselect', '上交所',NOW(),NOW()),
(32,8,'minIssueAmount', '最低发行规模(亿)','number',  '3',NOW(),NOW()),
-- 用例9：连续两次下调
(33,9,'currentRating',         '当前评级',         'select','AA-',NOW(),NOW()),
(34,9,'previousRating',        '前期评级',          'select','AA+',NOW(),NOW()),
(35,9,'consecutiveDowngrades', '连续下调次数',      'number','2',NOW(),NOW()),
(36,9,'monthlyVolatility',     '月收益率波动率(%)','number','3.5',NOW(),NOW()),
-- 用例10：单次下调
(37,10,'currentRating',        '当前评级',         'select','AA',NOW(),NOW()),
(38,10,'previousRating',       '前期评级',          'select','AA+',NOW(),NOW()),
(39,10,'consecutiveDowngrades','连续下调次数',      'number','1',NOW(),NOW()),
(40,10,'monthlyVolatility',    '月收益率波动率(%)','number','1.2',NOW(),NOW()),
-- 用例11：高债务城投
(41,11,'region',       '所属区域',          'string','某省B市',NOW(),NOW()),
(42,11,'debtToRevenue','债务/财政收入(倍)', 'number','2.5',NOW(),NOW()),
(43,11,'issuerRating', '发行人评级',        'select','AA',NOW(),NOW()),
(44,11,'debtScale',    '存量债务规模(亿)',  'number','350',NOW(),NOW()),
-- 用例12：央企短融通过
(45,12,'issuerType',     '主体类型',   'select','央企',NOW(),NOW()),
(46,12,'issuerRating',   '发行人评级', 'select','AAA',NOW(),NOW()),
(47,12,'registeredQuota','注册额度(亿)','number','50',NOW(),NOW()),
(48,12,'existingBalance','存量余额(亿)','number','20',NOW(),NOW()),
-- 用例13：民企低评级否决
(49,13,'issuerType',     '主体类型',   'select','民营企业',NOW(),NOW()),
(50,13,'issuerRating',   '发行人评级', 'select','AA-',NOW(),NOW()),
(51,13,'registeredQuota','注册额度(亿)','number','30',NOW(),NOW()),
(52,13,'existingBalance','存量余额(亿)','number','15',NOW(),NOW()),
-- 用例14：高逾期ABS（pending）
(53,14,'assetType',        '基础资产类型', 'select','消费金融',NOW(),NOW()),
(54,14,'overdueRate',      '逾期率(%)',     'number','6.5',NOW(),NOW()),
(55,14,'top3Concentration','Top3集中度(%)','number','35',NOW(),NOW()),
(56,14,'originalBalance',  '入池资产规模(亿)','number','3.5',NOW(),NOW()),
-- 用例15：民企高负债禁止准入
(57,15,'issuerRating',         '发行人评级',   'select','AA-',NOW(),NOW()),
(58,15,'assetLiabilityRatio',  '资产负债率',   'number','0.78',NOW(),NOW()),
(59,15,'interestCoverageRatio','利息保障倍数', 'number','1.2',NOW(),NOW()),
(60,15,'hasPledge',            '有无增信措施', 'select','否',NOW(),NOW()),
-- 用例16：民企无增信重点关注
(61,16,'issuerRating',         '发行人评级',   'select','AA',NOW(),NOW()),
(62,16,'assetLiabilityRatio',  '资产负债率',   'number','0.68',NOW(),NOW()),
(63,16,'interestCoverageRatio','利息保障倍数', 'number','2.3',NOW(),NOW()),
(64,16,'hasPledge',            '有无增信措施', 'select','否',NOW(),NOW()),
-- 用例17：中等金额分管领导审批
(65,17,'tradeAmount', '交易金额(百万)', 'number','2000',NOW(),NOW()),
(66,17,'poolType',    '目标池类型',    'select','信用债大库',NOW(),NOW()),
(67,17,'counterparty','交易对手方',   'string','某证券股份有限公司',NOW(),NOW()),
(68,17,'urgentFlag',  '是否紧急',     'select','否',NOW(),NOW()),
-- 用例18：禁投池不允许交易（pending）
(69,18,'tradeAmount', '交易金额(百万)', 'number','500',NOW(),NOW()),
(70,18,'poolType',    '目标池类型',    'select','禁投池',NOW(),NOW()),
(71,18,'counterparty','交易对手方',   'string','某基金管理公司',NOW(),NOW()),
(72,18,'urgentFlag',  '是否紧急',     'select','是',NOW(),NOW());

-- ============================================================================
-- 执行记录（16条，覆盖已运行的用例 1-13、15-17）
-- ============================================================================
INSERT INTO `rule_test_run` (id, case_id, rule_id, run_status, output, error_message, start_time, finish_time, crte_time, updt_time) VALUES
(1,  1,  1,'pass','一级库',                                      NULL,'2025-04-01 09:30:00','2025-04-01 09:30:01',NOW(),NOW()),
(2,  2,  1,'pass','三级库',                                      NULL,'2025-04-01 09:35:00','2025-04-01 09:35:01',NOW(),NOW()),
(3,  3,  2,'pass','2.6',                                         NULL,'2025-04-10 09:30:00','2025-04-10 09:30:01',NOW(),NOW()),
(4,  4,  2,'pass','4.3',                                         NULL,'2025-04-10 09:35:00','2025-04-10 09:35:01',NOW(),NOW()),
(5,  5,  3,'pass','5',                                           NULL,'2025-03-05 14:40:00','2025-03-05 14:40:01',NOW(),NOW()),
(6,  6,  3,'pass','70',                                          NULL,'2025-03-05 14:45:00','2025-03-05 14:45:01',NOW(),NOW()),
(7,  7,  4,'pass','通过:',                                       NULL,'2025-04-20 10:00:00','2025-04-20 10:00:01',NOW(),NOW()),
(8,  8,  4,'fail','否决:主体评级过低(D);规模不足5亿;',           NULL,'2025-04-20 10:05:00','2025-04-20 10:05:01',NOW(),NOW()),
(9,  9,  5,'pass','禁投预警-80',                                 NULL,'2025-05-01 09:00:00','2025-05-01 09:00:01',NOW(),NOW()),
(10,10,  5,'pass','正常-20',                                     NULL,'2025-05-01 09:05:00','2025-05-01 09:05:01',NOW(),NOW()),
(11,11,  6,'pass','中高风险-谨慎准入',                           NULL,'2025-05-10 10:00:00','2025-05-10 10:00:01',NOW(),NOW()),
(12,12,  7,'pass','通过:',                                       NULL,'2025-05-05 11:00:00','2025-05-05 11:00:01',NOW(),NOW()),
(13,13,  7,'pass','否决:民企评级不满足短融准入(需AA及以上);',    NULL,'2025-05-05 11:05:00','2025-05-05 11:05:01',NOW(),NOW()),
(14,15,  9,'pass','禁止准入:评级偏低;资产负债率过高(0.78);利息保障倍数不足;无增信且负债率偏高;',NULL,'2025-04-25 15:30:00','2025-04-25 15:30:01',NOW(),NOW()),
(15,16,  9,'pass','重点关注:无增信且负债率偏高;',                NULL,'2025-04-25 15:35:00','2025-04-25 15:35:01',NOW(),NOW()),
(16,17, 10,'pass','分管领导审批:单笔超1亿需分管领导;',           NULL,'2025-05-12 10:00:00','2025-05-12 10:00:01',NOW(),NOW());

-- ============================================================================
-- 执行日志（16条，选取关键用例的关键步骤）
-- ============================================================================
INSERT INTO `rule_test_run_log` (id, run_id, log_time, log_type, message, crte_time, updt_time) VALUES
(1, 1, '2025-04-01 09:30:00','info',   '执行规则: 信用债分级准入规则',                           NOW(),NOW()),
(2, 1, '2025-04-01 09:30:00','info',   '参数注入: issuerRating=AAA, securityRating=AAA, companyType=央企', NOW(),NOW()),
(3, 1, '2025-04-01 09:30:01','success','准入结果: 一级库',                                       NOW(),NOW()),
(4, 4, '2025-04-10 09:35:00','info',   '执行规则: 债券利率定价规则',                             NOW(),NOW()),
(5, 4, '2025-04-10 09:35:00','info',   '参数注入: baseRate=2.5, bondTerm=3-5年, issuerRating=AA, riskPremium=0.5', NOW(),NOW()),
(6, 4, '2025-04-10 09:35:01','success','最终利率: 4.3%',                                         NOW(),NOW()),
(7, 6, '2025-03-05 14:45:00','info',   '执行规则: 证券信用风险评分',                             NOW(),NOW()),
(8, 6, '2025-03-05 14:45:00','info',   '参数注入: issuerRating=AA, debtRatio=0.75, interestCoverageRatio=1.8, liquidityRatio=0.8', NOW(),NOW()),
(9, 6, '2025-03-05 14:45:01','success','风险评分: 70（高风险）',                                 NOW(),NOW()),
(10,8, '2025-04-20 10:05:00','info',   '执行规则: 证券投资准入规则',                             NOW(),NOW()),
(11,8, '2025-04-20 10:05:00','info',   '参数注入: issuerRating=D, securityType=企业债, industry=地产, minIssueAmount=3', NOW(),NOW()),
(12,8, '2025-04-20 10:05:01','error',  '否决原因: 主体评级过低(D);',                             NOW(),NOW()),
(13,8, '2025-04-20 10:05:01','error',  '否决原因: 规模不足5亿;',                                 NOW(),NOW()),
(14,9, '2025-05-01 09:00:00','info',   '执行规则: 评级下调禁投预警',                             NOW(),NOW()),
(15,9, '2025-05-01 09:00:00','info',   '参数注入: currentRating=AA-, previousRating=AA+, consecutiveDowngrades=2, monthlyVolatility=3.5', NOW(),NOW()),
(16,9, '2025-05-01 09:00:01','success','预警结果: 禁投预警-80',                                  NOW(),NOW());

ALTER TABLE `rule_category` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_preset_option_set` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_definition` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_param` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_param_option` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_test_case` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_test_case_param` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_test_run` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_test_run_log` AUTO_INCREMENT = 1000;
