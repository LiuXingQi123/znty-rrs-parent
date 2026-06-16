package com.znty.sirm.model;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调库校验（checkAdjust）的共享数据载体
 *
 * <p>checkAdjust 每次调用需要加载多项证券/池的基础数据，这些数据在整个调用过程中保持不变。
 * 将其封装为此类后，buildCheckContext、buildAutoResultItem 等辅助方法只需接收一个
 * shared 参数，既能保持方法签名简洁（≤4 个参数），也便于后续扩展新的校验参数——
 * 新增字段只需改动此类和 loadSharedData 的数据加载部分，辅助方法签名无需调整。
 */
@Data
public class AdjustSharedData {

    /** 证券基础信息（来自 sirm_securityinfo） */
    private SecurityInfoBo securityInfo;

    /** 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称 */
    private Map<Long, InvestmentPoolBo> poolMap;

    /** 证券当前有效所在池 ID 集合（ip_pool_status.audit_status='20'） */
    private Set<Long> currentPoolIds;

    /** 全量投资池关系配置（poolId → relationType → 关联池 ID 列表） */
    private Map<Long, Map<String, List<Long>>> poolRelationMap;

    /** 证券是否存在进行中的调库流程（以是否存在待处理步骤为准） */
    private boolean hasPendingProcess;

    /** 证券当前进行中流程所在步骤名称 */
    private String pendingProcessNodeLabel;

    /** 当前证券自身是否在观察池（pool_type='observe'，audit_status='20'） */
    private boolean securityInObservePool;

    /** 证券主体公司（发行人）旗下是否有证券在观察池中 */
    private boolean issuerInObservePool;

    /** 主体评级是否下调（当前版本写死，后续替换为真实评级历史查询） */
    private boolean issuerRatingDowngraded;

    /** 展望评级是否下调（当前版本写死，后续替换为真实评级历史查询） */
    private boolean outlookRatingDowngraded;

    /** 担保人评级是否下调（当前版本写死，后续替换为真实评级历史查询） */
    private boolean guarantorRatingDowngraded;

    /** 本次请求中所有调入操作涉及的目标池 ID 集合，用于互斥池同时勾选校验 */
    private Set<Long> requestInPoolIds;

    /** 本次请求中所有调出操作涉及的目标池 ID 集合，用于互斥池同时勾选校验 */
    private Set<Long> requestOutPoolIds;
}
