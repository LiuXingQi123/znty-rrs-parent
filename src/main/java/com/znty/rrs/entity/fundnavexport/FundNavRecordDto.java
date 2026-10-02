package com.znty.rrs.entity.fundnavexport;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 基金逐日净值导出记录。 */
@Data
public class FundNavRecordDto {
    /** 基金代码。 */
    private String fundCode;
    /** 净值交易日期。 */
    private Date tradeDate;
    /** 单位净值。 */
    private BigDecimal unitNav;
}
