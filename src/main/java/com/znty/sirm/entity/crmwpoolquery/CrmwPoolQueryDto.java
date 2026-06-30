package com.znty.sirm.entity.crmwpoolquery;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * CRMW池查询返回对象
 */
@Data
public class CrmwPoolQueryDto {

    /** 主键 ID */
    private Long id;

    /** CRMW名称 */
    private String crmwName;

    /** CRMW代码 */
    private String crmwScode;

    /** 证券名称 */
    private String securityShortName;

    /** 证券代码 */
    private String securityCode;

    /** 调整人 */
    private String adjusterName;

    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;

    /** 投资池名称 */
    private String targetPoolName;

    /** 目标投资池 ID */
    private Long targetPoolId;
}
