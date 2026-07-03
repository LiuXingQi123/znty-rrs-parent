package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 投资池权限配置业务对象
 */
@Data
public class PoolPermissionBo {

    /** 主键 ID */
    private Long id;

    /** 投资池 ID */
    private Long poolId;

    /** 权限类型：viewable / adjustable / excel_importable */
    private String permissionType;

    /** 处理人类型：role / user */
    private String handlerType;

    /** 处理人 ID（角色 ID 或人员 ID） */
    private Long handlerId;

    /** 处理人名称快照 */
    private String handlerName;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
