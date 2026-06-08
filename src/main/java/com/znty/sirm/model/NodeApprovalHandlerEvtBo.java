package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 审批节点处理人明细事件表实体，记录处理人配置变更历史
 */
@Data
public class NodeApprovalHandlerEvtBo {

    /** 事件主键 ID */
    private Long evtId;

    /** 原始处理人明细 ID */
    private Long id;

    /** 审批节点配置 ID */
    private Long approvalConfigId;

    /** 主体类型：role / user */
    private String subjectType;

    /** 主体 ID */
    private Long subjectId;

    /** 主体名称快照 */
    private String subjectName;

    /** 排序序号 */
    private Integer sortOrder;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;

    /** 操作人 ID */
    private String opterId;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date optTime;

    /** 操作类型 */
    private String oprtType;
}
