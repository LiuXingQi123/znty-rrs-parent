package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * 脚本工具可清空表分组。
 */
@Data
public class ScriptTableGroupDto {

    /** 分组编码 */
    private String groupCode;

    /** 分组名称 */
    private String groupName;

    /** 数据库名 */
    private String databaseName;

    /** 表列表 */
    private List<ScriptTableDto> tables;
}
