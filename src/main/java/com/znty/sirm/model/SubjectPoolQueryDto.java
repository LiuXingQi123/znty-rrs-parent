package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 主体池查询返回对象
 */
@Data
public class SubjectPoolQueryDto {

    /** 主键 ID */
    private Long id;

    /** 主体名称（对应 security_short_name） */
    private String securityShortName;

    /** 主体代码（对应 security_code） */
    private String securityCode;

    /** 调整人 */
    private String adjusterName;

    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;

    /** 投资池名称 */
    private String targetPoolName;
}
