package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程定义事件表
 */
@Data
public class FlowDefinitionEvtBo {
    /** 事件主键 */
    private Long evtId;
    /** 原始记录 ID */
    private Long id;
    /** 流程名称 */
    private String name;
    /** 流程唯一标识 */
    private String flowKey;
    /** 业务分类 */
    private String category;
    /** 描述 */
    private String description;
    /** 备注 */
    private String remark;
    /** 状态 */
    private String status;
    /** 创建人 */
    private Long createdBy;
    /** 更新人 */
    private Long updatedBy;
    /** 删除标记 */
    private Integer isDeleted;
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
    /** 操作类型：INSERT/UPDATE/DELETE */
    private String oprtType;
}
