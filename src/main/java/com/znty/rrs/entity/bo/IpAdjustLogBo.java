package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 证券池调库记录表实体，记录证券每次调入/调出投资池的操作详情及审核状态
 */
@Data
public class IpAdjustLogBo {

    /** 主键 ID */
    private Long id;

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 证券类型 */
    private String securityType;

    /** CRMW名称 */
    private String crmwName;

    /** CRMW 证券代码 */
    private String crmwScode;

    /** CRMW 市场代码 */
    private String crmwMktcode;

    /** CRMW 证券类型 */
    private String crmwStype;

    /** 调整类型 */
    private String adjustType;

    /** 调整模式：调入/调出 */
    private String adjustMode;

    /** 调库批次号，同一次提交产生的多条记录共用 */
    private String adjustBatchNo;

    /** 来源调库日志 ID，写入当前状态表时使用 */
    private Long adjustLogId;

    /** 目标投资池 ID */
    private Long targetPoolId;

    /** 目标投资池名称 */
    private String targetPoolName;

    /** 投资池类型 */
    private String poolType;

    /** 流程定义 ID 快照 */
    private Long flowId;

    /** 流程 Key 快照 */
    private String flowKey;

    /** 流程类型快照 */
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

    /** 调整意见 */
    private String adjustAdvice;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date auditTime;

    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date entryTime;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
