package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Date;

/**
 * 版本 DTO —— 合并 VersionDto / VersionDetailDto
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VersionDto {
    /** 版本 ID */
    private Long versionId;
    /** 版本序号 */
    private Integer verNum;
    /** 版本状态 */
    private String status;
    /** 发布说明 */
    private String publishNote;
    /** 发布人 */
    private Long publishedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 发布时间 */
    private Date publishedTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;

    // ===== 版本详情时额外返回画布数据 =====
    /** 画布节点 JSON */
    private Object nodes;
    /** 画布连线 JSON */
    private Object edges;
    /** 画布平移 X */
    private Double panX;
    /** 画布平移 Y */
    private Double panY;
    /** 画布缩放 */
    private Double zoom;
}
