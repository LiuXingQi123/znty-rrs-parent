package com.znty.rrs.entity.bo;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 基金调库记录业务对象，对应 ip_adjust_log_fund */
@Data
public class FundAdjustLogBo {
    /** 主键 ID */
    private Long id;
    /** 基金代码 */
    private String fundCode;
    /** 基金全称 */
    private String fundName;
    /** 基金简称 */
    private String fundShortName;
    /** 基金产品类型编码 */
    private String securityType;
    /** 基金评分 */
    private BigDecimal fundScore;
    /** 基金投资类型 */
    private String fundInvestmentType;
    /** 是否需要风管领导审批 */
    private Integer needRiskLeaderApproval;
    /** 调整类型 */
    private String adjustType;
    /** 调整方向 */
    private String adjustMode;
    /** 调库批次号 */
    private String adjustBatchNo;
    /** 目标池 ID */
    private Long targetPoolId;
    /** 目标池名称 */
    private String targetPoolName;
    /** 投资池类型 */
    private String poolType;
    /** 流程定义 ID */
    private Long flowId;
    /** 流程 Key */
    private String flowKey;
    /** 流程类型 */
    private String flowType;
    /** 流程名称 */
    private String flowName;
    /** 审核状态 */
    private String auditStatus;
    /** 调整人 ID */
    private String adjusterId;
    /** 调整人名称 */
    private String adjusterName;
    /** 调整原因 */
    private String adjustReason;
    /** 调整建议 */
    private String adjustAdvice;
    /** 提交时间 */
    private Date submitTime;
}
