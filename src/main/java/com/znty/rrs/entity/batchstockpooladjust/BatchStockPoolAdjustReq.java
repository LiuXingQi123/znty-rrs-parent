package com.znty.rrs.entity.batchstockpooladjust;

import com.znty.rrs.common.PageRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 股票池批量调整分页查询请求 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchStockPoolAdjustReq extends PageRequest {
    /** 当前用户 ID */
    private String currentUserId;
    /** 筛选投资池 ID */
    private List<Long> poolIds;
    /** 目标投资池 ID */
    private Long poolId;
    /** 调整方向：in / out */
    private String direction;
    /** 股票代码 */
    private String stockCode;
    /** 股票名称或简称 */
    private String stockName;
    /** 股票简称 */
    private String stockShortName;
    /** 行业编码 */
    private String industryCode;
    /** 市场编码 */
    private String marketCode;
    /** 股票产品类型 */
    private String securityType;
}
