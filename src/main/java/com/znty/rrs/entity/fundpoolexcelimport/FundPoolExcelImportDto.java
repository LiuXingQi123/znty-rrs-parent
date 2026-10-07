package com.znty.rrs.entity.fundpoolexcelimport;

import com.znty.rrs.common.PageResult;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Data;

/** 基金 Excel 导入批次、明细及校验快照。 */
@Data
public class FundPoolExcelImportDto {
    /** 导入批次号 */
    private String impId;
    /** 业务类型 */
    private String bizType;
    /** 原始文件名 */
    private String fileName;
    /** 导入时间 */
    private Date impTime;
    /** 调整方向 */
    private String bizMode;
    /** 调整原因 */
    private String reason;
    /** 调整建议 */
    private String advice;
    /** 导入行总数 */
    private Integer totalCount;
    /** 通过行数 */
    private Integer passCount;
    /** 失败行数 */
    private Integer failCount;
    /** 待校验行数 */
    private Integer pendingCount;
    /** 批次校验结果 */
    private String chkRslt;
    /** 批次校验说明 */
    private String chkDscr;
    /** 批次保存结果 */
    private String saveRslt;
    /** 批次保存说明 */
    private String saveDscr;
    /** 已完成业务校验 */
    private Boolean checkDone;
    /** 允许联动与互斥 */
    private Boolean allowLinkMutex;
    /** 已锁定导入参数 JSON */
    private String optionJson;
    /** 原始明细分页 */
    private PageResult<FundPoolExcelImportItemDto> items;
    /** 调库校验项 */
    private List<FundPoolExcelImportCheckItemDto> checkItems;
    /** 可调整项数 */
    private Integer checkPassCount;
    /** 不可调整项数 */
    private Integer checkFailCount;
    /** 提交产生的基金批次号 */
    private List<String> adjustBatchNoList;
    /** 提交产生的基金日志 ID */
    private List<Long> logIds;
    /** 清空出库项统一评分 */
    private BigDecimal clearFundScore;
    /** 清空出库项统一投资类型 */
    private String clearFundInvestmentType;
    /** 清空出库项统一风管领导审批 */
    private Integer clearNeedRiskLeaderApproval;
}
