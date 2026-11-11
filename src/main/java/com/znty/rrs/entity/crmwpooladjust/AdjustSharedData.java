package com.znty.rrs.entity.crmwpooladjust;


import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 保存一次 CRMW 组合调库校验共用的数据
 *
 * 手工项、联动项和互斥项共用已加载的证券资料、投资池配置及组合在池状态，避免逐项重复查询
 * 同券跨市场项沿用池配置，并按各自的标的代码读取组合在池状态
 */
@Data
public class AdjustSharedData {

    /** 批量及 Excel 校验时是否追加目标池配置的批量流程 */
    private boolean batchFlowOptionsEnabled;

    /** 本次查到证券主数据的主标的和跨市场标的代码，用于判断 Excel 调整组的证券资料是否齐全 */
    private Set<String> knownSecurityCodes = new HashSet<>();

    /** 标的证券基础信息（来自 rrs_securityinfo） */
    private SecurityInfoBo securityInfo;
    /** CRMW 凭证代码 */
    private String crmwScode;
    /** CRMW 凭证的证券类型 */
    private String crmwStype;

    /** 按池 ID 保存的投资池配置，用于查找池详情和拼接池全名 */
    private Map<Long, InvestmentPoolBo> poolMap;

    /** 当前凭证与标的组合有效在池的池 ID（ip_pool_status_crmw.audit_status='20'） */
    private Set<Long> currentPoolIds;

    /** 按池 ID 和关系类型保存的关联池 ID，用于查询来源池、限制池、联动池和互斥池 */
    private Map<Long, Map<String, List<Long>>> poolRelationMap;

    /** 标的证券是否有待处理的 CRMW 调库步骤 */
    private boolean hasPendingProcess;

    /** 当前组合进行中的手工调库流程所涉及的根池 ID，用于检查目标池组是否已有流程 */
    private Set<Long> pendingPoolGroupIds;

    /** 标的证券当前待处理的调库步骤名称 */
    private String pendingProcessNodeLabel;

    /** 标的证券是否有效在观察池（pool_type='observe'，audit_status='20'） */
    private boolean securityInObservePool;

    /** 标的发行人是否在观察池，按发行人代码查询 ip_pool_status 的主体级记录 */
    private boolean issuerInObservePool;

    /** 发行人主体评级是否下调，按 wind_cbondissuerrating 最新记录的当前评级、前次评级和评级变化判断 */
    private boolean issuerRatingDowngraded;

    /** 标的证券的评级展望是否为负面（rrs_securityinfo.rating_outlook） */
    private boolean outlookRatingDowngraded;

    /** 本次选择的担保人评级是否下调，未选担保人时为 false */
    private boolean guarantorRatingDowngraded;

    /** 本次请求调入的目标池 ID，用于检查是否同时调入互斥池 */
    private Set<Long> requestInPoolIds;

    /** 本次请求调出的目标池 ID，用于检查是否同时调出互斥池 */
    private Set<Long> requestOutPoolIds;
}
