package com.znty.rrs.entity.flow;

import lombok.Data;

/**
 * 流程连线条件规则项 DTO，表示单个条件判断的字段、操作符和比较值
 */
@Data
public class CondRuleItemDto {

    /** 条件字段 */
    private String field;

    /**
     * 操作符
     *
     * eq=等于、ne=不等于、gt=大于、lt=小于、in=包含
     */
    private String op;

    /** 条件值 */
    private String val;
}
