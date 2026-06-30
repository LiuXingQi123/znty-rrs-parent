package com.znty.sirm.entity.crmwpooladjust;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 证券调库记录列表 DTO，用于证券调整详情页展示每条调库操作的状态及内容
 */
@Data
public class AdjustLogDto {

    /** 记录 ID */
    private Long id;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** 投资池路径（父级名称/名称） */
    private String poolPath;

    /** 调整类型 */
    private String adjustType;

    /** 调整方向 */
    private String adjustMode;

    /** 流程名称 */
    private String flowName;

    /** 审核状态码 */
    private String auditStatus;

    /** 调整原因 */
    private String adjustReason;

    /** 调整意见 */
    private String adjustAdvice;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;
}
