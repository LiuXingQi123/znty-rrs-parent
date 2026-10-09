package com.znty.rrs.entity.stockpoolquery;

import lombok.Data;
import com.znty.rrs.common.PageRequest;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** 股票池查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StockPoolQueryReq extends PageRequest {
    /** 投资池树节点 ID。 */
    private List<Long> poolIds;
    /** 股票代码（模糊匹配）。 */
    private String stockCode;
    /** 入池自然日起，yyyy-MM-dd。 */
    private String entryTimeStart;
    /** 入池自然日止，包含当天。 */
    private String entryTimeEnd;
    /** 调整人（模糊匹配）。 */
    private String adjusterName;
    /** 仅查询我分管股票。 */
    private Boolean myManagedStocks;
    /** 仅查询我的自选股；与分管筛选取交集。 */
    private Boolean myStocks;
    /** 当前用户 ID。 */
    private String currentUserId;
    /** 服务计算的截止日次日零点，不接收客户端赋值。 */
    @JsonIgnore
    private LocalDateTime entryTimeEndExclusive;
}
