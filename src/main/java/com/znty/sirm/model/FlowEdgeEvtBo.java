package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程连线事件表
 */
@Data
public class FlowEdgeEvtBo {
    /** 事件主键 */
    private Long evtId;
    /** 原始记录 ID */
    private Long id;
    /** 版本 ID */
    private Long versionId;
    /** 流程定义 ID */
    private Long flowId;
    /** 业务连线 ID */
    private String edgeId;
    /** 起始节点 ID */
    private Long fromNodeId;
    /** 目标节点 ID */
    private Long toNodeId;
    /** 连线标签 */
    private String label;
    /** 条件逻辑 */
    private String condLogic;
    /** 连线备注 */
    private String remark;
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
