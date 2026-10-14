package com.znty.rrs.entity.sysattachment;

import lombok.Data;

/**
 * 系统附件返回对象
 */
@Data
public class SysAttachmentDto {

    /** 附件 ID */
    private Long id;

    /** 关联主键 ID */
    private Long mainId;

    /** 业务附件分类 */
    private String attachmentCategory;

    /** 报告库来源附件 ID */
    private Long sourceAttachmentId;

    /** 来源报告 ID，与来源类型共同定位报告 */
    private Long sourceReportId;

    /** 来源报告类型：internal / external */
    private String sourceReportSource;

    /** 来源报告标题 */
    private String sourceReportTitle;

    /** 原始文件名称 */
    private String originalFileName;

    /** 文件类型 */
    private String fileType;

    /** 文件大小，单位字节 */
    private Long fileSize;

    /** 文件 MIME 类型 */
    private String contentType;

    /** 下载访问地址 */
    private String fullUrl;
}
