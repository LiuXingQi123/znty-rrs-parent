package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 角色字典事件表
 */
@Data
public class RoleDictEvtBo {
    /** 事件主键 */
    private Long evtId;
    /** 原始记录 ID */
    private Long id;
    /** 角色编码 */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 排序 */
    private Integer sortOrder;
    /** 启用标记 */
    private Integer isActive;
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
    /** 操作类型 */
    private String oprtType;
}
