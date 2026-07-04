package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * 脚本任务配置返回对象。
 */
@Data
public class ScriptTaskDto {

    /** 脚本任务编码 */
    private String taskCode;

    /** 脚本任务名称 */
    private String taskName;

    /** 任务说明 */
    private String description;

    /** 风险等级 */
    private String riskLevel;

    /** 二次确认文本 */
    private String confirmText;

    /** 影响范围说明 */
    private String affectScope;

    /** 包含的脚本或表 */
    private List<String> items;
}
