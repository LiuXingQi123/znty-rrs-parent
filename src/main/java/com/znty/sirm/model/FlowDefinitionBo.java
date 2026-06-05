package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程定义主表实体，对应审批流程的元数据（名称、分类、状态及版本信息）
 */
@Data
public class FlowDefinitionBo {
    /** 主键 */
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
    /** 当前版本号 */
    private Integer currentVer;
    /** 是否有已发布版本（用于区分"从未发布的未发布"和"有已发布版本的未发布"） */
    private Boolean hasPublishedVersion;
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
}
