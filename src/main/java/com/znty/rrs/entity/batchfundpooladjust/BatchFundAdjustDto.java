package com.znty.rrs.entity.batchfundpooladjust;

import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 基金池批量校验及提交结果 */
@Data
public class BatchFundAdjustDto {
    /** 展开后的完整调库校验明细 */
    private List<FundAdjustCheckDto.CheckResultItem> items = new ArrayList<>();
    /** 本次提交的基金数量 */
    private Integer fundCount;
    /** 本次写入的调库日志数量 */
    private Integer submitCount;
    /** 本次写入的调库日志 ID */
    private List<Long> logIds = new ArrayList<>();
    /** 各基金独立的调库批次号 */
    private List<String> adjustBatchNos = new ArrayList<>();
}
