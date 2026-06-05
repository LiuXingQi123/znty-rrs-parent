package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 审批节点配置审计事件表，记录审批节点配置数据的每次变更历史
 */
@Data
public class NodeApprovalConfigEvtBo {
    /** 事件主键（evt_id） */
    private Long evtId;
    /** 原始配置记录 ID */
    private Long id;
    /** 关联的流程节点 ID */
    private Long nodeId;
    /** 审批策略（如：任意一人通过/全部通过） */
    private String approvalStrategy;
    /** 审批人列表（JSON 字符串，存储角色或人员 ID） */
    private String approvalPersons;
    /** 审批节点备注说明 */
    private String approvalRemark;
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
