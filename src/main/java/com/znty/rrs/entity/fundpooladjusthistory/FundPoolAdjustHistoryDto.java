package com.znty.rrs.entity.fundpooladjusthistory;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/** 基金池调整历史返回对象。 */
@Data
public class FundPoolAdjustHistoryDto {
    /** 调库记录 ID。 */
    private Long id;
    /** 基金代码。 */
    private String fundCode;
    /** 基金名称。 */
    private String fundName;
    /** 基金产品类型编码。 */
    private String securityType;
    /** 基金产品类型名称。 */
    private String securityTypeName;
    /** 调整类型。 */
    private String adjustType;
    /** 调整方向。 */
    private String adjustMode;
    /** 目标投资池 ID。 */
    private Long targetPoolId;
    /** 投资池完整路径。 */
    private String targetPoolPath;
    /** 调库批次号。 */
    private String adjustBatchNo;
    /** 审核状态码。 */
    private String auditStatus;
    /** 调整人。 */
    private String adjusterName;
    /** 提交时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;
}
