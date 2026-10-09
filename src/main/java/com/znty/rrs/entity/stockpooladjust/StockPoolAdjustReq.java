package com.znty.rrs.entity.stockpooladjust;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 股票池调整查询请求 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StockPoolAdjustReq extends PageRequest {
    /** 股票代码 */
    private String stockCode;
    /** 股票简称 */
    private String stockShortName;
    /** 股票产品类型 */
    private String securityType;
    /** 调库方向：in / out */
    private String adjustDirection;
    /** 当前用户 ID，1 为管理员 */
    private String currentUserId;
    /** 调库批次号 */
    private String adjustBatchNo;
    /** 调库记录 ID */
    private Long adjustLogId;
    /** 股票名称或简称 */
    private String stockName;
    /** 所属行业编码 */
    private String industryCode;
    /** 市场编码 */
    private String marketCode;
    /** 指定投资池 ID */
    private Long targetPoolId;
}
