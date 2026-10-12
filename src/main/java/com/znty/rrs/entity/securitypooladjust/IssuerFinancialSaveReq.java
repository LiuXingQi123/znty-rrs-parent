package com.znty.rrs.entity.securitypooladjust;

import lombok.Data;
import java.util.List;

/** 发行主体财务指标批量保存请求。 */
@Data
public class IssuerFinancialSaveReq {
    /** 用于服务端确定发行主体的证券代码 */
    private String securityCode;
    /** 本次修改过的报告期，包含完整财务指标；null 表示清空 */
    private List<IssuerFinancialDto> records;
}
