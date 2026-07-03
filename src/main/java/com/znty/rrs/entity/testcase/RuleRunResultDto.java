package com.znty.rrs.entity.testcase;


import com.znty.rrs.entity.bo.RuleTestRunBo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 规则执行结果 / 执行历史 DTO，同时承载临时执行结果和持久化执行历史。
 * <p>临时执行时 id/ruleId/caseId/errorMessage/startTime/finishTime 为 null。</p>
 */
@Data
public class RuleRunResultDto {
    /** 执行记录 ID（历史查询时有值，临时执行时为 null） */
    private Long id;
    /** 规则 ID（历史查询时有值） */
    private Long ruleId;
    /** 用例 ID（关联测试用例执行时有值） */
    private Long caseId;
    /** 执行状态：pass-通过，fail-失败 */
    private String status;
    /** 执行输出（脚本返回值或异常信息） */
    private String output;
    /** 错误信息（执行失败时填充） */
    private String errorMessage;
    /** 开始时间（yyyy-MM-dd HH:mm:ss 格式，历史查询时有值） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
    /** 结束时间（yyyy-MM-dd HH:mm:ss 格式，历史查询时有值） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;
    /** 步骤日志列表，每项含 time/type/msg 键 */
    private List<Map<String, Object>> logs = new ArrayList<>();

    /**
     * 从执行记录实体和日志列表构建 DTO，对时间字段做格式化。
     */
    public static RuleRunResultDto from(RuleTestRunBo run, List<Map<String, Object>> logs) {
        RuleRunResultDto dto = new RuleRunResultDto();
        dto.setId(run.getId());
        dto.setRuleId(run.getRuleId());
        dto.setCaseId(run.getCaseId());
        dto.setStatus(run.getRunStatus());
        dto.setOutput(run.getOutput());
        dto.setErrorMessage(run.getErrorMessage());
        dto.setStartTime(run.getStartTime());
        dto.setFinishTime(run.getFinishTime());
        dto.setLogs(logs);
        return dto;
    }
}
