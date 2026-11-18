package com.znty.rrs.entity.securitypooladjust;

import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketMappingDto;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** 当前批次的证券调库校验数据，批次结束后丢弃 */
public class BatchSecurityCheckData {
    /** 自动落池批次需要占用本轮容量并验证材料和直通资格 */
    public boolean scheduled;
    /** 本轮认可外评机构是否已配置，黑名单校验要求非空 */
    public boolean ratingAgenciesConfigured;
    /** 同时符合定时任务的品种、证券状态及到期门槛的代码 */
    public final Set<String> scheduledEligibleCodes = new HashSet<>();
    /** 主体 Excel 跟随允许跳过距到期门槛的债券 */
    public final Set<String> scheduledEligibleWithoutMaturityCodes = new HashSet<>();

    /** 发行主体最近一次生效的 Excel 决定为调出的池 */
    public final Map<String, Set<Long>> excelCompanyOutPools = new HashMap<>();
    /** 证券主档，构建每只券的校验对象时复制 */
    public final Map<String, SecurityInfoBo> securities = new HashMap<>();
    /** Excel 输入代码忽略大小写后对应的主档代码 */
    public final Map<String, String> excelSecurityCodes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** 本次 Excel 查过的主档输入，包含查询未命中的代码 */
    public final Set<String> lookedUpExcelSecurities = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    /** 本次查过的关联主体及自选权益人，用于补齐首屏缺项 */
    public final Set<String> lookedUpRatingCompanies = new HashSet<>();
    public final Set<String> lookedUpSelfSelectedCompanies = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    /** 返回主档里的证券代码，找不到时保留输入用于报错 */
    public String resolveExcelSecurityCode(String code) {
        return excelSecurityCodes.getOrDefault(code, code);
    }

    /** 投资池及对应的关系 */
    public final Map<Long, InvestmentPoolBo> pools = new HashMap<>();
    /** 投资池 ID、关系类型和关联池 ID */
    public final Map<Long, Map<String, List<Long>>> relations = new HashMap<>();
    /** 证券和主体当前生效的所在池 */
    public final Map<String, Set<Long>> activePools = new HashMap<>();
    /** 证券在池记录用于互斥调出的入池时间 */
    public final Map<String, Map<Long, Date>> entryTimes = new HashMap<>();
    /** 各池当前持仓数量 */
    public final Map<Long, Integer> counts = new HashMap<>();
    /** 当天存在开放区间的池 */
    public final Set<Long> openPools = new HashSet<>();
    /** 各券在途占用的目标池 */
    public final Map<String, Set<Long>> pendingPools = new HashMap<>();
    /** 各券首个在途流程节点 */
    public final Map<String, String> pendingLabels = new HashMap<>();
    /** 证券品种字典 */
    public final Map<String, String> categories = new HashMap<>();
    /** 证券码对应的所有市场记录，用于识别缺失或重复映射 */
    public final Map<String, List<SecurityMarketMappingDto>> markets = new HashMap<>();
    /** 各主券关联的其他市场代码 */
    public final Map<String, List<String>> relatedCodes = new HashMap<>();
    /** 债券关联评级主体，保留原候选顺序 */
    public final Map<String, List<RelatedRatingCompanyDto>> ratingCompanies = new HashMap<>();
    /** 通过精确代码查询的 ABS 自选权益人 */
    public final Map<String, SelfSelectedRightsHolderDto> selfSelectedCompanies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    /** 各实际 ABS 代码尚未确认主体或已占用的主体记录 */
    public final Map<String, List<Row>> absIdentities = new HashMap<>();
    /** 外评下调的发行主体或担保人 */
    public final Set<String> downgradedCompanies = new HashSet<>();
    /** 黑名单质押库条件中的低外评主体 */
    public final Set<String> lowRatedCompanies = new HashSet<>();
    /** 以主体记录在禁止库或重点观察名单生效的池 */
    public final Map<String, Set<Long>> blacklistCompanyPools = new HashMap<>();
    /** 期限档配置，同批共用 */
    public List<CreditBondTermBucketBo> termBuckets;
    /** 内评分档的排序 */
    public List<CreditBondInnerRatingGradeBo> ratingGrades;
    /** 同评级档和期限档的允许入池列表 */
    public final Map<String, List<Long>> matrix = new HashMap<>();
    /** 批次流程定义查询结果 */
    public final Map<String, FlowDefinitionBo> flows = new HashMap<>();
    /** 同发行人的简易入库历史资格 */
    public final Set<String> nonSimpleInboundIssuers = new HashSet<>();
    /** 同发行人的简易入库信评附件资格 */
    public final Set<String> recentReportIssuers = new HashSet<>();
    /** 同发行人近六个月报告豁免资格 */
    public final Set<String> reportExemptIssuers = new HashSet<>();
    /** 有有效 CRMW 组合来源的标的证券 */
    public final Set<String> crmwSources = new HashSet<>();

    /** 读取补债主档时一并返回此证券是否仍符合任务范围 */
    public static class ScheduledSecurity extends SecurityInfoBo {
        /** 证券符合任务的品种、上市状态和临时代码要求 */
        @Getter
        @Setter
        private boolean eligibleForFollow;
    }

    /** 批量查询的证券与池状态、在途节点、品种或关联代码 */
    @Data
    public static class Row {
        /** 传入的证券代码 */
        private String securityCode;
        /** 主档确认或在途申请使用的 ABS 主体 */
        private String companyCode;
        /** 是否已有在库或在途记录但尚未确认主体 */
        private boolean unconfirmedIdentity;
        /** 池 ID */
        private Long poolId;
        /** 入池时间 */
        private Date entryTime;
        /** 在途节点名称 */
        private String nodeLabel;
        /** 辞典品种或所在池证券类型 */
        private String securityType;
        /** 辞典证券大类 */
        private String categoryType;
        /** 同券关联代码 */
        private String relatedCode;
    }
}
