package com.znty.rrs.entity.stockpooladjusthistory;

import lombok.Data;

/** 实际股票历史行业选项。 */
@Data
public class StockIndustryOptionDto {
    /** 行业编码。 */
    private String industryCode;
    /** 行业名称。 */
    private String industryName;
}
