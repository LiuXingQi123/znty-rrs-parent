package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程连线审计事件表，记录流程连线数据的每次变更历史
 */
@Data
public class FlowEdgeEvtBo {
    /** 事件主键（evt_id） */
    private Long evtId;
    /** 原始连线记录 ID */
    private Long id;
    /** 所属流程版本 ID */
    private Long versionId;
    /** 所属流程定义 ID */
    private Long flowId;
    /** 业务连线 ID（前端画布生成的字符串 ID） */
    private String edgeId;
    /** 起始节点数据库代理键 ID */
    private Long fromNodeId;
    /** 目标节点数据库代理键 ID */
    private Long toNodeId;
    /** 连线标签（显示文本） */
    private String label;
    /** 流转动作：approve=通过 / reject=驳回 / auto=自动 / submit=提交 */
    private String routeAction;
    /** 条件逻辑：and=全部满足/or=任意满足 */
    private String condLogic;
    /** 连线备注 */
    private String remark;
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
