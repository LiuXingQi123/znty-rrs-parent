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

    /** 检查项列表 */
    private List<ScriptHealthItemDto> items;
}
