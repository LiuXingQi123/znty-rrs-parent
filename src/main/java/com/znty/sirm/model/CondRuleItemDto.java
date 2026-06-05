package com.znty.sirm.model;

import lombok.Data;

/**
 * 流程连线条件规则项 DTO，表示单个条件判断的字段、操作符和比较值
 */
@Data
public class CondRuleItemDto {
    /** 条件字段 */
    private String field;
    /** 操作符：eq/ne/gt/lt/in */
    private String op;
    /** 条件值 */
    private String val;
}
