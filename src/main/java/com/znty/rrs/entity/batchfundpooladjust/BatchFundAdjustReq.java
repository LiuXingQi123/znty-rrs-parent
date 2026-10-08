package com.znty.rrs.entity.batchfundpooladjust;

import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金池批量校验及提交请求，基金调库信息整批共用 */
@Data
public class BatchFundAdjustReq {
    /** 当前用户 ID */
    private String currentUserId;
    /** 手工调整目标投资池 ID */
    private Long poolId;
    /** 批量方向：in / out */
    private String direction;
    /** 待校验的基金 */
    private List<FundItem> funds;
    /** 基金评分 */
    private BigDecimal fundScore;
    /** 基金投资类型 */
    private String fundInvestmentType;
    /** 分管领导审批：1=是 / 0=否 */
    private Integer needRiskLeaderApproval;
    /** 调整原因 */
    private String adjustReason;
    /** 调整建议 */
    private String adjustAdvice;
    /** 调整人 ID */
    private String adjusterId;
    /** 调整人名称 */
    private String adjusterName;
    /** 已校验的完整基金调库明细 */
    private List<AdjustItem> items;

    /** 单个待校验基金 */
    @Data
    public static class FundItem {
        /** 基金代码 */
        private String fundCode;
    }

    /** 复用单笔明细字段，并标记所属基金 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AdjustItem extends FundPoolAdjustSubmitReq.AdjustItem {
        /** 基金代码 */
        private String fundCode;
    }
}
