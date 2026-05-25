package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程节点事件表
 */
@Data
public class FlowNodeEvtBo {
    /** 事件主键 */
    private Long evtId;
    /** 原始记录 ID */
    private Long id;
    /** 版本 ID */
    private Long versionId;
    /** 流程定义 ID */
    private Long flowId;
    /** 业务节点 ID */
    private String nodeId;
    /** 节点类型 */
    private String nodeType;
    /** 节点名称 */
    private String label;
    /** 形状 */
    private String shape;
    /** 画布 X 坐标 */
    private Double posX;
    /** 画布 Y 坐标 */
    private Double posY;
    /** 副标题 */
    private String subLabel;
    /** 排序 */
    private Integer sortOrder;
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
