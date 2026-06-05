package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 测试执行步骤日志实体，对应表 rule_test_run_log。
 * <p>记录规则执行过程中的每一步日志，用于排查问题。</p>
 */
@Data
public class RuleTestRunLogBo {
    /** 主键 ID */
    private Long id;
    /** 关联执行记录 ID */
    private Long runId;
    /** 日志时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date logTime;
    /** 日志类型：info-信息，success-成功，error-错误 */
    private String logType;
    /** 日志内容 */
    private String message;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
