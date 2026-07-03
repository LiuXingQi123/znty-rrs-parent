package com.znty.rrs.entity.mymatters;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 我的事宜列表返回对象。
 */
@Data
public class MyMattersDto {

    /** 步骤 ID，作为列表主键 */
    private Long id;

    /** 调库记录 ID */
    private Long adjustLogId;

    /** 证券代码 */
    private String securityCode;

    /** 证券名称 */
    private String securityShortName;

    /** CRMW代码 */
    private String crmwScode;

    /** 目标投资池 ID */
    private Long targetPoolId;

    /** 目标投资池名称（叶子名称） */
    private String targetPoolName;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 审批步骤 ID */
    private Long stepId;

    /** 流程名称 */
    private String flowName;

    /** 步骤名称 */
    private String stepName;

    /** 流程描述 */
    private String processDescription;

    /** 业务场景：crmwAdjust=CRMW池调整 / forbiddenCompanyAdjust=禁投池主体调整 / securityAdjust=证券池调整 */
    private String businessScene;

    /** 审核状态 */
    private String auditStatus;

    /** 步骤状态 */
    private String stepStatus;

    /** 发起人 */
    private String initiatorName;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
}
