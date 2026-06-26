package com.znty.sirm.entity.forbiddenpoolhistory;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 禁投池历史查询返回对象
 */
@Data
public class ForbiddenPoolHistoryDto {

    /** 主键 ID */
    private Long id;

    /** 调整人 */
    private String adjusterName;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;

    /** 证券名称 */
    private String securityShortName;

    /** 证券代码 */
    private String securityCode;

    /** 发行主体 */
    private String issuer;

    /** 调整类型 */
    private String adjustType;

    /** 调整方向 */
    private String adjustMode;

    /** 投资池名称 */
    private String targetPoolName;

    /** 目标投资池 ID */
    private Long targetPoolId;

    /** 审批状态 */
    private String auditStatus;
}
