package com.znty.rrs.entity.forbiddenpooladjust;

import lombok.Data;

import java.util.List;

/**
 * 禁投池主体调整提交结果。
 */
@Data
public class ForbiddenPoolAdjustSubmitDto {

    /** 主体代码 */
    private String companyCode;
    /** 提交数量 */
    private Integer submitCount;
    /** 调库记录 ID */
    private List<Long> logIds;
}
