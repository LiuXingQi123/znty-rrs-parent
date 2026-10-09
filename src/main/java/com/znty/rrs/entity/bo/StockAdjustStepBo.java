package com.znty.rrs.entity.bo;

import java.util.Date;
import lombok.Data;

/** 股票调库流程步骤业务对象，对应 ip_adjust_step_stock */
@Data
public class StockAdjustStepBo {
    /** 主键 ID */
    private Long id;
    /** 股票调库记录 ID */
    private Long adjustLogId;
    /** 调库批次号 */
    private String adjustBatchNo;
    /** 流程节点 ID */
    private Long flowNodeId;
    /** 节点业务标识 */
    private String nodeCode;
    /** 节点名称 */
    private String nodeLabel;
    /** 节点类型 */
    private String nodeType;
    /** 审批策略 */
    private String approvalStrategy;
    /** 排序号 */
    private Integer sortOrder;
    /** 步骤状态 */
    private String stepStatus;
    /** 处理人 ID */
    private String handlerId;
    /** 处理人名称 */
    private String handlerName;
    /** 处理动作 */
    private String processAction;
    /** 处理意见 */
    private String processComment;
    /** 激活时间 */
    private Date startTime;
    /** 处理时间 */
    private Date processTime;
    /** 删除标记 */
    private Integer isDeleted;
    /** 创建时间 */
    private Date crteTime;
    /** 更新时间 */
    private Date updtTime;
}
