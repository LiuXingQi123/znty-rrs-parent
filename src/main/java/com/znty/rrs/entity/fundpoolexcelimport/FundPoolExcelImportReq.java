package com.znty.rrs.entity.fundpoolexcelimport;

import com.znty.rrs.common.PageRequest;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金池 Excel 导入请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FundPoolExcelImportReq extends PageRequest {
    /** 导入批次号 */
    private String impId;
    /** 调整方向：in / out */
    private String direction;
    /** 首先清空目标池，仅调入生效 */
    private Boolean clearTarget;
    /** 允许联动与互斥 */
    private Boolean allowLinkMutex;
    /** 清空出库项统一基金评分 */
    private BigDecimal clearFundScore;
    /** 清空出库项统一基金投资类型 */
    private String clearFundInvestmentType;
    /** 清空出库项统一风管领导审批：0 / 1 */
    private Integer clearNeedRiskLeaderApproval;
    /** 调整原因 */
    private String adjustReason;
    /** 调整建议 */
    private String adjustAdvice;
    /** 当前用户 ID */
    private String currentUserId;
    /** 当前用户名称 */
    private String currentUserName;
    /** 行级校验结果筛选：0 / 1 / 2 */
    private String chkRslt;
    /** 基金代码或名称关键字 */
    private String keyword;
    /** 客户端流程选择，业务数据以服务端快照为准 */
    private List<FundPoolExcelImportCheckItemDto> checkItems;
}
