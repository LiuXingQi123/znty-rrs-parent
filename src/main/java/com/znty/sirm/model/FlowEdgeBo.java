package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程连线表
 */
@Data
public class FlowEdgeBo {
    /** 主键 */
    private Long id;
    /** 版本 ID */
    private Long versionId;
    /** 流程定义 ID */
    private Long flowId;
    /** 业务连线 ID（前端生成） */
    private String edgeId;
    /** 起始节点 ID（代理键） */
    private Long fromNodeId;
    /** 目标节点 ID（代理键） */
    private Long toNodeId;
    /** 连线标签 */
    private String label;
    /** 条件逻辑：and/or */
    private String condLogic;
    /** 连线备注 */
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;
}
