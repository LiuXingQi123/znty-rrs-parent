package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

import lombok.Data;

/**
 * 人员业务对象
 */
@Data
public class UserBo {

    /** 主键 ID */
    private Long id;

    /** 人员姓名 */
    private String name;

    /** 登录用户名/拼音 */
    private String userName;

    /** 逻辑删除标志 */
    private Integer dr;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;

    /** 角色名称拼接（非数据库字段，列表展示用，逗号分隔） */
    private String roleName;
}
