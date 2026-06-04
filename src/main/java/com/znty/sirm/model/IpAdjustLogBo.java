package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 证券池调库记录表实体
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

    /** 调整类型 */
    private String adjustType;

    /** 调整模式：调入/调出 */
    private String adjustMode;

    /** 目标投资池 ID */
    private Long targetPoolId;

    /** 目标投资池名称 */
    private String targetPoolName;

    /** 投资池类型 */
    private String poolType;

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

    /** 附件报告文件路径（JSON数组） */
    private String attachmentFiles;

    /** 其他材料文件路径（JSON数组） */
    private String materialFiles;

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
