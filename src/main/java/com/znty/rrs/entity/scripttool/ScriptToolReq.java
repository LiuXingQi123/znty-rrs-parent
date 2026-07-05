package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * 脚本工具请求参数。
 */
@Data
public class ScriptToolReq {

    /** 脚本任务编码 */
    private String taskCode;

    /** 模块编码 */
    private String moduleCode;

    /** Demo 场景编码 */
    private String sceneCode;

    /** 二次确认文本 */
    private String confirmText;

    /** 当前操作人 ID */
    private String currentUserId;

    /** 当前操作人名称 */
    private String currentUserName;

    /** 选中的清空表 key 列表，格式为 database.table */
    private List<String> tableKeys;
}
