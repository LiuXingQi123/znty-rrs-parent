package com.znty.rrs.entity.fundnavexport;

import com.znty.rrs.common.PageRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金净值导出请求对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FundNavExportReq extends PageRequest {
    /** 基金名称关键词。 */
    private String fundName;
    /** 基金代码关键词。 */
    private String fundCode;
    /** 市场编码多选条件。 */
    private List<String> marketCodes;
    /** 导出所选基金代码。 */
    private List<String> fundCodes;
    /** 净值日期起，格式 yyyy-MM-dd。 */
    private String navDateStart;
    /** 净值日期止，格式 yyyy-MM-dd。 */
    private String navDateEnd;
}
