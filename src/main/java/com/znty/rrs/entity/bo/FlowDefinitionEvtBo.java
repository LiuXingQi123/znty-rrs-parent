package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程定义审计事件表，记录流程定义数据的每次变更历史
 */
@Data
public class FlowDefinitionEvtBo {
    /** 事件主键（evt_id） */
    private Long evtId;
    /** 原始流程定义 ID */
    private Long id;
    /** 流程名称 */
    private String name;
    /** 流程唯一标识（flowKey） */
    private String flowKey;
    /** 业务分类 */
    private String category;
    /** 流程描述 */
    private String description;
    /** 备注 */
    private String remark;
    /** 状态：draft=草稿/active=已发布/disabled=已停用 */
    private String status;
    /** 创建人 ID */
    private Long createdBy;
    /** 最后更新人 ID */
    private Long updatedBy;
    /** 逻辑删除标记：0=正常/1=已删除 */
    private Integer isDeleted;
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
