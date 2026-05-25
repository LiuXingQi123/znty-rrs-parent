package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 流程定义主表
 */
@Data
public class FlowDefinitionBo {
    /** 主键 */
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
    /** 状态：draft/active/disabled */
    private String status;
    /** 当前版本号 */
    private Integer currentVer;
    /** 创建人 */
    private Long createdBy;
    /** 更新人 */
    private Long updatedBy;
    /** 删除标记：0-正常 1-已删除 */
    private Integer isDeleted;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;
}
