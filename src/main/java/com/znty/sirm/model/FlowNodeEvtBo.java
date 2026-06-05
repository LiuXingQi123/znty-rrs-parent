package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程节点审计事件表，记录流程节点数据的每次变更历史
 */
@Data
public class FlowNodeEvtBo {
    /** 事件主键（evt_id） */
    private Long evtId;
    /** 原始节点记录 ID */
    private Long id;
    /** 所属流程版本 ID */
    private Long versionId;
    /** 所属流程定义 ID */
    private Long flowId;
    /** 业务节点 ID（前端画布生成的字符串 ID） */
    private String nodeId;
    /** 节点类型：start=开始/end=结束/approval=审批/auto=自动/notify=通知/condition=条件 */
    private String nodeType;
    /** 节点显示名称 */
    private String label;
    /** 节点形状 */
    private String shape;
    /** 画布 X 坐标 */
    private Double posX;
    /** 画布 Y 坐标 */
    private Double posY;
    /** 节点副标题 */
    private String subLabel;
    /** 节点排序序号 */
    private Integer sortOrder;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
    /** 操作人 ID */
    private String opterId;
    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date optTime;
    /** 操作类型（INSERT=新增/UPDATE=修改/DELETE=删除） */
    private String oprtType;
}
