package com.znty.rrs.entity.securitypooladjust;


import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 证券调库校验上下文，封装校验所需的证券信息、目标池信息及请求状态，在三层校验方法间共享传递
 */
@Data
public class AdjustCheckContext {

    /** 证券基础信息 */
    private SecurityInfoBo securityInfo;

    /** 目标投资池信息 */
    private InvestmentPoolBo targetPool;

    /** 目标投资池父级信息（顶级池时为 null） */
    private InvestmentPoolBo parentPool;

    /** 证券当前有效所在池 ID 集合（audit_status=20） */
    private Set<Long> currentPoolIds;

    /** 目标池当前证券数量（用于容量校验） */
    private int poolCurrentCount;

    /** 调整方向：调入 / 调出 */
    private String adjustMode;

    /** 目标池的所有关系配置（relation_type → 关联池 ID 列表） */
    private Map<String, List<Long>> targetPoolRelations;

    /** 全量投资池信息（ID → Bo），用于构建错误消息中的池路径名称 */
    private Map<Long, InvestmentPoolBo> poolMap;

    /** 证券是否存在进行中的调库流程（以是否存在待处理步骤为准） */
    private boolean hasPendingProcess;

    /** 证券当前进行中流程所在步骤名称 */
    private String pendingProcessNodeLabel;

    /** 当前证券是否在观察池（pool_type='observe'，audit_status='20'） */
    private boolean securityInObservePool;

    /** 证券主体公司是否在观察池（同发行人的任意证券在观察池中） */
    private boolean issuerInObservePool;

    /** 本次请求中所有调入操作涉及的目标池 ID 集合，用于互斥池同时勾选校验 */
    private Set<Long> requestInPoolIds;

    /** 本次请求中所有调出操作涉及的目标池 ID 集合，用于互斥池同时勾选校验 */
    private Set<Long> requestOutPoolIds;

    /** 证券在目标池的入池时间（ip_pool_status.entry_time，audit_status=20），用于调出冻结期校验，调入时为 null */
    private java.util.Date targetPoolEntryTime;

    /** 证券品种大类（bond/fund/stock/company，查 dict_security_type），用于类型特有校验路由 */
    private String categoryType;
}
