package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程版本事件表
 */
@Data
public class FlowVersionEvtBo {
    /** 事件主键 */
    private Long evtId;
    /** 原始记录 ID */
    private Long id;
    /** 流程定义 ID */
    private Long flowId;
    /** 流程唯一标识 */
    private String flowKey;
    /** 版本序号 */
    private Integer verNum;
    /** 版本状态 */
    private String status;
    /** 发布说明 */
    private String publishNote;
    /** 画布节点 JSON */
    private String canvasNodes;
    /** 画布连线 JSON */
    private String canvasEdges;
    /** 画布平移 X */
    private Double canvasPanX;
    /** 画布平移 Y */
    private Double canvasPanY;
    /** 画布缩放 */
    private Double canvasZoom;
    /** 发布人 */
    private Long publishedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 发布时间 */
    private Date publishedTime;
    /** 创建人 */
    private Long createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;
    /** 操作人 ID */
    private String opterId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 操作时间 */
    private Date optTime;
    /** 操作类型 */
    private String oprtType;
}
