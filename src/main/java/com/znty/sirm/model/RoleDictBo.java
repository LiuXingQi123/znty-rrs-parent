package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 业务角色字典表
 */
@Data
public class RoleDictBo {
    /** 主键 */
    private Integer id;
    /** 角色编码 */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 排序 */
    private Integer sortOrder;
    /** 启用标记：0-禁用 1-启用 */
    private Integer isActive;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 创建时间 */
    private Date crteTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /** 更新时间 */
    private Date updtTime;
}
