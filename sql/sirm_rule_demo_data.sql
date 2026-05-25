-- ============================================================
-- znty-sirm 规则管理库 - 演示数据初始化脚本
-- 前提：需先执行 sirm_rule_schema.sql 完成建表
-- 说明：
--   1. 所有脚本兼容 QLExpress 语法（无 StringBuilder/boolean/int/float 声明）
--   2. 参数变量与脚本中使用到的变量一一对应
--   3. 覆盖 string / number / select / multiselect 四种参数类型
--   4. 测试用例覆盖 pass / fail / pending 三种状态
--   5. 10张表全覆盖：6分类 + 12预设集 + 10规则 + 34参数 + 70选项 + 18用例 + 60用例参数 + 15执行记录 + 16日志
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

INSERT INTO `rule_category` (id, category_code, category_name, sort_no, enabled, crte_time, updt_time) VALUES
(1,'risk','风控规则',10,1,NOW(),NOW()),
(2,'marketing','营销规则',20,1,NOW(),NOW()),
(3,'pricing','定价规则',30,1,NOW(),NOW()),
(4,'permission','权限规则',40,1,NOW(),NOW()),
(5,'business','业务规则',50,1,NOW(),NOW()),
(6,'other','其他',99,1,NOW(),NOW());

INSERT INTO `rule_preset_option_set` (id, set_name, sort_no, enabled, crte_time, updt_time) VALUES
(1,'主体评级',10,1,NOW(),NOW()),(2,'主体内评',20,1,NOW(),NOW()),(3,'外评机构',30,1,NOW(),NOW()),
(4,'期限',40,1,NOW(),NOW()),(5,'债券类型',50,1,NOW(),NOW()),(6,'行业分类',60,1,NOW(),NOW()),
(7,'是否/布尔',70,1,NOW(),NOW()),(8,'风险等级',80,1,NOW(),NOW()),(9,'审批状态',90,1,NOW(),NOW()),
(10,'交易场所',100,1,NOW(),NOW()),(11,'贷款用途',110,1,NOW(),NOW()),(12,'担保方式',120,1,NOW(),NOW());

INSERT INTO `rule_preset_option_item` (set_id, option_value, option_label, sort_no) VALUES
(1,'AAA+','AAA+',1),(1,'AAA','AAA',2),(1,'AA+','AA+',3),(1,'AA','AA',4),(1,'AA-','AA-',5),
(1,'A+','A+',6),(1,'A','A',7),(1,'A-','A-',8),(1,'BBB+','BBB+',9),(1,'BBB','BBB',10),
(1,'BBB-','BBB-',11),(1,'BB+','BB+',12),(1,'BB','BB',13),(1,'BB-','BB-',14),(1,'B','B',15),
(1,'CCC','CCC',16),(1,'CC','CC',17),(1,'C','C',18),(1,'D','D',19),
(2,'1级','1级',1),(2,'2级','2级',2),(2,'3级','3级',3),(2,'4级','4级',4),(2,'5级','5级',5),
(2,'6级','6级',6),(2,'7级','7级',7),(2,'8级','8级',8),(2,'9级','9级',9),(2,'10级','10级',10),
(3,'中诚信','中诚信',1),(3,'联合资信','联合资信',2),(3,'大公国际','大公国际',3),(3,'东方金诚','东方金诚',4),
(3,'标准普尔','标准普尔',5),(3,'穆迪','穆迪',6),(3,'惠誉','惠誉',7),
(4,'1年以内','1年以内',1),(4,'1-3年','1-3年',2),(4,'3-5年','3-5年',3),(4,'5-7年','5-7年',4),
(4,'7-10年','7-10年',5),(4,'10年以上','10年以上',6),
(5,'国债','国债',1),(5,'地方政府债','地方政府债',2),(5,'金融债','金融债',3),(5,'企业债','企业债',4),
(5,'公司债','公司债',5),(5,'ABS','ABS',6),(5,'ABN','ABN',7),(5,'可转债','可转债',8),(5,'短融','短融',9),
(5,'中票','中票',10),(5,'PPN','PPN',11),
(6,'金融','金融',1),(6,'地产','地产',2),(6,'城投','城投',3),(6,'制造业','制造业',4),(6,'能源','能源',5),
(6,'交通运输','交通运输',6),(6,'信息技术','信息技术',7),(6,'消费','消费',8),(6,'医疗','医疗',9),(6,'公用事业','公用事业',10),
(7,'是','是',1),(7,'否','否',2),
(8,'低风险','低风险',1),(8,'中低风险','中低风险',2),(8,'中风险','中风险',3),(8,'中高风险','中高风险',4),(8,'高风险','高风险',5),
(9,'待审批','待审批',1),(9,'审批中','审批中',2),(9,'已通过','已通过',3),(9,'已拒绝','已拒绝',4),(9,'已撤回','已撤回',5),
(10,'银行间','银行间市场',1),(10,'上交所','上交所',2),(10,'深交所','深交所',3),(10,'北交所','北交所',4),
(10,'中金所','中金所',5),(10,'上期所','上期所',6),(10,'OTC','场外柜台',7),
(11,'经营周转','经营周转',1),(11,'购房按揭','购房按揭',2),(11,'消费信贷','消费信贷',3),(11,'债务置换','债务置换',4),(11,'项目融资','项目融资',5),
(12,'信用','信用',1),(12,'保证','保证',2),(12,'抵押','抵押',3),(12,'质押','质押',4),(12,'组合担保','组合担保',5);

-- ============================================================
-- 10条规则，全部 QLExpress 兼容格式，带换行
-- ============================================================
INSERT INTO `rule_definition` (id, rule_name, description, category_code, script, status, deleted_flag, crte_time, updt_time) VALUES
(1,'订单金额折扣规则','根据订单金额和用户等级计算折扣','pricing',
'discount = 1.0;
if (orderAmount >= 10000) {
    discount = 0.75;
} else if (orderAmount >= 5000) {
    discount = 0.85;
} else if (orderAmount >= 1000) {
    discount = 0.90;
}
if (userLevel == "GOLD") {
    discount = discount - 0.05;
} else if (userLevel == "VIP") {
    discount = discount - 0.08;
}
return discount;','active',0,'2025-03-01 10:20:00','2025-03-20 08:00:00'),

(2,'利率定价规则','基准利率+期限加点+评级调整+风险溢价','pricing',
'finalRate = baseRate;
if (loanTerm == "1年以内") {
    finalRate = finalRate + 0.2;
} else if (loanTerm == "1-3年") {
    finalRate = finalRate + 0.5;
} else if (loanTerm == "3-5年") {
    finalRate = finalRate + 0.8;
} else {
    finalRate = finalRate + 1.2;
}
if (customerRating == "AAA" || customerRating == "AA+" || customerRating == "AA") {
    finalRate = finalRate - 0.3;
} else if (customerRating == "BBB" || customerRating == "BBB-" || customerRating == "BB+") {
    finalRate = finalRate + 0.5;
}
finalRate = finalRate + riskPremium;
return finalRate;','active',0,'2025-04-10 09:00:00','2025-04-10 09:00:00'),

(3,'用户风控评分规则','信用评分+负债率+逾期+月收入综合评分','risk',
'riskScore = 0;
if (creditScore < 600) {
    riskScore = riskScore + 40;
} else if (creditScore < 700) {
    riskScore = riskScore + 20;
}
if (debtRatio > 0.7) {
    riskScore = riskScore + 30;
}
if (overdueTimes > 3) {
    riskScore = riskScore + 25;
}
if (monthlyIncome < 3000) {
    riskScore = riskScore + 15;
}
return riskScore;','active',0,'2025-03-05 14:30:00','2025-03-05 14:30:00'),

(4,'债券投资准入规则','string+select+select+multiselect+number 全类型','risk',
'approved = 1;
reason = "";
if (issuerRating == "D" || issuerRating == "C" || issuerRating == "CC" || issuerRating == "CCC") {
    approved = 0;
    reason = reason + "主体评级过低(" + issuerRating + ");";
}
if (bondType == "ABS" || bondType == "ABN") {
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

(5,'异常交易检测规则','交易金额+频次+账户年限+交易类型','risk',
'abnormalScore = 0;
if (transactionAmount > 500000) {
    abnormalScore = abnormalScore + 30;
}
if (dailyFrequency > 20) {
    abnormalScore = abnormalScore + 25;
}
if (accountAge < 6) {
    abnormalScore = abnormalScore + 20;
}
if (transactionType == "跨境转账" || transactionType == "大额提现") {
    abnormalScore = abnormalScore + 25;
}
if (abnormalScore > 50) {
    return "异常-" + abnormalScore;
}
return "正常-" + abnormalScore;','disabled',0,'2025-05-01 08:00:00','2025-05-10 12:00:00'),

(6,'会员积分兑换规则','消费金额*10 按会员等级翻倍','marketing',
'basePoints = consumeAmount * 10;
if (memberLevel == "VIP") {
    return basePoints * 2;
} else if (memberLevel == "GOLD") {
    return basePoints * 1.5;
}
return basePoints;','active',0,'2025-02-20 09:10:00','2025-02-20 09:10:00'),

(7,'客户优惠折扣规则','string+number+select 年消费越高折扣越大','marketing',
'discount = 1.0;
if (annualConsumption > 500000) {
    discount = 0.70;
} else if (annualConsumption > 200000) {
    discount = 0.80;
} else if (annualConsumption > 50000) {
    discount = 0.90;
}
if (memberTier == "钻石会员") {
    discount = discount - 0.05;
} else if (memberTier == "金卡会员") {
    discount = discount - 0.03;
} else if (memberTier == "银卡会员") {
    discount = discount - 0.02;
}
if (discount < 0.60) {
    discount = 0.60;
}
return discount;','active',0,'2025-05-05 10:00:00','2025-05-05 10:00:00'),

(8,'产品推荐规则','select+multiselect+number 智能推荐','marketing',
'productType = "通用产品";
if (budget > 100000) {
    if (userAgeGroup == "26-35岁" && interests.indexOf("科技") >= 0) {
        productType = "高端数码";
    } else if (userAgeGroup == "36-50岁" && interests.indexOf("理财") >= 0) {
        productType = "财富管理";
    } else {
        productType = "定制方案";
    }
} else if (budget > 10000) {
    if (interests.indexOf("旅游") >= 0 || interests.indexOf("户外") >= 0) {
        productType = "旅行套餐";
    } else if (interests.indexOf("教育") >= 0) {
        productType = "在线课程";
    } else {
        productType = "标准产品";
    }
} else {
    if (interests.indexOf("游戏") >= 0 || interests.indexOf("娱乐") >= 0) {
        productType = "娱乐会员";
    } else {
        productType = "基础产品";
    }
}
return productType;','active',0,'2025-05-08 14:00:00','2025-05-08 14:00:00'),

(9,'合同审批权限规则','string+select 部门+密级判定审批权限','permission',
'if (fileLevel == "绝密") {
    return "需董事会审批";
} else if (fileLevel == "机密") {
    if (applicantDept == "法务部" || applicantDept == "风控部") {
        return "部门负责人审批";
    }
    return "分管副总审批";
} else if (fileLevel == "内部") {
    return "部门负责人审批";
}
return "直接归档";','active',0,'2025-04-25 15:00:00','2025-04-25 15:00:00'),

(10,'贷款审批规则','string+number+select+number+select 综合判定','business',
'if (loanAmount > 500) {
    return "超出审批额度，请提交贷审会";
}
if (creditScore < 600) {
    return "征信评分不足，建议拒绝";
}
if (loanAmount > 100 && guaranteeType == "信用") {
    return "大额信用贷款需追加担保";
}
if (loanPurpose == "项目融资" && guaranteeType == "信用") {
    return "项目融资不可采用纯信用方式";
}
if (creditScore >= 700 && loanAmount <= 100) {
    return "自动审批通过";
}
return "建议人工审核";','active',0,'2025-05-12 09:00:00','2025-05-12 09:00:00');

INSERT INTO `rule_param` (id, rule_id, param_name, param_label, param_type, required, sort_no, crte_time, updt_time) VALUES
(1,1,'orderAmount','订单金额','number',1,1,NOW(),NOW()),
(2,1,'userLevel','用户等级','select',0,2,NOW(),NOW()),
(3,2,'baseRate','基准利率(%)','number',1,1,NOW(),NOW()),
(4,2,'loanTerm','贷款期限','select',1,2,NOW(),NOW()),
(5,2,'customerRating','客户评级','select',1,3,NOW(),NOW()),
(6,2,'riskPremium','风险溢价(%)','number',0,4,NOW(),NOW()),
(7,3,'creditScore','信用评分','number',1,1,NOW(),NOW()),
(8,3,'monthlyIncome','月收入(元)','number',1,2,NOW(),NOW()),
(9,3,'debtRatio','负债率','number',1,3,NOW(),NOW()),
(10,3,'overdueTimes','逾期次数','number',1,4,NOW(),NOW()),
(11,4,'issuerRating','发行人评级','string',1,1,NOW(),NOW()),
(12,4,'bondType','债券类型','select',1,2,NOW(),NOW()),
(13,4,'industry','所属行业','select',1,3,NOW(),NOW()),
(14,4,'allowedExchanges','允许交易场所','multiselect',1,4,NOW(),NOW()),
(15,4,'minIssueAmount','最低发行规模(亿)','number',1,5,NOW(),NOW()),
(16,5,'transactionAmount','交易金额(元)','number',1,1,NOW(),NOW()),
(17,5,'dailyFrequency','日交易频次','number',1,2,NOW(),NOW()),
(18,5,'accountAge','账户年限(月)','number',1,3,NOW(),NOW()),
(19,5,'transactionType','交易类型','select',1,4,NOW(),NOW()),
(20,6,'consumeAmount','消费金额','number',1,1,NOW(),NOW()),
(21,6,'memberLevel','会员等级','select',1,2,NOW(),NOW()),
(22,7,'customerName','客户名称','string',1,1,NOW(),NOW()),
(23,7,'annualConsumption','年消费额(元)','number',1,2,NOW(),NOW()),
(24,7,'memberTier','会员等级','select',1,3,NOW(),NOW()),
(25,8,'userAgeGroup','用户年龄段','select',1,1,NOW(),NOW()),
(26,8,'interests','兴趣标签','multiselect',1,2,NOW(),NOW()),
(27,8,'budget','预算金额(元)','number',1,3,NOW(),NOW()),
(28,9,'applicantDept','申请部门','string',1,1,NOW(),NOW()),
(29,9,'fileLevel','合同密级','select',1,2,NOW(),NOW()),
(30,10,'applicantName','申请人姓名','string',1,1,NOW(),NOW()),
(31,10,'loanAmount','贷款金额(万元)','number',1,2,NOW(),NOW()),
(32,10,'loanPurpose','贷款用途','select',1,3,NOW(),NOW()),
(33,10,'creditScore','征信评分','number',1,4,NOW(),NOW()),
(34,10,'guaranteeType','担保方式','select',1,5,NOW(),NOW());

INSERT INTO `rule_param_option` (id, param_id, option_value, option_label, sort_no, crte_time, updt_time) VALUES
(1,2,'普通','普通用户',1,NOW(),NOW()),(2,2,'VIP','VIP会员',2,NOW(),NOW()),(3,2,'GOLD','金牌会员',3,NOW(),NOW()),
(4,4,'1年以内','1年以内',1,NOW(),NOW()),(5,4,'1-3年','1-3年',2,NOW(),NOW()),(6,4,'3-5年','3-5年',3,NOW(),NOW()),
(7,4,'5-7年','5-7年',4,NOW(),NOW()),(8,4,'7-10年','7-10年',5,NOW(),NOW()),(9,4,'10年以上','10年以上',6,NOW(),NOW()),
(10,5,'AAA','AAA',1,NOW(),NOW()),(11,5,'AA+','AA+',2,NOW(),NOW()),(12,5,'AA','AA',3,NOW(),NOW()),
(13,5,'A','A',4,NOW(),NOW()),(14,5,'BBB+','BBB+',5,NOW(),NOW()),(15,5,'BBB','BBB',6,NOW(),NOW()),
(16,5,'BBB-','BBB-',7,NOW(),NOW()),(17,5,'BB+','BB+',8,NOW(),NOW()),
(18,12,'国债','国债',1,NOW(),NOW()),(19,12,'金融债','金融债',2,NOW(),NOW()),(20,12,'企业债','企业债',3,NOW(),NOW()),
(21,12,'公司债','公司债',4,NOW(),NOW()),(22,12,'ABS','ABS',5,NOW(),NOW()),(23,12,'ABN','ABN',6,NOW(),NOW()),
(24,12,'中票','中票',7,NOW(),NOW()),(25,12,'短融','短融',8,NOW(),NOW()),
(26,13,'金融','金融',1,NOW(),NOW()),(27,13,'地产','地产',2,NOW(),NOW()),(28,13,'城投','城投',3,NOW(),NOW()),
(29,13,'制造业','制造业',4,NOW(),NOW()),(30,13,'能源','能源',5,NOW(),NOW()),(31,13,'信息技术','信息技术',6,NOW(),NOW()),
(32,14,'银行间','银行间市场',1,NOW(),NOW()),(33,14,'上交所','上交所',2,NOW(),NOW()),(34,14,'深交所','深交所',3,NOW(),NOW()),
(35,14,'北交所','北交所',4,NOW(),NOW()),(36,14,'OTC','场外柜台',5,NOW(),NOW()),
(37,19,'普通转账','普通转账',1,NOW(),NOW()),(38,19,'大额提现','大额提现',2,NOW(),NOW()),
(39,19,'跨境转账','跨境转账',3,NOW(),NOW()),(40,19,'第三方支付','第三方支付',4,NOW(),NOW()),
(41,21,'普通','普通会员',1,NOW(),NOW()),(42,21,'VIP','VIP会员',2,NOW(),NOW()),(43,21,'GOLD','金卡会员',3,NOW(),NOW()),
(44,24,'普通会员','普通会员',1,NOW(),NOW()),(45,24,'银卡会员','银卡会员',2,NOW(),NOW()),
(46,24,'金卡会员','金卡会员',3,NOW(),NOW()),(47,24,'钻石会员','钻石会员',4,NOW(),NOW()),
(48,25,'18-25岁','18-25岁',1,NOW(),NOW()),(49,25,'26-35岁','26-35岁',2,NOW(),NOW()),
(50,25,'36-50岁','36-50岁',3,NOW(),NOW()),(51,25,'50岁以上','50岁以上',4,NOW(),NOW()),
(52,26,'科技','科技数码',1,NOW(),NOW()),(53,26,'理财','投资理财',2,NOW(),NOW()),(54,26,'旅游','旅游出行',3,NOW(),NOW()),
(55,26,'户外','户外运动',4,NOW(),NOW()),(56,26,'教育','在线教育',5,NOW(),NOW()),(57,26,'游戏','游戏电竞',6,NOW(),NOW()),
(58,26,'娱乐','影音娱乐',7,NOW(),NOW()),
(59,29,'内部','内部文件',1,NOW(),NOW()),(60,29,'机密','机密文件',2,NOW(),NOW()),(61,29,'绝密','绝密文件',3,NOW(),NOW()),
(62,32,'经营周转','经营周转',1,NOW(),NOW()),(63,32,'购房按揭','购房按揭',2,NOW(),NOW()),
(64,32,'消费信贷','消费信贷',3,NOW(),NOW()),(65,32,'项目融资','项目融资',4,NOW(),NOW()),
(66,34,'信用','信用',1,NOW(),NOW()),(67,34,'保证','保证',2,NOW(),NOW()),(68,34,'抵押','抵押',3,NOW(),NOW()),
(69,34,'质押','质押',4,NOW(),NOW()),(70,34,'组合担保','组合担保',5,NOW(),NOW());

INSERT INTO `rule_test_case` (id, case_name, rule_id, rule_name_snapshot, last_result, last_output, last_run_time, crte_time, updt_time) VALUES
(1,'大额VIP折扣',1,'订单金额折扣规则','pass','0.67','2025-03-20 08:10:00',NOW(),NOW()),
(2,'中等GOLD折扣',1,'订单金额折扣规则','pass','0.80','2025-03-20 08:15:00',NOW(),NOW()),
(3,'小额普通无折扣',1,'订单金额折扣规则','pass','1.0','2025-03-20 08:20:00',NOW(),NOW()),
(4,'AAA短期低溢价',2,'利率定价规则','pass','3.55','2025-04-10 09:30:00',NOW(),NOW()),
(5,'BBB长期高溢价',2,'利率定价规则','pass','6.65','2025-04-10 09:35:00',NOW(),NOW()),
(6,'中风险用户',3,'用户风控评分规则','pass','20','2025-03-05 14:40:00',NOW(),NOW()),
(7,'高风险用户',3,'用户风控评分规则','pass','110','2025-03-05 14:45:00',NOW(),NOW()),
(8,'低风险用户',3,'用户风控评分规则','pass','0','2025-03-05 14:50:00',NOW(),NOW()),
(9,'AAA金融债通过',4,'债券投资准入规则','pass','通过:','2025-04-20 10:00:00',NOW(),NOW()),
(10,'D级地产小规模否决',4,'债券投资准入规则','fail','否决:主体评级过低(D);规模不足5亿;','2025-04-20 10:05:00',NOW(),NOW()),
(11,'大额高频跨境',5,'异常交易检测规则',NULL,NULL,NULL,NOW(),NOW()),
(12,'VIP积分翻倍',6,'会员积分兑换规则','pass','100000','2025-02-20 10:00:00',NOW(),NOW()),
(13,'普通会员积分',6,'会员积分兑换规则','pass','30000','2025-02-20 10:05:00',NOW(),NOW()),
(14,'钻石客户大额',7,'客户优惠折扣规则','pass','0.65','2025-05-05 11:00:00',NOW(),NOW()),
(15,'普通客户小额',7,'客户优惠折扣规则','pass','1.0','2025-05-05 11:05:00',NOW(),NOW()),
(16,'科技青年高预算',8,'产品推荐规则',NULL,NULL,NULL,NOW(),NOW()),
(17,'风控部机密',9,'合同审批权限规则','pass','部门负责人审批','2025-04-25 16:00:00',NOW(),NOW()),
(18,'高信用小额经营贷',10,'贷款审批规则',NULL,NULL,NULL,NOW(),NOW());

INSERT INTO `rule_test_case_param` (id, case_id, param_name, param_label_snapshot, param_type_snapshot, param_value, crte_time, updt_time) VALUES
(1,1,'orderAmount','订单金额','number','12000',NOW(),NOW()),
(2,1,'userLevel','用户等级','select','VIP',NOW(),NOW()),
(3,2,'orderAmount','订单金额','number','5500',NOW(),NOW()),
(4,2,'userLevel','用户等级','select','GOLD',NOW(),NOW()),
(5,3,'orderAmount','订单金额','number','800',NOW(),NOW()),
(6,3,'userLevel','用户等级','select','普通',NOW(),NOW()),
(7,4,'baseRate','基准利率(%)','number','3.15',NOW(),NOW()),
(8,4,'loanTerm','贷款期限','select','1年以内',NOW(),NOW()),
(9,4,'customerRating','客户评级','select','AAA',NOW(),NOW()),
(10,4,'riskPremium','风险溢价(%)','number','0.5',NOW(),NOW()),
(11,5,'baseRate','基准利率(%)','number','3.15',NOW(),NOW()),
(12,5,'loanTerm','贷款期限','select','5-7年',NOW(),NOW()),
(13,5,'customerRating','客户评级','select','BBB',NOW(),NOW()),
(14,5,'riskPremium','风险溢价(%)','number','1.8',NOW(),NOW()),
(15,6,'creditScore','信用评分','number','650',NOW(),NOW()),
(16,6,'monthlyIncome','月收入(元)','number','8000',NOW(),NOW()),
(17,6,'debtRatio','负债率','number','0.5',NOW(),NOW()),
(18,6,'overdueTimes','逾期次数','number','1',NOW(),NOW()),
(19,7,'creditScore','信用评分','number','550',NOW(),NOW()),
(20,7,'monthlyIncome','月收入(元)','number','2500',NOW(),NOW()),
(21,7,'debtRatio','负债率','number','0.85',NOW(),NOW()),
(22,7,'overdueTimes','逾期次数','number','5',NOW(),NOW()),
(23,8,'creditScore','信用评分','number','780',NOW(),NOW()),
(24,8,'monthlyIncome','月收入(元)','number','35000',NOW(),NOW()),
(25,8,'debtRatio','负债率','number','0.15',NOW(),NOW()),
(26,8,'overdueTimes','逾期次数','number','0',NOW(),NOW()),
(27,9,'issuerRating','发行人评级','string','AAA',NOW(),NOW()),
(28,9,'bondType','债券类型','select','金融债',NOW(),NOW()),
(29,9,'industry','所属行业','select','金融',NOW(),NOW()),
(30,9,'allowedExchanges','允许交易场所','multiselect','银行间,上交所,深交所',NOW(),NOW()),
(31,9,'minIssueAmount','最低发行规模(亿)','number','50',NOW(),NOW()),
(32,10,'issuerRating','发行人评级','string','D',NOW(),NOW()),
(33,10,'bondType','债券类型','select','企业债',NOW(),NOW()),
(34,10,'industry','所属行业','select','地产',NOW(),NOW()),
(35,10,'allowedExchanges','允许交易场所','multiselect','上交所',NOW(),NOW()),
(36,10,'minIssueAmount','最低发行规模(亿)','number','3',NOW(),NOW()),
(37,11,'transactionAmount','交易金额(元)','number','800000',NOW(),NOW()),
(38,11,'dailyFrequency','日交易频次','number','35',NOW(),NOW()),
(39,11,'accountAge','账户年限(月)','number','3',NOW(),NOW()),
(40,11,'transactionType','交易类型','select','跨境转账',NOW(),NOW()),
(41,12,'consumeAmount','消费金额','number','5000',NOW(),NOW()),
(42,12,'memberLevel','会员等级','select','VIP',NOW(),NOW()),
(43,13,'consumeAmount','消费金额','number','3000',NOW(),NOW()),
(44,13,'memberLevel','会员等级','select','普通',NOW(),NOW()),
(45,14,'customerName','客户名称','string','张三',NOW(),NOW()),
(46,14,'annualConsumption','年消费额(元)','number','600000',NOW(),NOW()),
(47,14,'memberTier','会员等级','select','钻石会员',NOW(),NOW()),
(48,15,'customerName','客户名称','string','李四',NOW(),NOW()),
(49,15,'annualConsumption','年消费额(元)','number','30000',NOW(),NOW()),
(50,15,'memberTier','会员等级','select','普通会员',NOW(),NOW()),
(51,16,'userAgeGroup','用户年龄段','select','26-35岁',NOW(),NOW()),
(52,16,'interests','兴趣标签','multiselect','科技,游戏,娱乐',NOW(),NOW()),
(53,16,'budget','预算金额(元)','number','150000',NOW(),NOW()),
(54,17,'applicantDept','申请部门','string','风控部',NOW(),NOW()),
(55,17,'fileLevel','合同密级','select','机密',NOW(),NOW()),
(56,18,'applicantName','申请人姓名','string','王五',NOW(),NOW()),
(57,18,'loanAmount','贷款金额(万元)','number','80',NOW(),NOW()),
(58,18,'loanPurpose','贷款用途','select','经营周转',NOW(),NOW()),
(59,18,'creditScore','征信评分','number','720',NOW(),NOW()),
(60,18,'guaranteeType','担保方式','select','抵押',NOW(),NOW());

INSERT INTO `rule_test_run` (id, case_id, rule_id, run_status, output, error_message, start_time, finish_time, crte_time, updt_time) VALUES
(1,1,1,'pass','0.67',NULL,'2025-03-20 08:10:00','2025-03-20 08:10:01',NOW(),NOW()),
(2,2,1,'pass','0.80',NULL,'2025-03-20 08:15:00','2025-03-20 08:15:01',NOW(),NOW()),
(3,3,1,'pass','1.0',NULL,'2025-03-20 08:20:00','2025-03-20 08:20:01',NOW(),NOW()),
(4,4,2,'pass','3.55',NULL,'2025-04-10 09:30:00','2025-04-10 09:30:01',NOW(),NOW()),
(5,5,2,'pass','6.65',NULL,'2025-04-10 09:35:00','2025-04-10 09:35:01',NOW(),NOW()),
(6,6,3,'pass','20',NULL,'2025-03-05 14:40:00','2025-03-05 14:40:01',NOW(),NOW()),
(7,7,3,'pass','110',NULL,'2025-03-05 14:45:00','2025-03-05 14:45:01',NOW(),NOW()),
(8,8,3,'pass','0',NULL,'2025-03-05 14:50:00','2025-03-05 14:50:01',NOW(),NOW()),
(9,9,4,'pass','通过:',NULL,'2025-04-20 10:00:00','2025-04-20 10:00:01',NOW(),NOW()),
(10,10,4,'fail','否决:主体评级过低(D);规模不足5亿;',NULL,'2025-04-20 10:05:00','2025-04-20 10:05:01',NOW(),NOW()),
(11,12,6,'pass','100000',NULL,'2025-02-20 10:00:00','2025-02-20 10:00:01',NOW(),NOW()),
(12,13,6,'pass','30000',NULL,'2025-02-20 10:05:00','2025-02-20 10:05:01',NOW(),NOW()),
(13,14,7,'pass','0.65',NULL,'2025-05-05 11:00:00','2025-05-05 11:00:01',NOW(),NOW()),
(14,15,7,'pass','1.0',NULL,'2025-05-05 11:05:00','2025-05-05 11:05:01',NOW(),NOW()),
(15,17,9,'pass','部门负责人审批',NULL,'2025-04-25 16:00:00','2025-04-25 16:00:01',NOW(),NOW());

INSERT INTO `rule_test_run_log` (id, run_id, log_time, log_type, message, crte_time, updt_time) VALUES
(1,1,'2025-03-20 08:10:00','info','执行规则: 订单金额折扣规则',NOW(),NOW()),
(2,1,'2025-03-20 08:10:00','info','参数注入: orderAmount=12000, userLevel=VIP',NOW(),NOW()),
(3,1,'2025-03-20 08:10:01','success','折扣: 0.67',NOW(),NOW()),
(4,5,'2025-04-10 09:35:00','info','执行规则: 利率定价规则',NOW(),NOW()),
(5,5,'2025-04-10 09:35:00','info','参数注入: baseRate=3.15, loanTerm=5-7年, customerRating=BBB, riskPremium=1.8',NOW(),NOW()),
(6,5,'2025-04-10 09:35:01','success','最终利率: 6.65',NOW(),NOW()),
(7,7,'2025-03-05 14:45:00','info','执行规则: 用户风控评分规则',NOW(),NOW()),
(8,7,'2025-03-05 14:45:00','info','参数注入: creditScore=550, monthlyIncome=2500, debtRatio=0.85, overdueTimes=5',NOW(),NOW()),
(9,7,'2025-03-05 14:45:01','success','风险评分: 110',NOW(),NOW()),
(10,10,'2025-04-20 10:05:00','info','执行规则: 债券投资准入规则',NOW(),NOW()),
(11,10,'2025-04-20 10:05:00','info','参数注入: issuerRating=D, bondType=企业债, industry=地产, minIssueAmount=3',NOW(),NOW()),
(12,10,'2025-04-20 10:05:01','error','否决原因: 主体评级过低(D);',NOW(),NOW()),
(13,10,'2025-04-20 10:05:01','error','否决原因: 规模不足5亿;',NOW(),NOW()),
(14,15,'2025-04-25 16:00:00','info','执行规则: 合同审批权限规则',NOW(),NOW()),
(15,15,'2025-04-25 16:00:00','info','参数注入: applicantDept=风控部, fileLevel=机密',NOW(),NOW()),
(16,15,'2025-04-25 16:00:01','success','审批结果: 部门负责人审批',NOW(),NOW());

ALTER TABLE `rule_category` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_preset_option_set` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_definition` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_param` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_param_option` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_test_case` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_test_case_param` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_test_run` AUTO_INCREMENT = 1000;
ALTER TABLE `rule_test_run_log` AUTO_INCREMENT = 1000;
