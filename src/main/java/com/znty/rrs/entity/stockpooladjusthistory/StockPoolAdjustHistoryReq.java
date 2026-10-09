package com.znty.rrs.entity.stockpooladjusthistory;

import lombok.Data;
import com.znty.rrs.common.PageRequest;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** 股票池调整历史筛选条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StockPoolAdjustHistoryReq extends PageRequest {
    /** 投资池树节点 ID。 */
    private List<Long> poolIds;
    /** 证券代码（模糊匹配）。 */
    private String stockCode;
    /** 提交自然日起，yyyy-MM-dd。 */
    private String adjustTimeStart;
    /** 提交自然日止，包含当天。 */
    private String adjustTimeEnd;
    /** 提交时行业编码。 */
    private String industryCode;
    /** 调整人（模糊匹配）。 */
    private String adjusterName;
    /** 调整方向。 */
    private String adjustMode;
    /** 审核状态编码。 */
    private String auditStatus;
    /** 服务计算的截止日次日零点。 */
    @JsonIgnore
    private LocalDateTime adjustTimeEndExclusive;
}
