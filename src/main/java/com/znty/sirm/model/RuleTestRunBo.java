package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 测试执行记录实体，对应表 rule_test_run。
 * <p>记录每次测试执行的完整信息，包括状态、输出、错误和耗时。</p>
 */
@Data
public class RuleTestRunBo {
    /** 主键 ID */
    private Long id;
    /** 关联用例 ID（临时执行时为 null） */
    private Long caseId;
    /** 关联规则 ID */
    private Long ruleId;
    /** 执行状态：running-执行中，pass-通过，fail-失败 */
    private String runStatus;
    /** 执行输出结果 */
    private String output;
    /** 错误信息（仅在失败时有值） */
    private String errorMessage;
    /** 执行开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
    /** 执行结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
