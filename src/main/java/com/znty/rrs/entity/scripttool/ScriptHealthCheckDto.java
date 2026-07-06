package com.znty.rrs.entity.scripttool;

import lombok.Data;

import java.util.List;

/**
 * 脚本工具环境健康检查结果。
 */
@Data
public class ScriptHealthCheckDto {

    /** 整体状态：success=正常 / warning=警告 / failed=失败 */
    private String status;

    /** SQL 目录绝对路径 */
    private String sqlPath;

    /** 数据库连接信息 */
    private String connectionInfo;

    /** 期望数据库数量 */
    private Integer expectedDatabaseCount;

    /** 期望表数量 */
    private Integer expectedTableCount;

    /** 已存在表数量 */
    private Integer existingTableCount;

    /** 缺失表数量 */
    private Integer missingTableCount;

    /** 空表数量 */
    private Integer emptyTableCount;

    /** 有数据表数量 */
    private Integer nonEmptyTableCount;

    /** 检查项列表 */
    private List<ScriptHealthItemDto> items;
}
