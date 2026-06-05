package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 通用字典 DTO，合并角色字典、自动任务字典和条件字段字典，用于流程设计器的下拉选项
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DictDto {
    // ===== 角色字典 =====
    /** 角色编码 */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 排序 */
    private Integer sortOrder;

    // ===== 自动任务 =====
    /** 任务编码 */
    private String taskCode;
    /** 任务名称 */
    private String taskName;

    // ===== 条件字段 =====
    /** 字段编码 */
    private String fieldCode;
    /** 字段名称 */
    private String fieldName;

    // ===== 条件字段分组 =====
    /** 分组编码 */
    private String groupCode;
    /** 分组名称 */
    private String groupName;
    /** 字段列表 */
    private List<DictDto> fields;
}
