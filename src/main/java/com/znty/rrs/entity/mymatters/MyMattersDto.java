package com.znty.rrs.entity.mymatters;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 我的事宜列表返回对象。
 */
@Data
public class MyMattersDto {

    /** 业务编码 */
    private String businessDomain;

    /** 统一摘要中的业务对象代码 */
    private String objectCode;

    /** 统一摘要中的业务对象名称 */
    private String objectName;

    /** 基金代码，仅基金业务用于定位 */
    private String fundCode;

    /** 基金简称 */
    private String fundShortName;

    /** 股票代码，仅股票业务用于定位 */
    private String stockCode;

    /** 股票简称 */
    private String stockShortName;

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

    /** 业务场景：crmwAdjust=CRMW池调整 / forbiddenCompanyAdjust=禁投池主体调整 / securityAdjust=证券池调整 / fundAdjust=基金池调整 / stockAdjust=股票池调整 */
    private String businessScene;

    /** 审核状态 */
    private String auditStatus;

    /** 步骤状态 */
    private String stepStatus;

    /** 发起人 */
    private String initiatorName;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /** 展示时间 */
    private String showDate;
}
