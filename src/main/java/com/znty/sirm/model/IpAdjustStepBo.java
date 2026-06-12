package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 证券池调库-流程步骤记录表实体，按节点+处理人维度记录审批流每一步的处理状态
 */
@Data
public class IpAdjustStepBo {

    /** 主键 ID */
    private Long id;

    /** 关联调库记录 ID */
    private Long adjustLogId;

    /** 调库批次号，同一批调库记录共用 */
    private String adjustBatchNo;

    /** 关联流程节点 ID */
    private Long flowNodeId;

    /** 节点业务标识 */
    private String nodeCode;

    /** 节点显示名称 */
    private String nodeLabel;

    /** 节点类型：start/approval/auto/end/notify/condition */
    private String nodeType;

    /** 审批策略：preempt=抢占审批 / all=会签 / initiator=发起人 */
    private String approvalStrategy;

    /** 排序序号 */
    private Integer sortOrder;

    /** 步骤处理状态：pending=待处理 / approve=通过 / reject=驳回 / submit=提交 / auto_process=自动处理 / canceled=已撤回 */
    private String stepStatus;

    /** 处理人 ID */
    private String handlerId;

    /** 处理人名称 */
    private String handlerName;

    /** 处理动作：submit=提交 / approve=通过 / reject=驳回 / auto_process=自动处理 / skipped=被跳过 */
    private String processAction;

    /** 处理意见 */
    private String processComment;

    /** 步骤激活时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date processTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
