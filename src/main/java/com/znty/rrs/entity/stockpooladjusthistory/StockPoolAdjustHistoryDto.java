package com.znty.rrs.entity.stockpooladjusthistory;

import lombok.Data;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/** 股票调整历史留痕。 */
@Data
public class StockPoolAdjustHistoryDto {
    /** 调整日志 ID。 */
    private Long id;
    /** 证券代码。 */
    private String stockCode;
    /** 提交时证券名称。 */
    private String stockName;
    /** 提交时股票简称。 */
    private String stockShortName;
    /** 提交时行业编码。 */
    private String industryCode;
    /** 提交时行业名称。 */
    private String industryName;
    /** 提交时最新评级。 */
    private String latestRating;
    /** 提交时上次评级。 */
    private String previousRating;
    /** 调整类型。 */
    private String adjustType;
    /** 调整方向。 */
    private String adjustMode;
    /** 投资池 ID。 */
    private Long targetPoolId;
    /** 投资池完整路径。 */
    private String targetPoolPath;
    /** 批次号。 */
    private String adjustBatchNo;
    /** 审核状态编码。 */
    private String auditStatus;
    /** 调整人。 */
    private String adjusterName;
    /** 提交时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;
}
