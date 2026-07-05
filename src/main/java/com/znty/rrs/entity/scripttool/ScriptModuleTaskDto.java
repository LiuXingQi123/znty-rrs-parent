package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * 模块级重置任务配置。
 */
@Data
public class ScriptModuleTaskDto {

    /** 模块编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 任务说明 */
    private String description;

    /** 风险等级 */
    private String riskLevel;

    /** 二次确认文本 */
    private String confirmText;

    /** Demo SQL 文件 */
    private String demoFileName;

    /** 影响表清单 */
    private List<String> affectedTables;
}
