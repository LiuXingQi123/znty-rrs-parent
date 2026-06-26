package com.znty.sirm.entity.sysattachment;

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
