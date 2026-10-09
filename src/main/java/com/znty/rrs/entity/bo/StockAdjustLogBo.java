package com.znty.rrs.entity.bo;

import java.util.Date;
import lombok.Data;

/** 股票调库记录业务对象，对应 ip_adjust_log_stock */
@Data
public class StockAdjustLogBo {
    /** 主键 ID */
    private Long id;
    /** 股票代码 */
    private String stockCode;
    /** 股票全称 */
    private String stockName;
    /** 股票简称 */
    private String stockShortName;
    /** 股票产品类型编码 */
    private String securityType;
    /** 冻结的行业编码 */
    private String industryCode;
    /** 冻结的行业名称 */
    private String industryName;
    /** 冻结的最新评级 */
    private String latestRating;
    /** 冻结的上次评级 */
    private String previousRating;
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
    /** 删除标记 */
    private Integer isDeleted;
    /** 创建时间 */
    private Date crteTime;
    /** 更新时间 */
    private Date updtTime;
    /** 审核完成时间 */
    private Date auditTime;
}
