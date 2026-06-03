package com.znty.sirm.model;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调库校验基础参数上下文（供三层校验方法共享）
 */
@Data
public class AdjustCheckContext {

    /** 债券基础信息 */
    private BondInfoBo bondInfo;

    /** 目标投资池信息 */
    private InvestmentPoolBo targetPool;

    /** 目标投资池父级信息（顶级池时为 null） */
    private InvestmentPoolBo parentPool;

    /** 债券当前有效所在池 ID 集合（audit_status=20） */
    private Set<Long> currentPoolIds;

    /** 目标池当前债券数量（用于容量校验） */
    private int poolCurrentCount;

    /** 调整方向：调入 / 调出 */
    private String adjustMode;

    /** 目标池的所有关系配置（relation_type → 关联池 ID 列表） */
    private Map<String, List<Long>> targetPoolRelations;

    /** 全量投资池信息（ID → Bo），用于构建错误消息中的池路径名称 */
    private Map<Long, InvestmentPoolBo> poolMap;

    /** 债券是否存在进行中的调库流程（audit_status IN ('00','11')） */
    private boolean hasPendingProcess;
}
