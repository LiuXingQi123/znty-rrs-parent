package com.znty.rrs.entity.securitypooladjust;

import lombok.Data;
import java.util.List;

/** 发行主体财务指标批量保存请求。 */
@Data
public class IssuerFinancialSaveReq {
    /** ABS 页面当前选择，空主体不允许回退到证券主档保存 */
    private RatingCompanySelectionReq selectedRatingCompany;
    /** 用于服务端确定发行主体的证券代码 */
    private String securityCode;
    /** 页面已加载的发行主体代码 */
    private String issuerCode;
    /** ABS 本次选择的普通权益人 */
    private String rightsHolderCode;
    /** ABS 本次选择的自选权益人 */
    private String selfSelectedRightsHolderCode;
    /** 本次修改过的报告期，包含完整财务指标；null 表示清空 */
    private List<IssuerFinancialDto> records;
}
