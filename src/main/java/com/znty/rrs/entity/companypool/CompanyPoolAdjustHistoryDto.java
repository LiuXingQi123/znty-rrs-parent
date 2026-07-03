package com.znty.rrs.entity.companypool;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 主体池调整历史查询返回对象
 */
@Data
public class CompanyPoolAdjustHistoryDto {

    /** 主键 ID */
    private Long id;

    /** 调整人 */
    private String adjusterName;

    /** 提交日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;

    /** 主体名称 */
    private String companyName;

    /** 主体代码 */
    private String companyCode;

    /** 调整类型 */
    private String adjustType;

    /** 调整方向 */
    private String adjustMode;

    /** 投资池名称 */
    private String targetPoolName;

    /** 目标投资池 ID */
    private Long targetPoolId;

    /** 审批状态 */
    private String auditStatus;
}
