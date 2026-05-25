package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 规则测试用例实体，对应表 rule_test_case。
 * <p>存储规则的测试用例，包含关联规则信息及最近一次执行结果快照。</p>
 */
@Data
public class RuleTestCaseBo {
    private Long id;
    /** 用例名称 */
    private String caseName;
    /** 关联规则 ID */
    private Long ruleId;
    /** 关联规则名称快照（创建时记录，保证规则改名后历史可读） */
    private String ruleNameSnapshot;
    /** 最近执行结果：pending-待执行，running-执行中，pass-通过，fail-失败 */
    private String lastResult;
    /** 最近执行输出 */
    private String lastOutput;
    /** 最近执行时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastRunTime;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
