package com.znty.rrs.entity.commonfile;

import lombok.Data;

/**
 * 公共文件下载结果（前端配合 Base64 下载）
 */
@Data
public class CommonFileDto {

    /** 文件名 */
    private String fileName;
    /** MIME 类型 */
    private String contentType;
    /** 文件大小 */
    private Long fileSize;
    /** Base64 内容 */
    private String contentBase64;
}
