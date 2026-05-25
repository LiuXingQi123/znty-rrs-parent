package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 角色业务对象
 */
@Data
public class RoleBo {

    /** 主键 ID */
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 父级角色 ID */
    private Long parentId;

    /** 排序序号 */
    private Integer sortOrder;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;

    /** 子角色列表（非数据库字段） */
    private List<RoleBo> children;
}
