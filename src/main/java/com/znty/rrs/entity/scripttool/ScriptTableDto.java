package com.znty.rrs.entity.scripttool;

import lombok.Data;

/**
 * 脚本工具可清空表信息。
 */
@Data
public class ScriptTableDto {

    /** 表唯一 key，格式为 database.table */
    private String tableKey;

    /** 数据库名 */
    private String databaseName;

    /** 表名 */
    private String tableName;

    /** 表说明 */
    private String tableDesc;
}
