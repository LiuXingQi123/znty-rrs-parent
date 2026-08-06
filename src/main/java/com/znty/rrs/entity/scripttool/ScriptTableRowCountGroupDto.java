package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * 单库表记录数统计结果。
 */
@Data
public class ScriptTableRowCountGroupDto {

    /** 库名 */
    private String databaseName;

    /** 库说明 */
    private String databaseDesc;

    /** 表记录数列表（按记录数降序） */
    private List<ScriptTableRowCountDto> tables;
}
