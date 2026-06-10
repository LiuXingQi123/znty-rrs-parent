package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 证券池调整历史列表 DTO，用于调整历史查询页面展示调整记录的关键信息
 */
@Data
public class AdjustHistoryDto {

    /** 记录 ID */
    private Long id;

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 证券类型编码 */
    private String securityType;

    /** 证券类型名称（关联 dict_security_type 表） */
    private String securityTypeName;

    /** 调整类型（手工调整/联动调整/...） */
    private String adjustType;

    /** 调整方向（调入/调出） */
    private String adjustMode;

    /** 投资池路径名称（父级/子级 格式，如"信用债大库/一级库"） */
    private String targetPoolPath;

    /** 目标投资池 ID */
    private Long targetPoolId;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 投资池类型 */
    private String poolType;

    /** 审核状态码（-1/00/10/11/20/21/99） */
    private String auditStatus;

    /** 调整人名称 */
    private String adjusterName;

    /** 调整原因 */
    private String adjustReason;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;
}
