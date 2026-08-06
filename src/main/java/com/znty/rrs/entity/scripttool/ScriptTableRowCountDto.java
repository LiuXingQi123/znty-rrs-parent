package com.znty.rrs.entity.scripttool;

import lombok.Data;

/**
 * 表记录数统计项。
 */
@Data
public class ScriptTableRowCountDto {

    /** 库名 */
    private String databaseName;

    /** 表名 */
    private String tableName;

    /** 表说明 */
    private String tableDesc;

    /** 记录数 */
    private Long rowCount;
}
