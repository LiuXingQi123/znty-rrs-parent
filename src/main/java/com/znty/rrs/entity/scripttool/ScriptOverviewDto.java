package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * 脚本清单与依赖总览。
 */
@Data
public class ScriptOverviewDto {

    /** SQL 目录绝对路径 */
    private String sqlPath;

    /** 建表脚本数量 */
    private Integer schemaFileCount;

    /** Demo 脚本数量 */
    private Integer demoFileCount;

    /** 缺失脚本数量 */
    private Integer missingFileCount;

    /** 脚本文件清单 */
    private List<ScriptFileDto> files;
}
