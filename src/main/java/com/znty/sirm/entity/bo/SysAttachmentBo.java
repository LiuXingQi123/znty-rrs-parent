package com.znty.sirm.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 系统附件关联表实体
 */
@Data
public class SysAttachmentBo {

    /** 主键 ID */
    private Long id;

    /** 关联表名称 */
    private String tableName;

    /** 关联主键 ID */
    private Long mainId;

    /** 业务附件分类 */
    private String attachmentCategory;

    /** 文件类型 */
    private String fileType;

    /** 原始文件名称 */
    private String originalFileName;

    /** 服务器新文件名称 */
    private String newFileName;

    /** 文件大小，单位字节 */
    private Long fileSize;

    /** 文件 MIME 类型 */
    private String contentType;

    /** 下载访问地址 */
    private String fullUrl;

    /** 服务器相对文件路径 */
    private String fileName;

    /** 上传人 ID */
    private String uploaderId;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
