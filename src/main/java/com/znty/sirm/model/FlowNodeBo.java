package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程节点表（通用字段）
 */
@Data
public class FlowNodeBo {
    /** 主键（代理键） */
    private Long id;
    /** 版本 ID */
    private Long versionId;
    /** 流程定义 ID */
    private Long flowId;
    /** 业务节点 ID（前端生成） */
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
}
