package com.znty.sirm.entity.securitypooladjust;

import lombok.Data;

import java.util.List;

/**
 * 证券调库提交结果 DTO，返回本次提交成功的调库项数量及生成的记录 ID 列表
 */
@Data
public class AdjustSubmitDto {

    /** 证券代码 */
    private String securityCode;

    /** 已提交的调库项数量 */
    private Integer submitCount;

    /** 生成的调库记录 ID 列表 */
    private List<Long> logIds;
}
