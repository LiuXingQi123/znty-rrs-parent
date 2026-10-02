package com.znty.rrs.entity.fundpooladjust;

import java.util.List;
import lombok.Data;

/** 基金调库提交结果 */
@Data
public class FundAdjustSubmitDto {
    /** 新增基金调库记录 ID */
    private List<Long> adjustLogIds;
    /** 本次生成的调库批次号 */
    private List<String> adjustBatchNos;
}
