package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 调库记录列表返回对象
 */
@Data
public class AdjustLogDto {

    /** 记录 ID */
    private Long id;

    /** 投资池路径（父级名称/名称） */
    private String poolPath;

    /** 调整类型 */
    private String adjustType;

    /** 调整方向 */
    private String adjustMode;

    /** 附件报告 */
    private String attachmentFiles;

    /** 其他材料 */
    private String materialFiles;

    /** 审核状态码 */
    private String auditStatus;

    /** 审核状态中文 */
    private String auditStatusLabel;

    /** 调整原因 */
    private String adjustReason;

    /** 调整意见 */
    private String adjustAdvice;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;
}
