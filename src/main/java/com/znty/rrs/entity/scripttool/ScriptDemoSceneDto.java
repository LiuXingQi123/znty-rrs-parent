package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * Demo 场景生成配置。
 */
@Data
public class ScriptDemoSceneDto {

    /** 场景编码 */
    private String sceneCode;

    /** 场景名称 */
    private String sceneName;

    /** 场景说明 */
    private String description;

    /** 风险等级 */
    private String riskLevel;

    /** 二次确认文本 */
    private String confirmText;

    /** 影响表清单 */
    private List<String> affectedTables;

    /** 依赖前置数据说明 */
    private List<String> dependencies;
}
