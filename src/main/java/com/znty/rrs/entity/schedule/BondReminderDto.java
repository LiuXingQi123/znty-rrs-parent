package com.znty.rrs.entity.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 债券定时提醒候选记录。
 */
@Data
public class BondReminderDto {

    /** 调库日志或池状态主键 */
    private Long recordId;

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 证券类型 */
    private String securityType;

    /** 发行主体代码 */
    private String issuerCode;

    /** 发行主体名称 */
    private String issuerName;

    /** 目标池 ID */
    private Long targetPoolId;

    /** 目标池名称 */
    private String targetPoolName;

    /** 调整模式 */
    private String adjustMode;

    /** 审核通过时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date auditTime;
}
