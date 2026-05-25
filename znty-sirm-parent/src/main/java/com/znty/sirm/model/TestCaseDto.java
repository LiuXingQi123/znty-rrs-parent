package com.znty.sirm.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用例 DTO，包含用例基本信息、参数键值对和最近执行结果快照。
 */
@Data
public class TestCaseDto {
    /** 用例 ID */
    private Long id;
    /** 用例名称 */
    private String name;
    /** 关联规则 ID */
    private Long ruleId;
    /** 关联规则名称 */
    private String ruleName;
    /** 用例参数（参数名 → 参数值，保持插入顺序） */
    private Map<String, String> params = new LinkedHashMap<>();
    /** 最近一次执行结果：pass / fail / null */
    private String lastResult;
    /** 最近一次执行输出 */
    private String lastOutput;
    /** 最近一次执行时间（格式化） */
    private String lastRunTime;
}
