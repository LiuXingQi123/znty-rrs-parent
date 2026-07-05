package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * SQL 脚本文件信息。
 */
@Data
public class ScriptFileDto {

    /** 脚本文件名 */
    private String fileName;

    /** 脚本类型：schema=建表 / demo=演示数据 */
    private String scriptType;

    /** 所属模块编码 */
    private String moduleCode;

    /** 所属模块名称 */
    private String moduleName;

    /** 执行顺序 */
    private Integer sortOrder;

    /** 文件是否存在 */
    private Boolean exists;

    /** 文件大小 */
    private Long fileSize;

    /** CREATE TABLE 数量 */
    private Integer createCount;

    /** TRUNCATE TABLE 数量 */
    private Integer truncateCount;

    /** INSERT INTO 数量 */
    private Integer insertCount;

    /** 影响表清单 */
    private List<String> affectedTables;
}
