package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程版本表实体，存储流程每个版本的画布快照（节点、连线及布局信息）
 */
@Data
public class FlowVersionBo {
    /** 主键 */
    private Long id;
    /** 所属流程定义 ID */
    private Long flowId;
    /** 流程唯一标识（flowKey） */
    private String flowKey;
    /** 版本序号（从1递增） */
    private Integer verNum;
    /** 版本状态：draft=草稿/active=已发布/archived=已归档/disabled=已停用 */
    private String status;
    /** 发布说明 */
    private String publishNote;
    /** 画布节点配置 JSON 字符串 */
    private String canvasNodes;
    /** 画布连线配置 JSON 字符串 */
    private String canvasEdges;
    /** 画布水平平移量 */
    private Double canvasPanX;
    /** 画布垂直平移量 */
    private Double canvasPanY;
    /** 画布缩放比例 */
    private Double canvasZoom;
    /** 发布人 ID */
    private Long publishedBy;
    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedTime;
    /** 创建人 ID */
    private Long createdBy;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
