package com.znty.rrs.entity.fundnavexport;

import lombok.Data;

/** 基金净值导出页面的基金信息。 */
@Data
public class FundNavFundDto {
    /** 基金信息主键。 */
    private Long id;
    /** 基金名称。 */
    private String fundName;
    /** 基金代码。 */
    private String fundCode;
    /** 市场编码。 */
    private String marketCode;
}
