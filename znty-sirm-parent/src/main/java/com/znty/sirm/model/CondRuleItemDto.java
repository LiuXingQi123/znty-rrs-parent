package com.znty.sirm.model;

import lombok.Data;

/**
 * 条件规则项
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
