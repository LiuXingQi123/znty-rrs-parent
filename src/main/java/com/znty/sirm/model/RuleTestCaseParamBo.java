package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 测试用例参数值实体，对应表 rule_test_case_param。
 * <p>存储每个测试用例中各个参数的实际值，同时快照参数标签和类型以保证历史可复现。</p>
 */
@Data
public class RuleTestCaseParamBo {
    private Long id;
    /** 所属用例 ID */
    private Long caseId;
    /** 参数名 */
    private String paramName;
    /** 参数标签快照（记录创建时的参数标签） */
    private String paramLabelSnapshot;
    /** 参数类型快照（记录创建时的参数类型） */
    private String paramTypeSnapshot;
    /** 参数值（多选用逗号分隔或 JSON 字符串） */
    private String paramValue;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
